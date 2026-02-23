#!/usr/bin/env python3
import argparse
import json
import os
from pathlib import Path

from security_rate_limits_common import DEFAULT_SNAPSHOTS_DIR


def main() -> int:
    parser = argparse.ArgumentParser(description="Reset (delete) security alert dedupe state file.")
    parser.add_argument(
        "--alert-state-path",
        default=os.getenv("ALERT_STATE_PATH", str(DEFAULT_SNAPSHOTS_DIR / "alerts-state.json")),
        help="Path to dedupe state file used by threshold checker.",
    )
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--compact", action="store_true")
    args = parser.parse_args()

    state_path = Path(args.alert_state_path).expanduser()
    existed = state_path.exists()
    removed = False
    if existed and not args.dry_run:
        state_path.unlink(missing_ok=True)
        removed = True

    payload = {
        "status": "ok",
        "alert_state_path": str(state_path),
        "existed": existed,
        "removed": removed,
        "dry_run": args.dry_run,
    }
    if args.compact:
        print(json.dumps(payload, separators=(",", ":")))
    else:
        print(json.dumps(payload, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
