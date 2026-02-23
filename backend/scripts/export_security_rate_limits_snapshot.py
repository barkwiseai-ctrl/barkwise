#!/usr/bin/env python3
import argparse
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path

from security_rate_limits_common import (
    DEFAULT_BASE_URL,
    DEFAULT_REQUESTER_USER_ID,
    DEFAULT_SNAPSHOTS_DIR,
    DEFAULT_TIMEOUT_SECONDS,
    fetch_rate_limits_snapshot,
    print_json,
)


def _utc_stamp() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")


def main() -> int:
    parser = argparse.ArgumentParser(description="Export /security/rate-limits snapshot to a timestamped JSON file.")
    parser.add_argument("--base-url", default=os.getenv("BASE_URL", DEFAULT_BASE_URL))
    parser.add_argument("--requester-user-id", default=os.getenv("REQUESTER_USER_ID", DEFAULT_REQUESTER_USER_ID))
    parser.add_argument("--token", default=os.getenv("AUTH_TOKEN"))
    parser.add_argument("--output-dir", default=os.getenv("OUTPUT_DIR", str(DEFAULT_SNAPSHOTS_DIR)))
    parser.add_argument("--timeout-seconds", type=float, default=DEFAULT_TIMEOUT_SECONDS)
    parser.add_argument("--print-path-only", action="store_true")
    args = parser.parse_args()

    if not args.token:
        print("Missing token. Use --token or set AUTH_TOKEN.", file=sys.stderr)
        return 2

    status_code, body = fetch_rate_limits_snapshot(
        base_url=args.base_url,
        requester_user_id=args.requester_user_id,
        token=args.token,
        timeout_seconds=args.timeout_seconds,
    )
    if not (200 <= status_code < 300):
        print(json.dumps({"status_code": status_code, "body": body}, indent=2, sort_keys=True), file=sys.stderr)
        return 1

    exported_at = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    payload = {
        "exported_at": exported_at,
        "base_url": args.base_url.rstrip("/"),
        "requester_user_id": args.requester_user_id,
        "status_code": status_code,
        "metrics": body,
    }

    output_dir = Path(args.output_dir).expanduser()
    output_dir.mkdir(parents=True, exist_ok=True)
    output_path = output_dir / f"security-rate-limits-{_utc_stamp()}.json"
    output_path.write_text(json.dumps(payload, indent=2, sort_keys=True), encoding="utf-8")

    latest_path = output_dir / "latest.json"
    latest_path.write_text(json.dumps(payload, indent=2, sort_keys=True), encoding="utf-8")

    if args.print_path_only:
        print(str(output_path))
    else:
        print_json({"status": "ok", "output_path": str(output_path)}, compact=False)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
