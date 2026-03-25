package com.petsocial.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationOpenResolutionTest {

    @Test
    fun resolveNotificationOpen_blankLinkDoesNothing() {
        val resolution = resolveNotificationOpen(
            deepLink = "   ",
            currentPostsSortBy = "recent",
            notificationRead = false,
        )

        assertNull(resolution.route)
        assertEquals("recent", resolution.postsSortBy)
        assertFalse(resolution.shouldMarkRead)
        assertFalse(resolution.shouldReloadHome)
        assertNull(resolution.providerIdToLoad)
    }

    @Test
    fun resolveNotificationOpen_providerLinkLoadsProviderWithoutReload() {
        val resolution = resolveNotificationOpen(
            deepLink = "provider:provider_1",
            currentPostsSortBy = "recent",
            notificationRead = false,
        )

        assertEquals(AppTab.Services, resolution.route?.tab)
        assertEquals("provider_1", resolution.providerIdToLoad)
        assertEquals("recent", resolution.postsSortBy)
        assertTrue(resolution.shouldMarkRead)
        assertFalse(resolution.shouldReloadHome)
    }

    @Test
    fun resolveNotificationOpen_eventLinkResetsCommunitySortAndReloads() {
        val resolution = resolveNotificationOpen(
            deepLink = "event:event_1",
            currentPostsSortBy = "distance",
            notificationRead = false,
        )

        assertEquals(AppTab.Community, resolution.route?.tab)
        assertEquals("relevance", resolution.postsSortBy)
        assertTrue(resolution.shouldReloadHome)
        assertTrue(resolution.shouldMarkRead)
        assertNull(resolution.providerIdToLoad)
    }

    @Test
    fun resolveNotificationOpen_alreadyReadNotificationDoesNotMarkAgain() {
        val resolution = resolveNotificationOpen(
            deepLink = "profile",
            currentPostsSortBy = "recent",
            notificationRead = true,
        )

        assertEquals(AppTab.Profile, resolution.route?.tab)
        assertFalse(resolution.shouldMarkRead)
    }
}
