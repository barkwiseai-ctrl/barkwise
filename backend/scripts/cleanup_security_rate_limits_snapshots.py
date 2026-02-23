#!/usr/bin/env python3
import argparse
import json
import os
from datetime import datetime, timedelta, timezone
from pathlib import Path

from security_rate_limits_common import DEFAULT_SNAPSHOTS_DIR, parse_positive_int

SNAPSHOT_PREFIX = "security-rate-limits-"
SNAPSHOT_SUFFIX = ".json"


def main() -> int:
    parser = argparse.ArgumentParser(description="Delete old security rate-limit snapshot files.")
    parser.add_argument("--output-dir", default=os.getenv("OUTPUT_DIR", str(DEFAULT_SNAPSHOTS_DIR)))
    parser.add_argument("--retain-days", default=os.getenv("RETAIN_DAYS", "14"))
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--compact", action="store_true")
    args = parser.parse_args()

    retain_days = parse_positive_int(str(args.retain_days), 14)
    output_dir = Path(args.output_dir).expanduser()
    output_dir.mkdir(parents=True, exist_ok=True)

    cutoff = datetime.now(timezone.utc) - timedelta(days=retain_days)
    deleted: list[str] = []
    kept: list[str] = []
    skipped: list[str] = []

    for path in sorted(output_dir.iterdir()):
        if not path.is_file():
            skipped.append(str(path))
            continue
        if path.name in {"latest.json", "export.log"}:
            kept.append(str(path))
            continue
        if not (path.name.startswith(SNAPSHOT_PREFIX) and path.name.endswith(SNAPSHOT_SUFFIX)):
            skipped.append(str(path))
            continue
        modified_at = datetime.fromtimestamp(path.stat().st_mtime, tz=timezone.utc)
        if modified_at >= cutoff:
            kept.append(str(path))
            continue
        deleted.append(str(path))
        if not args.dry_run:
            path.unlink(missing_ok=True)

    result = {
        "status": "ok",
        "output_dir": str(output_dir),
        "retain_days": retain_days,
        "dry_run": args.dry_run,
        "deleted_count": len(deleted),
        "kept_count": len(kept),
        "skipped_count": len(skipped),
        "deleted": deleted,
    }
    if args.compact:
        print(json.dumps(result, separators=(",", ":")))
    else:
        print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
