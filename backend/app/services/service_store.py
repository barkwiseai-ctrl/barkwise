import json
import math
import os
import sqlite3
from dataclasses import dataclass
from datetime import date, datetime, time, timedelta
from pathlib import Path
from threading import Lock
from typing import Any, Dict, List, Literal, Optional, Set, Tuple
from uuid import uuid4

from app.data import group_memberships, groups
from app.models import (
    Booking,
    BookingStatusHistoryEntry,
    BookingHold,
    BookingHoldRequest,
    BookingRequest,
    BookingStatusUpdateRequest,
    CalendarEvent,
    ProviderInboxItem,
    ProviderBlackout,
    ProviderBlackoutRequest,
    Review,
    ServiceAvailabilitySlot,
    ServiceQuoteOffer,
    ServiceQuoteRequest,
    ServiceQuoteTarget,
    ServiceProvider,
    VetCoachProfile,
    VetCoachSessionResult,
    VetGroomerVerification,
    VetGroomerVerificationResult,
    VetSpotlightActivationResult,
)
from app.services.mvp_bootstrap import mvp_bootstrap_enabled, seeded_providers, seeded_reviews


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

DEFAULT_VET_USERS = {"user_1", "user_3"}
SEEDED_PROVIDER_IDS = {
    "svc_1",
    "svc_2",
    "svc_3",
    "svc_4",
    "svc_5",
    "svc_6",
    "svc_7",
    "svc_8",
    "svc_9",
    "svc_10",
    "svc_11",
    "svc_12",
    "svc_sw_1",
    "svc_sw_2",
    "svc_sw_3",
    "svc_sw_4",
    "svc_sw_5",
}
SEEDED_REVIEW_IDS = {
    "r_1",
    "r_2",
    "r_3",
    "r_4",
    "r_5",
    "r_6",
    "r_7",
    "r_8",
    "r_9",
    "r_10",
    "r_11",
    "r_12",
    "r_13",
    "r_14",
    "r_15",
    "r_16",
    "r_17",
    "r_18",
    "r_19",
    "r_20",
    "r_21",
    "r_22",
    "r_23",
    "r_24",
    "r_sw_1",
    "r_sw_2",
    "r_sw_3",
    "r_sw_4",
    "r_sw_5",
}
SEEDED_QUOTE_REQUEST_IDS = {"qr_seed_1", "qr_seed_2", "qr_seed_3", "qr_seed_4"}
SEEDED_QUOTE_TARGET_IDS = {"qrt_seed_1", "qrt_seed_2", "qrt_seed_3", "qrt_seed_4", "qrt_seed_5", "qrt_seed_6", "qrt_seed_7"}
SEEDED_BOOKING_IDS = {"bk_seed_1", "bk_seed_2", "bk_seed_3", "bk_seed_4", "bk_seed_5", "bk_seed_6", "bk_seed_7"}
SEEDED_BOOKING_HISTORY_IDS = {"bkh_seed_1", "bkh_seed_2", "bkh_seed_3", "bkh_seed_4"}
SEEDED_BLACKOUT_IDS = {"bo_seed_1", "bo_seed_2"}
SEEDED_VET_SESSION_IDS = {"vcs_seed_1", "vcs_seed_2"}
SEEDED_VET_VERIFICATION_IDS = {"vver_seed_1", "vver_seed_2"}


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
        configured_vets = {value.strip() for value in os.getenv("VET_USER_IDS", "").split(",") if value.strip()}
        self._vet_user_ids: Set[str] = configured_vets or set(DEFAULT_VET_USERS)
        self._init_db()
        if mvp_bootstrap_enabled():
            self._seed_if_needed()
        else:
            self._remove_seeded_content()

    def _connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path, check_same_thread=False)
        conn.row_factory = sqlite3.Row
        return conn

    @staticmethod
    def _direct_message_thread_id(user_a: str, user_b: str) -> str:
        first, second = sorted([user_a.strip(), user_b.strip()])
        return f"dm_{first}_{second}"

    def _provider_owner_user_id_with_conn(self, conn: sqlite3.Connection, provider_id: str) -> str:
        owner = conn.execute(
            "SELECT user_id FROM provider_owners WHERE provider_id = ? ORDER BY user_id ASC LIMIT 1",
            (provider_id,),
        ).fetchone()
        return str(owner["user_id"]) if owner else ""

    def _booking_from_row(
        self,
        *,
        conn: sqlite3.Connection,
        row: sqlite3.Row,
        viewer_user_id: Optional[str] = None,
    ) -> Booking:
        owner_user_id = str(row["owner_user_id"])
        provider_owner_user_id = self._provider_owner_user_id_with_conn(conn, str(row["provider_id"]))
        counterparty_user_id = provider_owner_user_id
        if viewer_user_id:
            normalized_viewer = viewer_user_id.strip()
            if normalized_viewer == provider_owner_user_id:
                counterparty_user_id = owner_user_id
            elif normalized_viewer == owner_user_id:
                counterparty_user_id = provider_owner_user_id
        thread_id = (
            self._direct_message_thread_id(owner_user_id, provider_owner_user_id)
            if provider_owner_user_id
            else None
        )
        return Booking(
            id=row["id"],
            owner_user_id=owner_user_id,
            provider_id=row["provider_id"],
            provider_owner_user_id=provider_owner_user_id or None,
            counterparty_user_id=counterparty_user_id or None,
            thread_id=thread_id,
            pet_name=row["pet_name"],
            date=row["booking_date"],
            time_slot=row["time_slot"],
            note=row["note"],
            status=row["status"],
        )

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
                conn.execute(
                    """
                    CREATE TABLE IF NOT EXISTS quote_requests (
                        id TEXT PRIMARY KEY,
                        user_id TEXT NOT NULL,
                        category TEXT NOT NULL,
                        suburb TEXT NOT NULL,
                        preferred_window TEXT NOT NULL,
                        pet_details TEXT NOT NULL,
                        note TEXT NOT NULL,
                        status TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """
                )
                conn.execute(
                    """
                    CREATE TABLE IF NOT EXISTS quote_request_targets (
                        id TEXT PRIMARY KEY,
                        quote_request_id TEXT NOT NULL,
                        provider_id TEXT NOT NULL,
                        owner_user_id TEXT NOT NULL,
                        status TEXT NOT NULL,
                        response_message TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        responded_at TEXT,
                        reminder_15_sent INTEGER NOT NULL DEFAULT 0,
                        reminder_60_sent INTEGER NOT NULL DEFAULT 0
                    )
                    """
                )
                conn.execute(
                    """
                    CREATE TABLE IF NOT EXISTS quote_target_offers (
                        id TEXT PRIMARY KEY,
                        quote_request_id TEXT NOT NULL,
                        provider_id TEXT NOT NULL,
                        actor_user_id TEXT NOT NULL,
                        price_cents INTEGER NOT NULL,
                        currency TEXT NOT NULL,
                        proposed_date TEXT NOT NULL,
                        proposed_time_slot TEXT NOT NULL,
                        expires_at TEXT NOT NULL,
                        note TEXT NOT NULL,
                        status TEXT NOT NULL DEFAULT 'active',
                        created_at TEXT NOT NULL
                    )
                    """
                )
                conn.execute(
                    """
                    CREATE TABLE IF NOT EXISTS vet_profiles (
                        user_id TEXT PRIMARY KEY,
                        spotlight_minutes INTEGER NOT NULL DEFAULT 0,
                        coaching_minutes INTEGER NOT NULL DEFAULT 0,
                        coaching_sessions INTEGER NOT NULL DEFAULT 0,
                        quality_score_sum REAL NOT NULL DEFAULT 0.0,
                        quality_score_count INTEGER NOT NULL DEFAULT 0,
                        grooming_reviews_count INTEGER NOT NULL DEFAULT 0,
                        highlighted_until TEXT,
                        updated_at TEXT NOT NULL
                    )
                    """
                )
                conn.execute(
                    """
                    CREATE TABLE IF NOT EXISTS vet_coach_sessions (
                        id TEXT PRIMARY KEY,
                        user_id TEXT NOT NULL,
                        duration_minutes INTEGER NOT NULL,
                        quality_score REAL NOT NULL,
                        topic TEXT NOT NULL,
                        note TEXT NOT NULL,
                        minutes_earned INTEGER NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """
                )
                conn.execute(
                    """
                    CREATE TABLE IF NOT EXISTS vet_groomer_verifications (
                        id TEXT PRIMARY KEY,
                        provider_id TEXT NOT NULL,
                        vet_user_id TEXT NOT NULL,
                        decision TEXT NOT NULL,
                        confidence_score REAL NOT NULL,
                        note TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        valid_until TEXT,
                        spotlight_minutes_earned INTEGER NOT NULL DEFAULT 0
                    )
                    """
                )
                self._ensure_column(conn, "bookings", "owner_user_id", "TEXT NOT NULL DEFAULT 'guest_user'")
                self._ensure_column(conn, "providers", "status", "TEXT NOT NULL DEFAULT 'active'")
                self._ensure_column(conn, "quote_request_targets", "reminder_15_sent", "INTEGER NOT NULL DEFAULT 0")
                self._ensure_column(conn, "quote_request_targets", "reminder_60_sent", "INTEGER NOT NULL DEFAULT 0")
                self._ensure_column(conn, "vet_profiles", "grooming_reviews_count", "INTEGER NOT NULL DEFAULT 0")
                self._ensure_column(conn, "vet_profiles", "updated_at", "TEXT NOT NULL DEFAULT ''")
                conn.commit()
                conn.execute("UPDATE availability_slots SET is_booked = 0")
                conn.commit()

    def _ensure_column(self, conn: sqlite3.Connection, table: str, column: str, definition: str) -> None:
        columns = conn.execute(f"PRAGMA table_info({table})").fetchall()
        existing = {row["name"] for row in columns}
        if column in existing:
            return
        conn.execute(f"ALTER TABLE {table} ADD COLUMN {column} {definition}")

    def _remove_seeded_content(self) -> None:
        with self._lock:
            with self._connect() as conn:
                conn.executemany("DELETE FROM reviews WHERE id = ?", [(value,) for value in sorted(SEEDED_REVIEW_IDS)])
                conn.executemany(
                    "DELETE FROM quote_request_targets WHERE id = ?",
                    [(value,) for value in sorted(SEEDED_QUOTE_TARGET_IDS)],
                )
                conn.executemany(
                    "DELETE FROM quote_requests WHERE id = ?",
                    [(value,) for value in sorted(SEEDED_QUOTE_REQUEST_IDS)],
                )
                conn.executemany("DELETE FROM booking_status_history WHERE id = ?", [(value,) for value in sorted(SEEDED_BOOKING_HISTORY_IDS)])
                conn.executemany("DELETE FROM booking_holds WHERE provider_id = ?", [(value,) for value in sorted(SEEDED_PROVIDER_IDS)])
                conn.executemany("DELETE FROM bookings WHERE id = ?", [(value,) for value in sorted(SEEDED_BOOKING_IDS)])
                conn.executemany("DELETE FROM provider_blackout_slots WHERE id = ?", [(value,) for value in sorted(SEEDED_BLACKOUT_IDS)])
                conn.executemany(
                    "DELETE FROM vet_coach_sessions WHERE id = ?",
                    [(value,) for value in sorted(SEEDED_VET_SESSION_IDS)],
                )
                conn.executemany(
                    "DELETE FROM vet_groomer_verifications WHERE id = ?",
                    [(value,) for value in sorted(SEEDED_VET_VERIFICATION_IDS)],
                )
                conn.executemany("DELETE FROM provider_owners WHERE provider_id = ?", [(value,) for value in sorted(SEEDED_PROVIDER_IDS)])
                conn.executemany("DELETE FROM availability_slots WHERE provider_id = ?", [(value,) for value in sorted(SEEDED_PROVIDER_IDS)])
                conn.executemany("DELETE FROM providers WHERE id = ?", [(value,) for value in sorted(SEEDED_PROVIDER_IDS)])
                conn.commit()

    def _seed_if_needed(self) -> None:
        seeded_provider_rows = seeded_providers()
        seeded_review_rows = seeded_reviews()
        review_ids_by_provider: Dict[str, List[str]] = {}
        for review in seeded_review_rows:
            review_ids_by_provider.setdefault(review.provider_id, []).append(review.id)

        with self._lock:
            with self._connect() as conn:
                for provider in seeded_provider_rows:
                    conn.execute(
                        """
                        INSERT INTO providers (
                            id, name, category, suburb, rating, review_count, price_from,
                            description, full_description, image_urls_json, latitude, longitude, status
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT(id) DO UPDATE SET
                            name = excluded.name,
                            category = excluded.category,
                            suburb = excluded.suburb,
                            rating = excluded.rating,
                            review_count = excluded.review_count,
                            price_from = excluded.price_from,
                            description = excluded.description,
                            full_description = excluded.full_description,
                            image_urls_json = excluded.image_urls_json,
                            latitude = excluded.latitude,
                            longitude = excluded.longitude,
                            status = excluded.status
                        """,
                        (
                            provider.id,
                            provider.name,
                            provider.category,
                            provider.suburb,
                            provider.rating,
                            provider.review_count,
                            provider.price_from,
                            provider.description,
                            provider.full_description,
                            json.dumps(list(provider.image_urls)),
                            provider.latitude,
                            provider.longitude,
                            provider.status,
                        ),
                    )
                    conn.execute(
                        """
                        INSERT INTO provider_owners (provider_id, user_id)
                        VALUES (?, ?)
                        ON CONFLICT(provider_id) DO UPDATE SET user_id = excluded.user_id
                        """,
                        (provider.id, provider.owner_user_id),
                    )

                for review in seeded_review_rows:
                    conn.execute(
                        """
                        INSERT INTO reviews (id, provider_id, author, rating, comment)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT(id) DO UPDATE SET
                            provider_id = excluded.provider_id,
                            author = excluded.author,
                            rating = excluded.rating,
                            comment = excluded.comment
                        """,
                        (
                            review.id,
                            review.provider_id,
                            review.author,
                            review.rating,
                            review.comment,
                        ),
                    )

                for provider in seeded_provider_rows:
                    review_count = len(review_ids_by_provider.get(provider.id, [])) or provider.review_count
                    conn.execute(
                        "UPDATE providers SET review_count = ? WHERE id = ?",
                        (review_count, provider.id),
                    )
                conn.commit()

        for provider in seeded_provider_rows:
            self.ensure_availability(provider_id=provider.id, start_date=date.today(), days=21)

    def _row_to_provider(
        self,
        row: sqlite3.Row,
        distance_km: Optional[float] = None,
        owner_user_id: Optional[str] = None,
        response_time_minutes: Optional[int] = None,
        local_bookers_this_month: int = 0,
        shared_group_bookers: int = 0,
        quote_sprint_tier: str = "none",
        quote_response_rate_pct: int = 0,
        quote_response_streak: int = 0,
        vet_checked: bool = False,
        vet_checked_until: Optional[str] = None,
        vet_checked_by: Optional[str] = None,
        highlighted_vet: Optional[str] = None,
        highlighted_vet_until: Optional[str] = None,
    ) -> ServiceProvider:
        social_proof: List[str] = []
        if vet_checked and vet_checked_until:
            social_proof.append(f"Vet-checked until {vet_checked_until[:10]}")
        if quote_sprint_tier != "none":
            social_proof.append(
                f"Quote Sprint {quote_sprint_tier.title()} • {quote_response_rate_pct}% response rate • {quote_response_streak} streak"
            )
        if local_bookers_this_month > 0:
            social_proof.append(f"Used by {local_bookers_this_month} pet owners in {row['suburb']} this month")
        if shared_group_bookers > 0:
            social_proof.append(f"{shared_group_bookers} members from your groups booked this provider")
        if response_time_minutes is not None:
            social_proof.append(f"Typically responds in about {response_time_minutes} min")
        if highlighted_vet and highlighted_vet_until:
            social_proof.append(f"Highlighted vet owner until {highlighted_vet_until[:10]}")

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
            response_time_minutes=response_time_minutes,
            local_bookers_this_month=local_bookers_this_month,
            shared_group_bookers=shared_group_bookers,
            social_proof=social_proof,
            quote_sprint_tier=quote_sprint_tier,
            quote_response_rate_pct=quote_response_rate_pct,
            quote_response_streak=quote_response_streak,
            vet_checked=vet_checked,
            vet_checked_until=vet_checked_until,
            vet_checked_by=vet_checked_by,
            highlighted_vet=highlighted_vet,
            highlighted_vet_until=highlighted_vet_until,
        )

    def _compute_provider_response_times(self, conn: sqlite3.Connection) -> Dict[str, int]:
        rows = conn.execute(
            """
            SELECT provider_id, AVG((julianday(responded_at) - julianday(created_at)) * 24 * 60) AS avg_minutes
            FROM quote_request_targets
            WHERE responded_at IS NOT NULL
            GROUP BY provider_id
            """
        ).fetchall()
        response_minutes: Dict[str, int] = {}
        for row in rows:
            minutes = row["avg_minutes"]
            if minutes is None:
                continue
            response_minutes[str(row["provider_id"])] = max(1, int(round(float(minutes))))
        return response_minutes

    def _is_vet_user(self, user_id: str) -> bool:
        if user_id in self._vet_user_ids:
            return True
        normalized = user_id.strip().lower()
        return normalized.startswith("vet_") or normalized.endswith("_vet")

    def _vet_badge_tier(self, sessions: int, quality_avg: float) -> str:
        if sessions >= 20 and quality_avg >= 0.90:
            return "platinum"
        if sessions >= 10 and quality_avg >= 0.85:
            return "gold"
        if sessions >= 5 and quality_avg >= 0.75:
            return "silver"
        if sessions >= 2 and quality_avg >= 0.60:
            return "bronze"
        return "none"

    def _vet_profile_from_row(self, row: sqlite3.Row) -> VetCoachProfile:
        quality_count = int(row["quality_score_count"] or 0)
        quality_sum = float(row["quality_score_sum"] or 0.0)
        quality_avg = (quality_sum / quality_count) if quality_count > 0 else 0.0
        sessions = int(row["coaching_sessions"] or 0)
        return VetCoachProfile(
            user_id=str(row["user_id"]),
            spotlight_minutes=int(row["spotlight_minutes"] or 0),
            coaching_minutes=int(row["coaching_minutes"] or 0),
            coaching_sessions=sessions,
            coach_quality_score=round(quality_avg, 3),
            highlighted_until=row["highlighted_until"] or None,
            badge_tier=self._vet_badge_tier(sessions=sessions, quality_avg=quality_avg),
        )

    def _ensure_vet_profile_row(self, conn: sqlite3.Connection, user_id: str) -> None:
        now_iso = datetime.utcnow().isoformat()
        conn.execute(
            """
            INSERT INTO vet_profiles (
                user_id, spotlight_minutes, coaching_minutes, coaching_sessions, quality_score_sum,
                quality_score_count, grooming_reviews_count, highlighted_until, updated_at
            )
            VALUES (?, 0, 0, 0, 0.0, 0, 0, NULL, ?)
            ON CONFLICT(user_id) DO NOTHING
            """,
            (user_id, now_iso),
        )

    def _load_vet_profile(self, conn: sqlite3.Connection, user_id: str, *, create_missing: bool = True) -> Optional[VetCoachProfile]:
        if create_missing:
            self._ensure_vet_profile_row(conn, user_id)
        row = conn.execute("SELECT * FROM vet_profiles WHERE user_id = ?", (user_id,)).fetchone()
        if not row:
            return None
        return self._vet_profile_from_row(row)

    def _compute_highlighted_vets(self, conn: sqlite3.Connection) -> Dict[str, str]:
        now_iso = datetime.utcnow().isoformat()
        rows = conn.execute(
            """
            SELECT user_id, highlighted_until
            FROM vet_profiles
            WHERE highlighted_until IS NOT NULL
              AND highlighted_until > ?
            """,
            (now_iso,),
        ).fetchall()
        highlighted: Dict[str, str] = {}
        for row in rows:
            user_id = str(row["user_id"])
            highlighted_until = row["highlighted_until"]
            if highlighted_until:
                highlighted[user_id] = str(highlighted_until)
        return highlighted

    def _compute_latest_vet_verifications(
        self,
        conn: sqlite3.Connection,
    ) -> Dict[str, Dict[str, Any]]:
        now_iso = datetime.utcnow().isoformat()
        rows = conn.execute(
            """
            SELECT v.provider_id, v.vet_user_id, v.decision, v.valid_until, v.created_at
            FROM vet_groomer_verifications v
            JOIN (
                SELECT provider_id, MAX(created_at) AS max_created_at
                FROM vet_groomer_verifications
                GROUP BY provider_id
            ) latest
                ON latest.provider_id = v.provider_id
               AND latest.max_created_at = v.created_at
            """
        ).fetchall()
        latest: Dict[str, Dict[str, Any]] = {}
        for row in rows:
            provider_id = str(row["provider_id"])
            decision = str(row["decision"] or "")
            valid_until = row["valid_until"]
            is_valid = decision == "approved" and bool(valid_until) and str(valid_until) > now_iso
            latest[provider_id] = {
                "vet_checked": bool(is_valid),
                "vet_checked_by": str(row["vet_user_id"]) if row["vet_user_id"] else None,
                "vet_checked_until": str(valid_until) if valid_until else None,
            }
        return latest

    def _compute_quote_sprint_metrics(self, conn: sqlite3.Connection) -> Dict[str, Dict[str, Any]]:
        rows = conn.execute(
            """
            SELECT provider_id, status, created_at, responded_at
            FROM quote_request_targets
            ORDER BY provider_id ASC, created_at DESC
            """
        ).fetchall()
        grouped: Dict[str, List[sqlite3.Row]] = {}
        for row in rows:
            provider_id = str(row["provider_id"])
            grouped.setdefault(provider_id, []).append(row)

        metrics: Dict[str, Dict[str, Any]] = {}
        for provider_id, provider_rows in grouped.items():
            total = len(provider_rows)
            responded_rows = [
                row
                for row in provider_rows
                if str(row["status"]) in {"accepted", "declined"} or bool(row["responded_at"])
            ]
            responded = len(responded_rows)
            response_rate_pct = int(round((responded / total) * 100)) if total > 0 else 0
            avg_minutes_values: List[float] = []
            for row in responded_rows:
                created_raw = str(row["created_at"] or "")
                responded_raw = str(row["responded_at"] or "")
                if not created_raw or not responded_raw:
                    continue
                try:
                    created_dt = datetime.fromisoformat(created_raw)
                    responded_dt = datetime.fromisoformat(responded_raw)
                except ValueError:
                    continue
                diff = (responded_dt - created_dt).total_seconds() / 60.0
                if diff >= 0:
                    avg_minutes_values.append(diff)
            avg_minutes = int(round(sum(avg_minutes_values) / len(avg_minutes_values))) if avg_minutes_values else None

            streak = 0
            for row in provider_rows:
                status = str(row["status"])
                if status in {"accepted", "declined"} or bool(row["responded_at"]):
                    streak += 1
                else:
                    break

            if total < 3:
                tier = "none"
            elif response_rate_pct >= 95 and (avg_minutes is not None and avg_minutes <= 15) and streak >= 5:
                tier = "platinum"
            elif response_rate_pct >= 90 and (avg_minutes is not None and avg_minutes <= 20) and streak >= 3:
                tier = "gold"
            elif response_rate_pct >= 75 and (avg_minutes is not None and avg_minutes <= 35):
                tier = "silver"
            elif response_rate_pct >= 60 and (avg_minutes is not None and avg_minutes <= 60):
                tier = "bronze"
            else:
                tier = "none"

            metrics[provider_id] = {
                "tier": tier,
                "response_rate_pct": response_rate_pct,
                "streak": streak,
            }
        return metrics

    def _quote_sprint_tier_score(self, tier: str) -> int:
        return {
            "none": 0,
            "bronze": 1,
            "silver": 2,
            "gold": 3,
            "platinum": 4,
        }.get(tier, 0)

    def _compute_local_bookers_this_month(self, conn: sqlite3.Connection) -> Dict[str, int]:
        month_start = date.today().replace(day=1).isoformat()
        rows = conn.execute(
            """
            SELECT provider_id, COUNT(DISTINCT owner_user_id) AS owner_count
            FROM bookings
            WHERE booking_date >= ?
              AND status NOT IN ('provider_declined', 'cancelled_by_owner', 'cancelled_by_provider')
            GROUP BY provider_id
            """,
            (month_start,),
        ).fetchall()
        return {str(row["provider_id"]): int(row["owner_count"]) for row in rows}

    def _compute_shared_group_bookers(
        self,
        conn: sqlite3.Connection,
        viewer_user_id: Optional[str],
    ) -> Dict[str, int]:
        if not viewer_user_id:
            return {}

        group_map: Dict[str, Set[str]] = {}
        for record in group_memberships:
            if record.status != "member":
                continue
            group_map.setdefault(record.user_id, set()).add(record.group_id)

        viewer_groups = group_map.get(viewer_user_id, set())
        if not viewer_groups:
            return {}

        rows = conn.execute(
            """
            SELECT provider_id, owner_user_id
            FROM bookings
            WHERE status NOT IN ('provider_declined', 'cancelled_by_owner', 'cancelled_by_provider')
            """
        ).fetchall()

        shared_counts: Dict[str, Set[str]] = {}
        for row in rows:
            booking_owner = str(row["owner_user_id"])
            owner_groups = group_map.get(booking_owner, set())
            if not owner_groups or viewer_groups.isdisjoint(owner_groups):
                continue
            provider_id = str(row["provider_id"])
            shared_counts.setdefault(provider_id, set()).add(booking_owner)

        return {provider_id: len(owner_ids) for provider_id, owner_ids in shared_counts.items()}

    def _infer_user_focus_suburb(
        self,
        user_id: Optional[str],
    ) -> Tuple[Optional[str], Literal["explicit_suburb", "dog_park_membership", "group_membership", "none"]]:
        normalized_user_id = (user_id or "").strip()
        if not normalized_user_id:
            return None, "none"

        group_by_id = {group.id: group for group in groups}
        scored_groups: List[Tuple[int, str, str]] = []
        for membership in group_memberships:
            if membership.user_id != normalized_user_id or membership.status != "member":
                continue
            group = group_by_id.get(membership.group_id)
            if not group or not group.suburb.strip():
                continue
            group_name = group.name.lower()
            is_dog_park = "dog park" in group_name or "dogpark" in group_name
            score = (2000 if is_dog_park else 0) + (200 if group.official else 0) + int(group.member_count)
            source = "dog_park_membership" if is_dog_park else "group_membership"
            scored_groups.append((score, group.suburb, source))

        if not scored_groups:
            return None, "none"
        scored_groups.sort(key=lambda item: (item[0], item[1]), reverse=True)
        _, suburb, source = scored_groups[0]
        return suburb, source

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
        limit: int = 1000,
    ) -> List[ServiceProvider]:
        sort_key = (sort_by or "relevance").strip().lower()
        allowed_sorts = {"relevance", "distance", "rating", "price_low", "price_high"}
        if sort_key not in allowed_sorts:
            raise ServiceStoreValidationError(
                "Invalid sort_by value. Allowed: relevance, distance, rating, price_low, price_high"
            )

        effective_suburb = suburb.strip() if suburb and suburb.strip() else None

        with self._lock:
            with self._connect() as conn:
                rows = conn.execute("SELECT * FROM providers").fetchall()
                owner_rows = conn.execute("SELECT provider_id, user_id FROM provider_owners").fetchall()
                owner_map = {row["provider_id"]: row["user_id"] for row in owner_rows}
                response_time_map = self._compute_provider_response_times(conn)
                local_bookers_map = self._compute_local_bookers_this_month(conn)
                shared_group_bookers_map = self._compute_shared_group_bookers(conn, viewer_user_id=user_id)
                quote_sprint_map = self._compute_quote_sprint_metrics(conn)
                vet_verification_map = self._compute_latest_vet_verifications(conn)
                highlighted_vet_map = self._compute_highlighted_vets(conn)

        origin = self._resolve_origin(suburb=effective_suburb, user_lat=user_lat, user_lng=user_lng)
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
                if filter_suburb and effective_suburb and row["suburb"].lower() != effective_suburb.lower():
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

                provider_id = str(row["id"])
                sprint = quote_sprint_map.get(
                    provider_id,
                    {"tier": "none", "response_rate_pct": 0, "streak": 0},
                )
                verification = vet_verification_map.get(
                    provider_id,
                    {"vet_checked": False, "vet_checked_by": None, "vet_checked_until": None},
                )
                highlighted_until = highlighted_vet_map.get(owner_user_id or "")
                highlighted_vet = ACCOUNT_LABELS.get(owner_user_id, owner_user_id) if highlighted_until and owner_user_id else None
                result.append(
                    self._row_to_provider(
                        row,
                        distance_km=distance,
                        owner_user_id=owner_user_id,
                        response_time_minutes=response_time_map.get(provider_id),
                        local_bookers_this_month=local_bookers_map.get(provider_id, 0),
                        shared_group_bookers=shared_group_bookers_map.get(provider_id, 0),
                        quote_sprint_tier=str(sprint.get("tier", "none")),
                        quote_response_rate_pct=int(sprint.get("response_rate_pct", 0)),
                        quote_response_streak=int(sprint.get("streak", 0)),
                        vet_checked=bool(verification.get("vet_checked", False)),
                        vet_checked_by=verification.get("vet_checked_by"),
                        vet_checked_until=verification.get("vet_checked_until"),
                        highlighted_vet=highlighted_vet,
                        highlighted_vet_until=highlighted_until,
                    )
                )
            return result

        # If a specific suburb has no providers, fall back to broader results instead of blank state.
        result = collect(filter_suburb=bool(effective_suburb))
        if effective_suburb and not result:
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
                    -self._quote_sprint_tier_score(p.quote_sprint_tier),
                    -p.rating,
                    p.price_from,
                )
            )
        return result[:limit]

    def recommend_providers(
        self,
        *,
        user_id: Optional[str] = None,
        category: Optional[str] = None,
        suburb: Optional[str] = None,
        min_rating: Optional[float] = None,
        max_distance_km: Optional[float] = None,
        user_lat: Optional[float] = None,
        user_lng: Optional[float] = None,
        limit: int = 6,
    ) -> Tuple[List[ServiceProvider], Optional[str], Literal["explicit_suburb", "dog_park_membership", "group_membership", "none"]]:
        if category is not None and category not in {"dog_walking", "grooming"}:
            raise ServiceStoreValidationError("Invalid category. Allowed: dog_walking, grooming")

        cleaned_suburb = suburb.strip() if suburb and suburb.strip() else None
        suburb_source = "explicit_suburb" if cleaned_suburb else "none"
        providers = self.list_providers(
            category=category,
            suburb=cleaned_suburb,
            user_id=user_id,
            include_inactive=False,
            min_rating=min_rating,
            max_distance_km=max_distance_km,
            user_lat=user_lat,
            user_lng=user_lng,
            sort_by="relevance",
            limit=limit,
        )
        return providers, cleaned_suburb, suburb_source

    def list_provider_owner_user_ids(self, provider_id: str) -> List[str]:
        with self._lock:
            with self._connect() as conn:
                rows = conn.execute(
                    "SELECT user_id FROM provider_owners WHERE provider_id = ?",
                    (provider_id,),
                ).fetchall()
        return [str(row["user_id"]) for row in rows if row["user_id"]]

    def _quote_request_from_row(self, row: sqlite3.Row) -> ServiceQuoteRequest:
        return ServiceQuoteRequest(
            id=row["id"],
            user_id=row["user_id"],
            category=row["category"],
            suburb=row["suburb"],
            preferred_window=row["preferred_window"],
            pet_details=row["pet_details"],
            note=row["note"],
            status=row["status"],
            created_at=row["created_at"],
            updated_at=row["updated_at"],
        )

    def _quote_targets_for_request(self, conn: sqlite3.Connection, quote_request_id: str) -> List[ServiceQuoteTarget]:
        rows = conn.execute(
            """
            SELECT t.*, p.name AS provider_name
            FROM quote_request_targets t
            JOIN providers p ON p.id = t.provider_id
            WHERE t.quote_request_id = ?
            ORDER BY t.created_at ASC
            """,
            (quote_request_id,),
        ).fetchall()
        return [
            ServiceQuoteTarget(
                provider_id=row["provider_id"],
                provider_name=row["provider_name"],
                owner_user_id=row["owner_user_id"],
                status=row["status"],
                response_message=row["response_message"] or "",
                created_at=row["created_at"],
                responded_at=row["responded_at"],
                reminder_15_sent=bool(row["reminder_15_sent"]),
                reminder_60_sent=bool(row["reminder_60_sent"]),
            )
            for row in rows
        ]

    def _parse_iso_datetime(self, value: str, *, field: str) -> datetime:
        try:
            return datetime.fromisoformat(value)
        except ValueError as exc:
            raise ServiceStoreValidationError(f"Invalid {field}; expected ISO datetime") from exc

    def _format_offer_summary(
        self,
        *,
        currency: str,
        price_cents: int,
        proposed_date: str,
        proposed_time_slot: str,
    ) -> str:
        amount = price_cents / 100.0
        return f"Offer {currency} {amount:.2f} for {proposed_date} {proposed_time_slot}"

    def _refresh_quote_request_status(self, conn: sqlite3.Connection, quote_request_id: str) -> None:
        targets = conn.execute(
            """
            SELECT status
            FROM quote_request_targets
            WHERE quote_request_id = ?
            """,
            (quote_request_id,),
        ).fetchall()
        statuses = [str(row["status"]) for row in targets]
        if not statuses:
            next_status = "closed"
        elif any(status in {"accepted", "declined"} for status in statuses):
            if all(status == "declined" for status in statuses):
                next_status = "closed"
            else:
                next_status = "responded"
        else:
            next_status = "pending"

        conn.execute(
            """
            UPDATE quote_requests
            SET status = ?, updated_at = ?
            WHERE id = ?
            """,
            (next_status, datetime.utcnow().isoformat(), quote_request_id),
        )

    def create_quote_request(
        self,
        *,
        user_id: str,
        category: str,
        suburb: Optional[str] = None,
        preferred_window: str,
        pet_details: str,
        note: str = "",
        max_targets: int = 3,
    ) -> Tuple[ServiceQuoteRequest, List[ServiceQuoteTarget]]:
        cleaned_suburb = suburb.strip() if suburb else ""
        if not cleaned_suburb:
            inferred_suburb, _ = self._infer_user_focus_suburb(user_id=user_id)
            cleaned_suburb = inferred_suburb or ""
        cleaned_window = preferred_window.strip()
        cleaned_pet_details = pet_details.strip()
        if category not in {"dog_walking", "grooming"}:
            raise ServiceStoreValidationError("Invalid category. Allowed: dog_walking, grooming")
        if not cleaned_suburb:
            raise ServiceStoreValidationError("Suburb is required")
        if not cleaned_window:
            raise ServiceStoreValidationError("Preferred time window is required")
        if not cleaned_pet_details:
            raise ServiceStoreValidationError("Pet details are required")

        now_iso = datetime.utcnow().isoformat()
        quote_request_id = f"qr_{uuid4().hex[:8]}"
        with self._lock:
            with self._connect() as conn:
                provider_rows = conn.execute(
                    """
                    SELECT p.id, p.name, po.user_id AS owner_user_id
                    FROM providers p
                    JOIN provider_owners po ON po.provider_id = p.id
                    WHERE p.status = 'active'
                      AND p.category = ?
                      AND p.suburb = ?
                      AND po.user_id != ?
                    ORDER BY p.rowid DESC, p.rating DESC, p.review_count DESC
                    LIMIT 50
                    """,
                    (category, cleaned_suburb, user_id),
                ).fetchall()
                if not provider_rows:
                    provider_rows = conn.execute(
                        """
                        SELECT p.id, p.name, po.user_id AS owner_user_id
                        FROM providers p
                        JOIN provider_owners po ON po.provider_id = p.id
                        WHERE p.status = 'active'
                          AND p.category = ?
                          AND po.user_id != ?
                        ORDER BY p.rowid DESC, p.rating DESC, p.review_count DESC
                        LIMIT 50
                        """,
                        (category, user_id),
                    ).fetchall()
                if not provider_rows:
                    raise ServiceStoreNotFoundError("No matching providers found")

                selected = provider_rows[:max_targets]
                conn.execute(
                    """
                    INSERT INTO quote_requests (
                        id, user_id, category, suburb, preferred_window, pet_details, note, status, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        quote_request_id,
                        user_id,
                        category,
                        cleaned_suburb,
                        cleaned_window,
                        cleaned_pet_details,
                        note.strip(),
                        "pending",
                        now_iso,
                        now_iso,
                    ),
                )
                for row in selected:
                    conn.execute(
                        """
                        INSERT INTO quote_request_targets (
                            id, quote_request_id, provider_id, owner_user_id, status, response_message, created_at, responded_at, reminder_15_sent, reminder_60_sent
                        ) VALUES (?, ?, ?, ?, 'pending', '', ?, NULL, 0, 0)
                        """,
                        (
                            f"qrt_{uuid4().hex[:10]}",
                            quote_request_id,
                            row["id"],
                            row["owner_user_id"],
                            now_iso,
                        ),
                    )
                conn.commit()

                request_row = conn.execute("SELECT * FROM quote_requests WHERE id = ?", (quote_request_id,)).fetchone()
                targets = self._quote_targets_for_request(conn, quote_request_id=quote_request_id)
        if not request_row:
            raise ServiceStoreNotFoundError("Quote request not found after create")
        return self._quote_request_from_row(request_row), targets

    def respond_quote_request(
        self,
        *,
        quote_request_id: str,
        provider_id: str,
        actor_user_id: str,
        decision: str,
        message: str = "",
    ) -> Tuple[ServiceQuoteRequest, List[ServiceQuoteTarget]]:
        if decision not in {"accepted", "declined"}:
            raise ServiceStoreValidationError("Invalid decision. Allowed: accepted, declined")

        with self._lock:
            with self._connect() as conn:
                request_row = conn.execute(
                    "SELECT * FROM quote_requests WHERE id = ?",
                    (quote_request_id,),
                ).fetchone()
                if not request_row:
                    raise ServiceStoreNotFoundError("Quote request not found")

                target_row = conn.execute(
                    """
                    SELECT *
                    FROM quote_request_targets
                    WHERE quote_request_id = ? AND provider_id = ?
                    """,
                    (quote_request_id, provider_id),
                ).fetchone()
                if not target_row:
                    raise ServiceStoreNotFoundError("Quote target not found")
                if str(target_row["owner_user_id"]) != actor_user_id:
                    raise ServiceStorePermissionError("Only listing owner can respond to this quote")

                if str(target_row["status"]) in {"accepted", "declined"}:
                    raise ServiceStoreConflictError("Quote target already responded")

                now_iso = datetime.utcnow().isoformat()
                conn.execute(
                    """
                    UPDATE quote_request_targets
                    SET status = ?, response_message = ?, responded_at = ?
                    WHERE quote_request_id = ? AND provider_id = ?
                    """,
                    (decision, message.strip(), now_iso, quote_request_id, provider_id),
                )
                self._refresh_quote_request_status(conn, quote_request_id)
                conn.commit()

                updated_request_row = conn.execute(
                    "SELECT * FROM quote_requests WHERE id = ?",
                    (quote_request_id,),
                ).fetchone()
                targets = self._quote_targets_for_request(conn, quote_request_id=quote_request_id)

        if not updated_request_row:
            raise ServiceStoreNotFoundError("Quote request not found after response")
        return self._quote_request_from_row(updated_request_row), targets

    def create_quote_offer(
        self,
        *,
        quote_request_id: str,
        provider_id: str,
        actor_user_id: str,
        price_cents: int,
        currency: str,
        proposed_date: str,
        proposed_time_slot: str,
        expires_at: str,
        note: str = "",
    ) -> ServiceQuoteOffer:
        normalized_currency = currency.strip().upper()
        if len(normalized_currency) != 3 or not normalized_currency.isalpha():
            raise ServiceStoreValidationError("Invalid currency. Use a 3-letter code like AUD")
        if int(price_cents) <= 0:
            raise ServiceStoreValidationError("price_cents must be greater than 0")

        normalized_date = self._parse_iso_date(proposed_date, field="proposed_date").isoformat()
        normalized_time_slot = self._parse_time_slot(proposed_time_slot, field="proposed_time_slot").strftime("%H:%M")
        expires_at_dt = self._parse_iso_datetime(expires_at.strip(), field="expires_at")
        if expires_at_dt <= datetime.utcnow():
            raise ServiceStoreValidationError("Offer expiry must be in the future")

        with self._lock:
            with self._connect() as conn:
                request_row = conn.execute(
                    "SELECT * FROM quote_requests WHERE id = ?",
                    (quote_request_id,),
                ).fetchone()
                if not request_row:
                    raise ServiceStoreNotFoundError("Quote request not found")

                target_row = conn.execute(
                    """
                    SELECT *
                    FROM quote_request_targets
                    WHERE quote_request_id = ? AND provider_id = ?
                    """,
                    (quote_request_id, provider_id),
                ).fetchone()
                if not target_row:
                    raise ServiceStoreNotFoundError("Quote target not found")
                if str(target_row["owner_user_id"]) != actor_user_id:
                    raise ServiceStorePermissionError("Only listing owner can submit quote offers")
                if str(target_row["status"]) in {"accepted", "declined"}:
                    raise ServiceStoreConflictError("Quote target already responded")

                now_iso = datetime.utcnow().isoformat()
                offer_id = f"qof_{uuid4().hex[:10]}"
                conn.execute(
                    """
                    INSERT INTO quote_target_offers (
                        id, quote_request_id, provider_id, actor_user_id, price_cents, currency,
                        proposed_date, proposed_time_slot, expires_at, note, status, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'active', ?)
                    """,
                    (
                        offer_id,
                        quote_request_id,
                        provider_id,
                        actor_user_id,
                        int(price_cents),
                        normalized_currency,
                        normalized_date,
                        normalized_time_slot,
                        expires_at_dt.isoformat(),
                        note.strip(),
                        now_iso,
                    ),
                )

                summary = self._format_offer_summary(
                    currency=normalized_currency,
                    price_cents=int(price_cents),
                    proposed_date=normalized_date,
                    proposed_time_slot=normalized_time_slot,
                )
                response_message = note.strip() if note.strip() else summary
                conn.execute(
                    """
                    UPDATE quote_request_targets
                    SET status = 'accepted', response_message = ?, responded_at = ?
                    WHERE quote_request_id = ? AND provider_id = ?
                    """,
                    (response_message, now_iso, quote_request_id, provider_id),
                )
                self._refresh_quote_request_status(conn, quote_request_id)
                conn.commit()

        return ServiceQuoteOffer(
            id=offer_id,
            quote_request_id=quote_request_id,
            provider_id=provider_id,
            actor_user_id=actor_user_id,
            price_cents=int(price_cents),
            currency=normalized_currency,
            proposed_date=normalized_date,
            proposed_time_slot=normalized_time_slot,
            expires_at=expires_at_dt.isoformat(),
            note=note.strip(),
            status="active",
            created_at=now_iso,
        )

    def dispatch_quote_reminders(self) -> List[Dict[str, Any]]:
        reminders: List[Dict[str, Any]] = []
        now = datetime.utcnow()
        with self._lock:
            with self._connect() as conn:
                rows = conn.execute(
                    """
                    SELECT t.quote_request_id, t.provider_id, t.owner_user_id, t.created_at, t.reminder_15_sent, t.reminder_60_sent, p.name AS provider_name
                    FROM quote_request_targets t
                    JOIN providers p ON p.id = t.provider_id
                    WHERE t.status = 'pending'
                      AND t.responded_at IS NULL
                    """
                ).fetchall()
                for row in rows:
                    created_at_raw = str(row["created_at"])
                    try:
                        created_at = datetime.fromisoformat(created_at_raw)
                    except ValueError:
                        continue
                    elapsed_minutes = int(max(0, (now - created_at).total_seconds() // 60))
                    send_60 = elapsed_minutes >= 60 and not bool(row["reminder_60_sent"])
                    send_15 = elapsed_minutes >= 15 and not bool(row["reminder_15_sent"])
                    reminder_tier: Optional[str] = None
                    if send_60:
                        reminder_tier = "60m"
                        conn.execute(
                            """
                            UPDATE quote_request_targets
                            SET reminder_60_sent = 1, reminder_15_sent = 1
                            WHERE quote_request_id = ? AND provider_id = ?
                            """,
                            (row["quote_request_id"], row["provider_id"]),
                        )
                    elif send_15:
                        reminder_tier = "15m"
                        conn.execute(
                            """
                            UPDATE quote_request_targets
                            SET reminder_15_sent = 1
                            WHERE quote_request_id = ? AND provider_id = ?
                            """,
                            (row["quote_request_id"], row["provider_id"]),
                        )

                    if reminder_tier:
                        reminders.append(
                            {
                                "quote_request_id": row["quote_request_id"],
                                "provider_id": row["provider_id"],
                                "provider_name": row["provider_name"],
                                "owner_user_id": row["owner_user_id"],
                                "elapsed_minutes": elapsed_minutes,
                                "tier": reminder_tier,
                            }
                        )
                conn.commit()
        return reminders

    def get_quote_request(self, quote_request_id: str) -> Tuple[ServiceQuoteRequest, List[ServiceQuoteTarget]]:
        with self._lock:
            with self._connect() as conn:
                request_row = conn.execute("SELECT * FROM quote_requests WHERE id = ?", (quote_request_id,)).fetchone()
                if not request_row:
                    raise ServiceStoreNotFoundError("Quote request not found")
                targets = self._quote_targets_for_request(conn, quote_request_id=quote_request_id)
        return self._quote_request_from_row(request_row), targets

    def list_provider_inbox(
        self,
        *,
        actor_user_id: str,
        include_resolved: bool = False,
        limit: int = 50,
    ) -> List[ProviderInboxItem]:
        clean_actor = actor_user_id.strip()
        if not clean_actor:
            raise ServiceStoreValidationError("actor_user_id is required")
        if limit <= 0:
            raise ServiceStoreValidationError("limit must be greater than 0")

        booking_status_filter = "" if include_resolved else (
            "AND b.status IN ('requested', 'provider_confirmed', 'in_progress', 'reschedule_requested', 'rescheduled')"
        )
        quote_status_filter = "" if include_resolved else "AND t.status = 'pending'"

        items: List[ProviderInboxItem] = []
        with self._lock:
            with self._connect() as conn:
                quote_rows = conn.execute(
                    f"""
                    SELECT
                        t.id AS target_id,
                        t.quote_request_id,
                        t.provider_id,
                        t.status,
                        t.created_at,
                        qr.user_id AS customer_user_id,
                        qr.category,
                        qr.suburb,
                        qr.preferred_window,
                        p.name AS provider_name
                    FROM quote_request_targets t
                    JOIN quote_requests qr ON qr.id = t.quote_request_id
                    JOIN providers p ON p.id = t.provider_id
                    WHERE t.owner_user_id = ?
                    {quote_status_filter}
                    ORDER BY t.created_at DESC
                    """,
                    (clean_actor,),
                ).fetchall()

                booking_rows = conn.execute(
                    f"""
                    SELECT
                        b.id AS booking_id,
                        b.provider_id,
                        p.name AS provider_name,
                        b.owner_user_id AS customer_user_id,
                        b.pet_name,
                        b.booking_date,
                        b.time_slot,
                        b.status,
                        b.created_at
                    FROM bookings b
                    JOIN provider_owners po ON po.provider_id = b.provider_id
                    JOIN providers p ON p.id = b.provider_id
                    WHERE po.user_id = ?
                    {booking_status_filter}
                    ORDER BY b.created_at DESC
                    """,
                    (clean_actor,),
                ).fetchall()

        for row in quote_rows:
            created_at = str(row["created_at"])
            due_at: Optional[str] = None
            try:
                due_at = (datetime.fromisoformat(created_at) + timedelta(minutes=15)).isoformat()
            except ValueError:
                due_at = None

            items.append(
                ProviderInboxItem(
                    id=f"quote:{row['quote_request_id']}:{row['provider_id']}",
                    item_type="quote_request",
                    provider_id=str(row["provider_id"]),
                    provider_name=str(row["provider_name"]),
                    status=str(row["status"]),
                    title=f"Quote request • {str(row['category']).replace('_', ' ')}",
                    subtitle=f"{row['preferred_window']} • {row['suburb']}",
                    priority="high" if str(row["status"]) == "pending" else "normal",
                    created_at=created_at,
                    due_at=due_at,
                    quote_request_id=str(row["quote_request_id"]),
                    customer_user_id=str(row["customer_user_id"]),
                )
            )

        for row in booking_rows:
            status = str(row["status"])
            items.append(
                ProviderInboxItem(
                    id=f"booking:{row['booking_id']}",
                    item_type="booking",
                    provider_id=str(row["provider_id"]),
                    provider_name=str(row["provider_name"]),
                    status=status,
                    title=f"Booking • {row['pet_name']}",
                    subtitle=f"{row['booking_date']} {row['time_slot']}",
                    priority="high" if status in {"requested", "reschedule_requested"} else "normal",
                    created_at=str(row["created_at"]),
                    booking_id=str(row["booking_id"]),
                    customer_user_id=str(row["customer_user_id"]),
                )
            )

        items.sort(key=lambda item: item.created_at, reverse=True)
        return items[:limit]

    def get_vet_coach_profile(self, *, actor_user_id: str) -> VetCoachProfile:
        if not self._is_vet_user(actor_user_id):
            raise ServiceStorePermissionError("Only verified vets can access coach profile")
        with self._lock:
            with self._connect() as conn:
                profile = self._load_vet_profile(conn, actor_user_id, create_missing=True)
                conn.commit()
        if not profile:
            raise ServiceStoreNotFoundError("Vet profile not found")
        return profile

    def record_vet_coach_session(
        self,
        *,
        actor_user_id: str,
        duration_minutes: int,
        quality_score: float,
        topic: str = "",
        note: str = "",
    ) -> VetCoachSessionResult:
        if not self._is_vet_user(actor_user_id):
            raise ServiceStorePermissionError("Only verified vets can submit coach sessions")
        if duration_minutes <= 0:
            raise ServiceStoreValidationError("duration_minutes must be > 0")
        if quality_score < 0.0 or quality_score > 1.0:
            raise ServiceStoreValidationError("quality_score must be between 0.0 and 1.0")

        now = datetime.utcnow()
        now_iso = now.isoformat()
        today_prefix = now.date().isoformat()
        yesterday_prefix = (now.date() - timedelta(days=1)).isoformat()
        base_earned = max(1, int(round(duration_minutes * (0.6 + quality_score))))

        with self._lock:
            with self._connect() as conn:
                self._ensure_vet_profile_row(conn, actor_user_id)
                today_count_row = conn.execute(
                    """
                    SELECT COUNT(*) AS cnt
                    FROM vet_coach_sessions
                    WHERE user_id = ? AND created_at LIKE ?
                    """,
                    (actor_user_id, f"{today_prefix}%"),
                ).fetchone()
                had_session_today = int(today_count_row["cnt"] or 0) > 0 if today_count_row else False

                streak_bonus = 0
                if not had_session_today:
                    yesterday_count_row = conn.execute(
                        """
                        SELECT COUNT(*) AS cnt
                        FROM vet_coach_sessions
                        WHERE user_id = ? AND created_at LIKE ?
                        """,
                        (actor_user_id, f"{yesterday_prefix}%"),
                    ).fetchone()
                    if yesterday_count_row and int(yesterday_count_row["cnt"] or 0) > 0:
                        streak_bonus = 5

                minutes_earned = base_earned + streak_bonus
                session_id = f"vcs_{uuid4().hex[:10]}"
                conn.execute(
                    """
                    INSERT INTO vet_coach_sessions (
                        id, user_id, duration_minutes, quality_score, topic, note, minutes_earned, created_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        session_id,
                        actor_user_id,
                        int(duration_minutes),
                        float(quality_score),
                        topic.strip(),
                        note.strip(),
                        int(minutes_earned),
                        now_iso,
                    ),
                )
                conn.execute(
                    """
                    UPDATE vet_profiles
                    SET spotlight_minutes = spotlight_minutes + ?,
                        coaching_minutes = coaching_minutes + ?,
                        coaching_sessions = coaching_sessions + 1,
                        quality_score_sum = quality_score_sum + ?,
                        quality_score_count = quality_score_count + 1,
                        updated_at = ?
                    WHERE user_id = ?
                    """,
                    (
                        int(minutes_earned),
                        int(duration_minutes),
                        float(quality_score),
                        now_iso,
                        actor_user_id,
                    ),
                )
                profile = self._load_vet_profile(conn, actor_user_id, create_missing=False)
                conn.commit()

        if not profile:
            raise ServiceStoreNotFoundError("Vet profile not found after session save")
        return VetCoachSessionResult(
            session_id=session_id,
            minutes_earned=minutes_earned,
            profile=profile,
        )

    def activate_vet_spotlight(
        self,
        *,
        actor_user_id: str,
        minutes: int,
    ) -> VetSpotlightActivationResult:
        if not self._is_vet_user(actor_user_id):
            raise ServiceStorePermissionError("Only verified vets can activate spotlight")
        if minutes <= 0:
            raise ServiceStoreValidationError("minutes must be > 0")

        now = datetime.utcnow()
        now_iso = now.isoformat()
        with self._lock:
            with self._connect() as conn:
                self._ensure_vet_profile_row(conn, actor_user_id)
                row = conn.execute("SELECT * FROM vet_profiles WHERE user_id = ?", (actor_user_id,)).fetchone()
                if not row:
                    raise ServiceStoreNotFoundError("Vet profile not found")
                balance = int(row["spotlight_minutes"] or 0)
                if balance < minutes:
                    raise ServiceStoreValidationError(
                        f"Insufficient spotlight minutes ({balance} available, {minutes} requested)"
                    )
                current_until_raw = row["highlighted_until"]
                current_until: Optional[datetime] = None
                if current_until_raw:
                    try:
                        current_until = datetime.fromisoformat(str(current_until_raw))
                    except ValueError:
                        current_until = None
                base_time = current_until if current_until and current_until > now else now
                next_until = (base_time + timedelta(minutes=minutes)).isoformat()

                conn.execute(
                    """
                    UPDATE vet_profiles
                    SET spotlight_minutes = spotlight_minutes - ?,
                        highlighted_until = ?,
                        updated_at = ?
                    WHERE user_id = ?
                    """,
                    (minutes, next_until, now_iso, actor_user_id),
                )
                profile = self._load_vet_profile(conn, actor_user_id, create_missing=False)
                conn.commit()
        if not profile:
            raise ServiceStoreNotFoundError("Vet profile not found after spotlight activation")
        return VetSpotlightActivationResult(
            minutes_spent=minutes,
            profile=profile,
        )

    def verify_groomer_by_vet(
        self,
        *,
        provider_id: str,
        actor_user_id: str,
        decision: str,
        confidence_score: float,
        note: str = "",
    ) -> VetGroomerVerificationResult:
        if not self._is_vet_user(actor_user_id):
            raise ServiceStorePermissionError("Only verified vets can review groomers")
        if decision not in {"approved", "needs_improvement"}:
            raise ServiceStoreValidationError("Invalid decision. Allowed: approved, needs_improvement")
        if confidence_score < 0.0 or confidence_score > 1.0:
            raise ServiceStoreValidationError("confidence_score must be between 0.0 and 1.0")

        now = datetime.utcnow()
        now_iso = now.isoformat()
        valid_until = (now + timedelta(days=90)).isoformat() if decision == "approved" else None
        spotlight_minutes_earned = (
            12 + int(round(confidence_score * 8))
            if decision == "approved"
            else 4 + int(round(confidence_score * 4))
        )

        with self._lock:
            with self._connect() as conn:
                row = conn.execute("SELECT * FROM providers WHERE id = ?", (provider_id,)).fetchone()
                if not row:
                    raise ServiceStoreNotFoundError("Provider not found")
                if str(row["category"]) != "grooming":
                    raise ServiceStoreValidationError("Vet verification is only available for grooming providers")

                owner = conn.execute(
                    "SELECT user_id FROM provider_owners WHERE provider_id = ?",
                    (provider_id,),
                ).fetchone()
                owner_user_id = str(owner["user_id"]) if owner else None
                if owner_user_id and owner_user_id == actor_user_id:
                    raise ServiceStorePermissionError("Vets cannot verify their own listing")

                verification_id = f"vver_{uuid4().hex[:10]}"
                conn.execute(
                    """
                    INSERT INTO vet_groomer_verifications (
                        id, provider_id, vet_user_id, decision, confidence_score, note, created_at, valid_until, spotlight_minutes_earned
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        verification_id,
                        provider_id,
                        actor_user_id,
                        decision,
                        float(confidence_score),
                        note.strip(),
                        now_iso,
                        valid_until,
                        int(spotlight_minutes_earned),
                    ),
                )
                self._ensure_vet_profile_row(conn, actor_user_id)
                conn.execute(
                    """
                    UPDATE vet_profiles
                    SET spotlight_minutes = spotlight_minutes + ?,
                        grooming_reviews_count = grooming_reviews_count + 1,
                        updated_at = ?
                    WHERE user_id = ?
                    """,
                    (int(spotlight_minutes_earned), now_iso, actor_user_id),
                )

                response_time_map = self._compute_provider_response_times(conn)
                local_bookers_map = self._compute_local_bookers_this_month(conn)
                quote_sprint_map = self._compute_quote_sprint_metrics(conn)
                vet_verification_map = self._compute_latest_vet_verifications(conn)
                highlighted_vet_map = self._compute_highlighted_vets(conn)
                vet_profile = self._load_vet_profile(conn, actor_user_id, create_missing=False)
                conn.commit()

        if not vet_profile:
            raise ServiceStoreNotFoundError("Vet profile not found after grooming verification")
        sprint = quote_sprint_map.get(provider_id, {"tier": "none", "response_rate_pct": 0, "streak": 0})
        verification_map = vet_verification_map.get(
            provider_id,
            {"vet_checked": False, "vet_checked_by": None, "vet_checked_until": None},
        )
        highlighted_until = highlighted_vet_map.get(owner_user_id or "")
        highlighted_vet = ACCOUNT_LABELS.get(owner_user_id, owner_user_id) if highlighted_until and owner_user_id else None
        provider = self._row_to_provider(
            row,
            owner_user_id=owner_user_id,
            response_time_minutes=response_time_map.get(provider_id),
            local_bookers_this_month=local_bookers_map.get(provider_id, 0),
            shared_group_bookers=0,
            quote_sprint_tier=str(sprint.get("tier", "none")),
            quote_response_rate_pct=int(sprint.get("response_rate_pct", 0)),
            quote_response_streak=int(sprint.get("streak", 0)),
            vet_checked=bool(verification_map.get("vet_checked", False)),
            vet_checked_by=verification_map.get("vet_checked_by"),
            vet_checked_until=verification_map.get("vet_checked_until"),
            highlighted_vet=highlighted_vet,
            highlighted_vet_until=highlighted_until,
        )
        verification = VetGroomerVerification(
            id=verification_id,
            provider_id=provider_id,
            vet_user_id=actor_user_id,
            decision=decision,
            confidence_score=float(confidence_score),
            note=note.strip(),
            created_at=now_iso,
            valid_until=valid_until,
            spotlight_minutes_earned=int(spotlight_minutes_earned),
        )
        return VetGroomerVerificationResult(
            verification=verification,
            provider=provider,
            vet_profile=vet_profile,
        )

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
                response_time_map = self._compute_provider_response_times(conn)
                local_bookers_map = self._compute_local_bookers_this_month(conn)
                quote_sprint_map = self._compute_quote_sprint_metrics(conn)
                vet_verification_map = self._compute_latest_vet_verifications(conn)
                highlighted_vet_map = self._compute_highlighted_vets(conn)

        owner_user_id = owner["user_id"] if owner else None
        sprint = quote_sprint_map.get(provider_id, {"tier": "none", "response_rate_pct": 0, "streak": 0})
        verification = vet_verification_map.get(
            provider_id,
            {"vet_checked": False, "vet_checked_by": None, "vet_checked_until": None},
        )
        highlighted_until = highlighted_vet_map.get(owner_user_id or "")
        highlighted_vet = ACCOUNT_LABELS.get(owner_user_id, owner_user_id) if highlighted_until and owner_user_id else None
        provider = self._row_to_provider(
            row,
            owner_user_id=owner_user_id,
            response_time_minutes=response_time_map.get(provider_id),
            local_bookers_this_month=local_bookers_map.get(provider_id, 0),
            shared_group_bookers=0,
            quote_sprint_tier=str(sprint.get("tier", "none")),
            quote_response_rate_pct=int(sprint.get("response_rate_pct", 0)),
            quote_response_streak=int(sprint.get("streak", 0)),
            vet_checked=bool(verification.get("vet_checked", False)),
            vet_checked_by=verification.get("vet_checked_by"),
            vet_checked_until=verification.get("vet_checked_until"),
            highlighted_vet=highlighted_vet,
            highlighted_vet_until=highlighted_until,
        )
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

    def _ensure_availability_with_conn(
        self,
        conn: sqlite3.Connection,
        provider_id: str,
        start_date: date,
        days: int = 7,
    ) -> None:
        slots = ["09:00", "11:00", "14:00", "16:00", "18:00"]
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

    def ensure_availability(self, provider_id: str, start_date: date, days: int = 7) -> None:
        with self._lock:
            with self._connect() as conn:
                self._ensure_availability_with_conn(
                    conn=conn,
                    provider_id=provider_id,
                    start_date=start_date,
                    days=days,
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

    def _suggest_available_slots(
        self,
        conn: sqlite3.Connection,
        *,
        provider_id: str,
        start_date: date,
        max_days: int = 3,
        limit: int = 5,
    ) -> List[Tuple[str, str]]:
        suggestions: List[Tuple[str, str]] = []
        now_utc = datetime.utcnow()
        for day_offset in range(max_days):
            slot_date = (start_date + timedelta(days=day_offset)).isoformat()
            rows = conn.execute(
                """
                SELECT slot_date, time_slot
                FROM availability_slots
                WHERE provider_id = ? AND slot_date = ?
                ORDER BY time_slot
                """,
                (provider_id, slot_date),
            ).fetchall()
            for row in rows:
                blocked, _ = self._slot_is_blocked(conn, provider_id, row["slot_date"], row["time_slot"])
                slot_dt = self._parse_slot_datetime(row["slot_date"], row["time_slot"])
                if slot_dt - now_utc < timedelta(hours=2):
                    blocked = True
                if blocked:
                    continue
                suggestions.append((row["slot_date"], row["time_slot"]))
                if len(suggestions) >= limit:
                    return suggestions
        return suggestions

    def _format_alternative_slots(self, slots: List[Tuple[str, str]]) -> str:
        if not slots:
            return ""
        compact = ", ".join(f"{slot_date} {time_slot}" for slot_date, time_slot in slots[:5])
        return f" Next available: {compact}"

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
        self.ensure_availability(provider_id=request.provider_id, start_date=requested_slot.date(), days=3)

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
                    alternatives = self._suggest_available_slots(
                        conn,
                        provider_id=request.provider_id,
                        start_date=requested_slot.date(),
                    )
                    raise ServiceStoreValidationError(
                        f"Time slot not available.{self._format_alternative_slots(alternatives)}"
                    )
                if requested_slot - datetime.utcnow() < timedelta(hours=2):
                    alternatives = self._suggest_available_slots(
                        conn,
                        provider_id=request.provider_id,
                        start_date=requested_slot.date(),
                    )
                    raise ServiceStoreValidationError(
                        f"Booking cutoff applies for this slot.{self._format_alternative_slots(alternatives)}"
                    )

                conn.execute(
                    """
                    DELETE FROM booking_holds
                    WHERE owner_user_id = ? AND provider_id = ? AND booking_date = ? AND time_slot = ?
                    """,
                    (request.user_id, request.provider_id, request.date, request.time_slot),
                )
                blocked, reason = self._slot_is_blocked(conn, request.provider_id, request.date, request.time_slot)
                if blocked:
                    alternatives = self._suggest_available_slots(
                        conn,
                        provider_id=request.provider_id,
                        start_date=requested_slot.date(),
                    )
                    raise ServiceStoreConflictError(
                        f"Time slot unavailable ({reason}).{self._format_alternative_slots(alternatives)}"
                    )

                booking_id = f"b_{uuid4().hex[:8]}"

                conn.execute(
                    """
                    INSERT INTO bookings (id, owner_user_id, provider_id, pet_name, booking_date, time_slot, note, status, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        booking_id,
                        request.user_id,
                        request.provider_id,
                        request.pet_name,
                        request.date,
                        request.time_slot,
                        request.note,
                        "requested",
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
                        booking_id,
                        request.user_id,
                        "none",
                        "requested",
                        "booking requested",
                        datetime.utcnow().isoformat(),
                    ),
                )
                conn.commit()
                inserted = conn.execute("SELECT * FROM bookings WHERE id = ?", (booking_id,)).fetchone()
                if not inserted:
                    raise ServiceStoreNotFoundError("Booking not found after creation")
                return self._booking_from_row(conn=conn, row=inserted, viewer_user_id=request.user_id)

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
        with self._lock:
            with self._connect() as conn:
                return [
                    self._booking_from_row(conn=conn, row=row, viewer_user_id=user_id)
                    for row in rows
                ]

    def list_booking_status_history(
        self,
        *,
        booking_id: str,
        requester_user_id: str,
    ) -> List[BookingStatusHistoryEntry]:
        normalized_requester = requester_user_id.strip()
        if not normalized_requester:
            raise ServiceStoreValidationError("requester_user_id is required")

        with self._lock:
            with self._connect() as conn:
                booking_row = conn.execute(
                    "SELECT * FROM bookings WHERE id = ?",
                    (booking_id,),
                ).fetchone()
                if not booking_row:
                    raise ServiceStoreNotFoundError("Booking not found")

                provider_owner = conn.execute(
                    "SELECT user_id FROM provider_owners WHERE provider_id = ?",
                    (booking_row["provider_id"],),
                ).fetchone()
                owner_user_id = str(booking_row["owner_user_id"])
                provider_user_id = str(provider_owner["user_id"]) if provider_owner else ""
                if normalized_requester not in {owner_user_id, provider_user_id}:
                    raise ServiceStorePermissionError("Only booking owner or provider can view booking history")

                rows = conn.execute(
                    """
                    SELECT *
                    FROM booking_status_history
                    WHERE booking_id = ?
                    ORDER BY created_at ASC
                    """,
                    (booking_id,),
                ).fetchall()

        return [
            BookingStatusHistoryEntry(
                id=row["id"],
                booking_id=row["booking_id"],
                actor_user_id=row["actor_user_id"],
                from_status=row["from_status"],
                to_status=row["to_status"],
                note=row["note"],
                created_at=row["created_at"],
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
                if next_status != "rescheduled" and ((update.date or "").strip() or (update.time_slot or "").strip()):
                    raise ServiceStoreValidationError("date/time_slot can only be provided when status is rescheduled")

                updated_note = update.note or row["note"]
                history_note = update.note
                if next_status == "rescheduled":
                    target_date = (update.date or "").strip()
                    target_time_slot = (update.time_slot or "").strip()
                    if not target_date or not target_time_slot:
                        raise ServiceStoreValidationError("Rescheduled status requires date and time_slot")

                    target_slot = self._parse_slot_datetime(target_date, target_time_slot)
                    if target_slot - datetime.utcnow() < timedelta(hours=2):
                        raise ServiceStoreValidationError("Booking cutoff applies for this slot")

                    self._ensure_availability_with_conn(
                        conn=conn,
                        provider_id=row["provider_id"],
                        start_date=target_slot.date(),
                        days=3,
                    )
                    slot = conn.execute(
                        """
                        SELECT id FROM availability_slots
                        WHERE provider_id = ? AND slot_date = ? AND time_slot = ?
                        """,
                        (row["provider_id"], target_date, target_time_slot),
                    ).fetchone()
                    if not slot:
                        raise ServiceStoreValidationError("Reschedule target slot not available")

                    blocked, reason = self._slot_is_blocked(
                        conn,
                        row["provider_id"],
                        target_date,
                        target_time_slot,
                        ignore_booking_id=booking_id,
                    )
                    if blocked:
                        raise ServiceStoreConflictError(f"Reschedule target unavailable ({reason})")

                    if not update.note.strip():
                        updated_note = f"Rescheduled to {target_date} {target_time_slot}"
                    history_note = updated_note
                    conn.execute(
                        """
                        UPDATE bookings
                        SET status = ?, note = ?, booking_date = ?, time_slot = ?
                        WHERE id = ?
                        """,
                        (next_status, updated_note, target_date, target_time_slot, booking_id),
                    )
                else:
                    conn.execute(
                        "UPDATE bookings SET status = ?, note = ? WHERE id = ?",
                        (next_status, updated_note, booking_id),
                    )

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
                        history_note,
                        datetime.utcnow().isoformat(),
                    ),
                )
                conn.commit()

                updated = conn.execute("SELECT * FROM bookings WHERE id = ?", (booking_id,)).fetchone()
                if not updated:
                    raise ServiceStoreNotFoundError("Booking not found after update")
                return self._booking_from_row(conn=conn, row=updated, viewer_user_id=update.actor_user_id)

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

    def delete_user_data(self, *, user_id: str) -> None:
        clean_user = user_id.strip()
        if not clean_user:
            return
        with self._lock:
            with self._connect() as conn:
                owned_rows = conn.execute(
                    "SELECT provider_id FROM provider_owners WHERE user_id = ?",
                    (clean_user,),
                ).fetchall()
                owned_provider_ids = [str(row["provider_id"]) for row in owned_rows]
                if owned_provider_ids:
                    conn.executemany(
                        "DELETE FROM reviews WHERE provider_id = ?",
                        [(provider_id,) for provider_id in owned_provider_ids],
                    )
                    conn.executemany(
                        "DELETE FROM availability_slots WHERE provider_id = ?",
                        [(provider_id,) for provider_id in owned_provider_ids],
                    )
                    conn.executemany(
                        "DELETE FROM provider_blackout_slots WHERE provider_id = ?",
                        [(provider_id,) for provider_id in owned_provider_ids],
                    )
                    conn.executemany(
                        "DELETE FROM bookings WHERE provider_id = ?",
                        [(provider_id,) for provider_id in owned_provider_ids],
                    )
                    conn.executemany(
                        "DELETE FROM booking_holds WHERE provider_id = ?",
                        [(provider_id,) for provider_id in owned_provider_ids],
                    )
                    conn.executemany(
                        "DELETE FROM quote_request_targets WHERE provider_id = ?",
                        [(provider_id,) for provider_id in owned_provider_ids],
                    )
                    conn.executemany(
                        "DELETE FROM quote_target_offers WHERE provider_id = ?",
                        [(provider_id,) for provider_id in owned_provider_ids],
                    )
                    conn.executemany(
                        "DELETE FROM vet_groomer_verifications WHERE provider_id = ?",
                        [(provider_id,) for provider_id in owned_provider_ids],
                    )
                    conn.executemany(
                        "DELETE FROM providers WHERE id = ?",
                        [(provider_id,) for provider_id in owned_provider_ids],
                    )
                conn.execute("DELETE FROM provider_owners WHERE user_id = ?", (clean_user,))
                conn.execute("DELETE FROM bookings WHERE owner_user_id = ?", (clean_user,))
                conn.execute("DELETE FROM booking_holds WHERE owner_user_id = ?", (clean_user,))
                conn.execute("DELETE FROM booking_status_history WHERE actor_user_id = ?", (clean_user,))
                conn.execute("DELETE FROM quote_requests WHERE user_id = ?", (clean_user,))
                conn.execute("DELETE FROM quote_request_targets WHERE owner_user_id = ?", (clean_user,))
                conn.execute("DELETE FROM quote_target_offers WHERE actor_user_id = ?", (clean_user,))
                conn.execute("DELETE FROM vet_profiles WHERE user_id = ?", (clean_user,))
                conn.commit()

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
