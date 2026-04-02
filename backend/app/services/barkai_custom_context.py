from __future__ import annotations

import json
import os
import re
from pathlib import Path
from typing import Any

QUESTION_BANK_DEFAULT_PATH = Path(__file__).resolve().parents[1] / "resources" / "barkai_reddit_question_bank.json"
FORBIDDEN_PATTERNS_DEFAULT_PATH = (
    Path(__file__).resolve().parents[1] / "resources" / "barkai_forbidden_reply_patterns.json"
)

TOKEN_RE = re.compile(r"[a-z0-9']+")
STOPWORDS = {
    "a",
    "an",
    "and",
    "are",
    "be",
    "can",
    "do",
    "for",
    "from",
    "get",
    "help",
    "how",
    "i",
    "if",
    "in",
    "is",
    "it",
    "me",
    "my",
    "of",
    "on",
    "or",
    "should",
    "that",
    "the",
    "to",
    "what",
    "when",
    "where",
    "why",
    "with",
}


def _normalize_text(value: str) -> str:
    return " ".join(value.strip().lower().split())


def _tokenize(value: str) -> set[str]:
    return {token for token in TOKEN_RE.findall(_normalize_text(value)) if token not in STOPWORDS and len(token) > 1}


def _read_json_file(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}
    return payload if isinstance(payload, dict) else {}


def _question_bank_path() -> Path:
    configured = os.getenv("BARKAI_CUSTOM_REDDIT_QUESTION_BANK_FILE", "").strip().strip("'\"")
    return Path(configured) if configured else QUESTION_BANK_DEFAULT_PATH


def _forbidden_patterns_path() -> Path:
    configured = os.getenv("BARKAI_CUSTOM_FORBIDDEN_PATTERNS_FILE", "").strip().strip("'\"")
    return Path(configured) if configured else FORBIDDEN_PATTERNS_DEFAULT_PATH


def _load_question_bank() -> list[dict[str, Any]]:
    payload = _read_json_file(_question_bank_path())
    records = payload.get("records", [])
    return [record for record in records if isinstance(record, dict) and str(record.get("title", "")).strip()]


def _load_forbidden_patterns() -> list[dict[str, Any]]:
    payload = _read_json_file(_forbidden_patterns_path())
    patterns = payload.get("patterns", [])
    return [pattern for pattern in patterns if isinstance(pattern, dict) and str(pattern.get("instruction", "")).strip()]


def _match_question_examples(user_text: str, limit: int = 5) -> list[dict[str, Any]]:
    user_tokens = _tokenize(user_text)
    if not user_tokens:
        return []

    ranked: list[tuple[int, int, dict[str, Any]]] = []
    for record in _load_question_bank():
        title = str(record.get("title", "")).strip()
        title_tokens = _tokenize(title)
        overlap = len(user_tokens & title_tokens)
        if overlap <= 0:
            continue
        score = int(record.get("score", 0) or 0)
        ranked.append((overlap, score, record))

    ranked.sort(key=lambda item: (item[0], item[1]), reverse=True)
    return [record for _, _, record in ranked[:limit]]


def build_custom_guidance(*, latest_user_message: str) -> str:
    normalized_message = latest_user_message.strip()
    if not normalized_message:
        return ""

    sections: list[str] = []
    matched_questions = _match_question_examples(normalized_message)
    if matched_questions:
        lines = [
            "Reddit dog-forum question patterns to recognize as common user concerns. Use these only for intent recognition and missing-context spotting, not as authority or citations:",
        ]
        for item in matched_questions:
            title = str(item.get("title", "")).strip()
            tags = item.get("topic_tags", [])
            tag_text = ", ".join(str(tag).strip() for tag in tags if str(tag).strip())
            if tag_text:
                lines.append(f'- [{tag_text}] "{title}"')
            else:
                lines.append(f'- "{title}"')
        sections.append(" ".join(lines))

    forbidden_patterns = _load_forbidden_patterns()
    if forbidden_patterns:
        lines = [
            "Never answer in ways that resemble these harmful or low-quality dog-forum reply patterns:",
        ]
        for pattern in forbidden_patterns[:8]:
            label = str(pattern.get("label", "")).strip()
            instruction = str(pattern.get("instruction", "")).strip()
            if label:
                lines.append(f"- {label}: {instruction}")
            else:
                lines.append(f"- {instruction}")
        sections.append(" ".join(lines))

    return " ".join(section for section in sections if section)
