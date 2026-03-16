from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

try:
    import yaml
except Exception:  # pragma: no cover - optional dependency at import time
    yaml = None


def _default_subreddits() -> list[str]:
    return [
        "dogs",
        "DogTalk",
        "AllThingsDogs",
        "HappyDogHappyLife",
        "DogTraining",
        "puppy101",
        "reactivedogs",
        "CanineBehavior",
        "OpenDogTraining",
        "dogtrainingtips",
        "AskVet",
        "DogAdvice",
        "PetHealth",
        "muzzledogs",
        "dogquestions",
        "dogadvice",
        "firsttimehomebuyer",
        "fosterdogs",
        "RescueDogs",
    ]


def _default_exclude_title_keywords() -> list[str]:
    return [
        "look at my dog",
        "my dog is cute",
        "cute dog",
        "dog photo",
        "dog pics",
        "rate my dog",
        "name my dog",
        "meet my dog",
        "dog meme",
        "just sharing",
        "appreciation post",
        "showing off",
        "my pupper",
    ]


def _default_priority_keywords() -> list[str]:
    return [
        "puppy",
        "train",
        "behavior",
        "reactive",
        "aggression",
        "anxious",
        "separation",
        "crate",
        "potty",
        "housebreak",
        "vaccine",
        "vet",
        "vomit",
        "diarrhea",
        "itch",
        "allergy",
        "food",
        "diet",
        "feeding",
        "bark",
        "leash",
        "recall",
        "bite",
        "growl",
        "social",
        "dog park",
        "adopt",
        "rescue",
        "foster",
        "senior",
        "groom",
        "nail",
        "sleep",
        "exercise",
        "walk",
        "muzzle",
    ]


@dataclass
class SamplingPlan:
    n_year: int = 100
    n_month: int = 100
    n_week: int = 100
    n_new: int = 100
    n_best: int = 100
    n_hot: int = 100
    n_rising: int = 100
    new_within_days: int = 30
    scan_multiplier: int = 4


@dataclass
class FilterConfig:
    enabled: bool = True
    require_question_signal: bool = True
    require_priority_keyword: bool = False
    exclude_title_keywords: list[str] = field(default_factory=_default_exclude_title_keywords)
    priority_keywords: list[str] = field(default_factory=_default_priority_keywords)


@dataclass
class CommentConfig:
    max_top_level_answers: int = 2
    min_length_chars: int = 80
    skip_removed_or_moderation: bool = True


@dataclass
class RateLimitConfig:
    listing_item_sleep_seconds: float = 0.25
    comment_fetch_sleep_seconds: float = 0.15
    subreddit_sleep_seconds: float = 1.0
    error_backoff_seconds: float = 2.0


@dataclass
class CollectorConfig:
    subreddits: list[str] = field(default_factory=_default_subreddits)
    sampling: SamplingPlan = field(default_factory=SamplingPlan)
    filters: FilterConfig = field(default_factory=FilterConfig)
    comments: CommentConfig = field(default_factory=CommentConfig)
    rate_limit: RateLimitConfig = field(default_factory=RateLimitConfig)

    @staticmethod
    def from_dict(raw: dict[str, Any]) -> "CollectorConfig":
        subreddits = list(raw.get("subreddits", _default_subreddits()))
        sampling = SamplingPlan(**raw.get("sampling", {}))
        filters = FilterConfig(**raw.get("filters", {}))
        comments = CommentConfig(**raw.get("comments", {}))
        rate_limit = RateLimitConfig(**raw.get("rate_limit", {}))
        return CollectorConfig(
            subreddits=subreddits,
            sampling=sampling,
            filters=filters,
            comments=comments,
            rate_limit=rate_limit,
        )


def load_config(path: Path) -> CollectorConfig:
    payload = _load_config_payload(path)
    if not isinstance(payload, dict):
        raise ValueError(f"Config must decode to an object: {path}")
    return CollectorConfig.from_dict(payload)


def _load_config_payload(path: Path) -> dict[str, Any]:
    suffix = path.suffix.lower()
    raw = path.read_text(encoding="utf-8")
    if suffix in {".json", ".jsonc"}:
        return json.loads(raw)
    if suffix in {".yaml", ".yml"}:
        if yaml is None:
            raise RuntimeError("PyYAML is required for YAML configs. Install with `pip install PyYAML`.")
        loaded = yaml.safe_load(raw)
        return loaded if isinstance(loaded, dict) else {}
    raise ValueError(f"Unsupported config extension '{path.suffix}'. Use .json, .yaml, or .yml.")

