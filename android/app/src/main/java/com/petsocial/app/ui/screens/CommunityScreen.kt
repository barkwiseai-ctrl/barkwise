package com.petsocial.app.ui.screens

import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.petsocial.app.data.CommunityEvent
import com.petsocial.app.data.CommunityPost
import com.petsocial.app.data.CommunityPostCreate
import com.petsocial.app.data.Group
import com.petsocial.app.data.GroupInvite
import com.petsocial.app.ui.CommunityWeatherSnapshot
import com.petsocial.app.ui.PetRosterItem
import com.petsocial.app.ui.components.PetRosterShowcase
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    loading: Boolean,
    suburb: String,
    postsSortBy: String,
    communityOpenOnly: Boolean,
    communityRecentHours: Int?,
    selectedGroupId: String?,
    groups: List<Group>,
    groupPetRosters: Map<String, List<PetRosterItem>>,
    latestGroupInvites: Map<String, GroupInvite>,
    blockedUserIds: List<String>,
    savedPostIds: Set<String>,
    mutedKeywords: Set<String>,
    followedGroupIds: Set<String>,
    communityWeather: CommunityWeatherSnapshot,
    autoParkCheckInEnabled: Boolean,
    autoParkCheckInRequireCrowd: Boolean,
    autoParkCheckInQuorumCount: Int,
    autoParkCheckInQuorumThreshold: Int,
    autoParkCheckInQuorumWindowMinutes: Int,
    posts: List<CommunityPost>,
    events: List<CommunityEvent>,
    unreadNotificationCount: Int,
    onOpenGroup: (String) -> Unit,
    onOpenNotifications: (filter: String) -> Unit,
    onDismissSelectedGroup: () -> Unit,
    onJoinGroup: (String) -> Unit,
    onCreateGroupInvite: (String) -> Unit,
    onClearGroupInvite: (String) -> Unit,
    onCreateGroup: (String) -> Unit,
    onPostsSortChange: (String) -> Unit,
    onCommunityFilterChange: (openOnly: Boolean, recentHours: Int?) -> Unit,
    onCreateGroupPost: (title: String, body: String, suburb: String) -> Unit,
    onCreateLostFound: (CommunityPostCreate) -> Unit,
    onCreateEvent: (title: String, description: String, date: String, groupId: String?) -> Unit,
    onRsvpEvent: (eventId: String, attending: Boolean) -> Unit,
    onApproveJoinRequest: (groupId: String) -> Unit,
    onRejectJoinRequest: (groupId: String) -> Unit,
    onApproveEvent: (eventId: String) -> Unit,
    onLogCleanupCheckIn: (groupId: String) -> Unit,
    onResolveLostFound: (postId: String, status: String, note: String) -> Unit,
    onReportPost: (postId: String, reason: String, details: String) -> Unit,
    onBlockUser: (targetUserId: String) -> Unit,
    onToggleSavePost: (postId: String) -> Unit,
    onSetMutedKeywords: (Set<String>) -> Unit,
    onToggleFollowGroup: (groupId: String) -> Unit,
    onRefreshWeather: () -> Unit,
    onSetAutoParkCheckInEnabled: (Boolean) -> Unit,
    onSetAutoParkCheckInRequireCrowd: (Boolean) -> Unit,
    onSimulateParkArrival: () -> Unit,
) {
    var showCreatePostDialog by rememberSaveable { mutableStateOf(false) }
    var createPostType by rememberSaveable { mutableStateOf("group_post") }
    var createPostTitle by rememberSaveable { mutableStateOf("") }
    var createPostBody by rememberSaveable { mutableStateOf("") }
    var createEventDate by rememberSaveable { mutableStateOf("2026-02-28T10:00:00Z") }
    var createEventGroupId by rememberSaveable { mutableStateOf("") }
    var createLostFoundAlertType by rememberSaveable { mutableStateOf("lost") }
    var createLostFoundPetName by rememberSaveable { mutableStateOf("") }
    var createLostFoundPetTraits by rememberSaveable { mutableStateOf("") }
    var createLostFoundLastSeenLocation by rememberSaveable { mutableStateOf("") }
    var createLostFoundLastSeenAt by rememberSaveable { mutableStateOf("") }
    var createLostFoundContactPref by rememberSaveable { mutableStateOf("") }
    var createLostFoundPhotoUrls by rememberSaveable { mutableStateOf("") }
    var groupQuery by rememberSaveable { mutableStateOf("") }
    var selectedGroupFilter by rememberSaveable { mutableStateOf(GroupDiscoveryFilter.ForYou) }
    var createGroupName by rememberSaveable { mutableStateOf("") }
    var selectedPost by remember { mutableStateOf<CommunityPost?>(null) }
    var selectedLens by rememberSaveable { mutableStateOf(CommunityLens.Groups) }
    var showSavedOnly by rememberSaveable { mutableStateOf(false) }
    var selectedMeetupWindow by rememberSaveable { mutableStateOf(MeetupWindow.AllUpcoming) }
    var selectedMeetupArea by rememberSaveable { mutableStateOf(MeetupAreaFilter.Anywhere) }
    var suggestedJoinGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var suggestedJoinEventTitle by rememberSaveable { mutableStateOf("") }
    var mutedKeywordsInput by rememberSaveable { mutableStateOf(mutedKeywords.sorted().joinToString(", ")) }
    var showFeedSettingsSheet by rememberSaveable { mutableStateOf(false) }

    val listState = rememberLazyListState()

    val joinedGroups = remember(groups) { groups.filter { group -> group.membershipStatus == "member" } }
    val joinedGroupIds = remember(joinedGroups) { joinedGroups.map { group -> group.id }.toSet() }
    val eventById = remember(events) { events.associateBy { event -> event.id } }
    val groupById = remember(groups) { groups.associateBy { group -> group.id } }
    val matchingGroups = remember(groups, groupQuery, selectedGroupFilter, suburb) {
        filterGroupsForDiscovery(
            groups = groups,
            query = groupQuery,
            filter = selectedGroupFilter,
            suburb = suburb,
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
    val feedItems = remember(selectedLens, matchingGroups, visiblePosts, events) {
        buildCommunityFeed(
            lens = selectedLens,
            groups = matchingGroups,
            posts = visiblePosts,
            events = events,
        )
    }
    val groupNameById = remember(groups) { groups.associate { it.id to it.name } }
    val eventRelativeDayById = remember(events) {
        events.associate { event -> event.id to formatRelativeDay(event.date) }
    }
    val eventDateTimeById = remember(events) {
        events.associate { event -> event.id to formatIsoDateTime(event.date) }
    }
    val postCreatedAtLabelById = remember(visiblePosts) {
        visiblePosts.associate { post -> post.id to formatIsoDateTime(post.createdAt) }
    }
    val postCommentHintById = remember(visiblePosts) {
        visiblePosts.associate { post ->
            post.id to (6 + (((post.id.hashCode().toLong() and Long.MAX_VALUE) % 15L).toInt()))
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = { onOpenNotifications("community") },
                ) {
                    Text(if (unreadNotificationCount > 0) "Alerts ($unreadNotificationCount)" else "Alerts")
                }
            }
        }

        item {
            GroupDiscoveryCard(
                loading = loading,
                suburb = suburb,
                query = groupQuery,
                selectedFilter = selectedGroupFilter,
                matchingCount = matchingGroups.size,
                createGroupName = createGroupName,
                onQueryChange = { groupQuery = it },
                onSelectFilter = { selectedGroupFilter = it },
                onCreateGroupNameChange = { createGroupName = it },
                onCreateGroup = {
                    val cleanName = createGroupName.trim()
                    if (cleanName.length >= 4) {
                        onCreateGroup(cleanName)
                        createGroupName = ""
                    }
                },
            )
        }

        if (matchingGroups.isNotEmpty()) {
            item {
                Text("Groups", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(matchingGroups.take(10), key = { group -> group.id }) { group ->
                        GroupSnapshotCard(
                            group = group,
                            loading = loading,
                            onOpenGroup = onOpenGroup,
                            onJoinGroup = onJoinGroup,
                        )
                    }
                }
            }
        } else {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Text(
                        text = "No groups match your filters yet. Try another search or start a new group.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        item {
            MeetupPlannerCard(
                selectedWindow = selectedMeetupWindow,
                selectedArea = selectedMeetupArea,
                meetupEvents = focusedMeetupEvents,
                onSelectWindow = { selectedMeetupWindow = it },
                onSelectArea = { selectedMeetupArea = it },
                loading = loading,
                onRsvpEvent = handleRsvpEvent,
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Feed", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { showFeedSettingsSheet = true }) {
                        Text("Feed settings")
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CommunityLens.entries.toList(), key = { lens -> lens.name }) { lens ->
                        FilterChip(
                            selected = selectedLens == lens,
                            onClick = { selectedLens = lens },
                            label = { Text(lens.label) },
                        )
                    }
                }
                AnimatedVisibility(visible = selectedLens == CommunityLens.Discussions) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Sort discussions", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "relevance" to "Relevant",
                                "newest" to "Newest",
                                "lost_found" to "Lost/Found",
                            ).forEach { (key, label) ->
                                FilterChip(
                                    selected = postsSortBy == key,
                                    onClick = { onPostsSortChange(key) },
                                    label = { Text(label) },
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = communityOpenOnly,
                                onClick = { onCommunityFilterChange(!communityOpenOnly, communityRecentHours) },
                                label = { Text("Open alerts only") },
                            )
                            FilterChip(
                                selected = communityRecentHours == 24,
                                onClick = {
                                    val next = if (communityRecentHours == 24) null else 24
                                    onCommunityFilterChange(communityOpenOnly, next)
                                },
                                label = { Text("Last 24h") },
                            )
                            FilterChip(
                                selected = communityRecentHours == 72,
                                onClick = {
                                    val next = if (communityRecentHours == 72) null else 72
                                    onCommunityFilterChange(communityOpenOnly, next)
                                },
                                label = { Text("Last 3 days") },
                            )
                        }
                    }
                }
            }
        }

        if (feedItems.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "No community activity yet in $suburb. Join a group or check upcoming outings.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(
                            enabled = !loading,
                            onClick = {
                                createPostType = when (selectedLens) {
                                    CommunityLens.Events -> "community_event"
                                    CommunityLens.Discussions -> "group_post"
                                    else -> "group_post"
                                }
                                showCreatePostDialog = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                when (selectedLens) {
                                    CommunityLens.Events -> "Create event"
                                    CommunityLens.Discussions -> "Create discussion"
                                    CommunityLens.Groups -> "Open a group and post"
                                },
                            )
                        }
                    }
                }
            }
        } else {
            items(feedItems, key = { item -> item.stableId }) { item ->
                when (item) {
                    is CommunityFeedItem.EventItem -> {
                        EventFeedCard(
                            event = item.event,
                            groupId = item.event.groupId,
                            groupName = item.event.groupId?.let { groupId -> groupNameById[groupId] },
                            relativeDayLabel = eventRelativeDayById[item.event.id] ?: formatRelativeDay(item.event.date),
                            dateTimeLabel = eventDateTimeById[item.event.id] ?: formatIsoDateTime(item.event.date),
                            loading = loading,
                            onRsvpEvent = handleRsvpEvent,
                            onApproveEvent = onApproveEvent,
                            onOpenGroup = onOpenGroup,
                        )
                    }

                    is CommunityFeedItem.PostItem -> {
                        DiscussionFeedCard(
                            post = item.post,
                            createdAtLabel = postCreatedAtLabelById[item.post.id] ?: formatIsoDateTime(item.post.createdAt),
                            commentHint = postCommentHintById[item.post.id] ?: 8,
                            onOpenPost = { selectedPost = item.post },
                            isSaved = item.post.id in savedPostIds,
                            onQuickReport = {
                                onReportPost(
                                    item.post.id,
                                    if (item.post.type == "lost_found") "Suspicious alert" else "Harassment or abuse",
                                    "Reported from feed card",
                                )
                            },
                            onQuickBlock = {
                                item.post.createdBy
                                    ?.takeIf { userId -> userId.isNotBlank() }
                                    ?.let(onBlockUser)
                            },
                            onToggleSave = { onToggleSavePost(item.post.id) },
                        )
                    }

                    is CommunityFeedItem.GroupItem -> {
                        GroupFeedCard(
                            group = item.group,
                            isFollowed = item.group.id in followedGroupIds,
                            roster = groupPetRosters[item.group.id].orEmpty(),
                            latestInvite = latestGroupInvites[item.group.id],
                            loading = loading,
                            onOpenGroup = onOpenGroup,
                            onJoinGroup = onJoinGroup,
                            onCreateGroupInvite = onCreateGroupInvite,
                            onClearGroupInvite = onClearGroupInvite,
                            onApproveJoinRequest = onApproveJoinRequest,
                            onRejectJoinRequest = onRejectJoinRequest,
                            onToggleFollowGroup = onToggleFollowGroup,
                        )
                    }
                }
            }
        }
    }

    if (showFeedSettingsSheet) {
        ModalBottomSheet(onDismissRequest = { showFeedSettingsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
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

    selectedPost?.let { post ->
        PostDetailSheet(
            post = post,
            loading = loading,
            isSaved = post.id in savedPostIds,
            onReportPost = { postId, reason, details -> onReportPost(postId, reason, details) },
            onBlockUser = onBlockUser,
            onToggleSavePost = onToggleSavePost,
            onResolveLostFound = { postId, status, note ->
                onResolveLostFound(postId, status, note)
                selectedPost = null
            },
            onDismiss = { selectedPost = null },
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
                }
                .take(24),
            posts = posts
                .filter { post -> post.suburb.equals(group.suburb, ignoreCase = true) }
                .take(24),
            onRsvpEvent = handleRsvpEvent,
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
        AlertDialog(
            onDismissRequest = { showCreatePostDialog = false },
            title = { Text("Create post") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = createPostType == "group_post",
                            onClick = { createPostType = "group_post" },
                            label = { Text("Discussion") },
                        )
                        FilterChip(
                            selected = createPostType == "lost_found",
                            onClick = { createPostType = "lost_found" },
                            label = { Text("Lost/Found") },
                        )
                        FilterChip(
                            selected = createPostType == "community_event",
                            onClick = { createPostType = "community_event" },
                            label = { Text("Event") },
                        )
                    }
                    OutlinedTextField(
                        value = createPostTitle,
                        onValueChange = { createPostTitle = it },
                        label = {
                            Text(
                                when (createPostType) {
                                    "lost_found" -> "Alert title"
                                    "community_event" -> "Event title"
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
                        OutlinedTextField(
                            value = createEventDate,
                            onValueChange = { createEventDate = it },
                            label = { Text("Date (ISO, e.g. 2026-02-28T10:00:00Z)") },
                            modifier = Modifier.fillMaxWidth(),
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
                    }
                }
            },
            confirmButton = {
                val canSubmit = when (createPostType) {
                    "community_event" -> {
                        createPostTitle.isNotBlank() &&
                            createPostBody.isNotBlank() &&
                            createEventDate.isNotBlank() &&
                            parseIsoInstant(createEventDate.trim()) != null
                    }
                    "lost_found" -> {
                        createPostTitle.isNotBlank() &&
                            createPostBody.isNotBlank() &&
                            createLostFoundPetTraits.isNotBlank() &&
                            createLostFoundLastSeenLocation.isNotBlank() &&
                            createLostFoundContactPref.isNotBlank()
                    }
                    else -> {
                        createPostTitle.isNotBlank() && createPostBody.isNotBlank()
                    }
                }
                Button(
                    enabled = !loading && canSubmit,
                    onClick = {
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
                            )
                        } else {
                            onCreateGroupPost(createPostTitle.trim(), createPostBody.trim(), suburb)
                        }
                        createPostTitle = ""
                        createPostBody = ""
                        createEventDate = "2026-02-28T10:00:00Z"
                        createEventGroupId = ""
                        createLostFoundAlertType = "lost"
                        createLostFoundPetName = ""
                        createLostFoundPetTraits = ""
                        createLostFoundLastSeenLocation = ""
                        createLostFoundLastSeenAt = ""
                        createLostFoundContactPref = ""
                        createLostFoundPhotoUrls = ""
                        showCreatePostDialog = false
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
}

private enum class CommunityLens(val label: String) {
    Groups("Groups"),
    Discussions("Group activity"),
    Events("Outings"),
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

private enum class GroupDiscoveryFilter(val label: String) {
    ForYou("For you"),
    Joined("Joined"),
    Nearby("Nearby"),
    Official("Official"),
    Popular("Popular"),
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
    Alerts("Alerts"),
    OpenAlerts("Open alerts"),
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
    filter: GroupDiscoveryFilter,
    suburb: String,
): List<Group> {
    if (groups.isEmpty()) return emptyList()
    val normalizedQuery = query.trim().lowercase()
    return groups
        .asSequence()
        .filter { group ->
            when (filter) {
                GroupDiscoveryFilter.ForYou -> true
                GroupDiscoveryFilter.Joined -> group.membershipStatus == "member"
                GroupDiscoveryFilter.Nearby -> group.suburb.equals(suburb, ignoreCase = true)
                GroupDiscoveryFilter.Official -> group.official
                GroupDiscoveryFilter.Popular -> group.memberCount >= 20 || group.cooperativeScore >= 40
            }
        }
        .filter { group ->
            normalizedQuery.isBlank() ||
                group.name.contains(normalizedQuery, ignoreCase = true) ||
                group.suburb.contains(normalizedQuery, ignoreCase = true) ||
                group.groupBadges.any { badge -> badge.contains(normalizedQuery, ignoreCase = true) }
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

private sealed interface CommunityFeedItem {
    val stableId: String

    data class EventItem(val event: CommunityEvent) : CommunityFeedItem {
        override val stableId: String = "event_${event.id}"
    }

    data class PostItem(val post: CommunityPost) : CommunityFeedItem {
        override val stableId: String = "post_${post.id}"
    }

    data class GroupItem(val group: Group) : CommunityFeedItem {
        override val stableId: String = "group_${group.id}"
    }
}

private fun buildCommunityFeed(
    lens: CommunityLens,
    groups: List<Group>,
    posts: List<CommunityPost>,
    events: List<CommunityEvent>,
): List<CommunityFeedItem> {
    val sortedGroups = groups.sortedWith(
        compareByDescending<Group> { it.membershipStatus == "member" }
            .thenByDescending { it.official }
            .thenByDescending { it.memberCount },
    )
    val sortedEvents = sortEventsForCommunity(events)
    val now = Instant.now()
    val sortedPosts = posts.sortedWith(
        compareByDescending<CommunityPost> { post ->
            lostFoundPriorityScore(post = post, now = now)
        }.thenByDescending { post ->
            parseIsoInstant(post.createdAt) ?: Instant.EPOCH
        },
    )

    return when (lens) {
        CommunityLens.Groups -> sortedGroups.map { group -> CommunityFeedItem.GroupItem(group) }
        CommunityLens.Discussions -> sortedPosts.map { post -> CommunityFeedItem.PostItem(post) }
        CommunityLens.Events -> sortedEvents.map { event -> CommunityFeedItem.EventItem(event) }
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
private fun GroupDiscoveryCard(
    loading: Boolean,
    suburb: String,
    query: String,
    selectedFilter: GroupDiscoveryFilter,
    matchingCount: Int,
    createGroupName: String,
    onQueryChange: (String) -> Unit,
    onSelectFilter: (GroupDiscoveryFilter) -> Unit,
    onCreateGroupNameChange: (String) -> Unit,
    onCreateGroup: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Find your groups", style = MaterialTheme.typography.titleSmall)
            Text(
                "Discover active dog groups in and around $suburb.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("Search by name, suburb, or badge") },
                modifier = Modifier.fillMaxWidth(),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(GroupDiscoveryFilter.entries.toList(), key = { filter -> filter.name }) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { onSelectFilter(filter) },
                        label = { Text(filter.label) },
                    )
                }
            }
            Text(
                text = when (matchingCount) {
                    0 -> "No groups found"
                    1 -> "1 group found"
                    else -> "$matchingCount groups found"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = createGroupName,
                    onValueChange = onCreateGroupNameChange,
                    label = { Text("Start a new group") },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    enabled = !loading && createGroupName.trim().length >= 4,
                    onClick = onCreateGroup,
                ) {
                    Text("Create")
                }
            }
        }
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
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Dog community in $suburb", style = MaterialTheme.typography.titleLarge)
            Text(
                "Join one local dog park group for day-to-day posts and check-ins, then use outings when you want something new to do with your dog.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill(label = "Groups", value = totalGroups.toString())
                StatPill(label = "Outings", value = totalEvents.toString())
                StatPill(label = "Posts", value = totalDiscussions.toString())
            }
            if (joinedGroups > 0) {
                Text(
                    "$joinedGroups joined groups are ready for your next meetup.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
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
            Text("Live park weather (mock)", style = MaterialTheme.typography.titleSmall)
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
    var reminderEnabled by rememberSaveable(event.id) { mutableStateOf(false) }
    val cadence = remember(event.id, event.title, event.description) { inferMeetupCadence(event) }
    val cadenceLabel = cadence.label
    val reminderLabel = when (cadence) {
        MeetupCadence.Daily -> "Remind 2h before daily walk"
        MeetupCadence.Weekly -> "Remind 2h before weekly walk"
        MeetupCadence.Fortnightly -> "Remind 2h before fortnightly walk"
        MeetupCadence.OneOff -> "Remind me 2h before"
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AssistChip(onClick = {}, label = { Text(cadenceLabel) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                enabled = !loading,
                onClick = { reminderEnabled = !reminderEnabled },
                modifier = Modifier.weight(1f),
            ) {
                Text(if (reminderEnabled) "Reminder on" else reminderLabel)
            }
            TextButton(
                enabled = !loading,
                onClick = {
                    buildCalendarInsertIntent(event, cadence)?.let { intent ->
                        runCatching { context.startActivity(intent) }
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Add to calendar")
            }
        }
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
    onOpenPost: () -> Unit,
    isSaved: Boolean,
    onQuickReport: () -> Unit,
    onQuickBlock: () -> Unit,
    onToggleSave: () -> Unit,
) {
    val isLostFound = post.type == "lost_found"
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
                    text = if (isLostFound) "Local alert" else "Discussion",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
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
            Text(post.title, style = MaterialTheme.typography.titleMedium)
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
            Text(
                text = post.body,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${post.suburb} • $createdAtLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "$commentHint replies in thread",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onQuickReport) {
                    Text("Report")
                }
                TextButton(onClick = onToggleSave) {
                    Text(if (isSaved) "Unsave" else "Save")
                }
                if (!post.createdBy.isNullOrBlank()) {
                    TextButton(onClick = onQuickBlock) {
                        Text("Block")
                    }
                }
                TextButton(onClick = onOpenPost) {
                    Text("Open thread")
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
                    Text(if (isFollowed) "Unfollow alerts" else "Follow alerts")
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
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Invite link", style = MaterialTheme.typography.labelSmall)
                        Text(invite.inviteUrl, style = MaterialTheme.typography.bodySmall)
                        AsyncImage(
                            model = inviteQrImageUrl(invite.inviteUrl),
                            contentDescription = "Group invite QR code",
                            modifier = Modifier.size(100.dp),
                            contentScale = ContentScale.Crop,
                        )
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

private fun buildCalendarInsertIntent(
    event: CommunityEvent,
    cadence: MeetupCadence,
): Intent? {
    val start = parseIsoInstant(event.date) ?: return null
    val startMillis = start.toEpochMilli()
    val oneHourMs = 60L * 60L * 1000L
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
    return Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, event.title)
        putExtra(CalendarContract.Events.DESCRIPTION, "${event.description}\n\nBarkWise community dog walk")
        putExtra(CalendarContract.Events.EVENT_LOCATION, event.suburb)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startMillis + oneHourMs)
        recurrenceRule?.let { putExtra(CalendarContract.Events.RRULE, it) }
    }
}

private fun parseIsoInstant(raw: String?): Instant? {
    if (raw.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(raw).toInstant() }
        .recoverCatching { Instant.parse(raw) }
        .getOrNull()
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

private fun inviteQrImageUrl(inviteUrl: String): String {
    val encoded = URLEncoder.encode(inviteUrl, StandardCharsets.UTF_8.toString())
    return "https://quickchart.io/qr?size=220&text=$encoded"
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
            GroupPostFilter.Alerts -> sortedPosts.filter { post -> post.type == "lost_found" }
            GroupPostFilter.OpenAlerts -> sortedPosts.filter { post ->
                post.type == "lost_found" && (post.alertStatus ?: "open") == "open"
            }
        }
    }
    val groupPostFilterCounts = remember(sortedPosts) {
        mapOf(
            GroupPostFilter.All to sortedPosts.size,
            GroupPostFilter.Discussions to sortedPosts.count { post -> post.type == "group_post" },
            GroupPostFilter.Alerts to sortedPosts.count { post -> post.type == "lost_found" },
            GroupPostFilter.OpenAlerts to sortedPosts.count { post ->
                post.type == "lost_found" && (post.alertStatus ?: "open") == "open"
            },
        )
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
                                        GroupPostFilter.Alerts -> "No lost/found alerts yet."
                                        GroupPostFilter.OpenAlerts -> "No open lost/found alerts right now."
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
) {
    val clipboard = LocalClipboardManager.current
    var copied by rememberSaveable(event.id) { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
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

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
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
            TextButton(onClick = onOpenPost) {
                Text("Open thread")
            }
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PostDetailSheet(
    post: CommunityPost,
    loading: Boolean,
    isSaved: Boolean,
    onReportPost: (postId: String, reason: String, details: String) -> Unit,
    onBlockUser: (targetUserId: String) -> Unit,
    onToggleSavePost: (postId: String) -> Unit,
    onResolveLostFound: (postId: String, status: String, note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var commentInput by rememberSaveable(post.id) { mutableStateOf("") }
    var selectedReplyPreset by rememberSaveable(post.id) { mutableStateOf("") }
    var resolveNote by rememberSaveable(post.id) { mutableStateOf("") }
    var localComments by rememberSaveable(post.id) { mutableStateOf(emptyList<String>()) }
    var supported by rememberSaveable(post.id) { mutableStateOf(false) }
    var shareFeedback by rememberSaveable(post.id) { mutableStateOf("") }
    val isLostFound = post.type == "lost_found"
    val alertStatus = post.alertStatus ?: if (isLostFound) "open" else null
    val urgency = remember(post.id, post.alertStatus, post.followUpDueAt, post.expiresAt) {
        computeLostFoundUrgency(post)
    }

    val seedComments = remember(post.id, post.type, post.title, post.body, post.alertStatus) {
        buildSeedComments(post)
    }
    val visibleComments = remember(seedComments, localComments) { seedComments + localComments }

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
                            if (post.type == "lost_found") "Local Alert Board" else "Community Member",
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
                                        text = "Alert type:",
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
                                        Text("Alert timeline", style = MaterialTheme.typography.titleSmall)
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
                            text = "Resolve this alert",
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
                                val seed = if (isLostFound) "I can help check nearby." else "Following this thread."
                                commentInput = seed
                                selectedReplyPreset = seed
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
                                append(if (isLostFound) "Lost/Found alert" else "Community discussion")
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
                                        if (post.type == "lost_found") "Suspicious alert" else "Harassment or abuse",
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
                Text("Comments (${visibleComments.size})", style = MaterialTheme.typography.titleMedium)
            }

            items(visibleComments) { comment ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Text(
                        text = comment,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(10.dp),
                    )
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
                OutlinedTextField(
                    value = commentInput,
                    onValueChange = { commentInput = it },
                    label = { Text("Write a comment") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Button(
                    enabled = commentInput.isNotBlank(),
                    onClick = {
                        localComments = localComments + commentInput.trim()
                        commentInput = ""
                        selectedReplyPreset = ""
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
