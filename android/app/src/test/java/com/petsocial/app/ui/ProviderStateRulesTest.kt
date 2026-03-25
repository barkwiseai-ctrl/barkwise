package com.petsocial.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderStateRulesTest {

    @Test
    fun deriveProviderState_providerModeOnNoListings_keepsInboxAllowed() {
        val state = deriveProviderState(
            providerOsSurface = false,
            profileProviderMode = true,
            hasProviderListings = false,
        )

        assertEquals(
            ProviderStateResolution(
                providerModeEnabled = true,
                hasProviderListings = false,
                canLoadProviderInbox = true,
            ),
            state,
        )
    }

    @Test
    fun deriveProviderState_providerModeOffListingsPresent_keepsModeOffButInboxAllowed() {
        val state = deriveProviderState(
            providerOsSurface = false,
            profileProviderMode = false,
            hasProviderListings = true,
        )

        assertEquals(
            ProviderStateResolution(
                providerModeEnabled = false,
                hasProviderListings = true,
                canLoadProviderInbox = true,
            ),
            state,
        )
    }

    @Test
    fun deriveProviderState_providerSurfaceForcesModeAndInboxEvenWithoutListings() {
        val state = deriveProviderState(
            providerOsSurface = true,
            profileProviderMode = false,
            hasProviderListings = false,
        )

        assertEquals(
            ProviderStateResolution(
                providerModeEnabled = true,
                hasProviderListings = false,
                canLoadProviderInbox = true,
            ),
            state,
        )
    }

    @Test
    fun clearedProviderState_ownerSurface_clearsAllProviderFlags() {
        val (providerModeEnabled, hasProviderListings, canLoadProviderInbox) = clearedProviderState(false)
        assertEquals(false, providerModeEnabled)
        assertEquals(false, hasProviderListings)
        assertEquals(false, canLoadProviderInbox)
    }

    @Test
    fun clearedProviderState_providerSurface_keepsCapabilityOnly() {
        val (providerModeEnabled, hasProviderListings, canLoadProviderInbox) = clearedProviderState(true)
        assertEquals(true, providerModeEnabled)
        assertEquals(false, hasProviderListings)
        assertEquals(true, canLoadProviderInbox)
    }
}
