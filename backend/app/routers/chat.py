import asyncio
import json
import logging
from datetime import timedelta
from typing import Optional

from fastapi import APIRouter, Header, HTTPException
from fastapi.responses import StreamingResponse

from app.auth import assert_actor_authorized, resolve_request_user
from app.models import ChatRequest, ProfileAcceptRequest, ProviderSubmitRequest
from app.services.rate_limiting import SlidingWindowHitStore, read_positive_int_env
from app.services.security_audit import record_rate_limit_hit
from app.services.simple_chat_service import SimpleChatService

router = APIRouter(prefix="/chat", tags=["chat"])
chat_service = SimpleChatService()
logger = logging.getLogger(__name__)
DIAGNOSTIC_ADMIN_USER_IDS = {"admin", "user_1", "user_3"}


CHAT_RATE_LIMIT_WINDOW = timedelta(seconds=read_positive_int_env("CHAT_RATE_LIMIT_WINDOW_SECONDS", 60))
CHAT_RATE_LIMIT_MAX_REQUESTS = read_positive_int_env("CHAT_RATE_LIMIT_MAX_REQUESTS", 12)
CHAT_STREAM_RATE_LIMIT_MAX_REQUESTS = read_positive_int_env("CHAT_STREAM_RATE_LIMIT_MAX_REQUESTS", 6)
CHAT_ACTION_RATE_LIMIT_MAX_REQUESTS = read_positive_int_env("CHAT_ACTION_RATE_LIMIT_MAX_REQUESTS", 10)
_CHAT_RATE_LIMIT_STORE = SlidingWindowHitStore()
# Test compatibility: keep existing mutable history reference.
CHAT_RATE_LIMIT_HISTORY = _CHAT_RATE_LIMIT_STORE.history


def _check_chat_rate_limit(*, user_id: str, bucket: str, max_requests: int) -> None:
    key = f"{bucket}:{user_id.strip().lower()}"
    if not _CHAT_RATE_LIMIT_STORE.allow_and_add_hit(
        key=key,
        window=CHAT_RATE_LIMIT_WINDOW,
        limit=max_requests,
    ):
        record_rate_limit_hit(
            surface=f"chat_{bucket}",
            key=user_id,
            detail="chat_rate_limit_exceeded",
        )
        raise HTTPException(
            status_code=429,
            detail=(
                "Too many chat requests. "
                f"Limit {max_requests} per {CHAT_RATE_LIMIT_WINDOW.seconds} seconds."
            ),
        )


def _authorize_and_rate_limit(
    *,
    user_id: str,
    authorization: Optional[str],
    bucket: str,
    max_requests: int,
) -> None:
    assert_actor_authorized(actor_user_id=user_id, authorization=authorization)
    _check_chat_rate_limit(user_id=user_id, bucket=bucket, max_requests=max_requests)


@router.post("")
def chat(request: ChatRequest, authorization: Optional[str] = Header(default=None)):
    _authorize_and_rate_limit(
        user_id=request.user_id,
        authorization=authorization,
        bucket="chat",
        max_requests=CHAT_RATE_LIMIT_MAX_REQUESTS,
    )
    return chat_service.create_chat_response(request)


@router.post("/profile/accept")
def accept_profile(request: ProfileAcceptRequest, authorization: Optional[str] = Header(default=None)):
    _authorize_and_rate_limit(
        user_id=request.user_id,
        authorization=authorization,
        bucket="profile_accept",
        max_requests=CHAT_ACTION_RATE_LIMIT_MAX_REQUESTS,
    )
    return chat_service.accept_profile(user_id=request.user_id)


@router.post("/provider/submit")
def submit_provider_listing(request: ProviderSubmitRequest, authorization: Optional[str] = Header(default=None)):
    _authorize_and_rate_limit(
        user_id=request.user_id,
        authorization=authorization,
        bucket="provider_submit",
        max_requests=CHAT_ACTION_RATE_LIMIT_MAX_REQUESTS,
    )
    return chat_service.submit_provider_listing(user_id=request.user_id)


@router.post("/stream")
def chat_stream(request: ChatRequest, authorization: Optional[str] = Header(default=None)):
    _authorize_and_rate_limit(
        user_id=request.user_id,
        authorization=authorization,
        bucket="chat_stream",
        max_requests=CHAT_STREAM_RATE_LIMIT_MAX_REQUESTS,
    )

    def event_generator():
        yield ": stream-start\n\n"
        try:
            for event in chat_service.stream_chat(request):
                yield f"data: {json.dumps(event)}\n\n"
        except asyncio.CancelledError:
            # Client disconnected before stream completion.
            return
        except Exception:
            logger.exception("Chat stream failed")
            yield (
                "data: "
                + json.dumps(
                    {
                        "type": "error",
                        "error": "BarkAI could not reply. Please retry.",
                        "error_type": "backend_unavailable",
                    }
                )
                + "\n\n"
            )
        yield "data: [DONE]\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


@router.get("/diagnostics/llm")
def barkai_llm_diagnostics(
    authorization: Optional[str] = Header(default=None),
    x_barkai_diagnostics_token: Optional[str] = Header(default=None),
):
    expected = chat_service.diagnostics_token
    request_user = resolve_request_user(authorization)
    if expected and x_barkai_diagnostics_token == expected:
        return chat_service.run_llm_diagnostics()
    if request_user in DIAGNOSTIC_ADMIN_USER_IDS:
        return chat_service.run_llm_diagnostics()
    if not expected:
        raise HTTPException(status_code=403, detail="Forbidden")
    raise HTTPException(status_code=403, detail="Forbidden")
