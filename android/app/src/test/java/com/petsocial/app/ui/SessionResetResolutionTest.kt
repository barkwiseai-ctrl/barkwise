package com.petsocial.app.ui

import com.petsocial.app.data.AppNotification
import com.petsocial.app.data.CommunityPost
import com.petsocial.app.data.Group
import com.petsocial.app.data.GroupInvite
import com.petsocial.app.data.ServiceProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionResetResolutionTest {

    @Test
    fun applySessionResetResolution_clearsSessionBoundStateButKeepsNonMessageNavigation() {
        val state = UiState(
            providers = listOf(sampleProvider()),
            groups = listOf(Group(id = "group_1", name = "Beach Crew", suburb = "Eldon", memberCount = 5)),
            posts = listOf(CommunityPost(id = "post_1", type = "general", title = "Hi", body = "Body", suburb = "Eldon")),
            ownerBookings = listOf(
                OwnerBooking(
                    id = "booking_1",
                    serviceName = "Walk",
                    providerId = "provider_1",
                    providerUserId = "user_3",
                    threadId = "thread_1",
                    providerAccountLabel = "Chris",
                    date = "2026-03-20",
                    timeSlot = "09:00",
                    status = "pending",
                ),
            ),
            messageThreads = listOf(messageThread()),
            directMessages = listOf(
                DirectMessage(
                    id = "dm_1",
                    threadId = "thread_1",
                    senderUserId = "user_3",
                    recipientUserId = "user_2",
                    body = "Hello",
                ),
            ),
            friendProfiles = listOf(
                FriendProfile(
                    userId = "user_3",
                    humanName = "Chris",
                    dogName = "Maple",
                    dogPhotoUrl = "",
                    isFriend = true,
                ),
            ),
            notifications = listOf(
                AppNotification(
                    id = "notif_1",
                    userId = "user_2",
                    title = "Ping",
                    body = "Body",
                    category = "message",
                    read = false,
                    createdAt = "2026-03-20T00:00:00Z",
                    deepLink = "message:thread_1",
                ),
            ),
            providerModeEnabled = true,
            hasProviderListings = true,
            canLoadProviderInbox = true,
            navigation = NavigationState(
                selectedMessageThreadId = "thread_1",
                selectedCommunityGroupId = "group_1",
                pendingInvite = GroupInvite(
                    token = "abc",
                    groupId = "group_2",
                    groupName = "Park Crew",
                    suburb = "Eldon",
                    inviterUserId = "user_4",
                    expiresAt = "tomorrow",
                    inviteUrl = "url",
                ),
            ),
        )

        val updated = applySessionResetResolution(
            state = state,
            resolution = resolveSessionResetState(
                state = state,
                providerOsSurface = false,
                authRequired = true,
                activeUserId = "user_2",
                toastMessage = "Signed out",
            ),
        )

        assertNull(updated.selectedMessageThreadId)
        assertEquals("group_1", updated.selectedCommunityGroupId)
        assertNull(updated.pendingInvite)
        assertTrue(updated.authRequired)
        assertFalse(updated.providerModeEnabled)
        assertFalse(updated.hasProviderListings)
        assertFalse(updated.canLoadProviderInbox)
        assertTrue(updated.providers.isEmpty())
        assertTrue(updated.groups.isEmpty())
        assertTrue(updated.posts.isEmpty())
        assertTrue(updated.ownerBookings.isEmpty())
        assertTrue(updated.messageThreads.isEmpty())
        assertTrue(updated.directMessages.isEmpty())
        assertTrue(updated.friendProfiles.isEmpty())
        assertTrue(updated.notifications.isEmpty())
        assertEquals("Signed out", updated.toastMessage)
    }

    @Test
    fun resolveSessionResetState_providerSurfaceRetainsProviderCapabilityOnly() {
        val updated = applySessionResetResolution(
            state = UiState(
                navigation = NavigationState(selectedMessageThreadId = "thread_1"),
            ),
            resolution = resolveSessionResetState(
                state = UiState(
                    navigation = NavigationState(selectedMessageThreadId = "thread_1"),
                ),
                providerOsSurface = true,
                authRequired = false,
                activeUserId = "user_2",
                toastMessage = "Reset",
            ),
        )

        assertTrue(updated.providerModeEnabled)
        assertFalse(updated.hasProviderListings)
        assertTrue(updated.canLoadProviderInbox)
        assertFalse(updated.authRequired)
        assertNull(updated.selectedMessageThreadId)
        assertEquals("Reset", updated.toastMessage)
    }

    @Test
    fun resolveSessionResetState_recomputesModeratorFlagForTargetUser() {
        val moderatorUserId = "user_3"
        val updated = applySessionResetResolution(
            state = UiState(activeUserId = moderatorUserId),
            resolution = resolveSessionResetState(
                state = UiState(activeUserId = "user_2"),
                providerOsSurface = false,
                authRequired = false,
                activeUserId = moderatorUserId,
                toastMessage = "Account deleted",
            ),
        )

        assertTrue(updated.isCommunityModerator)
    }

    private fun sampleProvider(): ServiceProvider {
        return ServiceProvider(
            id = "provider_1",
            name = "Chris",
            category = "walking",
            suburb = "Eldon",
            rating = 4.8,
            reviewCount = 12,
            priceFrom = 40,
            description = "Reliable walks",
        )
    }

    private fun messageThread(): MessageThread {
        return MessageThread(
            id = "thread_1",
            title = "Chris",
            participantUserId = "user_3",
            participantAccountLabel = "Chris",
            lastMessage = "Hello",
            unreadCount = 1,
        )
    }
}
