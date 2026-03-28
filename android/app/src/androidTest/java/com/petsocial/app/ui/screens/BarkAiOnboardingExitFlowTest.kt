package com.petsocial.app.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petsocial.app.data.ChatResponse
import com.petsocial.app.data.ChatTurn
import com.petsocial.app.ui.BarkThread
import com.petsocial.app.ui.resolveOnboardingCompletion
import com.petsocial.app.testing.ComposeTestActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BarkAiOnboardingExitFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun onboardingCompletion_hidesOnboardingThreadAndShowsNormalBarkAiState() {
        val normalConversation = listOf(ChatTurn(role = "assistant", content = "Normal thread"))
        val normalChat = ChatResponse(answer = "Normal thread", conversation = normalConversation)
        val resolution = resolveOnboardingCompletion(
            barkThreads = listOf(
                BarkThread(
                    id = "bark_thread_onboarding",
                    title = "Onboarding",
                    conversation = listOf(ChatTurn(role = "assistant", content = "Welcome")),
                    updatedAt = 1L,
                ),
                BarkThread(
                    id = "bark_thread_existing",
                    title = "Existing",
                    conversation = normalConversation,
                    chat = normalChat,
                    updatedAt = 2L,
                ),
            ),
            fallbackThreadId = "bark_thread_fallback",
            fallbackUpdatedAt = 3L,
        )

        composeRule.setContent {
            MaterialTheme {
                ChatScreen(
                    loading = false,
                    chatResponse = resolution.chat,
                    conversation = resolution.conversation,
                    streamingAssistantText = "",
                    error = null,
                    profileSuggestion = null,
                    a2uiProfileCard = null,
                    a2uiProviderCard = null,
                    barkThreads = resolution.barkThreads,
                    selectedBarkThreadId = resolution.selectedThreadId,
                    onboardingMode = false,
                    onboardingNeedsPhoto = false,
                    onSelectBarkThread = {},
                    onNewBarkThread = {},
                    onSend = {},
                    onOnboardingPhotoCaptured = { _, _ -> },
                    onCtaClick = {},
                    onAcceptProfile = {},
                    onSubmitProvider = {},
                )
            }
        }

        composeRule.onNodeWithText("Normal thread").assertIsDisplayed()
        composeRule.onAllNodesWithText("Onboarding").assertCountEquals(0)
    }
}
