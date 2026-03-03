import os
from pathlib import Path
import sqlite3
from threading import Lock
from typing import Optional
from uuid import uuid4

from app.models import MessageRecord, MessageThreadView


def _thread_id(user_a: str, user_b: str) -> str:
    a, b = sorted([user_a.strip(), user_b.strip()])
    return f"dm_{a}_{b}"


class MessageStore:
    def __init__(self, db_path: str) -> None:
        self._lock = Lock()
        path = Path(db_path)
        path.parent.mkdir(parents=True, exist_ok=True)
        self.db_path = str(path)
        self._init_db()

    def _connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path, check_same_thread=False)
        conn.row_factory = sqlite3.Row
        return conn

    def _init_db(self) -> None:
        with self._lock:
            with self._connect() as conn:
                conn.execute(
                    """
                    CREATE TABLE IF NOT EXISTS threads (
                        id TEXT PRIMARY KEY,
                        user_a TEXT NOT NULL,
                        user_b TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """
                )
                conn.execute(
                    """
                    CREATE TABLE IF NOT EXISTS messages (
                        seq INTEGER PRIMARY KEY AUTOINCREMENT,
                        id TEXT NOT NULL UNIQUE,
                        thread_id TEXT NOT NULL,
                        sender_user_id TEXT NOT NULL,
                        recipient_user_id TEXT NOT NULL,
                        body TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """
                )
                conn.execute(
                    """
                    CREATE TABLE IF NOT EXISTS thread_reads (
                        thread_id TEXT NOT NULL,
                        user_id TEXT NOT NULL,
                        read_seq INTEGER NOT NULL DEFAULT 0,
                        updated_at TEXT NOT NULL,
                        PRIMARY KEY (thread_id, user_id)
                    )
                    """
                )
                conn.execute("CREATE INDEX IF NOT EXISTS idx_messages_thread_seq ON messages(thread_id, seq DESC)")
                conn.execute("CREATE INDEX IF NOT EXISTS idx_messages_recipient_seq ON messages(recipient_user_id, seq DESC)")
                conn.commit()

    def _ensure_thread(self, conn: sqlite3.Connection, thread_id: str, user_a: str, user_b: str, created_at: str) -> None:
        conn.execute(
            """
            INSERT OR IGNORE INTO threads(id, user_a, user_b, created_at)
            VALUES (?, ?, ?, ?)
            """,
            (thread_id, user_a, user_b, created_at),
        )

    def _participant_for(self, conn: sqlite3.Connection, thread_id: str, user_id: str) -> Optional[str]:
        row = conn.execute(
            "SELECT user_a, user_b FROM threads WHERE id = ?",
            (thread_id,),
        ).fetchone()
        if not row:
            return None
        user_a = str(row["user_a"])
        user_b = str(row["user_b"])
        if user_a == user_id:
            return user_b
        if user_b == user_id:
            return user_a
        return None

    def send_message(
        self,
        *,
        sender_user_id: str,
        recipient_user_id: str,
        body: str,
        created_at: str,
        thread_id: Optional[str] = None,
    ) -> MessageRecord:
        clean_sender = sender_user_id.strip()
        clean_recipient = recipient_user_id.strip()
        clean_body = body.strip()
        if not clean_sender or not clean_recipient:
            raise ValueError("sender_user_id and recipient_user_id are required")
        if not clean_body:
            raise ValueError("Message body cannot be empty")
        resolved_thread = thread_id.strip() if thread_id and thread_id.strip() else _thread_id(clean_sender, clean_recipient)
        message_id = f"msg_{uuid4().hex[:12]}"
        with self._lock:
            with self._connect() as conn:
                self._ensure_thread(conn, resolved_thread, *sorted([clean_sender, clean_recipient]), created_at)
                conn.execute(
                    """
                    INSERT INTO messages (id, thread_id, sender_user_id, recipient_user_id, body, created_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    (message_id, resolved_thread, clean_sender, clean_recipient, clean_body, created_at),
                )
                row = conn.execute(
                    """
                    SELECT id, thread_id, sender_user_id, recipient_user_id, body, created_at
                    FROM messages
                    WHERE id = ?
                    """,
                    (message_id,),
                ).fetchone()
                conn.commit()
        assert row is not None
        return MessageRecord(
            id=str(row["id"]),
            thread_id=str(row["thread_id"]),
            sender_user_id=str(row["sender_user_id"]),
            recipient_user_id=str(row["recipient_user_id"]),
            body=str(row["body"]),
            created_at=str(row["created_at"]),
        )

    def list_messages(
        self,
        *,
        user_id: str,
        thread_id: str,
        limit: int = 100,
    ) -> list[MessageRecord]:
        clean_user = user_id.strip()
        if not clean_user:
            return []
        with self._lock:
            with self._connect() as conn:
                participant = self._participant_for(conn, thread_id=thread_id, user_id=clean_user)
                if participant is None:
                    return []
                rows = conn.execute(
                    """
                    SELECT id, thread_id, sender_user_id, recipient_user_id, body, created_at
                    FROM messages
                    WHERE thread_id = ?
                    ORDER BY seq DESC
                    LIMIT ?
                    """,
                    (thread_id, max(1, min(limit, 500))),
                ).fetchall()
        messages = [
            MessageRecord(
                id=str(row["id"]),
                thread_id=str(row["thread_id"]),
                sender_user_id=str(row["sender_user_id"]),
                recipient_user_id=str(row["recipient_user_id"]),
                body=str(row["body"]),
                created_at=str(row["created_at"]),
            )
            for row in rows
        ]
        messages.reverse()
        return messages

    def mark_thread_read(self, *, user_id: str, thread_id: str, updated_at: str) -> int:
        clean_user = user_id.strip()
        if not clean_user:
            return 0
        with self._lock:
            with self._connect() as conn:
                participant = self._participant_for(conn, thread_id=thread_id, user_id=clean_user)
                if participant is None:
                    return 0
                row = conn.execute(
                    "SELECT COALESCE(MAX(seq), 0) AS max_seq FROM messages WHERE thread_id = ?",
                    (thread_id,),
                ).fetchone()
                max_seq = int(row["max_seq"]) if row else 0
                conn.execute(
                    """
                    INSERT INTO thread_reads(thread_id, user_id, read_seq, updated_at)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT(thread_id, user_id) DO UPDATE SET
                        read_seq = excluded.read_seq,
                        updated_at = excluded.updated_at
                    """,
                    (thread_id, clean_user, max_seq, updated_at),
                )
                conn.commit()
                return max_seq

    def list_threads(self, *, user_id: str, limit: int = 50) -> list[MessageThreadView]:
        clean_user = user_id.strip()
        if not clean_user:
            return []
        with self._lock:
            with self._connect() as conn:
                thread_rows = conn.execute(
                    """
                    SELECT id, user_a, user_b, created_at
                    FROM threads
                    WHERE user_a = ? OR user_b = ?
                    ORDER BY created_at DESC
                    LIMIT ?
                    """,
                    (clean_user, clean_user, max(1, min(limit, 300))),
                ).fetchall()
                results: list[MessageThreadView] = []
                for thread in thread_rows:
                    thread_id = str(thread["id"])
                    participant = str(thread["user_b"]) if str(thread["user_a"]) == clean_user else str(thread["user_a"])
                    last_message = conn.execute(
                        """
                        SELECT seq, body, created_at
                        FROM messages
                        WHERE thread_id = ?
                        ORDER BY seq DESC
                        LIMIT 1
                        """,
                        (thread_id,),
                    ).fetchone()
                    read_row = conn.execute(
                        """
                        SELECT read_seq
                        FROM thread_reads
                        WHERE thread_id = ? AND user_id = ?
                        """,
                        (thread_id, clean_user),
                    ).fetchone()
                    read_seq = int(read_row["read_seq"]) if read_row else 0
                    unread_row = conn.execute(
                        """
                        SELECT COUNT(*) AS unread_count
                        FROM messages
                        WHERE thread_id = ? AND recipient_user_id = ? AND seq > ?
                        """,
                        (thread_id, clean_user, read_seq),
                    ).fetchone()
                    unread_count = int(unread_row["unread_count"]) if unread_row else 0
                    results.append(
                        MessageThreadView(
                            id=thread_id,
                            participant_user_id=participant,
                            last_message=(str(last_message["body"]) if last_message else ""),
                            last_message_at=(str(last_message["created_at"]) if last_message else str(thread["created_at"])),
                            unread_count=unread_count,
                        )
                    )
        return sorted(results, key=lambda row: row.last_message_at, reverse=True)

    def delete_user_data(self, *, user_id: str) -> None:
        clean_user = user_id.strip()
        if not clean_user:
            return
        with self._lock:
            with self._connect() as conn:
                thread_rows = conn.execute(
                    "SELECT id FROM threads WHERE user_a = ? OR user_b = ?",
                    (clean_user, clean_user),
                ).fetchall()
                thread_ids = [str(row["id"]) for row in thread_rows]
                if thread_ids:
                    conn.executemany("DELETE FROM messages WHERE thread_id = ?", [(tid,) for tid in thread_ids])
                    conn.executemany("DELETE FROM thread_reads WHERE thread_id = ?", [(tid,) for tid in thread_ids])
                    conn.executemany("DELETE FROM threads WHERE id = ?", [(tid,) for tid in thread_ids])
                conn.commit()


default_db = str(Path(__file__).resolve().parents[2] / "data" / "messages.sqlite3")
message_store = MessageStore(db_path=os.getenv("MESSAGES_DB_PATH", default_db))
