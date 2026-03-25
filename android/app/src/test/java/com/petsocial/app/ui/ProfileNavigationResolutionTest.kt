package com.petsocial.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileNavigationResolutionTest {

    @Test
    fun resolveProfileNotificationNavigation_onlyAllowsSupportedFilters() {
        val navigation = NavigationState(
            selectedTab = AppTab.Messages,
            selectedMessageThreadId = "thread_1",
            selectedCommunityGroupId = "group_1",
            profileNotificationFilter = "messages",
        )

        val supported = resolveProfileNotificationNavigation(navigation, "Safety")
        val unsupported = resolveProfileNotificationNavigation(navigation, "weird")

        assertEquals(AppTab.Profile, supported.selectedTab)
        assertEquals("safety", supported.profileNotificationFilter)
        assertEquals("thread_1", supported.selectedMessageThreadId)
        assertEquals("group_1", supported.selectedCommunityGroupId)

        assertEquals(AppTab.Profile, unsupported.selectedTab)
        assertEquals("all", unsupported.profileNotificationFilter)
        assertEquals("thread_1", unsupported.selectedMessageThreadId)
        assertEquals("group_1", unsupported.selectedCommunityGroupId)
    }

    @Test
    fun resolveCommunityGroupNavigation_blankIdIsIgnored() {
        val navigation = NavigationState(selectedTab = AppTab.Profile)

        assertNull(resolveCommunityGroupNavigation(navigation, "   "))
    }

    @Test
    fun resolveCommunityGroupNavigation_selectsCommunityAndKeepsExactGroupId() {
        val navigation = NavigationState(
            selectedTab = AppTab.Profile,
            selectedMessageThreadId = "thread_1",
        )

        val resolution = resolveCommunityGroupNavigation(navigation, " group_42 ")

        assertEquals(AppTab.Community, resolution?.selectedTab)
        assertEquals("group_42", resolution?.selectedCommunityGroupId)
        assertEquals("thread_1", resolution?.selectedMessageThreadId)
    }

    @Test
    fun clearSelectedCommunityGroupNavigation_onlyClearsGroupSelection() {
        val navigation = NavigationState(
            selectedTab = AppTab.Messages,
            selectedBarkThreadId = "bark_thread_2",
            selectedMessageThreadId = "thread_1",
            selectedCommunityGroupId = "group_1",
            profileNotificationFilter = "messages",
        )

        val resolution = clearSelectedCommunityGroupNavigation(navigation)

        assertEquals(AppTab.Messages, resolution.selectedTab)
        assertEquals("bark_thread_2", resolution.selectedBarkThreadId)
        assertEquals("thread_1", resolution.selectedMessageThreadId)
        assertNull(resolution.selectedCommunityGroupId)
        assertEquals("messages", resolution.profileNotificationFilter)
    }
}
