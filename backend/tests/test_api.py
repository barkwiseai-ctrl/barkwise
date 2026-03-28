import json
import os
import sqlite3
import sys
from datetime import datetime, timedelta
from typing import Optional
from uuid import uuid4

from fastapi.testclient import TestClient

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

import app.auth as auth_module
import app.routers.auth as auth_router
import app.routers.chat as chat_router
import app.routers.community as community_router
import app.routers.notifications as notifications_router
import app.services.security_audit as security_audit_service
from app.main import app
from app.models import ChatMessage, ChatResponse, ChatTurn
from app.services.service_store import service_store

client = TestClient(app)


def _parse_sse_data_events(raw_text: str) -> list[object]:
    events: list[object] = []
    for line in raw_text.splitlines():
        if not line.startswith("data: "):
            continue
        payload = line[6:]
        if payload == "[DONE]":
            events.append(payload)
            continue
        events.append(json.loads(payload))
    return events


def _enable_provider_mode(user_id: str, token: str) -> None:
    response = client.put(
        "/auth/profile",
        json={
            "requester_user_id": user_id,
            "display_name": user_id,
            "human_role_label": "Member",
            "service_provider_mode": True,
        },
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 200
    assert response.json()["service_provider_mode"] is True


def _find_provider_with_available_slots(
    *,
    category: str,
    min_available_slots: int,
    day_offset_start: int = 180,
    day_offset_end: int = 260,
    exclude_owner_user_id: Optional[str] = None,
):
    providers_resp = client.get("/services/providers", params={"category": category})
    assert providers_resp.status_code == 200
    providers_payload = providers_resp.json()
    assert providers_payload

    for provider in providers_payload[:25]:
        owner_user_id = provider.get("owner_user_id")
        if exclude_owner_user_id and owner_user_id == exclude_owner_user_id:
            continue
        provider_id = provider["id"]
        for day_offset in range(day_offset_start, day_offset_end):
            booking_date = (datetime.utcnow().date() + timedelta(days=day_offset)).isoformat()
            availability_resp = client.get(
                f"/services/providers/{provider_id}/availability",
                params={"date": booking_date},
            )
            assert availability_resp.status_code == 200
            available_slots = [slot for slot in availability_resp.json() if slot.get("available")]
            if len(available_slots) >= min_available_slots:
                return provider, available_slots

    raise AssertionError(
        f"Could not find provider with >= {min_available_slots} available slots "
        f"for category={category} in day range [{day_offset_start}, {day_offset_end})."
    )


def test_health_ok():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_auth_login_and_me():
    login = client.post("/auth/login", json={"user_id": "user_2", "password": "petsocial-demo"})
    assert login.status_code == 200
    token = login.json()["access_token"]

    me = client.get("/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert me.status_code == 200
    assert me.json()["user_id"] == "user_2"


def test_auth_login_disabled_when_demo_login_flag_off(monkeypatch):
    monkeypatch.setattr(auth_router, "is_demo_login_allowed", lambda: False)
    login = client.post("/auth/login", json={"user_id": "user_2", "password": "petsocial-demo"})
    assert login.status_code == 403
    assert "disabled" in login.json()["detail"].lower()


def test_auth_invite_otp_verify_logout_flow(monkeypatch):
    sent: dict[str, str] = {}

    def _capture_otp(*, email: str, otp_code: str) -> bool:
        sent["email"] = email
        sent["otp_code"] = otp_code
        return True

    monkeypatch.setattr(auth_router, "send_otp_via_resend", _capture_otp)

    suffix = uuid4().hex[:8]
    email = f"beta-{suffix}@example.com"
    user_id = f"beta_{suffix}"

    invite_response = client.post(
        "/auth/invite",
        json={
            "requester_user_id": "user_1",
            "email": email,
            "user_id": user_id,
            "ttl_minutes": 30,
        },
    )
    assert invite_response.status_code == 200
    invite_payload = invite_response.json()
    invite_id = invite_payload["invite_id"]
    assert invite_payload["user_id"] == user_id

    request_otp_response = client.post(
        "/auth/otp/request",
        json={"invite_id": invite_id, "email": email},
    )
    assert request_otp_response.status_code == 200
    assert request_otp_response.json()["status"] == "otp_sent"
    assert sent.get("email") == email
    otp_code = sent.get("otp_code", "")
    assert len(otp_code) == 6

    verify_response = client.post(
        "/auth/otp/verify",
        json={"invite_id": invite_id, "email": email, "otp_code": otp_code},
    )
    assert verify_response.status_code == 200
    verify_payload = verify_response.json()
    assert verify_payload["user_id"] == user_id
    token = verify_payload["access_token"]

    me_response = client.get("/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert me_response.status_code == 200
    assert me_response.json()["user_id"] == user_id

    logout_response = client.post("/auth/logout", headers={"Authorization": f"Bearer {token}"})
    assert logout_response.status_code == 200
    assert logout_response.json()["status"] == "ok"

    revoked_me_response = client.get("/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert revoked_me_response.status_code == 401


def test_auth_otp_request_fails_when_delivery_unavailable_and_auth_required(monkeypatch):
    monkeypatch.setattr(auth_router, "AUTH_REQUIRED", True)
    monkeypatch.setattr(auth_router, "send_otp_via_resend", lambda **_: False)

    suffix = uuid4().hex[:8]
    email = f"otp-fail-{suffix}@example.com"
    invite_response = client.post(
        "/auth/invite",
        json={
            "requester_user_id": "user_1",
            "email": email,
            "user_id": f"beta_otp_fail_{suffix}",
            "ttl_minutes": 30,
        },
    )
    assert invite_response.status_code == 200
    invite_id = invite_response.json()["invite_id"]

    request_otp_response = client.post(
        "/auth/otp/request",
        json={"invite_id": invite_id, "email": email},
    )
    assert request_otp_response.status_code == 503
    assert "unable to deliver otp" in request_otp_response.json()["detail"].lower()


def test_auth_otp_verify_rejects_expired_code(monkeypatch):
    sent: dict[str, str] = {}

    def _capture_otp(*, email: str, otp_code: str) -> bool:
        sent["email"] = email
        sent["otp_code"] = otp_code
        return True

    monkeypatch.setattr(auth_router, "send_otp_via_resend", _capture_otp)

    suffix = uuid4().hex[:8]
    email = f"otp-expired-{suffix}@example.com"
    invite_response = client.post(
        "/auth/invite",
        json={
            "requester_user_id": "user_1",
            "email": email,
            "user_id": f"beta_otp_expired_{suffix}",
            "ttl_minutes": 30,
        },
    )
    assert invite_response.status_code == 200
    invite_id = invite_response.json()["invite_id"]

    request_otp_response = client.post(
        "/auth/otp/request",
        json={"invite_id": invite_id, "email": email},
    )
    assert request_otp_response.status_code == 200
    otp_code = sent.get("otp_code", "")
    assert len(otp_code) == 6

    with sqlite3.connect(auth_router.auth_otp_store.db_path) as conn:
        conn.execute(
            "UPDATE auth_otp_codes SET expires_at = ? WHERE invite_id = ? AND email = ? AND verified_at IS NULL",
            ("2000-01-01T00:00:00+00:00", invite_id, email),
        )
        conn.commit()

    verify_response = client.post(
        "/auth/otp/verify",
        json={"invite_id": invite_id, "email": email, "otp_code": otp_code},
    )
    assert verify_response.status_code == 401
    assert "invalid or expired otp" in verify_response.json()["detail"].lower()


def test_auth_profile_upsert_and_fetch_round_trip():
    suffix = uuid4().hex[:8]
    user_id = f"profile_user_{suffix}"
    initial = client.get(
        "/auth/profile",
        params={"user_id": user_id},
    )
    assert initial.status_code == 200
    initial_payload = initial.json()
    assert initial_payload["user_id"] == user_id

    upsert = client.put(
        "/auth/profile",
        json={
            "requester_user_id": user_id,
            "display_name": "Chris Xu",
            "email": f"{suffix}@example.com",
            "phone": "+61 400 123 456",
            "human_pronouns": "he/him",
            "human_role_label": "Member",
            "dog_name": "Milo",
            "dog_age_months": 36,
            "dog_breed_mix": "Cavoodle",
            "dog_sex_neuter": "Male, desexed",
            "dog_weight_class": "Small (0-10kg)",
            "dog_photo_urls": ["https://example.com/milo.jpg", "content://photos/2"],
            "secondary_dog_name": "Nori",
            "secondary_dog_age_months": 12,
            "bio": "Dog parent in Melbourne.",
            "suburb": "Richmond",
            "favorite_suburbs": ["Richmond", "Collingwood"],
            "play_energy_level": "Medium",
            "play_style": "Chase",
            "social_confidence": "Friendly",
            "trigger_notes": "Slow intro with bigger dogs.",
            "ideal_match": "Playful small dogs",
            "walk_preferences": "Morning walk",
            "training_style": "Positive reinforcement",
            "feeding_rules": "No chicken",
            "consent_boundaries": "Ask before treats",
            "vaccination_status": "Up to date",
            "microchipped": True,
            "recall_trained": False,
            "leash_reliability": "Good",
            "emergency_contact_name": "Taylor",
            "emergency_contact_phone": "+61 400 999 999",
            "field_visibility": {"phone": "friends", "email": "private"},
        },
    )
    assert upsert.status_code == 200
    upsert_payload = upsert.json()
    assert upsert_payload["display_name"] == "Chris Xu"
    assert upsert_payload["dog_photo_urls"][0] == "https://example.com/milo.jpg"
    assert upsert_payload["suburb"] == "Richmond"
    assert upsert_payload["dog_age_months"] == 36
    assert upsert_payload["field_visibility"]["phone"] == "friends"

    fetched = client.get(
        "/auth/profile",
        params={"user_id": user_id},
    )
    assert fetched.status_code == 200
    fetched_payload = fetched.json()
    assert fetched_payload["display_name"] == "Chris Xu"
    assert fetched_payload["dog_name"] == "Milo"
    assert fetched_payload["favorite_suburbs"] == ["Richmond", "Collingwood"]
    assert fetched_payload["secondary_dog_name"] == "Nori"
    assert fetched_payload["human_pronouns"] == "he/him"


def test_auth_profile_rejects_invalid_email():
    response = client.put(
        "/auth/profile",
        json={
            "requester_user_id": "user_2",
            "display_name": "Alex",
            "email": "invalid-email",
            "phone": "",
            "dog_name": "Milo",
            "dog_photo_urls": [],
            "bio": "",
            "suburb": "Surry Hills",
            "favorite_suburbs": [],
        },
    )
    assert response.status_code == 400
    assert "invalid email" in response.json()["detail"].lower()


def test_auth_otp_verify_rejects_after_max_attempts(monkeypatch):
    sent: dict[str, str] = {}

    def _capture_otp(*, email: str, otp_code: str) -> bool:
        sent["email"] = email
        sent["otp_code"] = otp_code
        return True

    monkeypatch.setattr(auth_router, "send_otp_via_resend", _capture_otp)

    suffix = uuid4().hex[:8]
    email = f"otp-attempts-{suffix}@example.com"
    invite_response = client.post(
        "/auth/invite",
        json={
            "requester_user_id": "user_1",
            "email": email,
            "user_id": f"beta_otp_attempts_{suffix}",
            "ttl_minutes": 30,
        },
    )
    assert invite_response.status_code == 200
    invite_id = invite_response.json()["invite_id"]

    request_otp_response = client.post(
        "/auth/otp/request",
        json={"invite_id": invite_id, "email": email},
    )
    assert request_otp_response.status_code == 200
    correct_code = sent.get("otp_code", "")
    assert len(correct_code) == 6

    for _ in range(5):
        bad_verify = client.post(
            "/auth/otp/verify",
            json={"invite_id": invite_id, "email": email, "otp_code": "000000"},
        )
        assert bad_verify.status_code == 401

    verify_after_lockout = client.post(
        "/auth/otp/verify",
        json={"invite_id": invite_id, "email": email, "otp_code": correct_code},
    )
    assert verify_after_lockout.status_code == 401
    assert "invalid or expired otp" in verify_after_lockout.json()["detail"].lower()


def test_auth_invite_requires_admin_requester():
    response = client.post(
        "/auth/invite",
        json={
            "requester_user_id": "user_2",
            "email": f"non-admin-{uuid4().hex[:6]}@example.com",
            "user_id": f"beta_non_admin_{uuid4().hex[:6]}",
            "ttl_minutes": 30,
        },
    )
    assert response.status_code == 403
    assert "admins" in response.json()["detail"].lower()


def test_messages_threads_send_read_flow():
    suffix = uuid4().hex[:8]
    sender_user = f"msg_sender_{suffix}"
    recipient_user = f"msg_recipient_{suffix}"

    sender_login = client.post("/auth/login", json={"user_id": sender_user, "password": "petsocial-demo"})
    recipient_login = client.post("/auth/login", json={"user_id": recipient_user, "password": "petsocial-demo"})
    assert sender_login.status_code == 200
    assert recipient_login.status_code == 200

    sender_headers = {"Authorization": f"Bearer {sender_login.json()['access_token']}"}
    recipient_headers = {"Authorization": f"Bearer {recipient_login.json()['access_token']}"}
    thread_id = f"dm_{min(sender_user, recipient_user)}_{max(sender_user, recipient_user)}"

    send_response = client.post(
        f"/messages/threads/{thread_id}/messages",
        json={
            "user_id": sender_user,
            "recipient_user_id": recipient_user,
            "body": "Hello from integration test",
        },
        headers=sender_headers,
    )
    assert send_response.status_code == 200
    sent_payload = send_response.json()
    assert sent_payload["thread_id"] == thread_id
    assert sent_payload["sender_user_id"] == sender_user
    assert sent_payload["recipient_user_id"] == recipient_user

    recipient_threads_before = client.get(
        "/messages/threads",
        params={"user_id": recipient_user},
        headers=recipient_headers,
    )
    assert recipient_threads_before.status_code == 200
    thread_before = next((item for item in recipient_threads_before.json() if item["id"] == thread_id), None)
    assert thread_before is not None
    assert thread_before["unread_count"] >= 1

    list_messages = client.get(
        f"/messages/threads/{thread_id}",
        params={"user_id": recipient_user},
        headers=recipient_headers,
    )
    assert list_messages.status_code == 200
    assert any(message["id"] == sent_payload["id"] for message in list_messages.json())

    mark_read_response = client.post(
        f"/messages/threads/{thread_id}/read",
        json={"user_id": recipient_user},
        headers=recipient_headers,
    )
    assert mark_read_response.status_code == 200
    assert mark_read_response.json()["status"] == "ok"
    assert mark_read_response.json()["read_seq"] >= 1

    recipient_threads_after = client.get(
        "/messages/threads",
        params={"user_id": recipient_user},
        headers=recipient_headers,
    )
    assert recipient_threads_after.status_code == 200
    thread_after = next((item for item in recipient_threads_after.json() if item["id"] == thread_id), None)
    assert thread_after is not None
    assert thread_after["unread_count"] == 0


def test_messages_rejects_thread_id_participant_mismatch():
    sender = f"msg_sender_{uuid4().hex[:6]}"
    recipient = f"msg_recipient_{uuid4().hex[:6]}"
    login = client.post("/auth/login", json={"user_id": sender, "password": "petsocial-demo"})
    assert login.status_code == 200
    headers = {"Authorization": f"Bearer {login.json()['access_token']}"}

    response = client.post(
        "/messages/threads/dm_wrong/messages",
        json={
            "user_id": sender,
            "recipient_user_id": recipient,
            "body": "Bad thread id should fail",
        },
        headers=headers,
    )
    assert response.status_code == 400
    assert "does not match participants" in response.json()["detail"]


def test_auth_delete_me_revokes_token_and_removes_message_threads():
    suffix = uuid4().hex[:8]
    deleted_user = f"delete_target_{suffix}"
    peer_user = f"delete_peer_{suffix}"
    thread_id = f"dm_{min(deleted_user, peer_user)}_{max(deleted_user, peer_user)}"

    deleted_login = client.post("/auth/login", json={"user_id": deleted_user, "password": "petsocial-demo"})
    peer_login = client.post("/auth/login", json={"user_id": peer_user, "password": "petsocial-demo"})
    assert deleted_login.status_code == 200
    assert peer_login.status_code == 200
    deleted_headers = {"Authorization": f"Bearer {deleted_login.json()['access_token']}"}
    peer_headers = {"Authorization": f"Bearer {peer_login.json()['access_token']}"}

    seed_message = client.post(
        f"/messages/threads/{thread_id}/messages",
        json={
            "user_id": peer_user,
            "recipient_user_id": deleted_user,
            "body": "Thread to validate delete cleanup",
        },
        headers=peer_headers,
    )
    assert seed_message.status_code == 200

    delete_response = client.delete(
        "/auth/me",
        params={"user_id": deleted_user},
        headers=deleted_headers,
    )
    assert delete_response.status_code == 200
    assert delete_response.json()["status"] == "deleted"
    assert delete_response.json()["user_id"] == deleted_user

    me_after_delete = client.get("/auth/me", headers=deleted_headers)
    assert me_after_delete.status_code == 401

    peer_threads = client.get(
        "/messages/threads",
        params={"user_id": peer_user},
        headers=peer_headers,
    )
    assert peer_threads.status_code == 200
    assert all(thread["id"] != thread_id for thread in peer_threads.json())


def test_auth_login_failed_attempts_rate_limited(monkeypatch):
    monkeypatch.setattr(auth_router, "LOGIN_FAILURE_LIMIT", 2)
    monkeypatch.setattr(auth_router, "LOGIN_FAILURE_WINDOW", timedelta(minutes=10))
    auth_router.FAILED_LOGIN_ATTEMPTS.clear()

    first = client.post("/auth/login", json={"user_id": "user_2", "password": "wrong"})
    assert first.status_code == 401

    second = client.post("/auth/login", json={"user_id": "user_2", "password": "wrong-again"})
    assert second.status_code == 401

    third = client.post("/auth/login", json={"user_id": "user_2", "password": "still-wrong"})
    assert third.status_code == 429
    assert "Too many failed login attempts" in third.json()["detail"]

    locked_valid = client.post("/auth/login", json={"user_id": "user_2", "password": "petsocial-demo"})
    assert locked_valid.status_code == 429


def test_chat_message_rate_limited(monkeypatch):
    monkeypatch.setattr(
        chat_router.chat_service,
        "create_chat_response",
        lambda request: ChatResponse(
            answer="ok",
            message=ChatMessage(role="assistant", content="ok"),
            conversation=[ChatTurn(role="user", content=request.message or "one"), ChatTurn(role="assistant", content="ok")],
        ),
    )
    monkeypatch.setattr(chat_router, "CHAT_RATE_LIMIT_MAX_REQUESTS", 2)
    monkeypatch.setattr(chat_router, "CHAT_RATE_LIMIT_WINDOW", timedelta(minutes=10))
    chat_router.CHAT_RATE_LIMIT_HISTORY.clear()

    first = client.post(
        "/chat",
        json={"user_id": "chat_rate_user", "message": "one", "suburb": "Surry Hills"},
    )
    assert first.status_code == 200

    second = client.post(
        "/chat",
        json={"user_id": "chat_rate_user", "message": "two", "suburb": "Surry Hills"},
    )
    assert second.status_code == 200

    third = client.post(
        "/chat",
        json={"user_id": "chat_rate_user", "message": "three", "suburb": "Surry Hills"},
    )
    assert third.status_code == 429
    assert "Too many chat requests" in third.json()["detail"]


def test_chat_requires_auth_when_auth_required(monkeypatch):
    monkeypatch.setattr(
        chat_router.chat_service,
        "create_chat_response",
        lambda request: ChatResponse(
            answer="ok",
            message=ChatMessage(role="assistant", content="ok"),
            conversation=[ChatTurn(role="user", content=request.message or "hello"), ChatTurn(role="assistant", content="ok")],
        ),
    )
    monkeypatch.setattr(auth_module, "AUTH_REQUIRED", True)
    chat_router.CHAT_RATE_LIMIT_HISTORY.clear()
    response = client.post(
        "/chat",
        json={"user_id": "chat_auth_required_user", "message": "hello", "suburb": "Surry Hills"},
    )
    assert response.status_code == 401

    token, _ = auth_module.create_access_token("chat_auth_required_user")
    authed = client.post(
        "/chat",
        json={"user_id": "chat_auth_required_user", "message": "hello", "suburb": "Surry Hills"},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert authed.status_code == 200


def test_chat_message_too_long_returns_422():
    too_long_message = "x" * 2001
    response = client.post(
        "/chat",
        json={"user_id": "chat_long_message_user", "message": too_long_message, "suburb": "Surry Hills"},
    )
    assert response.status_code == 422


def test_chat_stream_rate_limited(monkeypatch):
    monkeypatch.setattr(chat_router, "CHAT_STREAM_RATE_LIMIT_MAX_REQUESTS", 1)
    monkeypatch.setattr(chat_router, "CHAT_RATE_LIMIT_WINDOW", timedelta(minutes=10))
    chat_router.CHAT_RATE_LIMIT_HISTORY.clear()

    first = client.post(
        "/chat/stream",
        json={"user_id": "chat_stream_user", "message": "stream-one", "suburb": "Surry Hills"},
    )
    assert first.status_code == 200

    blocked = client.post(
        "/chat/stream",
        json={"user_id": "chat_stream_user", "message": "stream-two", "suburb": "Surry Hills"},
    )
    assert blocked.status_code == 429
    assert "Too many chat requests" in blocked.json()["detail"]


def test_chat_returns_minimal_message_payload(monkeypatch):
    def fake_chat(_request):
        return ChatResponse(
            answer="Hello from BarkAI",
            message=ChatMessage(role="assistant", content="Hello from BarkAI"),
            conversation=[
                ChatTurn(role="user", content="Hi"),
                ChatTurn(role="assistant", content="Hello from BarkAI"),
            ],
            answer_source="assistant",
        )

    monkeypatch.setattr(chat_router.chat_service, "create_chat_response", fake_chat)

    response = client.post(
        "/chat",
        json={
            "user_id": "chat_minimal_user",
            "messages": [{"role": "user", "content": "Hi"}],
        },
    )
    assert response.status_code == 200
    payload = response.json()
    assert payload["message"] == {"role": "assistant", "content": "Hello from BarkAI"}
    assert payload["answer"] == "Hello from BarkAI"
    assert payload["answer_source"] == "assistant"
    assert payload["answer_badges"] == []
    assert payload["citations"] == []
    assert payload["cta_chips"] == []


def test_chat_accepts_transcript_payload(monkeypatch):
    captured = {}

    def fake_chat(request):
        captured["messages"] = [item.model_dump() for item in request.messages]
        return ChatResponse(
            answer="I remember the transcript",
            message=ChatMessage(role="assistant", content="I remember the transcript"),
        )

    monkeypatch.setattr(chat_router.chat_service, "create_chat_response", fake_chat)

    response = client.post(
        "/chat",
        json={
            "user_id": "chat_transcript_user",
            "messages": [
                {"role": "user", "content": "Hello"},
                {"role": "assistant", "content": "Hi there"},
                {"role": "user", "content": "Tell me more"},
            ],
        },
    )
    assert response.status_code == 200
    assert captured["messages"] == [
        {"role": "user", "content": "Hello"},
        {"role": "assistant", "content": "Hi there"},
        {"role": "user", "content": "Tell me more"},
    ]


def test_chat_profile_accept_preserves_legacy_endpoint(monkeypatch):
    captured = {}

    def fake_accept(*, user_id):
        captured["user_id"] = user_id
        return ChatResponse(
            answer="Profile created",
            message=ChatMessage(role="assistant", content="Profile created"),
            answer_source="legacy_action",
        )

    monkeypatch.setattr(chat_router.chat_service, "accept_profile", fake_accept)

    response = client.post("/chat/profile/accept", json={"user_id": "chat_profile_user"})
    assert response.status_code == 200
    assert captured == {"user_id": "chat_profile_user"}
    assert response.json()["answer"] == "Profile created"


def test_chat_provider_submit_preserves_legacy_endpoint(monkeypatch):
    captured = {}

    def fake_submit(*, user_id):
        captured["user_id"] = user_id
        return ChatResponse(
            answer="Provider listed",
            message=ChatMessage(role="assistant", content="Provider listed"),
            answer_source="legacy_action",
        )

    monkeypatch.setattr(chat_router.chat_service, "submit_provider_listing", fake_submit)

    response = client.post("/chat/provider/submit", json={"user_id": "chat_provider_user"})
    assert response.status_code == 200
    assert captured == {"user_id": "chat_provider_user"}
    assert response.json()["answer"] == "Provider listed"

def test_chat_stream_returns_delta_and_final_message(monkeypatch):
    def fake_stream(_request):
        yield {"type": "delta", "delta": "Hello "}
        yield {"type": "delta", "delta": "there"}
        yield {
            "type": "final",
            "response": ChatResponse(
                answer="Hello there",
                message=ChatMessage(role="assistant", content="Hello there"),
                conversation=[
                    ChatTurn(role="user", content="Hi"),
                    ChatTurn(role="assistant", content="Hello there"),
                ],
                answer_source="assistant",
            ).model_dump(),
        }

    monkeypatch.setattr(chat_router.chat_service, "stream_chat", fake_stream)

    response = client.post(
        "/chat/stream",
        json={
            "user_id": "chat_stream_minimal_user",
            "messages": [{"role": "user", "content": "Hi"}],
        },
    )
    assert response.status_code == 200

    events = _parse_sse_data_events(response.text)
    assert events[-1] == "[DONE]"
    delta_events = [event for event in events if isinstance(event, dict) and event.get("type") == "delta"]
    final_event = next(event for event in events if isinstance(event, dict) and event.get("type") == "final")
    final_payload = final_event["response"]

    assert "".join(event["delta"] for event in delta_events) == "Hello there"
    assert final_payload["message"] == {"role": "assistant", "content": "Hello there"}
    assert final_payload["answer_badges"] == []
    assert final_payload["citations"] == []


def test_chat_stream_failure_emits_plain_error_event(monkeypatch):
    def broken_stream(_request):
        raise RuntimeError("boom")
        yield  # pragma: no cover

    monkeypatch.setattr(chat_router.chat_service, "stream_chat", broken_stream)

    response = client.post(
        "/chat/stream",
        json={
            "user_id": "chat_stream_error_user",
            "messages": [{"role": "user", "content": "Hi"}],
        },
    )
    assert response.status_code == 200

    events = _parse_sse_data_events(response.text)
    error_event = next(event for event in events if isinstance(event, dict) and event.get("type") == "error")
    assert "retry" in error_event["error"].lower()


def test_security_rate_limit_metrics_snapshot_tracks_429_surfaces(monkeypatch):
    security_audit_service.reset_rate_limit_metrics()
    monkeypatch.setattr(
        chat_router.chat_service,
        "create_chat_response",
        lambda request: ChatResponse(
            answer="ok",
            message=ChatMessage(role="assistant", content="ok"),
            conversation=[ChatTurn(role="user", content=request.message or "first"), ChatTurn(role="assistant", content="ok")],
        ),
    )
    monkeypatch.setattr(auth_router, "LOGIN_FAILURE_LIMIT", 1)
    monkeypatch.setattr(auth_router, "LOGIN_FAILURE_WINDOW", timedelta(minutes=10))
    auth_router.FAILED_LOGIN_ATTEMPTS.clear()
    monkeypatch.setattr(chat_router, "CHAT_RATE_LIMIT_MAX_REQUESTS", 1)
    monkeypatch.setattr(chat_router, "CHAT_RATE_LIMIT_WINDOW", timedelta(minutes=10))
    chat_router.CHAT_RATE_LIMIT_HISTORY.clear()

    # First bad password consumes the one allowed failure. Second should 429.
    first_bad = client.post("/auth/login", json={"user_id": "metrics_user", "password": "wrong"})
    assert first_bad.status_code == 401
    second_bad = client.post("/auth/login", json={"user_id": "metrics_user", "password": "wrong-again"})
    assert second_bad.status_code == 429

    first_chat = client.post(
        "/chat",
        json={"user_id": "metrics_chat_user", "message": "first", "suburb": "Surry Hills"},
    )
    assert first_chat.status_code == 200
    second_chat = client.post(
        "/chat",
        json={"user_id": "metrics_chat_user", "message": "second", "suburb": "Surry Hills"},
    )
    assert second_chat.status_code == 429

    admin_login = client.post("/auth/login", json={"user_id": "user_1", "password": "petsocial-demo"})
    assert admin_login.status_code == 200
    admin_token = admin_login.json()["access_token"]

    snapshot = client.get(
        "/security/rate-limits",
        params={"requester_user_id": "user_1"},
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert snapshot.status_code == 200
    payload = snapshot.json()
    assert payload["total_hits"] >= 2
    assert payload["by_surface"].get("auth_login", 0) >= 1
    assert payload["by_surface"].get("chat_chat", 0) >= 1
    assert any(item["surface"] == "auth_login" for item in payload["recent_hits"])
    assert any(item["surface"] == "chat_chat" for item in payload["recent_hits"])


def test_security_rate_limit_metrics_snapshot_forbidden_for_non_admin():
    user_login = client.post("/auth/login", json={"user_id": "user_2", "password": "petsocial-demo"})
    assert user_login.status_code == 200
    user_token = user_login.json()["access_token"]

    forbidden = client.get(
        "/security/rate-limits",
        params={"requester_user_id": "user_2"},
        headers={"Authorization": f"Bearer {user_token}"},
    )
    assert forbidden.status_code == 403
    assert "Only admins can view security rate limit metrics" in forbidden.json()["detail"]


def test_security_rate_limit_metrics_reset_clears_counts(monkeypatch):
    security_audit_service.reset_rate_limit_metrics()
    monkeypatch.setattr(
        chat_router.chat_service,
        "create_chat_response",
        lambda request: ChatResponse(
            answer="ok",
            message=ChatMessage(role="assistant", content="ok"),
            conversation=[ChatTurn(role="user", content=request.message or "first"), ChatTurn(role="assistant", content="ok")],
        ),
    )
    monkeypatch.setattr(chat_router, "CHAT_RATE_LIMIT_MAX_REQUESTS", 1)
    monkeypatch.setattr(chat_router, "CHAT_RATE_LIMIT_WINDOW", timedelta(minutes=10))
    chat_router.CHAT_RATE_LIMIT_HISTORY.clear()

    first_chat = client.post(
        "/chat",
        json={"user_id": "reset_metrics_user", "message": "first", "suburb": "Surry Hills"},
    )
    assert first_chat.status_code == 200
    blocked_chat = client.post(
        "/chat",
        json={"user_id": "reset_metrics_user", "message": "second", "suburb": "Surry Hills"},
    )
    assert blocked_chat.status_code == 429

    admin_login = client.post("/auth/login", json={"user_id": "user_1", "password": "petsocial-demo"})
    assert admin_login.status_code == 200
    admin_token = admin_login.json()["access_token"]

    before_reset = client.get(
        "/security/rate-limits",
        params={"requester_user_id": "user_1"},
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert before_reset.status_code == 200
    assert before_reset.json()["total_hits"] >= 1

    reset = client.post(
        "/security/rate-limits/reset",
        params={"requester_user_id": "user_1"},
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert reset.status_code == 200
    payload = reset.json()
    assert payload["status"] == "ok"
    assert payload["metrics"]["total_hits"] == 0
    assert payload["metrics"]["by_surface"] == {}
    assert payload["metrics"]["recent_hits"] == []


def test_security_rate_limit_metrics_reset_forbidden_for_non_admin():
    user_login = client.post("/auth/login", json={"user_id": "user_2", "password": "petsocial-demo"})
    assert user_login.status_code == 200
    user_token = user_login.json()["access_token"]

    forbidden = client.post(
        "/security/rate-limits/reset",
        params={"requester_user_id": "user_2"},
        headers={"Authorization": f"Bearer {user_token}"},
    )
    assert forbidden.status_code == 403
    assert "Only admins can view security rate limit metrics" in forbidden.json()["detail"]


def test_security_rate_limit_metrics_reset_rejects_actor_token_mismatch():
    user1_login = client.post("/auth/login", json={"user_id": "user_1", "password": "petsocial-demo"})
    assert user1_login.status_code == 200
    user1_token = user1_login.json()["access_token"]

    mismatch = client.post(
        "/security/rate-limits/reset",
        params={"requester_user_id": "user_3"},
        headers={"Authorization": f"Bearer {user1_token}"},
    )
    assert mismatch.status_code == 403
    assert mismatch.json()["detail"] == "Token user does not match actor user"


def test_services_search_and_sort():
    response = client.get("/services/providers", params={"q": "walk", "sort_by": "rating"})
    assert response.status_code == 200
    payload = response.json()
    assert isinstance(payload, list)
    if len(payload) >= 2:
        assert payload[0]["rating"] >= payload[1]["rating"]


def test_create_service_provider():
    login = client.post("/auth/login", json={"user_id": "user_2", "password": "petsocial-demo"})
    assert login.status_code == 200
    token = login.json()["access_token"]
    _enable_provider_mode("user_2", token)

    response = client.post(
        "/services/providers",
        json={
            "user_id": "user_2",
            "name": "Snowy Test Walkers",
            "category": "dog_walking",
            "suburb": "Sunshine West",
            "description": "Reliable 30 minute walks.",
            "price_from": 31,
        },
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 200
    payload = response.json()
    assert payload["name"] == "Snowy Test Walkers"
    assert payload["category"] == "dog_walking"
    assert payload["owner_user_id"] == "user_2"


def test_create_service_provider_requires_provider_mode():
    login = client.post("/auth/login", json={"user_id": "user_provider_mode_off", "password": "petsocial-demo"})
    assert login.status_code == 200
    token = login.json()["access_token"]

    response = client.post(
        "/services/providers",
        json={
            "user_id": "user_provider_mode_off",
            "name": "Mode Off Listing",
            "category": "dog_walking",
            "suburb": "Sunshine West",
            "description": "Should be blocked until provider mode is enabled.",
            "price_from": 25,
        },
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 403
    assert "Provider mode is off" in response.json()["detail"]


def test_create_service_provider_alias_routes():
    login = client.post("/auth/login", json={"user_id": "user_2", "password": "petsocial-demo"})
    assert login.status_code == 200
    token = login.json()["access_token"]
    _enable_provider_mode("user_2", token)

    singular_post = client.post(
        "/services/provider",
        json={
            "user_id": "user_2",
            "name": "Snowy Alias Provider",
            "category": "dog_walking",
            "suburb": "Sunshine West",
            "description": "Alias route test.",
            "price_from": 34,
        },
        headers={"Authorization": f"Bearer {token}"},
    )
    assert singular_post.status_code == 200
    assert singular_post.json()["owner_user_id"] == "user_2"

    legacy_put = client.put(
        "/services/providers/create",
        json={
            "user_id": "user_2",
            "name": "Snowy Alias Provider 2",
            "category": "dog_walking",
            "suburb": "Sunshine West",
            "description": "Legacy route test.",
            "price_from": 35,
        },
        headers={"Authorization": f"Bearer {token}"},
    )
    assert legacy_put.status_code == 200
    assert legacy_put.json()["owner_user_id"] == "user_2"


def test_update_cancel_restore_service_provider():
    login = client.post("/auth/login", json={"user_id": "user_2", "password": "petsocial-demo"})
    assert login.status_code == 200
    token = login.json()["access_token"]
    _enable_provider_mode("user_2", token)

    created = client.post(
        "/services/providers",
        json={
            "user_id": "user_2",
            "name": "Snowy Edit Me",
            "category": "dog_walking",
            "suburb": "Sunshine West",
            "description": "Initial description",
            "price_from": 29,
        },
        headers={"Authorization": f"Bearer {token}"},
    )
    assert created.status_code == 200
    provider_id = created.json()["id"]

    updated = client.post(
        f"/services/providers/{provider_id}/update",
        json={
            "user_id": "user_2",
            "name": "Snowy Updated Listing",
            "description": "Updated description",
            "price_from": 35,
        },
        headers={"Authorization": f"Bearer {token}"},
    )
    assert updated.status_code == 200
    updated_payload = updated.json()
    assert updated_payload["name"] == "Snowy Updated Listing"
    assert updated_payload["description"] == "Updated description"
    assert updated_payload["price_from"] == 35

    cancelled = client.post(
        f"/services/providers/{provider_id}/cancel",
        json={"user_id": "user_2"},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert cancelled.status_code == 200
    assert cancelled.json()["status"] == "cancelled"

    listed_after_cancel = client.get("/services/providers")
    assert listed_after_cancel.status_code == 200
    assert all(item["id"] != provider_id for item in listed_after_cancel.json())

    mine_including_inactive = client.get(
        "/services/providers",
        params={
            "user_id": "user_2",
            "include_inactive": "true",
            "q": "Snowy Updated Listing",
        },
    )
    assert mine_including_inactive.status_code == 200
    cancelled_item = next(item for item in mine_including_inactive.json() if item["id"] == provider_id)
    assert cancelled_item["status"] == "cancelled"

    restored = client.post(
        f"/services/providers/{provider_id}/restore",
        json={"user_id": "user_2"},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert restored.status_code == 200
    assert restored.json()["status"] == "active"


def test_service_provider_edit_cancel_forbidden_for_non_owner():
    login_owner = client.post("/auth/login", json={"user_id": "user_2", "password": "petsocial-demo"})
    assert login_owner.status_code == 200
    owner_token = login_owner.json()["access_token"]
    _enable_provider_mode("user_2", owner_token)

    create_response = client.post(
        "/services/providers",
        json={
            "user_id": "user_2",
            "name": "Snowy Protected Listing",
            "category": "dog_walking",
            "suburb": "Sunshine West",
            "description": "Owner-only edits",
            "price_from": 32,
        },
        headers={"Authorization": f"Bearer {owner_token}"},
    )
    assert create_response.status_code == 200
    provider_id = create_response.json()["id"]

    login_other = client.post("/auth/login", json={"user_id": "user_3", "password": "petsocial-demo"})
    assert login_other.status_code == 200
    other_token = login_other.json()["access_token"]

    forbidden_update = client.post(
        f"/services/providers/{provider_id}/update",
        json={"user_id": "user_3", "name": "Hijacked"},
        headers={"Authorization": f"Bearer {other_token}"},
    )
    assert forbidden_update.status_code == 403

    forbidden_cancel = client.post(
        f"/services/providers/{provider_id}/cancel",
        json={"user_id": "user_3"},
        headers={"Authorization": f"Bearer {other_token}"},
    )
    assert forbidden_cancel.status_code == 403


def test_services_invalid_sort_returns_400():
    response = client.get("/services/providers", params={"sort_by": "oops"})
    assert response.status_code == 400
    assert "Invalid sort_by value" in response.json()["detail"]


def test_services_availability_unknown_provider_returns_404():
    response = client.get("/services/providers/does_not_exist/availability", params={"date": "2026-02-19"})
    assert response.status_code == 404
    assert response.json()["detail"] == "Provider not found"


def test_services_bookings_invalid_role_returns_400():
    response = client.get("/services/bookings", params={"user_id": "user_1", "role": "admin"})
    assert response.status_code == 400
    assert "Invalid role value" in response.json()["detail"]


def test_services_booking_conflict_includes_alternative_slots_hint():
    login = client.post("/auth/login", json={"user_id": "user_2", "password": "petsocial-demo"})
    assert login.status_code == 200
    headers = {"Authorization": f"Bearer {login.json()['access_token']}"}

    provider, available_slots = _find_provider_with_available_slots(
        category="grooming",
        min_available_slots=1,
        day_offset_start=180,
        day_offset_end=260,
    )
    provider_id = provider["id"]
    candidate = available_slots[0]

    create_first = client.post(
        "/services/bookings",
        json={
            "user_id": "user_2",
            "provider_id": provider_id,
            "pet_name": "Milo",
            "date": candidate["date"],
            "time_slot": candidate["time_slot"],
            "note": "Conflict guardrail setup booking",
        },
        headers=headers,
    )
    assert create_first.status_code == 200

    create_conflict = client.post(
        "/services/bookings",
        json={
            "user_id": "user_2",
            "provider_id": provider_id,
            "pet_name": "Milo",
            "date": candidate["date"],
            "time_slot": candidate["time_slot"],
            "note": "Conflict guardrail retry booking",
        },
        headers=headers,
    )
    assert create_conflict.status_code == 409
    detail = create_conflict.json()["detail"]
    assert "Time slot unavailable" in detail
    assert "Next available:" in detail


def test_services_booking_history_endpoint_tracks_exact_transitions_and_permissions():
    owner_login = client.post("/auth/login", json={"user_id": "user_2", "password": "petsocial-demo"})
    assert owner_login.status_code == 200
    owner_headers = {"Authorization": f"Bearer {owner_login.json()['access_token']}"}

    provider, available_slots = _find_provider_with_available_slots(
        category="grooming",
        min_available_slots=1,
        day_offset_start=210,
        day_offset_end=290,
        exclude_owner_user_id="user_2",
    )
    provider_id = provider["id"]
    provider_owner_user_id = provider.get("owner_user_id")
    assert provider_owner_user_id

    available_slot = available_slots[0]

    create_booking = client.post(
        "/services/bookings",
        json={
            "user_id": "user_2",
            "provider_id": provider_id,
            "pet_name": "Milo",
            "date": available_slot["date"],
            "time_slot": available_slot["time_slot"],
            "note": "Timeline API test booking",
        },
        headers=owner_headers,
    )
    assert create_booking.status_code == 200
    booking_id = create_booking.json()["id"]

    provider_login = client.post("/auth/login", json={"user_id": provider_owner_user_id, "password": "petsocial-demo"})
    assert provider_login.status_code == 200
    provider_headers = {"Authorization": f"Bearer {provider_login.json()['access_token']}"}
    provider_confirm = client.post(
        f"/services/bookings/{booking_id}/status",
        json={
            "actor_user_id": provider_owner_user_id,
            "status": "provider_confirmed",
            "note": "Confirmed for timeline API test",
        },
        headers=provider_headers,
    )
    assert provider_confirm.status_code == 200

    history_owner = client.get(
        f"/services/bookings/{booking_id}/history",
        params={"requester_user_id": "user_2"},
        headers=owner_headers,
    )
    assert history_owner.status_code == 200
    history_payload = history_owner.json()
    assert len(history_payload) >= 2
    assert history_payload[0]["from_status"] == "none"
    assert history_payload[0]["to_status"] == "requested"
    assert history_payload[-1]["to_status"] == "provider_confirmed"
    assert history_payload[-1]["actor_user_id"] == provider_owner_user_id

    stranger_user_id = next(
        user_id for user_id in ["user_1", "user_3", "user_4"] if user_id not in {"user_2", provider_owner_user_id}
    )
    stranger_login = client.post("/auth/login", json={"user_id": stranger_user_id, "password": "petsocial-demo"})
    assert stranger_login.status_code == 200
    stranger_headers = {"Authorization": f"Bearer {stranger_login.json()['access_token']}"}
    forbidden = client.get(
        f"/services/bookings/{booking_id}/history",
        params={"requester_user_id": stranger_user_id},
        headers=stranger_headers,
    )
    assert forbidden.status_code == 403
    assert "Only booking owner or provider can view booking history" in forbidden.json()["detail"]


def test_services_booking_payload_includes_messaging_identity_fields():
    owner_login = client.post("/auth/login", json={"user_id": "user_2", "password": "petsocial-demo"})
    assert owner_login.status_code == 200
    owner_headers = {"Authorization": f"Bearer {owner_login.json()['access_token']}"}

    provider, available_slots = _find_provider_with_available_slots(
        category="dog_walking",
        min_available_slots=1,
        day_offset_start=210,
        day_offset_end=260,
        exclude_owner_user_id="user_2",
    )
    provider_id = provider["id"]
    provider_owner_user_id = provider.get("owner_user_id")
    assert provider_owner_user_id

    create_booking = client.post(
        "/services/bookings",
        json={
            "user_id": "user_2",
            "provider_id": provider_id,
            "pet_name": "Milo",
            "date": available_slots[0]["date"],
            "time_slot": available_slots[0]["time_slot"],
            "note": "Booking payload contract test",
        },
        headers=owner_headers,
    )
    assert create_booking.status_code == 200
    payload = create_booking.json()
    expected_thread_id = f"dm_{min('user_2', provider_owner_user_id)}_{max('user_2', provider_owner_user_id)}"
    assert payload["owner_user_id"] == "user_2"
    assert payload["provider_owner_user_id"] == provider_owner_user_id
    assert payload["counterparty_user_id"] == provider_owner_user_id
    assert payload["thread_id"] == expected_thread_id

    owner_bookings = client.get(
        "/services/bookings",
        params={"user_id": "user_2", "role": "owner"},
        headers=owner_headers,
    )
    assert owner_bookings.status_code == 200
    listed = next(item for item in owner_bookings.json() if item["id"] == payload["id"])
    assert listed["provider_owner_user_id"] == provider_owner_user_id
    assert listed["counterparty_user_id"] == provider_owner_user_id
    assert listed["thread_id"] == expected_thread_id


def test_services_provider_reschedule_updates_slot_and_keeps_transition_history():
    owner_login = client.post("/auth/login", json={"user_id": "user_2", "password": "petsocial-demo"})
    assert owner_login.status_code == 200
    owner_headers = {"Authorization": f"Bearer {owner_login.json()['access_token']}"}

    provider, available_slots = _find_provider_with_available_slots(
        category="grooming",
        min_available_slots=2,
        day_offset_start=230,
        day_offset_end=320,
        exclude_owner_user_id="user_2",
    )
    provider_id = provider["id"]
    provider_owner_user_id = provider.get("owner_user_id")
    assert provider_owner_user_id

    original_slot = available_slots[0]
    target_slot = available_slots[1]

    create_booking = client.post(
        "/services/bookings",
        json={
            "user_id": "user_2",
            "provider_id": provider_id,
            "pet_name": "Milo",
            "date": original_slot["date"],
            "time_slot": original_slot["time_slot"],
            "note": "Reschedule target flow test",
        },
        headers=owner_headers,
    )
    assert create_booking.status_code == 200
    booking_id = create_booking.json()["id"]

    provider_login = client.post("/auth/login", json={"user_id": provider_owner_user_id, "password": "petsocial-demo"})
    assert provider_login.status_code == 200
    provider_headers = {"Authorization": f"Bearer {provider_login.json()['access_token']}"}

    provider_confirm = client.post(
        f"/services/bookings/{booking_id}/status",
        json={
            "actor_user_id": provider_owner_user_id,
            "status": "provider_confirmed",
            "note": "Confirmed before reschedule",
        },
        headers=provider_headers,
    )
    assert provider_confirm.status_code == 200

    owner_request_reschedule = client.post(
        f"/services/bookings/{booking_id}/status",
        json={
            "actor_user_id": "user_2",
            "status": "reschedule_requested",
            "note": "Need a later slot",
        },
        headers=owner_headers,
    )
    assert owner_request_reschedule.status_code == 200
    assert owner_request_reschedule.json()["status"] == "reschedule_requested"

    provider_reschedule = client.post(
        f"/services/bookings/{booking_id}/status",
        json={
            "actor_user_id": provider_owner_user_id,
            "status": "rescheduled",
            "date": target_slot["date"],
            "time_slot": target_slot["time_slot"],
            "note": "Moved to a later slot",
        },
        headers=provider_headers,
    )
    assert provider_reschedule.status_code == 200
    rescheduled_payload = provider_reschedule.json()
    assert rescheduled_payload["status"] == "rescheduled"
    assert rescheduled_payload["date"] == target_slot["date"]
    assert rescheduled_payload["time_slot"] == target_slot["time_slot"]

    provider_reconfirm = client.post(
        f"/services/bookings/{booking_id}/status",
        json={
            "actor_user_id": provider_owner_user_id,
            "status": "provider_confirmed",
            "note": "Reconfirmed after reschedule",
        },
        headers=provider_headers,
    )
    assert provider_reconfirm.status_code == 200
    provider_reconfirm_payload = provider_reconfirm.json()
    assert provider_reconfirm_payload["status"] == "provider_confirmed"
    assert provider_reconfirm_payload["date"] == target_slot["date"]
    assert provider_reconfirm_payload["time_slot"] == target_slot["time_slot"]

    refreshed_availability = client.get(
        f"/services/providers/{provider_id}/availability",
        params={"date": target_slot["date"]},
    )
    assert refreshed_availability.status_code == 200
    moved_slot = next(
        (slot for slot in refreshed_availability.json() if slot["time_slot"] == target_slot["time_slot"]),
        None,
    )
    assert moved_slot is not None
    assert moved_slot["available"] is False

    history = client.get(
        f"/services/bookings/{booking_id}/history",
        params={"requester_user_id": "user_2"},
        headers=owner_headers,
    )
    assert history.status_code == 200
    history_payload = history.json()
    to_statuses = [row["to_status"] for row in history_payload]
    assert to_statuses[:5] == [
        "requested",
        "provider_confirmed",
        "reschedule_requested",
        "rescheduled",
        "provider_confirmed",
    ]


def test_services_provider_reschedule_requires_target_date_and_time_slot():
    owner_login = client.post("/auth/login", json={"user_id": "user_2", "password": "petsocial-demo"})
    assert owner_login.status_code == 200
    owner_headers = {"Authorization": f"Bearer {owner_login.json()['access_token']}"}

    provider, available_slots = _find_provider_with_available_slots(
        category="grooming",
        min_available_slots=1,
        day_offset_start=240,
        day_offset_end=320,
        exclude_owner_user_id="user_2",
    )
    provider_id = provider["id"]
    provider_owner_user_id = provider.get("owner_user_id")
    assert provider_owner_user_id

    available_slot = available_slots[0]

    create_booking = client.post(
        "/services/bookings",
        json={
            "user_id": "user_2",
            "provider_id": provider_id,
            "pet_name": "Milo",
            "date": available_slot["date"],
            "time_slot": available_slot["time_slot"],
            "note": "Missing reschedule payload test",
        },
        headers=owner_headers,
    )
    assert create_booking.status_code == 200
    booking_id = create_booking.json()["id"]

    provider_login = client.post("/auth/login", json={"user_id": provider_owner_user_id, "password": "petsocial-demo"})
    assert provider_login.status_code == 200
    provider_headers = {"Authorization": f"Bearer {provider_login.json()['access_token']}"}

    provider_confirm = client.post(
        f"/services/bookings/{booking_id}/status",
        json={
            "actor_user_id": provider_owner_user_id,
            "status": "provider_confirmed",
            "note": "Confirmed before reschedule request",
        },
        headers=provider_headers,
    )
    assert provider_confirm.status_code == 200

    owner_request_reschedule = client.post(
        f"/services/bookings/{booking_id}/status",
        json={
            "actor_user_id": "user_2",
            "status": "reschedule_requested",
            "note": "Need another time",
        },
        headers=owner_headers,
    )
    assert owner_request_reschedule.status_code == 200

    missing_slot_payload = client.post(
        f"/services/bookings/{booking_id}/status",
        json={
            "actor_user_id": provider_owner_user_id,
            "status": "rescheduled",
            "note": "No slot supplied",
        },
        headers=provider_headers,
    )
    assert missing_slot_payload.status_code == 400
    assert "Rescheduled status requires date and time_slot" in missing_slot_payload.json()["detail"]


def test_services_calendar_invalid_date_range_returns_400():
    response = client.get(
        "/services/calendar/events",
        params={
            "user_id": "user_1",
            "date_from": "2026-02-20",
            "date_to": "2026-02-19",
            "role": "all",
        },
    )
    assert response.status_code == 400
    assert response.json()["detail"] == "date_to must be on or after date_from"


def test_notifications_flow():
    login = client.post("/auth/login", json={"user_id": "user_1", "password": "petsocial-demo"})
    token = login.json()["access_token"]

    register = client.post(
        "/notifications/register-device",
        json={"user_id": "user_1", "device_token": "demo-token", "platform": "android"},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert register.status_code == 200

    list_response = client.get(
        "/notifications",
        params={"user_id": "user_1"},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert list_response.status_code == 200


def test_notifications_register_device_rate_limited(monkeypatch):
    monkeypatch.setattr(notifications_router, "DEVICE_REGISTER_RATE_LIMIT_MAX", 2)
    monkeypatch.setattr(notifications_router, "DEVICE_REGISTER_RATE_LIMIT_WINDOW", timedelta(minutes=10))
    notifications_router.DEVICE_REGISTER_RATE_LIMIT_HISTORY.clear()

    login = client.post("/auth/login", json={"user_id": "user_1", "password": "petsocial-demo"})
    assert login.status_code == 200
    token = login.json()["access_token"]
    headers = {"Authorization": f"Bearer {token}"}

    first = client.post(
        "/notifications/register-device",
        json={"user_id": "user_1", "device_token": "rl-token-1", "platform": "android"},
        headers=headers,
    )
    assert first.status_code == 200

    second = client.post(
        "/notifications/register-device",
        json={"user_id": "user_1", "device_token": "rl-token-2", "platform": "android"},
        headers=headers,
    )
    assert second.status_code == 200

    blocked = client.post(
        "/notifications/register-device",
        json={"user_id": "user_1", "device_token": "rl-token-3", "platform": "android"},
        headers=headers,
    )
    assert blocked.status_code == 429
    assert "Too many device registration attempts" in blocked.json()["detail"]


def test_notifications_list_forbidden_when_token_user_mismatch():
    user1_login = client.post("/auth/login", json={"user_id": "user_1", "password": "petsocial-demo"})
    assert user1_login.status_code == 200
    user1_token = user1_login.json()["access_token"]

    user2_login = client.post("/auth/login", json={"user_id": "user_2", "password": "petsocial-demo"})
    assert user2_login.status_code == 200
    user2_token = user2_login.json()["access_token"]

    register = client.post(
        "/notifications/register-device",
        json={"user_id": "user_2", "device_token": "mismatch-test-token", "platform": "android"},
        headers={"Authorization": f"Bearer {user2_token}"},
    )
    assert register.status_code == 200

    mismatch = client.get(
        "/notifications",
        params={"user_id": "user_2"},
        headers={"Authorization": f"Bearer {user1_token}"},
    )
    assert mismatch.status_code == 403
    assert mismatch.json()["detail"] == "Token user does not match actor user"


def test_notifications_list_requires_auth_when_auth_required_enabled(monkeypatch):
    monkeypatch.setattr(auth_module, "AUTH_REQUIRED", True)

    response = client.get("/notifications", params={"user_id": "user_1"})
    assert response.status_code == 401
    assert response.json()["detail"] == "Authentication required"


def test_mvp_day2_contract_freeze_parks_and_grooming():
    def assert_exact_keys(payload: dict, expected: set[str], context: str):
        actual = set(payload.keys())
        assert actual == expected, f"{context} keys mismatch. expected={sorted(expected)} actual={sorted(actual)}"

    group_keys = {
        "id",
        "name",
        "suburb",
        "member_count",
        "official",
        "owner_user_id",
        "membership_status",
        "is_admin",
        "pending_request_count",
        "group_badges",
        "cooperative_score",
        "my_pack_builder_points",
        "my_clean_park_points",
    }
    event_keys = {
        "id",
        "title",
        "description",
        "suburb",
        "date",
        "group_id",
        "location_name",
        "location_latitude",
        "location_longitude",
        "recurrence",
        "recurrence_interval",
        "attendee_count",
        "created_by",
        "rsvp_status",
        "status",
    }
    challenge_result_keys = {
        "challenge",
        "my_contribution_count",
        "contribution_count",
        "reward_unlocked",
        "unlocked_badges",
    }
    challenge_keys = {
        "id",
        "group_id",
        "type",
        "title",
        "description",
        "target_count",
        "progress_count",
        "status",
        "reward_label",
        "start_at",
        "end_at",
    }
    provider_keys = {
        "id",
        "name",
        "category",
        "suburb",
        "rating",
        "review_count",
        "price_from",
        "description",
        "full_description",
        "image_urls",
        "latitude",
        "longitude",
        "distance_km",
        "owner_user_id",
        "owner_label",
        "status",
        "response_time_minutes",
        "local_bookers_this_month",
        "shared_group_bookers",
        "social_proof",
        "quote_sprint_tier",
        "quote_response_rate_pct",
        "quote_response_streak",
        "vet_checked",
        "vet_checked_until",
        "vet_checked_by",
        "highlighted_vet",
        "highlighted_vet_until",
    }
    availability_slot_keys = {"date", "time_slot", "available", "reason"}
    booking_keys = {
        "id",
        "owner_user_id",
        "provider_id",
        "provider_owner_user_id",
        "counterparty_user_id",
        "thread_id",
        "pet_name",
        "date",
        "time_slot",
        "note",
        "status",
    }
    notification_keys = {"id", "user_id", "title", "body", "category", "read", "created_at", "deep_link"}

    login = client.post("/auth/login", json={"user_id": "user_2", "password": "petsocial-demo"})
    assert login.status_code == 200
    token = login.json()["access_token"]
    headers = {"Authorization": f"Bearer {token}"}

    # Journey A: meet up at park to socialize dogs
    groups_resp = client.get("/community/groups", params={"suburb": "Surry Hills", "user_id": "user_2"})
    assert groups_resp.status_code == 200
    groups_payload = groups_resp.json()
    assert isinstance(groups_payload, list)
    assert groups_payload, "Expected at least one local group"
    assert_exact_keys(groups_payload[0], group_keys, "community/groups[0]")

    event_create = client.post(
        "/community/events",
        json={
            "user_id": "user_2",
            "title": "Contract Freeze Walk",
            "description": "Schema stability walk",
            "suburb": "Surry Hills",
            "date": (datetime.utcnow() + timedelta(days=1)).isoformat() + "Z",
            "group_id": None,
        },
        headers=headers,
    )
    assert event_create.status_code == 200
    created_event = event_create.json()
    assert_exact_keys(created_event, event_keys, "community/events:create")
    event_id = created_event["id"]
    assert created_event["recurrence"] == "none"
    assert created_event["recurrence_interval"] == 1

    event_update = client.put(
        f"/community/events/{event_id}",
        json={
            "user_id": "user_2",
            "title": "Contract Freeze Walk (Recurring)",
            "location_name": "BarkWise test pin",
            "location_latitude": -33.8886,
            "location_longitude": 151.2094,
            "recurrence": "weekly",
            "recurrence_interval": 1,
        },
        headers=headers,
    )
    assert event_update.status_code == 200
    updated_event = event_update.json()
    assert_exact_keys(updated_event, event_keys, "community/events:update")
    assert updated_event["location_name"] == "BarkWise test pin"
    assert updated_event["recurrence"] == "weekly"

    events_resp = client.get("/community/events", params={"suburb": "Surry Hills", "user_id": "user_2"})
    assert events_resp.status_code == 200
    events_payload = events_resp.json()
    assert isinstance(events_payload, list)
    assert events_payload, "Expected at least one event"
    assert_exact_keys(events_payload[0], event_keys, "community/events:list[0]")

    rsvp_resp = client.post(
        f"/community/events/{event_id}/rsvp",
        json={"user_id": "user_2", "status": "attending"},
        headers=headers,
    )
    assert rsvp_resp.status_code == 200
    assert_exact_keys(rsvp_resp.json(), event_keys, "community/events/{id}/rsvp")

    member_group_id = next(
        (item["id"] for item in groups_payload if item["membership_status"] == "member"),
        None,
    )
    if not member_group_id:
        fallback_group_id = groups_payload[0]["id"]
        join_resp = client.post(
            f"/community/groups/{fallback_group_id}/join",
            json={"user_id": "user_2"},
            headers=headers,
        )
        assert join_resp.status_code == 200
        member_group_id = fallback_group_id

    checkin_resp = client.post(
        f"/community/groups/{member_group_id}/challenges/participate",
        json={
            "user_id": "user_2",
            "challenge_type": "clean_park_streak",
            "contribution_count": 1,
            "note": "Contract freeze check-in",
        },
        headers=headers,
    )
    assert checkin_resp.status_code == 200
    checkin_payload = checkin_resp.json()
    assert_exact_keys(checkin_payload, challenge_result_keys, "community/groups/{id}/challenges/participate")
    assert_exact_keys(checkin_payload["challenge"], challenge_keys, "community challenge payload")

    # Journey B: book a groomer
    providers_resp = client.get("/services/providers", params={"category": "grooming"})
    assert providers_resp.status_code == 200
    providers_payload = providers_resp.json()
    assert isinstance(providers_payload, list)
    assert providers_payload, "Expected at least one groomer provider"
    assert_exact_keys(providers_payload[0], provider_keys, "services/providers[0]")
    provider_id = providers_payload[0]["id"]

    booking_resp = None
    for day_offset in range(1, 15):
        booking_date = (datetime.utcnow().date() + timedelta(days=day_offset)).isoformat()
        availability_resp = client.get(
            f"/services/providers/{provider_id}/availability",
            params={"date": booking_date},
        )
        assert availability_resp.status_code == 200
        availability_payload = availability_resp.json()
        assert isinstance(availability_payload, list)
        if availability_payload:
            assert_exact_keys(availability_payload[0], availability_slot_keys, "services/providers/{id}/availability[0]")
        candidate_slots = [slot for slot in availability_payload if slot.get("available")]
        if not candidate_slots:
            continue
        for slot in candidate_slots:
            booking_resp = client.post(
                "/services/bookings",
                json={
                    "user_id": "user_2",
                    "provider_id": provider_id,
                    "pet_name": "Milo",
                    "date": slot["date"],
                    "time_slot": slot["time_slot"],
                    "note": "Contract freeze booking",
                },
                headers=headers,
            )
            if booking_resp.status_code == 200:
                break
        if booking_resp is not None and booking_resp.status_code == 200:
            break
    assert booking_resp is not None and booking_resp.status_code == 200
    assert_exact_keys(booking_resp.json(), booking_keys, "services/bookings:create")

    register_resp = client.post(
        "/notifications/register-device",
        json={"user_id": "user_2", "device_token": "contract-freeze-token", "platform": "android"},
        headers=headers,
    )
    assert register_resp.status_code == 200
    assert register_resp.json() == {"status": "ok"}

    notifications_resp = client.get(
        "/notifications",
        params={"user_id": "user_2"},
        headers=headers,
    )
    assert notifications_resp.status_code == 200
    notifications_payload = notifications_resp.json()
    assert isinstance(notifications_payload, list)
    if notifications_payload:
        assert_exact_keys(notifications_payload[0], notification_keys, "notifications:list[0]")
        mark_read = client.post(
            f"/notifications/{notifications_payload[0]['id']}/read",
            params={"user_id": "user_2"},
            headers=headers,
        )
        assert mark_read.status_code == 200
        assert_exact_keys(mark_read.json(), notification_keys, "notifications/read")


def test_quote_request_creates_targets_and_provider_notifications():
    requester_login = client.post("/auth/login", json={"user_id": "user_2", "password": "petsocial-demo"})
    assert requester_login.status_code == 200
    requester_token = requester_login.json()["access_token"]

    create = client.post(
        "/services/quotes/request",
        json={
            "user_id": "user_2",
            "category": "dog_walking",
            "suburb": "Surry Hills",
            "preferred_window": "Weekday mornings",
            "pet_details": "1 adult labrador, leash trained",
            "note": "Need recurring weekdays",
        },
        headers={"Authorization": f"Bearer {requester_token}"},
    )
    assert create.status_code == 200
    payload = create.json()
    assert payload["quote_request"]["status"] == "pending"
    assert len(payload["targets"]) >= 1
    assert len(payload["targets"]) <= 3

    first_target_owner = payload["targets"][0]["owner_user_id"]
    owner_notifications = client.get("/notifications", params={"user_id": first_target_owner})
    assert owner_notifications.status_code == 200
    assert any(item["deep_link"] == f"quote:{payload['quote_request']['id']}" for item in owner_notifications.json())


def test_quote_response_updates_response_time_metric():
    requester_login = client.post("/auth/login", json={"user_id": "user_2", "password": "petsocial-demo"})
    assert requester_login.status_code == 200
    requester_token = requester_login.json()["access_token"]

    create = client.post(
        "/services/quotes/request",
        json={
            "user_id": "user_2",
            "category": "grooming",
            "suburb": "Surry Hills",
            "preferred_window": "Saturday 10:00-12:00",
            "pet_details": "Toy cavoodle, anxious at dryers",
            "note": "Looking for gentle handling",
        },
        headers={"Authorization": f"Bearer {requester_token}"},
    )
    assert create.status_code == 200
    payload = create.json()
    quote_id = payload["quote_request"]["id"]
    first_target = payload["targets"][0]

    owner_user_id = first_target["owner_user_id"]
    owner_login = client.post("/auth/login", json={"user_id": owner_user_id, "password": "petsocial-demo"})
    assert owner_login.status_code == 200
    owner_token = owner_login.json()["access_token"]

    respond = client.post(
        f"/services/quotes/{quote_id}/respond",
        json={
            "actor_user_id": owner_user_id,
            "provider_id": first_target["provider_id"],
            "decision": "accepted",
            "message": "Can do this slot.",
        },
        headers={"Authorization": f"Bearer {owner_token}"},
    )
    assert respond.status_code == 200
    assert respond.json()["quote_request"]["status"] in {"responded", "closed"}

    providers = client.get("/services/providers", params={"suburb": "Surry Hills"})
    assert providers.status_code == 200
    matched = next((item for item in providers.json() if item["id"] == first_target["provider_id"]), None)
    assert matched is not None
    assert matched["response_time_minutes"] is not None


def test_quote_offer_creates_structured_offer_and_notifies_requester():
    requester_login = client.post("/auth/login", json={"user_id": "user_2", "password": "petsocial-demo"})
    assert requester_login.status_code == 200
    requester_token = requester_login.json()["access_token"]

    create = client.post(
        "/services/quotes/request",
        json={
            "user_id": "user_2",
            "category": "dog_walking",
            "suburb": "Surry Hills",
            "preferred_window": "Weekday mornings",
            "pet_details": "2-year old kelpie, high energy",
            "note": "",
        },
        headers={"Authorization": f"Bearer {requester_token}"},
    )
    assert create.status_code == 200
    quote_payload = create.json()
    quote_id = quote_payload["quote_request"]["id"]
    target = quote_payload["targets"][0]

    owner_user_id = target["owner_user_id"]
    owner_login = client.post("/auth/login", json={"user_id": owner_user_id, "password": "petsocial-demo"})
    assert owner_login.status_code == 200
    owner_token = owner_login.json()["access_token"]

    offer_resp = client.post(
        f"/services/quotes/{quote_id}/offer",
        json={
            "actor_user_id": owner_user_id,
            "provider_id": target["provider_id"],
            "price_cents": 6200,
            "currency": "AUD",
            "proposed_date": (datetime.utcnow().date() + timedelta(days=5)).isoformat(),
            "proposed_time_slot": "10:00",
            "expires_at": (datetime.utcnow() + timedelta(days=2)).isoformat(),
            "note": "Can do this as a recurring weekday slot.",
        },
        headers={"Authorization": f"Bearer {owner_token}"},
    )
    assert offer_resp.status_code == 200
    offer_payload = offer_resp.json()
    assert offer_payload["quote_request_id"] == quote_id
    assert offer_payload["provider_id"] == target["provider_id"]
    assert offer_payload["price_cents"] == 6200
    assert offer_payload["currency"] == "AUD"

    refreshed_request, refreshed_targets = service_store.get_quote_request(quote_id)
    assert refreshed_request.status in {"responded", "closed"}
    matched_target = next((row for row in refreshed_targets if row.provider_id == target["provider_id"]), None)
    assert matched_target is not None
    assert matched_target.status == "accepted"

    requester_notifications = client.get("/notifications", params={"user_id": "user_2"})
    assert requester_notifications.status_code == 200
    assert any(
        item["title"] == "New quote offer" and item["deep_link"] == f"quote:{quote_id}"
        for item in requester_notifications.json()
    )


def test_provider_inbox_lists_pending_quote_requests():
    requester_login = client.post("/auth/login", json={"user_id": "user_2", "password": "petsocial-demo"})
    assert requester_login.status_code == 200
    requester_token = requester_login.json()["access_token"]

    create = client.post(
        "/services/quotes/request",
        json={
            "user_id": "user_2",
            "category": "grooming",
            "suburb": "Surry Hills",
            "preferred_window": "Saturday afternoon",
            "pet_details": "Cavoodle, needs low-noise groom",
            "note": "",
        },
        headers={"Authorization": f"Bearer {requester_token}"},
    )
    assert create.status_code == 200
    quote_payload = create.json()
    quote_id = quote_payload["quote_request"]["id"]
    target = quote_payload["targets"][0]

    owner_user_id = target["owner_user_id"]
    owner_login = client.post("/auth/login", json={"user_id": owner_user_id, "password": "petsocial-demo"})
    assert owner_login.status_code == 200
    owner_token = owner_login.json()["access_token"]

    inbox_resp = client.get(
        "/services/provider/inbox",
        params={
            "actor_user_id": owner_user_id,
            "include_resolved": False,
            "limit": 50,
        },
        headers={"Authorization": f"Bearer {owner_token}"},
    )
    assert inbox_resp.status_code == 200
    inbox_payload = inbox_resp.json()
    assert inbox_payload["actor_user_id"] == owner_user_id
    assert inbox_payload["total"] >= 1
    assert any(
        item["item_type"] == "quote_request" and item["quote_request_id"] == quote_id
        for item in inbox_payload["items"]
    )


def test_quote_reminder_dispatch_at_15_minutes():
    requester_login = client.post("/auth/login", json={"user_id": "user_2", "password": "petsocial-demo"})
    assert requester_login.status_code == 200
    requester_token = requester_login.json()["access_token"]

    create = client.post(
        "/services/quotes/request",
        json={
            "user_id": "user_2",
            "category": "dog_walking",
            "suburb": "Surry Hills",
            "preferred_window": "Any weekday evening",
            "pet_details": "Medium-size rescue, gentle temperament",
            "note": "",
        },
        headers={"Authorization": f"Bearer {requester_token}"},
    )
    assert create.status_code == 200
    payload = create.json()
    quote_id = payload["quote_request"]["id"]
    target = payload["targets"][0]

    old_timestamp = (datetime.utcnow() - timedelta(minutes=20)).isoformat()
    with sqlite3.connect(service_store.db_path) as conn:
        conn.execute(
            """
            UPDATE quote_request_targets
            SET created_at = ?, reminder_15_sent = 0, reminder_60_sent = 0
            WHERE quote_request_id = ? AND provider_id = ?
            """,
            (old_timestamp, quote_id, target["provider_id"]),
        )
        conn.commit()

    trigger = client.get("/services/providers", params={"suburb": "Surry Hills"})
    assert trigger.status_code == 200

    owner_notifications = client.get("/notifications", params={"user_id": target["owner_user_id"]})
    assert owner_notifications.status_code == 200
    assert any(
        item["title"] == "Quote request reminder" and item["deep_link"] == f"quote:{quote_id}"
        for item in owner_notifications.json()
    )


def test_quote_reminder_60_minutes_sent_once_without_late_15_minute_duplicate():
    requester_login = client.post("/auth/login", json={"user_id": "user_2", "password": "petsocial-demo"})
    assert requester_login.status_code == 200
    requester_token = requester_login.json()["access_token"]

    create = client.post(
        "/services/quotes/request",
        json={
            "user_id": "user_2",
            "category": "dog_walking",
            "suburb": "Surry Hills",
            "preferred_window": "Weekend mornings",
            "pet_details": "Senior mixed breed, calm and social",
            "note": "",
        },
        headers={"Authorization": f"Bearer {requester_token}"},
    )
    assert create.status_code == 200
    payload = create.json()
    quote_id = payload["quote_request"]["id"]
    target = payload["targets"][0]

    old_timestamp = (datetime.utcnow() - timedelta(minutes=70)).isoformat()
    with sqlite3.connect(service_store.db_path) as conn:
        conn.execute(
            """
            UPDATE quote_request_targets
            SET created_at = ?, reminder_15_sent = 0, reminder_60_sent = 0
            WHERE quote_request_id = ? AND provider_id = ?
            """,
            (old_timestamp, quote_id, target["provider_id"]),
        )
        conn.commit()

    trigger_first = client.get("/services/providers", params={"suburb": "Surry Hills"})
    assert trigger_first.status_code == 200
    first_notifications = client.get("/notifications", params={"user_id": target["owner_user_id"]})
    assert first_notifications.status_code == 200
    first_count = sum(
        1
        for item in first_notifications.json()
        if item["title"] == "Quote request reminder" and item["deep_link"] == f"quote:{quote_id}"
    )
    assert first_count == 1

    trigger_second = client.get("/services/providers", params={"suburb": "Surry Hills"})
    assert trigger_second.status_code == 200
    second_notifications = client.get("/notifications", params={"user_id": target["owner_user_id"]})
    assert second_notifications.status_code == 200
    second_count = sum(
        1
        for item in second_notifications.json()
        if item["title"] == "Quote request reminder" and item["deep_link"] == f"quote:{quote_id}"
    )
    assert second_count == 1


def test_vet_coach_session_and_spotlight_activation():
    vet_login = client.post("/auth/login", json={"user_id": "user_1", "password": "petsocial-demo"})
    assert vet_login.status_code == 200
    vet_token = vet_login.json()["access_token"]

    before_profile = client.get(
        "/services/vet-coach/profile",
        params={"user_id": "user_1"},
        headers={"Authorization": f"Bearer {vet_token}"},
    )
    assert before_profile.status_code == 200
    before_minutes = int(before_profile.json()["spotlight_minutes"])

    session = client.post(
        "/services/vet-coach/sessions",
        json={
            "actor_user_id": "user_1",
            "duration_minutes": 20,
            "quality_score": 0.9,
            "topic": "Dermatitis triage prompts",
            "note": "Added caution guidance for persistent itch",
        },
        headers={"Authorization": f"Bearer {vet_token}"},
    )
    assert session.status_code == 200
    session_payload = session.json()
    assert session_payload["minutes_earned"] > 0
    assert session_payload["profile"]["spotlight_minutes"] >= before_minutes + session_payload["minutes_earned"]

    activate = client.post(
        "/services/vet-coach/spotlight/activate",
        json={
            "actor_user_id": "user_1",
            "minutes": 10,
        },
        headers={"Authorization": f"Bearer {vet_token}"},
    )
    assert activate.status_code == 200
    activated_profile = activate.json()["profile"]
    assert activated_profile["highlighted_until"] is not None


def test_vet_verify_groomer_sets_vet_checked_tag():
    vet_login = client.post("/auth/login", json={"user_id": "user_1", "password": "petsocial-demo"})
    assert vet_login.status_code == 200
    vet_token = vet_login.json()["access_token"]

    verify = client.post(
        "/services/providers/svc_3/vet-verify",
        json={
            "actor_user_id": "user_1",
            "decision": "approved",
            "confidence_score": 0.9,
            "note": "Strong hygiene process and stress-aware handling",
        },
        headers={"Authorization": f"Bearer {vet_token}"},
    )
    assert verify.status_code == 200
    payload = verify.json()
    assert payload["verification"]["decision"] == "approved"
    assert payload["verification"]["spotlight_minutes_earned"] > 0
    assert payload["provider"]["vet_checked"] is True
    assert payload["provider"]["vet_checked_by"] == "user_1"
    assert payload["provider"]["vet_checked_until"] is not None

    providers = client.get("/services/providers", params={"suburb": "Redfern"})
    assert providers.status_code == 200
    verified_provider = next((item for item in providers.json() if item["id"] == "svc_3"), None)
    assert verified_provider is not None
    assert verified_provider["vet_checked"] is True


def test_quote_sprint_metrics_surface_for_responding_provider():
    provider_login = client.post("/auth/login", json={"user_id": "user_4", "password": "petsocial-demo"})
    assert provider_login.status_code == 200
    provider_token = provider_login.json()["access_token"]
    _enable_provider_mode("user_4", provider_token)

    create_provider = client.post(
        "/services/providers",
        json={
            "user_id": "user_4",
            "name": "Sprint Metrics Grooming",
            "category": "grooming",
            "suburb": "Surry Hills",
            "description": "Quick quote-response specialist",
            "price_from": 43,
        },
        headers={"Authorization": f"Bearer {provider_token}"},
    )
    assert create_provider.status_code == 200
    provider_id = create_provider.json()["id"]

    requester_login = client.post("/auth/login", json={"user_id": "user_2", "password": "petsocial-demo"})
    assert requester_login.status_code == 200
    requester_token = requester_login.json()["access_token"]

    create_quote = client.post(
        "/services/quotes/request",
        json={
            "user_id": "user_2",
            "category": "grooming",
            "suburb": "Surry Hills",
            "preferred_window": "Weekday afternoons",
            "pet_details": "Mini poodle, skin-sensitive",
            "note": "",
        },
        headers={"Authorization": f"Bearer {requester_token}"},
    )
    assert create_quote.status_code == 200
    quote_payload = create_quote.json()
    quote_id = quote_payload["quote_request"]["id"]
    target = next((item for item in quote_payload["targets"] if item["provider_id"] == provider_id), None)
    assert target is not None

    respond = client.post(
        f"/services/quotes/{quote_id}/respond",
        json={
            "actor_user_id": "user_4",
            "provider_id": provider_id,
            "decision": "accepted",
            "message": "We can take this booking",
        },
        headers={"Authorization": f"Bearer {provider_token}"},
    )
    assert respond.status_code == 200

    providers = client.get("/services/providers", params={"suburb": "Surry Hills", "q": "Sprint Metrics Grooming"})
    assert providers.status_code == 200
    assert providers.json()
    sprint_provider = next((item for item in providers.json() if item["id"] == provider_id), None)
    assert sprint_provider is not None
    assert sprint_provider["quote_response_rate_pct"] >= 100
    assert sprint_provider["quote_response_streak"] >= 1
    assert sprint_provider["quote_sprint_tier"] in {"none", "bronze", "silver", "gold", "platinum"}


def test_group_challenge_participation_and_growth_rewards():
    user_login = client.post("/auth/login", json={"user_id": "user_1", "password": "petsocial-demo"})
    assert user_login.status_code == 200
    user_token = user_login.json()["access_token"]

    groups_response = client.get("/community/groups", params={"user_id": "user_1"})
    assert groups_response.status_code == 200
    joined_group = next((group for group in groups_response.json() if group["membership_status"] == "member"), None)
    assert joined_group is not None
    group_id = joined_group["id"]

    challenges_before = client.get(
        f"/community/groups/{group_id}/challenges",
        params={"user_id": "user_1"},
    )
    assert challenges_before.status_code == 200
    assert any(item["challenge"]["type"] == "clean_park_streak" for item in challenges_before.json())

    participate = client.post(
        f"/community/groups/{group_id}/challenges/participate",
        json={
            "user_id": "user_1",
            "challenge_type": "clean_park_streak",
            "contribution_count": 2,
            "note": "Cleanup check-in",
        },
        headers={"Authorization": f"Bearer {user_token}"},
    )
    assert participate.status_code == 200
    participation_payload = participate.json()
    assert participation_payload["challenge"]["type"] == "clean_park_streak"
    assert participation_payload["my_contribution_count"] >= 2

    invite = client.post(
        "/community/invites",
        json={"group_id": group_id, "inviter_user_id": "user_1"},
        headers={"Authorization": f"Bearer {user_token}"},
    )
    assert invite.status_code == 200
    token = invite.json()["token"]

    onboarding = client.post(
        "/community/onboarding/complete",
        json={
            "invite_token": token,
            "owner_name": "Riley",
            "dog_name": "Mochi",
            "suburb": joined_group["suburb"],
            "share_photo_to_group": False,
        },
    )
    assert onboarding.status_code == 200

    groups_after = client.get("/community/groups", params={"user_id": "user_1"})
    assert groups_after.status_code == 200
    updated_group = next((group for group in groups_after.json() if group["id"] == group_id), None)
    assert updated_group is not None
    assert updated_group["my_pack_builder_points"] >= joined_group.get("my_pack_builder_points", 0)
    assert updated_group["cooperative_score"] >= 1


def test_group_onboarding_uses_authenticated_user_and_is_idempotent():
    inviter_login = client.post("/auth/login", json={"user_id": "user_1", "password": "petsocial-demo"})
    assert inviter_login.status_code == 200
    inviter_headers = {"Authorization": f"Bearer {inviter_login.json()['access_token']}"}

    groups_before = client.get("/community/groups", params={"user_id": "user_1"})
    assert groups_before.status_code == 200
    inviter_group = next((group for group in groups_before.json() if group["membership_status"] == "member"), None)
    assert inviter_group is not None
    group_id = inviter_group["id"]
    member_count_before = inviter_group["member_count"]

    invite = client.post(
        "/community/invites",
        json={"group_id": group_id, "inviter_user_id": "user_1"},
        headers=inviter_headers,
    )
    assert invite.status_code == 200
    invite_token = invite.json()["token"]

    joining_user = f"user_invited_{uuid4().hex[:8]}"
    joining_login = client.post("/auth/login", json={"user_id": joining_user, "password": "petsocial-demo"})
    assert joining_login.status_code == 200
    joining_headers = {"Authorization": f"Bearer {joining_login.json()['access_token']}"}

    first = client.post(
        "/community/onboarding/complete",
        json={
            "invite_token": invite_token,
            "owner_name": "Jordan",
            "dog_name": "Poppy",
            "suburb": inviter_group["suburb"],
            "share_photo_to_group": False,
        },
        headers=joining_headers,
    )
    assert first.status_code == 200
    first_payload = first.json()
    assert first_payload["user_id"] == joining_user
    assert first_payload["group_id"] == group_id
    assert first_payload["membership_status"] == "member"

    groups_after_first = client.get("/community/groups", params={"user_id": "user_1"})
    assert groups_after_first.status_code == 200
    inviter_group_after_first = next((group for group in groups_after_first.json() if group["id"] == group_id), None)
    assert inviter_group_after_first is not None
    assert inviter_group_after_first["member_count"] >= member_count_before + 1
    member_count_after_first = inviter_group_after_first["member_count"]

    second = client.post(
        "/community/onboarding/complete",
        json={
            "invite_token": invite_token,
            "owner_name": "Jordan",
            "dog_name": "Poppy",
            "suburb": inviter_group["suburb"],
            "share_photo_to_group": False,
        },
        headers=joining_headers,
    )
    assert second.status_code == 200
    second_payload = second.json()
    assert second_payload["user_id"] == joining_user
    assert second_payload["group_id"] == group_id
    assert second_payload["membership_status"] == "member"

    groups_after_second = client.get("/community/groups", params={"user_id": "user_1"})
    assert groups_after_second.status_code == 200
    inviter_group_after_second = next((group for group in groups_after_second.json() if group["id"] == group_id), None)
    assert inviter_group_after_second is not None
    assert inviter_group_after_second["member_count"] == member_count_after_first

    joiner_groups = client.get("/community/groups", params={"user_id": joining_user})
    assert joiner_groups.status_code == 200
    joiner_group_view = next((group for group in joiner_groups.json() if group["id"] == group_id), None)
    assert joiner_group_view is not None
    assert joiner_group_view["membership_status"] == "member"


def test_group_onboarding_requires_auth_when_auth_required(monkeypatch):
    monkeypatch.setattr(auth_module, "AUTH_REQUIRED", True)

    inviter_login = client.post("/auth/login", json={"user_id": "user_1", "password": "petsocial-demo"})
    assert inviter_login.status_code == 200
    inviter_headers = {"Authorization": f"Bearer {inviter_login.json()['access_token']}"}

    groups_response = client.get("/community/groups", params={"user_id": "user_1"})
    assert groups_response.status_code == 200
    inviter_group = next((group for group in groups_response.json() if group["membership_status"] == "member"), None)
    assert inviter_group is not None

    invite = client.post(
        "/community/invites",
        json={"group_id": inviter_group["id"], "inviter_user_id": "user_1"},
        headers=inviter_headers,
    )
    assert invite.status_code == 200

    onboarding_without_auth = client.post(
        "/community/onboarding/complete",
        json={
            "invite_token": invite.json()["token"],
            "owner_name": "NoAuth User",
            "dog_name": "NoAuth Dog",
            "suburb": inviter_group["suburb"],
            "share_photo_to_group": False,
        },
    )
    assert onboarding_without_auth.status_code == 401


def test_group_invite_create_rate_limited(monkeypatch):
    monkeypatch.setattr(community_router, "INVITE_CREATE_RATE_LIMIT_MAX", 1)
    monkeypatch.setattr(community_router, "INVITE_CREATE_RATE_LIMIT_WINDOW", timedelta(minutes=10))
    community_router.COMMUNITY_ACTION_RATE_LIMIT_HISTORY.clear()

    inviter_login = client.post("/auth/login", json={"user_id": "user_1", "password": "petsocial-demo"})
    assert inviter_login.status_code == 200
    inviter_headers = {"Authorization": f"Bearer {inviter_login.json()['access_token']}"}

    groups_response = client.get("/community/groups", params={"user_id": "user_1"})
    assert groups_response.status_code == 200
    inviter_group = next((group for group in groups_response.json() if group["membership_status"] == "member"), None)
    assert inviter_group is not None

    first = client.post(
        "/community/invites",
        json={"group_id": inviter_group["id"], "inviter_user_id": "user_1"},
        headers=inviter_headers,
    )
    assert first.status_code == 200

    second = client.post(
        "/community/invites",
        json={"group_id": inviter_group["id"], "inviter_user_id": "user_1"},
        headers=inviter_headers,
    )
    assert second.status_code == 429
    assert "invite_create" in second.json()["detail"]


def test_group_onboarding_complete_rate_limited(monkeypatch):
    monkeypatch.setattr(community_router, "ONBOARDING_COMPLETE_RATE_LIMIT_MAX", 1)
    monkeypatch.setattr(community_router, "ONBOARDING_COMPLETE_RATE_LIMIT_WINDOW", timedelta(minutes=10))
    community_router.COMMUNITY_ACTION_RATE_LIMIT_HISTORY.clear()

    inviter_login = client.post("/auth/login", json={"user_id": "user_1", "password": "petsocial-demo"})
    assert inviter_login.status_code == 200
    inviter_headers = {"Authorization": f"Bearer {inviter_login.json()['access_token']}"}

    groups_response = client.get("/community/groups", params={"user_id": "user_1"})
    assert groups_response.status_code == 200
    inviter_group = next((group for group in groups_response.json() if group["membership_status"] == "member"), None)
    assert inviter_group is not None

    invite = client.post(
        "/community/invites",
        json={"group_id": inviter_group["id"], "inviter_user_id": "user_1"},
        headers=inviter_headers,
    )
    assert invite.status_code == 200
    invite_token = invite.json()["token"]

    first = client.post(
        "/community/onboarding/complete",
        json={
            "invite_token": invite_token,
            "owner_name": "Rate Limited User",
            "dog_name": "Rate Limited Dog",
            "suburb": inviter_group["suburb"],
            "share_photo_to_group": False,
        },
    )
    assert first.status_code == 200

    second = client.post(
        "/community/onboarding/complete",
        json={
            "invite_token": invite_token,
            "owner_name": "Rate Limited User",
            "dog_name": "Rate Limited Dog",
            "suburb": inviter_group["suburb"],
            "share_photo_to_group": False,
        },
    )
    assert second.status_code == 429
    assert "onboarding_complete" in second.json()["detail"]


def test_lost_found_post_creation_sets_created_at():
    created = client.post(
        "/community/posts",
        json={
            "type": "lost_found",
            "user_id": "user_lf_create_1",
            "title": "Lost dog near test block",
            "body": "Black lab with blue harness, seen 10 minutes ago.",
            "suburb": "Surry Hills",
            "alert_type": "lost",
            "pet_name": "Milo",
            "pet_traits": "Black labrador, blue harness, friendly but anxious",
            "last_seen_at": "2026-02-21T09:15:00Z",
            "last_seen_location": "Crown St near Baptist St",
            "contact_pref": "Call +61 400 000 111",
            "photo_urls": ["https://example.com/milo-1.jpg"],
        },
    )
    assert created.status_code == 200
    payload = created.json()
    assert payload["type"] == "lost_found"
    assert payload["created_at"] is not None
    assert payload["alert_status"] == "open"
    assert payload["pet_name"] == "Milo"
    assert payload["last_seen_location"] == "Crown St near Baptist St"
    assert payload["photo_urls"] == ["https://example.com/milo-1.jpg"]
    parsed = datetime.fromisoformat(payload["created_at"].replace("Z", "+00:00"))
    assert parsed.tzinfo is not None


def test_lost_found_sort_returns_only_lost_found_posts():
    created_group_post = client.post(
        "/community/posts",
        json={
            "type": "group_post",
            "user_id": "user_lf_sort_group_1",
            "title": "General community update",
            "body": "This should not appear in lost/found sorted feed.",
            "suburb": "Surry Hills",
        },
    )
    assert created_group_post.status_code == 200

    created_alert = client.post(
        "/community/posts",
        json={
            "type": "lost_found",
            "user_id": "user_lf_sort_alert_1",
            "title": "Found dog near station",
            "body": "Small terrier currently safe with nearby resident.",
            "suburb": "Surry Hills",
            "alert_type": "found",
            "pet_traits": "Small tan terrier",
            "last_seen_location": "Central Station concourse",
            "contact_pref": "Message in app",
        },
    )
    assert created_alert.status_code == 200

    feed = client.get(
        "/community/posts",
        params={
            "suburb": "Surry Hills",
            "sort_by": "lost_found",
        },
    )
    assert feed.status_code == 200
    items = feed.json()
    assert items
    assert all(item["type"] == "lost_found" for item in items)


def test_lost_found_resolve_updates_status_and_archive_order():
    owner_user = "user_lf_resolve_owner"
    first_alert = client.post(
        "/community/posts",
        json={
            "type": "lost_found",
            "user_id": owner_user,
            "title": "Lost shepherd test",
            "body": "Nervous shepherd, red leash attached.",
            "suburb": "Surry Hills",
            "alert_type": "lost",
            "pet_traits": "German shepherd, red leash",
            "last_seen_location": "Foveaux St park",
            "contact_pref": "SMS +61 400 000 222",
        },
    )
    assert first_alert.status_code == 200
    first_id = first_alert.json()["id"]

    second_alert = client.post(
        "/community/posts",
        json={
            "type": "lost_found",
            "user_id": owner_user,
            "title": "Found spaniel test",
            "body": "Safe at nearby cafe patio.",
            "suburb": "Surry Hills",
            "alert_type": "found",
            "pet_traits": "Brown spaniel, green collar",
            "last_seen_location": "Campbell St",
            "contact_pref": "Call front desk",
        },
    )
    assert second_alert.status_code == 200
    second_id = second_alert.json()["id"]

    resolved = client.post(
        f"/community/posts/{first_id}/resolve",
        json={
            "requester_user_id": owner_user,
            "status": "reunited",
            "note": "Owner confirmed pickup",
        },
    )
    assert resolved.status_code == 200
    resolved_payload = resolved.json()
    assert resolved_payload["alert_status"] == "reunited"
    assert resolved_payload["resolved_at"] is not None
    assert resolved_payload["resolved_note"] == "Owner confirmed pickup"

    feed = client.get(
        "/community/posts",
        params={"suburb": "Surry Hills", "sort_by": "lost_found"},
    )
    assert feed.status_code == 200
    items = feed.json()
    target = [item for item in items if item["id"] in {first_id, second_id}]
    assert len(target) == 2
    assert target[0]["id"] == second_id
    assert target[0]["alert_status"] == "open"
    assert target[1]["id"] == first_id
    assert target[1]["alert_status"] == "reunited"


def test_lost_found_resolve_requires_post_owner():
    owner_login = client.post("/auth/login", json={"user_id": "user_2", "password": "petsocial-demo"})
    assert owner_login.status_code == 200
    owner_token = owner_login.json()["access_token"]

    created = client.post(
        "/community/posts",
        json={
            "type": "lost_found",
            "user_id": "user_2",
            "title": "Owner-bound alert",
            "body": "Owner-only resolve should be enforced",
            "suburb": "Surry Hills",
            "alert_type": "lost",
            "pet_traits": "Small white terrier",
            "last_seen_location": "Crown St",
            "contact_pref": "In-app DM",
        },
        headers={"Authorization": f"Bearer {owner_token}"},
    )
    assert created.status_code == 200
    post_id = created.json()["id"]

    other_login = client.post("/auth/login", json={"user_id": "user_3", "password": "petsocial-demo"})
    assert other_login.status_code == 200
    other_token = other_login.json()["access_token"]

    forbidden = client.post(
        f"/community/posts/{post_id}/resolve",
        json={"requester_user_id": "user_3", "status": "reunited", "note": "Trying to resolve someone else's alert"},
        headers={"Authorization": f"Bearer {other_token}"},
    )
    assert forbidden.status_code == 403

    allowed = client.post(
        f"/community/posts/{post_id}/resolve",
        json={"requester_user_id": "user_2", "status": "reunited", "note": "Resolved by owner"},
        headers={"Authorization": f"Bearer {owner_token}"},
    )
    assert allowed.status_code == 200
    assert allowed.json()["alert_status"] == "reunited"


def test_lost_found_create_validation_requires_contact_fields():
    response = client.post(
        "/community/posts",
        json={
            "type": "lost_found",
            "user_id": "user_lf_validation_1",
            "title": "Missing details test",
            "body": "This should fail server-side validation",
            "suburb": "Surry Hills",
            "alert_type": "lost",
            "pet_traits": "Black lab",
            "last_seen_location": "Bourke St",
        },
    )
    assert response.status_code == 400
    assert "contact_pref" in response.json()["detail"]


def test_community_post_rate_limit():
    spam_user = f"user_rate_{int(datetime.utcnow().timestamp())}"
    for idx in range(POSTS_PER_WINDOW := 4):
        created = client.post(
            "/community/posts",
            json={
                "type": "group_post",
                "user_id": spam_user,
                "title": f"Rate limit test #{idx}",
                "body": "Burst posting test",
                "suburb": "Surry Hills",
            },
        )
        assert created.status_code == 200

    blocked = client.post(
        "/community/posts",
        json={
            "type": "group_post",
            "user_id": spam_user,
            "title": "Rate limit overflow",
            "body": "This should be throttled",
            "suburb": "Surry Hills",
        },
    )
    assert blocked.status_code == 429


def test_activation_analytics_endpoint_summarizes_events():
    suffix = uuid4().hex[:8]
    user_id = f"activation_user_{suffix}"
    login = client.post("/auth/login", json={"user_id": user_id, "password": "petsocial-demo"})
    assert login.status_code == 200
    headers = {"Authorization": f"Bearer {login.json()['access_token']}"}

    events_to_seed = [
        "activation_qr_scan_attempted",
        "activation_qr_scan_succeeded",
        "activation_otp_verify_failed",
    ]
    for event_name in events_to_seed:
        seeded = client.post(
            "/community/analytics/events",
            json={
                "user_id": user_id,
                "event": event_name,
                "category": "community",
                "metadata": {"source": "api_test"},
            },
            headers=headers,
        )
        assert seeded.status_code == 200

    diagnostic = client.post(
        "/community/diagnostics/events",
        json={
            "user_id": user_id,
            "kind": "error",
            "message": "activation_otp_verify_failed",
            "context": {"source": "api_test"},
        },
        headers=headers,
    )
    assert diagnostic.status_code == 200

    response = client.get(
        "/community/analytics/activation",
        params={"requester_user_id": user_id, "window_hours": 24},
    )
    assert response.status_code == 200
    payload = response.json()
    assert payload["requester_user_id"] == user_id
    assert payload["activation_event_count"] >= 3
    assert payload["activation_diagnostic_count"] >= 1
    assert payload["unique_user_count"] == 1
    assert payload["by_event"].get("activation_qr_scan_attempted", 0) >= 1
    assert payload["by_event"].get("activation_qr_scan_succeeded", 0) >= 1
    assert payload["by_event"].get("activation_otp_verify_failed", 0) >= 1
    assert payload["by_status"].get("attempted", 0) >= 1
    assert payload["by_status"].get("succeeded", 0) >= 1
    assert payload["by_status"].get("failed", 0) >= 1
    assert any(item.get("event") == "activation_otp_verify_failed" for item in payload["top_failures"])


def test_moderation_queue_and_block_filtering():
    created = client.post(
        "/community/posts",
        json={
            "type": "group_post",
            "user_id": "user_2",
            "title": "Moderation target post",
            "body": "Report and block flow validation.",
            "suburb": "Surry Hills",
        },
    )
    assert created.status_code == 200
    post_id = created.json()["id"]

    report = client.post(
        "/community/moderation/reports",
        json={
            "reporter_user_id": "user_4",
            "target_type": "post",
            "target_id": post_id,
            "reason": "Spam",
            "details": "Repeated promotional content",
        },
    )
    assert report.status_code == 200
    report_id = report.json()["id"]

    forbidden_queue = client.get(
        "/community/moderation/reports",
        params={"requester_user_id": "user_2"},
    )
    assert forbidden_queue.status_code == 403

    admin_queue = client.get(
        "/community/moderation/reports",
        params={"requester_user_id": "user_1"},
    )
    assert admin_queue.status_code == 200
    assert any(item["id"] == report_id and item["status"] == "pending" for item in admin_queue.json())

    block = client.post(
        "/community/moderation/blocks",
        json={"requester_user_id": "user_4", "target_user_id": "user_2"},
    )
    assert block.status_code == 200
    assert "user_2" in block.json()["blocked_user_ids"]

    user_4_feed = client.get("/community/posts", params={"user_id": "user_4"})
    assert user_4_feed.status_code == 200
    assert all(item.get("created_by") != "user_2" for item in user_4_feed.json())


def test_lost_found_distance_filter_and_coordinates():
    created = client.post(
        "/community/posts",
        json={
            "type": "lost_found",
            "user_id": "user_2",
            "title": "Distance-filter test alert",
            "body": "Coordinates should allow map and distance filtering.",
            "suburb": "Surry Hills",
            "alert_type": "lost",
            "pet_traits": "Golden retriever, red harness",
            "last_seen_location": "Devonshire St",
            "contact_pref": "In-app",
            "latitude": -33.8890,
            "longitude": 151.2100,
        },
    )
    assert created.status_code == 200
    post_id = created.json()["id"]

    nearby = client.get(
        "/community/posts",
        params={
            "post_type": "lost_found",
            "center_lat": -33.8891,
            "center_lng": 151.2101,
            "max_distance_km": 1.5,
        },
    )
    assert nearby.status_code == 200
    assert any(item["id"] == post_id for item in nearby.json())
    matched = next(item for item in nearby.json() if item["id"] == post_id)
    assert matched["latitude"] is not None
    assert matched["longitude"] is not None

    far = client.get(
        "/community/posts",
        params={
            "post_type": "lost_found",
            "center_lat": -33.95,
            "center_lng": 151.25,
            "max_distance_km": 1.0,
        },
    )
    assert far.status_code == 200
    assert all(item["id"] != post_id for item in far.json())


def test_services_recommendations_infer_suburb_from_dog_park_membership():
    response = client.get(
        "/services/recommendations",
        params={"user_id": "user_2", "category": "grooming"},
    )
    assert response.status_code == 200
    payload = response.json()
    assert payload["inferred_suburb"] is None
    assert payload["suburb_source"] == "none"
    assert isinstance(payload["providers"], list)
    if payload["providers"]:
        assert all(provider["category"] == "grooming" for provider in payload["providers"])


def test_services_recommendations_without_category_allow_mixed_results():
    response = client.get(
        "/services/recommendations",
        params={"user_id": "user_2"},
    )
    assert response.status_code == 200
    payload = response.json()
    categories = {provider["category"] for provider in payload["providers"]}
    assert categories.issubset({"dog_walking", "grooming"})
    assert payload["suburb_source"] in {"dog_park_membership", "group_membership", "none", "explicit_suburb"}


def test_quote_request_without_suburb_uses_membership_focus():
    requester_login = client.post("/auth/login", json={"user_id": "user_2", "password": "petsocial-demo"})
    assert requester_login.status_code == 200
    requester_token = requester_login.json()["access_token"]

    create = client.post(
        "/services/quotes/request",
        json={
            "user_id": "user_2",
            "category": "grooming",
            "preferred_window": "Saturday morning",
            "pet_details": "Toy poodle, anxious around loud dryers",
            "note": "Looking for a calm groomer",
        },
        headers={"Authorization": f"Bearer {requester_token}"},
    )
    assert create.status_code == 200
    payload = create.json()
    assert payload["quote_request"]["suburb"] == "Surry Hills"
    assert payload["quote_request"]["status"] == "pending"
    assert payload["targets"]


def test_community_post_comments_and_replies():
    community_router.POST_RATE_LIMIT_HISTORY.clear()
    owner_login = client.post("/auth/login", json={"user_id": "user_2", "password": "petsocial-demo"})
    assert owner_login.status_code == 200
    owner_headers = {"Authorization": f"Bearer {owner_login.json()['access_token']}"}

    peer_login = client.post("/auth/login", json={"user_id": "user_3", "password": "petsocial-demo"})
    assert peer_login.status_code == 200
    peer_headers = {"Authorization": f"Bearer {peer_login.json()['access_token']}"}

    created = client.post(
        "/community/posts",
        json={
            "type": "group_post",
            "user_id": "user_2",
            "title": "Sunday groom prep tips",
            "body": "Sharing products before our next meet-up.",
            "suburb": "Surry Hills",
        },
        headers=owner_headers,
    )
    assert created.status_code == 200
    post_id = created.json()["id"]

    first_comment = client.post(
        f"/community/posts/{post_id}/comments",
        json={"user_id": "user_3", "body": "Can you share your brush brand?"},
        headers=peer_headers,
    )
    assert first_comment.status_code == 200
    first_payload = first_comment.json()
    assert first_payload["parent_comment_id"] is None

    reply = client.post(
        f"/community/posts/{post_id}/comments",
        json={
            "user_id": "user_2",
            "body": "Yes, I use a slicker brush and detangler spray.",
            "parent_comment_id": first_payload["id"],
        },
        headers=owner_headers,
    )
    assert reply.status_code == 200
    reply_payload = reply.json()
    assert reply_payload["parent_comment_id"] == first_payload["id"]

    listed = client.get(f"/community/posts/{post_id}/comments")
    assert listed.status_code == 200
    comments = listed.json()
    assert any(comment["id"] == first_payload["id"] for comment in comments)
    assert any(comment["id"] == reply_payload["id"] for comment in comments)


def test_community_comment_rejects_missing_parent():
    community_router.POST_RATE_LIMIT_HISTORY.clear()
    owner_login = client.post("/auth/login", json={"user_id": "user_2", "password": "petsocial-demo"})
    assert owner_login.status_code == 200
    owner_headers = {"Authorization": f"Bearer {owner_login.json()['access_token']}"}

    peer_login = client.post("/auth/login", json={"user_id": "user_3", "password": "petsocial-demo"})
    assert peer_login.status_code == 200
    peer_headers = {"Authorization": f"Bearer {peer_login.json()['access_token']}"}

    created = client.post(
        "/community/posts",
        json={
            "type": "group_post",
            "user_id": "user_2",
            "title": "Weeknight walk planning",
            "body": "Posting a quick route for tomorrow.",
            "suburb": "Surry Hills",
        },
        headers=owner_headers,
    )
    assert created.status_code == 200
    post_id = created.json()["id"]

    bad_reply = client.post(
        f"/community/posts/{post_id}/comments",
        json={
            "user_id": "user_3",
            "body": "Replying to a missing parent",
            "parent_comment_id": "cmt_missing_parent",
        },
        headers=peer_headers,
    )
    assert bad_reply.status_code == 400
    assert bad_reply.json()["detail"] == "Parent comment not found"


def test_community_comments_pagination_and_moderation_controls():
    community_router.POST_RATE_LIMIT_HISTORY.clear()
    owner_login = client.post("/auth/login", json={"user_id": "user_2", "password": "petsocial-demo"})
    assert owner_login.status_code == 200
    owner_headers = {"Authorization": f"Bearer {owner_login.json()['access_token']}"}

    peer_login = client.post("/auth/login", json={"user_id": "user_3", "password": "petsocial-demo"})
    assert peer_login.status_code == 200
    peer_headers = {"Authorization": f"Bearer {peer_login.json()['access_token']}"}

    admin_login = client.post("/auth/login", json={"user_id": "user_1", "password": "petsocial-demo"})
    assert admin_login.status_code == 200
    admin_headers = {"Authorization": f"Bearer {admin_login.json()['access_token']}"}

    created = client.post(
        "/community/posts",
        json={
            "type": "group_post",
            "user_id": "user_2",
            "title": "Comment moderation flow",
            "body": "Testing comment pagination and moderator controls.",
            "suburb": "Surry Hills",
        },
        headers=owner_headers,
    )
    assert created.status_code == 200
    post_id = created.json()["id"]

    first = client.post(
        f"/community/posts/{post_id}/comments",
        json={"user_id": "user_3", "body": "First comment"},
        headers=peer_headers,
    )
    second = client.post(
        f"/community/posts/{post_id}/comments",
        json={"user_id": "user_3", "body": "Second comment"},
        headers=peer_headers,
    )
    third = client.post(
        f"/community/posts/{post_id}/comments",
        json={"user_id": "user_3", "body": "Third comment"},
        headers=peer_headers,
    )
    assert first.status_code == 200
    assert second.status_code == 200
    assert third.status_code == 200
    second_id = second.json()["id"]

    removed = client.post(
        f"/community/comments/{second_id}/moderate",
        json={"requester_user_id": "user_1", "action": "remove", "note": "Spam"},
        headers=admin_headers,
    )
    assert removed.status_code == 200
    assert removed.json()["status"] == "removed_by_moderator"

    regular_view = client.get(
        f"/community/posts/{post_id}/comments",
        params={"user_id": "user_2"},
        headers=owner_headers,
    )
    assert regular_view.status_code == 200
    assert all(comment["status"] == "active" for comment in regular_view.json())
    assert all(comment["id"] != second_id for comment in regular_view.json())

    paged = client.get(
        f"/community/posts/{post_id}/comments",
        params={"user_id": "user_2", "limit": 1, "offset": 1},
        headers=owner_headers,
    )
    assert paged.status_code == 200
    assert len(paged.json()) <= 1

    include_removed_admin = client.get(
        f"/community/posts/{post_id}/comments",
        params={"user_id": "user_1", "include_removed": "true"},
        headers=admin_headers,
    )
    assert include_removed_admin.status_code == 200
    assert any(comment["id"] == second_id and comment["status"] == "removed_by_moderator" for comment in include_removed_admin.json())

    restored = client.post(
        f"/community/comments/{second_id}/moderate",
        json={"requester_user_id": "user_1", "action": "restore", "note": "False positive"},
        headers=admin_headers,
    )
    assert restored.status_code == 200
    assert restored.json()["status"] == "active"
