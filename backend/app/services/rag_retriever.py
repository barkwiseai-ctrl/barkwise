import json
import os
import re
import time
from copy import deepcopy
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional, Set, Tuple

from app.data import community_events, community_posts, groups
from app.services.rag_dog_knowledge import TRUSTED_DOG_KNOWLEDGE
from app.services.service_store import service_store


class RagRetriever:
    """Local lexical retriever for BarkAI grounding context."""

    SOURCE_BASE_BOOSTS: Dict[str, float] = {
        "knowledge_base": 0.25,
        "provider": 0.10,
        "group": 0.05,
        "community_post": 0.05,
        "community_event": 0.05,
    }
    TOKEN_NORMALIZATION: Dict[str, str] = {
        "vaccines": "vaccine",
        "vaccination": "vaccine",
        "vaccinations": "vaccine",
        "puppies": "puppy",
        "dogs": "dog",
        "vomiting": "vomit",
        "diarrhoea": "diarrhea",
        "parvovirus": "parvo",
        "toxic": "toxin",
        "poisoning": "poison",
        "groomers": "grooming",
        "walkers": "walking",
    }
    QUERY_EXPANSIONS: Dict[str, Set[str]] = {
        "feed": {"nutrition", "diet", "food", "feeding"},
        "food": {"nutrition", "diet", "feed", "feeding"},
        "feeding": {"nutrition", "diet", "feed", "food"},
        "diet": {"nutrition", "food", "feeding", "weight"},
        "brush": {"brushing", "grooming", "coat", "skin"},
        "brushing": {"brush", "grooming", "coat"},
        "groom": {"grooming", "coat", "skin"},
        "grooming": {"groom", "coat", "brush"},
        "coat": {"grooming", "brush", "skin"},
        "itch": {"skin", "allergy", "coat"},
        "itchy": {"skin", "allergy", "coat"},
        "walk": {"walking", "exercise", "activity"},
        "walking": {"walk", "exercise", "activity"},
        "exercise": {"walk", "walking", "activity", "weight"},
        "overweight": {"weight", "obesity", "nutrition", "exercise"},
        "weight": {"obesity", "nutrition", "exercise", "diet"},
        "anxious": {"anxiety", "behavior", "training"},
        "anxiety": {"anxious", "behavior", "training"},
        "training": {"behavior", "reward", "reactive"},
        "reactive": {"behavior", "training", "anxiety"},
    }
    SOURCE_CAPS: Dict[str, int] = {
        "knowledge_base": 3,
        "provider": 2,
        "group": 2,
        "community_post": 2,
        "community_event": 2,
    }
    HIGH_RISK_SOURCE_CAPS: Dict[str, int] = {
        "knowledge_base": 4,
    }
    HIGH_RISK_AU_AUTHORITIES: Set[str] = {
        "rspca australia",
        "agriculture victoria",
        "nsw government",
        "australian veterinary association",
        "ava",
    }
    HIGH_RISK_GLOBAL_AUTHORITIES: Set[str] = {
        "aaha",
        "wsava",
        "avsab",
        "cdc",
        "fda",
        "fda cvm",
        "who",
        "american heartworm society",
        "merck veterinary manual",
        "aspca animal poison control",
        "avma",
    }
    HIGH_RISK_BLOCKED_AUTHORITY_MARKERS: Set[str] = {
        "reddit",
    }

    def __init__(self) -> None:
        self.cache_ttl_seconds = self._read_int_env("RAG_CONTEXT_CACHE_TTL_SECONDS", default=45, min_value=0, max_value=3600)
        self.cache_max_entries = self._read_int_env("RAG_CONTEXT_CACHE_MAX_ENTRIES", default=256, min_value=0, max_value=5000)
        self._context_cache: Dict[str, Tuple[float, Dict[str, Any]]] = {}

    def build_context(
        self,
        message: str,
        suburb: Optional[str],
        profile_memory: Dict[str, Any],
        intent: str,
        tool_results: Dict[str, Any],
        high_risk_mode: bool = False,
        high_risk_terms: Optional[List[str]] = None,
    ) -> Dict[str, Any]:
        profile_summary = self._profile_summary(profile_memory)
        tool_boost_names = self._extract_tool_entity_names(tool_results)
        cache_key = self._context_cache_key(
            message=message,
            suburb=suburb,
            intent=intent,
            profile_summary=profile_summary,
            tool_boost_names=tool_boost_names,
            high_risk_mode=high_risk_mode,
            high_risk_terms=high_risk_terms or [],
        )
        cached = self._context_cache_get(cache_key)
        if cached is not None:
            return cached

        query_tokens = self._expand_query_tokens(self._rag_tokens(message))
        scored: List[Tuple[float, Dict[str, Any]]] = []

        scored.extend(self._retrieve_dog_knowledge_docs(query_tokens=query_tokens, intent=intent))
        scored.extend(self._retrieve_provider_docs(query_tokens=query_tokens, suburb=suburb))
        scored.extend(self._retrieve_group_docs(query_tokens=query_tokens, suburb=suburb))
        scored.extend(self._retrieve_post_docs(query_tokens=query_tokens, suburb=suburb))
        scored.extend(self._retrieve_event_docs(query_tokens=query_tokens, suburb=suburb))

        if tool_boost_names:
            boosted: List[Tuple[float, Dict[str, Any]]] = []
            for score, doc in scored:
                title = str(doc.get("title", "")).lower()
                if any(name in title for name in tool_boost_names):
                    score += 0.5
                boosted.append((score, doc))
            scored = boosted

        intent_boosted: List[Tuple[float, Dict[str, Any]]] = []
        health_risk_query = self._is_health_risk_query(query_tokens) or high_risk_mode
        for score, doc in scored:
            source = str(doc.get("source", ""))
            score += self._intent_source_boost(intent=intent, source=source)
            score += self.SOURCE_BASE_BOOSTS.get(source, 0.0)
            if health_risk_query:
                if source == "knowledge_base":
                    score += 0.85
                elif source in {"community_post", "community_event", "group"}:
                    score -= 0.20
                elif source == "provider":
                    score -= 0.05
            intent_boosted.append((score, doc))
        scored = intent_boosted

        if high_risk_mode:
            scored = self._apply_high_risk_trust_policy(
                scored,
                high_risk_terms=high_risk_terms or [],
                query_tokens=query_tokens,
            )

        scored.sort(key=lambda item: item[0], reverse=True)
        if high_risk_mode:
            top_docs = self._select_top_docs(
                scored,
                max_docs=4,
                source_caps=self.HIGH_RISK_SOURCE_CAPS,
            )
            if high_risk_terms and not self._has_high_risk_term_match(top_docs, high_risk_terms):
                top_docs = self._fallback_high_risk_docs(priority_terms=high_risk_terms)
        else:
            top_docs = self._select_top_docs(scored)
        if not top_docs:
            if high_risk_mode:
                top_docs = self._fallback_high_risk_docs(priority_terms=high_risk_terms or [])
            elif intent in {"general_pet_question", "weight_concern", "lost_found"}:
                top_docs = []
            else:
                top_docs = [doc for _, doc in self._retrieve_provider_docs(query_tokens=set(), suburb=suburb)[:2]]

        context = {
            "intent": intent,
            "query": message.strip(),
            "suburb": suburb,
            "profile_summary": profile_summary,
            "documents": top_docs,
            "high_risk_mode": high_risk_mode,
            "high_risk_terms": list(high_risk_terms or []),
            "source_policy": "trusted_knowledge_only_no_reddit" if high_risk_mode else "default",
        }
        self._context_cache_set(cache_key, context)
        return context

    @staticmethod
    def _read_int_env(name: str, default: int, min_value: int, max_value: int) -> int:
        raw = os.getenv(name)
        if raw is None:
            parsed = default
        else:
            try:
                parsed = int(str(raw).strip())
            except (TypeError, ValueError):
                parsed = default
        if parsed < min_value:
            parsed = min_value
        if parsed > max_value:
            parsed = max_value
        return parsed

    @staticmethod
    def _profile_summary(profile_memory: Dict[str, Any]) -> Dict[str, Any]:
        return {
            key: value
            for key, value in profile_memory.items()
            if key in {"pet_name", "pet_type", "breed", "age_years", "weight_kg", "suburb"}
        }

    def _context_cache_key(
        self,
        *,
        message: str,
        suburb: Optional[str],
        intent: str,
        profile_summary: Dict[str, Any],
        tool_boost_names: Set[str],
        high_risk_mode: bool,
        high_risk_terms: List[str],
    ) -> str:
        payload = {
            "message": str(message).strip().lower(),
            "suburb": str(suburb or "").strip().lower(),
            "intent": str(intent),
            "profile_summary": profile_summary,
            "tool_entities": sorted(tool_boost_names),
            "high_risk_mode": bool(high_risk_mode),
            "high_risk_terms": sorted(str(term).strip().lower() for term in high_risk_terms if str(term).strip()),
        }
        return json.dumps(payload, sort_keys=True, separators=(",", ":"), default=str)

    def _context_cache_get(self, key: str) -> Optional[Dict[str, Any]]:
        if self.cache_ttl_seconds <= 0 or self.cache_max_entries <= 0:
            return None
        entry = self._context_cache.get(key)
        if entry is None:
            return None
        expires_at, context = entry
        now = time.monotonic()
        if expires_at <= now:
            self._context_cache.pop(key, None)
            return None
        return deepcopy(context)

    def _context_cache_set(self, key: str, context: Dict[str, Any]) -> None:
        if self.cache_ttl_seconds <= 0 or self.cache_max_entries <= 0:
            return
        now = time.monotonic()
        self._prune_context_cache(now)
        self._context_cache[key] = (now + float(self.cache_ttl_seconds), deepcopy(context))
        while len(self._context_cache) > self.cache_max_entries:
            oldest_key = next(iter(self._context_cache))
            self._context_cache.pop(oldest_key, None)

    def _prune_context_cache(self, now: float) -> None:
        expired_keys = [key for key, (expires_at, _) in self._context_cache.items() if expires_at <= now]
        for key in expired_keys:
            self._context_cache.pop(key, None)

    def _intent_source_boost(self, intent: str, source: str) -> float:
        if intent in {"general_pet_question", "weight_concern"} and source == "knowledge_base":
            return 0.35
        if intent in {"find_dog_walker", "find_groomer", "add_service_listing"} and source == "provider":
            return 0.35
        if intent == "community_discovery" and source in {"group", "community_post", "community_event"}:
            return 0.25
        if intent == "lost_found" and source in {"community_post", "group"}:
            return 0.25
        return 0.0

    def _is_health_risk_query(self, query_tokens: Set[str]) -> bool:
        risk_tokens = {
            "parvo",
            "poison",
            "toxin",
            "vomit",
            "seizure",
            "bloody",
            "fever",
            "dehydration",
            "diarrhea",
            "lethargy",
            "vaccine",
            "rabies",
        }
        return len(query_tokens.intersection(risk_tokens)) > 0

    def _select_top_docs(
        self,
        scored: List[Tuple[float, Dict[str, Any]]],
        *,
        max_docs: int = 6,
        source_caps: Optional[Dict[str, int]] = None,
    ) -> List[Dict[str, Any]]:
        selected: List[Dict[str, Any]] = []
        seen_keys: Set[str] = set()
        source_counts: Dict[str, int] = {}
        caps = source_caps or self.SOURCE_CAPS

        for score, doc in scored:
            if score <= 0:
                continue
            source = str(doc.get("source", "")).strip()
            doc_id = str(doc.get("id", "")).strip()
            dedupe_key = f"{source}:{doc_id}"
            if dedupe_key in seen_keys:
                continue
            cap = caps.get(source, 2)
            if source_counts.get(source, 0) >= cap:
                continue
            seen_keys.add(dedupe_key)
            source_counts[source] = source_counts.get(source, 0) + 1
            selected.append(doc)
            if len(selected) >= max_docs:
                break

        return selected

    def fallback_answer(
        self,
        rag_context: Dict[str, Any],
        support_mode: bool,
    ) -> Optional[str]:
        docs = rag_context.get("documents", [])
        if not isinstance(docs, list) or not docs:
            return None
        high_risk_mode = bool(rag_context.get("high_risk_mode", False))
        high_risk_terms = rag_context.get("high_risk_terms", [])
        if not isinstance(high_risk_terms, list):
            high_risk_terms = []

        lines: List[str] = []
        if support_mode:
            lines.append("I know this can feel stressful, and you are doing the right thing by asking.")
            lines.append("")
        if high_risk_mode:
            concern_label = ", ".join(str(item) for item in high_risk_terms[:3] if str(item).strip())
            if concern_label:
                lines.append(f"This looks high-risk ({concern_label}). I cannot diagnose in chat.")
            else:
                lines.append("This looks high-risk. I cannot diagnose in chat.")
            lines.append("Use this safety-first plan now:")
            lines.append("1. Contact a veterinarian or emergency clinic for real-time triage immediately.")
            lines.append("2. Keep your dog calm and avoid home medications or induced vomiting unless a vet directs it.")
            lines.append(
                "3. If collapse, trouble breathing, repeated vomiting, seizures, or severe weakness occur, treat as an emergency now."
            )
            lines.append("")
            lines.append("Trusted references for immediate guidance:")
        else:
            lines.append("From what I can see in your local BarkAI data:")
        for doc in docs[:3]:
            if not isinstance(doc, dict):
                continue
            title = str(doc.get("title", "Option")).strip()
            snippet = str(doc.get("snippet", "")).strip()
            authority = str(doc.get("authority", "")).strip()
            if authority:
                title = f"{title} ({authority})"
            if snippet:
                lines.append(f"- {title}: {snippet}")
            else:
                lines.append(f"- {title}")

        lines.append("")
        if high_risk_mode:
            lines.append("I can help you prepare what to tell the vet right now (symptoms, timing, and possible exposure).")
        else:
            lines.append("If you want, I can narrow this to the best next action for your specific pet.")
        return "\n".join(lines).strip()

    def _apply_high_risk_trust_policy(
        self,
        scored: List[Tuple[float, Dict[str, Any]]],
        *,
        high_risk_terms: List[str],
        query_tokens: Set[str],
    ) -> List[Tuple[float, Dict[str, Any]]]:
        filtered: List[Tuple[float, Dict[str, Any]]] = []
        phrase_terms, token_terms = self._expand_high_risk_terms(high_risk_terms)
        for score, doc in scored:
            source = str(doc.get("source", "")).strip()
            if source != "knowledge_base":
                continue
            authority = str(doc.get("authority", "")).strip()
            authority_priority = self._high_risk_authority_priority(authority)
            if authority_priority < 0:
                continue
            title = str(doc.get("title", "")).lower()
            snippet = str(doc.get("snippet", "")).lower()
            doc_blob = f"{title} {snippet}"
            doc_tokens = self._rag_tokens(doc_blob)
            phrase_hits = [term for term in phrase_terms if term in doc_blob]
            token_hits = [term for term in token_terms if term in doc_tokens]
            matched_terms = [*phrase_hits, *token_hits]
            relevance_bonus = 0.0
            if matched_terms:
                relevance_bonus += 1.2 + (0.2 * min(3, len(matched_terms)))
            elif (phrase_terms or token_terms) and score < 0.45:
                continue
            overlap_bonus = self._rag_overlap_score(query_tokens=query_tokens, doc_tokens=doc_tokens)
            filtered.append((score + 1.0 + authority_priority + relevance_bonus + overlap_bonus, doc))
        return filtered

    def _high_risk_authority_priority(self, authority: str) -> float:
        normalized = authority.strip().lower()
        if not normalized:
            return 0.0
        if any(marker in normalized for marker in self.HIGH_RISK_BLOCKED_AUTHORITY_MARKERS):
            return -5.0
        if any(authority_name in normalized for authority_name in self.HIGH_RISK_AU_AUTHORITIES):
            return 1.5
        if any(authority_name in normalized for authority_name in self.HIGH_RISK_GLOBAL_AUTHORITIES):
            return 0.9
        return 0.2

    def _fallback_high_risk_docs(self, priority_terms: List[str]) -> List[Dict[str, Any]]:
        topic_markers = {
            "parvo",
            "poison",
            "toxin",
            "emergency",
            "heatstroke",
            "tick",
            "tick paralysis",
            "rabies",
            "heartworm",
            "vaccine",
            "leptospirosis",
            "dehydration",
            "vomiting",
            "diarrhea",
        }
        phrase_terms, token_terms = self._expand_high_risk_terms(priority_terms)
        matched_scored_docs: List[Tuple[float, Dict[str, Any]]] = []
        generic_scored_docs: List[Tuple[float, Dict[str, Any]]] = []
        for item in TRUSTED_DOG_KNOWLEDGE:
            title = str(item.get("title", ""))
            content = str(item.get("content", ""))
            authority = str(item.get("source", ""))
            priority = self._high_risk_authority_priority(authority)
            if priority < 0:
                continue
            topics = [str(topic).lower() for topic in item.get("topics", [])]
            topic_score = 1.0 if any(marker in topic for marker in topic_markers for topic in topics) else 0.0
            if not topic_score and any(marker in content.lower() for marker in topic_markers):
                topic_score = 0.5
            doc_blob = f"{title.lower()} {content.lower()}"
            doc_tokens = self._rag_tokens(doc_blob)
            phrase_hits = [term for term in phrase_terms if term in doc_blob]
            token_hits = [term for term in token_terms if term in doc_tokens]
            has_match = bool(phrase_hits or token_hits)
            match_bonus = 1.5 if has_match else 0.0
            doc = {
                "source": "knowledge_base",
                "id": str(item.get("id", "")),
                "title": title,
                "authority": authority,
                "url": str(item.get("url", "")),
                "snippet": content,
            }
            scored_item = (priority + topic_score + match_bonus, doc)
            if has_match:
                matched_scored_docs.append(scored_item)
            else:
                generic_scored_docs.append(scored_item)
        if (phrase_terms or token_terms) and matched_scored_docs:
            scored_docs = matched_scored_docs
        else:
            scored_docs = [*matched_scored_docs, *generic_scored_docs]
        scored_docs.sort(key=lambda pair: pair[0], reverse=True)
        return self._select_top_docs(
            scored_docs,
            max_docs=4,
            source_caps=self.HIGH_RISK_SOURCE_CAPS,
        )

    def _expand_high_risk_terms(self, high_risk_terms: List[str]) -> Tuple[Set[str], Set[str]]:
        phrase_terms: Set[str] = set()
        token_terms: Set[str] = set()
        for raw_term in high_risk_terms:
            term = str(raw_term).strip().lower()
            if not term:
                continue
            if " " in term:
                phrase_terms.add(term)
            for token in self._rag_tokens(term):
                if len(token) >= 4:
                    token_terms.add(token)
        return phrase_terms, token_terms

    def _has_high_risk_term_match(self, docs: List[Dict[str, Any]], high_risk_terms: List[str]) -> bool:
        phrase_terms, token_terms = self._expand_high_risk_terms(high_risk_terms)
        if not phrase_terms and not token_terms:
            return False
        for doc in docs:
            if not isinstance(doc, dict):
                continue
            doc_blob = f"{str(doc.get('title', '')).lower()} {str(doc.get('snippet', '')).lower()}"
            if any(term in doc_blob for term in phrase_terms):
                return True
            doc_tokens = self._rag_tokens(doc_blob)
            if any(term in doc_tokens for term in token_terms):
                return True
        return False

    def _retrieve_dog_knowledge_docs(
        self,
        query_tokens: Set[str],
        intent: str,
    ) -> List[Tuple[float, Dict[str, Any]]]:
        if intent not in {"general_pet_question", "general_assistant_query", "weight_concern", "lost_found"}:
            return []

        scored: List[Tuple[float, Dict[str, Any]]] = []
        priority_tokens = {
            "vaccine",
            "vaccination",
            "weight",
            "nutrition",
            "skin",
            "coat",
            "itch",
            "toxin",
            "poison",
            "grooming",
            "preventive",
            "wellness",
        }
        for item in TRUSTED_DOG_KNOWLEDGE:
            title = str(item.get("title", ""))
            content = str(item.get("content", ""))
            source = str(item.get("source", ""))
            url = str(item.get("url", ""))
            topics = item.get("topics", [])
            topics_text = " ".join(str(topic) for topic in topics)
            doc_tokens = self._rag_tokens(f"{title} {topics_text} {content}")
            score = self._rag_overlap_score(query_tokens=query_tokens, doc_tokens=doc_tokens)
            if any(topic in query_tokens for topic in priority_tokens):
                score += 0.2
            if score <= 0:
                continue
            scored.append(
                (
                    score,
                    {
                        "source": "knowledge_base",
                        "id": str(item.get("id", "")),
                        "title": title,
                        "authority": source,
                        "url": url,
                        "snippet": content,
                    },
                )
            )

        scored.sort(key=lambda pair: pair[0], reverse=True)
        return scored[:12]

    def _retrieve_provider_docs(
        self,
        query_tokens: Set[str],
        suburb: Optional[str],
    ) -> List[Tuple[float, Dict[str, Any]]]:
        providers = service_store.list_providers(suburb=suburb, limit=20)
        scored: List[Tuple[float, Dict[str, Any]]] = []
        for provider in providers:
            text = " ".join(
                [
                    provider.name,
                    provider.category,
                    provider.suburb,
                    provider.description,
                    provider.full_description,
                ]
            )
            doc_tokens = self._rag_tokens(text)
            score = self._rag_overlap_score(query_tokens=query_tokens, doc_tokens=doc_tokens)
            if suburb and provider.suburb.lower() == suburb.lower():
                score += 0.2
            if score <= 0 and query_tokens:
                continue
            scored.append(
                (
                    score,
                    {
                        "source": "provider",
                        "id": provider.id,
                        "title": provider.name,
                        "suburb": provider.suburb,
                        "snippet": f"{provider.category.replace('_', ' ')} from ${provider.price_from}, rating {provider.rating}. {provider.description}",
                    },
                )
            )
        scored.sort(key=lambda item: item[0], reverse=True)
        return scored[:8]

    def _retrieve_group_docs(
        self,
        query_tokens: Set[str],
        suburb: Optional[str],
    ) -> List[Tuple[float, Dict[str, Any]]]:
        scored: List[Tuple[float, Dict[str, Any]]] = []
        for group in groups:
            text = f"{group.name} {group.suburb} group community pet owners members {group.member_count}"
            doc_tokens = self._rag_tokens(text)
            score = self._rag_overlap_score(query_tokens=query_tokens, doc_tokens=doc_tokens)
            if suburb and group.suburb.lower() == suburb.lower():
                score += 0.2
            if score <= 0 and query_tokens:
                continue
            scored.append(
                (
                    score,
                    {
                        "source": "group",
                        "id": group.id,
                        "title": group.name,
                        "suburb": group.suburb,
                        "snippet": f"{'Official' if group.official else 'Local'} group with {group.member_count} members.",
                    },
                )
            )
        scored.sort(key=lambda item: item[0], reverse=True)
        return scored[:6]

    def _retrieve_post_docs(
        self,
        query_tokens: Set[str],
        suburb: Optional[str],
    ) -> List[Tuple[float, Dict[str, Any]]]:
        scored: List[Tuple[float, Dict[str, Any]]] = []
        for post in community_posts:
            text = f"{post.title} {post.body} {post.suburb} {post.type}"
            doc_tokens = self._rag_tokens(text)
            score = self._rag_overlap_score(query_tokens=query_tokens, doc_tokens=doc_tokens)
            if suburb and post.suburb.lower() == suburb.lower():
                score += 0.15
            score += self._recency_boost(post.created_at)
            if score <= 0 and query_tokens:
                continue
            scored.append(
                (
                    score,
                    {
                        "source": "community_post",
                        "id": post.id,
                        "title": post.title,
                        "suburb": post.suburb,
                        "snippet": post.body,
                    },
                )
            )
        scored.sort(key=lambda item: item[0], reverse=True)
        return scored[:6]

    def _retrieve_event_docs(
        self,
        query_tokens: Set[str],
        suburb: Optional[str],
    ) -> List[Tuple[float, Dict[str, Any]]]:
        scored: List[Tuple[float, Dict[str, Any]]] = []
        for event in community_events:
            text = f"{event.title} {event.description} {event.suburb} event pets community"
            doc_tokens = self._rag_tokens(text)
            score = self._rag_overlap_score(query_tokens=query_tokens, doc_tokens=doc_tokens)
            if suburb and event.suburb.lower() == suburb.lower():
                score += 0.15
            score += self._recency_boost(event.date)
            if score <= 0 and query_tokens:
                continue
            scored.append(
                (
                    score,
                    {
                        "source": "community_event",
                        "id": event.id,
                        "title": event.title,
                        "suburb": event.suburb,
                        "snippet": event.description,
                    },
                )
            )
        scored.sort(key=lambda item: item[0], reverse=True)
        return scored[:4]

    def _extract_tool_entity_names(self, tool_results: Dict[str, Any]) -> Set[str]:
        names: Set[str] = set()
        services = tool_results.get("search_services")
        if isinstance(services, list):
            for service in services:
                if isinstance(service, dict):
                    value = str(service.get("name", "")).strip().lower()
                    if value:
                        names.add(value)

        found_groups = tool_results.get("search_groups")
        if isinstance(found_groups, list):
            for group in found_groups:
                if isinstance(group, dict):
                    value = str(group.get("name", "")).strip().lower()
                    if value:
                        names.add(value)
        return names

    def _rag_tokens(self, text: str) -> Set[str]:
        normalized = re.sub(r"[^a-z0-9\s]", " ", text.lower())
        raw_tokens = [token for token in normalized.split() if len(token) >= 3]
        stop_words = {
            "the",
            "and",
            "for",
            "with",
            "from",
            "that",
            "this",
            "your",
            "have",
            "about",
            "can",
            "are",
            "was",
            "but",
            "you",
            "our",
            "its",
            "near",
            "how",
            "what",
            "when",
            "where",
            "why",
            "who",
            "dog",
            "dogs",
            "pet",
            "pets",
        }
        cleaned: Set[str] = set()
        for token in raw_tokens:
            if token in stop_words:
                continue
            normalized_token = self.TOKEN_NORMALIZATION.get(token, token)
            if normalized_token.endswith("s") and len(normalized_token) >= 5:
                singular = normalized_token[:-1]
                if singular:
                    normalized_token = singular
            cleaned.add(normalized_token)
        return cleaned

    def _expand_query_tokens(self, tokens: Set[str]) -> Set[str]:
        expanded = set(tokens)
        for token in list(tokens):
            expanded.update(self.QUERY_EXPANSIONS.get(token, set()))
        return expanded

    def _rag_overlap_score(self, query_tokens: Set[str], doc_tokens: Set[str]) -> float:
        if not doc_tokens:
            return 0.0
        if not query_tokens:
            return 0.05
        overlap = len(query_tokens.intersection(doc_tokens))
        if overlap == 0:
            return 0.0
        return overlap / max(1.0, len(query_tokens) ** 0.5)

    def _recency_boost(self, iso_datetime: str) -> float:
        try:
            parsed = datetime.fromisoformat(iso_datetime.replace("Z", "+00:00"))
            if parsed.tzinfo is None:
                parsed = parsed.replace(tzinfo=timezone.utc)
            now = datetime.now(timezone.utc)
            age_days = max(0.0, (now - parsed).total_seconds() / 86400.0)
        except Exception:
            return 0.0
        if age_days <= 3:
            return 0.20
        if age_days <= 10:
            return 0.10
        if age_days <= 30:
            return 0.05
        return 0.0
