import os
import sys

from fastapi.testclient import TestClient

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

import app.auth as auth_module
from app.main import app

client = TestClient(app)


def _auth_headers(user_id: str) -> dict[str, str]:
    token, _ = auth_module.create_access_token(user_id=user_id)
    return {"Authorization": f"Bearer {token}"}


def test_activation_analytics_endpoint_summarizes_events():
    user_id = "user_2"
    headers = _auth_headers(user_id)

    for event_name in (
        "activation_qr_scan_attempted",
        "activation_qr_scan_succeeded",
        "activation_otp_verify_failed",
    ):
        seeded = client.post(
            "/community/analytics/events",
            json={
                "user_id": user_id,
                "event": event_name,
                "category": "community",
                "metadata": {"source": "activation_test"},
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
            "context": {"source": "activation_test"},
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

