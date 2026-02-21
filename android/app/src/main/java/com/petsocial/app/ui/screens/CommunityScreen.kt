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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TurnedInNot
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
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
) {
    var showThenVsNowFlow by rememberSaveable { mutableStateOf(false) }
    var showCreatePostDialog by rememberSaveable { mutableStateOf(false) }
    var createPostType by rememberSaveable { mutableStateOf("group_post") }
    var createPostTitle by rememberSaveable { mutableStateOf("") }
    var createPostBody by rememberSaveable { mutableStateOf("") }
    var createEventDate by rememberSaveable { mutableStateOf("2026-02-28T10:00:00Z") }
    var createEventGroupId by rememberSaveable { mutableStateOf("") }
    var selectedPost by remember { mutableStateOf<CommunityPost?>(null) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val listHasScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 20
        }
    }
    var controlsExpanded by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(listHasScrolled) {
        if (listHasScrolled) {
            controlsExpanded = false
        }
    }

    if (showThenVsNowFlow) {
        ThenVsNowFlow(onClose = { showThenVsNowFlow = false })
        return
    }

    Box(modifier = Modifier.fillMaxWidth()) {
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
                            controlsExpanded = true
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
                        Text("Create post")
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
            Text("Nearby community groups in $suburb", style = MaterialTheme.typography.titleMedium)
        }

        item {
            Text("Groups", style = MaterialTheme.typography.titleSmall)
        }
        items(groups) { group ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = if (group.membershipStatus == "member") {
                    Modifier.clickable { onOpenGroup(group.id) }
                } else {
                    Modifier
                },
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val groupRoster = groupPetRosters[group.id].orEmpty()
                    Text(
                        buildString {
                            append(group.name)
                            if (group.official) append(" • Official")
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text("${group.memberCount} members • ${group.suburb}")
                    if (group.isAdmin) {
                        Text("Admin controls enabled", style = MaterialTheme.typography.labelSmall)
                    }
                    Text("Group ID: ${group.id}", style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (group.membershipStatus) {
                            "member" -> Text("Joined", style = MaterialTheme.typography.labelLarge)
                            "pending" -> Text("Pending approval", style = MaterialTheme.typography.labelLarge)
                            else -> Button(enabled = !loading, onClick = { onJoinGroup(group.id) }) { Text("Apply to join") }
                        }
                        if (group.membershipStatus == "member") {
                            TextButton(
                                enabled = !loading,
                                onClick = { onCreateGroupInvite(group.id) },
                            ) { Text("Invite via QR link") }
                        }
                    }
                    latestGroupInvites[group.id]?.let { invite ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Invite link:", style = MaterialTheme.typography.labelSmall)
                                Text(invite.inviteUrl, style = MaterialTheme.typography.bodySmall)
                                AsyncImage(
                                    model = inviteQrImageUrl(invite.inviteUrl),
                                    contentDescription = "Group invite QR code",
                                    modifier = Modifier.size(110.dp),
                                    contentScale = ContentScale.Crop,
                                )
                                Text("Expires: ${invite.expiresAt}", style = MaterialTheme.typography.labelSmall)
                                TextButton(onClick = { onClearGroupInvite(group.id) }) { Text("Hide") }
                            }
                        }
                    }
                    if (group.isAdmin && group.pendingRequestCount > 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = !loading, onClick = { onApproveJoinRequest(group.id) }) {
                                Text("Approve next (${group.pendingRequestCount})")
                            }
                            Button(enabled = !loading, onClick = { onRejectJoinRequest(group.id) }) {
                                Text("Reject next")
                            }
                        }
                    }
                    if (groupRoster.isNotEmpty()) {
                        PetRosterShowcase(
                            title = "New dogs this week",
                            pets = groupRoster,
                        )
                    }
                }
            }
        }

        item {
            Text("Upcoming events", style = MaterialTheme.typography.titleSmall)
        }
        if (events.isEmpty()) {
            item {
                Text("No events yet in $suburb")
            }
        } else {
            items(events) { event ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(event.title, style = MaterialTheme.typography.titleSmall)
                        Text(event.description, style = MaterialTheme.typography.bodyMedium)
                        Text("${event.date} • ${event.attendeeCount} attending • ${event.status}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    Text("Approve event")
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("Recent posts", style = MaterialTheme.typography.titleSmall)
            AnimatedVisibility(visible = controlsExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

        items(posts) { post ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.clickable { selectedPost = post },
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(post.title, style = MaterialTheme.typography.titleSmall)
                    Text(post.body, style = MaterialTheme.typography.bodyMedium)
                    Text("${post.suburb} • ${post.type}", style = MaterialTheme.typography.labelSmall)
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
                            label = { Text("Community group") },
                        )
                        FilterChip(
                            selected = createPostType == "lost_found",
                            onClick = { createPostType = "lost_found" },
                            label = { Text("Lost dog") },
                        )
                        FilterChip(
                            selected = createPostType == "community_event",
                            onClick = { createPostType = "community_event" },
                            label = { Text("Community event") },
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
                items(events.take(8)) { event ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(event.title, style = MaterialTheme.typography.titleSmall)
                            Text(event.description, style = MaterialTheme.typography.bodyMedium)
                            Text(event.date, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            item { Text("Recent local posts", style = MaterialTheme.typography.titleMedium) }
            if (posts.isEmpty()) {
                item { Text("No posts yet for this area", style = MaterialTheme.typography.bodySmall) }
            } else {
                items(posts) { post ->
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

private fun inviteQrImageUrl(inviteUrl: String): String {
    val encoded = URLEncoder.encode(inviteUrl, StandardCharsets.UTF_8.toString())
    return "https://quickchart.io/qr?size=220&text=$encoded"
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
                            post.createdAt ?: "Just now",
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
