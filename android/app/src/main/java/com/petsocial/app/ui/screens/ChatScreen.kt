package com.petsocial.app.ui.screens

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.petsocial.app.data.ChatCta
import com.petsocial.app.data.ChatResponse
import com.petsocial.app.data.ChatTurn
import com.petsocial.app.data.PetProfileSuggestion
import com.petsocial.app.ui.A2uiCardState
import com.petsocial.app.ui.BarkThread
import java.io.File
import java.io.FileOutputStream

@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    loading: Boolean,
    chatResponse: ChatResponse?,
    conversation: List<ChatTurn>,
    streamingAssistantText: String,
    error: String?,
    profileSuggestion: PetProfileSuggestion?,
    a2uiProfileCard: A2uiCardState?,
    a2uiProviderCard: A2uiCardState?,
    barkThreads: List<BarkThread>,
    selectedBarkThreadId: String,
    onboardingMode: Boolean,
    onboardingNeedsPhoto: Boolean,
    onSelectBarkThread: (String) -> Unit,
    onNewBarkThread: () -> Unit,
    onSend: (String) -> Unit,
    onOnboardingPhotoCaptured: (Boolean, String?) -> Unit,
    onCtaClick: (ChatCta) -> Unit,
    onAcceptProfile: () -> Unit,
    onSubmitProvider: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var inputFocused by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    var launchCameraAfterPermission by rememberSaveable { mutableStateOf(false) }
    val conversationListState = rememberLazyListState()
    val isShowingStreaming = loading && streamingAssistantText.isNotBlank()
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
    ) { bitmap ->
        val capturedPhotoUri = bitmap?.let { captured ->
            persistOnboardingDogPhoto(context = context, bitmap = captured)
        }
        onOnboardingPhotoCaptured(capturedPhotoUri != null, capturedPhotoUri)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && launchCameraAfterPermission) {
            launchCameraAfterPermission = false
            runCatching { cameraLauncher.launch(null) }
                .onFailure { onOnboardingPhotoCaptured(false, null) }
        } else if (!granted) {
            launchCameraAfterPermission = false
            onOnboardingPhotoCaptured(false, null)
        }
    }
    val listCount = conversation.size +
        (if (isShowingStreaming) 1 else 0) +
        (if (!error.isNullOrBlank()) 1 else 0) +
        (if (conversation.isEmpty() && !loading) 1 else 0)
    val composerEnabled = resolveBarkAiComposerEnabled(
        loading = loading,
        onboardingMode = onboardingMode,
    )

    LaunchedEffect(
        listCount,
        error,
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
                            text = "Start a conversation with BarkAI.",
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
                if (!error.isNullOrBlank()) {
                    item {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.errorContainer,
                                    RoundedCornerShape(12.dp),
                                )
                                .padding(12.dp),
                        )
                    }
                }
            }
        }

        chatResponse?.ctaChips?.takeIf { it.isNotEmpty() }?.let { ctas ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ctas.forEach { cta ->
                    AssistChip(
                        onClick = { onCtaClick(cta) },
                        label = {
                            Text(
                                text = cta.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
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
                    label = { Text(if (onboardingMode) "Reply to BarkWiseAI" else "Message BarkAI") },
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { inputFocused = it.isFocused },
                    minLines = 1,
                    maxLines = if (inputFocused) 4 else 1,
                )
                if (onboardingMode && onboardingNeedsPhoto) {
                    Button(
                        enabled = composerEnabled,
                        onClick = {
                            val permissionGranted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (permissionGranted) {
                                runCatching { cameraLauncher.launch(null) }
                                    .onFailure { onOnboardingPhotoCaptured(false, null) }
                            } else {
                                launchCameraAfterPermission = true
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.width(92.dp),
                    ) {
                        Text("Camera", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Button(
                    enabled = composerEnabled && input.isNotBlank(),
                    onClick = {
                        onSend(input)
                        input = ""
                    },
                    modifier = Modifier.width(84.dp),
                ) {
                    Text("Send", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

internal fun resolveBarkAiComposerEnabled(
    loading: Boolean,
    onboardingMode: Boolean,
): Boolean = onboardingMode || !loading

@Composable
private fun MessageBubble(turn: ChatTurn) {
    val isUser = turn.role == "user"
    val bg = if (isUser) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val align = if (isUser) Arrangement.End else Arrangement.Start

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = align) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(bg, RoundedCornerShape(14.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = turn.content,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
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
                Text(actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun persistOnboardingDogPhoto(context: Context, bitmap: Bitmap): String? {
    val directory = File(context.filesDir, "onboarding_dog_photos")
    if (!directory.exists() && !directory.mkdirs()) return null
    val file = File(directory, "dog_${System.currentTimeMillis()}.jpg")
    return runCatching {
        FileOutputStream(file).use { stream ->
            val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
            if (!compressed) throw IllegalStateException("Failed to compress onboarding photo.")
            stream.flush()
        }
        file.toURI().toString()
    }.getOrNull()
}
