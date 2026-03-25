package com.petsocial.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.petsocial.app.ui.DirectMessage
import com.petsocial.app.ui.MessageThread

@Composable
fun MessagesScreen(
    activeUserId: String,
    threads: List<MessageThread>,
    mutedThreadIds: Set<String>,
    pinnedThreadIds: Set<String>,
    unreadNotificationCount: Int,
    selectedThreadId: String?,
    messages: List<DirectMessage>,
    onOpenNotifications: (filter: String) -> Unit,
    onSelectThread: (String) -> Unit,
    onBackToThreads: () -> Unit,
    onMarkThreadRead: (String) -> Unit,
    onToggleMuteThread: (String) -> Unit,
    onTogglePinThread: (String) -> Unit,
    onBlockParticipant: (String) -> Unit,
    onSend: (threadId: String, body: String) -> Unit,
) {
    val selectedThread = threads.firstOrNull { it.id == selectedThreadId }
    val threadMessages = messages.filter { msg -> msg.threadId == selectedThread?.id }
    var input by rememberSaveable(selectedThread?.id) { mutableStateOf("") }
    var inputFocused by rememberSaveable(selectedThread?.id) { mutableStateOf(false) }
    var listFilter by rememberSaveable { mutableStateOf("all") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val conversationsListState = rememberLazyListState()
    val threadListState = rememberLazyListState()
    val filteredThreads = remember(threads, listFilter, mutedThreadIds, pinnedThreadIds) {
        threads.filter { thread ->
            when (listFilter) {
                "unread" -> thread.unreadCount > 0
                "pinned" -> thread.id in pinnedThreadIds || thread.isPinned
                "muted" -> thread.id in mutedThreadIds || thread.isMuted
                else -> true
            }
        }
    }
    val searchedThreads = remember(filteredThreads, searchQuery) {
        val normalized = searchQuery.trim().lowercase()
        if (normalized.isBlank()) {
            filteredThreads
        } else {
            filteredThreads.filter { thread ->
                thread.title.contains(normalized, ignoreCase = true) ||
                    thread.participantAccountLabel.contains(normalized, ignoreCase = true) ||
                    thread.participantPetNames.any { pet -> pet.contains(normalized, ignoreCase = true) } ||
                    thread.lastMessage.contains(normalized, ignoreCase = true)
            }
        }
    }
    LaunchedEffect(selectedThread?.id, threadMessages.size) {
        if (threadMessages.isNotEmpty()) {
            threadListState.scrollToItem(threadMessages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (selectedThread == null) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MessageFilterIconChip(
                    selected = listFilter == "all",
                    onClick = { listFilter = "all" },
                    icon = Icons.Default.ChatBubble,
                    contentDescription = "All conversations (${threads.size})",
                )
                MessageFilterIconChip(
                    selected = listFilter == "unread",
                    onClick = { listFilter = "unread" },
                    icon = Icons.Default.MarkEmailUnread,
                    contentDescription = "Unread conversations (${threads.count { it.unreadCount > 0 }})",
                )
                MessageFilterIconChip(
                    selected = listFilter == "pinned",
                    onClick = { listFilter = "pinned" },
                    icon = Icons.Default.PushPin,
                    contentDescription = "Pinned conversations (${threads.count { it.id in pinnedThreadIds || it.isPinned }})",
                )
                MessageFilterIconChip(
                    selected = listFilter == "muted",
                    onClick = { listFilter = "muted" },
                    icon = Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = "Muted conversations (${threads.count { it.id in mutedThreadIds || it.isMuted }})",
                )
                IconButton(onClick = { onOpenNotifications("messages") }) {
                    BadgedBox(
                        badge = {
                            if (unreadNotificationCount > 0) {
                                Badge { Text(unreadNotificationCount.toString()) }
                            }
                        },
                    ) {
                        Icon(Icons.Default.NotificationsNone, contentDescription = "Message alerts")
                    }
                }
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search dog or human") },
                singleLine = true,
            )
            Text(
                "Swipe right to pin/unpin. Swipe left to mute/unmute.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LazyColumn(
                state = conversationsListState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                    .padding(10.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (searchedThreads.isEmpty()) {
                    item {
                        Text("No conversations found.", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    items(searchedThreads, key = { thread -> thread.id }) { thread ->
                        val isPinned = thread.id in pinnedThreadIds || thread.isPinned
                        val isMuted = thread.id in mutedThreadIds || thread.isMuted
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                when (value) {
                                    SwipeToDismissBoxValue.StartToEnd -> {
                                        onTogglePinThread(thread.id)
                                        false
                                    }

                                    SwipeToDismissBoxValue.EndToStart -> {
                                        onToggleMuteThread(thread.id)
                                        false
                                    }

                                    else -> true
                                }
                            },
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = true,
                            enableDismissFromEndToStart = true,
                            backgroundContent = {
                                MessageSwipeBackground(
                                    targetValue = dismissState.targetValue,
                                    isPinned = isPinned,
                                    isMuted = isMuted,
                                )
                            },
                            content = {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                        .clickable {
                                            onSelectThread(thread.id)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ConversationAvatar(
                                        label = thread.participantAccountLabel,
                                        imageUrl = thread.participantAvatarUrl,
                                        hasUnread = thread.unreadCount > 0,
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = thread.participantAccountLabel,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                        if (thread.participantPetNames.isNotEmpty()) {
                                            Text(
                                                text = thread.participantPetNames.joinToString(", "),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Text(
                                            text = thread.lastMessage,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        if (thread.unreadCount > 0) {
                                            UnreadCountBadge(thread.unreadCount)
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            if (isPinned) {
                                                AssistChip(onClick = {}, label = { Text("Pinned", maxLines = 1) })
                                            }
                                            if (isMuted) {
                                                AssistChip(onClick = {}, label = { Text("Muted", maxLines = 1) })
                                            }
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
            return
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onBackToThreads) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to conversations",
                )
            }
            ConversationAvatar(
                label = selectedThread.participantAccountLabel,
                imageUrl = selectedThread.participantAvatarUrl,
                hasUnread = selectedThread.unreadCount > 0,
                size = 42.dp,
            )
            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(selectedThread.participantAccountLabel, style = MaterialTheme.typography.titleMedium)
                    if (selectedThread.id in pinnedThreadIds || selectedThread.isPinned) {
                        AssistChip(onClick = {}, label = { Text("Pinned", maxLines = 1) })
                    }
                    if (selectedThread.id in mutedThreadIds || selectedThread.isMuted) {
                        AssistChip(onClick = {}, label = { Text("Muted", maxLines = 1) })
                    }
                }
                selectedThread.participantPetNames
                    .takeIf { names -> names.isNotEmpty() }
                    ?.let { names ->
                        Text(
                            names.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                Text(
                    selectedThread.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(onClick = { onMarkThreadRead(selectedThread.id) }, label = { Text("Read", maxLines = 1) })
            AssistChip(
                onClick = { onTogglePinThread(selectedThread.id) },
                label = {
                    Text(
                        if (selectedThread.id in pinnedThreadIds || selectedThread.isPinned) "Unpin" else "Pin",
                        maxLines = 1,
                    )
                },
            )
            AssistChip(
                onClick = { onToggleMuteThread(selectedThread.id) },
                label = {
                    Text(
                        if (selectedThread.id in mutedThreadIds || selectedThread.isMuted) "Unmute" else "Mute",
                        maxLines = 1,
                    )
                },
            )
            AssistChip(
                onClick = { onBlockParticipant(selectedThread.participantUserId) },
                label = { Text("Block", maxLines = 1) },
                leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp),
            state = threadListState,
            contentPadding = PaddingValues(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
        ) {
            items(threadMessages.size) { index ->
                val message = threadMessages[index]
                val mine = message.senderUserId == activeUserId
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    if (!mine) {
                        ConversationAvatar(
                            label = selectedThread.participantAccountLabel,
                            imageUrl = selectedThread.participantAvatarUrl,
                            hasUnread = false,
                            size = 30.dp,
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                    }
                    Text(
                        text = message.body,
                        modifier = Modifier
                            .fillMaxWidth(0.82f)
                            .widthIn(max = 340.dp)
                            .background(
                                if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomEnd = if (mine) 4.dp else 16.dp,
                                    bottomStart = if (mine) 16.dp else 4.dp,
                                ),
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { inputFocused = it.isFocused },
                label = { Text("Message", maxLines = 1) },
                placeholder = {
                    Text(
                        "Write to ${selectedThread.participantAccountLabel}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                minLines = 1,
                maxLines = if (inputFocused) 4 else 1,
            )
            FilledIconButton(
                enabled = input.isNotBlank(),
                onClick = {
                    onSend(selectedThread.id, input)
                    input = ""
                },
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun UnreadCountBadge(count: Int) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (count > 9) "9+" else count.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ConversationAvatar(
    label: String,
    imageUrl: String? = null,
    hasUnread: Boolean,
    size: androidx.compose.ui.unit.Dp = 48.dp,
) {
    val initial = label.trim().firstOrNull()?.uppercase() ?: "B"
    Box(
        modifier = Modifier
            .size(size)
            .background(
                color = if (hasUnread) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape,
            )
            .border(
                width = if (hasUnread) 1.5.dp else 0.dp,
                color = if (hasUnread) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "$label avatar",
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
            )
        } else {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (hasUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessageFilterIconChip(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
            )
        },
    )
}

@Composable
private fun MessageSwipeBackground(
    targetValue: SwipeToDismissBoxValue,
    isPinned: Boolean,
    isMuted: Boolean,
) {
    val isStart = targetValue == SwipeToDismissBoxValue.StartToEnd
    val isEnd = targetValue == SwipeToDismissBoxValue.EndToStart
    val backgroundColor = when {
        isStart -> MaterialTheme.colorScheme.secondaryContainer
        isEnd -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val label = when {
        isStart -> if (isPinned) "Unpin" else "Pin"
        isEnd -> if (isMuted) "Unmute" else "Mute"
        else -> ""
    }
    val icon = when {
        isStart -> Icons.Default.PushPin
        isEnd -> Icons.AutoMirrored.Filled.VolumeOff
        else -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = when {
            isStart -> Arrangement.Start
            isEnd -> Arrangement.End
            else -> Arrangement.Start
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null && label.isNotBlank()) {
            Icon(icon, contentDescription = null)
            Text(
                text = label,
                modifier = Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
