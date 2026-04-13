import os
import sys
from contextlib import contextmanager
from types import SimpleNamespace

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from app.data import community_events, event_rsvps, group_memberships, groups
from app.models import ChatMessage, ChatRequest, CommunityEventView, Group, GroupJoinRecord, MessageThreadView
from app.services import chat_tools as chat_tools_module
from app.services import simple_chat_service as simple_chat_module
from app.services.chat_eval import APP_INTENT_EVAL_CASES, DOG_ADVICE_EVAL_CASES, MIXED_EVAL_CASES, UNSAFE_REPLY_REGRESSION_CASES
from app.services.chat_router import PendingConfirmation
from app.services.llm_client import LlmClient, extract_openai_api_key
from app.services.simple_chat_service import SimpleChatService


@contextmanager
def temporary_groups(temp_groups, temp_memberships):
    original_groups = list(groups)
    original_memberships = list(group_memberships)
    groups[:] = temp_groups
    group_memberships[:] = temp_memberships
    try:
        yield
    finally:
        groups[:] = original_groups
        group_memberships[:] = original_memberships


@contextmanager
def temporary_events(temp_events, temp_rsvps=None):
    original_events = list(community_events)
    original_rsvps = list(event_rsvps)
    community_events[:] = temp_events
    event_rsvps[:] = temp_rsvps or []
    try:
        yield
    finally:
        community_events[:] = original_events
        event_rsvps[:] = original_rsvps


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


def test_build_openai_messages_includes_profile_and_custom_policy(monkeypatch):
    service = SimpleChatService()
    monkeypatch.setenv("BARKAI_MODE", "custom")
    monkeypatch.setattr(
        service,
        "_profile_context",
        lambda user_id: {
            "display_name": "Alex",
            "dog_name": "Milo",
            "suburb": "Richmond",
            "play_style": "gentle one-on-one play",
            "trigger_notes": "guards tennis balls",
        },
    )
    monkeypatch.setattr(service, "_load_user_state", lambda user_id: {"preferences": {"preferred_suburb": "Richmond"}, "conversation_summary": "Dog is shy at parks."})

    messages = service._build_openai_messages(
        user_id="user_2",
        transcript=[ChatMessage(role="user", content="Should I crate my dog every day while I work?")],
    )

    assert messages[0]["role"] == "system"
    assert "Milo" in messages[0]["content"]
    assert "guards tennis balls" in messages[0]["content"]
    assert "Use saved profile context" in messages[0]["content"]
    assert "The goal is to avoid crate use." in messages[0]["content"]
    assert "Dog is shy at parks." in messages[0]["content"]


def test_profile_context_includes_behavior_and_preference_fields(monkeypatch):
    service = SimpleChatService()
    monkeypatch.setattr(
        simple_chat_module.auth_otp_store,
        "get_or_create_user_profile",
        lambda user_id: SimpleNamespace(
            service_provider_mode=False,
            display_name="Alex",
            suburb="Richmond",
            dog_name="Milo",
            dog_breed_mix="Kelpie mix",
            dog_sex_neuter="male neutered",
            dog_age_months=30,
            dog_weight_class="12 kg",
            secondary_dog_name="Nori",
            secondary_dog_age_months=18,
            secondary_dog_gender="female",
            secondary_dog_weight_kg="9",
            bio="Sensitive rescue dog",
            favorite_suburbs=["Richmond", "Collingwood"],
            play_energy_level="medium",
            play_style="gentle one-on-one play",
            social_confidence="slow warm-up",
            trigger_notes="guards tennis balls",
            ideal_match="calm dogs",
            walk_preferences="quiet morning walks",
            training_style="force-free",
            feeding_rules="no shared bowls",
            consent_boundaries="no surprise pats",
            vaccination_status="up to date",
            microchipped=True,
            recall_trained=False,
            leash_reliability="needs leash near roads",
        ),
    )

    context = service._profile_context(user_id="user_2")

    assert context["dog_name"] == "Milo"
    assert context["dog_age_months"] == 30
    assert context["play_style"] == "gentle one-on-one play"
    assert context["trigger_notes"] == "guards tennis balls"
    assert context["training_style"] == "force-free"
    assert context["favorite_suburbs"] == ["Richmond", "Collingwood"]
    assert context["microchipped"] is True
    assert "recall_trained" not in context


def test_build_openai_messages_omits_custom_policy_in_standard_mode(monkeypatch):
    service = SimpleChatService()
    monkeypatch.setenv("BARKAI_MODE", "standard")
    monkeypatch.setattr(service, "_profile_context", lambda user_id: {})
    monkeypatch.setattr(service, "_load_user_state", lambda user_id: {"preferences": {}, "conversation_summary": ""})

    messages = service._build_openai_messages(
        user_id="user_2",
        transcript=[ChatMessage(role="user", content="Hi there")],
    )

    assert "welfare-first" not in messages[0]["content"]
    assert "crate use" not in messages[0]["content"]


def test_create_chat_response_uses_group_tool_and_returns_confirmation_cta(monkeypatch):
    service = SimpleChatService()
    monkeypatch.setattr(service, "_profile_context", lambda user_id: {"suburb": "Surry Hills"})

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
    assert any(cta.action == "send_bark_message" and cta.payload.get("message") == "join group g_1" for cta in response.cta_chips)


def test_create_chat_response_group_tool_requests_suburb_when_unknown(monkeypatch):
    service = SimpleChatService()
    monkeypatch.setattr(service, "_profile_context", lambda user_id: {})

    response = service.create_chat_response(
        ChatRequest(
            user_id="user_2",
            messages=[ChatMessage(role="user", content="Do you know any dog park groups nearby?")],
        )
    )

    assert response.answer_source == "tool_group_search"
    assert "Tell me which suburb" in response.answer


def test_create_chat_response_requires_confirmation_for_provider_mode(monkeypatch):
    service = SimpleChatService()
    calls: list[tuple[str, bool]] = []
    monkeypatch.setattr(
        simple_chat_module.auth_otp_store,
        "get_or_create_user_profile",
        lambda user_id: SimpleNamespace(service_provider_mode=False, display_name="", suburb="", dog_name="", dog_breed_mix="", dog_age_months=0, dog_weight_class="", bio=""),
    )
    monkeypatch.setattr(
        simple_chat_module.auth_otp_store,
        "set_service_provider_mode",
        lambda user_id, enabled: calls.append((user_id, enabled)),
    )

    response = service.create_chat_response(
        ChatRequest(
            user_id="user_2",
            messages=[ChatMessage(role="user", content="Turn on provider mode for me")],
        )
    )

    assert response.status == "needs_confirmation"
    assert response.error_type == "confirmation_required"
    assert calls == []
    assert response.pending_confirmation is not None


def test_create_chat_response_executes_confirmed_provider_mode(monkeypatch):
    service = SimpleChatService()
    monkeypatch.setenv("BARKAI_ENABLE_MUTATING_ACTIONS", "true")
    service.tools.mutating_actions_enabled = True
    calls: list[tuple[str, bool]] = []
    monkeypatch.setattr(
        simple_chat_module.auth_otp_store,
        "get_or_create_user_profile",
        lambda user_id: SimpleNamespace(service_provider_mode=False, display_name="", suburb="", dog_name="", dog_breed_mix="", dog_age_months=0, dog_weight_class="", bio=""),
    )
    monkeypatch.setattr(
        simple_chat_module.auth_otp_store,
        "set_service_provider_mode",
        lambda user_id, enabled: calls.append((user_id, enabled)),
    )
    monkeypatch.setattr(
        service,
        "_load_user_state",
        lambda user_id: {
            "profile_memory": {},
            "profile_accepted": False,
            "field_locks": {},
            "provider_state": {},
            "preferences": {},
            "conversation_summary": "",
            "pending_confirmation": PendingConfirmation(
                action="provider_mode_enable",
                prompt="I can turn on provider mode for your account. Do you want me to go ahead?",
                params={},
                expires_at="2099-01-01T00:00:00Z",
            ).to_dict(),
        },
    )

    response = service.create_chat_response(
        ChatRequest(
            user_id="user_2",
            messages=[ChatMessage(role="user", content="Yes, confirm")],
        )
    )

    assert response.answer_source == "tool_provider_mode"
    assert "Provider mode is now on" in response.answer
    assert calls == [("user_2", True)]


def test_create_chat_response_uses_event_tool_and_returns_rsvp_cta(monkeypatch):
    service = SimpleChatService()
    monkeypatch.setattr(service, "_profile_context", lambda user_id: {"suburb": "Surry Hills"})

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
        ]
    ):
        response = service.create_chat_response(
            ChatRequest(
                user_id="user_2",
                messages=[ChatMessage(role="user", content="Any dog meetups happening this week?")],
            )
        )

    assert response.answer_source == "tool_event_search"
    assert any(cta.action == "send_bark_message" and cta.payload.get("message") == "rsvp event evt_1" for cta in response.cta_chips)


def test_create_chat_response_uses_provider_search_tool(monkeypatch):
    service = SimpleChatService()
    monkeypatch.setattr(service, "_profile_context", lambda user_id: {"suburb": "Richmond"})
    monkeypatch.setattr(
        simple_chat_module.service_store,
        "list_providers",
        lambda **kwargs: [
            SimpleNamespace(id="prov_1", name="Clip Joint", rating=4.8, price_from=65),
            SimpleNamespace(id="prov_2", name="Paws & Polish", rating=4.6, price_from=58),
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


def test_create_chat_response_uses_provider_availability_tool(monkeypatch):
    service = SimpleChatService()
    monkeypatch.setattr(
        simple_chat_module.service_store,
        "get_available_slots",
        lambda provider_id, slot_date: [
            SimpleNamespace(time_slot="09:00", available=True),
            SimpleNamespace(time_slot="09:30", available=False),
            SimpleNamespace(time_slot="10:00", available=True),
        ],
    )

    response = service.create_chat_response(
        ChatRequest(
            user_id="user_2",
            messages=[ChatMessage(role="user", content="Availability for provider prov_1 on 2026-04-12")],
        )
    )

    assert response.answer_source == "tool_provider_availability"
    assert "09:00" in response.answer
    assert any(cta.action == "send_bark_message" for cta in response.cta_chips)


def test_create_chat_response_uses_booking_list_tool(monkeypatch):
    service = SimpleChatService()
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


def test_create_chat_response_uses_messages_tool(monkeypatch):
    service = SimpleChatService()
    monkeypatch.setattr(
        chat_tools_module.message_store,
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


def test_create_chat_response_falls_back_to_llm(monkeypatch):
    service = SimpleChatService()
    monkeypatch.setattr(service.llm_client, "generate_text", lambda messages: "Hello back")

    response = service.create_chat_response(
        ChatRequest(
            user_id="user_2",
            messages=[ChatMessage(role="user", content="Hello")],
        )
    )

    assert response.answer == "Hello back"
    assert response.status == "ok"
    assert response.error_type is None


def test_create_chat_response_returns_structured_error_when_llm_fails(monkeypatch):
    service = SimpleChatService()
    monkeypatch.setattr(
        service.llm_client,
        "generate_text",
        lambda messages: (_ for _ in ()).throw(simple_chat_module.LlmClientError(category="llm_unavailable", detail="boom")),
    )

    response = service.create_chat_response(
        ChatRequest(
            user_id="user_2",
            messages=[ChatMessage(role="user", content="Hello")],
        )
    )

    assert response.status == "error"
    assert response.error_type == "llm_unavailable"
    assert "trouble reaching" in response.answer


def test_create_chat_response_reports_llm_unavailable_when_openai_missing(monkeypatch):
    service = SimpleChatService()
    monkeypatch.setattr(service.llm_client, "_load_openai_api_key", lambda: "")

    response = service.create_chat_response(
        ChatRequest(
            user_id="user_2",
            messages=[ChatMessage(role="user", content="Hello")],
        )
    )

    assert response.status == "error"
    assert response.error_type == "llm_unavailable"
    assert "language service" in response.answer


def test_stream_chat_yields_tool_final_without_hitting_llm(monkeypatch):
    service = SimpleChatService()
    monkeypatch.setattr(service, "_profile_context", lambda user_id: {"suburb": "Surry Hills"})
    monkeypatch.setattr(
        service.llm_client,
        "stream_text",
        lambda messages: (_ for _ in ()).throw(AssertionError("llm should not be called")),
    )
    with temporary_groups(
        [
            Group(id="g_1", name="Surry Hills Dog Park Crew", suburb="Surry Hills", member_count=12, official=False, owner_user_id="user_1"),
        ],
        [],
    ):
        events = list(
            service.stream_chat(
                ChatRequest(
                    user_id="user_2",
                    messages=[ChatMessage(role="user", content="Any dog park groups around here?")],
                )
            )
        )

    assert events[0]["type"] == "delta"
    assert events[1]["type"] == "final"
    assert events[1]["response"]["answer_source"] == "tool_group_search"


def test_stream_chat_falls_back_to_non_stream(monkeypatch):
    service = SimpleChatService()
    monkeypatch.setattr(
        service.llm_client,
        "stream_text",
        lambda messages: (_ for _ in ()).throw(simple_chat_module.LlmClientError(category="backend_unavailable", detail="stream failed")),
    )
    monkeypatch.setattr(service.llm_client, "generate_text", lambda messages: "Fallback answer")

    events = list(
        service.stream_chat(
            ChatRequest(
                user_id="user_2",
                messages=[ChatMessage(role="user", content="Hi")],
            )
        )
    )

    assert events[-1]["type"] == "final"
    assert events[-1]["response"]["answer"] == "Fallback answer"


def test_extract_openai_api_key_accepts_env_file_format():
    raw_text = """
    OPENAI_API_KEY=sk-test-key
    OPENAI_MODEL=gpt-4o-mini
    """

    assert extract_openai_api_key(raw_text) == "sk-test-key"


def test_extract_openai_api_key_accepts_first_line_key_with_extra_lines():
    raw_text = "sk-test-key\\nOPENAI_MODEL=gpt-4o-mini\\n"

    assert extract_openai_api_key(raw_text) == "sk-test-key"


def test_chat_eval_fixtures_cover_expected_categories():
    assert len(DOG_ADVICE_EVAL_CASES) >= 25
    assert len(APP_INTENT_EVAL_CASES) >= 15
    assert len(MIXED_EVAL_CASES) >= 10
    assert len(UNSAFE_REPLY_REGRESSION_CASES) >= 10


def test_synthetic_probe_accepts_ok_variations():
    assert LlmClient._synthetic_probe_passed("OK")
    assert LlmClient._synthetic_probe_passed("OK.")
    assert LlmClient._synthetic_probe_passed("Okay, BarkAI is healthy.")
    assert LlmClient._synthetic_probe_passed("Sure, OK")
    assert not LlmClient._synthetic_probe_passed("")
    assert not LlmClient._synthetic_probe_passed("Healthy")
