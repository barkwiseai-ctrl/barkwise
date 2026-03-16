from __future__ import annotations

import json
import logging
import os
import time
from dataclasses import asdict, dataclass, field
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Iterable, Iterator

from .config import CollectorConfig
from .filters import evaluate_title, extract_topic_tags


@dataclass
class BucketStats:
    seen: int = 0
    accepted: int = 0
    filtered_out: int = 0
    deduped: int = 0
    errors: int = 0


@dataclass
class SubredditProgress:
    subreddit: str
    buckets: dict[str, BucketStats] = field(default_factory=dict)


class RedditDogCollector:
    def __init__(self, config: CollectorConfig, logger: logging.Logger | None = None) -> None:
        self.config = config
        self.logger = logger or logging.getLogger(__name__)
        self.records_by_id: dict[str, dict[str, Any]] = {}
        self.progress: dict[str, SubredditProgress] = {}

    def run(self, output_path: Path, summary_out: Path | None = None) -> dict[str, Any]:
        reddit = self._create_reddit_client()
        output_path.parent.mkdir(parents=True, exist_ok=True)

        for subreddit_name in self.config.subreddits:
            subreddit_progress = SubredditProgress(subreddit=subreddit_name)
            self.progress[subreddit_name] = subreddit_progress
            self.logger.info("Collecting subreddit=%s", subreddit_name)
            try:
                subreddit = reddit.subreddit(subreddit_name)
                subreddit.id  # trigger lookup; raises if invalid/private
            except Exception as exc:
                self.logger.warning("Skipping subreddit=%s due to API error: %s", subreddit_name, exc)
                continue

            for bucket_name, target_count in self._bucket_targets().items():
                if target_count <= 0:
                    continue
                stats = BucketStats()
                subreddit_progress.buckets[bucket_name] = stats
                iterator = self._iter_bucket(subreddit=subreddit, bucket_name=bucket_name, target_count=target_count)
                bucket_accept_count = 0
                for submission in iterator:
                    if bucket_accept_count >= target_count:
                        break
                    stats.seen += 1
                    try:
                        accepted = self._process_submission(submission=submission, bucket_name=bucket_name, stats=stats)
                        if accepted:
                            stats.accepted += 1
                            bucket_accept_count += 1
                    except Exception as exc:
                        stats.errors += 1
                        self.logger.warning(
                            "Failed submission id=%s subreddit=%s bucket=%s error=%s",
                            getattr(submission, "id", "unknown"),
                            subreddit_name,
                            bucket_name,
                            exc,
                        )
                        time.sleep(self.config.rate_limit.error_backoff_seconds)
                    time.sleep(self.config.rate_limit.listing_item_sleep_seconds)

                self.logger.info(
                    "Progress subreddit=%s bucket=%s seen=%s accepted=%s filtered=%s deduped=%s errors=%s",
                    subreddit_name,
                    bucket_name,
                    stats.seen,
                    stats.accepted,
                    stats.filtered_out,
                    stats.deduped,
                    stats.errors,
                )
            time.sleep(self.config.rate_limit.subreddit_sleep_seconds)

        records = sorted(self.records_by_id.values(), key=lambda row: float(row.get("created_utc", 0.0)), reverse=True)
        with output_path.open("w", encoding="utf-8") as handle:
            for record in records:
                handle.write(json.dumps(record, ensure_ascii=True) + "\n")

        summary = self._build_summary(records_count=len(records), output_path=output_path)
        self.logger.info("Collection finished unique_records=%s output=%s", len(records), output_path)
        self.logger.info("progress_summary=%s", json.dumps(summary, indent=2))

        if summary_out is not None:
            summary_out.parent.mkdir(parents=True, exist_ok=True)
            summary_out.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
            self.logger.info("Wrote summary report: %s", summary_out)
        return summary

    def _process_submission(self, submission: Any, bucket_name: str, stats: BucketStats) -> bool:
        title = (submission.title or "").strip()
        should_skip, reason = evaluate_title(title, self.config.filters)
        if should_skip:
            stats.filtered_out += 1
            self.logger.debug("Skipping submission id=%s reason=%s title=%r", submission.id, reason, title[:120])
            return False

        existing = self.records_by_id.get(submission.id)
        if existing is not None:
            source_buckets = existing.setdefault("source_buckets", [])
            if bucket_name not in source_buckets:
                source_buckets.append(bucket_name)
            stats.deduped += 1
            return False

        comments = self._collect_best_answers(submission)
        record = self._build_record(submission=submission, bucket_name=bucket_name, best_answers=comments)
        self.records_by_id[submission.id] = record
        return True

    def _build_record(self, submission: Any, bucket_name: str, best_answers: list[dict[str, Any]]) -> dict[str, Any]:
        selftext = submission.selftext or ""
        return {
            "id": submission.id,
            "subreddit": str(submission.subreddit.display_name),
            "created_utc": float(getattr(submission, "created_utc", 0.0)),
            "title": submission.title or "",
            "selftext": selftext,
            "selftest": selftext,  # alias kept for compatibility with existing request typo
            "permalink": f"https://www.reddit.com{submission.permalink}",
            "url": submission.url,
            "score": int(getattr(submission, "score", 0) or 0),
            "upvote_ratio": float(getattr(submission, "upvote_ratio", 0.0) or 0.0),
            "num_comments": int(getattr(submission, "num_comments", 0) or 0),
            "author": str(submission.author) if submission.author else None,
            "link_flair_text": submission.link_flair_text or None,
            "high_level_topic_tags": extract_topic_tags(submission.title or ""),
            "source_buckets": [bucket_name],
            "best_answers": best_answers,
        }

    def _collect_best_answers(self, submission: Any) -> list[dict[str, Any]]:
        from praw.models import Comment, MoreComments

        submission.comment_sort = "top"
        submission.comments.replace_more(limit=0)
        candidates: list[dict[str, Any]] = []
        for item in submission.comments:
            if isinstance(item, MoreComments) or not isinstance(item, Comment):
                continue
            if item.parent_id != f"t3_{submission.id}":
                continue
            body = (item.body or "").strip()
            if not self._is_comment_eligible(comment=item, body=body):
                continue
            candidates.append(
                {
                    "id": item.id,
                    "score": int(getattr(item, "score", 0) or 0),
                    "author": str(item.author) if item.author else None,
                    "created_utc": float(getattr(item, "created_utc", 0.0) or 0.0),
                    "body": body,
                    "permalink": f"https://www.reddit.com{item.permalink}",
                }
            )
        candidates.sort(key=lambda c: c["score"], reverse=True)
        time.sleep(self.config.rate_limit.comment_fetch_sleep_seconds)
        return candidates[: self.config.comments.max_top_level_answers]

    def _is_comment_eligible(self, comment: Any, body: str) -> bool:
        if len(body) < self.config.comments.min_length_chars:
            return False
        lower_body = body.lower()
        if lower_body in {"[deleted]", "[removed]"}:
            return False
        if self.config.comments.skip_removed_or_moderation:
            removal_markers = [
                "removed by moderator",
                "this comment has been removed",
                "comment removed by moderator",
                "this has been removed",
            ]
            if any(marker in lower_body for marker in removal_markers):
                return False
            author = str(comment.author).lower() if comment.author else ""
            if author == "automoderator":
                return False
        return True

    def _iter_bucket(self, subreddit: Any, bucket_name: str, target_count: int) -> Iterator[Any]:
        scan_limit = max(target_count * self.config.sampling.scan_multiplier, target_count)
        if bucket_name == "top_year":
            yield from self._limited_iterator(subreddit.top(time_filter="year", limit=scan_limit), scan_limit)
            return
        if bucket_name == "top_month":
            yield from self._limited_iterator(subreddit.top(time_filter="month", limit=scan_limit), scan_limit)
            return
        if bucket_name == "top_week":
            yield from self._limited_iterator(subreddit.top(time_filter="week", limit=scan_limit), scan_limit)
            return
        if bucket_name == "new":
            yield from self._iter_new_posts(subreddit=subreddit, scan_limit=scan_limit)
            return
        if bucket_name == "best":
            yield from self._limited_iterator(subreddit.best(limit=scan_limit), scan_limit)
            return
        if bucket_name == "hot":
            yield from self._limited_iterator(subreddit.hot(limit=scan_limit), scan_limit)
            return
        if bucket_name == "rising":
            yield from self._limited_iterator(subreddit.rising(limit=scan_limit), scan_limit)
            return
        raise ValueError(f"Unsupported bucket: {bucket_name}")

    def _iter_new_posts(self, subreddit: Any, scan_limit: int) -> Iterator[Any]:
        within_days = self.config.sampling.new_within_days
        if within_days <= 0:
            yield from self._limited_iterator(subreddit.new(limit=scan_limit), scan_limit)
            return

        cutoff_epoch = (datetime.now(tz=timezone.utc) - timedelta(days=within_days)).timestamp()
        newer: list[Any] = []
        older: list[Any] = []
        for submission in subreddit.new(limit=scan_limit):
            if float(getattr(submission, "created_utc", 0.0)) >= cutoff_epoch:
                newer.append(submission)
            else:
                older.append(submission)
        for item in newer:
            yield item
        for item in older:
            yield item

    @staticmethod
    def _limited_iterator(source: Iterable[Any], limit: int) -> Iterator[Any]:
        count = 0
        for item in source:
            if count >= limit:
                break
            yield item
            count += 1

    def _bucket_targets(self) -> dict[str, int]:
        return {
            "top_year": self.config.sampling.n_year,
            "top_month": self.config.sampling.n_month,
            "top_week": self.config.sampling.n_week,
            "new": self.config.sampling.n_new,
            "best": self.config.sampling.n_best,
            "hot": self.config.sampling.n_hot,
            "rising": self.config.sampling.n_rising,
        }

    def _build_summary(self, records_count: int, output_path: Path) -> dict[str, Any]:
        subreddits: dict[str, Any] = {}
        for subreddit_name, progress in self.progress.items():
            subreddits[subreddit_name] = {
                "buckets": {name: asdict(stats) for name, stats in progress.buckets.items()},
            }
        return {
            "timestamp_utc": datetime.now(tz=timezone.utc).isoformat(),
            "output_path": str(output_path),
            "unique_records": records_count,
            "subreddits": subreddits,
        }

    def _create_reddit_client(self) -> Any:
        try:
            import praw
        except ModuleNotFoundError as exc:  # pragma: no cover - dependency guard
            raise RuntimeError("praw is not installed. Run `pip install -r requirements.txt` in backend/.") from exc

        client_id = os.getenv("REDDIT_CLIENT_ID")
        client_secret = os.getenv("REDDIT_CLIENT_SECRET")
        user_agent = os.getenv("REDDIT_USER_AGENT")
        missing: list[str] = []
        if not client_id:
            missing.append("REDDIT_CLIENT_ID")
        if not client_secret:
            missing.append("REDDIT_CLIENT_SECRET")
        if not user_agent:
            missing.append("REDDIT_USER_AGENT")
        if missing:
            raise RuntimeError(f"Missing Reddit API environment variables: {', '.join(missing)}")
        return praw.Reddit(
            client_id=client_id,
            client_secret=client_secret,
            user_agent=user_agent,
            ratelimit_seconds=10,
            check_for_async=False,
        )
