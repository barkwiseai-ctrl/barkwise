import asyncio
import json
import logging
from datetime import timedelta

from fastapi import APIRouter, HTTPException
from fastapi.responses import StreamingResponse

from app.models import ChatRequest, ProfileAcceptRequest, ProviderSubmitRequest
from app.services.ai_orchestrator import AIOrchestrator
from app.services.rate_limiting import SlidingWindowHitStore, read_positive_int_env
from app.services.security_audit import record_rate_limit_hit

router = APIRouter(prefix="/chat", tags=["chat"])
orchestrator = AIOrchestrator()
logger = logging.getLogger(__name__)


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


@router.post("")
def chat(request: ChatRequest):
    _check_chat_rate_limit(user_id=request.user_id, bucket="chat", max_requests=CHAT_RATE_LIMIT_MAX_REQUESTS)
    return orchestrator.handle_message(
        message=request.message,
        user_id=request.user_id,
        suburb=request.suburb,
    )


@router.post("/profile/accept")
def accept_profile(request: ProfileAcceptRequest):
    _check_chat_rate_limit(
        user_id=request.user_id,
        bucket="profile_accept",
        max_requests=CHAT_ACTION_RATE_LIMIT_MAX_REQUESTS,
    )
    return orchestrator.accept_profile(user_id=request.user_id)


@router.post("/provider/submit")
def submit_provider_listing(request: ProviderSubmitRequest):
    _check_chat_rate_limit(
        user_id=request.user_id,
        bucket="provider_submit",
        max_requests=CHAT_ACTION_RATE_LIMIT_MAX_REQUESTS,
    )
    return orchestrator.submit_provider_listing(user_id=request.user_id)


@router.post("/stream")
def chat_stream(request: ChatRequest):
    _check_chat_rate_limit(
        user_id=request.user_id,
        bucket="chat_stream",
        max_requests=CHAT_STREAM_RATE_LIMIT_MAX_REQUESTS,
    )

    def event_generator():
        yield ": stream-start\n\n"
        try:
            for event in orchestrator.stream_message(
                message=request.message,
                user_id=request.user_id,
                suburb=request.suburb,
            ):
                yield f"data: {json.dumps(event)}\n\n"
        except asyncio.CancelledError:
            # Client disconnected before stream completion.
            return
        except Exception:
            logger.exception("Chat stream failed")
            fallback = {
                "type": "final",
                "response": {
                    "answer": "I hit a streaming issue. Please retry your message.",
                    "suggested_profile": {},
                    "cta_chips": [],
                    "conversation": [],
                    "profile_suggestion": None,
                    "a2ui_messages": [],
                },
            }
            yield f"data: {json.dumps(fallback)}\n\n"
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
