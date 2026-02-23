package com.petsocial.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.petsocial.app.BuildConfig
import com.petsocial.app.data.AppNotification
import com.petsocial.app.data.ChatCta
import com.petsocial.app.data.ChatResponse
import com.petsocial.app.data.ChatTurn
import com.petsocial.app.data.CalendarEvent
import com.petsocial.app.data.BookingResponse
import com.petsocial.app.data.CommunityEvent
import com.petsocial.app.data.CommunityFunnelMetrics
import com.petsocial.app.data.CommunityPost
import com.petsocial.app.data.CommunityPostCreate
import com.petsocial.app.data.CommunityReport
import com.petsocial.app.data.Group
import com.petsocial.app.data.GroupInvite
import com.petsocial.app.data.HomeCacheSnapshot
import com.petsocial.app.data.NearbyPetBusiness
import com.petsocial.app.data.PetProfileSuggestion
import com.petsocial.app.data.PetSocialRepository
import com.petsocial.app.data.ServiceProvider
import com.petsocial.app.data.ServiceProviderDetailsResponse
import com.petsocial.app.data.ServiceAvailabilitySlot
import com.petsocial.app.location.LocationSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
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
    val bio: String = "Pet parent of Milo. Loves social dog walks and local events.",
    val suburb: String = "Surry Hills",
    val favoriteSuburbs: List<String> = listOf("Newtown", "Redfern"),
)

data class OwnerBooking(
    val id: String,
    val serviceName: String,
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
    val communityEvents: List<CommunityEvent> = emptyList(),
    val selectedProviderDetails: ServiceProviderDetailsResponse? = null,
    val availableSlots: List<ServiceAvailabilitySlot> = emptyList(),
    val availabilityDate: String? = null,
    val serviceMinRating: Float? = null,
    val serviceMaxDistanceKm: Int? = null,
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
    val mutedCommunityKeywords: Set<String> = emptySet(),
    val followedGroupIds: Set<String> = emptySet(),
    val selectedCommunityGroupId: String? = null,
    val selectedSuburb: String = "Surry Hills",
    val selectedRangeCenter: String = "manual",
    val currentLocationSuburb: String? = null,
    val currentLatitude: Double? = null,
    val currentLongitude: Double? = null,
    val activeUserId: String = "user_2",
    val profileInfo: ProfileInfo = ProfileInfo(),
    val isServiceProvider: Boolean = false,
    val ownerBookings: List<OwnerBooking> = emptyList(),
    val joinedEvents: List<JoinedEvent> = emptyList(),
    val favoriteProviderIds: List<String> = emptyList(),
    val providerListings: List<ProviderListing> = emptyList(),
    val providerConfig: ProviderConfig = ProviderConfig(),
    val providerBookings: List<ProviderBooking> = emptyList(),
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
    val isCommunityModerator: Boolean = false,
    val communityWeather: CommunityWeatherSnapshot = CommunityWeatherSnapshot(
        suburb = "Surry Hills",
        temperatureC = 22,
        condition = "Partly cloudy",
        rainChancePercent = 20,
        windKph = 12,
        updatedAtLabel = "Just now (mock)",
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

private const val TEST_DOG_PARK_GROUP_ID = "g_user_dogpark_surry"
private val ENABLE_TEST_SEED_DATA = BuildConfig.USE_MOCK_DATA
private const val STAGING_TEST_SUBURB = "Sunshine West"
private val COMMUNITY_MODERATOR_IDS = setOf("admin", "user_1", "user_3")

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
        name = "Surry Hills Dog Park Crew",
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
            title = "Live now: Surry Hills Splash Social",
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
        if (isStagingTestBuild()) {
            _uiState.value = _uiState.value.copy(
                selectedSuburb = STAGING_TEST_SUBURB,
                selectedRangeCenter = "manual",
                profileInfo = _uiState.value.profileInfo.copy(suburb = STAGING_TEST_SUBURB),
            )
        }
        repository.setActiveUser(_uiState.value.activeUserId)
        refreshMockCommunityWeather()
        startMockWeatherTicker()
    }

    fun loadHomeData(category: String? = _uiState.value.selectedCategory) {
        val state = _uiState.value
        val resolvedCategory = category
        val suburb = if (isStagingTestBuild()) STAGING_TEST_SUBURB else state.selectedSuburb
        val useCurrentLocation = state.selectedRangeCenter == "current" &&
            state.currentLatitude != null &&
            state.currentLongitude != null
        viewModelScope.launch {
            val totalStartNs = System.nanoTime()
            _uiState.value = _uiState.value.copy(loading = true, error = null, selectedCategory = resolvedCategory)
            val fetchStartNs = System.nanoTime()
            runCatching {
                val providers = repository.loadProviders(
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
                val ownerListingProviders = repository.loadProviders(
                    userId = state.activeUserId,
                    includeInactive = true,
                )
                val groups = repository.loadGroups(suburb = suburb)
                val posts = repository.loadPosts(
                    suburb = suburb,
                    postType = if (state.postsSortBy == "lost_found") "lost_found" else null,
                    sortBy = state.postsSortBy,
                    openOnly = if (state.communityOpenOnly) true else null,
                    recentHours = state.communityRecentHours,
                    centerLat = if (useCurrentLocation) state.currentLatitude else null,
                    centerLng = if (useCurrentLocation) state.currentLongitude else null,
                    maxDistanceKm = if (useCurrentLocation && state.serviceMaxDistanceKm != null) {
                        state.serviceMaxDistanceKm.toDouble()
                    } else {
                        null
                    },
                )
                val events = repository.loadEvents(suburb = suburb)
                val ownerBookings = repository.loadOwnerBookings()
                val providerBookings = repository.loadProviderBookings()
                val calendarEvents = repository.loadCalendarEvents(role = state.selectedCalendarRole)
                val notifications = repository.loadNotifications(unreadOnly = false)
                val blockedUsers = runCatching { repository.loadBlockedUsers().blockedUserIds }.getOrDefault(emptyList())
                val moderationReports = runCatching { repository.loadModerationReports(includeResolved = false) }.getOrDefault(emptyList())
                val communityFunnel = runCatching { repository.loadCommunityFunnel(windowHours = 168) }.getOrNull()
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
                    nearbyPetBusinesses = nearbyPetBusinesses,
                    groups = groups,
                    posts = posts,
                    events = events,
                    ownerBookings = ownerBookings,
                    providerBookings = providerBookings,
                    calendarEvents = calendarEvents,
                    notifications = notifications,
                    blockedUserIds = blockedUsers,
                    moderationReports = moderationReports,
                    communityFunnelMetrics = communityFunnel,
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
                val cached = repository.loadHomeCache()
                if (cached != null) {
                    val fetchMs = elapsedMs(fetchStartNs)
                    val applyStartNs = System.nanoTime()
                    applyHomePayload(
                        payload = HomePayload(
                            providers = cached.providers,
                            ownerListingProviders = cached.ownerListingProviders,
                            nearbyPetBusinesses = cached.nearbyPetBusinesses,
                            groups = cached.groups,
                            posts = cached.posts,
                            events = cached.events,
                            ownerBookings = cached.ownerBookings,
                            providerBookings = cached.providerBookings,
                            calendarEvents = cached.calendarEvents,
                            notifications = emptyList(),
                            blockedUserIds = _uiState.value.blockedUserIds,
                            moderationReports = _uiState.value.moderationReports,
                            communityFunnelMetrics = _uiState.value.communityFunnelMetrics,
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
                serviceName = providerById[booking.providerId]?.name ?: booking.providerId,
                date = booking.date,
                timeSlot = booking.timeSlot,
                status = booking.status,
            )
        }
        val existingMessages = current.directMessages
        val seededMessages = if (existingMessages.isEmpty()) {
            seedDirectMessages(activeUserId = current.activeUserId)
        } else {
            existingMessages
        }
        val validReadMessageIds = current.readDirectMessageIds.intersect(seededMessages.map { it.id }.toSet())
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
        val messageThreads = buildMessageThreads(
            activeUserId = _uiState.value.activeUserId,
            providers = providers,
            groups = groups,
            ownerBookings = ownerBookings,
            providerBookings = providerBookings,
            directMessages = seededMessages,
            readMessageIds = validReadMessageIds,
            mutedThreadIds = current.mutedMessageThreadIds,
            pinnedThreadIds = current.pinnedMessageThreadIds,
            blockedParticipantIds = current.blockedUserIds.toSet(),
        )
        val selectedThreadId = current.selectedMessageThreadId
            ?.takeIf { existingId -> messageThreads.any { it.id == existingId } }
            ?: ""
        val validSavedPostIds = current.savedCommunityPostIds.intersect(posts.map { it.id }.toSet())
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
        _uiState.value = current.copy(
            providers = reuseIfEquivalent(current.providers, providers),
            nearbyPetBusinesses = reuseIfEquivalent(current.nearbyPetBusinesses, payload.nearbyPetBusinesses),
            groups = reuseIfEquivalent(current.groups, groups),
            posts = reuseIfEquivalent(current.posts, posts),
            communityEvents = reuseIfEquivalent(current.communityEvents, events),
            ownerBookings = reuseIfEquivalent(current.ownerBookings, ownerBookings),
            providerBookings = reuseIfEquivalent(current.providerBookings, providerBookings),
            calendarEvents = reuseIfEquivalent(current.calendarEvents, payload.calendarEvents),
            messageThreads = reuseIfEquivalent(current.messageThreads, messageThreads),
            selectedMessageThreadId = selectedThreadId.ifBlank { null },
            directMessages = reuseIfEquivalent(current.directMessages, seededMessages),
            readDirectMessageIds = validReadMessageIds,
            savedCommunityPostIds = validSavedPostIds,
            mutedCommunityKeywords = current.mutedCommunityKeywords,
            followedGroupIds = validFollowedGroupIds,
            joinedEvents = reuseIfEquivalent(current.joinedEvents, joinedEvents),
            favoriteProviderIds = reuseIfEquivalent(current.favoriteProviderIds, syncedFavorites),
            providerListings = reuseIfEquivalent(current.providerListings, syncedListings),
            headerRosterPet = boostedGroupRosters.values
                .flatten()
                .dailyShuffle("header", LocalDate.now())
                .firstOrNull(),
            groupPetRosters = reuseIfEquivalent(current.groupPetRosters, boostedGroupRosters),
            groomerPetRosters = reuseIfEquivalent(current.groomerPetRosters, groomerRosters),
            profileInfo = current.profileInfo.copy(suburb = suburb),
            loading = false,
            error = errorMessage,
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
            isCommunityModerator = current.activeUserId in COMMUNITY_MODERATOR_IDS,
            selectedCommunityGroupId = current.selectedCommunityGroupId
                ?.takeIf { selectedId -> groups.any { group -> group.id == selectedId } },
        )
        maybeRunAutoParkCheckIn(reason = "home_payload_applied")
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

    fun startNewBarkThread() {
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

    fun resolveInviteToken(token: String?) {
        val cleanToken = token?.trim().orEmpty()
        if (cleanToken.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.resolveGroupInvite(cleanToken) }
                .onSuccess { invite ->
                    _uiState.value = _uiState.value.copy(
                        pendingInvite = invite,
                        selectedSuburb = invite.suburb,
                        selectedTab = AppTab.Community,
                    )
                    loadHomeData(_uiState.value.selectedCategory)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(error = error.message)
                }
        }
    }

    fun dismissPendingInvite() {
        _uiState.value = _uiState.value.copy(pendingInvite = null)
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
        _uiState.value = _uiState.value.copy(postsSortBy = sortBy)
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
        val state = _uiState.value
        val readMessageIds = markThreadMessagesRead(
            directMessages = state.directMessages,
            activeUserId = state.activeUserId,
            threadId = threadId,
            existingReadIds = state.readDirectMessageIds,
        )
        val refreshedThreads = buildMessageThreads(
            activeUserId = state.activeUserId,
            providers = state.providers,
            groups = state.groups,
            ownerBookings = state.ownerBookings,
            providerBookings = state.providerBookings,
            directMessages = state.directMessages,
            readMessageIds = readMessageIds,
            mutedThreadIds = state.mutedMessageThreadIds,
            pinnedThreadIds = state.pinnedMessageThreadIds,
            blockedParticipantIds = state.blockedUserIds.toSet(),
        )
        _uiState.value = state.copy(
            selectedMessageThreadId = threadId,
            readDirectMessageIds = readMessageIds,
            messageThreads = refreshedThreads,
        )
    }

    fun markMessageThreadRead(threadId: String) {
        if (threadId.isBlank()) return
        val state = _uiState.value
        val readMessageIds = markThreadMessagesRead(
            directMessages = state.directMessages,
            activeUserId = state.activeUserId,
            threadId = threadId,
            existingReadIds = state.readDirectMessageIds,
        )
        val refreshedThreads = buildMessageThreads(
            activeUserId = state.activeUserId,
            providers = state.providers,
            groups = state.groups,
            ownerBookings = state.ownerBookings,
            providerBookings = state.providerBookings,
            directMessages = state.directMessages,
            readMessageIds = readMessageIds,
            mutedThreadIds = state.mutedMessageThreadIds,
            pinnedThreadIds = state.pinnedMessageThreadIds,
            blockedParticipantIds = state.blockedUserIds.toSet(),
        )
        _uiState.value = state.copy(
            readDirectMessageIds = readMessageIds,
            messageThreads = refreshedThreads,
        )
    }

    fun toggleMuteMessageThread(threadId: String) {
        if (threadId.isBlank()) return
        val state = _uiState.value
        val nextMutedIds = if (threadId in state.mutedMessageThreadIds) {
            state.mutedMessageThreadIds - threadId
        } else {
            state.mutedMessageThreadIds + threadId
        }
        val refreshedThreads = buildMessageThreads(
            activeUserId = state.activeUserId,
            providers = state.providers,
            groups = state.groups,
            ownerBookings = state.ownerBookings,
            providerBookings = state.providerBookings,
            directMessages = state.directMessages,
            readMessageIds = state.readDirectMessageIds,
            mutedThreadIds = nextMutedIds,
            pinnedThreadIds = state.pinnedMessageThreadIds,
            blockedParticipantIds = state.blockedUserIds.toSet(),
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
        val refreshedThreads = buildMessageThreads(
            activeUserId = state.activeUserId,
            providers = state.providers,
            groups = state.groups,
            ownerBookings = state.ownerBookings,
            providerBookings = state.providerBookings,
            directMessages = state.directMessages,
            readMessageIds = state.readDirectMessageIds,
            mutedThreadIds = state.mutedMessageThreadIds,
            pinnedThreadIds = nextPinnedIds,
            blockedParticipantIds = state.blockedUserIds.toSet(),
        )
        _uiState.value = state.copy(
            pinnedMessageThreadIds = nextPinnedIds,
            messageThreads = refreshedThreads,
            toastMessage = if (threadId in state.pinnedMessageThreadIds) "Thread unpinned" else "Thread pinned",
        )
    }

    fun clearMessageThreadSelection() {
        val state = _uiState.value
        val selectedThreadId = state.selectedMessageThreadId
        val readMessageIds = if (selectedThreadId.isNullOrBlank()) {
            state.readDirectMessageIds
        } else {
            markThreadMessagesRead(
                directMessages = state.directMessages,
                activeUserId = state.activeUserId,
                threadId = selectedThreadId,
                existingReadIds = state.readDirectMessageIds,
            )
        }
        val refreshedThreads = buildMessageThreads(
            activeUserId = state.activeUserId,
            providers = state.providers,
            groups = state.groups,
            ownerBookings = state.ownerBookings,
            providerBookings = state.providerBookings,
            directMessages = state.directMessages,
            readMessageIds = readMessageIds,
            mutedThreadIds = state.mutedMessageThreadIds,
            pinnedThreadIds = state.pinnedMessageThreadIds,
            blockedParticipantIds = state.blockedUserIds.toSet(),
        )
        _uiState.value = state.copy(
            selectedMessageThreadId = null,
            readDirectMessageIds = readMessageIds,
            messageThreads = refreshedThreads,
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
        val newMessage = DirectMessage(
            id = "dm_${System.currentTimeMillis()}",
            threadId = threadId,
            senderUserId = state.activeUserId,
            recipientUserId = recipientUserId,
            body = trimmed,
        )
        val updatedMessages = state.directMessages + newMessage
        val updatedThreads = state.messageThreads.map { thread ->
            if (thread.id == threadId) {
                thread.copy(lastMessage = trimmed, unreadCount = 0)
            } else {
                thread
            }
        }
        _uiState.value = state.copy(
            directMessages = updatedMessages,
            messageThreads = updatedThreads,
        )
    }

    fun switchAccount(userId: String) {
        if (userId.isBlank()) return
        viewModelScope.launch {
            val authOk = repository.authenticateAsUser(userId)
            repository.setActiveUser(userId)
            _uiState.value = _uiState.value.copy(
                activeUserId = userId,
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
                mutedCommunityKeywords = emptySet(),
                followedGroupIds = emptySet(),
                isCommunityModerator = userId in COMMUNITY_MODERATOR_IDS,
                toastMessage = if (authOk) "Switched to $userId" else "Switched to $userId (guest auth)",
            )
            loadHomeData(_uiState.value.selectedCategory)
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
            _uiState.value = _uiState.value.copy(
                currentLocationSuburb = detectedSuburb.ifBlank { null },
                currentLatitude = snapshot.latitude,
                currentLongitude = snapshot.longitude,
            )
            maybeRunAutoParkCheckIn(reason = "location_update")
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
        if (isStagingTestBuild()) {
            _uiState.value = _uiState.value.copy(
                profileInfo = profileInfo.copy(suburb = STAGING_TEST_SUBURB),
                selectedSuburb = STAGING_TEST_SUBURB,
                selectedRangeCenter = "manual",
                toastMessage = "Profile updated",
            )
            loadHomeData(_uiState.value.selectedCategory)
            return
        }
        _uiState.value = _uiState.value.copy(
            profileInfo = profileInfo,
            selectedSuburb = profileInfo.suburb,
            toastMessage = "Profile updated",
        )
        loadHomeData(_uiState.value.selectedCategory)
    }

    fun setServiceProviderMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            isServiceProvider = enabled,
            toastMessage = if (enabled) "Listing profile enabled" else "Listing profile disabled",
        )
    }

    fun requestBookingEdit(bookingId: String) {
        _uiState.value = _uiState.value.copy(toastMessage = "Reschedule workflow coming next")
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
                    suburb = state.selectedSuburb,
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

    fun createCommunityGroup(name: String) {
        if (name.isBlank()) return
        val suburb = _uiState.value.selectedSuburb
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

    fun createCommunityEvent(title: String, description: String, date: String, groupId: String? = null) {
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
                    postsSortBy = "lost_found",
                    toastMessage = "Lost/found post created",
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
                        postsSortBy = "lost_found",
                        toastMessage = toast,
                    )
                    loadHomeData(_uiState.value.selectedCategory)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(loading = false, error = error.message)
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
            updatedAtLabel = "Updated just now (mock)",
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
        if (deepLink.isBlank()) return
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
                    postsSortBy = if (deepLink.startsWith("post:")) "lost_found" else _uiState.value.postsSortBy,
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
        if (!notification.read) {
            markNotificationRead(notification.id)
        }
    }

    fun syncPushToken() {
        viewModelScope.launch {
            repository.syncDevicePushToken()
        }
    }

    private fun isStagingTestBuild(): Boolean = BuildConfig.ENVIRONMENT.equals("staging", ignoreCase = true)

    private fun applyChatResponse(response: ChatResponse, toast: String? = null) {
        val state = _uiState.value
        val parsed = parseA2uiMessages(response.a2uiMessages)
        val selectedThreadId = state.selectedBarkThreadId
        val activeThread = state.barkThreads.firstOrNull { it.id == selectedThreadId } ?: state.barkThreads.first()
        val updatedThread = activeThread.copy(
            title = resolveBarkThreadTitle(activeThread.title, response.conversation),
            conversation = response.conversation,
            chat = response,
            profileSuggestion = response.profileSuggestion,
            a2uiProfileCard = parsed.first,
            a2uiProviderCard = parsed.second,
            updatedAt = System.currentTimeMillis(),
        )
        _uiState.value = state.copy(
            chat = response,
            conversation = response.conversation,
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

private fun buildMessageThreads(
    activeUserId: String,
    providers: List<ServiceProvider>,
    groups: List<Group>,
    ownerBookings: List<OwnerBooking>,
    providerBookings: List<ProviderBooking>,
    directMessages: List<DirectMessage>,
    readMessageIds: Set<String> = emptySet(),
    mutedThreadIds: Set<String> = emptySet(),
    pinnedThreadIds: Set<String> = emptySet(),
    blockedParticipantIds: Set<String> = emptySet(),
): List<MessageThread> {
    val threadsById = linkedMapOf<String, MessageThread>()
    val unreadById = mutableMapOf<String, Int>()
    val lastMessageById = mutableMapOf<String, String>()

    fun upsert(participantUserId: String, title: String, fallbackMessage: String) {
        if (participantUserId == activeUserId) return
        if (participantUserId in blockedParticipantIds) return
        val id = directThreadId(activeUserId, participantUserId)
        val existing = threadsById[id]
        if (existing == null) {
            threadsById[id] = MessageThread(
                id = id,
                title = title,
                participantUserId = participantUserId,
                participantAccountLabel = accountLabel(participantUserId),
                lastMessage = fallbackMessage,
                unreadCount = 0,
            )
        } else if (existing.title == accountLabel(participantUserId) && title != existing.title) {
            threadsById[id] = existing.copy(title = title)
        }
    }

    providers
        .filter { provider -> provider.ownerUserId != activeUserId }
        .take(4)
        .forEach { provider ->
            val ownerUserId = provider.ownerUserId ?: "user_1"
            upsert(
                participantUserId = ownerUserId,
                title = provider.name,
                fallbackMessage = "Hi, I'd like to confirm a booking time.",
            )
        }

    ownerBookings.take(4).forEach { booking ->
        val participantUserId = providers.firstOrNull { it.name == booking.serviceName }?.ownerUserId ?: "user_1"
        upsert(
            participantUserId = participantUserId,
            title = booking.serviceName,
            fallbackMessage = "Booking status: ${booking.status}",
        )
    }

    providerBookings.take(4).forEach { booking ->
        val participantUserId = booking.ownerUserId.ifBlank { "user_1" }
        upsert(
            participantUserId = participantUserId,
            title = "${booking.petName} booking",
            fallbackMessage = "Owner asked about ${booking.timeSlot}.",
        )
    }

    groups.take(2).forEach { group ->
        val participantUserId = if (group.ownerUserId == activeUserId) "user_1" else (group.ownerUserId ?: "user_1")
        upsert(
            participantUserId = participantUserId,
            title = "${group.name} admins",
            fallbackMessage = "Can we approve the next join request?",
        )
    }

    directMessages.forEach { message ->
        val participantUserId = when (activeUserId) {
            message.senderUserId -> message.recipientUserId
            message.recipientUserId -> message.senderUserId
            else -> null
        } ?: return@forEach
        val threadId = directThreadId(activeUserId, participantUserId)
        if (participantUserId in blockedParticipantIds) return@forEach
        upsert(
            participantUserId = participantUserId,
            title = threadsById[threadId]?.title ?: accountLabel(participantUserId),
            fallbackMessage = message.body,
        )
        lastMessageById[threadId] = message.body
        if (
            message.recipientUserId == activeUserId &&
            message.senderUserId != activeUserId &&
            message.id !in readMessageIds
        ) {
            unreadById[threadId] = (unreadById[threadId] ?: 0) + 1
        }
    }

    return threadsById.values
        .map { thread ->
            thread.copy(
                lastMessage = lastMessageById[thread.id] ?: thread.lastMessage,
                unreadCount = unreadById[thread.id] ?: 0,
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

private fun markThreadMessagesRead(
    directMessages: List<DirectMessage>,
    activeUserId: String,
    threadId: String,
    existingReadIds: Set<String>,
): Set<String> {
    if (threadId.isBlank()) return existingReadIds
    val newlyRead = directMessages
        .asSequence()
        .filter { message ->
            message.threadId == threadId &&
                message.recipientUserId == activeUserId &&
                message.senderUserId != activeUserId
        }
        .map { it.id }
        .toSet()
    return if (newlyRead.isEmpty()) existingReadIds else existingReadIds + newlyRead
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

private fun isCommunityNotification(category: String): Boolean {
    val normalized = category.lowercase()
    return normalized.startsWith("community") || normalized.contains("group")
}

private fun isMessageNotification(category: String): Boolean {
    return category.lowercase().contains("message")
}

private fun seedDirectMessages(activeUserId: String): List<DirectMessage> {
    val pairs = listOf("user_1", "user_2", "user_3", "user_4")
        .filter { it != activeUserId }
        .take(3)
    return pairs.flatMap { otherUserId ->
        val threadId = directThreadId(activeUserId, otherUserId)
        listOf(
            DirectMessage(
                id = "${threadId}_1",
                threadId = threadId,
                senderUserId = otherUserId,
                recipientUserId = activeUserId,
                body = "Hi from ${accountLabel(otherUserId)}.",
            ),
            DirectMessage(
                id = "${threadId}_2",
                threadId = threadId,
                senderUserId = activeUserId,
                recipientUserId = otherUserId,
                body = "Thanks, let's coordinate here.",
            ),
        )
    }
}

private fun directThreadId(userA: String, userB: String): String {
    val sorted = listOf(userA, userB).sorted()
    return "dm_${sorted[0]}_${sorted[1]}"
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
    val nearbyPetBusinesses: List<NearbyPetBusiness>,
    val groups: List<Group>,
    val posts: List<CommunityPost>,
    val events: List<CommunityEvent>,
    val ownerBookings: List<BookingResponse>,
    val providerBookings: List<BookingResponse>,
    val calendarEvents: List<CalendarEvent>,
    val notifications: List<AppNotification>,
    val blockedUserIds: List<String>,
    val moderationReports: List<CommunityReport>,
    val communityFunnelMetrics: CommunityFunnelMetrics?,
)

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
    "https://loremflickr.com/640/640/bordoodle,dog?lock=201",
    "https://loremflickr.com/640/640/black,white,dog?lock=202",
    "https://loremflickr.com/640/640/cavoodle,dog?lock=203",
    "https://loremflickr.com/640/640/brown,toy,dog,cavoodle?lock=204",
    "https://images.unsplash.com/photo-1537151608828-ea2b11777ee8",
    "https://images.unsplash.com/photo-1525253013412-55c1a69a5738",
    "https://images.unsplash.com/photo-1543466835-00a7907e9de1",
    "https://images.unsplash.com/photo-1548199973-03cce0bbc87b",
    "https://images.unsplash.com/photo-1517423440428-a5a00ad493e8",
    "https://images.unsplash.com/photo-1507146426996-ef05306b995a",
    "https://images.unsplash.com/photo-1477884213360-7e9d7dcc1e48",
    "https://images.unsplash.com/photo-1583511655857-d19b40a7a54e",
    "https://images.unsplash.com/photo-1561037404-61cd46aa615b",
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
