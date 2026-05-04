from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import time
from typing import Generator, Iterable

from openai import APIConnectionError, APIStatusError, APITimeoutError, OpenAI, RateLimitError


def _clean_env_value(raw_value: str) -> str:
    return raw_value.strip().strip("'\"")


def extract_openai_api_key(raw_text: str) -> str:
    normalized_text = raw_text.replace("\\n", "\n")
    cleaned = _clean_env_value(normalized_text)
    first_line = _clean_env_value(cleaned.splitlines()[0]) if cleaned.splitlines() else ""
    if first_line.startswith("sk-"):
        return first_line

    for raw_line in normalized_text.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        if key.strip() == "OPENAI_API_KEY":
            return _clean_env_value(value)

    for raw_line in normalized_text.splitlines():
        line = _clean_env_value(raw_line)
        if line.startswith("sk-"):
            return line
    return ""


@dataclass
class LlmClientError(Exception):
    category: str
    detail: str

    def __str__(self) -> str:
        return self.detail


@dataclass
class StructuredChatReply:
    answer: str
    needs_clarification: bool
    safety_flags: list[str] = field(default_factory=list)
    confidence: str | None = None
    suggested_ctas: list[str] = field(default_factory=list)


class LlmClient:
    def __init__(
        self,
        *,
        model: str,
        timeout_seconds: float,
        max_retries: int,
        retry_backoff_seconds: float,
    ) -> None:
        self.model = model
        self.timeout_seconds = timeout_seconds
        self.max_retries = max(1, max_retries)
        self.retry_backoff_seconds = max(0.0, retry_backoff_seconds)
        self._client: OpenAI | None = None
        self._client_api_key: str = ""

    @property
    def configured(self) -> bool:
        return bool(self._load_openai_api_key())

    def _load_openai_api_key(self) -> str:
        raw_key = os.getenv("OPENAI_API_KEY", "")
        parsed_key = extract_openai_api_key(raw_key)
        if parsed_key:
            return parsed_key

        key_file = _clean_env_value(os.getenv("OPENAI_API_KEY_FILE", ""))
        if not key_file:
            return ""
        try:
            raw_text = Path(key_file).read_text(encoding="utf-8")
        except OSError:
            return ""
        return extract_openai_api_key(raw_text)

    def _get_client(self) -> OpenAI:
        api_key = self._load_openai_api_key()
        if not api_key:
            raise LlmClientError(category="llm_unavailable", detail="OpenAI is not configured")
        if self._client is None or api_key != self._client_api_key:
            self._client = OpenAI(api_key=api_key, timeout=self.timeout_seconds, max_retries=0)
            self._client_api_key = api_key
        return self._client

    def generate_text(self, *, messages: list[dict[str, str]], model: str | None = None) -> str:
        completion = self._create_completion(messages=messages, stream=False, model=model)
        text = self._extract_completion_text(completion)
        if not text.strip():
            raise LlmClientError(category="llm_unavailable", detail="OpenAI returned an empty chat response")
        return text.strip()

    def generate_json_object(self, *, messages: list[dict[str, str]], model: str | None = None) -> dict[str, object]:
        completion = self._create_completion(
            messages=messages,
            stream=False,
            model=model,
            response_format={"type": "json_object"},
        )
        text = self._extract_completion_text(completion)
        return self._parse_json_object(text)

    def stream_json_object(self, *, messages: list[dict[str, str]], model: str | None = None) -> dict[str, object]:
        raw_text = "".join(
            self.stream_text(
                messages=messages,
                model=model,
                response_format={"type": "json_object"},
            )
        )
        return self._parse_json_object(raw_text)

    def generate_structured_chat_reply(
        self,
        *,
        messages: list[dict[str, str]],
        model: str | None = None,
    ) -> StructuredChatReply:
        payload = self.generate_json_object(
            messages=self._structured_chat_messages(messages),
            model=model,
        )
        return self._validate_structured_chat_reply(payload)

    def stream_structured_chat_reply(
        self,
        *,
        messages: list[dict[str, str]],
        model: str | None = None,
    ) -> StructuredChatReply:
        payload = self.stream_json_object(
            messages=self._structured_chat_messages(messages),
            model=model,
        )
        return self._validate_structured_chat_reply(payload)

    def stream_text(
        self,
        *,
        messages: list[dict[str, str]],
        model: str | None = None,
        response_format: object | None = None,
    ) -> Generator[str, None, None]:
        stream = self._create_completion(messages=messages, stream=True, model=model, response_format=response_format)
        yielded = False
        try:
            for chunk in stream:
                delta = self._extract_stream_delta(chunk)
                if not delta:
                    continue
                yielded = True
                yield delta
        except LlmClientError:
            raise
        except Exception as exc:  # pragma: no cover - defensive fallback for SDK shape drift.
            category = "backend_unavailable" if yielded else "llm_unavailable"
            raise LlmClientError(category=category, detail=f"OpenAI stream failed: {exc}") from exc

    def run_synthetic_check(self) -> dict[str, object]:
        timestamp = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
        report = {
            "checked_at": timestamp,
            "config_loaded": self.configured,
            "provider_reachable": False,
            "non_stream_ok": False,
            "stream_ok": False,
            "last_error": None,
        }
        if not report["config_loaded"]:
            report["last_error"] = "OpenAI is not configured"
            return report

        probe_messages = [
            {"role": "system", "content": "Respond with exactly OK and nothing else."},
            {"role": "user", "content": "Health check"},
        ]
        try:
            text = self.generate_text(messages=probe_messages)
            report["provider_reachable"] = True
            report["non_stream_ok"] = self._synthetic_probe_passed(text)
        except LlmClientError as exc:
            report["last_error"] = exc.detail
            return report

        try:
            streamed = "".join(self.stream_text(messages=probe_messages)).strip()
            report["stream_ok"] = self._synthetic_probe_passed(streamed)
        except LlmClientError as exc:
            report["last_error"] = exc.detail
        return report

    @staticmethod
    def _synthetic_probe_passed(text: str) -> bool:
        normalized = text.strip().lower()
        if not normalized:
            return False
        collapsed = re.sub(r"[^a-z]+", " ", normalized).strip()
        if not collapsed:
            return False
        words = collapsed.split()
        head = words[:4]
        return any(word in {"ok", "okay"} for word in head)

    def _create_completion(
        self,
        *,
        messages: list[dict[str, str]],
        stream: bool,
        model: str | None,
        response_format: object | None = None,
    ):
        last_error: LlmClientError | None = None
        for attempt in range(1, self.max_retries + 1):
            try:
                client = self._get_client()
                request_kwargs = {
                    "model": model or self.model,
                    "messages": messages,
                    "stream": stream,
                }
                if response_format is not None:
                    request_kwargs["response_format"] = response_format
                return client.chat.completions.create(**request_kwargs)
            except LlmClientError as exc:
                last_error = exc
            except RateLimitError as exc:
                last_error = LlmClientError(category="llm_unavailable", detail=f"OpenAI request failed: {exc}")
            except APITimeoutError as exc:
                last_error = LlmClientError(category="backend_unavailable", detail=f"OpenAI request timed out: {exc}")
            except APIConnectionError as exc:
                last_error = LlmClientError(category="backend_unavailable", detail=f"OpenAI connection failed: {exc}")
            except APIStatusError as exc:
                detail = exc.body if isinstance(exc.body, str) else str(exc)
                category = "llm_unavailable" if exc.status_code in {400, 401, 403, 404, 408, 429} else "backend_unavailable"
                last_error = LlmClientError(category=category, detail=f"OpenAI request failed: {detail}")
            except Exception as exc:  # pragma: no cover - SDK fallback.
                last_error = LlmClientError(category="backend_unavailable", detail=f"OpenAI request failed: {exc}")
            if attempt < self.max_retries:
                time.sleep(self.retry_backoff_seconds * attempt)
                continue
            break
        raise last_error or LlmClientError(category="backend_unavailable", detail="OpenAI request failed")

    @staticmethod
    def _extract_completion_text(completion: object) -> str:
        choices = getattr(completion, "choices", None)
        if not choices:
            return ""
        choice = choices[0]
        message = getattr(choice, "message", None)
        content = getattr(message, "content", "") if message is not None else ""
        if isinstance(content, str):
            return content
        if isinstance(content, Iterable):
            text_parts: list[str] = []
            for item in content:
                maybe_text = getattr(item, "text", None)
                if maybe_text:
                    text_parts.append(str(maybe_text))
            return "".join(text_parts)
        return ""

    @staticmethod
    def _extract_stream_delta(chunk: object) -> str:
        choices = getattr(chunk, "choices", None)
        if not choices:
            return ""
        choice = choices[0]
        delta = getattr(choice, "delta", None)
        content = getattr(delta, "content", "") if delta is not None else ""
        if isinstance(content, str):
            return content
        if isinstance(content, Iterable):
            text_parts: list[str] = []
            for item in content:
                maybe_text = getattr(item, "text", None)
                if maybe_text:
                    text_parts.append(str(maybe_text))
            return "".join(text_parts)
        return ""

    @staticmethod
    def _structured_chat_messages(messages: list[dict[str, str]]) -> list[dict[str, str]]:
        instruction = (
            "Return a JSON object only. "
            "Use exactly these keys: answer, needs_clarification, safety_flags, confidence, suggested_ctas. "
            "Rules: answer must be a non-empty string; needs_clarification must be a boolean; "
            "safety_flags must be an array of short strings; confidence must be one of low, medium, high or null; "
            "suggested_ctas must be an array of short strings. "
            "Do not include markdown fences or any extra keys."
        )
        return [{"role": "system", "content": instruction}, *messages]

    @staticmethod
    def _parse_json_object(raw_text: str) -> dict[str, object]:
        text = raw_text.strip()
        if not text:
            raise LlmClientError(category="llm_unavailable", detail="Invalid structured LLM response")
        try:
            payload = json.loads(text)
        except json.JSONDecodeError as exc:
            raise LlmClientError(category="llm_unavailable", detail="Invalid structured LLM response") from exc
        if not isinstance(payload, dict):
            raise LlmClientError(category="llm_unavailable", detail="Invalid structured LLM response")
        return payload

    @staticmethod
    def _validate_structured_chat_reply(payload: dict[str, object]) -> StructuredChatReply:
        allowed_keys = {
            "answer",
            "needs_clarification",
            "safety_flags",
            "confidence",
            "suggested_ctas",
        }
        if any(key not in allowed_keys for key in payload):
            raise LlmClientError(category="llm_unavailable", detail="Invalid structured LLM response")

        answer = payload.get("answer")
        needs_clarification = payload.get("needs_clarification")
        safety_flags = payload.get("safety_flags")
        confidence = payload.get("confidence")
        suggested_ctas = payload.get("suggested_ctas", [])

        if not isinstance(answer, str) or not answer.strip():
            raise LlmClientError(category="llm_unavailable", detail="Invalid structured LLM response")
        if not isinstance(needs_clarification, bool):
            raise LlmClientError(category="llm_unavailable", detail="Invalid structured LLM response")
        if not isinstance(safety_flags, list) or any(not isinstance(item, str) or not item.strip() for item in safety_flags):
            raise LlmClientError(category="llm_unavailable", detail="Invalid structured LLM response")
        if confidence is not None and confidence not in {"low", "medium", "high"}:
            raise LlmClientError(category="llm_unavailable", detail="Invalid structured LLM response")
        if not isinstance(suggested_ctas, list) or any(not isinstance(item, str) or not item.strip() for item in suggested_ctas):
            raise LlmClientError(category="llm_unavailable", detail="Invalid structured LLM response")

        return StructuredChatReply(
            answer=answer.strip(),
            needs_clarification=needs_clarification,
            safety_flags=[item.strip() for item in safety_flags],
            confidence=confidence,
            suggested_ctas=[item.strip() for item in suggested_ctas],
        )
