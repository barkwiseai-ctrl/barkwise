import os
import sys

from fastapi.testclient import TestClient

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from app.main import app

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
