import asyncio
import json

from fastapi import APIRouter
from fastapi.responses import StreamingResponse

from app.models import ChatRequest, ProfileAcceptRequest, ProviderSubmitRequest
from app.services.ai_orchestrator import AIOrchestrator

router = APIRouter(prefix="/chat", tags=["chat"])
orchestrator = AIOrchestrator()


@router.post("")
def chat(request: ChatRequest):
    return orchestrator.handle_message(
        message=request.message,
        user_id=request.user_id,
        suburb=request.suburb,
    )


@router.post("/profile/accept")
def accept_profile(request: ProfileAcceptRequest):
    return orchestrator.accept_profile(user_id=request.user_id)


@router.post("/provider/submit")
def submit_provider_listing(request: ProviderSubmitRequest):
    return orchestrator.submit_provider_listing(user_id=request.user_id)


@router.post("/stream")
def chat_stream(request: ChatRequest):
    def event_generator():
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

    return StreamingResponse(event_generator(), media_type="text/event-stream")
