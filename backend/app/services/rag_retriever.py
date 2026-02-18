import re
from typing import Any, Dict, List, Optional, Set, Tuple

from app.data import community_events, community_posts, groups
from app.services.rag_dog_knowledge import TRUSTED_DOG_KNOWLEDGE
from app.services.service_store import service_store


class RagRetriever:
    """Local lexical retriever for BarkAI grounding context."""

    def build_context(
        self,
        message: str,
        suburb: Optional[str],
        profile_memory: Dict[str, Any],
        intent: str,
        tool_results: Dict[str, Any],
    ) -> Dict[str, Any]:
        query_tokens = self._rag_tokens(message)
        scored: List[Tuple[float, Dict[str, Any]]] = []

        scored.extend(self._retrieve_dog_knowledge_docs(query_tokens=query_tokens, intent=intent))
        scored.extend(self._retrieve_provider_docs(query_tokens=query_tokens, suburb=suburb))
        scored.extend(self._retrieve_group_docs(query_tokens=query_tokens, suburb=suburb))
        scored.extend(self._retrieve_post_docs(query_tokens=query_tokens, suburb=suburb))
        scored.extend(self._retrieve_event_docs(query_tokens=query_tokens, suburb=suburb))

        tool_boost_names = self._extract_tool_entity_names(tool_results)
        if tool_boost_names:
            boosted: List[Tuple[float, Dict[str, Any]]] = []
            for score, doc in scored:
                title = str(doc.get("title", "")).lower()
                if any(name in title for name in tool_boost_names):
                    score += 0.5
                boosted.append((score, doc))
            scored = boosted

        scored.sort(key=lambda item: item[0], reverse=True)
        top_docs = [doc for score, doc in scored if score > 0][:6]
        if not top_docs:
            top_docs = [doc for _, doc in self._retrieve_provider_docs(query_tokens=set(), suburb=suburb)[:2]]

        profile_summary = {
            key: value
            for key, value in profile_memory.items()
            if key in {"pet_name", "pet_type", "breed", "age_years", "weight_kg", "suburb"}
        }

        return {
            "intent": intent,
            "query": message.strip(),
            "suburb": suburb,
            "profile_summary": profile_summary,
            "documents": top_docs,
        }

    def fallback_answer(
        self,
        rag_context: Dict[str, Any],
        support_mode: bool,
    ) -> Optional[str]:
        docs = rag_context.get("documents", [])
        if not isinstance(docs, list) or not docs:
            return None

        lines: List[str] = []
        if support_mode:
            lines.append("I know this can feel stressful, and you are doing the right thing by asking.")
            lines.append("")
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
        lines.append("If you want, I can narrow this to the best next action for your specific pet.")
        return "\n".join(lines).strip()

    def _retrieve_dog_knowledge_docs(
        self,
        query_tokens: Set[str],
        intent: str,
    ) -> List[Tuple[float, Dict[str, Any]]]:
        if intent not in {"general_pet_question", "weight_concern", "lost_found"}:
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
            if intent == "general_pet_question":
                score += 0.35
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
        return scored[:4]

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
        }
        return {token for token in raw_tokens if token not in stop_words}

    def _rag_overlap_score(self, query_tokens: Set[str], doc_tokens: Set[str]) -> float:
        if not doc_tokens:
            return 0.0
        if not query_tokens:
            return 0.05
        overlap = len(query_tokens.intersection(doc_tokens))
        if overlap == 0:
            return 0.0
        return overlap / max(1.0, len(query_tokens) ** 0.5)

