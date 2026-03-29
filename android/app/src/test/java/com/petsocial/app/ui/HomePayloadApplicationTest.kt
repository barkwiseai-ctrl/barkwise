package com.petsocial.app.ui

import com.petsocial.app.data.ApiDirectMessage
import com.petsocial.app.data.ApiMessageThread
import com.petsocial.app.data.AppNotification
import com.petsocial.app.data.CommunityReport
import com.petsocial.app.data.HomeCacheSnapshot
import com.petsocial.app.data.ProfileInfoCacheSnapshot
import com.petsocial.app.data.ProviderInboxItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomePayloadApplicationTest {

    @Test
    fun resolveHomeLoadIndicatorState_preservesCurrentIndicatorsForSilentRefresh() {
        val resolution = resolveHomeLoadIndicatorState(
            showLoadingIndicators = false,
            shouldLoadProviderInbox = true,
            currentLoading = false,
            currentLoadingProviderInbox = false,
        )

        assertEquals(false, resolution.loading)
        assertEquals(false, resolution.loadingProviderInbox)
    }

    @Test
    fun resolveHomeLoadIndicatorState_enablesIndicatorsForForegroundRefresh() {
        val resolution = resolveHomeLoadIndicatorState(
            showLoadingIndicators = true,
            shouldLoadProviderInbox = true,
            currentLoading = false,
            currentLoadingProviderInbox = false,
        )

        assertEquals(true, resolution.loading)
        assertEquals(true, resolution.loadingProviderInbox)
    }

    @Test
    fun resolveHomePayloadState_withListings_setsHasProviderListingsWithoutForcingMode() {
        val resolution = resolveHomePayloadState(
            selectedMessageThreadId = "thread_1",
            validMessageThreadIds = listOf("thread_1"),
            selectedCommunityGroupId = "group_1",
            validGroupIds = listOf("group_1"),
            providerOsSurface = false,
            profileProviderMode = false,
            hasProviderListings = true,
        )

        assertEquals(false, resolution.providerState.providerModeEnabled)
        assertEquals(true, resolution.providerState.hasProviderListings)
        assertEquals(true, resolution.providerState.canLoadProviderInbox)
        assertEquals("thread_1", resolution.selectedMessageThreadId)
        assertEquals("group_1", resolution.selectedCommunityGroupId)
    }

    @Test
    fun resolveHomePayloadState_dropsInvalidSelections() {
        val resolution = resolveHomePayloadState(
            selectedMessageThreadId = "missing_thread",
            validMessageThreadIds = listOf("thread_1"),
            selectedCommunityGroupId = "missing_group",
            validGroupIds = listOf("group_1"),
            providerOsSurface = false,
            profileProviderMode = true,
            hasProviderListings = false,
        )

        assertNull(resolution.selectedMessageThreadId)
        assertNull(resolution.selectedCommunityGroupId)
        assertEquals(true, resolution.providerState.providerModeEnabled)
        assertEquals(false, resolution.providerState.hasProviderListings)
        assertEquals(true, resolution.providerState.canLoadProviderInbox)
    }

    @Test
    fun resolveHomePayloadState_preservesEachValidSelectionIndependently() {
        val resolution = resolveHomePayloadState(
            selectedMessageThreadId = "thread_1",
            validMessageThreadIds = listOf("thread_1"),
            selectedCommunityGroupId = "missing_group",
            validGroupIds = listOf("group_1"),
            providerOsSurface = true,
            profileProviderMode = false,
            hasProviderListings = false,
        )

        assertEquals("thread_1", resolution.selectedMessageThreadId)
        assertNull(resolution.selectedCommunityGroupId)
        assertEquals(true, resolution.providerState.providerModeEnabled)
        assertEquals(false, resolution.providerState.hasProviderListings)
        assertEquals(true, resolution.providerState.canLoadProviderInbox)
    }

    @Test
    fun homePayloadFromCacheSnapshot_restoresPersistedSessionData() {
        val snapshot = HomeCacheSnapshot(
            providers = emptyList(),
            nearbyPetBusinesses = emptyList(),
            groups = emptyList(),
            posts = emptyList(),
            events = emptyList(),
            ownerBookings = emptyList(),
            providerBookings = emptyList(),
            calendarEvents = emptyList(),
            providerInboxItems = listOf(
                ProviderInboxItem(
                    id = "inbox_1",
                    itemType = "booking",
                    providerId = "provider_1",
                    providerName = "Alex Walks",
                    status = "pending",
                    title = "Booking request",
                    subtitle = "Tomorrow 9:00",
                    createdAt = "2026-03-29T09:00:00Z",
                ),
            ),
            messageThreads = listOf(
                ApiMessageThread(
                    id = "dm_user_2_user_9",
                    participantUserId = "user_9",
                    lastMessage = "See you soon",
                    lastMessageAt = "2026-03-29T10:00:00Z",
                    unreadCount = 1,
                ),
            ),
            selectedMessageThreadId = "dm_user_2_user_9",
            selectedThreadMessages = listOf(
                ApiDirectMessage(
                    id = "msg_1",
                    threadId = "dm_user_2_user_9",
                    senderUserId = "user_9",
                    recipientUserId = "user_2",
                    body = "See you soon",
                    createdAt = "2026-03-29T10:00:00Z",
                ),
            ),
            notifications = listOf(
                AppNotification(
                    id = "notif_1",
                    userId = "user_2",
                    title = "New message",
                    body = "Maple sent a message",
                    category = "message",
                    read = false,
                    createdAt = "2026-03-29T10:05:00Z",
                    deepLink = "message:dm_user_2_user_9",
                ),
            ),
            profileInfo = ProfileInfoCacheSnapshot(
                displayName = "Alex",
                email = "alex@barkwise.test",
                dogName = "Milo",
                suburb = "Richmond",
            ),
            blockedUserIds = listOf("user_blocked"),
            moderationReports = listOf(
                CommunityReport(
                    id = "report_1",
                    reporterUserId = "user_2",
                    targetType = "post",
                    targetId = "post_1",
                    reason = "Spam",
                    status = "open",
                    createdAt = "2026-03-29T10:10:00Z",
                ),
            ),
        )

        val payload = homePayloadFromCacheSnapshot(
            snapshot = snapshot,
            state = UiState(activeUserId = "user_2"),
            suburb = "Surry Hills",
        )

        assertEquals("Alex", payload.profileInfo.displayName)
        assertEquals("Richmond", payload.profileInfo.suburb)
        assertEquals("Milo", payload.profileInfo.dogName)
        assertEquals("booking", payload.providerInboxItems.single().itemType)
        assertEquals("dm_user_2_user_9", payload.selectedMessageThreadId)
        assertEquals("msg_1", payload.selectedThreadMessages.single().id)
        assertEquals("notif_1", payload.notifications.single().id)
        assertEquals(listOf("user_blocked"), payload.blockedUserIds)
        assertEquals("report_1", payload.moderationReports.single().id)
    }
}
