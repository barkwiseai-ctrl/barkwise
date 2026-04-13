import json
import logging
import os
import sqlite3
from pathlib import Path
from threading import Lock, local
from time import perf_counter
from typing import Any, Dict, List, Tuple

from app.serialization import dump_json

logger = logging.getLogger(__name__)


class MemoryStore:
    def __init__(self, db_path: str) -> None:
        self.db_path = Path(db_path)
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self._lock = Lock()
        self._local = local()
        self._slow_query_ms = self._read_slow_query_ms()
        self._init_db()

    def _connect(self) -> sqlite3.Connection:
        conn = getattr(self._local, "connection", None)
        if conn is not None:
            return conn
        conn = sqlite3.connect(str(self.db_path), check_same_thread=False)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA journal_mode=WAL")
        conn.execute("PRAGMA synchronous=NORMAL")
        self._local.connection = conn
        return conn

    @staticmethod
    def _read_slow_query_ms() -> float:
        raw = os.getenv("MEMORY_STORE_SLOW_QUERY_MS", "30")
        try:
            return max(1.0, float(raw))
        except ValueError:
            return 30.0

    def _log_db_timing(self, operation: str, started_at: float) -> None:
        duration_ms = (perf_counter() - started_at) * 1000.0
        if duration_ms >= self._slow_query_ms:
            logger.warning("memory_store operation=%s duration_ms=%.2f", operation, duration_ms)
        else:
            logger.debug("memory_store operation=%s duration_ms=%.2f", operation, duration_ms)

    def _init_db(self) -> None:
        with self._lock:
            with self._connect() as conn:
                conn.execute(
                    """
                    CREATE TABLE IF NOT EXISTS user_memory (
                        user_id TEXT PRIMARY KEY,
                        profile_json TEXT NOT NULL DEFAULT '{}',
                        profile_accepted INTEGER NOT NULL DEFAULT 0,
                        field_locks_json TEXT NOT NULL DEFAULT '{}',
                        provider_state_json TEXT NOT NULL DEFAULT '{}',
                        preferences_json TEXT NOT NULL DEFAULT '{}',
                        conversation_summary TEXT NOT NULL DEFAULT '',
                        pending_confirmation_json TEXT NOT NULL DEFAULT '{}',
                        updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """
                )
                existing_columns = {
                    row["name"]
                    for row in conn.execute("PRAGMA table_info(user_memory)").fetchall()
                }
                if "preferences_json" not in existing_columns:
                    conn.execute(
                        "ALTER TABLE user_memory ADD COLUMN preferences_json TEXT NOT NULL DEFAULT '{}'"
                    )
                if "conversation_summary" not in existing_columns:
                    conn.execute(
                        "ALTER TABLE user_memory ADD COLUMN conversation_summary TEXT NOT NULL DEFAULT ''"
                    )
                if "pending_confirmation_json" not in existing_columns:
                    conn.execute(
                        "ALTER TABLE user_memory ADD COLUMN pending_confirmation_json TEXT NOT NULL DEFAULT '{}'"
                    )
                conn.execute(
                    """
                    CREATE TABLE IF NOT EXISTS chat_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id TEXT NOT NULL,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """
                )
                conn.execute(
                    """
                    CREATE INDEX IF NOT EXISTS idx_chat_history_user_id_id_desc
                    ON chat_history(user_id, id DESC)
                    """
                )
                conn.commit()

    def load_user_state(self, user_id: str) -> Dict[str, Any]:
        started_at = perf_counter()
        with self._lock:
            with self._connect() as conn:
                row = conn.execute(
                    """
                    SELECT
                        profile_json,
                        profile_accepted,
                        field_locks_json,
                        provider_state_json,
                        preferences_json,
                        conversation_summary,
                        pending_confirmation_json
                    FROM user_memory
                    WHERE user_id = ?
                    """,
                    (user_id,),
                ).fetchone()

                if not row:
                    result = {
                        "profile_memory": {},
                        "profile_accepted": False,
                        "field_locks": {},
                        "provider_state": {},
                        "preferences": {},
                        "conversation_summary": "",
                        "pending_confirmation": {},
                    }
                else:
                    result = {
                        "profile_memory": self._safe_json_object(row["profile_json"]),
                        "profile_accepted": bool(row["profile_accepted"]),
                        "field_locks": self._safe_json_object(row["field_locks_json"]),
                        "provider_state": self._safe_json_object(row["provider_state_json"]),
                        "preferences": self._safe_json_object(row["preferences_json"]),
                        "conversation_summary": str(row["conversation_summary"] or ""),
                        "pending_confirmation": self._safe_json_object(row["pending_confirmation_json"]),
                    }
        self._log_db_timing("load_user_state", started_at)
        return result

    def save_user_state(
        self,
        user_id: str,
        profile_memory: Dict[str, Any],
        profile_accepted: bool,
        field_locks: Dict[str, bool],
        provider_state: Dict[str, Any],
        preferences: Dict[str, Any],
        conversation_summary: str,
        pending_confirmation: Dict[str, Any],
    ) -> None:
        started_at = perf_counter()
        with self._lock:
            with self._connect() as conn:
                conn.execute(
                    """
                    INSERT INTO user_memory (
                        user_id,
                        profile_json,
                        profile_accepted,
                        field_locks_json,
                        provider_state_json,
                        preferences_json,
                        conversation_summary,
                        pending_confirmation_json,
                        updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT(user_id) DO UPDATE SET
                        profile_json = excluded.profile_json,
                        profile_accepted = excluded.profile_accepted,
                        field_locks_json = excluded.field_locks_json,
                        provider_state_json = excluded.provider_state_json,
                        preferences_json = excluded.preferences_json,
                        conversation_summary = excluded.conversation_summary,
                        pending_confirmation_json = excluded.pending_confirmation_json,
                        updated_at = CURRENT_TIMESTAMP
                    """,
                    (
                        user_id,
                        dump_json(profile_memory),
                        1 if profile_accepted else 0,
                        dump_json(field_locks),
                        dump_json(provider_state),
                        dump_json(preferences),
                        conversation_summary,
                        dump_json(pending_confirmation),
                    ),
                )
                conn.commit()
        self._log_db_timing("save_user_state", started_at)

    def append_turn(self, user_id: str, role: str, content: str) -> None:
        started_at = perf_counter()
        with self._lock:
            with self._connect() as conn:
                conn.execute(
                    "INSERT INTO chat_history (user_id, role, content) VALUES (?, ?, ?)",
                    (user_id, role, content),
                )
                conn.commit()
        self._log_db_timing("append_turn", started_at)

    def load_recent_turns(self, user_id: str, limit: int = 20) -> List[Dict[str, str]]:
        started_at = perf_counter()
        with self._lock:
            with self._connect() as conn:
                rows = conn.execute(
                    """
                    SELECT role, content
                    FROM chat_history
                    WHERE user_id = ?
                    ORDER BY id DESC
                    LIMIT ?
                    """,
                    (user_id, limit),
                ).fetchall()

        turns = [{"role": row["role"], "content": row["content"]} for row in rows]
        turns.reverse()
        self._log_db_timing("load_recent_turns", started_at)
        return turns

    def delete_user_data(self, user_id: str) -> None:
        started_at = perf_counter()
        with self._lock:
            with self._connect() as conn:
                conn.execute("DELETE FROM user_memory WHERE user_id = ?", (user_id,))
                conn.execute("DELETE FROM chat_history WHERE user_id = ?", (user_id,))
                conn.commit()
        self._log_db_timing("delete_user_data", started_at)

    def _safe_json_object(self, raw_value: Any) -> Dict[str, Any]:
        if raw_value in (None, ""):
            return {}
        if isinstance(raw_value, dict):
            return raw_value
        if not isinstance(raw_value, str):
            return {}
        try:
            parsed = json.loads(raw_value)
        except json.JSONDecodeError:
            return {}
        return parsed if isinstance(parsed, dict) else {}
