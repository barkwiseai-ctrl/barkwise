package com.petsocial.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageOpenResolutionTest {

    @Test
    fun resolveMessageOpen_prefersExplicitThreadIdWhenThreadExists() {
        val state = UiState(
            activeUserId = "user_eldon",
            messageThreads = listOf(
                messageThread(
                    id = "dm_user_chris_user_eldon",
                    participantUserId = "user_chris",
                ),
            ),
        )

        val resolution = resolveMessageOpen(
            state = state,
            target = MessageTarget(
                userId = "user_chris",
                threadId = "dm_user_chris_user_eldon",
                source = "booking",
            ),
        )

        assertEquals(
            MessageOpenResolution.SelectExistingThread("dm_user_chris_user_eldon"),
            resolution,
        )
    }

    @Test
    fun resolveMessageOpen_fallsBackToUserThreadWhenExplicitThreadMissing() {
        val state = UiState(
            activeUserId = "user_eldon",
            messageThreads = listOf(
                messageThread(
                    id = "dm_user_chris_user_eldon",
                    participantUserId = "user_chris",
                ),
            ),
        )

        val resolution = resolveMessageOpen(
            state = state,
            target = MessageTarget(
                userId = "user_chris",
                threadId = "missing_thread",
                source = "booking",
            ),
        )

        assertEquals(
            MessageOpenResolution.SelectExistingThread("dm_user_chris_user_eldon"),
            resolution,
        )
    }

    @Test
    fun resolveMessageOpen_trimsCanonicalIdsBeforeResolving() {
        val state = UiState(
            activeUserId = "user_eldon",
            messageThreads = listOf(
                messageThread(
                    id = "dm_user_chris_user_eldon",
                    participantUserId = "user_chris",
                ),
            ),
        )

        val resolution = resolveMessageOpen(
            state = state,
            target = MessageTarget(
                userId = " user_chris ",
                threadId = " dm_user_chris_user_eldon ",
                source = "booking",
            ),
        )

        assertEquals(
            MessageOpenResolution.SelectExistingThread("dm_user_chris_user_eldon"),
            resolution,
        )
    }

    @Test
    fun resolveMessageOpen_createsSyntheticThreadWhenOnlyUserIdExists() {
        val state = UiState(
            activeUserId = "user_eldon",
            friendProfiles = listOf(
                FriendProfile(
                    userId = "user_chris",
                    humanName = "Chris",
                    dogName = "Maple",
                    dogPhotoUrl = "avatar",
                    isFriend = true,
                ),
            ),
        )

        val resolution = resolveMessageOpen(
            state = state,
            target = MessageTarget(
                userId = "user_chris",
                source = "booking",
            ),
        )

        val synthetic = resolution as MessageOpenResolution.CreateSyntheticThread
        assertEquals("dm_user_chris_user_eldon", synthetic.selectedThreadId)
        assertEquals(1, synthetic.messageThreads.size)
        assertEquals("user_chris", synthetic.messageThreads.single().participantUserId)
        assertEquals("Chris", synthetic.messageThreads.single().title)
        assertEquals(listOf("Maple"), synthetic.messageThreads.single().participantPetNames)
        assertEquals("Say hi to start chatting", synthetic.messageThreads.single().lastMessage)
    }

    @Test
    fun resolveMessageOpen_returnsNoTargetWhenNoCanonicalIdentityExists() {
        val resolution = resolveMessageOpen(
            state = UiState(activeUserId = "user_eldon"),
            target = MessageTarget(source = "booking"),
        )

        assertEquals(MessageOpenResolution.NoTarget, resolution)
    }

    @Test
    fun resolveMessageOpen_keepsExistingSyntheticThreadStable() {
        val syntheticThread = messageThread(
            id = "dm_user_chris_user_eldon",
            participantUserId = "user_chris",
            lastMessage = "Say hi to start chatting",
        )
        val state = UiState(
            activeUserId = "user_eldon",
            messageThreads = listOf(syntheticThread),
        )

        val resolution = resolveMessageOpen(
            state = state,
            target = MessageTarget(
                userId = "user_chris",
                source = "booking",
            ),
        )

        assertTrue(resolution is MessageOpenResolution.SelectExistingThread)
        assertEquals("dm_user_chris_user_eldon", (resolution as MessageOpenResolution.SelectExistingThread).threadId)
    }

    private fun messageThread(
        id: String,
        participantUserId: String,
        lastMessage: String = "Hello",
    ): MessageThread {
        return MessageThread(
            id = id,
            title = "Thread",
            participantUserId = participantUserId,
            participantAccountLabel = "Participant",
            lastMessage = lastMessage,
            unreadCount = 0,
        )
    }
}
