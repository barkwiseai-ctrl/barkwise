from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from enum import Enum
import re
from typing import Any

from app.models import ChatMessage


class BarkAiRoute(str, Enum):
    CHAT = "chat"
    TOOL_READ = "tool_read"
    TOOL_ACTION_CONFIRMATION = "tool_action_confirmation"
    TOOL_ACTION_EXECUTE = "tool_action_execute"


class BarkAiFailureCategory(str, Enum):
    BACKEND_UNAVAILABLE = "backend_unavailable"
    LLM_UNAVAILABLE = "llm_unavailable"
    TOOL_FAILED = "tool_failed"
    CONFIRMATION_REQUIRED = "confirmation_required"


@dataclass
class PendingConfirmation:
    action: str
    prompt: str
    params: dict[str, Any] = field(default_factory=dict)
    expires_at: str | None = None

    @classmethod
    def from_dict(cls, payload: dict[str, Any]) -> "PendingConfirmation | None":
        action = str(payload.get("action") or "").strip()
        prompt = str(payload.get("prompt") or "").strip()
        if not action or not prompt:
            return None
        return cls(
            action=action,
            prompt=prompt,
            params=dict(payload.get("params") or {}),
            expires_at=str(payload.get("expires_at") or "") or None,
        )

    def to_dict(self) -> dict[str, Any]:
        return {
            "action": self.action,
            "prompt": self.prompt,
            "params": dict(self.params),
            "expires_at": self.expires_at,
        }


@dataclass
class BarkAiDecision:
    route: BarkAiRoute
    tool_name: str | None = None
    params: dict[str, Any] = field(default_factory=dict)
    policy_tags: list[str] = field(default_factory=list)
    pending_confirmation: PendingConfirmation | None = None


@dataclass
class BarkAiResult:
    answer: str
    answer_source: str
    status: str = "ok"
    cta_chips: list[Any] = field(default_factory=list)
    answer_badges: list[str] = field(default_factory=list)
    citations: list[Any] = field(default_factory=list)
    a2ui_messages: list[dict[str, Any]] = field(default_factory=list)
    error_type: str | None = None
    pending_confirmation: PendingConfirmation | None = None


class BarkAiRouter:
    _GROUP_JOIN_RE = re.compile(r"\bjoin group (?P<group_id>[A-Za-z0-9_:-]+)\b", re.IGNORECASE)
    _EVENT_RSVP_RE = re.compile(r"\brsvp event (?P<event_id>[A-Za-z0-9_:-]+)\b", re.IGNORECASE)
    _SEND_MESSAGE_RE = re.compile(
        r"\bsend message to (?P<recipient>[A-Za-z0-9_:-]+)\s*:\s*(?P<body>.+)",
        re.IGNORECASE,
    )
    _HOLD_RE = re.compile(
        r"\bhold booking provider (?P<provider_id>[A-Za-z0-9_:-]+) on (?P<date>\d{4}-\d{2}-\d{2}) at (?P<time>\d{2}:\d{2})\b",
        re.IGNORECASE,
    )
    _AVAILABILITY_RE = re.compile(
        r"\bavailability for provider (?P<provider_id>[A-Za-z0-9_:-]+) on (?P<date>\d{4}-\d{2}-\d{2})\b",
        re.IGNORECASE,
    )
    _CONFIRM_WORDS = {"yes", "yes please", "yes confirm", "confirm", "go ahead", "do it", "please do", "sounds good"}
    _CANCEL_WORDS = {"cancel", "never mind", "stop", "don't do that", "do not do that"}

    def route(
        self,
        *,
        transcript: list[ChatMessage],
        pending_confirmation: PendingConfirmation | None,
    ) -> BarkAiDecision:
        latest_user_message = next((message.content for message in reversed(transcript) if message.role == "user"), "").strip()
        if not latest_user_message:
            return BarkAiDecision(route=BarkAiRoute.CHAT)

        policy_tags = self._detect_policy_tags(latest_user_message)
        explicit_action = self._detect_explicit_action(latest_user_message, policy_tags=policy_tags)
        if explicit_action is not None:
            return explicit_action

        discovery = self._detect_discovery_intent(latest_user_message, policy_tags=policy_tags)
        if discovery is not None:
            return discovery

        confirmation = self._detect_confirmation(latest_user_message, pending_confirmation=pending_confirmation)
        if confirmation is not None:
            return confirmation

        return BarkAiDecision(route=BarkAiRoute.CHAT, policy_tags=policy_tags)

    def _detect_confirmation(
        self,
        message: str,
        *,
        pending_confirmation: PendingConfirmation | None,
    ) -> BarkAiDecision | None:
        if pending_confirmation is None:
            return None
        if pending_confirmation.expires_at:
            try:
                expires_at = datetime.fromisoformat(pending_confirmation.expires_at.replace("Z", "+00:00"))
                if datetime.now(timezone.utc) > expires_at:
                    return BarkAiDecision(
                        route=BarkAiRoute.TOOL_ACTION_CONFIRMATION,
                        tool_name="cancel_confirmation",
                        params={"reason": "expired"},
                    )
            except ValueError:
                pass

        normalized = re.sub(r"[^a-z\s]", "", message.strip().lower())
        normalized = re.sub(r"\s+", " ", normalized).strip()
        if normalized in self._CONFIRM_WORDS:
            return BarkAiDecision(
                route=BarkAiRoute.TOOL_ACTION_EXECUTE,
                tool_name=pending_confirmation.action,
                params=dict(pending_confirmation.params),
                pending_confirmation=pending_confirmation,
            )
        if normalized in self._CANCEL_WORDS:
            return BarkAiDecision(
                route=BarkAiRoute.TOOL_ACTION_CONFIRMATION,
                tool_name="cancel_confirmation",
                params={"reason": "cancelled"},
            )
        return None

    def _detect_explicit_action(self, message: str, *, policy_tags: list[str]) -> BarkAiDecision | None:
        normalized = message.lower().strip().rstrip(".")
        if "provider mode" in normalized and any(keyword in normalized for keyword in ("turn on", "switch on", "enable", "activate")):
            return BarkAiDecision(
                route=BarkAiRoute.TOOL_ACTION_CONFIRMATION,
                tool_name="provider_mode_enable",
                policy_tags=policy_tags,
            )

        join_match = self._GROUP_JOIN_RE.search(message)
        if join_match:
            return BarkAiDecision(
                route=BarkAiRoute.TOOL_ACTION_CONFIRMATION,
                tool_name="join_group",
                params={"group_id": join_match.group("group_id")},
                policy_tags=policy_tags,
            )

        event_match = self._EVENT_RSVP_RE.search(message)
        if event_match:
            return BarkAiDecision(
                route=BarkAiRoute.TOOL_ACTION_CONFIRMATION,
                tool_name="rsvp_event",
                params={"event_id": event_match.group("event_id")},
                policy_tags=policy_tags,
            )

        message_match = self._SEND_MESSAGE_RE.search(message)
        if message_match:
            return BarkAiDecision(
                route=BarkAiRoute.TOOL_ACTION_CONFIRMATION,
                tool_name="send_message",
                params={
                    "recipient_user_id": message_match.group("recipient"),
                    "body": message_match.group("body").strip(),
                },
                policy_tags=policy_tags,
            )

        hold_match = self._HOLD_RE.search(message)
        if hold_match:
            return BarkAiDecision(
                route=BarkAiRoute.TOOL_ACTION_CONFIRMATION,
                tool_name="create_booking_hold",
                params={
                    "provider_id": hold_match.group("provider_id"),
                    "date": hold_match.group("date"),
                    "time_slot": hold_match.group("time"),
                },
                policy_tags=policy_tags,
            )

        availability_match = self._AVAILABILITY_RE.search(message)
        if availability_match:
            return BarkAiDecision(
                route=BarkAiRoute.TOOL_READ,
                tool_name="provider_availability",
                params={
                    "provider_id": availability_match.group("provider_id"),
                    "date": availability_match.group("date"),
                },
                policy_tags=policy_tags,
            )
        return None

    def _detect_discovery_intent(self, message: str, *, policy_tags: list[str]) -> BarkAiDecision | None:
        normalized = message.lower()
        group_signal = any(keyword in normalized for keyword in ("group", "groups", "community", "communities"))
        dog_park_signal = "dog park" in normalized or "dogpark" in normalized
        find_signal = any(keyword in normalized for keyword in ("know any", "find", "recommend", "suggest", "looking for", "new to"))
        if group_signal and (dog_park_signal or find_signal):
            return BarkAiDecision(route=BarkAiRoute.TOOL_READ, tool_name="find_groups", policy_tags=policy_tags)

        event_signal = any(keyword in normalized for keyword in ("event", "events", "meetup", "meetups", "pack walk"))
        if event_signal and any(keyword in normalized for keyword in ("find", "any", "near", "this week", "happening", "coming up")):
            return BarkAiDecision(route=BarkAiRoute.TOOL_READ, tool_name="find_events", policy_tags=policy_tags)

        service_signal = any(
            keyword in normalized
            for keyword in ("groomer", "groomers", "grooming", "dog walker", "dog walkers", "walker", "walkers")
        )
        if service_signal and any(keyword in normalized for keyword in ("find", "recommend", "near", "available", "know any", "looking for")):
            return BarkAiDecision(route=BarkAiRoute.TOOL_READ, tool_name="find_providers", policy_tags=policy_tags)

        if "booking" in normalized and any(keyword in normalized for keyword in ("my", "show", "upcoming", "what", "list")):
            return BarkAiDecision(route=BarkAiRoute.TOOL_READ, tool_name="list_bookings", policy_tags=policy_tags)

        thread_signal = "message" in normalized or "messages" in normalized or "inbox" in normalized
        if thread_signal and any(keyword in normalized for keyword in ("my", "unread", "show", "from", "do i have", "list")):
            return BarkAiDecision(route=BarkAiRoute.TOOL_READ, tool_name="list_messages", policy_tags=policy_tags)
        return None

    def _detect_policy_tags(self, message: str) -> list[str]:
        normalized = message.lower()
        tags: list[str] = []
        if "crate" in normalized or "crated" in normalized:
            tags.append("crate")
        if any(keyword in normalized for keyword in ("alpha roll", "dominance", "dominant", "show him who's boss", "hit my dog", "punish")):
            tags.append("dominance")
        if any(keyword in normalized for keyword in ("vomit", "vomiting", "blood", "can't breathe", "collapsed", "collapse", "seizure", "toxin")):
            tags.append("medical_red_flag")
        if any(keyword in normalized for keyword in ("reactive", "reactivity", "aggressive", "aggression", "resource guard", "guarding", "bite")):
            tags.append("reactivity")
        if "rescue" in normalized or "decompression" in normalized:
            tags.append("rescue")
        return tags

    @staticmethod
    def confirmation_expiry_iso(minutes: int = 10) -> str:
        return (datetime.now(timezone.utc) + timedelta(minutes=minutes)).isoformat().replace("+00:00", "Z")
