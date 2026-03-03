from datetime import datetime, timedelta, timezone
import re
from typing import Optional

from fastapi import APIRouter, Depends, Header, HTTPException, Query
from fastapi import Request

from app.auth import (
    AUTH_REQUIRED,
    assert_actor_authorized,
    create_access_token,
    is_demo_login_allowed,
    parse_bearer_token,
    require_authenticated_user,
    revoke_access_token,
)
from app.models import (
    AuthDeleteResponse,
    AuthInviteCreateRequest,
    AuthInviteResponse,
    AuthLoginRequest,
    AuthLoginResponse,
    AuthLogoutResponse,
    AuthMeResponse,
    AuthOtpRequest,
    AuthOtpRequestResponse,
    AuthOtpVerifyRequest,
    AuthOtpVerifyResponse,
)
from app.services.auth_otp_store import auth_otp_store, send_otp_via_resend
from app.services.message_store import message_store
from app.services.notification_store import notification_store
from app.services.rate_limiting import SlidingWindowHitStore, read_positive_int_env
from app.services.security_audit import record_rate_limit_hit
from app.services.service_store import service_store

router = APIRouter(prefix="/auth", tags=["auth"])


LOGIN_FAILURE_LIMIT = read_positive_int_env("AUTH_LOGIN_FAILURE_LIMIT", 8)
LOGIN_FAILURE_WINDOW = timedelta(seconds=read_positive_int_env("AUTH_LOGIN_FAILURE_WINDOW_SECONDS", 600))
_LOGIN_FAILURE_STORE = SlidingWindowHitStore()
# Test compatibility: keep existing mutable history reference.
FAILED_LOGIN_ATTEMPTS = _LOGIN_FAILURE_STORE.history
_OTP_REQUEST_STORE = SlidingWindowHitStore()
OTP_REQUEST_LIMIT = read_positive_int_env("AUTH_OTP_REQUEST_LIMIT", 5)
OTP_REQUEST_WINDOW = timedelta(seconds=read_positive_int_env("AUTH_OTP_REQUEST_WINDOW_SECONDS", 600))
ADMIN_USER_IDS = {"admin", "user_1", "user_3"}


def _login_throttle_key(*, user_id: str, client_ip: str) -> str:
    normalized_user = user_id.strip().lower() or "_missing_user"
    normalized_ip = (client_ip or "unknown").strip() or "unknown"
    return f"{normalized_ip}:{normalized_user}"


def _assert_login_not_throttled(throttle_key: str) -> None:
    if _LOGIN_FAILURE_STORE.is_limited(
        key=throttle_key,
        window=LOGIN_FAILURE_WINDOW,
        limit=LOGIN_FAILURE_LIMIT,
    ):
        record_rate_limit_hit(
            surface="auth_login",
            key=throttle_key,
            detail="failed_login_attempts_limit_exceeded",
        )
        raise HTTPException(status_code=429, detail="Too many failed login attempts. Please retry later.")


def _record_failed_login(throttle_key: str) -> None:
    _LOGIN_FAILURE_STORE.add_hit(
        key=throttle_key,
        window=LOGIN_FAILURE_WINDOW,
    )


def _clear_failed_login(throttle_key: str) -> None:
    _LOGIN_FAILURE_STORE.reset_key(throttle_key)


@router.post("/login", response_model=AuthLoginResponse)
def login(payload: AuthLoginRequest, request: Request):
    if not is_demo_login_allowed():
        raise HTTPException(status_code=403, detail="Demo password login disabled")
    user_id = payload.user_id.strip()
    if not user_id:
        raise HTTPException(status_code=400, detail="user_id is required")
    client_ip = request.client.host if request.client else "unknown"
    throttle_key = _login_throttle_key(user_id=user_id, client_ip=client_ip)
    _assert_login_not_throttled(throttle_key)
    if payload.password != "petsocial-demo":
        _record_failed_login(throttle_key)
        raise HTTPException(status_code=401, detail="Invalid credentials")
    _clear_failed_login(throttle_key)
    token, expires_at = create_access_token(user_id=user_id)
    return AuthLoginResponse(access_token=token, user_id=user_id, expires_at=expires_at)


@router.get("/me", response_model=AuthMeResponse)
def me(user_id: str = Depends(require_authenticated_user)):
    return AuthMeResponse(user_id=user_id)


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


def _slug_user_id_from_email(email: str) -> str:
    local = email.split("@", 1)[0].strip().lower()
    base = re.sub(r"[^a-z0-9_]+", "_", local).strip("_")
    if not base:
        base = "user"
    return f"beta_{base}"


def _assert_admin_requester(user_id: str) -> None:
    if user_id not in ADMIN_USER_IDS:
        raise HTTPException(status_code=403, detail="Only admins can issue beta invites")


def _normalized_email(raw: str) -> str:
    return raw.strip().lower()


def _ensure_basic_email_format(email: str, *, detail: str) -> None:
    if "@" not in email:
        raise HTTPException(status_code=400, detail=detail)


def _send_otp_or_raise(email: str, otp_code: str) -> None:
    sent = send_otp_via_resend(email=email, otp_code=otp_code)
    if AUTH_REQUIRED and not sent:
        raise HTTPException(status_code=503, detail="Unable to deliver OTP email right now. Please retry.")


def _parse_iso_dt(raw: str) -> datetime:
    return datetime.fromisoformat(raw.replace("Z", "+00:00"))


def _assert_invite_valid(invite_id: str, email: str) -> tuple[str, str]:
    invite = auth_otp_store.get_invite(invite_id)
    if not invite:
        raise HTTPException(status_code=404, detail="Invite not found")
    stored_email = str(invite["email"]).strip().lower()
    if stored_email != email.strip().lower():
        raise HTTPException(status_code=403, detail="Invite email mismatch")
    expires_at = _parse_iso_dt(str(invite["expires_at"]))
    if expires_at <= _utc_now():
        raise HTTPException(status_code=410, detail="Invite expired")
    if invite["consumed_at"] is not None:
        raise HTTPException(status_code=409, detail="Invite already consumed")
    return str(invite["user_id"]), str(invite["expires_at"])


@router.post("/invite", response_model=AuthInviteResponse)
def create_invite(payload: AuthInviteCreateRequest, authorization: Optional[str] = Header(default=None)):
    if AUTH_REQUIRED:
        assert_actor_authorized(actor_user_id=payload.requester_user_id, authorization=authorization)
    _assert_admin_requester(payload.requester_user_id)
    email = _normalized_email(payload.email)
    _ensure_basic_email_format(email, detail="Invalid email")
    user_id = (payload.user_id or "").strip() or _slug_user_id_from_email(email)
    created_at = _utc_now()
    expires_at = created_at + timedelta(minutes=payload.ttl_minutes)
    return auth_otp_store.create_invite(
        requester_user_id=payload.requester_user_id,
        user_id=user_id,
        email=email,
        created_at=created_at.isoformat(),
        expires_at=expires_at.isoformat(),
    )


@router.post("/otp/request", response_model=AuthOtpRequestResponse)
def request_otp(payload: AuthOtpRequest):
    email = _normalized_email(payload.email)
    if not email:
        raise HTTPException(status_code=400, detail="Valid email is required")
    _ensure_basic_email_format(email, detail="Valid email is required")
    _assert_invite_valid(payload.invite_id, email)
    key = f"{payload.invite_id}:{email}"
    if not _OTP_REQUEST_STORE.allow_and_add_hit(key=key, window=OTP_REQUEST_WINDOW, limit=OTP_REQUEST_LIMIT):
        raise HTTPException(status_code=429, detail="Too many OTP requests. Please retry later.")
    now = _utc_now()
    otp_expiry = now + timedelta(minutes=10)
    otp_code = auth_otp_store.issue_otp(
        invite_id=payload.invite_id,
        email=email,
        created_at=now.isoformat(),
        expires_at=otp_expiry.isoformat(),
    )
    _send_otp_or_raise(email, otp_code)
    return AuthOtpRequestResponse(expires_at=otp_expiry.isoformat())


@router.post("/otp/verify", response_model=AuthOtpVerifyResponse)
def verify_otp(payload: AuthOtpVerifyRequest):
    email = _normalized_email(payload.email)
    user_id, _ = _assert_invite_valid(payload.invite_id, email)
    verified_user = auth_otp_store.verify_otp(
        invite_id=payload.invite_id,
        email=email,
        otp_code=payload.otp_code.strip(),
        verified_at=_utc_now().isoformat(),
    )
    if not verified_user:
        raise HTTPException(status_code=401, detail="Invalid or expired OTP code")
    if verified_user != user_id:
        raise HTTPException(status_code=403, detail="Invite user mismatch")
    token, expires_at = create_access_token(user_id=user_id)
    return AuthOtpVerifyResponse(
        access_token=token,
        user_id=user_id,
        expires_at=expires_at,
    )


@router.post("/logout", response_model=AuthLogoutResponse)
def logout(authorization: Optional[str] = Header(default=None)):
    token = parse_bearer_token(authorization)
    if token:
        revoke_access_token(token)
    return AuthLogoutResponse()


@router.delete("/me", response_model=AuthDeleteResponse)
def delete_me(
    user_id: str = Query(...),
    authorization: Optional[str] = Header(default=None),
):
    assert_actor_authorized(actor_user_id=user_id, authorization=authorization)
    token = parse_bearer_token(authorization)
    if token:
        revoke_access_token(token)
    notification_store.delete_user_data(user_id=user_id)
    message_store.delete_user_data(user_id=user_id)
    auth_otp_store.delete_user_data(user_id=user_id)
    service_store.delete_user_data(user_id=user_id)
    try:
        from app.routers import community as community_router

        community_router.remove_user_data(user_id=user_id)
    except Exception:
        pass
    try:
        from app.routers.chat import orchestrator

        orchestrator.memory_store.delete_user_data(user_id=user_id)
    except Exception:
        pass
    return AuthDeleteResponse(user_id=user_id)
