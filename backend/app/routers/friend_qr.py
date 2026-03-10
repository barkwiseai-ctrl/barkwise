from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field

from app.auth import create_friend_qr_token, require_authenticated_user, verify_friend_qr_token
from app.services.auth_otp_store import auth_otp_store

router = APIRouter(prefix="/auth", tags=["auth"])


class FriendQrIssueResponse(BaseModel):
    friend_token: str
    friend_url: str
    expires_at: str


class FriendQrVerifyRequest(BaseModel):
    friend_token: str = Field(min_length=16, max_length=2048)


class FriendQrVerifyResponse(BaseModel):
    user_id: str
    human_name: str
    dog_name: str
    expires_at: str


@router.post("/friend-qr", response_model=FriendQrIssueResponse)
def issue_friend_qr(
    user_id: str = Depends(require_authenticated_user),
):
    profile = auth_otp_store.get_or_create_user_profile(user_id=user_id)
    human_name = profile.display_name.strip() or user_id
    dog_name = profile.dog_name.strip() or "Dog"
    token, expires_at = create_friend_qr_token(
        user_id=user_id,
        human_name=human_name,
        dog_name=dog_name,
    )
    return FriendQrIssueResponse(
        friend_token=token,
        friend_url=f"barkwise://friend?friend_token={token}",
        expires_at=expires_at,
    )


@router.post("/friend-qr/verify", response_model=FriendQrVerifyResponse)
def verify_friend_qr(
    payload: FriendQrVerifyRequest,
    requester_user_id: str = Depends(require_authenticated_user),
):
    decoded = verify_friend_qr_token(payload.friend_token)
    if not decoded:
        raise HTTPException(status_code=401, detail="Invalid or expired friend QR token")
    friend_user_id = decoded["user_id"].strip()
    if friend_user_id == requester_user_id:
        raise HTTPException(status_code=409, detail="Cannot add yourself")
    return FriendQrVerifyResponse(
        user_id=friend_user_id,
        human_name=decoded["human_name"],
        dog_name=decoded["dog_name"],
        expires_at=decoded["expires_at"],
    )
