package com.petsocial.app.ui

import com.petsocial.app.data.ChatResponse
import com.petsocial.app.data.ChatTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingCompletionResolutionTest {

    @Test
    fun resolveOnboardingCompletion_removesOnboardingThreadAndKeepsNormalThread() {
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

        assertEquals("bark_thread_existing", resolution.selectedThreadId)
        assertEquals(1, resolution.barkThreads.size)
        assertEquals("bark_thread_existing", resolution.barkThreads.single().id)
        assertEquals(normalChat, resolution.chat)
        assertEquals(normalConversation, resolution.conversation)
        assertFalse(resolution.barkThreads.any { it.id == "bark_thread_onboarding" })
    }

    @Test
    fun resolveOnboardingCompletion_createsFreshThreadWhenOnlyOnboardingExists() {
        val resolution = resolveOnboardingCompletion(
            barkThreads = listOf(
                BarkThread(
                    id = "bark_thread_onboarding",
                    title = "Onboarding",
                    conversation = listOf(ChatTurn(role = "assistant", content = "Welcome")),
                    updatedAt = 1L,
                ),
            ),
            fallbackThreadId = "bark_thread_fallback",
            fallbackUpdatedAt = 99L,
        )

        assertEquals("bark_thread_fallback", resolution.selectedThreadId)
        assertEquals(1, resolution.barkThreads.size)
        assertEquals("bark_thread_fallback", resolution.barkThreads.single().id)
        assertEquals("New thread", resolution.barkThreads.single().title)
        assertTrue(resolution.conversation.isEmpty())
        assertEquals(null, resolution.chat)
    }

    @Test
    fun resolveOnboardingCompletion_preservesOrderingOfRemainingThreads() {
        val resolution = resolveOnboardingCompletion(
            barkThreads = listOf(
                BarkThread(id = "bark_thread_onboarding", title = "Onboarding", updatedAt = 100L),
                BarkThread(id = "bark_thread_latest", title = "Latest", updatedAt = 90L),
                BarkThread(id = "bark_thread_older", title = "Older", updatedAt = 80L),
            ),
            fallbackThreadId = "bark_thread_fallback",
            fallbackUpdatedAt = 70L,
        )

        assertEquals(listOf("bark_thread_latest", "bark_thread_older"), resolution.barkThreads.map { it.id })
        assertEquals("bark_thread_latest", resolution.selectedThreadId)
    }
}
