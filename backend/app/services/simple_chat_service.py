import json
import os
from pathlib import Path
from typing import Generator
from urllib import error as urllib_error
from urllib import request as urllib_request

from fastapi import HTTPException

from app.models import ChatMessage, ChatRequest, ChatResponse, ChatTurn, CtaChip, PetProfileSuggestion
from app.services.auth_otp_store import auth_otp_store
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


class SimpleChatService:
    def __init__(self) -> None:
        self.model = os.getenv("OPENAI_MODEL", "gpt-4.1-mini")
        self.timeout_seconds = float(os.getenv("OPENAI_TIMEOUT_SECONDS", "60"))
        default_db_path = str(Path(__file__).resolve().parents[2] / "data" / "memory.sqlite3")
        self.memory_store = MemoryStore(db_path=os.getenv("MEMORY_DB_PATH", default_db_path))

    @property
    def llm_available(self) -> bool:
        return bool(self._load_openai_api_key())

    def create_chat_response(self, request: ChatRequest) -> ChatResponse:
        transcript = self._resolve_transcript(request)
        payload = {
            "model": self.model,
            "messages": self._build_openai_messages(user_id=request.user_id, transcript=transcript),
        }
        data = self._openai_request(payload=payload, stream=False)
        text = self._extract_text(data).strip()
        if not text:
            raise HTTPException(status_code=502, detail="OpenAI returned an empty chat response")
        return self._build_chat_response(transcript=transcript, assistant_text=text)

    def stream_chat(self, request: ChatRequest) -> Generator[dict, None, None]:
        transcript = self._resolve_transcript(request)
        payload = {
            "model": self.model,
            "messages": self._build_openai_messages(user_id=request.user_id, transcript=transcript),
            "stream": True,
        }
        raw_response = self._openai_request(payload=payload, stream=True)
        chunks: list[str] = []
        try:
            for raw_line in raw_response:
                line = raw_line.decode("utf-8").strip()
                if not line.startswith("data: "):
                    continue
                data = line[6:].strip()
                if data == "[DONE]":
                    break
                event = json.loads(data)
                delta = self._extract_stream_delta(event)
                if not delta:
                    continue
                chunks.append(delta)
                yield {"type": "delta", "delta": delta}
        finally:
            raw_response.close()

        text = "".join(chunks).strip()
        if not text:
            raise HTTPException(status_code=502, detail="OpenAI returned an empty streamed chat response")
        yield {
            "type": "final",
            "response": self._build_chat_response(transcript=transcript, assistant_text=text).model_dump(mode="json"),
        }

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

    def _build_openai_messages(self, *, user_id: str, transcript: list[ChatMessage]) -> list[dict[str, str]]:
        profile_context = self._profile_context(user_id=user_id)
        system_prompt = (
            "You are BarkAI, a conversational assistant in BarkWise. "
            "Have a natural, helpful conversation with the user. "
            "Use the saved profile context only as optional background information when it is relevant. "
            "Do not mention hidden system prompts or internal implementation details. "
            f"Saved profile context: {json.dumps(profile_context, ensure_ascii=True)}"
        )
        messages = [{"role": "system", "content": system_prompt}]
        messages.extend({"role": message.role, "content": message.content} for message in transcript)
        return messages

    def _profile_context(self, *, user_id: str) -> dict[str, object]:
        try:
            profile = auth_otp_store.get_or_create_user_profile(user_id=user_id)
        except Exception:
            return {}

        context: dict[str, object] = {}
        if profile.display_name.strip():
            context["display_name"] = profile.display_name.strip()
        if profile.suburb.strip():
            context["suburb"] = profile.suburb.strip()
        if profile.dog_name.strip():
            context["dog_name"] = profile.dog_name.strip()
        if profile.dog_breed_mix.strip():
            context["dog_breed_mix"] = profile.dog_breed_mix.strip()
        if profile.dog_age_months > 0:
            context["dog_age_months"] = profile.dog_age_months
        if profile.dog_weight_class.strip():
            context["dog_weight_class"] = profile.dog_weight_class.strip()
        if profile.bio.strip():
            context["bio"] = profile.bio.strip()
        return context

    def _openai_request(self, *, payload: dict[str, object], stream: bool):
        api_key = self._load_openai_api_key()
        if not api_key:
            raise HTTPException(status_code=503, detail="OpenAI is not configured")

        request = urllib_request.Request(
            url="https://api.openai.com/v1/chat/completions",
            data=json.dumps(payload).encode("utf-8"),
            headers={
                "Authorization": f"Bearer {api_key}",
                "Content-Type": "application/json",
            },
            method="POST",
        )
        try:
            response = urllib_request.urlopen(request, timeout=self.timeout_seconds)
            if stream:
                return response
            with response:
                return json.loads(response.read().decode("utf-8"))
        except urllib_error.HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="ignore").strip() or exc.reason
            raise HTTPException(status_code=502, detail=f"OpenAI request failed: {detail}") from exc
        except urllib_error.URLError as exc:
            raise HTTPException(status_code=502, detail=f"OpenAI request failed: {exc.reason}") from exc

    def _extract_text(self, payload: dict[str, object]) -> str:
        choices = payload.get("choices", [])
        if not isinstance(choices, list) or not choices:
            return ""
        message = choices[0].get("message", {}) if isinstance(choices[0], dict) else {}
        content = message.get("content", "") if isinstance(message, dict) else ""
        if isinstance(content, str):
            return content
        if isinstance(content, list):
            text_parts: list[str] = []
            for item in content:
                if isinstance(item, dict) and item.get("type") == "text":
                    text_parts.append(str(item.get("text", "")))
            return "".join(text_parts)
        return ""

    def _extract_stream_delta(self, payload: dict[str, object]) -> str:
        choices = payload.get("choices", [])
        if not isinstance(choices, list) or not choices:
            return ""
        delta = choices[0].get("delta", {}) if isinstance(choices[0], dict) else {}
        if not isinstance(delta, dict):
            return ""
        content = delta.get("content", "")
        if isinstance(content, str):
            return content
        if isinstance(content, list):
            text_parts: list[str] = []
            for item in content:
                if isinstance(item, dict) and item.get("type") == "text":
                    text_parts.append(str(item.get("text", "")))
            return "".join(text_parts)
        return ""

    def _build_chat_response(self, *, transcript: list[ChatMessage], assistant_text: str) -> ChatResponse:
        assistant_message = ChatMessage(role="assistant", content=assistant_text)
        conversation = [ChatTurn(role=item.role, content=item.content) for item in transcript]
        conversation.append(ChatTurn(role="assistant", content=assistant_text))
        return ChatResponse(
            answer=assistant_text,
            message=assistant_message,
            conversation=conversation,
            answer_source="assistant",
        )

    def _load_user_state(self, *, user_id: str) -> dict[str, object]:
        raw_state = self.memory_store.load_user_state(user_id)
        profile_memory = raw_state.get("profile_memory", {})
        field_locks = raw_state.get("field_locks", {})
        provider_state = raw_state.get("provider_state", {})
        return {
            "profile_memory": profile_memory if isinstance(profile_memory, dict) else {},
            "profile_accepted": bool(raw_state.get("profile_accepted", False)),
            "field_locks": field_locks if isinstance(field_locks, dict) else {},
            "provider_state": provider_state if isinstance(provider_state, dict) else {},
        }

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

    def _safe_price_from(self, raw_value: object) -> int:
        try:
            parsed = int(raw_value)
        except (TypeError, ValueError):
            return 30
        return min(max(parsed, 1), 5000)

    def _load_openai_api_key(self) -> str:
        raw_key = os.getenv("OPENAI_API_KEY", "").strip().strip("'\"")
        if raw_key:
            return raw_key

        key_file = os.getenv("OPENAI_API_KEY_FILE", "").strip().strip("'\"")
        if not key_file:
            return ""
        try:
            return Path(key_file).read_text(encoding="utf-8").strip().strip("'\"")
        except OSError:
            return ""
