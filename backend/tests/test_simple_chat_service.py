import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from fastapi import HTTPException

from app.models import ChatMessage, ChatRequest
from app.services.simple_chat_service import SimpleChatService


def test_resolve_transcript_prefers_messages_and_limits_to_last_20():
    service = SimpleChatService()
    request = ChatRequest(
        user_id="user_2",
        messages=[ChatMessage(role="user", content=f"message-{index}") for index in range(25)],
    )

    transcript = service._resolve_transcript(request)

    assert len(transcript) == 20
    assert transcript[0].content == "message-5"
    assert transcript[-1].content == "message-24"


def test_resolve_transcript_falls_back_to_legacy_message():
    service = SimpleChatService()
    request = ChatRequest(user_id="user_2", message="hello")

    transcript = service._resolve_transcript(request)

    assert transcript == [ChatMessage(role="user", content="hello")]


def test_build_openai_messages_includes_profile_context(monkeypatch):
    service = SimpleChatService()
    monkeypatch.setattr(
        service,
        "_profile_context",
        lambda user_id: {"display_name": "Alex", "dog_name": "Milo", "suburb": "Richmond"},
    )

    messages = service._build_openai_messages(
        user_id="user_2",
        transcript=[ChatMessage(role="user", content="Hi there")],
    )

    assert messages[0]["role"] == "system"
    assert "Milo" in messages[0]["content"]
    assert messages[1:] == [{"role": "user", "content": "Hi there"}]


def test_create_chat_response_builds_minimal_output(monkeypatch):
    service = SimpleChatService()
    monkeypatch.setattr(service, "_openai_request", lambda payload, stream: {"choices": [{"message": {"content": "Hello back"}}]})

    response = service.create_chat_response(
        ChatRequest(
            user_id="user_2",
            messages=[ChatMessage(role="user", content="Hello")],
        )
    )

    assert response.answer == "Hello back"
    assert response.message is not None
    assert response.message.content == "Hello back"
    assert response.conversation[-1].content == "Hello back"
    assert response.answer_badges == []
    assert response.citations == []


def test_create_chat_response_requires_openai_configuration(monkeypatch):
    service = SimpleChatService()
    monkeypatch.setattr(service, "_load_openai_api_key", lambda: "")

    try:
        service._openai_request(payload={}, stream=False)
        assert False, "expected HTTPException"
    except HTTPException as exc:
        assert exc.status_code == 503


def test_stream_chat_yields_deltas_and_final_response(monkeypatch):
    service = SimpleChatService()
    stream_lines = [
        b"data: {\"choices\":[{\"delta\":{\"content\":\"Hello \"}}]}\n",
        b"\n",
        b"data: {\"choices\":[{\"delta\":{\"content\":\"world\"}}]}\n",
        b"\n",
        b"data: [DONE]\n",
    ]

    class FakeStream:
        def __iter__(self):
            return iter(stream_lines)

        def close(self):
            return None

    monkeypatch.setattr(service, "_openai_request", lambda payload, stream: FakeStream())

    events = list(
        service.stream_chat(
            ChatRequest(
                user_id="user_2",
                messages=[ChatMessage(role="user", content="Hi")],
            )
        )
    )

    assert events[0] == {"type": "delta", "delta": "Hello "}
    assert events[1] == {"type": "delta", "delta": "world"}
    assert events[2]["type"] == "final"
    assert events[2]["response"]["message"] == {"role": "assistant", "content": "Hello world"}
