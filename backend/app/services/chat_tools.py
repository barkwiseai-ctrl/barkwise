from __future__ import annotations

from datetime import datetime, timezone
import re
from typing import Any

from app.data import community_events, event_rsvps, group_memberships, groups
from app.models import BookingHoldRequest, ChatMessage, CtaChip
from app.services.auth_otp_store import auth_otp_store
from app.services.chat_router import BarkAiResult, PendingConfirmation
from app.services.message_store import message_store
from app.services.service_store import service_store
import app.routers.community as community_router


def _utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def _thread_id_from_users(user_a: str, user_b: str) -> str:
    first, second = sorted([user_a.strip(), user_b.strip()])
    return f"dm_{first}_{second}"


_TOOL_ID_RE = re.compile(r"^[A-Za-z0-9_:-]{1,80}$")
_DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
_TIME_SLOT_RE = re.compile(r"^\d{2}:\d{2}$")


class BarkAiTools:
    def __init__(self, *, mutating_actions_enabled: bool) -> None:
        self.mutating_actions_enabled = mutating_actions_enabled

    def execute(
        self,
        *,
        user_id: str,
        transcript: list[ChatMessage],
        tool_name: str,
        params: dict[str, Any],
        profile_context: dict[str, object],
    ) -> BarkAiResult:
        if tool_name == "find_groups":
            return self._find_groups(user_id=user_id, transcript=transcript, latest_user_message=self._latest_user_message(transcript), profile_context=profile_context)
        if tool_name == "find_events":
            return self._find_events(user_id=user_id, transcript=transcript, latest_user_message=self._latest_user_message(transcript), profile_context=profile_context)
        if tool_name == "find_providers":
            return self._find_providers(user_id=user_id, transcript=transcript, latest_user_message=self._latest_user_message(transcript), profile_context=profile_context)
        if tool_name == "provider_availability":
            return self._provider_availability(params=params)
        if tool_name == "list_bookings":
            return self._list_bookings(user_id=user_id)
        if tool_name == "list_messages":
            return self._list_messages(user_id=user_id)
        if tool_name == "provider_mode_enable":
            return self._confirm_provider_mode()
        if tool_name == "join_group":
            return self._confirm_join_group(group_id=str(params.get("group_id") or ""))
        if tool_name == "rsvp_event":
            return self._confirm_rsvp_event(event_id=str(params.get("event_id") or ""))
        if tool_name == "send_message":
            return self._confirm_send_message(
                recipient_user_id=str(params.get("recipient_user_id") or ""),
                body=str(params.get("body") or ""),
            )
        if tool_name == "create_booking_hold":
            return self._confirm_booking_hold(
                provider_id=str(params.get("provider_id") or ""),
                date=str(params.get("date") or ""),
                time_slot=str(params.get("time_slot") or ""),
            )
        if tool_name == "cancel_confirmation":
            return BarkAiResult(
                answer="Okay, I have cancelled that BarkAI action.",
                answer_source="tool_confirmation_cancelled",
            )
        return BarkAiResult(
            answer="I could not complete that BarkAI tool action.",
            answer_source="tool_failed",
            status="error",
            error_type="tool_failed",
        )

    def execute_confirmed_action(self, *, user_id: str, action: str, params: dict[str, Any]) -> BarkAiResult:
        if not self.mutating_actions_enabled:
            return BarkAiResult(
                answer="BarkAI actions are currently turned off, so I can suggest next steps but cannot complete that action.",
                answer_source="tool_action_disabled",
                status="error",
                error_type="tool_failed",
            )
        if action == "provider_mode_enable":
            profile = auth_otp_store.get_or_create_user_profile(user_id=user_id)
            if profile.service_provider_mode:
                answer = "Provider mode is already on for your account."
            else:
                auth_otp_store.set_service_provider_mode(user_id=user_id, enabled=True)
                answer = "Provider mode is now on. You can open Listings to create or manage your service profile."
            return BarkAiResult(
                answer=answer,
                answer_source="tool_provider_mode",
                cta_chips=[CtaChip(label="Open Listings", action="open_services")],
            )
        if action == "join_group":
            return self._execute_join_group(user_id=user_id, group_id=str(params.get("group_id") or ""))
        if action == "rsvp_event":
            return self._execute_rsvp_event(user_id=user_id, event_id=str(params.get("event_id") or ""))
        if action == "send_message":
            return self._execute_send_message(
                user_id=user_id,
                recipient_user_id=str(params.get("recipient_user_id") or ""),
                body=str(params.get("body") or ""),
            )
        if action == "create_booking_hold":
            return self._execute_booking_hold(
                user_id=user_id,
                provider_id=str(params.get("provider_id") or ""),
                date=str(params.get("date") or ""),
                time_slot=str(params.get("time_slot") or ""),
            )
        return BarkAiResult(
            answer="I could not complete that confirmed BarkAI action.",
            answer_source="tool_failed",
            status="error",
            error_type="tool_failed",
        )

    @staticmethod
    def _latest_user_message(transcript: list[ChatMessage]) -> str:
        return next((message.content for message in reversed(transcript) if message.role == "user"), "").strip()

    def _find_groups(
        self,
        *,
        user_id: str,
        transcript: list[ChatMessage],
        latest_user_message: str,
        profile_context: dict[str, object],
    ) -> BarkAiResult:
        suburb = self._resolve_suburb(latest_user_message=latest_user_message, profile_context=profile_context)
        if not suburb:
            return BarkAiResult(
                answer="I can help with that. Tell me which suburb you mean and I will look for nearby dog park groups in BarkWise.",
                answer_source="tool_group_search",
                cta_chips=[CtaChip(label="Open Community", action="open_community")],
            )

        wants_dog_park = "dog park" in latest_user_message.lower() or "dogpark" in latest_user_message.lower()
        matching_groups = self._find_groups_for_suburb(user_id=user_id, suburb=suburb, dog_park_only=wants_dog_park)
        fallback_used = False
        if not matching_groups and wants_dog_park:
            matching_groups = self._find_groups_for_suburb(user_id=user_id, suburb=suburb, dog_park_only=False)
            fallback_used = bool(matching_groups)
        if not matching_groups:
            return BarkAiResult(
                answer=(
                    f"I could not find a dog park group in {suburb} yet. "
                    "You can still open Community and create a local group if you want to start one."
                ),
                answer_source="tool_group_search",
                cta_chips=[CtaChip(label="Open Community", action="open_community")],
            )

        top_groups = matching_groups[:3]
        group_lines = [
            f"{group['name']} ({group['member_count']} members{' · official' if group['official'] else ''})"
            for group in top_groups
        ]
        if fallback_used:
            answer = (
                f"I could not find a dog-park-specific group in {suburb}, but these BarkWise community groups look relevant: "
                + "; ".join(group_lines)
                + "."
            )
        else:
            answer = f"I found these BarkWise groups in {suburb}: " + "; ".join(group_lines) + "."
        ctas = [CtaChip(label="Open Community", action="open_community")]
        for group in top_groups:
            if str(group["membership_status"]) == "none":
                ctas.append(
                    CtaChip(
                        label=f"Ask BarkAI to join {group['short_label']}",
                        action="send_bark_message",
                        payload={"message": f"join group {group['id']}"},
                    )
                )
        return BarkAiResult(answer=answer, answer_source="tool_group_search", cta_chips=ctas)

    def _find_events(
        self,
        *,
        user_id: str,
        transcript: list[ChatMessage],
        latest_user_message: str,
        profile_context: dict[str, object],
    ) -> BarkAiResult:
        suburb = self._resolve_suburb(latest_user_message=latest_user_message, profile_context=profile_context)
        if not suburb:
            return BarkAiResult(
                answer="Tell me the suburb you care about and I will look for nearby BarkWise meetups and events.",
                answer_source="tool_event_search",
                cta_chips=[CtaChip(label="Open Community", action="open_community")],
            )

        normalized_suburb = suburb.lower()
        relevant_events: list[dict[str, Any]] = []
        event_status_by_id = {
            rsvp.event_id: rsvp.status
            for rsvp in event_rsvps
            if rsvp.user_id == user_id
        }
        for event in community_events:
            if event.suburb.lower() != normalized_suburb or event.status != "approved":
                continue
            relevant_events.append(
                {
                    "id": event.id,
                    "date": event.date,
                    "title": event.title,
                    "attendee_count": event.attendee_count,
                    "location": event.location_name or "Location in app",
                    "rsvp_status": event_status_by_id.get(event.id, "none"),
                }
            )
        relevant_events.sort(key=lambda item: str(item["date"]))
        if not relevant_events:
            return BarkAiResult(
                answer=f"I could not find upcoming BarkWise events in {suburb} yet.",
                answer_source="tool_event_search",
                cta_chips=[CtaChip(label="Open Community", action="open_community")],
            )

        top_events = relevant_events[:3]
        answer = (
            f"Here are upcoming BarkWise events in {suburb}: "
            + "; ".join(
                f"{event['title']} on {str(event['date'])[:10]} at {event['location']} ({event['attendee_count']} attending)"
                for event in top_events
            )
            + "."
        )
        ctas = [CtaChip(label="Open Community", action="open_community")]
        for event in top_events:
            if event["rsvp_status"] == "none":
                ctas.append(
                    CtaChip(
                        label=f"Ask BarkAI to RSVP {str(event['title'])[:18]}",
                        action="send_bark_message",
                        payload={"message": f"rsvp event {event['id']}"},
                    )
                )
        return BarkAiResult(answer=answer, answer_source="tool_event_search", cta_chips=ctas)

    def _find_providers(
        self,
        *,
        user_id: str,
        transcript: list[ChatMessage],
        latest_user_message: str,
        profile_context: dict[str, object],
    ) -> BarkAiResult:
        normalized = latest_user_message.lower()
        category = None
        if any(keyword in normalized for keyword in ("groomer", "groomers", "grooming")):
            category = "grooming"
        elif any(keyword in normalized for keyword in ("dog walker", "dog walkers", "walker", "walkers")):
            category = "dog_walking"

        suburb = self._resolve_suburb(latest_user_message=latest_user_message, profile_context=profile_context)
        if not suburb and ("near me" in normalized or "nearby" in normalized):
            return BarkAiResult(
                answer="Tell me the suburb you want and I will look for relevant BarkWise providers there.",
                answer_source="tool_provider_search",
                cta_chips=[CtaChip(label="Open Listings", action="open_services", payload={"category": category} if category else {})],
            )

        providers = service_store.list_providers(category=category, suburb=suburb, user_id=user_id, limit=3)
        label = "providers" if category is None else ("groomers" if category == "grooming" else "dog walkers")
        location_text = f" in {suburb}" if suburb else ""
        if not providers:
            answer = f"I could not find any {label}{location_text} in BarkWise yet."
        else:
            answer = (
                f"I found these BarkWise {label}{location_text}: "
                + "; ".join(
                    f"{provider.name} (rating {provider.rating:.1f}, from ${provider.price_from}, id {provider.id})"
                    for provider in providers
                )
                + "."
            )
        cta_payload = {"category": category} if category else {}
        ctas = [CtaChip(label="Open Listings", action="open_services", payload=cta_payload)]
        for provider in providers:
            ctas.append(
                CtaChip(
                    label=f"Ask availability for {provider.name[:16]}",
                    action="send_bark_message",
                    payload={"message": f"availability for provider {provider.id} on {datetime.now(timezone.utc).date().isoformat()}"},
                )
            )
        return BarkAiResult(answer=answer, answer_source="tool_provider_search", cta_chips=ctas)

    def _provider_availability(self, *, params: dict[str, Any]) -> BarkAiResult:
        provider_id = str(params.get("provider_id") or "")
        date = str(params.get("date") or "")
        if not self._is_valid_tool_id(provider_id) or not self._is_iso_date(date):
            return self._tool_error("I need a valid provider ID and YYYY-MM-DD date before I can check availability.")
        slots = service_store.get_available_slots(provider_id=provider_id, slot_date=date)
        available = [slot.time_slot for slot in slots if slot.available][:5]
        if not available:
            answer = f"I could not find open BarkWise slots for {provider_id} on {date}."
        else:
            answer = f"Available BarkWise slots for {provider_id} on {date}: " + ", ".join(available) + "."
        ctas = []
        if available:
            ctas.append(
                CtaChip(
                    label=f"Ask BarkAI to hold {available[0]}",
                    action="send_bark_message",
                    payload={"message": f"hold booking provider {provider_id} on {date} at {available[0]}"},
                )
            )
        ctas.append(CtaChip(label="Open Listings", action="open_services"))
        return BarkAiResult(answer=answer, answer_source="tool_provider_availability", cta_chips=ctas)

    def _list_bookings(self, *, user_id: str) -> BarkAiResult:
        bookings = service_store.list_bookings(user_id=user_id, role="owner")[:3]
        if not bookings:
            answer = "You do not have any owner bookings in BarkWise right now."
        else:
            answer = (
                "Your upcoming BarkWise bookings: "
                + "; ".join(
                    f"{booking.pet_name} with {booking.provider_id} on {booking.date} at {booking.time_slot} ({booking.status})"
                    for booking in bookings
                )
                + "."
            )
        return BarkAiResult(
            answer=answer,
            answer_source="tool_booking_list",
            cta_chips=[CtaChip(label="Open Listings", action="open_services")],
        )

    def _list_messages(self, *, user_id: str) -> BarkAiResult:
        threads = message_store.list_threads(user_id=user_id, limit=3)
        if not threads:
            answer = "You do not have any BarkWise message threads yet."
        else:
            answer = (
                "Your recent BarkWise message threads: "
                + "; ".join(
                    f"{thread.participant_user_id} ({thread.unread_count} unread)"
                    if thread.unread_count
                    else f"{thread.participant_user_id}"
                    for thread in threads
                )
                + "."
            )
        return BarkAiResult(
            answer=answer,
            answer_source="tool_messages_list",
            cta_chips=[CtaChip(label="Open Messages", action="open_messages")],
        )

    def _confirm_provider_mode(self) -> BarkAiResult:
        pending = PendingConfirmation(
            action="provider_mode_enable",
            prompt="I can turn on provider mode for your account. Do you want me to go ahead?",
            params={},
            expires_at=None,
        )
        return self._confirmation_result(pending, label="Confirm Provider Mode")

    def _confirm_join_group(self, *, group_id: str) -> BarkAiResult:
        if not self._is_valid_tool_id(group_id):
            return self._tool_error("I need a valid BarkWise group ID before I can join a group.")
        group = next((item for item in groups if item.id == group_id), None)
        if group is None:
            return self._tool_error("I could not find that BarkWise group.")
        pending = PendingConfirmation(
            action="join_group",
            prompt=f"I can request to join {group.name}. Do you want me to do that?",
            params={"group_id": group_id},
            expires_at=None,
        )
        return self._confirmation_result(pending, label=f"Confirm Join {group.name[:18]}")

    def _confirm_rsvp_event(self, *, event_id: str) -> BarkAiResult:
        if not self._is_valid_tool_id(event_id):
            return self._tool_error("I need a valid BarkWise event ID before I can RSVP.")
        event = next((item for item in community_events if item.id == event_id), None)
        if event is None:
            return self._tool_error("I could not find that BarkWise event.")
        pending = PendingConfirmation(
            action="rsvp_event",
            prompt=f"I can RSVP you as attending for {event.title}. Do you want me to go ahead?",
            params={"event_id": event_id},
            expires_at=None,
        )
        return self._confirmation_result(pending, label=f"Confirm RSVP {event.title[:18]}")

    def _confirm_send_message(self, *, recipient_user_id: str, body: str) -> BarkAiResult:
        clean_body = self._clean_message_body(body)
        if not self._is_valid_tool_id(recipient_user_id) or clean_body is None:
            return self._tool_error("I need a valid recipient ID and a message body under 500 characters before I can send that.")
        pending = PendingConfirmation(
            action="send_message",
            prompt=f"I can send this BarkWise message to {recipient_user_id}: “{clean_body}”. Do you want me to send it?",
            params={"recipient_user_id": recipient_user_id, "body": clean_body},
            expires_at=None,
        )
        return self._confirmation_result(pending, label="Confirm Send Message")

    def _confirm_booking_hold(self, *, provider_id: str, date: str, time_slot: str) -> BarkAiResult:
        if not self._is_valid_tool_id(provider_id) or not self._is_iso_date(date) or not self._is_valid_time_slot(time_slot):
            return self._tool_error("I need a valid provider ID, YYYY-MM-DD date, and HH:MM time to create a BarkWise booking hold.")
        pending = PendingConfirmation(
            action="create_booking_hold",
            prompt=f"I can place a temporary hold for {provider_id} on {date} at {time_slot}. Do you want me to do that?",
            params={"provider_id": provider_id, "date": date, "time_slot": time_slot},
            expires_at=None,
        )
        return self._confirmation_result(pending, label="Confirm Booking Hold")

    def _execute_join_group(self, *, user_id: str, group_id: str) -> BarkAiResult:
        if not self._is_valid_tool_id(group_id):
            return self._tool_error("I need a valid BarkWise group ID before I can join a group.")
        group = next((item for item in groups if item.id == group_id), None)
        if group is None:
            return self._tool_error("I could not find that BarkWise group.")
        existing = next((item for item in group_memberships if item.group_id == group_id and item.user_id == user_id), None)
        if existing:
            status = existing.status
        else:
            status = "member" if group.official else "pending"
            group_memberships.append(community_router.GroupJoinRecord(group_id=group_id, user_id=user_id, status=status))
            if status == "member":
                group.member_count += 1
        community_router._persist_community_state()
        answer = (
            f"You are now a member of {group.name}."
            if status == "member"
            else f"I submitted your join request for {group.name}. The group owner will need to approve it."
        )
        return BarkAiResult(
            answer=answer,
            answer_source="tool_join_group",
            cta_chips=[CtaChip(label="Open Community", action="open_community")],
        )

    def _execute_rsvp_event(self, *, user_id: str, event_id: str) -> BarkAiResult:
        if not self._is_valid_tool_id(event_id):
            return self._tool_error("I need a valid BarkWise event ID before I can RSVP.")
        event = next((item for item in community_events if item.id == event_id), None)
        if event is None:
            return self._tool_error("I could not find that BarkWise event.")
        existing = next((item for item in event_rsvps if item.event_id == event_id and item.user_id == user_id), None)
        if existing is None:
            event_rsvps.append(community_router.EventRsvpRecord(event_id=event_id, user_id=user_id, status="attending"))
            event.attendee_count += 1
        community_router._persist_community_state()
        return BarkAiResult(
            answer=f"Done. You are marked as attending for {event.title}.",
            answer_source="tool_rsvp_event",
            cta_chips=[CtaChip(label="Open Community", action="open_community")],
        )

    def _execute_send_message(self, *, user_id: str, recipient_user_id: str, body: str) -> BarkAiResult:
        clean_body = self._clean_message_body(body)
        if not self._is_valid_tool_id(recipient_user_id) or clean_body is None:
            return self._tool_error("I need a valid recipient ID and a message body under 500 characters before I can send that.")
        thread_id = _thread_id_from_users(user_id, recipient_user_id)
        message_store.send_message(
            sender_user_id=user_id,
            recipient_user_id=recipient_user_id,
            body=clean_body,
            created_at=_utc_now_iso(),
            thread_id=thread_id,
        )
        return BarkAiResult(
            answer=f"Done. I sent that BarkWise message to {recipient_user_id}.",
            answer_source="tool_send_message",
            cta_chips=[CtaChip(label="Open Messages", action="open_messages")],
        )

    def _execute_booking_hold(self, *, user_id: str, provider_id: str, date: str, time_slot: str) -> BarkAiResult:
        if not self._is_valid_tool_id(provider_id) or not self._is_iso_date(date) or not self._is_valid_time_slot(time_slot):
            return self._tool_error("I need a valid provider ID, YYYY-MM-DD date, and HH:MM time to create a BarkWise booking hold.")
        hold = service_store.create_booking_hold(
            BookingHoldRequest(
                user_id=user_id,
                provider_id=provider_id,
                date=date,
                time_slot=time_slot,
            )
        )
        return BarkAiResult(
            answer=f"Done. I placed a temporary BarkWise hold for {provider_id} on {hold.date} at {hold.time_slot}. It expires at {hold.expires_at}.",
            answer_source="tool_booking_hold",
            cta_chips=[CtaChip(label="Open Listings", action="open_services")],
        )

    def _confirmation_result(self, pending: PendingConfirmation, *, label: str) -> BarkAiResult:
        confirmation_token = pending.ensure_confirmation_token()
        prompt = f'{pending.prompt} Reply "Confirm {confirmation_token}" to continue.'
        return BarkAiResult(
            answer=prompt,
            answer_source="tool_confirmation",
            status="needs_confirmation",
            error_type="confirmation_required",
            pending_confirmation=pending,
            cta_chips=[
                CtaChip(label=label, action="send_bark_message", payload={"message": f"Confirm {confirmation_token}"}),
                CtaChip(label="Cancel", action="send_bark_message", payload={"message": "Cancel"}),
            ],
        )

    @staticmethod
    def _tool_error(message: str) -> BarkAiResult:
        return BarkAiResult(
            answer=message,
            answer_source="tool_failed",
            status="error",
            error_type="tool_failed",
        )

    @staticmethod
    def _is_valid_tool_id(value: str) -> bool:
        return bool(_TOOL_ID_RE.fullmatch(value.strip()))

    @staticmethod
    def _is_iso_date(value: str) -> bool:
        cleaned = value.strip()
        if not _DATE_RE.fullmatch(cleaned):
            return False
        try:
            datetime.strptime(cleaned, "%Y-%m-%d")
            return True
        except ValueError:
            return False

    @staticmethod
    def _is_valid_time_slot(value: str) -> bool:
        if not _TIME_SLOT_RE.fullmatch(value.strip()):
            return False
        hour, minute = (int(part) for part in value.split(":", maxsplit=1))
        return 0 <= hour <= 23 and 0 <= minute <= 59

    @staticmethod
    def _clean_message_body(value: str) -> str | None:
        body = re.sub(r"\s+", " ", value).strip()
        if not body or len(body) > 500:
            return None
        return body

    @staticmethod
    def _resolve_suburb(*, latest_user_message: str, profile_context: dict[str, object]) -> str | None:
        normalized_message = latest_user_message.lower()
        known_suburbs = sorted({group.suburb.strip() for group in groups if group.suburb.strip()}, key=len, reverse=True)
        for suburb in known_suburbs:
            if suburb.lower() in normalized_message:
                return suburb
        profile_suburb = str(profile_context.get("suburb") or "").strip()
        if profile_suburb:
            return profile_suburb
        return None

    @staticmethod
    def _find_groups_for_suburb(*, user_id: str, suburb: str, dog_park_only: bool) -> list[dict[str, object]]:
        membership_by_group = {
            record.group_id: record.status
            for record in group_memberships
            if record.user_id == user_id
        }
        matched: list[dict[str, object]] = []
        normalized_suburb = suburb.lower()
        for group in groups:
            if group.suburb.lower() != normalized_suburb:
                continue
            lower_name = group.name.lower()
            is_dog_park = "dog park" in lower_name or "dogpark" in lower_name
            if dog_park_only and not is_dog_park:
                continue
            matched.append(
                {
                    "id": group.id,
                    "name": group.name,
                    "short_label": group.name.replace(" Dog Park", "").replace(" dog park", "")[:24].strip() or group.name[:24],
                    "member_count": group.member_count,
                    "official": group.official,
                    "membership_status": membership_by_group.get(group.id, "none"),
                    "dog_park": is_dog_park,
                }
            )
        matched.sort(
            key=lambda item: (
                1 if str(item["membership_status"]) == "member" else 0,
                1 if bool(item["dog_park"]) else 0,
                1 if bool(item["official"]) else 0,
                int(item["member_count"]),
            ),
            reverse=True,
        )
        return matched
