package com.petsocial.app.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petsocial.app.testing.ComposeTestActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThenVsNowFlowHappyPathTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun thenVsNow_happyPath_reachesResultState() {
        composeRule.setContent {
            MaterialTheme {
                ThenVsNowFlow(
                    onClose = {},
                    initialPetName = "Milo",
                    initialBirthdayInput = "2024-01-01",
                    initialThenPhotoUri = "content://then-photo",
                    initialNowPhotoUri = "content://now-photo",
                    generationStepDelayMs = 10,
                )
            }
        }

        composeRule.onNodeWithTag("then_vs_now_create_card_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("then_vs_now_generate_card_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("then_vs_now_generating_state").assertIsDisplayed()

        composeRule.waitUntil(timeoutMillis = 2_000) {
            composeRule.onAllNodesWithTag("then_vs_now_result_state").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("then_vs_now_result_state").assertIsDisplayed()
        composeRule.onNodeWithTag("then_vs_now_share_button").assertIsDisplayed()
    }
}
