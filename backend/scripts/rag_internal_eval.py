#!/usr/bin/env python3
import argparse
import json
import os
import sys
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any, Dict, List, Optional

REPO_BACKEND = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(REPO_BACKEND))

from app.services.ai_orchestrator import AIOrchestrator  # noqa: E402


@dataclass
class EvalCase:
    name: str
    message: str
    suburb: Optional[str]
    expected_lane: str
    expected_sources: List[str]
    expected_answer_terms: List[str]


@dataclass
class EvalResult:
    name: str
    lane: str
    lane_ok: bool
    sources: List[str]
    source_ok: bool
    answer_term_hits: List[str]
    answer_ok: bool
    rag_doc_count: int
    score: float


CASES: List[EvalCase] = [
    EvalCase(
        name="Parvo triage",
        message="Could this be parvo? puppy is vomiting",
        suburb="Surry Hills",
        expected_lane="RAG",
        expected_sources=["knowledge_base"],
        expected_answer_terms=["vet", "urgent", "parvo"],
    ),
    EvalCase(
        name="Vaccine guidance",
        message="What vaccines should my puppy get?",
        suburb="Newtown",
        expected_lane="RAG",
        expected_sources=["knowledge_base"],
        expected_answer_terms=["vaccine"],
    ),
    EvalCase(
        name="Poison concern",
        message="Possible poison exposure, what should I do right now?",
        suburb="Redfern",
        expected_lane="RAG",
        expected_sources=["knowledge_base"],
        expected_answer_terms=["poison", "vet"],
    ),
    EvalCase(
        name="Local dog walker",
        message="Find dog walkers near me",
        suburb="Surry Hills",
        expected_lane="APP",
        expected_sources=["provider"],
        expected_answer_terms=["services", "booking"],
    ),
    EvalCase(
        name="Community events",
        message="Any pet community events this week?",
        suburb="Newtown",
        expected_lane="APP",
        expected_sources=["community_event", "group", "community_post"],
        expected_answer_terms=["community", "group"],
    ),
]


def _to_lower_list(values: List[str]) -> List[str]:
    return [value.lower() for value in values]


def _sources_from_tools(tool_results: Dict[str, Any]) -> List[str]:
    inferred: List[str] = []
    if isinstance(tool_results.get("search_services"), list):
        inferred.append("provider")
    if isinstance(tool_results.get("search_groups"), list):
        inferred.append("group")
    if isinstance(tool_results.get("draft_lost_found"), dict):
        inferred.append("community_post")
    if isinstance(tool_results.get("create_user_group"), dict):
        inferred.append("group")
    if isinstance(tool_results.get("add_group_member"), dict):
        inferred.append("group")
    return inferred


def run_case(orchestrator: AIOrchestrator, case: EvalCase, user_id: str) -> EvalResult:
    plan = orchestrator._build_plan(message=case.message, suburb=case.suburb, session=orchestrator._get_session(user_id))
    route = orchestrator._route_query(message=case.message, plan=plan)
    tool_results = orchestrator._execute_tools(
        tool_calls=plan.get("tools", []),
        message=case.message,
        suburb=case.suburb,
        session=orchestrator._get_session(user_id),
        user_id=user_id,
    )
    if route["lane"] == "RAG":
        rag_context = orchestrator.rag_retriever.build_context(
            message=case.message,
            suburb=case.suburb,
            profile_memory={},
            intent=str(plan.get("intent", "general_pet_question")),
            tool_results=tool_results,
        )
    else:
        rag_context = {"documents": []}

    response = orchestrator.handle_message(message=case.message, user_id=user_id, suburb=case.suburb)
    answer = (response.answer or "").lower()
    if route["lane"] == "RAG":
        sources = [str(doc.get("source", "")) for doc in rag_context.get("documents", []) if isinstance(doc, dict)]
    else:
        sources = _sources_from_tools(tool_results)
    lower_sources = _to_lower_list(sources)

    lane_ok = route.get("lane", "") == case.expected_lane
    source_ok = any(source in lower_sources for source in _to_lower_list(case.expected_sources))
    answer_term_hits = [term for term in case.expected_answer_terms if term.lower() in answer]
    answer_ok = len(answer_term_hits) > 0

    checks = [lane_ok, source_ok, answer_ok]
    score = sum(1 for check in checks if check) / len(checks)
    return EvalResult(
        name=case.name,
        lane=str(route.get("lane", "")),
        lane_ok=lane_ok,
        sources=sources,
        source_ok=source_ok,
        answer_term_hits=answer_term_hits,
        answer_ok=answer_ok,
        rag_doc_count=len(rag_context.get("documents", [])),
        score=score,
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="Internal RAG quality eval for BarkAI.")
    parser.add_argument("--allow-fallback", action="store_true", help="Allow running when OpenAI client is unavailable.")
    parser.add_argument("--min-pass-rate", type=float, default=0.7, help="Minimum aggregate score required.")
    parser.add_argument("--json-out", type=str, default="", help="Optional output path for machine-readable results.")
    args = parser.parse_args()

    orchestrator = AIOrchestrator()
    if not orchestrator.llm_available and not args.allow_fallback:
        print("LLM unavailable (OPENAI_API_KEY missing). Re-run with --allow-fallback for non-LLM smoke eval.")
        return 2

    results: List[EvalResult] = []
    for index, case in enumerate(CASES):
        user_id = f"rag_eval_user_{index + 1}"
        result = run_case(orchestrator=orchestrator, case=case, user_id=user_id)
        results.append(result)

    aggregate = sum(result.score for result in results) / max(1, len(results))
    llm_mode = "openai" if orchestrator.llm_available else "fallback"
    print(f"RAG internal eval mode={llm_mode} cases={len(results)} aggregate_score={aggregate:.2f}")
    for result in results:
        print(
            f"- {result.name}: lane={result.lane} lane_ok={result.lane_ok} "
            f"source_ok={result.source_ok} answer_ok={result.answer_ok} score={result.score:.2f}"
        )

    if args.json_out:
        payload: Dict[str, Any] = {
            "mode": llm_mode,
            "aggregate_score": aggregate,
            "cases": [asdict(item) for item in results],
        }
        output_path = Path(args.json_out)
        output_path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
        print(f"Wrote report: {output_path}")

    if aggregate < args.min_pass_rate:
        print(f"Aggregate score below threshold ({aggregate:.2f} < {args.min_pass_rate:.2f})")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
