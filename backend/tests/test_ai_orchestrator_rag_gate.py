import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from app.services.ai_orchestrator import AIOrchestrator


def _minimal_plan():
    return {"intent": "general_pet_question", "tools": [], "suggested_profile": {}}


def test_rag_terms_default_and_trigger_match():
    orchestrator = AIOrchestrator()
    assert "parvo" in orchestrator.rag_trigger_terms
    assert orchestrator._should_apply_rag("Could this be PARVO?")
    assert not orchestrator._should_apply_rag("What should I feed my puppy tonight?")
    assert not orchestrator._should_apply_rag("My dog has matted paws after a walk.")


def test_rag_term_matching_uses_word_boundaries():
    orchestrator = AIOrchestrator()
    assert orchestrator._should_apply_rag("Could this be a UTI?")
    assert orchestrator._should_apply_rag("Could this be kennel-cough?")
    assert not orchestrator._should_apply_rag("This feels like futility, not a pet issue.")


def test_rag_terms_can_be_overridden_with_env(monkeypatch):
    monkeypatch.setenv("RAG_TRIGGER_TERMS", "trigger-one, trigger-two")
    orchestrator = AIOrchestrator()
    assert orchestrator.rag_trigger_terms == ["trigger-one", "trigger-two"]
    assert orchestrator._should_apply_rag("please use TRIGGER-TWO now")
    assert not orchestrator._should_apply_rag("Could this be parvo?")


def test_handle_message_skips_rag_without_trigger(monkeypatch):
    orchestrator = AIOrchestrator()
    rag_build_calls = {"count": 0}
    seen_rag_context = {"documents": None}

    monkeypatch.setattr(orchestrator, "_build_plan", lambda **_: _minimal_plan())
    monkeypatch.setattr(orchestrator, "_execute_tools", lambda **_: {})
    monkeypatch.setattr(orchestrator, "_should_start_provider_onboarding", lambda *_: False)
    monkeypatch.setattr(orchestrator, "_safety_guard", lambda *_: None)

    def fake_build_context(**_):
        rag_build_calls["count"] += 1
        return {"documents": [{"title": "doc"}]}

    def fake_compose_answer(**kwargs):
        seen_rag_context["documents"] = kwargs["rag_context"].get("documents")
        return "ok"

    monkeypatch.setattr(orchestrator.rag_retriever, "build_context", fake_build_context)
    monkeypatch.setattr(orchestrator, "_compose_answer", fake_compose_answer)

    response = orchestrator.handle_message("normal app chat message", user_id="rag_gate_user_1")
    assert response.answer == "ok"
    assert rag_build_calls["count"] == 0
    assert seen_rag_context["documents"] == []


def test_handle_message_applies_rag_with_trigger(monkeypatch):
    orchestrator = AIOrchestrator()
    rag_build_calls = {"count": 0}

    monkeypatch.setattr(orchestrator, "_build_plan", lambda **_: _minimal_plan())
    monkeypatch.setattr(orchestrator, "_execute_tools", lambda **_: {})
    monkeypatch.setattr(orchestrator, "_should_start_provider_onboarding", lambda *_: False)
    monkeypatch.setattr(orchestrator, "_safety_guard", lambda *_: None)
    monkeypatch.setattr(orchestrator, "_compose_answer", lambda **_: "ok")

    def fake_build_context(**_):
        rag_build_calls["count"] += 1
        return {"documents": [{"title": "doc"}]}

    monkeypatch.setattr(orchestrator.rag_retriever, "build_context", fake_build_context)

    response = orchestrator.handle_message("Could this be parvo in puppies?", user_id="rag_gate_user_2")
    assert response.answer == "ok"
    assert rag_build_calls["count"] == 1


def test_anxious_rag_query_returns_reassuring_rag_first_fallback():
    orchestrator = AIOrchestrator()
    session = orchestrator._get_session("rag_gate_user_3")
    plan = _minimal_plan()
    rag_context = {
        "documents": [
            {
                "title": "Parvovirus Triage",
                "snippet": "Watch hydration closely and seek urgent vet care for persistent vomiting.",
                "authority": "AAHA",
            }
        ]
    }

    answer = orchestrator._compose_answer(
        message="I am really worried my dog might have parvo.",
        suburb=None,
        plan=plan,
        route={"lane": "RAG", "reason": "trigger_terms"},
        tool_results={},
        session=session,
        rag_context=rag_context,
    )

    assert answer.startswith("I know this can feel stressful")
    assert "From what I can see in your local BarkAI data:" in answer


def test_known_breed_query_is_not_general_assistant():
    orchestrator = AIOrchestrator()
    assert not orchestrator._is_general_assistant_query("tell me about king charles caveliers")


def test_known_breed_query_returns_breed_summary():
    orchestrator = AIOrchestrator()
    session = orchestrator._get_session("rag_gate_user_4")
    plan = _minimal_plan()
    answer = orchestrator._compose_answer(
        message="tell me about king charles caveliers",
        suburb=None,
        plan=plan,
        route={"lane": "GENERAL", "reason": "default_general_pet"},
        tool_results={},
        session=session,
        rag_context={"documents": []},
    )

    lower = answer.lower()
    assert "cavalier king charles spaniels" in lower
    assert "moderate daily exercise" in lower


def test_known_breed_summary_does_not_override_vaccine_question():
    orchestrator = AIOrchestrator()
    assert orchestrator._known_breed_summary("tell me about king charles caveliers") is not None
    assert orchestrator._known_breed_summary("what vaccines does a king charles caveliers dog need?") is None


def test_route_query_prefers_app_lane_when_plan_has_tools():
    orchestrator = AIOrchestrator()
    route = orchestrator._route_query(
        message="show me dog walkers near me",
        plan={"intent": "find_dog_walker", "tools": [{"name": "search_services", "args": {}}]},
    )
    assert route["lane"] == "APP"


def test_route_query_uses_rag_lane_for_triggered_general_question():
    orchestrator = AIOrchestrator()
    route = orchestrator._route_query(
        message="Could this be parvo?",
        plan={"intent": "general_pet_question", "tools": []},
    )
    assert route["lane"] == "RAG"
    assert route["rag_triggered"] is True
    assert "parvo" in route["matched_terms"]


def test_route_query_uses_general_lane_without_trigger_or_tools():
    orchestrator = AIOrchestrator()
    route = orchestrator._route_query(
        message="tell me about king charles caveliers",
        plan={"intent": "general_pet_question", "tools": []},
    )
    assert route["lane"] == "GENERAL"
    assert route["rag_triggered"] is False
    assert route["matched_terms"] == []


def test_matched_rag_terms_returns_multiple_matches():
    orchestrator = AIOrchestrator()
    matches = orchestrator._matched_rag_trigger_terms("My dog has vomiting and possible parvo symptoms.")
    assert "vomiting" in matches
    assert "parvo" in matches


def test_heuristic_general_pet_question_has_no_tools():
    orchestrator = AIOrchestrator()
    plan = orchestrator._heuristic_plan("tell me about king charles caveliers", suburb=None)
    assert plan["intent"] == "general_pet_question"
    assert plan["tools"] == []


def test_execute_tools_accepts_string_limit_without_crashing():
    orchestrator = AIOrchestrator()
    session = orchestrator._get_session("rag_gate_user_5")
    result = orchestrator._execute_tools(
        tool_calls=[{"name": "search_services", "args": {"category": "dog_walking", "limit": "3"}}],
        message="find walkers",
        suburb=None,
        session=session,
        user_id="rag_gate_user_5",
    )
    assert "search_services" in result
    assert isinstance(result["search_services"], list)


def test_add_service_listing_invalid_price_uses_default(monkeypatch):
    orchestrator = AIOrchestrator()
    session = orchestrator._get_session("rag_gate_user_6")
    session.provider.collected = {
        "service_name": "Demo Walks",
        "category": "dog_walking",
        "suburb": "Surry Hills",
        "description": "Friendly daily walks",
        "price_from": "not-a-number",
        "contact_name": "Alex",
    }

    captured = {}

    class _ProviderStub:
        def model_dump(self):
            return {"id": "svc_test", "category": "dog_walking"}

    def fake_add_provider(**kwargs):
        captured.update(kwargs)
        return _ProviderStub()

    monkeypatch.setattr("app.services.ai_orchestrator.service_store.add_provider", fake_add_provider)

    result = orchestrator._tool_add_service_listing(
        session=session,
        message="submit listing",
        suburb="Surry Hills",
        user_id="rag_gate_user_6",
        args={"price_from": "not-a-number"},
    )
    assert result["status"] == "created"
    assert captured["price_from"] == 30


def test_handle_message_emits_route_telemetry(caplog, monkeypatch):
    orchestrator = AIOrchestrator()

    monkeypatch.setattr(orchestrator, "_build_plan", lambda **_: _minimal_plan())
    monkeypatch.setattr(orchestrator, "_execute_tools", lambda **_: {})
    monkeypatch.setattr(orchestrator, "_should_start_provider_onboarding", lambda *_: False)
    monkeypatch.setattr(orchestrator, "_safety_guard", lambda *_: None)
    monkeypatch.setattr(orchestrator, "_compose_answer", lambda **_: "ok")
    monkeypatch.setattr(orchestrator.rag_retriever, "build_context", lambda **_: {"documents": [{"title": "doc"}]})

    with caplog.at_level("INFO", logger="app.services.ai_orchestrator"):
        orchestrator.handle_message("Could this be parvo in puppies?", user_id="rag_gate_user_7")

    assert any("route_telemetry=" in record.message for record in caplog.records)
