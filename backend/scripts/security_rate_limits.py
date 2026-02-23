#!/usr/bin/env python3
import argparse
import os
import sys

from security_rate_limits_common import (
    DEFAULT_BASE_URL,
    DEFAULT_REQUESTER_USER_ID,
    DEFAULT_TIMEOUT_SECONDS,
    fetch_rate_limits_snapshot,
    print_json,
    reset_rate_limits_snapshot,
)


def main() -> int:
    parser = argparse.ArgumentParser(description="Inspect or reset security rate-limit metrics.")
    parser.add_argument("command", choices=["snapshot", "reset"], help="Operation to perform.")
    parser.add_argument("--base-url", default=os.getenv("BASE_URL", DEFAULT_BASE_URL))
    parser.add_argument("--requester-user-id", default=os.getenv("REQUESTER_USER_ID", DEFAULT_REQUESTER_USER_ID))
    parser.add_argument("--token", default=os.getenv("AUTH_TOKEN"))
    parser.add_argument("--timeout-seconds", type=float, default=DEFAULT_TIMEOUT_SECONDS)
    parser.add_argument("--compact", action="store_true", help="Output compact JSON (single line).")
    args = parser.parse_args()

    if not args.token:
        print("Missing token. Use --token or set AUTH_TOKEN.", file=sys.stderr)
        return 2

    if args.command == "snapshot":
        status_code, body = fetch_rate_limits_snapshot(
            base_url=args.base_url,
            requester_user_id=args.requester_user_id,
            token=args.token,
            timeout_seconds=args.timeout_seconds,
        )
    else:
        status_code, body = reset_rate_limits_snapshot(
            base_url=args.base_url,
            requester_user_id=args.requester_user_id,
            token=args.token,
            timeout_seconds=args.timeout_seconds,
        )
    output = {"status_code": status_code, "body": body}
    print_json(output, compact=args.compact)
    return 0 if 200 <= status_code < 300 else 1


if __name__ == "__main__":
    raise SystemExit(main())
