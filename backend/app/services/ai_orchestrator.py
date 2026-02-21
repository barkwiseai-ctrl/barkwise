import json
import logging
import os
import re
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, Generator, List, Optional, Pattern
from uuid import uuid4

from app.data import group_memberships, groups
from app.models import ChatResponse, ChatTurn, CtaChip, Group, GroupJoinRecord, PetProfileSuggestion
from app.services.memory_store import MemoryStore
from app.services.rag_retriever import RagRetriever
from app.services.service_store import service_store

try:
    from openai import OpenAI
except Exception:  # pragma: no cover
    OpenAI = None

logger = logging.getLogger(__name__)


ALLOWED_INTENTS = {
    "find_dog_walker",
    "find_groomer",
    "lost_found",
    "community_discovery",
    "weight_concern",
    "provider_onboarding",
    "add_service_listing",
    "add_pet_owner_profile",
    "manage_community_group",
    "general_pet_question",
    "general_assistant_query",
    "out_of_scope_non_pet",
}

APP_ROUTE_INTENTS = {
    "find_dog_walker",
    "find_groomer",
    "lost_found",
    "community_discovery",
    "weight_concern",
    "provider_onboarding",
    "add_service_listing",
    "add_pet_owner_profile",
    "manage_community_group",
}

TOOL_DEFS = [
    {
        "name": "search_services",
        "description": "Find service providers by category and optional suburb.",
        "args": {"category": "dog_walking|grooming", "suburb": "optional string", "limit": 3},
    },
    {
        "name": "search_groups",
        "description": "Find community groups by optional suburb.",
        "args": {"suburb": "optional string", "limit": 3},
    },
    {
        "name": "draft_lost_found",
        "description": "Create a lost/found draft from user message.",
        "args": {"suburb": "optional string"},
    },
    {
        "name": "add_service_listing",
        "description": "Create or continue provider service listing from user supplied details.",
        "args": {
            "service_name": "optional string",
            "category": "optional dog_walking|grooming",
            "suburb": "optional string",
            "description": "optional string",
            "price_from": "optional integer",
            "contact_name": "optional string",
        },
    },
    {
        "name": "add_pet_owner_profile",
        "description": "Update pet owner profile memory from the latest user message.",
        "args": {
            "pet_name": "optional string",
            "pet_type": "optional dog|cat|unknown",
            "breed": "optional string",
            "age_years": "optional number",
            "weight_kg": "optional number",
            "suburb": "optional string",
        },
    },
    {
        "name": "create_user_group",
        "description": "Create a pet-owner community group in a suburb.",
        "args": {"name": "group name", "suburb": "suburb", "user_id": "creator user id"},
    },
    {
        "name": "add_group_member",
        "description": "Add another pet owner to your own community group.",
        "args": {"group_name": "group name", "member_user_id": "member id", "requester_user_id": "owner user id"},
    },
]

PROVIDER_FIELDS = [
    "service_name",
    "category",
    "suburb",
    "description",
    "price_from",
    "contact_name",
]


@dataclass
class ProviderOnboardingState:
    active: bool = False
    collected: Dict[str, Any] = field(default_factory=dict)
    awaiting_field: Optional[str] = None


@dataclass
class SessionMemory:
    history: List[Dict[str, str]] = field(default_factory=list)
    profile_memory: Dict[str, Any] = field(default_factory=dict)
    field_locks: Dict[str, bool] = field(default_factory=dict)
    profile_accepted: bool = False
    provider: ProviderOnboardingState = field(default_factory=ProviderOnboardingState)


PROFILE_KEYS = ["pet_name", "pet_type", "breed", "age_years", "weight_kg", "suburb"]
SKILLS_DIR = Path(__file__).resolve().parents[2] / "skills"
BREED_GUIDES: Dict[str, Dict[str, Any]] = {
    "cavalier king charles spaniel": {
        "aliases": [
            "cavalier king charles spaniel",
            "cavalier king charles",
            "king charles spaniel",
            "king charles cavalier",
            "king charles cavaliers",
            "king charles cavelier",
            "king charles caveliers",
            "cavalier",
            "cavaliers",
            "cavelier",
            "caveliers",
        ],
        "summary": (
            "Cavalier King Charles Spaniels are affectionate, social companion dogs that usually do best close to their people. "
            "They need moderate daily exercise, plus short training and enrichment sessions to stay calm and engaged. "
            "Their silky coat and feathering need routine brushing, with regular ear and eye care. "
            "Common health watch-outs include mitral valve disease, syringomyelia, patellar luxation, and eye conditions."
        ),
    },
    "border collie": {
        "aliases": ["border collie", "border collies"],
        "summary": (
            "Border Collies are highly intelligent, energetic herding dogs. "
            "They usually need 1-2 hours of daily physical exercise plus mental work "
            "(training, scent games, puzzle toys) to stay settled. "
            "They are very trainable and thrive with clear structure, but can become frustrated "
            "if under-stimulated. Their coat needs regular brushing, and common health checks include hips, eyes, and joint care. "
            "They are best for owners who can provide consistent activity and engagement."
        ),
    },
    "golden retriever": {
        "aliases": ["golden retriever", "golden retrievers"],
        "summary": (
            "Golden Retrievers are social, friendly, and generally easy to train. "
            "They need daily exercise, routine grooming (especially coat brushing), and weight management. "
            "They are usually great family dogs when given regular activity and attention."
        ),
    },
    "labrador": {
        "aliases": ["labrador", "labradors", "labrador retriever", "labrador retrievers", "lab", "labs"],
        "summary": (
            "Labradors are active, food-motivated, and people-focused dogs. "
            "They benefit from daily exercise, obedience basics, and portion control to prevent weight gain. "
            "They are typically adaptable and do well with structured routines."
        ),
    },
    "poodle": {
        "aliases": ["poodle", "poodles"],
        "summary": (
            "Poodles are intelligent, trainable, and active. "
            "They need both physical activity and mental stimulation, and their coat requires regular grooming. "
            "They often do best with ongoing training and enrichment."
        ),
    },
    "beagle": {
        "aliases": ["beagle", "beagles"],
        "summary": (
            "Beagles are scent-driven, social hounds with strong curiosity. "
            "They need secure environments, consistent recall training, and daily exercise. "
            "They can be vocal and independent, so routine and patient training help."
        ),
    },
}
DEFAULT_RAG_TRIGGER_TERMS = (
    "allergy",
    "allergic reaction",
    "anaphylaxis",
    "arthritis",
    "asthma",
    "ataxia",
    "autoimmune",
    "bloat",
    "bloated abdomen",
    "blood in stool",
    "blood in urine",
    "bloody diarrhea",
    "bloody diarrhoea",
    "bronchitis",
    "cancer",
    "cardiomyopathy",
    "cataract",
    "chronic kidney disease",
    "ckd",
    "collapse",
    "congestive heart failure",
    "constipation",
    "coughing",
    "cushing",
    "cushings",
    "dehydration",
    "dental disease",
    "dermatitis",
    "diabetes",
    "diabetic ketoacidosis",
    "diarrhea",
    "diarrhoea",
    "distemper",
    "ear infection",
    "eating less",
    "endocrine",
    "epilepsy",
    "fever",
    "fip",
    "fiv",
    "fleas allergy dermatitis",
    "fracture",
    "gastroenteritis",
    "giardia",
    "glaucoma",
    "heart disease",
    "heart murmur",
    "heart failure",
    "heartworm",
    "heatstroke",
    "hypoglycemia",
    "hypoglycaemia",
    "ibd",
    "immune mediated",
    "infection",
    "inflamed gums",
    "injury",
    "intestinal blockage",
    "ivdd",
    "jaundice",
    "kennel cough",
    "kidney disease",
    "lameness",
    "leptospirosis",
    "liver disease",
    "loss of appetite",
    "lyme disease",
    "mast cell tumor",
    "mast cell tumour",
    "melena",
    "meningitis",
    "not eating",
    "obstruction",
    "otitis",
    "pain",
    "pancreatitis",
    "parvo",
    "parvovirus",
    "periodontal disease",
    "pneumonia",
    "poison",
    "poisoning",
    "pyometra",
    "rabies",
    "renal failure",
    "respiratory distress",
    "roundworm",
    "sepsis",
    "seizure",
    "seizures",
    "shock",
    "skin infection",
    "stomatitis",
    "straining to urinate",
    "stroke",
    "tapeworm",
    "toxin",
    "toxicity",
    "tracheal collapse",
    "trauma",
    "tumour",
    "urinary blockage",
    "urinary obstruction",
    "uti",
    "vaccine",
    "vaccines",
    "vaccination",
    "vaccinations",
    "booster",
    "boosters",
    "immunization",
    "immunisation",
    "vestibular disease",
    "vomit",
    "vomiting",
    "weakness",
    "worm infestation",
    "tumor",
)


# Fallback manifests used when no SKILL.md files are found on disk.
DEFAULT_SKILL_MANIFESTS = [
    {
        "name": "service-listing-management",
        "description": "Collect and submit provider listing details for groomers and dog walkers.",
        "when_to_use": "User wants to add/list/register their pet service.",
        "tools": ["add_service_listing"],
    },
    {
        "name": "pet-owner-profile",
        "description": "Capture and update pet profile attributes from chat context.",
        "when_to_use": "User shares pet details like name, breed, age, weight, or suburb.",
        "tools": ["add_pet_owner_profile"],
    },
    {
        "name": "services-discovery",
        "description": "Find nearby service providers.",
        "when_to_use": "User asks for walkers or groomers.",
        "tools": ["search_services"],
    },
    {
        "name": "community-and-safety",
        "description": "Draft lost/found posts and suggest community groups.",
        "when_to_use": "User asks about lost/found or community groups.",
        "tools": ["draft_lost_found", "search_groups"],
    },
]


class AIOrchestrator:
    """Conversational orchestrator with memory, provider onboarding and A2UI-style payloads."""

    def __init__(self) -> None:
        self.model = os.getenv("OPENAI_MODEL", "gpt-4.1-mini")
        api_key = self._load_openai_api_key()
        self.client = OpenAI(api_key=api_key) if api_key and OpenAI else None
        self.llm_available = self.client is not None
        if not self.llm_available:
            logger.warning(
                "LLM disabled: set OPENAI_API_KEY (or OPENAI_API_KEY_FILE) and ensure openai package is installed."
            )

        default_db_path = str(Path(__file__).resolve().parents[2] / "data" / "memory.sqlite3")
        self.memory_store = MemoryStore(db_path=os.getenv("MEMORY_DB_PATH", default_db_path))
        self.sessions: Dict[str, SessionMemory] = {}
        self.skill_manifests = self._load_skill_manifests()
        self.rag_retriever = RagRetriever()
        self.rag_trigger_terms = self._load_rag_trigger_terms()
        self.rag_trigger_patterns = self._compile_rag_trigger_patterns(self.rag_trigger_terms)
        self.rag_route_telemetry_enabled = self._read_bool_env("RAG_ROUTE_TELEMETRY_ENABLED", True)

    @staticmethod
    def _normalize_env_value(value: str) -> str:
        normalized = value.strip()
        if len(normalized) >= 2 and normalized[0] == normalized[-1] and normalized[0] in {"'", '"'}:
            normalized = normalized[1:-1].strip()
        return normalized

    def _load_openai_api_key(self) -> str:
        raw_key = os.getenv("OPENAI_API_KEY", "")
        api_key = self._normalize_env_value(raw_key)

        if not api_key:
            key_file = self._normalize_env_value(os.getenv("OPENAI_API_KEY_FILE", ""))
            if key_file:
                try:
                    api_key = self._normalize_env_value(Path(key_file).read_text(encoding="utf-8"))
                except OSError:
                    logger.warning("OPENAI_API_KEY_FILE is set but unreadable.")

        if api_key.lower() in {"replace-with-openai-key", "your-openai-api-key"}:
            return ""
        return api_key

    def handle_message(
        self,
        message: str,
        user_id: str = "guest",
        suburb: Optional[str] = None,
    ) -> ChatResponse:
        user_id = self._safe_text(user_id, default="guest", max_len=128)
        message = self._safe_text(message, default="Hi", max_len=4000)
        session = self._get_session(user_id)
        self._append_turn(session, user_id, "user", message)
        self._update_profile_memory(session, message, suburb)

        safety_response = self._safety_guard(message, session)
        if safety_response:
            self._append_turn(session, user_id, "assistant", safety_response.answer)
            self._persist_session_state(user_id, session)
            return self._attach_history_and_cards(safety_response, session)

        if self._should_start_provider_onboarding(message, session):
            listing_result = self._tool_add_service_listing(
                session=session,
                message=message,
                suburb=suburb,
                user_id=user_id,
                args={},
            )
            if listing_result.get("status") == "created":
                category = listing_result.get("provider", {}).get("category", "dog_walking")
                response = ChatResponse(
                    answer="Your service has been added to the listing and is now visible in Services.",
                    suggested_profile=session.profile_memory,
                    cta_chips=[CtaChip(label="Open Services", action="open_services", payload={"category": category})],
                )
                self._append_turn(session, user_id, "assistant", response.answer)
                self._persist_session_state(user_id, session)
                return self._attach_history_and_cards(response, session)

            session.provider.active = True
            if not session.provider.awaiting_field:
                session.provider.awaiting_field = "service_name"
            response = self._ask_next_provider_question(session)
            self._append_turn(session, user_id, "assistant", response.answer)
            self._persist_session_state(user_id, session)
            return self._attach_history_and_cards(response, session)

        if session.provider.active:
            response = self._handle_provider_onboarding_turn(session, message)
            self._append_turn(session, user_id, "assistant", response.answer)
            self._persist_session_state(user_id, session)
            return self._attach_history_and_cards(response, session)

        plan = self._build_plan(message=message, suburb=suburb, session=session)
        route = self._route_query(message=message, plan=plan)
        tool_results = self._execute_tools(
            tool_calls=plan.get("tools", []),
            message=message,
            suburb=suburb,
            session=session,
            user_id=user_id,
        )
        intent = str(plan.get("intent", "general_pet_question"))
        if route["lane"] == "RAG":
            rag_context = self.rag_retriever.build_context(
                message=message,
                suburb=suburb,
                profile_memory=session.profile_memory,
                intent=intent,
                tool_results=tool_results,
            )
        else:
            rag_context = {
                "intent": intent,
                "query": message.strip(),
                "suburb": suburb,
                "profile_summary": {},
                "documents": [],
            }
        self._emit_route_telemetry(
            user_id=user_id,
            message=message,
            plan=plan,
            route=route,
            rag_context=rag_context,
        )

        answer = self._compose_answer(
            message=message,
            suburb=suburb,
            plan=plan,
            route=route,
            tool_results=tool_results,
            session=session,
            rag_context=rag_context,
        )
        profile = plan.get("suggested_profile")
        if not isinstance(profile, dict):
            profile = self._fallback_profile(message)
        ctas = self._build_ctas(intent=plan.get("intent", "general_pet_question"), tool_results=tool_results)

        response = ChatResponse(
            answer=answer,
            suggested_profile=profile,
            cta_chips=ctas,
        )

        self._append_turn(session, user_id, "assistant", response.answer)
        self._persist_session_state(user_id, session)
        return self._attach_history_and_cards(response, session)

    def _load_rag_trigger_terms(self) -> List[str]:
        raw_terms = os.getenv("RAG_TRIGGER_TERMS", "")
        if not raw_terms.strip():
            parsed = [term.strip().lower() for term in DEFAULT_RAG_TRIGGER_TERMS]
        else:
            parsed = [term.strip().lower() for term in raw_terms.split(",")]
        terms: List[str] = []
        seen: set[str] = set()
        for term in parsed:
            if not term or term in seen:
                continue
            seen.add(term)
            terms.append(term)
        return terms

    @staticmethod
    def _read_bool_env(name: str, default: bool) -> bool:
        raw = os.getenv(name)
        if raw is None:
            return default
        return raw.strip().lower() not in {"0", "false", "no", "off"}

    def _compile_rag_trigger_patterns(self, terms: List[str]) -> List[Pattern[str]]:
        patterns: List[Pattern[str]] = []
        for term in terms:
            escaped = re.escape(term)
            escaped = escaped.replace(r"\ ", r"[\s\-]+")
            pattern = re.compile(rf"(?<![a-z0-9]){escaped}(?![a-z0-9])")
            patterns.append(pattern)
        return patterns

    def _matched_rag_trigger_terms(self, message: str) -> List[str]:
        normalized = message.lower()
        matches: List[str] = []
        for term, pattern in zip(self.rag_trigger_terms, self.rag_trigger_patterns):
            if pattern.search(normalized):
                matches.append(term)
        return matches

    def _should_apply_rag(self, message: str) -> bool:
        return bool(self._matched_rag_trigger_terms(message))

    def _route_query(self, message: str, plan: Dict[str, Any]) -> Dict[str, Any]:
        intent = str(plan.get("intent", "general_pet_question"))
        tools = plan.get("tools", [])
        has_tools = isinstance(tools, list) and len(tools) > 0
        matched_terms = self._matched_rag_trigger_terms(message)
        rag_triggered = bool(matched_terms)

        if intent in APP_ROUTE_INTENTS or has_tools:
            return {"lane": "APP", "reason": "intent_or_tools", "rag_triggered": rag_triggered, "matched_terms": []}
        if rag_triggered:
            return {"lane": "RAG", "reason": "trigger_terms", "rag_triggered": True, "matched_terms": matched_terms}
        return {"lane": "GENERAL", "reason": "default_general_pet", "rag_triggered": False, "matched_terms": []}

    def _emit_route_telemetry(
        self,
        *,
        user_id: str,
        message: str,
        plan: Dict[str, Any],
        route: Dict[str, Any],
        rag_context: Dict[str, Any],
    ) -> None:
        if not self.rag_route_telemetry_enabled:
            return
        rag_docs = rag_context.get("documents", [])
        rag_doc_count = len(rag_docs) if isinstance(rag_docs, list) else 0
        payload = {
            "user_id": user_id,
            "message_length": len(message),
            "intent": str(plan.get("intent", "general_pet_question")),
            "has_tools": bool(plan.get("tools")),
            "route_lane": route.get("lane", "GENERAL"),
            "route_reason": route.get("reason", "unknown"),
            "rag_triggered": bool(route.get("rag_triggered", False)),
            "matched_terms": route.get("matched_terms", []),
            "rag_doc_count": rag_doc_count,
        }
        logger.info("route_telemetry=%s", json.dumps(payload, sort_keys=True))

    def accept_profile(self, user_id: str) -> ChatResponse:
        session = self._get_session(user_id)
        suggestion = self._build_profile_suggestion(session)
        if not suggestion:
            response = ChatResponse(
                answer="I need a bit more information before creating your pet profile card.",
                suggested_profile=session.profile_memory,
                cta_chips=[],
            )
            return self._attach_history_and_cards(response, session)

        session.profile_accepted = True
        response = ChatResponse(
            answer="Profile created. You can edit details later from settings.",
            suggested_profile=session.profile_memory,
            cta_chips=[CtaChip(label="Open Community", action="open_community")],
            profile_suggestion=suggestion,
        )
        self._append_turn(session, user_id, "assistant", response.answer)
        self._persist_session_state(user_id, session)
        return self._attach_history_and_cards(response, session)

    def submit_provider_listing(self, user_id: str) -> ChatResponse:
        session = self._get_session(user_id)
        draft = session.provider.collected
        missing = [field for field in PROVIDER_FIELDS if not draft.get(field)]
        if missing:
            response = ChatResponse(
                answer=f"I still need: {', '.join(missing)}.",
                suggested_profile=session.profile_memory,
                cta_chips=[],
            )
            return self._attach_history_and_cards(response, session)

        category = draft.get("category", "dog_walking")
        if category not in {"dog_walking", "grooming"}:
            category = "dog_walking"

        new_provider = service_store.add_provider(
            owner_user_id=user_id,
            name=str(draft.get("service_name")),
            category=category,
            suburb=str(draft.get("suburb")),
            description=str(draft.get("description")),
            price_from=self._safe_int(draft.get("price_from"), default=30, min_value=1, max_value=5000),
        )

        session.provider = ProviderOnboardingState()
        response = ChatResponse(
            answer="Your service has been added to the listing and is now visible in Services.",
            suggested_profile=session.profile_memory,
            cta_chips=[
                CtaChip(label="Open Services", action="open_services", payload={"category": category}),
            ],
        )
        self._append_turn(session, user_id, "assistant", response.answer)
        self._persist_session_state(user_id, session)
        return self._attach_history_and_cards(response, session)

    def stream_message(
        self,
        message: str,
        user_id: str = "guest",
        suburb: Optional[str] = None,
    ) -> Generator[Dict[str, Any], None, None]:
        """Server-side stream: emits answer deltas then final structured response."""
        response = self.handle_message(message=message, user_id=user_id, suburb=suburb)
        answer = response.answer or ""
        if not answer:
            yield {"type": "delta", "delta": ""}
            yield {"type": "final", "response": response.model_dump()}
            return

        chunk_size = 20
        for index in range(0, len(answer), chunk_size):
            yield {"type": "delta", "delta": answer[index : index + chunk_size]}
            time.sleep(0.03)

        yield {"type": "final", "response": response.model_dump()}

    def _get_session(self, user_id: str) -> SessionMemory:
        cached = self.sessions.get(user_id)
        if cached:
            return cached

        state = self.memory_store.load_user_state(user_id)
        profile_memory = state.get("profile_memory", {}) or {}
        if not isinstance(profile_memory, dict):
            profile_memory = {}

        field_locks = state.get("field_locks", {}) or {}
        if not isinstance(field_locks, dict):
            field_locks = {}

        provider_state = state.get("provider_state", {}) or {}
        if not isinstance(provider_state, dict):
            provider_state = {}

        provider_collected = provider_state.get("collected", {}) or {}
        if not isinstance(provider_collected, dict):
            provider_collected = {}

        session = SessionMemory(
            history=self.memory_store.load_recent_turns(user_id, limit=20),
            profile_memory=profile_memory,
            field_locks={k: bool(v) for k, v in field_locks.items()},
            profile_accepted=bool(state.get("profile_accepted", False)),
            provider=ProviderOnboardingState(
                active=bool(provider_state.get("active", False)),
                collected=provider_collected,
                awaiting_field=provider_state.get("awaiting_field")
                if isinstance(provider_state.get("awaiting_field"), str)
                else None,
            ),
        )
        self.sessions[user_id] = session
        return session

    def _append_turn(self, session: SessionMemory, user_id: str, role: str, content: str) -> None:
        safe_role = role if role in {"user", "assistant"} else "assistant"
        safe_content = self._safe_text(content, default="", max_len=4000)
        session.history.append({"role": safe_role, "content": safe_content})
        self.memory_store.append_turn(user_id=user_id, role=safe_role, content=safe_content)

    def _persist_session_state(self, user_id: str, session: SessionMemory) -> None:
        provider_state = {
            "active": session.provider.active,
            "collected": session.provider.collected,
            "awaiting_field": session.provider.awaiting_field,
        }
        self.memory_store.save_user_state(
            user_id=user_id,
            profile_memory=session.profile_memory,
            profile_accepted=session.profile_accepted,
            field_locks=session.field_locks,
            provider_state=provider_state,
        )

    def _attach_history_and_cards(self, response: ChatResponse, session: SessionMemory) -> ChatResponse:
        history = session.history[-20:]
        response.conversation = [
            ChatTurn(role=turn["role"], content=turn["content"]) for turn in history
        ]

        suggestion = self._build_profile_suggestion(session)
        latest_user_message = next(
            (turn["content"] for turn in reversed(history) if turn.get("role") == "user"),
            "",
        )
        show_profile_card = (
            suggestion
            and not session.profile_accepted
            and self._is_profile_capture_request(str(latest_user_message).lower())
        )
        if show_profile_card:
            response.profile_suggestion = suggestion
            response.a2ui_messages.extend(self._a2ui_profile_messages(suggestion))

        if session.provider.active:
            response.a2ui_messages.extend(self._a2ui_provider_messages(session.provider))

        response.cta_chips = self._dedupe_ctas(response.cta_chips)
        return response

    def _safety_guard(self, message: str, session: SessionMemory) -> Optional[ChatResponse]:
        text = message.lower()
        emergency_patterns = [
            r"can'?t\s+breathe",
            r"seizure",
            r"collapsed|collapse",
            r"unconscious",
            r"poison(ed|ing)?",
            r"bloody\s+vomit|vomiting\s+blood",
            r"not\s+breathing",
        ]
        if any(re.search(pattern, text) for pattern in emergency_patterns):
            return ChatResponse(
                answer=(
                    "This may be an emergency. I cannot diagnose critical conditions in chat. "
                    "Please contact your nearest emergency vet now."
                ),
                suggested_profile=session.profile_memory,
                cta_chips=[
                    CtaChip(label="Open Community", action="open_community"),
                    CtaChip(label="Open Services", action="open_services"),
                ],
            )
        return None

    def _should_start_provider_onboarding(self, message: str, session: SessionMemory) -> bool:
        if session.provider.active:
            return False
        return self._is_service_listing_request(message.lower())

    def _handle_provider_onboarding_turn(self, session: SessionMemory, message: str) -> ChatResponse:
        if re.search(r"\b(cancel|stop|exit)\b", message.lower()):
            session.provider = ProviderOnboardingState()
            return ChatResponse(
                answer="Provider onboarding canceled. You can restart any time by saying 'add my service'.",
                suggested_profile=session.profile_memory,
                cta_chips=[],
            )

        field = session.provider.awaiting_field
        if field:
            extracted = self._extract_provider_field(field, message)
            session.provider.collected[field] = extracted

        missing = [f for f in PROVIDER_FIELDS if not session.provider.collected.get(f)]
        if not missing:
            session.provider.awaiting_field = None
            summary = ", ".join(f"{k}: {v}" for k, v in session.provider.collected.items())
            return ChatResponse(
                answer=(
                    f"Great, I captured your details ({summary}). "
                    "Tap 'Submit Provider Listing' to publish, or tell me what to edit."
                ),
                suggested_profile=session.profile_memory,
                cta_chips=[CtaChip(label="Submit Provider Listing", action="submit_provider_listing")],
            )

        session.provider.awaiting_field = missing[0]
        return self._ask_next_provider_question(session)

    def _ask_next_provider_question(self, session: SessionMemory) -> ChatResponse:
        field = session.provider.awaiting_field
        prompts = {
            "service_name": "What is your service business name?",
            "category": "What service category do you provide: dog_walking or grooming?",
            "suburb": "Which suburb do you primarily serve?",
            "description": "Please provide a short service description.",
            "price_from": "What is your starting price in whole dollars (e.g., 25)?",
            "contact_name": "What name should customers contact?",
        }
        question = prompts.get(field or "", "Tell me more about your service.")
        return ChatResponse(
            answer=question,
            suggested_profile=session.profile_memory,
            cta_chips=[],
        )

    def _extract_provider_field(self, field: str, message: str) -> Any:
        text = message.strip()
        if field == "category":
            lower = text.lower()
            if "groom" in lower:
                return "grooming"
            return "dog_walking"
        if field == "price_from":
            match = re.search(r"(\d+)", text)
            return self._safe_int(match.group(1) if match else None, default=30, min_value=1, max_value=5000)
        return text

    def _build_plan(self, message: str, suburb: Optional[str], session: SessionMemory) -> Dict[str, Any]:
        active_skills = self._select_active_skills(message=message, session=session)
        if self._is_general_assistant_query(message.lower()):
            return {
                "intent": "general_assistant_query",
                "tools": [],
                "suggested_profile": session.profile_memory or self._fallback_profile(message),
            }
        if not self.client:
            return self._heuristic_plan(message, suburb)

        system_prompt = (
            "You are the planner for a pet app assistant. "
            "Return strict JSON only with fields: intent, tools, suggested_profile. "
            "Allowed intents: find_dog_walker, find_groomer, lost_found, community_discovery, "
            "weight_concern, provider_onboarding, add_service_listing, add_pet_owner_profile, "
            "manage_community_group, general_pet_question, general_assistant_query, out_of_scope_non_pet. "
            "Allowed tools: search_services, search_groups, draft_lost_found, add_service_listing, "
            "add_pet_owner_profile, create_user_group, add_group_member. "
            "If the user query is clearly unrelated to pets, pet services, pet health/safety, bookings, "
            "or local pet community, set intent=out_of_scope_non_pet and tools=[]. "
            "Treat active_skills as SKILL.md-style capabilities and prefer tool plans compatible with those skills."
        )
        user_payload = {
            "message": message,
            "suburb": suburb,
            "available_tools": TOOL_DEFS,
            "active_skills": active_skills,
            "profile_memory": session.profile_memory,
            "provider_state": {
                "active": session.provider.active,
                "awaiting_field": session.provider.awaiting_field,
                "collected": session.provider.collected,
            },
        }
        try:
            response = self.client.responses.create(
                model=self.model,
                input=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": json.dumps(user_payload)},
                ],
                temperature=0.1,
            )
            content = getattr(response, "output_text", "") or ""
            data = json.loads(content)
            intent = data.get("intent", "general_pet_question")
            if intent not in ALLOWED_INTENTS:
                return self._heuristic_plan(message, suburb)
            tools = data.get("tools", [])
            if not isinstance(tools, list):
                data["tools"] = []
            profile = data.get("suggested_profile")
            if not isinstance(profile, dict):
                data["suggested_profile"] = self._fallback_profile(message)
            if (
                data.get("intent") == "add_pet_owner_profile"
                and not self._is_profile_capture_request(message.lower())
                and self._is_pet_health_question(message.lower())
            ):
                data["intent"] = "general_pet_question"
                data["tools"] = []
            if data.get("intent") == "general_pet_question":
                data["tools"] = []
            return data
        except Exception:
            return self._heuristic_plan(message, suburb)

    def _heuristic_plan(self, message: str, suburb: Optional[str]) -> Dict[str, Any]:
        text = message.lower()
        if self._is_general_assistant_query(text):
            return {
                "intent": "general_assistant_query",
                "tools": [],
                "suggested_profile": self._fallback_profile(message),
            }
        if "create group" in text or "new group" in text or "start group" in text:
            return {
                "intent": "manage_community_group",
                "tools": [{"name": "create_user_group", "args": {"suburb": suburb}}],
                "suggested_profile": self._fallback_profile(message),
            }
        if "add member" in text or "invite" in text:
            return {
                "intent": "manage_community_group",
                "tools": [{"name": "add_group_member", "args": {"requester_user_id": "from_context"}}],
                "suggested_profile": self._fallback_profile(message),
            }
        if self._is_service_listing_request(text):
            return {
                "intent": "add_service_listing",
                "tools": [{"name": "add_service_listing", "args": {"suburb": suburb}}],
                "suggested_profile": self._fallback_profile(message),
            }
        if self._is_profile_capture_request(text):
            return {
                "intent": "add_pet_owner_profile",
                "tools": [{"name": "add_pet_owner_profile", "args": {"suburb": suburb}}],
                "suggested_profile": self._fallback_profile(message),
            }
        if self._is_pet_health_question(text):
            return {
                "intent": "general_pet_question",
                "tools": [],
                "suggested_profile": self._fallback_profile(message),
            }
        if "lost" in text or "found" in text:
            return {
                "intent": "lost_found",
                "tools": [{"name": "draft_lost_found", "args": {"suburb": suburb}}],
                "suggested_profile": {"pet_type": "unknown", "concerns": ["lost_found"]},
            }
        if "walker" in text or "walk" in text:
            return {
                "intent": "find_dog_walker",
                "tools": [{"name": "search_services", "args": {"category": "dog_walking", "suburb": suburb, "limit": 3}}],
                "suggested_profile": {"pet_type": "dog", "concerns": []},
            }
        if "groom" in text:
            return {
                "intent": "find_groomer",
                "tools": [{"name": "search_services", "args": {"category": "grooming", "suburb": suburb, "limit": 3}}],
                "suggested_profile": {"pet_type": "dog", "concerns": []},
            }
        if "group" in text or "community" in text:
            return {
                "intent": "community_discovery",
                "tools": [{"name": "search_groups", "args": {"suburb": suburb, "limit": 3}}],
                "suggested_profile": {"pet_type": "unknown", "concerns": []},
            }
        if "fat" in text or "weight" in text:
            return {
                "intent": "weight_concern",
                "tools": [{"name": "search_services", "args": {"category": "dog_walking", "suburb": suburb, "limit": 3}}],
                "suggested_profile": {"pet_type": "dog", "concerns": ["weight"]},
            }
        return {
            "intent": "general_pet_question",
            "tools": [],
            "suggested_profile": self._fallback_profile(message),
        }

    def _select_active_skills(self, message: str, session: SessionMemory) -> List[Dict[str, Any]]:
        text = message.lower()
        if self._is_general_assistant_query(text):
            return []
        selected: List[Dict[str, Any]] = []

        if session.provider.active or self._is_service_listing_request(text):
            selected.append(self._skill_by_name("service-listing-management"))
        if self._is_profile_capture_request(text):
            selected.append(self._skill_by_name("pet-owner-profile"))
        if any(token in text for token in ["walker", "walk", "groom"]):
            selected.append(self._skill_by_name("services-discovery"))
        if any(token in text for token in ["lost", "found", "group", "community"]):
            selected.append(self._skill_by_name("community-and-safety"))

        compact = [skill for skill in selected if skill]
        if compact:
            return compact
        return []

    def _skill_by_name(self, name: str) -> Optional[Dict[str, Any]]:
        for skill in self.skill_manifests:
            if skill.get("name") == name:
                return skill
        return None

    def _load_skill_manifests(self) -> List[Dict[str, Any]]:
        manifests: List[Dict[str, Any]] = []
        if SKILLS_DIR.exists():
            for skill_file in sorted(SKILLS_DIR.glob("*/SKILL.md")):
                parsed = self._parse_skill_markdown(skill_file)
                if parsed:
                    manifests.append(parsed)
        return manifests or DEFAULT_SKILL_MANIFESTS

    def _parse_skill_markdown(self, path: Path) -> Optional[Dict[str, Any]]:
        try:
            raw = path.read_text(encoding="utf-8")
        except Exception:
            return None

        frontmatter, body = self._split_frontmatter(raw)
        name = frontmatter.get("name")
        description = frontmatter.get("description", "")
        if not name or not description:
            return None

        tools = self._extract_skill_tools(body)
        when_to_use = self._extract_skill_when_to_use(body)
        return {
            "name": name,
            "description": description,
            "when_to_use": when_to_use,
            "tools": tools,
        }

    def _split_frontmatter(self, text: str) -> tuple[Dict[str, str], str]:
        stripped = text.lstrip()
        if not stripped.startswith("---"):
            return {}, text

        lines = stripped.splitlines()
        if not lines or lines[0].strip() != "---":
            return {}, text

        end_index = None
        for idx in range(1, len(lines)):
            if lines[idx].strip() == "---":
                end_index = idx
                break
        if end_index is None:
            return {}, text

        front_lines = lines[1:end_index]
        body = "\n".join(lines[end_index + 1 :])
        frontmatter: Dict[str, str] = {}
        for line in front_lines:
            if ":" not in line:
                continue
            key, value = line.split(":", 1)
            frontmatter[key.strip()] = value.strip().strip("\"'")
        return frontmatter, body

    def _extract_skill_tools(self, body: str) -> List[str]:
        tools: List[str] = []
        in_tools = False
        valid_tool_names = {tool["name"] for tool in TOOL_DEFS}
        for raw_line in body.splitlines():
            line = raw_line.strip()
            lower = line.lower()
            if lower.startswith("## tools"):
                in_tools = True
                continue
            if in_tools and line.startswith("## "):
                break
            if not in_tools:
                continue
            match = re.match(r"-\s*`?([a-z0-9_]+)`?\s*$", line)
            if match:
                name = match.group(1)
                if name in valid_tool_names:
                    tools.append(name)
        deduped = list(dict.fromkeys(tools))
        return deduped

    def _extract_skill_when_to_use(self, body: str) -> str:
        lines = body.splitlines()
        collecting = False
        parts: List[str] = []
        for raw_line in lines:
            line = raw_line.strip()
            lower = line.lower()
            if lower.startswith("## when to use"):
                collecting = True
                continue
            if collecting and line.startswith("## "):
                break
            if collecting and line:
                parts.append(line.lstrip("- ").strip())
        return " ".join(parts).strip()

    def _is_service_listing_request(self, text: str) -> bool:
        triggers = [
            "add my service",
            "list my service",
            "list my grooming service",
            "list my walking service",
            "i am a dog walker",
            "i am a groomer",
            "become provider",
            "register service",
            "service listing",
        ]
        return any(trigger in text for trigger in triggers)

    def _is_profile_capture_request(self, text: str) -> bool:
        explicit_profile_phrases = [
            "pet profile",
            "update my profile",
            "update pet profile",
            "pet name is",
            "my dog's name is",
            "my cats name is",
            "my cat's name is",
            "breed is",
            "age is",
            "years old",
            "weight is",
            "weighs",
            "weight kg",
        ]
        if any(phrase in text for phrase in explicit_profile_phrases):
            return True

        # Only treat "my dog/my cat" as profile capture when structured profile fields are included.
        has_pet_subject = "my dog" in text or "my cat" in text or "my pet" in text
        has_profile_field = any(
            token in text for token in ["name", "breed", "age", "year old", "years old", "weight", "kg", "suburb"]
        )
        return has_pet_subject and has_profile_field

    def _is_pet_health_question(self, text: str) -> bool:
        if not any(token in text for token in ["my dog", "my cat", "my pet", "dog", "cat", "pet"]):
            return False
        health_tokens = [
            "limp",
            "limping",
            "sick",
            "vomit",
            "vomiting",
            "diarrhea",
            "diarrhoea",
            "itch",
            "itchy",
            "scratch",
            "scratching",
            "pain",
            "injur",
            "bleed",
            "cough",
            "letharg",
            "not eating",
            "won't eat",
            "wont eat",
        ]
        return any(token in text for token in health_tokens)

    def _is_general_assistant_query(self, text: str) -> bool:
        normalized = re.sub(r"\s+", " ", text.strip().lower())
        if not normalized:
            return False

        greeting_tokens = {"hi", "hello", "hey", "yo", "help", "help me"}
        if normalized in greeting_tokens:
            return False
        if normalized in {"thanks", "thank you", "ok", "okay", "cool"}:
            return False

        if self._match_known_breed(normalized):
            return False

        pet_keywords = [
            "pet",
            "dog",
            "cat",
            "puppy",
            "kitten",
            "groom",
            "groomer",
            "walker",
            "walk",
            "leash",
            "vet",
            "fur",
            "coat",
            "matted",
            "itch",
            "scratch",
            "vomit",
            "diarrhea",
            "diarrhoea",
            "litter",
            "flea",
            "tick",
            "collar",
            "training",
            "treat",
            "bark",
            "anxious",
            "scared",
            "reactive",
        ]
        if any(keyword in normalized for keyword in pet_keywords):
            return False

        # App-specific triggers: requests that should stay inside BarkWise workflows.
        app_intent_keywords = [
            "book",
            "booking",
            "appointment",
            "service",
            "provider",
            "groom",
            "walker",
            "walk",
            "community",
            "group",
            "event",
            "lost",
            "found",
            "join",
            "rsvp",
            "listing",
            "barkwise",
        ]
        if any(keyword in normalized for keyword in app_intent_keywords):
            return False

        # Personal pet-context statements should stay app-specific.
        personal_pet_patterns = [
            r"\bmy\s+(dog|cat|pet|puppy|kitten|border\s+collie|collie|labrador|poodle|beagle|bulldog|corgi)\b",
            r"\b(has|is)\s+(matted\s+fur|vomiting|diarrhea|diarrhoea|itchy|limping|anxious|aggressive)\b",
        ]
        if any(re.search(pattern, normalized) for pattern in personal_pet_patterns):
            return False

        # Non-pet topics should be politely declined in BarkAI.
        return True

    def _execute_tools(
        self,
        tool_calls: List[Any],
        message: str,
        suburb: Optional[str],
        session: SessionMemory,
        user_id: str,
    ) -> Dict[str, Any]:
        results: Dict[str, Any] = {}
        for call in tool_calls:
            if isinstance(call, str):
                name = call
                args: Dict[str, Any] = {}
            elif isinstance(call, dict):
                name = call.get("name")
                args = call.get("args", {})
            else:
                continue

            if not isinstance(args, dict):
                args = {}

            try:
                if name == "search_services":
                    category = self._safe_text(args.get("category"), default="dog_walking", max_len=32)
                    if category not in {"dog_walking", "grooming"}:
                        category = "dog_walking"
                    limit = self._safe_int(args.get("limit"), default=3, min_value=1, max_value=10)
                    results[name] = self._tool_search_services(
                        category=category,
                        suburb=args.get("suburb") or suburb,
                        limit=limit,
                    )
                elif name == "search_groups":
                    limit = self._safe_int(args.get("limit"), default=3, min_value=1, max_value=10)
                    results[name] = self._tool_search_groups(
                        suburb=args.get("suburb") or suburb,
                        limit=limit,
                    )
                elif name == "draft_lost_found":
                    results[name] = self._tool_draft_lost_found(message, suburb=args.get("suburb") or suburb)
                elif name == "add_service_listing":
                    results[name] = self._tool_add_service_listing(
                        session=session,
                        message=message,
                        suburb=args.get("suburb") or suburb,
                        user_id=user_id,
                        args=args,
                    )
                elif name == "add_pet_owner_profile":
                    results[name] = self._tool_add_pet_owner_profile(
                        session=session,
                        message=message,
                        suburb=args.get("suburb") or suburb,
                        args=args,
                    )
                    self._persist_session_state(user_id, session)
                elif name == "create_user_group":
                    results[name] = self._tool_create_user_group(
                        message=message,
                        suburb=args.get("suburb") or suburb,
                        user_id=user_id,
                        args=args,
                    )
                elif name == "add_group_member":
                    results[name] = self._tool_add_group_member(
                        message=message,
                        user_id=user_id,
                        args=args,
                    )
            except Exception:
                logger.exception("Tool execution failed: %s", name)
                if isinstance(name, str) and name:
                    results[name] = {"status": "error", "message": "tool execution failed"}
        return results

    def _tool_search_services(self, category: str, suburb: Optional[str], limit: int) -> List[Dict[str, Any]]:
        matched = service_store.list_providers(category=category, suburb=suburb, limit=limit)
        return [p.model_dump() for p in matched[:limit]]

    def _tool_search_groups(self, suburb: Optional[str], limit: int) -> List[Dict[str, Any]]:
        matched = groups
        if suburb:
            matched = [g for g in matched if g.suburb.lower() == suburb.lower()]
        return [g.model_dump() for g in matched[:limit]]

    def _tool_draft_lost_found(self, message: str, suburb: Optional[str]) -> Dict[str, str]:
        title = "Found pet alert" if "found" in message.lower() else "Lost pet alert"
        body = message.strip()
        if len(body) > 180:
            body = body[:177] + "..."
        return {
            "title": title,
            "body": body,
            "suburb": suburb or "Unknown",
            "post_type": "lost_found",
        }

    def _tool_add_service_listing(
        self,
        session: SessionMemory,
        message: str,
        suburb: Optional[str],
        user_id: str,
        args: Dict[str, Any],
    ) -> Dict[str, Any]:
        extracted = self._extract_provider_fields_from_text(message=message, suburb=suburb)
        for key, value in args.items():
            if key in PROVIDER_FIELDS and value not in (None, ""):
                extracted[key] = value

        session.provider.active = True
        session.provider.collected.update(extracted)
        missing = [field for field in PROVIDER_FIELDS if not session.provider.collected.get(field)]
        if missing:
            session.provider.awaiting_field = missing[0]
            return {
                "status": "requires_more_info",
                "missing_fields": missing,
                "awaiting_field": session.provider.awaiting_field,
                "collected": dict(session.provider.collected),
            }

        category = str(session.provider.collected.get("category", "dog_walking"))
        if category not in {"dog_walking", "grooming"}:
            category = "dog_walking"

        new_provider = service_store.add_provider(
            owner_user_id=user_id,
            name=str(session.provider.collected.get("service_name")),
            category=category,
            suburb=str(session.provider.collected.get("suburb")),
            description=str(session.provider.collected.get("description")),
            price_from=self._safe_int(session.provider.collected.get("price_from"), default=30, min_value=1, max_value=5000),
        )
        contact_name = str(session.provider.collected.get("contact_name", ""))
        session.provider = ProviderOnboardingState()
        return {
            "status": "created",
            "provider": new_provider.model_dump(),
            "contact_name": contact_name,
        }

    def _tool_add_pet_owner_profile(
        self,
        session: SessionMemory,
        message: str,
        suburb: Optional[str],
        args: Dict[str, Any],
    ) -> Dict[str, Any]:
        self._update_profile_memory(session, message, suburb)
        for key in PROFILE_KEYS:
            value = args.get(key)
            if value in (None, ""):
                continue
            session.profile_memory[key] = value
            session.field_locks[key] = True
        # Auto-accept profile updates to avoid intrusive confirmation steps in normal chat.
        session.profile_accepted = True

        suggestion = self._build_profile_suggestion(session)
        return {
            "status": "updated",
            "profile": dict(session.profile_memory),
            "profile_suggestion": suggestion.model_dump() if suggestion else None,
        }

    def _tool_create_user_group(
        self,
        message: str,
        suburb: Optional[str],
        user_id: str,
        args: Dict[str, Any],
    ) -> Dict[str, Any]:
        name = args.get("name") or self._extract_group_name(message) or "My Pet Community"
        final_suburb = (args.get("suburb") or suburb or self._extract_suburb_from_text(message) or "Surry Hills").strip().title()

        existing = next(
            (
                group
                for group in groups
                if group.suburb.lower() == final_suburb.lower() and group.name.lower() == name.lower()
            ),
            None,
        )
        if existing:
            return {"status": "exists", "group": existing.model_dump()}

        group = Group(
            id=f"g_user_{uuid4().hex[:8]}",
            name=name,
            suburb=final_suburb,
            member_count=1,
            official=False,
            owner_user_id=user_id,
        )
        groups.append(group)
        group_memberships.append(GroupJoinRecord(group_id=group.id, user_id=user_id, status="member"))
        return {"status": "created", "group": group.model_dump()}

    def _tool_add_group_member(
        self,
        message: str,
        user_id: str,
        args: Dict[str, Any],
    ) -> Dict[str, Any]:
        group_name = (args.get("group_name") or self._extract_group_name(message) or self._extract_group_name_from_add(message) or "").strip()
        member_user_id = (args.get("member_user_id") or self._extract_member_user_id(message) or "").strip()

        if not group_name or not member_user_id:
            return {"status": "missing_info", "required": ["group_name", "member_user_id"]}

        group = next(
            (g for g in groups if not g.official and g.owner_user_id == user_id and g.name.lower() == group_name.lower()),
            None,
        )
        if not group:
            return {"status": "group_not_found_or_not_owner", "group_name": group_name}

        existing = next((m for m in group_memberships if m.group_id == group.id and m.user_id == member_user_id), None)
        if existing and existing.status == "member":
            return {"status": "already_member", "group": group.model_dump(), "member_user_id": member_user_id}

        if existing:
            existing.status = "member"
        else:
            group_memberships.append(GroupJoinRecord(group_id=group.id, user_id=member_user_id, status="member"))
            group.member_count += 1

        return {"status": "member_added", "group": group.model_dump(), "member_user_id": member_user_id}

    def _extract_provider_fields_from_text(self, message: str, suburb: Optional[str]) -> Dict[str, Any]:
        extracted: Dict[str, Any] = {}
        text = message.strip()
        lowered = text.lower()

        if "groom" in lowered:
            extracted["category"] = "grooming"
        elif "walk" in lowered:
            extracted["category"] = "dog_walking"

        if suburb:
            extracted["suburb"] = suburb

        service_name_match = re.search(
            r"(?:service(?:\s+name)?\s*(?:is|:)\s*|business\s+name\s*(?:is|:)\s*)([A-Za-z0-9 '&.-]{2,})",
            text,
            re.I,
        )
        if service_name_match:
            extracted["service_name"] = service_name_match.group(1).strip()

        description_match = re.search(r"(?:description\s*(?:is|:)\s*)(.+)", text, re.I)
        if description_match:
            extracted["description"] = description_match.group(1).strip()

        contact_match = re.search(r"(?:contact\s+name\s*(?:is|:)\s*)([A-Za-z .'-]{2,})", text, re.I)
        if contact_match:
            extracted["contact_name"] = contact_match.group(1).strip()

        suburb_match = re.search(r"(?:in|at|suburb\s*(?:is|:)\s*)([A-Za-z ]{2,})", text, re.I)
        if suburb_match and "suburb" not in extracted:
            extracted["suburb"] = suburb_match.group(1).strip()

        price_match = re.search(r"\$?\s*(\d+)\s*(?:\/|per|from)?\s*(?:walk|visit|session|service)?", text, re.I)
        if price_match:
            extracted["price_from"] = int(price_match.group(1))

        if "description" not in extracted and len(text) > 16:
            extracted["description"] = text[:180]

        return extracted

    def _extract_group_name(self, text: str) -> Optional[str]:
        patterns = [
            r"(?:group\s+called|group\s+name\s+is|create\s+group\s+called)\s+['\"]?([A-Za-z0-9 '&.-]{3,}?)['\"]?(?:\s+in\s+[A-Za-z ]+)?$",
            r"(?:create|start)\s+(?:a\s+)?group\s+([A-Za-z0-9 '&.-]{3,}?)(?:\s+in\s+[A-Za-z ]+)?$",
        ]
        for pattern in patterns:
            match = re.search(pattern, text, re.I)
            if match:
                return match.group(1).strip()
        return None

    def _extract_suburb_from_text(self, text: str) -> Optional[str]:
        match = re.search(r"(?:in|at)\s+([A-Za-z ]{3,})", text, re.I)
        if match:
            return match.group(1).strip().title()
        return None

    def _extract_member_user_id(self, text: str) -> Optional[str]:
        match = re.search(r"(?:add(?:\s+member)?|invite)\s+@?([A-Za-z0-9_.-]{2,})", text, re.I)
        if match:
            return match.group(1).strip()
        return None

    def _extract_group_name_from_add(self, text: str) -> Optional[str]:
        match = re.search(r"\bto\s+([A-Za-z0-9 '&.-]{3,})$", text.strip(), re.I)
        if match:
            return match.group(1).strip()
        return None

    def _compose_answer(
        self,
        message: str,
        suburb: Optional[str],
        plan: Dict[str, Any],
        route: Dict[str, Any],
        tool_results: Dict[str, Any],
        session: SessionMemory,
        rag_context: Dict[str, Any],
    ) -> str:
        intent = plan.get("intent", "general_pet_question")
        breed_summary = self._known_breed_summary(message.lower())
        if breed_summary:
            return self._format_answer_paragraphs(breed_summary)
        rag_related = route.get("lane") == "RAG"
        tone_profile = self._tone_profile(message=message, suburb=suburb, intent=intent, rag_related=rag_related)
        known_fields = self._known_profile_fields(session.profile_memory)
        missing_fields = self._missing_profile_fields(session.profile_memory)
        locked_fields = [key for key, locked in session.field_locks.items() if locked]

        if self.client:
            if intent == "out_of_scope_non_pet":
                system_prompt = (
                    "You are BarkAI in a pet app. "
                    "If the query is out-of-scope for pets, respond with a short, kind refusal and redirect to pet help. "
                    "Do not answer non-pet topics directly."
                )
            elif intent == "general_assistant_query":
                system_prompt = (
                    "You are BarkAI. "
                    "Answer broad general queries directly with concise, practical guidance. "
                    "Follow tone_profile: if support_mode is true, use warm, supportive, non-clinical language. "
                    "Use short readable paragraphs and avoid unnecessary jargon."
                )
            elif self._is_pet_health_question(message.lower()):
                system_prompt = (
                    "You are BarkAI, a pet-care assistant. "
                    "Give practical triage-style guidance for symptom questions in concise steps. "
                    "Do not diagnose. Focus on immediate safe actions, red flags, and when to see a vet. "
                    "Do not include unrelated provider/community snippets unless directly relevant to the symptom. "
                    "If tone_profile.rag_support_mode is true, start with one reassuring sentence, then prioritize the most relevant points from rag_context."
                )
            else:
                system_prompt = (
                    "You are a pet assistant in a mobile app. "
                    "Be concise and practical. Do not provide definitive medical diagnosis. "
                    "Use short readable paragraphs. For answers longer than two sentences, split into 2-4 sentence paragraphs with a blank line between them. "
                    "Use conversation memory context. "
                    "Ground factual or local details in rag_context when relevant, and do not invent specific provider/group/post names not present there. "
                    "Follow tone_profile: if support_mode is true, use warm, supportive, non-clinical language, open with empathy, and include local context only when natural. "
                    "If tone_profile.rag_support_mode is true, start with one reassuring sentence, then prioritize the most relevant points from rag_context. "
                    "Never ask again for any profile field already known. "
                    "Only ask for at most one missing profile field if it is strictly needed for the user's current request. "
                    "If profile_accepted is true, do not ask profile collection questions unless the user explicitly asks to edit profile. "
                    "If a field is locked, it is forbidden to ask it again."
                )
            filtered_rag_context = (
                {"summary": rag_context.get("summary", "")}
                if self._is_pet_health_question(message.lower())
                else rag_context
            )
            payload = {
                "message": message,
                "suburb": suburb,
                "intent": plan.get("intent"),
                "tool_results": tool_results,
                "recent_conversation": session.history[-8:],
                "profile_memory": session.profile_memory,
                "profile_accepted": session.profile_accepted,
                "known_profile_fields": known_fields,
                "missing_profile_fields": missing_fields,
                "locked_fields": locked_fields,
                "tone_profile": tone_profile,
                "route_lane": route.get("lane", "GENERAL"),
                "route_reason": route.get("reason", "unknown"),
                "rag_context": filtered_rag_context,
            }
            try:
                response = self.client.responses.create(
                    model=self.model,
                    input=[
                        {"role": "system", "content": system_prompt},
                        {"role": "user", "content": json.dumps(payload)},
                    ],
                    temperature=0.2,
                )
                text = (getattr(response, "output_text", "") or "").strip()
                if text:
                    if not self._is_profile_edit_request(message):
                        text = self._strip_reask_questions(text, session.field_locks)
                    return self._format_answer_paragraphs(text)
            except Exception:
                pass

        intent = plan.get("intent", "general_pet_question")
        if intent == "out_of_scope_non_pet":
            return self._non_pet_scope_message(message)
        if intent == "general_assistant_query":
            text = self._fallback_general_assistant_answer(message)
            if tone_profile.get("support_mode"):
                local_hint = tone_profile.get("local_context_hint", "")
                prefix = "That sounds stressful. "
                if local_hint:
                    prefix = f"{prefix}{local_hint} "
                return self._format_answer_paragraphs(f"{prefix}{text}")
            return self._format_answer_paragraphs(text)
        if intent == "find_dog_walker":
            return "I found nearby dog walkers. Open Services to compare and request a booking."
        if intent == "find_groomer":
            return "I found groomers in your area. You can request a booking from the Services tab."
        if intent == "add_service_listing":
            listing = tool_results.get("add_service_listing", {})
            if isinstance(listing, dict) and listing.get("status") == "created":
                return "Your service listing has been created successfully."
            if isinstance(listing, dict) and listing.get("awaiting_field"):
                field = str(listing.get("awaiting_field"))
                return f"I started your provider listing. Please share your {field} to continue."
            return "I can help you add your service listing. Share your business details to continue."
        if intent == "add_pet_owner_profile":
            return "I updated your pet profile details."
        if intent == "manage_community_group":
            created = tool_results.get("create_user_group", {})
            if isinstance(created, dict) and created.get("status") == "created":
                group_name = created.get("group", {}).get("name", "your group")
                return f"I created {group_name}. Members can now apply to join from Community."
            added = tool_results.get("add_group_member", {})
            if isinstance(added, dict) and added.get("status") == "member_added":
                member = added.get("member_user_id", "member")
                return f"I added {member} to your group."
            if isinstance(added, dict) and added.get("status") == "missing_info":
                return "Tell me your group name and the member username to add."
            return "I can help create a community group or add members for you."
        if intent == "lost_found":
            return "I drafted a lost/found alert. Review details and post it to community."
        if intent == "community_discovery":
            return "I found local groups. Join one to get recommendations and local event updates."
        if intent == "weight_concern":
            return (
                "I cannot diagnose in chat, but I can help with practical next steps: track body condition weekly, "
                "maintain activity, and consult a vet if appetite or behavior changed."
            )
        rag_fallback = self.rag_retriever.fallback_answer(
            rag_context=rag_context,
            support_mode=bool(tone_profile.get("support_mode")),
        )
        if rag_related and rag_fallback:
            return rag_fallback
        if self._is_pet_health_question(message.lower()):
            return (
                "I cannot diagnose in chat, but limping after a walk should be managed carefully. "
                "Limit activity today, check the paw and nails for cuts or debris, and avoid human pain medications. "
                "If limping is severe, there is swelling, or it lasts beyond 24 hours, contact a vet promptly."
            )
        if tone_profile.get("support_mode"):
            local_hint = tone_profile.get("local_context_hint", "")
            suffix = f" {local_hint}" if local_hint else ""
            return (
                "I am sorry this feels stressful. You are not alone, and this is a common pet-care challenge."
                f"{suffix} I can help you with a calm step-by-step plan."
            )
        if rag_fallback:
            return rag_fallback
        return "I can help with pet advice, local services, and community support."

    def _build_ctas(self, intent: str, tool_results: Dict[str, Any]) -> List[CtaChip]:
        ctas: List[CtaChip] = []
        if intent in {"general_assistant_query", "out_of_scope_non_pet"}:
            return ctas
        if intent in {"find_dog_walker", "weight_concern"}:
            ctas.append(CtaChip(label="Find Dog Walkers", action="find_dog_walkers"))
        if intent == "find_groomer":
            ctas.append(CtaChip(label="Find Groomers", action="find_groomers"))
        if intent == "add_service_listing":
            listing = tool_results.get("add_service_listing", {})
            if isinstance(listing, dict) and listing.get("status") == "created":
                category = listing.get("provider", {}).get("category", "dog_walking")
                ctas.append(CtaChip(label="Open Services", action="open_services", payload={"category": category}))
            else:
                ctas.append(CtaChip(label="Submit Provider Listing", action="submit_provider_listing"))
        if intent == "manage_community_group":
            ctas.append(CtaChip(label="Open Community", action="open_community"))
        if intent == "lost_found":
            draft = tool_results.get("draft_lost_found", {})
            ctas.append(CtaChip(label="Create Lost/Found Post", action="create_lost_found", payload=draft))
        if intent == "community_discovery":
            ctas.append(CtaChip(label="Open Community", action="open_community"))
        if not ctas:
            ctas.append(CtaChip(label="Open Services", action="open_services"))
            ctas.append(CtaChip(label="Open Community", action="open_community"))
        return ctas

    def _update_profile_memory(self, session: SessionMemory, message: str, suburb: Optional[str]) -> None:
        profile = session.profile_memory
        text = message.lower()
        if suburb:
            profile["suburb"] = suburb
            session.field_locks["suburb"] = True

        if "dog" in text and not profile.get("pet_type"):
            profile["pet_type"] = "dog"
            session.field_locks["pet_type"] = True
        if "cat" in text and not profile.get("pet_type"):
            profile["pet_type"] = "cat"
            session.field_locks["pet_type"] = True

        name_match = re.search(
            r"(?:my\s+(?:dog|cat|pet)\s+is\s+named\s+|my\s+(?:dog|cat|pet)\s+name\s+is\s+)([A-Za-z]+)",
            message,
            re.I,
        )
        if name_match:
            profile["pet_name"] = name_match.group(1)
            session.field_locks["pet_name"] = True
        else:
            # Support compact patterns like "my dog Milo" or "this is Milo"
            alt_name_match = re.search(r"(?:my\s+(?:dog|cat|pet)\s+|this\s+is\s+)([A-Za-z]{2,})", message, re.I)
            invalid_name_tokens = {"dog", "cat", "pet", "is", "has", "was", "the", "a", "an", "my"}
            if alt_name_match and alt_name_match.group(1).lower() not in invalid_name_tokens:
                profile["pet_name"] = alt_name_match.group(1)
                session.field_locks["pet_name"] = True

        breed_match = re.search(r"\b(golden retriever|corgi|poodle|labrador|bulldog|beagle|persian|ragdoll|siamese)\b", text)
        if breed_match:
            profile["breed"] = breed_match.group(1)
            session.field_locks["breed"] = True

        age_match = re.search(r"(\d+(?:\.\d+)?)\s*(?:years?|yrs?)\s*old", text)
        if age_match:
            profile["age_years"] = float(age_match.group(1))
            session.field_locks["age_years"] = True
        else:
            # Support common shorthand: 4yo, 4 y/o, 4yr
            age_short_match = re.search(r"(\d+(?:\.\d+)?)\s*(?:yo|y/o|yr|yrs)\b", text)
            if age_short_match:
                profile["age_years"] = float(age_short_match.group(1))
                session.field_locks["age_years"] = True

        weight_match = re.search(r"(\d+(?:\.\d+)?)\s*(?:kg|kgs|kilograms?)\b", text)
        if weight_match:
            profile["weight_kg"] = float(weight_match.group(1))
            session.field_locks["weight_kg"] = True

        concerns = set(profile.get("concerns", []))
        if "fat" in text or "weight" in text:
            concerns.add("weight")
        if "itch" in text or "scratch" in text:
            concerns.add("skin")
        if concerns:
            profile["concerns"] = sorted(concerns)

    def _build_profile_suggestion(self, session: SessionMemory) -> Optional[PetProfileSuggestion]:
        profile = session.profile_memory
        score = 0
        for key in ["pet_name", "pet_type", "breed", "age_years", "weight_kg", "suburb"]:
            if profile.get(key) is not None:
                score += 1
        if profile.get("concerns"):
            score += 1

        if score < 3:
            return None

        return PetProfileSuggestion(
            pet_name=profile.get("pet_name"),
            pet_type=profile.get("pet_type"),
            breed=profile.get("breed"),
            age_years=profile.get("age_years"),
            weight_kg=profile.get("weight_kg"),
            suburb=profile.get("suburb"),
            concerns=profile.get("concerns", []),
        )

    def _a2ui_profile_messages(self, suggestion: PetProfileSuggestion) -> List[Dict[str, Any]]:
        return [
            {
                "beginRendering": {
                    "surfaceId": "chat_profile",
                }
            },
            {
                "surfaceUpdate": {
                    "surfaceId": "chat_profile",
                    "components": [
                        {
                            "id": "profile_card",
                            "type": "profile_suggestion_card",
                            "props": {"variant": "pet_owner_profile"},
                        },
                    ],
                }
            },
            {
                "dataModelUpdate": {
                    "surfaceId": "chat_profile",
                    "path": "contents",
                    "contents": {
                        "title": "Suggested Pet Profile",
                        "profile": suggestion.model_dump(),
                        "acceptAction": "accept_profile_card",
                        "schema": "a2ui.profile.v1",
                    },
                }
            },
        ]

    def _a2ui_provider_messages(self, state: ProviderOnboardingState) -> List[Dict[str, Any]]:
        return [
            {
                "beginRendering": {
                    "surfaceId": "provider_onboarding",
                }
            },
            {
                "surfaceUpdate": {
                    "surfaceId": "provider_onboarding",
                    "components": [
                        {
                            "id": "provider_card",
                            "type": "provider_onboarding_card",
                            "props": {"variant": "service_listing"},
                        },
                    ],
                }
            },
            {
                "dataModelUpdate": {
                    "surfaceId": "provider_onboarding",
                    "path": "contents",
                    "contents": {
                        "title": "Provider Onboarding",
                        "awaitingField": state.awaiting_field,
                        "collected": state.collected,
                        "submitAction": "submit_provider_listing",
                        "schema": "a2ui.provider_onboarding.v1",
                    },
                }
            },
        ]

    def _fallback_general_assistant_answer(self, message: str) -> str:
        text = re.sub(r"\s+", " ", message.strip())
        lower = text.lower()

        breed_answer = self._known_breed_summary(lower)
        if breed_answer:
            return breed_answer

        topic_match = re.search(r"(?:tell me about|what is|explain)\s+(.+)", lower)
        if topic_match:
            topic = topic_match.group(1).strip(" .!?")
            return (
                f"Here is a quick overview of {topic}: "
                "it helps to break this into definition, key traits, pros/cons, and practical next steps. "
                "If you want, I can give a deeper version with beginner tips, common mistakes, and a simple action plan."
            )

        if "compare" in lower:
            return (
                "I can compare this clearly. Share the two options and I will break it down by "
                "cost, effort, risk, time, and best-fit scenarios."
            )

        if "how do" in lower or "how to" in lower:
            return (
                "I can help with step-by-step guidance. "
                "Tell me your goal and constraints (time, budget, skill level), and I will produce a practical plan."
            )

        return (
            "I can answer broad questions. "
            "Ask me a topic and I will provide a concise explanation plus practical next steps."
        )

    def _known_breed_summary(self, text: str) -> Optional[str]:
        if any(token in text for token in ["vaccine", "vaccination", "booster", "immunization", "shot", "shots"]):
            return None
        matched_breed = self._match_known_breed(text)
        if not matched_breed:
            return None
        entry = BREED_GUIDES.get(matched_breed, {})
        summary = str(entry.get("summary", "")).strip()
        return summary or None

    def _match_known_breed(self, text: str) -> Optional[str]:
        normalized = re.sub(r"\s+", " ", text.strip().lower())
        for breed, entry in BREED_GUIDES.items():
            aliases = entry.get("aliases", [])
            for alias in aliases:
                escaped = re.escape(str(alias).strip().lower()).replace(r"\ ", r"[\s\-]+")
                pattern = re.compile(rf"(?<![a-z0-9]){escaped}(?![a-z0-9])")
                if pattern.search(normalized):
                    return breed
        return None

    def _non_pet_scope_message(self, message: str) -> str:
        topic = "that topic"
        cleaned = re.sub(r"\s+", " ", message.strip())
        topic_match = re.search(r"(?:about|on|for)\s+(.+)", cleaned, re.I)
        if topic_match:
            candidate = topic_match.group(1).strip(" .!?")
            if candidate:
                topic = candidate[:80]
        return (
            f"BarkAI is not great at helping with {topic}. "
            "I am best with pet care, pet behavior, local walkers or groomers, bookings, and community pet support."
        )

    def _tone_profile(self, message: str, suburb: Optional[str], intent: str, rag_related: bool = False) -> Dict[str, Any]:
        text = message.lower()
        support_markers = [
            "scared",
            "afraid",
            "anxious",
            "worried",
            "stress",
            "overwhelmed",
            "urgent",
            "panic",
            "help",
            "matted",
            "pain",
            "won't",
            "cannot",
            "can't",
        ]
        support_mode = any(marker in text for marker in support_markers) and intent != "out_of_scope_non_pet"

        local_context_hint = ""
        weather_markers = ["rain", "wet", "winter", "cold", "humidity", "humid"]
        if any(marker in text for marker in weather_markers):
            if suburb:
                local_context_hint = (
                    f"In and around {suburb}, wet weather can make tangles and matting get worse quickly."
                )
            else:
                local_context_hint = "Wet weather can make tangles and matting get worse quickly."

        return {
            "support_mode": support_mode,
            "rag_support_mode": support_mode and rag_related,
            "style": "supportive_non_clinical" if support_mode else "direct_practical",
            "local_context_hint": local_context_hint,
        }

    def _format_answer_paragraphs(self, text: str) -> str:
        normalized = text.replace("\r\n", "\n").strip()
        if not normalized:
            return normalized
        if "\n\n" in normalized:
            return normalized
        if re.search(r"^\s*[-*]\s+", normalized, re.M):
            return normalized
        if re.search(r"^\s*\d+\.\s+", normalized, re.M):
            return normalized

        sentences = re.split(r"(?<=[.!?])\s+", normalized)
        cleaned = [sentence.strip() for sentence in sentences if sentence.strip()]
        if len(cleaned) <= 2:
            return normalized

        chunks: List[str] = []
        for index in range(0, len(cleaned), 2):
            chunks.append(" ".join(cleaned[index : index + 2]))
        return "\n\n".join(chunks)

    def _fallback_profile(self, message: str) -> Dict[str, Any]:
        text = message.lower()
        pet_type = "dog" if "dog" in text else "cat" if "cat" in text else "unknown"
        concerns: List[str] = []
        if "weight" in text or "fat" in text:
            concerns.append("weight")
        if "itch" in text or "scratch" in text:
            concerns.append("skin")
        return {
            "pet_type": pet_type,
            "concerns": concerns,
        }

    def _known_profile_fields(self, profile: Dict[str, Any]) -> List[str]:
        return [key for key in PROFILE_KEYS if profile.get(key) is not None]

    def _missing_profile_fields(self, profile: Dict[str, Any]) -> List[str]:
        return [key for key in PROFILE_KEYS if profile.get(key) is None]

    def _is_profile_edit_request(self, message: str) -> bool:
        text = message.lower()
        triggers = [
            "update profile",
            "edit profile",
            "change my",
            "correct my",
            "that is wrong",
            "fix profile",
        ]
        return any(trigger in text for trigger in triggers)

    def _strip_reask_questions(self, answer: str, field_locks: Dict[str, bool]) -> str:
        if "?" not in answer:
            return answer

        locked = {key for key, is_locked in field_locks.items() if is_locked}
        if not locked:
            return answer

        field_keywords = {
            "pet_name": ["name", "pet name"],
            "pet_type": ["dog", "cat", "pet type", "what pet"],
            "breed": ["breed"],
            "age_years": ["age", "years old", "how old"],
            "weight_kg": ["weight", "kg", "kilograms"],
            "suburb": ["suburb", "location", "where are you"],
        }

        sentences = re.split(r"(?<=[.!?])\s+", answer)
        kept: List[str] = []
        for sentence in sentences:
            s_lower = sentence.lower()
            if "?" in sentence:
                should_drop = False
                for field in locked:
                    for keyword in field_keywords.get(field, []):
                        if keyword in s_lower:
                            should_drop = True
                            break
                    if should_drop:
                        break
                if should_drop:
                    continue
            kept.append(sentence)

        sanitized = " ".join(part.strip() for part in kept if part.strip()).strip()
        return sanitized or "Thanks. I already have your profile details and will use them in recommendations."

    def _dedupe_ctas(self, ctas: List[CtaChip]) -> List[CtaChip]:
        unique: List[CtaChip] = []
        seen: set[tuple[str, str]] = set()
        for cta in ctas:
            try:
                payload_key = json.dumps(cta.payload, sort_keys=True)
            except TypeError:
                payload_key = str(cta.payload)
            key = (cta.action, payload_key)
            if key in seen:
                continue
            seen.add(key)
            unique.append(cta)
        return unique

    def _safe_text(self, value: Any, default: str = "", max_len: int = 512) -> str:
        if value is None:
            return default
        text = str(value).strip()
        if not text:
            return default
        if len(text) > max_len:
            return text[:max_len]
        return text

    def _safe_int(
        self,
        value: Any,
        default: int,
        min_value: Optional[int] = None,
        max_value: Optional[int] = None,
    ) -> int:
        try:
            parsed = int(value)
        except (TypeError, ValueError):
            parsed = default
        if min_value is not None and parsed < min_value:
            parsed = min_value
        if max_value is not None and parsed > max_value:
            parsed = max_value
        return parsed
