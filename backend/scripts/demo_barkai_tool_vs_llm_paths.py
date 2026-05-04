#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
import time
from dataclasses import dataclass
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


@dataclass(frozen=True)
class DemoStep:
    title: str
    prompt: str
    expected_path: str
    narration: str
    use_previous_confirm_cta: bool = False


DEMO_STEPS: tuple[DemoStep, ...] = (
    DemoStep(
        title="LLM path: dog advice",
        prompt="I adopted a rescue dog yesterday and he is hiding. How should I help him settle?",
        expected_path="assistant",
        narration="This should route to the structured LLM answer path with welfare-first guidance.",
    ),
    DemoStep(
        title="Tool-read path: community discovery",
        prompt="I'm new to Sunshine West. Can you find any dog park groups nearby?",
        expected_path="tool_group_search",
        narration="This should bypass freeform chat and read BarkWise community data.",
    ),
    DemoStep(
        title="Tool-action path: confirmation required",
        prompt="Turn on provider mode for me",
        expected_path="tool_confirmation",
        narration="This should stop at confirmation and show a Confirm <code> CTA.",
    ),
    DemoStep(
        title="Tool-action hardening: generic yes rejected",
        prompt="Yes, confirm",
        expected_path="not tool_provider_mode",
        narration="This should not execute, because tokenized confirmations require the code-bearing CTA.",
    ),
    DemoStep(
        title="Tool-action path: explicit code confirmation",
        prompt="",
        expected_path="tool_provider_mode or tool_action_disabled",
        narration="This replays the actual confirmation CTA payload, demonstrating the safe execution path.",
        use_previous_confirm_cta=True,
    ),
)


def normalize_base_url(base_url: str) -> str:
    cleaned = base_url.strip().rstrip("/")
    if not cleaned:
        raise ValueError("base URL is required")
    return cleaned


def post_chat(
    *,
    base_url: str,
    prompt: str,
    user_id: str,
    suburb: str,
    authorization: str | None,
    timeout_seconds: int,
) -> dict[str, Any]:
    payload = {
        "user_id": user_id,
        "suburb": suburb,
        "message": prompt,
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
    with urlopen(request, timeout=timeout_seconds) as response:
        return json.loads(response.read().decode("utf-8"))


def confirmation_payload(response: dict[str, Any]) -> str | None:
    for chip in response.get("cta_chips", []):
        if not isinstance(chip, dict) or chip.get("action") != "send_bark_message":
            continue
        payload = chip.get("payload")
        if not isinstance(payload, dict):
            continue
        message = str(payload.get("message") or "")
        if message.lower().startswith("confirm "):
            return message
    return None


def print_script_only() -> None:
    print("BarkAI Tool Path vs LLM Path UI Demo")
    print("Use the same test user for every step so the pending confirmation state carries across.")
    for index, step in enumerate(DEMO_STEPS, start=1):
        prompt = "<tap the previous Confirm code CTA>" if step.use_previous_confirm_cta else step.prompt
        print(f"\n{index}. {step.title}")
        print(f"   UI input: {prompt}")
        print(f"   Expected path: {step.expected_path}")
        print(f"   What to point out: {step.narration}")


def run_demo(*, base_url: str, user_id: str, suburb: str, authorization: str | None, timeout_seconds: int) -> int:
    print(f"BarkAI path demo against {base_url}")
    print(f"user_id={user_id} suburb={suburb}")
    last_confirm_payload: str | None = None
    failures: list[str] = []

    for index, step in enumerate(DEMO_STEPS, start=1):
        prompt = last_confirm_payload if step.use_previous_confirm_cta else step.prompt
        if not prompt:
            failures.append(f"{step.title}: no confirmation CTA payload was available")
            continue
        print(f"\n{index}. {step.title}")
        print(f"   input: {prompt}")
        print(f"   expectation: {step.expected_path}")
        print(f"   note: {step.narration}")

        try:
            response = post_chat(
                base_url=base_url,
                prompt=prompt,
                user_id=user_id,
                suburb=suburb,
                authorization=authorization,
                timeout_seconds=timeout_seconds,
            )
        except HTTPError as error:
            body = error.read().decode("utf-8", errors="replace")
            failures.append(f"{step.title}: HTTP {error.code}: {body}")
            print(f"   result: HTTP {error.code}")
            continue
        except (URLError, TimeoutError, OSError, json.JSONDecodeError) as error:
            failures.append(f"{step.title}: {type(error).__name__}: {error}")
            print(f"   result: {type(error).__name__}: {error}")
            continue

        answer_source = str(response.get("answer_source") or "")
        status = str(response.get("status") or "")
        answer = str(response.get("answer") or "")
        print(f"   status={status} answer_source={answer_source}")
        print(f"   answer: {answer[:280]}")

        if answer_source == "tool_confirmation":
            last_confirm_payload = confirmation_payload(response)
            if last_confirm_payload:
                print(f"   captured CTA payload: {last_confirm_payload}")

        if step.expected_path == "not tool_provider_mode" and answer_source == "tool_provider_mode":
            failures.append(f"{step.title}: generic confirmation executed a tool action")
        elif step.expected_path not in {"not tool_provider_mode", "tool_provider_mode or tool_action_disabled"}:
            if answer_source != step.expected_path:
                failures.append(f"{step.title}: expected {step.expected_path}, got {answer_source}")
        elif step.expected_path == "tool_provider_mode or tool_action_disabled":
            if answer_source not in {"tool_provider_mode", "tool_action_disabled"}:
                failures.append(f"{step.title}: expected execution or disabled-action result, got {answer_source}")

    if failures:
        print("\nDemo completed with mismatches:")
        for failure in failures:
            print(f"- {failure}")
        return 1
    print("\nDemo completed successfully.")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Demonstrate BarkAI LLM, tool-read, and tool-action paths end to end.")
    parser.add_argument(
        "--base-url",
        default="http://localhost:8000",
        help="API base URL, e.g. http://localhost:8000 or https://api.barkwiseai.com",
    )
    parser.add_argument("--user-id", default=f"barkai_demo_{int(time.time())}", help="Stable test user ID for this demo run.")
    parser.add_argument("--suburb", default="Sunshine West", help="Suburb sent with each chat request.")
    parser.add_argument("--authorization", default=None, help="Optional Authorization header value.")
    parser.add_argument("--timeout-seconds", type=int, default=60)
    parser.add_argument("--script-only", action="store_true", help="Print the UI demo script without calling the API.")
    args = parser.parse_args()

    if args.script_only:
        print_script_only()
        return 0
    return run_demo(
        base_url=normalize_base_url(args.base_url),
        user_id=args.user_id,
        suburb=args.suburb,
        authorization=args.authorization,
        timeout_seconds=args.timeout_seconds,
    )


if __name__ == "__main__":
    sys.exit(main())
