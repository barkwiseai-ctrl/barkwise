from datetime import datetime, timezone
from threading import Lock
from typing import Dict, List, Optional
from uuid import uuid4

from app.models import NotificationRecord
from app.services.push_sender import push_sender


class NotificationStore:
    def __init__(self):
        self._lock = Lock()
        now = datetime.now(timezone.utc)
        self._notifications: List[NotificationRecord] = [
            NotificationRecord(
                id="ntf_seed_1",
                user_id="user_2",
                title="Quote response received",
                body="A provider accepted your latest quote request.",
                category="booking",
                read=False,
                created_at=(now).isoformat(),
                deep_link="quote:qr_seed_2",
            ),
            NotificationRecord(
                id="ntf_seed_2",
                user_id="user_1",
                title="New booking request",
                body="Milo requested a 09:00 grooming slot.",
                category="booking",
                read=False,
                created_at=(now).isoformat(),
                deep_link="booking:bk_seed_1",
            ),
            NotificationRecord(
                id="ntf_seed_3",
                user_id="user_3",
                title="Community reward unlocked",
                body="Your group advanced in Clean Park Streak.",
                category="community",
                read=False,
                created_at=(now).isoformat(),
                deep_link="group:g_user_dogpark_surry",
            ),
            NotificationRecord(
                id="ntf_seed_4",
                user_id="user_4",
                title="Listing reviewed by vet",
                body="Your grooming listing was marked Vet-Checked.",
                category="booking",
                read=True,
                created_at=(now).isoformat(),
                deep_link="provider:svc_7",
            ),
        ]
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
