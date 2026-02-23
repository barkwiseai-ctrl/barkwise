import os
import sqlite3
import sys
from datetime import datetime, timedelta

from fastapi.testclient import TestClient

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

import app.auth as auth_module
import app.routers.auth as auth_router
import app.routers.chat as chat_router
import app.routers.notifications as notifications_router
import app.services.security_audit as security_audit_service
from app.main import app
from app.services.service_store import service_store

client = TestClient(app)


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


def test_security_rate_limit_metrics_snapshot_tracks_429_surfaces(monkeypatch):
    security_audit_service.reset_rate_limit_metrics()
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


def test_create_service_provider_alias_routes():
    login = client.post("/auth/login", json={"user_id": "user_2", "password": "petsocial-demo"})
    assert login.status_code == 200
    token = login.json()["access_token"]

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
        params={"user_id": "user_2", "include_inactive": "true"},
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
    booking_keys = {"id", "owner_user_id", "provider_id", "pet_name", "date", "time_slot", "note", "status"}
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

    availability_resp = client.get(
        f"/services/providers/{provider_id}/availability",
        params={"date": "2026-03-01"},
    )
    assert availability_resp.status_code == 200
    availability_payload = availability_resp.json()
    assert isinstance(availability_payload, list)
    selected_booking_date = "2026-03-01"
    selected_booking_slot = "09:00"
    if availability_payload:
        assert_exact_keys(availability_payload[0], availability_slot_keys, "services/providers/{id}/availability[0]")
        available_slot = next((slot for slot in availability_payload if slot.get("available")), None)
        if available_slot:
            selected_booking_date = available_slot["date"]
            selected_booking_slot = available_slot["time_slot"]

    booking_resp = client.post(
        "/services/bookings",
        json={
            "user_id": "user_2",
            "provider_id": provider_id,
            "pet_name": "Milo",
            "date": selected_booking_date,
            "time_slot": selected_booking_slot,
            "note": "Contract freeze booking",
        },
        headers=headers,
    )
    assert booking_resp.status_code == 200
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
