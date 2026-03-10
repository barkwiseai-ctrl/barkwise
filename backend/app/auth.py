import base64
import hashlib
import hmac
import json
import logging
import os
from pathlib import Path
import sqlite3
from threading import Lock
from datetime import datetime, timedelta, timezone
from typing import Any, Optional

from fastapi import Header, HTTPException, status

logger = logging.getLogger(__name__)


def _read_bool_env(name: str, default: str) -> bool:
    return os.getenv(name, default).lower() in {"1", "true", "yes"}


def _read_positive_int_env(name: str, default: int, *, minimum: int, maximum: int) -> int:
    raw_value = os.getenv(name, str(default)).strip()
    try:
        parsed = int(raw_value)
    except ValueError:
        logger.warning("Invalid %s=%r; falling back to %s.", name, raw_value, default)
        return default
    if parsed < minimum or parsed > maximum:
        logger.warning(
            "%s=%r outside [%s, %s]; falling back to %s.",
            name,
            raw_value,
            minimum,
            maximum,
            default,
        )
        return default
    return parsed


def _read_token_ttl_hours() -> int:
    return _read_positive_int_env("AUTH_TOKEN_TTL_HOURS", 24, minimum=1, maximum=24 * 30)


TOKEN_TTL_HOURS = _read_token_ttl_hours()
FRIEND_QR_TTL_MINUTES = _read_positive_int_env("AUTH_FRIEND_QR_TTL_MINUTES", 30, minimum=1, maximum=24 * 60)
AUTH_REQUIRED = _read_bool_env("AUTH_REQUIRED", "false")
AUTH_ALLOW_DEMO_LOGIN = _read_bool_env("AUTH_ALLOW_DEMO_LOGIN", "false" if AUTH_REQUIRED else "true")
DEFAULT_AUTH_SECRET = "dev-insecure-secret-change-me"
_AUTH_SECRET = os.getenv("AUTH_SECRET", DEFAULT_AUTH_SECRET)
_AUTH_DB_PATH = os.getenv("AUTH_DB_PATH", str(Path(__file__).resolve().parents[1] / "data" / "auth.sqlite3"))
_AUTH_DB_LOCK = Lock()

if AUTH_REQUIRED and _AUTH_SECRET == DEFAULT_AUTH_SECRET:
    raise RuntimeError("AUTH_SECRET must be set to a non-default value when AUTH_REQUIRED=true")
if AUTH_REQUIRED and AUTH_ALLOW_DEMO_LOGIN:
    raise RuntimeError("AUTH_ALLOW_DEMO_LOGIN must be false when AUTH_REQUIRED=true")


def _b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode("utf-8").rstrip("=")


def _b64urldecode(value: str) -> bytes:
    padding = "=" * ((4 - len(value) % 4) % 4)
    return base64.urlsafe_b64decode((value + padding).encode("utf-8"))


def _connect_auth_db() -> sqlite3.Connection:
    db_path = Path(_AUTH_DB_PATH)
    db_path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(str(db_path), check_same_thread=False)
    conn.row_factory = sqlite3.Row
    return conn


def _init_auth_db() -> None:
    with _AUTH_DB_LOCK:
        with _connect_auth_db() as conn:
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS revoked_tokens (
                    token_hash TEXT PRIMARY KEY,
                    expires_at_ts INTEGER NOT NULL,
                    revoked_at TEXT NOT NULL
                )
                """
            )
            conn.commit()


_init_auth_db()


def create_access_token(user_id: str) -> tuple[str, str]:
    expiry = datetime.now(timezone.utc) + timedelta(hours=TOKEN_TTL_HOURS)
    payload = f"{user_id}|{int(expiry.timestamp())}".encode("utf-8")
    payload_part = _b64url(payload)
    sig = hmac.new(_AUTH_SECRET.encode("utf-8"), payload, hashlib.sha256).digest()
    token = f"{payload_part}.{_b64url(sig)}"
    return token, expiry.isoformat()


def decode_access_token(token: str) -> Optional[tuple[str, int]]:
    try:
        payload_part, sig_part = token.split(".", 1)
        payload = _b64urldecode(payload_part)
        sent_sig = _b64urldecode(sig_part)
        expected_sig = hmac.new(_AUTH_SECRET.encode("utf-8"), payload, hashlib.sha256).digest()
        if not hmac.compare_digest(sent_sig, expected_sig):
            return None
        user_id, expiry_ts = payload.decode("utf-8").split("|", 1)
        return user_id, int(expiry_ts)
    except Exception:
        return None


def _token_hash(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def is_token_revoked(token: str) -> bool:
    token_hash = _token_hash(token)
    with _AUTH_DB_LOCK:
        with _connect_auth_db() as conn:
            row = conn.execute(
                "SELECT expires_at_ts FROM revoked_tokens WHERE token_hash = ?",
                (token_hash,),
            ).fetchone()
            if not row:
                return False
            expires_at_ts = int(row["expires_at_ts"])
            now_ts = int(datetime.now(timezone.utc).timestamp())
            if expires_at_ts < now_ts:
                conn.execute("DELETE FROM revoked_tokens WHERE token_hash = ?", (token_hash,))
                conn.commit()
                return False
            return True


def revoke_access_token(token: str) -> None:
    decoded = decode_access_token(token)
    if not decoded:
        return
    _, expiry_ts = decoded
    with _AUTH_DB_LOCK:
        with _connect_auth_db() as conn:
            conn.execute(
                """
                INSERT INTO revoked_tokens(token_hash, expires_at_ts, revoked_at)
                VALUES (?, ?, ?)
                ON CONFLICT(token_hash) DO UPDATE SET
                    expires_at_ts = excluded.expires_at_ts,
                    revoked_at = excluded.revoked_at
                """,
                (_token_hash(token), expiry_ts, datetime.now(timezone.utc).isoformat()),
            )
            conn.commit()


def is_demo_login_allowed() -> bool:
    return AUTH_ALLOW_DEMO_LOGIN


def verify_access_token(token: str) -> Optional[str]:
    decoded = decode_access_token(token)
    if not decoded:
        return None
    user_id, expiry_ts = decoded
    if datetime.now(timezone.utc).timestamp() > int(expiry_ts):
        return None
    if is_token_revoked(token):
        return None
    return user_id


def _normalize_friend_profile_field(raw: str, *, fallback: str) -> str:
    cleaned = " ".join(raw.strip().split())
    return (cleaned[:48] if cleaned else fallback)


def create_friend_qr_token(
    *,
    user_id: str,
    human_name: str,
    dog_name: str,
) -> tuple[str, str]:
    normalized_user = user_id.strip()
    if not normalized_user:
        raise ValueError("user_id is required")
    safe_human_name = _normalize_friend_profile_field(human_name, fallback="BarkWise member")
    safe_dog_name = _normalize_friend_profile_field(dog_name, fallback="Dog")
    expiry = datetime.now(timezone.utc) + timedelta(minutes=FRIEND_QR_TTL_MINUTES)
    payload_dict: dict[str, Any] = {
        "typ": "friend_qr",
        "uid": normalized_user,
        "hn": safe_human_name,
        "dn": safe_dog_name,
        "exp": int(expiry.timestamp()),
    }
    payload_bytes = json.dumps(payload_dict, separators=(",", ":"), ensure_ascii=True).encode("utf-8")
    payload_part = _b64url(payload_bytes)
    signature = hmac.new(
        _AUTH_SECRET.encode("utf-8"),
        f"friend_qr:{payload_part}".encode("utf-8"),
        hashlib.sha256,
    ).digest()
    token = f"{payload_part}.{_b64url(signature)}"
    return token, expiry.isoformat()


def verify_friend_qr_token(token: str) -> Optional[dict[str, str]]:
    clean_token = token.strip()
    if not clean_token:
        return None
    try:
        payload_part, signature_part = clean_token.split(".", 1)
        expected_signature = hmac.new(
            _AUTH_SECRET.encode("utf-8"),
            f"friend_qr:{payload_part}".encode("utf-8"),
            hashlib.sha256,
        ).digest()
        sent_signature = _b64urldecode(signature_part)
        if not hmac.compare_digest(sent_signature, expected_signature):
            return None
        payload = json.loads(_b64urldecode(payload_part).decode("utf-8"))
        if payload.get("typ") != "friend_qr":
            return None
        user_id = str(payload.get("uid", "")).strip()
        if not user_id:
            return None
        expiry_ts = int(payload.get("exp", 0))
        now_ts = int(datetime.now(timezone.utc).timestamp())
        if expiry_ts <= now_ts:
            return None
        expires_at = datetime.fromtimestamp(expiry_ts, tz=timezone.utc).isoformat()
        return {
            "user_id": user_id,
            "human_name": _normalize_friend_profile_field(str(payload.get("hn", "")), fallback="BarkWise member"),
            "dog_name": _normalize_friend_profile_field(str(payload.get("dn", "")), fallback="Dog"),
            "expires_at": expires_at,
        }
    except Exception:
        return None


def parse_bearer_token(authorization: Optional[str]) -> Optional[str]:
    if not authorization:
        return None
    parts = authorization.split(" ", 1)
    if len(parts) != 2 or parts[0].lower() != "bearer":
        return None
    return parts[1].strip() or None


def resolve_request_user(authorization: Optional[str]) -> Optional[str]:
    token = parse_bearer_token(authorization)
    if not token:
        return None
    return verify_access_token(token)


def require_authenticated_user(authorization: Optional[str] = Header(default=None)) -> str:
    user_id = resolve_request_user(authorization)
    if not user_id:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid or missing bearer token")
    return user_id


def assert_actor_authorized(
    actor_user_id: str,
    authorization: Optional[str] = Header(default=None),
) -> None:
    token_user = resolve_request_user(authorization)
    if not token_user:
        if AUTH_REQUIRED:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Authentication required")
        return
    if token_user != actor_user_id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Token user does not match actor user")
