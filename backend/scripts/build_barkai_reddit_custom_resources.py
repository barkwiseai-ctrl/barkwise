from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


BAD_ANSWER_HEURISTICS: dict[str, list[str]] = {
    "punishment_or_intimidation": [
        "alpha roll",
        "show him who's boss",
        "show her who's boss",
        "smack",
        "hit the dog",
        "rub his nose",
        "rub her nose",
        "dominance",
        "make him submit",
        "make her submit",
    ],
    "delay_urgent_care": [
        "just wait it out",
        "don't go to the vet",
        "save the vet bill",
        "give it a few days",
        "it will pass on its own",
    ],
    "harmful_crate_advice": [
        "leave him in the crate all day",
        "leave her in the crate all day",
        "crate him all day",
        "crate her all day",
        "just ignore the crying in the crate",
        "let him cry it out in the crate all night",
        "let her cry it out in the crate all night",
        "use the crate when he acts up",
        "use the crate when she acts up",
    ],
    "false_certainty": [
        "this always works",
        "guaranteed",
        "100%",
        "definitely nothing serious",
        "no need to worry at all",
    ],
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build BarkAI custom Reddit resources from collector JSONL output.")
    parser.add_argument("--input", required=True, help="Collector JSONL input path.")
    parser.add_argument("--question-bank-out", required=True, help="Output JSON path for Reddit question-bank records.")
    parser.add_argument(
        "--bad-answer-candidates-out",
        required=True,
        help="Output JSONL path for suspicious Reddit answer candidates that need human review.",
    )
    parser.add_argument("--max-question-records", type=int, default=250, help="Maximum number of question-bank items.")
    return parser.parse_args()


def _load_jsonl(path: Path) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            stripped = line.strip()
            if not stripped:
                continue
            payload = json.loads(stripped)
            if isinstance(payload, dict):
                records.append(payload)
    return records


def _normalize_title(title: str) -> str:
    return " ".join(title.strip().lower().split())


def _build_question_bank(records: list[dict[str, Any]], max_records: int) -> dict[str, Any]:
    deduped: dict[str, dict[str, Any]] = {}
    for record in records:
        title = str(record.get("title", "")).strip()
        if not title:
            continue
        key = _normalize_title(title)
        score = int(record.get("score", 0) or 0)
        existing = deduped.get(key)
        if existing is not None and int(existing.get("score", 0) or 0) >= score:
            continue
        deduped[key] = {
            "title": title,
            "topic_tags": record.get("high_level_topic_tags", []),
            "subreddit": record.get("subreddit"),
            "permalink": record.get("permalink"),
            "score": score,
        }

    ranked = sorted(deduped.values(), key=lambda item: int(item.get("score", 0) or 0), reverse=True)
    return {"records": ranked[: max(1, max_records)]}


def _bad_answer_reason(body: str) -> str | None:
    lower_body = body.lower()
    for reason, phrases in BAD_ANSWER_HEURISTICS.items():
        if any(phrase in lower_body for phrase in phrases):
            return reason
    return None


def _build_bad_answer_candidates(records: list[dict[str, Any]]) -> list[dict[str, Any]]:
    candidates: list[dict[str, Any]] = []
    for record in records:
        title = str(record.get("title", "")).strip()
        permalink = record.get("permalink")
        subreddit = record.get("subreddit")
        for answer in record.get("best_answers", []):
            if not isinstance(answer, dict):
                continue
            body = str(answer.get("body", "")).strip()
            if not body:
                continue
            reason = _bad_answer_reason(body)
            if reason is None:
                continue
            candidates.append(
                {
                    "reason": reason,
                    "subreddit": subreddit,
                    "question_title": title,
                    "question_permalink": permalink,
                    "answer_permalink": answer.get("permalink"),
                    "answer_score": int(answer.get("score", 0) or 0),
                    "answer_body": body,
                }
            )
    return candidates


def main() -> int:
    args = parse_args()
    input_path = Path(args.input)
    question_bank_out = Path(args.question_bank_out)
    bad_answer_candidates_out = Path(args.bad_answer_candidates_out)

    records = _load_jsonl(input_path)
    question_bank = _build_question_bank(records, args.max_question_records)
    bad_answer_candidates = _build_bad_answer_candidates(records)

    question_bank_out.parent.mkdir(parents=True, exist_ok=True)
    question_bank_out.write_text(json.dumps(question_bank, indent=2) + "\n", encoding="utf-8")

    bad_answer_candidates_out.parent.mkdir(parents=True, exist_ok=True)
    with bad_answer_candidates_out.open("w", encoding="utf-8") as handle:
        for candidate in bad_answer_candidates:
            handle.write(json.dumps(candidate, ensure_ascii=True) + "\n")

    summary = {
        "input_records": len(records),
        "question_bank_records": len(question_bank.get("records", [])),
        "bad_answer_candidates": len(bad_answer_candidates),
        "question_bank_out": str(question_bank_out),
        "bad_answer_candidates_out": str(bad_answer_candidates_out),
    }
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
