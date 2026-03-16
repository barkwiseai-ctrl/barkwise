package com.petsocial.app.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petsocial.app.data.ServiceProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServicesScreenRegressionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun servicesList_mapToggle_keepsListScrollable() {
        composeRule.setContent {
            var viewMode by mutableStateOf("list")
            MaterialTheme {
                ServicesScreen(
                    providers = sampleProviders(count = 24),
                    nearbyPetBusinesses = emptyList(),
                    groomerPetRosters = emptyMap(),
                    recommendationSuburb = "Melbourne",
                    recommendationSource = "explicit_suburb",
                    selectedCategory = null,
                    viewMode = viewMode,
                    searchQuery = "",
                    sortBy = "relevance",
                    loading = false,
                    selectedDetails = null,
                    availableSlots = emptyList(),
                    availabilityDate = null,
                    minRating = null,
                    maxDistanceKm = null,
                    onChangeViewMode = { mode -> viewMode = mode },
                    onCategorySelect = {},
                    onSearchQueryChange = {},
                    onSortByChange = {},
                    onFilterChange = { _, _ -> },
                    onRequestQuote = { _, _, _, _ -> },
                    onBook = { _, _, _, _ -> },
                    onViewDetails = {},
                    onLoadAvailability = { _, _ -> },
                    onCloseDetails = {},
                )
            }
        }

        composeRule.onNodeWithTag("services_results_list").assertIsDisplayed()
        composeRule.onAllNodesWithTag("services_map_panel").assertCountEquals(0)

        composeRule.onNodeWithTag("services_view_mode_map_chip").performClick()
        composeRule.onNodeWithTag("services_map_panel").assertIsDisplayed()

        composeRule.onNodeWithTag("services_view_mode_list_chip").performClick()
        composeRule.onAllNodesWithTag("services_map_panel").assertCountEquals(0)

        composeRule.onNodeWithText("Provider 24").performScrollTo().assertIsDisplayed()
    }
}

private fun sampleProviders(count: Int): List<ServiceProvider> = (1..count).map { index ->
    ServiceProvider(
        id = "provider_$index",
        name = "Provider ${index.toString().padStart(2, '0')}",
        category = if (index % 2 == 0) "grooming" else "dog_walking",
        suburb = "Melbourne",
        rating = 4.2,
        reviewCount = 12,
        priceFrom = 40 + index,
        description = "Reliable local service",
        latitude = -37.8136 + (index * 0.001),
        longitude = 144.9631 + (index * 0.001),
        distanceKm = 1.0 + (index * 0.1),
        ownerLabel = "Owner $index",
    )
}
