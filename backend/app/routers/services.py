from typing import Optional

from fastapi import APIRouter, Header, HTTPException, Query

from app.auth import assert_actor_authorized
from app.models import (
    Booking,
    BookingHold,
    BookingHoldRequest,
    BookingRequest,
    BookingStatusUpdateRequest,
    CalendarEvent,
    ProviderBlackout,
    ProviderBlackoutRequest,
    ServiceAvailabilitySlot,
    ServiceProvider,
    ServiceProviderDetails,
)
from app.services.service_store import service_store
from app.services.notification_store import notification_store

router = APIRouter(prefix="/services", tags=["services"])


@router.get("/providers", response_model=list[ServiceProvider])
def list_providers(
    category: Optional[str] = Query(default=None),
    suburb: Optional[str] = Query(default=None),
    min_rating: Optional[float] = Query(default=None),
    max_distance_km: Optional[float] = Query(default=None),
    user_lat: Optional[float] = Query(default=None),
    user_lng: Optional[float] = Query(default=None),
    q: Optional[str] = Query(default=None),
    sort_by: str = Query(default="relevance"),
):
    return service_store.list_providers(
        category=category,
        suburb=suburb,
        min_rating=min_rating,
        max_distance_km=max_distance_km,
        user_lat=user_lat,
        user_lng=user_lng,
        q=q,
        sort_by=sort_by,
    )


@router.get("/providers/{provider_id}", response_model=ServiceProviderDetails)
def provider_details(provider_id: str):
    details = service_store.get_provider_details(provider_id)
    if not details:
        raise HTTPException(status_code=404, detail="Provider not found")
    return details


@router.get("/providers/{provider_id}/availability", response_model=list[ServiceAvailabilitySlot])
def provider_availability(provider_id: str, date: str = Query(...)):
    try:
        return service_store.get_available_slots(provider_id=provider_id, slot_date=date)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))


@router.post("/bookings", response_model=Booking)
def create_booking(request: BookingRequest, authorization: Optional[str] = Header(default=None)):
    assert_actor_authorized(actor_user_id=request.user_id, authorization=authorization)
    try:
        booking = service_store.create_booking(request)
        owners = service_store.list_provider_owner_user_ids(booking.provider_id)
        for owner_id in owners:
            if owner_id != booking.owner_user_id:
                notification_store.create(
                    user_id=owner_id,
                    title="New booking request",
                    body=f"{booking.pet_name} requested {booking.date} {booking.time_slot}",
                    category="booking",
                    deep_link=f"booking:{booking.id}",
                )
        return booking
    except ValueError as exc:
        message = str(exc)
        if "Provider not found" in message:
            raise HTTPException(status_code=404, detail=message)
        raise HTTPException(status_code=400, detail=message)


@router.get("/bookings", response_model=list[Booking])
def list_bookings(
    user_id: Optional[str] = Query(default=None),
    role: Optional[str] = Query(default=None),
):
    return service_store.list_bookings(user_id=user_id, role=role)


@router.post("/bookings/holds", response_model=BookingHold)
def create_booking_hold(request: BookingHoldRequest, authorization: Optional[str] = Header(default=None)):
    assert_actor_authorized(actor_user_id=request.user_id, authorization=authorization)
    try:
        return service_store.create_booking_hold(request)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))


@router.post("/bookings/{booking_id}/status", response_model=Booking)
def update_booking_status(
    booking_id: str,
    request: BookingStatusUpdateRequest,
    authorization: Optional[str] = Header(default=None),
):
    assert_actor_authorized(actor_user_id=request.actor_user_id, authorization=authorization)
    try:
        booking = service_store.update_booking_status(booking_id=booking_id, update=request)
        parties = {booking.owner_user_id, *service_store.list_provider_owner_user_ids(booking.provider_id)}
        for user_id in parties:
            if user_id and user_id != request.actor_user_id:
                notification_store.create(
                    user_id=user_id,
                    title="Booking updated",
                    body=f"Booking {booking.id} is now {booking.status}",
                    category="booking",
                    deep_link=f"booking:{booking.id}",
                )
        return booking
    except ValueError as exc:
        message = str(exc)
        if "not found" in message.lower():
            raise HTTPException(status_code=404, detail=message)
        raise HTTPException(status_code=400, detail=message)


@router.post("/providers/{provider_id}/blackouts", response_model=ProviderBlackout)
def create_provider_blackout(
    provider_id: str,
    request: ProviderBlackoutRequest,
    authorization: Optional[str] = Header(default=None),
):
    assert_actor_authorized(actor_user_id=request.actor_user_id, authorization=authorization)
    try:
        return service_store.create_provider_blackout(provider_id=provider_id, request=request)
    except ValueError as exc:
        message = str(exc)
        if "not found" in message.lower():
            raise HTTPException(status_code=404, detail=message)
        raise HTTPException(status_code=400, detail=message)


@router.get("/providers/{provider_id}/blackouts", response_model=list[ProviderBlackout])
def list_provider_blackouts(provider_id: str):
    return service_store.list_provider_blackouts(provider_id=provider_id)


@router.get("/calendar/events", response_model=list[CalendarEvent])
def list_calendar_events(
    user_id: str = Query(...),
    date_from: str = Query(...),
    date_to: str = Query(...),
    role: str = Query(default="all"),
):
    return service_store.list_calendar_events(
        user_id=user_id,
        date_from=date_from,
        date_to=date_to,
        role=role,
    )
