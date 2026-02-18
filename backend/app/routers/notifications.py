from typing import Optional

from fastapi import APIRouter, Header, HTTPException, Query

from app.auth import assert_actor_authorized
from app.models import DeviceTokenRegisterRequest, NotificationRecord
from app.services.notification_store import notification_store

router = APIRouter(prefix="/notifications", tags=["notifications"])


@router.get("", response_model=list[NotificationRecord])
def list_notifications(
    user_id: str = Query(...),
    unread_only: bool = Query(default=False),
):
    return notification_store.list_for_user(user_id=user_id, unread_only=unread_only)


@router.post("/register-device", response_model=dict)
def register_device(
    payload: DeviceTokenRegisterRequest,
    authorization: Optional[str] = Header(default=None),
):
    assert_actor_authorized(actor_user_id=payload.user_id, authorization=authorization)
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
