import importlib
import os
import sys


sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(__file__)), "scripts"))


def _reload(module_name: str):
    if module_name in sys.modules:
        return importlib.reload(sys.modules[module_name])
    return importlib.import_module(module_name)


def test_golden_prompt_confirmation_case_requires_tokenized_cta():
    script = _reload("run_barkai_golden_prompts")
    case = next(item for item in script.GOLDEN_CASES if item.name == "provider_mode_confirmation")

    valid_response = {
        "status": "needs_confirmation",
        "answer_source": "tool_confirmation",
        "answer": 'I can turn on provider mode. Reply "Confirm abc123" to continue.',
        "pending_confirmation": {"action": "provider_mode_enable"},
        "cta_chips": [{"action": "send_bark_message", "payload": {"message": "Confirm abc123"}}],
    }
    missing_token_response = {
        **valid_response,
        "answer": "I can turn on provider mode. Do you want me to go ahead?",
        "cta_chips": [{"action": "send_bark_message", "payload": {"message": "Yes, confirm"}}],
    }

    assert script.validate_case(case, valid_response) == []
    assert any("CTA payload" in failure for failure in script.validate_case(case, missing_token_response))


def test_red_team_validation_blocks_generic_confirmation_execution():
    script = _reload("run_barkai_red_team_prompts")
    case = next(item for item in script.RED_TEAM_CASES if item.name == "generic_yes_does_not_execute_tokenized_confirmation")

    failures = script.validate_case(
        case,
        {
            "status": "ok",
            "answer_source": "tool_provider_mode",
            "answer": "Provider mode is now on.",
            "cta_chips": [],
        },
    )

    assert any("forbidden answer_source" in failure for failure in failures)
    assert any("forbidden text" in failure for failure in failures)


def test_demo_script_extracts_confirmation_payload_and_prints_script(capsys):
    script = _reload("demo_barkai_tool_vs_llm_paths")

    assert (
        script.confirmation_payload(
            {
                "cta_chips": [
                    {"action": "send_bark_message", "payload": {"message": "Cancel"}},
                    {"action": "send_bark_message", "payload": {"message": "Confirm abc123"}},
                ]
            }
        )
        == "Confirm abc123"
    )

    script.print_script_only()
    output = capsys.readouterr().out
    assert "BarkAI Tool Path vs LLM Path UI Demo" in output
    assert "Tool-action hardening: generic yes rejected" in output
