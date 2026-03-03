from typing import Any, Dict, Literal, Optional

from pydantic import BaseModel, Field

MAX_USER_ID_LENGTH = 64
MAX_CHAT_MESSAGE_LENGTH = 2000
MAX_SUBURB_LENGTH = 80


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
    status: Literal["active", "cancelled"] = "active"
    response_time_minutes: Optional[int] = None
    local_bookers_this_month: int = 0
    shared_group_bookers: int = 0
    social_proof: list[str] = Field(default_factory=list)
    quote_sprint_tier: Literal["none", "bronze", "silver", "gold", "platinum"] = "none"
    quote_response_rate_pct: int = 0
    quote_response_streak: int = 0
    vet_checked: bool = False
    vet_checked_until: Optional[str] = None
    vet_checked_by: Optional[str] = None
    highlighted_vet: Optional[str] = None
    highlighted_vet_until: Optional[str] = None


class Review(BaseModel):
    id: str
    provider_id: str
    author: str
    rating: int = Field(ge=1, le=5)
    comment: str


class ServiceProviderDetails(BaseModel):
    provider: ServiceProvider
    reviews: list[Review]


class ServiceProviderCreateRequest(BaseModel):
    user_id: str
    name: str
    category: Literal["dog_walking", "grooming"]
    suburb: str
    description: str
    price_from: int = Field(ge=1)
    full_description: Optional[str] = None
    image_urls: list[str] = Field(default_factory=list)
    latitude: Optional[float] = None
    longitude: Optional[float] = None


class ServiceProviderUpdateRequest(BaseModel):
    user_id: str
    name: Optional[str] = None
    suburb: Optional[str] = None
    description: Optional[str] = None
    price_from: Optional[int] = Field(default=None, ge=1)
    full_description: Optional[str] = None
    image_urls: Optional[list[str]] = None
    latitude: Optional[float] = None
    longitude: Optional[float] = None


class ServiceProviderCancelRequest(BaseModel):
    user_id: str


class ServiceProviderRestoreRequest(BaseModel):
    user_id: str


class ServiceQuoteRequestCreate(BaseModel):
    user_id: str
    category: Literal["dog_walking", "grooming"]
    suburb: Optional[str] = None
    preferred_window: str
    pet_details: str
    note: str = ""


class ServiceQuoteProviderResponseRequest(BaseModel):
    actor_user_id: str
    provider_id: str
    decision: Literal["accepted", "declined"]
    message: str = ""


class ServiceQuoteTarget(BaseModel):
    provider_id: str
    provider_name: str
    owner_user_id: str
    status: Literal["pending", "accepted", "declined"]
    response_message: str = ""
    created_at: str
    responded_at: Optional[str] = None
    reminder_15_sent: bool = False
    reminder_60_sent: bool = False


class ServiceQuoteRequest(BaseModel):
    id: str
    user_id: str
    category: Literal["dog_walking", "grooming"]
    suburb: str
    preferred_window: str
    pet_details: str
    note: str = ""
    status: Literal["pending", "responded", "closed"] = "pending"
    created_at: str
    updated_at: str


class ServiceQuoteRequestView(BaseModel):
    quote_request: ServiceQuoteRequest
    targets: list[ServiceQuoteTarget]


class ServiceRecommendationsResponse(BaseModel):
    providers: list[ServiceProvider]
    inferred_suburb: Optional[str] = None
    suburb_source: Literal["explicit_suburb", "dog_park_membership", "group_membership", "none"] = "none"


class VetCoachSessionRequest(BaseModel):
    actor_user_id: str
    duration_minutes: int = Field(ge=1, le=240)
    quality_score: float = Field(ge=0.0, le=1.0)
    topic: str = ""
    note: str = ""


class VetSpotlightActivateRequest(BaseModel):
    actor_user_id: str
    minutes: int = Field(ge=5, le=600)


class VetCoachProfile(BaseModel):
    user_id: str
    spotlight_minutes: int = 0
    coaching_minutes: int = 0
    coaching_sessions: int = 0
    coach_quality_score: float = 0.0
    highlighted_until: Optional[str] = None
    badge_tier: Literal["none", "bronze", "silver", "gold", "platinum"] = "none"


class VetCoachSessionResult(BaseModel):
    session_id: str
    minutes_earned: int
    profile: VetCoachProfile


class VetSpotlightActivationResult(BaseModel):
    minutes_spent: int
    profile: VetCoachProfile


class VetGroomerVerificationRequest(BaseModel):
    actor_user_id: str
    decision: Literal["approved", "needs_improvement"]
    confidence_score: float = Field(default=0.8, ge=0.0, le=1.0)
    note: str = ""


class VetGroomerVerification(BaseModel):
    id: str
    provider_id: str
    vet_user_id: str
    decision: Literal["approved", "needs_improvement"]
    confidence_score: float
    note: str = ""
    created_at: str
    valid_until: Optional[str] = None
    spotlight_minutes_earned: int = 0


class VetGroomerVerificationResult(BaseModel):
    verification: VetGroomerVerification
    provider: ServiceProvider
    vet_profile: VetCoachProfile


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
    date: Optional[str] = None
    time_slot: Optional[str] = None


class BookingStatusHistoryEntry(BaseModel):
    id: str
    booking_id: str
    actor_user_id: str
    from_status: str
    to_status: str
    note: str = ""
    created_at: str


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
    user_id: str = Field(min_length=1, max_length=MAX_USER_ID_LENGTH)
    message: str = Field(min_length=1, max_length=MAX_CHAT_MESSAGE_LENGTH)
    suburb: Optional[str] = Field(default=None, max_length=MAX_SUBURB_LENGTH)


class ChatCitation(BaseModel):
    title: str
    source: str
    url: Optional[str] = None
    snippet: Optional[str] = None


class ChatTurn(BaseModel):
    role: Literal["user", "assistant"]
    content: str
    answer_source: Optional[str] = None
    answer_badges: list[str] = Field(default_factory=list)
    citations: list[ChatCitation] = Field(default_factory=list)


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
    answer_source: str = "fallback"
    answer_badges: list[str] = Field(default_factory=list)
    citations: list[ChatCitation] = Field(default_factory=list)


class ProfileAcceptRequest(BaseModel):
    user_id: str = Field(min_length=1, max_length=MAX_USER_ID_LENGTH)


class ProviderSubmitRequest(BaseModel):
    user_id: str = Field(min_length=1, max_length=MAX_USER_ID_LENGTH)


class CommunityPostCreate(BaseModel):
    type: Literal["lost_found", "group_post"]
    user_id: Optional[str] = None
    title: str
    body: str
    suburb: str
    alert_type: Optional[Literal["lost", "found"]] = None
    alert_status: Optional[Literal["open", "reunited", "owner_found", "expired"]] = None
    pet_name: Optional[str] = None
    pet_traits: Optional[str] = None
    last_seen_at: Optional[str] = None
    last_seen_location: Optional[str] = None
    contact_pref: Optional[str] = None
    photo_urls: list[str] = Field(default_factory=list)
    latitude: Optional[float] = Field(default=None, ge=-90, le=90)
    longitude: Optional[float] = Field(default=None, ge=-180, le=180)


class CommunityPost(BaseModel):
    id: str
    type: Literal["lost_found", "group_post"]
    created_by: Optional[str] = None
    title: str
    body: str
    suburb: str
    created_at: Optional[str] = None
    alert_type: Optional[Literal["lost", "found"]] = None
    alert_status: Optional[Literal["open", "reunited", "owner_found", "expired"]] = None
    pet_name: Optional[str] = None
    pet_traits: Optional[str] = None
    last_seen_at: Optional[str] = None
    last_seen_location: Optional[str] = None
    contact_pref: Optional[str] = None
    photo_urls: list[str] = Field(default_factory=list)
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    resolved_at: Optional[str] = None
    resolved_note: Optional[str] = None
    follow_up_due_at: Optional[str] = None
    expires_at: Optional[str] = None


class CommunityPostResolveRequest(BaseModel):
    requester_user_id: Optional[str] = None
    status: Literal["reunited", "owner_found", "expired"]
    note: str = ""


class CommunityPostUpdateRequest(BaseModel):
    requester_user_id: Optional[str] = None
    title: Optional[str] = None
    body: Optional[str] = None
    pet_name: Optional[str] = None
    pet_traits: Optional[str] = None
    last_seen_at: Optional[str] = None
    last_seen_location: Optional[str] = None
    contact_pref: Optional[str] = None
    photo_urls: Optional[list[str]] = None
    latitude: Optional[float] = Field(default=None, ge=-90, le=90)
    longitude: Optional[float] = Field(default=None, ge=-180, le=180)
    clear_last_seen_at: bool = False


class CommunityPostPhotoUploadResponse(BaseModel):
    url: str
    content_type: str
    size_bytes: int


class CommunityPostPhotoUploadRequest(BaseModel):
    requester_user_id: str
    filename: str
    content_type: str
    data_base64: str


class CommunityComment(BaseModel):
    id: str
    post_id: str
    user_id: str
    body: str
    parent_comment_id: Optional[str] = None
    created_at: str
    status: Literal["active", "removed_by_moderator"] = "active"
    moderated_at: Optional[str] = None
    moderated_by: Optional[str] = None
    moderation_note: Optional[str] = None


class CommunityCommentCreateRequest(BaseModel):
    user_id: str
    body: str
    parent_comment_id: Optional[str] = None


class CommunityCommentModerationRequest(BaseModel):
    requester_user_id: str
    action: Literal["remove", "restore"]
    note: str = ""


class CommunityReportCreateRequest(BaseModel):
    reporter_user_id: str
    target_type: Literal["post", "user"]
    target_id: str
    reason: str
    details: str = ""


class CommunityReport(BaseModel):
    id: str
    reporter_user_id: str
    target_type: Literal["post", "user"]
    target_id: str
    reason: str
    details: str = ""
    status: Literal["pending", "dismissed", "action_taken"] = "pending"
    created_at: str
    resolved_at: Optional[str] = None
    resolved_by: Optional[str] = None
    resolution_note: Optional[str] = None


class CommunityReportResolveRequest(BaseModel):
    requester_user_id: str
    action: Literal["dismissed", "action_taken"]
    note: str = ""


class CommunityBlockUserRequest(BaseModel):
    requester_user_id: str
    target_user_id: str


class CommunityBlockUserResponse(BaseModel):
    requester_user_id: str
    blocked_user_ids: list[str] = Field(default_factory=list)


class CommunityAnalyticsEventCreateRequest(BaseModel):
    user_id: str
    event: str
    category: Literal["community", "lost_found"] = "community"
    metadata: Dict[str, Any] = Field(default_factory=dict)
    duration_ms: Optional[int] = None


class CommunityDiagnosticEventCreateRequest(BaseModel):
    user_id: str
    kind: Literal["crash", "perf", "error"] = "error"
    message: str
    context: Dict[str, Any] = Field(default_factory=dict)
    duration_ms: Optional[int] = None


class CommunityFunnelMetrics(BaseModel):
    window_hours: int
    community_feed_views: int = 0
    lost_found_feed_views: int = 0
    lost_found_create_attempts: int = 0
    lost_found_create_successes: int = 0
    lost_found_resolution_actions: int = 0
    moderation_reports_submitted: int = 0
    blocks_submitted: int = 0
    lost_found_create_conversion_pct: float = 0.0


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
    group_badges: list[str] = Field(default_factory=list)
    cooperative_score: int = 0
    my_pack_builder_points: int = 0
    my_clean_park_points: int = 0


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


class GroupChallenge(BaseModel):
    id: str
    group_id: str
    type: Literal["pack_builder", "clean_park_streak"]
    title: str
    description: str
    target_count: int
    progress_count: int
    status: Literal["active", "completed"] = "active"
    reward_label: str
    start_at: str
    end_at: str


class GroupChallengeView(BaseModel):
    challenge: GroupChallenge
    my_contribution_count: int = 0


class GroupChallengeParticipationRequest(BaseModel):
    user_id: str
    challenge_type: Literal["pack_builder", "clean_park_streak"]
    contribution_count: int = Field(default=1, ge=1, le=50)
    note: str = ""


class GroupChallengeParticipationResult(BaseModel):
    challenge: GroupChallenge
    my_contribution_count: int
    contribution_count: int
    reward_unlocked: bool = False
    unlocked_badges: list[str] = Field(default_factory=list)


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


class AuthInviteCreateRequest(BaseModel):
    requester_user_id: str
    email: str
    user_id: Optional[str] = None
    ttl_minutes: int = Field(default=60, ge=5, le=1440)


class AuthInviteResponse(BaseModel):
    invite_id: str
    user_id: str
    email: str
    expires_at: str


class AuthOtpRequest(BaseModel):
    invite_id: str
    email: str


class AuthOtpRequestResponse(BaseModel):
    status: Literal["otp_sent"] = "otp_sent"
    expires_at: str


class AuthOtpVerifyRequest(BaseModel):
    invite_id: str
    email: str
    otp_code: str = Field(min_length=4, max_length=8)


class AuthOtpVerifyResponse(BaseModel):
    access_token: str
    token_type: Literal["bearer"] = "bearer"
    user_id: str
    expires_at: str


class AuthLogoutResponse(BaseModel):
    status: Literal["ok"] = "ok"


class AuthDeleteResponse(BaseModel):
    status: Literal["deleted"] = "deleted"
    user_id: str


class MessageThreadView(BaseModel):
    id: str
    participant_user_id: str
    last_message: str = ""
    last_message_at: str
    unread_count: int = 0


class MessageRecord(BaseModel):
    id: str
    thread_id: str
    sender_user_id: str
    recipient_user_id: str
    body: str
    created_at: str


class MessageSendRequest(BaseModel):
    user_id: str = Field(min_length=1, max_length=MAX_USER_ID_LENGTH)
    recipient_user_id: str = Field(min_length=1, max_length=MAX_USER_ID_LENGTH)
    body: str = Field(min_length=1, max_length=MAX_CHAT_MESSAGE_LENGTH)


class MessageMarkReadRequest(BaseModel):
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
