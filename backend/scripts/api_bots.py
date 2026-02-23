#!/usr/bin/env python3
import argparse
import json
import random
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Callable, Optional

from api_bot_lib import ApiClient, ApiResult


ANNIKA_USER_DEFAULT = "annika"
COLLENSO_OWNER_DEFAULT = ANNIKA_USER_DEFAULT
COLLENSO_GROUP_NAME_DEFAULT = "Collenso Dog Park"
COLLENSO_SUBURB_DEFAULT = "Sunshine West"

DOG_PERSONAS: dict[str, dict[str, Any]] = {
    "snowy": {
        "dog_name": "Snowy",
        "breed": "black and white bull arab",
        "summary": "a bit cowardly but loves to play and still thinks he is much smaller than he is",
        "interaction_notes": [
            "Snowy started cautious, then launched into chase and accidentally trampled a smaller dog during a turn.",
            "Snowy hid behind his owner at first, then joined playful wrestling and clipped another dog by misjudging his size.",
            "Snowy was nervous around louder dogs early, but later joined tag games and accidentally bowled through a smaller pup.",
        ],
        "photo_urls": [
            "https://images.unsplash.com/photo-1530281700549-e82e7bf110d6",
            "https://images.unsplash.com/photo-1518717758536-85ae29035b6d",
            "https://images.unsplash.com/photo-1517423440428-a5a00ad493e8",
        ],
    },
    "sesame": {
        "dog_name": "Sesame",
        "breed": "brown and black border collie/poodle cross (schnauzer-like face)",
        "summary": "high-drive and ball-obsessed, with quick defensive reactions if another dog threatens her ball",
        "interaction_notes": [
            "Sesame ran relentless ball sprints, then snapped into guard mode when another dog got close to her ball.",
            "Sesame looked brilliant in fetch drills, but became defensive and vocal when a second dog challenged her toy.",
            "Sesame had elite chase focus and then got angry during a ball dispute, requiring a calm reset.",
        ],
        "photo_urls": [
            "https://images.unsplash.com/photo-1525253013412-55c1a69a5738",
            "https://images.unsplash.com/photo-1522276498395-f4f68f7f8454",
            "https://images.unsplash.com/photo-1517849845537-4d257902454a",
        ],
    },
    "annika": {
        "dog_name": "Annika",
        "breed": "black golden retriever/poodle cross",
        "summary": "goofy, not very bright, playful, and always friendly with every dog",
        "interaction_notes": [
            "Annika bounced from dog to dog with goofy energy and open play bows all session.",
            "Annika misread cues a few times, then happily reset and made friendly re-introductions.",
            "Annika turned every interaction into a playful game and stayed social with all dogs.",
        ],
        "photo_urls": [
            "https://images.unsplash.com/photo-1518717758536-85ae29035b6d",
            "https://images.unsplash.com/photo-1548199973-03cce0bbc87b",
            "https://images.unsplash.com/photo-1507146426996-ef05306b995a",
        ],
    },
    "pepsi": {
        "dog_name": "Pepsi",
        "breed": "brown/tan staffie-jack russell cross",
        "summary": "a tough, aggressive player who is wary of men at first but usually warms up over time",
        "interaction_notes": [
            "Pepsi played hard with chest-bumps and fast pivots, then slowly built trust with a male handler.",
            "Pepsi came in tense around male strangers, but after structured greetings shifted into confident play.",
            "Pepsi pushed rough play boundaries early and then settled into balanced interactions after decompression.",
        ],
        "photo_urls": [
            "https://images.unsplash.com/photo-1601758228041-f3b2795255f1",
            "https://images.unsplash.com/photo-1516734212186-65266f4f17c8",
            "https://images.unsplash.com/photo-1537151608828-ea2b11777ee8",
        ],
    },
    "billie": {
        "dog_name": "Billie",
        "breed": "tan boxer cross",
        "summary": "older but still full of love and eager to join play sessions",
        "interaction_notes": [
            "Billie did slower warm-ups, then joined play with tail-wagging body slams and affectionate breaks.",
            "Billie paced herself because of age, but still jumped into group games and shared calm social time.",
            "Billie alternated cuddle checks with high-energy bursts and was warmly social throughout.",
        ],
        "photo_urls": [
            "https://images.unsplash.com/photo-1517849845537-4d257902454a",
            "https://images.unsplash.com/photo-1530281700549-e82e7bf110d6",
            "https://images.unsplash.com/photo-1543466835-00a7907e9de1",
        ],
    },
    "buddy": {
        "dog_name": "Buddy",
        "breed": "mostly black cavoodle with white spots",
        "summary": "playful and social, but repeatedly escalates with Sesame and neither dog backs down without intervention",
        "interaction_notes": [
            "Buddy and Sesame escalated during toy tension and had to be pulled apart before quickly re-engaging.",
            "Buddy played well with most dogs, then entered another prolonged standoff with Sesame over ball control.",
            "Buddy had multiple reset breaks after escalating with Sesame; both dogs kept trying to re-enter conflict.",
        ],
        "photo_urls": [
            "https://images.unsplash.com/photo-1507146426996-ef05306b995a",
            "https://images.unsplash.com/photo-1518717758536-85ae29035b6d",
            "https://images.unsplash.com/photo-1548199973-03cce0bbc87b",
        ],
    },
}

COLLENSO_CORE_USERS = list(DOG_PERSONAS.keys())
DEFAULT_USERS = ["annika", "snowy", "sesame", "pepsi", "billie", "buddy"]
SUBURBS = ["Sunshine West", "Surry Hills", "Redfern", "Newtown", "Marrickville"]
EVENTS = [
    "Collenso Morning Ball Session",
    "Collenso Controlled Play Rotation",
    "Collenso Confidence Walk",
    "Collenso Recall & Social Check-in",
]

NEW_DOG_SCENARIOS: list[dict[str, Any]] = [
    {
        "dog_name": "Maple",
        "breed": "tan kelpie cross",
        "interaction": (
            "Maple ran gentle loops with Billie, copied Annika's goofy play bows, and gave Snowy space during first greetings."
        ),
        "photo_urls": [
            "https://images.unsplash.com/photo-1543466835-00a7907e9de1",
            "https://images.unsplash.com/photo-1537151608828-ea2b11777ee8",
        ],
    },
    {
        "dog_name": "Koda",
        "breed": "white shepherd mix",
        "interaction": (
            "Koda entered confidently, triggered quick toy-guarding from Sesame, then settled once handlers split toy zones."
        ),
        "photo_urls": [
            "https://images.unsplash.com/photo-1517423440428-a5a00ad493e8",
            "https://images.unsplash.com/photo-1601758228041-f3b2795255f1",
        ],
    },
    {
        "dog_name": "Nori",
        "breed": "small brindle staffy mix",
        "interaction": (
            "Nori played rough with Pepsi, paused after clear corrections, and finished with balanced tag games near Annika."
        ),
        "photo_urls": [
            "https://images.unsplash.com/photo-1516734212186-65266f4f17c8",
            "https://images.unsplash.com/photo-1522276498395-f4f68f7f8454",
        ],
    },
]


@dataclass
class ActionStats:
    attempts: int = 0
    success: int = 0
    errors: int = 0
    status_codes: dict[int, int] = field(default_factory=dict)
    latencies_ms: list[float] = field(default_factory=list)


class StatsCollector:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self.by_action: dict[str, ActionStats] = {}

    def record(self, action: str, result: ApiResult) -> None:
        with self._lock:
            stats = self.by_action.setdefault(action, ActionStats())
            stats.attempts += 1
            stats.latencies_ms.append(result.latency_ms)
            stats.status_codes[result.status_code] = stats.status_codes.get(result.status_code, 0) + 1
            if result.ok:
                stats.success += 1
            else:
                stats.errors += 1


class BotWorker:
    def __init__(
        self,
        *,
        worker_id: int,
        user_id: str,
        password: str,
        base_url: str,
        iterations: int,
        read_only: bool,
        min_delay_ms: int,
        max_delay_ms: int,
        annika_user: str,
        annika_force_posts: int,
        collenso_group_name: str,
        collenso_suburb: str,
        collenso_owner: str,
        collenso_core_users: list[str],
        stats: StatsCollector,
        seed: int,
    ) -> None:
        self.worker_id = worker_id
        self.user_id = user_id
        self.password = password
        self.base_url = base_url
        self.iterations = iterations
        self.read_only = read_only
        self.min_delay_ms = min_delay_ms
        self.max_delay_ms = max_delay_ms
        self.annika_user = annika_user
        self.annika_force_posts = max(0, annika_force_posts)
        self.collenso_group_name = collenso_group_name
        self.collenso_suburb = collenso_suburb
        self.collenso_owner = collenso_owner
        self.collenso_core_users = collenso_core_users
        self.stats = stats
        self.rand = random.Random(seed)
        self.client = ApiClient(base_url=base_url)
        self.persona_profile = DOG_PERSONAS.get(self.user_id)
        self.collenso_group_id: Optional[str] = None
        self.device_registered = False
        self.write_actions_done = 0
        self.max_write_actions_per_run = 3 if self.user_id == self.annika_user else 2

    def run(self) -> None:
        login_result = self.client.login(user_id=self.user_id, password=self.password)
        self.stats.record("auth_login", login_result)
        if not login_result.ok:
            return

        if not self.read_only:
            self._ensure_collenso_group_and_members()
            if self.persona_profile:
                forced_report = self._action_collenso_day_report(i=0)
                self.stats.record("community_collenso_forced_day_report", forced_report)
                self.write_actions_done += 1
            if self.user_id == self.annika_user:
                self._force_annika_posts()

        for i in range(self.iterations):
            action_name, action, is_write = self._choose_action()
            result = action(i)
            self.stats.record(action_name, result)
            if is_write and result.status_code != 204:
                self.write_actions_done += 1
            if self.max_delay_ms > 0:
                delay_ms = self.rand.randint(self.min_delay_ms, self.max_delay_ms)
                time.sleep(delay_ms / 1000.0)

    def _choose_action(self) -> tuple[str, Callable[[int], ApiResult], bool]:
        read_actions: list[tuple[str, Callable[[int], ApiResult], float, bool]] = [
            ("auth_me", self._action_auth_me, 0.06, False),
            ("community_browse", self._action_community_browse, 0.25, False),
            ("groups_browse", self._action_groups_browse, 0.18, False),
            ("events_browse", self._action_events_browse, 0.14, False),
            ("notifications_check", self._action_notifications_check, 0.17, False),
            ("community_send_analytics", self._action_analytics_event, 0.14, False),
            ("services_browse", self._action_services_browse, 0.06, False),
        ]

        actions = list(read_actions)
        if not self.read_only and self.write_actions_done < self.max_write_actions_per_run:
            if self.persona_profile:
                day_report_weight = 0.30 if self.user_id == self.annika_user else 0.24
                annika_photo_weight = 0.22 if self.user_id == self.annika_user else 0.00
                actions.extend(
                    [
                        ("community_create_collenso_day_report", self._action_collenso_day_report, day_report_weight, True),
                        (
                            "community_create_collenso_interaction_report",
                            self._action_collenso_interaction_report,
                            0.14,
                            True,
                        ),
                        (
                            "community_create_collenso_new_dog_report",
                            self._action_collenso_new_dog_report,
                            0.05,
                            True,
                        ),
                        ("community_create_collenso_event", self._action_create_event, 0.05, True),
                    ]
                )
                if annika_photo_weight > 0:
                    actions.append(
                        (
                            "community_create_annika_photo_post",
                            self._action_annika_photo_post,
                            annika_photo_weight,
                            True,
                        )
                    )
            else:
                actions.append(
                    (
                        "community_create_collenso_observer_report",
                        self._action_collenso_observer_report,
                        0.10,
                        True,
                    )
                )

        total_weight = sum(weight for _, _, weight, _ in actions)
        pick = self.rand.random() * total_weight
        running = 0.0
        for name, fn, weight, is_write in actions:
            running += weight
            if pick <= running:
                return name, fn, is_write
        name, fn, _, is_write = actions[-1]
        return name, fn, is_write

    def _nonce(self, i: int) -> str:
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        return f"{stamp}-w{self.worker_id}-i{i}-r{self.rand.randint(100, 999)}"

    def _pick_photos(self, photo_pool: list[str], min_count: int = 1, max_count: int = 3) -> list[str]:
        if not photo_pool:
            return []
        upper = min(max_count, len(photo_pool))
        lower = min(min_count, upper)
        count = self.rand.randint(lower, upper)
        return self.rand.sample(photo_pool, k=count)

    def _ensure_collenso_group_and_members(self) -> None:
        group_id = self._find_collenso_group_id(record_action="community_lookup_collenso_group")
        if not group_id and self.user_id == self.collenso_owner:
            create_group = self.client.request(
                "POST",
                "/community/groups",
                json_body={
                    "user_id": self.user_id,
                    "name": self.collenso_group_name,
                    "suburb": self.collenso_suburb,
                },
            )
            self.stats.record("community_create_collenso_group", create_group)
            if create_group.ok and isinstance(create_group.body, dict):
                maybe_id = create_group.body.get("id")
                if isinstance(maybe_id, str) and maybe_id:
                    group_id = maybe_id

        if not group_id:
            return

        self.collenso_group_id = group_id
        if self.user_id != self.collenso_owner:
            return

        for member_user_id in self.collenso_core_users:
            if member_user_id == self.user_id:
                continue
            result = self.client.request(
                "POST",
                f"/community/groups/{group_id}/members",
                json_body={
                    "requester_user_id": self.user_id,
                    "member_user_id": member_user_id,
                },
            )
            self.stats.record("community_collenso_add_member", result)

    def _find_collenso_group_id(self, record_action: str) -> Optional[str]:
        lookup = self.client.request("GET", "/community/groups", query={"user_id": self.user_id})
        self.stats.record(record_action, lookup)
        if not lookup.ok or not isinstance(lookup.body, list):
            return None

        for row in lookup.body:
            if not isinstance(row, dict):
                continue
            name = str(row.get("name") or "").strip().lower()
            suburb = str(row.get("suburb") or "").strip().lower()
            if name != self.collenso_group_name.strip().lower():
                continue
            if self.collenso_suburb and suburb != self.collenso_suburb.strip().lower():
                continue
            maybe_id = row.get("id")
            if isinstance(maybe_id, str) and maybe_id:
                return maybe_id
        return None

    def _action_auth_me(self, _: int) -> ApiResult:
        return self.client.request("GET", "/auth/me")

    def _action_services_browse(self, _: int) -> ApiResult:
        query = self.rand.choice(["walk", "groom", "park", "dog", ""])
        sort_by = self.rand.choice(["relevance", "rating", "distance", "price_low", "price_high"])
        params = {"suburb": self.collenso_suburb, "q": query or None, "sort_by": sort_by}
        return self.client.request("GET", "/services/providers", query=params)

    def _action_community_browse(self, _: int) -> ApiResult:
        sort_by = self.rand.choice(["relevance", "newest"])
        params = {
            "user_id": self.user_id,
            "suburb": self.collenso_suburb,
            "sort_by": sort_by,
            "q": "collenso",
        }
        return self.client.request("GET", "/community/posts", query=params)

    def _action_groups_browse(self, _: int) -> ApiResult:
        return self.client.request(
            "GET",
            "/community/groups",
            query={"user_id": self.user_id, "suburb": self.collenso_suburb},
        )

    def _action_events_browse(self, _: int) -> ApiResult:
        return self.client.request(
            "GET",
            "/community/events",
            query={"suburb": self.collenso_suburb, "user_id": self.user_id},
        )

    def _action_notifications_check(self, i: int) -> ApiResult:
        if not self.device_registered:
            register = self.client.request(
                "POST",
                "/notifications/register-device",
                json_body={
                    "user_id": self.user_id,
                    "device_token": f"api-bot-{self.worker_id}-{i}",
                    "platform": "web",
                },
            )
            self.stats.record("notifications_register_device", register)
            if register.ok:
                self.device_registered = True
        return self.client.request("GET", "/notifications", query={"user_id": self.user_id})

    def _action_analytics_event(self, i: int) -> ApiResult:
        event_name = self.rand.choice(
            [
                "community_feed_viewed",
                "event_card_opened",
                "group_directory_viewed",
                "collenso_day_report_opened",
            ]
        )
        payload = {
            "user_id": self.user_id,
            "event": event_name,
            "category": "community",
            "metadata": {
                "worker_id": self.worker_id,
                "iteration": i,
                "client": "api_bots.py",
                "group": self.collenso_group_name,
                "suburb": self.collenso_suburb,
            },
            "duration_ms": self.rand.randint(120, 2200),
        }
        return self.client.request("POST", "/community/analytics/events", json_body=payload)

    def _action_collenso_observer_report(self, i: int) -> ApiResult:
        nonce = self._nonce(i)
        payload = {
            "type": "group_post",
            "user_id": self.user_id,
            "title": f"Collenso observer report ({nonce})",
            "body": (
                f"Observed structured play and handler-led resets at {self.collenso_group_name}. "
                f"Dogs showed mixed play styles with supervised decompression breaks. Ref: {nonce}."
            ),
            "suburb": self.collenso_suburb,
        }
        return self.client.request("POST", "/community/posts", json_body=payload)

    def _action_collenso_day_report(self, i: int) -> ApiResult:
        if not self.persona_profile:
            return self._action_collenso_observer_report(i)

        nonce = self._nonce(i)
        dog_name = self.persona_profile["dog_name"]
        breed = self.persona_profile["breed"]
        summary = self.persona_profile["summary"]
        interaction = self.rand.choice(self.persona_profile["interaction_notes"])
        cross_pack_note = self._cross_pack_line(dog_name)
        photo_urls = self._pick_photos(self.persona_profile["photo_urls"], min_count=2, max_count=3)

        payload = {
            "type": "group_post",
            "user_id": self.user_id,
            "title": f"Collenso day report: {dog_name} ({nonce})",
            "body": (
                f"{dog_name} ({breed}) - {summary}. "
                f"At {self.collenso_group_name}: {interaction} {cross_pack_note} Ref: {nonce}."
            ),
            "suburb": self.collenso_suburb,
            "photo_urls": photo_urls,
        }
        return self.client.request("POST", "/community/posts", json_body=payload)

    def _action_collenso_interaction_report(self, i: int) -> ApiResult:
        if not self.persona_profile:
            return self._action_collenso_observer_report(i)

        nonce = self._nonce(i)
        dog_name = self.persona_profile["dog_name"]
        interaction = self.rand.choice(self.persona_profile["interaction_notes"])
        photo_urls = self._pick_photos(self.persona_profile["photo_urls"], min_count=1, max_count=2)
        payload = {
            "type": "group_post",
            "user_id": self.user_id,
            "title": f"Collenso interaction snapshot: {dog_name} ({nonce})",
            "body": (
                f"Interaction snapshot from {self.collenso_group_name}: {interaction} "
                f"Handlers used short decompression loops and controlled re-entry. Ref: {nonce}."
            ),
            "suburb": self.collenso_suburb,
            "photo_urls": photo_urls,
        }
        return self.client.request("POST", "/community/posts", json_body=payload)

    def _cross_pack_line(self, dog_name: str) -> str:
        lines = [
            "Sesame and Buddy required another separation after toy tension escalated and neither backed down.",
            "Billie helped de-escalate the group with slower, social pacing before play resumed.",
            "Annika stayed friendly with every dog and kept inviting nervous dogs into lighter play.",
            "Pepsi started wary around men and then settled after repeated calm handler interactions.",
            "Snowy did confidence laps before rejoining full-speed play with the group.",
        ]
        if dog_name == "Sesame":
            lines.insert(0, "Sesame guarded her ball strongly, and Buddy had to be physically separated during escalation.")
        if dog_name == "Buddy":
            lines.insert(0, "Buddy re-engaged with Sesame quickly after separation, so handlers kept both on controlled resets.")
        if dog_name == "Snowy":
            lines.insert(0, "Snowy misjudged spacing during chase and clipped a smaller dog while trying to turn.")
        return self.rand.choice(lines)

    def _action_collenso_new_dog_report(self, i: int) -> ApiResult:
        nonce = self._nonce(i)
        scenario = self.rand.choice(NEW_DOG_SCENARIOS)
        reporter = self.persona_profile["dog_name"] if self.persona_profile else self.user_id
        payload = {
            "type": "group_post",
            "user_id": self.user_id,
            "title": f"Collenso newcomer report: {scenario['dog_name']} ({nonce})",
            "body": (
                f"{reporter} met a new dog today: {scenario['dog_name']} ({scenario['breed']}). "
                f"{scenario['interaction']} Ref: {nonce}."
            ),
            "suburb": self.collenso_suburb,
            "photo_urls": self._pick_photos(scenario["photo_urls"], min_count=1, max_count=2),
        }
        return self.client.request("POST", "/community/posts", json_body=payload)

    def _action_annika_photo_post(self, i: int) -> ApiResult:
        if self.user_id != self.annika_user:
            return self._action_collenso_day_report(i)

        nonce = self._nonce(i)
        profile = DOG_PERSONAS[self.annika_user]
        payload = {
            "type": "group_post",
            "user_id": self.user_id,
            "title": f"Annika photo dump from Collenso ({nonce})",
            "body": (
                "Annika is a goofy, always-friendly black golden retriever/poodle cross. "
                f"Play recap at {self.collenso_group_name}: she invited every dog into bounce play. Ref: {nonce}."
            ),
            "suburb": self.collenso_suburb,
            "photo_urls": self._pick_photos(profile["photo_urls"], min_count=2, max_count=3),
        }
        return self.client.request("POST", "/community/posts", json_body=payload)

    def _force_annika_posts(self) -> None:
        max_forced = max(0, min(self.annika_force_posts, 1))
        for i in range(max_forced):
            result = self._action_annika_photo_post(i)
            self.stats.record("community_create_annika_forced_post", result)
            self.write_actions_done += 1

    def _action_create_event(self, i: int) -> ApiResult:
        group_id = self.collenso_group_id or self._find_collenso_group_id(record_action="community_lookup_collenso_group_for_event")
        future_date = (datetime.now(timezone.utc) + timedelta(days=self.rand.randint(1, 7))).date().isoformat()
        payload = {
            "user_id": self.user_id,
            "title": f"{self.rand.choice(EVENTS)} ({self._nonce(i)})",
            "description": (
                f"Structured play day at {self.collenso_group_name} with ball boundaries, confidence loops, and handler resets."
            ),
            "suburb": self.collenso_suburb,
            "date": future_date,
            "group_id": group_id,
        }
        return self.client.request("POST", "/community/events", json_body=payload)


def _percentile(values: list[float], p: float) -> float:
    if not values:
        return 0.0
    sorted_values = sorted(values)
    idx = int(round((len(sorted_values) - 1) * p))
    return sorted_values[max(0, min(idx, len(sorted_values) - 1))]


def _render_summary(stats: StatsCollector, duration_s: float) -> dict[str, Any]:
    total_attempts = sum(item.attempts for item in stats.by_action.values())
    total_errors = sum(item.errors for item in stats.by_action.values())
    payload: dict[str, Any] = {
        "duration_seconds": round(duration_s, 2),
        "total_attempts": total_attempts,
        "total_errors": total_errors,
        "actions": {},
    }

    for action, item in sorted(stats.by_action.items()):
        p95 = _percentile(item.latencies_ms, 0.95)
        avg = (sum(item.latencies_ms) / len(item.latencies_ms)) if item.latencies_ms else 0.0
        payload["actions"][action] = {
            "attempts": item.attempts,
            "success": item.success,
            "errors": item.errors,
            "avg_latency_ms": round(avg, 2),
            "p95_latency_ms": round(p95, 2),
            "status_codes": item.status_codes,
        }
    return payload


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run synthetic API bot behavior against BarkWise backend.")
    parser.add_argument("--base-url", default="http://localhost:8000", help="API base URL.")
    parser.add_argument("--users", default=",".join(DEFAULT_USERS), help="Comma-separated user IDs.")
    parser.add_argument("--password", default="petsocial-demo", help="Login password for all users.")
    parser.add_argument("--iterations", type=int, default=24, help="Actions per bot worker.")
    parser.add_argument("--concurrency", type=int, default=6, help="Concurrent bot workers.")
    parser.add_argument("--seed", type=int, default=42, help="Random seed.")
    parser.add_argument("--min-delay-ms", type=int, default=600, help="Minimum delay between actions.")
    parser.add_argument("--max-delay-ms", type=int, default=1800, help="Maximum delay between actions.")
    parser.add_argument("--read-only", action="store_true", help="Disable write actions.")
    parser.add_argument("--annika-user", default=ANNIKA_USER_DEFAULT, help="Synthetic user ID to prioritize.")
    parser.add_argument(
        "--annika-force-posts",
        type=int,
        default=2,
        help="Forced Annika photo posts to publish at the start of each Annika worker run.",
    )
    parser.add_argument("--collenso-group-name", default=COLLENSO_GROUP_NAME_DEFAULT, help="Target community group name.")
    parser.add_argument("--collenso-suburb", default=COLLENSO_SUBURB_DEFAULT, help="Target suburb for Collenso reports.")
    parser.add_argument("--collenso-owner", default=COLLENSO_OWNER_DEFAULT, help="Owner account used to create/manage group members.")
    parser.add_argument("--json-out", help="Optional path to write summary JSON.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    users = [u.strip() for u in args.users.split(",") if u.strip()]
    if not users:
        raise SystemExit("At least one user is required via --users")
    if args.concurrency <= 0:
        raise SystemExit("--concurrency must be > 0")
    if args.iterations <= 0:
        raise SystemExit("--iterations must be > 0")
    if args.min_delay_ms < 0 or args.max_delay_ms < args.min_delay_ms:
        raise SystemExit("Invalid delay range")

    collenso_owner = args.collenso_owner.strip() or COLLENSO_OWNER_DEFAULT
    if (not args.read_only) and collenso_owner not in users:
        users.insert(0, collenso_owner)

    stats = StatsCollector()
    started = time.perf_counter()

    def build_worker(worker_index: int) -> BotWorker:
        user_id = users[worker_index % len(users)]
        return BotWorker(
            worker_id=worker_index,
            user_id=user_id,
            password=args.password,
            base_url=args.base_url,
            iterations=args.iterations,
            read_only=args.read_only,
            min_delay_ms=args.min_delay_ms,
            max_delay_ms=args.max_delay_ms,
            annika_user=args.annika_user.strip() or ANNIKA_USER_DEFAULT,
            annika_force_posts=args.annika_force_posts,
            collenso_group_name=args.collenso_group_name.strip() or COLLENSO_GROUP_NAME_DEFAULT,
            collenso_suburb=args.collenso_suburb.strip() or COLLENSO_SUBURB_DEFAULT,
            collenso_owner=collenso_owner,
            collenso_core_users=COLLENSO_CORE_USERS,
            stats=stats,
            seed=args.seed + worker_index,
        )

    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        futures = [pool.submit(build_worker(i).run) for i in range(args.concurrency)]
        for future in futures:
            future.result()

    duration_s = time.perf_counter() - started
    summary = _render_summary(stats=stats, duration_s=duration_s)

    print(json.dumps(summary, indent=2, sort_keys=True))
    if args.json_out:
        out_path = Path(args.json_out)
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    return 0 if summary["total_errors"] == 0 else 2


if __name__ == "__main__":
    raise SystemExit(main())
