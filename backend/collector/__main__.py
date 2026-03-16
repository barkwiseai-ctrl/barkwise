from __future__ import annotations

import argparse
import json
import logging
from pathlib import Path

from .config import load_config
from .runner import RedditDogCollector


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        prog="collector",
        description="Phase-1 Reddit dog-question collector for BarkWise AI.",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    run_parser = subparsers.add_parser("run", help="Run the Reddit collection job.")
    run_parser.add_argument("--config", required=True, help="Path to JSON/YAML config file.")
    run_parser.add_argument("--out", required=True, help="Output JSONL path.")
    run_parser.add_argument(
        "--summary-out",
        default="",
        help="Optional summary JSON path. If omitted, summary is only logged/stdout.",
    )
    run_parser.add_argument("--log-level", default="INFO", help="Python log level (INFO, DEBUG, WARNING).")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    logging.basicConfig(
        level=getattr(logging, str(args.log_level).upper(), logging.INFO),
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    logger = logging.getLogger("collector")

    if args.command == "run":
        config_path = Path(args.config)
        output_path = Path(args.out)
        summary_out = Path(args.summary_out) if args.summary_out else None

        try:
            config = load_config(config_path)
            collector = RedditDogCollector(config=config, logger=logger)
            summary = collector.run(output_path=output_path, summary_out=summary_out)
        except Exception as exc:
            logger.error("Collector failed: %s", exc)
            return 2

        print(json.dumps(summary, indent=2))
        return 0
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
