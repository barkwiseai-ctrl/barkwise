#!/usr/bin/env python3
import json
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

DEFAULT_BASE_URL = "http://localhost:8000"
DEFAULT_REQUESTER_USER_ID = "user_1"
DEFAULT_TIMEOUT_SECONDS = 10.0
DEFAULT_SNAPSHOTS_DIR = Path("/Users/yingxu/public-repos/pet-social-app/backend/data/security-audit-snapshots")


def parse_positive_int(value: str, fallback: int) -> int:
    try:
        parsed = int(value)
    except ValueError:
        return fallback
    if parsed <= 0:
        return fallback
    return parsed


def parse_json_or_text(raw: str) -> Any:
    if not raw:
        return None
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return raw


def print_json(payload: Any, compact: bool) -> None:
    if compact:
        print(json.dumps(payload, separators=(",", ":")))
        return
    print(json.dumps(payload, indent=2, sort_keys=True))


def _build_security_url(*, base_url: str, endpoint_path: str, requester_user_id: str) -> str:
    query = urllib.parse.urlencode({"requester_user_id": requester_user_id})
    return f"{base_url.rstrip('/')}{endpoint_path}?{query}"


def request_security_endpoint(
    *,
    base_url: str,
    endpoint_path: str,
    requester_user_id: str,
    token: str,
    method: str,
    timeout_seconds: float,
) -> tuple[int, Any]:
    url = _build_security_url(
        base_url=base_url,
        endpoint_path=endpoint_path,
        requester_user_id=requester_user_id,
    )
    headers = {
        "Accept": "application/json",
        "Authorization": f"Bearer {token}",
    }
    req = urllib.request.Request(url=url, method=method.upper(), headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout_seconds) as response:
            raw = response.read().decode("utf-8")
            return response.status, parse_json_or_text(raw)
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8")
        return exc.code, parse_json_or_text(raw)


def fetch_rate_limits_snapshot(
    *,
    base_url: str,
    requester_user_id: str,
    token: str,
    timeout_seconds: float,
) -> tuple[int, Any]:
    return request_security_endpoint(
        base_url=base_url,
        endpoint_path="/security/rate-limits",
        requester_user_id=requester_user_id,
        token=token,
        method="GET",
        timeout_seconds=timeout_seconds,
    )


def reset_rate_limits_snapshot(
    *,
    base_url: str,
    requester_user_id: str,
    token: str,
    timeout_seconds: float,
) -> tuple[int, Any]:
    return request_security_endpoint(
        base_url=base_url,
        endpoint_path="/security/rate-limits/reset",
        requester_user_id=requester_user_id,
        token=token,
        method="POST",
        timeout_seconds=timeout_seconds,
    )
