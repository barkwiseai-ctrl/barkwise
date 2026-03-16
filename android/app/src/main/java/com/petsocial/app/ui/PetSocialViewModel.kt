package com.petsocial.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.petsocial.app.BuildConfig
import com.petsocial.app.data.AppNotification
import com.petsocial.app.data.ApiDirectMessage
import com.petsocial.app.data.ApiMessageThread
import com.petsocial.app.data.ChatCta
import com.petsocial.app.data.ChatResponse
import com.petsocial.app.data.ChatTurn
import com.petsocial.app.data.CalendarEvent
import com.petsocial.app.data.BookingStatusHistoryEntry
import com.petsocial.app.data.BookingResponse
import com.petsocial.app.data.CommunityEvent
import com.petsocial.app.data.CommunityFunnelMetrics
import com.petsocial.app.data.CommunityActivationFunnel
import com.petsocial.app.data.CommunityComment
import com.petsocial.app.data.CommunityPost
import com.petsocial.app.data.CommunityPostCreate
import com.petsocial.app.data.CommunityReport
import com.petsocial.app.data.Group
import com.petsocial.app.data.GroupInvite
import com.petsocial.app.data.HomeCacheSnapshot
import com.petsocial.app.data.NearbyPetBusiness
import com.petsocial.app.data.PetProfileSuggestion
import com.petsocial.app.data.PetSocialRepository
import com.petsocial.app.data.ProviderInboxItem
import com.petsocial.app.data.ServiceProvider
import com.petsocial.app.data.ServiceProviderDetailsResponse
import com.petsocial.app.data.ServiceAvailabilitySlot
import com.petsocial.app.data.ProviderBlackout
import com.petsocial.app.data.UserProfileResponse
import com.petsocial.app.location.LocationSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

enum class AppTab {
    Services,
    Community,
    BarkAI,
    Messages,
    Profile,
}

data class MessageThread(
    val id: String,
    val title: String,
    val participantUserId: String,
    val participantAccountLabel: String,
    val participantPetNames: List<String> = emptyList(),
    val lastMessage: String,
    val unreadCount: Int = 0,
    val isMuted: Boolean = false,
    val isPinned: Boolean = false,
)

data class DirectMessage(
    val id: String,
    val threadId: String,
    val senderUserId: String,
    val recipientUserId: String,
    val body: String,
)

data class ProfileInfo(
    val displayName: String = "Alex Wong",
    val email: String = "alex@example.com",
    val phone: String = "+61 412 345 678",
    val humanPronouns: String = "",
    val humanRoleLabel: String = "Member",
    val dogName: String = "Milo",
    val dogAgeMonths: Int = 24,
    val dogBreedMix: String = "",
    val dogGender: String = "",
    val dogWeightKg: String = "",
    val dogPhotoUrls: List<String> = listOf("https://images.unsplash.com/photo-1580467277788-c6e040296602?auto=format&fit=crop&w=1200&q=80"),
    val secondaryDogName: String = "",
    val secondaryDogAgeMonths: Int = 0,
    val secondaryDogGender: String = "",
    val secondaryDogWeightKg: String = "",
    val bio: String = "Pet parent of Milo. Loves social dog walks and local events.",
    val suburb: String = "Surry Hills",
    val favoriteSuburbs: List<String> = listOf("Newtown", "Redfern"),
    val playEnergyLevel: String = "",
    val playStyle: String = "",
    val socialConfidence: String = "",
    val triggerNotes: String = "",
    val idealMatch: String = "",
    val walkPreferences: String = "",
    val trainingStyle: String = "",
    val feedingRules: String = "",
    val consentBoundaries: String = "",
    val vaccinationStatus: String = "",
    val microchipped: Boolean = false,
    val recallTrained: Boolean = false,
    val leashReliability: String = "",
    val emergencyContactName: String = "",
    val emergencyContactPhone: String = "",
    val fieldVisibility: Map<String, String> = emptyMap(),
)

data class FriendProfile(
    val userId: String,
    val humanName: String,
    val dogName: String,
    val dogPhotoUrl: String,
    val isFriend: Boolean = false,
)

data class OwnerBooking(
    val id: String,
    val serviceName: String,
    val providerId: String = "",
    val providerUserId: String = "",
    val providerAccountLabel: String = "",
    val date: String,
    val timeSlot: String,
    val status: String,
    val note: String = "",
)

data class JoinedEvent(
    val id: String,
    val title: String,
    val date: String,
    val suburb: String,
)

data class ProviderListing(
    val id: String,
    val title: String,
    val category: String,
    val status: String,
    val priceFrom: Int,
    val description: String = "",
    val suburb: String = "",
    val imageUrls: List<String> = emptyList(),
)

data class ProviderBooking(
    val id: String,
    val petName: String,
    val ownerUserId: String = "",
    val providerId: String = "",
    val serviceName: String,
    val date: String,
    val timeSlot: String,
    val status: String,
)

data class ProviderConfig(
    val availableTimeSlots: String = "Weekdays 9:00-17:00, Sat 9:00-12:00",
    val preferredSuburbs: String = "Surry Hills, Redfern, Newtown",
)

data class PetRosterItem(
    val id: String,
    val petName: String,
    val photoUrl: String,
    val addedDate: LocalDate,
    val suburb: String,
)

data class A2uiCardState(
    val title: String,
    val fields: Map<String, String> = emptyMap(),
    val submitAction: String? = null,
)

data class BarkThread(
    val id: String,
    val title: String,
    val conversation: List<ChatTurn> = emptyList(),
    val chat: ChatResponse? = null,
    val profileSuggestion: PetProfileSuggestion? = null,
    val a2uiProfileCard: A2uiCardState? = null,
    val a2uiProviderCard: A2uiCardState? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

data class HomeLoadMetrics(
    val source: String,
    val fetchMs: Long,
    val applyMs: Long,
    val totalMs: Long,
    val recordedAt: Long = System.currentTimeMillis(),
)

data class CommunityWeatherSnapshot(
    val suburb: String,
    val temperatureC: Int,
    val condition: String,
    val rainChancePercent: Int,
    val windKph: Int,
    val updatedAtLabel: String,
)

data class UiState(
    val providers: List<ServiceProvider> = emptyList(),
    val nearbyPetBusinesses: List<NearbyPetBusiness> = emptyList(),
    val groups: List<Group> = emptyList(),
    val posts: List<CommunityPost> = emptyList(),
    val communityCommentsByPostId: Map<String, List<CommunityComment>> = emptyMap(),
    val loadingCommentPostIds: Set<String> = emptySet(),
    val communityEvents: List<CommunityEvent> = emptyList(),
    val selectedProviderDetails: ServiceProviderDetailsResponse? = null,
    val availableSlots: List<ServiceAvailabilitySlot> = emptyList(),
    val availabilityDate: String? = null,
    val serviceMinRating: Float? = null,
    val serviceMaxDistanceKm: Int? = null,
    val servicesRecommendationSuburb: String? = null,
    val servicesRecommendationSource: String = "none",
    val chat: ChatResponse? = null,
    val conversation: List<ChatTurn> = emptyList(),
    val profileSuggestion: PetProfileSuggestion? = null,
    val a2uiProfileCard: A2uiCardState? = null,
    val a2uiProviderCard: A2uiCardState? = null,
    val barkThreads: List<BarkThread> = listOf(
        BarkThread(
            id = "bark_thread_1",
            title = "Thread 1",
        ),
    ),
    val selectedBarkThreadId: String = "bark_thread_1",
    val onboardingActive: Boolean = false,
    val onboardingStep: Int = 0,
    val onboardingOwnerName: String = "",
    val onboardingDogName: String = "",
    val onboardingPhotoCaptured: Boolean = false,
    val testProfileMode: String = TEST_PROFILE_MODE_READY,
    val profileIdentityHeaderVisible: Boolean = false,
    val friendQrPayload: String = "",
    val friendQrExpiresAt: String? = null,
    val friendQrLoading: Boolean = false,
    val friendProfiles: List<FriendProfile> = emptyList(),
    val messageThreads: List<MessageThread> = emptyList(),
    val selectedMessageThreadId: String? = null,
    val directMessages: List<DirectMessage> = emptyList(),
    val readDirectMessageIds: Set<String> = emptySet(),
    val mutedMessageThreadIds: Set<String> = emptySet(),
    val pinnedMessageThreadIds: Set<String> = emptySet(),
    val streamingAssistantText: String = "",
    val selectedTab: AppTab = AppTab.Services,
    val profileNotificationFilter: String = "all",
    val selectedCategory: String? = null,
    val servicesViewMode: String = "list",
    val servicesSearchQuery: String = "",
    val servicesSortBy: String = "relevance",
    val postsSortBy: String = "relevance",
    val communityOpenOnly: Boolean = false,
    val communityRecentHours: Int? = null,
    val savedCommunityPostIds: Set<String> = emptySet(),
    val savedCommunityEventIds: Set<String> = emptySet(),
    val mutedCommunityKeywords: Set<String> = emptySet(),
    val followedGroupIds: Set<String> = emptySet(),
    val selectedCommunityGroupId: String? = null,
    val selectedSuburb: String = "Surry Hills",
    val selectedRangeCenter: String = "manual",
    val currentLocationSuburb: String? = null,
    val currentLatitude: Double? = null,
    val currentLongitude: Double? = null,
    val activeUserId: String = "user_2",
    val authRequired: Boolean = false,
    val authOtpRequested: Boolean = false,
    val authInviteId: String = "",
    val authEmail: String = "",
    val authOtpExpiresAt: String? = null,
    val authInFlight: Boolean = false,
    val profileInfo: ProfileInfo = ProfileInfo(),
    val isServiceProvider: Boolean = false,
    val ownerBookings: List<OwnerBooking> = emptyList(),
    val joinedEvents: List<JoinedEvent> = emptyList(),
    val favoriteProviderIds: List<String> = emptyList(),
    val providerListings: List<ProviderListing> = emptyList(),
    val providerBlackoutsByProvider: Map<String, List<ProviderBlackout>> = emptyMap(),
    val bookingHistoryByBookingId: Map<String, List<BookingStatusHistoryEntry>> = emptyMap(),
    val loadingBookingHistoryIds: Set<String> = emptySet(),
    val providerRescheduleSlotsByKey: Map<String, List<ServiceAvailabilitySlot>> = emptyMap(),
    val loadingProviderRescheduleKeys: Set<String> = emptySet(),
    val providerConfig: ProviderConfig = ProviderConfig(),
    val providerBookings: List<ProviderBooking> = emptyList(),
    val providerInboxItems: List<ProviderInboxItem> = emptyList(),
    val loadingProviderInbox: Boolean = false,
    val sendingQuoteOfferItemIds: Set<String> = emptySet(),
    val calendarEvents: List<CalendarEvent> = emptyList(),
    val headerRosterPet: PetRosterItem? = null,
    val groupPetRosters: Map<String, List<PetRosterItem>> = emptyMap(),
    val groomerPetRosters: Map<String, List<PetRosterItem>> = emptyMap(),
    val latestGroupInvites: Map<String, GroupInvite> = emptyMap(),
    val pendingInvite: GroupInvite? = null,
    val hasPendingSync: Boolean = false,
    val isOfflineMode: Boolean = false,
    val selectedCalendarRole: String = "all",
    val locationAutoDetected: Boolean = false,
    val toastMessage: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val latestHomeLoadMetrics: HomeLoadMetrics? = null,
    val homeLoadHistory: List<HomeLoadMetrics> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
    val readLocalNotificationIds: Set<String> = emptySet(),
    val acknowledgedCommunityNotificationIds: Set<String> = emptySet(),
    val acknowledgedMessageNotificationIds: Set<String> = emptySet(),
    val notifyFollowedGroupAlerts: Boolean = true,
    val notifySavedPostUpdates: Boolean = true,
    val notifySafetyAlerts: Boolean = true,
    val blockedUserIds: List<String> = emptyList(),
    val moderationReports: List<CommunityReport> = emptyList(),
    val communityFunnelMetrics: CommunityFunnelMetrics? = null,
    val activationFunnelMetrics: CommunityActivationFunnel? = null,
    val isCommunityModerator: Boolean = false,
    val communityWeather: CommunityWeatherSnapshot = CommunityWeatherSnapshot(
        suburb = "Surry Hills",
        temperatureC = 22,
        condition = "Partly cloudy",
        rainChancePercent = 20,
        windKph = 12,
        updatedAtLabel = "Just now",
    ),
    val autoParkCheckInEnabled: Boolean = false,
    val autoParkCheckInRequireCrowd: Boolean = true,
    val autoParkCheckInQuorumThreshold: Int = 3,
    val autoParkCheckInQuorumWindowMinutes: Int = 20,
    val autoParkCheckInQuorumCount: Int = 0,
    val lastAutoParkCheckInGroupId: String? = null,
    val lastAutoParkCheckInAt: String? = null,
)

private fun accountLabel(userId: String): String = when (userId) {
    "user_1" -> "Sesame"
    "user_2" -> "Snowy"
    "user_3" -> "Anika"
    "user_4" -> "Tommy"
    else -> userId
}

private fun normalizeCommunitySort(sortBy: String): String = when (sortBy.trim().lowercase()) {
    "newest", "latest" -> "latest"
    "trending" -> "trending"
    else -> "relevance"
}

private const val TEST_DOG_PARK_GROUP_ID = "g_user_dogpark_surry"
private val ENABLE_TEST_SEED_DATA = BuildConfig.USE_MOCK_DATA
private val IS_PROVIDER_OS_SURFACE = BuildConfig.APP_SURFACE.equals("provider", ignoreCase = true)
private const val STAGING_TEST_SUBURB = "Sunshine West"
private const val TEST_PROFILE_MODE_READY = "ready"
private const val TEST_PROFILE_MODE_ONBOARDING = "onboarding"
private val COMMUNITY_MODERATOR_IDS = setOf("admin", "user_1", "user_3")
private val ONBOARD_GROUP_TITLE = BuildConfig.ONBOARD_GROUP_TITLE.trim().ifBlank { "Surry Hills Dog Park Crew" }
private val ONBOARD_EVENT_TITLE = BuildConfig.ONBOARD_EVENT_TITLE.trim().ifBlank { "Live now: Surry Hills Splash Social" }
private const val ONBOARD_THREAD_ID = "bark_thread_onboarding"
private const val ACTIVATION_EVENT_PREFIX = "activation"
private const val ACTIVATION_CATEGORY = "community"
private val ONBOARD_WELCOME_QUESTIONS = listOf(
    "Hey, welcome! What's your name?",
    "Hey there, welcome to BarkWise. What's your name?",
    "Welcome in! What should I call you?",
    "Hi and welcome. What's your name?",
    "Great to meet you. What's your name?",
    "Hello! Let's get started, what's your name?",
    "Welcome aboard. What's your name?",
    "Hey! Before we begin, what's your name?",
    "Hi there, what's your name so I can personalize this?",
    "Welcome to BarkWise. Tell me your name.",
)
private val ONBOARD_DOG_NAME_QUESTIONS = listOf(
    "What's your dogs' name?",
    "Awesome, and what's your dog's name?",
    "Love it. What's your pup's name?",
    "Nice to meet you. What's your dog's name?",
    "Great, now tell me your dog's name.",
    "Perfect. What should I call your dog?",
    "Thanks. What's your furry mate's name?",
    "Sweet. What's your dog's name so I can remember it?",
    "Cool, and your dog's name is?",
    "Brilliant. Who's your dog?",
)
private val ONBOARD_DOG_PHOTO_QUESTIONS = listOf(
    "Can I see your dog?",
    "Can you share a quick photo of your dog?",
    "Mind showing me your pup on camera?",
    "Let's add a dog photo. Can I see your dog?",
    "Want to snap a photo of your dog now?",
    "Could you show me your dog with a quick pic?",
    "Tap camera and show me your dog?",
    "Can we grab a photo of your dog?",
    "Ready for a dog pic? I'd love to see them.",
    "Last step, can I see your dog?",
)
private val ONBOARD_THANKS_VARIATIONS = listOf(
    "Thanks so much for sharing that.",
    "Thanks a lot, I really appreciate it.",
    "Thank you, that helps a ton.",
    "Amazing, thanks so much.",
    "Perfect, thank you for sending it.",
    "Legend, thanks for that.",
    "You're the best, thanks so much.",
    "Brilliant, thank you.",
    "Nice one, thanks for sharing.",
    "Great stuff, thanks a bunch.",
)
private val ONBOARD_CUTE_VARIATIONS = listOf(
    "Oh he's cute.",
    "Oh wow, your dog is adorable.",
    "That pup is seriously cute.",
    "What a cutie.",
    "Absolutely adorable dog.",
    "Your dog is so photogenic.",
    "Okay, that is very cute.",
    "Love that face, super cute.",
    "Your pup is ridiculously cute.",
    "That is one adorable dog.",
)
private val KNOWN_FRIEND_PROFILES = mapOf(
    "user_1" to ("Sesame" to ("Luna" to "https://images.unsplash.com/photo-1580467277788-c6e040296602?auto=format&fit=crop&w=1200&q=80")),
    "user_2" to ("Snowy" to ("Milo" to "https://images.unsplash.com/photo-1585943870180-be99fca07f23?auto=format&fit=crop&w=1200&q=80")),
    "user_3" to ("Anika" to ("Maple" to "https://images.unsplash.com/photo-1580467277788-c6e040296602?auto=format&fit=crop&w=1200&q=80")),
    "user_4" to ("Tommy" to ("Biscuit" to "https://images.unsplash.com/photo-1585943870180-be99fca07f23?auto=format&fit=crop&w=1200&q=80")),
)

private fun nextActionSwitchHint(
    targetUserId: String?,
    activeUserId: String,
    actionText: String,
): String {
    if (targetUserId.isNullOrBlank() || targetUserId == activeUserId) return ""
    return " Switch to ${accountLabel(targetUserId)} to $actionText."
}

private fun ensureSeedDogParkGroup(
    groups: List<Group>,
    activeUserId: String,
): List<Group> {
    if (!ENABLE_TEST_SEED_DATA) return groups
    val existing = groups.firstOrNull { it.id == TEST_DOG_PARK_GROUP_ID }
    val seeded = Group(
        id = TEST_DOG_PARK_GROUP_ID,
        name = ONBOARD_GROUP_TITLE,
        suburb = "Surry Hills",
        memberCount = 34,
        official = false,
        ownerUserId = "user_3",
        membershipStatus = when (activeUserId) {
            "user_1", "user_2", "user_3" -> "member"
            else -> existing?.membershipStatus ?: "none"
        },
        isAdmin = activeUserId == "user_3",
        pendingRequestCount = if (activeUserId == "user_3") 1 else 0,
        groupBadges = listOf("Pack Builder Lv2", "Clean Crew"),
        cooperativeScore = 87,
        myPackBuilderPoints = when (activeUserId) {
            "user_1" -> 145
            "user_2" -> 122
            "user_3" -> 168
            else -> 64
        },
        myCleanParkPoints = when (activeUserId) {
            "user_1" -> 54
            "user_2" -> 48
            "user_3" -> 73
            else -> 21
        },
    )
    return if (existing == null) {
        listOf(seeded) + groups
    } else {
        groups.map { group -> if (group.id == TEST_DOG_PARK_GROUP_ID) seeded else group }
    }
}

private fun ensureSeedProviders(
    providers: List<ServiceProvider>,
    suburb: String,
    category: String?,
): List<ServiceProvider> {
    if (!ENABLE_TEST_SEED_DATA) return providers
    if (providers.isNotEmpty()) return providers

    val base = listOf(
        ServiceProvider(
            id = "seed_svc_1",
            name = "Neighborhood Paws Walkers",
            category = "dog_walking",
            suburb = suburb,
            rating = 4.8,
            reviewCount = 86,
            priceFrom = 24,
            description = "Friendly daily dog walks with photo updates.",
            fullDescription = "Reliable local walkers for weekday and weekend sessions.",
            imageUrls = fallbackPetPhotos.take(3),
            latitude = -33.8889,
            longitude = 151.2111,
            ownerUserId = "user_1",
            ownerLabel = "Sesame",
            responseTimeMinutes = 16,
            localBookersThisMonth = 12,
            sharedGroupBookers = 4,
            socialProof = listOf(
                "Used by 12 pet owners in $suburb this month",
                "4 members from your groups booked this provider",
                "Typically responds in about 16 min",
            ),
        ),
        ServiceProvider(
            id = "seed_svc_2",
            name = "Coat Care Groom Studio",
            category = "grooming",
            suburb = suburb,
            rating = 4.9,
            reviewCount = 63,
            priceFrom = 48,
            description = "Gentle grooming for sensitive and anxious pets.",
            fullDescription = "Bath, nail trim, and breed-aware styling sessions.",
            imageUrls = fallbackPetPhotos.drop(2).take(3),
            latitude = -33.8928,
            longitude = 151.2040,
            ownerUserId = "user_3",
            ownerLabel = "Anika",
            responseTimeMinutes = 24,
            localBookersThisMonth = 9,
            sharedGroupBookers = 3,
            socialProof = listOf(
                "Used by 9 pet owners in $suburb this month",
                "3 members from your groups booked this provider",
                "Typically responds in about 24 min",
            ),
        ),
        ServiceProvider(
            id = "seed_svc_3",
            name = "Parkside Groom & Go",
            category = "grooming",
            suburb = suburb,
            rating = 4.6,
            reviewCount = 41,
            priceFrom = 44,
            description = "Quick grooming sessions and coat tidy plans.",
            fullDescription = "Practical recurring grooming packages for active dogs.",
            imageUrls = fallbackPetPhotos.drop(4).take(3),
            latitude = -33.8981,
            longitude = 151.1742,
            ownerUserId = "user_4",
            ownerLabel = "Tommy",
            responseTimeMinutes = 33,
            localBookersThisMonth = 7,
            sharedGroupBookers = 2,
            socialProof = listOf(
                "Used by 7 pet owners in $suburb this month",
                "2 members from your groups booked this provider",
                "Typically responds in about 33 min",
            ),
        ),
    )
    return if (category.isNullOrBlank()) {
        base
    } else {
        base.filter { provider -> provider.category == category }
    }
}

private fun ensureSeedDogParkPosts(posts: List<CommunityPost>): List<CommunityPost> {
    if (!ENABLE_TEST_SEED_DATA) return posts
    val seedIds = setOf(
        "p_dogpark_seed_1",
        "p_dogpark_seed_2",
        "p_dogpark_seed_3",
        "p_dogpark_seed_4",
        "p_dogpark_seed_5",
        "p_dogpark_seed_6",
        "p_dogpark_seed_7",
    )
    if (seedIds.all { id -> posts.any { post -> post.id == id } }) return posts
    val now = Instant.now()
    val photoPool = fallbackPetPhotos.distinct()
    val seeded = listOf(
        CommunityPost(
            id = "p_dogpark_seed_1",
            type = "group_post",
            title = "Photo drop: Sunrise zoomies at Surry Hills",
            body = "Luna, Milo, and Maple just wrapped six rounds of fetch. Added a few happy faces from this morning run.",
            suburb = "Surry Hills",
            createdAt = now.minusSeconds(2_700).toString(),
            photoUrls = listOf(
                photoPool[0 % photoPool.size],
                photoPool[1 % photoPool.size],
                photoPool[2 % photoPool.size],
            ),
        ),
        CommunityPost(
            id = "p_dogpark_seed_2",
            type = "group_post",
            title = "Live thread: Water-bowl relay happening now",
            body = "We are on the north lawn setting up shaded water bowls. Join in if you are nearby; extra bowls and poop bags ready.",
            suburb = "Surry Hills",
            createdAt = now.minusSeconds(5_400).toString(),
            photoUrls = listOf(
                photoPool[3 % photoPool.size],
                photoPool[4 % photoPool.size],
            ),
        ),
        CommunityPost(
            id = "p_dogpark_seed_3",
            type = "group_post",
            title = "Then vs Now: Teddy's leash confidence",
            body = "Six weeks ago Teddy froze near bikes. Tonight he stayed calm through the whole park loop with zero pulling.",
            suburb = "Surry Hills",
            createdAt = now.minusSeconds(86_400).toString(),
            photoUrls = listOf(
                photoPool[5 % photoPool.size],
                photoPool[6 % photoPool.size],
            ),
        ),
        CommunityPost(
            id = "p_dogpark_seed_4",
            type = "group_post",
            title = "Pupcake birthday recap for Nala",
            body = "Small-dog circle brought pupcakes, bubble machine, and a calm sniff walk. Thanks everyone for keeping it gentle.",
            suburb = "Surry Hills",
            createdAt = now.minusSeconds(129_600).toString(),
            photoUrls = listOf(
                photoPool[7 % photoPool.size],
                photoPool[8 % photoPool.size],
                photoPool[9 % photoPool.size],
            ),
        ),
        CommunityPost(
            id = "p_dogpark_seed_5",
            type = "group_post",
            title = "Cleanup crew check-in: 11 bags collected",
            body = "Quick thank-you to everyone who did the post-play cleanup loop around the south fence and cafe entrance.",
            suburb = "Surry Hills",
            createdAt = now.minusSeconds(176_400).toString(),
            photoUrls = listOf(photoPool[10 % photoPool.size]),
        ),
        CommunityPost(
            id = "p_dogpark_seed_6",
            type = "group_post",
            title = "Agility mini-course highlights",
            body = "Archie and Poppy flew through the tunnel and pause platform. Sharing a few shots for anyone who missed Sunday drills.",
            suburb = "Surry Hills",
            createdAt = now.minusSeconds(223_200).toString(),
            photoUrls = listOf(
                photoPool[11 % photoPool.size],
                photoPool[12 % photoPool.size],
            ),
        ),
        CommunityPost(
            id = "p_dogpark_seed_7",
            type = "group_post",
            title = "Looking for tomorrow's dawn walk crew",
            body = "Meeting 6:45am at the fountain entrance for a calm 30-minute loop before work. Reply if your pup is joining.",
            suburb = "Surry Hills",
            createdAt = now.minusSeconds(309_600).toString(),
            photoUrls = listOf(
                photoPool[2 % photoPool.size],
                photoPool[5 % photoPool.size],
            ),
        ),
    )
    val existingById = posts.associateBy { it.id }
    val mergedSeeds = seeded.map { seed -> existingById[seed.id] ?: seed }
    val remainingPosts = posts.filterNot { post -> post.id in seedIds }
    return mergedSeeds + remainingPosts
}

private fun ensureSeedDogParkEvents(
    events: List<CommunityEvent>,
    activeUserId: String,
): List<CommunityEvent> {
    if (!ENABLE_TEST_SEED_DATA) return events
    val seedIds = setOf(
        "event_dogpark_seed_live",
        "event_dogpark_seed_past_1",
        "event_dogpark_seed_past_2",
        "event_dogpark_seed_past_3",
    )
    if (seedIds.all { id -> events.any { event -> event.id == id } }) return events
    val now = Instant.now()
    val attendingByDefault = setOf("user_1", "user_2", "user_3")
    val seeded = listOf(
        CommunityEvent(
            id = "event_dogpark_seed_live",
            title = ONBOARD_EVENT_TITLE,
            description = "Happening now on the north lawn: splash pool lane, shaded water station, and relaxed social play.",
            suburb = "Surry Hills",
            date = now.plusSeconds(900).toString(),
            groupId = TEST_DOG_PARK_GROUP_ID,
            attendeeCount = 19,
            createdBy = "user_3",
            rsvpStatus = if (activeUserId in attendingByDefault) "attending" else "none",
            status = "approved",
        ),
        CommunityEvent(
            id = "event_dogpark_seed_past_1",
            title = "Sunset Recall Ladder",
            description = "Focused recall drills in three short rounds. Dogs with anxious greetings got a separate calm lane.",
            suburb = "Surry Hills",
            date = now.minusSeconds(60_000).toString(),
            groupId = TEST_DOG_PARK_GROUP_ID,
            attendeeCount = 14,
            createdBy = "user_3",
            status = "approved",
        ),
        CommunityEvent(
            id = "event_dogpark_seed_past_2",
            title = "Paws & Pastries Morning Walk",
            description = "Easy 35-minute park loop followed by coffee and water-bowl refills outside the corner bakery.",
            suburb = "Surry Hills",
            date = now.minusSeconds(170_000).toString(),
            groupId = TEST_DOG_PARK_GROUP_ID,
            attendeeCount = 11,
            createdBy = "user_1",
            status = "approved",
        ),
        CommunityEvent(
            id = "event_dogpark_seed_past_3",
            title = "Then vs Now Demo Night",
            description = "Members shared progress clips and practical training notes from the past month.",
            suburb = "Surry Hills",
            date = now.minusSeconds(300_000).toString(),
            groupId = TEST_DOG_PARK_GROUP_ID,
            attendeeCount = 17,
            createdBy = "user_2",
            status = "approved",
        ),
    )
    val existingById = events.associateBy { it.id }
    val mergedSeeds = seeded.map { seed -> existingById[seed.id] ?: seed }
    val remainingEvents = events.filterNot { event -> event.id in seedIds }
    return mergedSeeds + remainingEvents
}

private fun buildSeedDogParkRoster(
    today: LocalDate,
    photoPool: List<String>,
): List<PetRosterItem> {
    if (!ENABLE_TEST_SEED_DATA) return emptyList()
    val photos = photoPool.distinct().ifEmpty { fallbackPetPhotos }
    val names = listOf(
        "Luna",
        "Milo",
        "Maple",
        "Teddy",
        "Nala",
        "Archie",
        "Poppy",
        "Biscuit",
        "Coco",
        "Scout",
        "Blue",
        "Pepper",
    )
    return names.mapIndexed { index, name ->
        PetRosterItem(
            id = "dogpark_roster_$index",
            petName = name,
            photoUrl = photos[index % photos.size],
            addedDate = today.minusDays((index % 7).toLong()),
            suburb = "Surry Hills",
        )
    }.dailyShuffle(seed = TEST_DOG_PARK_GROUP_ID, today = today)
}

class PetSocialViewModel(
    private val repository: PetSocialRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    private var servicesSearchJob: Job? = null
    private var weatherTickerJob: Job? = null
    private val recentParkPresenceSignals = mutableMapOf<String, MutableList<ParkPresenceSignal>>()

    init {
        if (IS_PROVIDER_OS_SURFACE) {
            _uiState.value = _uiState.value.copy(
                isServiceProvider = true,
                selectedTab = AppTab.Profile,
            )
        }
        if (isStagingTestBuild()) {
            _uiState.value = _uiState.value.copy(
                selectedSuburb = STAGING_TEST_SUBURB,
                selectedRangeCenter = "manual",
                profileInfo = _uiState.value.profileInfo.copy(suburb = STAGING_TEST_SUBURB),
            )
        }
        val persistedUserId = repository.activeUserId()
        val persistedTestProfileMode = normalizeTestProfileMode(repository.testProfileMode())
        val persistedProfileHeaderVisible = repository.isTestProfileHeaderVisible()
        _uiState.value = _uiState.value.copy(
            activeUserId = persistedUserId,
            testProfileMode = persistedTestProfileMode,
            profileIdentityHeaderVisible = persistedProfileHeaderVisible,
            isServiceProvider = IS_PROVIDER_OS_SURFACE || _uiState.value.isServiceProvider,
            isCommunityModerator = persistedUserId in COMMUNITY_MODERATOR_IDS,
            authRequired = requiresOtpAuth() && repository.currentAuthToken().isBlank(),
        )
        repository.setActiveUser(persistedUserId)
        applyTestProfileMode(
            mode = persistedTestProfileMode,
            persist = false,
            triggerHomeReload = false,
            showToast = false,
        )
        initializeOnboardingFlowIfNeeded()
        refreshMockCommunityWeather()
        startMockWeatherTicker()
    }

    private fun initializeOnboardingFlowIfNeeded() {
        if (!isOnboardingScriptEnabled()) return
        if (normalizeTestProfileMode(_uiState.value.testProfileMode) != TEST_PROFILE_MODE_ONBOARDING) return
        val state = _uiState.value
        val firstPrompt = pickOnboardingVariation(ONBOARD_WELCOME_QUESTIONS)
        val introTurn = onboardingAssistantTurn(firstPrompt)
        val introConversation = listOf(introTurn)
        val introResponse = onboardingChatResponse(
            answer = firstPrompt,
            conversation = introConversation,
        )
        _uiState.value = state.copy(
            selectedTab = AppTab.BarkAI,
            barkThreads = listOf(
                BarkThread(
                    id = ONBOARD_THREAD_ID,
                    title = "Onboarding",
                    conversation = introConversation,
                    chat = introResponse,
                    updatedAt = System.currentTimeMillis(),
                ),
            ),
            selectedBarkThreadId = ONBOARD_THREAD_ID,
            chat = introResponse,
            conversation = introConversation,
            onboardingActive = true,
            onboardingStep = 0,
            onboardingOwnerName = "",
            onboardingDogName = "",
            onboardingPhotoCaptured = false,
            authRequired = false,
            authOtpRequested = false,
            authInviteId = "",
            authEmail = "",
            authOtpExpiresAt = null,
            authInFlight = false,
            error = null,
        )
    }

    fun setTestProfileMode(mode: String) {
        val normalized = normalizeTestProfileMode(mode)
        applyTestProfileMode(
            mode = normalized,
            persist = true,
            triggerHomeReload = true,
            showToast = true,
        )
    }

    fun setProfileIdentityHeaderVisible(visible: Boolean) {
        repository.setTestProfileHeaderVisible(visible)
        val current = _uiState.value
        _uiState.value = current.copy(
            profileIdentityHeaderVisible = visible,
            toastMessage = if (visible) {
                "Mode set: profile top header shown"
            } else {
                "Mode set: profile top header hidden"
            },
        )
    }

    private fun applyTestProfileMode(
        mode: String,
        persist: Boolean,
        triggerHomeReload: Boolean,
        showToast: Boolean,
    ) {
        val normalizedMode = normalizeTestProfileMode(mode)
        if (persist) {
            repository.setTestProfileMode(normalizedMode)
        }
        val current = _uiState.value
        val currentSuburb = if (isStagingTestBuild()) {
            STAGING_TEST_SUBURB
        } else {
            current.profileInfo.suburb.ifBlank { current.selectedSuburb.ifBlank { "Surry Hills" } }
        }
        val seededProfile = when (normalizedMode) {
            TEST_PROFILE_MODE_ONBOARDING -> buildFreshOnboardingProfile(activeUserId = current.activeUserId, suburb = currentSuburb)
            else -> buildReadyProfile(activeUserId = current.activeUserId, suburb = currentSuburb)
        }
        val nextState = current.copy(
            testProfileMode = normalizedMode,
            profileInfo = seededProfile,
            selectedSuburb = if (isStagingTestBuild()) STAGING_TEST_SUBURB else seededProfile.suburb,
            selectedRangeCenter = if (isStagingTestBuild()) "manual" else current.selectedRangeCenter,
            onboardingActive = false,
            onboardingStep = 0,
            onboardingOwnerName = "",
            onboardingDogName = "",
            onboardingPhotoCaptured = false,
            toastMessage = if (showToast) {
                if (normalizedMode == TEST_PROFILE_MODE_ONBOARDING) {
                    "Mode set: onboarding profile"
                } else {
                    "Mode set: ready profile"
                }
            } else {
                current.toastMessage
            },
        )
        _uiState.value = nextState
        if (normalizedMode == TEST_PROFILE_MODE_ONBOARDING) {
            initializeOnboardingFlowIfNeeded()
        }
        if (triggerHomeReload) {
            loadHomeData(_uiState.value.selectedCategory)
        }
    }

    fun loadHomeData(
        category: String? = _uiState.value.selectedCategory,
        allowAuthRetry: Boolean = true,
    ) {
        val state = _uiState.value
        if (requiresOtpAuth() && repository.currentAuthToken().isBlank()) {
            _uiState.value = state.copy(
                loading = false,
                error = null,
                authRequired = true,
                authInFlight = false,
            )
            return
        }
        val resolvedCategory = category
        val suburb = if (isStagingTestBuild()) STAGING_TEST_SUBURB else state.selectedSuburb
        val useCurrentLocation = state.selectedRangeCenter == "current" &&
            state.currentLatitude != null &&
            state.currentLongitude != null
        val shouldLoadProviderInbox = IS_PROVIDER_OS_SURFACE || state.isServiceProvider
        viewModelScope.launch {
            val totalStartNs = System.nanoTime()
            _uiState.value = _uiState.value.copy(
                loading = true,
                loadingProviderInbox = shouldLoadProviderInbox,
                error = null,
                selectedCategory = resolvedCategory,
            )
            val fetchStartNs = System.nanoTime()
            runCatching {
                val shouldUseRecommendations = state.servicesSearchQuery.isBlank() && state.servicesSortBy == "relevance"
                val recommendations = if (shouldUseRecommendations) {
                    runCatching {
                        repository.loadRecommendedProviders(
                            category = resolvedCategory,
                            suburb = if (isStagingTestBuild()) suburb else null,
                            minRating = state.serviceMinRating?.toDouble(),
                            maxDistanceKm = state.serviceMaxDistanceKm?.toDouble(),
                            userLat = if (useCurrentLocation) state.currentLatitude else null,
                            userLng = if (useCurrentLocation) state.currentLongitude else null,
                        )
                    }.getOrNull()
                } else {
                    null
                }
                val primaryProviders = recommendations?.providers ?: repository.loadProviders(
                    category = resolvedCategory,
                    suburb = suburb,
                    includeInactive = false,
                    minRating = state.serviceMinRating?.toDouble(),
                    maxDistanceKm = state.serviceMaxDistanceKm?.toDouble(),
                    userLat = if (useCurrentLocation) state.currentLatitude else null,
                    userLng = if (useCurrentLocation) state.currentLongitude else null,
                    query = state.servicesSearchQuery.ifBlank { null },
                    sortBy = state.servicesSortBy,
                )
                val providers = if (primaryProviders.isNotEmpty()) {
                    primaryProviders
                } else {
                    // Keep Listings usable when strict local filters return no rows.
                    val relaxedLocalProviders = repository.loadProviders(
                        category = resolvedCategory,
                        suburb = suburb,
                        includeInactive = false,
                        minRating = null,
                        maxDistanceKm = null,
                        userLat = null,
                        userLng = null,
                        query = state.servicesSearchQuery.ifBlank { null },
                        sortBy = "relevance",
                    )
                    if (relaxedLocalProviders.isNotEmpty()) {
                        relaxedLocalProviders
                    } else {
                        repository.loadProviders(
                            category = resolvedCategory,
                            suburb = null,
                            includeInactive = false,
                            minRating = null,
                            maxDistanceKm = null,
                            userLat = null,
                            userLng = null,
                            query = state.servicesSearchQuery.ifBlank { null },
                            sortBy = "relevance",
                        )
                    }
                }
                val ownerListingProviders = repository.loadProviders(
                    userId = state.activeUserId,
                    includeInactive = true,
                )
                val localGroups = repository.loadGroups(suburb = suburb)
                val groups = if (localGroups.isNotEmpty()) localGroups else repository.loadGroups(suburb = null)
                val normalizedCommunitySort = normalizeCommunitySort(state.postsSortBy)
                val localPosts = repository.loadPosts(
                    suburb = suburb,
                    postType = null,
                    sortBy = normalizedCommunitySort,
                    openOnly = null,
                    recentHours = null,
                    centerLat = null,
                    centerLng = null,
                    maxDistanceKm = null,
                )
                val posts = if (localPosts.isNotEmpty()) {
                    localPosts
                } else {
                    repository.loadPosts(
                        suburb = null,
                        postType = null,
                        sortBy = normalizedCommunitySort,
                        openOnly = null,
                        recentHours = null,
                        centerLat = null,
                        centerLng = null,
                        maxDistanceKm = null,
                    )
                }
                val localEvents = repository.loadEvents(suburb = suburb)
                val events = if (localEvents.isNotEmpty()) localEvents else repository.loadEvents(suburb = null)
                val ownerBookings = repository.loadOwnerBookings()
                val providerBookings = repository.loadProviderBookings()
                val providerInboxItems = if (shouldLoadProviderInbox) {
                    runCatching {
                        repository.loadProviderInbox(
                            includeResolved = false,
                            limit = 50,
                        ).items
                    }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
                val calendarEvents = repository.loadCalendarEvents(role = state.selectedCalendarRole)
                val apiMessageThreads = runCatching { repository.loadMessageThreads(limit = 50) }
                    .getOrElse {
                        state.messageThreads.map { thread ->
                            ApiMessageThread(
                                id = thread.id,
                                participantUserId = thread.participantUserId,
                                lastMessage = thread.lastMessage,
                                lastMessageAt = Instant.now().toString(),
                                unreadCount = thread.unreadCount,
                            )
                        }
                    }
                val selectedMessageThreadId = state.selectedMessageThreadId
                    ?.takeIf { selectedThreadId -> apiMessageThreads.any { thread -> thread.id == selectedThreadId } }
                val selectedThreadMessages = if (selectedMessageThreadId.isNullOrBlank()) {
                    emptyList()
                } else {
                    runCatching {
                        repository.loadThreadMessages(
                            threadId = selectedMessageThreadId,
                            limit = 200,
                        )
                    }.getOrDefault(emptyList())
                }
                val notifications = repository.loadNotifications(unreadOnly = false)
                val persistedProfile = runCatching { repository.loadUserProfile() }
                    .map { response ->
                        response.toProfileInfo(
                            activeUserId = state.activeUserId,
                            fallbackProfile = state.profileInfo,
                            fallbackSuburb = suburb,
                        )
                    }
                    .getOrElse { state.profileInfo.copy(suburb = if (isStagingTestBuild()) STAGING_TEST_SUBURB else suburb) }
                val blockedUsers = runCatching { repository.loadBlockedUsers().blockedUserIds }.getOrDefault(emptyList())
                val moderationReports = runCatching { repository.loadModerationReports(includeResolved = false) }.getOrDefault(emptyList())
                val communityFunnel = runCatching { repository.loadCommunityFunnel(windowHours = 168) }.getOrNull()
                val activationFunnel = runCatching { repository.loadCommunityActivationFunnel(windowHours = 72) }.getOrNull()
                val nearbyPetBusinesses = if (useCurrentLocation) {
                    repository.loadNearbyPetBusinesses(
                        latitude = state.currentLatitude ?: 0.0,
                        longitude = state.currentLongitude ?: 0.0,
                    )
                } else {
                    emptyList()
                }
                HomePayload(
                    providers = providers,
                    ownerListingProviders = ownerListingProviders,
                    recommendationSuburb = recommendations?.inferredSuburb,
                    recommendationSource = recommendations?.suburbSource ?: "none",
                    nearbyPetBusinesses = nearbyPetBusinesses,
                    groups = groups,
                    posts = posts,
                    events = events,
                    ownerBookings = ownerBookings,
                    providerBookings = providerBookings,
                    providerInboxItems = providerInboxItems,
                    calendarEvents = calendarEvents,
                    messageThreads = apiMessageThreads,
                    selectedMessageThreadId = selectedMessageThreadId,
                    selectedThreadMessages = selectedThreadMessages,
                    notifications = notifications,
                    profileInfo = persistedProfile,
                    blockedUserIds = blockedUsers,
                    moderationReports = moderationReports,
                    communityFunnelMetrics = communityFunnel,
                    activationFunnelMetrics = activationFunnel,
                )
            }.onSuccess { payload ->
                val fetchMs = elapsedMs(fetchStartNs)
                repository.saveHomeCache(
                    HomeCacheSnapshot(
                        providers = payload.providers,
                        ownerListingProviders = payload.ownerListingProviders,
                        nearbyPetBusinesses = payload.nearbyPetBusinesses,
                        groups = payload.groups,
                        posts = payload.posts,
                        events = payload.events,
                        ownerBookings = payload.ownerBookings,
                        providerBookings = payload.providerBookings,
                        calendarEvents = payload.calendarEvents,
                    ),
                )
                val applyStartNs = System.nanoTime()
                applyHomePayload(
                    payload = payload,
                    suburb = suburb,
                    errorMessage = null,
                    isOfflineMode = false,
                    hasPendingSync = false,
                    metrics = HomeLoadMetrics(
                        source = "network",
                        fetchMs = fetchMs,
                        applyMs = 0L,
                        totalMs = elapsedMs(totalStartNs),
                    ),
                )
                recordHomeLoadMetrics(
                    HomeLoadMetrics(
                        source = "network",
                        fetchMs = fetchMs,
                        applyMs = elapsedMs(applyStartNs),
                        totalMs = elapsedMs(totalStartNs),
                    ),
                )
            }.onFailure { error ->
                val statusCode = (error as? HttpException)?.code()
                if (allowAuthRetry && (statusCode == 401 || statusCode == 403)) {
                    if (allowsDemoLoginFallback()) {
                        val reAuthOk = repository.authenticateAsUser(_uiState.value.activeUserId)
                        if (reAuthOk) {
                            loadHomeData(category = resolvedCategory, allowAuthRetry = false)
                            return@onFailure
                        }
                    }
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        authRequired = true,
                        authInFlight = false,
                        error = "Sign in required",
                    )
                    return@onFailure
                }
                val cached = repository.loadHomeCache()
                if (cached != null) {
                    val fetchMs = elapsedMs(fetchStartNs)
                    val applyStartNs = System.nanoTime()
                    applyHomePayload(
                        payload = HomePayload(
                            providers = cached.providers,
                            ownerListingProviders = cached.ownerListingProviders,
                            recommendationSuburb = _uiState.value.servicesRecommendationSuburb,
                            recommendationSource = _uiState.value.servicesRecommendationSource,
                            nearbyPetBusinesses = cached.nearbyPetBusinesses,
                            groups = cached.groups,
                            posts = cached.posts,
                            events = cached.events,
                            ownerBookings = cached.ownerBookings,
                            providerBookings = cached.providerBookings,
                            providerInboxItems = _uiState.value.providerInboxItems,
                            calendarEvents = cached.calendarEvents,
                            messageThreads = emptyList(),
                            selectedMessageThreadId = null,
                            selectedThreadMessages = emptyList(),
                            notifications = emptyList(),
                            profileInfo = _uiState.value.profileInfo.copy(suburb = suburb),
                            blockedUserIds = _uiState.value.blockedUserIds,
                            moderationReports = _uiState.value.moderationReports,
                            communityFunnelMetrics = _uiState.value.communityFunnelMetrics,
                            activationFunnelMetrics = _uiState.value.activationFunnelMetrics,
                        ),
                        suburb = suburb,
                        errorMessage = error.message ?: "Network unavailable",
                        isOfflineMode = true,
                        hasPendingSync = true,
                        metrics = HomeLoadMetrics(
                            source = "cache",
                            fetchMs = fetchMs,
                            applyMs = 0L,
                            totalMs = elapsedMs(totalStartNs),
                        ),
                    )
                    recordHomeLoadMetrics(
                        HomeLoadMetrics(
                            source = "cache",
                            fetchMs = fetchMs,
                            applyMs = elapsedMs(applyStartNs),
                            totalMs = elapsedMs(totalStartNs),
                        ),
                    )
                } else {
                    val metrics = HomeLoadMetrics(
                        source = "error",
                        fetchMs = elapsedMs(fetchStartNs),
                        applyMs = 0L,
                        totalMs = elapsedMs(totalStartNs),
                    )
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        loadingProviderInbox = false,
                        error = error.message,
                        isOfflineMode = true,
                        hasPendingSync = true,
                        latestHomeLoadMetrics = metrics,
                    )
                    recordHomeLoadMetrics(metrics)
                }
            }
        }
    }

    private fun applyHomePayload(
        payload: HomePayload,
        suburb: String,
        errorMessage: String?,
        isOfflineMode: Boolean,
        hasPendingSync: Boolean,
        metrics: HomeLoadMetrics? = null,
    ) {
        val current = _uiState.value
        val providers = ensureSeedProviders(
            providers = payload.providers,
            suburb = suburb,
            category = current.selectedCategory,
        )
        val groups = ensureSeedDogParkGroup(
            groups = payload.groups,
            activeUserId = current.activeUserId,
        )
        val posts = ensureSeedDogParkPosts(payload.posts)
        val events = ensureSeedDogParkEvents(
            events = payload.events,
            activeUserId = current.activeUserId,
        )
        val providerById = providers.associateBy { it.id }
        val existingFavoriteIds = current.favoriteProviderIds
        val syncedFavorites = if (existingFavoriteIds.isEmpty()) {
            providers.take(3).map { it.id }
        } else {
            existingFavoriteIds.filter { id -> providers.any { provider -> provider.id == id } }
        }
        val syncedListings = payload.ownerListingProviders
            .filter { provider -> provider.ownerUserId == current.activeUserId }
            .map { provider ->
                ProviderListing(
                    id = provider.id,
                    title = provider.name,
                    category = provider.category.replace("_", " "),
                    status = provider.status,
                    priceFrom = provider.priceFrom,
                    description = provider.description,
                    suburb = provider.suburb,
                    imageUrls = provider.imageUrls,
                )
            }
        val joinedEvents = events
            .filter { event -> event.rsvpStatus == "attending" }
            .map { event ->
                JoinedEvent(
                    id = event.id,
                    title = event.title,
                    date = event.date,
                    suburb = event.suburb,
                )
            }
        val ownerBookings = payload.ownerBookings.map { booking ->
            val provider = providerById[booking.providerId]
            OwnerBooking(
                id = booking.id,
                serviceName = provider?.name ?: booking.providerId,
                providerId = booking.providerId,
                providerUserId = provider?.ownerUserId.orEmpty(),
                providerAccountLabel = provider?.ownerLabel
                    ?: provider?.ownerUserId?.let(::accountLabel)
                    ?: "Unknown owner",
                date = booking.date,
                timeSlot = booking.timeSlot,
                status = booking.status,
                note = booking.note,
            )
        }
        val providerBookings = payload.providerBookings.map { booking ->
            ProviderBooking(
                id = booking.id,
                petName = booking.petName,
                ownerUserId = booking.ownerUserId,
                providerId = booking.providerId,
                serviceName = providerById[booking.providerId]?.name ?: booking.providerId,
                date = booking.date,
                timeSlot = booking.timeSlot,
                status = booking.status,
            )
        }
        val selectedThreadId = payload.selectedMessageThreadId
            ?.takeIf { candidate -> payload.messageThreads.any { thread -> thread.id == candidate } }
        val selectedThreadMessages = if (selectedThreadId.isNullOrBlank()) {
            emptyList()
        } else {
            payload.selectedThreadMessages.map { message -> message.toDirectMessage() }
        }
        val validReadMessageIds = selectedThreadMessages.map { message -> message.id }.toSet()
        val localNotifications = buildLocalCommunityNotifications(
            activeUserId = current.activeUserId,
            followedGroupIds = current.followedGroupIds,
            savedPostIds = current.savedCommunityPostIds,
            groups = groups,
            events = events,
            posts = posts,
            moderationReports = payload.moderationReports,
            includeFollowedGroupAlerts = current.notifyFollowedGroupAlerts,
            includeSavedPostUpdates = current.notifySavedPostUpdates,
            includeSafetyAlerts = current.notifySafetyAlerts,
        )
        val validLocalReadIds = current.readLocalNotificationIds.intersect(localNotifications.map { it.id }.toSet())
        val mergedNotifications = mergeNotifications(
            remoteNotifications = payload.notifications,
            localNotifications = localNotifications,
            localReadIds = validLocalReadIds,
        )
        val messageThreads = buildMessageThreadsFromApi(
            activeUserId = _uiState.value.activeUserId,
            apiThreads = payload.messageThreads,
            providers = providers,
            groups = groups,
            posts = posts,
            ownerBookings = ownerBookings,
            providerBookings = providerBookings,
            mutedThreadIds = current.mutedMessageThreadIds,
            pinnedThreadIds = current.pinnedMessageThreadIds,
            blockedParticipantIds = current.blockedUserIds.toSet(),
        )
        val friendProfiles = buildFriendProfiles(
            activeUserId = current.activeUserId,
            messageThreads = messageThreads,
            existingProfiles = current.friendProfiles,
        )
        val validSavedPostIds = current.savedCommunityPostIds.intersect(posts.map { it.id }.toSet())
        val validSavedEventIds = current.savedCommunityEventIds.intersect(events.map { it.id }.toSet())
        val validFollowedGroupIds = current.followedGroupIds.intersect(groups.map { it.id }.toSet())
        val groupRosters = buildGroupRosters(
            groups = groups,
            posts = posts,
            providers = providers,
            today = LocalDate.now(),
        )
        val boostedGroupRosters = if (ENABLE_TEST_SEED_DATA) {
            buildMap {
                putAll(groupRosters)
                put(
                    TEST_DOG_PARK_GROUP_ID,
                    buildSeedDogParkRoster(
                        today = LocalDate.now(),
                        photoPool = providers.flatMap { it.imageUrls } + fallbackPetPhotos,
                    ),
                )
            }
        } else {
            groupRosters
        }
        val groomerRosters = buildGroomerRosters(
            providers = providers,
            today = LocalDate.now(),
        )
        val providerIdsInScope = buildSet {
            providerBookings
                .map { booking -> booking.providerId }
                .filter { id -> id.isNotBlank() }
                .forEach { id -> add(id) }
            syncedListings
                .map { listing -> listing.id }
                .filter { id -> id.isNotBlank() }
                .forEach { id -> add(id) }
        }
        _uiState.value = current.copy(
            providers = reuseIfEquivalent(current.providers, providers),
            nearbyPetBusinesses = reuseIfEquivalent(current.nearbyPetBusinesses, payload.nearbyPetBusinesses),
            groups = reuseIfEquivalent(current.groups, groups),
            posts = reuseIfEquivalent(current.posts, posts),
            communityCommentsByPostId = current.communityCommentsByPostId.filterKeys { postId ->
                posts.any { post -> post.id == postId }
            },
            loadingCommentPostIds = current.loadingCommentPostIds.filter { postId ->
                posts.any { post -> post.id == postId }
            }.toSet(),
            communityEvents = reuseIfEquivalent(current.communityEvents, events),
            ownerBookings = reuseIfEquivalent(current.ownerBookings, ownerBookings),
            providerBookings = reuseIfEquivalent(current.providerBookings, providerBookings),
            providerInboxItems = reuseIfEquivalent(current.providerInboxItems, payload.providerInboxItems),
            loadingProviderInbox = false,
            calendarEvents = reuseIfEquivalent(current.calendarEvents, payload.calendarEvents),
            messageThreads = reuseIfEquivalent(current.messageThreads, messageThreads),
            friendProfiles = reuseIfEquivalent(current.friendProfiles, friendProfiles),
            selectedMessageThreadId = selectedThreadId,
            directMessages = reuseIfEquivalent(current.directMessages, selectedThreadMessages),
            readDirectMessageIds = validReadMessageIds,
            savedCommunityPostIds = validSavedPostIds,
            savedCommunityEventIds = validSavedEventIds,
            mutedCommunityKeywords = current.mutedCommunityKeywords,
            followedGroupIds = validFollowedGroupIds,
            joinedEvents = reuseIfEquivalent(current.joinedEvents, joinedEvents),
            favoriteProviderIds = reuseIfEquivalent(current.favoriteProviderIds, syncedFavorites),
            providerListings = reuseIfEquivalent(current.providerListings, syncedListings),
            providerBlackoutsByProvider = current.providerBlackoutsByProvider.filterKeys { providerId ->
                syncedListings.any { listing -> listing.id == providerId }
            },
            bookingHistoryByBookingId = current.bookingHistoryByBookingId.filterKeys { bookingId ->
                ownerBookings.any { booking -> booking.id == bookingId } ||
                    providerBookings.any { booking -> booking.id == bookingId }
            },
            loadingBookingHistoryIds = current.loadingBookingHistoryIds.filter { bookingId ->
                ownerBookings.any { booking -> booking.id == bookingId } ||
                    providerBookings.any { booking -> booking.id == bookingId }
            }.toSet(),
            providerRescheduleSlotsByKey = current.providerRescheduleSlotsByKey.filterKeys { key ->
                providerIdsInScope.any { providerId -> key.startsWith("$providerId|") }
            },
            loadingProviderRescheduleKeys = current.loadingProviderRescheduleKeys.filter { key ->
                providerIdsInScope.any { providerId -> key.startsWith("$providerId|") }
            }.toSet(),
            headerRosterPet = boostedGroupRosters.values
                .flatten()
                .dailyShuffle("header", LocalDate.now())
                .firstOrNull(),
            groupPetRosters = reuseIfEquivalent(current.groupPetRosters, boostedGroupRosters),
            groomerPetRosters = reuseIfEquivalent(current.groomerPetRosters, groomerRosters),
            servicesRecommendationSuburb = payload.recommendationSuburb,
            servicesRecommendationSource = payload.recommendationSource,
            profileInfo = payload.profileInfo.copy(
                suburb = if (isStagingTestBuild()) STAGING_TEST_SUBURB else payload.profileInfo.suburb.ifBlank { suburb },
            ),
            loading = false,
            error = errorMessage,
            authRequired = false,
            authInFlight = false,
            latestHomeLoadMetrics = metrics ?: current.latestHomeLoadMetrics,
            isOfflineMode = isOfflineMode,
            hasPendingSync = hasPendingSync,
            notifications = reuseIfEquivalent(current.notifications, mergedNotifications),
            readLocalNotificationIds = validLocalReadIds,
            acknowledgedCommunityNotificationIds = current.acknowledgedCommunityNotificationIds
                .intersect(mergedNotifications.map { notification -> notification.id }.toSet()),
            acknowledgedMessageNotificationIds = current.acknowledgedMessageNotificationIds
                .intersect(mergedNotifications.map { notification -> notification.id }.toSet()),
            blockedUserIds = reuseIfEquivalent(current.blockedUserIds, payload.blockedUserIds),
            moderationReports = reuseIfEquivalent(current.moderationReports, payload.moderationReports),
            communityFunnelMetrics = payload.communityFunnelMetrics,
            activationFunnelMetrics = payload.activationFunnelMetrics ?: current.activationFunnelMetrics,
            isCommunityModerator = current.activeUserId in COMMUNITY_MODERATOR_IDS,
            selectedCommunityGroupId = current.selectedCommunityGroupId
                ?.takeIf { selectedId -> groups.any { group -> group.id == selectedId } },
        )
        maybeRunAutoParkCheckIn(reason = "home_payload_applied")
    }

    fun refreshProviderInbox(includeResolved: Boolean = false) {
        val state = _uiState.value
        if (!(IS_PROVIDER_OS_SURFACE || state.isServiceProvider)) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loadingProviderInbox = true, error = null)
            runCatching {
                repository.loadProviderInbox(
                    includeResolved = includeResolved,
                    limit = 50,
                ).items
            }.onSuccess { items ->
                _uiState.value = _uiState.value.copy(
                    providerInboxItems = items,
                    loadingProviderInbox = false,
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    loadingProviderInbox = false,
                    error = error.message,
                )
            }
        }
    }

    fun sendQuickQuoteOfferFromInbox(inboxItemId: String) {
        val proposedDate = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString()
        val proposedTimeSlot = "09:00"
        val expiresAt = Instant.now().plusSeconds(24 * 60 * 60).toString()
        sendQuoteOfferFromInbox(
            inboxItemId = inboxItemId,
            priceCents = 6500,
            proposedDate = proposedDate,
            proposedTimeSlot = proposedTimeSlot,
            expiresAt = expiresAt,
            note = "Quick offer from Provider Ops.",
        )
    }

    fun sendQuoteOfferFromInbox(
        inboxItemId: String,
        priceCents: Int,
        proposedDate: String,
        proposedTimeSlot: String,
        expiresAt: String,
        note: String = "",
    ) {
        val normalizedId = inboxItemId.trim()
        if (normalizedId.isBlank()) return
        if (priceCents <= 0) {
            _uiState.value = _uiState.value.copy(toastMessage = "Offer price must be greater than zero")
            return
        }
        val cleanedDate = proposedDate.trim()
        val cleanedTimeSlot = proposedTimeSlot.trim()
        val cleanedExpiresAt = expiresAt.trim()
        if (cleanedDate.isBlank() || cleanedTimeSlot.isBlank() || cleanedExpiresAt.isBlank()) {
            _uiState.value = _uiState.value.copy(toastMessage = "Offer date, time, and expiry are required")
            return
        }
        val state = _uiState.value
        val item = state.providerInboxItems.firstOrNull { inboxItem -> inboxItem.id == normalizedId }
            ?: run {
                _uiState.value = state.copy(toastMessage = "Inbox item not found")
                return
            }
        val quoteRequestId = item.quoteRequestId?.trim().orEmpty()
        if (item.itemType != "quote_request" || quoteRequestId.isBlank()) {
            _uiState.value = state.copy(toastMessage = "Only quote requests can receive offers")
            return
        }
        if (normalizedId in state.sendingQuoteOfferItemIds) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                sendingQuoteOfferItemIds = _uiState.value.sendingQuoteOfferItemIds + normalizedId,
                error = null,
            )
            runCatching {
                repository.createServiceQuoteOffer(
                    quoteRequestId = quoteRequestId,
                    providerId = item.providerId,
                    priceCents = priceCents,
                    proposedDate = cleanedDate,
                    proposedTimeSlot = cleanedTimeSlot,
                    expiresAt = cleanedExpiresAt,
                    note = note.trim(),
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    sendingQuoteOfferItemIds = _uiState.value.sendingQuoteOfferItemIds - normalizedId,
                    toastMessage = "Offer sent for ${item.providerName} (AUD ${formatAudCents(priceCents)})",
                )
                refreshProviderInbox(includeResolved = false)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    sendingQuoteOfferItemIds = _uiState.value.sendingQuoteOfferItemIds - normalizedId,
                    error = error.message,
                )
            }
        }
    }

    fun switchTab(tab: AppTab) {
        val current = _uiState.value
        _uiState.value = current.copy(
            selectedTab = tab,
            selectedCommunityGroupId = if (tab == AppTab.Community) {
                current.selectedCommunityGroupId
            } else {
                null
            },
            profileNotificationFilter = if (tab == AppTab.Profile) current.profileNotificationFilter else "all",
            acknowledgedCommunityNotificationIds = when (tab) {
                AppTab.Community -> current.acknowledgedCommunityNotificationIds + current.notifications
                    .asSequence()
                    .filter { notification -> isCommunityNotification(notification.category) }
                    .map { notification -> notification.id }
                    .toSet()
                else -> current.acknowledgedCommunityNotificationIds
            },
            acknowledgedMessageNotificationIds = when (tab) {
                AppTab.Messages -> current.acknowledgedMessageNotificationIds + current.notifications
                    .asSequence()
                    .filter { notification -> isMessageNotification(notification.category) }
                    .map { notification -> notification.id }
                    .toSet()
                else -> current.acknowledgedMessageNotificationIds
            },
        )
    }

    fun openProfileNotifications(filter: String = "all") {
        val normalized = when (filter.lowercase()) {
            "community", "messages", "safety" -> filter.lowercase()
            else -> "all"
        }
        _uiState.value = _uiState.value.copy(
            selectedTab = AppTab.Profile,
            profileNotificationFilter = normalized,
        )
    }

    fun openCommunityGroup(groupId: String) {
        if (groupId.isBlank()) return
        _uiState.value = _uiState.value.copy(
            selectedTab = AppTab.Community,
            selectedCommunityGroupId = groupId,
        )
    }

    fun clearSelectedCommunityGroup() {
        _uiState.value = _uiState.value.copy(selectedCommunityGroupId = null)
    }

    fun refreshFriendQrPayload() {
        val state = _uiState.value
        if (state.friendQrLoading) return
        _uiState.value = state.copy(friendQrLoading = true)
        viewModelScope.launch {
            runCatching { repository.issueFriendQr() }
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        friendQrPayload = response.friendUrl,
                        friendQrExpiresAt = response.expiresAt,
                        friendQrLoading = false,
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        friendQrLoading = false,
                        toastMessage = "Unable to refresh friend QR",
                    )
                }
        }
    }

    fun resolveFriendQrToken(friendToken: String) {
        val cleanToken = friendToken.trim()
        if (cleanToken.isBlank()) {
            _uiState.value = _uiState.value.copy(toastMessage = "Invalid friend QR")
            return
        }
        viewModelScope.launch {
            runCatching { repository.verifyFriendQr(cleanToken) }
                .onSuccess { response ->
                    addFriendFromQr(
                        userId = response.userId,
                        humanName = response.humanName,
                        dogName = response.dogName,
                    )
                }
                .onFailure { error ->
                    val message = when ((error as? HttpException)?.code()) {
                        401 -> "Friend QR expired. Ask for a new one."
                        409 -> "This is your profile QR"
                        else -> "Unable to verify friend QR"
                    }
                    _uiState.value = _uiState.value.copy(toastMessage = message)
                }
        }
    }

    fun addFriend(userId: String) {
        addFriendFromQr(userId = userId, humanName = null, dogName = null)
    }

    fun addFriendFromQr(
        userId: String,
        humanName: String? = null,
        dogName: String? = null,
    ) {
        val normalized = userId.trim()
        if (normalized.isBlank()) return
        val state = _uiState.value
        if (normalized == state.activeUserId) {
            _uiState.value = state.copy(toastMessage = "This is your profile QR")
            return
        }
        val incomingHumanName = humanName?.trim().orEmpty()
        val incomingDogName = dogName?.trim().orEmpty()
        val existing = state.friendProfiles.firstOrNull { profile -> profile.userId == normalized }
        val wasAlreadyFriend = existing?.isFriend == true
        val updatedProfiles = if (existing != null) {
            state.friendProfiles.map { profile ->
                if (profile.userId != normalized) {
                    profile
                } else {
                    profile.copy(
                        humanName = if (incomingHumanName.isNotBlank()) incomingHumanName else profile.humanName,
                        dogName = if (incomingDogName.isNotBlank()) incomingDogName else profile.dogName,
                        isFriend = true,
                    )
                }
            }
        } else {
            val inserted = FriendProfile(
                userId = normalized,
                humanName = incomingHumanName.ifBlank { accountLabel(normalized) },
                dogName = incomingDogName.ifBlank { "Dog" },
                dogPhotoUrl = "https://images.unsplash.com/photo-1580467277788-c6e040296602?auto=format&fit=crop&w=1200&q=80",
                isFriend = true,
            )
            (state.friendProfiles + inserted).sortedWith(
                compareByDescending<FriendProfile> { profile -> profile.isFriend }
                    .thenBy { profile -> profile.humanName.lowercase() },
            )
        }
        _uiState.value = state.copy(
            friendProfiles = updatedProfiles,
            toastMessage = if (wasAlreadyFriend) "Already friends" else "Friend added",
        )
    }

    fun removeFriend(userId: String) {
        val normalized = userId.trim()
        if (normalized.isBlank()) return
        val state = _uiState.value
        val updatedProfiles = state.friendProfiles.map { profile ->
            if (profile.userId == normalized) profile.copy(isFriend = false) else profile
        }
        _uiState.value = state.copy(
            friendProfiles = updatedProfiles,
            toastMessage = "Friend removed",
        )
    }

    fun openMessagesForUser(userId: String) {
        val normalized = userId.trim()
        if (normalized.isBlank()) return
        val state = _uiState.value
        trackActivationEventAsync(
            step = "message_handoff",
            status = "attempted",
            metadata = mapOf(
                "source" to "profile_social",
                "target_present" to "true",
            ),
        )
        val thread = state.messageThreads.firstOrNull { candidate -> candidate.participantUserId == normalized }
        if (thread == null) {
            trackActivationFailureAsync(
                step = "message_handoff",
                message = "thread_not_found",
                metadata = mapOf(
                    "source" to "profile_social",
                    "target_present" to "true",
                ),
            )
            _uiState.value = state.copy(
                selectedTab = AppTab.Messages,
                toastMessage = "Friend added. Start chat from Messages list.",
            )
            return
        }
        selectMessageThread(thread.id)
        _uiState.value = _uiState.value.copy(selectedTab = AppTab.Messages)
        trackActivationEventAsync(
            step = "message_handoff",
            status = "succeeded",
            metadata = mapOf(
                "source" to "profile_social",
                "target_present" to "true",
            ),
        )
    }

    fun startNewBarkThread() {
        if (isOnboardingScriptEnabled() && _uiState.value.onboardingActive) {
            _uiState.value = _uiState.value.copy(
                selectedTab = AppTab.BarkAI,
                toastMessage = "Finish onboarding first",
            )
            return
        }
        val now = System.currentTimeMillis()
        val newId = "bark_thread_$now"
        val newThread = BarkThread(
            id = newId,
            title = "New thread",
            updatedAt = now,
        )
        val existing = _uiState.value.barkThreads.filterNot { it.id == newId }
        _uiState.value = _uiState.value.copy(
            selectedTab = AppTab.BarkAI,
            selectedBarkThreadId = newId,
            barkThreads = (listOf(newThread) + existing).take(20),
            chat = null,
            conversation = emptyList(),
            profileSuggestion = null,
            a2uiProfileCard = null,
            a2uiProviderCard = null,
            streamingAssistantText = "",
            loading = false,
        )
    }

    fun selectBarkThread(threadId: String) {
        if (isOnboardingScriptEnabled() && _uiState.value.onboardingActive) {
            _uiState.value = _uiState.value.copy(
                selectedTab = AppTab.BarkAI,
                toastMessage = "Finish onboarding first",
            )
            return
        }
        val state = _uiState.value
        val selected = state.barkThreads.firstOrNull { it.id == threadId } ?: return
        _uiState.value = state.copy(
            selectedTab = AppTab.BarkAI,
            selectedBarkThreadId = threadId,
            chat = selected.chat,
            conversation = selected.conversation,
            profileSuggestion = selected.profileSuggestion,
            a2uiProfileCard = selected.a2uiProfileCard,
            a2uiProviderCard = selected.a2uiProviderCard,
            streamingAssistantText = "",
            loading = false,
        )
    }

    fun createGroupInvite(groupId: String) {
        if (groupId.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.createGroupInvite(groupId) }
                .onSuccess { invite ->
                    _uiState.value = _uiState.value.copy(
                        latestGroupInvites = _uiState.value.latestGroupInvites + (groupId to invite),
                        toastMessage = "Invite link created",
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(error = error.message)
                }
        }
    }

    fun clearGroupInvite(groupId: String) {
        _uiState.value = _uiState.value.copy(
            latestGroupInvites = _uiState.value.latestGroupInvites - groupId,
        )
    }

    fun trackQrScannerOutcome(outcome: String, detail: String?) {
        val normalizedOutcome = outcome.trim().lowercase().ifBlank { "unknown" }
        val metadata = mutableMapOf(
            "source" to "community_scanner",
            "outcome" to normalizedOutcome,
        )
        detail?.trim()?.takeIf { value -> value.isNotBlank() }?.let { cleanDetail ->
            metadata["detail"] = sanitizeTelemetryValue(cleanDetail, maxLength = 96)
        }
        trackActivationEventAsync(
            step = "qr_scan",
            status = when (normalizedOutcome) {
                "invite_token_detected", "open_install_url", "open_url" -> "succeeded"
                "open_url_failed", "invalid_payload" -> "failed"
                else -> "attempted"
            },
            metadata = metadata,
        )
        if (normalizedOutcome == "open_url_failed" || normalizedOutcome == "invalid_payload") {
            trackActivationFailureAsync(
                step = "qr_scan",
                message = normalizedOutcome,
                metadata = metadata,
            )
        }
    }

    fun resolveInviteToken(token: String?) {
        val cleanToken = token?.trim().orEmpty()
        if (cleanToken.isBlank()) return
        viewModelScope.launch {
            val startedAtNs = System.nanoTime()
            trackActivationEvent(
                step = "invite_resolve",
                status = "attempted",
                metadata = mapOf(
                    "source" to "invite_token",
                    "token_length" to cleanToken.length.toString(),
                ),
            )
            runCatching { repository.resolveGroupInvite(cleanToken) }
                .onSuccess { invite ->
                    trackActivationEvent(
                        step = "invite_resolve",
                        status = "succeeded",
                        metadata = mapOf(
                            "source" to "invite_token",
                            "group_id" to invite.groupId,
                            "suburb" to sanitizeTelemetryValue(invite.suburb, maxLength = 64),
                        ),
                        durationMs = elapsedMs(startedAtNs).toInt(),
                    )
                    _uiState.value = _uiState.value.copy(
                        pendingInvite = invite,
                        selectedSuburb = invite.suburb,
                        selectedTab = AppTab.Community,
                    )
                    loadHomeData(_uiState.value.selectedCategory)
                }
                .onFailure { error ->
                    trackActivationFailure(
                        step = "invite_resolve",
                        error = error,
                        metadata = mapOf(
                            "source" to "invite_token",
                            "token_length" to cleanToken.length.toString(),
                        ),
                        durationMs = elapsedMs(startedAtNs).toInt(),
                    )
                    _uiState.value = _uiState.value.copy(error = error.message)
                }
        }
    }

    fun dismissPendingInvite() {
        _uiState.value = _uiState.value.copy(pendingInvite = null)
    }

    fun requestAuthOtp(inviteId: String, email: String) {
        val cleanInviteId = inviteId.trim()
        val cleanEmail = email.trim().lowercase()
        if (cleanInviteId.isBlank() || cleanEmail.isBlank()) return
        viewModelScope.launch {
            val startedAtNs = System.nanoTime()
            trackActivationEvent(
                step = "otp_request",
                status = "attempted",
                metadata = mapOf(
                    "invite_id_present" to "true",
                    "email_domain" to emailDomain(cleanEmail),
                ),
            )
            _uiState.value = _uiState.value.copy(
                authInFlight = true,
                authRequired = true,
                error = null,
            )
            runCatching {
                repository.requestOtp(
                    inviteId = cleanInviteId,
                    email = cleanEmail,
                )
            }.onSuccess { response ->
                trackActivationEvent(
                    step = "otp_request",
                    status = "succeeded",
                    metadata = mapOf(
                        "invite_id_present" to "true",
                        "email_domain" to emailDomain(cleanEmail),
                        "expires_present" to (!response.expiresAt.isNullOrBlank()).toString(),
                    ),
                    durationMs = elapsedMs(startedAtNs).toInt(),
                )
                _uiState.value = _uiState.value.copy(
                    authRequired = true,
                    authOtpRequested = true,
                    authInviteId = cleanInviteId,
                    authEmail = cleanEmail,
                    authOtpExpiresAt = response.expiresAt,
                    authInFlight = false,
                    toastMessage = "OTP sent to $cleanEmail",
                )
            }.onFailure { error ->
                trackActivationFailure(
                    step = "otp_request",
                    error = error,
                    metadata = mapOf(
                        "invite_id_present" to "true",
                        "email_domain" to emailDomain(cleanEmail),
                    ),
                    durationMs = elapsedMs(startedAtNs).toInt(),
                )
                _uiState.value = _uiState.value.copy(
                    authRequired = true,
                    authInFlight = false,
                    error = error.message ?: "Unable to request OTP",
                )
            }
        }
    }

    fun resetAuthOtpRequest(inviteId: String, email: String) {
        _uiState.value = _uiState.value.copy(
            authRequired = true,
            authOtpRequested = false,
            authInviteId = inviteId.trim(),
            authEmail = email.trim().lowercase(),
            authOtpExpiresAt = null,
            authInFlight = false,
            error = null,
        )
    }

    fun verifyAuthOtp(otpCode: String) {
        val state = _uiState.value
        val cleanInviteId = state.authInviteId.trim()
        val cleanEmail = state.authEmail.trim().lowercase()
        val cleanOtp = otpCode.trim()
        if (cleanInviteId.isBlank() || cleanEmail.isBlank() || cleanOtp.isBlank()) return
        viewModelScope.launch {
            val startedAtNs = System.nanoTime()
            trackActivationEvent(
                step = "otp_verify",
                status = "attempted",
                metadata = mapOf(
                    "invite_id_present" to "true",
                    "email_domain" to emailDomain(cleanEmail),
                    "otp_length" to cleanOtp.length.toString(),
                ),
            )
            _uiState.value = state.copy(
                authInFlight = true,
                authRequired = true,
                error = null,
            )
            runCatching {
                repository.verifyOtp(
                    inviteId = cleanInviteId,
                    email = cleanEmail,
                    otpCode = cleanOtp,
                )
            }.onSuccess { response ->
                trackActivationEvent(
                    step = "otp_verify",
                    status = "succeeded",
                    metadata = mapOf(
                        "invite_id_present" to "true",
                        "email_domain" to emailDomain(cleanEmail),
                        "otp_length" to cleanOtp.length.toString(),
                        "user_id" to response.userId,
                    ),
                    durationMs = elapsedMs(startedAtNs).toInt(),
                )
                repository.setActiveUser(response.userId)
                _uiState.value = _uiState.value.copy(
                    activeUserId = response.userId,
                    authRequired = false,
                    authOtpRequested = false,
                    authInviteId = "",
                    authEmail = "",
                    authOtpExpiresAt = null,
                    authInFlight = false,
                    selectedMessageThreadId = null,
                    readDirectMessageIds = emptySet(),
                    mutedMessageThreadIds = emptySet(),
                    pinnedMessageThreadIds = emptySet(),
                    latestHomeLoadMetrics = null,
                    homeLoadHistory = emptyList(),
                    readLocalNotificationIds = emptySet(),
                    acknowledgedCommunityNotificationIds = emptySet(),
                    acknowledgedMessageNotificationIds = emptySet(),
                    notifyFollowedGroupAlerts = true,
                    notifySavedPostUpdates = true,
                    notifySafetyAlerts = true,
                    savedCommunityPostIds = emptySet(),
                    savedCommunityEventIds = emptySet(),
                    mutedCommunityKeywords = emptySet(),
                    followedGroupIds = emptySet(),
                    friendProfiles = emptyList(),
                    isCommunityModerator = response.userId in COMMUNITY_MODERATOR_IDS,
                    toastMessage = "Signed in as ${response.userId}",
                )
                loadHomeData(_uiState.value.selectedCategory, allowAuthRetry = false)
            }.onFailure { error ->
                trackActivationFailure(
                    step = "otp_verify",
                    error = error,
                    metadata = mapOf(
                        "invite_id_present" to "true",
                        "email_domain" to emailDomain(cleanEmail),
                        "otp_length" to cleanOtp.length.toString(),
                    ),
                    durationMs = elapsedMs(startedAtNs).toInt(),
                )
                _uiState.value = _uiState.value.copy(
                    authRequired = true,
                    authInFlight = false,
                    error = error.message ?: "Invalid OTP",
                )
            }
        }
    }

    fun completeInviteOnboarding(
        ownerName: String,
        dogName: String,
        sharePhotoToGroup: Boolean,
        photoCaptured: Boolean,
    ) {
        val invite = _uiState.value.pendingInvite ?: return
        if (ownerName.isBlank() || dogName.isBlank()) return
        viewModelScope.launch {
            val startedAtNs = System.nanoTime()
            trackActivationEvent(
                step = "onboarding_complete",
                status = "attempted",
                metadata = mapOf(
                    "group_id" to invite.groupId,
                    "share_photo" to sharePhotoToGroup.toString(),
                    "photo_captured" to photoCaptured.toString(),
                ),
            )
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching {
                repository.completeGroupOnboarding(
                    inviteToken = invite.token,
                    ownerName = ownerName.trim(),
                    dogName = dogName.trim(),
                    suburb = invite.suburb,
                    latitude = _uiState.value.currentLatitude,
                    longitude = _uiState.value.currentLongitude,
                    sharePhotoToGroup = sharePhotoToGroup,
                    photoSource = if (photoCaptured) "captured_on_device" else "not_captured",
                )
            }.onSuccess { response ->
                trackActivationEvent(
                    step = "onboarding_complete",
                    status = "succeeded",
                    metadata = mapOf(
                        "group_id" to invite.groupId,
                        "membership_status" to response.membershipStatus,
                        "created_post" to (!response.createdPostId.isNullOrBlank()).toString(),
                        "share_photo" to sharePhotoToGroup.toString(),
                        "photo_captured" to photoCaptured.toString(),
                    ),
                    durationMs = elapsedMs(startedAtNs).toInt(),
                )
                repository.setActiveUser(response.userId)
                _uiState.value = _uiState.value.copy(
                    activeUserId = response.userId,
                    pendingInvite = null,
                    selectedSuburb = invite.suburb,
                    selectedTab = AppTab.Community,
                    loading = false,
                    toastMessage = "Joined ${invite.groupName} as ${response.userId}",
                )
                loadHomeData(_uiState.value.selectedCategory)
            }.onFailure { error ->
                trackActivationFailure(
                    step = "onboarding_complete",
                    error = error,
                    metadata = mapOf(
                        "group_id" to invite.groupId,
                        "share_photo" to sharePhotoToGroup.toString(),
                        "photo_captured" to photoCaptured.toString(),
                    ),
                    durationMs = elapsedMs(startedAtNs).toInt(),
                )
                _uiState.value = _uiState.value.copy(loading = false, error = error.message)
            }
        }
    }

    fun retrySync() {
        if (_uiState.value.loading) return
        loadHomeData(_uiState.value.selectedCategory)
    }

    fun setServicesViewMode(mode: String) {
        if (mode != "list" && mode != "map") return
        _uiState.value = _uiState.value.copy(servicesViewMode = mode)
    }

    fun updateServicesSearchQuery(query: String) {
        if (_uiState.value.servicesSearchQuery == query) return
        _uiState.value = _uiState.value.copy(servicesSearchQuery = query)
        servicesSearchJob?.cancel()
        servicesSearchJob = viewModelScope.launch {
            delay(300)
            if (_uiState.value.servicesSearchQuery == query) {
                loadHomeData(_uiState.value.selectedCategory)
            }
        }
    }

    fun updateServicesSortBy(sortBy: String) {
        if (_uiState.value.servicesSortBy == sortBy) return
        _uiState.value = _uiState.value.copy(servicesSortBy = sortBy)
        loadHomeData(_uiState.value.selectedCategory)
    }

    fun updatePostsSortBy(sortBy: String) {
        _uiState.value = _uiState.value.copy(postsSortBy = normalizeCommunitySort(sortBy))
        loadHomeData(_uiState.value.selectedCategory)
    }

    fun updateCommunityFeedFilters(openOnly: Boolean, recentHours: Int?) {
        _uiState.value = _uiState.value.copy(
            communityOpenOnly = openOnly,
            communityRecentHours = recentHours,
        )
        loadHomeData(_uiState.value.selectedCategory)
    }

    fun toggleSaveCommunityPost(postId: String) {
        if (postId.isBlank()) return
        val state = _uiState.value
        val nextSaved = if (postId in state.savedCommunityPostIds) {
            state.savedCommunityPostIds - postId
        } else {
            state.savedCommunityPostIds + postId
        }
        _uiState.value = state.copy(
            savedCommunityPostIds = nextSaved,
            toastMessage = if (postId in state.savedCommunityPostIds) "Post removed from saved" else "Post saved",
        )
    }

    fun toggleSaveCommunityEvent(eventId: String) {
        if (eventId.isBlank()) return
        val state = _uiState.value
        val nextSaved = if (eventId in state.savedCommunityEventIds) {
            state.savedCommunityEventIds - eventId
        } else {
            state.savedCommunityEventIds + eventId
        }
        _uiState.value = state.copy(
            savedCommunityEventIds = nextSaved,
            toastMessage = if (eventId in state.savedCommunityEventIds) "Event removed from saved" else "Event saved",
        )
    }

    fun setMutedCommunityKeywords(keywords: Set<String>) {
        val normalized = keywords
            .map { value -> value.trim().lowercase() }
            .filter { value -> value.length >= 2 }
            .toSet()
        _uiState.value = _uiState.value.copy(
            mutedCommunityKeywords = normalized,
            toastMessage = if (normalized.isEmpty()) "Muted keyword list cleared" else "Muted keywords updated",
        )
    }

    fun toggleFollowGroup(groupId: String) {
        if (groupId.isBlank()) return
        val state = _uiState.value
        val nextFollowed = if (groupId in state.followedGroupIds) {
            state.followedGroupIds - groupId
        } else {
            state.followedGroupIds + groupId
        }
        _uiState.value = state.copy(
            followedGroupIds = nextFollowed,
            toastMessage = if (groupId in state.followedGroupIds) "Group alerts off" else "Group alerts on",
        )
    }

    fun setNotificationPreferences(
        followedGroupAlerts: Boolean,
        savedPostUpdates: Boolean,
        safetyAlerts: Boolean,
    ) {
        _uiState.value = _uiState.value.copy(
            notifyFollowedGroupAlerts = followedGroupAlerts,
            notifySavedPostUpdates = savedPostUpdates,
            notifySafetyAlerts = safetyAlerts,
            toastMessage = "Notification preferences updated",
        )
        loadHomeData(_uiState.value.selectedCategory)
    }

    fun selectMessageThread(threadId: String) {
        if (threadId.isBlank()) return
        _uiState.value = _uiState.value.copy(selectedMessageThreadId = threadId)
        viewModelScope.launch {
            runCatching { repository.markThreadRead(threadId) }
            val messages = runCatching {
                repository.loadThreadMessages(
                    threadId = threadId,
                    limit = 200,
                ).map { message -> message.toDirectMessage() }
            }.getOrDefault(emptyList())
            val refreshedThreads = runCatching {
                buildMessageThreadsForState(
                    state = _uiState.value,
                    apiThreads = repository.loadMessageThreads(limit = 50),
                )
            }.getOrElse { _uiState.value.messageThreads }
            _uiState.value = _uiState.value.copy(
                selectedMessageThreadId = threadId.takeIf { id -> refreshedThreads.any { thread -> thread.id == id } },
                directMessages = messages,
                readDirectMessageIds = messages.map { message -> message.id }.toSet(),
                messageThreads = refreshedThreads,
            )
        }
    }

    fun markMessageThreadRead(threadId: String) {
        if (threadId.isBlank()) return
        viewModelScope.launch {
            val markedRead = runCatching { repository.markThreadRead(threadId) }.getOrDefault(false)
            val refreshedThreads = runCatching {
                buildMessageThreadsForState(
                    state = _uiState.value,
                    apiThreads = repository.loadMessageThreads(limit = 50),
                )
            }.getOrElse { _uiState.value.messageThreads }
            val readIds = if (_uiState.value.selectedMessageThreadId == threadId) {
                _uiState.value.directMessages.map { message -> message.id }.toSet()
            } else {
                _uiState.value.readDirectMessageIds
            }
            _uiState.value = _uiState.value.copy(
                readDirectMessageIds = readIds,
                messageThreads = refreshedThreads,
                toastMessage = if (markedRead) "Thread marked read" else "Unable to update read state",
            )
        }
    }

    fun toggleMuteMessageThread(threadId: String) {
        if (threadId.isBlank()) return
        val state = _uiState.value
        val nextMutedIds = if (threadId in state.mutedMessageThreadIds) {
            state.mutedMessageThreadIds - threadId
        } else {
            state.mutedMessageThreadIds + threadId
        }
        val refreshedThreads = applyThreadPresentationFlags(
            threads = state.messageThreads,
            mutedThreadIds = nextMutedIds,
            pinnedThreadIds = state.pinnedMessageThreadIds,
        )
        _uiState.value = state.copy(
            mutedMessageThreadIds = nextMutedIds,
            messageThreads = refreshedThreads,
            toastMessage = if (threadId in state.mutedMessageThreadIds) "Thread unmuted" else "Thread muted",
        )
    }

    fun togglePinMessageThread(threadId: String) {
        if (threadId.isBlank()) return
        val state = _uiState.value
        val nextPinnedIds = if (threadId in state.pinnedMessageThreadIds) {
            state.pinnedMessageThreadIds - threadId
        } else {
            state.pinnedMessageThreadIds + threadId
        }
        val refreshedThreads = applyThreadPresentationFlags(
            threads = state.messageThreads,
            mutedThreadIds = state.mutedMessageThreadIds,
            pinnedThreadIds = nextPinnedIds,
        )
        _uiState.value = state.copy(
            pinnedMessageThreadIds = nextPinnedIds,
            messageThreads = refreshedThreads,
            toastMessage = if (threadId in state.pinnedMessageThreadIds) "Thread unpinned" else "Thread pinned",
        )
    }

    fun clearMessageThreadSelection() {
        val state = _uiState.value
        _uiState.value = state.copy(
            selectedMessageThreadId = null,
            readDirectMessageIds = state.readDirectMessageIds + state.directMessages.map { message -> message.id },
            directMessages = emptyList(),
        )
    }

    fun sendDirectMessage(threadId: String, body: String) {
        val trimmed = body.trim()
        if (trimmed.isBlank()) return
        val state = _uiState.value
        val recipientUserId = state.messageThreads
            .firstOrNull { it.id == threadId }
            ?.participantUserId
            ?: return
        viewModelScope.launch {
            runCatching {
                repository.sendThreadMessage(
                    threadId = threadId,
                    recipientUserId = recipientUserId,
                    body = trimmed,
                )
            }.onSuccess {
                val messages = runCatching {
                    repository.loadThreadMessages(
                        threadId = threadId,
                        limit = 200,
                    ).map { message -> message.toDirectMessage() }
                }.getOrDefault(_uiState.value.directMessages.filter { message -> message.threadId == threadId })
                val refreshedThreads = runCatching {
                    buildMessageThreadsForState(
                        state = _uiState.value,
                        apiThreads = repository.loadMessageThreads(limit = 50),
                    )
                }.getOrElse { _uiState.value.messageThreads }
                _uiState.value = _uiState.value.copy(
                    selectedMessageThreadId = threadId.takeIf { id -> refreshedThreads.any { thread -> thread.id == id } },
                    directMessages = messages,
                    readDirectMessageIds = messages.map { message -> message.id }.toSet(),
                    messageThreads = refreshedThreads,
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(error = error.message)
            }
        }
    }

    fun switchAccount(userId: String) {
        if (userId.isBlank()) return
        viewModelScope.launch {
            val authOk = repository.authenticateAsUser(userId)
            repository.setActiveUser(userId)
            val persistedTestProfileMode = normalizeTestProfileMode(repository.testProfileMode())
            val persistedProfileHeaderVisible = repository.isTestProfileHeaderVisible()
            _uiState.value = _uiState.value.copy(
                activeUserId = userId,
                testProfileMode = persistedTestProfileMode,
                profileIdentityHeaderVisible = persistedProfileHeaderVisible,
                authRequired = false,
                authOtpRequested = false,
                authInviteId = "",
                authEmail = "",
                authOtpExpiresAt = null,
                authInFlight = false,
                selectedMessageThreadId = null,
                profileNotificationFilter = "all",
                readDirectMessageIds = emptySet(),
                latestHomeLoadMetrics = null,
                homeLoadHistory = emptyList(),
                readLocalNotificationIds = emptySet(),
                acknowledgedCommunityNotificationIds = emptySet(),
                acknowledgedMessageNotificationIds = emptySet(),
                notifyFollowedGroupAlerts = true,
                notifySavedPostUpdates = true,
                notifySafetyAlerts = true,
                mutedMessageThreadIds = emptySet(),
                pinnedMessageThreadIds = emptySet(),
                savedCommunityPostIds = emptySet(),
                savedCommunityEventIds = emptySet(),
                mutedCommunityKeywords = emptySet(),
                followedGroupIds = emptySet(),
                friendProfiles = emptyList(),
                isCommunityModerator = userId in COMMUNITY_MODERATOR_IDS,
                toastMessage = if (authOk) "Switched to $userId" else "Switched to $userId (guest auth)",
            )
            applyTestProfileMode(
                mode = persistedTestProfileMode,
                persist = false,
                triggerHomeReload = false,
                showToast = false,
            )
            loadHomeData(_uiState.value.selectedCategory)
        }
    }

    fun logoutCurrentUser() {
        viewModelScope.launch {
            val success = repository.logout()
            val state = _uiState.value
            _uiState.value = state.copy(
                authRequired = requiresOtpAuth(),
                authOtpRequested = false,
                authInviteId = "",
                authEmail = "",
                authOtpExpiresAt = null,
                authInFlight = false,
                providers = emptyList(),
                nearbyPetBusinesses = emptyList(),
                groups = emptyList(),
                posts = emptyList(),
                communityCommentsByPostId = emptyMap(),
                communityEvents = emptyList(),
                ownerBookings = emptyList(),
                providerBookings = emptyList(),
                calendarEvents = emptyList(),
                messageThreads = emptyList(),
                selectedMessageThreadId = null,
                directMessages = emptyList(),
                readDirectMessageIds = emptySet(),
                savedCommunityPostIds = emptySet(),
                savedCommunityEventIds = emptySet(),
                pendingInvite = null,
                latestGroupInvites = emptyMap(),
                friendProfiles = emptyList(),
                notifications = emptyList(),
                readLocalNotificationIds = emptySet(),
                acknowledgedCommunityNotificationIds = emptySet(),
                acknowledgedMessageNotificationIds = emptySet(),
                loading = false,
                error = null,
                toastMessage = if (success) "Signed out" else "Signed out locally",
            )
            if (!requiresOtpAuth()) {
                loadHomeData(_uiState.value.selectedCategory)
            }
        }
    }

    fun deleteCurrentAccount() {
        viewModelScope.launch {
            val state = _uiState.value
            val targetUserId = state.activeUserId.trim().ifBlank { "user_2" }
            _uiState.value = state.copy(loading = true, error = null)
            runCatching { repository.deleteAccount(targetUserId = targetUserId) }
                .onSuccess {
                    val fallbackUserId = "user_2"
                    if (!requiresOtpAuth()) {
                        repository.setActiveUser(fallbackUserId)
                        runCatching { repository.authenticateAsUser(fallbackUserId) }
                    }
                    _uiState.value = state.copy(
                        activeUserId = fallbackUserId,
                        authRequired = requiresOtpAuth(),
                        authOtpRequested = false,
                        authInviteId = "",
                        authEmail = "",
                        authOtpExpiresAt = null,
                        authInFlight = false,
                        providers = emptyList(),
                        nearbyPetBusinesses = emptyList(),
                        groups = emptyList(),
                        posts = emptyList(),
                        communityCommentsByPostId = emptyMap(),
                        communityEvents = emptyList(),
                        ownerBookings = emptyList(),
                        providerBookings = emptyList(),
                        calendarEvents = emptyList(),
                        messageThreads = emptyList(),
                        selectedMessageThreadId = null,
                        directMessages = emptyList(),
                        readDirectMessageIds = emptySet(),
                        savedCommunityPostIds = emptySet(),
                        savedCommunityEventIds = emptySet(),
                        pendingInvite = null,
                        latestGroupInvites = emptyMap(),
                        friendProfiles = emptyList(),
                        notifications = emptyList(),
                        readLocalNotificationIds = emptySet(),
                        acknowledgedCommunityNotificationIds = emptySet(),
                        acknowledgedMessageNotificationIds = emptySet(),
                        loading = false,
                        error = null,
                        isCommunityModerator = fallbackUserId in COMMUNITY_MODERATOR_IDS,
                        toastMessage = "Account deleted",
                    )
                    if (!requiresOtpAuth()) {
                        loadHomeData(_uiState.value.selectedCategory)
                    }
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = error.message ?: "Unable to delete account",
                    )
                }
        }
    }

    fun updateSuburb(suburb: String) {
        if (isStagingTestBuild()) {
            _uiState.value = _uiState.value.copy(
                selectedSuburb = STAGING_TEST_SUBURB,
                selectedRangeCenter = "manual",
                profileInfo = _uiState.value.profileInfo.copy(suburb = STAGING_TEST_SUBURB),
            )
            return
        }
        _uiState.value = _uiState.value.copy(
            selectedSuburb = suburb,
            selectedRangeCenter = "manual",
            profileInfo = _uiState.value.profileInfo.copy(suburb = suburb),
        )
    }

    fun setDetectedLocation(snapshot: LocationSnapshot, applyAsSelected: Boolean) {
        if (isStagingTestBuild()) {
            _uiState.value = _uiState.value.copy(
                selectedSuburb = STAGING_TEST_SUBURB,
                selectedRangeCenter = "manual",
                currentLocationSuburb = snapshot.suburb?.trim()?.ifBlank { null },
                currentLatitude = snapshot.latitude,
                currentLongitude = snapshot.longitude,
                locationAutoDetected = true,
                profileInfo = _uiState.value.profileInfo.copy(suburb = STAGING_TEST_SUBURB),
            )
            return
        }
        val detectedSuburb = snapshot.suburb?.trim().orEmpty()
        if (!applyAsSelected && _uiState.value.locationAutoDetected) {
            val shouldRefreshForCurrentRange = _uiState.value.selectedRangeCenter == "current"
            _uiState.value = _uiState.value.copy(
                selectedSuburb = if (shouldRefreshForCurrentRange && detectedSuburb.isNotBlank()) {
                    detectedSuburb
                } else {
                    _uiState.value.selectedSuburb
                },
                currentLocationSuburb = detectedSuburb.ifBlank { null },
                currentLatitude = snapshot.latitude,
                currentLongitude = snapshot.longitude,
                profileInfo = if (shouldRefreshForCurrentRange && detectedSuburb.isNotBlank()) {
                    _uiState.value.profileInfo.copy(suburb = detectedSuburb)
                } else {
                    _uiState.value.profileInfo
                },
            )
            maybeRunAutoParkCheckIn(reason = "location_update")
            if (shouldRefreshForCurrentRange) {
                loadHomeData(_uiState.value.selectedCategory)
            }
            return
        }
        _uiState.value = _uiState.value.copy(
            selectedSuburb = detectedSuburb.ifBlank { _uiState.value.selectedSuburb },
            selectedRangeCenter = "current",
            currentLocationSuburb = detectedSuburb.ifBlank { null },
            currentLatitude = snapshot.latitude,
            currentLongitude = snapshot.longitude,
            locationAutoDetected = true,
            profileInfo = if (detectedSuburb.isBlank()) {
                _uiState.value.profileInfo
            } else {
                _uiState.value.profileInfo.copy(suburb = detectedSuburb)
            },
        )
        maybeRunAutoParkCheckIn(reason = "location_detected")
        loadHomeData(_uiState.value.selectedCategory)
    }

    fun refreshCommunityWeather() {
        refreshMockCommunityWeather()
    }

    fun setAutoParkCheckInEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            autoParkCheckInEnabled = enabled,
            toastMessage = if (enabled) {
                "Auto check-in enabled. Shares only with your local member group."
            } else {
                "Auto check-in disabled"
            },
        )
        if (enabled) {
            maybeRunAutoParkCheckIn(reason = "enabled")
        }
    }

    fun setAutoParkCheckInRequireCrowd(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            autoParkCheckInRequireCrowd = enabled,
            toastMessage = if (enabled) {
                "Safety mode on: only share when group activity is present"
            } else {
                "Safety mode off: share on park arrival"
            },
        )
    }

    fun simulateParkArrivalCheckIn() {
        val state = _uiState.value
        val simulatedSuburb = state.currentLocationSuburb ?: state.selectedSuburb
        _uiState.value = state.copy(
            currentLocationSuburb = simulatedSuburb,
            locationAutoDetected = true,
        )
        maybeRunAutoParkCheckIn(reason = "manual_test")
    }

    fun setRangeCenterCurrent(enabled: Boolean) {
        if (isStagingTestBuild()) {
            _uiState.value = _uiState.value.copy(selectedRangeCenter = "manual")
            loadHomeData(_uiState.value.selectedCategory)
            return
        }
        _uiState.value = _uiState.value.copy(
            selectedRangeCenter = if (enabled) "current" else "manual",
        )
        loadHomeData(_uiState.value.selectedCategory)
    }

    fun saveProfileInfo(profileInfo: ProfileInfo) {
        val normalized = if (isStagingTestBuild()) {
            profileInfo.copy(suburb = STAGING_TEST_SUBURB)
        } else {
            profileInfo
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching {
                repository.saveUserProfile(
                    displayName = normalized.displayName,
                    email = normalized.email,
                    phone = normalized.phone,
                    humanPronouns = normalized.humanPronouns,
                    humanRoleLabel = normalized.humanRoleLabel,
                    dogName = normalized.dogName,
                    dogAgeMonths = normalized.dogAgeMonths,
                    dogBreedMix = normalized.dogBreedMix,
                    dogSexNeuter = normalized.dogGender,
                    dogWeightClass = normalized.dogWeightKg,
                    dogPhotoUrls = normalized.dogPhotoUrls,
                    secondaryDogName = normalized.secondaryDogName,
                    secondaryDogAgeMonths = normalized.secondaryDogAgeMonths,
                    secondaryDogPhotoUrl = "",
                    secondaryDogGender = normalized.secondaryDogGender,
                    secondaryDogWeightKg = normalized.secondaryDogWeightKg,
                    bio = normalized.bio,
                    suburb = normalized.suburb,
                    favoriteSuburbs = normalized.favoriteSuburbs,
                    playEnergyLevel = normalized.playEnergyLevel,
                    playStyle = normalized.playStyle,
                    socialConfidence = normalized.socialConfidence,
                    triggerNotes = normalized.triggerNotes,
                    idealMatch = normalized.idealMatch,
                    walkPreferences = normalized.walkPreferences,
                    trainingStyle = normalized.trainingStyle,
                    feedingRules = normalized.feedingRules,
                    consentBoundaries = normalized.consentBoundaries,
                    vaccinationStatus = normalized.vaccinationStatus,
                    microchipped = normalized.microchipped,
                    recallTrained = normalized.recallTrained,
                    leashReliability = normalized.leashReliability,
                    emergencyContactName = normalized.emergencyContactName,
                    emergencyContactPhone = normalized.emergencyContactPhone,
                    fieldVisibility = normalized.fieldVisibility,
                )
            }.onSuccess { response ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    profileInfo = response.toProfileInfo(
                        activeUserId = _uiState.value.activeUserId,
                        fallbackProfile = normalized,
                        fallbackSuburb = normalized.suburb,
                    ),
                    selectedSuburb = if (isStagingTestBuild()) STAGING_TEST_SUBURB else normalized.suburb,
                    selectedRangeCenter = if (isStagingTestBuild()) "manual" else _uiState.value.selectedRangeCenter,
                    toastMessage = "Profile updated",
                )
                loadHomeData(_uiState.value.selectedCategory)
            }.onFailure { error ->
                val statusCode = (error as? HttpException)?.code()
                if (statusCode == 401 || statusCode == 403) {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        authRequired = true,
                        authInFlight = false,
                        error = "Sign in required to save profile",
                        toastMessage = "Session expired. Sign in again, then tap Save profile.",
                    )
                    return@onFailure
                }
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = error.message ?: "Unable to save profile",
                    toastMessage = "Could not save profile. Please retry.",
                )
            }
        }
    }

    fun setServiceProviderMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            isServiceProvider = enabled,
            toastMessage = if (enabled) "Listing profile enabled" else "Listing profile disabled",
        )
    }

    fun requestBookingEdit(bookingId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching { repository.requestOwnerBookingReschedule(bookingId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        toastMessage = "Reschedule request sent to provider",
                    )
                    loadHomeData(_uiState.value.selectedCategory)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = error.message,
                    )
                }
        }
    }

    fun cancelOwnerBooking(bookingId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching { repository.cancelOwnerBooking(bookingId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(loading = false, toastMessage = "Booking cancelled")
                    loadHomeData(_uiState.value.selectedCategory)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(loading = false, error = error.message)
                }
        }
    }

    fun leaveEvent(eventId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching { repository.rsvpCommunityEvent(eventId = eventId, attending = false) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        toastMessage = "Event removed from your profile",
                    )
                    loadHomeData(_uiState.value.selectedCategory)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = error.message,
                    )
                }
        }
    }

    fun removeFavourite(providerId: String) {
        _uiState.value = _uiState.value.copy(
            favoriteProviderIds = _uiState.value.favoriteProviderIds.filterNot { id -> id == providerId },
            toastMessage = "Removed from favourites",
        )
    }

    fun editProviderListing(
        listingId: String,
        name: String,
        description: String,
        priceFrom: Int,
        imageUrls: List<String> = emptyList(),
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            val state = _uiState.value
            runCatching {
                repository.updateServiceProvider(
                    providerId = listingId,
                    name = name,
                    description = description,
                    priceFrom = priceFrom,
                    fullDescription = description,
                    imageUrls = imageUrls,
                    latitude = state.currentLatitude,
                    longitude = state.currentLongitude,
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    toastMessage = "Listing updated",
                )
                loadHomeData(_uiState.value.selectedCategory)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(loading = false, error = error.message)
            }
        }
    }

    fun cancelProviderListing(listingId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            val cancelled = repository.cancelServiceProvider(listingId)
            if (cancelled) {
                _uiState.value = _uiState.value.copy(loading = false, toastMessage = "Listing cancelled")
                loadHomeData(_uiState.value.selectedCategory)
            } else {
                _uiState.value = _uiState.value.copy(loading = false, error = "Failed to cancel listing")
            }
        }
    }

    fun restoreProviderListing(listingId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching { repository.restoreServiceProvider(listingId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(loading = false, toastMessage = "Listing restored")
                    loadHomeData(_uiState.value.selectedCategory)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(loading = false, error = error.message)
                }
        }
    }

    fun createProviderListing(
        name: String,
        category: String,
        suburb: String,
        description: String,
        priceFrom: Int,
        imageUrls: List<String> = emptyList(),
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            val state = _uiState.value
            runCatching {
                repository.createServiceProvider(
                    name = name,
                    category = category,
                    suburb = suburb,
                    description = description,
                    priceFrom = priceFrom,
                    fullDescription = description,
                    imageUrls = imageUrls,
                    latitude = state.currentLatitude,
                    longitude = state.currentLongitude,
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    selectedTab = AppTab.Services,
                    toastMessage = "Listing created",
                )
                loadHomeData(_uiState.value.selectedCategory)
            }.onFailure { error ->
                val isMethodNotAllowed = (error as? HttpException)?.code() == 405 ||
                    (error.message?.contains("HTTP 405", ignoreCase = true) == true)
                val shouldLocalFallback = isMethodNotAllowed || BuildConfig.ENVIRONMENT.lowercase() == "staging"
                if (shouldLocalFallback) {
                    val localId = "local_provider_${System.currentTimeMillis()}"
                    val ownerUserId = state.activeUserId
                    val localProvider = ServiceProvider(
                        id = localId,
                        name = name,
                        category = category,
                        suburb = suburb,
                        rating = 5.0,
                        reviewCount = 0,
                        priceFrom = priceFrom,
                        description = description,
                        fullDescription = description,
                        imageUrls = imageUrls,
                        latitude = state.currentLatitude ?: 0.0,
                        longitude = state.currentLongitude ?: 0.0,
                        ownerUserId = ownerUserId,
                        ownerLabel = accountLabel(ownerUserId),
                        status = "active",
                    )
                    val categoryMatches = state.selectedCategory.isNullOrBlank() || state.selectedCategory == category
                    val updatedProviders = if (categoryMatches) listOf(localProvider) + state.providers else state.providers
                    val updatedListings = listOf(
                        ProviderListing(
                            id = localProvider.id,
                            title = localProvider.name,
                            category = localProvider.category.replace("_", " "),
                            status = localProvider.status,
                            priceFrom = localProvider.priceFrom,
                            description = localProvider.description,
                            suburb = localProvider.suburb,
                            imageUrls = localProvider.imageUrls,
                        ),
                    ) + state.providerListings
                    _uiState.value = state.copy(
                        loading = false,
                        selectedTab = AppTab.Services,
                        providers = updatedProviders,
                        providerListings = updatedListings,
                        isServiceProvider = true,
                        hasPendingSync = true,
                        toastMessage = "Listing created locally (sync pending)",
                        error = null,
                    )
                } else {
                    _uiState.value = _uiState.value.copy(loading = false, error = error.message)
                }
            }
        }
    }

    fun saveProviderConfig(availableTimeSlots: String, preferredSuburbs: String) {
        _uiState.value = _uiState.value.copy(
            providerConfig = ProviderConfig(
                availableTimeSlots = availableTimeSlots,
                preferredSuburbs = preferredSuburbs,
            ),
            toastMessage = "Booking configuration updated",
        )
    }

    fun loadProviderBlackouts(providerId: String, forceRefresh: Boolean = false) {
        if (providerId.isBlank()) return
        val cached = _uiState.value.providerBlackoutsByProvider[providerId]
        if (!forceRefresh && cached != null) return
        viewModelScope.launch {
            runCatching { repository.loadProviderBlackouts(providerId) }
                .onSuccess { blackouts ->
                    _uiState.value = _uiState.value.copy(
                        providerBlackoutsByProvider = _uiState.value.providerBlackoutsByProvider + (providerId to blackouts),
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(error = error.message)
                }
        }
    }

    fun createProviderBlackout(
        providerId: String,
        date: String,
        timeSlot: String,
        reason: String = "",
    ) {
        if (providerId.isBlank()) return
        val normalizedDate = date.trim()
        val normalizedSlot = timeSlot.trim()
        if (normalizedDate.isBlank() || normalizedSlot.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching {
                repository.createProviderBlackout(
                    providerId = providerId,
                    date = normalizedDate,
                    timeSlot = normalizedSlot,
                    reason = reason.trim(),
                )
            }.onSuccess { blackout ->
                val current = _uiState.value.providerBlackoutsByProvider[providerId].orEmpty()
                val merged = (current + blackout)
                    .distinctBy { row -> row.id }
                    .sortedWith(compareBy<ProviderBlackout> { row -> row.date }.thenBy { row -> row.timeSlot })
                _uiState.value = _uiState.value.copy(
                    providerBlackoutsByProvider = _uiState.value.providerBlackoutsByProvider + (providerId to merged),
                    loading = false,
                    toastMessage = "Availability updated: slot blocked",
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = error.message,
                )
            }
        }
    }

    fun loadBookingStatusHistory(bookingId: String, forceRefresh: Boolean = false) {
        if (bookingId.isBlank()) return
        if (!forceRefresh && _uiState.value.bookingHistoryByBookingId[bookingId] != null) return
        if (bookingId in _uiState.value.loadingBookingHistoryIds) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                loadingBookingHistoryIds = _uiState.value.loadingBookingHistoryIds + bookingId,
            )
            runCatching { repository.loadBookingStatusHistory(bookingId) }
                .onSuccess { history ->
                    _uiState.value = _uiState.value.copy(
                        bookingHistoryByBookingId = _uiState.value.bookingHistoryByBookingId + (
                            bookingId to history.sortedBy { entry -> entry.createdAt }
                            ),
                        loadingBookingHistoryIds = _uiState.value.loadingBookingHistoryIds - bookingId,
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        loadingBookingHistoryIds = _uiState.value.loadingBookingHistoryIds - bookingId,
                        error = error.message,
                    )
                }
        }
    }

    fun loadProviderRescheduleAvailability(
        providerId: String,
        date: String,
        forceRefresh: Boolean = false,
    ) {
        val normalizedProviderId = providerId.trim()
        val normalizedDate = date.trim()
        if (normalizedProviderId.isBlank() || normalizedDate.isBlank()) return
        val key = "$normalizedProviderId|$normalizedDate"
        if (!forceRefresh && _uiState.value.providerRescheduleSlotsByKey[key] != null) return
        if (key in _uiState.value.loadingProviderRescheduleKeys) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                loadingProviderRescheduleKeys = _uiState.value.loadingProviderRescheduleKeys + key,
            )
            runCatching { repository.loadProviderAvailability(normalizedProviderId, normalizedDate) }
                .onSuccess { slots ->
                    _uiState.value = _uiState.value.copy(
                        providerRescheduleSlotsByKey = _uiState.value.providerRescheduleSlotsByKey + (
                            key to slots
                            ),
                        loadingProviderRescheduleKeys = _uiState.value.loadingProviderRescheduleKeys - key,
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        loadingProviderRescheduleKeys = _uiState.value.loadingProviderRescheduleKeys - key,
                        error = error.message,
                    )
                }
        }
    }

    fun cancelProviderBooking(bookingId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching { repository.cancelProviderBooking(bookingId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(loading = false, toastMessage = "Provider booking cancelled")
                    loadHomeData(_uiState.value.selectedCategory)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(loading = false, error = error.message)
                }
        }
    }

    fun confirmProviderBooking(bookingId: String) {
        val state = _uiState.value
        val ownerUserId = state.providerBookings.firstOrNull { it.id == bookingId }?.ownerUserId
        val followUpHint = nextActionSwitchHint(
            targetUserId = ownerUserId,
            activeUserId = state.activeUserId,
            actionText = "continue as the owner",
        )
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching { repository.confirmProviderBooking(bookingId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        toastMessage = "Provider booking confirmed.$followUpHint",
                    )
                    loadHomeData(_uiState.value.selectedCategory)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(loading = false, error = error.message)
                }
        }
    }

    fun rescheduleProviderBooking(
        bookingId: String,
        date: String,
        timeSlot: String,
        note: String = "",
    ) {
        val normalizedDate = date.trim()
        val normalizedTimeSlot = timeSlot.trim()
        if (bookingId.isBlank() || normalizedDate.isBlank() || normalizedTimeSlot.isBlank()) return
        val state = _uiState.value
        val ownerUserId = state.providerBookings.firstOrNull { it.id == bookingId }?.ownerUserId
        val followUpHint = nextActionSwitchHint(
            targetUserId = ownerUserId,
            activeUserId = state.activeUserId,
            actionText = "review this updated booking time",
        )
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching {
                repository.rescheduleProviderBooking(
                    bookingId = bookingId,
                    date = normalizedDate,
                    timeSlot = normalizedTimeSlot,
                    note = note.trim(),
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    toastMessage = "Booking rescheduled.$followUpHint",
                )
                loadHomeData(_uiState.value.selectedCategory)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = error.message,
                )
            }
        }
    }

    fun loadProviderDetails(providerId: String) {
        val today = LocalDate.now().toString()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching {
                Pair(
                    repository.loadProviderDetails(providerId),
                    repository.loadProviderAvailability(providerId, today),
                )
            }.onSuccess { (details, slots) ->
                    _uiState.value = _uiState.value.copy(
                        selectedProviderDetails = details,
                        availableSlots = slots,
                        availabilityDate = today,
                        loading = false,
                    )
                }
                .onFailure { error -> _uiState.value = _uiState.value.copy(error = error.message, loading = false) }
        }
    }

    fun loadAvailability(providerId: String, date: String) {
        viewModelScope.launch {
            runCatching { repository.loadProviderAvailability(providerId, date) }
                .onSuccess { slots ->
                    _uiState.value = _uiState.value.copy(
                        availableSlots = slots,
                        availabilityDate = date,
                    )
                }
                .onFailure { error -> _uiState.value = _uiState.value.copy(error = error.message) }
        }
    }

    fun updateServiceFilters(minRating: Float?, maxDistanceKm: Int?) {
        if (
            _uiState.value.serviceMinRating == minRating &&
            _uiState.value.serviceMaxDistanceKm == maxDistanceKm
        ) return
        _uiState.value = _uiState.value.copy(serviceMinRating = minRating, serviceMaxDistanceKm = maxDistanceKm)
        loadHomeData(_uiState.value.selectedCategory)
    }

    fun closeProviderDetails() {
        _uiState.value = _uiState.value.copy(
            selectedProviderDetails = null,
            availableSlots = emptyList(),
            availabilityDate = null,
        )
    }

    fun sendChat(message: String) {
        val trimmedMessage = message.trim()
        if (trimmedMessage.isBlank()) return
        if (isOnboardingScriptEnabled() && _uiState.value.onboardingActive) {
            handleOnboardingTextReply(trimmedMessage)
            return
        }
        if (tryHandleLocalServicesIntent(trimmedMessage)) return
        val state = _uiState.value
        val suburb = state.selectedSuburb
        val selectedThreadId = state.selectedBarkThreadId
        val activeThread = state.barkThreads.firstOrNull { it.id == selectedThreadId } ?: state.barkThreads.first()
        val nextConversation = activeThread.conversation + ChatTurn(role = "user", content = trimmedMessage)
        val nextThread = activeThread.copy(
            title = resolveBarkThreadTitle(activeThread.title, nextConversation),
            conversation = nextConversation,
            updatedAt = System.currentTimeMillis(),
        )
        val nextThreads = upsertBarkThread(state.barkThreads, nextThread)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                loading = true,
                error = null,
                selectedTab = AppTab.BarkAI,
                streamingAssistantText = "",
                selectedBarkThreadId = nextThread.id,
                barkThreads = nextThreads,
                conversation = nextConversation,
            )
            runCatching {
                repository.streamChat(
                    message = trimmedMessage,
                    suburb = suburb,
                    onDelta = { delta ->
                        _uiState.value = _uiState.value.copy(
                            streamingAssistantText = _uiState.value.streamingAssistantText + delta,
                        )
                    },
                )
            }.onSuccess { applyChatResponse(it) }
                .onFailure { error ->
                    applyChatResponse(
                        buildFallbackChatResponse(
                            userMessage = trimmedMessage,
                            priorConversation = nextConversation,
                        ),
                        toast = "BarkAI network issue: using offline guidance",
                    )
                    _uiState.value = _uiState.value.copy(error = null)
                }
        }
    }

    fun submitOnboardingPhotoCapture(photoCaptured: Boolean, dogPhotoUri: String? = null) {
        val state = _uiState.value
        if (!isOnboardingScriptEnabled() || !state.onboardingActive || state.onboardingStep < 2) return

        val userTurn = ChatTurn(
            role = "user",
            content = if (photoCaptured) "Shared a dog photo from camera." else "Tried to share a dog photo.",
        )
        if (!photoCaptured) {
            val retryPrompt = "I couldn't read that photo. Tap Camera and try once more."
            applyOnboardingConversationUpdate(
                conversation = state.conversation + userTurn + onboardingAssistantTurn(retryPrompt),
                latestAssistantAnswer = retryPrompt,
                onboardingStep = 2,
                onboardingActive = true,
                ownerName = state.onboardingOwnerName,
                dogName = state.onboardingDogName,
                photoCaptured = false,
                dogPhotoUri = null,
            )
            return
        }

        val cuteLine = pickOnboardingVariation(ONBOARD_CUTE_VARIATIONS)
        val thanksLine = pickOnboardingVariation(ONBOARD_THANKS_VARIATIONS)
        val completion = "$cuteLine $thanksLine You're all set."
        applyOnboardingConversationUpdate(
            conversation = state.conversation + userTurn + onboardingAssistantTurn(completion),
            latestAssistantAnswer = completion,
            onboardingStep = 3,
            onboardingActive = false,
            ownerName = state.onboardingOwnerName,
            dogName = state.onboardingDogName,
            photoCaptured = true,
            dogPhotoUri = dogPhotoUri,
            toastMessage = "Onboarding complete",
        )
        loadHomeData(_uiState.value.selectedCategory, allowAuthRetry = false)
    }

    private fun handleOnboardingTextReply(message: String) {
        val state = _uiState.value
        val userTurn = ChatTurn(role = "user", content = message)
        when (state.onboardingStep) {
            0 -> {
                val dogNameQuestion = pickOnboardingVariation(ONBOARD_DOG_NAME_QUESTIONS)
                applyOnboardingConversationUpdate(
                    conversation = state.conversation + userTurn + onboardingAssistantTurn(dogNameQuestion),
                    latestAssistantAnswer = dogNameQuestion,
                    onboardingStep = 1,
                    onboardingActive = true,
                    ownerName = message,
                    dogName = "",
                    photoCaptured = false,
                    dogPhotoUri = null,
                )
            }
            1 -> {
                val photoQuestion = pickOnboardingVariation(ONBOARD_DOG_PHOTO_QUESTIONS)
                applyOnboardingConversationUpdate(
                    conversation = state.conversation + userTurn + onboardingAssistantTurn(photoQuestion),
                    latestAssistantAnswer = photoQuestion,
                    onboardingStep = 2,
                    onboardingActive = true,
                    ownerName = state.onboardingOwnerName,
                    dogName = message,
                    photoCaptured = false,
                    dogPhotoUri = null,
                )
            }
            else -> {
                val cameraReminder = "Tap the Camera button below so I can see your dog."
                applyOnboardingConversationUpdate(
                    conversation = state.conversation + userTurn + onboardingAssistantTurn(cameraReminder),
                    latestAssistantAnswer = cameraReminder,
                    onboardingStep = 2,
                    onboardingActive = true,
                    ownerName = state.onboardingOwnerName,
                    dogName = state.onboardingDogName,
                    photoCaptured = false,
                    dogPhotoUri = null,
                )
            }
        }
    }

    private fun applyOnboardingConversationUpdate(
        conversation: List<ChatTurn>,
        latestAssistantAnswer: String,
        onboardingStep: Int,
        onboardingActive: Boolean,
        ownerName: String,
        dogName: String,
        photoCaptured: Boolean,
        dogPhotoUri: String? = null,
        toastMessage: String? = null,
    ) {
        val state = _uiState.value
        val selectedThread = state.barkThreads.firstOrNull { it.id == state.selectedBarkThreadId } ?: BarkThread(
            id = ONBOARD_THREAD_ID,
            title = "Onboarding",
        )
        val response = onboardingChatResponse(
            answer = latestAssistantAnswer,
            conversation = conversation,
        )
        val updatedThread = selectedThread.copy(
            title = if (onboardingActive) "Onboarding" else "Onboarding complete",
            conversation = conversation,
            chat = response,
            updatedAt = System.currentTimeMillis(),
        )
        _uiState.value = state.copy(
            selectedTab = AppTab.BarkAI,
            chat = response,
            conversation = conversation,
            barkThreads = upsertBarkThread(state.barkThreads, updatedThread),
            selectedBarkThreadId = updatedThread.id,
            profileInfo = updateOnboardingProfileInfo(
                profile = state.profileInfo,
                ownerName = ownerName,
                dogName = dogName,
                dogPhotoUri = dogPhotoUri,
            ),
            onboardingStep = onboardingStep,
            onboardingActive = onboardingActive,
            onboardingOwnerName = ownerName,
            onboardingDogName = dogName,
            onboardingPhotoCaptured = photoCaptured,
            authRequired = false,
            authOtpRequested = false,
            authInviteId = "",
            authEmail = "",
            authOtpExpiresAt = null,
            authInFlight = false,
            loading = false,
            streamingAssistantText = "",
            error = null,
            toastMessage = toastMessage,
        )
    }

    private fun buildFallbackChatResponse(
        userMessage: String,
        priorConversation: List<ChatTurn>,
    ): ChatResponse {
        val lower = userMessage.lowercase()
        val vaccineTerms = listOf("vaccine", "vaccination", "booster", "immunization", "shot", "shots")
        val answer = if (vaccineTerms.any { lower.contains(it) }) {
            "I could not reach BarkAI right now, but here is a safe starting point: core vaccines " +
                "for dogs are typically planned by age and risk profile, with boosters scheduled over time. " +
                "Bring your dog's age, prior records, lifestyle, and travel plans to your regular vet to " +
                "finalize the exact schedule. If your dog has vomiting, breathing trouble, facial swelling, " +
                "or collapse after any vaccine, seek urgent in-person care immediately."
        } else {
            "I could not reach BarkAI right now. Please retry in a moment, or ask me to open nearby " +
                "walkers, groomers, bookings, or community groups while chat reconnects."
        }
        return ChatResponse(
            answer = answer,
            conversation = priorConversation + ChatTurn(role = "assistant", content = answer),
            answerSource = "fallback",
            answerBadges = listOf("Offline Fallback"),
        )
    }

    private fun tryHandleLocalServicesIntent(message: String): Boolean {
        val normalized = message.lowercase()
        val category = when {
            "groom" in normalized || "groomer" in normalized -> "grooming"
            "walk" in normalized || "walker" in normalized -> "dog_walking"
            else -> null
        }
        val isSearchIntent = listOf("find", "show", "near", "within", "search", "look for").any {
            normalized.contains(it)
        }
        if (!isSearchIntent || category == null) return false

        val distanceKm = Regex("""\bwithin\s+(\d{1,3})\s*km\b""")
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.coerceIn(1, 50)

        _uiState.value = _uiState.value.copy(
            selectedTab = AppTab.Services,
            selectedCategory = category,
            serviceMaxDistanceKm = distanceKm ?: _uiState.value.serviceMaxDistanceKm,
            servicesViewMode = "list",
            conversation = _uiState.value.conversation + ChatTurn(role = "user", content = message) +
                ChatTurn(
                    role = "assistant",
                    content = buildString {
                        append("Opened Listings for ")
                        append(if (category == "grooming") "groomers" else "dog walkers")
                        distanceKm?.let { append(" within $it km") }
                        append(".")
                    },
                ),
            toastMessage = "Applied BarkAI search to Listings",
        )
        loadHomeData(category = category)
        return true
    }

    fun requestBooking(providerId: String, date: String, timeSlot: String, note: String) {
        val state = _uiState.value
        val providerOwnerUserId = state.providers.firstOrNull { it.id == providerId }?.ownerUserId
        val approvalHint = nextActionSwitchHint(
            targetUserId = providerOwnerUserId,
            activeUserId = state.activeUserId,
            actionText = "approve this booking",
        )
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching {
                repository.createBookingHold(providerId = providerId, date = date, timeSlot = timeSlot)
                repository.requestBooking(providerId, date, timeSlot, note)
            }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        toastMessage = "Booking requested: ${it.id}.$approvalHint",
                    )
                    loadHomeData(_uiState.value.selectedCategory)
                    loadAvailability(providerId, date)
                }
                .onFailure { error -> _uiState.value = _uiState.value.copy(error = error.message, loading = false) }
        }
    }

    fun requestQuote(category: String, preferredWindow: String, petDetails: String, note: String) {
        val state = _uiState.value
        val cleanedCategory = category.trim()
        val cleanedWindow = preferredWindow.trim()
        val cleanedPetDetails = petDetails.trim()
        if (cleanedCategory.isBlank() || cleanedWindow.isBlank() || cleanedPetDetails.isBlank()) {
            _uiState.value = _uiState.value.copy(toastMessage = "Complete category, preferred window, and pet details")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching {
                repository.requestServiceQuote(
                    category = cleanedCategory,
                    suburb = if (isStagingTestBuild()) state.selectedSuburb else null,
                    preferredWindow = cleanedWindow,
                    petDetails = cleanedPetDetails,
                    note = note.trim(),
                )
            }.onSuccess { result ->
                val targetCount = result.targets.size
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    toastMessage = "Quote sent to $targetCount provider(s). +1 Local Scout XP",
                )
                loadHomeData(_uiState.value.selectedCategory)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(loading = false, error = error.message)
            }
        }
    }

    fun setCalendarRole(role: String) {
        _uiState.value = _uiState.value.copy(selectedCalendarRole = role)
        loadHomeData(_uiState.value.selectedCategory)
    }

    fun createCommunityGroup(name: String, suburbOverride: String? = null) {
        if (name.isBlank()) return
        val suburb = suburbOverride?.trim()?.ifBlank { null } ?: _uiState.value.selectedSuburb
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching { repository.createCommunityGroup(name.trim(), suburb) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(loading = false, toastMessage = "Group created")
                    loadHomeData(_uiState.value.selectedCategory)
                }
                .onFailure { error -> _uiState.value = _uiState.value.copy(loading = false, error = error.message) }
        }
    }

    fun createCommunityEvent(
        title: String,
        description: String,
        date: String,
        groupId: String? = null,
        locationName: String? = null,
        locationLatitude: Double? = null,
        locationLongitude: Double? = null,
        recurrence: String = "none",
        recurrenceInterval: Int = 1,
    ) {
        if (title.isBlank() || date.isBlank()) return
        val suburb = _uiState.value.selectedSuburb
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching {
                repository.createCommunityEvent(
                    title = title.trim(),
                    description = description.trim(),
                    suburb = suburb,
                    date = date.trim(),
                    groupId = groupId,
                    locationName = locationName?.trim()?.ifBlank { null },
                    locationLatitude = locationLatitude,
                    locationLongitude = locationLongitude,
                    recurrence = recurrence,
                    recurrenceInterval = recurrenceInterval,
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    toastMessage = "Event created",
                )
                loadHomeData(_uiState.value.selectedCategory)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(loading = false, error = error.message)
            }
        }
    }

    fun updateCommunityEvent(
        eventId: String,
        title: String,
        description: String,
        date: String,
        groupId: String? = null,
        locationName: String? = null,
        locationLatitude: Double? = null,
        locationLongitude: Double? = null,
        clearLocation: Boolean = false,
        recurrence: String = "none",
        recurrenceInterval: Int = 1,
    ) {
        if (eventId.isBlank() || title.isBlank() || date.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching {
                repository.updateCommunityEvent(
                    eventId = eventId,
                    title = title.trim(),
                    description = description.trim(),
                    date = date.trim(),
                    groupId = groupId,
                    locationName = locationName?.trim()?.ifBlank { null },
                    locationLatitude = locationLatitude,
                    locationLongitude = locationLongitude,
                    clearLocation = clearLocation,
                    recurrence = recurrence,
                    recurrenceInterval = recurrenceInterval,
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    toastMessage = "Event updated",
                )
                loadHomeData(_uiState.value.selectedCategory)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(loading = false, error = error.message)
            }
        }
    }

    fun rsvpEvent(eventId: String, attending: Boolean) {
        val state = _uiState.value
        val eventCreatorUserId = state.communityEvents.firstOrNull { it.id == eventId }?.createdBy
        val approvalHint = nextActionSwitchHint(
            targetUserId = eventCreatorUserId,
            activeUserId = state.activeUserId,
            actionText = "review this RSVP",
        )
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching { repository.rsvpCommunityEvent(eventId = eventId, attending = attending) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        toastMessage = if (attending) "RSVP submitted.$approvalHint" else "RSVP removed",
                    )
                    loadHomeData(_uiState.value.selectedCategory)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(loading = false, error = error.message)
                }
        }
    }

    fun approveEvent(eventId: String) {
        val state = _uiState.value
        val eventCreatorUserId = state.communityEvents.firstOrNull { it.id == eventId }?.createdBy
        val followUpHint = nextActionSwitchHint(
            targetUserId = eventCreatorUserId,
            activeUserId = state.activeUserId,
            actionText = "continue as the event creator",
        )
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching { repository.approveCommunityEvent(eventId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        toastMessage = "Event approved.$followUpHint",
                    )
                    loadHomeData(_uiState.value.selectedCategory)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(loading = false, error = error.message)
                }
        }
    }

    fun joinGroup(groupId: String) {
        val state = _uiState.value
        val groupOwnerUserId = state.groups.firstOrNull { it.id == groupId }?.ownerUserId
        val approvalHint = nextActionSwitchHint(
            targetUserId = groupOwnerUserId,
            activeUserId = state.activeUserId,
            actionText = "approve this join request",
        )
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching { repository.applyJoinGroup(groupId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        toastMessage = "Join request submitted.$approvalHint",
                    )
                    loadHomeData(_uiState.value.selectedCategory)
                }
                .onFailure { error -> _uiState.value = _uiState.value.copy(loading = false, error = error.message) }
        }
    }

    fun approveNextJoinRequest(groupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching {
                val requests = repository.loadPendingJoinRequests(groupId)
                val next = requests.firstOrNull() ?: error("No pending requests")
                repository.approveJoinRequest(groupId = groupId, memberUserId = next.userId)
                next.userId
            }.onSuccess { requesterUserId ->
                val followUpHint = nextActionSwitchHint(
                    targetUserId = requesterUserId,
                    activeUserId = _uiState.value.activeUserId,
                    actionText = "continue as the requester",
                )
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    toastMessage = "Approved one join request.$followUpHint",
                )
                loadHomeData(_uiState.value.selectedCategory)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(loading = false, error = error.message)
            }
        }
    }

    fun rejectNextJoinRequest(groupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching {
                val requests = repository.loadPendingJoinRequests(groupId)
                val next = requests.firstOrNull() ?: error("No pending requests")
                repository.rejectJoinRequest(groupId = groupId, memberUserId = next.userId)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(loading = false, toastMessage = "Rejected one join request")
                loadHomeData(_uiState.value.selectedCategory)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(loading = false, error = error.message)
            }
        }
    }

    fun logGroupCleanupCheckIn(groupId: String) {
        if (groupId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching {
                repository.participateGroupChallenge(
                    groupId = groupId,
                    challengeType = "clean_park_streak",
                    contributionCount = 1,
                    note = "Cleanup check-in from app",
                )
            }.onSuccess { result ->
                val unlocked = if (result.unlockedBadges.isNotEmpty()) {
                    " Unlocked: ${result.unlockedBadges.joinToString(", ")}."
                } else {
                    ""
                }
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    toastMessage = "Cleanup logged (${result.myContributionCount}/${result.challenge.targetCount}).$unlocked",
                )
                loadHomeData(_uiState.value.selectedCategory)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(loading = false, error = error.message)
            }
        }
    }

    fun handleCta(cta: ChatCta) {
        when (cta.action) {
            "find_dog_walkers" -> {
                _uiState.value = _uiState.value.copy(selectedTab = AppTab.Services)
                loadHomeData(category = "dog_walking")
            }

            "find_groomers" -> {
                _uiState.value = _uiState.value.copy(selectedTab = AppTab.Services)
                loadHomeData(category = "grooming")
            }

            "open_services" -> {
                val category = cta.payload.readString("category")
                _uiState.value = _uiState.value.copy(selectedTab = AppTab.Services)
                loadHomeData(category)
            }

            "open_community" -> {
                _uiState.value = _uiState.value.copy(selectedTab = AppTab.Community)
                loadHomeData(_uiState.value.selectedCategory)
            }

            "create_lost_found" -> {
                val title = cta.payload.readString("title") ?: "Lost/Found pet alert"
                val body = cta.payload.readString("body") ?: "Shared from AI assistant"
                val suburb = cta.payload.readString("suburb") ?: _uiState.value.selectedSuburb
                createLostFoundPost(title = title, body = body, suburb = suburb)
            }

            "new_bark_thread" -> startNewBarkThread()
            "accept_profile_card" -> acceptProfileCard()
            "submit_provider_listing" -> submitProviderListing()
            "join_group" -> {
                cta.payload.readString("group_id")?.let { joinGroup(it) }
            }
        }
    }

    fun acceptProfileCard() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching { repository.acceptProfileCard() }
                .onSuccess { applyChatResponse(it, toast = "Profile created") }
                .onFailure { error -> _uiState.value = _uiState.value.copy(error = error.message, loading = false) }
        }
    }

    fun submitProviderListing() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching { repository.submitProviderListing() }
                .onSuccess {
                    applyChatResponse(it, toast = "Provider listed")
                    val category = it.ctaChips
                        .firstOrNull { cta -> cta.action == "open_services" }
                        ?.payload
                        .readString("category")
                    _uiState.value = _uiState.value.copy(selectedTab = AppTab.Services, selectedCategory = category)
                    loadHomeData(category)
                }
                .onFailure { error -> _uiState.value = _uiState.value.copy(error = error.message, loading = false) }
        }
    }

    fun createLostFoundPost(title: String, body: String, suburb: String) {
        createLostFoundAlert(
            CommunityPostCreate(
                type = "lost_found",
                title = title,
                body = body,
                suburb = suburb,
            ),
        )
    }

    fun createLostFoundAlert(payload: CommunityPostCreate) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching {
                repository.createLostFoundPost(payload)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    selectedTab = AppTab.Community,
                    postsSortBy = "relevance",
                    toastMessage = "Lost/found post created",
                )
                loadHomeData(_uiState.value.selectedCategory)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(loading = false, error = error.message)
            }
        }
    }

    fun createManualSharePoint(payload: CommunityPostCreate) {
        if (payload.type != "share_point") return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching {
                repository.createCommunityPost(
                    payload.copy(
                        type = "share_point",
                        userId = payload.userId ?: _uiState.value.activeUserId,
                    ),
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    selectedTab = AppTab.Community,
                    postsSortBy = "latest",
                    toastMessage = "Location share posted",
                )
                loadHomeData(_uiState.value.selectedCategory)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(loading = false, error = error.message)
            }
        }
    }

    fun resolveLostFoundPost(postId: String, status: String, note: String = "") {
        if (postId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching { repository.resolveLostFoundPost(postId = postId, status = status, note = note) }
                .onSuccess {
                    val toast = when (status) {
                        "reunited" -> "Marked as reunited"
                        "owner_found" -> "Marked as owner found"
                        "expired" -> "Marked as no longer active"
                        else -> "Lost/found alert updated"
                    }
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        selectedTab = AppTab.Community,
                        postsSortBy = "relevance",
                        toastMessage = toast,
                    )
                    loadHomeData(_uiState.value.selectedCategory)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(loading = false, error = error.message)
                }
        }
    }

    fun deleteCommunityPost(postId: String) {
        if (postId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching { repository.deleteCommunityPost(postId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        selectedTab = AppTab.Community,
                        toastMessage = "Share stopped",
                    )
                    loadHomeData(_uiState.value.selectedCategory)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(loading = false, error = error.message)
                }
        }
    }

    fun loadPostComments(postId: String, forceRefresh: Boolean = false) {
        if (postId.isBlank()) return
        val existing = _uiState.value.communityCommentsByPostId[postId]
        if (!forceRefresh && existing != null) return
        if (postId in _uiState.value.loadingCommentPostIds) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                loadingCommentPostIds = _uiState.value.loadingCommentPostIds + postId,
            )
            runCatching {
                repository.loadPostComments(
                    postId = postId,
                    includeRemoved = _uiState.value.isCommunityModerator,
                )
            }.onSuccess { comments ->
                val sorted = comments.sortedBy { comment -> comment.createdAt }
                _uiState.value = _uiState.value.copy(
                    communityCommentsByPostId = _uiState.value.communityCommentsByPostId + (postId to sorted),
                    loadingCommentPostIds = _uiState.value.loadingCommentPostIds - postId,
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    loadingCommentPostIds = _uiState.value.loadingCommentPostIds - postId,
                    error = error.message,
                )
            }
        }
    }

    fun createPostComment(postId: String, body: String, parentCommentId: String? = null) {
        val cleanBody = body.trim()
        if (postId.isBlank() || cleanBody.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                loadingCommentPostIds = _uiState.value.loadingCommentPostIds + postId,
                error = null,
            )
            runCatching {
                repository.createPostComment(
                    postId = postId,
                    body = cleanBody,
                    parentCommentId = parentCommentId?.trim()?.ifBlank { null },
                )
            }.onSuccess { created ->
                val existing = _uiState.value.communityCommentsByPostId[postId].orEmpty()
                val merged = (existing + created)
                    .distinctBy { comment -> comment.id }
                    .sortedBy { comment -> comment.createdAt }
                _uiState.value = _uiState.value.copy(
                    communityCommentsByPostId = _uiState.value.communityCommentsByPostId + (postId to merged),
                    loadingCommentPostIds = _uiState.value.loadingCommentPostIds - postId,
                    toastMessage = "Comment posted",
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    loadingCommentPostIds = _uiState.value.loadingCommentPostIds - postId,
                    error = error.message,
                )
            }
        }
    }

    fun moderatePostComment(commentId: String, action: String, note: String = "") {
        val normalizedAction = action.trim().lowercase()
        if (commentId.isBlank() || normalizedAction !in setOf("remove", "restore")) return
        val preState = _uiState.value
        val targetPostId = preState.communityCommentsByPostId.entries
            .firstOrNull { (_, comments) -> comments.any { comment -> comment.id == commentId } }
            ?.key

        viewModelScope.launch {
            if (targetPostId != null) {
                _uiState.value = _uiState.value.copy(
                    loadingCommentPostIds = _uiState.value.loadingCommentPostIds + targetPostId,
                    error = null,
                )
            }
            runCatching {
                repository.moderatePostComment(
                    commentId = commentId,
                    action = normalizedAction,
                    note = note,
                )
            }.onSuccess { updated ->
                val mappedPostId = targetPostId ?: updated.postId
                val currentComments = _uiState.value.communityCommentsByPostId[mappedPostId].orEmpty()
                val merged = currentComments
                    .map { comment -> if (comment.id == updated.id) updated else comment }
                    .ifEmpty { listOf(updated) }
                    .sortedBy { comment -> comment.createdAt }
                _uiState.value = _uiState.value.copy(
                    communityCommentsByPostId = _uiState.value.communityCommentsByPostId + (mappedPostId to merged),
                    loadingCommentPostIds = _uiState.value.loadingCommentPostIds - mappedPostId,
                    toastMessage = if (normalizedAction == "remove") "Comment removed" else "Comment restored",
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    loadingCommentPostIds = if (targetPostId != null) _uiState.value.loadingCommentPostIds - targetPostId else _uiState.value.loadingCommentPostIds,
                    error = error.message,
                )
            }
        }
    }

    fun reportCommunityPost(postId: String, reason: String, details: String = "") {
        if (postId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching {
                repository.reportCommunityPost(
                    postId = postId,
                    reason = reason.ifBlank { "Other" },
                    details = details,
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    toastMessage = "Report submitted",
                )
                loadHomeData(_uiState.value.selectedCategory)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(loading = false, error = error.message)
            }
        }
    }

    fun reportCommunityEvent(eventId: String, reason: String, details: String = "") {
        if (eventId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching {
                repository.reportCommunityEvent(
                    eventId = eventId,
                    reason = reason.ifBlank { "Other" },
                    details = details,
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    toastMessage = "Event report submitted",
                )
                loadHomeData(_uiState.value.selectedCategory)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(loading = false, error = error.message)
            }
        }
    }

    fun blockCommunityUser(targetUserId: String) {
        if (targetUserId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching { repository.blockCommunityUser(targetUserId) }
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        blockedUserIds = response.blockedUserIds,
                        toastMessage = "User blocked in community",
                    )
                    loadHomeData(_uiState.value.selectedCategory)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(loading = false, error = error.message)
                }
        }
    }

    fun unblockCommunityUser(targetUserId: String) {
        if (targetUserId.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.unblockCommunityUser(targetUserId) }
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        blockedUserIds = response.blockedUserIds,
                        toastMessage = "User unblocked",
                    )
                    loadHomeData(_uiState.value.selectedCategory)
                }
        }
    }

    fun resolveModerationReport(reportId: String, action: String, note: String = "") {
        if (reportId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching { repository.resolveModerationReport(reportId, action, note) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        toastMessage = if (action == "action_taken") "Moderation action recorded" else "Report dismissed",
                    )
                    loadHomeData(_uiState.value.selectedCategory)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(loading = false, error = error.message)
                }
        }
    }

    fun createCommunityGroupPost(title: String, body: String, suburb: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching {
                repository.createCommunityGroupPost(
                    title = title,
                    body = body,
                    suburb = suburb,
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    selectedTab = AppTab.Community,
                    toastMessage = "Community post created",
                )
                loadHomeData(_uiState.value.selectedCategory)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(loading = false, error = error.message)
            }
        }
    }

    fun consumeToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    private fun startMockWeatherTicker() {
        weatherTickerJob?.cancel()
        weatherTickerJob = viewModelScope.launch {
            while (true) {
                delay(60_000)
                refreshMockCommunityWeather()
            }
        }
    }

    private fun refreshMockCommunityWeather() {
        val state = _uiState.value
        val suburb = state.currentLocationSuburb ?: state.selectedSuburb
        val snapshot = buildMockWeatherSnapshot(suburb)
        _uiState.value = state.copy(communityWeather = snapshot)
    }

    private fun buildMockWeatherSnapshot(suburb: String): CommunityWeatherSnapshot {
        val now = Instant.now()
        val minuteBucket = now.epochSecond / 60L
        val seed = (suburb.lowercase().hashCode().toLong() xor minuteBucket).toInt()
        val conditions = listOf("Partly cloudy", "Sunny breaks", "Light drizzle", "Breezy", "Overcast")
        val condition = conditions[Math.floorMod(seed, conditions.size)]
        val temperature = 16 + Math.floorMod(seed / 3, 15)
        val rain = Math.floorMod(seed / 5, 61)
        val wind = 6 + Math.floorMod(seed / 7, 24)
        return CommunityWeatherSnapshot(
            suburb = suburb,
            temperatureC = temperature,
            condition = condition,
            rainChancePercent = rain,
            windKph = wind,
            updatedAtLabel = "Updated just now",
        )
    }

    private fun maybeRunAutoParkCheckIn(reason: String) {
        val state = _uiState.value
        if (!state.autoParkCheckInEnabled) return
        val localSuburb = state.currentLocationSuburb?.trim().orEmpty().ifBlank { state.selectedSuburb }
        if (localSuburb.isBlank()) return
        val group = findEligibleLocalParkGroup(state, localSuburb) ?: return
        val now = Instant.now()
        registerParkPresenceSignal(
            groupId = group.id,
            userId = state.activeUserId,
            now = now,
        )
        val quorumCount = currentParkPresenceQuorumCount(
            groupId = group.id,
            now = now,
            windowMinutes = state.autoParkCheckInQuorumWindowMinutes,
        )
        _uiState.value = _uiState.value.copy(autoParkCheckInQuorumCount = quorumCount)
        if (state.autoParkCheckInRequireCrowd && quorumCount < state.autoParkCheckInQuorumThreshold) {
            if (reason == "manual_test" || reason == "enabled") {
                _uiState.value = _uiState.value.copy(
                    toastMessage = "Auto check-in waiting for quorum: $quorumCount/${state.autoParkCheckInQuorumThreshold} members present.",
                )
            }
            return
        }
        val lastGroupId = state.lastAutoParkCheckInGroupId
        val lastAt = state.lastAutoParkCheckInAt?.let(::parseIsoInstantForSort) ?: Instant.EPOCH
        if (lastGroupId == group.id && now.isBefore(lastAt.plusSeconds(6 * 60 * 60L))) {
            if (reason == "manual_test") {
                _uiState.value = state.copy(toastMessage = "Auto check-in skipped: already shared recently.")
            }
            return
        }

        viewModelScope.launch {
            runCatching {
                repository.participateGroupChallenge(
                    groupId = group.id,
                    challengeType = "clean_park_streak",
                    contributionCount = 1,
                    note = "Auto check-in (privacy-safe): presence shared to members only.",
                )
            }.onSuccess { result ->
                _uiState.value = _uiState.value.copy(
                    lastAutoParkCheckInGroupId = group.id,
                    lastAutoParkCheckInAt = now.toString(),
                    toastMessage = "Auto check-in shared to ${group.name} only (${state.autoParkCheckInQuorumThreshold}+ member quorum met).",
                )
                loadHomeData(_uiState.value.selectedCategory)
            }.onFailure { error ->
                if (reason == "manual_test") {
                    _uiState.value = _uiState.value.copy(error = error.message)
                }
            }
        }
    }

    private fun findEligibleLocalParkGroup(state: UiState, suburb: String): Group? {
        return state.groups.firstOrNull { group ->
            group.membershipStatus == "member" &&
                group.suburb.equals(suburb, ignoreCase = true) &&
                (
                    "park" in group.name.lowercase() ||
                        "walk" in group.name.lowercase() ||
                        group.groupBadges.any { badge ->
                            val lower = badge.lowercase()
                            "park" in lower || "walk" in lower
                        }
                    )
        }
    }

    private fun registerParkPresenceSignal(
        groupId: String,
        userId: String,
        now: Instant,
    ) {
        val bucket = recentParkPresenceSignals.getOrPut(groupId) { mutableListOf() }
        val cutoff = now.minusSeconds(20L * 60L)
        bucket.removeAll { signal -> signal.detectedAt.isBefore(cutoff) }
        val existing = bucket.indexOfFirst { signal -> signal.userId == userId }
        if (existing >= 0) {
            bucket[existing] = ParkPresenceSignal(userId = userId, detectedAt = now)
        } else {
            bucket += ParkPresenceSignal(userId = userId, detectedAt = now)
        }
    }

    private fun currentParkPresenceQuorumCount(
        groupId: String,
        now: Instant,
        windowMinutes: Int,
    ): Int {
        val bucket = recentParkPresenceSignals[groupId] ?: return 0
        val cutoff = now.minusSeconds(windowMinutes.toLong() * 60L)
        bucket.removeAll { signal -> signal.detectedAt.isBefore(cutoff) }
        return bucket.map { signal -> signal.userId }.toSet().size
    }

    override fun onCleared() {
        weatherTickerJob?.cancel()
        super.onCleared()
    }

    private fun recordHomeLoadMetrics(metrics: HomeLoadMetrics) {
        val current = _uiState.value
        _uiState.value = current.copy(
            latestHomeLoadMetrics = metrics,
            homeLoadHistory = (current.homeLoadHistory + metrics).takeLast(20),
        )
    }

    fun markNotificationRead(notificationId: String) {
        if (notificationId.startsWith("local:")) {
            _uiState.value = _uiState.value.copy(
                readLocalNotificationIds = _uiState.value.readLocalNotificationIds + notificationId,
                notifications = _uiState.value.notifications.map { existing ->
                    if (existing.id == notificationId) existing.copy(read = true) else existing
                },
            )
            return
        }
        viewModelScope.launch {
            runCatching { repository.markNotificationRead(notificationId) }
                .onSuccess { updated ->
                    _uiState.value = _uiState.value.copy(
                        notifications = _uiState.value.notifications.map { existing ->
                            if (existing.id == updated.id) updated else existing
                        },
                    )
                }
        }
    }

    fun markNotificationIdsRead(notificationIds: List<String>) {
        val ids = notificationIds
            .map { id -> id.trim() }
            .filter { id -> id.isNotEmpty() }
            .distinct()
        if (ids.isEmpty()) return

        val localIds = ids.filter { id -> id.startsWith("local:") }.toSet()
        if (localIds.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                readLocalNotificationIds = _uiState.value.readLocalNotificationIds + localIds,
                notifications = _uiState.value.notifications.map { notification ->
                    if (notification.id in localIds) notification.copy(read = true) else notification
                },
            )
        }

        val remoteIds = ids.filterNot { id -> id.startsWith("local:") }
        if (remoteIds.isEmpty()) return
        viewModelScope.launch {
            val updates = buildList {
                remoteIds.forEach { notificationId ->
                    runCatching { repository.markNotificationRead(notificationId) }
                        .onSuccess { updated -> add(updated) }
                }
            }
            if (updates.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    notifications = _uiState.value.notifications.map { existing ->
                        updates.firstOrNull { updated -> updated.id == existing.id } ?: existing
                    },
                )
            }
        }
    }

    fun markAllNotificationsRead() {
        val state = _uiState.value
        val localUnreadIds = state.notifications
            .asSequence()
            .filter { notification -> notification.id.startsWith("local:") && !notification.read }
            .map { notification -> notification.id }
            .toSet()
        if (localUnreadIds.isNotEmpty()) {
            _uiState.value = state.copy(
                readLocalNotificationIds = state.readLocalNotificationIds + localUnreadIds,
                notifications = state.notifications.map { notification ->
                    if (notification.id in localUnreadIds) notification.copy(read = true) else notification
                },
                toastMessage = "Local notifications marked read",
            )
        }
    }

    fun clearLocalNotifications() {
        val state = _uiState.value
        val remaining = state.notifications.filterNot { notification -> notification.id.startsWith("local:") }
        _uiState.value = state.copy(
            notifications = remaining,
            readLocalNotificationIds = emptySet(),
            toastMessage = "Local notifications cleared",
        )
    }

    fun clearLocalNotificationIds(notificationIds: List<String>) {
        val localIds = notificationIds
            .map { id -> id.trim() }
            .filter { id -> id.startsWith("local:") }
            .toSet()
        if (localIds.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            notifications = _uiState.value.notifications.filterNot { notification -> notification.id in localIds },
            readLocalNotificationIds = _uiState.value.readLocalNotificationIds - localIds,
            acknowledgedCommunityNotificationIds = _uiState.value.acknowledgedCommunityNotificationIds - localIds,
            acknowledgedMessageNotificationIds = _uiState.value.acknowledgedMessageNotificationIds - localIds,
            toastMessage = "Local notifications cleared",
        )
    }

    fun openNotificationDeepLink(notification: AppNotification) {
        val deepLink = notification.deepLink?.trim().orEmpty()
        val deepLinkKind = notificationDeepLinkKind(deepLink)
        if (deepLink.isBlank()) {
            trackActivationFailureAsync(
                step = "notification_open",
                message = "blank_deep_link",
                metadata = mapOf(
                    "category" to sanitizeTelemetryValue(notification.category, maxLength = 40),
                    "deep_link_kind" to deepLinkKind,
                ),
            )
            return
        }
        trackActivationEventAsync(
            step = "notification_open",
            status = "attempted",
            metadata = mapOf(
                "category" to sanitizeTelemetryValue(notification.category, maxLength = 40),
                "deep_link_kind" to deepLinkKind,
            ),
        )
        when {
            deepLink.startsWith("group:") -> {
                val groupId = deepLink.removePrefix("group:").trim()
                _uiState.value = _uiState.value.copy(
                    selectedTab = AppTab.Community,
                    selectedCommunityGroupId = groupId.ifBlank { null },
                )
            }
            deepLink.startsWith("event:") || deepLink.startsWith("post:") -> {
                _uiState.value = _uiState.value.copy(
                    selectedTab = AppTab.Community,
                    postsSortBy = "relevance",
                )
                loadHomeData(_uiState.value.selectedCategory)
            }
            deepLink.startsWith("provider:") -> {
                val providerId = deepLink.removePrefix("provider:").trim()
                _uiState.value = _uiState.value.copy(selectedTab = AppTab.Services)
                if (providerId.isNotBlank()) {
                    loadProviderDetails(providerId)
                }
            }
            deepLink.startsWith("booking:") || deepLink.startsWith("quote:") -> {
                _uiState.value = _uiState.value.copy(selectedTab = AppTab.Services)
            }
            deepLink.startsWith("message:") -> {
                _uiState.value = _uiState.value.copy(selectedTab = AppTab.Messages)
            }
            deepLink == "profile" -> {
                _uiState.value = _uiState.value.copy(selectedTab = AppTab.Profile)
            }
            else -> {
                _uiState.value = _uiState.value.copy(selectedTab = AppTab.Community)
            }
        }
        trackActivationEventAsync(
            step = "notification_open",
            status = "succeeded",
            metadata = mapOf(
                "category" to sanitizeTelemetryValue(notification.category, maxLength = 40),
                "deep_link_kind" to deepLinkKind,
            ),
        )
        if (!notification.read) {
            markNotificationRead(notification.id)
        }
    }

    fun syncPushToken() {
        viewModelScope.launch {
            repository.syncDevicePushToken()
        }
    }

    private suspend fun trackActivationEvent(
        step: String,
        status: String,
        metadata: Map<String, String> = emptyMap(),
        durationMs: Int? = null,
    ) {
        repository.trackCommunityAnalytics(
            event = "${ACTIVATION_EVENT_PREFIX}_${sanitizeTelemetryValue(step, 40)}_${sanitizeTelemetryValue(status, 24)}",
            category = ACTIVATION_CATEGORY,
            metadata = baseActivationMetadata() + metadata,
            durationMs = durationMs,
        )
    }

    private suspend fun trackActivationFailure(
        step: String,
        error: Throwable,
        metadata: Map<String, String> = emptyMap(),
        durationMs: Int? = null,
    ) {
        val message = sanitizeTelemetryValue(error.message ?: error.javaClass.simpleName, maxLength = 160)
        trackActivationEvent(
            step = step,
            status = "failed",
            metadata = metadata + ("error" to message),
            durationMs = durationMs,
        )
        repository.trackCommunityDiagnostic(
            kind = "error",
            message = "${ACTIVATION_EVENT_PREFIX}_${sanitizeTelemetryValue(step, 40)}_failed",
            context = baseActivationMetadata() + metadata + ("error" to message),
            durationMs = durationMs,
        )
    }

    private fun trackActivationEventAsync(
        step: String,
        status: String,
        metadata: Map<String, String> = emptyMap(),
        durationMs: Int? = null,
    ) {
        viewModelScope.launch {
            trackActivationEvent(step = step, status = status, metadata = metadata, durationMs = durationMs)
        }
    }

    private fun trackActivationFailureAsync(
        step: String,
        message: String,
        metadata: Map<String, String> = emptyMap(),
        durationMs: Int? = null,
    ) {
        viewModelScope.launch {
            val safeMessage = sanitizeTelemetryValue(message, maxLength = 160)
            trackActivationEvent(
                step = step,
                status = "failed",
                metadata = metadata + ("error" to safeMessage),
                durationMs = durationMs,
            )
            repository.trackCommunityDiagnostic(
                kind = "error",
                message = "${ACTIVATION_EVENT_PREFIX}_${sanitizeTelemetryValue(step, 40)}_failed",
                context = baseActivationMetadata() + metadata + ("error" to safeMessage),
                durationMs = durationMs,
            )
        }
    }

    private fun baseActivationMetadata(): Map<String, String> {
        return mapOf(
            "surface" to BuildConfig.APP_SURFACE.lowercase(),
            "environment" to BuildConfig.ENVIRONMENT.lowercase(),
            "auth_mode" to if (requiresOtpAuth()) "otp" else "demo",
        )
    }

    private fun notificationDeepLinkKind(raw: String): String {
        val normalized = raw.trim()
        return when {
            normalized.isBlank() -> "blank"
            normalized.startsWith("group:") -> "group"
            normalized.startsWith("event:") -> "event"
            normalized.startsWith("post:") -> "post"
            normalized.startsWith("provider:") -> "provider"
            normalized.startsWith("booking:") -> "booking"
            normalized.startsWith("quote:") -> "quote"
            normalized.startsWith("message:") -> "message"
            normalized == "profile" -> "profile"
            else -> "other"
        }
    }

    private fun emailDomain(email: String): String {
        val domain = email.substringAfter('@', "").trim().lowercase()
        return if (domain.isBlank()) "unknown" else sanitizeTelemetryValue(domain, maxLength = 80)
    }

    private fun sanitizeTelemetryValue(value: String, maxLength: Int): String {
        return value
            .trim()
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^a-zA-Z0-9._:\\-]"), "")
            .take(maxLength)
            .ifBlank { "na" }
    }

    private fun onboardingAssistantTurn(content: String): ChatTurn {
        return ChatTurn(
            role = "assistant",
            content = content,
            answerSource = "onboarding_script",
            answerBadges = emptyList(),
        )
    }

    private fun onboardingChatResponse(
        answer: String,
        conversation: List<ChatTurn>,
    ): ChatResponse {
        return ChatResponse(
            answer = answer,
            conversation = conversation,
            answerSource = "onboarding_script",
            answerBadges = emptyList(),
        )
    }

    private fun pickOnboardingVariation(options: List<String>): String {
        if (options.isEmpty()) return ""
        return options[Random.nextInt(options.size)]
    }

    private fun updateOnboardingProfileInfo(
        profile: ProfileInfo,
        ownerName: String,
        dogName: String,
        dogPhotoUri: String? = null,
    ): ProfileInfo {
        val normalizedOwner = ownerName.trim()
        val normalizedDog = dogName.trim()
        val normalizedPhoto = dogPhotoUri?.trim().orEmpty()
        var next = profile
        if (normalizedOwner.isNotBlank()) {
            next = next.copy(displayName = normalizedOwner)
        }
        if (normalizedOwner.isNotBlank() && normalizedDog.isNotBlank()) {
            next = next.copy(
                dogName = normalizedDog,
                bio = "$normalizedOwner's dog: $normalizedDog",
            )
        }
        if (normalizedPhoto.isNotBlank()) {
            val mergedPhotos = buildList {
                add(normalizedPhoto)
                next.dogPhotoUrls
                    .asSequence()
                    .map { value -> value.trim() }
                    .filter { value -> value.isNotBlank() && value != normalizedPhoto }
                    .take(5)
                    .forEach(::add)
            }
            next = next.copy(dogPhotoUrls = mergedPhotos)
        }
        return next
    }

    private fun normalizeTestProfileMode(raw: String): String {
        return when (raw.trim().lowercase()) {
            TEST_PROFILE_MODE_ONBOARDING -> TEST_PROFILE_MODE_ONBOARDING
            else -> TEST_PROFILE_MODE_READY
        }
    }

    private fun buildFreshOnboardingProfile(
        activeUserId: String,
        suburb: String,
    ): ProfileInfo {
        return ProfileInfo(
            displayName = "",
            email = "${activeUserId}@barkwise.test",
            phone = "",
            humanRoleLabel = "New member",
            dogName = "",
            dogAgeMonths = 0,
            dogPhotoUrls = emptyList(),
            bio = "",
            suburb = suburb,
            favoriteSuburbs = emptyList(),
            fieldVisibility = mapOf(
                "phone" to "private",
                "email" to "private",
                "suburb" to "group",
            ),
        )
    }

    private fun buildReadyProfile(
        activeUserId: String,
        suburb: String,
    ): ProfileInfo {
        val fallbackName = accountLabel(activeUserId)
        val seeded = KNOWN_FRIEND_PROFILES[activeUserId]
        val displayName = seeded?.first ?: fallbackName
        val dogName = seeded?.second?.first ?: "Milo"
        val dogPhoto = seeded?.second?.second ?: "https://images.unsplash.com/photo-1580467277788-c6e040296602?auto=format&fit=crop&w=1200&q=80"
        return ProfileInfo(
            displayName = displayName,
            email = "${activeUserId}@barkwise.test",
            phone = "+61 400 000 000",
            humanRoleLabel = "Member",
            dogName = dogName,
            dogAgeMonths = 30,
            dogBreedMix = "Mixed breed",
            dogGender = "male",
            dogWeightKg = "12.5",
            dogPhotoUrls = listOf(dogPhoto),
            bio = "Pet parent of $dogName. Loves social dog walks and local events.",
            suburb = suburb,
            favoriteSuburbs = listOf(suburb, "Surry Hills", "Newtown").distinct().take(3),
            playEnergyLevel = "Medium",
            playStyle = "Balanced play",
            socialConfidence = "Friendly",
            walkPreferences = "Morning and evening walks",
            trainingStyle = "Positive reinforcement",
            vaccinationStatus = "Up to date",
            microchipped = true,
            leashReliability = "Reliable on leash",
            fieldVisibility = mapOf(
                "phone" to "private",
                "email" to "private",
                "suburb" to "group",
            ),
        )
    }

    private fun requiresOtpAuth(): Boolean {
        return BuildConfig.REQUIRE_INVITE_OTP_AUTH
    }

    private fun allowsDemoLoginFallback(): Boolean {
        return BuildConfig.ALLOW_DEMO_LOGIN
    }

    private fun isOnboardingScriptEnabled(): Boolean = BuildConfig.ONBOARD_SCRIPT_ENABLED

    private fun isStagingTestBuild(): Boolean = BuildConfig.ENVIRONMENT.equals("staging", ignoreCase = true)

    private fun applyChatResponse(response: ChatResponse, toast: String? = null) {
        val state = _uiState.value
        val parsed = parseA2uiMessages(response.a2uiMessages)
        val selectedThreadId = state.selectedBarkThreadId
        val activeThread = state.barkThreads.firstOrNull { it.id == selectedThreadId } ?: state.barkThreads.first()
        val responseConversation = response.conversation.toMutableList()
        val lastAssistantIndex = responseConversation.indexOfLast { turn -> turn.role == "assistant" }
        if (lastAssistantIndex >= 0) {
            val lastAssistant = responseConversation[lastAssistantIndex]
            val needsRouteMetadata = lastAssistant.answerSource.isNullOrBlank() &&
                lastAssistant.answerBadges.isEmpty() &&
                lastAssistant.citations.isEmpty()
            if (needsRouteMetadata && lastAssistant.content == response.answer) {
                responseConversation[lastAssistantIndex] = lastAssistant.copy(
                    answerSource = response.answerSource,
                    answerBadges = response.answerBadges,
                    citations = response.citations,
                )
            }
        }
        val hydratedConversation = responseConversation.toList()
        val updatedThread = activeThread.copy(
            title = resolveBarkThreadTitle(activeThread.title, hydratedConversation),
            conversation = hydratedConversation,
            chat = response,
            profileSuggestion = response.profileSuggestion,
            a2uiProfileCard = parsed.first,
            a2uiProviderCard = parsed.second,
            updatedAt = System.currentTimeMillis(),
        )
        _uiState.value = state.copy(
            chat = response,
            conversation = hydratedConversation,
            profileSuggestion = response.profileSuggestion,
            a2uiProfileCard = parsed.first,
            a2uiProviderCard = parsed.second,
            barkThreads = upsertBarkThread(state.barkThreads, updatedThread),
            loading = false,
            streamingAssistantText = "",
            selectedTab = AppTab.BarkAI,
            toastMessage = toast,
        )
    }

    private fun parseA2uiMessages(messages: List<JsonObject>): Pair<A2uiCardState?, A2uiCardState?> {
        var profileCard: A2uiCardState? = null
        var providerCard: A2uiCardState? = null

        messages.forEach { msg ->
            val dataModel = msg["dataModelUpdate"] as? JsonObject ?: return@forEach
            val surfaceId = dataModel.readNestedString("surfaceId") ?: return@forEach
            val contents = dataModel["contents"] as? JsonObject ?: return@forEach

            if (surfaceId == "chat_profile") {
                val profile = contents["profile"] as? JsonObject
                val fields = mutableMapOf<String, String>()
                profile?.forEach { (k, v) ->
                    fields[k] = jsonElementToDisplay(v)
                }
                profileCard = A2uiCardState(
                    title = contents.readNestedString("title") ?: "Suggested Pet Profile",
                    fields = fields,
                    submitAction = contents.readNestedString("acceptAction"),
                )
            }

            if (surfaceId == "provider_onboarding") {
                val collected = contents["collected"] as? JsonObject
                val fields = mutableMapOf<String, String>()
                collected?.forEach { (k, v) ->
                    fields[k] = jsonElementToDisplay(v)
                }
                contents.readNestedString("awaitingField")?.let { fields["awaitingField"] = it }
                providerCard = A2uiCardState(
                    title = contents.readNestedString("title") ?: "Provider Onboarding",
                    fields = fields,
                    submitAction = contents.readNestedString("submitAction"),
                )
            }
        }

        return profileCard to providerCard
    }
}

private fun upsertBarkThread(threads: List<BarkThread>, updated: BarkThread): List<BarkThread> {
    val filtered = threads.filterNot { it.id == updated.id }
    return (listOf(updated) + filtered)
        .sortedByDescending { it.updatedAt }
        .take(20)
}

private fun <T> reuseIfEquivalent(current: T, candidate: T): T {
    return if (current == candidate) current else candidate
}

private fun elapsedMs(startNs: Long): Long = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(0L)

private fun resolveBarkThreadTitle(existingTitle: String, conversation: List<ChatTurn>): String {
    if (existingTitle != "New thread" && existingTitle != "Thread 1") return existingTitle
    val firstUser = conversation.firstOrNull { it.role == "user" }?.content?.trim().orEmpty()
    if (firstUser.isBlank()) return existingTitle
    return if (firstUser.length <= 36) firstUser else firstUser.take(33).trimEnd() + "..."
}

private fun buildFriendProfiles(
    activeUserId: String,
    messageThreads: List<MessageThread>,
    existingProfiles: List<FriendProfile>,
): List<FriendProfile> {
    val existingByUserId = existingProfiles.associateBy { profile -> profile.userId }
    val existingFriendIds = existingProfiles
        .asSequence()
        .filter { profile -> profile.isFriend }
        .map { profile -> profile.userId }
        .toSet()
    val defaultFriendIds = if (existingFriendIds.isNotEmpty()) {
        existingFriendIds
    } else {
        messageThreads
            .asSequence()
            .map { thread -> thread.participantUserId }
            .filter { userId -> userId.isNotBlank() }
            .toSet()
    }

    val candidateUserIds = buildSet {
        addAll(KNOWN_FRIEND_PROFILES.keys)
        messageThreads
            .asSequence()
            .map { thread -> thread.participantUserId }
            .filter { userId -> userId.isNotBlank() }
            .forEach(::add)
        existingProfiles
            .asSequence()
            .map { profile -> profile.userId }
            .filter { userId -> userId.isNotBlank() }
            .forEach(::add)
    }.filter { userId -> userId != activeUserId }

    return candidateUserIds.map { userId ->
        val seed = KNOWN_FRIEND_PROFILES[userId]
        val existing = existingByUserId[userId]
        val thread = messageThreads.firstOrNull { candidate -> candidate.participantUserId == userId }
        val humanName = thread?.participantAccountLabel
            ?.takeIf { value -> value.isNotBlank() }
            ?: existing?.humanName?.takeIf { value -> value.isNotBlank() }
            ?: seed?.first
            ?: accountLabel(userId)
        val seedDogName = seed?.second?.first
        val seedPhoto = seed?.second?.second
        val dogName = thread?.participantPetNames
            ?.firstOrNull()
            ?.takeIf { value -> value.isNotBlank() }
            ?: existing?.dogName?.takeIf { value -> value.isNotBlank() }
            ?: seedDogName
            ?: "Dog"
        val dogPhotoUrl = existing?.dogPhotoUrl
            ?.takeIf { value -> value.isNotBlank() }
            ?: seedPhoto
            ?: "https://images.unsplash.com/photo-1580467277788-c6e040296602?auto=format&fit=crop&w=1200&q=80"
        FriendProfile(
            userId = userId,
            humanName = humanName,
            dogName = dogName,
            dogPhotoUrl = dogPhotoUrl,
            isFriend = existing?.isFriend ?: (userId in defaultFriendIds),
        )
    }.sortedWith(
        compareByDescending<FriendProfile> { profile -> profile.isFriend }
            .thenBy { profile -> profile.humanName.lowercase() },
    )
}

private fun ApiDirectMessage.toDirectMessage(): DirectMessage {
    return DirectMessage(
        id = id,
        threadId = threadId,
        senderUserId = senderUserId,
        recipientUserId = recipientUserId,
        body = body,
    )
}

private fun buildMessageThreadsForState(
    state: UiState,
    apiThreads: List<ApiMessageThread>,
): List<MessageThread> {
    return buildMessageThreadsFromApi(
        activeUserId = state.activeUserId,
        apiThreads = apiThreads,
        providers = state.providers,
        groups = state.groups,
        posts = state.posts,
        ownerBookings = state.ownerBookings,
        providerBookings = state.providerBookings,
        mutedThreadIds = state.mutedMessageThreadIds,
        pinnedThreadIds = state.pinnedMessageThreadIds,
        blockedParticipantIds = state.blockedUserIds.toSet(),
    )
}

private fun buildMessageThreadsFromApi(
    activeUserId: String,
    apiThreads: List<ApiMessageThread>,
    providers: List<ServiceProvider>,
    groups: List<Group>,
    posts: List<CommunityPost>,
    ownerBookings: List<OwnerBooking>,
    providerBookings: List<ProviderBooking>,
    mutedThreadIds: Set<String> = emptySet(),
    pinnedThreadIds: Set<String> = emptySet(),
    blockedParticipantIds: Set<String> = emptySet(),
): List<MessageThread> {
    val providerNameByOwner = providers
        .mapNotNull { provider ->
            val ownerUserId = provider.ownerUserId?.trim().orEmpty()
            if (ownerUserId.isBlank() || ownerUserId == activeUserId) {
                null
            } else {
                ownerUserId to provider.name
            }
        }
        .groupBy({ pair -> pair.first }, { pair -> pair.second })
        .mapValues { entry -> entry.value.firstOrNull().orEmpty() }
    val bookingLabelByUser = mutableMapOf<String, String>()
    ownerBookings.forEach { booking ->
        val providerOwner = providers
            .firstOrNull { provider -> provider.name == booking.serviceName }
            ?.ownerUserId
            ?.trim()
            .orEmpty()
        if (providerOwner.isNotBlank() && providerOwner != activeUserId) {
            bookingLabelByUser.putIfAbsent(providerOwner, booking.serviceName)
        }
    }
    providerBookings.forEach { booking ->
        val ownerUser = booking.ownerUserId.trim()
        if (ownerUser.isNotBlank() && ownerUser != activeUserId) {
            bookingLabelByUser.putIfAbsent(ownerUser, "${booking.petName} booking")
        }
    }
    val petNamesByUser = mutableMapOf<String, LinkedHashSet<String>>()
    providerBookings.forEach { booking ->
        val ownerUser = booking.ownerUserId.trim()
        val petName = booking.petName.trim()
        if (ownerUser.isBlank() || ownerUser == activeUserId || petName.isBlank()) return@forEach
        petNamesByUser.getOrPut(ownerUser) { linkedSetOf() }.add(petName)
    }
    posts.forEach { post ->
        val ownerUser = post.createdBy?.trim().orEmpty()
        if (ownerUser.isBlank() || ownerUser == activeUserId) return@forEach
        val explicitName = post.petName?.trim().orEmpty()
        val inferredFromCheckInTitle = if (post.type == "group_post" && post.title.contains("Dog park check-in:", ignoreCase = true)) {
            post.title.substringAfter(":").trim()
        } else {
            ""
        }
        val candidateNames = listOf(explicitName, inferredFromCheckInTitle)
            .filter { name -> name.isNotBlank() }
            .map { name -> name.take(48) }
        if (candidateNames.isNotEmpty()) {
            val pets = petNamesByUser.getOrPut(ownerUser) { linkedSetOf() }
            candidateNames.forEach { name -> pets.add(name) }
        }
    }
    val groupAdminByUser = groups
        .mapNotNull { group ->
            val owner = group.ownerUserId?.trim().orEmpty()
            if (owner.isBlank() || owner == activeUserId) {
                null
            } else {
                owner to "${group.name} admins"
            }
        }
        .toMap()
    return apiThreads
        .asSequence()
        .filter { thread -> thread.participantUserId != activeUserId }
        .filterNot { thread -> thread.participantUserId in blockedParticipantIds }
        .map { thread ->
            val title = providerNameByOwner[thread.participantUserId]
                ?: bookingLabelByUser[thread.participantUserId]
                ?: groupAdminByUser[thread.participantUserId]
                ?: accountLabel(thread.participantUserId)
            MessageThread(
                id = thread.id,
                title = title,
                participantUserId = thread.participantUserId,
                participantAccountLabel = accountLabel(thread.participantUserId),
                participantPetNames = petNamesByUser[thread.participantUserId]
                    ?.toList()
                    ?.sortedBy { name -> name.lowercase() }
                    .orEmpty(),
                lastMessage = thread.lastMessage.ifBlank { "No messages yet" },
                unreadCount = thread.unreadCount,
                isMuted = thread.id in mutedThreadIds,
                isPinned = thread.id in pinnedThreadIds,
            )
        }
        .toList()
        .let { threads ->
            applyThreadPresentationFlags(
                threads = threads,
                mutedThreadIds = mutedThreadIds,
                pinnedThreadIds = pinnedThreadIds,
            )
        }
}

private fun applyThreadPresentationFlags(
    threads: List<MessageThread>,
    mutedThreadIds: Set<String>,
    pinnedThreadIds: Set<String>,
): List<MessageThread> {
    return threads
        .map { thread ->
            thread.copy(
                isMuted = thread.id in mutedThreadIds,
                isPinned = thread.id in pinnedThreadIds,
            )
        }
        .sortedWith(
            compareByDescending<MessageThread> { it.isPinned }
                .thenBy { it.isMuted }
                .thenByDescending { it.unreadCount }
                .thenBy { it.title },
        )
}

private fun buildLocalCommunityNotifications(
    activeUserId: String,
    followedGroupIds: Set<String>,
    savedPostIds: Set<String>,
    groups: List<Group>,
    events: List<CommunityEvent>,
    posts: List<CommunityPost>,
    moderationReports: List<CommunityReport>,
    includeFollowedGroupAlerts: Boolean,
    includeSavedPostUpdates: Boolean,
    includeSafetyAlerts: Boolean,
): List<AppNotification> {
    val groupNameById = groups.associate { group -> group.id to group.name }
    val followedEventNotifications = if (includeFollowedGroupAlerts) events
        .asSequence()
        .filter { event ->
            event.groupId != null &&
                event.groupId in followedGroupIds &&
                event.status == "approved"
        }
        .map { event ->
            val groupId = event.groupId.orEmpty()
            AppNotification(
                id = "local:followed_event:${event.id}",
                userId = activeUserId,
                title = "Followed group event",
                body = buildString {
                    append(event.title)
                    groupNameById[groupId]?.let { groupName -> append(" • $groupName") }
                },
                category = "community_followed_group",
                read = false,
                createdAt = event.date,
                deepLink = "group:$groupId",
            )
        }
        .toList()
    else emptyList()

    val savedPostUpdateNotifications = if (includeSavedPostUpdates) posts
        .asSequence()
        .filter { post ->
            post.id in savedPostIds &&
                post.type == "lost_found" &&
                (post.alertStatus?.lowercase() ?: "open") != "open"
        }
        .map { post ->
            val status = post.alertStatus?.replace("_", " ")?.replaceFirstChar { it.uppercase() } ?: "updated"
            AppNotification(
                id = "local:saved_post:${post.id}:${post.alertStatus ?: "updated"}",
                userId = activeUserId,
                title = "Saved alert update",
                body = "${post.title} is now $status",
                category = "community_saved_post",
                read = false,
                createdAt = post.resolvedAt ?: post.createdAt ?: Instant.now().toString(),
                deepLink = "post:${post.id}",
            )
        }
        .toList()
    else emptyList()

    val safetyNotifications = if (includeSafetyAlerts) moderationReports
        .asSequence()
        .filter { report -> report.status == "pending" }
        .take(3)
        .map { report ->
            AppNotification(
                id = "local:safety_report:${report.id}",
                userId = activeUserId,
                title = "Moderation queue update",
                body = "Open report: ${report.reason}",
                category = "community_safety",
                read = false,
                createdAt = report.createdAt,
                deepLink = "profile",
            )
        }
        .toList()
    else emptyList()

    return (followedEventNotifications + savedPostUpdateNotifications + safetyNotifications)
}

private fun mergeNotifications(
    remoteNotifications: List<AppNotification>,
    localNotifications: List<AppNotification>,
    localReadIds: Set<String>,
): List<AppNotification> {
    val normalizedLocal = localNotifications.map { notification ->
        if (notification.id in localReadIds) notification.copy(read = true) else notification
    }
    val merged = (normalizedLocal + remoteNotifications)
        .distinctBy { notification -> notification.id }
    return merged
        .sortedByDescending { notification -> parseIsoInstantForSort(notification.createdAt) }
        .take(60)
}

private fun parseIsoInstantForSort(raw: String?): Instant {
    if (raw.isNullOrBlank()) return Instant.EPOCH
    return runCatching { Instant.parse(raw) }
        .recoverCatching { java.time.OffsetDateTime.parse(raw).toInstant() }
        .getOrDefault(Instant.EPOCH)
}

private fun formatAudCents(cents: Int): String {
    return String.format(java.util.Locale.US, "%.2f", cents / 100.0)
}

private fun isCommunityNotification(category: String): Boolean {
    val normalized = category.lowercase()
    return normalized.startsWith("community") || normalized.contains("group")
}

private fun isMessageNotification(category: String): Boolean {
    return category.lowercase().contains("message")
}

private data class ParkPresenceSignal(
    val userId: String,
    val detectedAt: Instant,
)

class PetSocialViewModelFactory(
    private val repository: PetSocialRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PetSocialViewModel::class.java)) {
            return PetSocialViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

private data class HomePayload(
    val providers: List<ServiceProvider>,
    val ownerListingProviders: List<ServiceProvider>,
    val recommendationSuburb: String? = null,
    val recommendationSource: String = "none",
    val nearbyPetBusinesses: List<NearbyPetBusiness>,
    val groups: List<Group>,
    val posts: List<CommunityPost>,
    val events: List<CommunityEvent>,
    val ownerBookings: List<BookingResponse>,
    val providerBookings: List<BookingResponse>,
    val providerInboxItems: List<ProviderInboxItem>,
    val calendarEvents: List<CalendarEvent>,
    val messageThreads: List<ApiMessageThread>,
    val selectedMessageThreadId: String?,
    val selectedThreadMessages: List<ApiDirectMessage>,
    val notifications: List<AppNotification>,
    val profileInfo: ProfileInfo,
    val blockedUserIds: List<String>,
    val moderationReports: List<CommunityReport>,
    val communityFunnelMetrics: CommunityFunnelMetrics?,
    val activationFunnelMetrics: CommunityActivationFunnel?,
)

private fun UserProfileResponse.toProfileInfo(
    activeUserId: String,
    fallbackProfile: ProfileInfo,
    fallbackSuburb: String,
): ProfileInfo {
    val normalizedPhotos = dogPhotoUrls
        .asSequence()
        .map { value -> value.trim() }
        .filter { value -> value.isNotBlank() }
        .distinct()
        .toList()
    val normalizedFavorites = favoriteSuburbs
        .asSequence()
        .map { value -> value.trim() }
        .filter { value -> value.isNotBlank() }
        .distinct()
        .toList()
    val normalizedVisibility = fieldVisibility
        .mapKeys { (key, _) -> key.trim().lowercase() }
        .mapValues { (_, value) -> value.trim().lowercase() }
        .filter { (key, value) -> key.isNotBlank() && value.isNotBlank() }
    return ProfileInfo(
        displayName = displayName.trim().ifBlank { fallbackProfile.displayName.ifBlank { accountLabel(activeUserId) } },
        email = email.trim().ifBlank { fallbackProfile.email.ifBlank { "$activeUserId@barkwise.test" } },
        phone = phone.trim().ifBlank { fallbackProfile.phone },
        humanPronouns = humanPronouns.trim().ifBlank { fallbackProfile.humanPronouns },
        humanRoleLabel = humanRoleLabel.trim().ifBlank { fallbackProfile.humanRoleLabel.ifBlank { "Member" } },
        dogName = dogName.trim().ifBlank { fallbackProfile.dogName },
        dogAgeMonths = dogAgeMonths.takeIf { it > 0 } ?: fallbackProfile.dogAgeMonths,
        dogBreedMix = dogBreedMix.trim().ifBlank { fallbackProfile.dogBreedMix },
        dogGender = dogSexNeuter.trim().ifBlank { fallbackProfile.dogGender },
        dogWeightKg = dogWeightClass.trim().ifBlank { fallbackProfile.dogWeightKg },
        dogPhotoUrls = normalizedPhotos.ifEmpty { fallbackProfile.dogPhotoUrls },
        secondaryDogName = secondaryDogName.trim().ifBlank { fallbackProfile.secondaryDogName },
        secondaryDogAgeMonths = secondaryDogAgeMonths.takeIf { it > 0 } ?: fallbackProfile.secondaryDogAgeMonths,
        secondaryDogGender = secondaryDogGender.trim().ifBlank { fallbackProfile.secondaryDogGender },
        secondaryDogWeightKg = secondaryDogWeightKg.trim().ifBlank { fallbackProfile.secondaryDogWeightKg },
        bio = bio.trim().ifBlank { fallbackProfile.bio },
        suburb = suburb.trim().ifBlank { fallbackProfile.suburb.ifBlank { fallbackSuburb } },
        favoriteSuburbs = normalizedFavorites.ifEmpty { fallbackProfile.favoriteSuburbs },
        playEnergyLevel = playEnergyLevel.trim().ifBlank { fallbackProfile.playEnergyLevel },
        playStyle = playStyle.trim().ifBlank { fallbackProfile.playStyle },
        socialConfidence = socialConfidence.trim().ifBlank { fallbackProfile.socialConfidence },
        triggerNotes = triggerNotes.trim().ifBlank { fallbackProfile.triggerNotes },
        idealMatch = idealMatch.trim().ifBlank { fallbackProfile.idealMatch },
        walkPreferences = walkPreferences.trim().ifBlank { fallbackProfile.walkPreferences },
        trainingStyle = trainingStyle.trim().ifBlank { fallbackProfile.trainingStyle },
        feedingRules = feedingRules.trim().ifBlank { fallbackProfile.feedingRules },
        consentBoundaries = consentBoundaries.trim().ifBlank { fallbackProfile.consentBoundaries },
        vaccinationStatus = vaccinationStatus.trim().ifBlank { fallbackProfile.vaccinationStatus },
        microchipped = microchipped,
        recallTrained = recallTrained,
        leashReliability = leashReliability.trim().ifBlank { fallbackProfile.leashReliability },
        emergencyContactName = emergencyContactName.trim().ifBlank { fallbackProfile.emergencyContactName },
        emergencyContactPhone = emergencyContactPhone.trim().ifBlank { fallbackProfile.emergencyContactPhone },
        fieldVisibility = if (normalizedVisibility.isEmpty()) fallbackProfile.fieldVisibility else normalizedVisibility,
    )
}

private fun JsonObject?.readString(key: String): String? = this
    ?.get(key)
    ?.let { it as? JsonPrimitive }
    ?.contentOrNull

private fun JsonObject.readNestedString(key: String): String? = this[key]
    ?.let { it as? JsonPrimitive }
    ?.contentOrNull

private fun jsonElementToDisplay(element: JsonElement): String {
    return when (element) {
        is JsonArray -> element.joinToString(", ") { item ->
            when (item) {
                is JsonPrimitive -> item.contentOrNull ?: item.toString()
                else -> item.toString()
            }
        }
        is JsonObject -> element.toString()
        is JsonPrimitive -> element.contentOrNull ?: element.toString()
    }
}

private val fallbackPetPhotos = listOf(
    "https://images.unsplash.com/photo-1580467277788-c6e040296602?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1585943870180-be99fca07f23?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1580467277788-c6e040296602?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1580467277788-c6e040296602?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1585943870180-be99fca07f23?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1580467277788-c6e040296602?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1580467277788-c6e040296602?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1585943870180-be99fca07f23?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1580467277788-c6e040296602?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1580467277788-c6e040296602?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1585943870180-be99fca07f23?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1580467277788-c6e040296602?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1580467277788-c6e040296602?auto=format&fit=crop&w=1200&q=80",
)

private fun buildGroupRosters(
    groups: List<Group>,
    posts: List<CommunityPost>,
    providers: List<ServiceProvider>,
    today: LocalDate,
): Map<String, List<PetRosterItem>> {
    val recentPosts = posts.filter { post ->
        val createdDate = parseRosterDate(post.createdAt) ?: return@filter false
        isInRollingWindow(createdDate, today, days = 7)
    }
    if (recentPosts.isEmpty()) return emptyMap()

    val photoPool = (providers.flatMap { it.imageUrls } + fallbackPetPhotos).distinct()
    if (photoPool.isEmpty()) return emptyMap()

    val entries = recentPosts.mapIndexed { index, post ->
        val createdDate = parseRosterDate(post.createdAt) ?: today
        val petName = extractPetName(post.title, post.body, index)
        val photoUrl = photoPool[(post.id.hashCode().absoluteValue + index) % photoPool.size]
        PetRosterItem(
            id = "community_${post.id}",
            petName = petName,
            photoUrl = photoUrl,
            addedDate = createdDate,
            suburb = post.suburb,
        )
    } + seedTestCommunityRosterEntries(today = today, photoPool = photoPool)

    return groups.associate { group ->
        val roster = entries
            .filter { item -> item.suburb.equals(group.suburb, ignoreCase = true) }
            .dailyShuffle(seed = "group_${group.id}", today = today)
            .take(8)
        group.id to roster
    }
}

private fun buildGroomerRosters(
    providers: List<ServiceProvider>,
    today: LocalDate,
): Map<String, List<PetRosterItem>> {
    return providers
        .filter { it.category == "grooming" }
        .associate { provider ->
            val photos = (provider.imageUrls + fallbackPetPhotos).distinct()
            val seeded = seedTestGroomerRosterEntries(
                providerId = provider.id,
                providerName = provider.name,
                suburb = provider.suburb,
                today = today,
                photos = photos,
            )
            val roster = photos.mapIndexed { index, photo ->
                PetRosterItem(
                    id = "groomer_${provider.id}_$index",
                    petName = "${provider.name.substringBefore(' ')} Pup ${index + 1}",
                    photoUrl = photo,
                    addedDate = today.minusDays((index % 7).toLong()),
                    suburb = provider.suburb,
                )
            } + seeded
                .filter { item -> isInRollingWindow(item.addedDate, today, days = 7) }
                .dailyShuffle(seed = "groomer_${provider.id}", today = today)
                .take(8)
            provider.id to roster
        }
}

private fun seedTestCommunityRosterEntries(
    today: LocalDate,
    photoPool: List<String>,
): List<PetRosterItem> {
    val suburbs = listOf("Surry Hills", "Newtown", "Redfern")
    val names = listOf(
        "Milo",
        "Luna",
        "Waffles",
        "Poppy",
        "Ollie",
        "Maple",
        "Nala",
        "Biscuit",
        "Archie",
    )
    var cursor = 0
    return suburbs.flatMap { suburb ->
        (0..6).map { dayOffset ->
            val name = names[cursor % names.size]
            val photo = photoPool[cursor % photoPool.size]
            val id = "seed_${suburb.replace(" ", "_").lowercase()}_$dayOffset"
            cursor += 1
            PetRosterItem(
                id = id,
                petName = name,
                photoUrl = photo,
                addedDate = today.minusDays(dayOffset.toLong()),
                suburb = suburb,
            )
        }
    }
}

private fun seedTestGroomerRosterEntries(
    providerId: String,
    providerName: String,
    suburb: String,
    today: LocalDate,
    photos: List<String>,
): List<PetRosterItem> {
    val sampleNames = listOf("Teddy", "Coco", "Blue", "Daisy", "Mochi", "Scout", "Pepper", "Ziggy")
    return sampleNames.mapIndexed { index, name ->
        PetRosterItem(
            id = "seed_groomer_${providerId}_$index",
            petName = "$name (${providerName.substringBefore(' ')})",
            photoUrl = photos[index % photos.size],
            addedDate = today.minusDays((index % 7).toLong()),
            suburb = suburb,
        )
    }
}

private fun extractPetName(title: String, body: String, index: Int): String {
    val quotedName = Regex("['\"]([^'\"]{2,20})['\"]").find("$title $body")
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
    if (!quotedName.isNullOrBlank()) return quotedName
    val titleWords = title.split(" ")
        .map { it.trim(',', '.', ':', ';') }
        .filter { it.length in 3..12 && it.firstOrNull()?.isUpperCase() == true }
    return titleWords.firstOrNull() ?: "Park Pup ${index + 1}"
}

private fun parseRosterDate(value: String?): LocalDate? {
    if (value.isNullOrBlank()) return null
    return try {
        Instant.parse(value).atZone(ZoneOffset.UTC).toLocalDate()
    } catch (_: DateTimeParseException) {
        runCatching { LocalDate.parse(value) }.getOrNull()
    }
}

private fun isInRollingWindow(date: LocalDate, today: LocalDate, days: Long): Boolean {
    val earliest = today.minusDays(days)
    return !date.isBefore(earliest) && !date.isAfter(today)
}

private fun List<PetRosterItem>.dailyShuffle(seed: String, today: LocalDate): List<PetRosterItem> {
    return this.sortedBy {
        "${today.toEpochDay()}|$seed|${it.id}".hashCode().absoluteValue
    }
}

private val Int.absoluteValue: Int
    get() = if (this < 0) -this else this
