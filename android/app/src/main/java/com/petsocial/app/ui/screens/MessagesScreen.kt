package com.petsocial.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Messages", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Direct chat with providers, clients, and group admins.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onOpenNotifications("messages") }) {
                    Text(if (unreadNotificationCount > 0) "Alerts ($unreadNotificationCount)" else "Alerts")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = listFilter == "all",
                    onClick = { listFilter = "all" },
                    label = { Text("All (${threads.size})") },
                )
                FilterChip(
                    selected = listFilter == "unread",
                    onClick = { listFilter = "unread" },
                    label = { Text("Unread (${threads.count { it.unreadCount > 0 }})") },
                )
                FilterChip(
                    selected = listFilter == "pinned",
                    onClick = { listFilter = "pinned" },
                    label = { Text("Pinned (${threads.count { it.id in pinnedThreadIds || it.isPinned }})") },
                )
                FilterChip(
                    selected = listFilter == "muted",
                    onClick = { listFilter = "muted" },
                    label = { Text("Muted (${threads.count { it.id in mutedThreadIds || it.isMuted }})") },
                )
            }

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
                if (filteredThreads.isEmpty()) {
                    item {
                        Text("No conversations yet.", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    items(filteredThreads, key = { thread -> thread.id }) { thread ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectThread(thread.id) },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = thread.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = "${thread.participantAccountLabel} • ${thread.lastMessage}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (thread.unreadCount > 0) {
                                    Badge { Text(thread.unreadCount.toString()) }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (thread.id in pinnedThreadIds || thread.isPinned) {
                                    AssistChip(onClick = {}, label = { Text("Pinned") })
                                }
                                if (thread.id in mutedThreadIds || thread.isMuted) {
                                    AssistChip(onClick = {}, label = { Text("Muted") })
                                }
                                TextButton(onClick = { onMarkThreadRead(thread.id) }) {
                                    Text("Mark read")
                                }
                                TextButton(onClick = { onTogglePinThread(thread.id) }) {
                                    Text(if (thread.id in pinnedThreadIds || thread.isPinned) "Unpin" else "Pin")
                                }
                                TextButton(onClick = { onToggleMuteThread(thread.id) }) {
                                    Text(if (thread.id in mutedThreadIds || thread.isMuted) "Unmute" else "Mute")
                                }
                                TextButton(onClick = { onBlockParticipant(thread.participantUserId) }) {
                                    Text("Block")
                                }
                            }
                        }
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
            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(selectedThread.title, style = MaterialTheme.typography.titleMedium)
                    if (selectedThread.id in pinnedThreadIds || selectedThread.isPinned) {
                        AssistChip(onClick = {}, label = { Text("Pinned") })
                    }
                    if (selectedThread.id in mutedThreadIds || selectedThread.isMuted) {
                        AssistChip(onClick = {}, label = { Text("Muted") })
                    }
                }
                Text(
                    selectedThread.participantAccountLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = { onMarkThreadRead(selectedThread.id) }) {
                Text("Mark read")
            }
            TextButton(onClick = { onTogglePinThread(selectedThread.id) }) {
                Text(if (selectedThread.id in pinnedThreadIds || selectedThread.isPinned) "Unpin thread" else "Pin thread")
            }
            TextButton(onClick = { onToggleMuteThread(selectedThread.id) }) {
                Text(if (selectedThread.id in mutedThreadIds || selectedThread.isMuted) "Unmute thread" else "Mute thread")
            }
            TextButton(onClick = { onBlockParticipant(selectedThread.participantUserId) }) {
                Text("Block user")
            }
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
                ) {
                    Text(
                        text = message.body,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .widthIn(max = 340.dp)
                            .background(
                                if (mine) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(12.dp),
                            )
                            .padding(10.dp),
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
                label = { Text("Message ${selectedThread.participantAccountLabel}") },
                minLines = 1,
                maxLines = if (inputFocused) 4 else 1,
            )
            Button(
                onClick = {
                    onSend(selectedThread.id, input)
                    input = ""
                },
            ) { Text("Send") }
        }
    }
}
