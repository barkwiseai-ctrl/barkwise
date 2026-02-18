from typing import Any, Dict, Literal, Optional

from pydantic import BaseModel, Field


class ServiceProvider(BaseModel):
    id: str
    name: str
    category: Literal["dog_walking", "grooming"]
    suburb: str
    rating: float
    review_count: int
    price_from: int
    description: str
    full_description: str = ""
    image_urls: list[str] = Field(default_factory=list)
    latitude: float = 0.0
    longitude: float = 0.0
    distance_km: Optional[float] = None
    owner_user_id: Optional[str] = None
    owner_label: Optional[str] = None


class Review(BaseModel):
    id: str
    provider_id: str
    author: str
    rating: int = Field(ge=1, le=5)
    comment: str


class ServiceProviderDetails(BaseModel):
    provider: ServiceProvider
    reviews: list[Review]


class BookingRequest(BaseModel):
    user_id: str = "guest_user"
    provider_id: str
    pet_name: str
    date: str
    time_slot: str
    note: str = ""


class Booking(BaseModel):
    id: str
    owner_user_id: str = "guest_user"
    provider_id: str
    pet_name: str
    date: str
    time_slot: str
    note: str = ""
    status: Literal[
        "requested",
        "provider_confirmed",
        "provider_declined",
        "in_progress",
        "completed",
        "cancelled_by_owner",
        "cancelled_by_provider",
        "reschedule_requested",
        "rescheduled",
    ]


class ServiceAvailabilitySlot(BaseModel):
    date: str
    time_slot: str
    available: bool
    reason: Optional[str] = None


class BookingHoldRequest(BaseModel):
    user_id: str
    provider_id: str
    date: str
    time_slot: str


class BookingHold(BaseModel):
    id: str
    provider_id: str
    owner_user_id: str
    date: str
    time_slot: str
    expires_at: str


class BookingStatusUpdateRequest(BaseModel):
    actor_user_id: str
    status: Literal[
        "provider_confirmed",
        "provider_declined",
        "in_progress",
        "completed",
        "cancelled_by_owner",
        "cancelled_by_provider",
        "reschedule_requested",
        "rescheduled",
    ]
    note: str = ""


class ProviderBlackoutRequest(BaseModel):
    actor_user_id: str
    date: str
    time_slot: str
    reason: str = ""


class ProviderBlackout(BaseModel):
    id: str
    provider_id: str
    date: str
    time_slot: str
    reason: str = ""


class CalendarEvent(BaseModel):
    id: str
    type: Literal["booking", "hold", "blackout"]
    role: Literal["owner", "provider", "system"]
    title: str
    subtitle: str
    date: str
    time_slot: str
    status: str
    provider_id: Optional[str] = None
    booking_id: Optional[str] = None


class ChatRequest(BaseModel):
    user_id: str
    message: str
    suburb: Optional[str] = None


class ChatTurn(BaseModel):
    role: Literal["user", "assistant"]
    content: str


class PetProfileSuggestion(BaseModel):
    pet_name: Optional[str] = None
    pet_type: Optional[str] = None
    breed: Optional[str] = None
    age_years: Optional[float] = None
    weight_kg: Optional[float] = None
    suburb: Optional[str] = None
    concerns: list[str] = Field(default_factory=list)


class CtaChip(BaseModel):
    label: str
    action: Literal[
        "open_services",
        "open_community",
        "create_lost_found",
        "find_dog_walkers",
        "find_groomers",
        "accept_profile_card",
        "submit_provider_listing",
        "join_group",
    ]
    payload: Dict[str, Any] = Field(default_factory=dict)


class ChatResponse(BaseModel):
    answer: str
    suggested_profile: Dict[str, Any]
    cta_chips: list[CtaChip]
    conversation: list[ChatTurn] = Field(default_factory=list)
    profile_suggestion: Optional[PetProfileSuggestion] = None
    a2ui_messages: list[Dict[str, Any]] = Field(default_factory=list)


class ProfileAcceptRequest(BaseModel):
    user_id: str


class ProviderSubmitRequest(BaseModel):
    user_id: str


class CommunityPostCreate(BaseModel):
    type: Literal["lost_found", "group_post"]
    title: str
    body: str
    suburb: str


class CommunityPost(BaseModel):
    id: str
    type: Literal["lost_found", "group_post"]
    title: str
    body: str
    suburb: str
    created_at: Optional[str] = None


class Group(BaseModel):
    id: str
    name: str
    suburb: str
    member_count: int
    official: bool = False
    owner_user_id: Optional[str] = None


class GroupView(BaseModel):
    id: str
    name: str
    suburb: str
    member_count: int
    official: bool
    owner_user_id: Optional[str] = None
    membership_status: Literal["none", "pending", "member"] = "none"
    is_admin: bool = False
    pending_request_count: int = 0


class GroupCreateRequest(BaseModel):
    user_id: str
    name: str
    suburb: str


class GroupJoinRequest(BaseModel):
    user_id: str


class GroupAddMemberRequest(BaseModel):
    requester_user_id: str
    member_user_id: str


class GroupJoinRecord(BaseModel):
    group_id: str
    user_id: str
    status: Literal["pending", "member"]


class GroupJoinModerationRequest(BaseModel):
    requester_user_id: str
    member_user_id: str
    action: Literal["approve", "reject"]


class GroupJoinRequestView(BaseModel):
    group_id: str
    user_id: str
    status: Literal["pending"]


class CommunityEvent(BaseModel):
    id: str
    title: str
    description: str
    suburb: str
    date: str
    group_id: Optional[str] = None
    attendee_count: int = 0
    created_by: str
    status: Literal["approved", "pending_approval"] = "approved"


class CommunityEventView(BaseModel):
    id: str
    title: str
    description: str
    suburb: str
    date: str
    group_id: Optional[str] = None
    attendee_count: int = 0
    created_by: str
    rsvp_status: Literal["none", "attending"] = "none"
    status: Literal["approved", "pending_approval"] = "approved"


class CommunityEventCreateRequest(BaseModel):
    user_id: str
    title: str
    description: str
    suburb: str
    date: str
    group_id: Optional[str] = None


class CommunityEventRsvpRequest(BaseModel):
    user_id: str
    status: Literal["attending", "none"] = "attending"


class EventRsvpRecord(BaseModel):
    event_id: str
    user_id: str
    status: Literal["attending"]


class GroupInviteCreateRequest(BaseModel):
    group_id: str
    inviter_user_id: str


class GroupInviteResolveResponse(BaseModel):
    token: str
    group_id: str
    group_name: str
    suburb: str
    inviter_user_id: str
    expires_at: str
    invite_url: str


class GroupInviteCreateResponse(GroupInviteResolveResponse):
    pass


class GroupOnboardingCompleteRequest(BaseModel):
    invite_token: str
    owner_name: str
    dog_name: str
    suburb: Optional[str] = None
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    share_photo_to_group: bool = True
    photo_source: Optional[str] = None


class GroupOnboardingCompleteResponse(BaseModel):
    user_id: str
    group_id: str
    membership_status: Literal["member", "pending"]
    created_post_id: Optional[str] = None


class AuthLoginRequest(BaseModel):
    user_id: str
    password: str = "petsocial-demo"


class AuthLoginResponse(BaseModel):
    access_token: str
    token_type: Literal["bearer"] = "bearer"
    user_id: str
    expires_at: str


class AuthMeResponse(BaseModel):
    user_id: str


class DeviceTokenRegisterRequest(BaseModel):
    user_id: str
    device_token: str
    platform: Literal["android", "ios", "web"] = "android"


class NotificationRecord(BaseModel):
    id: str
    user_id: str
    title: str
    body: str
    category: Literal["booking", "message", "community", "system"] = "system"
    read: bool = False
    created_at: str
    deep_link: Optional[str] = None
