import importlib
import os
import sqlite3
import sys


sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from app.services.memory_store import MemoryStore


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
