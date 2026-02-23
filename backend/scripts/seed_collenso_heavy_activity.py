#!/usr/bin/env python3
import argparse
import json
import random
from datetime import datetime, timedelta, timezone
from typing import Any, Optional

from api_bot_lib import ApiClient, ApiResult


GROUP_NAME_DEFAULT = "Collenso Dog Park"
SUBURB_DEFAULT = "Sunshine West"
OWNER_USER_DEFAULT = "annika"
TEST_USER_DEFAULT = "user_2"
PASSWORD_DEFAULT = "petsocial-demo"

PHOTO_LIBRARY = [
    "https://images.unsplash.com/photo-1518717758536-85ae29035b6d",
    "https://images.unsplash.com/photo-1548199973-03cce0bbc87b",
    "https://images.unsplash.com/photo-1507146426996-ef05306b995a",
    "https://images.unsplash.com/photo-1530281700549-e82e7bf110d6",
    "https://images.unsplash.com/photo-1522276498395-f4f68f7f8454",
    "https://images.unsplash.com/photo-1543466835-00a7907e9de1",
    "https://images.unsplash.com/photo-1517423440428-a5a00ad493e8",
    "https://images.unsplash.com/photo-1516734212186-65266f4f17c8",
    "https://images.unsplash.com/photo-1537151608828-ea2b11777ee8",
]

DOG_PERSONAS: dict[str, dict[str, str]] = {
    "snowy": {
        "dog_name": "Snowy",
        "breed": "black and white bull arab",
        "style": (
            "Started timid and then launched into chase play, still misjudging his size and clipping smaller dogs."
        ),
    },
    "sesame": {
        "dog_name": "Sesame",
        "breed": "brown and black border collie/poodle cross with a schnauzer-like face",
        "style": (
            "Dominated fetch sprints and became defensive when another dog threatened her ball."
        ),
    },
    "annika": {
        "dog_name": "Annika",
        "breed": "black golden retriever/poodle cross",
        "style": "Goofy and always friendly, turning every greeting into a playful bounce session.",
    },
    "pepsi": {
        "dog_name": "Pepsi",
        "breed": "brown/tan staffie-jack russell cross",
        "style": "Played physically, started wary of men, then warmed up after calm repeated greetings.",
    },
    "billie": {
        "dog_name": "Billie",
        "breed": "tan boxer cross",
        "style": "Older but still a ball of love, mixing gentle check-ins with short energetic bursts.",
    },
    "buddy": {
        "dog_name": "Buddy",
        "breed": "mostly black cavoodle with white spots",
        "style": "Social with most dogs but repeatedly escalated with Sesame until handlers separated both.",
    },
}

PERSONA_THREAD_COMMENTS: dict[str, list[str]] = {
    "snowy": [
        "Thread comment: Snowy looked brave today after a cautious start.",
        "Thread comment: Please keep tighter chase spacing so he does not accidentally trample smaller dogs.",
        "Thread comment: Great confidence gains with short pause-and-reset loops.",
    ],
    "sesame": [
        "Thread comment: Sesame had elite fetch focus and still needs clear ball boundaries.",
        "Thread comment: If Buddy approaches her ball, handlers should split toy zones fast.",
        "Thread comment: Good work de-escalating with short leash resets.",
    ],
    "annika": [
        "Thread comment: Annika stayed goofy and friendly with every dog in the pack.",
        "Thread comment: She kept inviting nervous dogs into calmer bounce play.",
        "Thread comment: Photo three is peak Annika energy.",
    ],
    "pepsi": [
        "Thread comment: Pepsi played tough but regulated better after slower greetings.",
        "Thread comment: Men should approach side-on first; he settled faster that way.",
        "Thread comment: Strong session once trust was established.",
    ],
    "billie": [
        "Thread comment: Billie is still the warmest greeter in the group.",
        "Thread comment: Love how she alternates soft social checks with short play bursts.",
        "Thread comment: Senior queen behavior, no notes.",
    ],
    "buddy": [
        "Thread comment: Buddy had great social moments outside the Sesame ball conflict loop.",
        "Thread comment: Buddy and Sesame escalated twice and needed a full separation/reset.",
        "Thread comment: Better outcomes when handlers rotate both dogs through calm decompression.",
    ],
}

SPICY_SCENARIOS: list[dict[str, Any]] = [
    {
        "title": "Collenso spicy thread: toy dispute escalated",
        "body": (
            "Sesame and Buddy escalated around a ball when one handler ignored the agreed toy boundary rule. "
            "Raised voices from owners made the situation worse before a calm reset team stepped in."
        ),
        "reason": "Unsafe handling escalation",
    },
    {
        "title": "Collenso spicy thread: rough play crossed the line",
        "body": (
            "Pepsi and a new dog pushed rough play too far while one owner encouraged chest-bumping after warnings. "
            "Several members asked for stricter intervention before re-entry."
        ),
        "reason": "Unsafe rough play encouragement",
    },
    {
        "title": "Collenso spicy thread: repeated off-leash recall failures",
        "body": (
            "A visitor repeatedly ignored recall requests and let their dog barge into shy dogs, including Snowy. "
            "Members requested moderation follow-up for repeat boundary breaches."
        ),
        "reason": "Repeated boundary violations",
    },
    {
        "title": "Collenso spicy thread: heated owner argument",
        "body": (
            "Two owners argued publicly after a trample incident during high-speed chase. "
            "Group volunteers de-escalated, but several members requested community guideline reminders."
        ),
        "reason": "Hostile behavior in group area",
    },
]

THREAD_THEMES = [
    (
        "Live thread",
        "Live thread from Collenso Dog Park: water-bowl relay happening now, rotating dog pairs every 10 minutes.",
    ),
    (
        "Cleanup thread",
        "Cleanup thread: handlers did a rapid sweep, reset toy zones, and reduced conflict in under 15 minutes.",
    ),
    (
        "Then vs now thread",
        "Then vs now thread: compared first-week behavior to current sessions and saw major confidence gains.",
    ),
    (
        "Day report thread",
        "Day report thread: structured play blocks, decompression loops, and short confidence resets between rounds.",
    ),
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Seed Collenso Dog Park with heavy social activity for test realism.")
    parser.add_argument("--base-url", default="http://127.0.0.1:8000", help="API base URL.")
    parser.add_argument("--group-name", default=GROUP_NAME_DEFAULT, help="Community group name.")
    parser.add_argument("--suburb", default=SUBURB_DEFAULT, help="Community suburb.")
    parser.add_argument("--owner-user", default=OWNER_USER_DEFAULT, help="Group owner user id.")
    parser.add_argument("--test-user", default=TEST_USER_DEFAULT, help="Primary test account to ensure is a member.")
    parser.add_argument("--password", default=PASSWORD_DEFAULT, help="Password for all users.")
    parser.add_argument(
        "--users",
        default=(
            "annika,snowy,sesame,pepsi,billie,buddy,user_2,"
            "collenso_guest_1,collenso_guest_2,collenso_guest_3,collenso_guest_4,collenso_guest_5,collenso_guest_6"
        ),
        help="Comma-separated users to seed as active participants.",
    )
    parser.add_argument("--posts-per-user", type=int, default=3, help="Group posts per user.")
    parser.add_argument("--events", type=int, default=6, help="Collenso events to create.")
    parser.add_argument("--rsvps-per-event", type=int, default=5, help="Random attendees per event.")
    parser.add_argument("--spicy-posts", type=int, default=3, help="Extra spicy interaction posts to seed.")
    parser.add_argument(
        "--seed-moderation-reports",
        type=int,
        default=1,
        help="Set to 1 to auto-create pending moderation reports for spicy posts, 0 to skip.",
    )
    parser.add_argument("--seed", type=int, default=23, help="Random seed.")
    parser.add_argument("--json-out", default="", help="Optional path for JSON summary.")
    return parser.parse_args()


def _parse_users(raw: str) -> list[str]:
    users = [item.strip() for item in raw.split(",") if item.strip()]
    deduped: list[str] = []
    seen: set[str] = set()
    for user in users:
        if user in seen:
            continue
        seen.add(user)
        deduped.append(user)
    return deduped


def _must_login(base_url: str, user_id: str, password: str) -> tuple[ApiClient, dict[str, Any]]:
    client = ApiClient(base_url=base_url)
    login = client.login(user_id=user_id, password=password)
    if not login.ok:
        raise RuntimeError(f"Login failed for {user_id}: status={login.status_code} error={login.error}")
    return client, {"ok": login.ok, "status": login.status_code}


def _find_group_id(client: ApiClient, user_id: str, group_name: str, suburb: str) -> Optional[str]:
    result = client.request("GET", "/community/groups", query={"user_id": user_id, "suburb": suburb})
    if not result.ok or not isinstance(result.body, list):
        return None
    target_name = group_name.strip().lower()
    target_suburb = suburb.strip().lower()
    for row in result.body:
        if not isinstance(row, dict):
            continue
        name = str(row.get("name") or "").strip().lower()
        row_suburb = str(row.get("suburb") or "").strip().lower()
        if name == target_name and row_suburb == target_suburb:
            maybe_id = row.get("id")
            if isinstance(maybe_id, str) and maybe_id:
                return maybe_id
    return None


def _ensure_group(owner_client: ApiClient, owner_user: str, group_name: str, suburb: str) -> tuple[str, ApiResult]:
    existing_id = _find_group_id(owner_client, owner_user, group_name, suburb)
    if existing_id:
        return existing_id, ApiResult(status_code=200, body={"id": existing_id, "existing": True}, latency_ms=0.0)

    created = owner_client.request(
        "POST",
        "/community/groups",
        json_body={"user_id": owner_user, "name": group_name, "suburb": suburb},
    )
    if not created.ok or not isinstance(created.body, dict):
        return "", created
    group_id = created.body.get("id")
    if isinstance(group_id, str) and group_id:
        return group_id, created
    return "", ApiResult(status_code=500, body=created.body, latency_ms=created.latency_ms, error="missing_group_id")


def _ensure_member(owner_client: ApiClient, group_id: str, owner_user: str, member_user: str) -> ApiResult:
    return owner_client.request(
        "POST",
        f"/community/groups/{group_id}/members",
        json_body={
            "requester_user_id": owner_user,
            "member_user_id": member_user,
        },
    )


def _sample_photos(rand: random.Random, count: int = 3) -> list[str]:
    size = min(max(1, count), len(PHOTO_LIBRARY))
    return rand.sample(PHOTO_LIBRARY, k=size)


def _build_post_payload(
    *,
    user_id: str,
    idx: int,
    group_name: str,
    suburb: str,
    rand: random.Random,
) -> dict[str, Any]:
    persona = DOG_PERSONAS.get(user_id)
    theme_title, theme_body = THREAD_THEMES[idx % len(THREAD_THEMES)]
    nonce = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    if persona:
        dog_name = persona["dog_name"]
        breed = persona["breed"]
        style = persona["style"]
        title = f"{group_name} {theme_title}: {dog_name} ({nonce}-{user_id}-{idx})"
        body = (
            f"{dog_name} ({breed}) update at {group_name}. {style} "
            f"{theme_body} Sesame and Buddy still required calm supervised resets. Ref: {nonce}-{idx}."
        )
    else:
        title = f"{group_name} {theme_title}: local handler update ({nonce}-{user_id}-{idx})"
        body = (
            f"Observer update from {group_name}. {theme_body} "
            "Annika stayed friendly, Snowy needed confidence laps, and Pepsi settled after slower introductions."
        )
    return {
        "type": "group_post",
        "user_id": user_id,
        "title": title,
        "body": body,
        "suburb": suburb,
        "photo_urls": _sample_photos(rand, count=3),
    }


def _build_persona_feature_post(
    *,
    user_id: str,
    group_name: str,
    suburb: str,
    rand: random.Random,
) -> Optional[dict[str, Any]]:
    persona = DOG_PERSONAS.get(user_id)
    if not persona:
        return None
    nonce = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    comments = PERSONA_THREAD_COMMENTS.get(user_id, [])
    thread_cues = " ".join(comments)
    body = (
        f"{persona['dog_name']} ({persona['breed']}) spotlight at {group_name}. "
        f"{persona['style']} {thread_cues} Ref: persona-{user_id}-{nonce}."
    )
    return {
        "type": "group_post",
        "user_id": user_id,
        "title": f"{group_name} feature thread: {persona['dog_name']} ({nonce})",
        "body": body,
        "suburb": suburb,
        "photo_urls": _sample_photos(rand, count=3),
    }


def _build_spicy_post(
    *,
    user_id: str,
    group_name: str,
    suburb: str,
    scenario: dict[str, Any],
    rand: random.Random,
) -> dict[str, Any]:
    nonce = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    return {
        "type": "group_post",
        "user_id": user_id,
        "title": f"{scenario['title']} ({nonce}-{user_id})",
        "body": (
            f"{scenario['body']} "
            "Thread comment: Recommend moderation review and reminder of Collenso play rules."
        ),
        "suburb": suburb,
        "photo_urls": _sample_photos(rand, count=2),
    }


def _build_event_payload(
    *,
    user_id: str,
    group_id: str,
    group_name: str,
    suburb: str,
    idx: int,
    rand: random.Random,
) -> dict[str, Any]:
    event_titles = [
        "Ball Control Rotation",
        "Confidence Walk Loop",
        "Structured Decompression Session",
        "Recall Ladder Practice",
        "Sunset Social Reset",
        "Handler Skills Mini Clinic",
    ]
    event_title = event_titles[idx % len(event_titles)]
    start = datetime.now(timezone.utc) + timedelta(days=1 + (idx % 8), hours=rand.randint(6, 18))
    event_date = start.replace(microsecond=0).isoformat().replace("+00:00", "Z")
    return {
        "user_id": user_id,
        "title": f"{group_name}: {event_title}",
        "description": (
            "Threaded meetup with play rules, toy boundaries, and cooldown loops. "
            "Includes live handler check-ins and post-session notes."
        ),
        "suburb": suburb,
        "date": event_date,
        "group_id": group_id,
    }


def main() -> int:
    args = parse_args()
    rand = random.Random(args.seed)
    users = _parse_users(args.users)
    if args.owner_user not in users:
        users.insert(0, args.owner_user)
    if args.test_user not in users:
        users.insert(0, args.test_user)
    if args.posts_per_user < 0:
        raise SystemExit("--posts-per-user must be >= 0")
    if args.events < 0:
        raise SystemExit("--events must be >= 0")
    if args.rsvps_per_event < 0:
        raise SystemExit("--rsvps-per-event must be >= 0")
    if args.spicy_posts < 0:
        raise SystemExit("--spicy-posts must be >= 0")
    if args.seed_moderation_reports not in (0, 1):
        raise SystemExit("--seed-moderation-reports must be 0 or 1")

    summary: dict[str, Any] = {
        "base_url": args.base_url,
        "group_name": args.group_name,
        "suburb": args.suburb,
        "owner_user": args.owner_user,
        "test_user": args.test_user,
        "users": users,
        "memberships": {"attempted": 0, "ok": 0, "errors": 0, "rate_limited": 0},
        "posts": {"attempted": 0, "ok": 0, "errors": 0, "rate_limited": 0},
        "persona_posts": {"attempted": 0, "ok": 0, "errors": 0, "rate_limited": 0},
        "spicy_posts": {"attempted": 0, "ok": 0, "errors": 0, "rate_limited": 0},
        "events": {"attempted": 0, "ok": 0, "errors": 0, "rate_limited": 0},
        "rsvps": {"attempted": 0, "ok": 0, "errors": 0, "rate_limited": 0},
        "moderation_reports": {"attempted": 0, "ok": 0, "errors": 0, "rate_limited": 0},
    }

    owner_client, owner_login = _must_login(args.base_url, args.owner_user, args.password)
    summary["owner_login"] = owner_login

    group_id, group_result = _ensure_group(owner_client, args.owner_user, args.group_name, args.suburb)
    summary["group_create_or_fetch"] = {
        "ok": group_result.ok,
        "status": group_result.status_code,
        "payload": group_result.body,
    }
    if not group_result.ok or not group_id:
        print(json.dumps(summary, indent=2, sort_keys=True))
        return 2

    user_clients: dict[str, ApiClient] = {}
    login_errors: list[dict[str, Any]] = []
    for user_id in users:
        client = ApiClient(base_url=args.base_url)
        login = client.login(user_id=user_id, password=args.password)
        if not login.ok:
            login_errors.append({"user_id": user_id, "status": login.status_code, "error": login.error})
            continue
        user_clients[user_id] = client
    summary["login_errors"] = login_errors

    # Ensure test account and all active users are members so the feed feels realistic from member context.
    for member_user in users:
        summary["memberships"]["attempted"] += 1
        result = _ensure_member(owner_client, group_id, args.owner_user, member_user)
        if result.ok:
            summary["memberships"]["ok"] += 1
        elif result.status_code == 429:
            summary["memberships"]["rate_limited"] += 1
        else:
            summary["memberships"]["errors"] += 1
    # Redundant explicit ensure for test user to satisfy request intent.
    summary["memberships"]["attempted"] += 1
    forced_test_member = _ensure_member(owner_client, group_id, args.owner_user, args.test_user)
    if forced_test_member.ok:
        summary["memberships"]["ok"] += 1
    elif forced_test_member.status_code == 429:
        summary["memberships"]["rate_limited"] += 1
    else:
        summary["memberships"]["errors"] += 1

    created_post_ids: list[str] = []
    spicy_post_ids: list[str] = []

    # Guarantee one rich, persona-accurate post for each core dog account.
    core_dog_users = [user for user in DOG_PERSONAS.keys() if user in user_clients]
    for user_id in core_dog_users:
        payload = _build_persona_feature_post(
            user_id=user_id,
            group_name=args.group_name,
            suburb=args.suburb,
            rand=rand,
        )
        if not payload:
            continue
        summary["persona_posts"]["attempted"] += 1
        summary["posts"]["attempted"] += 1
        result = user_clients[user_id].request("POST", "/community/posts", json_body=payload)
        if result.ok:
            summary["persona_posts"]["ok"] += 1
            summary["posts"]["ok"] += 1
            if isinstance(result.body, dict):
                maybe_id = result.body.get("id")
                if isinstance(maybe_id, str) and maybe_id:
                    created_post_ids.append(maybe_id)
        elif result.status_code == 429:
            summary["persona_posts"]["rate_limited"] += 1
            summary["posts"]["rate_limited"] += 1
        else:
            summary["persona_posts"]["errors"] += 1
            summary["posts"]["errors"] += 1

    # Generic high-volume threads: prioritize non-core accounts so core persona posts avoid rate-limit suppression.
    generic_user_ids = [user for user in user_clients.keys() if user not in DOG_PERSONAS]
    if not generic_user_ids:
        generic_user_ids = list(user_clients.keys())
    for user_id in generic_user_ids:
        client = user_clients[user_id]
        for idx in range(args.posts_per_user):
            payload = _build_post_payload(
                user_id=user_id,
                idx=idx,
                group_name=args.group_name,
                suburb=args.suburb,
                rand=rand,
            )
            summary["posts"]["attempted"] += 1
            result = client.request("POST", "/community/posts", json_body=payload)
            if result.ok:
                summary["posts"]["ok"] += 1
                if isinstance(result.body, dict):
                    maybe_id = result.body.get("id")
                    if isinstance(maybe_id, str) and maybe_id:
                        created_post_ids.append(maybe_id)
            elif result.status_code == 429:
                summary["posts"]["rate_limited"] += 1
            else:
                summary["posts"]["errors"] += 1

    # Seed a few higher-conflict posts and submit pending moderation reports for future queue testing.
    spicy_authors = generic_user_ids if generic_user_ids else list(user_clients.keys())
    moderation_reporters = [uid for uid in (args.test_user, args.owner_user, "user_1", "user_3") if uid in user_clients]
    if not moderation_reporters:
        moderation_reporters = list(user_clients.keys())
    for idx in range(min(args.spicy_posts, len(SPICY_SCENARIOS))):
        if not spicy_authors:
            break
        scenario = SPICY_SCENARIOS[idx]
        author = spicy_authors[idx % len(spicy_authors)]
        payload = _build_spicy_post(
            user_id=author,
            group_name=args.group_name,
            suburb=args.suburb,
            scenario=scenario,
            rand=rand,
        )
        summary["spicy_posts"]["attempted"] += 1
        summary["posts"]["attempted"] += 1
        created = user_clients[author].request("POST", "/community/posts", json_body=payload)
        created_post_id: Optional[str] = None
        if created.ok and isinstance(created.body, dict):
            summary["spicy_posts"]["ok"] += 1
            summary["posts"]["ok"] += 1
            maybe_id = created.body.get("id")
            if isinstance(maybe_id, str) and maybe_id:
                created_post_id = maybe_id
                created_post_ids.append(maybe_id)
                spicy_post_ids.append(maybe_id)
        elif created.status_code == 429:
            summary["spicy_posts"]["rate_limited"] += 1
            summary["posts"]["rate_limited"] += 1
        else:
            summary["spicy_posts"]["errors"] += 1
            summary["posts"]["errors"] += 1

        if args.seed_moderation_reports != 1:
            continue
        if not created_post_id or not moderation_reporters:
            continue
        reporter = moderation_reporters[idx % len(moderation_reporters)]
        report_payload = {
            "reporter_user_id": reporter,
            "target_type": "post",
            "target_id": created_post_id,
            "reason": scenario["reason"],
            "details": "Auto-seeded moderation candidate for QA review queue realism.",
        }
        summary["moderation_reports"]["attempted"] += 1
        reported = user_clients[reporter].request("POST", "/community/moderation/reports", json_body=report_payload)
        if reported.ok:
            summary["moderation_reports"]["ok"] += 1
        elif reported.status_code == 429:
            summary["moderation_reports"]["rate_limited"] += 1
        else:
            summary["moderation_reports"]["errors"] += 1

    event_ids: list[str] = []
    event_creators = list(user_clients.keys()) or [args.owner_user]
    for idx in range(args.events):
        creator = event_creators[idx % len(event_creators)]
        creator_client = user_clients.get(creator, owner_client)
        payload = _build_event_payload(
            user_id=creator,
            group_id=group_id,
            group_name=args.group_name,
            suburb=args.suburb,
            idx=idx,
            rand=rand,
        )
        summary["events"]["attempted"] += 1
        created = creator_client.request("POST", "/community/events", json_body=payload)
        if not created.ok or not isinstance(created.body, dict):
            if created.status_code == 429:
                summary["events"]["rate_limited"] += 1
            else:
                summary["events"]["errors"] += 1
            continue
        summary["events"]["ok"] += 1
        event_id = created.body.get("id")
        event_status = str(created.body.get("status") or "")
        if isinstance(event_id, str) and event_id:
            event_ids.append(event_id)
            if event_status == "pending_approval":
                owner_client.request(
                    "POST",
                    f"/community/events/{event_id}/approve",
                    query={"requester_user_id": args.owner_user},
                )

    attendees = list(user_clients.keys())
    for event_id in event_ids:
        if not attendees:
            break
        rand.shuffle(attendees)
        for attendee in attendees[: args.rsvps_per_event]:
            attendee_client = user_clients[attendee]
            summary["rsvps"]["attempted"] += 1
            rsvp = attendee_client.request(
                "POST",
                f"/community/events/{event_id}/rsvp",
                json_body={"user_id": attendee, "status": "attending"},
            )
            if rsvp.ok:
                summary["rsvps"]["ok"] += 1
            elif rsvp.status_code == 429:
                summary["rsvps"]["rate_limited"] += 1
            else:
                summary["rsvps"]["errors"] += 1

    groups_check = owner_client.request(
        "GET",
        "/community/groups",
        query={"user_id": args.test_user, "suburb": args.suburb},
    )
    membership_status = "unknown"
    if groups_check.ok and isinstance(groups_check.body, list):
        for row in groups_check.body:
            if not isinstance(row, dict):
                continue
            if str(row.get("id") or "") == group_id:
                membership_status = str(row.get("membership_status") or "unknown")
                break
    summary["test_user_membership_status"] = membership_status

    newest_posts = owner_client.request(
        "GET",
        "/community/posts",
        query={"user_id": args.test_user, "suburb": args.suburb, "q": "collenso", "sort_by": "newest"},
    )
    if newest_posts.ok and isinstance(newest_posts.body, list):
        summary["newest_collenso_posts"] = [
            {
                "id": row.get("id"),
                "title": row.get("title"),
                "created_by": row.get("created_by"),
                "created_at": row.get("created_at"),
                "photo_count": len(row.get("photo_urls") or []),
            }
            for row in newest_posts.body[:12]
            if isinstance(row, dict)
        ]
    else:
        summary["newest_collenso_posts"] = []

    summary["created_post_ids"] = created_post_ids[:20]
    summary["spicy_post_ids"] = spicy_post_ids[:20]
    has_error = bool(summary["login_errors"]) or any(
        summary[key]["errors"] > 0
        for key in ("memberships", "posts", "persona_posts", "spicy_posts", "events", "rsvps", "moderation_reports")
    )

    output = json.dumps(summary, indent=2, sort_keys=True)
    print(output)
    if args.json_out:
        with open(args.json_out, "w", encoding="utf-8") as handle:
            handle.write(output + "\n")
    return 2 if has_error else 0


if __name__ == "__main__":
    raise SystemExit(main())
