#!/usr/bin/env python3
import argparse
import json
import re
import sys
from collections import Counter
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional

TELEMETRY_PATTERN = re.compile(r"route_telemetry=(\{.*\})")


def _iter_lines(paths: List[str]) -> Iterable[str]:
    if not paths:
        for line in sys.stdin:
            yield line.rstrip("\n")
        return
    for path in paths:
        with open(path, "r", encoding="utf-8", errors="replace") as handle:
            for line in handle:
                yield line.rstrip("\n")


def _parse_payload(line: str) -> Optional[Dict[str, Any]]:
    match = TELEMETRY_PATTERN.search(line)
    if not match:
        return None
    raw = match.group(1)
    try:
        value = json.loads(raw)
    except Exception:
        return None
    if not isinstance(value, dict):
        return None
    return value


def _safe_int(value: Any) -> int:
    try:
        return int(value)
    except Exception:
        return 0


def build_report(rows: List[Dict[str, Any]]) -> Dict[str, Any]:
    lane_counts: Counter[str] = Counter()
    reason_counts: Counter[str] = Counter()
    intent_counts: Counter[str] = Counter()
    matched_terms: Counter[str] = Counter()

    rag_rows = 0
    rag_zero_docs = 0
    rag_doc_sum = 0
    trigger_true = 0

    for row in rows:
        lane = str(row.get("route_lane", "UNKNOWN"))
        reason = str(row.get("route_reason", "unknown"))
        intent = str(row.get("intent", "unknown"))
        lane_counts[lane] += 1
        reason_counts[reason] += 1
        intent_counts[intent] += 1

        if bool(row.get("rag_triggered", False)):
            trigger_true += 1
        for term in row.get("matched_terms", []) or []:
            matched_terms[str(term)] += 1

        if lane == "RAG":
            rag_rows += 1
            docs = _safe_int(row.get("rag_doc_count", 0))
            rag_doc_sum += docs
            if docs == 0:
                rag_zero_docs += 1

    total = len(rows)
    rag_zero_doc_rate = (rag_zero_docs / rag_rows) if rag_rows else 0.0
    trigger_rate = (trigger_true / total) if total else 0.0
    avg_rag_docs = (rag_doc_sum / rag_rows) if rag_rows else 0.0

    return {
        "total_messages": total,
        "lane_counts": dict(lane_counts),
        "reason_counts": dict(reason_counts),
        "intent_counts_top10": dict(intent_counts.most_common(10)),
        "matched_terms_top20": dict(matched_terms.most_common(20)),
        "rag": {
            "messages": rag_rows,
            "zero_doc_messages": rag_zero_docs,
            "zero_doc_rate": round(rag_zero_doc_rate, 4),
            "avg_doc_count": round(avg_rag_docs, 4),
        },
        "trigger_rate": round(trigger_rate, 4),
    }


def print_human(report: Dict[str, Any]) -> None:
    print(f"Total messages: {report['total_messages']}")
    print(f"Trigger rate: {report['trigger_rate']:.2%}")
    print("Lane counts:")
    for lane, count in sorted(report["lane_counts"].items(), key=lambda x: x[1], reverse=True):
        print(f"  - {lane}: {count}")
    rag = report["rag"]
    print(
        f"RAG stats: messages={rag['messages']} zero_doc={rag['zero_doc_messages']} "
        f"zero_doc_rate={rag['zero_doc_rate']:.2%} avg_docs={rag['avg_doc_count']:.2f}"
    )
    print("Top matched trigger terms:")
    for term, count in report["matched_terms_top20"].items():
        print(f"  - {term}: {count}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Summarize BarkAI route_telemetry logs.")
    parser.add_argument("log_files", nargs="*", help="Log files to parse. If omitted, read stdin.")
    parser.add_argument("--json-out", default="", help="Optional path to write JSON summary.")
    args = parser.parse_args()

    rows: List[Dict[str, Any]] = []
    for line in _iter_lines(args.log_files):
        payload = _parse_payload(line)
        if payload:
            rows.append(payload)

    report = build_report(rows)
    print_human(report)

    if args.json_out:
        path = Path(args.json_out)
        path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(f"Wrote report: {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
