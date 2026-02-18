package com.petsocial.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import com.petsocial.app.data.ChatCta
import com.petsocial.app.data.ChatResponse
import com.petsocial.app.data.ChatTurn
import com.petsocial.app.data.PetProfileSuggestion
import com.petsocial.app.ui.A2uiCardState

@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    loading: Boolean,
    chatResponse: ChatResponse?,
    conversation: List<ChatTurn>,
    streamingAssistantText: String,
    profileSuggestion: PetProfileSuggestion?,
    a2uiProfileCard: A2uiCardState?,
    a2uiProviderCard: A2uiCardState?,
    onSend: (String) -> Unit,
    onCtaClick: (ChatCta) -> Unit,
    onAcceptProfile: () -> Unit,
    onSubmitProvider: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var inputFocused by rememberSaveable { mutableStateOf(false) }
    val conversationListState = rememberLazyListState()
    val isShowingStreaming = loading && streamingAssistantText.isNotBlank()
    val isShowingTyping = loading && streamingAssistantText.isBlank()
    val hasQuickActions = chatResponse?.ctaChips?.isNotEmpty() == true
    val hasProfileCard = ((a2uiProfileCard?.fields?.isNotEmpty() == true) || profileSuggestion != null)
    val hasProviderCard = a2uiProviderCard != null
    val listCount = conversation.size +
        (if (isShowingStreaming) 1 else 0) +
        (if (isShowingTyping) 1 else 0) +
        (if (hasProviderCard) 1 else 0) +
        (if (hasProfileCard) 1 else 0) +
        (if (hasQuickActions) 1 else 0) +
        (if (conversation.isEmpty() && !loading) 1 else 0)

    LaunchedEffect(
        listCount,
        streamingAssistantText,
        chatResponse?.answer,
        chatResponse?.ctaChips?.size,
        a2uiProfileCard,
        a2uiProviderCard,
        profileSuggestion,
    ) {
        if (listCount > 0) {
            conversationListState.scrollToItem(listCount - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 10.dp),
                state = conversationListState,
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
            ) {
                if (conversation.isEmpty() && !loading) {
                    item {
                        Text(
                            text = "BarkAI is ready for pet questions, local pet services, and community help.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(12.dp),
                                )
                                .padding(12.dp),
                        )
                    }
                }
                items(conversation.size) { index ->
                    MessageBubble(conversation[index])
                }
                if (loading && streamingAssistantText.isNotBlank()) {
                    item {
                        MessageBubble(ChatTurn(role = "assistant", content = streamingAssistantText))
                    }
                }
                if (loading && streamingAssistantText.isBlank()) {
                    item {
                        MessageBubble(ChatTurn(role = "assistant", content = "BarkWise is typing..."))
                    }
                }
                a2uiProviderCard?.let { providerCard ->
                    item {
                        A2uiCard(
                            title = providerCard.title,
                            fields = providerCard.fields,
                            actionLabel = "Submit Provider Listing",
                            onAction = onSubmitProvider,
                        )
                    }
                }
                val profileCardSource = a2uiProfileCard?.fields?.takeIf { it.isNotEmpty() } ?: profileSuggestion?.let {
                    buildMap {
                        it.petName?.let { value -> put("pet_name", value) }
                        it.petType?.let { value -> put("pet_type", value) }
                        it.breed?.let { value -> put("breed", value) }
                        it.ageYears?.let { value -> put("age_years", value.toString()) }
                        it.weightKg?.let { value -> put("weight_kg", value.toString()) }
                        it.suburb?.let { value -> put("suburb", value) }
                        if (it.concerns.isNotEmpty()) put("concerns", it.concerns.joinToString(", "))
                    }
                }
                if (profileCardSource != null && profileCardSource.isNotEmpty()) {
                    item {
                        A2uiCard(
                            title = a2uiProfileCard?.title ?: "Suggested Pet Profile",
                            fields = profileCardSource,
                            actionLabel = "Accept Profile",
                            onAction = onAcceptProfile,
                        )
                    }
                }
                chatResponse?.let { response ->
                    if (response.ctaChips.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Quick actions", style = MaterialTheme.typography.labelLarge)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                                ) {
                                    response.ctaChips.forEach { cta ->
                                        AssistChip(
                                            onClick = { onCtaClick(cta) },
                                            label = { Text(cta.label) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Message BarkAI") },
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { inputFocused = it.isFocused },
                    minLines = 1,
                    maxLines = if (inputFocused) 4 else 1,
                )
                Button(
                    enabled = !loading,
                    onClick = {
                        onSend(input)
                        input = ""
                    },
                    modifier = Modifier.width(84.dp),
                ) {
                    Text("Send")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(turn: ChatTurn) {
    val isUser = turn.role == "user"
    val bg = if (isUser) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val align = if (isUser) Arrangement.End else Arrangement.Start

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = align) {
        Text(
            text = turn.content,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(bg, RoundedCornerShape(14.dp))
                .padding(10.dp),
        )
    }
}

@Composable
private fun A2uiCard(
    title: String,
    fields: Map<String, String>,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            fields.forEach { (k, v) ->
                Text("$k: $v", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}
