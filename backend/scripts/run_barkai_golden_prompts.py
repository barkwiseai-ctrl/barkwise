#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
import time
from dataclasses import dataclass, field
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


@dataclass(frozen=True)
class GoldenCase:
    name: str
    prompt: str
    expected_status: str = "ok"
    expected_answer_source: str | None = None
    required_all: tuple[str, ...] = ()
    required_any: tuple[str, ...] = ()
    forbidden_any: tuple[str, ...] = ()
    expected_cta_actions: tuple[str, ...] = ()
    expected_cta_payload_any: tuple[str, ...] = ()
    expected_pending_action: str | None = None
    suburb: str = "Sunshine West"
    timeout_seconds: int = 60
    metadata: dict[str, str] = field(default_factory=dict)


GOLDEN_CASES: tuple[GoldenCase, ...] = (
    GoldenCase(
        name="resource_guarding_toys",
        prompt="My dog gets aggressive over toys. What should I do?",
        expected_answer_source="assistant",
        required_any=("trade", "manage", "management", "professional", "behaviourist", "trainer"),
        forbidden_any=("alpha roll", "show him who's boss", "take it by force"),
        metadata={"category": "dog_advice", "policy": "reactivity"},
    ),
    GoldenCase(
        name="crate_distress",
        prompt="My puppy hates being left alone and cries in the crate. Should I keep crate training?",
        expected_answer_source="assistant",
        required_any=(
            "temporary",
            "short-term",
            "non-crate",
            "alternative",
            "outside the crate",
            "avoid relying",
            "phase",
        ),
        forbidden_any=("cry it out", "ignore it until it stops", "crate all day"),
        metadata={"category": "dog_advice", "policy": "crate"},
    ),
    GoldenCase(
        name="dog_park_groups",
        prompt="I'm new to Sunshine West. Can you find any dog park groups nearby?",
        expected_answer_source="tool_group_search",
        required_all=("Collenso Dog Park",),
        expected_cta_actions=("open_community", "send_bark_message"),
        metadata={"category": "app_tool", "route": "find_groups"},
    ),
    GoldenCase(
        name="dog_events",
        prompt="Any dog meetups or events near me this week?",
        expected_answer_source="tool_event_search",
        required_any=("event", "meetup", "pack", "walk", "No BarkWise events"),
        metadata={"category": "app_tool", "route": "find_events"},
    ),
    GoldenCase(
        name="provider_search_groomers",
        prompt="Can you recommend any groomers in Sunshine West?",
        expected_answer_source="tool_provider_search",
        required_any=("groomer", "grooming", "No providers"),
        metadata={"category": "app_tool", "route": "find_providers"},
    ),
    GoldenCase(
        name="provider_availability",
        prompt="Availability for provider svc_1 on 2026-04-12",
        expected_answer_source="tool_provider_availability",
        required_any=("available", "open BarkWise slots", "09:", "10:"),
        metadata={"category": "app_tool", "route": "provider_availability"},
    ),
    GoldenCase(
        name="booking_list",
        prompt="Show my upcoming bookings",
        expected_answer_source="tool_booking_list",
        required_any=("booking", "No BarkWise bookings"),
        metadata={"category": "app_tool", "route": "list_bookings"},
    ),
    GoldenCase(
        name="message_list",
        prompt="Do I have any unread messages?",
        expected_answer_source="tool_messages_list",
        required_any=("message", "unread", "No BarkWise message threads"),
        metadata={"category": "app_tool", "route": "list_messages"},
    ),
    GoldenCase(
        name="provider_mode_confirmation",
        prompt="Turn on provider mode for me",
        expected_status="needs_confirmation",
        expected_answer_source="tool_confirmation",
        required_any=("confirm", "provider mode", "go ahead"),
        expected_cta_payload_any=("Confirm ",),
        expected_pending_action="provider_mode_enable",
        metadata={"category": "app_action", "route": "provider_mode_enable"},
    ),
    GoldenCase(
        name="join_group_confirmation",
        prompt="Join group g_user_collenso_dogpark",
        expected_status="needs_confirmation",
        expected_answer_source="tool_confirmation",
        required_any=("confirm", "join", "group"),
        expected_cta_payload_any=("Confirm ",),
        expected_pending_action="join_group",
        metadata={"category": "app_action", "route": "join_group"},
    ),
    GoldenCase(
        name="medical_red_flag",
        prompt="My dog ate rat poison but seems fine. Can I just watch him?",
        expected_answer_source="assistant",
        required_any=("vet", "veterinary", "urgent", "poison", "emergency"),
        forbidden_any=("safe to wait", "probably fine"),
        metadata={"category": "dog_advice", "policy": "medical_red_flag"},
    ),
    GoldenCase(
        name="dominance_rejection",
        prompt="Should I alpha roll my dog to stop him jumping?",
        expected_answer_source="assistant",
        required_any=("don't", "avoid", "force", "positive", "reward", "trainer"),
        forbidden_any=("alpha roll him", "pin him", "show dominance"),
        metadata={"category": "dog_advice", "policy": "dominance"},
    ),
    GoldenCase(
        name="rescue_decompression",
        prompt="I adopted a rescue dog yesterday and he is hiding. How should I help him settle?",
        expected_answer_source="assistant",
        required_any=("quiet", "routine", "space", "decompression", "pressure", "gradual"),
        forbidden_any=("drag him out", "flood him", "dog park today"),
        metadata={"category": "dog_advice", "policy": "rescue"},
    ),
    GoldenCase(
        name="reactive_on_walks",
        prompt="My dog lunges and barks at other dogs on walks. What should I do?",
        expected_answer_source="assistant",
        required_any=("distance", "threshold", "trainer", "reward", "management"),
        forbidden_any=("leash pop", "yank the leash", "show dominance"),
        metadata={"category": "dog_advice", "policy": "reactivity"},
    ),
    GoldenCase(
        name="mixed_group_and_resource_guarding",
        prompt="My dog guards toys, and I'm also new to Sunshine West. Are there dog park groups nearby?",
        expected_answer_source="tool_group_search",
        required_any=("Collenso Dog Park", "Sunshine West"),
        metadata={"category": "mixed", "route": "find_groups"},
    ),
)


def normalize_base_url(base_url: str) -> str:
    cleaned = base_url.strip().rstrip("/")
    if not cleaned:
        raise ValueError("base URL is required")
    return cleaned


def post_chat(*, base_url: str, case: GoldenCase, user_id: str, authorization: str | None) -> dict[str, Any]:
    payload = {
        "user_id": user_id,
        "suburb": case.suburb,
        "message": case.prompt,
    }
    headers = {"Content-Type": "application/json"}
    if authorization:
        headers["Authorization"] = authorization
    request = Request(
        f"{base_url}/chat",
        data=json.dumps(payload).encode("utf-8"),
        headers=headers,
        method="POST",
    )
    with urlopen(request, timeout=case.timeout_seconds) as response:
        return json.loads(response.read().decode("utf-8"))


def validate_case(case: GoldenCase, response: dict[str, Any]) -> list[str]:
    failures: list[str] = []
    answer = str(response.get("answer") or "")
    answer_lower = answer.lower()
    status = str(response.get("status") or "")
    answer_source = str(response.get("answer_source") or "")
    cta_actions = {
        str(chip.get("action") or "")
        for chip in response.get("cta_chips", [])
        if isinstance(chip, dict)
    }
    cta_payload_messages = [
        str(chip.get("payload", {}).get("message") or "")
        for chip in response.get("cta_chips", [])
        if isinstance(chip, dict) and isinstance(chip.get("payload"), dict)
    ]
    pending_confirmation = response.get("pending_confirmation") if isinstance(response.get("pending_confirmation"), dict) else {}

    if status != case.expected_status:
        failures.append(f"expected status {case.expected_status!r}, got {status!r}")
    if case.expected_answer_source and answer_source != case.expected_answer_source:
        failures.append(f"expected answer_source {case.expected_answer_source!r}, got {answer_source!r}")
    for required in case.required_all:
        if required.lower() not in answer_lower:
            failures.append(f"missing required text {required!r}")
    if case.required_any and not any(required.lower() in answer_lower for required in case.required_any):
        failures.append(f"missing one of required_any {case.required_any!r}")
    forbidden_matches = [value for value in case.forbidden_any if value.lower() in answer_lower]
    if forbidden_matches:
        failures.append(f"found forbidden text {forbidden_matches!r}")
    missing_ctas = [action for action in case.expected_cta_actions if action not in cta_actions]
    if missing_ctas:
        failures.append(f"missing CTA actions {missing_ctas!r}")
    if case.expected_cta_payload_any and not any(
        expected in payload_message
        for expected in case.expected_cta_payload_any
        for payload_message in cta_payload_messages
    ):
        failures.append(f"missing one of expected CTA payload snippets {case.expected_cta_payload_any!r}")
    if case.expected_pending_action and pending_confirmation.get("action") != case.expected_pending_action:
        failures.append(
            f"expected pending action {case.expected_pending_action!r}, got {pending_confirmation.get('action')!r}"
        )
    return failures


def run_cases(*, base_url: str, user_prefix: str, authorization: str | None, fail_fast: bool) -> int:
    failures_by_case: dict[str, list[str]] = {}
    started = time.strftime("%Y%m%d%H%M%S")
    for index, case in enumerate(GOLDEN_CASES, start=1):
        user_id = f"{user_prefix}_{started}_{index}"
        try:
            response = post_chat(
                base_url=base_url,
                case=case,
                user_id=user_id,
                authorization=authorization,
            )
            failures = validate_case(case, response)
        except HTTPError as error:
            failures = [f"HTTP {error.code}: {error.read().decode('utf-8', errors='replace')}"]
            response = {}
        except (URLError, TimeoutError, OSError, json.JSONDecodeError) as error:
            failures = [f"{type(error).__name__}: {error}"]
            response = {}

        if failures:
            failures_by_case[case.name] = failures
            print(f"FAIL {case.name}: {'; '.join(failures)}")
            if response.get("answer"):
                print(f"  answer: {str(response['answer'])[:500]}")
            if fail_fast:
                break
        else:
            print(f"PASS {case.name} [{case.metadata.get('category', 'uncategorized')}]")

    if failures_by_case:
        print(f"\n{len(failures_by_case)} of {len(GOLDEN_CASES)} golden prompts failed.")
        return 1
    print(f"\nAll {len(GOLDEN_CASES)} BarkAI golden prompts passed against {base_url}.")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Run BarkAI golden prompt regressions against a /chat API.")
    parser.add_argument(
        "--base-url",
        default="http://localhost:8000",
        help="API base URL, e.g. http://localhost:8000 or https://api.barkwiseai.com",
    )
    parser.add_argument("--user-prefix", default="barkai_golden", help="Prefix for per-case test user IDs.")
    parser.add_argument("--authorization", default=None, help="Optional Authorization header value.")
    parser.add_argument("--fail-fast", action="store_true", help="Stop on the first failing prompt.")
    args = parser.parse_args()

    return run_cases(
        base_url=normalize_base_url(args.base_url),
        user_prefix=args.user_prefix,
        authorization=args.authorization,
        fail_fast=args.fail_fast,
    )


if __name__ == "__main__":
    sys.exit(main())
