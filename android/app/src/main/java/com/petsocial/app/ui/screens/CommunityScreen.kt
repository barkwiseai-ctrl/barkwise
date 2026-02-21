package com.petsocial.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.petsocial.app.data.CommunityEvent
import com.petsocial.app.data.CommunityPost
import com.petsocial.app.data.Group
import com.petsocial.app.data.GroupInvite
import com.petsocial.app.ui.PetRosterItem
import com.petsocial.app.ui.components.PetRosterShowcase
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun CommunityScreen(
    loading: Boolean,
    suburb: String,
    postsSortBy: String,
    selectedGroupId: String?,
    groups: List<Group>,
    groupPetRosters: Map<String, List<PetRosterItem>>,
    latestGroupInvites: Map<String, GroupInvite>,
    posts: List<CommunityPost>,
    events: List<CommunityEvent>,
    onOpenGroup: (String) -> Unit,
    onDismissSelectedGroup: () -> Unit,
    onJoinGroup: (String) -> Unit,
    onCreateGroupInvite: (String) -> Unit,
    onClearGroupInvite: (String) -> Unit,
    onPostsSortChange: (String) -> Unit,
    onCreateGroupPost: (title: String, body: String, suburb: String) -> Unit,
    onCreateLostFound: (title: String, body: String, suburb: String) -> Unit,
    onCreateEvent: (title: String, description: String, date: String, groupId: String?) -> Unit,
    onRsvpEvent: (eventId: String, attending: Boolean) -> Unit,
    onApproveJoinRequest: (groupId: String) -> Unit,
    onRejectJoinRequest: (groupId: String) -> Unit,
    onApproveEvent: (eventId: String) -> Unit,
    onLogCleanupCheckIn: (groupId: String) -> Unit,
) {
    var showThenVsNowFlow by rememberSaveable { mutableStateOf(false) }
    var showCreatePostDialog by rememberSaveable { mutableStateOf(false) }
    var createPostType by rememberSaveable { mutableStateOf("group_post") }
    var createPostTitle by rememberSaveable { mutableStateOf("") }
    var createPostBody by rememberSaveable { mutableStateOf("") }
    var createEventDate by rememberSaveable { mutableStateOf("2026-02-28T10:00:00Z") }
    var createEventGroupId by rememberSaveable { mutableStateOf("") }
    var selectedPost by remember { mutableStateOf<CommunityPost?>(null) }
    var selectedLens by rememberSaveable { mutableStateOf(CommunityLens.All) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    if (showThenVsNowFlow) {
        ThenVsNowFlow(onClose = { showThenVsNowFlow = false })
        return
    }

    val feedItems = remember(selectedLens, groups, posts, events) {
        buildCommunityFeed(
            lens = selectedLens,
            groups = groups,
            posts = posts,
            events = events,
        )
    }
    val groupNameById = remember(groups) { groups.associate { it.id to it.name } }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 90.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch { listState.animateScrollToItem(0) }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(suburb)
                }
                Button(
                    enabled = !loading,
                    onClick = { showCreatePostDialog = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Create")
                }
                OutlinedButton(
                    onClick = { showThenVsNowFlow = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Then vs Now")
                }
            }
        }

        item {
            MeetupHeroCard(
                suburb = suburb,
                totalGroups = groups.size,
                totalEvents = events.size,
                totalDiscussions = posts.size,
                joinedGroups = groups.count { group -> group.membershipStatus == "member" },
            )
        }

        item {
            Text("Browse", style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CommunityLens.entries.toList(), key = { lens -> lens.name }) { lens ->
                    FilterChip(
                        selected = selectedLens == lens,
                        onClick = { selectedLens = lens },
                        label = { Text(lens.label) },
                    )
                }
            }
        }

        item {
            AnimatedVisibility(visible = selectedLens == CommunityLens.All || selectedLens == CommunityLens.Discussions) {
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
                }
            }
        }

        if (groups.isNotEmpty()) {
            item {
                Text("Groups near $suburb", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(groups.take(10), key = { group -> group.id }) { group ->
                        GroupSnapshotCard(
                            group = group,
                            loading = loading,
                            onOpenGroup = onOpenGroup,
                            onJoinGroup = onJoinGroup,
                            onLogCleanupCheckIn = onLogCleanupCheckIn,
                        )
                    }
                }
            }
        }

        item {
            Text("What people are doing", style = MaterialTheme.typography.titleSmall)
            Text(
                when (selectedLens) {
                    CommunityLens.All -> "A blended stream of meetups, discussions, and neighborhood groups"
                    CommunityLens.Events -> "Upcoming meetups and local dog events"
                    CommunityLens.Discussions -> "Local conversations and alerts"
                    CommunityLens.Groups -> "Groups you can join right now"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (feedItems.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Text(
                        text = "No community activity yet in $suburb. Start with a post or create an event.",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            items(feedItems, key = { item -> item.stableId }) { item ->
                when (item) {
                    is CommunityFeedItem.EventItem -> {
                        EventFeedCard(
                            event = item.event,
                            groupName = item.event.groupId?.let { groupId -> groupNameById[groupId] },
                            loading = loading,
                            onRsvpEvent = onRsvpEvent,
                            onApproveEvent = onApproveEvent,
                        )
                    }

                    is CommunityFeedItem.PostItem -> {
                        DiscussionFeedCard(
                            post = item.post,
                            onOpenPost = { selectedPost = item.post },
                        )
                    }

                    is CommunityFeedItem.GroupItem -> {
                        GroupFeedCard(
                            group = item.group,
                            roster = groupPetRosters[item.group.id].orEmpty(),
                            latestInvite = latestGroupInvites[item.group.id],
                            loading = loading,
                            onOpenGroup = onOpenGroup,
                            onJoinGroup = onJoinGroup,
                            onCreateGroupInvite = onCreateGroupInvite,
                            onClearGroupInvite = onClearGroupInvite,
                            onApproveJoinRequest = onApproveJoinRequest,
                            onRejectJoinRequest = onRejectJoinRequest,
                            onLogCleanupCheckIn = onLogCleanupCheckIn,
                        )
                    }
                }
            }
        }
    }

    selectedPost?.let { post ->
        PostDetailSheet(
            post = post,
            onDismiss = { selectedPost = null },
        )
    }

    groups.firstOrNull { group -> group.id == selectedGroupId }?.let { group ->
        GroupDetailSheet(
            group = group,
            roster = groupPetRosters[group.id].orEmpty(),
            events = events.filter { event -> event.groupId == group.id },
            posts = posts.filter { post -> post.suburb.equals(group.suburb, ignoreCase = true) }.take(12),
            onDismiss = onDismissSelectedGroup,
        )
    }

    if (showCreatePostDialog) {
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
                        label = { Text(if (createPostType == "community_event") "Event description" else "Details") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                    )
                    if (createPostType == "community_event") {
                        OutlinedTextField(
                            value = createEventDate,
                            onValueChange = { createEventDate = it },
                            label = { Text("Date (ISO, e.g. 2026-02-28T10:00:00Z)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = createEventGroupId,
                            onValueChange = { createEventGroupId = it },
                            label = { Text("Group ID (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !loading &&
                        createPostTitle.isNotBlank() &&
                        createPostBody.isNotBlank() &&
                        (createPostType != "community_event" || createEventDate.isNotBlank()),
                    onClick = {
                        if (createPostType == "lost_found") {
                            onCreateLostFound(createPostTitle.trim(), createPostBody.trim(), suburb)
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
    All("All"),
    Events("Events"),
    Discussions("Discussions"),
    Groups("Groups"),
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
    val sortedEvents = events.sortedByDescending { event -> parseIsoInstant(event.date) ?: Instant.EPOCH }
    val sortedPosts = posts.sortedByDescending { post -> parseIsoInstant(post.createdAt) ?: Instant.EPOCH }

    return when (lens) {
        CommunityLens.Events -> sortedEvents.map { event -> CommunityFeedItem.EventItem(event) }
        CommunityLens.Discussions -> sortedPosts.map { post -> CommunityFeedItem.PostItem(post) }
        CommunityLens.Groups -> sortedGroups.map { group -> CommunityFeedItem.GroupItem(group) }
        CommunityLens.All -> {
            val mixed = mutableListOf<CommunityFeedItem>()
            val spotlightGroups = sortedGroups.take(8)
            val maxPrimary = maxOf(sortedEvents.size, sortedPosts.size)

            for (index in 0 until maxPrimary) {
                if (index < sortedEvents.size) {
                    mixed += CommunityFeedItem.EventItem(sortedEvents[index])
                }
                if (index < sortedPosts.size) {
                    mixed += CommunityFeedItem.PostItem(sortedPosts[index])
                }
                val spotlightIndex = index / 2
                if (index % 2 == 0 && spotlightIndex < spotlightGroups.size) {
                    mixed += CommunityFeedItem.GroupItem(spotlightGroups[spotlightIndex])
                }
            }

            if (mixed.isEmpty()) {
                mixed += spotlightGroups.map { group -> CommunityFeedItem.GroupItem(group) }
            }

            mixed
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
                "A single stream for meetups, conversation threads, and groups so people can move from discovery to action fast.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill(label = "Groups", value = totalGroups.toString())
                StatPill(label = "Events", value = totalEvents.toString())
                StatPill(label = "Discussions", value = totalDiscussions.toString())
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
    onLogCleanupCheckIn: (String) -> Unit,
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            enabled = !loading,
                            onClick = { onOpenGroup(group.id) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Open")
                        }
                        TextButton(
                            enabled = !loading,
                            onClick = { onLogCleanupCheckIn(group.id) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Cleanup check-in")
                        }
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
    groupName: String?,
    loading: Boolean,
    onRsvpEvent: (eventId: String, attending: Boolean) -> Unit,
    onApproveEvent: (eventId: String) -> Unit,
) {
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
                text = formatIsoDateTime(event.date),
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
        }
    }
}

@Composable
private fun DiscussionFeedCard(
    post: CommunityPost,
    onOpenPost: () -> Unit,
) {
    val commentHint = remember(post.id) { 6 + (((post.id.hashCode().toLong() and Long.MAX_VALUE) % 15L).toInt()) }

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
                    text = if (post.type == "lost_found") "Local alert" else "Discussion",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(post.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = post.body,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${post.suburb} • ${formatIsoDateTime(post.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "$commentHint replies in thread",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GroupFeedCard(
    group: Group,
    roster: List<PetRosterItem>,
    latestInvite: GroupInvite?,
    loading: Boolean,
    onOpenGroup: (String) -> Unit,
    onJoinGroup: (String) -> Unit,
    onCreateGroupInvite: (String) -> Unit,
    onClearGroupInvite: (String) -> Unit,
    onApproveJoinRequest: (groupId: String) -> Unit,
    onRejectJoinRequest: (groupId: String) -> Unit,
    onLogCleanupCheckIn: (groupId: String) -> Unit,
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when (group.membershipStatus) {
                    "member" -> {
                        OutlinedButton(enabled = !loading, onClick = { onOpenGroup(group.id) }) {
                            Text("Open")
                        }
                        TextButton(enabled = !loading, onClick = { onCreateGroupInvite(group.id) }) {
                            Text("Invite")
                        }
                        TextButton(enabled = !loading, onClick = { onLogCleanupCheckIn(group.id) }) {
                            Text("Cleanup")
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

private fun parseIsoInstant(raw: String?): Instant? {
    if (raw.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(raw).toInstant() }
        .recoverCatching { Instant.parse(raw) }
        .getOrNull()
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
    group: Group,
    roster: List<PetRosterItem>,
    events: List<CommunityEvent>,
    posts: List<CommunityPost>,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            item {
                Text(group.name, style = MaterialTheme.typography.headlineSmall)
                Text("${group.memberCount} members • ${group.suburb}", style = MaterialTheme.typography.bodyMedium)
            }

            if (roster.isNotEmpty()) {
                item {
                    PetRosterShowcase(
                        title = "Recently active pets",
                        pets = roster,
                    )
                }
            }

            item { Text("Group events", style = MaterialTheme.typography.titleMedium) }
            if (events.isEmpty()) {
                item { Text("No group events yet", style = MaterialTheme.typography.bodySmall) }
            } else {
                items(events.take(8), key = { event -> event.id }) { event ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(event.title, style = MaterialTheme.typography.titleSmall)
                            Text(event.description, style = MaterialTheme.typography.bodyMedium)
                            Text(formatIsoDateTime(event.date), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            item { Text("Recent local posts", style = MaterialTheme.typography.titleMedium) }
            if (posts.isEmpty()) {
                item { Text("No posts yet for this area", style = MaterialTheme.typography.bodySmall) }
            } else {
                items(posts, key = { post -> post.id }) { post ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(post.title, style = MaterialTheme.typography.titleSmall)
                            Text(post.body, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PostDetailSheet(
    post: CommunityPost,
    onDismiss: () -> Unit,
) {
    var commentInput by rememberSaveable(post.id) { mutableStateOf("") }

    val seedComments = remember(post.id) {
        listOf(
            "This is super helpful for local pet parents.",
            "Thanks for posting this update.",
            "Interested. Following for more details.",
        )
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = true,
                                onClick = {},
                                label = { Text(post.suburb) },
                            )
                            FilterChip(
                                selected = true,
                                onClick = {},
                                label = { Text(post.type.replace("_", " ")) },
                            )
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
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FavoriteBorder, contentDescription = "Like")
                        Text("Like")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Comment")
                        Text("Comment")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.IosShare, contentDescription = "Share")
                        Text("Share")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TurnedInNot, contentDescription = "Save")
                        Text("Save")
                    }
                }
            }

            item {
                Text("Comments", style = MaterialTheme.typography.titleMedium)
            }

            items(seedComments) { comment ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Text(
                        text = comment,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(10.dp),
                    )
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
                    onClick = { commentInput = "" },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Post comment")
                }
            }
        }
    }
}
