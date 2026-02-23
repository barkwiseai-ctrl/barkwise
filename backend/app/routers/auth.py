from datetime import timedelta

from fastapi import APIRouter, Depends, HTTPException
from fastapi import Request

from app.auth import create_access_token, require_authenticated_user
from app.models import AuthLoginRequest, AuthLoginResponse, AuthMeResponse
from app.services.rate_limiting import SlidingWindowHitStore, read_positive_int_env
from app.services.security_audit import record_rate_limit_hit

router = APIRouter(prefix="/auth", tags=["auth"])


LOGIN_FAILURE_LIMIT = read_positive_int_env("AUTH_LOGIN_FAILURE_LIMIT", 8)
LOGIN_FAILURE_WINDOW = timedelta(seconds=read_positive_int_env("AUTH_LOGIN_FAILURE_WINDOW_SECONDS", 600))
_LOGIN_FAILURE_STORE = SlidingWindowHitStore()
# Test compatibility: keep existing mutable history reference.
FAILED_LOGIN_ATTEMPTS = _LOGIN_FAILURE_STORE.history


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
