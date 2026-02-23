#!/usr/bin/env python3
import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from security_rate_limits_common import (
    DEFAULT_BASE_URL,
    DEFAULT_REQUESTER_USER_ID,
    DEFAULT_SNAPSHOTS_DIR,
    DEFAULT_TIMEOUT_SECONDS,
    fetch_rate_limits_snapshot,
    parse_positive_int,
    print_json,
)


def _parse_surface_limit(item: str) -> tuple[str, int]:
    parts = item.split("=", 1)
    if len(parts) != 2:
        raise ValueError(f"Invalid --surface-limit '{item}'. Expected surface=limit.")
    surface = parts[0].strip().lower()
    if not surface:
        raise ValueError(f"Invalid --surface-limit '{item}'. Missing surface.")
    try:
        limit = int(parts[1].strip())
    except ValueError as exc:
        raise ValueError(f"Invalid --surface-limit '{item}'. Limit must be integer.") from exc
    if limit < 0:
        raise ValueError(f"Invalid --surface-limit '{item}'. Limit must be >= 0.")
    return surface, limit


def _now_iso_utc() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def _parse_iso_utc(value: str) -> datetime | None:
    if not value:
        return None
    try:
        normalized = value.replace("Z", "+00:00")
        parsed = datetime.fromisoformat(normalized)
    except ValueError:
        return None
    if parsed.tzinfo is None:
        return parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def _load_alert_state(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return {}
    return payload if isinstance(payload, dict) else {}


def _save_alert_state(path: Path, payload: dict[str, Any]) -> None:
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(payload, separators=(",", ":")), encoding="utf-8")
    except Exception:
        return


def _build_alert_payload(
    *,
    result: dict[str, Any],
    base_url: str,
    requester_user_id: str,
    environment: str,
) -> dict[str, Any]:
    summary = f"[{environment}] security rate-limit alert: {len(result.get('violations', []))} violation(s)"
    return {
        "text": summary,
        "kind": "security_rate_limit_alert",
        "environment": environment,
        "base_url": base_url.rstrip("/"),
        "requester_user_id": requester_user_id,
        "occurred_at": _now_iso_utc(),
        "result": result,
    }


def _render_webhook_payload(*, payload: dict[str, Any], webhook_kind: str) -> dict[str, Any]:
    kind = webhook_kind.strip().lower()
    if kind == "discord":
        return {
            "content": payload.get("text", "Security rate-limit alert"),
            "embeds": [
                {
                    "title": "Security Rate-Limit Alert",
                    "description": payload.get("text", ""),
                    "fields": [
                        {"name": "Environment", "value": str(payload.get("environment", "unknown")), "inline": True},
                        {"name": "Base URL", "value": str(payload.get("base_url", "")), "inline": True},
                        {
                            "name": "Violations",
                            "value": str(len(payload.get("result", {}).get("violations", []))),
                            "inline": True,
                        },
                    ],
                }
            ],
        }
    if kind == "slack":
        # Slack incoming webhooks accept plain "text" and optional "blocks".
        return {
            "text": payload.get("text", "Security rate-limit alert"),
            "blocks": [
                {
                    "type": "section",
                    "text": {"type": "mrkdwn", "text": f"*{payload.get('text', 'Security rate-limit alert')}*"},
                },
                {
                    "type": "context",
                    "elements": [
                        {
                            "type": "mrkdwn",
                            "text": (
                                f"env={payload.get('environment', 'unknown')} "
                                f"base={payload.get('base_url', '')} "
                                f"violations={len(payload.get('result', {}).get('violations', []))}"
                            ),
                        }
                    ],
                },
            ],
            "metadata": {
                "event_type": "security_rate_limit_alert",
                "event_payload": payload,
            },
        }
    # generic mode sends the full structured payload unchanged.
    return payload


def _post_webhook_json(*, webhook_url: str, payload: dict[str, Any], timeout_seconds: float) -> tuple[bool, str | None]:
    body = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url=webhook_url,
        method="POST",
        data=body,
        headers={"Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout_seconds) as response:
            if 200 <= response.status < 300:
                return True, None
            return False, f"webhook_http_status:{response.status}"
    except urllib.error.HTTPError as exc:
        return False, f"webhook_http_error:{exc.code}"
    except Exception as exc:
        return False, f"webhook_failed:{type(exc).__name__}:{exc}"


def _maybe_send_alert(
    *,
    result: dict[str, Any],
    webhook_url: str | None,
    webhook_kind: str,
    webhook_timeout_seconds: float,
    dedupe_seconds: int,
    alert_state_path: Path,
    base_url: str,
    requester_user_id: str,
    environment: str,
) -> dict[str, Any]:
    info = {
        "configured": bool(webhook_url),
        "webhook_kind": webhook_kind.strip().lower(),
        "attempted": False,
        "sent": False,
        "skipped_reason": None,
        "error": None,
    }
    if not webhook_url:
        info["skipped_reason"] = "webhook_not_configured"
        return info

    signature = json.dumps(result.get("violations", []), sort_keys=True, separators=(",", ":"))
    now = datetime.now(timezone.utc)
    state = _load_alert_state(alert_state_path)
    last_signature = state.get("last_signature")
    last_alert_at = _parse_iso_utc(str(state.get("last_alert_at") or ""))
    if last_signature == signature and last_alert_at is not None:
        elapsed = (now - last_alert_at).total_seconds()
        if elapsed < dedupe_seconds:
            info["skipped_reason"] = f"deduped_within_{dedupe_seconds}s"
            return info

    info["attempted"] = True
    payload = _build_alert_payload(
        result=result,
        base_url=base_url,
        requester_user_id=requester_user_id,
        environment=environment,
    )
    rendered_payload = _render_webhook_payload(payload=payload, webhook_kind=webhook_kind)
    ok, error = _post_webhook_json(
        webhook_url=webhook_url,
        payload=rendered_payload,
        timeout_seconds=webhook_timeout_seconds,
    )
    info["sent"] = ok
    info["error"] = error
    if ok:
        _save_alert_state(
            alert_state_path,
            {
                "last_alert_at": now.isoformat().replace("+00:00", "Z"),
                "last_signature": signature,
            },
        )
    return info


def main() -> int:
    parser = argparse.ArgumentParser(description="Check /security/rate-limits values against alert thresholds.")
    parser.add_argument("--base-url", default=os.getenv("BASE_URL", DEFAULT_BASE_URL))
    parser.add_argument("--requester-user-id", default=os.getenv("REQUESTER_USER_ID", DEFAULT_REQUESTER_USER_ID))
    parser.add_argument("--token", default=os.getenv("AUTH_TOKEN"))
    parser.add_argument("--timeout-seconds", type=float, default=DEFAULT_TIMEOUT_SECONDS)
    parser.add_argument("--total-limit", type=int, default=None, help="Alert when total_hits exceeds this value.")
    parser.add_argument(
        "--surface-limit",
        action="append",
        default=[],
        help="Per-surface threshold in form surface=limit. Can be repeated.",
    )
    parser.add_argument("--alert-webhook-url", default=os.getenv("ALERT_WEBHOOK_URL"))
    parser.add_argument(
        "--alert-webhook-kind",
        default=os.getenv("ALERT_WEBHOOK_KIND", "generic"),
        choices=["generic", "slack", "discord"],
        help="Webhook payload shape to emit.",
    )
    parser.add_argument("--alert-timeout-seconds", type=float, default=float(os.getenv("ALERT_TIMEOUT_SECONDS", "5")))
    parser.add_argument(
        "--alert-dedupe-seconds",
        type=int,
        default=parse_positive_int(os.getenv("ALERT_DEDUPE_SECONDS", "900"), 900),
        help="Suppress duplicate alerts with same violation signature within this window.",
    )
    parser.add_argument(
        "--alert-state-path",
        default=os.getenv("ALERT_STATE_PATH", str(DEFAULT_SNAPSHOTS_DIR / "alerts-state.json")),
        help="Local JSON file for alert dedupe state.",
    )
    parser.add_argument("--alert-environment", default=os.getenv("ALERT_ENV", "unknown"))
    parser.add_argument("--compact", action="store_true")
    args = parser.parse_args()

    if not args.token:
        print("Missing token. Use --token or set AUTH_TOKEN.", file=sys.stderr)
        return 2
    if args.total_limit is not None and args.total_limit < 0:
        print("--total-limit must be >= 0", file=sys.stderr)
        return 2
    if args.alert_dedupe_seconds < 0:
        print("--alert-dedupe-seconds must be >= 0", file=sys.stderr)
        return 2

    surface_limits: dict[str, int] = {}
    try:
        for item in args.surface_limit:
            surface, limit = _parse_surface_limit(item)
            surface_limits[surface] = limit
    except ValueError as exc:
        print(str(exc), file=sys.stderr)
        return 2

    status_code, body = fetch_rate_limits_snapshot(
        base_url=args.base_url,
        requester_user_id=args.requester_user_id,
        token=args.token,
        timeout_seconds=args.timeout_seconds,
    )
    if not (200 <= status_code < 300):
        payload = {"status": "error", "status_code": status_code, "body": body}
        print_json(payload, compact=args.compact)
        return 2

    total_hits = 0
    by_surface: dict[str, int] = {}
    if isinstance(body, dict):
        raw_total = body.get("total_hits")
        if isinstance(raw_total, int):
            total_hits = raw_total
        raw_by_surface = body.get("by_surface")
        if isinstance(raw_by_surface, dict):
            for key, value in raw_by_surface.items():
                if isinstance(key, str) and isinstance(value, int):
                    by_surface[key.lower()] = value

    violations: list[dict[str, Any]] = []
    if args.total_limit is not None and total_hits > args.total_limit:
        violations.append(
            {
                "type": "total_hits",
                "actual": total_hits,
                "limit": args.total_limit,
            }
        )
    for surface, limit in surface_limits.items():
        actual = by_surface.get(surface, 0)
        if actual > limit:
            violations.append(
                {
                    "type": "surface_hits",
                    "surface": surface,
                    "actual": actual,
                    "limit": limit,
                }
            )

    result = {
        "status": "alert" if violations else "ok",
        "total_hits": total_hits,
        "by_surface": by_surface,
        "violations": violations,
    }
    if violations:
        result["alert"] = _maybe_send_alert(
            result=result,
            webhook_url=args.alert_webhook_url,
            webhook_kind=args.alert_webhook_kind,
            webhook_timeout_seconds=args.alert_timeout_seconds,
            dedupe_seconds=args.alert_dedupe_seconds,
            alert_state_path=Path(args.alert_state_path).expanduser(),
            base_url=args.base_url,
            requester_user_id=args.requester_user_id,
            environment=args.alert_environment,
        )
    print_json(result, compact=args.compact)
    return 1 if violations else 0


if __name__ == "__main__":
    raise SystemExit(main())
