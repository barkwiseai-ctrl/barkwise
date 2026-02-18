package com.petsocial.app.ui.screens

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

@Composable
fun CommunityScreen(
    loading: Boolean,
    suburb: String,
    postsSortBy: String,
    groups: List<Group>,
    groupPetRosters: Map<String, List<PetRosterItem>>,
    latestGroupInvites: Map<String, GroupInvite>,
    posts: List<CommunityPost>,
    events: List<CommunityEvent>,
    onJoinGroup: (String) -> Unit,
    onCreateGroupInvite: (String) -> Unit,
    onClearGroupInvite: (String) -> Unit,
    onPostsSortChange: (String) -> Unit,
    onCreateLostFound: (title: String, body: String, suburb: String) -> Unit,
    onCreateEvent: (title: String, description: String, date: String, groupId: String?) -> Unit,
    onRsvpEvent: (eventId: String, attending: Boolean) -> Unit,
    onApproveJoinRequest: (groupId: String) -> Unit,
    onRejectJoinRequest: (groupId: String) -> Unit,
    onApproveEvent: (eventId: String) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("Lost pet alert") }
    var body by rememberSaveable { mutableStateOf("") }
    var eventTitle by rememberSaveable { mutableStateOf("") }
    var eventDescription by rememberSaveable { mutableStateOf("") }
    var eventDate by rememberSaveable { mutableStateOf("2026-02-28T10:00:00Z") }
    var eventGroupId by rememberSaveable { mutableStateOf("") }
    var selectedPost by remember { mutableStateOf<CommunityPost?>(null) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 90.dp),
    ) {
        item {
            Text("Nearby community groups in $suburb", style = MaterialTheme.typography.titleMedium)
        }

        item {
            Text("Groups", style = MaterialTheme.typography.titleSmall)
        }
        items(groups) { group ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
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
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Create Community Event", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(value = eventTitle, onValueChange = { eventTitle = it }, label = { Text("Event title") })
                    OutlinedTextField(
                        value = eventDescription,
                        onValueChange = { eventDescription = it },
                        label = { Text("Description") },
                        minLines = 2,
                        maxLines = 4,
                    )
                    OutlinedTextField(
                        value = eventDate,
                        onValueChange = { eventDate = it },
                        label = { Text("Date (ISO, e.g. 2026-02-28T10:00:00Z)") },
                    )
                    OutlinedTextField(
                        value = eventGroupId,
                        onValueChange = { eventGroupId = it },
                        label = { Text("Group ID (optional, enables admin approval)") },
                    )
                    Button(
                        enabled = !loading && eventTitle.isNotBlank() && eventDate.isNotBlank(),
                        onClick = {
                            onCreateEvent(
                                eventTitle.trim(),
                                eventDescription.trim(),
                                eventDate.trim(),
                                eventGroupId.trim().ifBlank { null },
                            )
                            eventTitle = ""
                            eventDescription = ""
                            eventGroupId = ""
                        },
                    ) {
                        Text("Create event")
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Create Lost/Found Post", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Details") },
                        minLines = 2,
                        maxLines = 4,
                    )
                    Button(
                        enabled = !loading && body.isNotBlank(),
                        onClick = { onCreateLostFound(title.trim(), body.trim(), suburb) },
                    ) {
                        Text("Post alert")
                    }
                }
            }
        }

        item {
            Text("Recent posts", style = MaterialTheme.typography.titleSmall)
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

    selectedPost?.let { post ->
        PostDetailSheet(
            post = post,
            onDismiss = { selectedPost = null },
        )
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
