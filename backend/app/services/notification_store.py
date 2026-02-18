from datetime import datetime, timezone
from threading import Lock
from typing import Dict, List, Optional
from uuid import uuid4

from app.models import NotificationRecord
from app.services.push_sender import push_sender


class NotificationStore:
    def __init__(self):
        self._lock = Lock()
        self._notifications: List[NotificationRecord] = []
        self._device_tokens: Dict[str, set[str]] = {}

    def register_device_token(self, user_id: str, device_token: str) -> None:
        if not device_token.strip():
            return
        with self._lock:
            self._device_tokens.setdefault(user_id, set()).add(device_token.strip())

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
            self._notifications.insert(0, record)
            tokens = list(self._device_tokens.get(user_id, set()))
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
        if invalid_tokens:
            with self._lock:
                current = self._device_tokens.get(user_id, set())
                for token in invalid_tokens:
                    current.discard(token)
        return record

    def list_for_user(self, user_id: str, unread_only: bool = False) -> List[NotificationRecord]:
        with self._lock:
            rows = [n for n in self._notifications if n.user_id == user_id]
            if unread_only:
                rows = [n for n in rows if not n.read]
            return rows[:100]

    def mark_read(self, user_id: str, notification_id: str) -> Optional[NotificationRecord]:
        with self._lock:
            for idx, row in enumerate(self._notifications):
                if row.id == notification_id and row.user_id == user_id:
                    updated = row.model_copy(update={"read": True})
                    self._notifications[idx] = updated
                    return updated
        return None


notification_store = NotificationStore()
