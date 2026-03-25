from datetime import datetime, timezone
import hashlib
import hmac
import json
import logging
import os
from pathlib import Path
import random
import sqlite3
from threading import Lock
from typing import Optional
from uuid import uuid4

from app.models import AuthInviteResponse, UserProfile

logger = logging.getLogger(__name__)


def _read_positive_int_env(name: str, default: int) -> int:
    raw = os.getenv(name, str(default)).strip()
    try:
        parsed = int(raw)
    except ValueError:
        return default
    return parsed if parsed > 0 else default


OTP_VERIFY_MAX_ATTEMPTS = _read_positive_int_env("AUTH_OTP_VERIFY_MAX_ATTEMPTS", 5)


def _sha256(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def _parse_iso_dt(raw: str) -> Optional[datetime]:
    try:
        parsed = datetime.fromisoformat(raw.replace("Z", "+00:00"))
        if parsed.tzinfo is None:
            return parsed.replace(tzinfo=timezone.utc)
        return parsed
    except Exception:
        return None


def _can_attempt_otp_verify(*, attempts: int, expires_at_raw: str, verified_at_raw: str) -> bool:
    if attempts >= OTP_VERIFY_MAX_ATTEMPTS:
        return False
    expires_at = _parse_iso_dt(expires_at_raw)
    verified_at = _parse_iso_dt(verified_at_raw)
    if expires_at is None or verified_at is None:
        return False
    return verified_at < expires_at


class AuthOtpStore:
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
                    CREATE TABLE IF NOT EXISTS auth_invites (
                        invite_id TEXT PRIMARY KEY,
                        user_id TEXT NOT NULL,
                        email TEXT NOT NULL,
                        created_by TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        expires_at TEXT NOT NULL,
                        consumed_at TEXT
                    )
                    """
                )
                conn.execute(
                    """
                    CREATE TABLE IF NOT EXISTS auth_otp_codes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        invite_id TEXT NOT NULL,
                        email TEXT NOT NULL,
                        code_hash TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        expires_at TEXT NOT NULL,
                        verified_at TEXT,
                        attempts INTEGER NOT NULL DEFAULT 0
                    )
                    """
                )
                conn.execute(
                    """
                    CREATE TABLE IF NOT EXISTS auth_users (
                        user_id TEXT PRIMARY KEY,
                        email TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """
                )
                conn.execute(
                    """
                    CREATE TABLE IF NOT EXISTS user_profiles (
                        user_id TEXT PRIMARY KEY,
                        display_name TEXT NOT NULL,
                        email TEXT NOT NULL,
                        phone TEXT NOT NULL,
                        human_pronouns TEXT NOT NULL,
                        human_role_label TEXT NOT NULL,
                        service_provider_mode INTEGER NOT NULL DEFAULT 0,
                        dog_name TEXT NOT NULL,
                        dog_age_months INTEGER NOT NULL DEFAULT 0,
                        dog_breed_mix TEXT NOT NULL,
                        dog_sex_neuter TEXT NOT NULL,
                        dog_weight_class TEXT NOT NULL,
                        dog_photo_urls TEXT NOT NULL,
                        secondary_dog_name TEXT NOT NULL,
                        secondary_dog_age_months INTEGER NOT NULL DEFAULT 0,
                        secondary_dog_photo_url TEXT NOT NULL,
                        secondary_dog_gender TEXT NOT NULL,
                        secondary_dog_weight_kg TEXT NOT NULL,
                        bio TEXT NOT NULL,
                        suburb TEXT NOT NULL,
                        favorite_suburbs TEXT NOT NULL,
                        play_energy_level TEXT NOT NULL,
                        play_style TEXT NOT NULL,
                        social_confidence TEXT NOT NULL,
                        trigger_notes TEXT NOT NULL,
                        ideal_match TEXT NOT NULL,
                        walk_preferences TEXT NOT NULL,
                        training_style TEXT NOT NULL,
                        feeding_rules TEXT NOT NULL,
                        consent_boundaries TEXT NOT NULL,
                        vaccination_status TEXT NOT NULL,
                        microchipped INTEGER NOT NULL DEFAULT 0,
                        recall_trained INTEGER NOT NULL DEFAULT 0,
                        leash_reliability TEXT NOT NULL,
                        emergency_contact_name TEXT NOT NULL,
                        emergency_contact_phone TEXT NOT NULL,
                        field_visibility TEXT NOT NULL DEFAULT '{}',
                        updated_at TEXT NOT NULL
                    )
                    """
                )
                conn.execute(
                    "CREATE INDEX IF NOT EXISTS idx_auth_otp_invite_email ON auth_otp_codes(invite_id, email, created_at DESC)"
                )
                self._migrate_user_profiles_table(conn)
                conn.commit()

    def _migrate_user_profiles_table(self, conn: sqlite3.Connection) -> None:
        required_columns: dict[str, str] = {
            "display_name": "TEXT NOT NULL DEFAULT ''",
            "email": "TEXT NOT NULL DEFAULT ''",
            "phone": "TEXT NOT NULL DEFAULT ''",
            "human_pronouns": "TEXT NOT NULL DEFAULT ''",
            "human_role_label": "TEXT NOT NULL DEFAULT ''",
            "service_provider_mode": "INTEGER NOT NULL DEFAULT 0",
            "dog_name": "TEXT NOT NULL DEFAULT ''",
            "dog_age_months": "INTEGER NOT NULL DEFAULT 0",
            "dog_breed_mix": "TEXT NOT NULL DEFAULT ''",
            "dog_sex_neuter": "TEXT NOT NULL DEFAULT ''",
            "dog_weight_class": "TEXT NOT NULL DEFAULT ''",
            "dog_photo_urls": "TEXT NOT NULL DEFAULT '[]'",
            "secondary_dog_name": "TEXT NOT NULL DEFAULT ''",
            "secondary_dog_age_months": "INTEGER NOT NULL DEFAULT 0",
            "secondary_dog_photo_url": "TEXT NOT NULL DEFAULT ''",
            "secondary_dog_gender": "TEXT NOT NULL DEFAULT ''",
            "secondary_dog_weight_kg": "TEXT NOT NULL DEFAULT ''",
            "bio": "TEXT NOT NULL DEFAULT ''",
            "suburb": "TEXT NOT NULL DEFAULT ''",
            "favorite_suburbs": "TEXT NOT NULL DEFAULT '[]'",
            "play_energy_level": "TEXT NOT NULL DEFAULT ''",
            "play_style": "TEXT NOT NULL DEFAULT ''",
            "social_confidence": "TEXT NOT NULL DEFAULT ''",
            "trigger_notes": "TEXT NOT NULL DEFAULT ''",
            "ideal_match": "TEXT NOT NULL DEFAULT ''",
            "walk_preferences": "TEXT NOT NULL DEFAULT ''",
            "training_style": "TEXT NOT NULL DEFAULT ''",
            "feeding_rules": "TEXT NOT NULL DEFAULT ''",
            "consent_boundaries": "TEXT NOT NULL DEFAULT ''",
            "vaccination_status": "TEXT NOT NULL DEFAULT ''",
            "microchipped": "INTEGER NOT NULL DEFAULT 0",
            "recall_trained": "INTEGER NOT NULL DEFAULT 0",
            "leash_reliability": "TEXT NOT NULL DEFAULT ''",
            "emergency_contact_name": "TEXT NOT NULL DEFAULT ''",
            "emergency_contact_phone": "TEXT NOT NULL DEFAULT ''",
            "field_visibility": "TEXT NOT NULL DEFAULT '{}'",
            "updated_at": "TEXT NOT NULL DEFAULT ''",
        }
        existing_columns = {
            str(row["name"]).strip()
            for row in conn.execute("PRAGMA table_info(user_profiles)").fetchall()
        }
        for column_name, column_ddl in required_columns.items():
            if column_name in existing_columns:
                continue
            conn.execute(f"ALTER TABLE user_profiles ADD COLUMN {column_name} {column_ddl}")

    def create_invite(
        self,
        *,
        requester_user_id: str,
        user_id: str,
        email: str,
        created_at: str,
        expires_at: str,
    ) -> AuthInviteResponse:
        invite_id = f"ainv_{uuid4().hex[:12]}"
        with self._lock:
            with self._connect() as conn:
                conn.execute(
                    """
                    INSERT INTO auth_invites(invite_id, user_id, email, created_by, created_at, expires_at, consumed_at)
                    VALUES (?, ?, ?, ?, ?, ?, NULL)
                    """,
                    (invite_id, user_id, email, requester_user_id, created_at, expires_at),
                )
                conn.execute(
                    """
                    INSERT INTO auth_users(user_id, email, created_at)
                    VALUES (?, ?, ?)
                    ON CONFLICT(user_id) DO UPDATE SET
                        email = excluded.email
                    """,
                    (user_id, email, created_at),
                )
                conn.commit()
        return AuthInviteResponse(
            invite_id=invite_id,
            user_id=user_id,
            email=email,
            expires_at=expires_at,
        )

    def get_invite(self, invite_id: str) -> Optional[sqlite3.Row]:
        with self._lock:
            with self._connect() as conn:
                return conn.execute(
                    """
                    SELECT invite_id, user_id, email, created_by, created_at, expires_at, consumed_at
                    FROM auth_invites
                    WHERE invite_id = ?
                    """,
                    (invite_id,),
                ).fetchone()

    def _lookup_user_email(self, conn: sqlite3.Connection, user_id: str) -> str:
        row = conn.execute(
            "SELECT email FROM auth_users WHERE user_id = ?",
            (user_id,),
        ).fetchone()
        email = str(row["email"]).strip().lower() if row else ""
        return email or f"{user_id}@barkwise.test"

    def _default_profile(self, *, user_id: str, email: str, updated_at: str) -> UserProfile:
        display_name = user_id.replace("_", " ").strip().title()
        if not display_name:
            display_name = user_id
        return UserProfile(
            user_id=user_id,
            display_name=display_name,
            email=email.strip().lower(),
            phone="",
            human_pronouns="",
            human_role_label="Member",
            service_provider_mode=False,
            dog_name="",
            dog_age_months=0,
            dog_breed_mix="",
            dog_sex_neuter="",
            dog_weight_class="",
            dog_photo_urls=[],
            secondary_dog_name="",
            secondary_dog_age_months=0,
            secondary_dog_photo_url="",
            secondary_dog_gender="",
            secondary_dog_weight_kg="",
            bio="",
            suburb="",
            favorite_suburbs=[],
            play_energy_level="",
            play_style="",
            social_confidence="",
            trigger_notes="",
            ideal_match="",
            walk_preferences="",
            training_style="",
            feeding_rules="",
            consent_boundaries="",
            vaccination_status="",
            microchipped=False,
            recall_trained=False,
            leash_reliability="",
            emergency_contact_name="",
            emergency_contact_phone="",
            field_visibility={},
            updated_at=updated_at,
        )

    def _row_to_profile(self, row: sqlite3.Row) -> UserProfile:
        dog_photo_urls: list[str]
        favorite_suburbs: list[str]
        field_visibility: dict[str, str]
        try:
            parsed_photos = json.loads(str(row["dog_photo_urls"]))
            dog_photo_urls = [str(value).strip() for value in parsed_photos if str(value).strip()]
        except Exception:
            dog_photo_urls = []
        try:
            parsed_favorites = json.loads(str(row["favorite_suburbs"]))
            favorite_suburbs = [str(value).strip() for value in parsed_favorites if str(value).strip()]
        except Exception:
            favorite_suburbs = []
        try:
            parsed_visibility = json.loads(str(row["field_visibility"]))
            if isinstance(parsed_visibility, dict):
                field_visibility = {
                    str(key).strip(): str(value).strip()
                    for key, value in parsed_visibility.items()
                    if str(key).strip() and str(value).strip()
                }
            else:
                field_visibility = {}
        except Exception:
            field_visibility = {}

        def _to_int(value: object, default: int = 0) -> int:
            try:
                parsed = int(value)
            except Exception:
                return default
            return max(0, parsed)

        return UserProfile(
            user_id=str(row["user_id"]),
            display_name=str(row["display_name"]),
            email=str(row["email"]),
            phone=str(row["phone"]),
            human_pronouns=str(row["human_pronouns"]),
            human_role_label=str(row["human_role_label"]),
            service_provider_mode=bool(int(row["service_provider_mode"])),
            dog_name=str(row["dog_name"]),
            dog_age_months=_to_int(row["dog_age_months"]),
            dog_breed_mix=str(row["dog_breed_mix"]),
            dog_sex_neuter=str(row["dog_sex_neuter"]),
            dog_weight_class=str(row["dog_weight_class"]),
            dog_photo_urls=dog_photo_urls,
            secondary_dog_name=str(row["secondary_dog_name"]),
            secondary_dog_age_months=_to_int(row["secondary_dog_age_months"]),
            secondary_dog_photo_url=str(row["secondary_dog_photo_url"]),
            secondary_dog_gender=str(row["secondary_dog_gender"]),
            secondary_dog_weight_kg=str(row["secondary_dog_weight_kg"]),
            bio=str(row["bio"]),
            suburb=str(row["suburb"]),
            favorite_suburbs=favorite_suburbs,
            play_energy_level=str(row["play_energy_level"]),
            play_style=str(row["play_style"]),
            social_confidence=str(row["social_confidence"]),
            trigger_notes=str(row["trigger_notes"]),
            ideal_match=str(row["ideal_match"]),
            walk_preferences=str(row["walk_preferences"]),
            training_style=str(row["training_style"]),
            feeding_rules=str(row["feeding_rules"]),
            consent_boundaries=str(row["consent_boundaries"]),
            vaccination_status=str(row["vaccination_status"]),
            microchipped=bool(int(row["microchipped"])),
            recall_trained=bool(int(row["recall_trained"])),
            leash_reliability=str(row["leash_reliability"]),
            emergency_contact_name=str(row["emergency_contact_name"]),
            emergency_contact_phone=str(row["emergency_contact_phone"]),
            field_visibility=field_visibility,
            updated_at=str(row["updated_at"]),
        )

    def _normalize_field_visibility(self, raw: dict[str, str]) -> dict[str, str]:
        allowed = {"public", "group", "friends", "private"}
        normalized: dict[str, str] = {}
        for key, value in raw.items():
            normalized_key = str(key).strip().lower()
            normalized_value = str(value).strip().lower()
            if not normalized_key or normalized_value not in allowed:
                continue
            normalized[normalized_key] = normalized_value
        return normalized

    def get_or_create_user_profile(self, *, user_id: str) -> UserProfile:
        normalized_user_id = user_id.strip()
        if not normalized_user_id:
            raise ValueError("user_id is required")
        with self._lock:
            with self._connect() as conn:
                row = conn.execute(
                    """
                    SELECT
                        user_id, display_name, email, phone, human_pronouns, human_role_label, service_provider_mode,
                        dog_name, dog_age_months, dog_breed_mix, dog_sex_neuter, dog_weight_class, dog_photo_urls,
                        secondary_dog_name, secondary_dog_age_months, secondary_dog_photo_url, secondary_dog_gender, secondary_dog_weight_kg,
                        bio, suburb, favorite_suburbs,
                        play_energy_level, play_style, social_confidence, trigger_notes, ideal_match,
                        walk_preferences, training_style, feeding_rules, consent_boundaries,
                        vaccination_status, microchipped, recall_trained, leash_reliability,
                        emergency_contact_name, emergency_contact_phone, field_visibility, updated_at
                    FROM user_profiles
                    WHERE user_id = ?
                    """,
                    (normalized_user_id,),
                ).fetchone()
                if row:
                    return self._row_to_profile(row)

                now = datetime.now(timezone.utc).isoformat()
                email = self._lookup_user_email(conn, normalized_user_id)
                profile = self._default_profile(
                    user_id=normalized_user_id,
                    email=email,
                    updated_at=now,
                )
                conn.execute(
                    """
                    INSERT INTO user_profiles (
                        user_id, display_name, email, phone, human_pronouns, human_role_label,
                        service_provider_mode,
                        dog_name, dog_age_months, dog_breed_mix, dog_sex_neuter, dog_weight_class, dog_photo_urls,
                        secondary_dog_name, secondary_dog_age_months, secondary_dog_photo_url, secondary_dog_gender, secondary_dog_weight_kg,
                        bio, suburb, favorite_suburbs,
                        play_energy_level, play_style, social_confidence, trigger_notes, ideal_match,
                        walk_preferences, training_style, feeding_rules, consent_boundaries,
                        vaccination_status, microchipped, recall_trained, leash_reliability,
                        emergency_contact_name, emergency_contact_phone, field_visibility, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        profile.user_id,
                        profile.display_name,
                        profile.email,
                        profile.phone,
                        profile.human_pronouns,
                        profile.human_role_label,
                        1 if profile.service_provider_mode else 0,
                        profile.dog_name,
                        profile.dog_age_months,
                        profile.dog_breed_mix,
                        profile.dog_sex_neuter,
                        profile.dog_weight_class,
                        json.dumps(profile.dog_photo_urls),
                        profile.secondary_dog_name,
                        profile.secondary_dog_age_months,
                        profile.secondary_dog_photo_url,
                        profile.secondary_dog_gender,
                        profile.secondary_dog_weight_kg,
                        profile.bio,
                        profile.suburb,
                        json.dumps(profile.favorite_suburbs),
                        profile.play_energy_level,
                        profile.play_style,
                        profile.social_confidence,
                        profile.trigger_notes,
                        profile.ideal_match,
                        profile.walk_preferences,
                        profile.training_style,
                        profile.feeding_rules,
                        profile.consent_boundaries,
                        profile.vaccination_status,
                        1 if profile.microchipped else 0,
                        1 if profile.recall_trained else 0,
                        profile.leash_reliability,
                        profile.emergency_contact_name,
                        profile.emergency_contact_phone,
                        json.dumps(profile.field_visibility),
                        profile.updated_at,
                    ),
                )
                conn.execute(
                    """
                    INSERT INTO auth_users(user_id, email, created_at)
                    VALUES (?, ?, ?)
                    ON CONFLICT(user_id) DO UPDATE SET
                        email = excluded.email
                    """,
                    (profile.user_id, profile.email, now),
                )
                conn.commit()
                return profile

    def upsert_user_profile(
        self,
        *,
        user_id: str,
        display_name: str,
        email: str,
        phone: str,
        human_pronouns: str,
        human_role_label: str,
        service_provider_mode: bool,
        dog_name: str,
        dog_age_months: int,
        dog_breed_mix: str,
        dog_sex_neuter: str,
        dog_weight_class: str,
        dog_photo_urls: list[str],
        secondary_dog_name: str,
        secondary_dog_age_months: int,
        secondary_dog_photo_url: str,
        secondary_dog_gender: str,
        secondary_dog_weight_kg: str,
        bio: str,
        suburb: str,
        favorite_suburbs: list[str],
        play_energy_level: str,
        play_style: str,
        social_confidence: str,
        trigger_notes: str,
        ideal_match: str,
        walk_preferences: str,
        training_style: str,
        feeding_rules: str,
        consent_boundaries: str,
        vaccination_status: str,
        microchipped: bool,
        recall_trained: bool,
        leash_reliability: str,
        emergency_contact_name: str,
        emergency_contact_phone: str,
        field_visibility: dict[str, str],
    ) -> UserProfile:
        normalized_user_id = user_id.strip()
        if not normalized_user_id:
            raise ValueError("user_id is required")
        now = datetime.now(timezone.utc).isoformat()
        normalized_email = email.strip().lower()
        normalized_profile = UserProfile(
            user_id=normalized_user_id,
            display_name=display_name.strip(),
            email=normalized_email,
            phone=phone.strip(),
            human_pronouns=human_pronouns.strip(),
            human_role_label=human_role_label.strip(),
            service_provider_mode=bool(service_provider_mode),
            dog_name=dog_name.strip(),
            dog_age_months=max(0, int(dog_age_months)),
            dog_breed_mix=dog_breed_mix.strip(),
            dog_sex_neuter=dog_sex_neuter.strip(),
            dog_weight_class=dog_weight_class.strip(),
            dog_photo_urls=[value.strip() for value in dog_photo_urls if value.strip()][:8],
            secondary_dog_name=secondary_dog_name.strip(),
            secondary_dog_age_months=max(0, int(secondary_dog_age_months)),
            secondary_dog_photo_url=secondary_dog_photo_url.strip(),
            secondary_dog_gender=secondary_dog_gender.strip().lower(),
            secondary_dog_weight_kg=secondary_dog_weight_kg.strip(),
            bio=bio.strip(),
            suburb=suburb.strip(),
            favorite_suburbs=[value.strip() for value in favorite_suburbs if value.strip()][:8],
            play_energy_level=play_energy_level.strip(),
            play_style=play_style.strip(),
            social_confidence=social_confidence.strip(),
            trigger_notes=trigger_notes.strip(),
            ideal_match=ideal_match.strip(),
            walk_preferences=walk_preferences.strip(),
            training_style=training_style.strip(),
            feeding_rules=feeding_rules.strip(),
            consent_boundaries=consent_boundaries.strip(),
            vaccination_status=vaccination_status.strip(),
            microchipped=bool(microchipped),
            recall_trained=bool(recall_trained),
            leash_reliability=leash_reliability.strip(),
            emergency_contact_name=emergency_contact_name.strip(),
            emergency_contact_phone=emergency_contact_phone.strip(),
            field_visibility=self._normalize_field_visibility(field_visibility),
            updated_at=now,
        )
        with self._lock:
            with self._connect() as conn:
                conn.execute(
                    """
                    INSERT INTO user_profiles (
                        user_id, display_name, email, phone, human_pronouns, human_role_label,
                        service_provider_mode,
                        dog_name, dog_age_months, dog_breed_mix, dog_sex_neuter, dog_weight_class, dog_photo_urls,
                        secondary_dog_name, secondary_dog_age_months, secondary_dog_photo_url, secondary_dog_gender, secondary_dog_weight_kg,
                        bio, suburb, favorite_suburbs,
                        play_energy_level, play_style, social_confidence, trigger_notes, ideal_match,
                        walk_preferences, training_style, feeding_rules, consent_boundaries,
                        vaccination_status, microchipped, recall_trained, leash_reliability,
                        emergency_contact_name, emergency_contact_phone, field_visibility, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(user_id) DO UPDATE SET
                        display_name = excluded.display_name,
                        email = excluded.email,
                        phone = excluded.phone,
                        human_pronouns = excluded.human_pronouns,
                        human_role_label = excluded.human_role_label,
                        service_provider_mode = excluded.service_provider_mode,
                        dog_name = excluded.dog_name,
                        dog_age_months = excluded.dog_age_months,
                        dog_breed_mix = excluded.dog_breed_mix,
                        dog_sex_neuter = excluded.dog_sex_neuter,
                        dog_weight_class = excluded.dog_weight_class,
                        dog_photo_urls = excluded.dog_photo_urls,
                        secondary_dog_name = excluded.secondary_dog_name,
                        secondary_dog_age_months = excluded.secondary_dog_age_months,
                        secondary_dog_photo_url = excluded.secondary_dog_photo_url,
                        secondary_dog_gender = excluded.secondary_dog_gender,
                        secondary_dog_weight_kg = excluded.secondary_dog_weight_kg,
                        bio = excluded.bio,
                        suburb = excluded.suburb,
                        favorite_suburbs = excluded.favorite_suburbs,
                        play_energy_level = excluded.play_energy_level,
                        play_style = excluded.play_style,
                        social_confidence = excluded.social_confidence,
                        trigger_notes = excluded.trigger_notes,
                        ideal_match = excluded.ideal_match,
                        walk_preferences = excluded.walk_preferences,
                        training_style = excluded.training_style,
                        feeding_rules = excluded.feeding_rules,
                        consent_boundaries = excluded.consent_boundaries,
                        vaccination_status = excluded.vaccination_status,
                        microchipped = excluded.microchipped,
                        recall_trained = excluded.recall_trained,
                        leash_reliability = excluded.leash_reliability,
                        emergency_contact_name = excluded.emergency_contact_name,
                        emergency_contact_phone = excluded.emergency_contact_phone,
                        field_visibility = excluded.field_visibility,
                        updated_at = excluded.updated_at
                    """,
                    (
                        normalized_profile.user_id,
                        normalized_profile.display_name,
                        normalized_profile.email,
                        normalized_profile.phone,
                        normalized_profile.human_pronouns,
                        normalized_profile.human_role_label,
                        1 if normalized_profile.service_provider_mode else 0,
                        normalized_profile.dog_name,
                        normalized_profile.dog_age_months,
                        normalized_profile.dog_breed_mix,
                        normalized_profile.dog_sex_neuter,
                        normalized_profile.dog_weight_class,
                        json.dumps(normalized_profile.dog_photo_urls),
                        normalized_profile.secondary_dog_name,
                        normalized_profile.secondary_dog_age_months,
                        normalized_profile.secondary_dog_photo_url,
                        normalized_profile.secondary_dog_gender,
                        normalized_profile.secondary_dog_weight_kg,
                        normalized_profile.bio,
                        normalized_profile.suburb,
                        json.dumps(normalized_profile.favorite_suburbs),
                        normalized_profile.play_energy_level,
                        normalized_profile.play_style,
                        normalized_profile.social_confidence,
                        normalized_profile.trigger_notes,
                        normalized_profile.ideal_match,
                        normalized_profile.walk_preferences,
                        normalized_profile.training_style,
                        normalized_profile.feeding_rules,
                        normalized_profile.consent_boundaries,
                        normalized_profile.vaccination_status,
                        1 if normalized_profile.microchipped else 0,
                        1 if normalized_profile.recall_trained else 0,
                        normalized_profile.leash_reliability,
                        normalized_profile.emergency_contact_name,
                        normalized_profile.emergency_contact_phone,
                        json.dumps(normalized_profile.field_visibility),
                        normalized_profile.updated_at,
                    ),
                )
                if normalized_profile.email:
                    conn.execute(
                        """
                        INSERT INTO auth_users(user_id, email, created_at)
                        VALUES (?, ?, ?)
                        ON CONFLICT(user_id) DO UPDATE SET
                            email = excluded.email
                        """,
                        (normalized_profile.user_id, normalized_profile.email, now),
                    )
                conn.commit()
        return normalized_profile

    def user_can_create_provider_listings(self, *, user_id: str) -> bool:
        return self.get_or_create_user_profile(user_id=user_id).service_provider_mode

    def set_service_provider_mode(self, *, user_id: str, enabled: bool) -> UserProfile:
        current = self.get_or_create_user_profile(user_id=user_id)
        return self.upsert_user_profile(
            user_id=user_id,
            display_name=current.display_name,
            email=current.email,
            phone=current.phone,
            human_pronouns=current.human_pronouns,
            human_role_label=current.human_role_label,
            service_provider_mode=enabled,
            dog_name=current.dog_name,
            dog_age_months=current.dog_age_months,
            dog_breed_mix=current.dog_breed_mix,
            dog_sex_neuter=current.dog_sex_neuter,
            dog_weight_class=current.dog_weight_class,
            dog_photo_urls=current.dog_photo_urls,
            secondary_dog_name=current.secondary_dog_name,
            secondary_dog_age_months=current.secondary_dog_age_months,
            secondary_dog_photo_url=current.secondary_dog_photo_url,
            secondary_dog_gender=current.secondary_dog_gender,
            secondary_dog_weight_kg=current.secondary_dog_weight_kg,
            bio=current.bio,
            suburb=current.suburb,
            favorite_suburbs=current.favorite_suburbs,
            play_energy_level=current.play_energy_level,
            play_style=current.play_style,
            social_confidence=current.social_confidence,
            trigger_notes=current.trigger_notes,
            ideal_match=current.ideal_match,
            walk_preferences=current.walk_preferences,
            training_style=current.training_style,
            feeding_rules=current.feeding_rules,
            consent_boundaries=current.consent_boundaries,
            vaccination_status=current.vaccination_status,
            microchipped=current.microchipped,
            recall_trained=current.recall_trained,
            leash_reliability=current.leash_reliability,
            emergency_contact_name=current.emergency_contact_name,
            emergency_contact_phone=current.emergency_contact_phone,
            field_visibility=current.field_visibility,
        )

    def issue_otp(
        self,
        *,
        invite_id: str,
        email: str,
        created_at: str,
        expires_at: str,
    ) -> str:
        code = f"{random.randint(0, 999999):06d}"
        code_hash = _sha256(code)
        with self._lock:
            with self._connect() as conn:
                conn.execute(
                    """
                    INSERT INTO auth_otp_codes(invite_id, email, code_hash, created_at, expires_at, verified_at, attempts)
                    VALUES (?, ?, ?, ?, ?, NULL, 0)
                    """,
                    (invite_id, email, code_hash, created_at, expires_at),
                )
                conn.commit()
        return code

    def verify_otp(
        self,
        *,
        invite_id: str,
        email: str,
        otp_code: str,
        verified_at: str,
    ) -> Optional[str]:
        with self._lock:
            with self._connect() as conn:
                row = conn.execute(
                    """
                    SELECT id, code_hash, expires_at, attempts
                    FROM auth_otp_codes
                    WHERE invite_id = ? AND email = ? AND verified_at IS NULL
                    ORDER BY id DESC
                    LIMIT 1
                    """,
                    (invite_id, email),
                ).fetchone()
                if not row:
                    return None
                otp_id = int(row["id"])
                if not _can_attempt_otp_verify(
                    attempts=int(row["attempts"]),
                    expires_at_raw=str(row["expires_at"]),
                    verified_at_raw=verified_at,
                ):
                    return None
                code_hash = str(row["code_hash"])
                if not hmac.compare_digest(code_hash, _sha256(otp_code.strip())):
                    conn.execute(
                        "UPDATE auth_otp_codes SET attempts = attempts + 1 WHERE id = ?",
                        (otp_id,),
                    )
                    conn.commit()
                    return None
                conn.execute(
                    "UPDATE auth_otp_codes SET verified_at = ? WHERE id = ?",
                    (verified_at, otp_id),
                )
                conn.execute(
                    "UPDATE auth_invites SET consumed_at = ? WHERE invite_id = ?",
                    (verified_at, invite_id),
                )
                invite = conn.execute(
                    "SELECT user_id FROM auth_invites WHERE invite_id = ?",
                    (invite_id,),
                ).fetchone()
                conn.commit()
                if not invite:
                    return None
                return str(invite["user_id"])

    def delete_user_data(self, *, user_id: str) -> None:
        with self._lock:
            with self._connect() as conn:
                invite_rows = conn.execute(
                    "SELECT invite_id FROM auth_invites WHERE user_id = ?",
                    (user_id,),
                ).fetchall()
                invite_ids = [str(row["invite_id"]) for row in invite_rows]
                if invite_ids:
                    conn.executemany(
                        "DELETE FROM auth_otp_codes WHERE invite_id = ?",
                        [(invite_id,) for invite_id in invite_ids],
                    )
                    conn.executemany(
                        "DELETE FROM auth_invites WHERE invite_id = ?",
                        [(invite_id,) for invite_id in invite_ids],
                    )
                conn.execute("DELETE FROM user_profiles WHERE user_id = ?", (user_id,))
                conn.execute("DELETE FROM auth_users WHERE user_id = ?", (user_id,))
                conn.commit()


def send_otp_via_resend(*, email: str, otp_code: str) -> bool:
    api_key = os.getenv("RESEND_API_KEY", "").strip()
    from_email = os.getenv("RESEND_FROM_EMAIL", "").strip()
    if not api_key or not from_email:
        logger.info("OTP email skipped (RESEND_API_KEY/RESEND_FROM_EMAIL missing) for %s", email)
        return False
    try:
        import urllib.error
        import urllib.request

        payload = {
            "from": from_email,
            "to": [email],
            "subject": "Your BarkWise login code",
            "text": f"Your BarkWise verification code is {otp_code}. It expires in 10 minutes.",
        }
        req = urllib.request.Request(
            "https://api.resend.com/emails",
            method="POST",
            headers={
                "Authorization": f"Bearer {api_key}",
                "Content-Type": "application/json",
                "User-Agent": "barkwise-backend/1.0",
            },
            data=json.dumps(payload).encode("utf-8"),
        )
        with urllib.request.urlopen(req, timeout=10) as response:  # nosec: B310
            return int(response.status) in {200, 201, 202}
    except urllib.error.HTTPError as exc:
        response_body = ""
        try:
            response_body = exc.read().decode("utf-8", errors="ignore").strip()
        except Exception:
            response_body = ""
        logger.error(
            "Resend email HTTP error status=%s reason=%s body=%s",
            getattr(exc, "code", "unknown"),
            getattr(exc, "reason", "unknown"),
            response_body,
        )
        return False
    except urllib.error.URLError as exc:
        logger.error("Resend email URL error reason=%s", exc.reason)
        return False
    except Exception:
        logger.exception("Failed to send OTP email via Resend")
        return False


default_db = str(Path(__file__).resolve().parents[2] / "data" / "auth.sqlite3")
auth_otp_store = AuthOtpStore(db_path=os.getenv("AUTH_DB_PATH", default_db))
