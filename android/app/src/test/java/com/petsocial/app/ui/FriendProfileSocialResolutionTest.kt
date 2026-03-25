package com.petsocial.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendProfileSocialResolutionTest {

    @Test
    fun resolveFriendQrTokenInput_blankTokenIsInvalid() {
        assertEquals(
            FriendQrTokenResolution.Invalid("Invalid friend QR"),
            resolveFriendQrTokenInput("   "),
        )
    }

    @Test
    fun resolveFriendMutation_selfAddReturnsProfileQrToastWithoutMutatingState() {
        val state = UiState(
            activeUserId = "user_2",
            friendProfiles = listOf(friendProfile(userId = "user_3", isFriend = true)),
            messageThreads = listOf(messageThread(userId = "user_3")),
        )

        val resolution = resolveFriendMutation(
            state = state,
            action = FriendMutationAction.AddOrUpdate,
            userId = " user_2 ",
            humanName = "Snowy",
            dogName = "Milo",
        )

        assertEquals(
            FriendMutationResolution.ToastOnly("This is your profile QR"),
            resolution,
        )
    }

    @Test
    fun resolveFriendMutation_newFriendCreatesSingleSyntheticThread() {
        val state = UiState(activeUserId = "user_2")

        val resolution = resolveFriendMutation(
            state = state,
            action = FriendMutationAction.AddOrUpdate,
            userId = " user_9 ",
            humanName = "Chris",
            dogName = "Maple",
        ) as FriendMutationResolution.StateUpdate

        assertEquals("Friend added", resolution.toastMessage)
        assertEquals(1, resolution.friendProfiles.size)
        assertEquals("user_9", resolution.friendProfiles.single().userId)
        assertEquals(1, resolution.messageThreads.size)
        assertEquals("user_9", resolution.messageThreads.single().participantUserId)
        assertEquals("Say hi to start chatting", resolution.messageThreads.single().lastMessage)
    }

    @Test
    fun resolveFriendMutation_reusesExistingSyntheticThreadForNewFriend() {
        val existingThread = messageThread(userId = "user_9", lastMessage = "Say hi to start chatting")
        val state = UiState(
            activeUserId = "user_2",
            messageThreads = listOf(existingThread),
        )

        val resolution = resolveFriendMutation(
            state = state,
            action = FriendMutationAction.AddOrUpdate,
            userId = "user_9",
            humanName = "Chris",
            dogName = "Maple",
        ) as FriendMutationResolution.StateUpdate

        assertEquals(1, resolution.messageThreads.size)
        assertEquals(existingThread.id, resolution.messageThreads.single().id)
    }

    @Test
    fun buildFriendProfiles_marksThreadParticipantsAsFriendsForBothSides() {
        val profiles = buildFriendProfiles(
            activeUserId = "user_2",
            messageThreads = listOf(messageThread(userId = "user_9")),
            existingProfiles = listOf(friendProfile(userId = "user_3", isFriend = true)),
        )

        val threadedFriend = profiles.first { it.userId == "user_9" }
        assertTrue(threadedFriend.isFriend)
    }

    @Test
    fun resolveFriendMutation_existingFriendUpgradesWithoutDuplicates() {
        val state = UiState(
            activeUserId = "user_2",
            friendProfiles = listOf(
                friendProfile(
                    userId = "user_9",
                    humanName = "Old Name",
                    dogName = "Old Dog",
                    isFriend = false,
                ),
            ),
            messageThreads = listOf(messageThread(userId = "user_9")),
        )

        val resolution = resolveFriendMutation(
            state = state,
            action = FriendMutationAction.AddOrUpdate,
            userId = "user_9",
            humanName = "Chris",
            dogName = "Maple",
        ) as FriendMutationResolution.StateUpdate

        assertEquals(1, resolution.friendProfiles.size)
        assertEquals(true, resolution.friendProfiles.single().isFriend)
        assertEquals("Chris", resolution.friendProfiles.single().humanName)
        assertEquals("Maple", resolution.friendProfiles.single().dogName)
        assertEquals(1, resolution.messageThreads.size)
    }

    @Test
    fun resolveFriendMutation_removeOnlyFlipsFriendStateAndKeepsThread() {
        val state = UiState(
            activeUserId = "user_2",
            friendProfiles = listOf(friendProfile(userId = "user_9", isFriend = true)),
            messageThreads = listOf(messageThread(userId = "user_9")),
        )

        val resolution = resolveFriendMutation(
            state = state,
            action = FriendMutationAction.Remove,
            userId = " user_9 ",
        ) as FriendMutationResolution.StateUpdate

        assertEquals("Friend removed", resolution.toastMessage)
        assertEquals(false, resolution.friendProfiles.single().isFriend)
        assertEquals(1, resolution.messageThreads.size)
        assertEquals("user_9", resolution.messageThreads.single().participantUserId)
    }

    @Test
    fun resolveProfileSocialMessageTarget_staysOnCanonicalMessagingPath() {
        assertEquals(
            MessageTarget(
                userId = "user_9",
                source = "profile_social",
            ),
            resolveProfileSocialMessageTarget("user_9"),
        )
    }

    @Test
    fun resolveFriendQrVerificationFailure_mapsExpectedStatuses() {
        assertEquals("Friend QR expired. Ask for a new one.", resolveFriendQrVerificationFailure(401))
        assertEquals("This is your profile QR", resolveFriendQrVerificationFailure(409))
        assertEquals("Unable to verify friend QR", resolveFriendQrVerificationFailure(500))
    }

    private fun friendProfile(
        userId: String,
        humanName: String = "Chris",
        dogName: String = "Maple",
        isFriend: Boolean,
    ): FriendProfile {
        return FriendProfile(
            userId = userId,
            humanName = humanName,
            dogName = dogName,
            dogPhotoUrl = "avatar",
            isFriend = isFriend,
        )
    }

    private fun messageThread(
        userId: String,
        lastMessage: String = "Hello",
    ): MessageThread {
        return MessageThread(
            id = "dm_user_2_${userId}",
            title = "Thread",
            participantUserId = userId,
            participantAccountLabel = "Participant",
            lastMessage = lastMessage,
            unreadCount = 0,
        )
    }
}
