import json
import math
import os
import sqlite3
from dataclasses import dataclass
from datetime import date, datetime, time, timedelta
from pathlib import Path
from threading import Lock
from typing import Any, Dict, List, Optional, Tuple
from uuid import uuid4

from app.models import (
    Booking,
    BookingHold,
    BookingHoldRequest,
    BookingRequest,
    BookingStatusUpdateRequest,
    CalendarEvent,
    ProviderBlackout,
    ProviderBlackoutRequest,
    Review,
    ServiceAvailabilitySlot,
    ServiceProvider,
)


BOOKING_ACTIVE_STATUSES = {
    "requested",
    "provider_confirmed",
    "in_progress",
    "reschedule_requested",
    "rescheduled",
}

BOOKING_TERMINAL_STATUSES = {
    "provider_declined",
    "completed",
    "cancelled_by_owner",
    "cancelled_by_provider",
}


SUBURB_COORDS = {
    "Surry Hills": (-33.8889, 151.2111),
    "Newtown": (-33.8981, 151.1742),
    "Redfern": (-33.8928, 151.2040),
    "Sunshine West": (-37.7919, 144.8164),
    "Mountain View": (37.3861, -122.0839),
}

ACCOUNT_LABELS = {
    "user_1": "Account A",
    "user_2": "Account B",
    "user_3": "Account C",
    "user_4": "Account D",
}


class ServiceStoreError(ValueError):
    """Base class for user-visible service-store errors."""


class ServiceStoreValidationError(ServiceStoreError):
    pass


class ServiceStoreNotFoundError(ServiceStoreError):
    pass


class ServiceStoreConflictError(ServiceStoreError):
    pass


class ServiceStorePermissionError(ServiceStoreError):
    pass


@dataclass
class ServiceStore:
    db_path: str

    def __post_init__(self) -> None:
        self._lock = Lock()
        path = Path(self.db_path)
        path.parent.mkdir(parents=True, exist_ok=True)
        self.db_path = str(path)
        self._init_db()
        self._seed_if_needed()

    def _connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path, check_same_thread=False)
        conn.row_factory = sqlite3.Row
        return conn

    def _init_db(self) -> None:
        with self._lock:
            with self._connect() as conn:
                conn.execute(
                    """
                    CREATE TABLE IF NOT EXISTS providers (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        category TEXT NOT NULL,
                        suburb TEXT NOT NULL,
                        rating REAL NOT NULL,
                        review_count INTEGER NOT NULL,
                        price_from INTEGER NOT NULL,
                        description TEXT NOT NULL,
                        full_description TEXT NOT NULL,
                        image_urls_json TEXT NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        status TEXT NOT NULL DEFAULT 'active'
                    )
                    """
                )
                conn.execute(
                    """
                    CREATE TABLE IF NOT EXISTS reviews (
                        id TEXT PRIMARY KEY,
                        provider_id TEXT NOT NULL,
                        author TEXT NOT NULL,
                        rating INTEGER NOT NULL,
                        comment TEXT NOT NULL
                    )
                    """
                )
                conn.execute(
                    """
                    CREATE TABLE IF NOT EXISTS availability_slots (
                        id TEXT PRIMARY KEY,
                        provider_id TEXT NOT NULL,
                        slot_date TEXT NOT NULL,
                        time_slot TEXT NOT NULL,
                        is_booked INTEGER NOT NULL DEFAULT 0
                    )
                    """
                )
                conn.execute(
                    """
                    CREATE TABLE IF NOT EXISTS bookings (
                        id TEXT PRIMARY KEY,
                        owner_user_id TEXT NOT NULL DEFAULT 'guest_user',
                        provider_id TEXT NOT NULL,
                        pet_name TEXT NOT NULL,
                        booking_date TEXT NOT NULL,
                        time_slot TEXT NOT NULL,
                        note TEXT NOT NULL,
                        status TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """
                )
                conn.execute(
                    """
                    CREATE TABLE IF NOT EXISTS booking_holds (
                        id TEXT PRIMARY KEY,
                        owner_user_id TEXT NOT NULL,
                        provider_id TEXT NOT NULL,
                        booking_date TEXT NOT NULL,
                        time_slot TEXT NOT NULL,
                        expires_at TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """
                )
                conn.execute(
                    """
                    CREATE TABLE IF NOT EXISTS booking_status_history (
                        id TEXT PRIMARY KEY,
                        booking_id TEXT NOT NULL,
                        actor_user_id TEXT NOT NULL,
                        from_status TEXT NOT NULL,
                        to_status TEXT NOT NULL,
                        note TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """
                )
                conn.execute(
                    """
                    CREATE TABLE IF NOT EXISTS provider_blackout_slots (
                        id TEXT PRIMARY KEY,
                        provider_id TEXT NOT NULL,
                        slot_date TEXT NOT NULL,
                        time_slot TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        created_by TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """
                )
                conn.execute(
                    """
                    CREATE TABLE IF NOT EXISTS provider_owners (
                        provider_id TEXT PRIMARY KEY,
                        user_id TEXT NOT NULL
                    )
                    """
                )
                self._ensure_column(conn, "bookings", "owner_user_id", "TEXT NOT NULL DEFAULT 'guest_user'")
                self._ensure_column(conn, "providers", "status", "TEXT NOT NULL DEFAULT 'active'")
                conn.commit()
                conn.execute("UPDATE availability_slots SET is_booked = 0")
                conn.commit()

    def _ensure_column(self, conn: sqlite3.Connection, table: str, column: str, definition: str) -> None:
        columns = conn.execute(f"PRAGMA table_info({table})").fetchall()
        existing = {row["name"] for row in columns}
        if column in existing:
            return
        conn.execute(f"ALTER TABLE {table} ADD COLUMN {column} {definition}")

    def _seed_if_needed(self) -> None:
        seed_providers = [
            {
                "id": "svc_1",
                "name": "Happy Paws Walkers",
                "category": "dog_walking",
                "suburb": "Surry Hills",
                "rating": 4.8,
                "review_count": 128,
                "price_from": 25,
                "description": "30-60 minute neighborhood walks with photo updates.",
                "full_description": "Trusted local walkers offering flexible plans, GPS-tracked routes, and photo check-ins after each walk.",
                "image_urls": [
                    "https://images.unsplash.com/photo-1548199973-03cce0bbc87b",
                    "https://images.unsplash.com/photo-1517849845537-4d257902454a",
                ],
                "latitude": -33.8889,
                "longitude": 151.2111,
            },
            {
                "id": "svc_2",
                "name": "Urban Tail Walk Co",
                "category": "dog_walking",
                "suburb": "Newtown",
                "rating": 4.6,
                "review_count": 74,
                "price_from": 22,
                "description": "Reliable weekday walks and weekend pack sessions.",
                "full_description": "Structured walk plans for energetic dogs, including social pack walks and solo sessions for reactive pets.",
                "image_urls": [
                    "https://images.unsplash.com/photo-1518717758536-85ae29035b6d",
                    "https://images.unsplash.com/photo-1518020382113-a7e8fc38eac9",
                ],
                "latitude": -33.8981,
                "longitude": 151.1742,
            },
            {
                "id": "svc_3",
                "name": "Fresh Fur Groom Studio",
                "category": "grooming",
                "suburb": "Redfern",
                "rating": 4.9,
                "review_count": 96,
                "price_from": 45,
                "description": "Bath, nail trim, and breed-specific grooming.",
                "full_description": "Gentle grooming sessions with skin-safe products, breed-specific styling, and stress-aware handling.",
                "image_urls": [
                    "https://images.unsplash.com/photo-1516734212186-65266f4f17c8",
                    "https://images.unsplash.com/photo-1525253013412-55c1a69a5738",
                ],
                "latitude": -33.8928,
                "longitude": 151.2040,
            },
            {
                "id": "svc_4",
                "name": "City Stride Canine Walkers",
                "category": "dog_walking",
                "suburb": "Surry Hills",
                "rating": 4.7,
                "review_count": 63,
                "price_from": 28,
                "description": "Structured solo walks with behavior notes after each outing.",
                "full_description": "Ideal for dogs needing routine. Includes solo walk focus, leash manners, and owner debrief.",
                "image_urls": [
                    "https://images.unsplash.com/photo-1444212477490-ca407925329e",
                    "https://images.unsplash.com/photo-1523480717984-24cba35ae1ef",
                ],
                "latitude": -33.8878,
                "longitude": 151.2137,
            },
            {
                "id": "svc_5",
                "name": "Paws & Play Newtown",
                "category": "dog_walking",
                "suburb": "Newtown",
                "rating": 4.5,
                "review_count": 58,
                "price_from": 24,
                "description": "Energetic group walks for social dogs in local parks.",
                "full_description": "Small-pack walks with controlled introductions and post-walk hydration checks.",
                "image_urls": [
                    "https://images.unsplash.com/photo-1537151608828-ea2b11777ee8",
                    "https://images.unsplash.com/photo-1517423440428-a5a00ad493e8",
                ],
                "latitude": -33.8999,
                "longitude": 151.1768,
            },
            {
                "id": "svc_6",
                "name": "Redfern Rover Routes",
                "category": "dog_walking",
                "suburb": "Redfern",
                "rating": 4.8,
                "review_count": 89,
                "price_from": 27,
                "description": "Neighborhood adventure walks with live location sharing.",
                "full_description": "Route variety around parks and quieter streets, with quick photo updates and flexible timing.",
                "image_urls": [
                    "https://images.unsplash.com/photo-1450778869180-41d0601e046e",
                    "https://images.unsplash.com/photo-1472053217156-31b42df2319f",
                ],
                "latitude": -33.8935,
                "longitude": 151.2018,
            },
            {
                "id": "svc_7",
                "name": "Eucalyptus Groom House",
                "category": "grooming",
                "suburb": "Surry Hills",
                "rating": 4.9,
                "review_count": 132,
                "price_from": 52,
                "description": "Calm one-on-one grooming for anxious and senior pets.",
                "full_description": "Low-noise grooming room, sensitive-skin products, and handling pace tailored to each pet.",
                "image_urls": [
                    "https://images.unsplash.com/photo-1516734212186-65266f4f17c8",
                    "https://images.unsplash.com/photo-1601758228041-f3b2795255f1",
                ],
                "latitude": -33.8872,
                "longitude": 151.2098,
            },
            {
                "id": "svc_8",
                "name": "King Street Pet Spa",
                "category": "grooming",
                "suburb": "Newtown",
                "rating": 4.6,
                "review_count": 80,
                "price_from": 48,
                "description": "Bath, brush, tidy clips, and coat care plans.",
                "full_description": "Practical grooming packages for regular maintenance, including coat and skin notes.",
                "image_urls": [
                    "https://images.unsplash.com/photo-1527529482837-4698179dc6ce",
                    "https://images.unsplash.com/photo-1522276498395-f4f68f7f8454",
                ],
                "latitude": -33.8977,
                "longitude": 151.1781,
            },
            {
                "id": "svc_9",
                "name": "Redfern Gentle Groomers",
                "category": "grooming",
                "suburb": "Redfern",
                "rating": 4.7,
                "review_count": 69,
                "price_from": 46,
                "description": "Breed-aware grooming with coat health tracking.",
                "full_description": "Trim plans for doodles, terriers, and double-coated breeds with owner follow-up notes.",
                "image_urls": [
                    "https://images.unsplash.com/photo-1525253013412-55c1a69a5738",
                    "https://images.unsplash.com/photo-1544568100-847a948585b9",
                ],
                "latitude": -33.8917,
                "longitude": 151.2056,
            },
            {
                "id": "svc_10",
                "name": "Laneway Leash Collective",
                "category": "dog_walking",
                "suburb": "Surry Hills",
                "rating": 4.4,
                "review_count": 37,
                "price_from": 21,
                "description": "Budget-friendly weekday lunchtime walks.",
                "full_description": "Great for office-hour families needing consistent weekday exercise windows.",
                "image_urls": [
                    "https://images.unsplash.com/photo-1543466835-00a7907e9de1",
                    "https://images.unsplash.com/photo-1548199973-03cce0bbc87b",
                ],
                "latitude": -33.8898,
                "longitude": 151.2149,
            },
            {
                "id": "svc_11",
                "name": "Inner West Wash & Style",
                "category": "grooming",
                "suburb": "Newtown",
                "rating": 4.8,
                "review_count": 104,
                "price_from": 50,
                "description": "Premium wash, de-shed and tidy styling.",
                "full_description": "Detailed grooming with seasonal coat management and optional nail care bundle.",
                "image_urls": [
                    "https://images.unsplash.com/photo-1522276498395-f4f68f7f8454",
                    "https://images.unsplash.com/photo-1601758228041-f3b2795255f1",
                ],
                "latitude": -33.9006,
                "longitude": 151.1725,
            },
            {
                "id": "svc_12",
                "name": "South Sydney Striders",
                "category": "dog_walking",
                "suburb": "Redfern",
                "rating": 4.7,
                "review_count": 77,
                "price_from": 26,
                "description": "Morning and evening dog-walk slots with flexible duration.",
                "full_description": "Reliable recurring slots, harness checks, and post-walk hydration support.",
                "image_urls": [
                    "https://images.unsplash.com/photo-1517423440428-a5a00ad493e8",
                    "https://images.unsplash.com/photo-1537151608828-ea2b11777ee8",
                ],
                "latitude": -33.8946,
                "longitude": 151.2029,
            },
            {
                "id": "svc_sw_1",
                "name": "Sunshine Coat Care",
                "category": "grooming",
                "suburb": "Sunshine West",
                "rating": 4.9,
                "review_count": 41,
                "price_from": 47,
                "description": "Neighbourhood grooming studio with gentle handling and quick turnarounds.",
                "full_description": "Ideal for regular maintenance trims, wash-and-dry sessions, and coat-friendly skin checks.",
                "image_urls": [
                    "https://images.unsplash.com/photo-1516734212186-65266f4f17c8",
                    "https://images.unsplash.com/photo-1522276498395-f4f68f7f8454",
                ],
                "latitude": -37.7915,
                "longitude": 144.8162,
            },
            {
                "id": "svc_sw_2",
                "name": "West Paws Styling",
                "category": "grooming",
                "suburb": "Sunshine West",
                "rating": 4.7,
                "review_count": 33,
                "price_from": 44,
                "description": "Clip, bath, nails, and tidy-up plans for active dogs.",
                "full_description": "Balanced grooming packages for short and medium coats with optional de-shed add-ons.",
                "image_urls": [
                    "https://images.unsplash.com/photo-1525253013412-55c1a69a5738",
                    "https://images.unsplash.com/photo-1601758228041-f3b2795255f1",
                ],
                "latitude": -37.8040,
                "longitude": 144.8300,
            },
            {
                "id": "svc_sw_3",
                "name": "Maribyrnong Mobile Groom",
                "category": "grooming",
                "suburb": "Sunshine West",
                "rating": 4.6,
                "review_count": 27,
                "price_from": 52,
                "description": "Mobile grooming van servicing Sunshine West and nearby streets.",
                "full_description": "Convenient at-home appointments with flexible windows and stress-aware routines.",
                "image_urls": [
                    "https://images.unsplash.com/photo-1544568100-847a948585b9",
                    "https://images.unsplash.com/photo-1516734212186-65266f4f17c8",
                ],
                "latitude": -37.7790,
                "longitude": 144.7810,
            },
            {
                "id": "svc_sw_4",
                "name": "Footscray Fur Finish",
                "category": "grooming",
                "suburb": "Sunshine West",
                "rating": 4.8,
                "review_count": 38,
                "price_from": 49,
                "description": "Premium finish trims for coat health and hygiene maintenance.",
                "full_description": "Detailed coat shaping, nail care, and paw tidy work with post-visit notes.",
                "image_urls": [
                    "https://images.unsplash.com/photo-1527529482837-4698179dc6ce",
                    "https://images.unsplash.com/photo-1601758228041-f3b2795255f1",
                ],
                "latitude": -37.7600,
                "longitude": 144.9000,
            },
            {
                "id": "svc_sw_5",
                "name": "Inner West Deluxe Grooming",
                "category": "grooming",
                "suburb": "Sunshine West",
                "rating": 4.5,
                "review_count": 19,
                "price_from": 56,
                "description": "Longer premium sessions for anxious or senior pets.",
                "full_description": "Extended appointments with calm pacing, break windows, and optional coat restoration treatment.",
                "image_urls": [
                    "https://images.unsplash.com/photo-1522276498395-f4f68f7f8454",
                    "https://images.unsplash.com/photo-1525253013412-55c1a69a5738",
                ],
                "latitude": -37.7200,
                "longitude": 144.9600,
            },
        ]
        seed_reviews = [
            ("r_1", "svc_1", "Amy", 5, "Very caring walker."),
            ("r_2", "svc_1", "Liam", 4, "On-time and friendly."),
            ("r_3", "svc_3", "Noah", 5, "Great groom every time."),
            ("r_4", "svc_2", "Sofia", 5, "My pup comes back calm and happy."),
            ("r_5", "svc_2", "Jasper", 4, "Good communication and photos."),
            ("r_6", "svc_4", "Priya", 5, "Detailed updates after each walk."),
            ("r_7", "svc_4", "Mason", 4, "Consistent timing and care."),
            ("r_8", "svc_5", "Ella", 4, "Great for social dogs."),
            ("r_9", "svc_5", "Nora", 5, "Super patient with shy pups."),
            ("r_10", "svc_6", "Lucas", 5, "Best walker in Redfern."),
            ("r_11", "svc_6", "Grace", 4, "Reliable and friendly."),
            ("r_12", "svc_7", "Anika", 5, "Handled my anxious dog gently."),
            ("r_13", "svc_7", "Theo", 5, "Excellent coat finish."),
            ("r_14", "svc_8", "Ruby", 4, "Good value grooming."),
            ("r_15", "svc_8", "Arlo", 5, "Great tidy trim."),
            ("r_16", "svc_9", "Finn", 5, "Professional and kind groomers."),
            ("r_17", "svc_9", "Mia", 4, "Very happy with the result."),
            ("r_18", "svc_10", "Jack", 4, "Affordable and dependable."),
            ("r_19", "svc_10", "Leah", 4, "Easy booking experience."),
            ("r_20", "svc_11", "Hannah", 5, "Premium quality service."),
            ("r_21", "svc_11", "Daniel", 5, "Best de-shed package so far."),
            ("r_22", "svc_12", "Chloe", 5, "Great energy and punctual."),
            ("r_23", "svc_12", "Ryan", 4, "Solid recurring walk plan."),
            ("r_24", "svc_3", "Isla", 5, "Our go-to groom studio."),
            ("r_sw_1", "svc_sw_1", "Mina", 5, "Excellent with nervous dogs."),
            ("r_sw_2", "svc_sw_2", "Owen", 4, "Good value and reliable quality."),
            ("r_sw_3", "svc_sw_3", "Pri", 5, "Mobile option is very convenient."),
            ("r_sw_4", "svc_sw_4", "Sasha", 5, "Great finish and clear communication."),
            ("r_sw_5", "svc_sw_5", "Dylan", 4, "Premium service worth it for seniors."),
        ]

        with self._lock:
            with self._connect() as conn:
                for provider in seed_providers:
                    conn.execute(
                        """
                        INSERT OR IGNORE INTO providers (
                            id, name, category, suburb, rating, review_count, price_from,
                            description, full_description, image_urls_json, latitude, longitude, status
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        (
                            provider["id"],
                            provider["name"],
                            provider["category"],
                            provider["suburb"],
                            provider["rating"],
                            provider["review_count"],
                            provider["price_from"],
                            provider["description"],
                            provider["full_description"],
                            json.dumps(provider["image_urls"]),
                            provider["latitude"],
                            provider["longitude"],
                            "active",
                        ),
                    )
                    # Seed sample ownership so users can act as providers and customers.
                    if provider["id"] in {"svc_2", "svc_8"}:
                        owner_user_id = "user_2"
                    elif provider["id"] in {"svc_1", "svc_7"}:
                        owner_user_id = "user_1"
                    elif provider["id"] in {"svc_3", "svc_9"}:
                        owner_user_id = "user_3"
                    elif provider["id"] in {"svc_4", "svc_10"}:
                        owner_user_id = "user_4"
                    else:
                        owner_user_id = f"owner_{provider['id']}"
                    conn.execute(
                        """
                        INSERT INTO provider_owners (provider_id, user_id)
                        VALUES (?, ?)
                        ON CONFLICT(provider_id) DO UPDATE SET user_id = excluded.user_id
                        """,
                        (provider["id"], owner_user_id),
                    )

                conn.executemany(
                    "INSERT OR IGNORE INTO reviews (id, provider_id, author, rating, comment) VALUES (?, ?, ?, ?, ?)",
                    seed_reviews,
                )

                conn.commit()

        # Seed availability for the next 14 days.
        for provider in seed_providers:
            self.ensure_availability(provider_id=provider["id"], start_date=date.today(), days=14)

    def _row_to_provider(
        self,
        row: sqlite3.Row,
        distance_km: Optional[float] = None,
        owner_user_id: Optional[str] = None,
    ) -> ServiceProvider:
        return ServiceProvider(
            id=row["id"],
            name=row["name"],
            category=row["category"],
            suburb=row["suburb"],
            rating=float(row["rating"]),
            review_count=int(row["review_count"]),
            price_from=int(row["price_from"]),
            description=row["description"],
            full_description=row["full_description"],
            image_urls=json.loads(row["image_urls_json"] or "[]"),
            latitude=float(row["latitude"]),
            longitude=float(row["longitude"]),
            distance_km=distance_km,
            owner_user_id=owner_user_id,
            owner_label=ACCOUNT_LABELS.get(owner_user_id, owner_user_id),
            status=row["status"] or "active",
        )

    def list_providers(
        self,
        category: Optional[str] = None,
        suburb: Optional[str] = None,
        user_id: Optional[str] = None,
        include_inactive: bool = False,
        min_rating: Optional[float] = None,
        max_distance_km: Optional[float] = None,
        user_lat: Optional[float] = None,
        user_lng: Optional[float] = None,
        q: Optional[str] = None,
        sort_by: str = "relevance",
        limit: int = 200,
    ) -> List[ServiceProvider]:
        sort_key = (sort_by or "relevance").strip().lower()
        allowed_sorts = {"relevance", "distance", "rating", "price_low", "price_high"}
        if sort_key not in allowed_sorts:
            raise ServiceStoreValidationError(
                "Invalid sort_by value. Allowed: relevance, distance, rating, price_low, price_high"
            )

        with self._lock:
            with self._connect() as conn:
                rows = conn.execute("SELECT * FROM providers").fetchall()
                owner_rows = conn.execute("SELECT provider_id, user_id FROM provider_owners").fetchall()
                owner_map = {row["provider_id"]: row["user_id"] for row in owner_rows}

        origin = self._resolve_origin(suburb=suburb, user_lat=user_lat, user_lng=user_lng)
        query = (q or "").strip().lower()

        def collect(filter_suburb: bool) -> List[ServiceProvider]:
            result: List[ServiceProvider] = []
            for row in rows:
                owner_user_id = owner_map.get(row["id"])
                provider_status = (row["status"] or "active").strip().lower()
                if provider_status != "active":
                    can_include_inactive = include_inactive and user_id and owner_user_id == user_id
                    if not can_include_inactive:
                        continue
                if category and row["category"] != category:
                    continue
                if filter_suburb and suburb and row["suburb"].lower() != suburb.lower():
                    continue
                if min_rating is not None and float(row["rating"]) < min_rating:
                    continue
                if query:
                    searchable = " ".join(
                        [
                            str(row["name"]),
                            str(row["description"]),
                            str(row["category"]),
                            str(row["suburb"]),
                        ]
                    ).lower()
                    if query not in searchable:
                        continue

                distance = None
                if origin:
                    distance = self._haversine_km(origin[0], origin[1], float(row["latitude"]), float(row["longitude"]))
                    if max_distance_km is not None and distance > max_distance_km:
                        continue

                result.append(self._row_to_provider(row, distance_km=distance, owner_user_id=owner_user_id))
            return result

        # If a specific suburb has no providers, fall back to broader results instead of blank state.
        result = collect(filter_suburb=bool(suburb))
        if suburb and not result:
            result = collect(filter_suburb=False)

        if sort_key == "distance":
            result.sort(key=lambda p: (p.distance_km if p.distance_km is not None else 9999, -p.rating))
        elif sort_key == "rating":
            result.sort(key=lambda p: (-p.rating, p.distance_km if p.distance_km is not None else 9999))
        elif sort_key == "price_low":
            result.sort(key=lambda p: (p.price_from, p.distance_km if p.distance_km is not None else 9999))
        elif sort_key == "price_high":
            result.sort(key=lambda p: (-p.price_from, p.distance_km if p.distance_km is not None else 9999))
        else:
            # Relevance: distance first (if known), then rating and lower price.
            result.sort(
                key=lambda p: (
                    p.distance_km if p.distance_km is not None else 9999,
                    -p.rating,
                    p.price_from,
                )
            )
        return result[:limit]

    def list_provider_owner_user_ids(self, provider_id: str) -> List[str]:
        with self._lock:
            with self._connect() as conn:
                rows = conn.execute(
                    "SELECT user_id FROM provider_owners WHERE provider_id = ?",
                    (provider_id,),
                ).fetchall()
        return [str(row["user_id"]) for row in rows if row["user_id"]]

    def get_provider_details(self, provider_id: str) -> Optional[Dict[str, Any]]:
        with self._lock:
            with self._connect() as conn:
                row = conn.execute("SELECT * FROM providers WHERE id = ?", (provider_id,)).fetchone()
                if not row:
                    return None
                owner = conn.execute(
                    "SELECT user_id FROM provider_owners WHERE provider_id = ?",
                    (provider_id,),
                ).fetchone()
                review_rows = conn.execute(
                    "SELECT * FROM reviews WHERE provider_id = ? ORDER BY id DESC",
                    (provider_id,),
                ).fetchall()

        owner_user_id = owner["user_id"] if owner else None
        provider = self._row_to_provider(row, owner_user_id=owner_user_id)
        reviews = [
            Review(
                id=r["id"],
                provider_id=r["provider_id"],
                author=r["author"],
                rating=int(r["rating"]),
                comment=r["comment"],
            )
            for r in review_rows
        ]
        return {"provider": provider, "reviews": reviews}

    def ensure_availability(self, provider_id: str, start_date: date, days: int = 7) -> None:
        slots = ["09:00", "11:00", "14:00", "16:00", "18:00"]
        with self._lock:
            with self._connect() as conn:
                for d in range(days):
                    current = (start_date + timedelta(days=d)).isoformat()
                    for slot in slots:
                        exists = conn.execute(
                            "SELECT 1 FROM availability_slots WHERE provider_id = ? AND slot_date = ? AND time_slot = ?",
                            (provider_id, current, slot),
                        ).fetchone()
                        if not exists:
                            conn.execute(
                                "INSERT INTO availability_slots (id, provider_id, slot_date, time_slot, is_booked) VALUES (?, ?, ?, ?, 0)",
                                (f"av_{uuid4().hex[:10]}", provider_id, current, slot),
                            )
                conn.commit()

    def _cleanup_expired_holds(self, conn: sqlite3.Connection) -> None:
        now_iso = datetime.utcnow().isoformat()
        conn.execute("DELETE FROM booking_holds WHERE expires_at <= ?", (now_iso,))

    def _slot_is_blocked(
        self,
        conn: sqlite3.Connection,
        provider_id: str,
        slot_date: str,
        time_slot: str,
        ignore_booking_id: Optional[str] = None,
    ) -> Tuple[bool, Optional[str]]:
        blackout = conn.execute(
            """
            SELECT id FROM provider_blackout_slots
            WHERE provider_id = ? AND slot_date = ? AND time_slot = ?
            LIMIT 1
            """,
            (provider_id, slot_date, time_slot),
        ).fetchone()
        if blackout:
            return True, "blackout"

        booking = conn.execute(
            """
            SELECT id, status FROM bookings
            WHERE provider_id = ? AND booking_date = ? AND time_slot = ?
            ORDER BY created_at DESC
            """,
            (provider_id, slot_date, time_slot),
        ).fetchall()
        for row in booking:
            if ignore_booking_id and row["id"] == ignore_booking_id:
                continue
            if row["status"] in BOOKING_ACTIVE_STATUSES:
                return True, "booked"

        hold = conn.execute(
            """
            SELECT id FROM booking_holds
            WHERE provider_id = ? AND booking_date = ? AND time_slot = ?
            LIMIT 1
            """,
            (provider_id, slot_date, time_slot),
        ).fetchone()
        if hold:
            return True, "held"
        return False, None

    def _parse_iso_date(self, value: str, *, field: str = "date") -> date:
        try:
            return date.fromisoformat(value)
        except ValueError as exc:
            raise ServiceStoreValidationError(f"Invalid {field}; expected YYYY-MM-DD") from exc

    def _parse_time_slot(self, value: str, *, field: str = "time_slot") -> time:
        try:
            return time.fromisoformat(value)
        except ValueError as exc:
            raise ServiceStoreValidationError(f"Invalid {field}; expected HH:MM") from exc

    def _parse_slot_datetime(self, slot_date: str, time_slot: str) -> datetime:
        parsed_date = self._parse_iso_date(slot_date)
        parsed_time = self._parse_time_slot(time_slot)
        return datetime.combine(parsed_date, parsed_time)

    def _assert_provider_exists(self, provider_id: str) -> None:
        with self._lock:
            with self._connect() as conn:
                provider = conn.execute("SELECT id FROM providers WHERE id = ?", (provider_id,)).fetchone()
        if not provider:
            raise ServiceStoreNotFoundError("Provider not found")

    def get_available_slots(self, provider_id: str, slot_date: str) -> List[ServiceAvailabilitySlot]:
        # Auto-ensure nearby availability for convenience.
        parsed_date = self._parse_iso_date(slot_date)
        self._assert_provider_exists(provider_id)
        self.ensure_availability(provider_id=provider_id, start_date=parsed_date, days=1)
        slots: List[ServiceAvailabilitySlot] = []
        normalized_date = parsed_date.isoformat()
        with self._lock:
            with self._connect() as conn:
                self._cleanup_expired_holds(conn)
                rows = conn.execute(
                    """
                    SELECT slot_date, time_slot, is_booked
                    FROM availability_slots
                    WHERE provider_id = ? AND slot_date = ?
                    ORDER BY time_slot
                    """,
                    (provider_id, normalized_date),
                ).fetchall()
                now_utc = datetime.utcnow()
                for row in rows:
                    blocked, reason = self._slot_is_blocked(conn, provider_id, row["slot_date"], row["time_slot"])
                    slot_dt = self._parse_slot_datetime(row["slot_date"], row["time_slot"])
                    if slot_dt - now_utc < timedelta(hours=2):
                        blocked = True
                        reason = "cutoff"
                    slots.append(
                        ServiceAvailabilitySlot(
                            date=row["slot_date"],
                            time_slot=row["time_slot"],
                            available=not blocked,
                            reason=reason,
                        )
                    )
                conn.commit()

        return slots

    def create_booking(self, request: BookingRequest) -> Booking:
        requested_slot = self._parse_slot_datetime(request.date, request.time_slot)
        self._assert_provider_exists(request.provider_id)
        self.ensure_availability(provider_id=request.provider_id, start_date=requested_slot.date(), days=1)

        with self._lock:
            with self._connect() as conn:
                self._cleanup_expired_holds(conn)
                slot = conn.execute(
                    """
                    SELECT id, is_booked FROM availability_slots
                    WHERE provider_id = ? AND slot_date = ? AND time_slot = ?
                    """,
                    (request.provider_id, request.date, request.time_slot),
                ).fetchone()
                if not slot:
                    raise ServiceStoreValidationError("Time slot not available")
                if requested_slot - datetime.utcnow() < timedelta(hours=2):
                    raise ServiceStoreValidationError("Booking cutoff applies for this slot")

                conn.execute(
                    """
                    DELETE FROM booking_holds
                    WHERE owner_user_id = ? AND provider_id = ? AND booking_date = ? AND time_slot = ?
                    """,
                    (request.user_id, request.provider_id, request.date, request.time_slot),
                )
                blocked, reason = self._slot_is_blocked(conn, request.provider_id, request.date, request.time_slot)
                if blocked:
                    raise ServiceStoreConflictError(f"Time slot unavailable ({reason})")

                booking = Booking(
                    id=f"b_{uuid4().hex[:8]}",
                    owner_user_id=request.user_id,
                    provider_id=request.provider_id,
                    pet_name=request.pet_name,
                    date=request.date,
                    time_slot=request.time_slot,
                    note=request.note,
                    status="requested",
                )

                conn.execute(
                    """
                    INSERT INTO bookings (id, owner_user_id, provider_id, pet_name, booking_date, time_slot, note, status, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        booking.id,
                        booking.owner_user_id,
                        booking.provider_id,
                        booking.pet_name,
                        booking.date,
                        booking.time_slot,
                        booking.note,
                        booking.status,
                        datetime.utcnow().isoformat(),
                    ),
                )
                conn.execute(
                    """
                    INSERT INTO booking_status_history (id, booking_id, actor_user_id, from_status, to_status, note, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        f"bsh_{uuid4().hex[:10]}",
                        booking.id,
                        request.user_id,
                        "none",
                        booking.status,
                        "booking requested",
                        datetime.utcnow().isoformat(),
                    ),
                )
                conn.commit()

        return booking

    def list_bookings(self, user_id: Optional[str] = None, role: Optional[str] = None) -> List[Booking]:
        allowed_roles = {None, "all", "owner", "provider"}
        normalized_role = role.strip().lower() if role else None
        if normalized_role not in allowed_roles:
            raise ServiceStoreValidationError("Invalid role value. Allowed: all, owner, provider")

        with self._lock:
            with self._connect() as conn:
                query = "SELECT b.* FROM bookings b"
                params: List[Any] = []
                if user_id and normalized_role == "provider":
                    query += " JOIN provider_owners po ON po.provider_id = b.provider_id WHERE po.user_id = ?"
                    params.append(user_id)
                elif user_id and normalized_role == "owner":
                    query += " WHERE b.owner_user_id = ?"
                    params.append(user_id)
                elif user_id:
                    query += (
                        " LEFT JOIN provider_owners po ON po.provider_id = b.provider_id "
                        " WHERE b.owner_user_id = ? OR po.user_id = ?"
                    )
                    params.extend([user_id, user_id])

                query += " ORDER BY b.created_at DESC"
                rows = conn.execute(
                    query,
                    tuple(params),
                ).fetchall()
        return [
            Booking(
                id=row["id"],
                owner_user_id=row["owner_user_id"],
                provider_id=row["provider_id"],
                pet_name=row["pet_name"],
                date=row["booking_date"],
                time_slot=row["time_slot"],
                note=row["note"],
                status=row["status"],
            )
            for row in rows
        ]

    def create_booking_hold(self, request: BookingHoldRequest, ttl_minutes: int = 15) -> BookingHold:
        requested_slot = self._parse_slot_datetime(request.date, request.time_slot)
        self._assert_provider_exists(request.provider_id)
        self.ensure_availability(provider_id=request.provider_id, start_date=requested_slot.date(), days=1)

        with self._lock:
            with self._connect() as conn:
                self._cleanup_expired_holds(conn)
                slot = conn.execute(
                    """
                    SELECT id FROM availability_slots
                    WHERE provider_id = ? AND slot_date = ? AND time_slot = ?
                    """,
                    (request.provider_id, request.date, request.time_slot),
                ).fetchone()
                if not slot:
                    raise ServiceStoreValidationError("Time slot not available")

                if requested_slot - datetime.utcnow() < timedelta(hours=2):
                    raise ServiceStoreValidationError("Booking cutoff applies for this slot")

                blocked, reason = self._slot_is_blocked(conn, request.provider_id, request.date, request.time_slot)
                if blocked:
                    raise ServiceStoreConflictError(f"Time slot unavailable ({reason})")

                expires_at = (datetime.utcnow() + timedelta(minutes=ttl_minutes)).isoformat()
                hold = BookingHold(
                    id=f"hold_{uuid4().hex[:8]}",
                    provider_id=request.provider_id,
                    owner_user_id=request.user_id,
                    date=request.date,
                    time_slot=request.time_slot,
                    expires_at=expires_at,
                )
                conn.execute(
                    """
                    INSERT INTO booking_holds (id, owner_user_id, provider_id, booking_date, time_slot, expires_at, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        hold.id,
                        hold.owner_user_id,
                        hold.provider_id,
                        hold.date,
                        hold.time_slot,
                        hold.expires_at,
                        datetime.utcnow().isoformat(),
                    ),
                )
                conn.commit()
                return hold

    def update_booking_status(self, booking_id: str, update: BookingStatusUpdateRequest) -> Booking:
        allowed_transitions: Dict[str, set[str]] = {
            "requested": {"provider_confirmed", "provider_declined", "cancelled_by_owner"},
            "provider_confirmed": {"in_progress", "cancelled_by_owner", "cancelled_by_provider", "reschedule_requested"},
            "in_progress": {"completed", "cancelled_by_provider"},
            "reschedule_requested": {"rescheduled", "cancelled_by_owner", "cancelled_by_provider"},
            "rescheduled": {"provider_confirmed", "cancelled_by_owner", "cancelled_by_provider"},
        }

        with self._lock:
            with self._connect() as conn:
                row = conn.execute("SELECT * FROM bookings WHERE id = ?", (booking_id,)).fetchone()
                if not row:
                    raise ServiceStoreNotFoundError("Booking not found")

                current_status = str(row["status"])
                if current_status in BOOKING_TERMINAL_STATUSES:
                    raise ServiceStoreConflictError("Booking is already terminal")

                next_status = update.status
                if next_status not in allowed_transitions.get(current_status, set()):
                    raise ServiceStoreValidationError(f"Invalid status transition: {current_status} -> {next_status}")

                owner_user_id = str(row["owner_user_id"])
                provider_owner = conn.execute(
                    "SELECT user_id FROM provider_owners WHERE provider_id = ?",
                    (row["provider_id"],),
                ).fetchone()
                provider_user_id = provider_owner["user_id"] if provider_owner else ""

                if next_status in {"provider_confirmed", "provider_declined", "in_progress", "completed", "cancelled_by_provider"}:
                    if update.actor_user_id != provider_user_id:
                        raise ServiceStorePermissionError("Only provider can apply this status")
                if next_status in {"cancelled_by_owner", "reschedule_requested"}:
                    if update.actor_user_id != owner_user_id:
                        raise ServiceStorePermissionError("Only owner can apply this status")
                if next_status == "rescheduled" and update.actor_user_id != provider_user_id:
                    raise ServiceStorePermissionError("Only provider can finalize reschedule")

                conn.execute("UPDATE bookings SET status = ?, note = ? WHERE id = ?", (next_status, update.note or row["note"], booking_id))
                conn.execute(
                    """
                    INSERT INTO booking_status_history (id, booking_id, actor_user_id, from_status, to_status, note, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        f"bsh_{uuid4().hex[:10]}",
                        booking_id,
                        update.actor_user_id,
                        current_status,
                        next_status,
                        update.note,
                        datetime.utcnow().isoformat(),
                    ),
                )
                conn.commit()

                updated = conn.execute("SELECT * FROM bookings WHERE id = ?", (booking_id,)).fetchone()
                return Booking(
                    id=updated["id"],
                    owner_user_id=updated["owner_user_id"],
                    provider_id=updated["provider_id"],
                    pet_name=updated["pet_name"],
                    date=updated["booking_date"],
                    time_slot=updated["time_slot"],
                    note=updated["note"],
                    status=updated["status"],
                )

    def create_provider_blackout(self, provider_id: str, request: ProviderBlackoutRequest) -> ProviderBlackout:
        self._parse_slot_datetime(request.date, request.time_slot)
        with self._lock:
            with self._connect() as conn:
                owner = conn.execute("SELECT user_id FROM provider_owners WHERE provider_id = ?", (provider_id,)).fetchone()
                if not owner:
                    raise ServiceStoreNotFoundError("Provider owner not found")
                if owner["user_id"] != request.actor_user_id:
                    raise ServiceStorePermissionError("Only provider owner can create blackout")

                exists = conn.execute(
                    """
                    SELECT id FROM provider_blackout_slots
                    WHERE provider_id = ? AND slot_date = ? AND time_slot = ?
                    """,
                    (provider_id, request.date, request.time_slot),
                ).fetchone()
                if exists:
                    raise ServiceStoreConflictError("Blackout already exists")

                blackout = ProviderBlackout(
                    id=f"blk_{uuid4().hex[:8]}",
                    provider_id=provider_id,
                    date=request.date,
                    time_slot=request.time_slot,
                    reason=request.reason,
                )
                conn.execute(
                    """
                    INSERT INTO provider_blackout_slots (id, provider_id, slot_date, time_slot, reason, created_by, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        blackout.id,
                        blackout.provider_id,
                        blackout.date,
                        blackout.time_slot,
                        blackout.reason,
                        request.actor_user_id,
                        datetime.utcnow().isoformat(),
                    ),
                )
                conn.commit()
                return blackout

    def list_provider_blackouts(self, provider_id: str) -> List[ProviderBlackout]:
        with self._lock:
            with self._connect() as conn:
                rows = conn.execute(
                    """
                    SELECT * FROM provider_blackout_slots
                    WHERE provider_id = ?
                    ORDER BY slot_date, time_slot
                    """,
                    (provider_id,),
                ).fetchall()
        return [
            ProviderBlackout(
                id=row["id"],
                provider_id=row["provider_id"],
                date=row["slot_date"],
                time_slot=row["time_slot"],
                reason=row["reason"],
            )
            for row in rows
        ]

    def list_calendar_events(
        self,
        user_id: str,
        date_from: str,
        date_to: str,
        role: str = "all",
    ) -> List[CalendarEvent]:
        parsed_from = self._parse_iso_date(date_from, field="date_from")
        parsed_to = self._parse_iso_date(date_to, field="date_to")
        if parsed_to < parsed_from:
            raise ServiceStoreValidationError("date_to must be on or after date_from")

        normalized_role = (role or "all").strip().lower()
        if normalized_role not in {"all", "owner", "provider"}:
            raise ServiceStoreValidationError("Invalid role value. Allowed: all, owner, provider")

        date_from_iso = parsed_from.isoformat()
        date_to_iso = parsed_to.isoformat()
        with self._lock:
            with self._connect() as conn:
                self._cleanup_expired_holds(conn)
                events: List[CalendarEvent] = []
                booking_rows: List[sqlite3.Row] = []

                if normalized_role in {"all", "owner"}:
                    booking_rows.extend(
                        conn.execute(
                            """
                            SELECT * FROM bookings
                            WHERE owner_user_id = ? AND booking_date BETWEEN ? AND ?
                            """,
                            (user_id, date_from_iso, date_to_iso),
                        ).fetchall()
                    )

                if normalized_role in {"all", "provider"}:
                    booking_rows.extend(
                        conn.execute(
                            """
                            SELECT b.*
                            FROM bookings b
                            JOIN provider_owners po ON po.provider_id = b.provider_id
                            WHERE po.user_id = ? AND b.booking_date BETWEEN ? AND ?
                            """,
                            (user_id, date_from_iso, date_to_iso),
                        ).fetchall()
                    )

                seen_booking_ids: set[str] = set()
                for row in booking_rows:
                    if row["id"] in seen_booking_ids:
                        continue
                    seen_booking_ids.add(row["id"])
                    role_value = "owner" if row["owner_user_id"] == user_id else "provider"
                    events.append(
                        CalendarEvent(
                            id=f"cal_booking_{row['id']}",
                            type="booking",
                            role=role_value,
                            title=f"Booking {row['pet_name']}",
                            subtitle=f"{row['time_slot']} • {row['status']}",
                            date=row["booking_date"],
                            time_slot=row["time_slot"],
                            status=row["status"],
                            provider_id=row["provider_id"],
                            booking_id=row["id"],
                        )
                    )

                hold_rows = conn.execute(
                    """
                    SELECT * FROM booking_holds
                    WHERE owner_user_id = ? AND booking_date BETWEEN ? AND ?
                    """,
                    (user_id, date_from_iso, date_to_iso),
                ).fetchall()
                for row in hold_rows:
                    events.append(
                        CalendarEvent(
                            id=f"cal_hold_{row['id']}",
                            type="hold",
                            role="owner",
                            title="Booking hold",
                            subtitle=f"Expires {row['expires_at']}",
                            date=row["booking_date"],
                            time_slot=row["time_slot"],
                            status="held",
                            provider_id=row["provider_id"],
                        )
                    )

                if normalized_role in {"all", "provider"}:
                    blackout_rows = conn.execute(
                        """
                        SELECT bs.*, po.user_id AS owner_user_id
                        FROM provider_blackout_slots bs
                        JOIN provider_owners po ON po.provider_id = bs.provider_id
                        WHERE po.user_id = ? AND bs.slot_date BETWEEN ? AND ?
                        """,
                        (user_id, date_from_iso, date_to_iso),
                    ).fetchall()
                    for row in blackout_rows:
                        events.append(
                            CalendarEvent(
                                id=f"cal_blackout_{row['id']}",
                                type="blackout",
                                role="provider",
                                title="Blackout slot",
                                subtitle=row["reason"] or "Unavailable",
                                date=row["slot_date"],
                                time_slot=row["time_slot"],
                                status="blackout",
                                provider_id=row["provider_id"],
                            )
                        )

                conn.commit()

        events.sort(key=lambda e: (e.date, e.time_slot, e.type))
        return events

    def add_provider(
        self,
        *,
        owner_user_id: str = "guest_user",
        name: str,
        category: str,
        suburb: str,
        description: str,
        price_from: int,
        full_description: Optional[str] = None,
        image_urls: Optional[List[str]] = None,
        latitude: Optional[float] = None,
        longitude: Optional[float] = None,
    ) -> ServiceProvider:
        if category not in {"dog_walking", "grooming"}:
            raise ServiceStoreValidationError("Invalid category. Allowed: dog_walking, grooming")
        if not name.strip():
            raise ServiceStoreValidationError("Provider name is required")
        if not suburb.strip():
            raise ServiceStoreValidationError("Suburb is required")
        if not description.strip():
            raise ServiceStoreValidationError("Description is required")
        if int(price_from) <= 0:
            raise ServiceStoreValidationError("price_from must be greater than 0")

        coords = self._resolve_origin(suburb=suburb, user_lat=latitude, user_lng=longitude)
        lat, lng = coords if coords else (-33.8889, 151.2111)
        provider_id = f"svc_{uuid4().hex[:8]}"

        full_description = (full_description or description).strip()
        images = image_urls or [
            "https://images.unsplash.com/photo-1450778869180-41d0601e046e",
            "https://images.unsplash.com/photo-1517849845537-4d257902454a",
        ]

        with self._lock:
            with self._connect() as conn:
                conn.execute(
                    """
                    INSERT INTO providers (
                        id, name, category, suburb, rating, review_count, price_from,
                        description, full_description, image_urls_json, latitude, longitude, status
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        provider_id,
                        name.strip(),
                        category,
                        suburb.strip(),
                        5.0,
                        0,
                        int(price_from),
                        description.strip(),
                        full_description,
                        json.dumps(images),
                        lat,
                        lng,
                        "active",
                    ),
                )
                conn.execute(
                    """
                    INSERT INTO provider_owners (provider_id, user_id)
                    VALUES (?, ?)
                    ON CONFLICT(provider_id) DO UPDATE SET user_id = excluded.user_id
                    """,
                    (provider_id, owner_user_id),
                )
                conn.commit()

        self.ensure_availability(provider_id=provider_id, start_date=date.today(), days=14)
        with self._connect() as conn:
            row = conn.execute("SELECT * FROM providers WHERE id = ?", (provider_id,)).fetchone()
        return self._row_to_provider(row, owner_user_id=owner_user_id)

    def update_provider(
        self,
        *,
        provider_id: str,
        actor_user_id: str,
        name: Optional[str] = None,
        suburb: Optional[str] = None,
        description: Optional[str] = None,
        price_from: Optional[int] = None,
        full_description: Optional[str] = None,
        image_urls: Optional[List[str]] = None,
        latitude: Optional[float] = None,
        longitude: Optional[float] = None,
    ) -> ServiceProvider:
        with self._lock:
            with self._connect() as conn:
                row = conn.execute("SELECT * FROM providers WHERE id = ?", (provider_id,)).fetchone()
                if not row:
                    raise ServiceStoreNotFoundError("Provider not found")

                owner = conn.execute(
                    "SELECT user_id FROM provider_owners WHERE provider_id = ?",
                    (provider_id,),
                ).fetchone()
                if not owner:
                    raise ServiceStoreNotFoundError("Provider owner not found")
                owner_user_id = str(owner["user_id"])
                if owner_user_id != actor_user_id:
                    raise ServiceStorePermissionError("Only provider owner can edit listing")

                updated_name = (name if name is not None else row["name"]).strip()
                updated_suburb = (suburb if suburb is not None else row["suburb"]).strip()
                updated_description = (description if description is not None else row["description"]).strip()
                updated_price_from = int(price_from if price_from is not None else row["price_from"])
                updated_full_description = (full_description if full_description is not None else row["full_description"]).strip()
                if not updated_full_description:
                    updated_full_description = updated_description

                if not updated_name:
                    raise ServiceStoreValidationError("Provider name is required")
                if not updated_suburb:
                    raise ServiceStoreValidationError("Suburb is required")
                if not updated_description:
                    raise ServiceStoreValidationError("Description is required")
                if updated_price_from <= 0:
                    raise ServiceStoreValidationError("price_from must be greater than 0")

                if image_urls is None:
                    updated_image_urls = json.loads(row["image_urls_json"] or "[]")
                else:
                    updated_image_urls = [url.strip() for url in image_urls if url and url.strip()]
                if not updated_image_urls:
                    updated_image_urls = [
                        "https://images.unsplash.com/photo-1450778869180-41d0601e046e",
                        "https://images.unsplash.com/photo-1517849845537-4d257902454a",
                    ]

                next_latitude = latitude if latitude is not None else float(row["latitude"])
                next_longitude = longitude if longitude is not None else float(row["longitude"])
                if suburb is not None and latitude is None and longitude is None:
                    resolved = self._resolve_origin(suburb=updated_suburb, user_lat=None, user_lng=None)
                    if resolved:
                        next_latitude, next_longitude = resolved

                conn.execute(
                    """
                    UPDATE providers
                    SET name = ?, suburb = ?, description = ?, price_from = ?,
                        full_description = ?, image_urls_json = ?, latitude = ?, longitude = ?
                    WHERE id = ?
                    """,
                    (
                        updated_name,
                        updated_suburb,
                        updated_description,
                        updated_price_from,
                        updated_full_description,
                        json.dumps(updated_image_urls),
                        float(next_latitude),
                        float(next_longitude),
                        provider_id,
                    ),
                )
                conn.commit()
                updated_row = conn.execute("SELECT * FROM providers WHERE id = ?", (provider_id,)).fetchone()
        return self._row_to_provider(updated_row, owner_user_id=owner_user_id)

    def cancel_provider(self, *, provider_id: str, actor_user_id: str) -> None:
        with self._lock:
            with self._connect() as conn:
                row = conn.execute("SELECT id FROM providers WHERE id = ?", (provider_id,)).fetchone()
                if not row:
                    raise ServiceStoreNotFoundError("Provider not found")

                owner = conn.execute(
                    "SELECT user_id FROM provider_owners WHERE provider_id = ?",
                    (provider_id,),
                ).fetchone()
                if not owner:
                    raise ServiceStoreNotFoundError("Provider owner not found")
                if str(owner["user_id"]) != actor_user_id:
                    raise ServiceStorePermissionError("Only provider owner can cancel listing")

                conn.execute("UPDATE providers SET status = 'cancelled' WHERE id = ?", (provider_id,))
                conn.execute(
                    """
                    UPDATE bookings
                    SET status = 'cancelled_by_provider'
                    WHERE provider_id = ? AND status IN ('requested', 'provider_confirmed', 'in_progress', 'reschedule_requested', 'rescheduled')
                    """,
                    (provider_id,),
                )
                conn.commit()

    def restore_provider(self, *, provider_id: str, actor_user_id: str) -> ServiceProvider:
        with self._lock:
            with self._connect() as conn:
                row = conn.execute("SELECT * FROM providers WHERE id = ?", (provider_id,)).fetchone()
                if not row:
                    raise ServiceStoreNotFoundError("Provider not found")
                owner = conn.execute(
                    "SELECT user_id FROM provider_owners WHERE provider_id = ?",
                    (provider_id,),
                ).fetchone()
                if not owner:
                    raise ServiceStoreNotFoundError("Provider owner not found")
                owner_user_id = str(owner["user_id"])
                if owner_user_id != actor_user_id:
                    raise ServiceStorePermissionError("Only provider owner can restore listing")
                if (row["status"] or "active") == "active":
                    raise ServiceStoreConflictError("Listing is already active")

                conn.execute("UPDATE providers SET status = 'active' WHERE id = ?", (provider_id,))
                conn.commit()
                updated = conn.execute("SELECT * FROM providers WHERE id = ?", (provider_id,)).fetchone()
        return self._row_to_provider(updated, owner_user_id=owner_user_id)

    def _resolve_origin(
        self,
        suburb: Optional[str],
        user_lat: Optional[float],
        user_lng: Optional[float],
    ) -> Optional[tuple[float, float]]:
        if user_lat is not None and user_lng is not None:
            return user_lat, user_lng
        if suburb:
            return SUBURB_COORDS.get(suburb.title())
        return None

    def _haversine_km(self, lat1: float, lon1: float, lat2: float, lon2: float) -> float:
        r = 6371.0
        phi1 = math.radians(lat1)
        phi2 = math.radians(lat2)
        dphi = math.radians(lat2 - lat1)
        dlambda = math.radians(lon2 - lon1)
        a = math.sin(dphi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2) ** 2
        c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
        return r * c


default_db = str(Path(__file__).resolve().parents[2] / "data" / "services.sqlite3")
service_store = ServiceStore(db_path=os.getenv("SERVICES_DB_PATH", default_db))
