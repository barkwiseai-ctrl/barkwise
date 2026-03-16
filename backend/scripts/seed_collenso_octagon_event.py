#!/usr/bin/env python3
import argparse
import json
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any

from api_bot_lib import ApiClient, ApiResult


GROUP_NAME_DEFAULT = "Collenso Dog Park"
SUBURB_DEFAULT = "Sunshine West"
OWNER_USER_DEFAULT = "annika"
PASSWORD_DEFAULT = "petsocial-demo"

PERSONA_USERS = ["annika", "snowy", "sesame", "pepsi", "billie", "buddy", "user_2"]

THREAD_COMMENTS: list[dict[str, str]] = [
    {
        "user_id": "annika",
        "body": (
            "[MOD TEST ROLEPLAY] WELCOME TO THE BARKAGON. Crowd noise is unreal. "
            "Annika opens with pure chaos-footwork and zero tactical awareness."
        ),
    },
    {
        "user_id": "sesame",
        "body": (
            "Sesame with instant cage control around the ball zone. High drive, sharp pivots, "
            "and that 'touch my ball and we have a problem' stare."
        ),
    },
    {
        "user_id": "buddy",
        "body": (
            "Buddy answers with pressure and re-entry speed. This is giving rivalry main-event energy, "
            "possible verbal TKO if handlers miss the reset timing."
        ),
    },
    {
        "user_id": "pepsi",
        "body": (
            "Pepsi enters like a seasoned brawler, testing everyone in round one. "
            "Slow intro with unfamiliar handlers, then full send."
        ),
    },
    {
        "user_id": "snowy",
        "body": (
            "Snowy starts on the back foot, then lands the accidental heavyweight shoulder check. "
            "Underdog momentum shift!"
        ),
    },
    {
        "user_id": "billie",
        "body": (
            "Billie showing veteran pacing: no wasted movement, smart breaks, then surprise burst. "
            "Classic championship composure."
        ),
    },
    {
        "user_id": "user_2",
        "body": (
            "Commentary desk: this is absurd and perfect for moderation testing. "
            "Keywords flying: octagon, elimination, knockout, smackdown, tap out."
        ),
    },
]

THREAD_REPLIES: list[dict[str, Any]] = [
    {
        "reply_to_index": 1,
        "user_id": "buddy",
        "body": "Respectfully, that wasn't cage control, that was toy tyranny.",
    },
    {
        "reply_to_index": 2,
        "user_id": "sesame",
        "body": "Counterpoint: keep your paws out of my lane and we stay technical.",
    },
    {
        "reply_to_index": 4,
        "user_id": "annika",
        "body": "Snowy with the accidental power meta. Zero intent, maximum impact.",
    },
    {
        "reply_to_index": 6,
        "user_id": "pepsi",
        "body": "Add 'ground game' to that keyword list. This thread is pure chaos.",
    },
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Seed a Collenso Dog Park octagon-style event and UFC-like commentary thread (moderation test)."
    )
    parser.add_argument("--base-url", default="http://127.0.0.1:8000", help="API base URL.")
    parser.add_argument("--group-name", default=GROUP_NAME_DEFAULT, help="Target group name.")
    parser.add_argument("--suburb", default=SUBURB_DEFAULT, help="Target suburb.")
    parser.add_argument("--owner-user", default=OWNER_USER_DEFAULT, help="Group owner user id.")
    parser.add_argument("--password", default=PASSWORD_DEFAULT, help="Password used by persona users.")
    parser.add_argument("--days-ahead", type=int, default=2, help="Event date offset in days.")
    parser.add_argument("--json-out", default="", help="Optional JSON summary output path.")
    return parser.parse_args()


def must_ok(result: ApiResult, step: str) -> dict[str, Any]:
    if not result.ok:
        raise RuntimeError(
            f"{step} failed: status={result.status_code} error={result.error} body={result.body}"
        )
    if isinstance(result.body, dict):
        return result.body
    return {"value": result.body}


def login_clients(base_url: str, password: str, users: list[str]) -> dict[str, ApiClient]:
    clients: dict[str, ApiClient] = {}
    for user_id in users:
        client = ApiClient(base_url=base_url)
        login = client.login(user_id=user_id, password=password)
        if not login.ok:
            raise RuntimeError(
                f"Login failed for {user_id}: status={login.status_code} error={login.error} body={login.body}"
            )
        clients[user_id] = client
    return clients


def find_group_id(client: ApiClient, owner_user: str, group_name: str, suburb: str) -> str:
    lookup = client.request(
        "GET",
        "/community/groups",
        query={"user_id": owner_user, "suburb": suburb},
    )
    if not lookup.ok or not isinstance(lookup.body, list):
        raise RuntimeError(f"Unable to list groups for {owner_user}: {lookup.status_code} {lookup.body}")
    target_name = group_name.strip().lower()
    target_suburb = suburb.strip().lower()
    for row in lookup.body:
        if not isinstance(row, dict):
            continue
        name = str(row.get("name") or "").strip().lower()
        row_suburb = str(row.get("suburb") or "").strip().lower()
        if name == target_name and row_suburb == target_suburb:
            group_id = str(row.get("id") or "").strip()
            if group_id:
                return group_id
    raise RuntimeError(f"Group not found: {group_name} in {suburb}")


def main() -> None:
    args = parse_args()
    users = list(dict.fromkeys([args.owner_user] + PERSONA_USERS))
    clients = login_clients(base_url=args.base_url, password=args.password, users=users)
    owner_client = clients[args.owner_user]

    group_id = find_group_id(
        client=owner_client,
        owner_user=args.owner_user,
        group_name=args.group_name,
        suburb=args.suburb,
    )

    now = datetime.now(timezone.utc)
    nonce = now.strftime("%Y%m%dT%H%M%SZ")
    event_date = (now + timedelta(days=max(0, args.days_ahead))).date().isoformat()
    event_payload = {
        "user_id": args.owner_user,
        "title": f"Collenso Barkagon Elimination Night [MOD TEST] ({nonce})",
        "description": (
            "[ROLEPLAY TEST ONLY] Octagon-style elimination commentary thread for known Collenso personas. "
            "This is test content for early moderation and keyword filtering in a closed environment."
        ),
        "suburb": args.suburb,
        "date": event_date,
        "group_id": group_id,
    }
    event = must_ok(
        owner_client.request("POST", "/community/events", json_body=event_payload),
        "create_event",
    )
    event_id = str(event.get("id") or "").strip()
    if not event_id:
        raise RuntimeError("Event created without id")

    for user_id in PERSONA_USERS:
        rsvp_client = clients.get(user_id)
        if not rsvp_client:
            continue
        rsvp_client.request(
            "POST",
            f"/community/events/{event_id}/rsvp",
            json_body={"user_id": user_id, "status": "attending"},
        )

    thread_post: dict[str, Any] = {}
    thread_creator = ""
    thread_users = list(dict.fromkeys([args.owner_user] + PERSONA_USERS))
    for candidate_user in thread_users:
        candidate_client = clients.get(candidate_user)
        if not candidate_client:
            continue
        thread_payload = {
            "type": "group_post",
            "user_id": candidate_user,
            "title": f"Live Thread: Collenso Barkagon Commentary [MOD TEST] ({nonce})",
            "body": (
                "[TEST THREAD] Running commentary for the Barkagon elimination event. "
                "Not intended app behavior; used to validate moderation/risk controls."
            ),
            "suburb": args.suburb,
            "photo_urls": [
                "https://images.unsplash.com/photo-1518717758536-85ae29035b6d",
                "https://images.unsplash.com/photo-1548199973-03cce0bbc87b",
            ],
        }
        attempt = candidate_client.request("POST", "/community/posts", json_body=thread_payload)
        if attempt.ok and isinstance(attempt.body, dict):
            thread_post = attempt.body
            thread_creator = candidate_user
            break
        if attempt.status_code != 429:
            raise RuntimeError(
                f"create_thread_post failed for {candidate_user}: "
                f"status={attempt.status_code} error={attempt.error} body={attempt.body}"
            )
    if not thread_post:
        raise RuntimeError("Unable to create thread post: all candidate users were rate limited.")

    post = thread_post
    post_id = str(post.get("id") or "").strip()
    if not post_id:
        raise RuntimeError("Thread post created without id")

    comment_ids: list[str] = []
    reply_ids: list[str] = []
    comments_supported = owner_client.request(
        "GET",
        f"/community/posts/{post_id}/comments",
        query={"user_id": args.owner_user, "limit": 1},
    ).status_code != 404

    if comments_supported:
        for idx, row in enumerate(THREAD_COMMENTS):
            user_id = row["user_id"]
            client = clients.get(user_id)
            if not client:
                continue
            body = str(row["body"]).strip()
            created = must_ok(
                client.request(
                    "POST",
                    f"/community/posts/{post_id}/comments",
                    json_body={"user_id": user_id, "body": body},
                ),
                f"create_comment_{idx}",
            )
            comment_ids.append(str(created.get("id") or ""))

        for idx, row in enumerate(THREAD_REPLIES):
            parent_index = int(row["reply_to_index"])
            if parent_index < 0 or parent_index >= len(comment_ids):
                continue
            parent_id = comment_ids[parent_index]
            if not parent_id:
                continue
            user_id = str(row["user_id"])
            client = clients.get(user_id)
            if not client:
                continue
            created = must_ok(
                client.request(
                    "POST",
                    f"/community/posts/{post_id}/comments",
                    json_body={
                        "user_id": user_id,
                        "body": str(row["body"]).strip(),
                        "parent_comment_id": parent_id,
                    },
                ),
                f"create_reply_{idx}",
            )
            reply_ids.append(str(created.get("id") or ""))
    else:
        # Fallback for older backends without comment endpoints: emit commentary as linked group posts.
        for idx, row in enumerate(THREAD_COMMENTS):
            original_user_id = str(row["user_id"]).strip()
            body = str(row["body"]).strip()
            created_id = ""
            for candidate_user in [original_user_id] + [u for u in thread_users if u != original_user_id]:
                client = clients.get(candidate_user)
                if not client:
                    continue
                body_with_voice = body
                if candidate_user != original_user_id:
                    body_with_voice = f"[Voice: {original_user_id}] {body}"
                attempt = client.request(
                    "POST",
                    "/community/posts",
                    json_body={
                        "type": "group_post",
                        "user_id": candidate_user,
                        "title": f"Barkagon Desk #{idx + 1} ({nonce})",
                        "body": (
                            f"[Thread ref: {post_id}] {body_with_voice} "
                            "Live commentary stream continues."
                        ),
                        "suburb": args.suburb,
                    },
                )
                if attempt.ok and isinstance(attempt.body, dict):
                    created_id = str(attempt.body.get("id") or "")
                    break
                if attempt.status_code != 429:
                    raise RuntimeError(
                        f"create_comment_fallback_post_{idx} failed for {candidate_user}: "
                        f"status={attempt.status_code} error={attempt.error} body={attempt.body}"
                    )
            comment_ids.append(created_id)

        for idx, row in enumerate(THREAD_REPLIES):
            original_user_id = str(row["user_id"]).strip()
            body = str(row["body"]).strip()
            created_id = ""
            for candidate_user in [original_user_id] + [u for u in thread_users if u != original_user_id]:
                client = clients.get(candidate_user)
                if not client:
                    continue
                body_with_voice = body
                if candidate_user != original_user_id:
                    body_with_voice = f"[Voice: {original_user_id}] {body}"
                attempt = client.request(
                    "POST",
                    "/community/posts",
                    json_body={
                        "type": "group_post",
                        "user_id": candidate_user,
                        "title": f"Barkagon Reply #{idx + 1} ({nonce})",
                        "body": (
                            f"[Reply ref thread: {post_id}] {body_with_voice} "
                            "Analyst desk keeps the UFC-style commentary rolling."
                        ),
                        "suburb": args.suburb,
                    },
                )
                if attempt.ok and isinstance(attempt.body, dict):
                    created_id = str(attempt.body.get("id") or "")
                    break
                if attempt.status_code != 429:
                    raise RuntimeError(
                        f"create_reply_fallback_post_{idx} failed for {candidate_user}: "
                        f"status={attempt.status_code} error={attempt.error} body={attempt.body}"
                    )
            reply_ids.append(created_id)

    summary = {
        "group_id": group_id,
        "event_id": event_id,
        "event_title": event_payload["title"],
        "event_date": event_date,
        "thread_post_id": post_id,
        "thread_title": str(post.get("title") or ""),
        "thread_created_by": thread_creator,
        "comment_mode": "comments" if comments_supported else "fallback_posts",
        "top_level_comments_created": len([c for c in comment_ids if c]),
        "replies_created": len([c for c in reply_ids if c]),
        "comment_ids": [c for c in comment_ids if c],
        "reply_ids": [c for c in reply_ids if c],
    }
    print(json.dumps(summary, indent=2))

    if args.json_out:
        out_path = Path(args.json_out)
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
