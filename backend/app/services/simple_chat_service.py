from __future__ import annotations

from datetime import datetime, timezone
import os
from pathlib import Path
from typing import Generator

from fastapi import HTTPException

from app.models import (
    ChatMessage,
    ChatPendingConfirmation,
    ChatRequest,
    ChatResponse,
    ChatTurn,
    CtaChip,
    PetProfileSuggestion,
)
from app.services.auth_otp_store import auth_otp_store
from app.services.chat_policy import ChatPolicyBuilder
from app.services.chat_router import BarkAiDecision, BarkAiFailureCategory, BarkAiResult, BarkAiRoute, BarkAiRouter, PendingConfirmation
from app.services.chat_tools import BarkAiTools
from app.services.llm_client import LlmClient, LlmClientError, extract_openai_api_key as _extract_openai_api_key
from app.services.memory_store import MemoryStore
from app.services.service_store import service_store

PROVIDER_FIELDS = (
    "service_name",
    "category",
    "suburb",
    "description",
    "price_from",
    "contact_name",
)


def _read_bool_env(name: str, default: str) -> bool:
    return os.getenv(name, default).strip().lower() in {"1", "true", "yes", "on"}


class SimpleChatService:
    def __init__(self) -> None:
        self.model = os.getenv("OPENAI_MODEL", "gpt-4.1-mini")
        self.timeout_seconds = float(os.getenv("OPENAI_TIMEOUT_SECONDS", "60"))
        self.max_retries = max(1, int(os.getenv("OPENAI_MAX_RETRIES", "2")))
        self.retry_backoff_seconds = max(0.0, float(os.getenv("OPENAI_RETRY_BACKOFF_SECONDS", "1.0")))
        default_db_path = str(Path(__file__).resolve().parents[2] / "data" / "memory.sqlite3")
        self.memory_store = MemoryStore(db_path=os.getenv("MEMORY_DB_PATH", default_db_path))
        self.llm_client = LlmClient(
            model=self.model,
            timeout_seconds=self.timeout_seconds,
            max_retries=self.max_retries,
            retry_backoff_seconds=self.retry_backoff_seconds,
        )
        self.policy_builder = ChatPolicyBuilder()
        self.router = BarkAiRouter()
        self.tools = BarkAiTools(mutating_actions_enabled=self.mutating_actions_enabled)
        self._last_llm_report: dict[str, object] = {
            "status": "unknown",
            "checked_at": None,
            "last_error": None,
            "provider_reachable": False,
            "non_stream_ok": False,
            "stream_ok": False,
        }

    @property
    def llm_available(self) -> bool:
        return self.llm_client.configured

    @property
    def barkai_mode(self) -> str:
        return self.policy_builder.barkai_mode

    @property
    def tools_enabled(self) -> bool:
        return _read_bool_env("BARKAI_ENABLE_TOOLS", "true")

    @property
    def mutating_actions_enabled(self) -> bool:
        return _read_bool_env("BARKAI_ENABLE_MUTATING_ACTIONS", "false")

    @property
    def memory_summary_enabled(self) -> bool:
        return self.policy_builder.summary_enabled

    @property
    def diagnostics_token(self) -> str:
        return os.getenv("BARKAI_DIAGNOSTICS_TOKEN", "").strip()

    @property
    def ready_state(self) -> dict[str, object]:
        llm_status = "unconfigured" if not self.llm_available else str(self._last_llm_report.get("status") or "configured")
        return {
            "status": "ready",
            "llm_configured": self.llm_available,
            "llm_mode": "openai" if self.llm_available else "unconfigured",
            "barkai_mode": self.barkai_mode,
            "llm_status": llm_status,
        }

    def create_chat_response(self, request: ChatRequest) -> ChatResponse:
        transcript = self._resolve_transcript(request)
        state = self._load_user_state(user_id=request.user_id)
        profile_context = self._profile_context(user_id=request.user_id)
        self._update_preferences(state=state, request=request, profile_context=profile_context)
        decision = self.router.route(
            transcript=transcript,
            pending_confirmation=PendingConfirmation.from_dict(state["pending_confirmation"]),
        )
        response = self._execute_decision(
            request=request,
            transcript=transcript,
            state=state,
            profile_context=profile_context,
            decision=decision,
        )
        self._persist_chat_state(
            user_id=request.user_id,
            transcript=transcript,
            response=response,
            state=state,
            persist_assistant_turn=response.status != "error",
        )
        return response

    def stream_chat(self, request: ChatRequest) -> Generator[dict, None, None]:
        transcript = self._resolve_transcript(request)
        state = self._load_user_state(user_id=request.user_id)
        profile_context = self._profile_context(user_id=request.user_id)
        self._update_preferences(state=state, request=request, profile_context=profile_context)
        decision = self.router.route(
            transcript=transcript,
            pending_confirmation=PendingConfirmation.from_dict(state["pending_confirmation"]),
        )
        if decision.route != BarkAiRoute.CHAT:
            response = self._execute_non_chat_decision(
                request=request,
                transcript=transcript,
                state=state,
                profile_context=profile_context,
                decision=decision,
            )
            self._persist_chat_state(
                user_id=request.user_id,
                transcript=transcript,
                response=response,
                state=state,
                persist_assistant_turn=response.status != "error",
            )
            yield {"type": "delta", "delta": response.answer}
            yield {"type": "final", "response": response.model_dump(mode="json")}
            return

        messages = self._build_openai_messages(
            transcript=transcript,
            profile_context=profile_context,
            state=state,
            policy_tags=decision.policy_tags,
        )
        chunks: list[str] = []
        try:
            for delta in self.llm_client.stream_text(messages=messages):
                chunks.append(delta)
                yield {"type": "delta", "delta": delta}
            text = "".join(chunks).strip()
            if not text:
                raise LlmClientError(category="llm_unavailable", detail="OpenAI returned an empty streamed chat response")
            self._record_llm_success(stream=True)
            result = BarkAiResult(answer=text, answer_source="assistant")
        except LlmClientError as exc:
            if chunks:
                try:
                    fallback_text = self.llm_client.generate_text(messages=messages)
                    self._record_llm_success(stream=False)
                    result = BarkAiResult(answer=fallback_text, answer_source="assistant")
                except LlmClientError as fallback_exc:
                    self._record_llm_failure(fallback_exc)
                    result = self._error_result_from_llm_exception(fallback_exc)
            else:
                try:
                    fallback_text = self.llm_client.generate_text(messages=messages)
                    self._record_llm_success(stream=False)
                    result = BarkAiResult(answer=fallback_text, answer_source="assistant")
                except LlmClientError as fallback_exc:
                    self._record_llm_failure(fallback_exc)
                    result = self._error_result_from_llm_exception(fallback_exc)
        response = self._build_chat_response_from_result(transcript=transcript, result=result)
        self._persist_chat_state(
            user_id=request.user_id,
            transcript=transcript,
            response=response,
            state=state,
            persist_assistant_turn=response.status != "error",
        )
        yield {"type": "final", "response": response.model_dump(mode="json")}

    def run_llm_diagnostics(self) -> dict[str, object]:
        report = self.llm_client.run_synthetic_check()
        report["barkai_mode"] = self.barkai_mode
        report["enabled_capabilities"] = {
            "tools": self.tools_enabled,
            "mutating_actions": self.mutating_actions_enabled,
            "reddit_curated_guidance": self.policy_builder.reddit_guidance_enabled,
            "memory_summary": self.memory_summary_enabled,
        }
        report["status"] = (
            "ok"
            if report.get("config_loaded") and report.get("non_stream_ok") and report.get("stream_ok")
            else ("degraded" if report.get("config_loaded") else "unconfigured")
        )
        self._last_llm_report = {
            "status": report["status"],
            "checked_at": report.get("checked_at"),
            "last_error": report.get("last_error"),
            "provider_reachable": report.get("provider_reachable", False),
            "non_stream_ok": report.get("non_stream_ok", False),
            "stream_ok": report.get("stream_ok", False),
        }
        return report

    def accept_profile(self, *, user_id: str) -> ChatResponse:
        state = self._load_user_state(user_id=user_id)
        suggestion = self._build_profile_suggestion(state["profile_memory"])
        if not suggestion:
            return self._persist_action_response(
                user_id=user_id,
                state=state,
                answer="I need a bit more information before creating your pet profile card.",
            )

        state["profile_accepted"] = True
        return self._persist_action_response(
            user_id=user_id,
            state=state,
            answer="Profile created. You can edit details later from settings.",
            cta_chips=[CtaChip(label="Open Community", action="open_community")],
            profile_suggestion=suggestion,
        )

    def submit_provider_listing(self, *, user_id: str) -> ChatResponse:
        state = self._load_user_state(user_id=user_id)
        if not auth_otp_store.user_can_create_provider_listings(user_id=user_id):
            auth_otp_store.set_service_provider_mode(user_id=user_id, enabled=True)

        draft = state["provider_state"].get("collected", {})
        if not isinstance(draft, dict):
            draft = {}
        missing = [field for field in PROVIDER_FIELDS if not draft.get(field)]
        if missing:
            return self._persist_action_response(
                user_id=user_id,
                state=state,
                answer=f"I still need: {', '.join(missing)}.",
            )

        category = str(draft.get("category", "dog_walking"))
        if category not in {"dog_walking", "grooming"}:
            category = "dog_walking"

        service_store.add_provider(
            owner_user_id=user_id,
            name=str(draft.get("service_name")),
            category=category,
            suburb=str(draft.get("suburb")),
            description=str(draft.get("description")),
            price_from=self._safe_price_from(draft.get("price_from")),
        )
        state["provider_state"] = {}

        return self._persist_action_response(
            user_id=user_id,
            state=state,
            answer="Your service has been added to the listing and is now visible in Services.",
            cta_chips=[CtaChip(label="Open Services", action="open_services", payload={"category": category})],
        )

    def _execute_decision(
        self,
        *,
        request: ChatRequest,
        transcript: list[ChatMessage],
        state: dict[str, object],
        profile_context: dict[str, object],
        decision: BarkAiDecision,
    ) -> ChatResponse:
        if decision.route != BarkAiRoute.CHAT:
            return self._execute_non_chat_decision(
                request=request,
                transcript=transcript,
                state=state,
                profile_context=profile_context,
                decision=decision,
            )
        try:
            messages = self._build_openai_messages(
                transcript=transcript,
                profile_context=profile_context,
                state=state,
                policy_tags=decision.policy_tags,
            )
            text = self.llm_client.generate_text(messages=messages)
            self._record_llm_success(stream=False)
            result = BarkAiResult(answer=text, answer_source="assistant")
        except LlmClientError as exc:
            self._record_llm_failure(exc)
            result = self._error_result_from_llm_exception(exc)
        return self._build_chat_response_from_result(transcript=transcript, result=result)

    def _execute_non_chat_decision(
        self,
        *,
        request: ChatRequest,
        transcript: list[ChatMessage],
        state: dict[str, object],
        profile_context: dict[str, object],
        decision: BarkAiDecision,
    ) -> ChatResponse:
        if not self.tools_enabled and decision.route in {BarkAiRoute.TOOL_READ, BarkAiRoute.TOOL_ACTION_CONFIRMATION, BarkAiRoute.TOOL_ACTION_EXECUTE}:
            fallback_decision = BarkAiDecision(route=BarkAiRoute.CHAT, policy_tags=decision.policy_tags)
            return self._execute_decision(
                request=request,
                transcript=transcript,
                state=state,
                profile_context=profile_context,
                decision=fallback_decision,
            )

        if decision.route == BarkAiRoute.TOOL_ACTION_EXECUTE:
            self.tools.mutating_actions_enabled = self.mutating_actions_enabled
            result = self.tools.execute_confirmed_action(
                user_id=request.user_id,
                action=str(decision.tool_name or ""),
                params=dict(decision.params),
            )
        else:
            result = self.tools.execute(
                user_id=request.user_id,
                transcript=transcript,
                tool_name=str(decision.tool_name or ""),
                params=dict(decision.params),
                profile_context=profile_context,
            )
        if result.pending_confirmation and not result.pending_confirmation.expires_at:
            result.pending_confirmation.expires_at = self.router.confirmation_expiry_iso()
        return self._build_chat_response_from_result(transcript=transcript, result=result)

    def _resolve_transcript(self, request: ChatRequest) -> list[ChatMessage]:
        transcript = [
            ChatMessage(role=message.role, content=message.content.strip())
            for message in request.messages
            if message.content.strip()
        ]
        if transcript:
            return transcript[-20:]

        legacy_message = (request.message or "").strip()
        if legacy_message:
            return [ChatMessage(role="user", content=legacy_message)]

        raise HTTPException(status_code=422, detail="At least one chat message is required")

    def _build_openai_messages(
        self,
        *,
        transcript: list[ChatMessage],
        user_id: str | None = None,
        profile_context: dict[str, object] | None = None,
        state: dict[str, object] | None = None,
        policy_tags: list[str] | None = None,
    ) -> list[dict[str, str]]:
        resolved_profile_context = profile_context or self._profile_context(user_id=user_id or "")
        resolved_state = state or self._load_user_state(user_id=user_id or "")
        return self.policy_builder.build_messages(
            transcript=transcript,
            profile_context=resolved_profile_context,
            memory_summary=str(resolved_state.get("conversation_summary") or ""),
            preferences=dict(resolved_state.get("preferences") or {}),
            policy_tags=policy_tags or [],
        )

    def _profile_context(self, *, user_id: str) -> dict[str, object]:
        try:
            profile = auth_otp_store.get_or_create_user_profile(user_id=user_id)
        except Exception:
            return {}

        context: dict[str, object] = {}

        def add_text(key: str, attr: str) -> None:
            value = str(getattr(profile, attr, "") or "").strip()
            if value:
                context[key] = value

        def read_positive_int(attr: str) -> int:
            try:
                return int(getattr(profile, attr, 0) or 0)
            except (TypeError, ValueError):
                return 0

        add_text("display_name", "display_name")
        add_text("suburb", "suburb")
        add_text("dog_name", "dog_name")
        add_text("dog_breed_mix", "dog_breed_mix")
        add_text("dog_sex_neuter", "dog_sex_neuter")
        add_text("dog_weight_class", "dog_weight_class")
        add_text("secondary_dog_name", "secondary_dog_name")
        add_text("secondary_dog_gender", "secondary_dog_gender")
        add_text("secondary_dog_weight_kg", "secondary_dog_weight_kg")
        add_text("bio", "bio")
        add_text("play_energy_level", "play_energy_level")
        add_text("play_style", "play_style")
        add_text("social_confidence", "social_confidence")
        add_text("trigger_notes", "trigger_notes")
        add_text("ideal_match", "ideal_match")
        add_text("walk_preferences", "walk_preferences")
        add_text("training_style", "training_style")
        add_text("feeding_rules", "feeding_rules")
        add_text("consent_boundaries", "consent_boundaries")
        add_text("vaccination_status", "vaccination_status")
        add_text("leash_reliability", "leash_reliability")

        dog_age_months = read_positive_int("dog_age_months")
        if dog_age_months > 0:
            context["dog_age_months"] = dog_age_months
        secondary_dog_age_months = read_positive_int("secondary_dog_age_months")
        if secondary_dog_age_months > 0:
            context["secondary_dog_age_months"] = secondary_dog_age_months
        favorite_suburbs = [
            str(value).strip()
            for value in list(getattr(profile, "favorite_suburbs", []) or [])
            if str(value).strip()
        ]
        if favorite_suburbs:
            context["favorite_suburbs"] = favorite_suburbs[:5]
        if bool(getattr(profile, "microchipped", False)):
            context["microchipped"] = True
        if bool(getattr(profile, "recall_trained", False)):
            context["recall_trained"] = True
        return context

    def _load_openai_api_key(self) -> str:
        return self.llm_client._load_openai_api_key()

    def _build_chat_response_from_result(self, *, transcript: list[ChatMessage], result: BarkAiResult) -> ChatResponse:
        conversation = [ChatTurn(role=item.role, content=item.content) for item in transcript]
        conversation.append(
            ChatTurn(
                role="assistant",
                content=result.answer,
                answer_source=result.answer_source,
                answer_badges=list(result.answer_badges),
                citations=list(result.citations),
            )
        )
        pending_confirmation = (
            ChatPendingConfirmation(
                action=result.pending_confirmation.action,
                prompt=result.pending_confirmation.prompt,
                expires_at=result.pending_confirmation.expires_at,
                params=dict(result.pending_confirmation.params),
            )
            if result.pending_confirmation
            else None
        )
        return ChatResponse(
            answer=result.answer,
            message=ChatMessage(role="assistant", content=result.answer),
            conversation=conversation,
            cta_chips=list(result.cta_chips),
            answer_source=result.answer_source,
            answer_badges=list(result.answer_badges),
            citations=list(result.citations),
            a2ui_messages=list(result.a2ui_messages),
            status=result.status,
            error_type=result.error_type,
            pending_confirmation=pending_confirmation,
        )

    def _error_result_from_llm_exception(self, exc: LlmClientError) -> BarkAiResult:
        category = (
            BarkAiFailureCategory.BACKEND_UNAVAILABLE.value
            if exc.category == "backend_unavailable"
            else BarkAiFailureCategory.LLM_UNAVAILABLE.value
        )
        answer = (
            "BarkAI is having trouble reaching its language service right now. Please retry in a moment."
            if category == BarkAiFailureCategory.LLM_UNAVAILABLE.value
            else "BarkAI is temporarily unavailable because the backend connection failed. Please retry shortly."
        )
        return BarkAiResult(
            answer=answer,
            answer_source="assistant_error",
            status="error",
            error_type=category,
        )

    def _record_llm_success(self, *, stream: bool) -> None:
        self._last_llm_report = {
            "status": "ok",
            "checked_at": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
            "last_error": None,
            "provider_reachable": True,
            "non_stream_ok": True,
            "stream_ok": stream or bool(self._last_llm_report.get("stream_ok")),
        }

    def _record_llm_failure(self, exc: LlmClientError) -> None:
        self._last_llm_report = {
            "status": "degraded" if self.llm_available else "unconfigured",
            "checked_at": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
            "last_error": exc.detail,
            "provider_reachable": False,
            "non_stream_ok": False,
            "stream_ok": False,
        }

    def _load_user_state(self, *, user_id: str) -> dict[str, object]:
        raw_state = self.memory_store.load_user_state(user_id)
        profile_memory = raw_state.get("profile_memory", {})
        field_locks = raw_state.get("field_locks", {})
        provider_state = raw_state.get("provider_state", {})
        preferences = raw_state.get("preferences", {})
        pending_confirmation = raw_state.get("pending_confirmation", {})
        return {
            "profile_memory": profile_memory if isinstance(profile_memory, dict) else {},
            "profile_accepted": bool(raw_state.get("profile_accepted", False)),
            "field_locks": field_locks if isinstance(field_locks, dict) else {},
            "provider_state": provider_state if isinstance(provider_state, dict) else {},
            "preferences": preferences if isinstance(preferences, dict) else {},
            "conversation_summary": str(raw_state.get("conversation_summary") or ""),
            "pending_confirmation": pending_confirmation if isinstance(pending_confirmation, dict) else {},
        }

    def _save_user_state(self, *, user_id: str, state: dict[str, object]) -> None:
        self.memory_store.save_user_state(
            user_id=user_id,
            profile_memory=dict(state["profile_memory"]),
            profile_accepted=bool(state["profile_accepted"]),
            field_locks=dict(state["field_locks"]),
            provider_state=dict(state["provider_state"]),
            preferences=dict(state["preferences"]),
            conversation_summary=str(state["conversation_summary"]),
            pending_confirmation=dict(state["pending_confirmation"]),
        )

    def _persist_chat_state(
        self,
        *,
        user_id: str,
        transcript: list[ChatMessage],
        response: ChatResponse,
        state: dict[str, object],
        persist_assistant_turn: bool,
    ) -> None:
        latest_user_message = next((message.content for message in reversed(transcript) if message.role == "user"), "").strip()
        if latest_user_message:
            self._append_turn_if_new(user_id=user_id, role="user", content=latest_user_message)
        if persist_assistant_turn and response.answer.strip():
            self._append_turn_if_new(user_id=user_id, role="assistant", content=response.answer.strip())
        if response.pending_confirmation:
            state["pending_confirmation"] = {
                "action": response.pending_confirmation.action,
                "prompt": response.pending_confirmation.prompt,
                "params": dict(response.pending_confirmation.params),
                "expires_at": response.pending_confirmation.expires_at,
            }
        else:
            state["pending_confirmation"] = {}
        if self.memory_summary_enabled:
            self._maybe_refresh_summary(user_id=user_id, state=state)
        self._save_user_state(user_id=user_id, state=state)

    def _append_turn_if_new(self, *, user_id: str, role: str, content: str) -> None:
        recent_turns = self.memory_store.load_recent_turns(user_id, limit=1)
        if recent_turns and recent_turns[-1]["role"] == role and recent_turns[-1]["content"] == content:
            return
        self.memory_store.append_turn(user_id=user_id, role=role, content=content)

    def _maybe_refresh_summary(self, *, user_id: str, state: dict[str, object]) -> None:
        recent_turns = self.memory_store.load_recent_turns(user_id, limit=8)
        if len(recent_turns) < 6:
            return
        try:
            summary_messages = self.policy_builder.build_summary_messages(
                recent_turns=recent_turns,
                previous_summary=str(state.get("conversation_summary") or ""),
            )
            summary = self.llm_client.generate_text(messages=summary_messages)
            if summary.strip():
                state["conversation_summary"] = summary.strip()
        except LlmClientError:
            return

    def _update_preferences(
        self,
        *,
        state: dict[str, object],
        request: ChatRequest,
        profile_context: dict[str, object],
    ) -> None:
        preferences = dict(state.get("preferences") or {})
        explicit_suburb = (request.suburb or "").strip()
        if explicit_suburb:
            preferences["preferred_suburb"] = explicit_suburb
        elif str(profile_context.get("suburb") or "").strip():
            preferences.setdefault("preferred_suburb", str(profile_context["suburb"]).strip())
        state["preferences"] = preferences

    def _persist_action_response(
        self,
        *,
        user_id: str,
        state: dict[str, object],
        answer: str,
        cta_chips: list[CtaChip] | None = None,
        profile_suggestion: PetProfileSuggestion | None = None,
    ) -> ChatResponse:
        self.memory_store.save_user_state(
            user_id=user_id,
            profile_memory=dict(state["profile_memory"]),
            profile_accepted=bool(state["profile_accepted"]),
            field_locks=dict(state["field_locks"]),
            provider_state=dict(state["provider_state"]),
            preferences=dict(state["preferences"]),
            conversation_summary=str(state["conversation_summary"]),
            pending_confirmation=dict(state["pending_confirmation"]),
        )
        self.memory_store.append_turn(user_id=user_id, role="assistant", content=answer)
        history = self.memory_store.load_recent_turns(user_id, limit=20)
        return ChatResponse(
            answer=answer,
            message=ChatMessage(role="assistant", content=answer),
            suggested_profile=dict(state["profile_memory"]),
            cta_chips=cta_chips or [],
            conversation=[ChatTurn(role=turn["role"], content=turn["content"]) for turn in history],
            profile_suggestion=profile_suggestion,
            answer_source="assistant",
        )

    def _build_profile_suggestion(self, profile: dict[str, object]) -> PetProfileSuggestion | None:
        score = 0
        for key in ("pet_name", "pet_type", "breed", "age_years", "weight_kg", "suburb"):
            if profile.get(key) is not None:
                score += 1
        if profile.get("concerns"):
            score += 1
        if score < 3:
            return None
        concerns = profile.get("concerns", [])
        if not isinstance(concerns, list):
            concerns = []
        return PetProfileSuggestion(
            owner_name=str(profile.get("owner_name") or "") or None,
            pet_name=str(profile.get("pet_name") or "") or None,
            pet_type=str(profile.get("pet_type") or "") or None,
            breed=str(profile.get("breed") or "") or None,
            age_years=float(profile["age_years"]) if isinstance(profile.get("age_years"), (int, float)) else None,
            weight_kg=float(profile["weight_kg"]) if isinstance(profile.get("weight_kg"), (int, float)) else None,
            suburb=str(profile.get("suburb") or "") or None,
            concerns=[str(item) for item in concerns if str(item).strip()],
        )

    @staticmethod
    def _safe_price_from(raw_value: object) -> int:
        try:
            parsed = int(raw_value)
        except (TypeError, ValueError):
            return 30
        return min(max(parsed, 1), 5000)
