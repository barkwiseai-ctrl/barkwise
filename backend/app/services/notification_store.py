from datetime import datetime, timezone
import os
from pathlib import Path
import sqlite3
from threading import Lock
from typing import List, Optional
from uuid import uuid4

from app.models import NotificationRecord
from app.services.push_sender import push_sender


class NotificationStore:
    def __init__(self, db_path: str) -> None:
        self._lock = Lock()
        path = Path(db_path)
        path.parent.mkdir(parents=True, exist_ok=True)
        self.db_path = str(path)
        self._init_db()
        self._remove_seeded_notifications()

    def _connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path, check_same_thread=False)
        conn.row_factory = sqlite3.Row
        return conn

    def _init_db(self) -> None:
        with self._lock:
            with self._connect() as conn:
                conn.execute(
                    """
                    CREATE TABLE IF NOT EXISTS notifications (
                        id TEXT PRIMARY KEY,
                        user_id TEXT NOT NULL,
                        title TEXT NOT NULL,
                        body TEXT NOT NULL,
                        category TEXT NOT NULL,
                        is_read INTEGER NOT NULL DEFAULT 0,
                        created_at TEXT NOT NULL,
                        deep_link TEXT
                    )
                    """
                )
                conn.execute(
                    """
                    CREATE TABLE IF NOT EXISTS device_tokens (
                        user_id TEXT NOT NULL,
                        token TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        PRIMARY KEY (user_id, token)
                    )
                    """
                )
                conn.execute(
                    "CREATE INDEX IF NOT EXISTS idx_notifications_user_created_at ON notifications(user_id, created_at DESC)"
                )
                conn.commit()

    def _remove_seeded_notifications(self) -> None:
        with self._lock:
            with self._connect() as conn:
                conn.execute("DELETE FROM notifications WHERE id IN ('ntf_seed_1', 'ntf_seed_2', 'ntf_seed_3')")
                conn.commit()

    def _row_to_notification(self, row: sqlite3.Row) -> NotificationRecord:
        return NotificationRecord(
            id=str(row["id"]),
            user_id=str(row["user_id"]),
            title=str(row["title"]),
            body=str(row["body"]),
            category=str(row["category"]),  # type: ignore[arg-type]
            read=bool(row["is_read"]),
            created_at=str(row["created_at"]),
            deep_link=str(row["deep_link"]) if row["deep_link"] is not None else None,
        )

    def register_device_token(self, user_id: str, device_token: str) -> None:
        token = device_token.strip()
        if not token:
            return
        now = datetime.now(timezone.utc).isoformat()
        with self._lock:
            with self._connect() as conn:
                conn.execute(
                    """
                    INSERT OR IGNORE INTO device_tokens (user_id, token, created_at)
                    VALUES (?, ?, ?)
                    """,
                    (user_id, token, now),
                )
                conn.commit()

    def _list_tokens(self, user_id: str) -> List[str]:
        with self._lock:
            with self._connect() as conn:
                rows = conn.execute(
                    """
                    SELECT token
                    FROM device_tokens
                    WHERE user_id = ?
                    ORDER BY created_at DESC
                    """,
                    (user_id,),
                ).fetchall()
        return [str(row["token"]) for row in rows]

    def _drop_invalid_tokens(self, user_id: str, invalid_tokens: List[str]) -> None:
        if not invalid_tokens:
            return
        with self._lock:
            with self._connect() as conn:
                conn.executemany(
                    "DELETE FROM device_tokens WHERE user_id = ? AND token = ?",
                    [(user_id, token) for token in invalid_tokens],
                )
                conn.commit()

    def create(
        self,
        user_id: str,
        title: str,
        body: str,
        category: str = "system",
        deep_link: Optional[str] = None,
    ) -> NotificationRecord:
        record = NotificationRecord(
            id=f"ntf_{uuid4().hex[:10]}",
            user_id=user_id,
            title=title,
            body=body,
            category=category,  # type: ignore[arg-type]
            read=False,
            created_at=datetime.now(timezone.utc).isoformat(),
            deep_link=deep_link,
        )
        with self._lock:
            with self._connect() as conn:
                conn.execute(
                    """
                    INSERT INTO notifications
                    (id, user_id, title, body, category, is_read, created_at, deep_link)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        record.id,
                        record.user_id,
                        record.title,
                        record.body,
                        record.category,
                        0,
                        record.created_at,
                        record.deep_link,
                    ),
                )
                conn.commit()
        tokens = self._list_tokens(user_id)
        invalid_tokens = push_sender.send_notification(
            tokens=tokens,
            title=title,
            body=body,
            data={
                "notification_id": record.id,
                "category": category,
                "deep_link": deep_link or "",
            },
        )
        self._drop_invalid_tokens(user_id, invalid_tokens)
        return record

    def list_for_user(self, user_id: str, unread_only: bool = False) -> List[NotificationRecord]:
        query = """
            SELECT id, user_id, title, body, category, is_read, created_at, deep_link
            FROM notifications
            WHERE user_id = ?
        """
        params: list[object] = [user_id]
        if unread_only:
            query += " AND is_read = 0"
        query += " ORDER BY created_at DESC LIMIT 100"
        with self._lock:
            with self._connect() as conn:
                rows = conn.execute(query, params).fetchall()
        return [self._row_to_notification(row) for row in rows]

    def mark_read(self, user_id: str, notification_id: str) -> Optional[NotificationRecord]:
        with self._lock:
            with self._connect() as conn:
                conn.execute(
                    """
                    UPDATE notifications
                    SET is_read = 1
                    WHERE id = ? AND user_id = ?
                    """,
                    (notification_id, user_id),
                )
                row = conn.execute(
                    """
                    SELECT id, user_id, title, body, category, is_read, created_at, deep_link
                    FROM notifications
                    WHERE id = ? AND user_id = ?
                    """,
                    (notification_id, user_id),
                ).fetchone()
                conn.commit()
        return self._row_to_notification(row) if row else None

    def delete_user_data(self, user_id: str) -> None:
        with self._lock:
            with self._connect() as conn:
                conn.execute("DELETE FROM notifications WHERE user_id = ?", (user_id,))
                conn.execute("DELETE FROM device_tokens WHERE user_id = ?", (user_id,))
                conn.commit()


default_db = str(Path(__file__).resolve().parents[2] / "data" / "notifications.sqlite3")
notification_store = NotificationStore(db_path=os.getenv("NOTIFICATIONS_DB_PATH", default_db))
