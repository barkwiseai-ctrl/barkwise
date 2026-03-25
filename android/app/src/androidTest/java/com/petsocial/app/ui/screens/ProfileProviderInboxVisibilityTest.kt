package com.petsocial.app.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.petsocial.app.ui.ProfileInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petsocial.app.testing.ComposeTestActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileProviderInboxVisibilityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun providerInbox_hiddenWhenProviderModeOff() {
        composeRule.setContent {
            MaterialTheme {
                profileScreenForTest(
                    providerModeEnabled = false,
                    hasProviderListings = false,
                    canLoadProviderInbox = false,
                )
            }
        }

        composeRule.onNodeWithText("Listings").performClick()
        composeRule.onNodeWithText("Turn on Provider mode to manage quotes and bookings here.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun providerInbox_emptyStateExplainsNeedForListings() {
        composeRule.setContent {
            MaterialTheme {
                profileScreenForTest(
                    providerModeEnabled = true,
                    hasProviderListings = false,
                    canLoadProviderInbox = true,
                )
            }
        }

        composeRule.onNodeWithText("Listings").performClick()
        composeRule.onNodeWithText("Create first listing").assertIsDisplayed()
    }

    @Test
    fun providerInbox_emptyStateExplainsWaitingForActivity() {
        composeRule.setContent {
            MaterialTheme {
                profileScreenForTest(
                    providerModeEnabled = false,
                    hasProviderListings = true,
                    canLoadProviderInbox = true,
                )
            }
        }

        composeRule.onNodeWithText("Listings").performClick()
        composeRule.onNodeWithText("Provider inbox will appear when your listings receive quotes or bookings.").performScrollTo().assertIsDisplayed()
    }
}
