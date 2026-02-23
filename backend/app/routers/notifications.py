from datetime import timedelta
from typing import Optional

from fastapi import APIRouter, Header, HTTPException, Query

from app.auth import assert_actor_authorized
from app.models import DeviceTokenRegisterRequest, NotificationRecord
from app.services.rate_limiting import SlidingWindowHitStore, read_positive_int_env
from app.services.security_audit import record_rate_limit_hit
from app.services.notification_store import notification_store

router = APIRouter(prefix="/notifications", tags=["notifications"])


DEVICE_REGISTER_RATE_LIMIT_MAX = read_positive_int_env("NOTIFICATIONS_DEVICE_REGISTER_RATE_LIMIT_MAX", 6)
DEVICE_REGISTER_RATE_LIMIT_WINDOW = timedelta(
    seconds=read_positive_int_env("NOTIFICATIONS_DEVICE_REGISTER_RATE_LIMIT_WINDOW_SECONDS", 300)
)
_DEVICE_REGISTER_STORE = SlidingWindowHitStore()
# Test compatibility: keep existing mutable history reference.
DEVICE_REGISTER_RATE_LIMIT_HISTORY = _DEVICE_REGISTER_STORE.history


def _check_device_register_rate_limit(user_id: str) -> None:
    if not _DEVICE_REGISTER_STORE.allow_and_add_hit(
        key=user_id,
        window=DEVICE_REGISTER_RATE_LIMIT_WINDOW,
        limit=DEVICE_REGISTER_RATE_LIMIT_MAX,
    ):
        record_rate_limit_hit(
            surface="notifications_register_device",
            key=user_id,
            detail="device_register_limit_exceeded",
        )
        raise HTTPException(
            status_code=429,
            detail=(
                "Too many device registration attempts. "
                f"Limit {DEVICE_REGISTER_RATE_LIMIT_MAX} per {DEVICE_REGISTER_RATE_LIMIT_WINDOW.seconds // 60} minutes."
            ),
        )


@router.get("", response_model=list[NotificationRecord])
def list_notifications(
    user_id: str = Query(...),
    unread_only: bool = Query(default=False),
    authorization: Optional[str] = Header(default=None),
):
    assert_actor_authorized(actor_user_id=user_id, authorization=authorization)
    return notification_store.list_for_user(user_id=user_id, unread_only=unread_only)


@router.post("/register-device", response_model=dict)
def register_device(
    payload: DeviceTokenRegisterRequest,
    authorization: Optional[str] = Header(default=None),
):
    assert_actor_authorized(actor_user_id=payload.user_id, authorization=authorization)
    _check_device_register_rate_limit(payload.user_id)
    notification_store.register_device_token(user_id=payload.user_id, device_token=payload.device_token)
    return {"status": "ok"}


@router.post("/{notification_id}/read", response_model=NotificationRecord)
def mark_notification_read(
    notification_id: str,
    user_id: str = Query(...),
    authorization: Optional[str] = Header(default=None),
):
    assert_actor_authorized(actor_user_id=user_id, authorization=authorization)
    updated = notification_store.mark_read(user_id=user_id, notification_id=notification_id)
    if not updated:
        raise HTTPException(status_code=404, detail="Notification not found")
    return updated
