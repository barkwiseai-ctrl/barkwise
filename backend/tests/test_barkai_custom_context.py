import json
import os
import sys
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from app.services.barkai_custom_context import build_custom_guidance


def test_build_custom_guidance_uses_question_bank_and_forbidden_patterns(tmp_path, monkeypatch):
    question_bank_path = tmp_path / "question_bank.json"
    forbidden_patterns_path = tmp_path / "forbidden_patterns.json"

    question_bank_path.write_text(
        json.dumps(
            {
                "records": [
                    {
                        "title": "How do I stop my puppy from biting hands and clothes all day?",
                        "topic_tags": ["puppy_care", "training_obedience"],
                        "score": 412,
                    },
                    {
                        "title": "My dog barks at every dog on walks. Where do I start with reactive behavior?",
                        "topic_tags": ["behavior_reactivity"],
                        "score": 365,
                    },
                ]
            }
        ),
        encoding="utf-8",
    )
    forbidden_patterns_path.write_text(
        json.dumps(
            {
                "patterns": [
                    {
                        "label": "Punishment-first training",
                        "instruction": "Do not recommend hitting or intimidation as dog training advice.",
                    }
                ]
            }
        ),
        encoding="utf-8",
    )

    monkeypatch.setenv("BARKAI_CUSTOM_REDDIT_QUESTION_BANK_FILE", str(question_bank_path))
    monkeypatch.setenv("BARKAI_CUSTOM_FORBIDDEN_PATTERNS_FILE", str(forbidden_patterns_path))

    guidance = build_custom_guidance(
        latest_user_message="My puppy keeps biting hands. What should I do?",
    )

    assert "question patterns to recognize" in guidance
    assert "stop my puppy from biting hands" in guidance
    assert "Never answer in ways that resemble these harmful" in guidance
    assert "Punishment-first training" in guidance
