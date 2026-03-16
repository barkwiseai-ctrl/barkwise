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
from app.models import BookingRequest, ChatCitation, ChatResponse, ChatTurn, CtaChip, Group, GroupJoinRecord, PetProfileSuggestion
from app.services.faq_dog_qa import FaqMatchResult, match_faq_answer
from app.services.memory_store import MemoryStore
from app.services.rag_retriever import RagRetriever
from app.services.service_store import ServiceStoreError, service_store

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
    "manage_booking",
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
    "manage_booking",
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
    {
        "name": "search_availability",
        "description": "Get availability slots for a provider on a date.",
        "args": {"provider_id": "required provider id", "date": "required YYYY-MM-DD"},
    },
    {
        "name": "create_booking_request",
        "description": "Create a booking request after explicit confirmation.",
        "args": {
            "provider_id": "required provider id",
            "date": "required YYYY-MM-DD",
            "time_slot": "required HH:MM",
            "pet_name": "optional pet name",
            "note": "optional note",
            "confirm": "required true to create booking",
        },
    },
    {
        "name": "get_booking_status",
        "description": "Get booking status for a booking id or list latest user bookings.",
        "args": {"booking_id": "optional booking id", "role": "optional all|owner|provider"},
    },
]

TOOL_ARG_ALLOWLIST: Dict[str, set[str]] = {
    "search_services": {"category", "suburb", "limit"},
    "search_groups": {"suburb", "limit"},
    "draft_lost_found": {"suburb"},
    "add_service_listing": {"service_name", "category", "suburb", "description", "price_from", "contact_name"},
    "add_pet_owner_profile": {"pet_name", "pet_type", "breed", "age_years", "weight_kg", "suburb"},
    "create_user_group": {"name", "suburb", "user_id"},
    "add_group_member": {"group_name", "member_user_id", "requester_user_id"},
    "search_availability": {"provider_id", "date"},
    "create_booking_request": {"provider_id", "date", "time_slot", "pet_name", "note", "confirm"},
    "get_booking_status": {"booking_id", "role"},
}
MAX_TOOL_CALLS_PER_TURN = 3
MAX_TOOL_ARG_TEXT_LENGTH = 160
PROMPT_EXFILTRATION_PATTERNS = (
    r"\b(ignore|bypass|override)\b.{0,48}\b(previous|prior|system|developer|safety)\b.{0,24}\b(instruction|prompt|rule)s?\b",
    r"\b(system|developer)\s+prompt\b",
    r"\breveal\b.{0,48}\b(prompt|instruction|api\s*key|token|secret|environment|env(?:ironment)?\s*var)\b",
    r"\b(print|dump|show|list)\b.{0,48}\b(api\s*key|token|secret|environment|env(?:ironment)?\s*var)\b",
    r"\b(exfiltrate|leak)\b.{0,24}\b(prompt|key|token|secret)\b",
)
DEFAULT_ANSWER_INTENT_BUDGETS: Dict[str, int] = {
    "general_pet_question": 80,
    "general_assistant_query": 60,
    "out_of_scope_non_pet": 40,
}

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

DEFAULT_HIGH_RISK_TERMS = (
    "parvo",
    "parvovirus",
    "poison",
    "poisoning",
    "toxin",
    "toxicity",
    "xylitol",
    "chocolate",
    "grapes",
    "raisins",
    "antifreeze",
    "heatstroke",
    "overheat",
    "panting",
    "collapsed",
    "collapse",
    "seizure",
    "seizures",
    "bloat",
    "bloated abdomen",
    "not breathing",
    "can't breathe",
    "cannot breathe",
    "bloody vomit",
    "vomiting blood",
    "bloody diarrhea",
    "bloody diarrhoea",
    "unconscious",
    "snake bite",
    "tick paralysis",
    "rabies",
    "leptospirosis",
)

WELFARE_POLICY_RESOURCE_PATH = Path(__file__).resolve().parents[1] / "resources" / "welfare_policy_countries.json"
DEFAULT_WELFARE_POLICY_CONFIG: Dict[str, Any] = {
    "default_country_code": "AU",
    "country_names": {
        "AU": "Australia",
        "NZ": "New Zealand",
        "US": "United States",
        "GB": "United Kingdom",
        "DEFAULT": "your region",
    },
    "ute_tray_policy_by_country": {
        "AU": "au_working_dog_transition",
        "NZ": "au_working_dog_transition",
        "DEFAULT": "global_strict_transition",
    },
    "suburb_country_hints": {
        "nsw": "AU",
        "new south wales": "AU",
        "vic": "AU",
        "victoria": "AU",
        "qld": "AU",
        "queensland": "AU",
        "wa": "AU",
        "western australia": "AU",
        "sa": "AU",
        "south australia": "AU",
        "tas": "AU",
        "tasmania": "AU",
        "act": "AU",
        "nt": "AU",
        "northern territory": "AU",
        "sydney": "AU",
        "melbourne": "AU",
        "brisbane": "AU",
        "perth": "AU",
        "adelaide": "AU",
        "canberra": "AU",
        "hobart": "AU",
        "darwin": "AU",
        "sunshine west": "AU",
        "surry hills": "AU",
    },
}


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
        self.high_risk_terms = self._load_high_risk_terms()
        self.high_risk_patterns = self._compile_rag_trigger_patterns(self.high_risk_terms)
        self.high_risk_safe_mode_enabled = self._read_bool_env("HIGH_RISK_SAFE_MODE_ENABLED", True)
        self.welfare_country_policy = self._load_welfare_country_policy()
        self.default_country_code = str(self.welfare_country_policy.get("default_country_code", "AU") or "AU").upper()
        self.country_names = {
            str(key).upper(): str(value)
            for key, value in dict(self.welfare_country_policy.get("country_names", {})).items()
            if str(key).strip() and str(value).strip()
        }
        self.ute_tray_policy_by_country = {
            str(key).upper(): str(value).strip().lower()
            for key, value in dict(self.welfare_country_policy.get("ute_tray_policy_by_country", {})).items()
            if str(key).strip() and str(value).strip()
        }
        self.suburb_country_hints = {
            str(key).strip().lower(): str(value).strip().upper()
            for key, value in dict(self.welfare_country_policy.get("suburb_country_hints", {})).items()
            if str(key).strip() and str(value).strip()
        }
        self.rag_route_telemetry_enabled = self._read_bool_env("RAG_ROUTE_TELEMETRY_ENABLED", True)
        self.model_usage_telemetry_enabled = self._read_bool_env("MODEL_USAGE_TELEMETRY_ENABLED", True)
        self.model_budget_window_seconds = self._read_int_env("MODEL_BUDGET_WINDOW_SECONDS", default=3600, min_value=60, max_value=86400)
        self.model_planner_budget_per_window = self._read_int_env(
            "MODEL_PLANNER_BUDGET_PER_WINDOW",
            default=240,
            min_value=0,
            max_value=5000,
        )
        self.model_answer_budget_per_window = self._read_int_env(
            "MODEL_ANSWER_BUDGET_PER_WINDOW",
            default=160,
            min_value=0,
            max_value=5000,
        )
        self.max_planner_output_tokens = self._read_int_env(
            "MODEL_MAX_PLANNER_OUTPUT_TOKENS",
            default=220,
            min_value=32,
            max_value=4000,
        )
        self.max_answer_output_tokens = self._read_int_env(
            "MODEL_MAX_ANSWER_OUTPUT_TOKENS",
            default=420,
            min_value=64,
            max_value=4000,
        )
        self.answer_intent_budgets = self._load_answer_intent_budgets()
        self._model_budget_events: Dict[str, List[float]] = {}
        self.prompt_exfiltration_patterns = [re.compile(pattern, re.I) for pattern in PROMPT_EXFILTRATION_PATTERNS]

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

        crate_policy_response = self._crate_policy_guard(message, session)
        if crate_policy_response:
            self._append_turn(session, user_id, "assistant", crate_policy_response.answer)
            self._persist_session_state(user_id, session)
            return self._attach_history_and_cards(crate_policy_response, session)

        welfare_policy_response = self._welfare_policy_guard(message, session, suburb=suburb)
        if welfare_policy_response:
            self._append_turn(session, user_id, "assistant", welfare_policy_response.answer)
            self._persist_session_state(user_id, session)
            return self._attach_history_and_cards(welfare_policy_response, session)

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

        plan = self._normalize_plan(
            self._build_plan(message=message, suburb=suburb, session=session, user_id=user_id),
            message=message,
            suburb=suburb,
        )
        if self._is_high_risk_query(message):
            plan["intent"] = "general_pet_question"
            plan["tools"] = []
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
                high_risk_mode=bool(route.get("high_risk_mode", False)),
                high_risk_terms=route.get("matched_high_risk_terms", []),
            )
        else:
            rag_context = {
                "intent": intent,
                "query": message.strip(),
                "suburb": suburb,
                "profile_summary": {},
                "documents": [],
                "high_risk_mode": bool(route.get("high_risk_mode", False)),
                "high_risk_terms": route.get("matched_high_risk_terms", []),
                "source_policy": "trusted_knowledge_only_no_reddit"
                if route.get("high_risk_mode", False)
                else "default",
            }
        self._emit_route_telemetry(
            user_id=user_id,
            message=message,
            plan=plan,
            route=route,
            rag_context=rag_context,
        )

        app_workflow_answer = (
            self._compose_app_workflow_answer(intent=intent, tool_results=tool_results)
            if route.get("lane") == "APP"
            else None
        )
        faq_match = None if app_workflow_answer else self._select_faq_match(message=message, route=route, intent=intent)
        if app_workflow_answer:
            answer = app_workflow_answer
            answer_source = "app"
            answer_badges = ["App Workflow"]
            citations = []
        elif faq_match:
            answer = faq_match.answer
            answer_source = "faq"
            answer_badges = self._dedupe_badges(faq_match.badges + ["Barkwise QA"])
            citations = faq_match.citations[:3]
        else:
            answer = self._compose_answer(
                message=message,
                suburb=suburb,
                plan=plan,
                route=route,
                tool_results=tool_results,
                session=session,
                rag_context=rag_context,
                user_id=user_id,
            )
            rag_citations = self._build_rag_citations(rag_context=rag_context, limit=3)
            if route.get("lane") == "RAG" and rag_citations:
                answer_source = "rag"
                answer_badges = ["RAG Grounded", "Barkwise AI"]
                citations = rag_citations
            elif self.client:
                answer_source = "gpt_fallback"
                answer_badges = ["GPT Fallback", "Barkwise AI"]
                citations = []
            else:
                answer_source = "fallback"
                answer_badges = ["Heuristic Fallback"]
                citations = []
        if route.get("high_risk_mode"):
            answer_badges = self._dedupe_badges(answer_badges + ["High Risk Safe Mode", "Trusted Sources Only"])
        profile = plan.get("suggested_profile")
        if not isinstance(profile, dict):
            profile = self._fallback_profile(message)
        ctas = self._build_ctas(intent=plan.get("intent", "general_pet_question"), tool_results=tool_results)

        response = ChatResponse(
            answer=answer,
            suggested_profile=profile,
            cta_chips=ctas,
            answer_source=answer_source,
            answer_badges=answer_badges,
            citations=citations,
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

    def _load_high_risk_terms(self) -> List[str]:
        raw_terms = os.getenv("HIGH_RISK_TRIGGER_TERMS", "")
        if not raw_terms.strip():
            parsed = [term.strip().lower() for term in DEFAULT_HIGH_RISK_TERMS]
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

    def _load_welfare_country_policy(self) -> Dict[str, Any]:
        config_path = self._normalize_env_value(os.getenv("WELFARE_POLICY_COUNTRY_PATH", ""))
        path = Path(config_path) if config_path else WELFARE_POLICY_RESOURCE_PATH
        merged = json.loads(json.dumps(DEFAULT_WELFARE_POLICY_CONFIG))

        if not path.exists():
            logger.info("Welfare country policy config not found at %s; using defaults.", path)
            return merged

        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except Exception as exc:
            logger.warning("Failed to parse welfare country policy config %s: %s", path, exc)
            return merged

        if not isinstance(payload, dict):
            logger.warning("Welfare country policy config must be a JSON object: %s", path)
            return merged

        for key in {"country_names", "ute_tray_policy_by_country", "suburb_country_hints"}:
            value = payload.get(key)
            if isinstance(value, dict):
                merged[key].update(value)

        default_country = payload.get("default_country_code")
        if isinstance(default_country, str) and default_country.strip():
            merged["default_country_code"] = default_country.strip().upper()

        return merged

    @staticmethod
    def _read_bool_env(name: str, default: bool) -> bool:
        raw = os.getenv(name)
        if raw is None:
            return default
        return raw.strip().lower() not in {"0", "false", "no", "off"}

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

    def _load_answer_intent_budgets(self) -> Dict[str, int]:
        budgets = dict(DEFAULT_ANSWER_INTENT_BUDGETS)
        raw = os.getenv("MODEL_ANSWER_INTENT_BUDGETS", "")
        if not raw.strip():
            return budgets
        try:
            payload = json.loads(raw)
        except Exception:
            logger.warning("MODEL_ANSWER_INTENT_BUDGETS is not valid JSON; using defaults.")
            return budgets
        if not isinstance(payload, dict):
            logger.warning("MODEL_ANSWER_INTENT_BUDGETS must be a JSON object; using defaults.")
            return budgets
        for key, value in payload.items():
            intent = self._safe_text(key, default="", max_len=64)
            if intent not in ALLOWED_INTENTS:
                continue
            try:
                parsed = int(value)
            except (TypeError, ValueError):
                continue
            budgets[intent] = max(0, min(parsed, 5000))
        return budgets

    def _answer_budget_for_intent(self, intent: str) -> int:
        return int(self.answer_intent_budgets.get(intent, self.model_answer_budget_per_window))

    def _allow_model_call(self, *, stage: str, user_id: str, intent: str) -> bool:
        budget = self.model_planner_budget_per_window if stage == "planner" else self._answer_budget_for_intent(intent)
        if budget <= 0:
            self._emit_model_usage_telemetry(stage=stage, user_id=user_id, intent=intent, allowed=False, budget=budget)
            return False

        key = f"{stage}:{user_id}:{intent}"
        now = time.monotonic()
        window_start = now - float(self.model_budget_window_seconds)
        prior = self._model_budget_events.get(key, [])
        active = [ts for ts in prior if ts >= window_start]
        if len(active) >= budget:
            self._model_budget_events[key] = active
            self._emit_model_usage_telemetry(stage=stage, user_id=user_id, intent=intent, allowed=False, budget=budget)
            return False

        active.append(now)
        self._model_budget_events[key] = active
        self._emit_model_usage_telemetry(stage=stage, user_id=user_id, intent=intent, allowed=True, budget=budget)
        return True

    def _emit_model_usage_telemetry(self, *, stage: str, user_id: str, intent: str, allowed: bool, budget: int) -> None:
        if not self.model_usage_telemetry_enabled:
            return
        payload = {
            "stage": stage,
            "user_id": user_id,
            "intent": intent,
            "allowed": bool(allowed),
            "budget": int(budget),
            "window_seconds": int(self.model_budget_window_seconds),
            "max_output_tokens": int(self.max_planner_output_tokens if stage == "planner" else self.max_answer_output_tokens),
        }
        logger.info("model_usage_telemetry=%s", json.dumps(payload, sort_keys=True))

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

    def _matched_high_risk_terms(self, message: str) -> List[str]:
        if not self.high_risk_safe_mode_enabled:
            return []
        normalized = message.lower()
        matches: List[str] = []
        for term, pattern in zip(self.high_risk_terms, self.high_risk_patterns):
            if pattern.search(normalized):
                matches.append(term)
        return matches

    def _should_apply_rag(self, message: str) -> bool:
        return bool(self._matched_rag_trigger_terms(message))

    def _is_high_risk_query(self, message: str) -> bool:
        return bool(self._matched_high_risk_terms(message))

    def _route_query(self, message: str, plan: Dict[str, Any]) -> Dict[str, Any]:
        intent = str(plan.get("intent", "general_pet_question"))
        tools = plan.get("tools", [])
        has_tools = isinstance(tools, list) and len(tools) > 0
        matched_terms = self._matched_rag_trigger_terms(message)
        matched_high_risk_terms = self._matched_high_risk_terms(message)
        high_risk_mode = bool(matched_high_risk_terms)
        merged_terms = list(dict.fromkeys([*matched_high_risk_terms, *matched_terms]))
        rag_triggered = bool(matched_terms)
        grounded_pet_knowledge = self._is_groundable_pet_knowledge_query(message, intent)

        if intent in APP_ROUTE_INTENTS or has_tools:
            if high_risk_mode:
                return {
                    "lane": "RAG",
                    "reason": "high_risk_safe_mode",
                    "rag_triggered": True,
                    "matched_terms": merged_terms,
                    "high_risk_mode": True,
                    "matched_high_risk_terms": matched_high_risk_terms,
                }
            return {
                "lane": "APP",
                "reason": "intent_or_tools",
                "rag_triggered": rag_triggered,
                "matched_terms": [],
                "high_risk_mode": False,
                "matched_high_risk_terms": [],
            }
        if rag_triggered or high_risk_mode or grounded_pet_knowledge:
            return {
                "lane": "RAG",
                "reason": "high_risk_safe_mode" if high_risk_mode else ("trigger_terms" if rag_triggered else "grounded_pet_knowledge"),
                "rag_triggered": True,
                "matched_terms": merged_terms if high_risk_mode else (matched_terms if rag_triggered else []),
                "high_risk_mode": high_risk_mode,
                "matched_high_risk_terms": matched_high_risk_terms,
            }
        return {
            "lane": "GENERAL",
            "reason": "default_general_pet",
            "rag_triggered": False,
            "matched_terms": [],
            "high_risk_mode": False,
            "matched_high_risk_terms": [],
        }

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
            "high_risk_mode": bool(route.get("high_risk_mode", False)),
            "matched_high_risk_terms": route.get("matched_high_risk_terms", []),
            "rag_doc_count": rag_doc_count,
        }
        logger.info("route_telemetry=%s", json.dumps(payload, sort_keys=True))

    def _select_faq_match(
        self,
        *,
        message: str,
        route: Dict[str, Any],
        intent: str,
    ) -> Optional[FaqMatchResult]:
        if route.get("lane") == "APP":
            return None
        if intent in {"out_of_scope_non_pet", "general_assistant_query"}:
            return None
        return match_faq_answer(message)

    def _build_rag_citations(self, *, rag_context: Dict[str, Any], limit: int = 3) -> List[ChatCitation]:
        docs = rag_context.get("documents", [])
        if not isinstance(docs, list):
            return []
        source_labels = {
            "knowledge_base": "Knowledge Base",
            "provider": "Provider",
            "group": "Community Group",
            "community_post": "Community Post",
            "community_event": "Community Event",
        }
        citations: List[ChatCitation] = []
        seen: set[str] = set()
        for raw in docs:
            if not isinstance(raw, dict):
                continue
            title = str(raw.get("title", "")).strip()
            if not title:
                continue
            authority = str(raw.get("authority", "")).strip()
            source = authority or source_labels.get(str(raw.get("source", "")).strip(), "Barkwise")
            key = f"{title.lower()}::{source.lower()}"
            if key in seen:
                continue
            seen.add(key)
            citations.append(
                ChatCitation(
                    title=title,
                    source=source,
                    url=str(raw.get("url", "")).strip() or None,
                    snippet=str(raw.get("snippet", "")).strip() or None,
                )
            )
            if len(citations) >= limit:
                break
        return citations

    def _dedupe_badges(self, badges: List[str]) -> List[str]:
        seen: set[str] = set()
        compact: List[str] = []
        for badge in badges:
            normalized = badge.strip()
            if not normalized:
                continue
            key = normalized.lower()
            if key in seen:
                continue
            seen.add(key)
            compact.append(normalized)
        return compact

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
        conversation = [
            ChatTurn(role=turn["role"], content=turn["content"]) for turn in history
        ]
        last_assistant_index = next(
            (index for index in range(len(conversation) - 1, -1, -1) if conversation[index].role == "assistant"),
            None,
        )
        if last_assistant_index is not None:
            conversation[last_assistant_index] = conversation[last_assistant_index].model_copy(
                update={
                    "answer_source": response.answer_source,
                    "answer_badges": response.answer_badges,
                    "citations": response.citations,
                }
            )
        response.conversation = conversation

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
        if self._is_prompt_exfiltration_attempt(message):
            return ChatResponse(
                answer=(
                    "I cannot help with extracting hidden prompts, secrets, or keys. "
                    "I can help with pet-care guidance, services, community, and bookings."
                ),
                suggested_profile=session.profile_memory,
                cta_chips=[
                    CtaChip(label="Open Community", action="open_community"),
                    CtaChip(label="Open Services", action="open_services"),
                ],
                answer_source="security",
                answer_badges=["Security Guardrail"],
            )
        # Keep this guard for immediate life-threatening phrasing only.
        # Broader risk terms are handled by high-risk safe mode with trusted-source guidance.
        emergency_patterns = [
            r"can'?t\s+breathe",
            r"seizure\s+(?:won't\s+stop|wont\s+stop|for\s+\d+|lasting)",
            r"continuous\s+seizure",
            r"collapsed|collapse",
            r"unconscious",
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
                answer_source="safety",
                answer_badges=["Safety Alert"],
            )
        return None

    def _crate_policy_guard(self, message: str, session: SessionMemory) -> Optional[ChatResponse]:
        text = message.lower()
        if not self._is_crate_related_query(text):
            return None

        long_term_markers = [
            "all day",
            "all night",
            "entire day",
            "whole day",
            "while i work",
            "while at work",
            "overnight and day",
            "long term",
            "long-term",
            "every day crate",
        ]
        has_long_term_marker = any(marker in text for marker in long_term_markers)
        duration_match = re.search(r"(\d+)\s*(hours|hrs|hr|h)\b", text)
        if duration_match:
            hours = int(duration_match.group(1))
            if hours >= 4:
                has_long_term_marker = True

        urine_hold_patterns = [
            r"(hold|stop|prevent).{0,24}(pee|urine|toilet|wee)",
            r"(not|no).{0,12}(urinate|pee|wee)",
            r"(train).{0,24}(not).{0,12}(urinate|pee|wee)",
        ]
        has_urine_hold_marker = any(re.search(pattern, text) for pattern in urine_hold_patterns)
        young_puppy_marker = bool(
            re.search(r"\b(8|9|10|11|12)\s*[- ]?\s*week", text)
            or re.search(r"\b(2|3)\s*[- ]?\s*month", text)
            or "young puppy" in text
        )
        high_risk_urination_question = has_urine_hold_marker and (young_puppy_marker or "puppy" in text)

        alternatives_failed_markers = [
            "tried everything",
            "nothing works",
            "nothing else works",
            "safe room failed",
            "gates failed",
            "pen failed",
            "crate is only option",
        ]
        alternatives_failed = any(marker in text for marker in alternatives_failed_markers)
        already_crating_markers = [
            "we crate",
            "i crate",
            "currently crate",
            "already crate",
            "already crating",
            "in the crate now",
        ]
        already_crating = any(marker in text for marker in already_crating_markers)
        short_term_medical_context = bool(
            re.search(r"\b(post[- ]?op|post surgery|after surgery|vet advised|medical recovery|injury recovery)\b", text)
        )

        response_lines: List[str] = [
            "BarkWise uses a least-restrictive, welfare-first policy, so we do not recommend crating as a default solution.",
            "The explicit target is no routine crating if that is safely achievable for your dog and household.",
            "Start with lower-restriction options first: safe room setup, baby gates, structured toilet schedule, enrichment before rest periods, and short supervised separation training.",
        ]

        if high_risk_urination_question:
            response_lines.append(
                "I cannot support plans to make a young puppy hold urine in confinement. That can increase stress and health risk (including urinary issues)."
            )
            response_lines.append(
                "Use frequent toilet breaks, water access, and short predictable rest blocks instead, then review with your vet if accidents persist."
            )
        if has_long_term_marker:
            response_lines.append(
                "I cannot support long-duration or long-term daily crating plans. If confinement is being used this way, move to a transition plan now."
            )

        if already_crating or alternatives_failed or has_long_term_marker:
            response_lines.append("Use this de-escalation ladder with a goal of no crating if possible:")
            response_lines.append("1. Level 3 (temporary only): shortest possible crate blocks for immediate safety, with toilet and water breaks.")
            response_lines.append("2. Level 2: move one block at a time to a gated room or x-pen with more space and comfort.")
            response_lines.append("3. Level 1: progress to a dog-proofed room plus short check-ins and calm departure/return routines.")
            response_lines.append("4. Level 0 (target): no routine crating, with stable home setup and separation training.")
        elif "crate" in text or "crating" in text:
            response_lines.append(
                "If you choose any temporary confinement, keep it brief and keep stepping down toward a no-crating routine as soon as behavior is stable."
            )

        if short_term_medical_context:
            response_lines.append(
                "Temporary confinement can be used only for clear vet-directed medical safety, with minimum duration and a defined step-down plan."
            )
        else:
            response_lines.append(
                "Crate use should be a last-resort, short-term safety tool only, not a routine behavior-control method."
            )

        response_lines.append("If there is panic, self-injury risk, or persistent distress, contact your vet or a qualified behavior professional promptly.")

        citations = [
            ChatCitation(
                title="What can I do if my dog is anxious when I'm not at home?",
                source="RSPCA Australia",
                url="https://kb.rspca.org.au/knowledge-base/what-can-i-do-if-my-dog-is-anxious-when-im-not-at-home/",
            ),
            ChatCitation(
                title="Humane Dog Training Position Statement",
                source="AVSAB",
                url="https://avsab.org/wp-content/uploads/2021/08/AVSAB-Humane-Dog-Training-Position-Statement-2021.pdf",
            ),
            ChatCitation(
                title="Canine Life Stage Care",
                source="AAHA",
                url="https://www.aaha.org/resources/life-stage-canine/",
            ),
        ]
        return ChatResponse(
            answer=self._format_answer_paragraphs("\n\n".join(response_lines)),
            suggested_profile=session.profile_memory,
            cta_chips=[
                CtaChip(label="Open Community", action="open_community"),
                CtaChip(label="Open Services", action="open_services"),
            ],
            answer_source="policy",
            answer_badges=["Welfare First", "Crate Last", "Least Restrictive"],
            citations=citations,
        )

    def _is_crate_related_query(self, text: str) -> bool:
        if "kennel cough" in text:
            return False
        patterns = [
            r"\bcrate\b",
            r"\bcrating\b",
            r"\bcrate train(?:ing)?\b",
            r"\bkennel\b",
            r"\bcage\b",
            r"\bcaged\b",
        ]
        return any(re.search(pattern, text) for pattern in patterns)

    def _welfare_policy_guard(
        self,
        message: str,
        session: SessionMemory,
        suburb: Optional[str] = None,
    ) -> Optional[ChatResponse]:
        text = message.lower()
        policy_topic = self._detect_welfare_policy_topic(text)
        if policy_topic is None:
            return None

        response_lines: List[str]
        badges: List[str]
        citations: List[ChatCitation]
        if policy_topic == "corporal_punishment":
            response_lines = [
                "BarkWise does not support corporal punishment or pain/fear-based training.",
                "Do not use hitting, smacking, alpha rolls, or punitive tool use to change behavior.",
                "Use reward-based training, environment management, and structured behavior plans instead.",
                "If there is growling, bite risk, or escalating fear, involve your vet and a qualified behavior professional.",
            ]
            badges = ["Welfare First", "No Corporal Punishment", "Reward-Based"]
            citations = [
                ChatCitation(
                    title="Humane Dog Training Position Statement",
                    source="AVSAB",
                    url="https://avsab.org/wp-content/uploads/2021/08/AVSAB-Humane-Dog-Training-Position-Statement-2021.pdf",
                ),
                ChatCitation(
                    title="Welfare Risks of Pronged Collars",
                    source="RSPCA Australia",
                    url="https://kb.rspca.org.au/knowledge-base/are-pronged-collars-harmful-to-my-dog/",
                ),
            ]
        elif policy_topic == "ute_tray_transport":
            country_code = self._resolve_country_code(message=message, session=session, suburb=suburb)
            country_name = self.country_names.get(country_code, self.country_names.get("DEFAULT", "your region"))
            policy_mode = self._ute_tray_policy_mode(country_code)

            if policy_mode == "au_working_dog_transition":
                response_lines = [
                    f"BarkWise recognizes ute-tray transport is common in {country_name} working-dog settings, but we do not recommend routine open-tray short-lead restraint as a default approach.",
                    "Safety order should be: inside-cabin restraint first, then secure enclosed transport (for example canopy/crate setup), and only then very short tray transfers if unavoidable.",
                    "If a short tray transfer is unavoidable: use a well-fitted chest harness (not neck collar), two-point restraint that prevents edge access, non-slip surface, weather protection, and frequent water/check stops.",
                    "Avoid high heat, high speed, and long duration, and set a transition plan toward enclosed or in-cabin transport as the standard.",
                    "Check your local welfare and road rules because legal requirements vary by state/territory.",
                ]
            else:
                response_lines = [
                    "BarkWise does not recommend routine open-tray or truck-bed restraint for dogs.",
                    "Safety order should be: inside-cabin restraint first, then secure enclosed transport, and only then very short emergency-only tray transfers if unavoidable.",
                    "If any short transfer is unavoidable: use a chest harness, two-point restraint, non-slip surface, weather protection, and frequent safety checks.",
                    "Avoid high heat, high speed, and long duration, and move to enclosed or in-cabin transport as the standard.",
                    "Check your local welfare and transport laws because requirements vary by jurisdiction.",
                ]
            badges = ["Welfare First", "Transport Safety", "Least Restrictive"]
            citations = [
                ChatCitation(
                    title="Heat and Pets",
                    source="Agriculture Victoria",
                    url="https://agriculture.vic.gov.au/livestock-and-animals/animal-welfare-victoria/dogs/health/heat-and-pets",
                ),
                ChatCitation(
                    title="Prepare Pets and Livestock for Hot Weather",
                    source="NSW Government",
                    url="https://www.nsw.gov.au/emergency/prepare/pets-and-livestock",
                ),
            ]
        else:
            response_lines = [
                "BarkWise does not support long-term outdoor tethering, chaining, or routine heavy restraint.",
                "Use least-restrictive options first: secure fencing, supervised time, safe indoor areas, and gradual alone-time training.",
                "If temporary restraint is required for immediate safety, keep it brief, supervised, and paired with water, shade, and a clear step-down plan.",
                "For repeated escape, panic, or aggression concerns, involve your vet and a qualified behavior professional.",
            ]
            badges = ["Welfare First", "Least Restrictive", "No Long-Term Tethering"]
            citations = [
                ChatCitation(
                    title="What can I do if my dog is anxious when I'm not at home?",
                    source="RSPCA Australia",
                    url="https://kb.rspca.org.au/knowledge-base/what-can-i-do-if-my-dog-is-anxious-when-im-not-at-home/",
                ),
                ChatCitation(
                    title="Humane Dog Training Position Statement",
                    source="AVSAB",
                    url="https://avsab.org/wp-content/uploads/2021/08/AVSAB-Humane-Dog-Training-Position-Statement-2021.pdf",
                ),
            ]

        return ChatResponse(
            answer=self._format_answer_paragraphs("\n\n".join(response_lines)),
            suggested_profile=session.profile_memory,
            cta_chips=[
                CtaChip(label="Open Community", action="open_community"),
                CtaChip(label="Open Services", action="open_services"),
            ],
            answer_source="policy",
            answer_badges=badges,
            citations=citations,
        )

    def _detect_welfare_policy_topic(self, text: str) -> Optional[str]:
        corporal_patterns = [
            r"\bcorporal punishment\b",
            r"\b(hit|hitting|smack|smacking|spank|beating)\b",
            r"\balpha roll\b",
            r"\b(shock|prong|choke|punish|punishment|aversive)\b.{0,24}\b(collar|training|method|correction)\b",
            r"\bphysical correction\b",
        ]
        if any(re.search(pattern, text) for pattern in corporal_patterns):
            return "corporal_punishment"

        has_transport_terms = bool(
            re.search(r"\b(ute|utility vehicle|truck bed|pickup bed|pick-up bed|open tray|back tray|car tray)\b", text)
        )
        has_dog_reference = bool(re.search(r"\b(dog|dogs|puppy|puppies)\b", text))
        has_transport_context = bool(re.search(r"\b(transport|ride|riding|travel|carry|carrying)\b", text))
        if has_transport_terms and (has_dog_reference or has_transport_context):
            return "ute_tray_transport"

        restraint_patterns = [
            r"\b(outdoor restrain(?:ing|t)?|restrain(?:ing|t)|restraint)\b",
            r"\b(tether|tethering|chain|chaining|chained|tie out|tied outside|tie outside)\b",
            r"\b(keep|leave).{0,24}\b(outside|outdoors)\b.{0,24}\b(tied|chained|restrain(?:ed|ing)?)\b",
        ]
        if any(re.search(pattern, text) for pattern in restraint_patterns):
            return "outdoor_restraining"

        return None

    def _resolve_country_code(self, *, message: str, session: SessionMemory, suburb: Optional[str]) -> str:
        from_message = self._extract_country_code_from_text(message)
        if from_message:
            return from_message

        profile_country = str(session.profile_memory.get("country_code", "")).strip().upper()
        if profile_country:
            return profile_country

        suburb_text = (suburb or str(session.profile_memory.get("suburb", ""))).strip()
        from_suburb = self._infer_country_code_from_suburb(suburb_text)
        if from_suburb:
            return from_suburb

        return self.default_country_code or "DEFAULT"

    def _ute_tray_policy_mode(self, country_code: str) -> str:
        normalized = (country_code or "").strip().upper()
        if normalized in self.ute_tray_policy_by_country:
            return self.ute_tray_policy_by_country[normalized]
        return self.ute_tray_policy_by_country.get("DEFAULT", "global_strict_transition")

    def _extract_country_code_from_text(self, text: str) -> Optional[str]:
        lowered = text.lower()
        patterns = [
            (r"\b(australia|australian|nsw|victoria|queensland|new south wales|western australia)\b", "AU"),
            (r"\b(new zealand|nz|kiwi)\b", "NZ"),
            (r"\b(united states|usa|u\.s\.a\.|u\.s\.)\b", "US"),
            (r"\b(united kingdom|uk|england|scotland|wales)\b", "GB"),
        ]
        for pattern, code in patterns:
            if re.search(pattern, lowered):
                return code
        return None

    def _infer_country_code_from_suburb(self, suburb: str) -> Optional[str]:
        lowered = suburb.strip().lower()
        if not lowered:
            return None
        for hint, country_code in self.suburb_country_hints.items():
            if hint and hint in lowered:
                return country_code
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

    def _build_plan(
        self,
        message: str,
        suburb: Optional[str],
        session: SessionMemory,
        user_id: str = "guest",
    ) -> Dict[str, Any]:
        active_skills = self._select_active_skills(message=message, session=session)
        if self._is_general_assistant_query(message.lower()):
            return self._normalize_plan(
                {
                    "intent": "general_assistant_query",
                    "tools": [],
                    "suggested_profile": session.profile_memory or self._fallback_profile(message),
                },
                message=message,
                suburb=suburb,
            )
        if not self.client:
            return self._normalize_plan(self._heuristic_plan(message, suburb), message=message, suburb=suburb)
        if not self._allow_model_call(stage="planner", user_id=user_id, intent="planner"):
            return self._normalize_plan(self._heuristic_plan(message, suburb), message=message, suburb=suburb)

        system_prompt = (
            "You are the planner for a pet app assistant. "
            "Return strict JSON only with fields: intent, tools, suggested_profile. "
            "Allowed intents: find_dog_walker, find_groomer, lost_found, community_discovery, "
            "weight_concern, provider_onboarding, add_service_listing, add_pet_owner_profile, "
            "manage_community_group, manage_booking, general_pet_question, general_assistant_query, out_of_scope_non_pet. "
            "Allowed tools: search_services, search_groups, draft_lost_found, add_service_listing, "
            "add_pet_owner_profile, create_user_group, add_group_member, "
            "search_availability, create_booking_request, get_booking_status. "
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
                max_output_tokens=self.max_planner_output_tokens,
            )
            content = getattr(response, "output_text", "") or ""
            data = json.loads(content)
            if not isinstance(data, dict):
                return self._normalize_plan(self._heuristic_plan(message, suburb), message=message, suburb=suburb)
            if (
                data.get("intent") == "add_pet_owner_profile"
                and not self._is_profile_capture_request(message.lower())
                and self._is_pet_health_question(message.lower())
            ):
                data["intent"] = "general_pet_question"
                data["tools"] = []
            if data.get("intent") == "general_pet_question":
                data["tools"] = []
            return self._normalize_plan(data, message=message, suburb=suburb)
        except Exception:
            return self._normalize_plan(self._heuristic_plan(message, suburb), message=message, suburb=suburb)

    def _normalize_plan(self, plan: Any, message: str, suburb: Optional[str]) -> Dict[str, Any]:
        if not isinstance(plan, dict):
            plan = self._heuristic_plan(message, suburb)

        intent = self._safe_text(plan.get("intent"), default="general_pet_question", max_len=64)
        if intent not in ALLOWED_INTENTS:
            intent = "general_pet_question"

        tools = self._sanitize_tool_calls(plan.get("tools", []))
        if intent in {"general_pet_question", "general_assistant_query", "out_of_scope_non_pet"}:
            tools = []

        profile = plan.get("suggested_profile")
        if not isinstance(profile, dict):
            profile = self._fallback_profile(message)

        return {
            "intent": intent,
            "tools": tools,
            "suggested_profile": profile,
        }

    def _sanitize_tool_calls(self, tool_calls: Any) -> List[Dict[str, Any]]:
        if not isinstance(tool_calls, list):
            return []

        sanitized: List[Dict[str, Any]] = []
        for raw_call in tool_calls:
            if len(sanitized) >= MAX_TOOL_CALLS_PER_TURN:
                break

            name = ""
            args: Dict[str, Any] = {}
            raw_args: Any = {}
            if isinstance(raw_call, str):
                name = self._safe_text(raw_call, default="", max_len=64)
            elif isinstance(raw_call, dict):
                name = self._safe_text(raw_call.get("name"), default="", max_len=64)
                raw_args = raw_call.get("args", {})
            else:
                continue

            allowed_args = TOOL_ARG_ALLOWLIST.get(name)
            if allowed_args is None:
                continue

            if isinstance(raw_args, dict):
                for arg_name in allowed_args:
                    value = raw_args.get(arg_name)
                    if value in (None, ""):
                        continue
                    if isinstance(value, bool):
                        args[arg_name] = value
                    elif isinstance(value, (int, float)):
                        args[arg_name] = value
                    else:
                        cleaned = self._safe_text(value, default="", max_len=MAX_TOOL_ARG_TEXT_LENGTH)
                        if cleaned:
                            args[arg_name] = cleaned

            sanitized.append({"name": name, "args": args})

        return sanitized

    def _is_prompt_exfiltration_attempt(self, message: str) -> bool:
        compact = re.sub(r"\s+", " ", message.strip())
        if not compact:
            return False
        return any(pattern.search(compact) for pattern in self.prompt_exfiltration_patterns)

    def _heuristic_plan(self, message: str, suburb: Optional[str]) -> Dict[str, Any]:
        text = message.lower()
        provider_id = self._extract_provider_id_from_text(message)
        booking_id = self._extract_booking_id_from_text(message)
        date_hint = self._extract_iso_date_from_text(message)
        time_hint = self._extract_time_slot_from_text(message)
        if self._is_general_assistant_query(text):
            return {
                "intent": "general_assistant_query",
                "tools": [],
                "suggested_profile": self._fallback_profile(message),
            }
        if self._is_high_risk_query(message):
            return {
                "intent": "general_pet_question",
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
        if booking_id or "booking status" in text or "my bookings" in text or "booking update" in text:
            args: Dict[str, Any] = {}
            if booking_id:
                args["booking_id"] = booking_id
            return {
                "intent": "manage_booking",
                "tools": [{"name": "get_booking_status", "args": args}],
                "suggested_profile": self._fallback_profile(message),
            }
        if provider_id and ("availability" in text or "available slot" in text or "free slot" in text):
            args = {"provider_id": provider_id}
            if date_hint:
                args["date"] = date_hint
            return {
                "intent": "manage_booking",
                "tools": [{"name": "search_availability", "args": args}],
                "suggested_profile": self._fallback_profile(message),
            }
        if provider_id and ("book" in text or "booking" in text or "reserve" in text):
            args = {"provider_id": provider_id}
            if date_hint:
                args["date"] = date_hint
            if time_hint:
                args["time_slot"] = time_hint
            pet_name_hint = self._extract_pet_name_for_booking(message)
            if pet_name_hint:
                args["pet_name"] = pet_name_hint
            if self._is_booking_confirmation_text(text):
                args["confirm"] = True
            return {
                "intent": "manage_booking",
                "tools": [{"name": "create_booking_request", "args": args}],
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

    def _is_groundable_pet_knowledge_query(self, message: str, intent: str) -> bool:
        normalized = re.sub(r"\s+", " ", message.strip().lower())
        if not normalized or intent != "general_pet_question":
            return False
        if self._known_breed_summary(normalized):
            return False
        if self._is_general_assistant_query(normalized):
            return False

        pet_subject_tokens = {
            "dog",
            "dogs",
            "puppy",
            "puppies",
            "cat",
            "cats",
            "kitten",
            "pet",
            "pets",
        }
        knowledge_tokens = {
            "safe",
            "safely",
            "feed",
            "food",
            "diet",
            "nutrition",
            "treat",
            "treats",
            "exercise",
            "walking",
            "walk",
            "grooming",
            "groom",
            "bathing",
            "brush",
            "brushing",
            "vaccine",
            "vaccines",
            "vaccination",
            "booster",
            "prevention",
            "preventive",
            "poison",
            "toxin",
            "toxic",
            "itch",
            "itchy",
            "skin",
            "coat",
            "allergy",
            "training",
            "reactive",
            "anxiety",
            "crate",
            "heartworm",
            "flea",
            "tick",
            "worm",
            "worms",
            "weight",
            "obesity",
            "overweight",
            "underweight",
            "vomiting",
            "vomit",
            "diarrhea",
            "diarrhoea",
            "limping",
            "limp",
            "panting",
            "heat",
            "heatstroke",
        }
        guidance_phrases = (
            "what should",
            "how often",
            "how much",
            "is it safe",
            "can my",
            "can dogs",
            "can cats",
            "should i",
            "should my",
            "when should",
            "why is my",
            "what does",
            "what is",
            "tell me about",
            "help me with",
        )
        has_pet_subject = any(token in normalized for token in pet_subject_tokens)
        has_knowledge_signal = any(token in normalized for token in knowledge_tokens) or any(
            phrase in normalized for phrase in guidance_phrases
        )
        return has_pet_subject and has_knowledge_signal

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
        if not isinstance(tool_calls, list):
            return results
        for call in tool_calls[:MAX_TOOL_CALLS_PER_TURN]:
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
                elif name == "search_availability":
                    provider_id = self._safe_text(
                        args.get("provider_id") or self._extract_provider_id_from_text(message),
                        default="",
                        max_len=64,
                    )
                    slot_date = self._safe_text(
                        args.get("date") or self._extract_iso_date_from_text(message),
                        default="",
                        max_len=16,
                    )
                    results[name] = self._tool_search_availability(
                        provider_id=provider_id,
                        slot_date=slot_date,
                    )
                elif name == "create_booking_request":
                    results[name] = self._tool_create_booking_request(
                        session=session,
                        message=message,
                        user_id=user_id,
                        args=args,
                    )
                elif name == "get_booking_status":
                    results[name] = self._tool_get_booking_status(
                        message=message,
                        user_id=user_id,
                        args=args,
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

    def _tool_search_availability(self, provider_id: str, slot_date: str) -> Dict[str, Any]:
        if not provider_id:
            return {"status": "missing_info", "required": ["provider_id"]}
        if not slot_date:
            return {"status": "missing_info", "required": ["date"]}
        try:
            slots = service_store.get_available_slots(provider_id=provider_id, slot_date=slot_date)
            available_slots = [slot.model_dump() for slot in slots if slot.available]
            return {
                "status": "ok",
                "provider_id": provider_id,
                "date": slot_date,
                "available_slots": available_slots,
                "available_count": len(available_slots),
            }
        except ServiceStoreError as exc:
            return {
                "status": "error",
                "provider_id": provider_id,
                "date": slot_date,
                "message": str(exc),
            }

    def _tool_create_booking_request(
        self,
        session: SessionMemory,
        message: str,
        user_id: str,
        args: Dict[str, Any],
    ) -> Dict[str, Any]:
        provider_id = self._safe_text(
            args.get("provider_id") or self._extract_provider_id_from_text(message),
            default="",
            max_len=64,
        )
        slot_date = self._safe_text(args.get("date") or self._extract_iso_date_from_text(message), default="", max_len=16)
        time_slot = self._safe_text(args.get("time_slot") or self._extract_time_slot_from_text(message), default="", max_len=8)
        pet_name = self._safe_text(
            args.get("pet_name") or session.profile_memory.get("pet_name") or self._extract_pet_name_for_booking(message),
            default="",
            max_len=64,
        )
        note = self._safe_text(args.get("note"), default="", max_len=240)

        missing: List[str] = []
        if not provider_id:
            missing.append("provider_id")
        if not slot_date:
            missing.append("date")
        if not time_slot:
            missing.append("time_slot")
        if not pet_name:
            missing.append("pet_name")
        if missing:
            return {"status": "missing_info", "required": missing}

        confirmed = self._safe_bool(args.get("confirm"), default=False) or self._is_booking_confirmation_text(message.lower())
        if not confirmed:
            return {
                "status": "requires_confirmation",
                "draft": {
                    "provider_id": provider_id,
                    "date": slot_date,
                    "time_slot": time_slot,
                    "pet_name": pet_name,
                    "note": note,
                },
            }

        try:
            booking = service_store.create_booking(
                BookingRequest(
                    user_id=user_id,
                    provider_id=provider_id,
                    pet_name=pet_name,
                    date=slot_date,
                    time_slot=time_slot,
                    note=note,
                )
            )
            return {"status": "created", "booking": booking.model_dump()}
        except ServiceStoreError as exc:
            return {
                "status": "error",
                "message": str(exc),
                "draft": {
                    "provider_id": provider_id,
                    "date": slot_date,
                    "time_slot": time_slot,
                    "pet_name": pet_name,
                },
            }

    def _tool_get_booking_status(self, message: str, user_id: str, args: Dict[str, Any]) -> Dict[str, Any]:
        booking_id = self._safe_text(
            args.get("booking_id") or self._extract_booking_id_from_text(message),
            default="",
            max_len=64,
        )
        role = self._safe_text(args.get("role"), default="all", max_len=16).lower()
        if role not in {"all", "owner", "provider"}:
            role = "all"

        try:
            bookings = service_store.list_bookings(user_id=user_id, role=role)
            if booking_id:
                matched = next((booking for booking in bookings if booking.id == booking_id), None)
                if not matched:
                    return {"status": "not_found", "booking_id": booking_id}
                return {"status": "found", "booking": matched.model_dump()}
            compact = [booking.model_dump() for booking in bookings[:5]]
            return {"status": "list", "bookings": compact}
        except ServiceStoreError as exc:
            return {"status": "error", "message": str(exc)}

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

    def _extract_provider_id_from_text(self, text: str) -> Optional[str]:
        match = re.search(r"\b(svc_[A-Za-z0-9_-]+)\b", text, re.I)
        if match:
            return match.group(1)
        return None

    def _extract_booking_id_from_text(self, text: str) -> Optional[str]:
        match = re.search(r"\b((?:b|bk)_[A-Za-z0-9_-]+)\b", text, re.I)
        if match:
            return match.group(1)
        return None

    def _extract_iso_date_from_text(self, text: str) -> Optional[str]:
        match = re.search(r"\b(20\d{2}-\d{2}-\d{2})\b", text)
        if match:
            return match.group(1)
        return None

    def _extract_time_slot_from_text(self, text: str) -> Optional[str]:
        match = re.search(r"\b([01]\d|2[0-3]):([0-5]\d)\b", text)
        if match:
            return f"{match.group(1)}:{match.group(2)}"
        return None

    def _extract_pet_name_for_booking(self, text: str) -> Optional[str]:
        match = re.search(r"\bfor\s+([A-Za-z]{2,})\b", text, re.I)
        if match:
            token = match.group(1).strip()
            if token.lower() not in {"booking", "tomorrow", "today", "next"}:
                return token
        return None

    def _is_booking_confirmation_text(self, text: str) -> bool:
        confirm_markers = [
            "confirm booking",
            "please book",
            "book it",
            "go ahead",
            "yes book",
            "confirm it",
        ]
        return any(marker in text for marker in confirm_markers)

    def _compose_app_workflow_answer(self, *, intent: str, tool_results: Dict[str, Any]) -> Optional[str]:
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
        if intent == "manage_booking":
            availability = tool_results.get("search_availability")
            if isinstance(availability, dict):
                if availability.get("status") == "ok":
                    slots = availability.get("available_slots", [])
                    if isinstance(slots, list) and slots:
                        preview = ", ".join(str(slot.get("time_slot", "")) for slot in slots[:5] if isinstance(slot, dict))
                        return f"Available slots for {availability.get('date', 'that date')}: {preview}."
                    return "I checked availability and there are no open slots on that date."
                if availability.get("status") == "error":
                    return f"I could not check availability: {availability.get('message', 'unknown error')}."

            booking_create = tool_results.get("create_booking_request")
            if isinstance(booking_create, dict):
                status = str(booking_create.get("status", ""))
                if status == "created":
                    booking = booking_create.get("booking", {})
                    if isinstance(booking, dict):
                        return (
                            f"Booking requested: {booking.get('id', 'new booking')} for "
                            f"{booking.get('date', '')} {booking.get('time_slot', '')}."
                        )
                    return "Booking request created."
                if status == "requires_confirmation":
                    return (
                        "I have the booking details ready. Reply with 'confirm booking' to submit this request."
                    )
                if status == "missing_info":
                    required = booking_create.get("required", [])
                    if isinstance(required, list) and required:
                        return f"I need a few details first: {', '.join(str(item) for item in required)}."
                    return "I need more details to create this booking."
                if status == "error":
                    return f"I could not create the booking: {booking_create.get('message', 'unknown error')}."

            booking_status = tool_results.get("get_booking_status")
            if isinstance(booking_status, dict):
                status = str(booking_status.get("status", ""))
                if status == "found":
                    booking = booking_status.get("booking", {})
                    if isinstance(booking, dict):
                        return (
                            f"Booking {booking.get('id', '')} is {booking.get('status', 'unknown')} "
                            f"for {booking.get('date', '')} {booking.get('time_slot', '')}."
                        )
                    return "I found your booking status."
                if status == "list":
                    bookings = booking_status.get("bookings", [])
                    if isinstance(bookings, list) and bookings:
                        latest = bookings[0] if isinstance(bookings[0], dict) else {}
                        return (
                            f"You have {len(bookings)} booking(s). Latest is {latest.get('id', '')}: "
                            f"{latest.get('status', 'unknown')} on {latest.get('date', '')} {latest.get('time_slot', '')}."
                        )
                    return "You do not have any bookings yet."
                if status == "not_found":
                    return "I could not find that booking."
                if status == "error":
                    return f"I could not fetch booking status: {booking_status.get('message', 'unknown error')}."
        return None

    def _compact_rag_context_for_model(self, rag_context: Dict[str, Any], *, max_docs: int = 3) -> Dict[str, Any]:
        compact_docs: List[Dict[str, str]] = []
        docs = rag_context.get("documents", [])
        if isinstance(docs, list):
            for doc in docs[:max_docs]:
                if not isinstance(doc, dict):
                    continue
                compact_doc = {
                    "title": self._safe_text(doc.get("title"), default="", max_len=160),
                    "authority": self._safe_text(doc.get("authority"), default="", max_len=120),
                    "snippet": self._safe_text(doc.get("snippet"), default="", max_len=420),
                    "url": self._safe_text(doc.get("url"), default="", max_len=240),
                }
                if compact_doc["title"] or compact_doc["snippet"]:
                    compact_docs.append(compact_doc)

        return {
            "query": self._safe_text(rag_context.get("query"), default="", max_len=240),
            "intent": self._safe_text(rag_context.get("intent"), default="", max_len=64),
            "source_policy": self._safe_text(rag_context.get("source_policy"), default="default", max_len=64),
            "high_risk_mode": bool(rag_context.get("high_risk_mode", False)),
            "high_risk_terms": rag_context.get("high_risk_terms", []),
            "documents": compact_docs,
        }

    def _compose_answer(
        self,
        message: str,
        suburb: Optional[str],
        plan: Dict[str, Any],
        route: Dict[str, Any],
        tool_results: Dict[str, Any],
        session: SessionMemory,
        rag_context: Dict[str, Any],
        user_id: str = "guest",
    ) -> str:
        intent = plan.get("intent", "general_pet_question")
        high_risk_mode = bool(route.get("high_risk_mode", False))
        breed_summary = self._known_breed_summary(message.lower())
        if breed_summary:
            return self._format_answer_paragraphs(breed_summary)
        rag_related = route.get("lane") == "RAG"
        tone_profile = self._tone_profile(message=message, suburb=suburb, intent=intent, rag_related=rag_related)
        known_fields = self._known_profile_fields(session.profile_memory)
        missing_fields = self._missing_profile_fields(session.profile_memory)
        locked_fields = [key for key, locked in session.field_locks.items() if locked]

        if self.client and self._allow_model_call(stage="answer", user_id=user_id, intent=str(intent)):
            if high_risk_mode:
                system_prompt = (
                    "You are BarkAI in HIGH-RISK SAFETY MODE for dog health concerns. "
                    "Respond with ultra-safe guidance only. "
                    "Do not diagnose. Do not use or cite community anecdotes, social posts, or Reddit-style content. "
                    "Use only trusted medical/public-health references from rag_context documents. "
                    "Provide concise immediate actions, critical red flags, and clear escalation to emergency vet care."
                )
            elif intent == "out_of_scope_non_pet":
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
                    "If rag_context.documents are present, use them as the primary evidence and avoid unsupported specifics. "
                    "If evidence is limited, say that briefly and stay conservative. "
                    "If tone_profile.rag_support_mode is true, start with one reassuring sentence, then prioritize the most relevant points from rag_context."
                )
            else:
                system_prompt = (
                    "You are a pet assistant in a mobile app. "
                    "Be concise and practical. Do not provide definitive medical diagnosis. "
                    "Use short readable paragraphs. For answers longer than two sentences, split into 2-4 sentence paragraphs with a blank line between them. "
                    "Use conversation memory context. "
                    "Ground factual or local details in rag_context when relevant, and do not invent specific provider/group/post names not present there. "
                    "If rag_context.documents are present, treat them as the main evidence for factual claims and do not add unsupported details beyond them. "
                    "If the retrieved evidence is thin or partial, say so briefly and give the safest practical next step. "
                    "Follow tone_profile: if support_mode is true, use warm, supportive, non-clinical language, open with empathy, and include local context only when natural. "
                    "If tone_profile.rag_support_mode is true, start with one reassuring sentence, then prioritize the most relevant points from rag_context. "
                    "Never ask again for any profile field already known. "
                    "Only ask for at most one missing profile field if it is strictly needed for the user's current request. "
                    "If profile_accepted is true, do not ask profile collection questions unless the user explicitly asks to edit profile. "
                    "If a field is locked, it is forbidden to ask it again."
                )
            if high_risk_mode:
                filtered_rag_context = self._compact_rag_context_for_model(rag_context, max_docs=4)
            else:
                filtered_rag_context = self._compact_rag_context_for_model(
                    rag_context,
                    max_docs=3 if self._is_pet_health_question(message.lower()) else 4,
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
                "high_risk_mode": high_risk_mode,
                "matched_high_risk_terms": route.get("matched_high_risk_terms", []),
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
                    max_output_tokens=self.max_answer_output_tokens,
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
        app_workflow_answer = self._compose_app_workflow_answer(intent=intent, tool_results=tool_results)
        if app_workflow_answer:
            return app_workflow_answer
        rag_fallback = self.rag_retriever.fallback_answer(
            rag_context=rag_context,
            support_mode=bool(tone_profile.get("support_mode")),
        )
        if high_risk_mode and rag_fallback:
            return rag_fallback
        if rag_related and rag_fallback:
            return rag_fallback
        if high_risk_mode:
            return (
                "This may be high risk. I cannot diagnose in chat, but you should contact a veterinarian now for real-time triage. "
                "Avoid home medications and do not induce vomiting unless a vet explicitly tells you to. "
                "If collapse, breathing difficulty, seizures, repeated vomiting, or severe weakness are present, seek emergency care immediately."
            )
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
        if intent == "manage_booking":
            ctas.append(CtaChip(label="Open Services", action="open_services"))
        if not ctas:
            ctas.append(CtaChip(label="Open Services", action="open_services"))
            ctas.append(CtaChip(label="Open Community", action="open_community"))
        return ctas

    def _update_profile_memory(self, session: SessionMemory, message: str, suburb: Optional[str]) -> None:
        profile = session.profile_memory
        text = message.lower()
        country_code = self._extract_country_code_from_text(message)
        if country_code:
            profile["country_code"] = country_code
            session.field_locks["country_code"] = True

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
        text = re.sub(r"[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]", "", str(value)).strip()
        if not text:
            return default
        if len(text) > max_len:
            return text[:max_len]
        return text

    def _safe_bool(self, value: Any, default: bool = False) -> bool:
        if isinstance(value, bool):
            return value
        if value is None:
            return default
        normalized = self._safe_text(value, default="", max_len=16).lower()
        if not normalized:
            return default
        if normalized in {"1", "true", "yes", "y", "on", "confirm", "confirmed"}:
            return True
        if normalized in {"0", "false", "no", "n", "off"}:
            return False
        return default

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
