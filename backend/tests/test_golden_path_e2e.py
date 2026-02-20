import os
import sys
from uuid import uuid4

from fastapi.testclient import TestClient

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from app.main import app

client = TestClient(app)


def _login(user_id: str) -> str:
    response = client.post("/auth/login", json={"user_id": user_id, "password": "petsocial-demo"})
    assert response.status_code == 200
    payload = response.json()
    return payload["access_token"]


def test_golden_path_login_services_booking_chat_notifications_read():
    owner_user = f"golden_owner_{uuid4().hex[:8]}"
    customer_user = f"golden_customer_{uuid4().hex[:8]}"

    owner_token = _login(owner_user)

    created_provider = client.post(
        "/services/providers",
        json={
            "user_id": owner_user,
            "name": f"Golden Walkers {uuid4().hex[:6]}",
            "category": "dog_walking",
            "suburb": "Surry Hills",
            "description": "Reliable neighborhood walks.",
            "price_from": 28,
        },
        headers={"Authorization": f"Bearer {owner_token}"},
    )
    assert created_provider.status_code == 200
    provider_id = created_provider.json()["id"]

    search = client.get("/services/providers", params={"q": "walk", "suburb": "Surry Hills", "sort_by": "relevance"})
    assert search.status_code == 200
    providers = search.json()
    assert any(item["id"] == provider_id for item in providers)

    availability = client.get(f"/services/providers/{provider_id}/availability", params={"date": "2026-02-21"})
    assert availability.status_code == 200
    slots = availability.json()
    assert isinstance(slots, list)
    assert slots
    selected_slot = next((slot for slot in slots if slot.get("available")), slots[0])
    time_slot = selected_slot["time_slot"]

    customer_token = _login(customer_user)
    booking = client.post(
        "/services/bookings",
        json={
            "user_id": customer_user,
            "provider_id": provider_id,
            "pet_name": "Milo",
            "date": "2026-02-21",
            "time_slot": time_slot,
            "note": "Golden path booking",
        },
        headers={"Authorization": f"Bearer {customer_token}"},
    )
    assert booking.status_code == 200
    booking_payload = booking.json()
    assert booking_payload["provider_id"] == provider_id

    chat = client.post(
        "/chat",
        json={"user_id": customer_user, "message": "Find a dog walker near me", "suburb": "Surry Hills"},
    )
    assert chat.status_code == 200
    assert isinstance(chat.json().get("answer"), str)
    assert chat.json().get("answer")

    owner_notifications = client.get("/notifications", params={"user_id": owner_user})
    assert owner_notifications.status_code == 200
    notifications = owner_notifications.json()
    assert notifications
    booking_notification = next((item for item in notifications if item.get("category") == "booking"), None)
    assert booking_notification is not None

    mark_read = client.post(
        f"/notifications/{booking_notification['id']}/read",
        params={"user_id": owner_user},
        headers={"Authorization": f"Bearer {owner_token}"},
    )
    assert mark_read.status_code == 200
    assert mark_read.json()["read"] is True
