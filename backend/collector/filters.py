from __future__ import annotations

import re

from .config import FilterConfig

QUESTION_HINT_RE = re.compile(
    r"\b(how|what|why|when|where|who|which|can|should|is|are|do|does|did|help)\b", re.IGNORECASE
)

TOPIC_KEYWORDS: dict[str, list[str]] = {
    "health_vet": [
        "vet",
        "veterinarian",
        "sick",
        "ill",
        "vomit",
        "diarrhea",
        "poison",
        "tox",
        "injury",
        "parvo",
        "vaccine",
        "allergy",
        "itch",
        "flea",
        "tick",
        "ear infection",
    ],
    "training_obedience": [
        "train",
        "obedience",
        "recall",
        "command",
        "heel",
        "stay",
        "sit",
        "down",
        "leash",
        "pulling",
        "housebreak",
        "potty",
        "crate",
    ],
    "behavior_reactivity": [
        "reactive",
        "aggression",
        "aggressive",
        "growl",
        "bite",
        "biting",
        "bark",
        "lunging",
        "anxious",
        "anxiety",
        "fear",
        "separation",
        "resource guard",
    ],
    "puppy_care": [
        "puppy",
        "new dog",
        "first dog",
        "socialization",
        "teething",
        "schedule",
        "sleep",
    ],
    "nutrition_feeding": [
        "food",
        "diet",
        "feeding",
        "kibble",
        "treat",
        "weight",
        "overweight",
        "underweight",
    ],
    "grooming_hygiene": [
        "groom",
        "grooming",
        "bath",
        "nail",
        "shed",
        "brushing",
        "teeth",
        "dental",
    ],
    "socialization_multidog": [
        "dog park",
        "other dog",
        "introduc",
        "playdate",
        "social",
        "multi dog",
        "new puppy with dog",
    ],
    "rescue_adoption_foster": [
        "rescue",
        "adopt",
        "adoption",
        "foster",
        "shelter",
        "rehom",
    ],
    "safety_emergency": [
        "emergency",
        "urgent",
        "choking",
        "swallow",
        "heatstroke",
        "bloat",
        "seizure",
        "toxic",
    ],
    "lifestyle_exercise": [
        "walk",
        "exercise",
        "enrichment",
        "bored",
        "apartment",
        "travel",
        "car",
        "routine",
    ],
}


def normalize_text(value: str) -> str:
    return " ".join(value.strip().lower().split())


def extract_topic_tags(title: str, max_tags: int = 4) -> list[str]:
    normalized = normalize_text(title)
    if not normalized:
        return []
    tags: list[str] = []
    for tag, keywords in TOPIC_KEYWORDS.items():
        if any(keyword in normalized for keyword in keywords):
            tags.append(tag)
        if len(tags) >= max_tags:
            break
    if not tags and QUESTION_HINT_RE.search(title):
        return ["general_dog_question"]
    return tags


def evaluate_title(title: str, config: FilterConfig) -> tuple[bool, str]:
    normalized = normalize_text(title)
    if not normalized:
        return True, "empty_title"

    if not config.enabled:
        return False, "filter_disabled"

    if any(keyword.lower() in normalized for keyword in config.exclude_title_keywords):
        return True, "excluded_keyword"

    has_question_signal = bool(QUESTION_HINT_RE.search(normalized) or "?" in normalized)
    if config.require_question_signal and not has_question_signal:
        return True, "missing_question_signal"

    if config.require_priority_keyword and not any(
        keyword.lower() in normalized for keyword in config.priority_keywords
    ):
        return True, "missing_priority_keyword"

    return False, "accepted"
