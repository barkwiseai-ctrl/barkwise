package com.petsocial.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageAndSessionInvariantsTest {

    @Test
    fun mergeLocalMessageThreads_preservesSyntheticThreadAcrossRefresh() {
        val localThread = MessageThread(
            id = "dm_user_2_user_9",
            title = "Chris",
            participantUserId = "user_9",
            participantAccountLabel = "Chris",
            lastMessage = "Say hi to start chatting",
            unreadCount = 0,
        )
        val refreshedThread = MessageThread(
            id = "dm_user_2_user_3",
            title = "Anika",
            participantUserId = "user_3",
            participantAccountLabel = "Anika",
            lastMessage = "See you soon",
            unreadCount = 1,
        )

        val merged = mergeLocalMessageThreads(
            currentThreads = listOf(localThread),
            refreshedThreads = listOf(refreshedThread),
        )

        assertEquals(2, merged.size)
        assertTrue(merged.any { thread -> thread.id == "dm_user_2_user_9" })
        assertTrue(merged.any { thread -> thread.id == "dm_user_2_user_3" })
    }

    @Test
    fun mergeLocalMessageThreads_doesNotDuplicateWhenServerThreadArrives() {
        val localThread = MessageThread(
            id = "dm_user_2_user_9",
            title = "Chris",
            participantUserId = "user_9",
            participantAccountLabel = "Chris",
            lastMessage = "Say hi to start chatting",
            unreadCount = 0,
        )
        val serverThread = MessageThread(
            id = "dm_user_2_user_9",
            title = "Walk booking",
            participantUserId = "user_9",
            participantAccountLabel = "Chris",
            lastMessage = "Can do 9am",
            unreadCount = 1,
        )

        val merged = mergeLocalMessageThreads(
            currentThreads = listOf(localThread),
            refreshedThreads = listOf(serverThread),
        )

        assertEquals(1, merged.size)
        assertEquals("Can do 9am", merged.single().lastMessage)
    }

    @Test
    fun mergeLocalMessageThreads_doesNotPreserveNonSyntheticThread() {
        val nonSyntheticThread = MessageThread(
            id = "dm_user_2_user_9",
            title = "Chris",
            participantUserId = "user_9",
            participantAccountLabel = "Chris",
            lastMessage = "Real previous message",
            unreadCount = 0,
        )

        val merged = mergeLocalMessageThreads(
            currentThreads = listOf(nonSyntheticThread),
            refreshedThreads = emptyList(),
        )

        assertTrue(merged.isEmpty())
    }

    @Test
    fun clearedProviderState_resetsListingsAndInboxForOwnerSurface() {
        val (providerModeEnabled, hasProviderListings, canLoadProviderInbox) = clearedProviderState(
            providerOsSurface = false,
        )

        assertFalse(providerModeEnabled)
        assertFalse(hasProviderListings)
        assertFalse(canLoadProviderInbox)
    }

    @Test
    fun clearedProviderState_keepsProviderSurfaceCapabilityOnly() {
        val (providerModeEnabled, hasProviderListings, canLoadProviderInbox) = clearedProviderState(
            providerOsSurface = true,
        )

        assertTrue(providerModeEnabled)
        assertFalse(hasProviderListings)
        assertTrue(canLoadProviderInbox)
    }
}
