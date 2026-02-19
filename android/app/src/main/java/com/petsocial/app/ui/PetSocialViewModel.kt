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
import com.petsocial.app.data.CommunityPost
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
    val messageThreads: List<MessageThread> = emptyList(),
    val selectedMessageThreadId: String? = null,
    val directMessages: List<DirectMessage> = emptyList(),
    val streamingAssistantText: String = "",
    val selectedTab: AppTab = AppTab.Services,
    val selectedCategory: String? = null,
    val servicesViewMode: String = "list",
    val servicesSearchQuery: String = "",
    val servicesSortBy: String = "relevance",
    val postsSortBy: String = "relevance",
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
    val notifications: List<AppNotification> = emptyList(),
)

private fun accountLabel(userId: String): String = when (userId) {
    "user_1" -> "Account A"
    "user_2" -> "Account B"
    "user_3" -> "Account C"
    "user_4" -> "Account D"
    else -> userId
}

private const val TEST_DOG_PARK_GROUP_ID = "g_user_dogpark_surry"
private val ENABLE_TEST_SEED_DATA = BuildConfig.USE_MOCK_DATA
private const val STAGING_TEST_SUBURB = "Sunshine West"

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
    )
    return if (existing == null) {
        listOf(seeded) + groups
    } else {
        groups.map { group -> if (group.id == TEST_DOG_PARK_GROUP_ID) seeded else group }
    }
}

private fun ensureSeedDogParkPosts(posts: List<CommunityPost>): List<CommunityPost> {
    if (!ENABLE_TEST_SEED_DATA) return posts
    if (posts.any { it.id == "p_dogpark_seed_1" }) return posts
    val seeded = listOf(
        CommunityPost("p_dogpark_seed_1", "group_post", "Dog park check-in: Luna", "Luna joined the dog park crew this week.", "Surry Hills", "2026-02-18T08:00:00Z"),
        CommunityPost("p_dogpark_seed_2", "group_post", "Dog park check-in: Milo", "Milo joined the sunrise zoomie circle.", "Surry Hills", "2026-02-17T07:00:00Z"),
        CommunityPost("p_dogpark_seed_3", "group_post", "Dog park check-in: Maple", "Maple joined and made new friends.", "Surry Hills", "2026-02-16T18:00:00Z"),
        CommunityPost("p_dogpark_seed_4", "group_post", "Dog park check-in: Teddy", "Teddy joined the fetch meetup.", "Surry Hills", "2026-02-15T10:00:00Z"),
        CommunityPost("p_dogpark_seed_5", "group_post", "Dog park check-in: Nala", "Nala joined the small-dogs social.", "Surry Hills", "2026-02-14T11:15:00Z"),
        CommunityPost("p_dogpark_seed_6", "group_post", "Dog park check-in: Archie", "Archie joined and loved agility drills.", "Surry Hills", "2026-02-13T17:25:00Z"),
        CommunityPost("p_dogpark_seed_7", "group_post", "Dog park check-in: Poppy", "Poppy joined the evening play session.", "Surry Hills", "2026-02-12T19:30:00Z"),
    )
    return seeded + posts
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

    init {
        if (isStagingTestBuild()) {
            _uiState.value = _uiState.value.copy(
                selectedSuburb = STAGING_TEST_SUBURB,
                selectedRangeCenter = "manual",
                selectedCategory = "grooming",
                profileInfo = _uiState.value.profileInfo.copy(suburb = STAGING_TEST_SUBURB),
            )
        }
        repository.setActiveUser(_uiState.value.activeUserId)
    }

    fun loadHomeData(category: String? = _uiState.value.selectedCategory) {
        val state = _uiState.value
        val resolvedCategory = if (isStagingTestBuild()) "grooming" else category
        val suburb = if (isStagingTestBuild()) STAGING_TEST_SUBURB else state.selectedSuburb
        val useCurrentLocation = state.selectedRangeCenter == "current" &&
            state.currentLatitude != null &&
            state.currentLongitude != null
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null, selectedCategory = resolvedCategory)
            runCatching {
                val providers = repository.loadProviders(
                    category = resolvedCategory,
                    suburb = suburb,
                    minRating = state.serviceMinRating?.toDouble(),
                    maxDistanceKm = state.serviceMaxDistanceKm?.toDouble(),
                    userLat = if (useCurrentLocation) state.currentLatitude else null,
                    userLng = if (useCurrentLocation) state.currentLongitude else null,
                    query = state.servicesSearchQuery.ifBlank { null },
                    sortBy = state.servicesSortBy,
                )
                val groups = repository.loadGroups(suburb = suburb)
                val posts = repository.loadPosts(suburb = suburb, sortBy = state.postsSortBy)
                val events = repository.loadEvents(suburb = suburb)
                val ownerBookings = repository.loadOwnerBookings()
                val providerBookings = repository.loadProviderBookings()
                val calendarEvents = repository.loadCalendarEvents(role = state.selectedCalendarRole)
                val notifications = repository.loadNotifications(unreadOnly = false)
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
                    nearbyPetBusinesses = nearbyPetBusinesses,
                    groups = groups,
                    posts = posts,
                    events = events,
                    ownerBookings = ownerBookings,
                    providerBookings = providerBookings,
                    calendarEvents = calendarEvents,
                    notifications = notifications,
                )
            }.onSuccess { payload ->
                repository.saveHomeCache(
                    HomeCacheSnapshot(
                        providers = payload.providers,
                        nearbyPetBusinesses = payload.nearbyPetBusinesses,
                        groups = payload.groups,
                        posts = payload.posts,
                        events = payload.events,
                        ownerBookings = payload.ownerBookings,
                        providerBookings = payload.providerBookings,
                        calendarEvents = payload.calendarEvents,
                    ),
                )
                applyHomePayload(
                    payload = payload,
                    suburb = suburb,
                    errorMessage = null,
                    isOfflineMode = false,
                    hasPendingSync = false,
                )
            }.onFailure { error ->
                val cached = repository.loadHomeCache()
                if (cached != null) {
                    applyHomePayload(
                        payload = HomePayload(
                            providers = cached.providers,
                            nearbyPetBusinesses = cached.nearbyPetBusinesses,
                            groups = cached.groups,
                            posts = cached.posts,
                            events = cached.events,
                            ownerBookings = cached.ownerBookings,
                            providerBookings = cached.providerBookings,
                            calendarEvents = cached.calendarEvents,
                            notifications = emptyList(),
                        ),
                        suburb = suburb,
                        errorMessage = error.message ?: "Network unavailable",
                        isOfflineMode = true,
                        hasPendingSync = true,
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = error.message,
                        isOfflineMode = true,
                        hasPendingSync = true,
                    )
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
    ) {
        val providers = payload.providers
        val groups = ensureSeedDogParkGroup(
            groups = payload.groups,
            activeUserId = _uiState.value.activeUserId,
        )
        val posts = ensureSeedDogParkPosts(payload.posts)
        val events = payload.events
        val providerById = providers.associateBy { it.id }
        val existingFavoriteIds = _uiState.value.favoriteProviderIds
        val syncedFavorites = if (existingFavoriteIds.isEmpty()) {
            providers.take(3).map { it.id }
        } else {
            existingFavoriteIds.filter { id -> providers.any { provider -> provider.id == id } }
        }
        val existingListings = _uiState.value.providerListings
        val syncedListings = if (existingListings.isEmpty()) {
            providers.take(2).map { provider ->
                ProviderListing(
                    id = "listing_${provider.id}",
                    title = provider.name,
                    category = provider.category.replace("_", " "),
                    status = "active",
                    priceFrom = provider.priceFrom,
                )
            }
        } else {
            existingListings
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
        val existingMessages = _uiState.value.directMessages
        val seededMessages = if (existingMessages.isEmpty()) {
            seedDirectMessages(activeUserId = _uiState.value.activeUserId)
        } else {
            existingMessages
        }
        val messageThreads = buildMessageThreads(
            activeUserId = _uiState.value.activeUserId,
            providers = providers,
            groups = groups,
            ownerBookings = ownerBookings,
            providerBookings = providerBookings,
            directMessages = seededMessages,
        )
        val selectedThreadId = _uiState.value.selectedMessageThreadId
            ?.takeIf { existingId -> messageThreads.any { it.id == existingId } }
            ?: ""
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
        _uiState.value = _uiState.value.copy(
            providers = providers,
            nearbyPetBusinesses = payload.nearbyPetBusinesses,
            groups = groups,
            posts = posts,
            communityEvents = events,
            ownerBookings = ownerBookings,
            providerBookings = providerBookings,
            calendarEvents = payload.calendarEvents,
            messageThreads = messageThreads,
            selectedMessageThreadId = selectedThreadId.ifBlank { null },
            directMessages = seededMessages,
            joinedEvents = joinedEvents,
            favoriteProviderIds = syncedFavorites,
            providerListings = syncedListings,
            headerRosterPet = boostedGroupRosters.values
                .flatten()
                .dailyShuffle("header", LocalDate.now())
                .firstOrNull(),
            groupPetRosters = boostedGroupRosters,
            groomerPetRosters = groomerRosters,
            profileInfo = _uiState.value.profileInfo.copy(suburb = suburb),
            loading = false,
            error = errorMessage,
            isOfflineMode = isOfflineMode,
            hasPendingSync = hasPendingSync,
            notifications = payload.notifications,
        )
    }

    fun switchTab(tab: AppTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
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
        _uiState.value = _uiState.value.copy(servicesSearchQuery = query)
        loadHomeData(_uiState.value.selectedCategory)
    }

    fun updateServicesSortBy(sortBy: String) {
        _uiState.value = _uiState.value.copy(servicesSortBy = sortBy)
        loadHomeData(_uiState.value.selectedCategory)
    }

    fun updatePostsSortBy(sortBy: String) {
        _uiState.value = _uiState.value.copy(postsSortBy = sortBy)
        loadHomeData(_uiState.value.selectedCategory)
    }

    fun selectMessageThread(threadId: String) {
        _uiState.value = _uiState.value.copy(selectedMessageThreadId = threadId)
    }

    fun clearMessageThreadSelection() {
        _uiState.value = _uiState.value.copy(selectedMessageThreadId = null)
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
        loadHomeData(_uiState.value.selectedCategory)
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
            toastMessage = if (enabled) "Service provider profile enabled" else "Service provider profile disabled",
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

    fun editProviderListing(listingId: String) {
        _uiState.value = _uiState.value.copy(
            providerListings = _uiState.value.providerListings.map { listing ->
                if (listing.id == listingId) listing.copy(title = "${listing.title} (Updated)") else listing
            },
            toastMessage = "Service listing edited",
        )
    }

    fun cancelProviderListing(listingId: String) {
        _uiState.value = _uiState.value.copy(
            providerListings = _uiState.value.providerListings.map { listing ->
                if (listing.id == listingId) listing.copy(status = "cancelled") else listing
            },
            toastMessage = "Service listing cancelled",
        )
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
        val suburb = _uiState.value.selectedSuburb
        viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                loading = true,
                error = null,
                selectedTab = AppTab.BarkAI,
                streamingAssistantText = "",
                conversation = _uiState.value.conversation + ChatTurn(role = "user", content = trimmedMessage),
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
                    _uiState.value = _uiState.value.copy(
                        error = error.message,
                        loading = false,
                        streamingAssistantText = "",
                    )
                }
        }
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
                        append("Opened Services for ")
                        append(if (category == "grooming") "groomers" else "dog walkers")
                        distanceKm?.let { append(" within $it km") }
                        append(".")
                    },
                ),
            toastMessage = "Applied BarkAI search to Services",
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
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching {
                repository.createLostFoundPost(
                    title = title,
                    body = body,
                    suburb = suburb,
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    selectedTab = AppTab.Community,
                    toastMessage = "Lost/found post created",
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

    fun markNotificationRead(notificationId: String) {
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

    fun syncPushToken() {
        viewModelScope.launch {
            repository.syncDevicePushToken()
        }
    }

    private fun isStagingTestBuild(): Boolean = BuildConfig.ENVIRONMENT.equals("staging", ignoreCase = true)

    private fun applyChatResponse(response: ChatResponse, toast: String? = null) {
        val parsed = parseA2uiMessages(response.a2uiMessages)
        _uiState.value = _uiState.value.copy(
            chat = response,
            conversation = response.conversation,
            profileSuggestion = response.profileSuggestion,
            a2uiProfileCard = parsed.first,
            a2uiProviderCard = parsed.second,
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

private fun buildMessageThreads(
    activeUserId: String,
    providers: List<ServiceProvider>,
    groups: List<Group>,
    ownerBookings: List<OwnerBooking>,
    providerBookings: List<ProviderBooking>,
    directMessages: List<DirectMessage>,
): List<MessageThread> {
    val threadsById = linkedMapOf<String, MessageThread>()
    val unreadById = mutableMapOf<String, Int>()
    val lastMessageById = mutableMapOf<String, String>()

    fun upsert(participantUserId: String, title: String, fallbackMessage: String) {
        if (participantUserId == activeUserId) return
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
        upsert(
            participantUserId = participantUserId,
            title = threadsById[threadId]?.title ?: accountLabel(participantUserId),
            fallbackMessage = message.body,
        )
        lastMessageById[threadId] = message.body
        if (message.recipientUserId == activeUserId && message.senderUserId != activeUserId) {
            unreadById[threadId] = (unreadById[threadId] ?: 0) + 1
        }
    }

    return threadsById.values
        .map { thread ->
            thread.copy(
                lastMessage = lastMessageById[thread.id] ?: thread.lastMessage,
                unreadCount = unreadById[thread.id] ?: 0,
            )
        }
        .sortedWith(compareByDescending<MessageThread> { it.unreadCount }.thenBy { it.title })
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
    val nearbyPetBusinesses: List<NearbyPetBusiness>,
    val groups: List<Group>,
    val posts: List<CommunityPost>,
    val events: List<CommunityEvent>,
    val ownerBookings: List<BookingResponse>,
    val providerBookings: List<BookingResponse>,
    val calendarEvents: List<CalendarEvent>,
    val notifications: List<AppNotification>,
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
    "https://images.unsplash.com/photo-1517849845537-4d257902454a",
    "https://images.unsplash.com/photo-1450778869180-41d0601e046e",
    "https://images.unsplash.com/photo-1518717758536-85ae29035b6d",
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
