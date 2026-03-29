import importlib
import os
import sqlite3
import sys
from datetime import datetime, timedelta, timezone

import pytest


sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from app.services.community_store import CommunityStore
from app.services.memory_store import MemoryStore
from app.services.rate_limiting import SlidingWindowHitStore, read_positive_int_env


def test_auth_ttl_invalid_env_falls_back(monkeypatch):
    monkeypatch.setenv("AUTH_TOKEN_TTL_HOURS", "not-a-number")
    sys.modules.pop("app.auth", None)
    auth = importlib.import_module("app.auth")
    assert auth.TOKEN_TTL_HOURS == 24


def test_auth_ttl_non_positive_env_falls_back(monkeypatch):
    monkeypatch.setenv("AUTH_TOKEN_TTL_HOURS", "0")
    sys.modules.pop("app.auth", None)
    auth = importlib.import_module("app.auth")
    assert auth.TOKEN_TTL_HOURS == 24


def test_auth_required_rejects_default_secret(monkeypatch):
    monkeypatch.setenv("AUTH_REQUIRED", "true")
    monkeypatch.delenv("AUTH_SECRET", raising=False)
    monkeypatch.setenv("AUTH_ALLOW_DEMO_LOGIN", "false")
    sys.modules.pop("app.auth", None)
    with pytest.raises(RuntimeError, match="AUTH_SECRET must be set"):
        importlib.import_module("app.auth")
    sys.modules.pop("app.auth", None)


def test_auth_required_rejects_demo_login_enabled(monkeypatch):
    monkeypatch.setenv("AUTH_REQUIRED", "true")
    monkeypatch.setenv("AUTH_SECRET", "hardening-secret")
    monkeypatch.setenv("AUTH_ALLOW_DEMO_LOGIN", "true")
    sys.modules.pop("app.auth", None)
    with pytest.raises(RuntimeError, match="AUTH_ALLOW_DEMO_LOGIN must be false"):
        importlib.import_module("app.auth")
    sys.modules.pop("app.auth", None)


def test_memory_store_handles_invalid_json_state(tmp_path):
    db_path = tmp_path / "memory.sqlite3"
    store = MemoryStore(db_path=str(db_path))
    with sqlite3.connect(str(db_path)) as conn:
        conn.execute(
            """
            INSERT INTO user_memory (user_id, profile_json, profile_accepted, field_locks_json, provider_state_json)
            VALUES (?, ?, ?, ?, ?)
            """,
            ("u1", "{bad", 1, "[]", "42"),
        )
        conn.commit()

    state = store.load_user_state("u1")
    assert state["profile_memory"] == {}
    assert state["field_locks"] == {}
    assert state["provider_state"] == {}
    assert state["profile_accepted"] is True


def test_memory_store_serializes_jsonable_state(tmp_path):
    db_path = tmp_path / "memory-jsonable.sqlite3"
    store = MemoryStore(db_path=str(db_path))
    created_at = datetime(2026, 3, 29, 9, 30, tzinfo=timezone.utc)

    store.save_user_state(
        "u_jsonable",
        profile_memory={
            "last_seen_at": created_at,
            "traits": {"friendly", "curious"},
        },
        profile_accepted=True,
        field_locks={"profile_complete": True},
        provider_state={
            "draft_steps": ("intro", "photos"),
        },
    )

    state = store.load_user_state("u_jsonable")
    assert state["profile_accepted"] is True
    assert state["profile_memory"]["last_seen_at"] == "2026-03-29T09:30:00Z"
    assert set(state["profile_memory"]["traits"]) == {"friendly", "curious"}
    assert state["provider_state"]["draft_steps"] == ["intro", "photos"]


def test_community_store_serializes_jsonable_snapshot_payload(tmp_path):
    db_path = tmp_path / "community.sqlite3"
    store = CommunityStore(db_path=str(db_path))
    created_at = datetime(2026, 3, 29, 10, 45, tzinfo=timezone.utc)

    store.save_state(
        {
            "community_analytics_events": [
                {
                    "event": "activation_qr_scan_attempted",
                    "metadata": {
                        "created_at": created_at,
                        "tags": {"scan", "camera"},
                    },
                }
            ],
            "community_diagnostic_events": [
                {
                    "message": "activation_qr_scan_failed",
                    "context": {
                        "steps": ("camera", "permission"),
                    },
                }
            ],
        }
    )

    payload = store.load_state()
    assert payload is not None
    assert payload["community_analytics_events"][0]["metadata"]["created_at"] == "2026-03-29T10:45:00Z"
    assert set(payload["community_analytics_events"][0]["metadata"]["tags"]) == {"scan", "camera"}
    assert payload["community_diagnostic_events"][0]["context"]["steps"] == ["camera", "permission"]


def test_auth_login_rate_limit_env_invalid_falls_back(monkeypatch):
    monkeypatch.setenv("AUTH_LOGIN_FAILURE_LIMIT", "oops")
    monkeypatch.setenv("AUTH_LOGIN_FAILURE_WINDOW_SECONDS", "0")
    sys.modules.pop("app.routers.auth", None)
    auth_router = importlib.import_module("app.routers.auth")
    assert auth_router.LOGIN_FAILURE_LIMIT == 8
    assert int(auth_router.LOGIN_FAILURE_WINDOW.total_seconds()) == 600


def test_chat_rate_limit_env_invalid_falls_back(monkeypatch):
    monkeypatch.setenv("CHAT_RATE_LIMIT_WINDOW_SECONDS", "-1")
    monkeypatch.setenv("CHAT_RATE_LIMIT_MAX_REQUESTS", "bad")
    monkeypatch.setenv("CHAT_STREAM_RATE_LIMIT_MAX_REQUESTS", "bad")
    monkeypatch.setenv("CHAT_ACTION_RATE_LIMIT_MAX_REQUESTS", "bad")
    sys.modules.pop("app.routers.chat", None)
    chat_router = importlib.import_module("app.routers.chat")
    assert int(chat_router.CHAT_RATE_LIMIT_WINDOW.total_seconds()) == 60
    assert chat_router.CHAT_RATE_LIMIT_MAX_REQUESTS == 12
    assert chat_router.CHAT_STREAM_RATE_LIMIT_MAX_REQUESTS == 6
    assert chat_router.CHAT_ACTION_RATE_LIMIT_MAX_REQUESTS == 10


def test_notifications_rate_limit_env_reads_valid_values(monkeypatch):
    monkeypatch.setenv("NOTIFICATIONS_DEVICE_REGISTER_RATE_LIMIT_MAX", "9")
    monkeypatch.setenv("NOTIFICATIONS_DEVICE_REGISTER_RATE_LIMIT_WINDOW_SECONDS", "420")
    sys.modules.pop("app.routers.notifications", None)
    notifications_router = importlib.import_module("app.routers.notifications")
    assert notifications_router.DEVICE_REGISTER_RATE_LIMIT_MAX == 9
    assert int(notifications_router.DEVICE_REGISTER_RATE_LIMIT_WINDOW.total_seconds()) == 420


def test_security_audit_metrics_persist_across_module_reload(monkeypatch, tmp_path):
    metrics_path = tmp_path / "security-audit.json"
    monkeypatch.setenv("SECURITY_AUDIT_METRICS_PATH", str(metrics_path))
    sys.modules.pop("app.services.security_audit", None)
    audit = importlib.import_module("app.services.security_audit")

    audit.reset_rate_limit_metrics()
    audit.record_rate_limit_hit(surface="chat_chat", key="user_1", detail="limit_exceeded")
    snapshot = audit.rate_limit_snapshot()
    assert snapshot["total_hits"] == 1
    assert snapshot["by_surface"].get("chat_chat") == 1
    assert metrics_path.exists()

    sys.modules.pop("app.services.security_audit", None)
    reloaded = importlib.import_module("app.services.security_audit")
    restored = reloaded.rate_limit_snapshot()
    assert restored["total_hits"] == 1
    assert restored["by_surface"].get("chat_chat") == 1
    assert len(restored["recent_hits"]) == 1


def test_security_audit_metrics_invalid_json_file_is_ignored(monkeypatch, tmp_path):
    metrics_path = tmp_path / "security-audit-bad.json"
    metrics_path.write_text("{bad", encoding="utf-8")
    monkeypatch.setenv("SECURITY_AUDIT_METRICS_PATH", str(metrics_path))
    sys.modules.pop("app.services.security_audit", None)
    audit = importlib.import_module("app.services.security_audit")
    snapshot = audit.rate_limit_snapshot()
    assert snapshot["total_hits"] == 0
    assert snapshot["by_surface"] == {}
    assert snapshot["recent_hits"] == []


def test_read_positive_int_env_falls_back_for_invalid_values(monkeypatch):
    monkeypatch.setenv("RATE_LIMIT_TEST_VALUE", "abc")
    assert read_positive_int_env("RATE_LIMIT_TEST_VALUE", 7) == 7
    monkeypatch.setenv("RATE_LIMIT_TEST_VALUE", "0")
    assert read_positive_int_env("RATE_LIMIT_TEST_VALUE", 7) == 7
    monkeypatch.setenv("RATE_LIMIT_TEST_VALUE", "-2")
    assert read_positive_int_env("RATE_LIMIT_TEST_VALUE", 7) == 7
    monkeypatch.setenv("RATE_LIMIT_TEST_VALUE", "11")
    assert read_positive_int_env("RATE_LIMIT_TEST_VALUE", 7) == 11


def test_sliding_window_hit_store_prunes_and_enforces_limits():
    store = SlidingWindowHitStore()
    window = timedelta(seconds=30)
    t0 = datetime(2026, 2, 22, 10, 0, 0)

    assert store.allow_and_add_hit(key="user_1", window=window, limit=2, now=t0) is True
    assert store.allow_and_add_hit(key="user_1", window=window, limit=2, now=t0 + timedelta(seconds=10)) is True
    assert store.allow_and_add_hit(key="user_1", window=window, limit=2, now=t0 + timedelta(seconds=20)) is False
    assert store.is_limited(key="user_1", window=window, limit=2, now=t0 + timedelta(seconds=20)) is True

    # Old hits should age out after the window.
    assert store.allow_and_add_hit(key="user_1", window=window, limit=2, now=t0 + timedelta(seconds=45)) is True
    assert len(store.history["user_1"]) == 1

    store.reset_key("user_1")
    assert "user_1" not in store.history
