from datetime import timedelta
from typing import Dict, Optional

from fastapi import APIRouter, Query

router = APIRouter(prefix="/community", tags=["community"])


@router.get("/analytics/activation", response_model=dict)
def get_activation_funnel(
    requester_user_id: Optional[str] = Query(default=None),
    window_hours: int = Query(default=168, ge=1, le=720),
):
    # Lazy import avoids touching community globals during app startup.
    from app.routers import community as community_router

    cutoff = community_router._utc_now() - timedelta(hours=window_hours)
    recent = [
        row
        for row in community_router.COMMUNITY_ANALYTICS_EVENTS
        if community_router._parse_created_at(str(row.get("created_at"))) >= cutoff
        and str(row.get("event", "")).startswith("activation_")
        and (requester_user_id is None or str(row.get("user_id")) == requester_user_id)
    ]
    diagnostics = [
        row
        for row in community_router.COMMUNITY_DIAGNOSTIC_EVENTS
        if community_router._parse_created_at(str(row.get("created_at"))) >= cutoff
        and str(row.get("message", "")).startswith("activation_")
        and (requester_user_id is None or str(row.get("user_id")) == requester_user_id)
    ]

    by_event: Dict[str, int] = {}
    by_stage: Dict[str, int] = {}
    by_status: Dict[str, int] = {}

    for row in recent:
        event_name = str(row.get("event", "")).strip()
        if not event_name:
            continue
        by_event[event_name] = by_event.get(event_name, 0) + 1
        suffix = event_name.removeprefix("activation_")
        if "_" not in suffix:
            stage = suffix or "unknown"
            by_stage[stage] = by_stage.get(stage, 0) + 1
            continue
        stage, status = suffix.rsplit("_", 1)
        clean_stage = stage.strip() or "unknown"
        clean_status = status.strip() or "unknown"
        by_stage[clean_stage] = by_stage.get(clean_stage, 0) + 1
        by_status[clean_status] = by_status.get(clean_status, 0) + 1

    unique_users = sorted(
        {
            str(row.get("user_id", "")).strip()
            for row in recent
            if str(row.get("user_id", "")).strip()
        }
    )
    last_event_at = max((str(row.get("created_at", "")) for row in recent), default=None)
    top_failures = sorted(
        (
            {
                "event": str(row.get("event", "")).strip(),
                "created_at": str(row.get("created_at", "")).strip(),
                "user_id": str(row.get("user_id", "")).strip(),
                "error": str((row.get("metadata") or {}).get("error", "")).strip(),
            }
            for row in recent
            if str(row.get("event", "")).endswith("_failed")
        ),
        key=lambda item: item.get("created_at", ""),
        reverse=True,
    )[:25]

    return {
        "window_hours": window_hours,
        "requester_user_id": requester_user_id,
        "activation_event_count": len(recent),
        "activation_diagnostic_count": len(diagnostics),
        "unique_users": unique_users,
        "unique_user_count": len(unique_users),
        "last_event_at": last_event_at,
        "by_event": dict(sorted(by_event.items())),
        "by_stage": dict(sorted(by_stage.items())),
        "by_status": dict(sorted(by_status.items())),
        "top_failures": top_failures,
    }

