package com.petsocial.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarkAiThreadStateTest {

    @Test
    fun resolveBarkAiEntry_whileOnboarding_staysOnOnboardingThread() {
        val resolution = resolveBarkAiEntry(
            onboardingActive = true,
            selectedBarkThreadId = "bark_thread_onboarding",
            barkThreads = listOf(BarkThread(id = "bark_thread_onboarding", title = "Onboarding")),
            newThreadId = "bark_thread_new",
            updatedAt = 10L,
        )

        assertEquals(
            BarkAiEntryResolution.StayOnOnboarding("bark_thread_onboarding"),
            resolution,
        )
    }

    @Test
    fun resolveBarkAiEntry_whileOnboarding_defaultsToCanonicalOnboardingThreadId() {
        val resolution = resolveBarkAiEntry(
            onboardingActive = true,
            selectedBarkThreadId = "missing_thread",
            barkThreads = emptyList(),
            newThreadId = "bark_thread_new",
            updatedAt = 10L,
        )

        assertEquals(
            BarkAiEntryResolution.StayOnOnboarding("bark_thread_onboarding"),
            resolution,
        )
    }

    @Test
    fun resolveBarkAiEntry_whenNotOnboarding_startsNewThread() {
        val resolution = resolveBarkAiEntry(
            onboardingActive = false,
            selectedBarkThreadId = "bark_thread_existing",
            barkThreads = listOf(BarkThread(id = "bark_thread_existing", title = "Existing")),
            newThreadId = "bark_thread_new",
            updatedAt = 10L,
        ) as BarkAiEntryResolution.StartNewThread

        assertEquals("bark_thread_new", resolution.selectedThreadId)
        assertEquals("bark_thread_new", resolution.barkThreads.first().id)
    }

    @Test
    fun resolveBarkAiEntry_newThreadNeverRevivesOnboardingId() {
        val resolution = resolveBarkAiEntry(
            onboardingActive = false,
            selectedBarkThreadId = "bark_thread_existing",
            barkThreads = listOf(BarkThread(id = "bark_thread_onboarding", title = "Onboarding")),
            newThreadId = "bark_thread_new",
            updatedAt = 10L,
        ) as BarkAiEntryResolution.StartNewThread

        assertTrue(resolution.barkThreads.none { thread -> thread.id == "bark_thread_onboarding" && thread.id == resolution.selectedThreadId })
        assertEquals("bark_thread_new", resolution.selectedThreadId)
    }

    @Test
    fun resolveBarkAiEntry_replacesExistingThreadWithSameIdAtFront() {
        val resolution = resolveBarkAiEntry(
            onboardingActive = false,
            selectedBarkThreadId = "bark_thread_existing",
            barkThreads = listOf(
                BarkThread(id = "bark_thread_new", title = "Old copy", updatedAt = 1L),
                BarkThread(id = "bark_thread_existing", title = "Existing", updatedAt = 2L),
            ),
            newThreadId = "bark_thread_new",
            updatedAt = 10L,
        ) as BarkAiEntryResolution.StartNewThread

        assertEquals("bark_thread_new", resolution.selectedThreadId)
        assertEquals(2, resolution.barkThreads.size)
        assertEquals("bark_thread_new", resolution.barkThreads.first().id)
        assertEquals("New thread", resolution.barkThreads.first().title)
    }
}
