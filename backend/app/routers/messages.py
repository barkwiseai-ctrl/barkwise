from datetime import datetime, timezone
from typing import Optional

from fastapi import APIRouter, Header, HTTPException, Query

from app.auth import assert_actor_authorized
from app.models import MessageMarkReadRequest, MessageRecord, MessageSendRequest, MessageThreadView
from app.services.message_store import message_store

router = APIRouter(prefix="/messages", tags=["messages"])


def _utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def _thread_id_from_users(user_a: str, user_b: str) -> str:
    first, second = sorted([user_a.strip(), user_b.strip()])
    return f"dm_{first}_{second}"


@router.get("/threads", response_model=list[MessageThreadView])
def list_threads(
    user_id: str = Query(...),
    limit: int = Query(default=50, ge=1, le=300),
    authorization: Optional[str] = Header(default=None),
):
    assert_actor_authorized(actor_user_id=user_id, authorization=authorization)
    return message_store.list_threads(user_id=user_id, limit=limit)


@router.get("/threads/{thread_id}", response_model=list[MessageRecord])
def list_thread_messages(
    thread_id: str,
    user_id: str = Query(...),
    limit: int = Query(default=100, ge=1, le=500),
    authorization: Optional[str] = Header(default=None),
):
    assert_actor_authorized(actor_user_id=user_id, authorization=authorization)
    return message_store.list_messages(user_id=user_id, thread_id=thread_id, limit=limit)


@router.post("/threads/{thread_id}/messages", response_model=MessageRecord)
def send_message(
    thread_id: str,
    payload: MessageSendRequest,
    authorization: Optional[str] = Header(default=None),
):
    assert_actor_authorized(actor_user_id=payload.user_id, authorization=authorization)
    resolved_thread_id = _thread_id_from_users(payload.user_id, payload.recipient_user_id)
    if thread_id != resolved_thread_id:
        raise HTTPException(status_code=400, detail="thread_id does not match participants")
    return message_store.send_message(
        sender_user_id=payload.user_id,
        recipient_user_id=payload.recipient_user_id,
        body=payload.body,
        created_at=_utc_now_iso(),
        thread_id=thread_id,
    )


@router.post("/threads/{thread_id}/read", response_model=dict)
def mark_thread_read(
    thread_id: str,
    payload: MessageMarkReadRequest,
    authorization: Optional[str] = Header(default=None),
):
    assert_actor_authorized(actor_user_id=payload.user_id, authorization=authorization)
    read_seq = message_store.mark_thread_read(
        user_id=payload.user_id,
        thread_id=thread_id,
        updated_at=_utc_now_iso(),
    )
    return {"status": "ok", "read_seq": read_seq}
