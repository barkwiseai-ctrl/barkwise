package com.petsocial.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationRoutingTest {

    @Test
    fun resolveNotificationRoute_group_selectsCommunityGroup() {
        assertEquals(
            NotificationRoute(
                tab = AppTab.Community,
                selectedCommunityGroupId = "group_1",
            ),
            resolveNotificationRoute("group:group_1"),
        )
    }

    @Test
    fun resolveNotificationRoute_provider_opensServicesWithProviderId() {
        assertEquals(
            NotificationRoute(
                tab = AppTab.Services,
                providerId = "provider_1",
            ),
            resolveNotificationRoute("provider:provider_1"),
        )
    }

    @Test
    fun resolveNotificationRoute_event_andPost_forceCommunityReload() {
        assertEquals(
            NotificationRoute(
                tab = AppTab.Community,
                shouldReload = true,
            ),
            resolveNotificationRoute("event:event_1"),
        )
        assertEquals(
            NotificationRoute(
                tab = AppTab.Community,
                shouldReload = true,
            ),
            resolveNotificationRoute("post:post_1"),
        )
    }

    @Test
    fun resolveNotificationRoute_trimsWhitespaceAndBlankIdsToNull() {
        assertEquals(
            NotificationRoute(
                tab = AppTab.Community,
                selectedCommunityGroupId = "group_1",
            ),
            resolveNotificationRoute("  group:group_1  "),
        )
        assertEquals(
            NotificationRoute(
                tab = AppTab.Services,
                providerId = null,
            ),
            resolveNotificationRoute("provider:   "),
        )
    }

    @Test
    fun resolveNotificationRoute_message_opensMessages() {
        assertEquals(
            NotificationRoute(tab = AppTab.Messages),
            resolveNotificationRoute("message:thread_1"),
        )
    }

    @Test
    fun resolveNotificationRoute_profile_opensProfile() {
        assertEquals(
            NotificationRoute(tab = AppTab.Profile),
            resolveNotificationRoute("profile"),
        )
    }

    @Test
    fun resolveNotificationRoute_unknown_fallsBackToCommunity() {
        assertEquals(
            NotificationRoute(tab = AppTab.Community),
            resolveNotificationRoute("something:else"),
        )
    }
}
