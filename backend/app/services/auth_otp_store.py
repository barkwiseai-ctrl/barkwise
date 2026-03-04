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
                        dog_name TEXT NOT NULL,
                        dog_photo_urls TEXT NOT NULL,
                        bio TEXT NOT NULL,
                        suburb TEXT NOT NULL,
                        favorite_suburbs TEXT NOT NULL,
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
            "dog_name": "TEXT NOT NULL DEFAULT ''",
            "dog_photo_urls": "TEXT NOT NULL DEFAULT '[]'",
            "bio": "TEXT NOT NULL DEFAULT ''",
            "suburb": "TEXT NOT NULL DEFAULT ''",
            "favorite_suburbs": "TEXT NOT NULL DEFAULT '[]'",
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
            dog_name="",
            dog_photo_urls=[],
            bio="",
            suburb="",
            favorite_suburbs=[],
            updated_at=updated_at,
        )

    def _row_to_profile(self, row: sqlite3.Row) -> UserProfile:
        dog_photo_urls: list[str]
        favorite_suburbs: list[str]
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
        return UserProfile(
            user_id=str(row["user_id"]),
            display_name=str(row["display_name"]),
            email=str(row["email"]),
            phone=str(row["phone"]),
            dog_name=str(row["dog_name"]),
            dog_photo_urls=dog_photo_urls,
            bio=str(row["bio"]),
            suburb=str(row["suburb"]),
            favorite_suburbs=favorite_suburbs,
            updated_at=str(row["updated_at"]),
        )

    def get_or_create_user_profile(self, *, user_id: str) -> UserProfile:
        normalized_user_id = user_id.strip()
        if not normalized_user_id:
            raise ValueError("user_id is required")
        with self._lock:
            with self._connect() as conn:
                row = conn.execute(
                    """
                    SELECT user_id, display_name, email, phone, dog_name, dog_photo_urls, bio, suburb, favorite_suburbs, updated_at
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
                        user_id, display_name, email, phone, dog_name,
                        dog_photo_urls, bio, suburb, favorite_suburbs, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        profile.user_id,
                        profile.display_name,
                        profile.email,
                        profile.phone,
                        profile.dog_name,
                        json.dumps(profile.dog_photo_urls),
                        profile.bio,
                        profile.suburb,
                        json.dumps(profile.favorite_suburbs),
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
        dog_name: str,
        dog_photo_urls: list[str],
        bio: str,
        suburb: str,
        favorite_suburbs: list[str],
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
            dog_name=dog_name.strip(),
            dog_photo_urls=[value.strip() for value in dog_photo_urls if value.strip()][:8],
            bio=bio.strip(),
            suburb=suburb.strip(),
            favorite_suburbs=[value.strip() for value in favorite_suburbs if value.strip()][:8],
            updated_at=now,
        )
        with self._lock:
            with self._connect() as conn:
                conn.execute(
                    """
                    INSERT INTO user_profiles (
                        user_id, display_name, email, phone, dog_name,
                        dog_photo_urls, bio, suburb, favorite_suburbs, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(user_id) DO UPDATE SET
                        display_name = excluded.display_name,
                        email = excluded.email,
                        phone = excluded.phone,
                        dog_name = excluded.dog_name,
                        dog_photo_urls = excluded.dog_photo_urls,
                        bio = excluded.bio,
                        suburb = excluded.suburb,
                        favorite_suburbs = excluded.favorite_suburbs,
                        updated_at = excluded.updated_at
                    """,
                    (
                        normalized_profile.user_id,
                        normalized_profile.display_name,
                        normalized_profile.email,
                        normalized_profile.phone,
                        normalized_profile.dog_name,
                        json.dumps(normalized_profile.dog_photo_urls),
                        normalized_profile.bio,
                        normalized_profile.suburb,
                        json.dumps(normalized_profile.favorite_suburbs),
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
