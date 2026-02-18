from fastapi import APIRouter, Depends, HTTPException

from app.auth import create_access_token, require_authenticated_user
from app.models import AuthLoginRequest, AuthLoginResponse, AuthMeResponse

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post("/login", response_model=AuthLoginResponse)
def login(payload: AuthLoginRequest):
    user_id = payload.user_id.strip()
    if not user_id:
        raise HTTPException(status_code=400, detail="user_id is required")
    if payload.password != "petsocial-demo":
        raise HTTPException(status_code=401, detail="Invalid credentials")
    token, expires_at = create_access_token(user_id=user_id)
    return AuthLoginResponse(access_token=token, user_id=user_id, expires_at=expires_at)


@router.get("/me", response_model=AuthMeResponse)
def me(user_id: str = Depends(require_authenticated_user)):
    return AuthMeResponse(user_id=user_id)
