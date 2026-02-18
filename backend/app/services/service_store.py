import json
import math
import os
import sqlite3
from dataclasses import dataclass
from datetime import date, datetime, timedelta
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
                        longitude REAL NOT NULL
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
        ]

        with self._lock:
            with self._connect() as conn:
                for provider in seed_providers:
                    conn.execute(
                        """
                        INSERT OR IGNORE INTO providers (
                            id, name, category, suburb, rating, review_count, price_from,
                            description, full_description, image_urls_json, latitude, longitude
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
        )

    def list_providers(
        self,
        category: Optional[str] = None,
        suburb: Optional[str] = None,
        min_rating: Optional[float] = None,
        max_distance_km: Optional[float] = None,
        user_lat: Optional[float] = None,
        user_lng: Optional[float] = None,
        q: Optional[str] = None,
        sort_by: str = "relevance",
        limit: int = 200,
    ) -> List[ServiceProvider]:
        with self._lock:
            with self._connect() as conn:
                rows = conn.execute("SELECT * FROM providers").fetchall()
                owner_rows = conn.execute("SELECT provider_id, user_id FROM provider_owners").fetchall()
                owner_map = {row["provider_id"]: row["user_id"] for row in owner_rows}

        origin = self._resolve_origin(suburb=suburb, user_lat=user_lat, user_lng=user_lng)
        result: List[ServiceProvider] = []
        query = (q or "").strip().lower()
        for row in rows:
            if category and row["category"] != category:
                continue
            if suburb and row["suburb"].lower() != suburb.lower():
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

            owner_user_id = owner_map.get(row["id"])
            result.append(self._row_to_provider(row, distance_km=distance, owner_user_id=owner_user_id))

        sort_key = (sort_by or "relevance").strip().lower()
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

    def get_available_slots(self, provider_id: str, slot_date: str) -> List[ServiceAvailabilitySlot]:
        # Auto-ensure nearby availability for convenience.
        self.ensure_availability(provider_id=provider_id, start_date=date.fromisoformat(slot_date), days=1)
        slots: List[ServiceAvailabilitySlot] = []
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
                    (provider_id, slot_date),
                ).fetchall()
                now_utc = datetime.utcnow()
                for row in rows:
                    blocked, reason = self._slot_is_blocked(conn, provider_id, row["slot_date"], row["time_slot"])
                    slot_dt = datetime.fromisoformat(f"{row['slot_date']}T{row['time_slot']}:00")
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
        self.ensure_availability(provider_id=request.provider_id, start_date=date.fromisoformat(request.date), days=1)

        with self._lock:
            with self._connect() as conn:
                self._cleanup_expired_holds(conn)
                provider = conn.execute("SELECT id FROM providers WHERE id = ?", (request.provider_id,)).fetchone()
                if not provider:
                    raise ValueError("Provider not found")

                slot = conn.execute(
                    """
                    SELECT id, is_booked FROM availability_slots
                    WHERE provider_id = ? AND slot_date = ? AND time_slot = ?
                    """,
                    (request.provider_id, request.date, request.time_slot),
                ).fetchone()
                if not slot:
                    raise ValueError("Time slot not available")
                slot_dt = datetime.fromisoformat(f"{request.date}T{request.time_slot}:00")
                if slot_dt - datetime.utcnow() < timedelta(hours=2):
                    raise ValueError("Booking cutoff applies for this slot")

                conn.execute(
                    """
                    DELETE FROM booking_holds
                    WHERE owner_user_id = ? AND provider_id = ? AND booking_date = ? AND time_slot = ?
                    """,
                    (request.user_id, request.provider_id, request.date, request.time_slot),
                )
                blocked, reason = self._slot_is_blocked(conn, request.provider_id, request.date, request.time_slot)
                if blocked:
                    raise ValueError(f"Time slot unavailable ({reason})")

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
        with self._lock:
            with self._connect() as conn:
                query = "SELECT b.* FROM bookings b"
                params: List[Any] = []
                if user_id and role == "provider":
                    query += " JOIN provider_owners po ON po.provider_id = b.provider_id WHERE po.user_id = ?"
                    params.append(user_id)
                elif user_id and role == "owner":
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
        self.ensure_availability(provider_id=request.provider_id, start_date=date.fromisoformat(request.date), days=1)

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
                    raise ValueError("Time slot not available")

                slot_dt = datetime.fromisoformat(f"{request.date}T{request.time_slot}:00")
                if slot_dt - datetime.utcnow() < timedelta(hours=2):
                    raise ValueError("Booking cutoff applies for this slot")

                blocked, reason = self._slot_is_blocked(conn, request.provider_id, request.date, request.time_slot)
                if blocked:
                    raise ValueError(f"Time slot unavailable ({reason})")

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
                    raise ValueError("Booking not found")

                current_status = str(row["status"])
                if current_status in BOOKING_TERMINAL_STATUSES:
                    raise ValueError("Booking is already terminal")

                next_status = update.status
                if next_status not in allowed_transitions.get(current_status, set()):
                    raise ValueError(f"Invalid status transition: {current_status} -> {next_status}")

                owner_user_id = str(row["owner_user_id"])
                provider_owner = conn.execute(
                    "SELECT user_id FROM provider_owners WHERE provider_id = ?",
                    (row["provider_id"],),
                ).fetchone()
                provider_user_id = provider_owner["user_id"] if provider_owner else ""

                if next_status in {"provider_confirmed", "provider_declined", "in_progress", "completed", "cancelled_by_provider"}:
                    if update.actor_user_id != provider_user_id:
                        raise ValueError("Only provider can apply this status")
                if next_status in {"cancelled_by_owner", "reschedule_requested"}:
                    if update.actor_user_id != owner_user_id:
                        raise ValueError("Only owner can apply this status")
                if next_status == "rescheduled" and update.actor_user_id != provider_user_id:
                    raise ValueError("Only provider can finalize reschedule")

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
        with self._lock:
            with self._connect() as conn:
                owner = conn.execute("SELECT user_id FROM provider_owners WHERE provider_id = ?", (provider_id,)).fetchone()
                if not owner:
                    raise ValueError("Provider owner not found")
                if owner["user_id"] != request.actor_user_id:
                    raise ValueError("Only provider owner can create blackout")

                exists = conn.execute(
                    """
                    SELECT id FROM provider_blackout_slots
                    WHERE provider_id = ? AND slot_date = ? AND time_slot = ?
                    """,
                    (provider_id, request.date, request.time_slot),
                ).fetchone()
                if exists:
                    raise ValueError("Blackout already exists")

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
        with self._lock:
            with self._connect() as conn:
                self._cleanup_expired_holds(conn)
                events: List[CalendarEvent] = []
                booking_rows: List[sqlite3.Row] = []

                if role in {"all", "owner"}:
                    booking_rows.extend(
                        conn.execute(
                            """
                            SELECT * FROM bookings
                            WHERE owner_user_id = ? AND booking_date BETWEEN ? AND ?
                            """,
                            (user_id, date_from, date_to),
                        ).fetchall()
                    )

                if role in {"all", "provider"}:
                    booking_rows.extend(
                        conn.execute(
                            """
                            SELECT b.*
                            FROM bookings b
                            JOIN provider_owners po ON po.provider_id = b.provider_id
                            WHERE po.user_id = ? AND b.booking_date BETWEEN ? AND ?
                            """,
                            (user_id, date_from, date_to),
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
                    (user_id, date_from, date_to),
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

                if role in {"all", "provider"}:
                    blackout_rows = conn.execute(
                        """
                        SELECT bs.*, po.user_id AS owner_user_id
                        FROM provider_blackout_slots bs
                        JOIN provider_owners po ON po.provider_id = bs.provider_id
                        WHERE po.user_id = ? AND bs.slot_date BETWEEN ? AND ?
                        """,
                        (user_id, date_from, date_to),
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
        coords = self._resolve_origin(suburb=suburb, user_lat=latitude, user_lng=longitude)
        lat, lng = coords if coords else (-33.8889, 151.2111)
        provider_id = f"svc_{uuid4().hex[:8]}"

        full_description = full_description or description
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
                        description, full_description, image_urls_json, latitude, longitude
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        provider_id,
                        name,
                        category,
                        suburb,
                        5.0,
                        0,
                        int(price_from),
                        description,
                        full_description,
                        json.dumps(images),
                        lat,
                        lng,
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
        return self._row_to_provider(row)

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
