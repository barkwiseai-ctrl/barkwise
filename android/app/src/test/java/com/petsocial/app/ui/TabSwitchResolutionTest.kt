package com.petsocial.app.ui

import com.petsocial.app.data.AppNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabSwitchResolutionTest {

    @Test
    fun resolveTabSwitchState_messagesPreservesSelectedThreadAndDirectMessages() {
        val resolution = resolveTabSwitchState(
            navigation = NavigationState(
                selectedTab = AppTab.Profile,
                selectedMessageThreadId = "thread_1",
                selectedCommunityGroupId = "group_1",
                profileNotificationFilter = "messages",
            ),
            tab = AppTab.Messages,
            directMessages = listOf(
                DirectMessage(
                    id = "dm_1",
                    threadId = "thread_1",
                    senderUserId = "user_3",
                    recipientUserId = "user_2",
                    body = "Hello",
                ),
            ),
            notifications = listOf(
                notification(id = "msg_1", category = "message"),
                notification(id = "community_1", category = "community_alert"),
            ),
            acknowledgedCommunityNotificationIds = setOf("existing_community"),
            acknowledgedMessageNotificationIds = setOf("existing_message"),
        )

        assertEquals(AppTab.Messages, resolution.navigation.selectedTab)
        assertEquals("thread_1", resolution.navigation.selectedMessageThreadId)
        assertNull(resolution.navigation.selectedCommunityGroupId)
        assertEquals("all", resolution.navigation.profileNotificationFilter)
        assertEquals(1, resolution.directMessages.size)
        assertEquals(setOf("existing_message", "msg_1"), resolution.acknowledgedMessageNotificationIds)
        assertEquals(setOf("existing_community"), resolution.acknowledgedCommunityNotificationIds)
    }

    @Test
    fun resolveTabSwitchState_communityKeepsGroupSelectionAndAcknowledgesOnlyCommunityItems() {
        val resolution = resolveTabSwitchState(
            navigation = NavigationState(
                selectedTab = AppTab.Profile,
                selectedMessageThreadId = "thread_1",
                selectedCommunityGroupId = "group_1",
                profileNotificationFilter = "community",
            ),
            tab = AppTab.Community,
            directMessages = emptyList(),
            notifications = listOf(
                notification(id = "community_1", category = "community_alert"),
                notification(id = "group_1", category = "group_invite"),
                notification(id = "message_1", category = "message"),
            ),
            acknowledgedCommunityNotificationIds = setOf("existing_community"),
            acknowledgedMessageNotificationIds = setOf("existing_message"),
        )

        assertEquals(AppTab.Community, resolution.navigation.selectedTab)
        assertEquals("thread_1", resolution.navigation.selectedMessageThreadId)
        assertEquals("group_1", resolution.navigation.selectedCommunityGroupId)
        assertEquals("all", resolution.navigation.profileNotificationFilter)
        assertEquals(
            setOf("existing_community", "community_1", "group_1"),
            resolution.acknowledgedCommunityNotificationIds,
        )
        assertEquals(setOf("existing_message"), resolution.acknowledgedMessageNotificationIds)
    }

    @Test
    fun resolveTabSwitchState_profileKeepsFilterWithoutAcknowledgingNotifications() {
        val resolution = resolveTabSwitchState(
            navigation = NavigationState(
                selectedTab = AppTab.Messages,
                selectedMessageThreadId = "thread_1",
                profileNotificationFilter = "safety",
            ),
            tab = AppTab.Profile,
            directMessages = emptyList(),
            notifications = listOf(notification(id = "message_1", category = "message")),
            acknowledgedCommunityNotificationIds = setOf("existing_community"),
            acknowledgedMessageNotificationIds = setOf("existing_message"),
        )

        assertEquals(AppTab.Profile, resolution.navigation.selectedTab)
        assertEquals("thread_1", resolution.navigation.selectedMessageThreadId)
        assertEquals("safety", resolution.navigation.profileNotificationFilter)
        assertEquals(setOf("existing_community"), resolution.acknowledgedCommunityNotificationIds)
        assertEquals(setOf("existing_message"), resolution.acknowledgedMessageNotificationIds)
    }

    private fun notification(id: String, category: String): AppNotification {
        return AppNotification(
            id = id,
            userId = "user_2",
            title = "Title",
            body = "Body",
            category = category,
            read = false,
            createdAt = "2026-03-20T00:00:00Z",
        )
    }
}
