from typing import Optional

from fastapi import APIRouter, Header, HTTPException, Query

from app.auth import assert_actor_authorized
from app.services.security_audit import rate_limit_snapshot, reset_rate_limit_metrics

router = APIRouter(prefix="/security", tags=["security"])

AUDIT_ADMIN_USER_IDS = {"admin", "user_1", "user_3"}


def _assert_security_metrics_access(requester_user_id: str, authorization: Optional[str]) -> None:
    assert_actor_authorized(actor_user_id=requester_user_id, authorization=authorization)
    if requester_user_id not in AUDIT_ADMIN_USER_IDS:
        raise HTTPException(status_code=403, detail="Only admins can view security rate limit metrics")


@router.get("/rate-limits", response_model=dict)
def get_rate_limit_audit(
    requester_user_id: str = Query(...),
    authorization: Optional[str] = Header(default=None),
):
    _assert_security_metrics_access(requester_user_id=requester_user_id, authorization=authorization)
    return rate_limit_snapshot()


@router.post("/rate-limits/reset", response_model=dict)
def reset_rate_limit_audit(
    requester_user_id: str = Query(...),
    authorization: Optional[str] = Header(default=None),
):
    _assert_security_metrics_access(requester_user_id=requester_user_id, authorization=authorization)
    reset_rate_limit_metrics()
    return {"status": "ok", "metrics": rate_limit_snapshot()}
