import json
import os
from pathlib import Path
import sqlite3
from threading import Lock
from typing import Any, Dict, List, Optional, Tuple

from app.models import (
    CommunityComment,
    CommunityEvent,
    CommunityPost,
    CommunityReport,
    EventRsvpRecord,
    Group,
    GroupChallenge,
    GroupJoinRecord,
)


class CommunityStore:
    def __init__(self, db_path: str) -> None:
        self._lock = Lock()
        path = Path(db_path)
        path.parent.mkdir(parents=True, exist_ok=True)
        self.db_path = str(path)
        self._init_db()

    def _connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path, check_same_thread=False)
        conn.row_factory = sqlite3.Row
        return conn

    def _init_db(self) -> None:
        with self._lock:
            with self._connect() as conn:
                conn.execute(
                    """
                    CREATE TABLE IF NOT EXISTS snapshots (
                        key TEXT PRIMARY KEY,
                        value_json TEXT NOT NULL,
                        updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """
                )
                conn.commit()

    def load_state(self) -> Optional[dict[str, Any]]:
        with self._lock:
            with self._connect() as conn:
                row = conn.execute(
                    "SELECT value_json FROM snapshots WHERE key = 'community_state'",
                ).fetchone()
        if not row:
            return None
        try:
            payload = json.loads(str(row["value_json"]))
        except json.JSONDecodeError:
            return None
        return payload if isinstance(payload, dict) else None

    def save_state(self, payload: dict[str, Any]) -> None:
        encoded = json.dumps(payload, separators=(",", ":"), ensure_ascii=True)
        with self._lock:
            with self._connect() as conn:
                conn.execute(
                    """
                    INSERT INTO snapshots(key, value_json, updated_at)
                    VALUES ('community_state', ?, CURRENT_TIMESTAMP)
                    ON CONFLICT(key) DO UPDATE SET
                        value_json = excluded.value_json,
                        updated_at = CURRENT_TIMESTAMP
                    """,
                    (encoded,),
                )
                conn.commit()


def encode_state_payload(
    *,
    groups: List[Group],
    group_memberships: List[GroupJoinRecord],
    community_posts: List[CommunityPost],
    community_events: List[CommunityEvent],
    event_rsvps: List[EventRsvpRecord],
    group_invites: Dict[str, Dict[str, str]],
    community_comments_by_post: Dict[str, List[CommunityComment]],
    community_reports: List[CommunityReport],
    blocked_users_by_user: Dict[str, set[str]],
    community_analytics_events: List[Dict[str, Any]],
    community_diagnostic_events: List[Dict[str, Any]],
    group_badges: Dict[str, set[str]],
    group_challenges: Dict[str, GroupChallenge],
    group_challenge_contributions: Dict[Tuple[str, str], int],
    group_member_reward_points: Dict[Tuple[str, str], Dict[str, int]],
    lost_found_followups_sent: Dict[Tuple[str, str], str],
) -> dict[str, Any]:
    return {
        "groups": [item.model_dump() for item in groups],
        "group_memberships": [item.model_dump() for item in group_memberships],
        "community_posts": [item.model_dump() for item in community_posts],
        "community_events": [item.model_dump() for item in community_events],
        "event_rsvps": [item.model_dump() for item in event_rsvps],
        "group_invites": {str(key): dict(value) for key, value in group_invites.items()},
        "community_comments_by_post": {
            str(post_id): [comment.model_dump() for comment in comments]
            for post_id, comments in community_comments_by_post.items()
        },
        "community_reports": [item.model_dump() for item in community_reports],
        "blocked_users_by_user": {
            str(user_id): sorted(list(blocked))
            for user_id, blocked in blocked_users_by_user.items()
        },
        "community_analytics_events": list(community_analytics_events),
        "community_diagnostic_events": list(community_diagnostic_events),
        "group_badges": {
            str(group_id): sorted(list(badges))
            for group_id, badges in group_badges.items()
        },
        "group_challenges": {
            str(challenge_id): challenge.model_dump()
            for challenge_id, challenge in group_challenges.items()
        },
        "group_challenge_contributions": [
            {"challenge_id": challenge_id, "user_id": user_id, "count": count}
            for (challenge_id, user_id), count in group_challenge_contributions.items()
        ],
        "group_member_reward_points": [
            {"group_id": group_id, "user_id": user_id, "points": dict(points)}
            for (group_id, user_id), points in group_member_reward_points.items()
        ],
        "lost_found_followups_sent": [
            {"post_id": post_id, "status": status, "at": value}
            for (post_id, status), value in lost_found_followups_sent.items()
        ],
    }


def decode_state_payload(payload: dict[str, Any]) -> dict[str, Any]:
    groups = [Group.model_validate(item) for item in payload.get("groups", []) if isinstance(item, dict)]
    group_memberships = [
        GroupJoinRecord.model_validate(item)
        for item in payload.get("group_memberships", [])
        if isinstance(item, dict)
    ]
    community_posts = [
        CommunityPost.model_validate(item)
        for item in payload.get("community_posts", [])
        if isinstance(item, dict)
    ]
    community_events = [
        CommunityEvent.model_validate(item)
        for item in payload.get("community_events", [])
        if isinstance(item, dict)
    ]
    event_rsvps = [
        EventRsvpRecord.model_validate(item)
        for item in payload.get("event_rsvps", [])
        if isinstance(item, dict)
    ]
    group_invites = {
        str(key): dict(value)
        for key, value in payload.get("group_invites", {}).items()
        if isinstance(key, str) and isinstance(value, dict)
    }
    comments_raw = payload.get("community_comments_by_post", {})
    community_comments_by_post: Dict[str, List[CommunityComment]] = {}
    if isinstance(comments_raw, dict):
        for post_id, comments in comments_raw.items():
            if not isinstance(post_id, str) or not isinstance(comments, list):
                continue
            community_comments_by_post[post_id] = [
                CommunityComment.model_validate(item) for item in comments if isinstance(item, dict)
            ]
    community_reports = [
        CommunityReport.model_validate(item)
        for item in payload.get("community_reports", [])
        if isinstance(item, dict)
    ]
    blocked_users_by_user = {
        str(user_id): set(str(item) for item in blocked if isinstance(item, str))
        for user_id, blocked in payload.get("blocked_users_by_user", {}).items()
        if isinstance(user_id, str) and isinstance(blocked, list)
    }
    analytics_raw = payload.get("community_analytics_events", [])
    diagnostics_raw = payload.get("community_diagnostic_events", [])
    community_analytics_events = [item for item in analytics_raw if isinstance(item, dict)]
    community_diagnostic_events = [item for item in diagnostics_raw if isinstance(item, dict)]
    group_badges = {
        str(group_id): set(str(item) for item in badges if isinstance(item, str))
        for group_id, badges in payload.get("group_badges", {}).items()
        if isinstance(group_id, str) and isinstance(badges, list)
    }
    group_challenges = {
        str(challenge_id): GroupChallenge.model_validate(challenge)
        for challenge_id, challenge in payload.get("group_challenges", {}).items()
        if isinstance(challenge_id, str) and isinstance(challenge, dict)
    }
    group_challenge_contributions: Dict[Tuple[str, str], int] = {}
    for item in payload.get("group_challenge_contributions", []):
        if not isinstance(item, dict):
            continue
        challenge_id = str(item.get("challenge_id", "")).strip()
        user_id = str(item.get("user_id", "")).strip()
        count = int(item.get("count", 0))
        if challenge_id and user_id:
            group_challenge_contributions[(challenge_id, user_id)] = count
    group_member_reward_points: Dict[Tuple[str, str], Dict[str, int]] = {}
    for item in payload.get("group_member_reward_points", []):
        if not isinstance(item, dict):
            continue
        group_id = str(item.get("group_id", "")).strip()
        user_id = str(item.get("user_id", "")).strip()
        points = item.get("points", {})
        if not group_id or not user_id or not isinstance(points, dict):
            continue
        parsed_points = {
            str(key): int(value)
            for key, value in points.items()
            if isinstance(key, str) and isinstance(value, (int, float))
        }
        group_member_reward_points[(group_id, user_id)] = parsed_points
    lost_found_followups_sent: Dict[Tuple[str, str], str] = {}
    for item in payload.get("lost_found_followups_sent", []):
        if not isinstance(item, dict):
            continue
        post_id = str(item.get("post_id", "")).strip()
        status = str(item.get("status", "")).strip()
        at = str(item.get("at", "")).strip()
        if post_id and status and at:
            lost_found_followups_sent[(post_id, status)] = at
    return {
        "groups": groups,
        "group_memberships": group_memberships,
        "community_posts": community_posts,
        "community_events": community_events,
        "event_rsvps": event_rsvps,
        "group_invites": group_invites,
        "community_comments_by_post": community_comments_by_post,
        "community_reports": community_reports,
        "blocked_users_by_user": blocked_users_by_user,
        "community_analytics_events": community_analytics_events,
        "community_diagnostic_events": community_diagnostic_events,
        "group_badges": group_badges,
        "group_challenges": group_challenges,
        "group_challenge_contributions": group_challenge_contributions,
        "group_member_reward_points": group_member_reward_points,
        "lost_found_followups_sent": lost_found_followups_sent,
    }


default_db = str(Path(__file__).resolve().parents[2] / "data" / "community.sqlite3")
community_store = CommunityStore(db_path=os.getenv("COMMUNITY_DB_PATH", default_db))
