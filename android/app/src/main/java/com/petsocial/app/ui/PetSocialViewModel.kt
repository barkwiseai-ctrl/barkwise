package com.petsocial.app.ui

import android.os.Trace
import android.util.Log
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
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
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
    val participantAvatarUrl: String? = null,
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

data class MessageTarget(
    val userId: String? = null,
    val threadId: String? = null,
    val source: String = "unknown",
)

internal sealed interface MessageOpenResolution {
    data class SelectExistingThread(val threadId: String) : MessageOpenResolution
    data class CreateSyntheticThread(
        val selectedThreadId: String,
        val messageThreads: List<MessageThread>,
    ) : MessageOpenResolution
    data object NoTarget : MessageOpenResolution
}

internal data class OnboardingCompletionResolution(
    val selectedThreadId: String,
    val barkThreads: List<BarkThread>,
    val chat: ChatResponse?,
    val conversation: List<ChatTurn>,
)

internal data class ProviderStateResolution(
    val providerModeEnabled: Boolean,
    val hasProviderListings: Boolean,
    val canLoadProviderInbox: Boolean,
)

internal data class HomePayloadStateResolution(
    val providerState: ProviderStateResolution,
    val selectedMessageThreadId: String?,
    val selectedCommunityGroupId: String?,
)

internal data class SessionResetResolution(
    val navigation: NavigationState,
    val providerModeEnabled: Boolean,
    val hasProviderListings: Boolean,
    val canLoadProviderInbox: Boolean,
    val authRequired: Boolean,
    val authOtpRequested: Boolean = false,
    val authInviteId: String = "",
    val authEmail: String = "",
    val authOtpExpiresAt: String? = null,
    val authInFlight: Boolean = false,
    val providers: List<ServiceProvider> = emptyList(),
    val nearbyPetBusinesses: List<NearbyPetBusiness> = emptyList(),
    val groups: List<Group> = emptyList(),
    val posts: List<CommunityPost> = emptyList(),
    val communityCommentsByPostId: Map<String, List<CommunityComment>> = emptyMap(),
    val communityEvents: List<CommunityEvent> = emptyList(),
    val ownerBookings: List<OwnerBooking> = emptyList(),
    val providerBookings: List<ProviderBooking> = emptyList(),
    val calendarEvents: List<CalendarEvent> = emptyList(),
    val messageThreads: List<MessageThread> = emptyList(),
    val directMessages: List<DirectMessage> = emptyList(),
    val readDirectMessageIds: Set<String> = emptySet(),
    val savedCommunityPostIds: Set<String> = emptySet(),
    val savedCommunityEventIds: Set<String> = emptySet(),
    val latestGroupInvites: Map<String, GroupInvite> = emptyMap(),
    val friendProfiles: List<FriendProfile> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
    val readLocalNotificationIds: Set<String> = emptySet(),
    val acknowledgedCommunityNotificationIds: Set<String> = emptySet(),
    val acknowledgedMessageNotificationIds: Set<String> = emptySet(),
    val loading: Boolean = false,
    val error: String? = null,
    val isCommunityModerator: Boolean,
    val toastMessage: String,
)

internal data class TabSwitchResolution(
    val navigation: NavigationState,
    val directMessages: List<DirectMessage>,
    val acknowledgedCommunityNotificationIds: Set<String>,
    val acknowledgedMessageNotificationIds: Set<String>,
)

internal sealed interface FriendQrTokenResolution {
    data class Verify(val token: String) : FriendQrTokenResolution
    data class Invalid(val toastMessage: String) : FriendQrTokenResolution
}

internal enum class FriendMutationAction {
    AddOrUpdate,
    Remove,
}

internal sealed interface FriendMutationResolution {
    data object NoChange : FriendMutationResolution
    data class ToastOnly(val toastMessage: String) : FriendMutationResolution
    data class StateUpdate(
        val friendProfiles: List<FriendProfile>,
        val messageThreads: List<MessageThread>,
        val toastMessage: String,
    ) : FriendMutationResolution
}

internal sealed interface QuoteOfferSubmissionResolution {
    data object Ignore : QuoteOfferSubmissionResolution
    data class Toast(val toastMessage: String) : QuoteOfferSubmissionResolution
    data class Submit(
        val inboxItemId: String,
        val quoteRequestId: String,
        val providerId: String,
        val providerName: String,
        val priceCents: Int,
        val proposedDate: String,
        val proposedTimeSlot: String,
        val expiresAt: String,
        val note: String,
    ) : QuoteOfferSubmissionResolution
}

internal sealed interface BookingRequestResolution {
    data object Ignore : BookingRequestResolution
    data class Toast(val toastMessage: String) : BookingRequestResolution
    data class Submit(
        val providerId: String,
        val date: String,
        val timeSlot: String,
        val note: String,
        val approvalHint: String,
    ) : BookingRequestResolution
}

internal sealed interface ServiceQuoteRequestResolution {
    data class Toast(val toastMessage: String) : ServiceQuoteRequestResolution
    data class Submit(
        val category: String,
        val preferredWindow: String,
        val petDetails: String,
        val note: String,
        val suburb: String?,
    ) : ServiceQuoteRequestResolution
}

internal sealed interface BarkAiEntryResolution {
    data class StayOnOnboarding(val selectedThreadId: String) : BarkAiEntryResolution
    data class StartNewThread(
        val selectedThreadId: String,
        val barkThreads: List<BarkThread>,
    ) : BarkAiEntryResolution
}

internal data class NotificationRoute(
    val tab: AppTab,
    val selectedCommunityGroupId: String? = null,
    val providerId: String? = null,
    val shouldReload: Boolean = false,
)

internal data class NotificationOpenResolution(
    val route: NotificationRoute?,
    val postsSortBy: String,
    val shouldMarkRead: Boolean,
    val shouldReloadHome: Boolean,
    val providerIdToLoad: String? = null,
)

internal data class HomeLoadIndicatorState(
    val loading: Boolean,
    val loadingProviderInbox: Boolean,
)

enum class HomeRefreshScope {
    Full,
    ActiveTab,
}

internal data class HomeRefreshPlan(
    val fetchServices: Boolean,
    val fetchOwnerListings: Boolean,
    val fetchCommunity: Boolean,
    val fetchProfilePane: Boolean,
    val fetchMessages: Boolean,
    val fetchNotifications: Boolean,
    val fetchProfileInfo: Boolean,
    val fetchBlockedUsers: Boolean,
    val fetchModerationReports: Boolean,
    val fetchFunnels: Boolean,
)

internal data class LocalNotificationStateResolution(
    val notifications: List<AppNotification>,
    val readLocalNotificationIds: Set<String>,
    val acknowledgedCommunityNotificationIds: Set<String>,
    val acknowledgedMessageNotificationIds: Set<String>,
    val toastMessage: String? = null,
)

data class ProfileInfo(
    val displayName: String = "Alex Wong",
    val email: String = "alex@example.com",
    val phone: String = "+61 412 345 678",
    val humanPronouns: String = "",
    val humanRoleLabel: String = "Member",
    val serviceProviderMode: Boolean = false,
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
    val threadId: String? = null,
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
    val threadId: String? = null,
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

data class NavigationState(
    val selectedProviderDetails: ServiceProviderDetailsResponse? = null,
    val selectedBarkThreadId: String = "bark_thread_1",
    val onboardingActive: Boolean = false,
    val onboardingStep: Int = 0,
    val selectedMessageThreadId: String? = null,
    val selectedTab: AppTab = AppTab.Profile,
    val profileNotificationFilter: String = "all",
    val selectedCommunityGroupId: String? = null,
    val pendingInvite: GroupInvite? = null,
)

data class UiState(
    val providers: List<ServiceProvider> = emptyList(),
    val nearbyPetBusinesses: List<NearbyPetBusiness> = emptyList(),
    val groups: List<Group> = emptyList(),
    val posts: List<CommunityPost> = emptyList(),
    val communityCommentsByPostId: Map<String, List<CommunityComment>> = emptyMap(),
    val loadingCommentPostIds: Set<String> = emptySet(),
    val communityEvents: List<CommunityEvent> = emptyList(),
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
    val onboardingOwnerName: String = "",
    val onboardingDogName: String = "",
    val onboardingSuburb: String = "",
    val onboardingPhotoCaptured: Boolean = false,
    val testProfileMode: String = TEST_PROFILE_MODE_READY,
    val profileIdentityHeaderVisible: Boolean = false,
    val friendQrPayload: String = "",
    val friendQrExpiresAt: String? = null,
    val friendQrLoading: Boolean = false,
    val friendProfiles: List<FriendProfile> = emptyList(),
    val messageThreads: List<MessageThread> = emptyList(),
    val directMessages: List<DirectMessage> = emptyList(),
    val readDirectMessageIds: Set<String> = emptySet(),
    val mutedMessageThreadIds: Set<String> = emptySet(),
    val pinnedMessageThreadIds: Set<String> = emptySet(),
    val streamingAssistantText: String = "",
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
    val providerModeEnabled: Boolean = false,
    val hasProviderListings: Boolean = false,
    val canLoadProviderInbox: Boolean = false,
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
    val navigation: NavigationState = NavigationState(),
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
) {
    val selectedProviderDetails: ServiceProviderDetailsResponse? get() = navigation.selectedProviderDetails
    val selectedBarkThreadId: String get() = navigation.selectedBarkThreadId
    val onboardingActive: Boolean get() = navigation.onboardingActive
    val onboardingStep: Int get() = navigation.onboardingStep
    val selectedMessageThreadId: String? get() = navigation.selectedMessageThreadId
    val selectedTab: AppTab get() = navigation.selectedTab
    val profileNotificationFilter: String get() = navigation.profileNotificationFilter
    val selectedCommunityGroupId: String? get() = navigation.selectedCommunityGroupId
    val pendingInvite: GroupInvite? get() = navigation.pendingInvite
    val isServiceProvider: Boolean get() = providerModeEnabled
}

data class ShellUiState(
    val activeUserId: String = "user_2",
    val toastMessage: String? = null,
    val selectedTab: AppTab = AppTab.Profile,
    val selectedProviderDetails: ServiceProviderDetailsResponse? = null,
    val selectedMessageThreadId: String? = null,
    val headerRosterPet: PetRosterItem? = null,
    val selectedSuburb: String = "Surry Hills",
    val selectedRangeCenter: String = "manual",
    val currentLocationSuburb: String? = null,
    val serviceMaxDistanceKm: Int? = null,
    val error: String? = null,
    val loading: Boolean = false,
    val hasPendingSync: Boolean = false,
    val isOfflineMode: Boolean = false,
    val unreadNotificationCount: Int = 0,
    val unreadCommunityNotificationCount: Int = 0,
    val unreadMessageNotificationCount: Int = 0,
)

data class ServicesTabUiState(
    val providers: List<ServiceProvider> = emptyList(),
    val nearbyPetBusinesses: List<NearbyPetBusiness> = emptyList(),
    val groomerPetRosters: Map<String, List<PetRosterItem>> = emptyMap(),
    val recommendationSuburb: String? = null,
    val recommendationSource: String = "none",
    val selectedCategory: String? = null,
    val viewMode: String = "list",
    val searchQuery: String = "",
    val sortBy: String = "relevance",
    val loading: Boolean = false,
    val selectedDetails: ServiceProviderDetailsResponse? = null,
    val availableSlots: List<ServiceAvailabilitySlot> = emptyList(),
    val availabilityDate: String? = null,
    val minRating: Float? = null,
    val maxDistanceKm: Int? = null,
)

data class BarkAiTabUiState(
    val loading: Boolean = false,
    val chatResponse: ChatResponse? = null,
    val conversation: List<ChatTurn> = emptyList(),
    val streamingAssistantText: String = "",
    val profileSuggestion: PetProfileSuggestion? = null,
    val a2uiProfileCard: A2uiCardState? = null,
    val a2uiProviderCard: A2uiCardState? = null,
    val barkThreads: List<BarkThread> = emptyList(),
    val selectedBarkThreadId: String = "bark_thread_1",
    val onboardingMode: Boolean = false,
    val onboardingNeedsPhoto: Boolean = false,
)

data class CommunityTabUiState(
    val activeUserId: String = "user_2",
    val loading: Boolean = false,
    val suburb: String = "Surry Hills",
    val currentLocationSuburb: String? = null,
    val currentLatitude: Double? = null,
    val currentLongitude: Double? = null,
    val postsSortBy: String = "relevance",
    val selectedGroupId: String? = null,
    val groups: List<Group> = emptyList(),
    val groupPetRosters: Map<String, List<PetRosterItem>> = emptyMap(),
    val latestGroupInvites: Map<String, GroupInvite> = emptyMap(),
    val blockedUserIds: List<String> = emptyList(),
    val savedPostIds: Set<String> = emptySet(),
    val savedEventIds: Set<String> = emptySet(),
    val mutedKeywords: Set<String> = emptySet(),
    val followedGroupIds: Set<String> = emptySet(),
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
    val autoParkCheckInQuorumCount: Int = 0,
    val autoParkCheckInQuorumThreshold: Int = 3,
    val autoParkCheckInQuorumWindowMinutes: Int = 20,
    val posts: List<CommunityPost> = emptyList(),
    val postCommentsByPostId: Map<String, List<CommunityComment>> = emptyMap(),
    val loadingCommentPostIds: Set<String> = emptySet(),
    val isCommunityModerator: Boolean = false,
    val events: List<CommunityEvent> = emptyList(),
    val messageThreads: List<MessageThread> = emptyList(),
)

data class MessagesTabUiState(
    val activeUserId: String = "user_2",
    val threads: List<MessageThread> = emptyList(),
    val mutedThreadIds: Set<String> = emptySet(),
    val pinnedThreadIds: Set<String> = emptySet(),
    val unreadNotificationCount: Int = 0,
    val selectedThreadId: String? = null,
    val messages: List<DirectMessage> = emptyList(),
)

data class ProfileTabUiState(
    val profileInfo: ProfileInfo = ProfileInfo(),
    val providerModeEnabled: Boolean = false,
    val hasProviderListings: Boolean = false,
    val canLoadProviderInbox: Boolean = false,
    val activeUserId: String = "user_2",
    val friendProfiles: List<FriendProfile> = emptyList(),
    val joinedEvents: List<JoinedEvent> = emptyList(),
    val ownerBookings: List<OwnerBooking> = emptyList(),
    val providerListings: List<ProviderListing> = emptyList(),
    val providerBookings: List<ProviderBooking> = emptyList(),
    val providerInboxItems: List<ProviderInboxItem> = emptyList(),
    val loadingProviderInbox: Boolean = false,
    val sendingQuoteOfferItemIds: Set<String> = emptySet(),
    val isSubmittingProviderInboxAction: Boolean = false,
    val calendarEvents: List<CalendarEvent> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
    val activationFunnelMetrics: CommunityActivationFunnel? = null,
    val notifyFollowedGroupAlerts: Boolean = true,
    val notifySavedPostUpdates: Boolean = true,
    val notifySafetyAlerts: Boolean = true,
    val showIdentityHeader: Boolean = false,
    val friendQrPayload: String = "",
    val friendQrExpiresAt: String? = null,
    val friendQrLoading: Boolean = false,
)

data class AuthDialogUiState(
    val isRequired: Boolean = false,
    val inviteId: String = "",
    val email: String = "",
    val otpRequested: Boolean = false,
    val otpExpiresAt: String? = null,
    val inFlight: Boolean = false,
    val error: String? = null,
)

data class PendingInviteUiState(
    val invite: GroupInvite? = null,
    val loading: Boolean = false,
)

private fun UiState.toShellUiState(): ShellUiState {
    val unreadNotifications = notifications.count { notification -> !notification.read }
    val unreadCommunityNotifications = notifications.count { notification ->
        !notification.read &&
            notification.id !in acknowledgedCommunityNotificationIds && (
            notification.category.startsWith("community") ||
                notification.category.contains("group")
            )
    }
    val unreadMessageNotifications = notifications.count { notification ->
        !notification.read &&
            notification.id !in acknowledgedMessageNotificationIds &&
            notification.category.contains("message")
    }
    return ShellUiState(
        activeUserId = activeUserId,
        toastMessage = toastMessage,
        selectedTab = selectedTab,
        selectedProviderDetails = selectedProviderDetails,
        selectedMessageThreadId = selectedMessageThreadId,
        headerRosterPet = headerRosterPet,
        selectedSuburb = selectedSuburb,
        selectedRangeCenter = selectedRangeCenter,
        currentLocationSuburb = currentLocationSuburb,
        serviceMaxDistanceKm = serviceMaxDistanceKm,
        error = error,
        loading = loading,
        hasPendingSync = hasPendingSync,
        isOfflineMode = isOfflineMode,
        unreadNotificationCount = unreadNotifications,
        unreadCommunityNotificationCount = unreadCommunityNotifications,
        unreadMessageNotificationCount = unreadMessageNotifications,
    )
}

private fun UiState.toServicesTabUiState(): ServicesTabUiState = ServicesTabUiState(
    providers = providers,
    nearbyPetBusinesses = nearbyPetBusinesses,
    groomerPetRosters = groomerPetRosters,
    recommendationSuburb = servicesRecommendationSuburb,
    recommendationSource = servicesRecommendationSource,
    selectedCategory = selectedCategory,
    viewMode = servicesViewMode,
    searchQuery = servicesSearchQuery,
    sortBy = servicesSortBy,
    loading = loading && (providers.isEmpty() || selectedProviderDetails != null),
    selectedDetails = selectedProviderDetails,
    availableSlots = availableSlots,
    availabilityDate = availabilityDate,
    minRating = serviceMinRating,
    maxDistanceKm = serviceMaxDistanceKm,
)

private fun UiState.toBarkAiTabUiState(): BarkAiTabUiState = BarkAiTabUiState(
    loading = loading,
    chatResponse = chat,
    conversation = conversation,
    streamingAssistantText = streamingAssistantText,
    profileSuggestion = profileSuggestion,
    a2uiProfileCard = a2uiProfileCard,
    a2uiProviderCard = a2uiProviderCard,
    barkThreads = barkThreads,
    selectedBarkThreadId = selectedBarkThreadId,
    onboardingMode = onboardingActive,
    onboardingNeedsPhoto = onboardingActive && onboardingStep >= 2 && !onboardingPhotoCaptured,
)

private fun UiState.toCommunityTabUiState(): CommunityTabUiState = CommunityTabUiState(
    activeUserId = activeUserId,
    loading = loading,
    suburb = selectedSuburb,
    currentLocationSuburb = currentLocationSuburb,
    currentLatitude = currentLatitude,
    currentLongitude = currentLongitude,
    postsSortBy = postsSortBy,
    selectedGroupId = selectedCommunityGroupId,
    groups = groups,
    groupPetRosters = groupPetRosters,
    latestGroupInvites = latestGroupInvites,
    blockedUserIds = blockedUserIds,
    savedPostIds = savedCommunityPostIds,
    savedEventIds = savedCommunityEventIds,
    mutedKeywords = mutedCommunityKeywords,
    followedGroupIds = followedGroupIds,
    communityWeather = communityWeather,
    autoParkCheckInEnabled = autoParkCheckInEnabled,
    autoParkCheckInRequireCrowd = autoParkCheckInRequireCrowd,
    autoParkCheckInQuorumCount = autoParkCheckInQuorumCount,
    autoParkCheckInQuorumThreshold = autoParkCheckInQuorumThreshold,
    autoParkCheckInQuorumWindowMinutes = autoParkCheckInQuorumWindowMinutes,
    posts = posts,
    postCommentsByPostId = communityCommentsByPostId,
    loadingCommentPostIds = loadingCommentPostIds,
    isCommunityModerator = isCommunityModerator,
    events = communityEvents,
    messageThreads = messageThreads,
)

private fun UiState.toMessagesTabUiState(): MessagesTabUiState = MessagesTabUiState(
    activeUserId = activeUserId,
    threads = messageThreads,
    mutedThreadIds = mutedMessageThreadIds,
    pinnedThreadIds = pinnedMessageThreadIds,
    unreadNotificationCount = notifications.count { notification -> !notification.read },
    selectedThreadId = selectedMessageThreadId,
    messages = directMessages,
)

private fun UiState.toProfileTabUiState(): ProfileTabUiState = ProfileTabUiState(
    profileInfo = profileInfo,
    providerModeEnabled = providerModeEnabled,
    hasProviderListings = hasProviderListings,
    canLoadProviderInbox = canLoadProviderInbox,
    activeUserId = activeUserId,
    friendProfiles = friendProfiles,
    joinedEvents = joinedEvents,
    ownerBookings = ownerBookings,
    providerListings = providerListings,
    providerBookings = providerBookings,
    providerInboxItems = providerInboxItems,
    loadingProviderInbox = loadingProviderInbox,
    sendingQuoteOfferItemIds = sendingQuoteOfferItemIds,
    isSubmittingProviderInboxAction = loading,
    calendarEvents = calendarEvents,
    notifications = notifications,
    activationFunnelMetrics = activationFunnelMetrics,
    notifyFollowedGroupAlerts = notifyFollowedGroupAlerts,
    notifySavedPostUpdates = notifySavedPostUpdates,
    notifySafetyAlerts = notifySafetyAlerts,
    showIdentityHeader = profileIdentityHeaderVisible,
    friendQrPayload = friendQrPayload,
    friendQrExpiresAt = friendQrExpiresAt,
    friendQrLoading = friendQrLoading,
)

private fun UiState.toAuthDialogUiState(): AuthDialogUiState = AuthDialogUiState(
    isRequired = authRequired,
    inviteId = authInviteId,
    email = authEmail,
    otpRequested = authOtpRequested,
    otpExpiresAt = authOtpExpiresAt,
    inFlight = authInFlight,
    error = error,
)

private fun UiState.toPendingInviteUiState(): PendingInviteUiState = PendingInviteUiState(
    invite = pendingInvite,
    loading = loading,
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
private val ONBOARD_SUBURB_QUESTIONS = listOf(
    "Which suburb are you in?",
    "Nice. What suburb should I use for your local BarkWise activity?",
    "Great. Which suburb are you based in?",
    "Perfect. What suburb do you want on your profile?",
    "Thanks. Which suburb should I set for you?",
    "Awesome. What suburb are you and your dog usually in?",
    "Sweet. Which local suburb should I save?",
    "Great, what suburb are you in?",
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

class PetSocialViewModel(
    private val repository: PetSocialRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    val shellUiState: StateFlow<ShellUiState> = _uiState
        .map { state -> state.toShellUiState() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value.toShellUiState())
    val servicesUiState: StateFlow<ServicesTabUiState> = _uiState
        .map { state -> state.toServicesTabUiState() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value.toServicesTabUiState())
    val barkAiUiState: StateFlow<BarkAiTabUiState> = _uiState
        .map { state -> state.toBarkAiTabUiState() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value.toBarkAiTabUiState())
    val communityUiState: StateFlow<CommunityTabUiState> = _uiState
        .map { state -> state.toCommunityTabUiState() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value.toCommunityTabUiState())
    val messagesUiState: StateFlow<MessagesTabUiState> = _uiState
        .map { state -> state.toMessagesTabUiState() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value.toMessagesTabUiState())
    val profileUiState: StateFlow<ProfileTabUiState> = _uiState
        .map { state -> state.toProfileTabUiState() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value.toProfileTabUiState())
    val authDialogUiState: StateFlow<AuthDialogUiState> = _uiState
        .map { state -> state.toAuthDialogUiState() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value.toAuthDialogUiState())
    val pendingInviteUiState: StateFlow<PendingInviteUiState> = _uiState
        .map { state -> state.toPendingInviteUiState() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value.toPendingInviteUiState())
    private var servicesSearchJob: Job? = null
    private var weatherTickerJob: Job? = null
    private var messageRefreshJob: Job? = null
    private val recentParkPresenceSignals = mutableMapOf<String, MutableList<ParkPresenceSignal>>()

    init {
        if (IS_PROVIDER_OS_SURFACE) {
            val state = _uiState.value
            _uiState.value = state.withNavigation { copy(selectedTab = AppTab.Profile) }.copy(
                providerModeEnabled = true,
                canLoadProviderInbox = true,
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
            providerModeEnabled = IS_PROVIDER_OS_SURFACE || _uiState.value.providerModeEnabled,
            canLoadProviderInbox = IS_PROVIDER_OS_SURFACE || _uiState.value.canLoadProviderInbox,
            isCommunityModerator = persistedUserId in COMMUNITY_MODERATOR_IDS,
            authRequired = false,
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
        startMessageRefreshTicker()
        if (requiresOtpAuth() && repository.currentAuthToken().isBlank()) {
            attemptTrustedDeviceSignIn()
        } else {
            _uiState.value = _uiState.value.copy(
                authRequired = requiresOtpAuth() && repository.currentAuthToken().isBlank(),
            )
        }
    }

    private fun attemptTrustedDeviceSignIn() {
        viewModelScope.launch {
            runCatching { repository.tryTrustedDeviceLogin() }
                .onSuccess { response ->
                    repository.setActiveUser(response.userId)
                    _uiState.value = _uiState.value.copy(
                        activeUserId = response.userId,
                        authRequired = false,
                        authOtpRequested = false,
                        authInviteId = "",
                        authEmail = "",
                        authOtpExpiresAt = null,
                        authInFlight = false,
                        isCommunityModerator = response.userId in COMMUNITY_MODERATOR_IDS,
                    )
                    loadHomeData(_uiState.value.selectedCategory, allowAuthRetry = false)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        authRequired = requiresOtpAuth(),
                        authInFlight = false,
                    )
                }
        }
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
        _uiState.value = state.withNavigation {
            copy(
                selectedTab = AppTab.BarkAI,
                selectedBarkThreadId = ONBOARD_THREAD_ID,
                onboardingActive = true,
                onboardingStep = 0,
            )
        }.copy(
            barkThreads = listOf(
                BarkThread(
                    id = ONBOARD_THREAD_ID,
                    title = "Onboarding",
                    conversation = introConversation,
                    chat = introResponse,
                    updatedAt = System.currentTimeMillis(),
                ),
            ),
            chat = introResponse,
            conversation = introConversation,
            onboardingOwnerName = "",
            onboardingDogName = "",
            onboardingSuburb = "",
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
            onboardingOwnerName = "",
            onboardingDogName = "",
            onboardingSuburb = "",
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
        ).withNavigation {
            copy(
                onboardingActive = false,
                onboardingStep = 0,
            )
        }
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
        showLoadingIndicators: Boolean = true,
        scope: HomeRefreshScope = HomeRefreshScope.Full,
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
        val shouldLoadProviderInbox = state.canLoadProviderInbox
        val indicatorState = resolveHomeLoadIndicatorState(
            showLoadingIndicators = showLoadingIndicators,
            shouldLoadProviderInbox = shouldLoadProviderInbox,
            currentLoading = state.loading,
            currentLoadingProviderInbox = state.loadingProviderInbox,
        )
        val refreshPlan = resolveHomeRefreshPlan(
            selectedTab = state.selectedTab,
            scope = scope,
        )
        viewModelScope.launch {
            Trace.beginSection("loadHomeData")
            val totalStartNs = System.nanoTime()
            _uiState.value = _uiState.value.copy(
                loading = indicatorState.loading,
                loadingProviderInbox = indicatorState.loadingProviderInbox,
                error = null,
                selectedCategory = resolvedCategory,
            )
            val fetchStartNs = System.nanoTime()
            runCatching {
                val currentPayload = currentHomePayload(state = state, suburb = suburb)
                loadHomePayload(
                    repository = repository,
                    state = state,
                    currentPayload = currentPayload,
                    refreshPlan = refreshPlan,
                    category = resolvedCategory,
                    suburb = suburb,
                    isStagingTestBuild = isStagingTestBuild(),
                    useCurrentLocation = useCurrentLocation,
                    shouldLoadProviderInbox = shouldLoadProviderInbox,
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
                    val metrics = HomeLoadMetrics(
                        source = "network",
                        fetchMs = fetchMs,
                        applyMs = elapsedMs(applyStartNs),
                        totalMs = elapsedMs(totalStartNs),
                    )
                    logHomeLoadMetrics(metrics)
                    recordHomeLoadMetrics(metrics)
            }.onFailure { error ->
                val statusCode = (error as? HttpException)?.code()
                if (allowAuthRetry && (statusCode == 401 || statusCode == 403)) {
                    if (allowsDemoLoginFallback()) {
                        val reAuthOk = repository.authenticateAsUser(_uiState.value.activeUserId)
                        if (reAuthOk) {
                            loadHomeData(
                                category = resolvedCategory,
                                allowAuthRetry = false,
                                scope = scope,
                            )
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
                    val metrics = HomeLoadMetrics(
                        source = "cache",
                        fetchMs = fetchMs,
                        applyMs = elapsedMs(applyStartNs),
                        totalMs = elapsedMs(totalStartNs),
                    )
                    logHomeLoadMetrics(metrics)
                    recordHomeLoadMetrics(metrics)
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
                    logHomeLoadMetrics(metrics)
                    recordHomeLoadMetrics(metrics)
                }
            }
            Trace.endSection()
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
        val applySnapshot = buildHomeApplySnapshot(
            current = current,
            payload = payload,
            suburb = suburb,
            isStagingTestBuild = isStagingTestBuild(),
        )
        _uiState.value = current.applyHomePayloadSnapshot(
            payload = payload,
            snapshot = applySnapshot,
            errorMessage = errorMessage,
            isOfflineMode = isOfflineMode,
            hasPendingSync = hasPendingSync,
            metrics = metrics,
        )
        maybeRunAutoParkCheckIn(reason = "home_payload_applied")
    }

    fun refreshProviderInbox(includeResolved: Boolean = false) {
        val state = _uiState.value
        if (!shouldRefreshProviderInbox(state.canLoadProviderInbox)) return
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
        val state = _uiState.value
        when (
            val resolution = resolveQuoteOfferSubmission(
                providerInboxItems = state.providerInboxItems,
                sendingQuoteOfferItemIds = state.sendingQuoteOfferItemIds,
                inboxItemId = inboxItemId,
                priceCents = priceCents,
                proposedDate = proposedDate,
                proposedTimeSlot = proposedTimeSlot,
                expiresAt = expiresAt,
                note = note,
            )
        ) {
            QuoteOfferSubmissionResolution.Ignore -> return
            is QuoteOfferSubmissionResolution.Toast -> {
                _uiState.value = state.copy(toastMessage = resolution.toastMessage)
                return
            }
            is QuoteOfferSubmissionResolution.Submit -> viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    sendingQuoteOfferItemIds = _uiState.value.sendingQuoteOfferItemIds + resolution.inboxItemId,
                    error = null,
                )
                runCatching {
                    repository.createServiceQuoteOffer(
                        quoteRequestId = resolution.quoteRequestId,
                        providerId = resolution.providerId,
                        priceCents = resolution.priceCents,
                        proposedDate = resolution.proposedDate,
                        proposedTimeSlot = resolution.proposedTimeSlot,
                        expiresAt = resolution.expiresAt,
                        note = resolution.note,
                    )
                }.onSuccess {
                    _uiState.value = _uiState.value.copy(
                        sendingQuoteOfferItemIds = _uiState.value.sendingQuoteOfferItemIds - resolution.inboxItemId,
                        toastMessage = "Offer sent for ${resolution.providerName} (AUD ${formatAudCents(resolution.priceCents)})",
                    )
                    refreshProviderInbox(includeResolved = false)
                }.onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        sendingQuoteOfferItemIds = _uiState.value.sendingQuoteOfferItemIds - resolution.inboxItemId,
                        error = error.message,
                    )
                }
            }
        }
    }

    fun switchTab(tab: AppTab) {
        val current = _uiState.value
        val resolution = resolveTabSwitchState(
            navigation = current.navigation,
            tab = tab,
            directMessages = current.directMessages,
            notifications = current.notifications,
            acknowledgedCommunityNotificationIds = current.acknowledgedCommunityNotificationIds,
            acknowledgedMessageNotificationIds = current.acknowledgedMessageNotificationIds,
        )
        _uiState.value = current.copy(
            navigation = resolution.navigation,
            directMessages = resolution.directMessages,
            acknowledgedCommunityNotificationIds = resolution.acknowledgedCommunityNotificationIds,
            acknowledgedMessageNotificationIds = resolution.acknowledgedMessageNotificationIds,
        )
        startMessageRefreshTicker()
    }

    fun openBarkAiTab() {
        val state = _uiState.value
        when (val resolution = resolveBarkAiEntry(
            onboardingActive = isOnboardingScriptEnabled() && state.onboardingActive,
            selectedBarkThreadId = state.selectedBarkThreadId,
            barkThreads = state.barkThreads,
            newThreadId = "bark_thread_${System.currentTimeMillis()}",
            updatedAt = System.currentTimeMillis(),
        )) {
            is BarkAiEntryResolution.StayOnOnboarding -> {
                _uiState.value = _uiState.value.withNavigation {
                    copy(
                        selectedTab = AppTab.BarkAI,
                        selectedBarkThreadId = resolution.selectedThreadId,
                    )
                }
            }
            is BarkAiEntryResolution.StartNewThread -> {
                applyBarkAiNewThreadState(state = state, resolution = resolution)
            }
        }
    }

    fun openProfileNotifications(filter: String = "all") {
        _uiState.value = _uiState.value.copy(
            navigation = resolveProfileNotificationNavigation(_uiState.value.navigation, filter),
        )
    }

    fun openCommunityGroup(groupId: String) {
        val resolution = resolveCommunityGroupNavigation(_uiState.value.navigation, groupId) ?: return
        _uiState.value = _uiState.value.copy(navigation = resolution)
    }

    fun clearSelectedCommunityGroup() {
        _uiState.value = _uiState.value.copy(
            navigation = clearSelectedCommunityGroupNavigation(_uiState.value.navigation),
        )
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
        when (val resolution = resolveFriendQrTokenInput(friendToken)) {
            is FriendQrTokenResolution.Invalid -> {
                _uiState.value = _uiState.value.copy(toastMessage = resolution.toastMessage)
                return
            }
            is FriendQrTokenResolution.Verify -> viewModelScope.launch {
                runCatching { repository.verifyFriendQr(resolution.token) }
                .onSuccess { response ->
                    addFriendFromQr(
                        userId = response.userId,
                        humanName = response.humanName,
                        dogName = response.dogName,
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        toastMessage = resolveFriendQrVerificationFailure((error as? HttpException)?.code()),
                    )
                }
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
        val state = _uiState.value
        when (
            val resolution = resolveFriendMutation(
                state = state,
                action = FriendMutationAction.AddOrUpdate,
                userId = userId,
                humanName = humanName,
                dogName = dogName,
            )
        ) {
            FriendMutationResolution.NoChange -> return
            is FriendMutationResolution.ToastOnly -> {
                _uiState.value = state.copy(toastMessage = resolution.toastMessage)
            }
            is FriendMutationResolution.StateUpdate -> {
                _uiState.value = state.copy(
                    messageThreads = resolution.messageThreads,
                    friendProfiles = resolution.friendProfiles,
                    toastMessage = resolution.toastMessage,
                )
            }
        }
    }

    fun removeFriend(userId: String) {
        val state = _uiState.value
        when (
            val resolution = resolveFriendMutation(
                state = state,
                action = FriendMutationAction.Remove,
                userId = userId,
            )
        ) {
            FriendMutationResolution.NoChange -> return
            is FriendMutationResolution.ToastOnly -> {
                _uiState.value = state.copy(toastMessage = resolution.toastMessage)
            }
            is FriendMutationResolution.StateUpdate -> {
                _uiState.value = state.copy(
                    friendProfiles = resolution.friendProfiles,
                    messageThreads = resolution.messageThreads,
                    toastMessage = resolution.toastMessage,
                )
            }
        }
    }

    fun openMessages(target: MessageTarget) {
        val state = _uiState.value
        val normalizedThreadId = target.threadId?.trim().orEmpty()
        val normalizedUserId = target.userId?.trim().orEmpty()
        trackActivationEventAsync(
            step = "message_handoff",
            status = "attempted",
            metadata = mapOf(
                "source" to target.source,
                "target_present" to (normalizedThreadId.isNotBlank() || normalizedUserId.isNotBlank()).toString(),
            ),
        )
        when (val resolution = resolveMessageOpen(state, target)) {
            is MessageOpenResolution.SelectExistingThread -> {
                selectMessageThread(resolution.threadId)
                _uiState.value = _uiState.value.withNavigation { copy(selectedTab = AppTab.Messages) }
                trackActivationEventAsync(
                    step = "message_handoff",
                    status = "succeeded",
                    metadata = mapOf(
                        "source" to target.source,
                        "target_present" to "true",
                    ),
                )
            }
            is MessageOpenResolution.CreateSyntheticThread -> {
                _uiState.value = state.withNavigation {
                    copy(
                        selectedTab = AppTab.Messages,
                        selectedMessageThreadId = resolution.selectedThreadId,
                    )
                }.copy(
                    messageThreads = resolution.messageThreads,
                    directMessages = emptyList(),
                )
            }
            MessageOpenResolution.NoTarget -> {
                _uiState.value = state.withNavigation { copy(selectedTab = AppTab.Messages) }.copy(
                    toastMessage = "No message target available yet",
                )
            }
        }
    }

    fun openMessagesForUser(userId: String) {
        openMessages(resolveProfileSocialMessageTarget(userId))
    }

    fun startNewBarkThread() {
        val state = _uiState.value
        when (val resolution = resolveBarkAiEntry(
            onboardingActive = isOnboardingScriptEnabled() && state.onboardingActive,
            selectedBarkThreadId = state.selectedBarkThreadId,
            barkThreads = state.barkThreads,
            newThreadId = "bark_thread_${System.currentTimeMillis()}",
            updatedAt = System.currentTimeMillis(),
        )) {
            is BarkAiEntryResolution.StayOnOnboarding -> {
                _uiState.value = _uiState.value.withNavigation {
                    copy(
                        selectedTab = AppTab.BarkAI,
                        selectedBarkThreadId = resolution.selectedThreadId,
                    )
                }.copy(
                    toastMessage = "Finish onboarding first",
                )
            }
            is BarkAiEntryResolution.StartNewThread -> {
                applyBarkAiNewThreadState(state = state, resolution = resolution)
            }
        }
    }

    private fun applyBarkAiNewThreadState(
        state: UiState,
        resolution: BarkAiEntryResolution.StartNewThread,
    ) {
        _uiState.value = state.withNavigation {
            copy(
                selectedTab = AppTab.BarkAI,
                selectedBarkThreadId = resolution.selectedThreadId,
            )
        }.copy(
            barkThreads = resolution.barkThreads,
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
            _uiState.value = _uiState.value.withNavigation { copy(selectedTab = AppTab.BarkAI) }.copy(
                toastMessage = "Finish onboarding first",
            )
            return
        }
        val state = _uiState.value
        val selected = state.barkThreads.firstOrNull { it.id == threadId } ?: return
        _uiState.value = state.withNavigation {
            copy(
                selectedTab = AppTab.BarkAI,
                selectedBarkThreadId = threadId,
            )
        }.copy(
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
                    _uiState.value = _uiState.value.withNavigation {
                        copy(
                            pendingInvite = invite,
                            selectedTab = AppTab.Community,
                        )
                    }.copy(
                        selectedSuburb = invite.suburb,
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
        _uiState.value = _uiState.value.withNavigation { copy(pendingInvite = null) }
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
                _uiState.value = _uiState.value.withNavigation { copy(selectedMessageThreadId = null) }.copy(
                    activeUserId = response.userId,
                    providerModeEnabled = IS_PROVIDER_OS_SURFACE,
                    hasProviderListings = false,
                    canLoadProviderInbox = IS_PROVIDER_OS_SURFACE,
                    authRequired = false,
                    authOtpRequested = false,
                    authInviteId = "",
                    authEmail = "",
                    authOtpExpiresAt = null,
                    authInFlight = false,
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
                if (isOnboardingScriptEnabled()) {
                    repository.setTestProfileMode(TEST_PROFILE_MODE_ONBOARDING)
                    applyTestProfileMode(
                        mode = TEST_PROFILE_MODE_ONBOARDING,
                        persist = false,
                        triggerHomeReload = false,
                        showToast = false,
                    )
                } else {
                    loadHomeData(_uiState.value.selectedCategory, allowAuthRetry = false)
                }
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
                _uiState.value = _uiState.value.withNavigation {
                    copy(
                        pendingInvite = null,
                        selectedTab = AppTab.Community,
                    )
                }.copy(
                    activeUserId = response.userId,
                    providerModeEnabled = IS_PROVIDER_OS_SURFACE,
                    hasProviderListings = false,
                    canLoadProviderInbox = IS_PROVIDER_OS_SURFACE,
                    selectedSuburb = invite.suburb,
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
        loadHomeData(
            category = _uiState.value.selectedCategory,
            scope = HomeRefreshScope.ActiveTab,
        )
    }

    fun reloadHomeData(
        showLoadingIndicators: Boolean = true,
        scope: HomeRefreshScope = HomeRefreshScope.ActiveTab,
    ) {
        if (_uiState.value.loading && showLoadingIndicators) return
        loadHomeData(
            category = _uiState.value.selectedCategory,
            showLoadingIndicators = showLoadingIndicators,
            scope = scope,
        )
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
        val state = _uiState.value
        _uiState.value = state.withNavigation { copy(selectedMessageThreadId = threadId) }
        startMessageRefreshTicker()
        viewModelScope.launch {
            runCatching { repository.markThreadRead(threadId) }
            val messages = runCatching {
                repository.loadThreadMessages(
                    threadId = threadId,
                    limit = 200,
                ).map { message -> message.toDirectMessage() }
            }.getOrElse {
                state.directMessages.filter { message -> message.threadId == threadId }
            }
            val refreshedThreads = runCatching {
                buildMessageThreadsForState(
                    state = _uiState.value,
                    apiThreads = repository.loadMessageThreads(limit = 50),
                )
            }.getOrElse { _uiState.value.messageThreads }
            _uiState.value = _uiState.value.withNavigation {
                copy(selectedMessageThreadId = threadId.takeIf { id -> refreshedThreads.any { thread -> thread.id == id } })
            }.copy(
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
                toastMessage = if (markedRead) "Thread marked read" else null,
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
        _uiState.value = state.withNavigation {
            copy(selectedMessageThreadId = null)
        }.copy(
            readDirectMessageIds = state.readDirectMessageIds + state.directMessages.map { message -> message.id },
            directMessages = emptyList(),
        )
        startMessageRefreshTicker()
    }

    fun sendDirectMessage(threadId: String, body: String) {
        val trimmed = body.trim()
        if (trimmed.isBlank()) return
        val state = _uiState.value
        val existingThread = state.messageThreads
            .firstOrNull { it.id == threadId }
            ?: return
        val recipientUserId = existingThread
            ?.participantUserId
            ?: return
        val optimisticMessage = DirectMessage(
            id = "local_${System.currentTimeMillis()}",
            threadId = threadId,
            senderUserId = state.activeUserId,
            recipientUserId = recipientUserId,
            body = trimmed,
        )
        val previousThread = existingThread
        val optimisticThreads = state.messageThreads.map { thread ->
            if (thread.id == threadId) {
                thread.copy(
                    lastMessage = trimmed,
                    unreadCount = 0,
                )
            } else {
                thread
            }
        }
        _uiState.value = state.copy(
            messageThreads = optimisticThreads,
            directMessages = state.directMessages + optimisticMessage,
            readDirectMessageIds = state.readDirectMessageIds + optimisticMessage.id,
            error = null,
        )
        viewModelScope.launch {
            runCatching {
                repository.sendThreadMessage(
                    threadId = threadId,
                    recipientUserId = recipientUserId,
                    body = trimmed,
                )
            }.onSuccess {
                refreshMessageState(threadId = threadId)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    messageThreads = _uiState.value.messageThreads.map { thread ->
                        if (thread.id == threadId) previousThread else thread
                    },
                    directMessages = _uiState.value.directMessages.filterNot { message -> message.id == optimisticMessage.id },
                    error = error.message,
                )
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
            val (providerModeEnabled, hasProviderListings, canLoadProviderInbox) = clearedProviderState(
                providerOsSurface = IS_PROVIDER_OS_SURFACE,
            )
            _uiState.value = _uiState.value.withNavigation {
                copy(
                    selectedMessageThreadId = null,
                    profileNotificationFilter = "all",
                )
            }.copy(
                activeUserId = userId,
                testProfileMode = persistedTestProfileMode,
                profileIdentityHeaderVisible = persistedProfileHeaderVisible,
                providerModeEnabled = providerModeEnabled,
                hasProviderListings = hasProviderListings,
                canLoadProviderInbox = canLoadProviderInbox,
                authRequired = false,
                authOtpRequested = false,
                authInviteId = "",
                authEmail = "",
                authOtpExpiresAt = null,
                authInFlight = false,
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
            val reset = resolveSessionResetState(
                state = state,
                providerOsSurface = IS_PROVIDER_OS_SURFACE,
                authRequired = requiresOtpAuth(),
                activeUserId = state.activeUserId,
                toastMessage = if (success) "Signed out" else "Signed out locally",
            )
            _uiState.value = applySessionResetResolution(state, reset)
            if (!requiresOtpAuth()) {
                loadHomeData(_uiState.value.selectedCategory)
            }
        }
    }

    fun resetCurrentDeviceSignIn() {
        viewModelScope.launch {
            val success = repository.resetTrustedDevice()
            val state = _uiState.value
            val reset = resolveSessionResetState(
                state = state,
                providerOsSurface = IS_PROVIDER_OS_SURFACE,
                authRequired = requiresOtpAuth(),
                activeUserId = state.activeUserId,
                toastMessage = if (success) "Device sign-in reset" else "Signed out locally",
            )
            _uiState.value = applySessionResetResolution(state, reset)
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
                    val reset = resolveSessionResetState(
                        state = state,
                        providerOsSurface = IS_PROVIDER_OS_SURFACE,
                        authRequired = requiresOtpAuth(),
                        activeUserId = fallbackUserId,
                        toastMessage = "Account deleted",
                    )
                    _uiState.value = applySessionResetResolution(state.copy(activeUserId = fallbackUserId), reset)
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

    fun applyManualSuburb(suburb: String) {
        updateSuburb(suburb)
        reloadHomeData(scope = HomeRefreshScope.ActiveTab)
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
                    serviceProviderMode = normalized.serviceProviderMode,
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
                val savedProfile = response.toProfileInfo(
                    activeUserId = _uiState.value.activeUserId,
                    fallbackProfile = normalized,
                    fallbackSuburb = normalized.suburb,
                )
                val providerState = deriveProviderState(
                    providerOsSurface = IS_PROVIDER_OS_SURFACE,
                    profileProviderMode = savedProfile.serviceProviderMode,
                    hasProviderListings = _uiState.value.hasProviderListings,
                )
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    profileInfo = savedProfile,
                    providerModeEnabled = providerState.providerModeEnabled,
                    hasProviderListings = providerState.hasProviderListings,
                    canLoadProviderInbox = providerState.canLoadProviderInbox,
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
        viewModelScope.launch {
            val current = _uiState.value
            val normalized = current.profileInfo.copy(serviceProviderMode = enabled)
            val optimisticProviderState = deriveProviderState(
                providerOsSurface = IS_PROVIDER_OS_SURFACE,
                profileProviderMode = enabled,
                hasProviderListings = current.hasProviderListings,
            )
            _uiState.value = current.copy(
                loading = true,
                error = null,
                providerModeEnabled = optimisticProviderState.providerModeEnabled,
                hasProviderListings = optimisticProviderState.hasProviderListings,
                canLoadProviderInbox = optimisticProviderState.canLoadProviderInbox,
                profileInfo = normalized,
            )
            runCatching {
                repository.saveUserProfile(
                    displayName = normalized.displayName,
                    email = normalized.email,
                    phone = normalized.phone,
                    humanPronouns = normalized.humanPronouns,
                    humanRoleLabel = normalized.humanRoleLabel,
                    serviceProviderMode = normalized.serviceProviderMode,
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
                val savedProfile = response.toProfileInfo(
                    activeUserId = _uiState.value.activeUserId,
                    fallbackProfile = normalized,
                    fallbackSuburb = normalized.suburb,
                )
                val providerState = deriveProviderState(
                    providerOsSurface = IS_PROVIDER_OS_SURFACE,
                    profileProviderMode = savedProfile.serviceProviderMode,
                    hasProviderListings = _uiState.value.hasProviderListings,
                )
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    providerModeEnabled = providerState.providerModeEnabled,
                    hasProviderListings = providerState.hasProviderListings,
                    canLoadProviderInbox = providerState.canLoadProviderInbox,
                    profileInfo = savedProfile,
                    toastMessage = if (enabled) "Provider mode on" else "Provider mode off",
                )
                loadHomeData(_uiState.value.selectedCategory)
            }.onFailure { error ->
                _uiState.value = current.copy(
                    loading = false,
                    error = error.message ?: "Unable to update provider mode",
                    toastMessage = "Could not update provider mode",
                )
            }
        }
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
        suburb: String,
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
                _uiState.value = _uiState.value.withNavigation { copy(selectedTab = AppTab.Services) }.copy(
                    loading = false,
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
                    _uiState.value = state.withNavigation { copy(selectedTab = AppTab.Services) }.copy(
                        loading = false,
                        providers = updatedProviders,
                        providerListings = updatedListings,
                        hasProviderListings = true,
                        canLoadProviderInbox = true,
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
                    _uiState.value = _uiState.value.withNavigation { copy(selectedProviderDetails = details) }.copy(
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

    fun updateServiceRange(maxDistanceKm: Int?) {
        updateServiceFilters(
            minRating = _uiState.value.serviceMinRating,
            maxDistanceKm = maxDistanceKm,
        )
    }

    fun closeProviderDetails() {
        _uiState.value = _uiState.value.withNavigation { copy(selectedProviderDetails = null) }.copy(
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
            _uiState.value = _uiState.value.withNavigation {
                copy(
                    selectedTab = AppTab.BarkAI,
                    selectedBarkThreadId = nextThread.id,
                )
            }.copy(
                loading = true,
                error = null,
                streamingAssistantText = "",
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
        if (!isOnboardingScriptEnabled() || !state.onboardingActive || state.onboardingStep < 3) return

        val userTurn = ChatTurn(
            role = "user",
            content = if (photoCaptured) "Shared a dog photo from camera." else "Tried to share a dog photo.",
        )
        if (!photoCaptured) {
            val retryPrompt = "I couldn't read that photo. Tap Camera and try once more."
            applyOnboardingConversationUpdate(
                conversation = state.conversation + userTurn + onboardingAssistantTurn(retryPrompt),
                latestAssistantAnswer = retryPrompt,
                onboardingStep = 3,
                onboardingActive = true,
                ownerName = state.onboardingOwnerName,
                dogName = state.onboardingDogName,
                suburb = state.onboardingSuburb,
                photoCaptured = false,
                dogPhotoUri = null,
            )
            return
        }

        val completedProfile = updateOnboardingProfileInfo(
            profile = state.profileInfo,
            ownerName = state.onboardingOwnerName,
            dogName = state.onboardingDogName,
            suburb = state.onboardingSuburb,
            dogPhotoUri = dogPhotoUri,
        )
        val completionResolution = resolveOnboardingCompletion(
            barkThreads = state.barkThreads,
            fallbackThreadId = "bark_thread_${System.currentTimeMillis()}",
            fallbackUpdatedAt = System.currentTimeMillis(),
        )
        _uiState.value = state.withNavigation {
            copy(
                selectedTab = AppTab.Profile,
                selectedBarkThreadId = completionResolution.selectedThreadId,
                onboardingStep = 3,
                onboardingActive = false,
            )
        }.copy(
            chat = completionResolution.chat,
            conversation = completionResolution.conversation,
            barkThreads = completionResolution.barkThreads,
            profileInfo = completedProfile,
            onboardingOwnerName = state.onboardingOwnerName,
            onboardingDogName = state.onboardingDogName,
            onboardingSuburb = state.onboardingSuburb,
            onboardingPhotoCaptured = true,
            selectedSuburb = state.onboardingSuburb.trim().ifBlank { state.selectedSuburb },
            authRequired = false,
            authOtpRequested = false,
            authInviteId = "",
            authEmail = "",
            authOtpExpiresAt = null,
            authInFlight = false,
            loading = false,
            streamingAssistantText = "",
            error = null,
            toastMessage = "You're all set. Home is on the far right if you want to review or edit your profile.",
        )
        repository.setTestProfileMode(TEST_PROFILE_MODE_READY)
        _uiState.value = _uiState.value.copy(testProfileMode = TEST_PROFILE_MODE_READY)
        viewModelScope.launch {
            persistProfileInfoSilently(completedProfile)
            loadHomeData(_uiState.value.selectedCategory, allowAuthRetry = false)
        }
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
                    suburb = "",
                    photoCaptured = false,
                    dogPhotoUri = null,
                )
            }
            1 -> {
                val suburbQuestion = pickOnboardingVariation(ONBOARD_SUBURB_QUESTIONS)
                applyOnboardingConversationUpdate(
                    conversation = state.conversation + userTurn + onboardingAssistantTurn(suburbQuestion),
                    latestAssistantAnswer = suburbQuestion,
                    onboardingStep = 2,
                    onboardingActive = true,
                    ownerName = state.onboardingOwnerName,
                    dogName = message,
                    suburb = "",
                    photoCaptured = false,
                    dogPhotoUri = null,
                )
            }
            2 -> {
                val photoQuestion = pickOnboardingVariation(ONBOARD_DOG_PHOTO_QUESTIONS)
                applyOnboardingConversationUpdate(
                    conversation = state.conversation + userTurn + onboardingAssistantTurn(photoQuestion),
                    latestAssistantAnswer = photoQuestion,
                    onboardingStep = 3,
                    onboardingActive = true,
                    ownerName = state.onboardingOwnerName,
                    dogName = state.onboardingDogName,
                    suburb = message,
                    photoCaptured = false,
                    dogPhotoUri = null,
                )
            }
            else -> {
                val cameraReminder = "Tap the Camera button below so I can see your dog."
                applyOnboardingConversationUpdate(
                    conversation = state.conversation + userTurn + onboardingAssistantTurn(cameraReminder),
                    latestAssistantAnswer = cameraReminder,
                    onboardingStep = 3,
                    onboardingActive = true,
                    ownerName = state.onboardingOwnerName,
                    dogName = state.onboardingDogName,
                    suburb = state.onboardingSuburb,
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
        suburb: String,
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
        _uiState.value = state.withNavigation {
            copy(
                selectedTab = AppTab.BarkAI,
                selectedBarkThreadId = updatedThread.id,
                onboardingStep = onboardingStep,
                onboardingActive = onboardingActive,
            )
        }.copy(
            chat = response,
            conversation = conversation,
            barkThreads = upsertBarkThread(state.barkThreads, updatedThread),
            profileInfo = updateOnboardingProfileInfo(
                profile = state.profileInfo,
                ownerName = ownerName,
                dogName = dogName,
                suburb = suburb,
                dogPhotoUri = dogPhotoUri,
            ),
            onboardingOwnerName = ownerName,
            onboardingDogName = dogName,
            onboardingSuburb = suburb,
            onboardingPhotoCaptured = photoCaptured,
            selectedSuburb = suburb.trim().ifBlank { state.selectedSuburb },
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

        _uiState.value = _uiState.value.withNavigation { copy(selectedTab = AppTab.Services) }.copy(
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
        when (
            val resolution = resolveBookingRequest(
                state = state,
                providerId = providerId,
                date = date,
                timeSlot = timeSlot,
                note = note,
            )
        ) {
            BookingRequestResolution.Ignore -> return
            is BookingRequestResolution.Toast -> {
                _uiState.value = state.copy(toastMessage = resolution.toastMessage)
                return
            }
            is BookingRequestResolution.Submit -> {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching {
                        repository.createBookingHold(
                            providerId = resolution.providerId,
                            date = resolution.date,
                            timeSlot = resolution.timeSlot,
                        )
                        repository.requestBooking(
                            resolution.providerId,
                            resolution.date,
                            resolution.timeSlot,
                            resolution.note,
                        )
            }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                                toastMessage = "Booking requested: ${it.id}.${resolution.approvalHint}",
                    )
                    loadHomeData(_uiState.value.selectedCategory)
                            loadAvailability(resolution.providerId, resolution.date)
                }
                .onFailure { error -> _uiState.value = _uiState.value.copy(error = error.message, loading = false) }
        }
            }
        }
    }

    fun requestQuote(category: String, preferredWindow: String, petDetails: String, note: String) {
        val state = _uiState.value
        val resolvedSuburb = resolveServiceQuoteSuburb(
            selectedSuburb = state.selectedSuburb,
            currentLocationSuburb = state.currentLocationSuburb,
            profileSuburb = state.profileInfo.suburb,
            recommendationSuburb = state.servicesRecommendationSuburb,
            providers = state.providers,
            category = category,
        )
        when (
            val resolution = resolveServiceQuoteRequest(
                selectedSuburb = resolvedSuburb.orEmpty(),
                stagingTestBuild = isStagingTestBuild(),
                category = category,
                preferredWindow = preferredWindow,
                petDetails = petDetails,
                note = note,
            )
        ) {
            is ServiceQuoteRequestResolution.Toast -> {
                _uiState.value = state.copy(toastMessage = resolution.toastMessage)
                return
            }
            is ServiceQuoteRequestResolution.Submit -> {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching {
                repository.requestServiceQuote(
                            category = resolution.category,
                            suburb = resolution.suburb,
                            preferredWindow = resolution.preferredWindow,
                            petDetails = resolution.petDetails,
                            note = resolution.note,
                )
            }.onSuccess { result ->
                val targetCount = result.targets.size
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    toastMessage = "Quote sent to $targetCount provider(s). +1 Local Scout XP",
                )
                loadHomeData(
                    category = _uiState.value.selectedCategory,
                    showLoadingIndicators = false,
                )
            }.onFailure { error ->
                val httpCode = (error as? HttpException)?.code()
                val friendlyError = when {
                    httpCode == 404 && error.message?.contains("No matching providers found", ignoreCase = true) == true ->
                        "No matching providers found right now. Try another category or suburb."
                    httpCode == 404 -> "No eligible providers found for this quote right now. Try another category or suburb."
                    else -> error.message
                }
                _uiState.value = _uiState.value.copy(loading = false, error = friendlyError)
            }
        }
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
                _uiState.value = _uiState.value.withNavigation { copy(selectedTab = AppTab.Services) }
                loadHomeData(category = "dog_walking")
            }

            "find_groomers" -> {
                _uiState.value = _uiState.value.withNavigation { copy(selectedTab = AppTab.Services) }
                loadHomeData(category = "grooming")
            }

            "open_services" -> {
                val category = cta.payload.readString("category")
                _uiState.value = _uiState.value.withNavigation { copy(selectedTab = AppTab.Services) }
                loadHomeData(category)
            }

            "open_community" -> {
                _uiState.value = _uiState.value.withNavigation { copy(selectedTab = AppTab.Community) }
                loadHomeData(_uiState.value.selectedCategory)
            }

            "open_home" -> {
                _uiState.value = _uiState.value.withNavigation { copy(selectedTab = AppTab.Profile) }
                loadHomeData(_uiState.value.selectedCategory)
            }

            "create_lost_found" -> {
                val title = cta.payload.readString("title") ?: "Lost/Found pet alert"
                val body = cta.payload.readString("body") ?: "Shared from AI assistant"
                val suburb = cta.payload.readString("suburb") ?: _uiState.value.selectedSuburb
                createLostFoundPost(title = title, body = body, suburb = suburb)
            }

            "new_bark_thread" -> startNewBarkThread()
            "urgent_vet_steps" -> {
                _uiState.value = _uiState.value.withNavigation { copy(selectedTab = AppTab.BarkAI) }.copy(
                    toastMessage = "Share timing, breathing, weakness, and what changed so BarkWiseAI can help you triage.",
                )
            }

            "what_to_monitor" -> {
                _uiState.value = _uiState.value.withNavigation { copy(selectedTab = AppTab.BarkAI) }.copy(
                    toastMessage = "Tell BarkWiseAI what changed next so it can help you monitor symptoms.",
                )
            }

            "prepare_vet_summary" -> {
                startNewBarkThread()
                _uiState.value = _uiState.value.copy(
                    toastMessage = "Start a fresh BarkWiseAI thread with symptom timing, frequency, appetite, and any exposures.",
                )
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
                    _uiState.value = _uiState.value.withNavigation { copy(selectedTab = AppTab.Services) }.copy(selectedCategory = category)
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
                _uiState.value = _uiState.value.withNavigation { copy(selectedTab = AppTab.Community) }.copy(
                    loading = false,
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
                _uiState.value = _uiState.value.withNavigation { copy(selectedTab = AppTab.Community) }.copy(
                    loading = false,
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
                    _uiState.value = _uiState.value.withNavigation { copy(selectedTab = AppTab.Community) }.copy(
                        loading = false,
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
                    _uiState.value = _uiState.value.withNavigation { copy(selectedTab = AppTab.Community) }.copy(
                        loading = false,
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
                _uiState.value = _uiState.value.withNavigation { copy(selectedTab = AppTab.Community) }.copy(
                    loading = false,
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

    private fun startMessageRefreshTicker() {
        messageRefreshJob?.cancel()
        messageRefreshJob = viewModelScope.launch {
            while (true) {
                val intervalMs = if (_uiState.value.selectedTab == AppTab.Messages) 2_000L else 6_000L
                delay(intervalMs)
                if (_uiState.value.authRequired || repository.currentAuthToken().isBlank()) {
                    continue
                }
                refreshMessageState(threadId = _uiState.value.selectedMessageThreadId, silent = true)
            }
        }
    }

    private suspend fun refreshMessageState(threadId: String?, silent: Boolean = false) {
        val current = _uiState.value
        val refreshedThreads = runCatching {
            buildMessageThreadsForState(
                state = current,
                apiThreads = repository.loadMessageThreads(limit = 50),
            )
        }.getOrElse {
            if (!silent) {
                _uiState.value = _uiState.value.copy(error = it.message)
            }
            current.messageThreads
        }
        val resolvedThreadId = threadId?.takeIf { id ->
            refreshedThreads.any { thread -> thread.id == id }
        }
        val refreshedMessages = if (resolvedThreadId.isNullOrBlank()) {
            if (current.selectedMessageThreadId == threadId) current.directMessages else emptyList()
        } else {
            runCatching {
                repository.loadThreadMessages(
                    threadId = resolvedThreadId,
                    limit = 200,
                ).map { message -> message.toDirectMessage() }
            }.map { fetched ->
                if (fetched.isEmpty()) {
                    current.directMessages.filter { message -> message.threadId == resolvedThreadId }
                } else {
                    fetched
                }
            }.getOrElse {
                if (!silent) {
                    _uiState.value = _uiState.value.copy(error = it.message)
                }
                current.directMessages.filter { message -> message.threadId == resolvedThreadId }
            }
        }
        val friendProfiles = buildFriendProfiles(
            activeUserId = current.activeUserId,
            messageThreads = refreshedThreads,
            existingProfiles = current.friendProfiles,
        )
        _uiState.value = _uiState.value.withNavigation { copy(selectedMessageThreadId = resolvedThreadId) }.copy(
            messageThreads = applyFriendProfileAvatars(
                threads = refreshedThreads,
                friendProfiles = friendProfiles,
            ),
            directMessages = refreshedMessages,
            readDirectMessageIds = if (resolvedThreadId.isNullOrBlank()) {
                current.readDirectMessageIds
            } else {
                refreshedMessages.map { message -> message.id }.toSet()
            },
            friendProfiles = reuseIfEquivalent(current.friendProfiles, friendProfiles),
        )
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
        messageRefreshJob?.cancel()
        super.onCleared()
    }

    private fun recordHomeLoadMetrics(metrics: HomeLoadMetrics) {
        val current = _uiState.value
        _uiState.value = current.copy(
            latestHomeLoadMetrics = metrics,
            homeLoadHistory = (current.homeLoadHistory + metrics).takeLast(20),
        )
    }

    private fun logHomeLoadMetrics(metrics: HomeLoadMetrics) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            "BarkWisePerf",
            "loadHomeData source=${metrics.source} fetchMs=${metrics.fetchMs} " +
                "applyMs=${metrics.applyMs} totalMs=${metrics.totalMs}",
        )
    }

    fun markNotificationRead(notificationId: String) {
        if (notificationId.startsWith("local:")) {
            val resolution = resolveLocalNotificationRead(
                notifications = _uiState.value.notifications,
                readLocalNotificationIds = _uiState.value.readLocalNotificationIds,
                notificationId = notificationId,
            )
            _uiState.value = _uiState.value.copy(
                notifications = resolution.notifications,
                readLocalNotificationIds = resolution.readLocalNotificationIds,
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
            val resolution = resolveLocalNotificationIdsRead(
                notifications = _uiState.value.notifications,
                readLocalNotificationIds = _uiState.value.readLocalNotificationIds,
                notificationIds = localIds,
            )
            _uiState.value = _uiState.value.copy(
                notifications = resolution.notifications,
                readLocalNotificationIds = resolution.readLocalNotificationIds,
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
            val resolution = resolveLocalNotificationIdsRead(
                notifications = state.notifications,
                readLocalNotificationIds = state.readLocalNotificationIds,
                notificationIds = localUnreadIds,
                toastMessage = "Local notifications marked read",
            )
            _uiState.value = state.copy(
                notifications = resolution.notifications,
                readLocalNotificationIds = resolution.readLocalNotificationIds,
                toastMessage = resolution.toastMessage,
            )
        }
    }

    fun clearLocalNotifications() {
        val state = _uiState.value
        val resolution = resolveClearAllLocalNotifications(
            notifications = state.notifications,
            toastMessage = "Local notifications cleared",
        )
        _uiState.value = state.copy(
            notifications = resolution.notifications,
            readLocalNotificationIds = resolution.readLocalNotificationIds,
            toastMessage = resolution.toastMessage,
        )
    }

    fun clearLocalNotificationIds(notificationIds: List<String>) {
        val resolution = resolveClearLocalNotificationIds(
            notifications = _uiState.value.notifications,
            readLocalNotificationIds = _uiState.value.readLocalNotificationIds,
            acknowledgedCommunityNotificationIds = _uiState.value.acknowledgedCommunityNotificationIds,
            acknowledgedMessageNotificationIds = _uiState.value.acknowledgedMessageNotificationIds,
            notificationIds = notificationIds,
        )
        if (
            resolution.notifications == _uiState.value.notifications &&
            resolution.readLocalNotificationIds == _uiState.value.readLocalNotificationIds &&
            resolution.acknowledgedCommunityNotificationIds == _uiState.value.acknowledgedCommunityNotificationIds &&
            resolution.acknowledgedMessageNotificationIds == _uiState.value.acknowledgedMessageNotificationIds &&
            resolution.toastMessage == null
        ) return
        _uiState.value = _uiState.value.copy(
            notifications = resolution.notifications,
            readLocalNotificationIds = resolution.readLocalNotificationIds,
            acknowledgedCommunityNotificationIds = resolution.acknowledgedCommunityNotificationIds,
            acknowledgedMessageNotificationIds = resolution.acknowledgedMessageNotificationIds,
            toastMessage = resolution.toastMessage,
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
        val resolution = resolveNotificationOpen(
            deepLink = deepLink,
            currentPostsSortBy = _uiState.value.postsSortBy,
            notificationRead = notification.read,
        )
        val route = resolution.route ?: return
        _uiState.value = _uiState.value.withNavigation {
            copy(
                selectedTab = route.tab,
                selectedCommunityGroupId = route.selectedCommunityGroupId,
            )
        }.copy(
            postsSortBy = resolution.postsSortBy,
        )
        resolution.providerIdToLoad?.let { providerId ->
            if (providerId.isNotBlank()) loadProviderDetails(providerId)
        }
        if (resolution.shouldReloadHome) {
            loadHomeData(_uiState.value.selectedCategory)
        }
        trackActivationEventAsync(
            step = "notification_open",
            status = "succeeded",
            metadata = mapOf(
                "category" to sanitizeTelemetryValue(notification.category, maxLength = 40),
                "deep_link_kind" to deepLinkKind,
            ),
        )
        if (resolution.shouldMarkRead) {
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
        suburb: String,
        dogPhotoUri: String? = null,
    ): ProfileInfo {
        val normalizedOwner = ownerName.trim()
        val normalizedDog = dogName.trim()
        val normalizedSuburb = suburb.trim()
        val normalizedPhoto = dogPhotoUri?.trim().orEmpty()
        var next = profile
        if (normalizedOwner.isNotBlank()) {
            next = next.copy(displayName = normalizedOwner)
        }
        if (normalizedSuburb.isNotBlank()) {
            next = next.copy(
                suburb = normalizedSuburb,
                favoriteSuburbs = listOf(normalizedSuburb) + next.favoriteSuburbs.filterNot { value ->
                    value.equals(normalizedSuburb, ignoreCase = true)
                },
            )
        }
        if (normalizedOwner.isNotBlank() && normalizedDog.isNotBlank()) {
            next = next.copy(
                dogName = normalizedDog,
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

    private suspend fun persistProfileInfoSilently(profileInfo: ProfileInfo) {
        val normalized = if (isStagingTestBuild()) {
            profileInfo.copy(suburb = STAGING_TEST_SUBURB)
        } else {
            profileInfo
        }
        runCatching {
            repository.saveUserProfile(
                displayName = normalized.displayName,
                email = normalized.email,
                phone = normalized.phone,
                humanPronouns = normalized.humanPronouns,
                humanRoleLabel = normalized.humanRoleLabel,
                serviceProviderMode = normalized.serviceProviderMode,
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
        }
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
            bio = "",
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
        val mergedProfile = mergeProfileInfoFromAiResponse(state.profileInfo, response)
        val mergedSuburb = mergedProfile.suburb.trim().ifBlank { state.selectedSuburb }
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
        _uiState.value = state.withNavigation { copy(selectedTab = AppTab.BarkAI) }.copy(
            chat = response,
            conversation = hydratedConversation,
            profileSuggestion = response.profileSuggestion,
            a2uiProfileCard = parsed.first,
            a2uiProviderCard = parsed.second,
            barkThreads = upsertBarkThread(state.barkThreads, updatedThread),
            profileInfo = mergedProfile,
            selectedSuburb = mergedSuburb,
            loading = false,
            streamingAssistantText = "",
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

private fun mergeProfileInfoFromAiResponse(profile: ProfileInfo, response: ChatResponse): ProfileInfo {
    val ownerName = response.profileSuggestion?.ownerName
        ?.trim()
        ?.ifBlank { null }
        ?: response.suggestedProfile.readString("owner_name")?.trim()?.ifBlank { null }
    val suburb = response.profileSuggestion?.suburb
        ?.trim()
        ?.ifBlank { null }
        ?: response.suggestedProfile.readString("suburb")?.trim()?.ifBlank { null }

    var next = profile
    if (ownerName != null) {
        next = next.copy(displayName = ownerName)
    }
    if (suburb != null) {
        next = next.copy(
            suburb = suburb,
            favoriteSuburbs = listOf(suburb) + next.favoriteSuburbs.filterNot { value ->
                value.equals(suburb, ignoreCase = true)
            },
        )
    }
    return next
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

private inline fun UiState.withNavigation(
    update: NavigationState.() -> NavigationState,
): UiState = copy(navigation = navigation.update())

private fun elapsedMs(startNs: Long): Long = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(0L)

private fun resolveBarkThreadTitle(existingTitle: String, conversation: List<ChatTurn>): String {
    if (existingTitle != "New thread" && existingTitle != "Thread 1") return existingTitle
    val firstUser = conversation.firstOrNull { it.role == "user" }?.content?.trim().orEmpty()
    if (firstUser.isBlank()) return existingTitle
    return if (firstUser.length <= 36) firstUser else firstUser.take(33).trimEnd() + "..."
}

internal fun buildFriendProfiles(
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
    val defaultFriendIds = existingFriendIds + messageThreads
        .asSequence()
        .map { thread -> thread.participantUserId }
        .filter { userId -> userId.isNotBlank() }
        .toSet()

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

private fun directMessageThreadId(userA: String, userB: String): String {
    val ordered = listOf(userA.trim(), userB.trim()).sorted()
    return "dm_${ordered[0]}_${ordered[1]}"
}

internal fun resolveMessageOpen(
    state: UiState,
    target: MessageTarget,
): MessageOpenResolution {
    val normalizedThreadId = target.threadId?.trim().orEmpty()
    val normalizedUserId = target.userId?.trim().orEmpty()
    if (normalizedThreadId.isNotBlank()) {
        val thread = state.messageThreads.firstOrNull { candidate -> candidate.id == normalizedThreadId }
        if (thread != null) {
            return MessageOpenResolution.SelectExistingThread(thread.id)
        }
    }
    if (normalizedUserId.isBlank()) {
        return MessageOpenResolution.NoTarget
    }
    val thread = state.messageThreads.firstOrNull { candidate -> candidate.participantUserId == normalizedUserId }
    if (thread != null) {
        return MessageOpenResolution.SelectExistingThread(thread.id)
    }
    val friend = state.friendProfiles.firstOrNull { profile -> profile.userId == normalizedUserId }
    val (syntheticThread, updatedThreads) = ensureMessageThreadForUser(
        state = state,
        userId = normalizedUserId,
        humanName = friend?.humanName.orEmpty(),
        dogName = friend?.dogName.orEmpty(),
        avatarUrl = friend?.dogPhotoUrl,
    )
    return MessageOpenResolution.CreateSyntheticThread(
        selectedThreadId = syntheticThread.id,
        messageThreads = updatedThreads,
    )
}

private fun ensureMessageThreadForUser(
    state: UiState,
    userId: String,
    humanName: String,
    dogName: String,
    avatarUrl: String? = null,
): Pair<MessageThread, List<MessageThread>> {
    val existing = state.messageThreads.firstOrNull { thread -> thread.participantUserId == userId }
    if (existing != null) {
        return existing to state.messageThreads
    }
    val inserted = MessageThread(
        id = directMessageThreadId(state.activeUserId, userId),
        title = humanName.ifBlank { accountLabel(userId) },
        participantUserId = userId,
        participantAccountLabel = humanName.ifBlank { accountLabel(userId) },
        participantAvatarUrl = avatarUrl?.takeIf { it.isNotBlank() } ?: KNOWN_FRIEND_PROFILES[userId]?.second?.second,
        participantPetNames = listOfNotNull(dogName.takeIf { it.isNotBlank() }),
        lastMessage = "Say hi to start chatting",
        unreadCount = 0,
    )
    val updatedThreads = (state.messageThreads + inserted).sortedWith(
        compareByDescending<MessageThread> { thread -> thread.isPinned }
            .thenByDescending { thread -> thread.unreadCount > 0 }
            .thenBy { thread -> thread.title.lowercase() },
    )
    return inserted to updatedThreads
}

internal fun mergeLocalMessageThreads(
    currentThreads: List<MessageThread>,
    refreshedThreads: List<MessageThread>,
): List<MessageThread> {
    if (currentThreads.isEmpty()) return refreshedThreads
    val refreshedByParticipant = refreshedThreads.associateBy { thread -> thread.participantUserId }
    val preservedLocalThreads = currentThreads.filter { thread ->
        thread.participantUserId.isNotBlank() &&
            thread.participantUserId !in refreshedByParticipant &&
            thread.unreadCount == 0 &&
            thread.lastMessage == "Say hi to start chatting"
    }
    if (preservedLocalThreads.isEmpty()) return refreshedThreads
    return (refreshedThreads + preservedLocalThreads)
        .distinctBy { thread -> thread.id }
}

internal fun clearedProviderState(
    providerOsSurface: Boolean,
): Triple<Boolean, Boolean, Boolean> {
    return Triple(
        providerOsSurface,
        false,
        providerOsSurface,
    )
}

internal fun deriveProviderState(
    providerOsSurface: Boolean,
    profileProviderMode: Boolean,
    hasProviderListings: Boolean,
): ProviderStateResolution {
    return ProviderStateResolution(
        providerModeEnabled = providerOsSurface || profileProviderMode,
        hasProviderListings = hasProviderListings,
        canLoadProviderInbox = providerOsSurface || profileProviderMode || hasProviderListings,
    )
}

internal fun resolveHomePayloadState(
    selectedMessageThreadId: String?,
    validMessageThreadIds: List<String>,
    selectedCommunityGroupId: String?,
    validGroupIds: List<String>,
    providerOsSurface: Boolean,
    profileProviderMode: Boolean,
    hasProviderListings: Boolean,
): HomePayloadStateResolution {
    return HomePayloadStateResolution(
        providerState = deriveProviderState(
            providerOsSurface = providerOsSurface,
            profileProviderMode = profileProviderMode,
            hasProviderListings = hasProviderListings,
        ),
        selectedMessageThreadId = selectedMessageThreadId?.takeIf { candidate -> candidate in validMessageThreadIds },
        selectedCommunityGroupId = selectedCommunityGroupId?.takeIf { candidate -> candidate in validGroupIds },
    )
}

internal fun resolveBarkAiEntry(
    onboardingActive: Boolean,
    selectedBarkThreadId: String,
    barkThreads: List<BarkThread>,
    newThreadId: String,
    updatedAt: Long,
): BarkAiEntryResolution {
    if (onboardingActive) {
        return BarkAiEntryResolution.StayOnOnboarding(
            selectedThreadId = barkThreads.firstOrNull { thread -> thread.id == selectedBarkThreadId }?.id ?: ONBOARD_THREAD_ID,
        )
    }
    val newThread = BarkThread(
        id = newThreadId,
        title = "New thread",
        updatedAt = updatedAt,
    )
    val nextThreads = (listOf(newThread) + barkThreads.filterNot { thread -> thread.id == newThreadId })
        .take(20)
    return BarkAiEntryResolution.StartNewThread(
        selectedThreadId = newThread.id,
        barkThreads = nextThreads,
    )
}

internal fun resolveNotificationRoute(deepLink: String): NotificationRoute {
    val normalized = deepLink.trim()
    return when {
        normalized.startsWith("group:") -> NotificationRoute(
            tab = AppTab.Community,
            selectedCommunityGroupId = normalized.removePrefix("group:").trim().ifBlank { null },
        )
        normalized.startsWith("event:") || normalized.startsWith("post:") -> NotificationRoute(
            tab = AppTab.Community,
            shouldReload = true,
        )
        normalized.startsWith("provider:") -> NotificationRoute(
            tab = AppTab.Services,
            providerId = normalized.removePrefix("provider:").trim().ifBlank { null },
        )
        normalized.startsWith("booking:") || normalized.startsWith("quote:") -> NotificationRoute(
            tab = AppTab.Services,
        )
        normalized.startsWith("message:") -> NotificationRoute(
            tab = AppTab.Messages,
        )
        normalized == "profile" -> NotificationRoute(
            tab = AppTab.Profile,
        )
        else -> NotificationRoute(
            tab = AppTab.Community,
        )
    }
}

internal fun resolveNotificationOpen(
    deepLink: String,
    currentPostsSortBy: String,
    notificationRead: Boolean,
): NotificationOpenResolution {
    val normalized = deepLink.trim()
    if (normalized.isBlank()) {
        return NotificationOpenResolution(
            route = null,
            postsSortBy = currentPostsSortBy,
            shouldMarkRead = false,
            shouldReloadHome = false,
        )
    }
    val route = resolveNotificationRoute(normalized)
    return NotificationOpenResolution(
        route = route,
        postsSortBy = if (route.shouldReload && route.tab == AppTab.Community) "relevance" else currentPostsSortBy,
        shouldMarkRead = !notificationRead,
        shouldReloadHome = route.shouldReload,
        providerIdToLoad = route.providerId?.takeIf { it.isNotBlank() },
    )
}

internal fun resolveLocalNotificationRead(
    notifications: List<AppNotification>,
    readLocalNotificationIds: Set<String>,
    notificationId: String,
): LocalNotificationStateResolution {
    val normalized = notificationId.trim()
    if (!normalized.startsWith("local:")) {
        return LocalNotificationStateResolution(
            notifications = notifications,
            readLocalNotificationIds = readLocalNotificationIds,
            acknowledgedCommunityNotificationIds = emptySet(),
            acknowledgedMessageNotificationIds = emptySet(),
        )
    }
    return LocalNotificationStateResolution(
        notifications = notifications.map { existing ->
            if (existing.id == normalized) existing.copy(read = true) else existing
        },
        readLocalNotificationIds = readLocalNotificationIds + normalized,
        acknowledgedCommunityNotificationIds = emptySet(),
        acknowledgedMessageNotificationIds = emptySet(),
    )
}

internal fun resolveLocalNotificationIdsRead(
    notifications: List<AppNotification>,
    readLocalNotificationIds: Set<String>,
    notificationIds: Set<String>,
    toastMessage: String? = null,
): LocalNotificationStateResolution {
    val localIds = notificationIds
        .map { id -> id.trim() }
        .filter { id -> id.startsWith("local:") }
        .toSet()
    return LocalNotificationStateResolution(
        notifications = notifications.map { notification ->
            if (notification.id in localIds) notification.copy(read = true) else notification
        },
        readLocalNotificationIds = readLocalNotificationIds + localIds,
        acknowledgedCommunityNotificationIds = emptySet(),
        acknowledgedMessageNotificationIds = emptySet(),
        toastMessage = toastMessage,
    )
}

internal fun resolveClearAllLocalNotifications(
    notifications: List<AppNotification>,
    toastMessage: String,
): LocalNotificationStateResolution {
    return LocalNotificationStateResolution(
        notifications = notifications.filterNot { notification -> notification.id.startsWith("local:") },
        readLocalNotificationIds = emptySet(),
        acknowledgedCommunityNotificationIds = emptySet(),
        acknowledgedMessageNotificationIds = emptySet(),
        toastMessage = toastMessage,
    )
}

internal fun resolveClearLocalNotificationIds(
    notifications: List<AppNotification>,
    readLocalNotificationIds: Set<String>,
    acknowledgedCommunityNotificationIds: Set<String>,
    acknowledgedMessageNotificationIds: Set<String>,
    notificationIds: List<String>,
): LocalNotificationStateResolution {
    val localIds = notificationIds
        .map { id -> id.trim() }
        .filter { id -> id.startsWith("local:") }
        .toSet()
    if (localIds.isEmpty()) {
        return LocalNotificationStateResolution(
            notifications = notifications,
            readLocalNotificationIds = readLocalNotificationIds,
            acknowledgedCommunityNotificationIds = acknowledgedCommunityNotificationIds,
            acknowledgedMessageNotificationIds = acknowledgedMessageNotificationIds,
        )
    }
    return LocalNotificationStateResolution(
        notifications = notifications.filterNot { notification -> notification.id in localIds },
        readLocalNotificationIds = readLocalNotificationIds - localIds,
        acknowledgedCommunityNotificationIds = acknowledgedCommunityNotificationIds - localIds,
        acknowledgedMessageNotificationIds = acknowledgedMessageNotificationIds - localIds,
        toastMessage = "Local notifications cleared",
    )
}

internal fun resolveOnboardingCompletion(
    barkThreads: List<BarkThread>,
    fallbackThreadId: String,
    fallbackUpdatedAt: Long,
): OnboardingCompletionResolution {
    val nextThreads = barkThreads.filterNot { it.id == ONBOARD_THREAD_ID }
    val fallbackThread = BarkThread(
        id = fallbackThreadId,
        title = "New thread",
        updatedAt = fallbackUpdatedAt,
    )
    val remainingThreads = if (nextThreads.isEmpty()) listOf(fallbackThread) else nextThreads
    val selectedThread = remainingThreads.first()
    return OnboardingCompletionResolution(
        selectedThreadId = selectedThread.id,
        barkThreads = remainingThreads,
        chat = selectedThread.chat,
        conversation = selectedThread.conversation,
    )
}

internal fun resolveTabSwitchState(
    navigation: NavigationState,
    tab: AppTab,
    directMessages: List<DirectMessage>,
    notifications: List<AppNotification>,
    acknowledgedCommunityNotificationIds: Set<String>,
    acknowledgedMessageNotificationIds: Set<String>,
): TabSwitchResolution {
    return TabSwitchResolution(
        navigation = navigation.copy(
            selectedTab = tab,
            selectedMessageThreadId = navigation.selectedMessageThreadId,
            selectedCommunityGroupId = if (tab == AppTab.Community) navigation.selectedCommunityGroupId else null,
            profileNotificationFilter = if (tab == AppTab.Profile) navigation.profileNotificationFilter else "all",
        ),
        directMessages = directMessages,
        acknowledgedCommunityNotificationIds = when (tab) {
            AppTab.Community -> acknowledgedCommunityNotificationIds + notifications
                .asSequence()
                .filter { notification -> isCommunityNotification(notification.category) }
                .map { notification -> notification.id }
                .toSet()
            else -> acknowledgedCommunityNotificationIds
        },
        acknowledgedMessageNotificationIds = when (tab) {
            AppTab.Messages -> acknowledgedMessageNotificationIds + notifications
                .asSequence()
                .filter { notification -> isMessageNotification(notification.category) }
                .map { notification -> notification.id }
                .toSet()
            else -> acknowledgedMessageNotificationIds
        },
    )
}

internal fun resolveFriendQrTokenInput(friendToken: String): FriendQrTokenResolution {
    val cleanToken = friendToken.trim()
    return if (cleanToken.isBlank()) {
        FriendQrTokenResolution.Invalid("Invalid friend QR")
    } else {
        FriendQrTokenResolution.Verify(cleanToken)
    }
}

internal fun resolveFriendQrVerificationFailure(statusCode: Int?): String = when (statusCode) {
    401 -> "Friend QR expired. Ask for a new one."
    409 -> "This is your profile QR"
    else -> "Unable to verify friend QR"
}

internal fun resolveFriendMutation(
    state: UiState,
    action: FriendMutationAction,
    userId: String,
    humanName: String? = null,
    dogName: String? = null,
): FriendMutationResolution {
    val normalized = userId.trim()
    if (normalized.isBlank()) return FriendMutationResolution.NoChange
    if (action == FriendMutationAction.AddOrUpdate && normalized == state.activeUserId) {
        return FriendMutationResolution.ToastOnly("This is your profile QR")
    }
    return when (action) {
        FriendMutationAction.AddOrUpdate -> {
            val incomingHumanName = humanName?.trim().orEmpty()
            val incomingDogName = dogName?.trim().orEmpty()
            val existing = state.friendProfiles.firstOrNull { profile -> profile.userId == normalized }
            val wasAlreadyFriend = existing?.isFriend == true
            val ensuredThread = ensureMessageThreadForUser(
                state = state,
                userId = normalized,
                humanName = incomingHumanName,
                dogName = incomingDogName,
            )
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
            FriendMutationResolution.StateUpdate(
                friendProfiles = updatedProfiles,
                messageThreads = ensuredThread.second,
                toastMessage = if (wasAlreadyFriend) "Already friends" else "Friend added",
            )
        }
        FriendMutationAction.Remove -> FriendMutationResolution.StateUpdate(
            friendProfiles = state.friendProfiles.map { profile ->
                if (profile.userId == normalized) profile.copy(isFriend = false) else profile
            },
            messageThreads = state.messageThreads,
            toastMessage = "Friend removed",
        )
    }
}

internal fun resolveProfileSocialMessageTarget(userId: String): MessageTarget {
    return MessageTarget(
        userId = userId,
        source = "profile_social",
    )
}

internal fun shouldRefreshProviderInbox(canLoadProviderInbox: Boolean): Boolean = canLoadProviderInbox

internal fun resolveQuoteOfferSubmission(
    providerInboxItems: List<ProviderInboxItem>,
    sendingQuoteOfferItemIds: Set<String>,
    inboxItemId: String,
    priceCents: Int,
    proposedDate: String,
    proposedTimeSlot: String,
    expiresAt: String,
    note: String = "",
): QuoteOfferSubmissionResolution {
    val normalizedId = inboxItemId.trim()
    if (normalizedId.isBlank()) return QuoteOfferSubmissionResolution.Ignore
    if (priceCents <= 0) {
        return QuoteOfferSubmissionResolution.Toast("Offer price must be greater than zero")
    }
    val cleanedDate = proposedDate.trim()
    val cleanedTimeSlot = proposedTimeSlot.trim()
    val cleanedExpiresAt = expiresAt.trim()
    if (cleanedDate.isBlank() || cleanedTimeSlot.isBlank() || cleanedExpiresAt.isBlank()) {
        return QuoteOfferSubmissionResolution.Toast("Offer date, time, and expiry are required")
    }
    val item = providerInboxItems.firstOrNull { inboxItem -> inboxItem.id == normalizedId }
        ?: return QuoteOfferSubmissionResolution.Toast("Inbox item not found")
    val quoteRequestId = item.quoteRequestId?.trim().orEmpty()
    if (item.itemType != "quote_request" || quoteRequestId.isBlank()) {
        return QuoteOfferSubmissionResolution.Toast("Only quote requests can receive offers")
    }
    if (normalizedId in sendingQuoteOfferItemIds) return QuoteOfferSubmissionResolution.Ignore
    return QuoteOfferSubmissionResolution.Submit(
        inboxItemId = normalizedId,
        quoteRequestId = quoteRequestId,
        providerId = item.providerId,
        providerName = item.providerName,
        priceCents = priceCents,
        proposedDate = cleanedDate,
        proposedTimeSlot = cleanedTimeSlot,
        expiresAt = cleanedExpiresAt,
        note = note.trim(),
    )
}

internal fun resolveBookingRequest(
    state: UiState,
    providerId: String,
    date: String,
    timeSlot: String,
    note: String,
): BookingRequestResolution {
    val normalizedProviderId = providerId.trim()
    val normalizedDate = date.trim()
    val normalizedTimeSlot = timeSlot.trim()
    if (normalizedProviderId.isBlank() || normalizedDate.isBlank() || normalizedTimeSlot.isBlank()) {
        return BookingRequestResolution.Toast("Select a provider, date, and time slot")
    }
    val providerOwnerUserId = state.providers.firstOrNull { it.id == normalizedProviderId }?.ownerUserId
    val approvalHint = nextActionSwitchHint(
        targetUserId = providerOwnerUserId,
        activeUserId = state.activeUserId,
        actionText = "approve this booking",
    )
    return BookingRequestResolution.Submit(
        providerId = normalizedProviderId,
        date = normalizedDate,
        timeSlot = normalizedTimeSlot,
        note = note.trim(),
        approvalHint = approvalHint,
    )
}

internal fun resolveServiceQuoteRequest(
    selectedSuburb: String,
    stagingTestBuild: Boolean,
    category: String,
    preferredWindow: String,
    petDetails: String,
    note: String,
): ServiceQuoteRequestResolution {
    val cleanedCategory = normalizeServiceCategory(category)
    val cleanedWindow = preferredWindow.trim()
    val cleanedPetDetails = petDetails.trim()
    val cleanedSuburb = selectedSuburb.trim()
    if (cleanedCategory.isBlank() || cleanedWindow.isBlank() || cleanedPetDetails.isBlank()) {
        return ServiceQuoteRequestResolution.Toast("Complete category, preferred window, and pet details")
    }
    return ServiceQuoteRequestResolution.Submit(
        category = cleanedCategory,
        preferredWindow = cleanedWindow,
        petDetails = cleanedPetDetails,
        note = note.trim(),
        suburb = cleanedSuburb.ifBlank { null },
    )
}

internal fun resolveHomeLoadIndicatorState(
    showLoadingIndicators: Boolean,
    shouldLoadProviderInbox: Boolean,
    currentLoading: Boolean,
    currentLoadingProviderInbox: Boolean,
): HomeLoadIndicatorState {
    if (!showLoadingIndicators) {
        return HomeLoadIndicatorState(
            loading = currentLoading,
            loadingProviderInbox = currentLoadingProviderInbox,
        )
    }
    return HomeLoadIndicatorState(
        loading = true,
        loadingProviderInbox = shouldLoadProviderInbox,
    )
}

internal fun resolveServiceQuoteSuburb(
    selectedSuburb: String,
    currentLocationSuburb: String?,
    profileSuburb: String,
    recommendationSuburb: String?,
    providers: List<ServiceProvider>,
    category: String,
): String? {
    val normalizedCategory = normalizeServiceCategory(category)
    val visibleProviderSuburb = providers
        .firstOrNull { provider ->
            normalizeServiceCategory(provider.category) == normalizedCategory && provider.suburb.isNotBlank()
        }
        ?.suburb
        ?.trim()
    return listOf(
        visibleProviderSuburb,
        currentLocationSuburb?.trim(),
        recommendationSuburb?.trim(),
        profileSuburb.trim(),
        selectedSuburb.trim(),
    ).firstOrNull { suburb -> !suburb.isNullOrBlank() }
}

private fun normalizeServiceCategory(category: String): String {
    return when (category.trim().lowercase()) {
        "walking", "dog walking", "dog_walking" -> "dog_walking"
        "grooming", "groomer", "groomers" -> "grooming"
        else -> category.trim()
    }
}

internal fun resolveProfileNotificationNavigation(
    navigation: NavigationState,
    filter: String,
): NavigationState {
    val normalized = when (filter.lowercase()) {
        "community", "messages", "safety" -> filter.lowercase()
        else -> "all"
    }
    return navigation.copy(
        selectedTab = AppTab.Profile,
        profileNotificationFilter = normalized,
    )
}

internal fun resolveCommunityGroupNavigation(
    navigation: NavigationState,
    groupId: String,
): NavigationState? {
    val normalized = groupId.trim()
    if (normalized.isBlank()) return null
    return navigation.copy(
        selectedTab = AppTab.Community,
        selectedCommunityGroupId = normalized,
    )
}

internal fun clearSelectedCommunityGroupNavigation(
    navigation: NavigationState,
): NavigationState = navigation.copy(selectedCommunityGroupId = null)

internal fun resolveSessionResetState(
    state: UiState,
    providerOsSurface: Boolean,
    authRequired: Boolean,
    activeUserId: String,
    toastMessage: String,
): SessionResetResolution {
    val (providerModeEnabled, hasProviderListings, canLoadProviderInbox) = clearedProviderState(
        providerOsSurface = providerOsSurface,
    )
    return SessionResetResolution(
        navigation = state.navigation.copy(
            selectedMessageThreadId = null,
            pendingInvite = null,
        ),
        providerModeEnabled = providerModeEnabled,
        hasProviderListings = hasProviderListings,
        canLoadProviderInbox = canLoadProviderInbox,
        authRequired = authRequired,
        isCommunityModerator = activeUserId in COMMUNITY_MODERATOR_IDS,
        toastMessage = toastMessage,
    )
}

internal fun applySessionResetResolution(
    state: UiState,
    resolution: SessionResetResolution,
): UiState {
    return state.copy(
        navigation = resolution.navigation,
        providerModeEnabled = resolution.providerModeEnabled,
        hasProviderListings = resolution.hasProviderListings,
        canLoadProviderInbox = resolution.canLoadProviderInbox,
        authRequired = resolution.authRequired,
        authOtpRequested = resolution.authOtpRequested,
        authInviteId = resolution.authInviteId,
        authEmail = resolution.authEmail,
        authOtpExpiresAt = resolution.authOtpExpiresAt,
        authInFlight = resolution.authInFlight,
        providers = resolution.providers,
        nearbyPetBusinesses = resolution.nearbyPetBusinesses,
        groups = resolution.groups,
        posts = resolution.posts,
        communityCommentsByPostId = resolution.communityCommentsByPostId,
        communityEvents = resolution.communityEvents,
        ownerBookings = resolution.ownerBookings,
        providerBookings = resolution.providerBookings,
        calendarEvents = resolution.calendarEvents,
        messageThreads = resolution.messageThreads,
        directMessages = resolution.directMessages,
        readDirectMessageIds = resolution.readDirectMessageIds,
        savedCommunityPostIds = resolution.savedCommunityPostIds,
        savedCommunityEventIds = resolution.savedCommunityEventIds,
        latestGroupInvites = resolution.latestGroupInvites,
        friendProfiles = resolution.friendProfiles,
        notifications = resolution.notifications,
        readLocalNotificationIds = resolution.readLocalNotificationIds,
        acknowledgedCommunityNotificationIds = resolution.acknowledgedCommunityNotificationIds,
        acknowledgedMessageNotificationIds = resolution.acknowledgedMessageNotificationIds,
        loading = resolution.loading,
        error = resolution.error,
        isCommunityModerator = resolution.isCommunityModerator,
        toastMessage = resolution.toastMessage,
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
    val refreshedThreads = buildMessageThreadsFromApi(
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
    return applyThreadPresentationFlags(
        threads = mergeLocalMessageThreads(
            currentThreads = state.messageThreads,
            refreshedThreads = refreshedThreads,
        ),
        mutedThreadIds = state.mutedMessageThreadIds,
        pinnedThreadIds = state.pinnedMessageThreadIds,
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
                participantAvatarUrl = KNOWN_FRIEND_PROFILES[thread.participantUserId]?.second?.second,
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

private fun applyFriendProfileAvatars(
    threads: List<MessageThread>,
    friendProfiles: List<FriendProfile>,
): List<MessageThread> {
    val friendByUserId = friendProfiles.associateBy { profile -> profile.userId }
    return threads.map { thread ->
        val friend = friendByUserId[thread.participantUserId]
        val humanName = friend?.humanName?.takeIf { value -> value.isNotBlank() }
        val petNames = friend?.dogName
            ?.takeIf { value -> value.isNotBlank() }
            ?.let { listOf(it) }
            ?: thread.participantPetNames
        thread.copy(
            title = humanName ?: thread.title,
            participantAccountLabel = humanName ?: thread.participantAccountLabel,
            participantAvatarUrl = friend?.dogPhotoUrl
                ?.takeIf { value -> value.isNotBlank() }
                ?: thread.participantAvatarUrl,
            participantPetNames = petNames,
        )
    }
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

private data class ServicesTabPayload(
    val providers: List<ServiceProvider>,
    val recommendationSuburb: String?,
    val recommendationSource: String,
    val nearbyPetBusinesses: List<NearbyPetBusiness>,
)

private data class CommunityTabPayload(
    val groups: List<Group>,
    val posts: List<CommunityPost>,
    val events: List<CommunityEvent>,
)

private data class ProfileTabPayload(
    val ownerBookings: List<BookingResponse>,
    val providerBookings: List<BookingResponse>,
    val providerInboxItems: List<ProviderInboxItem>,
    val calendarEvents: List<CalendarEvent>,
)

private data class MessagesTabPayload(
    val messageThreads: List<ApiMessageThread>,
    val selectedMessageThreadId: String?,
    val selectedThreadMessages: List<ApiDirectMessage>,
)

private data class SharedHomePayload(
    val notifications: List<AppNotification>,
    val profileInfo: ProfileInfo,
    val blockedUserIds: List<String>,
    val moderationReports: List<CommunityReport>,
    val communityFunnelMetrics: CommunityFunnelMetrics?,
    val activationFunnelMetrics: CommunityActivationFunnel?,
)

private data class ProviderHomeSnapshot(
    val syncedFavorites: List<String>,
    val syncedListings: List<ProviderListing>,
    val ownerBookings: List<OwnerBooking>,
    val providerBookings: List<ProviderBooking>,
    val providerIdsInScope: Set<String>,
)

private data class MessagingHomeSnapshot(
    val selectedThreadId: String?,
    val selectedThreadMessages: List<DirectMessage>,
    val validReadMessageIds: Set<String>,
    val decoratedMessageThreads: List<MessageThread>,
    val friendProfiles: List<FriendProfile>,
)

private data class NotificationHomeSnapshot(
    val mergedNotifications: List<AppNotification>,
    val validLocalReadIds: Set<String>,
)

private data class CommunityHomeSnapshot(
    val joinedEvents: List<JoinedEvent>,
    val validSavedPostIds: Set<String>,
    val validSavedEventIds: Set<String>,
    val validFollowedGroupIds: Set<String>,
    val groupRosters: Map<String, List<PetRosterItem>>,
    val groomerRosters: Map<String, List<PetRosterItem>>,
    val headerRosterPet: PetRosterItem?,
)

private data class HomeApplySnapshot(
    val providers: List<ServiceProvider>,
    val groups: List<Group>,
    val posts: List<CommunityPost>,
    val events: List<CommunityEvent>,
    val providerSnapshot: ProviderHomeSnapshot,
    val messagingSnapshot: MessagingHomeSnapshot,
    val notificationSnapshot: NotificationHomeSnapshot,
    val communitySnapshot: CommunityHomeSnapshot,
    val homePayloadState: HomePayloadStateResolution,
    val validPostIds: Set<String>,
    val validBookingIds: Set<String>,
    val validListingIds: Set<String>,
    val validNotificationIds: Set<String>,
    val profileInfo: ProfileInfo,
)

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

private suspend fun loadHomePayload(
    repository: PetSocialRepository,
    state: UiState,
    currentPayload: HomePayload,
    refreshPlan: HomeRefreshPlan,
    category: String?,
    suburb: String,
    isStagingTestBuild: Boolean,
    useCurrentLocation: Boolean,
    shouldLoadProviderInbox: Boolean,
): HomePayload = supervisorScope {
    val servicesDeferred = if (refreshPlan.fetchServices) {
        async {
            loadServicesTabPayload(
                repository = repository,
                state = state,
                category = category,
                suburb = suburb,
                isStagingTestBuild = isStagingTestBuild,
                useCurrentLocation = useCurrentLocation,
            )
        }
    } else {
        null
    }
    val ownerListingsDeferred = if (refreshPlan.fetchOwnerListings) {
        async {
            repository.loadProviders(
                userId = state.activeUserId,
                includeInactive = true,
            )
        }
    } else {
        null
    }
    val communityDeferred = if (refreshPlan.fetchCommunity) {
        async {
            loadCommunityTabPayload(
                repository = repository,
                state = state,
                suburb = suburb,
            )
        }
    } else {
        null
    }
    val profileDeferred = if (refreshPlan.fetchProfilePane) {
        async {
            loadProfileTabPayload(
                repository = repository,
                state = state,
                shouldLoadProviderInbox = shouldLoadProviderInbox,
            )
        }
    } else {
        null
    }
    val messagesDeferred = if (refreshPlan.fetchMessages) {
        async {
            loadMessagesTabPayload(
                repository = repository,
                state = state,
                currentPayload = currentPayload,
            )
        }
    } else {
        null
    }
    val sharedDeferred = if (
        refreshPlan.fetchNotifications ||
        refreshPlan.fetchProfileInfo ||
        refreshPlan.fetchBlockedUsers ||
        refreshPlan.fetchModerationReports ||
        refreshPlan.fetchFunnels
    ) {
        async {
            loadSharedHomePayload(
                repository = repository,
                state = state,
                currentPayload = currentPayload,
                refreshPlan = refreshPlan,
                suburb = suburb,
                isStagingTestBuild = isStagingTestBuild,
            )
        }
    } else {
        null
    }

    var payload = currentPayload

    servicesDeferred?.await()?.let { services ->
        payload = payload.copy(
            providers = services.providers,
            recommendationSuburb = services.recommendationSuburb,
            recommendationSource = services.recommendationSource,
            nearbyPetBusinesses = services.nearbyPetBusinesses,
        )
    }
    ownerListingsDeferred?.await()?.let { ownerListingProviders ->
        payload = payload.copy(ownerListingProviders = ownerListingProviders)
    }
    communityDeferred?.await()?.let { community ->
        payload = payload.copy(
            groups = community.groups,
            posts = community.posts,
            events = community.events,
        )
    }
    messagesDeferred?.await()?.let { messages ->
        payload = payload.copy(
            messageThreads = messages.messageThreads,
            selectedMessageThreadId = messages.selectedMessageThreadId,
            selectedThreadMessages = messages.selectedThreadMessages,
        )
    }
    sharedDeferred?.await()?.let { shared ->
        payload = payload.copy(
            notifications = shared.notifications,
            profileInfo = shared.profileInfo,
            blockedUserIds = shared.blockedUserIds,
            moderationReports = shared.moderationReports,
            communityFunnelMetrics = shared.communityFunnelMetrics,
            activationFunnelMetrics = shared.activationFunnelMetrics,
        )
    }
    profileDeferred?.await()?.let { profile ->
        val providersWithBookings = payload.providers + resolveMissingBookingProviders(
            repository = repository,
            knownProviders = payload.providers,
            ownerBookings = profile.ownerBookings,
        )
        payload = payload.copy(
            providers = providersWithBookings.distinctBy { provider -> provider.id },
            ownerBookings = profile.ownerBookings,
            providerBookings = profile.providerBookings,
            providerInboxItems = profile.providerInboxItems,
            calendarEvents = profile.calendarEvents,
        )
    }

    payload
}

private suspend fun loadServicesTabPayload(
    repository: PetSocialRepository,
    state: UiState,
    category: String?,
    suburb: String,
    isStagingTestBuild: Boolean,
    useCurrentLocation: Boolean,
): ServicesTabPayload = supervisorScope {
    val shouldUseRecommendations = state.servicesSearchQuery.isBlank() && state.servicesSortBy == "relevance"
    val recommendationsDeferred = if (shouldUseRecommendations) {
        async {
            runCatching {
                repository.loadRecommendedProviders(
                    category = category,
                    suburb = if (isStagingTestBuild) suburb else null,
                    minRating = state.serviceMinRating?.toDouble(),
                    maxDistanceKm = state.serviceMaxDistanceKm?.toDouble(),
                    userLat = if (useCurrentLocation) state.currentLatitude else null,
                    userLng = if (useCurrentLocation) state.currentLongitude else null,
                )
            }.getOrNull()
        }
    } else {
        null
    }
    val nearbyPetBusinessesDeferred = if (useCurrentLocation) {
        async {
            repository.loadNearbyPetBusinesses(
                latitude = state.currentLatitude ?: 0.0,
                longitude = state.currentLongitude ?: 0.0,
            )
        }
    } else {
        null
    }

    val recommendations = recommendationsDeferred?.await()
    val primaryProviders = recommendations?.providers ?: repository.loadProviders(
        category = category,
        suburb = suburb,
        includeInactive = false,
        minRating = state.serviceMinRating?.toDouble(),
        maxDistanceKm = state.serviceMaxDistanceKm?.toDouble(),
        userLat = if (useCurrentLocation) state.currentLatitude else null,
        userLng = if (useCurrentLocation) state.currentLongitude else null,
        query = state.servicesSearchQuery.ifBlank { null },
        sortBy = state.servicesSortBy,
    )
    val resolvedProviders = if (primaryProviders.isNotEmpty()) {
        primaryProviders
    } else {
        val relaxedLocalProviders = repository.loadProviders(
            category = category,
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
                category = category,
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

    ServicesTabPayload(
        providers = resolvedProviders,
        recommendationSuburb = recommendations?.inferredSuburb,
        recommendationSource = recommendations?.suburbSource ?: "none",
        nearbyPetBusinesses = nearbyPetBusinessesDeferred?.await().orEmpty(),
    )
}

private suspend fun loadCommunityTabPayload(
    repository: PetSocialRepository,
    state: UiState,
    suburb: String,
): CommunityTabPayload = supervisorScope {
    val normalizedCommunitySort = normalizeCommunitySort(state.postsSortBy)
    val groupsDeferred = async {
        val localGroups = repository.loadGroups(suburb = suburb)
        if (localGroups.isNotEmpty()) localGroups else repository.loadGroups(suburb = null)
    }
    val postsDeferred = async {
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
        if (localPosts.isNotEmpty()) {
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
    }
    val eventsDeferred = async {
        val localEvents = repository.loadEvents(suburb = suburb)
        if (localEvents.isNotEmpty()) localEvents else repository.loadEvents(suburb = null)
    }

    CommunityTabPayload(
        groups = groupsDeferred.await(),
        posts = postsDeferred.await(),
        events = eventsDeferred.await(),
    )
}

private suspend fun loadProfileTabPayload(
    repository: PetSocialRepository,
    state: UiState,
    shouldLoadProviderInbox: Boolean,
): ProfileTabPayload = supervisorScope {
    val ownerBookingsDeferred = async { repository.loadOwnerBookings() }
    val providerBookingsDeferred = async { repository.loadProviderBookings() }
    val providerInboxDeferred = if (shouldLoadProviderInbox) {
        async<List<ProviderInboxItem>> {
            runCatching {
                repository.loadProviderInbox(
                    includeResolved = false,
                    limit = 50,
                ).items
            }.getOrDefault(emptyList())
        }
    } else {
        null
    }
    val calendarEventsDeferred = async {
        repository.loadCalendarEvents(role = state.selectedCalendarRole)
    }

    ProfileTabPayload(
        ownerBookings = ownerBookingsDeferred.await(),
        providerBookings = providerBookingsDeferred.await(),
        providerInboxItems = providerInboxDeferred?.await().orEmpty(),
        calendarEvents = calendarEventsDeferred.await(),
    )
}

private suspend fun loadMessagesTabPayload(
    repository: PetSocialRepository,
    state: UiState,
    currentPayload: HomePayload,
): MessagesTabPayload = supervisorScope {
    val messageThreads = runCatching { repository.loadMessageThreads(limit = 50) }
        .getOrElse { currentPayload.messageThreads }
    val selectedMessageThreadId = state.selectedMessageThreadId
        ?.takeIf { selectedThreadId -> messageThreads.any { thread -> thread.id == selectedThreadId } }
    val selectedThreadMessagesDeferred = if (selectedMessageThreadId.isNullOrBlank()) {
        null
    } else {
        async {
            runCatching {
                repository.loadThreadMessages(
                    threadId = selectedMessageThreadId,
                    limit = 200,
                )
            }.getOrDefault(currentPayload.selectedThreadMessages)
        }
    }

    MessagesTabPayload(
        messageThreads = messageThreads,
        selectedMessageThreadId = selectedMessageThreadId,
        selectedThreadMessages = selectedThreadMessagesDeferred?.await().orEmpty(),
    )
}

private suspend fun loadSharedHomePayload(
    repository: PetSocialRepository,
    state: UiState,
    currentPayload: HomePayload,
    refreshPlan: HomeRefreshPlan,
    suburb: String,
    isStagingTestBuild: Boolean,
): SharedHomePayload = supervisorScope {
    val notificationsDeferred = if (refreshPlan.fetchNotifications) {
        async { repository.loadNotifications(unreadOnly = false) }
    } else {
        null
    }
    val profileInfoDeferred = if (refreshPlan.fetchProfileInfo) {
        async {
            runCatching { repository.loadUserProfile() }
                .map { response ->
                    response.toProfileInfo(
                        activeUserId = state.activeUserId,
                        fallbackProfile = state.profileInfo,
                        fallbackSuburb = suburb,
                    )
                }
                .getOrElse {
                    state.profileInfo.copy(
                        suburb = if (isStagingTestBuild) STAGING_TEST_SUBURB else suburb,
                    )
                }
        }
    } else {
        null
    }
    val blockedUsersDeferred = if (refreshPlan.fetchBlockedUsers) {
        async<List<String>> { runCatching { repository.loadBlockedUsers().blockedUserIds }.getOrDefault(emptyList()) }
    } else {
        null
    }
    val moderationReportsDeferred = if (refreshPlan.fetchModerationReports) {
        async<List<CommunityReport>> {
            runCatching { repository.loadModerationReports(includeResolved = false) }.getOrDefault(emptyList())
        }
    } else {
        null
    }
    val communityFunnelDeferred = if (refreshPlan.fetchFunnels) {
        async { runCatching { repository.loadCommunityFunnel(windowHours = 168) }.getOrNull() }
    } else {
        null
    }
    val activationFunnelDeferred = if (refreshPlan.fetchFunnels) {
        async { runCatching { repository.loadCommunityActivationFunnel(windowHours = 72) }.getOrNull() }
    } else {
        null
    }

    SharedHomePayload(
        notifications = notificationsDeferred?.await() ?: currentPayload.notifications,
        profileInfo = profileInfoDeferred?.await() ?: currentPayload.profileInfo,
        blockedUserIds = blockedUsersDeferred?.await() ?: currentPayload.blockedUserIds,
        moderationReports = moderationReportsDeferred?.await() ?: currentPayload.moderationReports,
        communityFunnelMetrics = if (refreshPlan.fetchFunnels) {
            communityFunnelDeferred?.await()
        } else {
            currentPayload.communityFunnelMetrics
        },
        activationFunnelMetrics = if (refreshPlan.fetchFunnels) {
            activationFunnelDeferred?.await()
        } else {
            currentPayload.activationFunnelMetrics
        },
    )
}

private suspend fun resolveMissingBookingProviders(
    repository: PetSocialRepository,
    knownProviders: List<ServiceProvider>,
    ownerBookings: List<BookingResponse>,
): List<ServiceProvider> = supervisorScope {
    ownerBookings
        .map { booking -> booking.providerId }
        .filter { providerId -> providerId.isNotBlank() && knownProviders.none { provider -> provider.id == providerId } }
        .distinct()
        .map { providerId ->
            async { runCatching { repository.loadProviderDetails(providerId).provider }.getOrNull() }
        }
        .mapNotNull { deferred -> deferred.await() }
}

private fun resolveHomeRefreshPlan(
    selectedTab: AppTab,
    scope: HomeRefreshScope,
): HomeRefreshPlan {
    if (scope == HomeRefreshScope.Full) {
        return HomeRefreshPlan(
            fetchServices = true,
            fetchOwnerListings = true,
            fetchCommunity = true,
            fetchProfilePane = true,
            fetchMessages = true,
            fetchNotifications = true,
            fetchProfileInfo = true,
            fetchBlockedUsers = true,
            fetchModerationReports = true,
            fetchFunnels = true,
        )
    }
    return when (selectedTab) {
        AppTab.Services -> HomeRefreshPlan(
            fetchServices = true,
            fetchOwnerListings = false,
            fetchCommunity = false,
            fetchProfilePane = false,
            fetchMessages = false,
            fetchNotifications = true,
            fetchProfileInfo = true,
            fetchBlockedUsers = false,
            fetchModerationReports = false,
            fetchFunnels = false,
        )
        AppTab.Community -> HomeRefreshPlan(
            fetchServices = false,
            fetchOwnerListings = false,
            fetchCommunity = true,
            fetchProfilePane = false,
            fetchMessages = false,
            fetchNotifications = true,
            fetchProfileInfo = true,
            fetchBlockedUsers = true,
            fetchModerationReports = true,
            fetchFunnels = false,
        )
        AppTab.BarkAI -> HomeRefreshPlan(
            fetchServices = false,
            fetchOwnerListings = false,
            fetchCommunity = false,
            fetchProfilePane = false,
            fetchMessages = false,
            fetchNotifications = true,
            fetchProfileInfo = true,
            fetchBlockedUsers = false,
            fetchModerationReports = false,
            fetchFunnels = false,
        )
        AppTab.Messages -> HomeRefreshPlan(
            fetchServices = false,
            fetchOwnerListings = false,
            fetchCommunity = false,
            fetchProfilePane = false,
            fetchMessages = true,
            fetchNotifications = true,
            fetchProfileInfo = true,
            fetchBlockedUsers = true,
            fetchModerationReports = false,
            fetchFunnels = false,
        )
        AppTab.Profile -> HomeRefreshPlan(
            fetchServices = true,
            fetchOwnerListings = true,
            fetchCommunity = true,
            fetchProfilePane = true,
            fetchMessages = true,
            fetchNotifications = true,
            fetchProfileInfo = true,
            fetchBlockedUsers = true,
            fetchModerationReports = true,
            fetchFunnels = true,
        )
    }
}

private fun currentHomePayload(
    state: UiState,
    suburb: String,
): HomePayload {
    return HomePayload(
        providers = state.providers,
        ownerListingProviders = state.providers.filter { provider -> provider.ownerUserId == state.activeUserId },
        recommendationSuburb = state.servicesRecommendationSuburb,
        recommendationSource = state.servicesRecommendationSource,
        nearbyPetBusinesses = state.nearbyPetBusinesses,
        groups = state.groups,
        posts = state.posts,
        events = state.communityEvents,
        ownerBookings = state.ownerBookings.map { booking -> booking.toBookingResponse() },
        providerBookings = state.providerBookings.map { booking -> booking.toBookingResponse() },
        providerInboxItems = state.providerInboxItems,
        calendarEvents = state.calendarEvents,
        messageThreads = state.messageThreads.map { thread -> thread.toApiMessageThread() },
        selectedMessageThreadId = state.selectedMessageThreadId,
        selectedThreadMessages = state.directMessages
            .filter { message -> message.threadId == state.selectedMessageThreadId }
            .map { message -> message.toApiDirectMessage() },
        notifications = state.notifications.filterNot { notification -> notification.id.startsWith("local:") },
        profileInfo = state.profileInfo.copy(suburb = state.profileInfo.suburb.ifBlank { suburb }),
        blockedUserIds = state.blockedUserIds,
        moderationReports = state.moderationReports,
        communityFunnelMetrics = state.communityFunnelMetrics,
        activationFunnelMetrics = state.activationFunnelMetrics,
    )
}

private fun buildProviderHomeSnapshot(
    current: UiState,
    payload: HomePayload,
    providers: List<ServiceProvider>,
): ProviderHomeSnapshot {
    val providerById = providers.associateBy { provider -> provider.id }
    val syncedFavorites = if (current.favoriteProviderIds.isEmpty()) {
        providers.take(3).map { provider -> provider.id }
    } else {
        current.favoriteProviderIds.filter { id -> providers.any { provider -> provider.id == id } }
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
    val ownerBookings = payload.ownerBookings.map { booking ->
        val provider = providerById[booking.providerId]
        OwnerBooking(
            id = booking.id,
            serviceName = provider?.name ?: booking.providerId,
            providerId = booking.providerId,
            providerUserId = booking.counterpartyUserId
                ?: booking.providerOwnerUserId
                ?: provider?.ownerUserId
                .orEmpty(),
            threadId = booking.threadId,
            providerAccountLabel = provider?.ownerLabel
                ?: booking.providerOwnerUserId?.let(::accountLabel)
                ?: booking.counterpartyUserId?.let(::accountLabel)
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
            ownerUserId = booking.counterpartyUserId ?: booking.ownerUserId,
            threadId = booking.threadId,
            providerId = booking.providerId,
            serviceName = providerById[booking.providerId]?.name ?: booking.providerId,
            date = booking.date,
            timeSlot = booking.timeSlot,
            status = booking.status,
        )
    }
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

    return ProviderHomeSnapshot(
        syncedFavorites = syncedFavorites,
        syncedListings = syncedListings,
        ownerBookings = ownerBookings,
        providerBookings = providerBookings,
        providerIdsInScope = providerIdsInScope,
    )
}

private fun buildMessagingHomeSnapshot(
    current: UiState,
    payload: HomePayload,
    providers: List<ServiceProvider>,
    groups: List<Group>,
    posts: List<CommunityPost>,
    ownerBookings: List<OwnerBooking>,
    providerBookings: List<ProviderBooking>,
): MessagingHomeSnapshot {
    val selectedThreadId = payload.selectedMessageThreadId
        ?.takeIf { candidate -> payload.messageThreads.any { thread -> thread.id == candidate } }
        ?: current.selectedMessageThreadId?.takeIf { candidate ->
            payload.messageThreads.any { thread -> thread.id == candidate }
        }
    val selectedThreadMessages = if (selectedThreadId.isNullOrBlank()) {
        emptyList()
    } else {
        val payloadMessages = payload.selectedThreadMessages.map { message -> message.toDirectMessage() }
        if (payloadMessages.isNotEmpty()) {
            payloadMessages
        } else {
            current.directMessages.filter { message -> message.threadId == selectedThreadId }
        }
    }
    val messageThreads = buildMessageThreadsFromApi(
        activeUserId = current.activeUserId,
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

    return MessagingHomeSnapshot(
        selectedThreadId = selectedThreadId,
        selectedThreadMessages = selectedThreadMessages,
        validReadMessageIds = selectedThreadMessages.map { message -> message.id }.toSet(),
        decoratedMessageThreads = applyFriendProfileAvatars(
            threads = messageThreads,
            friendProfiles = friendProfiles,
        ),
        friendProfiles = friendProfiles,
    )
}

private fun buildNotificationHomeSnapshot(
    current: UiState,
    payload: HomePayload,
    groups: List<Group>,
    posts: List<CommunityPost>,
    events: List<CommunityEvent>,
): NotificationHomeSnapshot {
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

    return NotificationHomeSnapshot(
        mergedNotifications = mergeNotifications(
            remoteNotifications = payload.notifications,
            localNotifications = localNotifications,
            localReadIds = validLocalReadIds,
        ),
        validLocalReadIds = validLocalReadIds,
    )
}

private fun buildCommunityHomeSnapshot(
    current: UiState,
    providers: List<ServiceProvider>,
    groups: List<Group>,
    posts: List<CommunityPost>,
    events: List<CommunityEvent>,
): CommunityHomeSnapshot {
    val today = LocalDate.now()
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
    val groupRosters = buildGroupRosters(
        groups = groups,
        posts = posts,
        providers = providers,
        today = today,
    )
    val groomerRosters = buildGroomerRosters(
        providers = providers,
        today = today,
    )

    return CommunityHomeSnapshot(
        joinedEvents = joinedEvents,
        validSavedPostIds = current.savedCommunityPostIds.intersect(posts.map { post -> post.id }.toSet()),
        validSavedEventIds = current.savedCommunityEventIds.intersect(events.map { event -> event.id }.toSet()),
        validFollowedGroupIds = current.followedGroupIds.intersect(groups.map { group -> group.id }.toSet()),
        groupRosters = groupRosters,
        groomerRosters = groomerRosters,
        headerRosterPet = groupRosters.values
            .flatten()
            .dailyShuffle("header", today)
            .firstOrNull(),
    )
}

private fun buildHomeApplySnapshot(
    current: UiState,
    payload: HomePayload,
    suburb: String,
    isStagingTestBuild: Boolean,
): HomeApplySnapshot {
    val providers = payload.providers
    val groups = payload.groups
    val posts = payload.posts
    val events = payload.events
    val providerSnapshot = buildProviderHomeSnapshot(
        current = current,
        payload = payload,
        providers = providers,
    )
    val messagingSnapshot = buildMessagingHomeSnapshot(
        current = current,
        payload = payload,
        providers = providers,
        groups = groups,
        posts = posts,
        ownerBookings = providerSnapshot.ownerBookings,
        providerBookings = providerSnapshot.providerBookings,
    )
    val notificationSnapshot = buildNotificationHomeSnapshot(
        current = current,
        payload = payload,
        groups = groups,
        posts = posts,
        events = events,
    )
    val communitySnapshot = buildCommunityHomeSnapshot(
        current = current,
        providers = providers,
        groups = groups,
        posts = posts,
        events = events,
    )
    val homePayloadState = resolveHomePayloadState(
        selectedMessageThreadId = messagingSnapshot.selectedThreadId,
        validMessageThreadIds = payload.messageThreads.map { thread -> thread.id },
        selectedCommunityGroupId = current.selectedCommunityGroupId,
        validGroupIds = groups.map { group -> group.id },
        providerOsSurface = IS_PROVIDER_OS_SURFACE,
        profileProviderMode = payload.profileInfo.serviceProviderMode,
        hasProviderListings = providerSnapshot.syncedListings.isNotEmpty(),
    )
    val validBookingIds = (
        providerSnapshot.ownerBookings.map { booking -> booking.id } +
            providerSnapshot.providerBookings.map { booking -> booking.id }
        ).toSet()

    return HomeApplySnapshot(
        providers = providers,
        groups = groups,
        posts = posts,
        events = events,
        providerSnapshot = providerSnapshot,
        messagingSnapshot = messagingSnapshot,
        notificationSnapshot = notificationSnapshot,
        communitySnapshot = communitySnapshot,
        homePayloadState = homePayloadState,
        validPostIds = posts.map { post -> post.id }.toSet(),
        validBookingIds = validBookingIds,
        validListingIds = providerSnapshot.syncedListings.map { listing -> listing.id }.toSet(),
        validNotificationIds = notificationSnapshot.mergedNotifications.map { notification -> notification.id }.toSet(),
        profileInfo = payload.profileInfo.copy(
            suburb = if (isStagingTestBuild) STAGING_TEST_SUBURB else payload.profileInfo.suburb.ifBlank { suburb },
        ),
    )
}

private fun UiState.applyHomePayloadSnapshot(
    payload: HomePayload,
    snapshot: HomeApplySnapshot,
    errorMessage: String?,
    isOfflineMode: Boolean,
    hasPendingSync: Boolean,
    metrics: HomeLoadMetrics?,
): UiState {
    return withNavigation {
        copy(
            selectedMessageThreadId = snapshot.homePayloadState.selectedMessageThreadId,
            selectedCommunityGroupId = snapshot.homePayloadState.selectedCommunityGroupId,
        )
    }.copy(
        providers = reuseIfEquivalent(providers, snapshot.providers),
        nearbyPetBusinesses = reuseIfEquivalent(nearbyPetBusinesses, payload.nearbyPetBusinesses),
        groups = reuseIfEquivalent(groups, snapshot.groups),
        posts = reuseIfEquivalent(posts, snapshot.posts),
        communityCommentsByPostId = communityCommentsByPostId.filterKeys(snapshot.validPostIds::contains),
        loadingCommentPostIds = loadingCommentPostIds.filter(snapshot.validPostIds::contains).toSet(),
        communityEvents = reuseIfEquivalent(communityEvents, snapshot.events),
        ownerBookings = reuseIfEquivalent(ownerBookings, snapshot.providerSnapshot.ownerBookings),
        providerBookings = reuseIfEquivalent(providerBookings, snapshot.providerSnapshot.providerBookings),
        providerInboxItems = reuseIfEquivalent(providerInboxItems, payload.providerInboxItems),
        loadingProviderInbox = false,
        calendarEvents = reuseIfEquivalent(calendarEvents, payload.calendarEvents),
        messageThreads = reuseIfEquivalent(messageThreads, snapshot.messagingSnapshot.decoratedMessageThreads),
        friendProfiles = reuseIfEquivalent(friendProfiles, snapshot.messagingSnapshot.friendProfiles),
        directMessages = reuseIfEquivalent(directMessages, snapshot.messagingSnapshot.selectedThreadMessages),
        readDirectMessageIds = snapshot.messagingSnapshot.validReadMessageIds,
        savedCommunityPostIds = snapshot.communitySnapshot.validSavedPostIds,
        savedCommunityEventIds = snapshot.communitySnapshot.validSavedEventIds,
        mutedCommunityKeywords = mutedCommunityKeywords,
        followedGroupIds = snapshot.communitySnapshot.validFollowedGroupIds,
        joinedEvents = reuseIfEquivalent(joinedEvents, snapshot.communitySnapshot.joinedEvents),
        favoriteProviderIds = reuseIfEquivalent(favoriteProviderIds, snapshot.providerSnapshot.syncedFavorites),
        providerListings = reuseIfEquivalent(providerListings, snapshot.providerSnapshot.syncedListings),
        providerBlackoutsByProvider = providerBlackoutsByProvider.filterKeys(snapshot.validListingIds::contains),
        bookingHistoryByBookingId = bookingHistoryByBookingId.filterKeys(snapshot.validBookingIds::contains),
        loadingBookingHistoryIds = loadingBookingHistoryIds.filter(snapshot.validBookingIds::contains).toSet(),
        providerRescheduleSlotsByKey = providerRescheduleSlotsByKey.filterKeys { key ->
            snapshot.providerSnapshot.providerIdsInScope.any { providerId -> key.startsWith("$providerId|") }
        },
        loadingProviderRescheduleKeys = loadingProviderRescheduleKeys.filter { key ->
            snapshot.providerSnapshot.providerIdsInScope.any { providerId -> key.startsWith("$providerId|") }
        }.toSet(),
        headerRosterPet = snapshot.communitySnapshot.headerRosterPet,
        groupPetRosters = reuseIfEquivalent(groupPetRosters, snapshot.communitySnapshot.groupRosters),
        groomerPetRosters = reuseIfEquivalent(groomerPetRosters, snapshot.communitySnapshot.groomerRosters),
        servicesRecommendationSuburb = payload.recommendationSuburb,
        servicesRecommendationSource = payload.recommendationSource,
        profileInfo = snapshot.profileInfo,
        providerModeEnabled = snapshot.homePayloadState.providerState.providerModeEnabled,
        hasProviderListings = snapshot.homePayloadState.providerState.hasProviderListings,
        canLoadProviderInbox = snapshot.homePayloadState.providerState.canLoadProviderInbox,
        loading = false,
        error = errorMessage,
        authRequired = false,
        authInFlight = false,
        latestHomeLoadMetrics = metrics ?: latestHomeLoadMetrics,
        isOfflineMode = isOfflineMode,
        hasPendingSync = hasPendingSync,
        notifications = reuseIfEquivalent(notifications, snapshot.notificationSnapshot.mergedNotifications),
        readLocalNotificationIds = snapshot.notificationSnapshot.validLocalReadIds,
        acknowledgedCommunityNotificationIds = acknowledgedCommunityNotificationIds.intersect(snapshot.validNotificationIds),
        acknowledgedMessageNotificationIds = acknowledgedMessageNotificationIds.intersect(snapshot.validNotificationIds),
        blockedUserIds = reuseIfEquivalent(blockedUserIds, payload.blockedUserIds),
        moderationReports = reuseIfEquivalent(moderationReports, payload.moderationReports),
        communityFunnelMetrics = payload.communityFunnelMetrics,
        activationFunnelMetrics = payload.activationFunnelMetrics ?: activationFunnelMetrics,
        isCommunityModerator = activeUserId in COMMUNITY_MODERATOR_IDS,
    )
}

private fun OwnerBooking.toBookingResponse(): BookingResponse = BookingResponse(
    id = id,
    ownerUserId = "",
    providerId = providerId,
    providerOwnerUserId = providerUserId.ifBlank { null },
    counterpartyUserId = providerUserId.ifBlank { null },
    threadId = threadId,
    petName = "",
    date = date,
    timeSlot = timeSlot,
    note = note,
    status = status,
)

private fun ProviderBooking.toBookingResponse(): BookingResponse = BookingResponse(
    id = id,
    ownerUserId = ownerUserId,
    providerId = providerId,
    counterpartyUserId = ownerUserId.ifBlank { null },
    threadId = threadId,
    petName = petName,
    date = date,
    timeSlot = timeSlot,
    note = "",
    status = status,
)

private fun MessageThread.toApiMessageThread(): ApiMessageThread = ApiMessageThread(
    id = id,
    participantUserId = participantUserId,
    lastMessage = lastMessage,
    lastMessageAt = Instant.now().toString(),
    unreadCount = unreadCount,
)

private fun DirectMessage.toApiDirectMessage(): ApiDirectMessage = ApiDirectMessage(
    id = id,
    threadId = threadId,
    senderUserId = senderUserId,
    recipientUserId = recipientUserId,
    body = body,
    createdAt = Instant.now().toString(),
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
        serviceProviderMode = serviceProviderMode || fallbackProfile.serviceProviderMode,
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
    }

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
            val roster = photos.mapIndexed { index, photo ->
                PetRosterItem(
                    id = "groomer_${provider.id}_$index",
                    petName = "${provider.name.substringBefore(' ')} Pup ${index + 1}",
                    photoUrl = photo,
                    addedDate = today.minusDays((index % 7).toLong()),
                    suburb = provider.suburb,
                )
            }
                .filter { item -> isInRollingWindow(item.addedDate, today, days = 7) }
                .dailyShuffle(seed = "groomer_${provider.id}", today = today)
                .take(8)
            provider.id to roster
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
