import os
import sqlite3
import sys
from datetime import datetime, timedelta

from fastapi.testclient import TestClient

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

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

    list_response = client.get("/notifications", params={"user_id": "user_1"})
    assert list_response.status_code == 200


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
