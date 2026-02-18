import json
import sqlite3
from pathlib import Path
from threading import Lock
from typing import Any, Dict, List, Tuple


class MemoryStore:
    def __init__(self, db_path: str) -> None:
        self.db_path = Path(db_path)
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self._lock = Lock()
        self._init_db()

    def _connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(str(self.db_path), check_same_thread=False)
        conn.row_factory = sqlite3.Row
        return conn

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
                        updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """
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
                conn.commit()

    def load_user_state(self, user_id: str) -> Dict[str, Any]:
        with self._lock:
            with self._connect() as conn:
                row = conn.execute(
                    "SELECT profile_json, profile_accepted, field_locks_json, provider_state_json FROM user_memory WHERE user_id = ?",
                    (user_id,),
                ).fetchone()

                if not row:
                    return {
                        "profile_memory": {},
                        "profile_accepted": False,
                        "field_locks": {},
                        "provider_state": {},
                    }

                return {
                    "profile_memory": json.loads(row["profile_json"] or "{}"),
                    "profile_accepted": bool(row["profile_accepted"]),
                    "field_locks": json.loads(row["field_locks_json"] or "{}"),
                    "provider_state": json.loads(row["provider_state_json"] or "{}"),
                }

    def save_user_state(
        self,
        user_id: str,
        profile_memory: Dict[str, Any],
        profile_accepted: bool,
        field_locks: Dict[str, bool],
        provider_state: Dict[str, Any],
    ) -> None:
        with self._lock:
            with self._connect() as conn:
                conn.execute(
                    """
                    INSERT INTO user_memory (user_id, profile_json, profile_accepted, field_locks_json, provider_state_json, updated_at)
                    VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT(user_id) DO UPDATE SET
                        profile_json = excluded.profile_json,
                        profile_accepted = excluded.profile_accepted,
                        field_locks_json = excluded.field_locks_json,
                        provider_state_json = excluded.provider_state_json,
                        updated_at = CURRENT_TIMESTAMP
                    """,
                    (
                        user_id,
                        json.dumps(profile_memory),
                        1 if profile_accepted else 0,
                        json.dumps(field_locks),
                        json.dumps(provider_state),
                    ),
                )
                conn.commit()

    def append_turn(self, user_id: str, role: str, content: str) -> None:
        with self._lock:
            with self._connect() as conn:
                conn.execute(
                    "INSERT INTO chat_history (user_id, role, content) VALUES (?, ?, ?)",
                    (user_id, role, content),
                )
                conn.commit()

    def load_recent_turns(self, user_id: str, limit: int = 20) -> List[Dict[str, str]]:
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
        return turns
