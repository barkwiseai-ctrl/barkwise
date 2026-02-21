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
    ServiceQuoteProviderResponseRequest,
    ServiceQuoteRequestCreate,
    ServiceQuoteRequestView,
    ServiceProviderCancelRequest,
    ServiceProvider,
    ServiceProviderCreateRequest,
    ServiceProviderDetails,
    ServiceProviderRestoreRequest,
    ServiceProviderUpdateRequest,
    VetCoachProfile,
    VetCoachSessionRequest,
    VetCoachSessionResult,
    VetGroomerVerificationRequest,
    VetGroomerVerificationResult,
    VetSpotlightActivateRequest,
    VetSpotlightActivationResult,
)
from app.services.service_store import (
    ServiceStoreConflictError,
    ServiceStoreError,
    ServiceStoreNotFoundError,
    ServiceStorePermissionError,
    service_store,
)
from app.services.notification_store import notification_store

router = APIRouter(tags=["listings"])


def _raise_service_http_error(exc: ServiceStoreError) -> None:
    if isinstance(exc, ServiceStoreNotFoundError):
        raise HTTPException(status_code=404, detail=str(exc))
    if isinstance(exc, ServiceStorePermissionError):
        raise HTTPException(status_code=403, detail=str(exc))
    if isinstance(exc, ServiceStoreConflictError):
        raise HTTPException(status_code=409, detail=str(exc))
    raise HTTPException(status_code=400, detail=str(exc))


def _dispatch_quote_reminders() -> None:
    reminders = service_store.dispatch_quote_reminders()
    for reminder in reminders:
        notification_store.create(
            user_id=str(reminder["owner_user_id"]),
            title="Quote request reminder",
            body=f"Please respond to quote request for {reminder['provider_name']} ({reminder['elapsed_minutes']}m).",
            category="booking",
            deep_link=f"quote:{reminder['quote_request_id']}",
        )


@router.get("/providers", response_model=list[ServiceProvider])
def list_providers(
    category: Optional[str] = Query(default=None),
    suburb: Optional[str] = Query(default=None),
    user_id: Optional[str] = Query(default=None),
    include_inactive: bool = Query(default=False),
    min_rating: Optional[float] = Query(default=None),
    max_distance_km: Optional[float] = Query(default=None),
    user_lat: Optional[float] = Query(default=None),
    user_lng: Optional[float] = Query(default=None),
    q: Optional[str] = Query(default=None),
    sort_by: str = Query(default="relevance"),
):
    _dispatch_quote_reminders()
    try:
        return service_store.list_providers(
            category=category,
            suburb=suburb,
            user_id=user_id,
            include_inactive=include_inactive,
            min_rating=min_rating,
            max_distance_km=max_distance_km,
            user_lat=user_lat,
            user_lng=user_lng,
            q=q,
            sort_by=sort_by,
        )
    except ServiceStoreError as exc:
        _raise_service_http_error(exc)


@router.post("/quotes/request", response_model=ServiceQuoteRequestView)
def request_quote(
    request: ServiceQuoteRequestCreate,
    authorization: Optional[str] = Header(default=None),
):
    assert_actor_authorized(actor_user_id=request.user_id, authorization=authorization)
    try:
        quote_request, targets = service_store.create_quote_request(
            user_id=request.user_id,
            category=request.category,
            suburb=request.suburb,
            preferred_window=request.preferred_window,
            pet_details=request.pet_details,
            note=request.note,
        )
        for target in targets:
            notification_store.create(
                user_id=target.owner_user_id,
                title="New quote request",
                body=f"{quote_request.category.replace('_', ' ')} in {quote_request.suburb} ({quote_request.preferred_window})",
                category="booking",
                deep_link=f"quote:{quote_request.id}",
            )
        return ServiceQuoteRequestView(quote_request=quote_request, targets=targets)
    except ServiceStoreError as exc:
        _raise_service_http_error(exc)


@router.post("/quotes/{quote_request_id}/respond", response_model=ServiceQuoteRequestView)
def respond_quote_request(
    quote_request_id: str,
    request: ServiceQuoteProviderResponseRequest,
    authorization: Optional[str] = Header(default=None),
):
    assert_actor_authorized(actor_user_id=request.actor_user_id, authorization=authorization)
    try:
        updated_request, targets = service_store.respond_quote_request(
            quote_request_id=quote_request_id,
            provider_id=request.provider_id,
            actor_user_id=request.actor_user_id,
            decision=request.decision,
            message=request.message,
        )
        notification_store.create(
            user_id=updated_request.user_id,
            title="Quote response received",
            body=f"A provider {request.decision} your quote request in {updated_request.suburb}.",
            category="booking",
            deep_link=f"quote:{updated_request.id}",
        )
        return ServiceQuoteRequestView(quote_request=updated_request, targets=targets)
    except ServiceStoreError as exc:
        _raise_service_http_error(exc)


@router.get("/vet-coach/profile", response_model=VetCoachProfile)
def get_vet_coach_profile(
    user_id: str = Query(...),
    authorization: Optional[str] = Header(default=None),
):
    assert_actor_authorized(actor_user_id=user_id, authorization=authorization)
    try:
        return service_store.get_vet_coach_profile(actor_user_id=user_id)
    except ServiceStoreError as exc:
        _raise_service_http_error(exc)


@router.post("/vet-coach/sessions", response_model=VetCoachSessionResult)
def submit_vet_coach_session(
    request: VetCoachSessionRequest,
    authorization: Optional[str] = Header(default=None),
):
    assert_actor_authorized(actor_user_id=request.actor_user_id, authorization=authorization)
    try:
        return service_store.record_vet_coach_session(
            actor_user_id=request.actor_user_id,
            duration_minutes=request.duration_minutes,
            quality_score=request.quality_score,
            topic=request.topic,
            note=request.note,
        )
    except ServiceStoreError as exc:
        _raise_service_http_error(exc)


@router.post("/vet-coach/spotlight/activate", response_model=VetSpotlightActivationResult)
def activate_vet_spotlight(
    request: VetSpotlightActivateRequest,
    authorization: Optional[str] = Header(default=None),
):
    assert_actor_authorized(actor_user_id=request.actor_user_id, authorization=authorization)
    try:
        return service_store.activate_vet_spotlight(
            actor_user_id=request.actor_user_id,
            minutes=request.minutes,
        )
    except ServiceStoreError as exc:
        _raise_service_http_error(exc)


@router.post("/providers/{provider_id}/vet-verify", response_model=VetGroomerVerificationResult)
def vet_verify_groomer(
    provider_id: str,
    request: VetGroomerVerificationRequest,
    authorization: Optional[str] = Header(default=None),
):
    assert_actor_authorized(actor_user_id=request.actor_user_id, authorization=authorization)
    try:
        result = service_store.verify_groomer_by_vet(
            provider_id=provider_id,
            actor_user_id=request.actor_user_id,
            decision=request.decision,
            confidence_score=request.confidence_score,
            note=request.note,
        )
        owner_ids = service_store.list_provider_owner_user_ids(provider_id)
        for owner_id in owner_ids:
            if owner_id and owner_id != request.actor_user_id:
                notification_store.create(
                    user_id=owner_id,
                    title="Listing reviewed by vet",
                    body=(
                        f"Your listing is now Vet-Checked until {result.verification.valid_until[:10]}"
                        if result.verification.valid_until
                        else "A vet submitted improvement notes for your listing"
                    ),
                    category="booking",
                    deep_link=f"provider:{provider_id}",
                )
        return result
    except ServiceStoreError as exc:
        _raise_service_http_error(exc)


@router.get("/providers/{provider_id}", response_model=ServiceProviderDetails)
def provider_details(provider_id: str):
    details = service_store.get_provider_details(provider_id)
    if not details:
        raise HTTPException(status_code=404, detail="Provider not found")
    return details


def _create_provider_impl(
    request: ServiceProviderCreateRequest,
    authorization: Optional[str] = Header(default=None),
) -> ServiceProvider:
    assert_actor_authorized(actor_user_id=request.user_id, authorization=authorization)
    try:
        return service_store.add_provider(
            owner_user_id=request.user_id,
            name=request.name,
            category=request.category,
            suburb=request.suburb,
            description=request.description,
            price_from=request.price_from,
            full_description=request.full_description,
            image_urls=request.image_urls,
            latitude=request.latitude,
            longitude=request.longitude,
        )
    except ServiceStoreError as exc:
        _raise_service_http_error(exc)


@router.post("/providers", response_model=ServiceProvider)
def create_provider(request: ServiceProviderCreateRequest, authorization: Optional[str] = Header(default=None)):
    return _create_provider_impl(request=request, authorization=authorization)


@router.api_route("/provider", methods=["POST", "PUT"], response_model=ServiceProvider)
def create_provider_singular(request: ServiceProviderCreateRequest, authorization: Optional[str] = Header(default=None)):
    return _create_provider_impl(request=request, authorization=authorization)


@router.api_route("/providers/create", methods=["POST", "PUT"], response_model=ServiceProvider)
def create_provider_legacy_plural(request: ServiceProviderCreateRequest, authorization: Optional[str] = Header(default=None)):
    return _create_provider_impl(request=request, authorization=authorization)


@router.api_route("/provider/create", methods=["POST", "PUT"], response_model=ServiceProvider)
def create_provider_legacy_singular(request: ServiceProviderCreateRequest, authorization: Optional[str] = Header(default=None)):
    return _create_provider_impl(request=request, authorization=authorization)


@router.post("/providers/{provider_id}/update", response_model=ServiceProvider)
def update_provider(
    provider_id: str,
    request: ServiceProviderUpdateRequest,
    authorization: Optional[str] = Header(default=None),
):
    assert_actor_authorized(actor_user_id=request.user_id, authorization=authorization)
    try:
        return service_store.update_provider(
            provider_id=provider_id,
            actor_user_id=request.user_id,
            name=request.name,
            suburb=request.suburb,
            description=request.description,
            price_from=request.price_from,
            full_description=request.full_description,
            image_urls=request.image_urls,
            latitude=request.latitude,
            longitude=request.longitude,
        )
    except ServiceStoreError as exc:
        _raise_service_http_error(exc)


@router.post("/providers/{provider_id}/cancel")
def cancel_provider(
    provider_id: str,
    request: ServiceProviderCancelRequest,
    authorization: Optional[str] = Header(default=None),
):
    assert_actor_authorized(actor_user_id=request.user_id, authorization=authorization)
    try:
        service_store.cancel_provider(provider_id=provider_id, actor_user_id=request.user_id)
        return {"status": "cancelled", "provider_id": provider_id}
    except ServiceStoreError as exc:
        _raise_service_http_error(exc)


@router.post("/providers/{provider_id}/restore", response_model=ServiceProvider)
def restore_provider(
    provider_id: str,
    request: ServiceProviderRestoreRequest,
    authorization: Optional[str] = Header(default=None),
):
    assert_actor_authorized(actor_user_id=request.user_id, authorization=authorization)
    try:
        return service_store.restore_provider(provider_id=provider_id, actor_user_id=request.user_id)
    except ServiceStoreError as exc:
        _raise_service_http_error(exc)


@router.get("/providers/{provider_id}/availability", response_model=list[ServiceAvailabilitySlot])
def provider_availability(provider_id: str, date: str = Query(...)):
    try:
        return service_store.get_available_slots(provider_id=provider_id, slot_date=date)
    except ServiceStoreError as exc:
        _raise_service_http_error(exc)


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
    except ServiceStoreError as exc:
        _raise_service_http_error(exc)


@router.get("/bookings", response_model=list[Booking])
def list_bookings(
    user_id: Optional[str] = Query(default=None),
    role: Optional[str] = Query(default=None),
):
    _dispatch_quote_reminders()
    try:
        return service_store.list_bookings(user_id=user_id, role=role)
    except ServiceStoreError as exc:
        _raise_service_http_error(exc)


@router.post("/bookings/holds", response_model=BookingHold)
def create_booking_hold(request: BookingHoldRequest, authorization: Optional[str] = Header(default=None)):
    assert_actor_authorized(actor_user_id=request.user_id, authorization=authorization)
    try:
        return service_store.create_booking_hold(request)
    except ServiceStoreError as exc:
        _raise_service_http_error(exc)


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
    except ServiceStoreError as exc:
        _raise_service_http_error(exc)


@router.post("/providers/{provider_id}/blackouts", response_model=ProviderBlackout)
def create_provider_blackout(
    provider_id: str,
    request: ProviderBlackoutRequest,
    authorization: Optional[str] = Header(default=None),
):
    assert_actor_authorized(actor_user_id=request.actor_user_id, authorization=authorization)
    try:
        return service_store.create_provider_blackout(provider_id=provider_id, request=request)
    except ServiceStoreError as exc:
        _raise_service_http_error(exc)


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
    try:
        return service_store.list_calendar_events(
            user_id=user_id,
            date_from=date_from,
            date_to=date_to,
            role=role,
        )
    except ServiceStoreError as exc:
        _raise_service_http_error(exc)
