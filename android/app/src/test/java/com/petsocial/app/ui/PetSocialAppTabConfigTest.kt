package com.petsocial.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PetSocialAppTabConfigTest {

    @Test
    fun resolveBottomTabs_ownerProdUsesSwappedOrder() {
        assertEquals(
            listOf(
                AppTab.Profile,
                AppTab.Messages,
                AppTab.BarkAI,
                AppTab.Community,
                AppTab.Services,
            ),
            resolveBottomTabs(appSurface = "owner", environment = "prod"),
        )
    }

    @Test
    fun resolveBottomTabs_ownerStagingMatchesProdOrder() {
        assertEquals(
            listOf(
                AppTab.Profile,
                AppTab.Messages,
                AppTab.BarkAI,
                AppTab.Community,
                AppTab.Services,
            ),
            resolveBottomTabs(appSurface = "owner", environment = "staging"),
        )
    }

    @Test
    fun resolveDefaultTab_ownerProdReturnsProfile() {
        assertEquals(
            AppTab.Profile,
            resolveDefaultTab(appSurface = "owner", environment = "prod"),
        )
    }

    @Test
    fun resolveDefaultTab_ownerStagingReturnsProfile() {
        assertEquals(
            AppTab.Profile,
            resolveDefaultTab(appSurface = "owner", environment = "staging"),
        )
    }
}
