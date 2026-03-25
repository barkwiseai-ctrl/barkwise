package com.petsocial.app.ui.screens

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.TurnedIn
import androidx.compose.material.icons.filled.TurnedInNot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.petsocial.app.data.CommunityComment
import com.petsocial.app.data.CommunityEvent
import com.petsocial.app.data.CommunityPost
import com.petsocial.app.data.CommunityPostCreate
import com.petsocial.app.data.Group
import com.petsocial.app.data.GroupInvite
import com.petsocial.app.ui.CommunityWeatherSnapshot
import com.petsocial.app.ui.MessageThread
import com.petsocial.app.ui.PetRosterItem
import com.petsocial.app.ui.calendar.communityEventToCalendarDraft
import com.petsocial.app.ui.calendar.openCalendarDraft
import com.petsocial.app.ui.components.PetRosterShowcase
import com.petsocial.app.ui.qr.QrPayloadAction
import com.petsocial.app.ui.qr.QrScannerSheet
import com.petsocial.app.ui.qr.generateQrImageBitmap
import com.petsocial.app.ui.qr.parseQrPayload
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val CommunitySpaceXs = 8.dp
private val CommunitySpaceSm = 10.dp
private val CommunitySpaceMd = 12.dp
private const val COMMUNITY_PRIVACY_PREFS = "community_privacy_prefs"
private const val SHARE_POINT_CONSENT_ACK_KEY = "share_point_consent_ack"
private const val BARKWISE_PRIVACY_POLICY_URL = "https://api.barkwiseai.com/web/privacy/"
private const val REPORT_REASON_CHILD_SAFETY = "Child safety concern"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    activeUserId: String,
    loading: Boolean,
    suburb: String,
    currentLocationSuburb: String?,
    currentLatitude: Double?,
    currentLongitude: Double?,
    postsSortBy: String,
    selectedGroupId: String?,
    groups: List<Group>,
    groupPetRosters: Map<String, List<PetRosterItem>>,
    latestGroupInvites: Map<String, GroupInvite>,
    blockedUserIds: List<String>,
    savedPostIds: Set<String>,
    savedEventIds: Set<String>,
    mutedKeywords: Set<String>,
    followedGroupIds: Set<String>,
    communityWeather: CommunityWeatherSnapshot,
    autoParkCheckInEnabled: Boolean,
    autoParkCheckInRequireCrowd: Boolean,
    autoParkCheckInQuorumCount: Int,
    autoParkCheckInQuorumThreshold: Int,
    autoParkCheckInQuorumWindowMinutes: Int,
    posts: List<CommunityPost>,
    postCommentsByPostId: Map<String, List<CommunityComment>>,
    loadingCommentPostIds: Set<String>,
    isCommunityModerator: Boolean,
    events: List<CommunityEvent>,
    messageThreads: List<MessageThread>,
    onOpenGroup: (String) -> Unit,
    onOpenMessages: (String?) -> Unit,
    onDismissSelectedGroup: () -> Unit,
    onJoinGroup: (String) -> Unit,
    onCreateGroupInvite: (String) -> Unit,
    onClearGroupInvite: (String) -> Unit,
    onCreateGroup: (String, String) -> Unit,
    onPostsSortChange: (String) -> Unit,
    onCreateGroupPost: (title: String, body: String, suburb: String) -> Unit,
    onCreateLostFound: (CommunityPostCreate) -> Unit,
    onCreateSharePoint: (CommunityPostCreate) -> Unit,
    onCreateEvent: (
        title: String,
        description: String,
        date: String,
        groupId: String?,
        locationName: String?,
        locationLatitude: Double?,
        locationLongitude: Double?,
        recurrence: String,
        recurrenceInterval: Int,
    ) -> Unit,
    onUpdateEvent: (
        eventId: String,
        title: String,
        description: String,
        date: String,
        groupId: String?,
        locationName: String?,
        locationLatitude: Double?,
        locationLongitude: Double?,
        clearLocation: Boolean,
        recurrence: String,
        recurrenceInterval: Int,
    ) -> Unit,
    onRsvpEvent: (eventId: String, attending: Boolean) -> Unit,
    onApproveJoinRequest: (groupId: String) -> Unit,
    onRejectJoinRequest: (groupId: String) -> Unit,
    onApproveEvent: (eventId: String) -> Unit,
    onLogCleanupCheckIn: (groupId: String) -> Unit,
    onResolveLostFound: (postId: String, status: String, note: String) -> Unit,
    onLoadPostComments: (postId: String, forceRefresh: Boolean) -> Unit,
    onCreatePostComment: (postId: String, body: String, parentCommentId: String?) -> Unit,
    onModeratePostComment: (commentId: String, action: String) -> Unit,
    onResolveInviteToken: (String) -> Unit,
    onTrackQrScanOutcome: (outcome: String, detail: String?) -> Unit,
    onReportPost: (postId: String, reason: String, details: String) -> Unit,
    onReportEvent: (eventId: String, reason: String, details: String) -> Unit,
    onBlockUser: (targetUserId: String) -> Unit,
    onDeletePost: (postId: String) -> Unit,
    onToggleSavePost: (postId: String) -> Unit,
    onToggleSaveEvent: (eventId: String) -> Unit,
    onSetMutedKeywords: (Set<String>) -> Unit,
    onToggleFollowGroup: (groupId: String) -> Unit,
    onRefreshWeather: () -> Unit,
    onSetAutoParkCheckInEnabled: (Boolean) -> Unit,
    onSetAutoParkCheckInRequireCrowd: (Boolean) -> Unit,
    onSimulateParkArrival: () -> Unit,
) {
    val context = LocalContext.current
    val sharePrivacyPrefs = remember(context) {
        context.getSharedPreferences(COMMUNITY_PRIVACY_PREFS, Context.MODE_PRIVATE)
    }
    var sharePointConsentAccepted by rememberSaveable {
        mutableStateOf(sharePrivacyPrefs.getBoolean(SHARE_POINT_CONSENT_ACK_KEY, false))
    }
    var showSharePointConsentDialog by rememberSaveable { mutableStateOf(false) }
    var pendingSharePointPayload by remember { mutableStateOf<CommunityPostCreate?>(null) }
    var showGroupDiscoverySheet by rememberSaveable { mutableStateOf(false) }
    var discoveryType by rememberSaveable { mutableStateOf(CommunityDiscoveryType.Groups.name) }
    var discoveryQuery by rememberSaveable { mutableStateOf("") }
    var discoverySuburb by rememberSaveable { mutableStateOf(suburb) }
    var showCreateGroupDialog by rememberSaveable { mutableStateOf(false) }
    var createGroupName by rememberSaveable { mutableStateOf("") }
    var createGroupSuburb by rememberSaveable(suburb) { mutableStateOf(suburb) }
    var showMeetupPlannerSheet by rememberSaveable { mutableStateOf(false) }
    var meetupPlannerMode by rememberSaveable { mutableStateOf(MeetupPlannerMode.CreateEvent.name) }
    var plannerTitle by rememberSaveable { mutableStateOf("") }
    var plannerDescription by rememberSaveable { mutableStateOf("") }
    var plannerDate by rememberSaveable { mutableStateOf("2026-02-28T10:00:00Z") }
    var plannerGroupId by rememberSaveable { mutableStateOf("") }
    var plannerPhotoUrls by rememberSaveable { mutableStateOf("") }
    var plannerPublic by rememberSaveable { mutableStateOf(true) }
    var plannerLocationEnabled by rememberSaveable { mutableStateOf(false) }
    var plannerLocationName by rememberSaveable { mutableStateOf("") }
    var plannerLocationLatitude by rememberSaveable { mutableStateOf("") }
    var plannerLocationLongitude by rememberSaveable { mutableStateOf("") }
    var plannerRecurrence by rememberSaveable { mutableStateOf("none") }
    var plannerRecurrenceInterval by rememberSaveable { mutableStateOf("1") }
    var showCreatePostDialog by rememberSaveable { mutableStateOf(false) }
    var createPostType by rememberSaveable { mutableStateOf("group_post") }
    var createPostTitle by rememberSaveable { mutableStateOf("") }
    var createPostBody by rememberSaveable { mutableStateOf("") }
    var createEventDate by rememberSaveable { mutableStateOf("2026-02-28T10:00:00Z") }
    var createEventGroupId by rememberSaveable { mutableStateOf("") }
    var createEventLocationEnabled by rememberSaveable { mutableStateOf(false) }
    var createEventLocationName by rememberSaveable { mutableStateOf("") }
    var createEventLocationLatitude by rememberSaveable { mutableStateOf("") }
    var createEventLocationLongitude by rememberSaveable { mutableStateOf("") }
    var createEventRecurrence by rememberSaveable { mutableStateOf("none") }
    var createEventRecurrenceInterval by rememberSaveable { mutableStateOf("1") }
    var createSharePointLocationName by rememberSaveable { mutableStateOf("") }
    var createSharePointLatitude by rememberSaveable { mutableStateOf("") }
    var createSharePointLongitude by rememberSaveable { mutableStateOf("") }
    var createSharePointMode by rememberSaveable { mutableStateOf("now") }
    var createSharePointAt by rememberSaveable { mutableStateOf("2026-03-06T09:00:00Z") }
    var createSharePointScope by rememberSaveable { mutableStateOf("friends") }
    var createSharePointPrecision by rememberSaveable { mutableStateOf("approximate") }
    var createLostFoundAlertType by rememberSaveable { mutableStateOf("lost") }
    var createLostFoundPetName by rememberSaveable { mutableStateOf("") }
    var createLostFoundPetTraits by rememberSaveable { mutableStateOf("") }
    var createLostFoundLastSeenLocation by rememberSaveable { mutableStateOf("") }
    var createLostFoundLastSeenAt by rememberSaveable { mutableStateOf("") }
    var createLostFoundContactPref by rememberSaveable { mutableStateOf("") }
    var createLostFoundPhotoUrls by rememberSaveable { mutableStateOf("") }
    var selectedPost by remember { mutableStateOf<CommunityPost?>(null) }
    var selectedLens by rememberSaveable(postsSortBy) {
        mutableStateOf(
            if (postsSortBy.equals("lost_found", ignoreCase = true)) CommunityLens.LostFound else CommunityLens.Posts,
        )
    }
    var showSavedOnly by rememberSaveable { mutableStateOf(false) }
    var selectedMeetupWindow by rememberSaveable { mutableStateOf(MeetupWindow.AllUpcoming) }
    var selectedMeetupArea by rememberSaveable { mutableStateOf(MeetupAreaFilter.Anywhere) }
    var suggestedJoinGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var suggestedJoinEventTitle by rememberSaveable { mutableStateOf("") }
    var mutedKeywordsInput by rememberSaveable { mutableStateOf(mutedKeywords.sorted().joinToString(", ")) }
    var showFeedSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var showInviteQrScanner by rememberSaveable { mutableStateOf(false) }
    var scannerStatusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedEvent by remember { mutableStateOf<CommunityEvent?>(null) }
    var editingEvent by remember { mutableStateOf<CommunityEvent?>(null) }
    var eventCommentInput by rememberSaveable { mutableStateOf("") }
    var sortDropdownExpanded by rememberSaveable { mutableStateOf(false) }
    var lastPlannerSubmitAt by rememberSaveable { mutableStateOf(0L) }
    var lastCreatePostSubmitAt by rememberSaveable { mutableStateOf(0L) }

    val listState = rememberLazyListState()

    LaunchedEffect(postsSortBy) {
        if (postsSortBy.equals("lost_found", ignoreCase = true)) {
            selectedLens = CommunityLens.LostFound
            onPostsSortChange("relevance")
        }
    }

    val joinedGroups = remember(groups) { groups.filter { group -> group.membershipStatus == "member" } }
    val joinedGroupIds = remember(joinedGroups) { joinedGroups.map { group -> group.id }.toSet() }
    val friendThreads = remember(messageThreads, blockedUserIds) {
        messageThreads.filterNot { thread -> thread.participantUserId in blockedUserIds }
    }
    val friendUserIds = remember(friendThreads) { friendThreads.map { it.participantUserId }.toSet() }
    val selectedDiscoveryType = remember(discoveryType) {
        CommunityDiscoveryType.entries.firstOrNull { it.name == discoveryType } ?: CommunityDiscoveryType.Groups
    }
    val eventById = remember(events) { events.associateBy { event -> event.id } }
    val groupById = remember(groups) { groups.associateBy { group -> group.id } }
    val featuredGroups = remember(groups, suburb) {
        filterGroupsForDiscovery(
            groups = groups,
            query = "",
            suburb = suburb,
        )
    }
    val discoveryGroups = remember(groups, discoveryQuery, discoverySuburb) {
        filterGroupsForDiscovery(
            groups = groups,
            query = discoveryQuery,
            suburb = discoverySuburb,
        )
    }
    val discoveryEvents = remember(events, discoveryQuery, discoverySuburb) {
        filterEventsForDiscovery(
            events = events,
            query = discoveryQuery,
            suburb = discoverySuburb,
        )
    }
    val discoveryFriends = remember(friendThreads, discoveryQuery) {
        filterFriendsForDiscovery(
            threads = friendThreads,
            query = discoveryQuery,
        )
    }
    val normalizedMutedKeywords = remember(mutedKeywords) {
        mutedKeywords
            .map { value -> value.trim().lowercase() }
            .filter { value -> value.length >= 2 }
            .toSet()
    }
    val blockedFilteredPosts = remember(posts, blockedUserIds) {
        if (blockedUserIds.isEmpty()) posts else posts.filterNot { post -> post.createdBy in blockedUserIds }
    }
    val keywordFilteredPosts = remember(blockedFilteredPosts, normalizedMutedKeywords) {
        if (normalizedMutedKeywords.isEmpty()) {
            blockedFilteredPosts
        } else {
            blockedFilteredPosts.filterNot { post ->
                val haystack = "${post.title} ${post.body}".lowercase()
                normalizedMutedKeywords.any { keyword -> keyword in haystack }
            }
        }
    }
    val visiblePosts = remember(keywordFilteredPosts, showSavedOnly, savedPostIds) {
        if (showSavedOnly) {
            keywordFilteredPosts.filter { post -> post.id in savedPostIds }
        } else {
            keywordFilteredPosts
        }
    }
    val feedItems = remember(selectedLens, visiblePosts) {
        buildCommunityFeed(
            lens = selectedLens,
            posts = visiblePosts,
        )
    }
    val groupNameById = remember(groups) { groups.associate { it.id to it.name } }
    val groupNameBySuburb = remember(groups) {
        groups
            .groupBy { group -> group.suburb.trim().lowercase() }
            .mapValues { entry -> entry.value.firstOrNull()?.name.orEmpty() }
    }
    val eventsByGroupId = remember(featuredGroups, events) {
        featuredGroups.associate { group ->
            group.id to events
                .asSequence()
                .filter { event ->
                    event.groupId == group.id ||
                        (event.groupId.isNullOrBlank() && event.suburb.equals(group.suburb, ignoreCase = true))
                }
                .sortedBy { event -> parseIsoInstant(event.date) ?: Instant.MAX }
                .take(8)
                .toList()
        }
    }
    val postsByGroupId = remember(featuredGroups, visiblePosts) {
        featuredGroups.associate { group ->
            group.id to visiblePosts
                .asSequence()
                .filter { post -> post.suburb.equals(group.suburb, ignoreCase = true) }
                .sortedByDescending { post -> parseIsoInstant(post.createdAt) ?: Instant.EPOCH }
                .take(12)
                .toList()
        }
    }
    val eventRelativeDayById = remember(events) {
        events.associate { event -> event.id to formatRelativeDay(event.date) }
    }
    val eventDateTimeById = remember(events) {
        events.associate { event -> event.id to formatIsoDateTime(event.date) }
    }
    val postCreatedAtLabelById = remember(visiblePosts) {
        visiblePosts.associate { post -> post.id to formatIsoDateTime(post.createdAt) }
    }
    val postCommentHintById = remember(visiblePosts, postCommentsByPostId) {
        visiblePosts.associate { post ->
            val loadedCount = postCommentsByPostId[post.id]?.count { comment -> comment.status == "active" } ?: 0
            post.id to if (loadedCount > 0) loadedCount else (6 + (((post.id.hashCode().toLong() and Long.MAX_VALUE) % 15L).toInt()))
        }
    }
    val sortedEvents = remember(events) { sortEventsForCommunity(events) }
    val approvedUpcomingEvents = remember(sortedEvents) {
        sortedEvents.filter { event -> event.status == "approved" && !isEventInPast(event.date) }
    }
    val focusedMeetupEvents = remember(
        approvedUpcomingEvents,
        selectedMeetupWindow,
        selectedMeetupArea,
        suburb,
        joinedGroupIds,
    ) {
        filterMeetupEvents(
            events = approvedUpcomingEvents,
            window = selectedMeetupWindow,
            area = selectedMeetupArea,
            selectedSuburb = suburb,
            joinedGroupIds = joinedGroupIds,
        )
    }
    val suggestedJoinGroup = remember(suggestedJoinGroupId, groupById) {
        suggestedJoinGroupId?.let { groupId -> groupById[groupId] }
    }
    val handleRsvpEvent: (String, Boolean) -> Unit = remember(
        onRsvpEvent,
        eventById,
        groupById,
    ) {
        { eventId, attending ->
            onRsvpEvent(eventId, attending)
            if (attending) {
                val event = eventById[eventId]
                val groupId = event?.groupId
                val group = groupId?.let { id -> groupById[id] }
                if (event != null && groupId != null && group != null && group.membershipStatus != "member") {
                    suggestedJoinGroupId = groupId
                    suggestedJoinEventTitle = event.title
                }
            }
        }
    }
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(CommunitySpaceSm),
        contentPadding = PaddingValues(bottom = 90.dp),
    ) {
        item {
            MeetupHeroCard(
                suburb = suburb,
                totalGroups = groups.size,
                totalEvents = events.size,
                totalDiscussions = posts.size,
                joinedGroups = joinedGroups.size,
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CommunitySpaceSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactCommunityActionButton(
                    enabled = !loading,
                    onClick = { showInviteQrScanner = true },
                    icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Invite QR") },
                )
                CompactCommunityActionButton(
                    enabled = !loading,
                    onClick = {
                        createGroupName = ""
                        createGroupSuburb = suburb
                        showCreateGroupDialog = true
                    },
                    icon = { Icon(Icons.Default.AddCircle, contentDescription = "Create group") },
                )
                CompactCommunityActionButton(
                    enabled = !loading,
                    onClick = { showGroupDiscoverySheet = true },
                    icon = { Icon(Icons.Default.Groups, contentDescription = "Find your groups") },
                )
                CompactCommunityActionButton(
                    enabled = !loading,
                    onClick = { showMeetupPlannerSheet = true },
                    icon = { Icon(Icons.Default.Event, contentDescription = "Meetup planner") },
                )
            }
        }

        scannerStatusMessage?.let { message ->
            item {
                Text(
                    message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (featuredGroups.isNotEmpty()) {
            item {
                Text("Groups", style = MaterialTheme.typography.titleSmall)
            }
            items(featuredGroups.take(8), key = { group -> "group_lane_${group.id}" }) { group ->
                GroupPriorityCard(
                    group = group,
                    loading = loading,
                    events = eventsByGroupId[group.id].orEmpty(),
                    posts = postsByGroupId[group.id].orEmpty(),
                    savedEventIds = savedEventIds,
                    onOpenGroup = onOpenGroup,
                    onJoinGroup = onJoinGroup,
                    onRsvpEvent = handleRsvpEvent,
                    onReportEvent = onReportEvent,
                    onToggleSaveEvent = onToggleSaveEvent,
                    onOpenMessages = onOpenMessages,
                    onOpenEventDetails = { event, presetComment ->
                        selectedEvent = event
                        eventCommentInput = presetComment.orEmpty()
                    },
                    onOpenPost = { post -> selectedPost = post },
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Feed", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(CommunitySpaceXs), verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            FilterChip(
                                selected = sortDropdownExpanded,
                                onClick = { sortDropdownExpanded = true },
                                label = { Text(communitySortLabel(postsSortBy)) },
                            )
                            androidx.compose.material3.DropdownMenu(
                                expanded = sortDropdownExpanded,
                                onDismissRequest = { sortDropdownExpanded = false },
                            ) {
                                listOf(
                                    "relevance" to "Relevant",
                                    "latest" to "Latest",
                                    "trending" to "Trending",
                                ).forEach { (value, label) ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            sortDropdownExpanded = false
                                            onPostsSortChange(value)
                                        },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { showFeedSettingsSheet = true }) {
                            Icon(Icons.Default.Tune, contentDescription = "Feed settings")
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(CommunitySpaceXs)) {
                    FilterChip(
                        selected = selectedLens == CommunityLens.Posts,
                        onClick = { selectedLens = CommunityLens.Posts },
                        label = { Text("Posts") },
                    )
                    FilterChip(
                        selected = selectedLens == CommunityLens.LostFound,
                        onClick = { selectedLens = CommunityLens.LostFound },
                        label = { Text("Lost & Found") },
                    )
                }
            }
        }

        if (loading && feedItems.isEmpty()) {
            items(3, key = { index -> "community_feed_skeleton_$index" }) {
                CommunityFeedSkeletonCard()
            }
        } else if (feedItems.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Text(
                        text = "No community activity yet.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            items(feedItems, key = { item -> item.stableId }) { item ->
                when (item) {
                    is CommunityFeedItem.PostItem -> {
                        val authorUserId = item.post.createdBy?.trim().orEmpty()
                        val isFriendActivity = authorUserId.isNotBlank() && authorUserId in friendUserIds
                        DiscussionFeedCard(
                            post = item.post,
                            createdAtLabel = postCreatedAtLabelById[item.post.id] ?: formatIsoDateTime(item.post.createdAt),
                            commentHint = postCommentHintById[item.post.id] ?: 8,
                            isFriendActivity = isFriendActivity,
                            groupName = selectedGroupId
                                ?.let { groupId -> groupById[groupId]?.name }
                                ?: groupNameBySuburb[item.post.suburb.trim().lowercase()],
                            authorLabel = item.post.createdBy,
                            onOpenPost = { selectedPost = item.post },
                            isSaved = item.post.id in savedPostIds,
                            onQuickReport = {
                                onReportPost(
                                    item.post.id,
                                    if (item.post.type == "lost_found") "Suspicious lost and found post" else "Harassment or abuse",
                                    "Reported from feed card",
                                )
                            },
                            onQuickBlock = {
                                item.post.createdBy
                                    ?.takeIf { userId -> userId.isNotBlank() }
                                    ?.let(onBlockUser)
                            },
                            onToggleSave = { onToggleSavePost(item.post.id) },
                            onMessageAuthor = item.post.createdBy
                                ?.takeIf { userId -> userId.isNotBlank() }
                                ?.let { authorId -> { onOpenMessages(authorId) } },
                        )
                    }
                }
            }
        }
    }

    if (showInviteQrScanner) {
        QrScannerSheet(
            onDetected = { rawValue ->
                showInviteQrScanner = false
                when (val action = parseQrPayload(rawValue)) {
                    is QrPayloadAction.InviteToken -> {
                        scannerStatusMessage = "Invite token detected"
                        onTrackQrScanOutcome(
                            "invite_token_detected",
                            "token_length:${action.token.length.coerceAtMost(128)}",
                        )
                        onResolveInviteToken(action.token)
                    }

                    is QrPayloadAction.OpenUrl -> {
                        scannerStatusMessage = "Opening install link"
                        val urlHost = qrUrlHostLabel(action.url)
                        val urlOutcome = if (action.url.contains("play.google.com/apps/testing", ignoreCase = true)) {
                            "open_install_url"
                        } else {
                            "open_url"
                        }
                        onTrackQrScanOutcome(urlOutcome, urlHost)
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(action.url),
                                ),
                            )
                        }.onFailure {
                            onTrackQrScanOutcome("open_url_failed", urlHost)
                            scannerStatusMessage = "Unable to open URL"
                        }
                    }

                    is QrPayloadAction.FriendConnection -> {
                        onTrackQrScanOutcome("friend_qr_detected", "use_social_sheet")
                        scannerStatusMessage = "Friend QR detected. Open Home > Social."
                    }

                    is QrPayloadAction.FriendToken -> {
                        onTrackQrScanOutcome("friend_qr_token_detected", "use_social_sheet")
                        scannerStatusMessage = "Friend QR detected. Open Home > Social."
                    }

                    QrPayloadAction.Invalid -> {
                        onTrackQrScanOutcome("invalid_payload", null)
                        scannerStatusMessage = "QR payload not recognized"
                    }
                }
            },
            onDismiss = { showInviteQrScanner = false },
        )
    }

    if (showFeedSettingsSheet) {
        ModalBottomSheet(onDismissRequest = { showFeedSettingsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(CommunitySpaceSm),
            ) {
                Text("Feed settings", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tweak saved view and muted keywords without taking space on the main community feed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = showSavedOnly,
                        onClick = { showSavedOnly = !showSavedOnly },
                        label = { Text(if (showSavedOnly) "Saved only" else "All posts") },
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text("Saved ${savedPostIds.size}") },
                    )
                    if (mutedKeywords.isNotEmpty()) {
                        AssistChip(onClick = {}, label = { Text("Muted words ${mutedKeywords.size}") })
                    }
                }
                OutlinedTextField(
                    value = mutedKeywordsInput,
                    onValueChange = { mutedKeywordsInput = it },
                    label = { Text("Mute keywords (comma-separated)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1,
                    maxLines = 2,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        enabled = !loading,
                        onClick = {
                            val parsed = mutedKeywordsInput
                                .split(",", "\n")
                                .map { value -> value.trim().lowercase() }
                                .filter { value -> value.length >= 2 }
                                .toSet()
                            onSetMutedKeywords(parsed)
                            mutedKeywordsInput = parsed.sorted().joinToString(", ")
                            showFeedSettingsSheet = false
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Apply")
                    }
                    OutlinedButton(
                        enabled = !loading,
                        onClick = {
                            mutedKeywordsInput = ""
                            onSetMutedKeywords(emptySet())
                            showFeedSettingsSheet = false
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Clear mute")
                    }
                }
            }
        }
    }

    if (showCreateGroupDialog) {
        val normalizedName = createGroupName.trim()
        val normalizedSuburb = createGroupSuburb.trim()
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            title = { Text("Create group") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = createGroupName,
                        onValueChange = { createGroupName = it.take(64) },
                        label = { Text("Group name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = createGroupSuburb,
                        onValueChange = { createGroupSuburb = it.take(48) },
                        label = { Text("Suburb") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Text(
                        "Tip: This creates a local group immediately so members can join and post.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !loading && normalizedName.length >= 4 && normalizedSuburb.isNotBlank(),
                    onClick = {
                        onCreateGroup(normalizedName, normalizedSuburb)
                        showCreateGroupDialog = false
                        createGroupName = ""
                    },
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showGroupDiscoverySheet) {
        ModalBottomSheet(onDismissRequest = { showGroupDiscoverySheet = false }) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                item {
                    Text("Find your groups", style = MaterialTheme.typography.titleLarge)
                }
                item {
                    Text(
                        "Search groups, events, or friends and jump straight into local activity.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    OutlinedTextField(
                        value = discoveryQuery,
                        onValueChange = { discoveryQuery = it },
                        label = { Text("Search") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = discoverySuburb,
                        onValueChange = { discoverySuburb = it },
                        label = { Text("Suburb") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CommunityDiscoveryType.entries.toList(), key = { type -> type.name }) { type ->
                            FilterChip(
                                selected = selectedDiscoveryType == type,
                                onClick = { discoveryType = type.name },
                                label = { Text(type.label) },
                            )
                        }
                    }
                }

                when (selectedDiscoveryType) {
                    CommunityDiscoveryType.Groups -> {
                        item {
                            Text(
                                text = when (discoveryGroups.size) {
                                    0 -> "No groups found"
                                    1 -> "1 group found"
                                    else -> "${discoveryGroups.size} groups found"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (discoveryGroups.isEmpty()) {
                            item {
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                    Text(
                                        "Try another suburb or open Events/Friends to explore community activity.",
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        } else {
                            items(discoveryGroups.take(20), key = { group -> "discover_group_${group.id}" }) { group ->
                                GroupSnapshotCard(
                                    group = group,
                                    loading = loading,
                                    onOpenGroup = onOpenGroup,
                                    onJoinGroup = onJoinGroup,
                                )
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedTextField(
                                    value = createGroupName,
                                    onValueChange = { createGroupName = it },
                                    label = { Text("Start a new group") },
                                    modifier = Modifier.weight(1f),
                                )
                                Button(
                                    enabled = !loading && createGroupName.trim().length >= 4,
                                    onClick = {
                                        val cleanName = createGroupName.trim()
                                        if (cleanName.length >= 4) {
                                            val cleanSuburb = discoverySuburb.trim().ifBlank { suburb }
                                            onCreateGroup(cleanName, cleanSuburb)
                                            createGroupName = ""
                                        }
                                    },
                                ) {
                                    Text("Create")
                                }
                            }
                        }
                    }

                    CommunityDiscoveryType.Events -> {
                        if (discoveryEvents.isEmpty()) {
                            item {
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                    Text(
                                        "No events match yet. Try a different search or suburb.",
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        } else {
                            items(discoveryEvents.take(20), key = { event -> "discover_event_${event.id}" }) { event ->
                                EventDiscoveryCard(
                                    event = event,
                                    groupName = event.groupId?.let { groupId -> groupNameById[groupId] },
                                    loading = loading,
                                    onOpenGroup = onOpenGroup,
                                    onRsvpEvent = handleRsvpEvent,
                                )
                            }
                        }
                    }

                    CommunityDiscoveryType.Friends -> {
                        if (discoveryFriends.isEmpty()) {
                            item {
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                    Text(
                                        "No friends found. Try another name or open Messages.",
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        } else {
                            items(discoveryFriends.take(20), key = { thread -> "discover_friend_${thread.id}" }) { thread ->
                                FriendDiscoveryCard(
                                    thread = thread,
                                    loading = loading,
                                    onOpenMessages = { onOpenMessages(thread.participantUserId) },
                                )
                            }
                        }
                        item {
                            OutlinedButton(
                                enabled = !loading,
                                onClick = { onOpenMessages(null) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Open all messages")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMeetupPlannerSheet) {
        val plannerMode = remember(meetupPlannerMode) {
            MeetupPlannerMode.entries.firstOrNull { it.name == meetupPlannerMode } ?: MeetupPlannerMode.CreateEvent
        }
        val plannerDateOptions = remember { eventDatePresets() }
        val parsedPlannerDate = parseIsoInstant(plannerDate.trim())
        val plannerDateValid = plannerDate.isBlank() || parsedPlannerDate != null
        val plannerLat = parseCoordinateOrNull(plannerLocationLatitude)
        val plannerLng = parseCoordinateOrNull(plannerLocationLongitude)
        val plannerLocationValid = !plannerLocationEnabled || (plannerLat != null && plannerLng != null)
        val plannerRecurrenceIntervalInt = plannerRecurrenceInterval.trim().toIntOrNull()
        val plannerRecurrenceValid = plannerRecurrence == "none" ||
            (plannerRecurrenceIntervalInt != null && plannerRecurrenceIntervalInt in 1..30)
        val canSubmitPlanner = when (plannerMode) {
            MeetupPlannerMode.PingGroup -> plannerTitle.trim().isNotBlank() && plannerDescription.trim().isNotBlank() && plannerDateValid
            MeetupPlannerMode.CreateEvent, MeetupPlannerMode.ComplexEvent -> {
                plannerTitle.trim().isNotBlank() &&
                    plannerDescription.trim().isNotBlank() &&
                    plannerDate.trim().isNotBlank() &&
                    parsedPlannerDate != null &&
                    plannerLocationValid &&
                    plannerRecurrenceValid
            }
        }
        ModalBottomSheet(onDismissRequest = { showMeetupPlannerSheet = false }) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                item {
                    Text("Meetup planner", style = MaterialTheme.typography.titleLarge)
                }
                item {
                    Text(
                        "Create meetups, ping your group, or plan larger public events.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(MeetupPlannerMode.entries.toList(), key = { mode -> mode.name }) { mode ->
                            FilterChip(
                                selected = plannerMode == mode,
                                onClick = { meetupPlannerMode = mode.name },
                                label = { Text(mode.label) },
                            )
                        }
                    }
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(MeetupWindow.entries.toList(), key = { window -> "planner_window_${window.name}" }) { window ->
                            FilterChip(
                                selected = selectedMeetupWindow == window,
                                onClick = { selectedMeetupWindow = window },
                                label = { Text(window.label) },
                            )
                        }
                    }
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(MeetupAreaFilter.entries.toList(), key = { area -> "planner_area_${area.name}" }) { area ->
                            FilterChip(
                                selected = selectedMeetupArea == area,
                                onClick = { selectedMeetupArea = area },
                                label = { Text(area.label) },
                            )
                        }
                    }
                }
                item {
                    if (focusedMeetupEvents.isEmpty()) {
                        Text(
                            "No upcoming meetups in this window yet.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val nextEvent = focusedMeetupEvents.first()
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text("Next meetup: ${nextEvent.title}", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${formatRelativeDay(nextEvent.date)} • ${formatIsoDateTime(nextEvent.date)} • ${nextEvent.attendeeCount} going",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (nextEvent.rsvpStatus != "attending") {
                                    OutlinedButton(
                                        enabled = !loading,
                                        onClick = { handleRsvpEvent(nextEvent.id, true) },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("RSVP")
                                    }
                                } else {
                                    AssistChip(onClick = {}, label = { Text("You are going") })
                                }
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = plannerTitle,
                        onValueChange = { plannerTitle = it },
                        label = {
                            Text(
                                when (plannerMode) {
                                    MeetupPlannerMode.PingGroup -> "Ping title"
                                    MeetupPlannerMode.CreateEvent -> "Event title"
                                    MeetupPlannerMode.ComplexEvent -> "Complex event title"
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = plannerDescription,
                        onValueChange = { plannerDescription = it },
                        label = {
                            Text(
                                when (plannerMode) {
                                    MeetupPlannerMode.PingGroup -> "Where are you going?"
                                    MeetupPlannerMode.CreateEvent -> "Event description"
                                    MeetupPlannerMode.ComplexEvent -> "Plan details"
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 5,
                    )
                }
                item {
                    val plannerDatePickerLabel = plannerDate.trim()
                        .takeIf { it.isNotBlank() }
                        ?.let(::formatIsoDateTime)
                        ?: "Tap to choose a date"
                    OutlinedTextField(
                        value = plannerDatePickerLabel,
                        onValueChange = {},
                        label = {
                            Text(
                                when (plannerMode) {
                                    MeetupPlannerMode.PingGroup -> "Date"
                                    else -> "Event day"
                                }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showIsoDatePicker(
                                    context = context,
                                    currentIsoValue = plannerDate.trim(),
                                    fallbackHour = if (plannerMode == MeetupPlannerMode.PingGroup) 16 else 10,
                                ) { selectedIso ->
                                    plannerDate = selectedIso
                                }
                            },
                        readOnly = true,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    showIsoDatePicker(
                                        context = context,
                                        currentIsoValue = plannerDate.trim(),
                                        fallbackHour = if (plannerMode == MeetupPlannerMode.PingGroup) 16 else 10,
                                    ) { selectedIso ->
                                        plannerDate = selectedIso
                                    }
                                },
                            ) {
                                Icon(Icons.Default.Event, contentDescription = "Choose event date")
                            }
                        },
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(plannerDateOptions, key = { option -> "planner_date_${option.label}" }) { option ->
                            FilterChip(
                                selected = plannerDate.trim() == option.value,
                                onClick = { plannerDate = option.value },
                                label = { Text(option.label) },
                            )
                        }
                    }
                }
                if (!plannerDateValid) {
                    item {
                        Text(
                            "Use a valid ISO datetime with timezone, e.g. 2026-02-28T10:00:00Z",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = plannerPhotoUrls,
                        onValueChange = { plannerPhotoUrls = it },
                        label = { Text("Photo URLs (comma-separated)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 1,
                        maxLines = 2,
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = plannerPublic,
                            onClick = {
                                plannerPublic = !plannerPublic
                                if (plannerPublic) plannerGroupId = ""
                            },
                            label = { Text(if (plannerPublic) "Public event" else "Group tagged") },
                        )
                    }
                }
                if (!plannerPublic && joinedGroups.isNotEmpty()) {
                    item {
                        Text(
                            "Tag one of your groups:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(joinedGroups, key = { group -> "planner_group_${group.id}" }) { group ->
                                FilterChip(
                                    selected = plannerGroupId == group.id,
                                    onClick = {
                                        plannerGroupId = if (plannerGroupId == group.id) "" else group.id
                                    },
                                    label = { Text(group.name) },
                                )
                            }
                        }
                    }
                }
                if (plannerMode != MeetupPlannerMode.PingGroup) {
                    item {
                        EventLocationAndRecurrenceFields(
                            locationEnabled = plannerLocationEnabled,
                            onLocationEnabledChange = { plannerLocationEnabled = it },
                            locationName = plannerLocationName,
                            onLocationNameChange = { plannerLocationName = it },
                            locationLatitude = plannerLocationLatitude,
                            onLocationLatitudeChange = { plannerLocationLatitude = it },
                            locationLongitude = plannerLocationLongitude,
                            onLocationLongitudeChange = { plannerLocationLongitude = it },
                            currentLocationSuburb = currentLocationSuburb,
                            currentLatitude = currentLatitude,
                            currentLongitude = currentLongitude,
                            onUseCurrentLocation = {
                                plannerLocationEnabled = true
                                plannerLocationName = currentLocationSuburb.orEmpty()
                                plannerLocationLatitude = currentLatitude?.let { value ->
                                    String.format(Locale.US, "%.6f", value)
                                }.orEmpty()
                                plannerLocationLongitude = currentLongitude?.let { value ->
                                    String.format(Locale.US, "%.6f", value)
                                }.orEmpty()
                            },
                            recurrence = plannerRecurrence,
                            onRecurrenceChange = { plannerRecurrence = it },
                            recurrenceInterval = plannerRecurrenceInterval,
                            onRecurrenceIntervalChange = { plannerRecurrenceInterval = it },
                        )
                    }
                    if (!plannerLocationValid) {
                        item {
                            Text(
                                "Pick an event location on the map.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    if (!plannerRecurrenceValid) {
                        item {
                            Text(
                                "Recurring events need an interval between 1 and 30.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                item {
                Row(horizontalArrangement = Arrangement.spacedBy(CommunitySpaceXs), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        enabled = !loading && canSubmitPlanner,
                        onClick = {
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastPlannerSubmitAt < 900L) return@Button
                            lastPlannerSubmitAt = now
                            val title = plannerTitle.trim()
                            val description = plannerDescription.trim()
                                val normalizedPhotoUrls = plannerPhotoUrls
                                    .split(",")
                                    .map { value -> value.trim() }
                                    .filter { value -> value.isNotBlank() }
                                val photoDetails = if (normalizedPhotoUrls.isEmpty()) {
                                    ""
                                } else {
                                    "\n\nPhotos: ${normalizedPhotoUrls.joinToString(", ")}"
                                }
                                val taggedGroupName = plannerGroupId
                                    .trim()
                                    .takeIf { value -> value.isNotBlank() }
                                    ?.let { groupId -> groupNameById[groupId] ?: groupId }
                                when (plannerMode) {
                                    MeetupPlannerMode.PingGroup -> {
                                        val pingBody = buildString {
                                            append(description)
                                            if (plannerDate.trim().isNotBlank() && parsedPlannerDate != null) {
                                                append("\nTime: ${formatIsoDateTime(plannerDate.trim())}")
                                            }
                                            taggedGroupName?.let { append("\nGroup: $it") }
                                            append(photoDetails)
                                        }
                                        onCreateGroupPost(title, pingBody, suburb)
                                    }

                                    MeetupPlannerMode.CreateEvent, MeetupPlannerMode.ComplexEvent -> {
                                        val eventDescription = buildString {
                                            append(description)
                                            taggedGroupName?.let { append("\n\nTagged group: $it") }
                                            if (plannerMode == MeetupPlannerMode.ComplexEvent) {
                                                append("\n\nPlanning mode: complex event")
                                            }
                                            append(photoDetails)
                                        }
                                        onCreateEvent(
                                            title,
                                            eventDescription,
                                            plannerDate.trim(),
                                            if (plannerPublic) null else plannerGroupId.trim().ifBlank { null },
                                            plannerLocationName.trim().ifBlank { null },
                                            if (plannerLocationEnabled) plannerLat else null,
                                            if (plannerLocationEnabled) plannerLng else null,
                                            plannerRecurrence,
                                            if (plannerRecurrence == "none") 1 else (plannerRecurrenceIntervalInt ?: 1),
                                        )
                                    }
                                }
                                plannerTitle = ""
                                plannerDescription = ""
                                plannerDate = "2026-02-28T10:00:00Z"
                                plannerGroupId = ""
                                plannerPhotoUrls = ""
                                plannerPublic = true
                                plannerLocationEnabled = false
                                plannerLocationName = ""
                                plannerLocationLatitude = ""
                                plannerLocationLongitude = ""
                                plannerRecurrence = "none"
                                plannerRecurrenceInterval = "1"
                                showMeetupPlannerSheet = false
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                when (plannerMode) {
                                    MeetupPlannerMode.PingGroup -> "Send ping"
                                    MeetupPlannerMode.CreateEvent -> "Create event"
                                    MeetupPlannerMode.ComplexEvent -> "Plan event"
                                }
                            )
                        }
                        OutlinedButton(
                            enabled = !loading,
                            onClick = { showMeetupPlannerSheet = false },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }

    selectedPost?.let { post ->
        val loadedComments = postCommentsByPostId[post.id].orEmpty()
        val commentsLoading = post.id in loadingCommentPostIds
        LaunchedEffect(post.id) {
            onLoadPostComments(post.id, false)
        }
        PostDetailSheet(
            post = post,
            activeUserId = activeUserId,
            comments = loadedComments,
            commentsLoading = commentsLoading,
            loading = loading,
            canModerateComments = isCommunityModerator,
            onRefreshComments = { onLoadPostComments(post.id, true) },
            onCreateComment = { body, parentCommentId ->
                onCreatePostComment(post.id, body, parentCommentId)
            },
            onModerateComment = onModeratePostComment,
            isSaved = post.id in savedPostIds,
            onReportPost = { postId, reason, details -> onReportPost(postId, reason, details) },
            onBlockUser = onBlockUser,
            onDeletePost = onDeletePost,
            onToggleSavePost = onToggleSavePost,
            onResolveLostFound = { postId, status, note ->
                onResolveLostFound(postId, status, note)
                selectedPost = null
            },
            onDismiss = { selectedPost = null },
        )
    }

    selectedEvent?.let { event ->
        EventDetailSheet(
            event = event,
            activeUserId = activeUserId,
            loading = loading,
            initialComment = eventCommentInput,
            isSaved = event.id in savedEventIds,
            onDismiss = {
                selectedEvent = null
                eventCommentInput = ""
            },
            onRsvpEvent = { eventId, attending -> onRsvpEvent(eventId, attending) },
            onReportEvent = { eventId ->
                onReportEvent(eventId, "Safety concern", "Reported from event details")
            },
            onToggleSaveEvent = onToggleSaveEvent,
            onMessageOrganizer = {
                if (event.createdBy.isNotBlank()) {
                    onOpenMessages(event.createdBy)
                }
            },
            onOpenCalendar = { selected ->
                communityEventToCalendarDraft(selected)?.let { draft ->
                    context.openCalendarDraft(draft)
                }
            },
            onEditEvent = { editable ->
                editingEvent = editable
            },
        )
    }

    editingEvent?.let { event ->
        EventEditorDialog(
            event = event,
            loading = loading,
            currentLocationSuburb = currentLocationSuburb,
            currentLatitude = currentLatitude,
            currentLongitude = currentLongitude,
            onDismiss = { editingEvent = null },
            onSubmit = {
                eventId,
                title,
                description,
                date,
                groupId,
                locationName,
                locationLatitude,
                locationLongitude,
                clearLocation,
                recurrence,
                recurrenceInterval,
                ->
                onUpdateEvent(
                    eventId,
                    title,
                    description,
                    date,
                    groupId,
                    locationName,
                    locationLatitude,
                    locationLongitude,
                    clearLocation,
                    recurrence,
                    recurrenceInterval,
                )
                editingEvent = null
                selectedEvent = null
            },
        )
    }

    groups.firstOrNull { group -> group.id == selectedGroupId }?.let { group ->
        GroupDetailSheet(
            loading = loading,
            group = group,
            roster = groupPetRosters[group.id].orEmpty(),
            events = events
                .filter { event ->
                    event.groupId == group.id || event.suburb.equals(group.suburb, ignoreCase = true)
                },
            posts = posts
                .filter { post -> post.suburb.equals(group.suburb, ignoreCase = true) },
            onRsvpEvent = handleRsvpEvent,
            savedEventIds = savedEventIds,
            onReportEvent = onReportEvent,
            onToggleSaveEvent = onToggleSaveEvent,
            onOpenMessages = onOpenMessages,
            onOpenEventDetails = { event, presetComment ->
                selectedEvent = event
                eventCommentInput = presetComment.orEmpty()
            },
            onOpenPost = { post -> selectedPost = post },
            onDismiss = onDismissSelectedGroup,
        )
    }

    suggestedJoinGroup?.let { group ->
        AlertDialog(
            onDismissRequest = {
                suggestedJoinGroupId = null
                suggestedJoinEventTitle = ""
            },
            title = { Text("Join the host group?") },
            text = {
                Text(
                    "You RSVP'd to \"$suggestedJoinEventTitle\". Join ${group.name} to get meetup updates and group chat context.",
                )
            },
            confirmButton = {
                Button(
                    enabled = !loading,
                    onClick = {
                        onJoinGroup(group.id)
                        suggestedJoinGroupId = null
                        suggestedJoinEventTitle = ""
                    },
                ) {
                    Text("Join group")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        suggestedJoinGroupId = null
                        suggestedJoinEventTitle = ""
                    },
                ) {
                    Text("Not now")
                }
            },
        )
    }

    if (showCreatePostDialog) {
        val eventDateOptions = remember { eventDatePresets() }
        val createEventLat = parseCoordinateOrNull(createEventLocationLatitude)
        val createEventLng = parseCoordinateOrNull(createEventLocationLongitude)
        val createEventLocationValid = !createEventLocationEnabled || (createEventLat != null && createEventLng != null)
        val createEventRecurrenceIntervalInt = createEventRecurrenceInterval.trim().toIntOrNull()
        val createEventRecurrenceValid = createEventRecurrence == "none" ||
            (createEventRecurrenceIntervalInt != null && createEventRecurrenceIntervalInt in 1..30)
        val createSharePointLat = parseCoordinateOrNull(createSharePointLatitude)
        val createSharePointLng = parseCoordinateOrNull(createSharePointLongitude)
        val createSharePointLocationValid = createSharePointLat != null && createSharePointLng != null
        val parsedSharePointAt = parseIsoInstant(createSharePointAt.trim())
        val createSharePointTimeValid = createSharePointMode == "now" || parsedSharePointAt != null
        val createSharePointWindowValid = createSharePointMode == "now" ||
            (parsedSharePointAt != null && !parsedSharePointAt.isAfter(Instant.now().plus(24, ChronoUnit.HOURS)))
        fun resetCreatePostInputs() {
            createPostTitle = ""
            createPostBody = ""
            createEventDate = "2026-02-28T10:00:00Z"
            createEventGroupId = ""
            createEventLocationEnabled = false
            createEventLocationName = ""
            createEventLocationLatitude = ""
            createEventLocationLongitude = ""
            createEventRecurrence = "none"
            createEventRecurrenceInterval = "1"
            createSharePointLocationName = ""
            createSharePointLatitude = ""
            createSharePointLongitude = ""
            createSharePointMode = "now"
            createSharePointAt = "2026-03-06T09:00:00Z"
            createSharePointScope = "friends"
            createSharePointPrecision = "approximate"
            createLostFoundAlertType = "lost"
            createLostFoundPetName = ""
            createLostFoundPetTraits = ""
            createLostFoundLastSeenLocation = ""
            createLostFoundLastSeenAt = ""
            createLostFoundContactPref = ""
            createLostFoundPhotoUrls = ""
            showCreatePostDialog = false
        }
        AlertDialog(
            onDismissRequest = { showCreatePostDialog = false },
            title = { Text("Create post") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = createPostType == "group_post",
                                onClick = { createPostType = "group_post" },
                                label = { Text("Discussion") },
                            )
                        }
                        item {
                            FilterChip(
                                selected = createPostType == "lost_found",
                                onClick = { createPostType = "lost_found" },
                                label = { Text("Lost/Found") },
                            )
                        }
                        item {
                            FilterChip(
                                selected = createPostType == "community_event",
                                onClick = { createPostType = "community_event" },
                                label = { Text("Event") },
                            )
                        }
                        item {
                            FilterChip(
                                selected = createPostType == "share_point",
                                onClick = { createPostType = "share_point" },
                                label = { Text("Share point") },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = createPostTitle,
                        onValueChange = { createPostTitle = it },
                        label = {
                            Text(
                                when (createPostType) {
                                    "lost_found" -> "Lost & Found title"
                                    "community_event" -> "Event title"
                                    "share_point" -> "Share title"
                                    else -> "Post title"
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = createPostBody,
                        onValueChange = { createPostBody = it },
                        label = {
                            Text(
                                when (createPostType) {
                                    "community_event" -> "Event description"
                                    "lost_found" -> "Additional details"
                                    "share_point" -> "Note for friends"
                                    else -> "Details"
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                    )
                    if (createPostType == "lost_found") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = createLostFoundAlertType == "lost",
                                onClick = { createLostFoundAlertType = "lost" },
                                label = { Text("Lost") },
                            )
                            FilterChip(
                                selected = createLostFoundAlertType == "found",
                                onClick = { createLostFoundAlertType = "found" },
                                label = { Text("Found") },
                            )
                        }
                        OutlinedTextField(
                            value = createLostFoundPetName,
                            onValueChange = { createLostFoundPetName = it },
                            label = { Text("Pet name (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = createLostFoundPetTraits,
                            onValueChange = { createLostFoundPetTraits = it },
                            label = { Text("Pet traits (required)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = createLostFoundLastSeenLocation,
                            onValueChange = { createLostFoundLastSeenLocation = it },
                            label = { Text("Last seen location (required)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = createLostFoundLastSeenAt,
                            onValueChange = { createLostFoundLastSeenAt = it },
                            label = { Text("Last seen time (optional ISO, e.g. 2026-02-21T18:30:00Z)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = createLostFoundContactPref,
                            onValueChange = { createLostFoundContactPref = it },
                            label = { Text("Contact preference (required)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = createLostFoundPhotoUrls,
                            onValueChange = { createLostFoundPhotoUrls = it },
                            label = { Text("Photo URLs (comma-separated, optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 1,
                            maxLines = 2,
                        )
                    }
                    if (createPostType == "community_event") {
                        val parsedEventInstant = parseIsoInstant(createEventDate.trim())
                        val createEventDateLabel = createEventDate.trim()
                            .takeIf { it.isNotBlank() }
                            ?.let(::formatIsoDateTime)
                            ?: "Tap to choose a date"
                        OutlinedTextField(
                            value = createEventDateLabel,
                            onValueChange = {},
                            label = { Text("Event day") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showIsoDatePicker(
                                        context = context,
                                        currentIsoValue = createEventDate.trim(),
                                        fallbackHour = 10,
                                    ) { selectedIso ->
                                        createEventDate = selectedIso
                                    }
                                },
                            readOnly = true,
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        showIsoDatePicker(
                                            context = context,
                                            currentIsoValue = createEventDate.trim(),
                                            fallbackHour = 10,
                                        ) { selectedIso ->
                                            createEventDate = selectedIso
                                        }
                                    },
                                ) {
                                    Icon(Icons.Default.Event, contentDescription = "Choose event date")
                                }
                            },
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(eventDateOptions, key = { option -> "event_date_${option.label}" }) { option ->
                                FilterChip(
                                    selected = createEventDate.trim() == option.value,
                                    onClick = { createEventDate = option.value },
                                    label = { Text(option.label) },
                                )
                            }
                        }
                        if (parsedEventInstant == null) {
                            Text(
                                "Use a valid ISO datetime with timezone, e.g. 2026-02-28T10:00:00Z",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            Text(
                                "Scheduled ${formatRelativeDay(createEventDate)} • ${formatIsoDateTime(createEventDate)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (joinedGroups.isNotEmpty()) {
                            Text(
                                "Post as neighborhood event, or attach to a joined group:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    FilterChip(
                                        selected = createEventGroupId.isBlank(),
                                        onClick = { createEventGroupId = "" },
                                        label = { Text("No group") },
                                    )
                                }
                                items(joinedGroups, key = { group -> "event_group_${group.id}" }) { group ->
                                    FilterChip(
                                        selected = createEventGroupId == group.id,
                                        onClick = {
                                            createEventGroupId = if (createEventGroupId == group.id) "" else group.id
                                        },
                                        label = { Text(group.name) },
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = createEventGroupId,
                            onValueChange = { createEventGroupId = it },
                            label = { Text("Group ID (optional, advanced)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        EventLocationAndRecurrenceFields(
                            locationEnabled = createEventLocationEnabled,
                            onLocationEnabledChange = { createEventLocationEnabled = it },
                            locationName = createEventLocationName,
                            onLocationNameChange = { createEventLocationName = it },
                            locationLatitude = createEventLocationLatitude,
                            onLocationLatitudeChange = { createEventLocationLatitude = it },
                            locationLongitude = createEventLocationLongitude,
                            onLocationLongitudeChange = { createEventLocationLongitude = it },
                            currentLocationSuburb = currentLocationSuburb,
                            currentLatitude = currentLatitude,
                            currentLongitude = currentLongitude,
                            onUseCurrentLocation = {
                                createEventLocationEnabled = true
                                createEventLocationName = currentLocationSuburb.orEmpty()
                                createEventLocationLatitude = currentLatitude?.let { value ->
                                    String.format(Locale.US, "%.6f", value)
                                }.orEmpty()
                                createEventLocationLongitude = currentLongitude?.let { value ->
                                    String.format(Locale.US, "%.6f", value)
                                }.orEmpty()
                            },
                            recurrence = createEventRecurrence,
                            onRecurrenceChange = { createEventRecurrence = it },
                            recurrenceInterval = createEventRecurrenceInterval,
                            onRecurrenceIntervalChange = { createEventRecurrenceInterval = it },
                        )
                        if (!createEventLocationValid) {
                            Text(
                                "Pick an event location on the map.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (!createEventRecurrenceValid) {
                            Text(
                                "Recurring events need an interval between 1 and 30.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    if (createPostType == "share_point") {
                        Text(
                            "Share a map pin safely. \"Now\" auto-expires in 1 hour.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Location permission is used only to place your selected map pin.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = createSharePointMode == "now",
                                onClick = { createSharePointMode = "now" },
                                label = { Text("Now (1h)") },
                            )
                            FilterChip(
                                selected = createSharePointMode == "at_time",
                                onClick = { createSharePointMode = "at_time" },
                                label = { Text("At x time") },
                            )
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = createSharePointScope == "friends",
                                    onClick = { createSharePointScope = "friends" },
                                    label = { Text("Friends only (Recommended)") },
                                )
                            }
                            item {
                                FilterChip(
                                    selected = createSharePointScope == "community",
                                    onClick = { createSharePointScope = "community" },
                                    label = { Text("Community") },
                                )
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = createSharePointPrecision == "approximate",
                                    onClick = { createSharePointPrecision = "approximate" },
                                    label = { Text("Approximate (Recommended)") },
                                )
                            }
                            item {
                                FilterChip(
                                    selected = createSharePointPrecision == "exact",
                                    onClick = { createSharePointPrecision = "exact" },
                                    label = { Text("Exact") },
                                )
                            }
                        }
                        Text(
                            text = if (createSharePointScope == "friends") {
                                "Who can see this: Friends only"
                            } else {
                                "Who can see this: Community members nearby"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(BARKWISE_PRIVACY_POLICY_URL)),
                                )
                            },
                        ) {
                            Text("Open privacy policy")
                        }
                        OutlinedButton(
                            enabled = currentLatitude != null && currentLongitude != null,
                            onClick = {
                                createSharePointLocationName = currentLocationSuburb.orEmpty()
                                createSharePointLatitude = currentLatitude?.let { value ->
                                    String.format(Locale.US, "%.6f", value)
                                }.orEmpty()
                                createSharePointLongitude = currentLongitude?.let { value ->
                                    String.format(Locale.US, "%.6f", value)
                                }.orEmpty()
                            },
                        ) {
                            val label = currentLocationSuburb?.takeIf { it.isNotBlank() } ?: "current location"
                            Text("Use $label")
                        }
                        OutlinedTextField(
                            value = createSharePointLocationName,
                            onValueChange = { createSharePointLocationName = it },
                            label = { Text("Location label (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = createSharePointLatitude,
                                onValueChange = { createSharePointLatitude = it },
                                label = { Text("Latitude") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = createSharePointLongitude,
                                onValueChange = { createSharePointLongitude = it },
                                label = { Text("Longitude") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                        }
                        if (!createSharePointLocationValid) {
                            Text(
                                "Pick a valid map location.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (createSharePointMode == "at_time") {
                            OutlinedTextField(
                                value = createSharePointAt,
                                onValueChange = { createSharePointAt = it },
                                label = { Text("Share time (ISO, e.g. 2026-03-06T09:00:00Z)") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (!createSharePointTimeValid) {
                                Text(
                                    "Use a valid ISO datetime with timezone.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            } else if (!createSharePointWindowValid) {
                                Text(
                                    "Scheduled shares must be within the next 24 hours.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                val canSubmit = when (createPostType) {
                    "community_event" -> {
                        createPostTitle.isNotBlank() &&
                            createPostBody.isNotBlank() &&
                            createEventDate.isNotBlank() &&
                            parseIsoInstant(createEventDate.trim()) != null &&
                            createEventLocationValid &&
                            createEventRecurrenceValid
                    }
                    "lost_found" -> {
                        createPostTitle.isNotBlank() &&
                            createPostBody.isNotBlank() &&
                            createLostFoundPetTraits.isNotBlank() &&
                            createLostFoundLastSeenLocation.isNotBlank() &&
                            createLostFoundContactPref.isNotBlank()
                    }
                    "share_point" -> {
                        createPostTitle.isNotBlank() &&
                            createPostBody.isNotBlank() &&
                            createSharePointLocationValid &&
                            createSharePointTimeValid &&
                            createSharePointWindowValid
                    }
                    else -> {
                        createPostTitle.isNotBlank() && createPostBody.isNotBlank()
                    }
                }
                Button(
                    enabled = !loading && canSubmit,
                    onClick = {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastCreatePostSubmitAt < 900L) return@Button
                        lastCreatePostSubmitAt = now
                        if (createPostType == "lost_found") {
                            onCreateLostFound(
                                CommunityPostCreate(
                                    type = "lost_found",
                                    title = createPostTitle.trim(),
                                    body = createPostBody.trim(),
                                    suburb = suburb,
                                    alertType = createLostFoundAlertType,
                                    alertStatus = "open",
                                    petName = createLostFoundPetName.trim().ifBlank { null },
                                    petTraits = createLostFoundPetTraits.trim(),
                                    lastSeenAt = createLostFoundLastSeenAt.trim().ifBlank { null },
                                    lastSeenLocation = createLostFoundLastSeenLocation.trim(),
                                    contactPref = createLostFoundContactPref.trim(),
                                    photoUrls = createLostFoundPhotoUrls
                                        .split(",")
                                        .map { it.trim() }
                                        .filter { it.isNotBlank() },
                                ),
                            )
                        } else if (createPostType == "community_event") {
                            onCreateEvent(
                                createPostTitle.trim(),
                                createPostBody.trim(),
                                createEventDate.trim(),
                                createEventGroupId.trim().ifBlank { null },
                                createEventLocationName.trim().ifBlank { null },
                                if (createEventLocationEnabled) createEventLat else null,
                                if (createEventLocationEnabled) createEventLng else null,
                                createEventRecurrence,
                                if (createEventRecurrence == "none") 1 else (createEventRecurrenceIntervalInt ?: 1),
                            )
                        } else if (createPostType == "share_point") {
                            val payload = CommunityPostCreate(
                                type = "share_point",
                                title = createPostTitle.trim(),
                                body = createPostBody.trim(),
                                suburb = suburb,
                                lastSeenAt = if (createSharePointMode == "now") {
                                    "now"
                                } else {
                                    createSharePointAt.trim()
                                },
                                lastSeenLocation = createSharePointLocationName.trim().ifBlank { null },
                                contactPref = if (createSharePointMode == "now") "share_now" else "share_at",
                                shareScope = createSharePointScope,
                                sharePrecision = createSharePointPrecision,
                                latitude = createSharePointLat,
                                longitude = createSharePointLng,
                            )
                            if (!sharePointConsentAccepted) {
                                pendingSharePointPayload = payload
                                showSharePointConsentDialog = true
                                return@Button
                            }
                            onCreateSharePoint(payload)
                        } else {
                            onCreateGroupPost(createPostTitle.trim(), createPostBody.trim(), suburb)
                        }
                        resetCreatePostInputs()
                    },
                ) {
                    Text("Post")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePostDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showSharePointConsentDialog) {
        AlertDialog(
            onDismissRequest = { showSharePointConsentDialog = false },
            title = { Text("Location sharing privacy") },
            text = {
                Text(
                    "Share points are manual only. \"Now\" expires in 1 hour. " +
                        "Scheduled shares must start within 24 hours and are not background tracking.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        sharePointConsentAccepted = true
                        sharePrivacyPrefs.edit().putBoolean(SHARE_POINT_CONSENT_ACK_KEY, true).apply()
                        pendingSharePointPayload?.let { payload -> onCreateSharePoint(payload) }
                        pendingSharePointPayload = null
                        showCreatePostDialog = false
                        showSharePointConsentDialog = false
                        createSharePointMode = "now"
                        createSharePointScope = "friends"
                        createSharePointPrecision = "approximate"
                    },
                ) {
                    Text("I understand")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingSharePointPayload = null
                        showSharePointConsentDialog = false
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun CompactCommunityActionButton(
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        IconButton(
            enabled = enabled,
            onClick = onClick,
            modifier = Modifier.size(44.dp),
        ) {
            icon()
        }
    }
}

@Composable
private fun CommunityFeedSkeletonCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CommunitySpaceMd),
            verticalArrangement = Arrangement.spacedBy(CommunitySpaceXs),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(12.dp),
            ) {}
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
            ) {}
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(10.dp),
            ) {}
        }
    }
}

private fun communitySortLabel(sortBy: String): String = when (sortBy.lowercase()) {
    "latest", "newest" -> "Latest"
    "trending" -> "Trending"
    else -> "Relevant"
}

private enum class CommunityLens(val label: String) {
    Posts("Posts"),
    LostFound("Lost & Found"),
}

private enum class CommunityDiscoveryType(val label: String) {
    Groups("Groups"),
    Events("Events"),
    Friends("Friends"),
}

private enum class MeetupWindow(val label: String) {
    AllUpcoming("All upcoming"),
    Today("Today"),
    Tomorrow("Tomorrow"),
    Weekend("Weekend"),
    Going("Going"),
}

private enum class MeetupAreaFilter(val label: String) {
    Anywhere("Anywhere"),
    ThisSuburb("This suburb"),
    JoinedGroups("Joined groups"),
}

private enum class MeetupCadence(val label: String) {
    Daily("Daily walk routine"),
    Weekly("Weekly walk routine"),
    Fortnightly("Fortnightly walk routine"),
    OneOff("One-off meetup"),
}

private enum class MeetupPlannerMode(val label: String) {
    CreateEvent("Create event"),
    PingGroup("Ping a group"),
    ComplexEvent("Plan complex event"),
}

private enum class GroupDetailTab(val label: String) {
    About("About"),
    Events("Events"),
    Members("Members"),
    Posts("Posts"),
}

private enum class GroupPostFilter(val label: String) {
    All("All"),
    Discussions("Discussions"),
    LostFound("Lost & Found"),
    OpenLostFound("Open Lost & Found"),
}

private data class EventDateOption(
    val label: String,
    val value: String,
)

private fun eventDatePresets(
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): List<EventDateOption> {
    val today = now.atZone(zone).toLocalDate()
    val tomorrow = today.plusDays(1)
    val saturday = nextOrSameDay(today, DayOfWeek.SATURDAY)
    fun atLocal(date: LocalDate, hour: Int): String {
        return date.atTime(hour, 0).atZone(zone).toInstant().toString()
    }
    return listOf(
        EventDateOption("Tonight 6pm", atLocal(today, 18)),
        EventDateOption("Tomorrow 9am", atLocal(tomorrow, 9)),
        EventDateOption("This weekend 10am", atLocal(saturday, 10)),
    )
}

private fun showIsoDatePicker(
    context: Context,
    currentIsoValue: String,
    fallbackHour: Int,
    onDateSelected: (String) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val currentInstant = parseIsoInstant(currentIsoValue)
    val initialDate = currentInstant?.atZone(zone)?.toLocalDate() ?: LocalDate.now(zone)
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
            onDateSelected(updateIsoDateKeepingLocalTime(currentIsoValue, selectedDate, fallbackHour, zone))
        },
        initialDate.year,
        initialDate.monthValue - 1,
        initialDate.dayOfMonth,
    ).show()
}

private fun updateIsoDateKeepingLocalTime(
    currentIsoValue: String,
    selectedDate: LocalDate,
    fallbackHour: Int,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val currentInstant = parseIsoInstant(currentIsoValue)
    val selectedDateTime = if (currentInstant != null) {
        val localDateTime = currentInstant.atZone(zone).toLocalDateTime()
        selectedDate.atTime(localDateTime.toLocalTime())
    } else {
        selectedDate.atTime(fallbackHour, 0)
    }
    return selectedDateTime.atZone(zone).toInstant().toString()
}

private fun nextOrSameDay(start: LocalDate, target: DayOfWeek): LocalDate {
    var cursor = start
    repeat(7) {
        if (cursor.dayOfWeek == target) return cursor
        cursor = cursor.plusDays(1)
    }
    return start.plusDays(7)
}

private fun filterGroupsForDiscovery(
    groups: List<Group>,
    query: String,
    suburb: String,
): List<Group> {
    if (groups.isEmpty()) return emptyList()
    val normalizedQuery = query.trim().lowercase()
    val normalizedSuburb = suburb.trim().lowercase()
    return groups
        .asSequence()
        .filter { group ->
            val suburbMatches = normalizedSuburb.isBlank() || group.suburb.lowercase().contains(normalizedSuburb)
            val queryMatches = normalizedQuery.isBlank() ||
                group.name.contains(normalizedQuery, ignoreCase = true) ||
                group.suburb.contains(normalizedQuery, ignoreCase = true) ||
                group.groupBadges.any { badge -> badge.contains(normalizedQuery, ignoreCase = true) }
            suburbMatches && queryMatches
        }
        .sortedWith(
            compareByDescending<Group> { it.membershipStatus == "member" }
                .thenByDescending { it.suburb.equals(suburb, ignoreCase = true) }
                .thenByDescending { it.official }
                .thenByDescending { it.cooperativeScore }
                .thenByDescending { it.memberCount },
        )
        .toList()
}

private fun filterEventsForDiscovery(
    events: List<CommunityEvent>,
    query: String,
    suburb: String,
): List<CommunityEvent> {
    if (events.isEmpty()) return emptyList()
    val normalizedQuery = query.trim().lowercase()
    val normalizedSuburb = suburb.trim().lowercase()
    return events
        .asSequence()
        .filter { event ->
            val suburbMatches = normalizedSuburb.isBlank() || event.suburb.lowercase().contains(normalizedSuburb)
            val queryMatches = normalizedQuery.isBlank() ||
                event.title.contains(normalizedQuery, ignoreCase = true) ||
                event.description.contains(normalizedQuery, ignoreCase = true) ||
                event.suburb.contains(normalizedQuery, ignoreCase = true)
            suburbMatches && queryMatches
        }
        .sortedByDescending { event -> parseIsoInstant(event.date) ?: Instant.EPOCH }
        .toList()
}

private fun filterFriendsForDiscovery(
    threads: List<MessageThread>,
    query: String,
): List<MessageThread> {
    if (threads.isEmpty()) return emptyList()
    val normalizedQuery = query.trim().lowercase()
    return threads
        .asSequence()
        .filter { thread ->
            normalizedQuery.isBlank() ||
                thread.title.contains(normalizedQuery, ignoreCase = true) ||
                thread.participantAccountLabel.contains(normalizedQuery, ignoreCase = true) ||
                thread.participantPetNames.any { pet -> pet.contains(normalizedQuery, ignoreCase = true) } ||
                thread.lastMessage.contains(normalizedQuery, ignoreCase = true)
        }
        .sortedWith(
            compareByDescending<MessageThread> { it.unreadCount > 0 }
                .thenByDescending { it.isPinned }
                .thenBy { it.title },
        )
        .toList()
}

private sealed interface CommunityFeedItem {
    val stableId: String

    data class PostItem(val post: CommunityPost) : CommunityFeedItem {
        override val stableId: String = "post_${post.id}"
    }
}

private fun buildCommunityFeed(
    lens: CommunityLens,
    posts: List<CommunityPost>,
): List<CommunityFeedItem> {
    val sortedPosts = posts.sortedWith(
        compareByDescending<CommunityPost> { post ->
            lostFoundPriorityScore(post = post, now = Instant.now())
        }.thenByDescending { post ->
            parseIsoInstant(post.createdAt) ?: Instant.EPOCH
        },
    )

    return when (lens) {
        CommunityLens.Posts -> sortedPosts
            .filter { post -> post.type != "lost_found" }
            .map { post -> CommunityFeedItem.PostItem(post) }
        CommunityLens.LostFound -> sortedPosts
            .filter { post -> post.type == "lost_found" }
            .map { post -> CommunityFeedItem.PostItem(post) }
    }
}

private fun lostFoundPriorityScore(
    post: CommunityPost,
    now: Instant = Instant.now(),
): Int {
    if (post.type != "lost_found") return 0
    if ((post.alertStatus ?: "open") != "open") return 0
    val urgency = computeLostFoundUrgency(post, now)
    return when (urgency?.level) {
        LostFoundUrgencyLevel.Critical -> 3
        LostFoundUrgencyLevel.Warning -> 2
        null -> 1
    }
}

@Composable
private fun MeetupHeroCard(
    suburb: String,
    totalGroups: Int,
    totalEvents: Int,
    totalDiscussions: Int,
    joinedGroups: Int,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Dog community in $suburb", style = MaterialTheme.typography.titleLarge)
            Text(
                "$totalGroups groups • $totalDiscussions posts • $totalEvents events nearby.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (joinedGroups > 0) "You're in $joinedGroups groups. Keep exploring local activity."
                else "Join a group to get social updates from local dog owners.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeatherAndPrivacyCard(
    weather: CommunityWeatherSnapshot,
    autoCheckInEnabled: Boolean,
    requireCrowd: Boolean,
    quorumCount: Int,
    quorumThreshold: Int,
    quorumWindowMinutes: Int,
    loading: Boolean,
    onRefreshWeather: () -> Unit,
    onSetAutoCheckInEnabled: (Boolean) -> Unit,
    onSetRequireCrowd: (Boolean) -> Unit,
    onSimulateParkArrival: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Live park weather", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "${weather.suburb} • ${weather.temperatureC}°C • ${weather.condition} • Rain ${weather.rainChancePercent}% • Wind ${weather.windKph}km/h",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = weather.updatedAtLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    enabled = !loading,
                    onClick = onRefreshWeather,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Refresh weather")
                }
                TextButton(
                    enabled = !loading,
                    onClick = onSimulateParkArrival,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Simulate park arrival")
                }
            }
            Text("Privacy-safe auto check-in", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = autoCheckInEnabled,
                    onClick = { onSetAutoCheckInEnabled(!autoCheckInEnabled) },
                    label = { Text(if (autoCheckInEnabled) "Auto check-in on" else "Auto check-in off") },
                )
                FilterChip(
                    selected = requireCrowd,
                    onClick = { onSetRequireCrowd(!requireCrowd) },
                    label = { Text(if (requireCrowd) "Safety mode on" else "Safety mode off") },
                )
            }
            Text(
                text = "Auto check-in requires $quorumThreshold distinct members within $quorumWindowMinutes minutes. Current quorum: $quorumCount/$quorumThreshold.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Shares to your member park group only. No precise location and no solo visibility feed.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MeetupPlannerCard(
    selectedWindow: MeetupWindow,
    selectedArea: MeetupAreaFilter,
    meetupEvents: List<CommunityEvent>,
    onSelectWindow: (MeetupWindow) -> Unit,
    onSelectArea: (MeetupAreaFilter) -> Unit,
    loading: Boolean,
    onRsvpEvent: (eventId: String, attending: Boolean) -> Unit,
) {
    val nextEvent = meetupEvents.firstOrNull()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Meetup planner", style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MeetupWindow.entries.toList(), key = { window -> window.name }) { window ->
                    FilterChip(
                        selected = selectedWindow == window,
                        onClick = { onSelectWindow(window) },
                        label = { Text(window.label) },
                    )
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MeetupAreaFilter.entries.toList(), key = { area -> area.name }) { area ->
                    FilterChip(
                        selected = selectedArea == area,
                        onClick = { onSelectArea(area) },
                        label = { Text(area.label) },
                    )
                }
            }
            if (nextEvent == null) {
                Text(
                    text = "No meetups in this window yet. Switch the window or create one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "Next: ${nextEvent.title}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${formatRelativeDay(nextEvent.date)} • ${formatIsoDateTime(nextEvent.date)} • ${nextEvent.attendeeCount} going",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (nextEvent.rsvpStatus != "attending") {
                    Button(
                        enabled = !loading,
                        onClick = { onRsvpEvent(nextEvent.id, true) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("RSVP next meetup")
                    }
                } else {
                    AssistChip(
                        onClick = {},
                        label = { Text("You are going") },
                    )
                }
            }
        }
    }
}

@Composable
private fun MeetupRoutineActions(
    event: CommunityEvent,
    loading: Boolean,
) {
    val context = LocalContext.current
    var recurringEnabled by rememberSaveable(event.id) { mutableStateOf(true) }
    val cadence = remember(event.id, event.title, event.description) { inferMeetupCadence(event) }
    val cadenceLabel = cadence.label
    val recurrenceRule = remember(event.id, event.date, cadence, recurringEnabled) {
        if (recurringEnabled) recurrenceRuleForCadence(event, cadence) else null
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AssistChip(onClick = {}, label = { Text(cadenceLabel) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                enabled = !loading,
                onClick = { recurringEnabled = !recurringEnabled },
                modifier = Modifier.weight(1f),
            ) {
                Text(if (recurringEnabled) "Recurring on" else "One-off event")
            }
            Button(
                enabled = !loading,
                onClick = {
                    communityEventToCalendarDraft(
                        event = event,
                        recurrenceRule = recurrenceRule,
                    )?.let { draft ->
                        context.openCalendarDraft(draft)
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Add to calendar")
            }
        }
        Text(
            "Creates a prefilled calendar draft with default reminders (24h + 1h).",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(value, style = MaterialTheme.typography.titleSmall)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun GroupSnapshotCard(
    group: Group,
    loading: Boolean,
    onOpenGroup: (String) -> Unit,
    onJoinGroup: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.width(260.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.People, contentDescription = "Group", modifier = Modifier.size(18.dp))
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${group.memberCount} members • ${group.suburb}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Co-op ${group.cooperativeScore} • Your clean points ${group.myCleanParkPoints}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (group.groupBadges.isNotEmpty()) {
                Text(
                    text = "Badges: ${group.groupBadges.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            when (group.membershipStatus) {
                "member" -> {
                    OutlinedButton(
                        enabled = !loading,
                        onClick = { onOpenGroup(group.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Open")
                    }
                }

                "pending" -> {
                    OutlinedButton(enabled = false, onClick = {}, modifier = Modifier.fillMaxWidth()) {
                        Text("Pending approval")
                    }
                }

                else -> {
                    Button(
                        enabled = !loading,
                        onClick = { onJoinGroup(group.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Join")
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupPriorityCard(
    group: Group,
    loading: Boolean,
    events: List<CommunityEvent>,
    posts: List<CommunityPost>,
    savedEventIds: Set<String>,
    onOpenGroup: (String) -> Unit,
    onJoinGroup: (String) -> Unit,
    onRsvpEvent: (eventId: String, attending: Boolean) -> Unit,
    onReportEvent: (eventId: String, reason: String, details: String) -> Unit,
    onToggleSaveEvent: (eventId: String) -> Unit,
    onOpenMessages: (String?) -> Unit,
    onOpenEventDetails: (CommunityEvent, String?) -> Unit,
    onOpenPost: (CommunityPost) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(group.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${group.memberCount} members • ${group.suburb}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (group.membershipStatus == "member") {
                    TextButton(onClick = { onOpenGroup(group.id) }, enabled = !loading) { Text("Open") }
                } else {
                    Button(onClick = { onJoinGroup(group.id) }, enabled = !loading) { Text("Join") }
                }
            }

            Text("Events", style = MaterialTheme.typography.labelMedium)
            if (events.isEmpty()) {
                Text(
                    "No upcoming events yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(events, key = { event -> "group_lane_event_${event.id}" }) { event ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            modifier = Modifier
                                .width(220.dp)
                                .clickable { onOpenEventDetails(event, null) },
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(event.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${formatRelativeDay(event.date)} • ${event.attendeeCount} going",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedButton(
                                    onClick = { onRsvpEvent(event.id, event.rsvpStatus != "attending") },
                                    enabled = !loading && event.status == "approved",
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(if (event.rsvpStatus == "attending") "Going" else "RSVP")
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    IconButton(
                                        enabled = !loading,
                                        onClick = { onReportEvent(event.id, "Safety concern", "Reported from group card") },
                                    ) {
                                        Icon(Icons.Default.Flag, contentDescription = "Report event")
                                    }
                                    IconButton(
                                        enabled = !loading,
                                        onClick = { onToggleSaveEvent(event.id) },
                                    ) {
                                        Icon(
                                            imageVector = if (event.id in savedEventIds) Icons.Default.TurnedIn else Icons.Default.FavoriteBorder,
                                            contentDescription = "Save event",
                                        )
                                    }
                                    IconButton(
                                        enabled = !loading,
                                        onClick = {
                                            onOpenEventDetails(event, "Following this event.")
                                        },
                                    ) {
                                        Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Comment")
                                    }
                                    if (event.createdBy.isNotBlank()) {
                                        IconButton(
                                            enabled = !loading,
                                            onClick = { onOpenMessages(event.createdBy) },
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Message organizer")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Text("Posts", style = MaterialTheme.typography.labelMedium)
            if (posts.isEmpty()) {
                Text(
                    "No recent posts yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(posts, key = { post -> "group_lane_post_${post.id}" }) { post ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            modifier = Modifier
                                .width(220.dp)
                                .clickable { onOpenPost(post) },
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(post.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    post.body,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    formatIsoDateTime(post.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventDiscoveryCard(
    event: CommunityEvent,
    groupName: String?,
    loading: Boolean,
    onOpenGroup: (String) -> Unit,
    onRsvpEvent: (eventId: String, attending: Boolean) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(event.title, style = MaterialTheme.typography.titleSmall)
            Text(
                "${formatRelativeDay(event.date)} • ${formatIsoDateTime(event.date)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${event.suburb} • ${event.attendeeCount} going",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                eventRecurrenceLabel(event)?.let { label -> AssistChip(onClick = {}, label = { Text(label) }) }
                eventLocationLabel(event)?.let { label -> AssistChip(onClick = {}, label = { Text(label) }) }
            }
            Text(
                event.description,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (event.status == "approved") {
                    OutlinedButton(
                        enabled = !loading,
                        onClick = { onRsvpEvent(event.id, event.rsvpStatus != "attending") },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (event.rsvpStatus == "attending") "Leave" else "RSVP")
                    }
                }
                event.groupId?.takeIf { groupId -> groupId.isNotBlank() }?.let { groupId ->
                    Button(
                        enabled = !loading,
                        onClick = { onOpenGroup(groupId) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(groupName ?: "Open group")
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendDiscoveryCard(
    thread: MessageThread,
    loading: Boolean,
    onOpenMessages: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(thread.participantAccountLabel, style = MaterialTheme.typography.titleSmall)
            if (thread.participantPetNames.isNotEmpty()) {
                Text(
                    thread.participantPetNames.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                thread.lastMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (thread.unreadCount > 0) {
                    AssistChip(onClick = {}, label = { Text("${thread.unreadCount} unread") })
                }
                OutlinedButton(
                    enabled = !loading,
                    onClick = onOpenMessages,
                ) {
                    Text("Message")
                }
            }
        }
    }
}

@Composable
private fun EventFeedCard(
    event: CommunityEvent,
    groupId: String?,
    groupName: String?,
    relativeDayLabel: String,
    dateTimeLabel: String,
    loading: Boolean,
    onRsvpEvent: (eventId: String, attending: Boolean) -> Unit,
    onApproveEvent: (eventId: String) -> Unit,
    onOpenGroup: (String) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by rememberSaveable(event.id) { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Event", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(event.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = event.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$relativeDayLabel • $dateTimeLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = buildString {
                    append("${event.attendeeCount} going")
                    append(" • ${event.suburb}")
                    groupName?.let { append(" • $it") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                eventRecurrenceLabel(event)?.let { label -> AssistChip(onClick = {}, label = { Text(label) }) }
                eventLocationLabel(event)?.let { label -> AssistChip(onClick = {}, label = { Text(label) }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val isAttending = event.rsvpStatus == "attending"
                if (event.status == "approved") {
                    Button(
                        enabled = !loading,
                        onClick = { onRsvpEvent(event.id, !isAttending) },
                    ) {
                        Text(if (isAttending) "Leave" else "RSVP")
                    }
                } else {
                    Button(
                        enabled = !loading,
                        onClick = { onApproveEvent(event.id) },
                    ) {
                        Text("Approve")
                    }
                }
                if (event.rsvpStatus == "attending") {
                    Text("You are going", style = MaterialTheme.typography.labelMedium)
                }
            }
            MeetupRoutineActions(
                event = event,
                loading = loading,
            )
            if (!groupId.isNullOrBlank()) {
                TextButton(
                    enabled = !loading,
                    onClick = { onOpenGroup(groupId) },
                ) {
                    Text("Open group")
                }
            }
            TextButton(
                enabled = !loading,
                onClick = {
                    clipboard.setText(AnnotatedString(formatEventShareText(event, groupName)))
                    copied = true
                },
            ) {
                Text(if (copied) "Copied event details" else "Copy event details")
            }
        }
    }
}

@Composable
private fun DiscussionFeedCard(
    post: CommunityPost,
    createdAtLabel: String,
    commentHint: Int,
    isFriendActivity: Boolean,
    groupName: String?,
    authorLabel: String?,
    onOpenPost: () -> Unit,
    isSaved: Boolean,
    onQuickReport: () -> Unit,
    onQuickBlock: () -> Unit,
    onToggleSave: () -> Unit,
    onMessageAuthor: (() -> Unit)?,
) {
    val isLostFound = post.type == "lost_found"
    val isSharePoint = post.type == "share_point"
    val alertStatus = post.alertStatus ?: if (isLostFound) "open" else null
    val urgency = remember(post.id, post.alertStatus, post.followUpDueAt, post.expiresAt) {
        computeLostFoundUrgency(post)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.clickable { onOpenPost() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ChatBubble, contentDescription = "Discussion", modifier = Modifier.size(16.dp))
                Text(
                    text = when {
                        isLostFound -> "Lost & Found"
                        isSharePoint -> "Location Share"
                        else -> "Discussion"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (!isLostFound && !isSharePoint && !groupName.isNullOrBlank()) {
                    Text(
                        text = groupName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!isLostFound && !isSharePoint) {
                    AssistChip(
                        onClick = {},
                        label = { Text(if (isFriendActivity) "Friend activity" else "Group activity") },
                    )
                }
                if (isSharePoint) {
                    sharePointTimingLabel(post)?.let { label ->
                        AssistChip(onClick = {}, label = { Text(label) })
                    }
                }
                if (isLostFound) {
                    AlertTypeChip(post.alertType ?: "lost")
                }
                if (isLostFound && alertStatus != null) {
                    AlertStatusChip(alertStatus)
                }
                if (urgency != null) {
                    LostFoundUrgencyChip(urgency)
                }
                if (isSaved) {
                    AssistChip(onClick = {}, label = { Text("Saved") })
                }
            }
            Text(
                post.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (post.photoUrls.isNotEmpty()) {
                AsyncImage(
                    model = post.photoUrls.first(),
                    contentDescription = "${post.title} photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp),
                    contentScale = ContentScale.Crop,
                )
                if (post.photoUrls.size > 1) {
                    Text(
                        text = "+${post.photoUrls.size - 1} more photo${if (post.photoUrls.size > 2) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (isLostFound) {
                Text(
                    text = buildString {
                        append((post.alertType ?: "lost").replaceFirstChar { it.uppercase() })
                        post.petName?.let { append(" • $it") }
                        post.petTraits?.let { append(" • $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                post.lastSeenLocation?.let { location ->
                    Text(
                        text = "Last seen: $location",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (isSharePoint) {
                sharePointLocationLabel(post)?.let { locationLabel ->
                    Text(
                        text = "Pin: $locationLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                sharePointTimingSummary(post)?.let { summary ->
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "Visible to ${sharePointAudienceLabel(post)} • ${sharePointPrecisionLabel(post)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = post.body,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!authorLabel.isNullOrBlank()) {
                Text(
                    text = "From $authorLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${post.suburb} • $createdAtLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "$commentHint replies in thread",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onQuickReport) {
                    Icon(Icons.Default.Flag, contentDescription = "Report")
                }
                IconButton(onClick = onToggleSave) {
                    Icon(
                        imageVector = if (isSaved) Icons.Default.TurnedIn else Icons.Default.FavoriteBorder,
                        contentDescription = if (isSaved) "Saved" else "Save",
                    )
                }
                IconButton(onClick = onOpenPost) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Comment")
                }
                if (onMessageAuthor != null) {
                    IconButton(onClick = onMessageAuthor) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Message")
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupFeedCard(
    group: Group,
    isFollowed: Boolean,
    roster: List<PetRosterItem>,
    latestInvite: GroupInvite?,
    loading: Boolean,
    onOpenGroup: (String) -> Unit,
    onJoinGroup: (String) -> Unit,
    onCreateGroupInvite: (String) -> Unit,
    onClearGroupInvite: (String) -> Unit,
    onApproveJoinRequest: (groupId: String) -> Unit,
    onRejectJoinRequest: (groupId: String) -> Unit,
    onToggleFollowGroup: (groupId: String) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.People, contentDescription = "Group", modifier = Modifier.size(18.dp))
                Text(
                    text = buildString {
                        append(group.name)
                        if (group.official) append(" • Official")
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = "${group.memberCount} members • ${group.suburb}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Co-op ${group.cooperativeScore} • Pack points ${group.myPackBuilderPoints} • Clean points ${group.myCleanParkPoints}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (group.groupBadges.isNotEmpty()) {
                Text(
                    text = "Badges: ${group.groupBadges.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = !loading,
                    onClick = { onToggleFollowGroup(group.id) },
                ) {
                    Text(if (isFollowed) "Unfollow updates" else "Follow updates")
                }
                if (isFollowed) {
                    AssistChip(onClick = {}, label = { Text("Following") })
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when (group.membershipStatus) {
                    "member" -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedButton(
                                enabled = !loading,
                                onClick = { onOpenGroup(group.id) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Open")
                            }
                            TextButton(
                                enabled = !loading,
                                onClick = { onCreateGroupInvite(group.id) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Invite")
                            }
                        }
                    }

                    "pending" -> {
                        OutlinedButton(enabled = false, onClick = {}) { Text("Pending approval") }
                    }

                    else -> {
                        Button(enabled = !loading, onClick = { onJoinGroup(group.id) }) {
                            Text("Apply to join")
                        }
                    }
                }
            }

            latestInvite?.let { invite ->
                val inviteQrBitmap = remember(invite.inviteUrl) {
                    generateQrImageBitmap(
                        content = invite.inviteUrl,
                        sizePx = 360,
                    )
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Invite link", style = MaterialTheme.typography.labelSmall)
                        Text(invite.inviteUrl, style = MaterialTheme.typography.bodySmall)
                        if (inviteQrBitmap != null) {
                            Image(
                                bitmap = inviteQrBitmap,
                                contentDescription = "Group invite QR code",
                                modifier = Modifier.size(100.dp),
                            )
                        }
                        Text("Expires ${formatIsoDateTime(invite.expiresAt)}", style = MaterialTheme.typography.labelSmall)
                        TextButton(onClick = { onClearGroupInvite(group.id) }) {
                            Text("Hide")
                        }
                    }
                }
            }

            if (group.isAdmin && group.pendingRequestCount > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !loading, onClick = { onApproveJoinRequest(group.id) }) {
                        Text("Approve next (${group.pendingRequestCount})")
                    }
                    OutlinedButton(enabled = !loading, onClick = { onRejectJoinRequest(group.id) }) {
                        Text("Reject")
                    }
                }
            }

            if (roster.isNotEmpty()) {
                PetRosterShowcase(
                    title = "Recently active dogs",
                    pets = roster,
                )
            }
        }
    }
}

private fun sortEventsForCommunity(events: List<CommunityEvent>): List<CommunityEvent> {
    if (events.isEmpty()) return emptyList()
    val now = Instant.now()
    return events.sortedWith(
        compareBy<CommunityEvent> { event ->
            val instant = parseIsoInstant(event.date)
            when {
                event.status != "approved" -> 2
                instant == null -> 3
                instant.isBefore(now) -> 1
                else -> 0
            }
        }.thenBy { event ->
            val instant = parseIsoInstant(event.date) ?: return@thenBy Long.MAX_VALUE
            if (instant.isBefore(now)) Long.MAX_VALUE - instant.toEpochMilli() else instant.toEpochMilli()
        },
    )
}

private fun formatAlertType(alertType: String): String = when (alertType.lowercase()) {
    "found" -> "Found"
    else -> "Lost"
}

@Composable
private fun AlertTypeChip(alertType: String) {
    val colorScheme = MaterialTheme.colorScheme
    val normalized = alertType.lowercase()
    val (container, label) = if (normalized == "found") {
        colorScheme.tertiaryContainer to colorScheme.onTertiaryContainer
    } else {
        colorScheme.secondaryContainer to colorScheme.onSecondaryContainer
    }
    AssistChip(
        onClick = {},
        label = { Text(formatAlertType(normalized)) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = container,
            labelColor = label,
        ),
    )
}

@Composable
private fun AlertStatusChip(status: String) {
    val colorScheme = MaterialTheme.colorScheme
    val normalized = status.lowercase()
    val (container, label) = when (normalized) {
        "open" -> colorScheme.primaryContainer to colorScheme.onPrimaryContainer
        "reunited" -> colorScheme.secondaryContainer to colorScheme.onSecondaryContainer
        "owner_found" -> colorScheme.secondaryContainer to colorScheme.onSecondaryContainer
        "expired" -> colorScheme.surfaceVariant to colorScheme.onSurfaceVariant
        else -> colorScheme.surfaceVariant to colorScheme.onSurfaceVariant
    }
    AssistChip(
        onClick = {},
        label = { Text(formatAlertStatus(normalized)) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = container,
            labelColor = label,
        ),
    )
}

private enum class LostFoundUrgencyLevel {
    Critical,
    Warning,
}

private data class LostFoundUrgency(
    val label: String,
    val level: LostFoundUrgencyLevel,
)

private fun computeLostFoundUrgency(post: CommunityPost, now: Instant = Instant.now()): LostFoundUrgency? {
    if (post.type != "lost_found") return null
    if ((post.alertStatus ?: "open") != "open") return null
    val followUpDueAt = parseIsoInstant(post.followUpDueAt)
    val expiresAt = parseIsoInstant(post.expiresAt)
    if (expiresAt != null && !expiresAt.isAfter(now)) {
        return LostFoundUrgency(label = "Expired", level = LostFoundUrgencyLevel.Critical)
    }
    if (followUpDueAt != null && !followUpDueAt.isAfter(now)) {
        return LostFoundUrgency(label = "Follow-up due", level = LostFoundUrgencyLevel.Critical)
    }
    if (expiresAt != null && !expiresAt.isAfter(now.plus(24, ChronoUnit.HOURS))) {
        return LostFoundUrgency(label = "Expires in <24h", level = LostFoundUrgencyLevel.Warning)
    }
    return null
}

@Composable
private fun LostFoundUrgencyChip(urgency: LostFoundUrgency) {
    val colorScheme = MaterialTheme.colorScheme
    val (container, label) = when (urgency.level) {
        LostFoundUrgencyLevel.Critical -> colorScheme.errorContainer to colorScheme.onErrorContainer
        LostFoundUrgencyLevel.Warning -> colorScheme.tertiaryContainer to colorScheme.onTertiaryContainer
    }
    AssistChip(
        onClick = {},
        label = { Text(urgency.label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = container,
            labelColor = label,
        ),
    )
}

private fun formatAlertStatus(status: String): String = when (status) {
    "open" -> "Open"
    "reunited" -> "Reunited"
    "owner_found" -> "Owner found"
    "expired" -> "Expired"
    else -> status.replace("_", " ").replaceFirstChar { it.uppercase() }
}

private fun sharePointLocationLabel(post: CommunityPost): String? {
    val lat = post.latitude
    val lng = post.longitude
    if (lat == null || lng == null) return null
    return post.lastSeenLocation?.takeIf { it.isNotBlank() } ?: "${"%.4f".format(Locale.US, lat)}, ${"%.4f".format(Locale.US, lng)}"
}

private fun sharePointTimingLabel(post: CommunityPost): String? {
    if (post.type != "share_point") return null
    return when (post.contactPref) {
        "share_now" -> "Now"
        "share_at" -> "Scheduled"
        else -> if (post.expiresAt != null) "Now" else "Scheduled"
    }
}

private fun sharePointTimingSummary(post: CommunityPost): String? {
    if (post.type != "share_point") return null
    val startsAt = post.lastSeenAt?.let { value -> "Starts ${formatIsoDateTime(value)}" }
    val expiresAt = post.expiresAt?.let { value -> "Expires ${formatIsoDateTime(value)}" }
    return listOfNotNull(startsAt, expiresAt).joinToString(" • ").ifBlank { null }
}

private fun sharePointAudienceLabel(post: CommunityPost): String {
    return when (post.shareScope?.lowercase()) {
        "community" -> "Community"
        else -> "Friends only"
    }
}

private fun sharePointPrecisionLabel(post: CommunityPost): String {
    return when (post.sharePrecision?.lowercase()) {
        "exact" -> "Exact"
        else -> "Approximate (~100m)"
    }
}

private fun formatEventShareText(
    event: CommunityEvent,
    groupName: String?,
): String {
    return buildString {
        append(event.title)
        append(" • ")
        append(formatIsoDateTime(event.date))
        append(" • ")
        append(event.suburb)
        groupName?.takeIf { it.isNotBlank() }?.let { name ->
            append(" • ")
            append(name)
        }
        eventLocationLabel(event)?.let { location ->
            append(" • ")
            append(location)
        }
        eventRecurrenceLabel(event)?.let { recurrence ->
            append(" • ")
            append(recurrence)
        }
        append(" • ")
        append(event.attendeeCount)
        append(" going")
    }
}

private fun isWithinNextDays(raw: String?, days: Long): Boolean {
    val instant = parseIsoInstant(raw) ?: return false
    val now = Instant.now()
    if (instant.isBefore(now)) return false
    val limit = now.plus(days, ChronoUnit.DAYS)
    return instant <= limit
}

private fun isEventInPast(raw: String?, now: Instant = Instant.now()): Boolean {
    val instant = parseIsoInstant(raw) ?: return false
    return instant.isBefore(now)
}

private fun filterMeetupEvents(
    events: List<CommunityEvent>,
    window: MeetupWindow,
    area: MeetupAreaFilter,
    selectedSuburb: String,
    joinedGroupIds: Set<String>,
    zone: ZoneId = ZoneId.systemDefault(),
    now: Instant = Instant.now(),
): List<CommunityEvent> {
    if (events.isEmpty()) return emptyList()
    val today = now.atZone(zone).toLocalDate()
    val tomorrow = today.plusDays(1)
    return events.filter { event ->
        val instant = parseIsoInstant(event.date) ?: return@filter false
        if (instant.isBefore(now)) return@filter false
        val localDate = instant.atZone(zone).toLocalDate()
        val matchesWindow = when (window) {
            MeetupWindow.AllUpcoming -> true
            MeetupWindow.Today -> localDate == today
            MeetupWindow.Tomorrow -> localDate == tomorrow
            MeetupWindow.Weekend -> localDate.dayOfWeek == DayOfWeek.SATURDAY || localDate.dayOfWeek == DayOfWeek.SUNDAY
            MeetupWindow.Going -> event.rsvpStatus == "attending"
        }
        if (!matchesWindow) return@filter false

        when (area) {
            MeetupAreaFilter.Anywhere -> true
            MeetupAreaFilter.ThisSuburb -> event.suburb.equals(selectedSuburb, ignoreCase = true)
            MeetupAreaFilter.JoinedGroups -> event.groupId != null && event.groupId in joinedGroupIds
        }
    }
}

private fun inferMeetupCadence(event: CommunityEvent): MeetupCadence {
    val lower = "${event.title} ${event.description}".lowercase()
    return when {
        "fortnight" in lower || "biweekly" in lower || "every 2 weeks" in lower -> MeetupCadence.Fortnightly
        "daily" in lower || "every day" in lower -> MeetupCadence.Daily
        "weekly" in lower || "every week" in lower || "pack walk" in lower || "morning walk" in lower || "evening walk" in lower -> MeetupCadence.Weekly
        "walk" in lower -> MeetupCadence.Weekly
        else -> MeetupCadence.OneOff
    }
}

private fun recurrenceRuleForCadence(
    event: CommunityEvent,
    cadence: MeetupCadence,
): String? {
    val start = parseIsoInstant(event.date) ?: return null
    val zone = ZoneId.systemDefault()
    val dayCode = when (start.atZone(zone).dayOfWeek) {
        DayOfWeek.MONDAY -> "MO"
        DayOfWeek.TUESDAY -> "TU"
        DayOfWeek.WEDNESDAY -> "WE"
        DayOfWeek.THURSDAY -> "TH"
        DayOfWeek.FRIDAY -> "FR"
        DayOfWeek.SATURDAY -> "SA"
        DayOfWeek.SUNDAY -> "SU"
    }
    val recurrenceRule = when (cadence) {
        MeetupCadence.Daily -> "FREQ=DAILY"
        MeetupCadence.Weekly -> "FREQ=WEEKLY;BYDAY=$dayCode"
        MeetupCadence.Fortnightly -> "FREQ=WEEKLY;INTERVAL=2;BYDAY=$dayCode"
        MeetupCadence.OneOff -> null
    }
    return recurrenceRule
}

private fun parseIsoInstant(raw: String?): Instant? {
    if (raw.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(raw).toInstant() }
        .recoverCatching { Instant.parse(raw) }
        .getOrNull()
}

private fun qrUrlHostLabel(rawUrl: String): String {
    return runCatching {
        Uri.parse(rawUrl).host?.trim()?.lowercase().orEmpty().ifBlank { "unknown_host" }
    }.getOrDefault("malformed_url")
}

private fun formatRelativeDay(raw: String?): String {
    val instant = parseIsoInstant(raw) ?: return "Soon"
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val eventDay = instant.atZone(zone).toLocalDate()
    val diff = ChronoUnit.DAYS.between(today, eventDay)
    return when {
        diff == 0L -> "Today"
        diff == 1L -> "Tomorrow"
        diff in 2L..6L -> "In $diff days"
        diff == -1L -> "Yesterday"
        diff < -1L -> "${-diff} days ago"
        else -> eventDay.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    }
}

private fun formatIsoDateTime(raw: String?): String {
    if (raw.isNullOrBlank()) return "Just now"
    val instant = parseIsoInstant(raw) ?: return raw
    return runCatching {
        instant.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("EEE, d MMM • h:mm a"))
    }.getOrElse { raw }
}

private fun normalizeForEventPostMatch(raw: String): String = raw
    .lowercase()
    .replace(Regex("[^a-z0-9\\s]"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()

private fun scoreEventPostMatch(event: CommunityEvent, post: CommunityPost): Int {
    if (post.type != "group_post") return Int.MIN_VALUE
    val eventTitle = normalizeForEventPostMatch(event.title)
    val eventDescription = normalizeForEventPostMatch(event.description)
    val eventId = event.id.lowercase()
    val postTitle = normalizeForEventPostMatch(post.title)
    val postBody = normalizeForEventPostMatch(post.body)
    val postCombined = "$postTitle $postBody"
    if (postCombined.isBlank()) return Int.MIN_VALUE

    var score = 0
    if (eventId in postCombined) score += 14
    if (eventTitle.isNotBlank() && postTitle == eventTitle) score += 12
    if (eventTitle.isNotBlank() && (eventTitle in postCombined || postTitle in eventTitle)) score += 8

    val eventTokens = eventTitle.split(" ").filter { token -> token.length >= 4 }.toSet()
    val sharedTokens = eventTokens.count { token -> token in postCombined }
    score += sharedTokens * 2

    val barkagonSignals = listOf("barkagon", "octagon", "elimination", "ufc", "main event")
    val eventHasSignal = barkagonSignals.any { signal -> signal in eventTitle || signal in eventDescription }
    if (eventHasSignal && barkagonSignals.any { signal -> signal in postCombined }) score += 5

    if (event.createdBy.isNotBlank() && post.createdBy.equals(event.createdBy, ignoreCase = true)) score += 2
    if (post.suburb.equals(event.suburb, ignoreCase = true)) score += 1
    return score
}

private fun findDiscussionPostForEvent(
    event: CommunityEvent,
    posts: List<CommunityPost>,
): CommunityPost? {
    if (posts.isEmpty()) return null
    return posts
        .asSequence()
        .map { post -> post to scoreEventPostMatch(event, post) }
        .filter { (_, score) -> score >= 6 }
        .sortedByDescending { (_, score) -> score }
        .map { (post, _) -> post }
        .firstOrNull()
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun GroupDetailSheet(
    loading: Boolean,
    group: Group,
    roster: List<PetRosterItem>,
    events: List<CommunityEvent>,
    posts: List<CommunityPost>,
    onRsvpEvent: (eventId: String, attending: Boolean) -> Unit,
    savedEventIds: Set<String>,
    onReportEvent: (eventId: String, reason: String, details: String) -> Unit,
    onToggleSaveEvent: (eventId: String) -> Unit,
    onOpenMessages: (String?) -> Unit,
    onOpenEventDetails: (CommunityEvent, String?) -> Unit,
    onOpenPost: (CommunityPost) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTabName by rememberSaveable(group.id) { mutableStateOf(GroupDetailTab.About.name) }
    var selectedPostFilterName by rememberSaveable(group.id) { mutableStateOf(GroupPostFilter.All.name) }
    val selectedTab = GroupDetailTab.entries.firstOrNull { tab -> tab.name == selectedTabName } ?: GroupDetailTab.About
    val selectedPostFilter = GroupPostFilter.entries.firstOrNull { filter -> filter.name == selectedPostFilterName } ?: GroupPostFilter.All
    val sortedEvents = remember(events) { sortEventsForCommunity(events) }
    val sortedPosts = remember(posts) { posts.sortedByDescending { post -> parseIsoInstant(post.createdAt) ?: Instant.EPOCH } }
    val now = remember { Instant.now() }
    val groupEvents = remember(sortedEvents, group.id) {
        sortedEvents.filter { event -> event.groupId == group.id }
    }
    val nearbyEvents = remember(sortedEvents, group.id, group.suburb) {
        sortedEvents.filter { event ->
            event.groupId != group.id && event.suburb.equals(group.suburb, ignoreCase = true)
        }
    }
    val upcomingGroupEvents = remember(groupEvents, now) {
        groupEvents.filter { event ->
            val instant = parseIsoInstant(event.date) ?: return@filter true
            !instant.isBefore(now)
        }
    }
    val needsRsvpEvents = remember(upcomingGroupEvents) {
        upcomingGroupEvents.filter { event ->
            event.status == "approved" && event.rsvpStatus != "attending"
        }
    }
    val goingEvents = remember(upcomingGroupEvents) {
        upcomingGroupEvents.filter { event ->
            event.status == "approved" && event.rsvpStatus == "attending"
        }
    }
    val otherUpcomingEvents = remember(upcomingGroupEvents) {
        upcomingGroupEvents.filter { event ->
            event.status != "approved"
        }
    }
    val pastGroupEvents = remember(groupEvents, now) {
        groupEvents.filter { event ->
            val instant = parseIsoInstant(event.date) ?: return@filter false
            instant.isBefore(now)
        }
    }
    val filteredGroupPosts = remember(sortedPosts, selectedPostFilter) {
        when (selectedPostFilter) {
            GroupPostFilter.All -> sortedPosts
            GroupPostFilter.Discussions -> sortedPosts.filter { post -> post.type == "group_post" }
            GroupPostFilter.LostFound -> sortedPosts.filter { post -> post.type == "lost_found" }
            GroupPostFilter.OpenLostFound -> sortedPosts.filter { post ->
                post.type == "lost_found" && (post.alertStatus ?: "open") == "open"
            }
        }
    }
    val groupPostFilterCounts = remember(sortedPosts) {
        mapOf(
            GroupPostFilter.All to sortedPosts.size,
            GroupPostFilter.Discussions to sortedPosts.count { post -> post.type == "group_post" },
            GroupPostFilter.LostFound to sortedPosts.count { post -> post.type == "lost_found" },
            GroupPostFilter.OpenLostFound to sortedPosts.count { post ->
                post.type == "lost_found" && (post.alertStatus ?: "open") == "open"
            },
        )
    }
    val eventDiscussionPostById = remember(groupEvents, nearbyEvents, sortedPosts) {
        (groupEvents + nearbyEvents)
            .distinctBy { event -> event.id }
            .associate { event -> event.id to findDiscussionPostForEvent(event, sortedPosts) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(group.name, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "${group.memberCount} members • ${group.suburb}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = {},
                                label = { Text(groupMembershipLabel(group.membershipStatus)) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                            if (group.official) {
                                AssistChip(onClick = {}, label = { Text("Official") })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatPill(label = "Events", value = groupEvents.size.toString())
                            StatPill(label = "Posts", value = sortedPosts.size.toString())
                            StatPill(label = "Active pets", value = roster.size.toString())
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatPill(label = "Need RSVP", value = needsRsvpEvents.size.toString())
                            StatPill(label = "Going", value = goingEvents.size.toString())
                            StatPill(label = "Past", value = pastGroupEvents.size.toString())
                        }
                        if (nearbyEvents.isNotEmpty()) {
                            val nearbyLabel = if (nearbyEvents.size == 1) "1 event nearby" else "${nearbyEvents.size} events nearby"
                            AssistChip(
                                onClick = {
                                    if (nearbyEvents.size == 1) {
                                        onOpenEventDetails(nearbyEvents.first(), group.name)
                                    } else {
                                        selectedTabName = GroupDetailTab.Events.name
                                    }
                                },
                                label = { Text(nearbyLabel) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Event,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(GroupDetailTab.entries.toList(), key = { tab -> tab.name }) { tab ->
                        FilterChip(
                            selected = selectedTab == tab,
                            onClick = { selectedTabName = tab.name },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }

            when (selectedTab) {
                GroupDetailTab.About -> {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("About this group", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = buildGroupSummary(group, groupEvents.size, sortedPosts.size),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = "Co-op score ${group.cooperativeScore} • Pack ${group.myPackBuilderPoints} • Clean ${group.myCleanParkPoints}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (group.groupBadges.isNotEmpty()) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(group.groupBadges, key = { badge -> "${group.id}_$badge" }) { badge ->
                                            AssistChip(onClick = {}, label = { Text(badge) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text("Group basics", style = MaterialTheme.typography.titleSmall)
                                Text("1. Keep meetup times accurate so members can plan ahead.", style = MaterialTheme.typography.bodySmall)
                                Text("2. Add clear pet details in posts when asking for help.", style = MaterialTheme.typography.bodySmall)
                                Text("3. Use invite links for people who are active in this suburb.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                GroupDetailTab.Events -> {
                    if (upcomingGroupEvents.isEmpty() && pastGroupEvents.isEmpty() && nearbyEvents.isEmpty()) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                Text(
                                    text = "No events yet. Create one from Community to start gathering this group.",
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    } else {
                        if (needsRsvpEvents.isNotEmpty()) {
                            item { Text("Needs your RSVP", style = MaterialTheme.typography.titleSmall) }
                            items(needsRsvpEvents.take(8), key = { event -> "need_${event.id}" }) { event ->
                                GroupDetailEventCard(
                                    event = event,
                                    supportingTag = "Group event",
                                    loading = loading,
                                    onRsvpEvent = onRsvpEvent,
                                    isSaved = event.id in savedEventIds,
                                    onReportEvent = onReportEvent,
                                    onToggleSaveEvent = onToggleSaveEvent,
                                    onOpenMessages = onOpenMessages,
                                    onOpenEventDetails = onOpenEventDetails,
                                    linkedPost = eventDiscussionPostById[event.id],
                                    onOpenDiscussion = onOpenPost,
                                )
                            }
                        }

                        if (goingEvents.isNotEmpty()) {
                            item { Text("You're going", style = MaterialTheme.typography.titleSmall) }
                            items(goingEvents.take(8), key = { event -> "going_${event.id}" }) { event ->
                                GroupDetailEventCard(
                                    event = event,
                                    supportingTag = "Confirmed",
                                    loading = loading,
                                    onRsvpEvent = onRsvpEvent,
                                    isSaved = event.id in savedEventIds,
                                    onReportEvent = onReportEvent,
                                    onToggleSaveEvent = onToggleSaveEvent,
                                    onOpenMessages = onOpenMessages,
                                    onOpenEventDetails = onOpenEventDetails,
                                    linkedPost = eventDiscussionPostById[event.id],
                                    onOpenDiscussion = onOpenPost,
                                )
                            }
                        }

                        if (otherUpcomingEvents.isNotEmpty()) {
                            item { Text("Other upcoming", style = MaterialTheme.typography.titleSmall) }
                            items(otherUpcomingEvents.take(6), key = { event -> "other_${event.id}" }) { event ->
                                GroupDetailEventCard(
                                    event = event,
                                    supportingTag = "Pending approval",
                                    loading = loading,
                                    onRsvpEvent = onRsvpEvent,
                                    isSaved = event.id in savedEventIds,
                                    onReportEvent = onReportEvent,
                                    onToggleSaveEvent = onToggleSaveEvent,
                                    onOpenMessages = onOpenMessages,
                                    onOpenEventDetails = onOpenEventDetails,
                                    linkedPost = eventDiscussionPostById[event.id],
                                    onOpenDiscussion = onOpenPost,
                                )
                            }
                        }

                        if (nearbyEvents.isNotEmpty()) {
                            item { Text("Also happening in ${group.suburb}", style = MaterialTheme.typography.titleSmall) }
                            items(nearbyEvents.take(6), key = { event -> "near_${event.id}" }) { event ->
                                GroupDetailEventCard(
                                    event = event,
                                    supportingTag = "Nearby",
                                    loading = loading,
                                    onRsvpEvent = onRsvpEvent,
                                    isSaved = event.id in savedEventIds,
                                    onReportEvent = onReportEvent,
                                    onToggleSaveEvent = onToggleSaveEvent,
                                    onOpenMessages = onOpenMessages,
                                    onOpenEventDetails = onOpenEventDetails,
                                    linkedPost = eventDiscussionPostById[event.id],
                                    onOpenDiscussion = onOpenPost,
                                )
                            }
                        }

                        if (pastGroupEvents.isNotEmpty()) {
                            item { Text("Past events", style = MaterialTheme.typography.titleSmall) }
                            items(pastGroupEvents.take(6), key = { event -> "past_${event.id}" }) { event ->
                                GroupDetailEventCard(
                                    event = event,
                                    supportingTag = "Past",
                                    loading = loading,
                                    onRsvpEvent = onRsvpEvent,
                                    isSaved = event.id in savedEventIds,
                                    onReportEvent = onReportEvent,
                                    onToggleSaveEvent = onToggleSaveEvent,
                                    onOpenMessages = onOpenMessages,
                                    onOpenEventDetails = onOpenEventDetails,
                                    linkedPost = eventDiscussionPostById[event.id],
                                    onOpenDiscussion = onOpenPost,
                                )
                            }
                        }
                    }
                }

                GroupDetailTab.Members -> {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("Member activity", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${group.memberCount} total members • ${roster.size} recently active pets",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    if (roster.isNotEmpty()) {
                        item {
                            PetRosterShowcase(
                                title = "Recently active pets",
                                pets = roster,
                            )
                        }
                        item { Text("Recent check-ins", style = MaterialTheme.typography.titleSmall) }
                        items(
                            roster.sortedByDescending { item -> item.addedDate }.take(12),
                            key = { item -> item.id },
                        ) { item ->
                            GroupMemberActivityRow(item = item)
                        }
                    } else {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                Text(
                                    text = "No recent member check-ins yet. Ask members to share dog park updates.",
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }

                GroupDetailTab.Posts -> {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(GroupPostFilter.entries.toList(), key = { filter -> "group_post_filter_${filter.name}" }) { filter ->
                                FilterChip(
                                    selected = selectedPostFilter == filter,
                                    onClick = { selectedPostFilterName = filter.name },
                                    label = {
                                        Text(
                                            "${filter.label} ${groupPostFilterCounts[filter] ?: 0}",
                                        )
                                    },
                                )
                            }
                        }
                    }
                    if (filteredGroupPosts.isEmpty()) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                Text(
                                    text = when (selectedPostFilter) {
                                        GroupPostFilter.All -> "No posts yet for this area."
                                        GroupPostFilter.Discussions -> "No discussion posts yet."
                                        GroupPostFilter.LostFound -> "No lost/found posts yet."
                                        GroupPostFilter.OpenLostFound -> "No open lost/found posts right now."
                                    },
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    } else {
                        items(filteredGroupPosts.take(20), key = { post -> post.id }) { post ->
                            GroupDetailPostCard(
                                post = post,
                                onOpenPost = { onOpenPost(post) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupDetailEventCard(
    event: CommunityEvent,
    supportingTag: String,
    loading: Boolean,
    onRsvpEvent: (eventId: String, attending: Boolean) -> Unit,
    isSaved: Boolean,
    onReportEvent: (eventId: String, reason: String, details: String) -> Unit,
    onToggleSaveEvent: (eventId: String) -> Unit,
    onOpenMessages: (String?) -> Unit,
    onOpenEventDetails: (CommunityEvent, String?) -> Unit,
    linkedPost: CommunityPost?,
    onOpenDiscussion: (CommunityPost) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var copied by rememberSaveable(event.id) { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.clickable { onOpenEventDetails(event, null) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(event.title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = event.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${formatRelativeDay(event.date)} • ${formatIsoDateTime(event.date)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "$supportingTag • ${event.attendeeCount} going",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (event.status == "approved") {
                val isAttending = event.rsvpStatus == "attending"
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        enabled = !loading,
                        onClick = { onRsvpEvent(event.id, !isAttending) },
                    ) {
                        Text(if (isAttending) "Leave" else "RSVP")
                    }
                    if (isAttending) {
                        Text("You are going", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    enabled = !loading,
                    onClick = { onReportEvent(event.id, "Safety concern", "Reported from event card") },
                ) {
                    Icon(Icons.Default.Flag, contentDescription = "Report event")
                }
                IconButton(
                    enabled = !loading,
                    onClick = { onToggleSaveEvent(event.id) },
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Default.TurnedIn else Icons.Default.FavoriteBorder,
                        contentDescription = if (isSaved) "Saved" else "Save event",
                    )
                }
                IconButton(
                    enabled = !loading,
                    onClick = { onOpenEventDetails(event, "Following this event.") },
                ) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Comment")
                }
                if (event.createdBy.isNotBlank()) {
                    IconButton(
                        enabled = !loading,
                        onClick = { onOpenMessages(event.createdBy) },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Message organizer")
                    }
                }
            }
            MeetupRoutineActions(
                event = event,
                loading = loading,
            )
            TextButton(
                enabled = !loading,
                onClick = {
                    clipboard.setText(AnnotatedString(formatEventShareText(event, groupName = null)))
                    copied = true
                },
            ) {
                Text(if (copied) "Copied event details" else "Copy event details")
            }
        }
    }
}

@Composable
private fun GroupMemberActivityRow(item: PetRosterItem) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = item.photoUrl,
                contentDescription = "${item.petName} photo",
                modifier = Modifier.size(48.dp),
                contentScale = ContentScale.Crop,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.petName, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Checked in ${formatRosterDate(item.addedDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GroupDetailPostCard(
    post: CommunityPost,
    onOpenPost: () -> Unit,
) {
    val isLostFound = post.type == "lost_found"
    val alertStatus = post.alertStatus ?: if (isLostFound) "open" else null

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.clickable(onClick = onOpenPost),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(post.title, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = {}, label = { Text(post.type.replace("_", " ")) })
                if (isLostFound) {
                    AlertTypeChip(post.alertType ?: "lost")
                }
                if (isLostFound && alertStatus != null) {
                    AlertStatusChip(alertStatus)
                }
            }
            Text(
                post.body,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (post.photoUrls.isNotEmpty()) {
                AsyncImage(
                    model = post.photoUrls.first(),
                    contentDescription = "${post.title} photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentScale = ContentScale.Crop,
                )
            }
            Text(
                text = "${post.suburb} • ${formatIsoDateTime(post.createdAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun buildGroupSummary(
    group: Group,
    eventCount: Int,
    postCount: Int,
): String = buildString {
    append(group.name)
    append(" connects pet owners in ")
    append(group.suburb)
    append(" for dog meetups, neighborhood updates, and practical support.")
    append(" Right now it has ")
    append(eventCount)
    append(" related event")
    if (eventCount != 1) append("s")
    append(" and ")
    append(postCount)
    append(" local post")
    if (postCount != 1) append("s")
    append(".")
}

private fun groupMembershipLabel(status: String): String = when (status) {
    "member" -> "Joined"
    "pending" -> "Pending"
    else -> "Explore"
}

private fun formatRosterDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEE, d MMM"))

private fun parseCoordinateOrNull(raw: String): Double? = raw
    .trim()
    .takeIf { value -> value.isNotBlank() }
    ?.toDoubleOrNull()

private fun eventLocationLabel(event: CommunityEvent): String? {
    val hasLocation = event.locationLatitude != null && event.locationLongitude != null
    if (!hasLocation) return null
    return event.locationName?.takeIf { it.isNotBlank() } ?: "Map location"
}

private fun eventRecurrenceLabel(event: CommunityEvent): String? {
    val recurrence = event.recurrence.lowercase()
    val interval = event.recurrenceInterval.coerceAtLeast(1)
    return when (recurrence) {
        "daily" -> if (interval == 1) "Repeats daily" else "Repeats every $interval days"
        "weekly" -> if (interval == 1) "Repeats weekly" else "Repeats every $interval weeks"
        "monthly" -> if (interval == 1) "Repeats monthly" else "Repeats every $interval months"
        else -> null
    }
}

@Composable
private fun EventLocationAndRecurrenceFields(
    locationEnabled: Boolean,
    onLocationEnabledChange: (Boolean) -> Unit,
    locationName: String,
    onLocationNameChange: (String) -> Unit,
    locationLatitude: String,
    onLocationLatitudeChange: (String) -> Unit,
    locationLongitude: String,
    onLocationLongitudeChange: (String) -> Unit,
    currentLocationSuburb: String?,
    currentLatitude: Double?,
    currentLongitude: Double?,
    onUseCurrentLocation: (() -> Unit)? = null,
    recurrence: String,
    onRecurrenceChange: (String) -> Unit,
    recurrenceInterval: String,
    onRecurrenceIntervalChange: (String) -> Unit,
) {
    val parsedLat = parseCoordinateOrNull(locationLatitude)
    val parsedLng = parseCoordinateOrNull(locationLongitude)
    val selectedLatLng = remember(parsedLat, parsedLng) {
        if (parsedLat != null && parsedLng != null) LatLng(parsedLat, parsedLng) else null
    }
    val fallbackLatLng = remember(currentLatitude, currentLongitude) {
        if (currentLatitude != null && currentLongitude != null) LatLng(currentLatitude, currentLongitude) else null
    }
    val cameraPositionState = rememberCameraPositionState()
    val mapUiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false,
            compassEnabled = false,
        )
    }
    LaunchedEffect(selectedLatLng, fallbackLatLng) {
        val target = selectedLatLng ?: fallbackLatLng ?: LatLng(-37.8136, 144.9631)
        cameraPositionState.move(
            CameraUpdateFactory.newLatLngZoom(
                target,
                if (selectedLatLng != null) 15f else 12f,
            ),
        )
    }
    Text(
        "Location and recurrence",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FilterChip(
        selected = locationEnabled,
        onClick = { onLocationEnabledChange(!locationEnabled) },
        label = { Text(if (locationEnabled) "Map location enabled" else "Add map location") },
    )
    if (locationEnabled) {
        if (onUseCurrentLocation != null) {
            OutlinedButton(
                enabled = currentLatitude != null && currentLongitude != null,
                onClick = onUseCurrentLocation,
            ) {
                val label = currentLocationSuburb?.takeIf { it.isNotBlank() } ?: "current location"
                Text("Use $label")
            }
        }
        OutlinedTextField(
            value = locationName,
            onValueChange = onLocationNameChange,
            label = { Text("Location label (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Tap the map to choose the event location.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GoogleMap(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    cameraPositionState = cameraPositionState,
                    uiSettings = mapUiSettings,
                    onMapClick = { latLng ->
                        onLocationLatitudeChange(String.format(Locale.US, "%.6f", latLng.latitude))
                        onLocationLongitudeChange(String.format(Locale.US, "%.6f", latLng.longitude))
                    },
                ) {
                    if (selectedLatLng != null) {
                        val markerState = remember(selectedLatLng) { MarkerState(position = selectedLatLng) }
                        Marker(
                            state = markerState,
                            title = locationName.ifBlank { "Event location" },
                            snippet = currentLocationSuburb ?: "Selected location",
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (selectedLatLng != null) {
                            "Pinned: ${String.format(Locale.US, "%.4f", selectedLatLng.latitude)}, ${String.format(Locale.US, "%.4f", selectedLatLng.longitude)}"
                        } else {
                            "No location selected yet."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (selectedLatLng != null) {
                        TextButton(
                            onClick = {
                                onLocationLatitudeChange("")
                                onLocationLongitudeChange("")
                            },
                        ) {
                            Text("Clear pin")
                        }
                    }
                }
            }
        }
    }

    Text(
        "Recurrence",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(
            listOf(
                "none" to "One-off",
                "daily" to "Daily",
                "weekly" to "Weekly",
                "monthly" to "Monthly",
            ),
            key = { option -> "event_recur_${option.first}" },
        ) { (value, label) ->
            FilterChip(
                selected = recurrence == value,
                onClick = { onRecurrenceChange(value) },
                label = { Text(label) },
            )
        }
    }
    if (recurrence != "none") {
        OutlinedTextField(
            value = recurrenceInterval,
            onValueChange = onRecurrenceIntervalChange,
            label = { Text("Repeat interval (1-30)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun EventEditorDialog(
    event: CommunityEvent,
    loading: Boolean,
    currentLocationSuburb: String?,
    currentLatitude: Double?,
    currentLongitude: Double?,
    onDismiss: () -> Unit,
    onSubmit: (
        eventId: String,
        title: String,
        description: String,
        date: String,
        groupId: String?,
        locationName: String?,
        locationLatitude: Double?,
        locationLongitude: Double?,
        clearLocation: Boolean,
        recurrence: String,
        recurrenceInterval: Int,
    ) -> Unit,
) {
    var title by rememberSaveable(event.id) { mutableStateOf(event.title) }
    var description by rememberSaveable(event.id) { mutableStateOf(event.description) }
    var date by rememberSaveable(event.id) { mutableStateOf(event.date) }
    var groupId by rememberSaveable(event.id) { mutableStateOf(event.groupId.orEmpty()) }
    var locationEnabled by rememberSaveable(event.id) {
        mutableStateOf(event.locationLatitude != null && event.locationLongitude != null)
    }
    var locationName by rememberSaveable(event.id) { mutableStateOf(event.locationName.orEmpty()) }
    var locationLatitude by rememberSaveable(event.id) { mutableStateOf(event.locationLatitude?.toString().orEmpty()) }
    var locationLongitude by rememberSaveable(event.id) { mutableStateOf(event.locationLongitude?.toString().orEmpty()) }
    var recurrence by rememberSaveable(event.id) { mutableStateOf(event.recurrence.lowercase()) }
    var recurrenceInterval by rememberSaveable(event.id) { mutableStateOf(event.recurrenceInterval.toString()) }

    val parsedDate = parseIsoInstant(date.trim())
    val parsedLat = parseCoordinateOrNull(locationLatitude)
    val parsedLng = parseCoordinateOrNull(locationLongitude)
    val locationValid = !locationEnabled || (parsedLat != null && parsedLng != null)
    val recurrenceIntervalInt = recurrenceInterval.trim().toIntOrNull()
    val recurrenceValid = recurrence == "none" || (recurrenceIntervalInt != null && recurrenceIntervalInt in 1..30)
    val canSubmit = title.trim().isNotBlank() &&
        description.trim().isNotBlank() &&
        parsedDate != null &&
        locationValid &&
        recurrenceValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit event") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Event title") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Event description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (ISO, e.g. 2026-02-28T10:00:00Z)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (parsedDate == null) {
                    Text(
                        "Use a valid ISO datetime with timezone.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    value = groupId,
                    onValueChange = { groupId = it },
                    label = { Text("Group ID (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                EventLocationAndRecurrenceFields(
                    locationEnabled = locationEnabled,
                    onLocationEnabledChange = { locationEnabled = it },
                    locationName = locationName,
                    onLocationNameChange = { locationName = it },
                    locationLatitude = locationLatitude,
                    onLocationLatitudeChange = { locationLatitude = it },
                    locationLongitude = locationLongitude,
                    onLocationLongitudeChange = { locationLongitude = it },
                    currentLocationSuburb = currentLocationSuburb,
                    currentLatitude = currentLatitude,
                    currentLongitude = currentLongitude,
                    onUseCurrentLocation = {
                        locationEnabled = true
                        locationName = currentLocationSuburb.orEmpty()
                        locationLatitude = currentLatitude?.let { value ->
                            String.format(Locale.US, "%.6f", value)
                        }.orEmpty()
                        locationLongitude = currentLongitude?.let { value ->
                            String.format(Locale.US, "%.6f", value)
                        }.orEmpty()
                    },
                    recurrence = recurrence,
                    onRecurrenceChange = { recurrence = it },
                    recurrenceInterval = recurrenceInterval,
                    onRecurrenceIntervalChange = { recurrenceInterval = it },
                )
                if (!locationValid) {
                    Text(
                        "Pick an event location on the map.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (!recurrenceValid) {
                    Text(
                        "Recurring events need an interval between 1 and 30.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !loading && canSubmit,
                onClick = {
                    val hadLocation = event.locationLatitude != null && event.locationLongitude != null
                    onSubmit(
                        event.id,
                        title.trim(),
                        description.trim(),
                        date.trim(),
                        groupId.trim().ifBlank { null },
                        if (locationEnabled) locationName.trim().ifBlank { null } else null,
                        if (locationEnabled) parsedLat else null,
                        if (locationEnabled) parsedLng else null,
                        !locationEnabled && hadLocation,
                        recurrence,
                        if (recurrence == "none") 1 else (recurrenceIntervalInt ?: 1),
                    )
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(enabled = !loading, onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun EventDetailSheet(
    event: CommunityEvent,
    activeUserId: String,
    loading: Boolean,
    initialComment: String,
    isSaved: Boolean,
    onDismiss: () -> Unit,
    onRsvpEvent: (eventId: String, attending: Boolean) -> Unit,
    onReportEvent: (eventId: String) -> Unit,
    onToggleSaveEvent: (eventId: String) -> Unit,
    onMessageOrganizer: () -> Unit,
    onOpenCalendar: (CommunityEvent) -> Unit,
    onEditEvent: (CommunityEvent) -> Unit,
) {
    val context = LocalContext.current
    var commentInput by rememberSaveable(event.id, initialComment) { mutableStateOf(initialComment) }
    val mapLatLng = remember(event.id, event.locationLatitude, event.locationLongitude) {
        event.locationLatitude?.let { lat -> event.locationLongitude?.let { lng -> LatLng(lat, lng) } }
    }
    val cameraPositionState = rememberCameraPositionState()
    val mapUiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false,
            compassEnabled = false,
        )
    }
    LaunchedEffect(mapLatLng) {
        mapLatLng?.let { latLng ->
            cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(event.title, style = MaterialTheme.typography.titleLarge)
            Text(
                "${formatRelativeDay(event.date)} • ${formatIsoDateTime(event.date)} • ${event.suburb}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                eventRecurrenceLabel(event)?.let { label ->
                    AssistChip(
                        onClick = {},
                        label = { Text(label) },
                        leadingIcon = { Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
                eventLocationLabel(event)?.let { label ->
                    AssistChip(
                        onClick = {},
                        label = { Text(label) },
                        leadingIcon = { Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
            }
            Text(event.description, style = MaterialTheme.typography.bodyMedium)
            if (mapLatLng != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = event.locationName ?: "Event map location",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        GoogleMap(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            cameraPositionState = cameraPositionState,
                            uiSettings = mapUiSettings,
                        ) {
                            val eventMarkerState = remember(mapLatLng) { MarkerState(position = mapLatLng) }
                            Marker(
                                state = eventMarkerState,
                                title = event.title,
                                snippet = event.locationName ?: event.suburb,
                            )
                        }
                        TextButton(
                            enabled = !loading,
                            onClick = {
                                val query = "${mapLatLng.latitude},${mapLatLng.longitude}"
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("geo:$query?q=$query"),
                                )
                                context.startActivity(intent)
                            },
                        ) {
                            Text("Open in maps")
                        }
                    }
                }
            }
            OutlinedTextField(
                value = commentInput,
                onValueChange = { commentInput = it },
                label = { Text("Comment") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                enabled = !loading,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = !loading, onClick = { onReportEvent(event.id) }) {
                    Icon(Icons.Default.Flag, contentDescription = "Report event")
                }
                IconButton(enabled = !loading, onClick = { onToggleSaveEvent(event.id) }) {
                    Icon(
                        imageVector = if (isSaved) Icons.Default.TurnedIn else Icons.Default.FavoriteBorder,
                        contentDescription = "Save event",
                    )
                }
                IconButton(enabled = !loading, onClick = onMessageOrganizer) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Message organizer")
                }
                IconButton(enabled = !loading, onClick = { onOpenCalendar(event) }) {
                    Icon(Icons.Default.Event, contentDescription = "Add to calendar")
                }
                if (event.createdBy == activeUserId) {
                    IconButton(enabled = !loading, onClick = { onEditEvent(event) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit event")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val isAttending = event.rsvpStatus == "attending"
                Button(
                    enabled = !loading && event.status == "approved",
                    onClick = { onRsvpEvent(event.id, !isAttending) },
                ) {
                    Text(if (isAttending) "Leave" else "RSVP")
                }
                OutlinedButton(onClick = onDismiss, enabled = !loading) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PostDetailSheet(
    post: CommunityPost,
    activeUserId: String,
    comments: List<CommunityComment>,
    commentsLoading: Boolean,
    loading: Boolean,
    canModerateComments: Boolean,
    onRefreshComments: () -> Unit,
    onCreateComment: (body: String, parentCommentId: String?) -> Unit,
    onModerateComment: (commentId: String, action: String) -> Unit,
    isSaved: Boolean,
    onReportPost: (postId: String, reason: String, details: String) -> Unit,
    onBlockUser: (targetUserId: String) -> Unit,
    onDeletePost: (postId: String) -> Unit,
    onToggleSavePost: (postId: String) -> Unit,
    onResolveLostFound: (postId: String, status: String, note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var commentInput by rememberSaveable(post.id) { mutableStateOf("") }
    var selectedReplyPreset by rememberSaveable(post.id) { mutableStateOf("") }
    var replyParentCommentId by rememberSaveable(post.id) { mutableStateOf<String?>(null) }
    var replyParentPreview by rememberSaveable(post.id) { mutableStateOf("") }
    var resolveNote by rememberSaveable(post.id) { mutableStateOf("") }
    var supported by rememberSaveable(post.id) { mutableStateOf(false) }
    var shareFeedback by rememberSaveable(post.id) { mutableStateOf("") }
    val isLostFound = post.type == "lost_found"
    val isSharePoint = post.type == "share_point"
    val isSharePointOwner = isSharePoint && post.createdBy == activeUserId
    val isSharePointExpired = parseIsoInstant(post.expiresAt)?.isBefore(Instant.now()) == true
    val alertStatus = post.alertStatus ?: if (isLostFound) "open" else null
    val sharePointLatLng = remember(post.id, post.latitude, post.longitude) {
        post.latitude?.let { lat -> post.longitude?.let { lng -> LatLng(lat, lng) } }
    }
    val sharePointCameraPositionState = rememberCameraPositionState()
    val sharePointMapUiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false,
            compassEnabled = false,
        )
    }
    val urgency = remember(post.id, post.alertStatus, post.followUpDueAt, post.expiresAt) {
        computeLostFoundUrgency(post)
    }
    val visibleComments = remember(comments) { comments.sortedBy { comment -> comment.createdAt } }
    LaunchedEffect(sharePointLatLng) {
        sharePointLatLng?.let { latLng ->
            sharePointCameraPositionState.move(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Author",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Column {
                        Text(
                            when {
                                isLostFound -> "Lost & Found board"
                                isSharePoint -> "Manual location share"
                                else -> "Community Member"
                            },
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            formatIsoDateTime(post.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(post.title, style = MaterialTheme.typography.titleLarge)
                        Text(post.body, style = MaterialTheme.typography.bodyLarge)
                        if (post.photoUrls.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(post.photoUrls) { photoUrl ->
                                    AsyncImage(
                                        model = photoUrl,
                                        contentDescription = "${post.title} photo",
                                        modifier = Modifier.size(width = 180.dp, height = 140.dp),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            }
                            Text(
                                text = "${post.photoUrls.size} shared photo${if (post.photoUrls.size > 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = {}, label = { Text(post.suburb) })
                            FilterChip(
                                selected = true,
                                onClick = {},
                                label = { Text(post.type.replace("_", " ")) },
                            )
                            if (isLostFound && alertStatus != null) {
                                AlertStatusChip(alertStatus)
                            }
                            if (urgency != null) {
                                LostFoundUrgencyChip(urgency)
                            }
                            if (isSaved) {
                                AssistChip(onClick = {}, label = { Text("Saved") })
                            }
                        }
                        if (isLostFound) {
                            post.alertType?.let { alertType ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Type:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    AlertTypeChip(alertType)
                                }
                            }
                            post.petName?.let { petName ->
                                Text(
                                    text = "Pet: $petName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            post.petTraits?.let { petTraits ->
                                Text(
                                    text = "Traits: $petTraits",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            post.lastSeenLocation?.let { location ->
                                Text(
                                    text = "Last seen location: $location",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            post.lastSeenAt?.let { lastSeenAt ->
                                Text(
                                    text = "Last seen time: ${formatIsoDateTime(lastSeenAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            post.contactPref?.let { contact ->
                                Text(
                                    text = "Contact: $contact",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (urgency != null) {
                                Text(
                                    text = "Urgency: ${urgency.label}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = when (urgency.level) {
                                        LostFoundUrgencyLevel.Critical -> MaterialTheme.colorScheme.error
                                        LostFoundUrgencyLevel.Warning -> MaterialTheme.colorScheme.tertiary
                                    },
                                )
                            }
                            if (alertStatus != null && alertStatus != "open") {
                                Text(
                                    text = buildString {
                                        append("Resolved: ${formatAlertStatus(alertStatus)}")
                                        post.resolvedAt?.let { append(" • ${formatIsoDateTime(it)}") }
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                post.resolvedNote?.takeIf { it.isNotBlank() }?.let { note ->
                                    Text(
                                        text = "Resolution note: $note",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            val timeline = buildLostFoundTimeline(post)
                            if (timeline.isNotEmpty()) {
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text("Activity timeline", style = MaterialTheme.typography.titleSmall)
                                        timeline.forEach { entry ->
                                            Text(
                                                text = "${entry.first}: ${entry.second}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                            val sightings = buildRecentSightingHints(post)
                            if (sightings.isNotEmpty()) {
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text("Recent sightings", style = MaterialTheme.typography.titleSmall)
                                        sightings.forEach { sighting ->
                                            Text(
                                                text = "• $sighting",
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (isSharePoint) {
                            Text(
                                text = "Visible to: ${sharePointAudienceLabel(post)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Precision: ${sharePointPrecisionLabel(post)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            sharePointLocationLabel(post)?.let { label ->
                                Text(
                                    text = "Location: $label",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            sharePointTimingSummary(post)?.let { summary ->
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (sharePointLatLng != null) {
                                GoogleMap(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp),
                                    cameraPositionState = sharePointCameraPositionState,
                                    uiSettings = sharePointMapUiSettings,
                                ) {
                                    val sharePointMarkerState =
                                        remember(sharePointLatLng) { MarkerState(position = sharePointLatLng) }
                                    Marker(
                                        state = sharePointMarkerState,
                                        title = post.title,
                                        snippet = post.lastSeenLocation ?: post.suburb,
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        val query = "${sharePointLatLng.latitude},${sharePointLatLng.longitude}"
                                        val intent = Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("geo:$query?q=$query"),
                                        )
                                        context.startActivity(intent)
                                    },
                                ) {
                                    Text("Open in maps")
                                }
                            }
                            if (isSharePointOwner && !isSharePointExpired) {
                                OutlinedButton(
                                    enabled = !loading,
                                    onClick = {
                                        onDeletePost(post.id)
                                        onDismiss()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Stop sharing now")
                                }
                            }
                        }
                    }
                }
            }

            if (isLostFound && alertStatus == "open") {
                item {
                    val resolveStatus = if (post.alertType == "found") "owner_found" else "reunited"
                    val resolveLabel = if (post.alertType == "found") "Mark owner found" else "Mark reunited"
                    val resolveHint = if (post.alertType == "found") {
                        "Use this when the owner has been reached."
                    } else {
                        "Use this when the pet is safely back home."
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Resolve this post",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = resolveHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = resolveNote,
                            onValueChange = { resolveNote = it },
                            label = { Text("Resolution note (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 3,
                        )
                        Button(
                            enabled = !loading,
                            onClick = { onResolveLostFound(post.id, resolveStatus, resolveNote.trim()) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(resolveLabel)
                        }
                        OutlinedButton(
                            enabled = !loading,
                            onClick = { onResolveLostFound(post.id, "expired", resolveNote.trim()) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Mark no longer active")
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { supported = !supported },
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FavoriteBorder, contentDescription = "Support")
                            Text(if (supported) "Supported" else "Support")
                        }
                    }
                    TextButton(
                        onClick = {
                            if (commentInput.isBlank()) {
                                commentInput = if (isLostFound) "I can help check nearby." else "Following this thread."
                                selectedReplyPreset = commentInput
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Comment")
                            Text("Comment")
                        }
                    }
                    TextButton(
                        onClick = {
                            val shareText = buildString {
                                append(post.title)
                                append(" • ")
                                append(post.suburb)
                                append(" • ")
                                append(if (isLostFound) "Lost & Found post" else "Community discussion")
                            }
                            clipboard.setText(AnnotatedString(shareText))
                            shareFeedback = "Copied"
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.IosShare, contentDescription = "Share")
                            Text(if (shareFeedback.isNotBlank()) shareFeedback else "Share")
                        }
                    }
                    TextButton(
                        onClick = { onToggleSavePost(post.id) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TurnedInNot, contentDescription = "Save")
                            Text(if (isSaved) "Saved" else "Save")
                        }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Safety actions",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "Use this if the post seems unsafe or abusive.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                enabled = !loading,
                                onClick = {
                                    onReportPost(
                                        post.id,
                                        if (post.type == "lost_found") "Suspicious lost and found post" else "Harassment or abuse",
                                        "Reported from post details sheet",
                                    )
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                ) {
                                    Text("Report post")
                                }
                            OutlinedButton(
                                enabled = !loading,
                                onClick = {
                                    onReportPost(
                                        post.id,
                                        REPORT_REASON_CHILD_SAFETY,
                                        "Reported from post details sheet",
                                    )
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Report child safety")
                            }
                            OutlinedButton(
                                enabled = !loading,
                                onClick = { onToggleSavePost(post.id) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(if (isSaved) "Unsave post" else "Save post")
                            }
                            val authorId = post.createdBy?.takeIf { it.isNotBlank() }
                            if (authorId != null) {
                                OutlinedButton(
                                    enabled = !loading,
                                    onClick = {
                                        onBlockUser(authorId)
                                        onDismiss()
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Block author")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Comments (${visibleComments.size})", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onRefreshComments, enabled = !commentsLoading) {
                        Text(if (commentsLoading) "Loading..." else "Refresh")
                    }
                }
            }

            if (visibleComments.isEmpty() && !commentsLoading) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Text(
                            text = "No comments yet. Start the conversation.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            }

            items(visibleComments, key = { comment -> comment.id }) { comment ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "${comment.userId} • ${formatIsoDateTime(comment.createdAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = if (comment.status == "removed_by_moderator") "[removed by moderator]" else comment.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (comment.status == "removed_by_moderator") {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (comment.status == "active") {
                                TextButton(
                                    onClick = {
                                        replyParentCommentId = comment.id
                                        replyParentPreview = comment.body.take(80)
                                        commentInput = "@${comment.userId} "
                                    },
                                ) {
                                    Text("Reply")
                                }
                            }
                            if (canModerateComments) {
                                val nextAction = if (comment.status == "active") "remove" else "restore"
                                TextButton(
                                    onClick = { onModerateComment(comment.id, nextAction) },
                                    enabled = !loading,
                                ) {
                                    Text(if (nextAction == "remove") "Remove" else "Restore")
                                }
                            }
                        }
                    }
                }
            }

            item {
                val presets = if (isLostFound) {
                    listOf("I can check nearby", "I saw a similar dog", "Shared to my group")
                } else {
                    listOf("Count me in", "Can help with setup", "Following this thread")
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets) { preset ->
                        FilterChip(
                            selected = selectedReplyPreset == preset,
                            onClick = {
                                selectedReplyPreset = if (selectedReplyPreset == preset) "" else preset
                                commentInput = preset
                            },
                            label = { Text(preset) },
                        )
                    }
                }
            }

            item {
                if (replyParentCommentId != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Replying to: $replyParentPreview",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = {
                                    replyParentCommentId = null
                                    replyParentPreview = ""
                                },
                            ) {
                                Text("Clear")
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = commentInput,
                    onValueChange = { commentInput = it },
                    label = { Text("Write a comment") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Button(
                    enabled = commentInput.isNotBlank() && !commentsLoading,
                    onClick = {
                        onCreateComment(commentInput.trim(), replyParentCommentId)
                        commentInput = ""
                        selectedReplyPreset = ""
                        replyParentCommentId = null
                        replyParentPreview = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Post comment")
                }
            }
        }
    }
}

private fun buildSeedComments(post: CommunityPost): List<String> {
    if (post.type == "lost_found") {
        val status = post.alertStatus ?: "open"
        val petLabel = post.petName ?: "this pup"
        return listOf(
            "Shared to nearby groups so more people can keep an eye out.",
            "I am walking through ${post.suburb} later and will check this area.",
            if (status == "open") "Hope $petLabel gets home safely soon." else "Great update, glad this has been resolved.",
        )
    }

    val lowerTitle = post.title.lowercase()
    val lowerBody = post.body.lowercase()
    val threadText = "$lowerTitle $lowerBody"

    if ("annika" in threadText) {
        return listOf(
            "Annika energy is undefeated. Absolute goofball in every frame.",
            "Love that she stayed friendly with every dog, even during tense moments.",
            "Please keep posting Annika threads. They are the best read in this group.",
        )
    }
    if ("snowy" in threadText) {
        return listOf(
            "Snowy is getting braver every week, great to see the confidence climb.",
            "Reminder to keep wider chase spacing because he still thinks he is tiny.",
            "Handled well after that accidental trample moment, nice calm reset.",
        )
    }
    if ("sesame" in threadText && "buddy" in threadText) {
        return listOf(
            "Sesame and Buddy need fast toy separation, otherwise it escalates every time.",
            "Fetch lane looked sharp until that ball dispute kicked off.",
            "Good intervention timing by handlers before it got worse.",
        )
    }
    if ("sesame" in threadText) {
        return listOf(
            "Sesame's chase drive is wild, those ball sprints are elite.",
            "Please keep clear ball ownership boundaries when she is in peak mode.",
            "Great focus session with quick decompression breaks.",
        )
    }
    if ("buddy" in threadText) {
        return listOf(
            "Buddy looked social and playful outside the toy conflict loop.",
            "He and Sesame still need stricter separation when toys are involved.",
            "Strong recovery after the second reset block.",
        )
    }
    if ("pepsi" in threadText) {
        return listOf(
            "Pepsi started intense but settled well once introductions slowed down.",
            "The wary-around-men note is real, thanks for handling it thoughtfully.",
            "Rough player, but much better regulation in this report.",
        )
    }
    if ("billie" in threadText) {
        return listOf(
            "Billie is still pure love. Senior legend behavior.",
            "She paced herself well and still joined the fun rounds.",
            "The whole group looked calmer once Billie started social check-ins.",
        )
    }
    if ("spicy" in threadText || "escalated" in threadText || "argument" in threadText) {
        return listOf(
            "This one likely needs moderation follow-up before the next session.",
            "Please keep replies factual so admins can review cleanly.",
            "Hope everyone resets and returns to safer play standards next meetup.",
        )
    }

    val contextualThird = when {
        "live" in lowerTitle || "happening now" in lowerBody ->
            "On my way with spare water bowls and treats."
        "cleanup" in lowerTitle || "cleanup" in lowerBody ->
            "Count me in for the next cleanup round."
        "then vs now" in lowerTitle ->
            "Love seeing this progress story, keep posting these updates."
        post.photoUrls.size >= 3 ->
            "Photo two is excellent, every dog looks so happy."
        else ->
            "Thanks for organising this and sharing it with the crew."
    }

    return listOf(
        "This made my morning. The dogs look like they had the best time.",
        "Invited two neighbours to join this group after seeing this post.",
        contextualThird,
    )
}

private fun buildLostFoundTimeline(post: CommunityPost): List<Pair<String, String>> {
    if (post.type != "lost_found") return emptyList()
    val entries = mutableListOf<Pair<String, String>>()
    post.createdAt?.let { createdAt ->
        entries += "Posted" to formatIsoDateTime(createdAt)
    }
    post.lastSeenAt?.let { lastSeenAt ->
        entries += "Last seen" to formatIsoDateTime(lastSeenAt)
    }
    post.resolvedAt?.let { resolvedAt ->
        entries += "Resolved" to formatIsoDateTime(resolvedAt)
    }
    post.expiresAt?.let { expiresAt ->
        entries += "Auto-expire" to formatIsoDateTime(expiresAt)
    }
    return entries
}

private fun buildRecentSightingHints(post: CommunityPost): List<String> {
    if (post.type != "lost_found") return emptyList()
    if ((post.alertStatus ?: "open") != "open") return emptyList()
    val base = post.lastSeenLocation ?: post.suburb
    val petLabel = post.petName ?: "this dog"
    return listOf(
        "$petLabel may have been seen near $base about 1h ago.",
        "Another member is checking nearby parks in ${post.suburb}.",
    )
}
