package com.petsocial.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ServiceProvider(
    val id: String,
    val name: String,
    val category: String,
    val suburb: String,
    val rating: Double,
    @SerialName("review_count") val reviewCount: Int,
    @SerialName("price_from") val priceFrom: Int,
    val description: String,
    @SerialName("full_description") val fullDescription: String = "",
    @SerialName("image_urls") val imageUrls: List<String> = emptyList(),
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    @SerialName("distance_km") val distanceKm: Double? = null,
    @SerialName("owner_user_id") val ownerUserId: String? = null,
    @SerialName("owner_label") val ownerLabel: String? = null,
    val status: String = "active",
    @SerialName("response_time_minutes") val responseTimeMinutes: Int? = null,
    @SerialName("local_bookers_this_month") val localBookersThisMonth: Int = 0,
    @SerialName("shared_group_bookers") val sharedGroupBookers: Int = 0,
    @SerialName("social_proof") val socialProof: List<String> = emptyList(),
    @SerialName("quote_sprint_tier") val quoteSprintTier: String = "none",
    @SerialName("quote_response_rate_pct") val quoteResponseRatePct: Int = 0,
    @SerialName("quote_response_streak") val quoteResponseStreak: Int = 0,
    @SerialName("vet_checked") val vetChecked: Boolean = false,
    @SerialName("vet_checked_until") val vetCheckedUntil: String? = null,
    @SerialName("vet_checked_by") val vetCheckedBy: String? = null,
    @SerialName("highlighted_vet") val highlightedVet: String? = null,
    @SerialName("highlighted_vet_until") val highlightedVetUntil: String? = null,
)

@Serializable
data class Review(
    val id: String,
    @SerialName("provider_id") val providerId: String,
    val author: String,
    val rating: Int,
    val comment: String,
)

@Serializable
data class ServiceProviderDetailsResponse(
    val provider: ServiceProvider,
    val reviews: List<Review>,
)

@Serializable
data class CreateServiceProviderRequest(
    @SerialName("user_id") val userId: String,
    val name: String,
    val category: String,
    val suburb: String,
    val description: String,
    @SerialName("price_from") val priceFrom: Int,
    @SerialName("full_description") val fullDescription: String? = null,
    @SerialName("image_urls") val imageUrls: List<String> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class UpdateServiceProviderRequest(
    @SerialName("user_id") val userId: String,
    val name: String? = null,
    val suburb: String? = null,
    val description: String? = null,
    @SerialName("price_from") val priceFrom: Int? = null,
    @SerialName("full_description") val fullDescription: String? = null,
    @SerialName("image_urls") val imageUrls: List<String>? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class CancelServiceProviderRequest(
    @SerialName("user_id") val userId: String,
)

@Serializable
data class RestoreServiceProviderRequest(
    @SerialName("user_id") val userId: String,
)

@Serializable
data class ServiceQuoteRequestCreate(
    @SerialName("user_id") val userId: String,
    val category: String,
    val suburb: String? = null,
    @SerialName("preferred_window") val preferredWindow: String,
    @SerialName("pet_details") val petDetails: String,
    val note: String = "",
)

@Serializable
data class ServiceQuoteProviderResponseRequest(
    @SerialName("actor_user_id") val actorUserId: String,
    @SerialName("provider_id") val providerId: String,
    val decision: String,
    val message: String = "",
)

@Serializable
data class ServiceQuoteTarget(
    @SerialName("provider_id") val providerId: String,
    @SerialName("provider_name") val providerName: String,
    @SerialName("owner_user_id") val ownerUserId: String,
    val status: String,
    @SerialName("response_message") val responseMessage: String = "",
    @SerialName("created_at") val createdAt: String,
    @SerialName("responded_at") val respondedAt: String? = null,
    @SerialName("reminder_15_sent") val reminder15Sent: Boolean = false,
    @SerialName("reminder_60_sent") val reminder60Sent: Boolean = false,
)

@Serializable
data class ServiceQuoteRequest(
    val id: String,
    @SerialName("user_id") val userId: String,
    val category: String,
    val suburb: String,
    @SerialName("preferred_window") val preferredWindow: String,
    @SerialName("pet_details") val petDetails: String,
    val note: String = "",
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class ServiceQuoteRequestView(
    @SerialName("quote_request") val quoteRequest: ServiceQuoteRequest,
    val targets: List<ServiceQuoteTarget>,
)

@Serializable
data class ServiceQuoteOfferCreateRequest(
    @SerialName("actor_user_id") val actorUserId: String,
    @SerialName("provider_id") val providerId: String,
    @SerialName("price_cents") val priceCents: Int,
    val currency: String = "AUD",
    @SerialName("proposed_date") val proposedDate: String,
    @SerialName("proposed_time_slot") val proposedTimeSlot: String,
    @SerialName("expires_at") val expiresAt: String,
    val note: String = "",
)

@Serializable
data class ServiceQuoteOffer(
    val id: String,
    @SerialName("quote_request_id") val quoteRequestId: String,
    @SerialName("provider_id") val providerId: String,
    @SerialName("actor_user_id") val actorUserId: String,
    @SerialName("price_cents") val priceCents: Int,
    val currency: String,
    @SerialName("proposed_date") val proposedDate: String,
    @SerialName("proposed_time_slot") val proposedTimeSlot: String,
    @SerialName("expires_at") val expiresAt: String,
    val note: String = "",
    val status: String = "active",
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class ProviderInboxItem(
    val id: String,
    @SerialName("item_type") val itemType: String,
    @SerialName("provider_id") val providerId: String,
    @SerialName("provider_name") val providerName: String,
    val status: String,
    val title: String,
    val subtitle: String,
    val priority: String = "normal",
    @SerialName("created_at") val createdAt: String,
    @SerialName("due_at") val dueAt: String? = null,
    @SerialName("quote_request_id") val quoteRequestId: String? = null,
    @SerialName("booking_id") val bookingId: String? = null,
    @SerialName("customer_user_id") val customerUserId: String? = null,
)

@Serializable
data class ProviderInboxResponse(
    @SerialName("actor_user_id") val actorUserId: String,
    val total: Int,
    val items: List<ProviderInboxItem> = emptyList(),
)

@Serializable
data class ServiceRecommendationsResponse(
    val providers: List<ServiceProvider>,
    @SerialName("inferred_suburb") val inferredSuburb: String? = null,
    @SerialName("suburb_source") val suburbSource: String = "none",
)

@Serializable
data class VetCoachSessionRequest(
    @SerialName("actor_user_id") val actorUserId: String,
    @SerialName("duration_minutes") val durationMinutes: Int,
    @SerialName("quality_score") val qualityScore: Double,
    val topic: String = "",
    val note: String = "",
)

@Serializable
data class VetSpotlightActivateRequest(
    @SerialName("actor_user_id") val actorUserId: String,
    val minutes: Int,
)

@Serializable
data class VetCoachProfile(
    @SerialName("user_id") val userId: String,
    @SerialName("spotlight_minutes") val spotlightMinutes: Int,
    @SerialName("coaching_minutes") val coachingMinutes: Int,
    @SerialName("coaching_sessions") val coachingSessions: Int,
    @SerialName("coach_quality_score") val coachQualityScore: Double,
    @SerialName("highlighted_until") val highlightedUntil: String? = null,
    @SerialName("badge_tier") val badgeTier: String = "none",
)

@Serializable
data class VetCoachSessionResult(
    @SerialName("session_id") val sessionId: String,
    @SerialName("minutes_earned") val minutesEarned: Int,
    val profile: VetCoachProfile,
)

@Serializable
data class VetSpotlightActivationResult(
    @SerialName("minutes_spent") val minutesSpent: Int,
    val profile: VetCoachProfile,
)

@Serializable
data class VetGroomerVerificationRequest(
    @SerialName("actor_user_id") val actorUserId: String,
    val decision: String,
    @SerialName("confidence_score") val confidenceScore: Double = 0.8,
    val note: String = "",
)

@Serializable
data class VetGroomerVerification(
    val id: String,
    @SerialName("provider_id") val providerId: String,
    @SerialName("vet_user_id") val vetUserId: String,
    val decision: String,
    @SerialName("confidence_score") val confidenceScore: Double,
    val note: String = "",
    @SerialName("created_at") val createdAt: String,
    @SerialName("valid_until") val validUntil: String? = null,
    @SerialName("spotlight_minutes_earned") val spotlightMinutesEarned: Int = 0,
)

@Serializable
data class VetGroomerVerificationResult(
    val verification: VetGroomerVerification,
    val provider: ServiceProvider,
    @SerialName("vet_profile") val vetProfile: VetCoachProfile,
)

@Serializable
data class BookingRequest(
    @SerialName("user_id") val userId: String,
    @SerialName("provider_id") val providerId: String,
    @SerialName("pet_name") val petName: String,
    val date: String,
    @SerialName("time_slot") val timeSlot: String,
    val note: String,
)

@Serializable
data class BookingResponse(
    val id: String,
    @SerialName("owner_user_id") val ownerUserId: String = "",
    @SerialName("provider_id") val providerId: String,
    @SerialName("provider_owner_user_id") val providerOwnerUserId: String? = null,
    @SerialName("counterparty_user_id") val counterpartyUserId: String? = null,
    @SerialName("thread_id") val threadId: String? = null,
    @SerialName("pet_name") val petName: String,
    val date: String,
    @SerialName("time_slot") val timeSlot: String,
    val note: String = "",
    val status: String,
)

@Serializable
data class BookingHoldRequest(
    @SerialName("user_id") val userId: String,
    @SerialName("provider_id") val providerId: String,
    val date: String,
    @SerialName("time_slot") val timeSlot: String,
)

@Serializable
data class BookingHoldResponse(
    val id: String,
    @SerialName("provider_id") val providerId: String,
    @SerialName("owner_user_id") val ownerUserId: String,
    val date: String,
    @SerialName("time_slot") val timeSlot: String,
    @SerialName("expires_at") val expiresAt: String,
)

@Serializable
data class BookingStatusUpdateRequest(
    @SerialName("actor_user_id") val actorUserId: String,
    val status: String,
    val note: String = "",
    val date: String? = null,
    @SerialName("time_slot") val timeSlot: String? = null,
)

@Serializable
data class BookingStatusHistoryEntry(
    val id: String,
    @SerialName("booking_id") val bookingId: String,
    @SerialName("actor_user_id") val actorUserId: String,
    @SerialName("from_status") val fromStatus: String,
    @SerialName("to_status") val toStatus: String,
    val note: String = "",
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class ProviderBlackoutRequest(
    @SerialName("actor_user_id") val actorUserId: String,
    val date: String,
    @SerialName("time_slot") val timeSlot: String,
    val reason: String = "",
)

@Serializable
data class ProviderBlackout(
    val id: String,
    @SerialName("provider_id") val providerId: String,
    val date: String,
    @SerialName("time_slot") val timeSlot: String,
    val reason: String = "",
)

@Serializable
data class CalendarEvent(
    val id: String,
    val type: String,
    val role: String,
    val title: String,
    val subtitle: String,
    val date: String,
    @SerialName("time_slot") val timeSlot: String,
    val status: String,
    @SerialName("provider_id") val providerId: String? = null,
    @SerialName("booking_id") val bookingId: String? = null,
)

@Serializable
data class ServiceAvailabilitySlot(
    val date: String,
    @SerialName("time_slot") val timeSlot: String,
    val available: Boolean,
    val reason: String? = null,
)

@Serializable
data class ChatRequest(
    @SerialName("user_id") val userId: String,
    val messages: List<ChatMessage>,
)

@Serializable
data class ChatCta(
    val label: String,
    val action: String,
    val payload: JsonObject? = null,
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
data class ChatTurn(
    val role: String,
    val content: String,
    @SerialName("answer_source") val answerSource: String? = null,
    @SerialName("answer_badges") val answerBadges: List<String> = emptyList(),
    val citations: List<ChatCitation> = emptyList(),
)

@Serializable
data class ChatCitation(
    val title: String,
    val source: String,
    val url: String? = null,
    val snippet: String? = null,
)

@Serializable
data class PetProfileSuggestion(
    @SerialName("owner_name") val ownerName: String? = null,
    @SerialName("pet_name") val petName: String? = null,
    @SerialName("pet_type") val petType: String? = null,
    val breed: String? = null,
    @SerialName("age_years") val ageYears: Double? = null,
    @SerialName("weight_kg") val weightKg: Double? = null,
    val suburb: String? = null,
    val concerns: List<String> = emptyList(),
)

@Serializable
data class ChatResponse(
    val answer: String = "",
    val message: ChatMessage? = null,
    @SerialName("suggested_profile") val suggestedProfile: JsonObject? = null,
    @SerialName("cta_chips") val ctaChips: List<ChatCta> = emptyList(),
    val conversation: List<ChatTurn> = emptyList(),
    @SerialName("profile_suggestion") val profileSuggestion: PetProfileSuggestion? = null,
    @SerialName("a2ui_messages") val a2uiMessages: List<JsonObject> = emptyList(),
    @SerialName("answer_source") val answerSource: String = "fallback",
    @SerialName("answer_badges") val answerBadges: List<String> = emptyList(),
    val citations: List<ChatCitation> = emptyList(),
)

@Serializable
data class ProfileActionRequest(
    @SerialName("user_id") val userId: String,
)

@Serializable
data class CommunityPost(
    val id: String,
    val type: String,
    @SerialName("created_by") val createdBy: String? = null,
    val title: String,
    val body: String,
    val suburb: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("alert_type") val alertType: String? = null,
    @SerialName("alert_status") val alertStatus: String? = null,
    @SerialName("pet_name") val petName: String? = null,
    @SerialName("pet_traits") val petTraits: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("last_seen_location") val lastSeenLocation: String? = null,
    @SerialName("contact_pref") val contactPref: String? = null,
    @SerialName("share_scope") val shareScope: String? = null,
    @SerialName("share_precision") val sharePrecision: String? = null,
    @SerialName("photo_urls") val photoUrls: List<String> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("resolved_at") val resolvedAt: String? = null,
    @SerialName("resolved_note") val resolvedNote: String? = null,
    @SerialName("follow_up_due_at") val followUpDueAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
)

@Serializable
data class CommunityPostCreate(
    val type: String,
    @SerialName("user_id") val userId: String? = null,
    val title: String,
    val body: String,
    val suburb: String,
    @SerialName("alert_type") val alertType: String? = null,
    @SerialName("alert_status") val alertStatus: String? = null,
    @SerialName("pet_name") val petName: String? = null,
    @SerialName("pet_traits") val petTraits: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("last_seen_location") val lastSeenLocation: String? = null,
    @SerialName("contact_pref") val contactPref: String? = null,
    @SerialName("share_scope") val shareScope: String? = null,
    @SerialName("share_precision") val sharePrecision: String? = null,
    @SerialName("photo_urls") val photoUrls: List<String> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class CommunityPostResolveRequest(
    @SerialName("requester_user_id") val requesterUserId: String? = null,
    val status: String,
    val note: String = "",
)

@Serializable
data class CommunityPostUpdateRequest(
    @SerialName("requester_user_id") val requesterUserId: String? = null,
    val title: String? = null,
    val body: String? = null,
    @SerialName("pet_name") val petName: String? = null,
    @SerialName("pet_traits") val petTraits: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("last_seen_location") val lastSeenLocation: String? = null,
    @SerialName("contact_pref") val contactPref: String? = null,
    @SerialName("share_scope") val shareScope: String? = null,
    @SerialName("share_precision") val sharePrecision: String? = null,
    @SerialName("photo_urls") val photoUrls: List<String>? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("clear_last_seen_at") val clearLastSeenAt: Boolean = false,
)

@Serializable
data class CommunityPostPhotoUploadResponse(
    val url: String,
    @SerialName("content_type") val contentType: String,
    @SerialName("size_bytes") val sizeBytes: Int,
)

@Serializable
data class CommunityPostPhotoUploadRequest(
    @SerialName("requester_user_id") val requesterUserId: String,
    val filename: String,
    @SerialName("content_type") val contentType: String,
    @SerialName("data_base64") val dataBase64: String,
)

@Serializable
data class CommunityComment(
    val id: String,
    @SerialName("post_id") val postId: String,
    @SerialName("user_id") val userId: String,
    val body: String,
    @SerialName("parent_comment_id") val parentCommentId: String? = null,
    @SerialName("created_at") val createdAt: String,
    val status: String = "active",
    @SerialName("moderated_at") val moderatedAt: String? = null,
    @SerialName("moderated_by") val moderatedBy: String? = null,
    @SerialName("moderation_note") val moderationNote: String? = null,
)

@Serializable
data class CommunityCommentCreateRequest(
    @SerialName("user_id") val userId: String,
    val body: String,
    @SerialName("parent_comment_id") val parentCommentId: String? = null,
)

@Serializable
data class CommunityCommentModerationRequest(
    @SerialName("requester_user_id") val requesterUserId: String,
    val action: String,
    val note: String = "",
)

@Serializable
data class CommunityReportCreateRequest(
    @SerialName("reporter_user_id") val reporterUserId: String,
    @SerialName("target_type") val targetType: String,
    @SerialName("target_id") val targetId: String,
    val reason: String,
    val details: String = "",
)

@Serializable
data class CommunityReport(
    val id: String,
    @SerialName("reporter_user_id") val reporterUserId: String,
    @SerialName("target_type") val targetType: String,
    @SerialName("target_id") val targetId: String,
    val reason: String,
    val details: String = "",
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("resolved_at") val resolvedAt: String? = null,
    @SerialName("resolved_by") val resolvedBy: String? = null,
    @SerialName("resolution_note") val resolutionNote: String? = null,
)

@Serializable
data class CommunityReportResolveRequest(
    @SerialName("requester_user_id") val requesterUserId: String,
    val action: String,
    val note: String = "",
)

@Serializable
data class CommunityBlockUserRequest(
    @SerialName("requester_user_id") val requesterUserId: String,
    @SerialName("target_user_id") val targetUserId: String,
)

@Serializable
data class CommunityBlockUserResponse(
    @SerialName("requester_user_id") val requesterUserId: String,
    @SerialName("blocked_user_ids") val blockedUserIds: List<String> = emptyList(),
)

@Serializable
data class CommunityAnalyticsEventCreateRequest(
    @SerialName("user_id") val userId: String,
    val event: String,
    val category: String = "community",
    val metadata: Map<String, String> = emptyMap(),
    @SerialName("duration_ms") val durationMs: Int? = null,
)

@Serializable
data class CommunityDiagnosticEventCreateRequest(
    @SerialName("user_id") val userId: String,
    val kind: String = "error",
    val message: String,
    val context: Map<String, String> = emptyMap(),
    @SerialName("duration_ms") val durationMs: Int? = null,
)

@Serializable
data class CommunityFunnelMetrics(
    @SerialName("window_hours") val windowHours: Int,
    @SerialName("community_feed_views") val communityFeedViews: Int = 0,
    @SerialName("lost_found_feed_views") val lostFoundFeedViews: Int = 0,
    @SerialName("lost_found_create_attempts") val lostFoundCreateAttempts: Int = 0,
    @SerialName("lost_found_create_successes") val lostFoundCreateSuccesses: Int = 0,
    @SerialName("lost_found_resolution_actions") val lostFoundResolutionActions: Int = 0,
    @SerialName("moderation_reports_submitted") val moderationReportsSubmitted: Int = 0,
    @SerialName("blocks_submitted") val blocksSubmitted: Int = 0,
    @SerialName("lost_found_create_conversion_pct") val lostFoundCreateConversionPct: Double = 0.0,
)

@Serializable
data class CommunityActivationFailure(
    val event: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("user_id") val userId: String = "",
    val error: String = "",
)

@Serializable
data class CommunityActivationFunnel(
    @SerialName("window_hours") val windowHours: Int = 72,
    @SerialName("requester_user_id") val requesterUserId: String? = null,
    @SerialName("activation_event_count") val activationEventCount: Int = 0,
    @SerialName("activation_diagnostic_count") val activationDiagnosticCount: Int = 0,
    @SerialName("unique_users") val uniqueUsers: List<String> = emptyList(),
    @SerialName("unique_user_count") val uniqueUserCount: Int = 0,
    @SerialName("last_event_at") val lastEventAt: String? = null,
    @SerialName("by_event") val byEvent: Map<String, Int> = emptyMap(),
    @SerialName("by_stage") val byStage: Map<String, Int> = emptyMap(),
    @SerialName("by_status") val byStatus: Map<String, Int> = emptyMap(),
    @SerialName("top_failures") val topFailures: List<CommunityActivationFailure> = emptyList(),
)

@Serializable
data class CommunityEvent(
    val id: String,
    val title: String,
    val description: String,
    val suburb: String,
    val date: String,
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("location_name") val locationName: String? = null,
    @SerialName("location_latitude") val locationLatitude: Double? = null,
    @SerialName("location_longitude") val locationLongitude: Double? = null,
    val recurrence: String = "none",
    @SerialName("recurrence_interval") val recurrenceInterval: Int = 1,
    @SerialName("attendee_count") val attendeeCount: Int = 0,
    @SerialName("created_by") val createdBy: String,
    @SerialName("rsvp_status") val rsvpStatus: String = "none",
    val status: String = "approved",
)

@Serializable
data class CommunityEventCreateRequest(
    @SerialName("user_id") val userId: String,
    val title: String,
    val description: String,
    val suburb: String,
    val date: String,
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("location_name") val locationName: String? = null,
    @SerialName("location_latitude") val locationLatitude: Double? = null,
    @SerialName("location_longitude") val locationLongitude: Double? = null,
    val recurrence: String = "none",
    @SerialName("recurrence_interval") val recurrenceInterval: Int = 1,
)

@Serializable
data class CommunityEventUpdateRequest(
    @SerialName("user_id") val userId: String,
    val title: String? = null,
    val description: String? = null,
    val date: String? = null,
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("location_name") val locationName: String? = null,
    @SerialName("location_latitude") val locationLatitude: Double? = null,
    @SerialName("location_longitude") val locationLongitude: Double? = null,
    @SerialName("clear_location") val clearLocation: Boolean = false,
    val recurrence: String? = null,
    @SerialName("recurrence_interval") val recurrenceInterval: Int? = null,
)

@Serializable
data class CommunityEventRsvpRequest(
    @SerialName("user_id") val userId: String,
    val status: String,
)

@Serializable
data class Group(
    val id: String,
    val name: String,
    val suburb: String,
    @SerialName("member_count") val memberCount: Int,
    val official: Boolean = false,
    @SerialName("owner_user_id") val ownerUserId: String? = null,
    @SerialName("membership_status") val membershipStatus: String = "none",
    @SerialName("is_admin") val isAdmin: Boolean = false,
    @SerialName("pending_request_count") val pendingRequestCount: Int = 0,
    @SerialName("group_badges") val groupBadges: List<String> = emptyList(),
    @SerialName("cooperative_score") val cooperativeScore: Int = 0,
    @SerialName("my_pack_builder_points") val myPackBuilderPoints: Int = 0,
    @SerialName("my_clean_park_points") val myCleanParkPoints: Int = 0,
)

@Serializable
data class GroupJoinRequestView(
    @SerialName("group_id") val groupId: String,
    @SerialName("user_id") val userId: String,
    val status: String,
)

@Serializable
data class GroupJoinModerationRequest(
    @SerialName("requester_user_id") val requesterUserId: String,
    @SerialName("member_user_id") val memberUserId: String,
    val action: String,
)

@Serializable
data class GroupCreateRequest(
    @SerialName("user_id") val userId: String,
    val name: String,
    val suburb: String,
)

@Serializable
data class GroupJoinRequest(
    @SerialName("user_id") val userId: String,
)

@Serializable
data class GroupChallenge(
    val id: String,
    @SerialName("group_id") val groupId: String,
    val type: String,
    val title: String,
    val description: String,
    @SerialName("target_count") val targetCount: Int,
    @SerialName("progress_count") val progressCount: Int,
    val status: String,
    @SerialName("reward_label") val rewardLabel: String,
    @SerialName("start_at") val startAt: String,
    @SerialName("end_at") val endAt: String,
)

@Serializable
data class GroupChallengeView(
    val challenge: GroupChallenge,
    @SerialName("my_contribution_count") val myContributionCount: Int = 0,
)

@Serializable
data class GroupChallengeParticipationRequest(
    @SerialName("user_id") val userId: String,
    @SerialName("challenge_type") val challengeType: String,
    @SerialName("contribution_count") val contributionCount: Int = 1,
    val note: String = "",
)

@Serializable
data class GroupChallengeParticipationResult(
    val challenge: GroupChallenge,
    @SerialName("my_contribution_count") val myContributionCount: Int,
    @SerialName("contribution_count") val contributionCount: Int,
    @SerialName("reward_unlocked") val rewardUnlocked: Boolean = false,
    @SerialName("unlocked_badges") val unlockedBadges: List<String> = emptyList(),
)

@Serializable
data class GroupInviteCreateRequest(
    @SerialName("group_id") val groupId: String,
    @SerialName("inviter_user_id") val inviterUserId: String,
)

@Serializable
data class GroupInvite(
    val token: String,
    @SerialName("group_id") val groupId: String,
    @SerialName("group_name") val groupName: String,
    val suburb: String,
    @SerialName("inviter_user_id") val inviterUserId: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("invite_url") val inviteUrl: String,
)

@Serializable
data class GroupOnboardingCompleteRequest(
    @SerialName("invite_token") val inviteToken: String,
    @SerialName("owner_name") val ownerName: String,
    @SerialName("dog_name") val dogName: String,
    val suburb: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("share_photo_to_group") val sharePhotoToGroup: Boolean = true,
    @SerialName("photo_source") val photoSource: String? = null,
)

@Serializable
data class GroupOnboardingCompleteResponse(
    @SerialName("user_id") val userId: String,
    @SerialName("group_id") val groupId: String,
    @SerialName("membership_status") val membershipStatus: String,
    @SerialName("created_post_id") val createdPostId: String? = null,
)

@Serializable
data class NearbyPetBusiness(
    val placeId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val vicinity: String?,
    val primaryType: String?,
    val rating: Double?,
    val userRatingsTotal: Int?,
    val openNow: Boolean?,
)

@Serializable
data class HomeCacheSnapshot(
    val providers: List<ServiceProvider>,
    @SerialName("owner_listing_providers") val ownerListingProviders: List<ServiceProvider> = emptyList(),
    val nearbyPetBusinesses: List<NearbyPetBusiness>,
    val groups: List<Group>,
    val posts: List<CommunityPost>,
    val events: List<CommunityEvent>,
    val ownerBookings: List<BookingResponse>,
    val providerBookings: List<BookingResponse>,
    val calendarEvents: List<CalendarEvent>,
)

@Serializable
data class HomeDiscoveryCacheSlice(
    val providers: List<ServiceProvider>,
    @SerialName("owner_listing_providers") val ownerListingProviders: List<ServiceProvider> = emptyList(),
    val nearbyPetBusinesses: List<NearbyPetBusiness>,
    val groups: List<Group>,
    val posts: List<CommunityPost>,
    val events: List<CommunityEvent>,
)

@Serializable
data class HomeBookingsCacheSlice(
    val ownerBookings: List<BookingResponse>,
    val providerBookings: List<BookingResponse>,
)

@Serializable
data class HomeCalendarCacheSlice(
    val calendarEvents: List<CalendarEvent>,
)

@Serializable
data class AuthLoginRequest(
    @SerialName("user_id") val userId: String,
    val password: String = "petsocial-demo",
)

@Serializable
data class AuthLoginResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("user_id") val userId: String,
    @SerialName("expires_at") val expiresAt: String,
)

@Serializable
data class AuthInviteCreateRequest(
    @SerialName("requester_user_id") val requesterUserId: String,
    val email: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("ttl_minutes") val ttlMinutes: Int = 60,
)

@Serializable
data class AuthInviteResponse(
    @SerialName("invite_id") val inviteId: String,
    @SerialName("user_id") val userId: String,
    val email: String,
    @SerialName("expires_at") val expiresAt: String,
)

@Serializable
data class AuthOtpRequest(
    @SerialName("invite_id") val inviteId: String,
    val email: String,
)

@Serializable
data class AuthOtpRequestResponse(
    val status: String = "otp_sent",
    @SerialName("expires_at") val expiresAt: String,
)

@Serializable
data class AuthOtpVerifyRequest(
    @SerialName("invite_id") val inviteId: String,
    val email: String,
    @SerialName("otp_code") val otpCode: String,
    @SerialName("device_id") val deviceId: String? = null,
)

@Serializable
data class AuthOtpVerifyResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("user_id") val userId: String,
    @SerialName("expires_at") val expiresAt: String,
)

@Serializable
data class AuthTrustedDeviceLoginRequest(
    @SerialName("device_id") val deviceId: String,
)

@Serializable
data class AuthTrustedDeviceResetRequest(
    @SerialName("device_id") val deviceId: String,
)

@Serializable
data class AuthFriendQrIssueResponse(
    @SerialName("friend_token") val friendToken: String,
    @SerialName("friend_url") val friendUrl: String,
    @SerialName("expires_at") val expiresAt: String,
)

@Serializable
data class AuthFriendQrVerifyRequest(
    @SerialName("friend_token") val friendToken: String,
)

@Serializable
data class AuthFriendQrVerifyResponse(
    @SerialName("user_id") val userId: String,
    @SerialName("human_name") val humanName: String,
    @SerialName("dog_name") val dogName: String,
    @SerialName("expires_at") val expiresAt: String,
)

@Serializable
data class AuthLogoutResponse(
    val status: String = "ok",
)

@Serializable
data class AuthDeleteResponse(
    val status: String = "deleted",
    @SerialName("user_id") val userId: String,
)

@Serializable
data class UserProfileResponse(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String = "",
    val email: String = "",
    val phone: String = "",
    @SerialName("human_pronouns") val humanPronouns: String = "",
    @SerialName("human_role_label") val humanRoleLabel: String = "",
    @SerialName("service_provider_mode") val serviceProviderMode: Boolean = false,
    @SerialName("dog_name") val dogName: String = "",
    @SerialName("dog_age_months") val dogAgeMonths: Int = 0,
    @SerialName("dog_breed_mix") val dogBreedMix: String = "",
    @SerialName("dog_sex_neuter") val dogSexNeuter: String = "",
    @SerialName("dog_weight_class") val dogWeightClass: String = "",
    @SerialName("dog_photo_urls") val dogPhotoUrls: List<String> = emptyList(),
    @SerialName("secondary_dog_name") val secondaryDogName: String = "",
    @SerialName("secondary_dog_age_months") val secondaryDogAgeMonths: Int = 0,
    @SerialName("secondary_dog_photo_url") val secondaryDogPhotoUrl: String = "",
    @SerialName("secondary_dog_gender") val secondaryDogGender: String = "",
    @SerialName("secondary_dog_weight_kg") val secondaryDogWeightKg: String = "",
    val bio: String = "",
    val suburb: String = "",
    @SerialName("favorite_suburbs") val favoriteSuburbs: List<String> = emptyList(),
    @SerialName("play_energy_level") val playEnergyLevel: String = "",
    @SerialName("play_style") val playStyle: String = "",
    @SerialName("social_confidence") val socialConfidence: String = "",
    @SerialName("trigger_notes") val triggerNotes: String = "",
    @SerialName("ideal_match") val idealMatch: String = "",
    @SerialName("walk_preferences") val walkPreferences: String = "",
    @SerialName("training_style") val trainingStyle: String = "",
    @SerialName("feeding_rules") val feedingRules: String = "",
    @SerialName("consent_boundaries") val consentBoundaries: String = "",
    @SerialName("vaccination_status") val vaccinationStatus: String = "",
    @SerialName("microchipped") val microchipped: Boolean = false,
    @SerialName("recall_trained") val recallTrained: Boolean = false,
    @SerialName("leash_reliability") val leashReliability: String = "",
    @SerialName("emergency_contact_name") val emergencyContactName: String = "",
    @SerialName("emergency_contact_phone") val emergencyContactPhone: String = "",
    @SerialName("field_visibility") val fieldVisibility: Map<String, String> = emptyMap(),
    @SerialName("updated_at") val updatedAt: String = "",
)

@Serializable
data class UserProfileUpsertRequest(
    @SerialName("requester_user_id") val requesterUserId: String,
    @SerialName("display_name") val displayName: String = "",
    val email: String = "",
    val phone: String = "",
    @SerialName("human_pronouns") val humanPronouns: String = "",
    @SerialName("human_role_label") val humanRoleLabel: String = "",
    @SerialName("service_provider_mode") val serviceProviderMode: Boolean = false,
    @SerialName("dog_name") val dogName: String = "",
    @SerialName("dog_age_months") val dogAgeMonths: Int = 0,
    @SerialName("dog_breed_mix") val dogBreedMix: String = "",
    @SerialName("dog_sex_neuter") val dogSexNeuter: String = "",
    @SerialName("dog_weight_class") val dogWeightClass: String = "",
    @SerialName("dog_photo_urls") val dogPhotoUrls: List<String> = emptyList(),
    @SerialName("secondary_dog_name") val secondaryDogName: String = "",
    @SerialName("secondary_dog_age_months") val secondaryDogAgeMonths: Int = 0,
    @SerialName("secondary_dog_photo_url") val secondaryDogPhotoUrl: String = "",
    @SerialName("secondary_dog_gender") val secondaryDogGender: String = "",
    @SerialName("secondary_dog_weight_kg") val secondaryDogWeightKg: String = "",
    val bio: String = "",
    val suburb: String = "",
    @SerialName("favorite_suburbs") val favoriteSuburbs: List<String> = emptyList(),
    @SerialName("play_energy_level") val playEnergyLevel: String = "",
    @SerialName("play_style") val playStyle: String = "",
    @SerialName("social_confidence") val socialConfidence: String = "",
    @SerialName("trigger_notes") val triggerNotes: String = "",
    @SerialName("ideal_match") val idealMatch: String = "",
    @SerialName("walk_preferences") val walkPreferences: String = "",
    @SerialName("training_style") val trainingStyle: String = "",
    @SerialName("feeding_rules") val feedingRules: String = "",
    @SerialName("consent_boundaries") val consentBoundaries: String = "",
    @SerialName("vaccination_status") val vaccinationStatus: String = "",
    @SerialName("microchipped") val microchipped: Boolean = false,
    @SerialName("recall_trained") val recallTrained: Boolean = false,
    @SerialName("leash_reliability") val leashReliability: String = "",
    @SerialName("emergency_contact_name") val emergencyContactName: String = "",
    @SerialName("emergency_contact_phone") val emergencyContactPhone: String = "",
    @SerialName("field_visibility") val fieldVisibility: Map<String, String> = emptyMap(),
)

@Serializable
data class ApiMessageThread(
    val id: String,
    @SerialName("participant_user_id") val participantUserId: String,
    @SerialName("last_message") val lastMessage: String = "",
    @SerialName("last_message_at") val lastMessageAt: String,
    @SerialName("unread_count") val unreadCount: Int = 0,
)

@Serializable
data class ApiDirectMessage(
    val id: String,
    @SerialName("thread_id") val threadId: String,
    @SerialName("sender_user_id") val senderUserId: String,
    @SerialName("recipient_user_id") val recipientUserId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class MessageSendRequest(
    @SerialName("user_id") val userId: String,
    @SerialName("recipient_user_id") val recipientUserId: String,
    val body: String,
)

@Serializable
data class MessageMarkReadRequest(
    @SerialName("user_id") val userId: String,
)

@Serializable
data class DeviceTokenRegisterRequest(
    @SerialName("user_id") val userId: String,
    @SerialName("device_token") val deviceToken: String,
    val platform: String = "android",
)

@Serializable
data class AppNotification(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val body: String,
    val category: String,
    val read: Boolean,
    @SerialName("created_at") val createdAt: String,
    @SerialName("deep_link") val deepLink: String? = null,
)
