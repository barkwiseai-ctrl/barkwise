import os
from dataclasses import dataclass
from datetime import datetime, timedelta
from typing import Optional


@dataclass(frozen=True)
class SeededProviderRecord:
    id: str
    owner_user_id: str
    name: str
    category: str
    suburb: str
    description: str
    full_description: str
    price_from: int
    latitude: float
    longitude: float
    rating: float
    review_count: int
    status: str = "active"
    image_urls: tuple[str, ...] = ()


@dataclass(frozen=True)
class SeededReviewRecord:
    id: str
    provider_id: str
    author: str
    rating: int
    comment: str


@dataclass(frozen=True)
class SeededGroupRecord:
    id: str
    name: str
    suburb: str
    official: bool
    owner_user_id: Optional[str]


@dataclass(frozen=True)
class SeededMembershipRecord:
    group_id: str
    user_id: str
    status: str


@dataclass(frozen=True)
class SeededEventRecord:
    id: str
    title: str
    description: str
    suburb: str
    created_by: str
    group_id: Optional[str] = None
    location_name: Optional[str] = None
    location_latitude: Optional[float] = None
    location_longitude: Optional[float] = None
    recurrence: str = "none"
    recurrence_interval: int = 1
    status: str = "approved"
    attendee_user_ids: tuple[str, ...] = ()
    date_offset_days: int = 2


def mvp_bootstrap_enabled() -> bool:
    raw = os.getenv("MVP_BOOTSTRAP_ENABLED")
    if raw is not None:
        return raw.strip().lower() in {"1", "true", "yes", "on"}
    environment = os.getenv("ENVIRONMENT", "").strip().lower()
    if environment in {"prod", "production"}:
        return False
    return True


def seeded_providers() -> tuple[SeededProviderRecord, ...]:
    default_images = (
        "https://images.unsplash.com/photo-1517849845537-4d257902454a",
        "https://images.unsplash.com/photo-1548199973-03cce0bbc87b",
    )
    return (
        SeededProviderRecord(
            id="svc_1",
            owner_user_id="user_1",
            name="Surry Hills Groom Club",
            category="grooming",
            suburb="Surry Hills",
            description="Gentle grooming for anxious and first-time dogs.",
            full_description="Gentle grooming for anxious and first-time dogs with quiet handling, coat notes, and clear pickup windows.",
            price_from=68,
            latitude=-33.8886,
            longitude=151.2094,
            rating=4.8,
            review_count=2,
            image_urls=default_images,
        ),
        SeededProviderRecord(
            id="svc_2",
            owner_user_id="user_3",
            name="Neighbourhood Walk Co",
            category="dog_walking",
            suburb="Surry Hills",
            description="Structured solo and paired walks with behavior notes.",
            full_description="Structured solo and paired walks with behavior notes, hydration checks, and predictable pickup windows.",
            price_from=29,
            latitude=-33.8878,
            longitude=151.2110,
            rating=4.7,
            review_count=2,
            image_urls=default_images,
        ),
        SeededProviderRecord(
            id="svc_3",
            owner_user_id="user_4",
            name="Redfern Coat Studio",
            category="grooming",
            suburb="Redfern",
            description="Stress-aware grooms with hygiene-first handling.",
            full_description="Stress-aware grooms with hygiene-first handling, calm transitions, and practical after-care handover notes.",
            price_from=74,
            latitude=-33.8928,
            longitude=151.2040,
            rating=4.9,
            review_count=3,
            image_urls=default_images,
        ),
        SeededProviderRecord(
            id="svc_4",
            owner_user_id="user_3",
            name="Quick Trim Surry",
            category="grooming",
            suburb="Surry Hills",
            description="Fast tidy-ups and full coats with flexible weekday slots.",
            full_description="Fast tidy-ups and full coats with flexible weekday slots, photo updates, and transparent timing.",
            price_from=61,
            latitude=-33.8893,
            longitude=151.2105,
            rating=4.6,
            review_count=1,
            image_urls=default_images,
        ),
        SeededProviderRecord(
            id="svc_5",
            owner_user_id="user_1",
            name="Parkside Walkers Newtown",
            category="dog_walking",
            suburb="Newtown",
            description="Route-matched dog walks with safety-first pacing.",
            full_description="Route-matched dog walks with safety-first pacing, photo updates, and reinforced sit-wait basics.",
            price_from=27,
            latitude=-33.8981,
            longitude=151.1742,
            rating=4.7,
            review_count=1,
            image_urls=default_images,
        ),
    )


def seeded_reviews() -> tuple[SeededReviewRecord, ...]:
    return (
        SeededReviewRecord("r_1", "svc_1", "Casey", 5, "Patient with nervous dogs and really clear on pickup timing."),
        SeededReviewRecord("r_2", "svc_1", "Riley", 4, "Gentle groom and good coat notes afterward."),
        SeededReviewRecord("r_3", "svc_2", "Jordan", 5, "Reliable walks and consistent updates."),
        SeededReviewRecord("r_4", "svc_2", "Morgan", 4, "Easy to coordinate and great with leash manners."),
        SeededReviewRecord("r_5", "svc_3", "Taylor", 5, "Very clean setup and calm handling."),
        SeededReviewRecord("r_6", "svc_3", "Alex", 5, "Best grooming handover notes we have had."),
        SeededReviewRecord("r_7", "svc_3", "Jamie", 4, "Lovely result and patient with our older dog."),
        SeededReviewRecord("r_8", "svc_4", "Harper", 5, "Quick tidy-up and friendly check-in."),
        SeededReviewRecord("r_9", "svc_5", "Drew", 4, "Dependable walking service."),
    )


def seeded_groups() -> tuple[SeededGroupRecord, ...]:
    return (
        SeededGroupRecord(
            id="g_official_surryhills",
            name="Surry Hills Official Pet Community",
            suburb="Surry Hills",
            official=True,
            owner_user_id="user_1",
        ),
        SeededGroupRecord(
            id="g_official_newtown",
            name="Newtown Official Pet Community",
            suburb="Newtown",
            official=True,
            owner_user_id="user_3",
        ),
        SeededGroupRecord(
            id="g_official_redfern",
            name="Redfern Official Pet Community",
            suburb="Redfern",
            official=True,
            owner_user_id="user_4",
        ),
        SeededGroupRecord(
            id="g_user_collenso_dogpark",
            name="Collenso Dog Park",
            suburb="Sunshine West",
            official=False,
            owner_user_id="annika",
        ),
    )


def seeded_memberships() -> tuple[SeededMembershipRecord, ...]:
    return (
        SeededMembershipRecord("g_official_surryhills", "user_1", "member"),
        SeededMembershipRecord("g_official_surryhills", "user_2", "member"),
        SeededMembershipRecord("g_official_surryhills", "user_3", "member"),
        SeededMembershipRecord("g_official_newtown", "user_3", "member"),
        SeededMembershipRecord("g_official_redfern", "user_4", "member"),
        SeededMembershipRecord("g_user_collenso_dogpark", "annika", "member"),
        SeededMembershipRecord("g_user_collenso_dogpark", "snowy", "member"),
        SeededMembershipRecord("g_user_collenso_dogpark", "sesame", "member"),
    )


def seeded_events() -> tuple[SeededEventRecord, ...]:
    return (
        SeededEventRecord(
            id="evt_000",
            title="Surry Hills Pack Walk",
            description="Casual Saturday morning meet-up for social dogs.",
            suburb="Surry Hills",
            created_by="user_1",
            group_id="g_official_surryhills",
            location_name="Prince Alfred Park",
            location_latitude=-33.8930,
            location_longitude=151.2070,
            attendee_user_ids=("user_1", "user_2"),
            date_offset_days=2,
        ),
    )


def seeded_event_date_iso(*, offset_days: int) -> str:
    return (datetime.utcnow() + timedelta(days=offset_days)).replace(microsecond=0).isoformat() + "Z"
