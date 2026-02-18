from datetime import datetime, timedelta, timezone
from typing import Optional
from uuid import uuid4

from fastapi import APIRouter, Header, HTTPException, Query

from app.auth import assert_actor_authorized
from app.data import KNOWN_SUBURBS, community_events, community_posts, event_rsvps, group_invites, group_memberships, groups
from app.models import (
    CommunityEvent,
    CommunityEventCreateRequest,
    CommunityEventRsvpRequest,
    CommunityEventView,
    CommunityPost,
    CommunityPostCreate,
    EventRsvpRecord,
    Group,
    GroupAddMemberRequest,
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


def _normalize_suburb(suburb: str) -> str:
    return " ".join(suburb.strip().split()).title()


def ensure_official_group(suburb: str) -> Group:
    normalized = _normalize_suburb(suburb)
    existing = next((g for g in groups if g.official and g.suburb.lower() == normalized.lower()), None)
    if existing:
        return existing

    group = Group(
        id=f"g_official_{uuid4().hex[:8]}",
        name=f"{normalized} Official Pet Community",
        suburb=normalized,
        member_count=0,
        official=True,
    )
    groups.append(group)
    return group


for suburb in KNOWN_SUBURBS:
    ensure_official_group(suburb)


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

    ranked = [
        GroupView(
            id=g.id,
            name=g.name,
            suburb=g.suburb,
            member_count=g.member_count,
            official=g.official,
            owner_user_id=g.owner_user_id,
            membership_status=_membership_status(g.id, user_id),
            is_admin=_is_group_admin(g, user_id),
            pending_request_count=_pending_count(g.id),
        )
        for g in result
    ]
    ranked.sort(
        key=lambda g: (
            1 if g.membership_status == "member" else 0,
            1 if g.membership_status == "pending" else 0,
            1 if g.official else 0,
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

    created_post_id: Optional[str] = None
    if payload.share_photo_to_group:
        created_post_id = f"p_{uuid4().hex[:8]}"
        created_at = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
        photo_note = f" Photo source: {payload.photo_source}." if payload.photo_source else ""
        post = CommunityPost(
            id=created_post_id,
            type="group_post",
            title=f"Dog park check-in: {payload.dog_name.strip() or 'New pup'}",
            body=(
                f"{payload.owner_name.strip() or 'New member'} joined via invite and added "
                f"{payload.dog_name.strip() or 'their dog'} to the group roster.{photo_note}"
            ),
            suburb=_normalize_suburb(payload.suburb or invite["suburb"]),
            created_at=created_at,
        )
        community_posts.insert(0, post)

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

    return GroupView(
        **group.model_dump(),
        membership_status="member",
        is_admin=True,
        pending_request_count=_pending_count(group.id),
    )


@router.post("/groups/{group_id}/join", response_model=GroupView)
def apply_join_group(group_id: str, payload: GroupJoinRequest, authorization: Optional[str] = Header(default=None)):
    assert_actor_authorized(actor_user_id=payload.user_id, authorization=authorization)
    group = next((g for g in groups if g.id == group_id), None)
    if not group:
        raise HTTPException(status_code=404, detail="Group not found")

    existing = next((m for m in group_memberships if m.group_id == group_id and m.user_id == payload.user_id), None)
    if existing:
        status = existing.status
    else:
        status = "member" if group.official else "pending"
        group_memberships.append(GroupJoinRecord(group_id=group_id, user_id=payload.user_id, status=status))
        if status == "member":
            group.member_count += 1

    view = GroupView(
        **group.model_dump(),
        membership_status=status,
        is_admin=_is_group_admin(group, payload.user_id),
        pending_request_count=_pending_count(group.id),
    )
    if status == "pending" and group.owner_user_id and group.owner_user_id != payload.user_id:
        notification_store.create(
            user_id=group.owner_user_id,
            title="New group join request",
            body=f"{payload.user_id} requested to join {group.name}",
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

    return GroupView(
        **group.model_dump(),
        membership_status=_membership_status(group.id, payload.requester_user_id),
        is_admin=_is_group_admin(group, payload.requester_user_id),
        pending_request_count=_pending_count(group.id),
    )


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
    else:
        group_memberships.remove(record)

    view = GroupView(
        **group.model_dump(),
        membership_status=_membership_status(group.id, payload.requester_user_id),
        is_admin=True,
        pending_request_count=_pending_count(group.id),
    )
    notification_store.create(
        user_id=payload.member_user_id,
        title="Group request updated",
        body=f"Your request for {group.name} was {'approved' if payload.action == 'approve' else 'rejected'}",
        category="community",
        deep_link=f"group:{group.id}",
    )
    return view


@router.get("/posts")
def list_posts(
    suburb: Optional[str] = Query(default=None),
    post_type: Optional[str] = Query(default=None),
    user_id: Optional[str] = Query(default=None),
    q: Optional[str] = Query(default=None),
    sort_by: str = Query(default="relevance"),
):
    result = community_posts[:]
    if post_type:
        result = [p for p in result if p.type == post_type]

    joined_group_suburbs: set[str] = set()
    if user_id:
        joined_group_ids = {
            membership.group_id
            for membership in group_memberships
            if membership.user_id == user_id and membership.status == "member"
        }
        joined_group_suburbs = {g.suburb.lower() for g in groups if g.id in joined_group_ids}

    query = (q or "").strip().lower()
    indexed_posts = list(enumerate(result))

    def _score(item: tuple[int, CommunityPost]) -> float:
        index, post = item
        score = 0.0
        if suburb and post.suburb.lower() == suburb.lower():
            score += 4.0
        if post.suburb.lower() in joined_group_suburbs:
            score += 2.0
        if query and (query in post.title.lower() or query in post.body.lower()):
            score += 5.0
        if post.type == "lost_found":
            score += 1.5
        score += max(0.0, 2.5 - index * 0.15)
        return score

    indexed_posts.sort(key=_score, reverse=True)
    ranked = [post for _, post in indexed_posts]
    if sort_by == "newest":
        # Newest approximation for in-memory seed data.
        ranked = list(reversed(ranked))
    elif sort_by == "lost_found":
        ranked = sorted(ranked, key=lambda post: (post.type != "lost_found", post.title))

    if suburb:
        # Keep suburb-only behavior available for callers expecting strict locality.
        suburb_posts = [p for p in ranked if p.suburb.lower() == suburb.lower()]
        other_posts = [p for p in ranked if p.suburb.lower() != suburb.lower()]
        return suburb_posts + other_posts
    return ranked


@router.post("/posts", response_model=CommunityPost)
def create_post(payload: CommunityPostCreate, authorization: Optional[str] = Header(default=None)):
    # Keep post creation permissive for now; auth enforcement is done on user-bound actions.
    post = CommunityPost(
        id=f"p_{uuid4().hex[:8]}",
        type=payload.type,
        title=payload.title,
        body=payload.body,
        suburb=_normalize_suburb(payload.suburb),
        created_at=None,
    )
    community_posts.insert(0, post)
    return post


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
