import base64
import hashlib
import hmac
import os
from datetime import datetime, timedelta, timezone
from typing import Optional

from fastapi import Header, HTTPException, status

TOKEN_TTL_HOURS = int(os.getenv("AUTH_TOKEN_TTL_HOURS", "24"))
AUTH_REQUIRED = os.getenv("AUTH_REQUIRED", "false").lower() in {"1", "true", "yes"}
_AUTH_SECRET = os.getenv("AUTH_SECRET", "dev-insecure-secret-change-me")


def _b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode("utf-8").rstrip("=")


def _b64urldecode(value: str) -> bytes:
    padding = "=" * ((4 - len(value) % 4) % 4)
    return base64.urlsafe_b64decode((value + padding).encode("utf-8"))


def create_access_token(user_id: str) -> tuple[str, str]:
    expiry = datetime.now(timezone.utc) + timedelta(hours=TOKEN_TTL_HOURS)
    payload = f"{user_id}|{int(expiry.timestamp())}".encode("utf-8")
    payload_part = _b64url(payload)
    sig = hmac.new(_AUTH_SECRET.encode("utf-8"), payload, hashlib.sha256).digest()
    token = f"{payload_part}.{_b64url(sig)}"
    return token, expiry.isoformat()


def verify_access_token(token: str) -> Optional[str]:
    try:
        payload_part, sig_part = token.split(".", 1)
        payload = _b64urldecode(payload_part)
        sent_sig = _b64urldecode(sig_part)
        expected_sig = hmac.new(_AUTH_SECRET.encode("utf-8"), payload, hashlib.sha256).digest()
        if not hmac.compare_digest(sent_sig, expected_sig):
            return None
        user_id, expiry_ts = payload.decode("utf-8").split("|", 1)
        if datetime.now(timezone.utc).timestamp() > int(expiry_ts):
            return None
        return user_id
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
