import os
import sys
from contextlib import contextmanager
from types import SimpleNamespace
import json

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from fastapi import HTTPException

from app.models import CommunityEventView, Group, GroupJoinRecord, MessageThreadView
from app.services import simple_chat_service as simple_chat_module
from app.models import ChatMessage, ChatRequest
from app.services.simple_chat_service import SimpleChatService, _extract_openai_api_key


@contextmanager
def temporary_groups(temp_groups, temp_memberships):
    original_groups = list(simple_chat_module.groups)
    original_memberships = list(simple_chat_module.group_memberships)
    simple_chat_module.groups[:] = temp_groups
    simple_chat_module.group_memberships[:] = temp_memberships
    try:
        yield
    finally:
        simple_chat_module.groups[:] = original_groups
        simple_chat_module.group_memberships[:] = original_memberships


@contextmanager
def temporary_events(temp_events):
    original_events = list(simple_chat_module.community_events)
    simple_chat_module.community_events[:] = temp_events
    try:
        yield
    finally:
        simple_chat_module.community_events[:] = original_events


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


def test_build_openai_messages_omits_custom_prompt_in_standard_mode(monkeypatch):
    service = SimpleChatService()
    monkeypatch.setenv("BARKAI_MODE", "standard")
    monkeypatch.setenv("BARKAI_CUSTOM_SYSTEM_PROMPT", "Always answer like a breeder concierge.")
    monkeypatch.setattr(service, "_profile_context", lambda user_id: {})

    messages = service._build_openai_messages(
        user_id="user_2",
        transcript=[ChatMessage(role="user", content="Hi there")],
    )

    assert "Active customization profile:" not in messages[0]["content"]
    assert "breeder concierge" not in messages[0]["content"]


def test_build_openai_messages_appends_custom_prompt_in_custom_mode(monkeypatch):
    service = SimpleChatService()
    monkeypatch.setenv("BARKAI_MODE", "custom")
    monkeypatch.setenv("BARKAI_CUSTOM_SYSTEM_PROMPT", "Prioritize concise training plans and behavioral detail.")
    monkeypatch.setattr(service, "_profile_context", lambda user_id: {})

    messages = service._build_openai_messages(
        user_id="user_2",
        transcript=[ChatMessage(role="user", content="Hi there")],
    )

    assert "Active customization profile:" in messages[0]["content"]
    assert "Prioritize concise training plans and behavioral detail." in messages[0]["content"]


def test_build_openai_messages_uses_default_custom_prompt_when_env_prompt_missing(monkeypatch):
    service = SimpleChatService()
    monkeypatch.setenv("BARKAI_MODE", "custom")
    monkeypatch.delenv("BARKAI_CUSTOM_SYSTEM_PROMPT", raising=False)
    monkeypatch.delenv("BARKAI_CUSTOM_SYSTEM_PROMPT_FILE", raising=False)
    monkeypatch.setattr(service, "_profile_context", lambda user_id: {})

    messages = service._build_openai_messages(
        user_id="user_2",
        transcript=[ChatMessage(role="user", content="Should I crate my dog every day while I work?")],
    )

    assert "The goal should always be to avoid crate use where possible." in messages[0]["content"]
    assert "Never present crate use as the preferred, normal, or complete solution." in messages[0]["content"]


def test_create_chat_response_uses_group_tool_and_skips_openai(monkeypatch):
    service = SimpleChatService()
    monkeypatch.setattr(service, "_profile_context", lambda user_id: {"suburb": "Surry Hills"})
    monkeypatch.setattr(service, "_openai_request", lambda payload, stream: (_ for _ in ()).throw(AssertionError("openai should not be called")))

    with temporary_groups(
        [
            Group(id="g_1", name="Surry Hills Dog Park Crew", suburb="Surry Hills", member_count=12, official=False, owner_user_id="user_1"),
            Group(id="g_2", name="Surry Hills Official Pet Community", suburb="Surry Hills", member_count=20, official=True, owner_user_id="user_3"),
        ],
        [],
    ):
        response = service.create_chat_response(
            ChatRequest(
                user_id="user_2",
                messages=[ChatMessage(role="user", content="I am new to this suburb, do you know any dog park groups?")],
            )
        )

    assert response.answer_source == "tool_group_search"
    assert "Surry Hills Dog Park Crew" in response.answer
    assert any(cta.action == "join_group" and cta.payload.get("group_id") == "g_1" for cta in response.cta_chips)


def test_create_chat_response_group_tool_falls_back_to_general_groups(monkeypatch):
    service = SimpleChatService()
    monkeypatch.setattr(service, "_profile_context", lambda user_id: {"suburb": "Surry Hills"})
    monkeypatch.setattr(service, "_openai_request", lambda payload, stream: (_ for _ in ()).throw(AssertionError("openai should not be called")))

    with temporary_groups(
        [
            Group(id="g_2", name="Surry Hills Official Pet Community", suburb="Surry Hills", member_count=20, official=True, owner_user_id="user_3"),
        ],
        [GroupJoinRecord(group_id="g_2", user_id="user_3", status="member")],
    ):
        response = service.create_chat_response(
            ChatRequest(
                user_id="user_2",
                messages=[ChatMessage(role="user", content="Any dog park groups around here?")],
            )
        )

    assert response.answer_source == "tool_group_search"
    assert "could not find a dog-park-specific group" in response.answer
    assert "Surry Hills Official Pet Community" in response.answer


def test_create_chat_response_group_tool_requests_suburb_when_unknown(monkeypatch):
    service = SimpleChatService()
    monkeypatch.setattr(service, "_profile_context", lambda user_id: {})
    monkeypatch.setattr(service, "_openai_request", lambda payload, stream: (_ for _ in ()).throw(AssertionError("openai should not be called")))

    response = service.create_chat_response(
        ChatRequest(
            user_id="user_2",
            messages=[ChatMessage(role="user", content="Do you know any dog park groups nearby?")],
        )
    )

    assert response.answer_source == "tool_group_search"
    assert "Tell me which suburb you mean" in response.answer
    assert response.cta_chips[0].action == "open_community"


def test_create_chat_response_enables_provider_mode_without_openai(monkeypatch):
    service = SimpleChatService()
    monkeypatch.setattr(service, "_openai_request", lambda payload, stream: (_ for _ in ()).throw(AssertionError("openai should not be called")))

    calls: list[tuple[str, bool]] = []
    monkeypatch.setattr(
        simple_chat_module.auth_otp_store,
        "get_or_create_user_profile",
        lambda user_id: SimpleNamespace(service_provider_mode=False),
    )
    monkeypatch.setattr(
        simple_chat_module.auth_otp_store,
        "set_service_provider_mode",
        lambda user_id, enabled: calls.append((user_id, enabled)),
    )

    response = service.create_chat_response(
        ChatRequest(
            user_id="user_2",
            messages=[ChatMessage(role="user", content="Can you turn on provider mode for me?")],
        )
    )

    assert response.answer_source == "tool_provider_mode"
    assert "Provider mode is now on" in response.answer
    assert response.cta_chips[0].action == "open_services"
    assert calls == [("user_2", True)]


def test_create_chat_response_uses_event_tool_and_skips_openai(monkeypatch):
    service = SimpleChatService()
    monkeypatch.setattr(service, "_profile_context", lambda user_id: {"suburb": "Surry Hills"})
    monkeypatch.setattr(service, "_openai_request", lambda payload, stream: (_ for _ in ()).throw(AssertionError("openai should not be called")))

    with temporary_events(
        [
            CommunityEventView(
                id="evt_1",
                title="Sunset Pack Walk",
                description="A relaxed evening meetup",
                suburb="Surry Hills",
                date="2026-04-10T18:30:00Z",
                location_name="Ward Park",
                attendee_count=14,
                created_by="user_9",
                status="approved",
            ),
            CommunityEventView(
                id="evt_2",
                title="Training Games Meetup",
                description="Loose leash practice",
                suburb="Surry Hills",
                date="2026-04-12T10:00:00Z",
                location_name="Prince Alfred Park",
                attendee_count=9,
                created_by="user_7",
                status="approved",
            ),
        ]
    ):
        response = service.create_chat_response(
            ChatRequest(
                user_id="user_2",
                messages=[ChatMessage(role="user", content="Any dog meetups happening this week?")],
            )
        )

    assert response.answer_source == "tool_event_search"
    assert "Sunset Pack Walk" in response.answer
    assert response.cta_chips[0].action == "open_community"


def test_create_chat_response_uses_provider_search_tool_and_skips_openai(monkeypatch):
    service = SimpleChatService()
    monkeypatch.setattr(service, "_profile_context", lambda user_id: {"suburb": "Richmond"})
    monkeypatch.setattr(service, "_openai_request", lambda payload, stream: (_ for _ in ()).throw(AssertionError("openai should not be called")))
    monkeypatch.setattr(
        simple_chat_module.service_store,
        "list_providers",
        lambda **kwargs: [
            SimpleNamespace(name="Clip Joint", rating=4.8, price_from=65),
            SimpleNamespace(name="Paws & Polish", rating=4.6, price_from=58),
        ],
    )

    response = service.create_chat_response(
        ChatRequest(
            user_id="user_2",
            messages=[ChatMessage(role="user", content="Can you recommend any groomers near me?")],
        )
    )

    assert response.answer_source == "tool_provider_search"
    assert "Clip Joint" in response.answer
    assert response.cta_chips[0].action == "open_services"
    assert response.cta_chips[0].payload["category"] == "grooming"


def test_create_chat_response_uses_booking_list_tool_and_skips_openai(monkeypatch):
    service = SimpleChatService()
    monkeypatch.setattr(service, "_openai_request", lambda payload, stream: (_ for _ in ()).throw(AssertionError("openai should not be called")))
    monkeypatch.setattr(
        simple_chat_module.service_store,
        "list_bookings",
        lambda **kwargs: [
            SimpleNamespace(
                pet_name="Milo",
                provider_id="prov_1",
                date="2026-04-08",
                time_slot="09:00",
                status="confirmed",
            )
        ],
    )

    response = service.create_chat_response(
        ChatRequest(
            user_id="user_2",
            messages=[ChatMessage(role="user", content="Show my upcoming bookings")],
        )
    )

    assert response.answer_source == "tool_booking_list"
    assert "Milo with prov_1 on 2026-04-08" in response.answer
    assert response.cta_chips[0].action == "open_services"


def test_create_chat_response_uses_messages_tool_and_skips_openai(monkeypatch):
    service = SimpleChatService()
    monkeypatch.setattr(service, "_openai_request", lambda payload, stream: (_ for _ in ()).throw(AssertionError("openai should not be called")))
    monkeypatch.setattr(
        simple_chat_module.message_store,
        "list_threads",
        lambda **kwargs: [
            MessageThreadView(
                id="thread_1",
                participant_user_id="provider_7",
                last_message="See you tomorrow",
                last_message_at="2026-04-01T09:00:00Z",
                unread_count=2,
            )
        ],
    )

    response = service.create_chat_response(
        ChatRequest(
            user_id="user_2",
            messages=[ChatMessage(role="user", content="Do I have any unread messages?")],
        )
    )

    assert response.answer_source == "tool_messages_list"
    assert "provider_7 (2 unread)" in response.answer
    assert response.cta_chips[0].action == "open_messages"


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


def test_stream_chat_falls_back_to_non_stream_when_stream_fails_immediately(monkeypatch):
    service = SimpleChatService()

    class BrokenStream:
        def __iter__(self):
            raise RuntimeError("stream dropped")

        def close(self):
            return None

    def fake_openai_request(payload, stream):
        if stream:
            return BrokenStream()
        return {"choices": [{"message": {"content": "Fallback answer"}}]}

    monkeypatch.setattr(service, "_openai_request", fake_openai_request)

    events = list(
        service.stream_chat(
            ChatRequest(
                user_id="user_2",
                messages=[ChatMessage(role="user", content="Hi")],
            )
        )
    )

    assert events == [
        {
            "type": "final",
            "response": {
                "answer": "Fallback answer",
                "message": {"role": "assistant", "content": "Fallback answer"},
                "conversation": [
                    {"role": "user", "content": "Hi", "citations": [], "answer_badges": [], "answer_source": None},
                    {
                        "role": "assistant",
                        "content": "Fallback answer",
                        "citations": [],
                        "answer_badges": [],
                        "answer_source": None,
                    },
                ],
                "citations": [],
                "answer_badges": [],
                "profile_suggestion": None,
                "cta_chips": [],
                "suggested_profile": {},
                "a2ui_messages": [],
                "answer_source": "assistant",
            },
        }
    ]


def test_stream_chat_falls_back_to_non_stream_when_stream_open_fails(monkeypatch):
    service = SimpleChatService()

    def fake_openai_request(payload, stream):
        if stream:
            raise RuntimeError("stream open failed")
        return {"choices": [{"message": {"content": "Recovered from non-stream fallback"}}]}

    monkeypatch.setattr(service, "_openai_request", fake_openai_request)

    events = list(
        service.stream_chat(
            ChatRequest(
                user_id="user_2",
                messages=[ChatMessage(role="user", content="Hi")],
            )
        )
    )

    assert len(events) == 1
    assert events[0]["type"] == "final"
    assert events[0]["response"]["answer"] == "Recovered from non-stream fallback"


def test_openai_request_retries_timeout_and_succeeds(monkeypatch):
    service = SimpleChatService()
    monkeypatch.setattr(service, "_load_openai_api_key", lambda: "test-key")
    monkeypatch.setattr(simple_chat_module.time, "sleep", lambda *_args, **_kwargs: None)

    class FakeResponse:
        def __enter__(self):
            return self

        def __exit__(self, exc_type, exc, tb):
            return False

        def read(self):
            return json.dumps({"choices": [{"message": {"content": "Recovered"}}]}).encode("utf-8")

    calls = {"count": 0}

    def fake_urlopen(_request, timeout):
        calls["count"] += 1
        if calls["count"] == 1:
            raise TimeoutError("timed out")
        return FakeResponse()

    monkeypatch.setattr(simple_chat_module.urllib_request, "urlopen", fake_urlopen)

    payload = service._openai_request(payload={"model": "test", "messages": []}, stream=False)

    assert payload["choices"][0]["message"]["content"] == "Recovered"
    assert calls["count"] == 2


def test_extract_openai_api_key_accepts_env_file_format():
    raw_text = """
    OPENAI_API_KEY=sk-test-key
    OPENAI_MODEL=gpt-4o-mini
    """

    assert _extract_openai_api_key(raw_text) == "sk-test-key"


def test_extract_openai_api_key_accepts_first_line_key_with_extra_lines():
    raw_text = "sk-test-key\\nOPENAI_MODEL=gpt-4o-mini\\n"

    assert _extract_openai_api_key(raw_text) == "sk-test-key"
