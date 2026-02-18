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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
    selectedThreadId: String?,
    messages: List<DirectMessage>,
    onSelectThread: (String) -> Unit,
    onBackToThreads: () -> Unit,
    onSend: (threadId: String, body: String) -> Unit,
) {
    val selectedThread = threads.firstOrNull { it.id == selectedThreadId }
    val threadMessages = messages.filter { msg -> msg.threadId == selectedThread?.id }
    var input by rememberSaveable(selectedThread?.id) { mutableStateOf("") }
    var inputFocused by rememberSaveable(selectedThread?.id) { mutableStateOf(false) }
    val threadListState = rememberLazyListState()
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
            Text("Messages", style = MaterialTheme.typography.titleMedium)
            Text(
                "Direct chat with providers, clients, and group admins.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (threads.isEmpty()) {
                    Text("No conversations yet.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    threads.forEach { thread ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                .clickable { onSelectThread(thread.id) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
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
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back to conversations",
                )
            }
            Column {
                Text(selectedThread.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    selectedThread.participantAccountLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
