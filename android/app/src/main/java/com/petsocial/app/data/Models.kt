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
    val suburb: String,
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
    val message: String,
    val suburb: String? = null,
)

@Serializable
data class ChatCta(
    val label: String,
    val action: String,
    val payload: JsonObject? = null,
)

@Serializable
data class ChatTurn(
    val role: String,
    val content: String,
)

@Serializable
data class PetProfileSuggestion(
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
    val answer: String,
    @SerialName("suggested_profile") val suggestedProfile: JsonObject? = null,
    @SerialName("cta_chips") val ctaChips: List<ChatCta> = emptyList(),
    val conversation: List<ChatTurn> = emptyList(),
    @SerialName("profile_suggestion") val profileSuggestion: PetProfileSuggestion? = null,
    @SerialName("a2ui_messages") val a2uiMessages: List<JsonObject> = emptyList(),
)

@Serializable
data class ProfileActionRequest(
    @SerialName("user_id") val userId: String,
)

@Serializable
data class CommunityPost(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val suburb: String,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CommunityPostCreate(
    val type: String,
    val title: String,
    val body: String,
    val suburb: String,
)

@Serializable
data class CommunityEvent(
    val id: String,
    val title: String,
    val description: String,
    val suburb: String,
    val date: String,
    @SerialName("group_id") val groupId: String? = null,
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
