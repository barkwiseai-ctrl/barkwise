import base64
from datetime import datetime, timedelta, timezone
from math import asin, cos, radians, sin, sqrt
from pathlib import Path
from typing import Any, Dict, Optional, Tuple
from uuid import uuid4

from fastapi import APIRouter, Header, HTTPException, Query, status

from app.auth import assert_actor_authorized, resolve_request_user
from app.data import KNOWN_SUBURBS, community_events, community_posts, event_rsvps, group_invites, group_memberships, groups
from app.models import (
    CommunityAnalyticsEventCreateRequest,
    CommunityBlockUserRequest,
    CommunityBlockUserResponse,
    CommunityDiagnosticEventCreateRequest,
    CommunityEvent,
    CommunityEventCreateRequest,
    CommunityEventRsvpRequest,
    CommunityEventView,
    CommunityFunnelMetrics,
    CommunityPostPhotoUploadResponse,
    CommunityPostPhotoUploadRequest,
    CommunityPost,
    CommunityPostCreate,
    CommunityPostResolveRequest,
    CommunityPostUpdateRequest,
    CommunityReport,
    CommunityReportCreateRequest,
    CommunityReportResolveRequest,
    EventRsvpRecord,
    Group,
    GroupAddMemberRequest,
    GroupChallenge,
    GroupChallengeParticipationRequest,
    GroupChallengeParticipationResult,
    GroupChallengeView,
    GroupCreateRequest,
    GroupJoinModerationRequest,
    GroupJoinRecord,
    GroupJoinRequest,
    GroupJoinRequestView,
    GroupInviteCreateRequest,
    GroupInviteCreateResponse,
    GroupInviteResolveResponse,
    GroupOnboardingCompleteRequest,
    GroupOnboardingCompleteResponse,
    GroupView,
)
from app.services.notification_store import notification_store

router = APIRouter(prefix="/community", tags=["community"])
INVITE_TTL_HOURS = 48
GROUP_BADGES: Dict[str, set[str]] = {}
GROUP_CHALLENGES: Dict[str, GroupChallenge] = {}
GROUP_CHALLENGE_CONTRIBUTIONS: Dict[Tuple[str, str], int] = {}
GROUP_MEMBER_REWARD_POINTS: Dict[Tuple[str, str], Dict[str, int]] = {}
POST_RATE_LIMIT_WINDOW = timedelta(minutes=10)
POST_RATE_LIMIT_MAX_POSTS = 4
POST_RATE_LIMIT_HISTORY: Dict[str, list[datetime]] = {}
BLOCKED_USERS_BY_USER: Dict[str, set[str]] = {}
COMMUNITY_REPORTS: list[CommunityReport] = []
COMMUNITY_ANALYTICS_EVENTS: list[Dict[str, Any]] = []
COMMUNITY_DIAGNOSTIC_EVENTS: list[Dict[str, Any]] = []
LOST_FOUND_FOLLOWUPS_SENT: Dict[Tuple[str, str], str] = {}
SUBURB_COORDINATES: Dict[str, Tuple[float, float]] = {
    "surry hills": (-33.8886, 151.2094),
    "newtown": (-33.8981, 151.1742),
    "redfern": (-33.8928, 151.2040),
}
ADMIN_USER_IDS = {"admin", "user_1", "user_3"}
UPLOAD_ROOT = Path(__file__).resolve().parent.parent / "web" / "uploads" / "lost_found"


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


def _month_cycle_bounds(now: datetime) -> Tuple[datetime, datetime, str]:
    start = now.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
    if start.month == 12:
        end = start.replace(year=start.year + 1, month=1)
    else:
        end = start.replace(month=start.month + 1)
    cycle = start.strftime("%Y%m")
    return start, end, cycle


def _week_cycle_bounds(now: datetime) -> Tuple[datetime, datetime, str]:
    monday = (now - timedelta(days=now.weekday())).replace(hour=0, minute=0, second=0, microsecond=0)
    end = monday + timedelta(days=7)
    iso = monday.isocalendar()
    cycle = f"{iso.year}W{iso.week:02d}"
    return monday, end, cycle


def _challenge_id(group_id: str, challenge_type: str, cycle: str) -> str:
    return f"gc_{challenge_type}_{group_id}_{cycle}"


def _challenge_template(group: Group, challenge_type: str) -> Tuple[str, str, int, str]:
    if challenge_type == "pack_builder":
        target = max(5, min(30, group.member_count // 4 + 3))
        return (
            "Pack Builder",
            "Grow your local pack together. Every new approved member helps.",
            target,
            "Group badge: Pack Builder",
        )
    target = max(8, min(40, group.member_count // 3 + 6))
    return (
        "Clean Park Streak",
        "Log cleanup check-ins as a group. Team progress unlocks shared rewards.",
        target,
        "Group badge: Clean Park Collective",
    )


def _sum_challenge_progress(challenge_id: str) -> int:
    return sum(value for (cid, _), value in GROUP_CHALLENGE_CONTRIBUTIONS.items() if cid == challenge_id)


def _ensure_group_challenge(group: Group, challenge_type: str) -> GroupChallenge:
    now = _utc_now()
    if challenge_type == "pack_builder":
        start_at, end_at, cycle = _month_cycle_bounds(now)
    else:
        start_at, end_at, cycle = _week_cycle_bounds(now)
    challenge_id = _challenge_id(group.id, challenge_type, cycle)
    existing = GROUP_CHALLENGES.get(challenge_id)
    if existing:
        existing.progress_count = _sum_challenge_progress(existing.id)
        existing.status = "completed" if existing.progress_count >= existing.target_count else "active"
        return existing

    title, description, target_count, reward_label = _challenge_template(group, challenge_type)
    challenge = GroupChallenge(
        id=challenge_id,
        group_id=group.id,
        type=challenge_type,  # type: ignore[arg-type]
        title=title,
        description=description,
        target_count=target_count,
        progress_count=0,
        status="active",
        reward_label=reward_label,
        start_at=start_at.isoformat().replace("+00:00", "Z"),
        end_at=end_at.isoformat().replace("+00:00", "Z"),
    )
    GROUP_CHALLENGES[challenge_id] = challenge
    return challenge


def _active_group_challenges(group: Group) -> list[GroupChallenge]:
    pack = _ensure_group_challenge(group, "pack_builder")
    clean = _ensure_group_challenge(group, "clean_park_streak")
    return [pack, clean]


def _reward_points(group_id: str, user_id: str) -> Dict[str, int]:
    return GROUP_MEMBER_REWARD_POINTS.setdefault((group_id, user_id), {"pack_builder": 0, "clean_park": 0})


def _group_cooperative_score(group_id: str) -> int:
    total = 0
    for (member_group_id, _), points in GROUP_MEMBER_REWARD_POINTS.items():
        if member_group_id != group_id:
            continue
        total += points.get("pack_builder", 0) + points.get("clean_park", 0)
    return total


def _group_badges(group_id: str) -> list[str]:
    return sorted(GROUP_BADGES.get(group_id, set()))


def _build_group_view(group: Group, user_id: Optional[str]) -> GroupView:
    _active_group_challenges(group)
    if user_id:
        points = _reward_points(group.id, user_id)
        pack_points = points.get("pack_builder", 0)
        clean_points = points.get("clean_park", 0)
    else:
        pack_points = 0
        clean_points = 0
    return GroupView(
        id=group.id,
        name=group.name,
        suburb=group.suburb,
        member_count=group.member_count,
        official=group.official,
        owner_user_id=group.owner_user_id,
        membership_status=_membership_status(group.id, user_id),
        is_admin=_is_group_admin(group, user_id),
        pending_request_count=_pending_count(group.id),
        group_badges=_group_badges(group.id),
        cooperative_score=_group_cooperative_score(group.id),
        my_pack_builder_points=pack_points,
        my_clean_park_points=clean_points,
    )


def _apply_group_growth_reward(
    *,
    group: Group,
    contributor_user_id: Optional[str],
    member_added_user_id: Optional[str],
    contribution_count: int = 1,
) -> None:
    challenge = _ensure_group_challenge(group, "pack_builder")
    before_status = challenge.status
    if contributor_user_id:
        key = (challenge.id, contributor_user_id)
        GROUP_CHALLENGE_CONTRIBUTIONS[key] = GROUP_CHALLENGE_CONTRIBUTIONS.get(key, 0) + contribution_count
        contributor_points = _reward_points(group.id, contributor_user_id)
        contributor_points["pack_builder"] += contribution_count
    if member_added_user_id:
        newcomer_points = _reward_points(group.id, member_added_user_id)
        newcomer_points["pack_builder"] += 1

    challenge.progress_count = _sum_challenge_progress(challenge.id)
    challenge.status = "completed" if challenge.progress_count >= challenge.target_count else "active"
    if before_status != "completed" and challenge.status == "completed":
        GROUP_BADGES.setdefault(group.id, set()).add("Pack Builder")


def _normalize_suburb(suburb: str) -> str:
    return " ".join(suburb.strip().split()).title()


def _parse_created_at(value: Optional[str]) -> datetime:
    if not value:
        return datetime.min.replace(tzinfo=timezone.utc)
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return datetime.min.replace(tzinfo=timezone.utc)


def _infer_alert_type(title: str, body: str) -> str:
    haystack = f"{title} {body}".lower()
    if "found" in haystack and "lost" not in haystack:
        return "found"
    return "lost"


def _resolve_actor_user(
    *,
    authorization: Optional[str],
    explicit_user_id: Optional[str] = None,
    require_user: bool = True,
) -> Optional[str]:
    token_user_id = resolve_request_user(authorization)
    provided_user_id = explicit_user_id.strip() if explicit_user_id and explicit_user_id.strip() else None
    if token_user_id and provided_user_id and token_user_id != provided_user_id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Token user does not match actor user")
    actor_user_id = token_user_id or provided_user_id
    if require_user and not actor_user_id:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Actor user is required")
    return actor_user_id


def _ensure_post_owner(post: CommunityPost, actor_user_id: str) -> None:
    owner = (post.created_by or "").strip()
    if not owner:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Legacy post has no mutable owner")
    if owner != actor_user_id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Only post owner can modify this post")


def _derived_post_owner(post: CommunityPost) -> str:
    owner = (post.created_by or "").strip()
    if owner:
        return owner
    seed = abs(hash(post.id)) % 4
    return f"user_{seed + 1}"


def _record_analytics_event(
    *,
    user_id: str,
    event: str,
    category: str = "community",
    metadata: Optional[Dict[str, Any]] = None,
    duration_ms: Optional[int] = None,
) -> None:
    COMMUNITY_ANALYTICS_EVENTS.append(
        {
            "id": f"aevt_{uuid4().hex[:10]}",
            "user_id": user_id,
            "event": event,
            "category": category,
            "metadata": metadata or {},
            "duration_ms": duration_ms,
            "created_at": _utc_now(),
        }
    )
    if len(COMMUNITY_ANALYTICS_EVENTS) > 4000:
        del COMMUNITY_ANALYTICS_EVENTS[:-3000]


def _record_diagnostic_event(
    *,
    user_id: str,
    kind: str,
    message: str,
    context: Optional[Dict[str, Any]] = None,
    duration_ms: Optional[int] = None,
) -> None:
    COMMUNITY_DIAGNOSTIC_EVENTS.append(
        {
            "id": f"diag_{uuid4().hex[:10]}",
            "user_id": user_id,
            "kind": kind,
            "message": message.strip(),
            "context": context or {},
            "duration_ms": duration_ms,
            "created_at": _utc_now(),
        }
    )
    if len(COMMUNITY_DIAGNOSTIC_EVENTS) > 4000:
        del COMMUNITY_DIAGNOSTIC_EVENTS[:-3000]


def _haversine_km(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    radius_km = 6371.0
    dlat = radians(lat2 - lat1)
    dlng = radians(lng2 - lng1)
    sin_dlat = sin(dlat / 2.0)
    sin_dlng = sin(dlng / 2.0)
    a = sin_dlat * sin_dlat + cos(radians(lat1)) * cos(radians(lat2)) * sin_dlng * sin_dlng
    c = 2.0 * asin(min(1.0, sqrt(a)))
    return radius_km * c


def _default_suburb_coordinate(suburb: str) -> Optional[Tuple[float, float]]:
    key = suburb.strip().lower()
    return SUBURB_COORDINATES.get(key)


def _enrich_post_coordinates(post: CommunityPost) -> CommunityPost:
    if post.latitude is not None and post.longitude is not None:
        return post if post.created_by else post.model_copy(update={"created_by": _derived_post_owner(post)})
    center = _default_suburb_coordinate(post.suburb)
    if not center:
        return post if post.created_by else post.model_copy(update={"created_by": _derived_post_owner(post)})
    # Deterministic jitter keeps map markers from overlapping exactly in seeded data.
    jitter_seed = abs(hash(post.id)) % 400
    lat_offset = ((jitter_seed % 20) - 10) / 10000.0
    lng_offset = ((jitter_seed // 20) - 10) / 10000.0
    return post.model_copy(
        update={
            "created_by": post.created_by or _derived_post_owner(post),
            "latitude": center[0] + lat_offset,
            "longitude": center[1] + lng_offset,
        }
    )


def _validate_lost_found_create(payload: CommunityPostCreate) -> None:
    if payload.type != "lost_found":
        return
    if payload.alert_status and payload.alert_status != "open":
        raise HTTPException(status_code=400, detail="Lost/found alerts must start as open")
    if not (payload.pet_traits or "").strip():
        raise HTTPException(status_code=400, detail="pet_traits is required for lost/found alerts")
    if not (payload.last_seen_location or "").strip():
        raise HTTPException(status_code=400, detail="last_seen_location is required for lost/found alerts")
    if not (payload.contact_pref or "").strip():
        raise HTTPException(status_code=400, detail="contact_pref is required for lost/found alerts")
    if payload.latitude is not None and payload.longitude is None:
        raise HTTPException(status_code=400, detail="longitude is required when latitude is provided")
    if payload.longitude is not None and payload.latitude is None:
        raise HTTPException(status_code=400, detail="latitude is required when longitude is provided")


def _check_post_rate_limit(actor_user_id: str) -> None:
    now = _utc_now()
    history = POST_RATE_LIMIT_HISTORY.get(actor_user_id, [])
    history = [ts for ts in history if now - ts <= POST_RATE_LIMIT_WINDOW]
    if len(history) >= POST_RATE_LIMIT_MAX_POSTS:
        raise HTTPException(
            status_code=429,
            detail=f"Too many community posts. Limit {POST_RATE_LIMIT_MAX_POSTS} per {POST_RATE_LIMIT_WINDOW.seconds // 60} minutes.",
        )
    history.append(now)
    POST_RATE_LIMIT_HISTORY[actor_user_id] = history


def _send_nearby_lost_found_notifications(*, post: CommunityPost) -> None:
    if post.type != "lost_found":
        return
    target_suburb = post.suburb.lower()
    candidate_user_ids = {
        membership.user_id
        for membership in group_memberships
        if membership.status == "member"
        and any(group.id == membership.group_id and group.suburb.lower() == target_suburb for group in groups)
    }
    if post.created_by:
        candidate_user_ids.discard(post.created_by)
    for user_id in candidate_user_ids:
        blocked_by_viewer = post.created_by in BLOCKED_USERS_BY_USER.get(user_id, set()) if post.created_by else False
        blocked_by_author = user_id in BLOCKED_USERS_BY_USER.get(post.created_by or "", set())
        if blocked_by_viewer or blocked_by_author:
            continue
        notification_store.create(
            user_id=user_id,
            title="Nearby lost/found alert",
            body=f"{post.title} in {post.suburb}",
            category="community",
            deep_link=f"post:{post.id}",
        )


def _dispatch_lost_found_followups() -> None:
    now = _utc_now()
    for idx, post in enumerate(community_posts):
        if post.type != "lost_found":
            continue
        if (post.alert_status or "open") != "open":
            continue
        created_at = _parse_created_at(post.created_at)
        if created_at == datetime.min.replace(tzinfo=timezone.utc):
            continue
        age_hours = (now - created_at).total_seconds() / 3600.0

        followup_key = (post.id, "still_missing")
        if age_hours >= 12 and followup_key not in LOST_FOUND_FOLLOWUPS_SENT and post.created_by:
            notification_store.create(
                user_id=post.created_by,
                title="Still missing?",
                body=f"Update your alert \"{post.title}\" if your pet is still missing.",
                category="community",
                deep_link=f"post:{post.id}",
            )
            LOST_FOUND_FOLLOWUPS_SENT[followup_key] = now.isoformat()

        auto_expire_key = (post.id, "auto_expired")
        if age_hours >= 72 and auto_expire_key not in LOST_FOUND_FOLLOWUPS_SENT:
            updated = post.model_copy(
                update={
                    "alert_status": "expired",
                    "resolved_at": now.isoformat().replace("+00:00", "Z"),
                    "resolved_note": post.resolved_note or "Auto-expired after 72 hours without update",
                }
            )
            community_posts[idx] = updated
            LOST_FOUND_FOLLOWUPS_SENT[auto_expire_key] = now.isoformat()
            if updated.created_by:
                notification_store.create(
                    user_id=updated.created_by,
                    title="Alert auto-archived",
                    body=f"Your alert \"{updated.title}\" was marked no longer active.",
                    category="community",
                    deep_link=f"post:{updated.id}",
                )


def ensure_official_group(suburb: str) -> Group:
    normalized = _normalize_suburb(suburb)
    existing = next((g for g in groups if g.official and g.suburb.lower() == normalized.lower()), None)
    if existing:
        _active_group_challenges(existing)
        return existing

    group = Group(
        id=f"g_official_{uuid4().hex[:8]}",
        name=f"{normalized} Official Pet Community",
        suburb=normalized,
        member_count=0,
        official=True,
    )
    groups.append(group)
    _active_group_challenges(group)
    return group


def _seed_group_rewards() -> None:
    seed_points = {
        ("g_user_dogpark_surry", "user_1"): {"pack_builder": 3, "clean_park": 3},
        ("g_user_dogpark_surry", "user_2"): {"pack_builder": 2, "clean_park": 2},
        ("g_user_dogpark_surry", "user_3"): {"pack_builder": 1, "clean_park": 2},
        ("g_user_5", "user_2"): {"pack_builder": 4, "clean_park": 2},
        ("g_official_surryhills", "guest_user"): {"pack_builder": 2, "clean_park": 1},
    }
    for (group_id, user_id), values in seed_points.items():
        record = _reward_points(group_id, user_id)
        record["pack_builder"] = max(record.get("pack_builder", 0), int(values["pack_builder"]))
        record["clean_park"] = max(record.get("clean_park", 0), int(values["clean_park"]))

    seed_contributions = [
        ("g_user_dogpark_surry", "pack_builder", "user_1", 3),
        ("g_user_dogpark_surry", "pack_builder", "user_2", 2),
        ("g_user_dogpark_surry", "pack_builder", "user_3", 1),
        ("g_user_dogpark_surry", "clean_park_streak", "user_1", 3),
        ("g_user_dogpark_surry", "clean_park_streak", "user_2", 2),
        ("g_user_dogpark_surry", "clean_park_streak", "user_3", 2),
        ("g_user_5", "pack_builder", "user_2", 4),
        ("g_user_5", "clean_park_streak", "user_2", 2),
        ("g_official_surryhills", "pack_builder", "guest_user", 2),
    ]
    for group_id, challenge_type, user_id, contribution_count in seed_contributions:
        group = next((item for item in groups if item.id == group_id), None)
        if not group:
            continue
        membership = next(
            (
                record
                for record in group_memberships
                if record.group_id == group_id and record.user_id == user_id and record.status == "member"
            ),
            None,
        )
        if not membership:
            continue
        challenge = _ensure_group_challenge(group, challenge_type)
        key = (challenge.id, user_id)
        GROUP_CHALLENGE_CONTRIBUTIONS[key] = max(
            GROUP_CHALLENGE_CONTRIBUTIONS.get(key, 0),
            contribution_count,
        )

    for group in groups:
        for challenge in _active_group_challenges(group):
            challenge.progress_count = _sum_challenge_progress(challenge.id)
            challenge.status = "completed" if challenge.progress_count >= challenge.target_count else "active"
            if challenge.status == "completed":
                badge = "Pack Builder" if challenge.type == "pack_builder" else "Clean Park Collective"
                GROUP_BADGES.setdefault(group.id, set()).add(badge)


for suburb in KNOWN_SUBURBS:
    ensure_official_group(suburb)

for seeded_group in groups:
    _active_group_challenges(seeded_group)

_seed_group_rewards()


def _membership_status(group_id: str, user_id: Optional[str]) -> str:
    if not user_id:
        return "none"
    record = next((m for m in group_memberships if m.group_id == group_id and m.user_id == user_id), None)
    if not record:
        return "none"
    return record.status


def _is_group_admin(group: Group, user_id: Optional[str]) -> bool:
    if not user_id:
        return False
    if group.owner_user_id and group.owner_user_id == user_id:
        return True
    return False


def _pending_count(group_id: str) -> int:
    return sum(1 for m in group_memberships if m.group_id == group_id and m.status == "pending")


def _event_rsvp_status(event_id: str, user_id: Optional[str]) -> str:
    if not user_id:
        return "none"
    record = next((r for r in event_rsvps if r.event_id == event_id and r.user_id == user_id), None)
    if not record:
        return "none"
    return record.status


def _invite_url(token: str, group_id: str) -> str:
    return f"barkwise://join?invite_token={token}&group_id={group_id}"


@router.get("/groups", response_model=list[GroupView])
def list_groups(
    suburb: Optional[str] = Query(default=None),
    user_id: Optional[str] = Query(default=None),
):
    if suburb:
        ensure_official_group(suburb)

    result = groups
    if suburb:
        result = [g for g in result if g.suburb.lower() == suburb.lower()]

    ranked = [_build_group_view(g, user_id=user_id) for g in result]
    ranked.sort(
        key=lambda g: (
            1 if g.membership_status == "member" else 0,
            1 if g.membership_status == "pending" else 0,
            1 if g.official else 0,
            len(g.group_badges),
            g.cooperative_score,
            g.member_count,
        ),
        reverse=True,
    )
    return ranked


@router.post("/invites", response_model=GroupInviteCreateResponse)
def create_group_invite(
    payload: GroupInviteCreateRequest,
    authorization: Optional[str] = Header(default=None),
):
    assert_actor_authorized(actor_user_id=payload.inviter_user_id, authorization=authorization)
    group = next((g for g in groups if g.id == payload.group_id), None)
    if not group:
        raise HTTPException(status_code=404, detail="Group not found")

    membership = _membership_status(group_id=group.id, user_id=payload.inviter_user_id)
    if membership != "member" and not _is_group_admin(group, payload.inviter_user_id):
        raise HTTPException(status_code=403, detail="Only members can create invite links")

    token = f"inv_{uuid4().hex[:18]}"
    expires_at_dt = datetime.now(timezone.utc) + timedelta(hours=INVITE_TTL_HOURS)
    expires_at = expires_at_dt.isoformat().replace("+00:00", "Z")
    group_invites[token] = {
        "group_id": group.id,
        "group_name": group.name,
        "suburb": group.suburb,
        "inviter_user_id": payload.inviter_user_id,
        "expires_at": expires_at,
    }
    return GroupInviteCreateResponse(
        token=token,
        group_id=group.id,
        group_name=group.name,
        suburb=group.suburb,
        inviter_user_id=payload.inviter_user_id,
        expires_at=expires_at,
        invite_url=_invite_url(token=token, group_id=group.id),
    )


@router.get("/invites/{token}", response_model=GroupInviteResolveResponse)
def resolve_group_invite(token: str):
    invite = group_invites.get(token)
    if not invite:
        raise HTTPException(status_code=404, detail="Invite not found")

    expires_at = invite["expires_at"]
    expires_at_dt = datetime.fromisoformat(expires_at.replace("Z", "+00:00"))
    if expires_at_dt <= datetime.now(timezone.utc):
        raise HTTPException(status_code=410, detail="Invite expired")

    return GroupInviteResolveResponse(
        token=token,
        group_id=invite["group_id"],
        group_name=invite["group_name"],
        suburb=invite["suburb"],
        inviter_user_id=invite["inviter_user_id"],
        expires_at=expires_at,
        invite_url=_invite_url(token=token, group_id=invite["group_id"]),
    )


@router.post("/onboarding/complete", response_model=GroupOnboardingCompleteResponse)
def complete_group_onboarding(payload: GroupOnboardingCompleteRequest):
    invite = group_invites.get(payload.invite_token)
    if not invite:
        raise HTTPException(status_code=404, detail="Invite not found")

    expires_at_dt = datetime.fromisoformat(invite["expires_at"].replace("Z", "+00:00"))
    if expires_at_dt <= datetime.now(timezone.utc):
        raise HTTPException(status_code=410, detail="Invite expired")

    group_id = invite["group_id"]
    group = next((g for g in groups if g.id == group_id), None)
    if not group:
        raise HTTPException(status_code=404, detail="Group not found")

    new_user_id = f"user_join_{uuid4().hex[:8]}"
    membership_status = "member"
    group_memberships.append(GroupJoinRecord(group_id=group_id, user_id=new_user_id, status=membership_status))
    if membership_status == "member":
        group.member_count += 1
        _apply_group_growth_reward(
            group=group,
            contributor_user_id=invite.get("inviter_user_id"),
            member_added_user_id=new_user_id,
            contribution_count=1,
        )

    created_post_id: Optional[str] = None
    if payload.share_photo_to_group:
        created_post_id = f"p_{uuid4().hex[:8]}"
        created_at = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
        photo_note = f" Photo source: {payload.photo_source}." if payload.photo_source else ""
        post = CommunityPost(
            id=created_post_id,
            type="group_post",
            created_by=new_user_id,
            title=f"Dog park check-in: {payload.dog_name.strip() or 'New pup'}",
            body=(
                f"{payload.owner_name.strip() or 'New member'} joined via invite and added "
                f"{payload.dog_name.strip() or 'their dog'} to the group roster.{photo_note}"
            ),
            suburb=_normalize_suburb(payload.suburb or invite["suburb"]),
            created_at=created_at,
            latitude=payload.latitude,
            longitude=payload.longitude,
        )
        community_posts.insert(0, _enrich_post_coordinates(post))

    inviter_user_id = invite.get("inviter_user_id")
    if inviter_user_id:
        notification_store.create(
            user_id=inviter_user_id,
            title="Pack Builder progress",
            body=f"A new member joined {group.name}. Challenge progress increased.",
            category="community",
            deep_link=f"group:{group.id}",
        )
    notification_store.create(
        user_id=new_user_id,
        title="Welcome reward unlocked",
        body=f"You earned Pack Builder points by joining {group.name}.",
        category="community",
        deep_link=f"group:{group.id}",
    )

    return GroupOnboardingCompleteResponse(
        user_id=new_user_id,
        group_id=group_id,
        membership_status=membership_status,
        created_post_id=created_post_id,
    )


@router.post("/groups", response_model=GroupView)
def create_group(payload: GroupCreateRequest, authorization: Optional[str] = Header(default=None)):
    assert_actor_authorized(actor_user_id=payload.user_id, authorization=authorization)
    suburb = _normalize_suburb(payload.suburb)
    ensure_official_group(suburb)

    existing = next(
        (
            g
            for g in groups
            if g.suburb.lower() == suburb.lower() and g.name.lower() == payload.name.strip().lower()
        ),
        None,
    )
    if existing:
        raise HTTPException(status_code=409, detail="Group with same name already exists in suburb")

    group = Group(
        id=f"g_user_{uuid4().hex[:8]}",
        name=payload.name.strip(),
        suburb=suburb,
        member_count=1,
        official=False,
        owner_user_id=payload.user_id,
    )
    groups.append(group)
    group_memberships.append(GroupJoinRecord(group_id=group.id, user_id=payload.user_id, status="member"))
    _active_group_challenges(group)
    _reward_points(group.id, payload.user_id)
    return _build_group_view(group, user_id=payload.user_id)


@router.post("/groups/{group_id}/join", response_model=GroupView)
def apply_join_group(group_id: str, payload: GroupJoinRequest, authorization: Optional[str] = Header(default=None)):
    assert_actor_authorized(actor_user_id=payload.user_id, authorization=authorization)
    group = next((g for g in groups if g.id == group_id), None)
    if not group:
        raise HTTPException(status_code=404, detail="Group not found")

    existing = next((m for m in group_memberships if m.group_id == group_id and m.user_id == payload.user_id), None)
    created_membership = False
    if existing:
        status = existing.status
    else:
        created_membership = True
        status = "member" if group.official else "pending"
        group_memberships.append(GroupJoinRecord(group_id=group_id, user_id=payload.user_id, status=status))
        if status == "member":
            group.member_count += 1
            _apply_group_growth_reward(
                group=group,
                contributor_user_id=payload.user_id,
                member_added_user_id=payload.user_id,
                contribution_count=1,
            )

    view = _build_group_view(group, user_id=payload.user_id)
    if status == "pending" and group.owner_user_id and group.owner_user_id != payload.user_id:
        notification_store.create(
            user_id=group.owner_user_id,
            title="New group join request",
            body=f"{payload.user_id} requested to join {group.name}",
            category="community",
            deep_link=f"group:{group.id}",
        )
    if created_membership and status == "member":
        notification_store.create(
            user_id=payload.user_id,
            title="Pack Builder points earned",
            body=f"You helped grow {group.name}.",
            category="community",
            deep_link=f"group:{group.id}",
        )
    return view


@router.post("/groups/{group_id}/members", response_model=GroupView)
def add_member(group_id: str, payload: GroupAddMemberRequest, authorization: Optional[str] = Header(default=None)):
    assert_actor_authorized(actor_user_id=payload.requester_user_id, authorization=authorization)
    group = next((g for g in groups if g.id == group_id), None)
    if not group:
        raise HTTPException(status_code=404, detail="Group not found")

    if group.owner_user_id != payload.requester_user_id:
        raise HTTPException(status_code=403, detail="Only group owner can add members")

    existing = next((m for m in group_memberships if m.group_id == group_id and m.user_id == payload.member_user_id), None)
    if not existing:
        group_memberships.append(GroupJoinRecord(group_id=group_id, user_id=payload.member_user_id, status="member"))
        group.member_count += 1
        _apply_group_growth_reward(
            group=group,
            contributor_user_id=payload.requester_user_id,
            member_added_user_id=payload.member_user_id,
            contribution_count=1,
        )

    return _build_group_view(group, user_id=payload.requester_user_id)


@router.get("/groups/{group_id}/join-requests", response_model=list[GroupJoinRequestView])
def list_join_requests(group_id: str, requester_user_id: str = Query(...)):
    group = next((g for g in groups if g.id == group_id), None)
    if not group:
        raise HTTPException(status_code=404, detail="Group not found")
    if not _is_group_admin(group, requester_user_id):
        raise HTTPException(status_code=403, detail="Only group admins can view requests")

    pending = [
        GroupJoinRequestView(group_id=record.group_id, user_id=record.user_id, status="pending")
        for record in group_memberships
        if record.group_id == group_id and record.status == "pending"
    ]
    return pending


@router.post("/groups/{group_id}/join-requests", response_model=GroupView)
def moderate_join_request(
    group_id: str,
    payload: GroupJoinModerationRequest,
    authorization: Optional[str] = Header(default=None),
):
    assert_actor_authorized(actor_user_id=payload.requester_user_id, authorization=authorization)
    group = next((g for g in groups if g.id == group_id), None)
    if not group:
        raise HTTPException(status_code=404, detail="Group not found")
    if not _is_group_admin(group, payload.requester_user_id):
        raise HTTPException(status_code=403, detail="Only group admins can moderate requests")

    record = next(
        (
            membership
            for membership in group_memberships
            if membership.group_id == group_id and membership.user_id == payload.member_user_id and membership.status == "pending"
        ),
        None,
    )
    if not record:
        raise HTTPException(status_code=404, detail="Pending request not found")

    if payload.action == "approve":
        record.status = "member"
        group.member_count += 1
        _apply_group_growth_reward(
            group=group,
            contributor_user_id=payload.requester_user_id,
            member_added_user_id=payload.member_user_id,
            contribution_count=1,
        )
    else:
        group_memberships.remove(record)

    view = _build_group_view(group, user_id=payload.requester_user_id)
    notification_store.create(
        user_id=payload.member_user_id,
        title="Group request updated",
        body=f"Your request for {group.name} was {'approved' if payload.action == 'approve' else 'rejected'}",
        category="community",
        deep_link=f"group:{group.id}",
    )
    return view


@router.get("/groups/{group_id}/challenges", response_model=list[GroupChallengeView])
def list_group_challenges(
    group_id: str,
    user_id: Optional[str] = Query(default=None),
):
    group = next((g for g in groups if g.id == group_id), None)
    if not group:
        raise HTTPException(status_code=404, detail="Group not found")

    challenges = _active_group_challenges(group)
    views: list[GroupChallengeView] = []
    for challenge in challenges:
        my_contribution_count = GROUP_CHALLENGE_CONTRIBUTIONS.get((challenge.id, user_id), 0) if user_id else 0
        views.append(
            GroupChallengeView(
                challenge=challenge,
                my_contribution_count=my_contribution_count,
            )
        )
    views.sort(key=lambda row: row.challenge.type)
    return views


@router.post("/groups/{group_id}/challenges/participate", response_model=GroupChallengeParticipationResult)
def participate_group_challenge(
    group_id: str,
    payload: GroupChallengeParticipationRequest,
    authorization: Optional[str] = Header(default=None),
):
    assert_actor_authorized(actor_user_id=payload.user_id, authorization=authorization)
    group = next((g for g in groups if g.id == group_id), None)
    if not group:
        raise HTTPException(status_code=404, detail="Group not found")
    if _membership_status(group_id=group_id, user_id=payload.user_id) != "member":
        raise HTTPException(status_code=403, detail="Only members can contribute to group challenges")

    challenge = _ensure_group_challenge(group, payload.challenge_type)
    if challenge.status == "completed":
        return GroupChallengeParticipationResult(
            challenge=challenge,
            my_contribution_count=GROUP_CHALLENGE_CONTRIBUTIONS.get((challenge.id, payload.user_id), 0),
            contribution_count=0,
            reward_unlocked=False,
            unlocked_badges=[],
        )

    key = (challenge.id, payload.user_id)
    previous_contribution = GROUP_CHALLENGE_CONTRIBUTIONS.get(key, 0)
    GROUP_CHALLENGE_CONTRIBUTIONS[key] = previous_contribution + payload.contribution_count

    points = _reward_points(group.id, payload.user_id)
    if payload.challenge_type == "pack_builder":
        points["pack_builder"] += payload.contribution_count
    else:
        points["clean_park"] += payload.contribution_count

    challenge.progress_count = _sum_challenge_progress(challenge.id)
    before_completed = challenge.status == "completed"
    challenge.status = "completed" if challenge.progress_count >= challenge.target_count else "active"

    unlocked_badges: list[str] = []
    if not before_completed and challenge.status == "completed":
        badge = "Pack Builder" if challenge.type == "pack_builder" else "Clean Park Collective"
        GROUP_BADGES.setdefault(group.id, set()).add(badge)
        unlocked_badges.append(badge)
        if group.owner_user_id and group.owner_user_id != payload.user_id:
            notification_store.create(
                user_id=group.owner_user_id,
                title="Group challenge completed",
                body=f"{group.name} completed {challenge.title}.",
                category="community",
                deep_link=f"group:{group.id}",
            )

    my_contribution_count = GROUP_CHALLENGE_CONTRIBUTIONS.get(key, 0)
    reward_unlocked = bool(unlocked_badges) or (my_contribution_count > 0 and my_contribution_count % 5 == 0)
    if reward_unlocked:
        notification_store.create(
            user_id=payload.user_id,
            title="Community reward unlocked",
            body=f"You earned recognition in {challenge.title}.",
            category="community",
            deep_link=f"group:{group.id}",
        )
    return GroupChallengeParticipationResult(
        challenge=challenge,
        my_contribution_count=my_contribution_count,
        contribution_count=payload.contribution_count,
        reward_unlocked=reward_unlocked,
        unlocked_badges=unlocked_badges,
    )


@router.get("/posts", response_model=list[CommunityPost])
def list_posts(
    suburb: Optional[str] = Query(default=None),
    post_type: Optional[str] = Query(default=None),
    user_id: Optional[str] = Query(default=None),
    q: Optional[str] = Query(default=None),
    sort_by: str = Query(default="relevance"),
    alert_type: Optional[str] = Query(default=None),
    alert_status: Optional[str] = Query(default=None),
    open_only: bool = Query(default=False),
    recent_hours: Optional[int] = Query(default=None, ge=1, le=720),
    center_lat: Optional[float] = Query(default=None, ge=-90, le=90),
    center_lng: Optional[float] = Query(default=None, ge=-180, le=180),
    max_distance_km: Optional[float] = Query(default=None, ge=0.1, le=200.0),
):
    _dispatch_lost_found_followups()
    result = [_enrich_post_coordinates(post) for post in community_posts]
    if post_type:
        result = [p for p in result if p.type == post_type]
    if alert_type:
        result = [p for p in result if (p.alert_type or "").lower() == alert_type.lower()]
    if alert_status:
        result = [p for p in result if (p.alert_status or "").lower() == alert_status.lower()]
    if open_only:
        result = [p for p in result if p.type != "lost_found" or (p.alert_status or "open") == "open"]
    if recent_hours:
        cutoff = _utc_now() - timedelta(hours=recent_hours)
        result = [p for p in result if _parse_created_at(p.created_at) >= cutoff]

    joined_group_suburbs: set[str] = set()
    if user_id:
        joined_group_ids = {
            membership.group_id
            for membership in group_memberships
            if membership.user_id == user_id and membership.status == "member"
        }
        joined_group_suburbs = {g.suburb.lower() for g in groups if g.id in joined_group_ids}
        blocked_user_ids = BLOCKED_USERS_BY_USER.get(user_id, set())
        if blocked_user_ids:
            result = [p for p in result if (p.created_by or "") not in blocked_user_ids]

    if (center_lat is None) != (center_lng is None):
        raise HTTPException(status_code=400, detail="center_lat and center_lng must be provided together")
    if max_distance_km is not None and (center_lat is None or center_lng is None):
        raise HTTPException(status_code=400, detail="center_lat and center_lng are required with max_distance_km")
    if max_distance_km is not None and center_lat is not None and center_lng is not None:
        filtered_by_distance: list[CommunityPost] = []
        for post in result:
            if post.latitude is None or post.longitude is None:
                continue
            distance = _haversine_km(center_lat, center_lng, post.latitude, post.longitude)
            if distance <= max_distance_km:
                filtered_by_distance.append(post)
        result = filtered_by_distance

    query = (q or "").strip().lower()
    indexed_posts = list(enumerate(result))

    def _score(item: tuple[int, CommunityPost]) -> float:
        index, post = item
        score = 0.0
        if suburb and post.suburb.lower() == suburb.lower():
            score += 4.0
        if post.suburb.lower() in joined_group_suburbs:
            score += 2.0
        if query and (
            query in post.title.lower()
            or query in post.body.lower()
            or query in (post.pet_name or "").lower()
            or query in (post.pet_traits or "").lower()
            or query in (post.last_seen_location or "").lower()
        ):
            score += 5.0
        if post.type == "lost_found":
            score += 1.5
        score += max(0.0, 2.5 - index * 0.15)
        return score

    indexed_posts.sort(key=_score, reverse=True)
    ranked = [post for _, post in indexed_posts]
    if sort_by == "newest":
        ranked = sorted(ranked, key=lambda post: _parse_created_at(post.created_at), reverse=True)
    elif sort_by == "lost_found":
        ranked = [post for post in ranked if post.type == "lost_found"]
        ranked = sorted(
            ranked,
            key=lambda post: (
                (post.alert_status or "open") == "open",
                _parse_created_at(post.created_at),
            ),
            reverse=True,
        )

    if suburb:
        # Keep suburb-only behavior available for callers expecting strict locality.
        suburb_posts = [p for p in ranked if p.suburb.lower() == suburb.lower()]
        other_posts = [p for p in ranked if p.suburb.lower() != suburb.lower()]
        ranked = suburb_posts + other_posts
    if user_id:
        event = "lost_found_feed_viewed" if sort_by == "lost_found" or post_type == "lost_found" else "community_feed_viewed"
        _record_analytics_event(
            user_id=user_id,
            event=event,
            category="lost_found" if "lost_found" in event else "community",
            metadata={
                "sort_by": sort_by,
                "post_type": post_type or "",
                "result_count": len(ranked),
            },
        )
    return ranked


@router.post("/posts", response_model=CommunityPost)
def create_post(payload: CommunityPostCreate, authorization: Optional[str] = Header(default=None)):
    actor_user_id = _resolve_actor_user(
        authorization=authorization,
        explicit_user_id=payload.user_id,
        require_user=False,
    )
    actor_user_id = actor_user_id or (payload.user_id.strip() if payload.user_id else "") or "guest_user"
    assert_actor_authorized(actor_user_id=actor_user_id, authorization=authorization)
    _check_post_rate_limit(actor_user_id)
    _validate_lost_found_create(payload)
    _record_analytics_event(
        user_id=actor_user_id,
        event="lost_found_create_attempted" if payload.type == "lost_found" else "community_post_create_attempted",
        category="lost_found" if payload.type == "lost_found" else "community",
    )

    now = _utc_now().isoformat().replace("+00:00", "Z")
    normalized_suburb = _normalize_suburb(payload.suburb)
    normalized_photo_urls = [url.strip() for url in payload.photo_urls if url and url.strip()][:6]

    if payload.type == "lost_found":
        alert_type = payload.alert_type or _infer_alert_type(payload.title, payload.body)
        follow_up_due_at = (_utc_now() + timedelta(hours=12)).isoformat().replace("+00:00", "Z")
        expires_at = (_utc_now() + timedelta(hours=72)).isoformat().replace("+00:00", "Z")
        post = CommunityPost(
            id=f"p_{uuid4().hex[:8]}",
            type=payload.type,
            created_by=actor_user_id,
            title=payload.title.strip(),
            body=payload.body.strip(),
            suburb=normalized_suburb,
            created_at=now,
            alert_type=alert_type,  # type: ignore[arg-type]
            alert_status="open",
            pet_name=payload.pet_name.strip() if payload.pet_name else None,
            pet_traits=payload.pet_traits.strip() if payload.pet_traits else None,
            last_seen_at=payload.last_seen_at,
            last_seen_location=payload.last_seen_location.strip() if payload.last_seen_location else None,
            contact_pref=payload.contact_pref.strip() if payload.contact_pref else None,
            photo_urls=normalized_photo_urls,
            latitude=payload.latitude,
            longitude=payload.longitude,
            resolved_at=None,
            resolved_note=None,
            follow_up_due_at=follow_up_due_at,
            expires_at=expires_at,
        )
        inserted = _enrich_post_coordinates(post)
        community_posts.insert(0, inserted)
        _send_nearby_lost_found_notifications(post=inserted)
        _record_analytics_event(
            user_id=actor_user_id,
            event="lost_found_create_succeeded",
            category="lost_found",
            metadata={"post_id": inserted.id, "alert_type": inserted.alert_type or "lost"},
        )
        return inserted

    post = CommunityPost(
        id=f"p_{uuid4().hex[:8]}",
        type=payload.type,
        created_by=actor_user_id,
        title=payload.title.strip(),
        body=payload.body.strip(),
        suburb=normalized_suburb,
        created_at=now,
        alert_type=None,
        alert_status=None,
        pet_name=None,
        pet_traits=None,
        last_seen_at=None,
        last_seen_location=None,
        contact_pref=None,
        photo_urls=normalized_photo_urls,
        latitude=payload.latitude,
        longitude=payload.longitude,
        resolved_at=None,
        resolved_note=None,
        follow_up_due_at=None,
        expires_at=None,
    )
    inserted = _enrich_post_coordinates(post)
    community_posts.insert(0, inserted)
    _record_analytics_event(
        user_id=actor_user_id,
        event="community_post_create_succeeded",
        category="community",
        metadata={"post_id": inserted.id},
    )
    return inserted


@router.post("/posts/{post_id}/resolve", response_model=CommunityPost)
def resolve_post(post_id: str, payload: CommunityPostResolveRequest, authorization: Optional[str] = Header(default=None)):
    actor_user_id = _resolve_actor_user(
        authorization=authorization,
        explicit_user_id=payload.requester_user_id,
        require_user=True,
    )
    assert actor_user_id is not None
    assert_actor_authorized(actor_user_id=actor_user_id, authorization=authorization)
    index = next((i for i, post in enumerate(community_posts) if post.id == post_id), None)
    if index is None:
        raise HTTPException(status_code=404, detail="Post not found")

    post = community_posts[index]
    if post.type != "lost_found":
        raise HTTPException(status_code=400, detail="Only lost/found posts can be resolved")
    _ensure_post_owner(post, actor_user_id)
    current_status = post.alert_status or "open"
    if current_status != "open":
        raise HTTPException(status_code=409, detail=f"Only open alerts can be resolved. Current status: {current_status}")
    if (post.alert_type or "lost") == "lost" and payload.status == "owner_found":
        raise HTTPException(status_code=400, detail="Lost alerts can only resolve to reunited or expired")
    if (post.alert_type or "lost") == "found" and payload.status == "reunited":
        raise HTTPException(status_code=400, detail="Found alerts can only resolve to owner_found or expired")

    updated = post.model_copy(
        update={
            "alert_status": payload.status,
            "resolved_at": _utc_now().isoformat().replace("+00:00", "Z"),
            "resolved_note": payload.note.strip() or None,
        }
    )
    community_posts[index] = updated
    _record_analytics_event(
        user_id=actor_user_id,
        event="lost_found_resolved",
        category="lost_found",
        metadata={"post_id": post.id, "status": payload.status},
    )
    if post.created_by:
        notification_store.create(
            user_id=post.created_by,
            title="Alert updated",
            body=f"Your alert \"{post.title}\" was marked {payload.status.replace('_', ' ')}.",
            category="community",
            deep_link=f"post:{post.id}",
        )
    return updated


@router.patch("/posts/{post_id}", response_model=CommunityPost)
def update_post(post_id: str, payload: CommunityPostUpdateRequest, authorization: Optional[str] = Header(default=None)):
    actor_user_id = _resolve_actor_user(
        authorization=authorization,
        explicit_user_id=payload.requester_user_id,
        require_user=True,
    )
    assert actor_user_id is not None
    assert_actor_authorized(actor_user_id=actor_user_id, authorization=authorization)
    index = next((i for i, row in enumerate(community_posts) if row.id == post_id), None)
    if index is None:
        raise HTTPException(status_code=404, detail="Post not found")
    current = community_posts[index]
    _ensure_post_owner(current, actor_user_id)

    update: Dict[str, Any] = {}
    if payload.title is not None:
        clean = payload.title.strip()
        if not clean:
            raise HTTPException(status_code=400, detail="title cannot be blank")
        update["title"] = clean
    if payload.body is not None:
        clean = payload.body.strip()
        if not clean:
            raise HTTPException(status_code=400, detail="body cannot be blank")
        update["body"] = clean
    if payload.pet_name is not None:
        update["pet_name"] = payload.pet_name.strip() or None
    if payload.pet_traits is not None:
        update["pet_traits"] = payload.pet_traits.strip() or None
    if payload.last_seen_location is not None:
        update["last_seen_location"] = payload.last_seen_location.strip() or None
    if payload.contact_pref is not None:
        update["contact_pref"] = payload.contact_pref.strip() or None
    if payload.clear_last_seen_at:
        update["last_seen_at"] = None
    elif payload.last_seen_at is not None:
        update["last_seen_at"] = payload.last_seen_at.strip() or None
    if payload.photo_urls is not None:
        update["photo_urls"] = [item.strip() for item in payload.photo_urls if item and item.strip()][:6]

    if payload.latitude is not None and payload.longitude is None:
        raise HTTPException(status_code=400, detail="longitude is required when latitude is provided")
    if payload.longitude is not None and payload.latitude is None:
        raise HTTPException(status_code=400, detail="latitude is required when longitude is provided")
    if payload.latitude is not None and payload.longitude is not None:
        update["latitude"] = payload.latitude
        update["longitude"] = payload.longitude

    updated = current.model_copy(update=update)
    if updated.type == "lost_found":
        if not (updated.pet_traits or "").strip():
            raise HTTPException(status_code=400, detail="pet_traits is required for lost/found alerts")
        if not (updated.last_seen_location or "").strip():
            raise HTTPException(status_code=400, detail="last_seen_location is required for lost/found alerts")
        if not (updated.contact_pref or "").strip():
            raise HTTPException(status_code=400, detail="contact_pref is required for lost/found alerts")
    enriched = _enrich_post_coordinates(updated)
    community_posts[index] = enriched
    return enriched


@router.delete("/posts/{post_id}", response_model=dict)
def delete_post(
    post_id: str,
    requester_user_id: Optional[str] = Query(default=None),
    authorization: Optional[str] = Header(default=None),
):
    actor_user_id = _resolve_actor_user(
        authorization=authorization,
        explicit_user_id=requester_user_id,
        require_user=True,
    )
    assert actor_user_id is not None
    assert_actor_authorized(actor_user_id=actor_user_id, authorization=authorization)
    index = next((i for i, row in enumerate(community_posts) if row.id == post_id), None)
    if index is None:
        raise HTTPException(status_code=404, detail="Post not found")
    current = community_posts[index]
    _ensure_post_owner(current, actor_user_id)
    community_posts.pop(index)
    return {"status": "deleted", "post_id": post_id}


@router.post("/posts/uploads", response_model=CommunityPostPhotoUploadResponse)
def upload_post_photo(payload: CommunityPostPhotoUploadRequest, authorization: Optional[str] = Header(default=None)):
    actor_user_id = _resolve_actor_user(
        authorization=authorization,
        explicit_user_id=payload.requester_user_id,
        require_user=True,
    )
    assert actor_user_id is not None
    assert_actor_authorized(actor_user_id=actor_user_id, authorization=authorization)

    content_type = payload.content_type.strip().lower()
    if not content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="Only image uploads are supported")
    try:
        data = base64.b64decode(payload.data_base64.encode("utf-8"), validate=True)
    except Exception as exc:  # pragma: no cover - defensive parsing branch
        raise HTTPException(status_code=400, detail="Invalid base64 image payload") from exc
    size = len(data)
    if size <= 0:
        raise HTTPException(status_code=400, detail="Empty upload")
    if size > 5 * 1024 * 1024:
        raise HTTPException(status_code=413, detail="Image upload exceeds 5MB")

    ext = Path(payload.filename or "").suffix.lower()
    if ext not in {".jpg", ".jpeg", ".png", ".webp", ".heic"}:
        ext = ".jpg" if "jpeg" in content_type or "jpg" in content_type else ".png"
    UPLOAD_ROOT.mkdir(parents=True, exist_ok=True)
    filename = f"{actor_user_id}_{uuid4().hex[:12]}{ext}"
    target = UPLOAD_ROOT / filename
    target.write_bytes(data)
    return CommunityPostPhotoUploadResponse(
        url=f"/web/uploads/lost_found/{filename}",
        content_type=content_type,
        size_bytes=size,
    )


@router.post("/moderation/reports", response_model=CommunityReport)
def create_moderation_report(payload: CommunityReportCreateRequest, authorization: Optional[str] = Header(default=None)):
    assert_actor_authorized(actor_user_id=payload.reporter_user_id, authorization=authorization)
    if payload.target_type == "post":
        post = next((row for row in community_posts if row.id == payload.target_id), None)
        if not post:
            raise HTTPException(status_code=404, detail="Post not found")
    if payload.target_type == "user" and payload.target_id == payload.reporter_user_id:
        raise HTTPException(status_code=400, detail="Cannot report yourself")

    report = CommunityReport(
        id=f"rep_{uuid4().hex[:10]}",
        reporter_user_id=payload.reporter_user_id,
        target_type=payload.target_type,
        target_id=payload.target_id,
        reason=payload.reason.strip(),
        details=payload.details.strip(),
        status="pending",
        created_at=_utc_now().isoformat().replace("+00:00", "Z"),
    )
    COMMUNITY_REPORTS.insert(0, report)
    _record_analytics_event(
        user_id=payload.reporter_user_id,
        event="moderation_report_submitted",
        category="community",
        metadata={"target_type": payload.target_type, "target_id": payload.target_id},
    )
    return report


@router.get("/moderation/reports", response_model=list[CommunityReport])
def list_moderation_reports(
    requester_user_id: str = Query(...),
    include_resolved: bool = Query(default=False),
    authorization: Optional[str] = Header(default=None),
):
    assert_actor_authorized(actor_user_id=requester_user_id, authorization=authorization)
    if requester_user_id not in ADMIN_USER_IDS:
        raise HTTPException(status_code=403, detail="Only moderators can view report queue")
    if include_resolved:
        return COMMUNITY_REPORTS[:200]
    return [item for item in COMMUNITY_REPORTS[:200] if item.status == "pending"]


@router.post("/moderation/reports/{report_id}/resolve", response_model=CommunityReport)
def resolve_moderation_report(
    report_id: str,
    payload: CommunityReportResolveRequest,
    authorization: Optional[str] = Header(default=None),
):
    assert_actor_authorized(actor_user_id=payload.requester_user_id, authorization=authorization)
    if payload.requester_user_id not in ADMIN_USER_IDS:
        raise HTTPException(status_code=403, detail="Only moderators can resolve reports")
    index = next((i for i, report in enumerate(COMMUNITY_REPORTS) if report.id == report_id), None)
    if index is None:
        raise HTTPException(status_code=404, detail="Report not found")
    report = COMMUNITY_REPORTS[index]
    resolved = report.model_copy(
        update={
            "status": payload.action,
            "resolved_at": _utc_now().isoformat().replace("+00:00", "Z"),
            "resolved_by": payload.requester_user_id,
            "resolution_note": payload.note.strip() or None,
        }
    )
    COMMUNITY_REPORTS[index] = resolved
    return resolved


@router.post("/moderation/blocks", response_model=CommunityBlockUserResponse)
def block_user(payload: CommunityBlockUserRequest, authorization: Optional[str] = Header(default=None)):
    assert_actor_authorized(actor_user_id=payload.requester_user_id, authorization=authorization)
    if payload.requester_user_id == payload.target_user_id:
        raise HTTPException(status_code=400, detail="Cannot block yourself")
    blocked = BLOCKED_USERS_BY_USER.setdefault(payload.requester_user_id, set())
    blocked.add(payload.target_user_id)
    _record_analytics_event(
        user_id=payload.requester_user_id,
        event="community_block_submitted",
        category="community",
        metadata={"target_user_id": payload.target_user_id},
    )
    return CommunityBlockUserResponse(
        requester_user_id=payload.requester_user_id,
        blocked_user_ids=sorted(blocked),
    )


@router.delete("/moderation/blocks", response_model=CommunityBlockUserResponse)
def unblock_user(
    requester_user_id: str = Query(...),
    target_user_id: str = Query(...),
    authorization: Optional[str] = Header(default=None),
):
    assert_actor_authorized(actor_user_id=requester_user_id, authorization=authorization)
    blocked = BLOCKED_USERS_BY_USER.setdefault(requester_user_id, set())
    blocked.discard(target_user_id)
    return CommunityBlockUserResponse(
        requester_user_id=requester_user_id,
        blocked_user_ids=sorted(blocked),
    )


@router.get("/moderation/blocks", response_model=CommunityBlockUserResponse)
def list_blocks(
    requester_user_id: str = Query(...),
    authorization: Optional[str] = Header(default=None),
):
    assert_actor_authorized(actor_user_id=requester_user_id, authorization=authorization)
    blocked = BLOCKED_USERS_BY_USER.get(requester_user_id, set())
    return CommunityBlockUserResponse(
        requester_user_id=requester_user_id,
        blocked_user_ids=sorted(blocked),
    )


@router.post("/analytics/events", response_model=dict)
def create_analytics_event(payload: CommunityAnalyticsEventCreateRequest, authorization: Optional[str] = Header(default=None)):
    assert_actor_authorized(actor_user_id=payload.user_id, authorization=authorization)
    _record_analytics_event(
        user_id=payload.user_id,
        event=payload.event,
        category=payload.category,
        metadata=payload.metadata,
        duration_ms=payload.duration_ms,
    )
    return {"status": "ok"}


@router.get("/analytics/funnel", response_model=CommunityFunnelMetrics)
def get_analytics_funnel(
    requester_user_id: Optional[str] = Query(default=None),
    window_hours: int = Query(default=168, ge=1, le=720),
):
    cutoff = _utc_now() - timedelta(hours=window_hours)
    recent = [row for row in COMMUNITY_ANALYTICS_EVENTS if row["created_at"] >= cutoff]

    def _count(event_name: str) -> int:
        return sum(1 for row in recent if row.get("event") == event_name)

    create_attempts = _count("lost_found_create_attempted")
    create_successes = _count("lost_found_create_succeeded")
    conversion_pct = round((create_successes / create_attempts) * 100.0, 2) if create_attempts else 0.0
    return CommunityFunnelMetrics(
        window_hours=window_hours,
        community_feed_views=_count("community_feed_viewed"),
        lost_found_feed_views=_count("lost_found_feed_viewed"),
        lost_found_create_attempts=create_attempts,
        lost_found_create_successes=create_successes,
        lost_found_resolution_actions=_count("lost_found_resolved"),
        moderation_reports_submitted=_count("moderation_report_submitted"),
        blocks_submitted=_count("community_block_submitted"),
        lost_found_create_conversion_pct=conversion_pct,
    )


@router.post("/diagnostics/events", response_model=dict)
def create_diagnostic_event(payload: CommunityDiagnosticEventCreateRequest, authorization: Optional[str] = Header(default=None)):
    assert_actor_authorized(actor_user_id=payload.user_id, authorization=authorization)
    _record_diagnostic_event(
        user_id=payload.user_id,
        kind=payload.kind,
        message=payload.message,
        context=payload.context,
        duration_ms=payload.duration_ms,
    )
    return {"status": "ok"}


@router.get("/events", response_model=list[CommunityEventView])
def list_events(
    suburb: Optional[str] = Query(default=None),
    user_id: Optional[str] = Query(default=None),
):
    result = community_events
    if suburb:
        result = [event for event in result if event.suburb.lower() == suburb.lower()]

    visible: list[CommunityEvent] = []
    for event in result:
        if event.status == "approved":
            visible.append(event)
            continue
        if user_id and event.created_by == user_id:
            visible.append(event)
            continue
        if user_id and event.group_id:
            group = next((g for g in groups if g.id == event.group_id), None)
            if group and _is_group_admin(group, user_id):
                visible.append(event)
    views = [CommunityEventView(**event.model_dump(), rsvp_status=_event_rsvp_status(event.id, user_id)) for event in visible]
    views.sort(
        key=lambda event: (
            1 if event.rsvp_status == "attending" else 0,
            event.attendee_count,
            event.date,
        ),
        reverse=True,
    )
    return views


@router.post("/events", response_model=CommunityEventView)
def create_event(payload: CommunityEventCreateRequest, authorization: Optional[str] = Header(default=None)):
    assert_actor_authorized(actor_user_id=payload.user_id, authorization=authorization)
    suburb = _normalize_suburb(payload.suburb)
    group = None
    if payload.group_id and not next((g for g in groups if g.id == payload.group_id), None):
        raise HTTPException(status_code=404, detail="Group not found")
    if payload.group_id:
        group = next((g for g in groups if g.id == payload.group_id), None)

    status = "approved"
    if group and not _is_group_admin(group, payload.user_id):
        status = "pending_approval"
    event = CommunityEvent(
        id=f"evt_{uuid4().hex[:8]}",
        title=payload.title.strip(),
        description=payload.description.strip(),
        suburb=suburb,
        date=payload.date.strip(),
        group_id=payload.group_id,
        attendee_count=1,
        created_by=payload.user_id,
        status=status,
    )
    community_events.insert(0, event)
    if status == "approved":
        event_rsvps.append(EventRsvpRecord(event_id=event.id, user_id=payload.user_id, status="attending"))
    view = CommunityEventView(
        **event.model_dump(),
        rsvp_status="attending" if status == "approved" else "none",
    )
    if event.group_id:
        group = next((g for g in groups if g.id == event.group_id), None)
        if group and group.owner_user_id and group.owner_user_id != payload.user_id:
            notification_store.create(
                user_id=group.owner_user_id,
                title="Event awaiting approval",
                body=f"{payload.user_id} created {event.title}",
                category="community",
                deep_link=f"event:{event.id}",
            )
    return view


@router.post("/events/{event_id}/rsvp", response_model=CommunityEventView)
def rsvp_event(event_id: str, payload: CommunityEventRsvpRequest, authorization: Optional[str] = Header(default=None)):
    assert_actor_authorized(actor_user_id=payload.user_id, authorization=authorization)
    event = next((e for e in community_events if e.id == event_id), None)
    if not event:
        raise HTTPException(status_code=404, detail="Event not found")

    existing = next((r for r in event_rsvps if r.event_id == event_id and r.user_id == payload.user_id), None)

    if payload.status == "attending":
        if not existing:
            event_rsvps.append(EventRsvpRecord(event_id=event_id, user_id=payload.user_id, status="attending"))
            event.attendee_count += 1
    else:
        if existing:
            event_rsvps.remove(existing)
            event.attendee_count = max(0, event.attendee_count - 1)

    view = CommunityEventView(
        **event.model_dump(),
        rsvp_status=_event_rsvp_status(event.id, payload.user_id),
    )
    if event.created_by != payload.user_id:
        notification_store.create(
            user_id=event.created_by,
            title="Event RSVP update",
            body=f"{payload.user_id} is now {payload.status} for {event.title}",
            category="community",
            deep_link=f"event:{event.id}",
        )
    return view


@router.post("/events/{event_id}/approve", response_model=CommunityEventView)
def approve_event(
    event_id: str,
    requester_user_id: str = Query(...),
    authorization: Optional[str] = Header(default=None),
):
    assert_actor_authorized(actor_user_id=requester_user_id, authorization=authorization)
    event = next((e for e in community_events if e.id == event_id), None)
    if not event:
        raise HTTPException(status_code=404, detail="Event not found")
    if not event.group_id:
        raise HTTPException(status_code=400, detail="Only group events need approval")
    group = next((g for g in groups if g.id == event.group_id), None)
    if not group:
        raise HTTPException(status_code=404, detail="Group not found")
    if not _is_group_admin(group, requester_user_id):
        raise HTTPException(status_code=403, detail="Only group admins can approve events")

    event.status = "approved"
    if not next((r for r in event_rsvps if r.event_id == event.id and r.user_id == event.created_by), None):
        event_rsvps.append(EventRsvpRecord(event_id=event.id, user_id=event.created_by, status="attending"))
    view = CommunityEventView(
        **event.model_dump(),
        rsvp_status=_event_rsvp_status(event.id, requester_user_id),
    )
    if event.created_by != requester_user_id:
        notification_store.create(
            user_id=event.created_by,
            title="Event approved",
            body=f"{event.title} is now live",
            category="community",
            deep_link=f"event:{event.id}",
        )
    return view
    if event.status != "approved":
        raise HTTPException(status_code=400, detail="Event is pending approval")
