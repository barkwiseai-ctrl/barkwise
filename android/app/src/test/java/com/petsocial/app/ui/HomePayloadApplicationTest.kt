package com.petsocial.app.ui

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
}
