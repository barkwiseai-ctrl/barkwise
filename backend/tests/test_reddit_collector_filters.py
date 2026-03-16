from collector.config import FilterConfig
from collector.filters import evaluate_title, extract_topic_tags


def test_extract_topic_tags_for_common_training_question():
    tags = extract_topic_tags("How do I stop my puppy from biting and leash pulling?")
    assert "training_obedience" in tags
    assert "puppy_care" in tags
    assert "behavior_reactivity" in tags


def test_extract_topic_tags_falls_back_for_general_question():
    tags = extract_topic_tags("What should I know before adopting my first dog?")
    assert tags


def test_evaluate_title_blocks_showcase_content():
    config = FilterConfig(
        enabled=True,
        require_question_signal=True,
        require_priority_keyword=False,
        exclude_title_keywords=["look at my dog"],
        priority_keywords=[],
    )
    should_skip, reason = evaluate_title("Look at my dog sleeping!", config)
    assert should_skip is True
    assert reason == "excluded_keyword"


def test_evaluate_title_accepts_core_question():
    config = FilterConfig(
        enabled=True,
        require_question_signal=True,
        require_priority_keyword=True,
        exclude_title_keywords=["look at my dog"],
        priority_keywords=["crate", "puppy"],
    )
    should_skip, reason = evaluate_title("How do I crate train a puppy quickly?", config)
    assert should_skip is False
    assert reason == "accepted"

