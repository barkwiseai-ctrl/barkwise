package com.petsocial.app.ui

import com.petsocial.app.data.ServiceProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookingAndQuoteRequestResolutionTest {

    @Test
    fun resolveBookingRequest_requiresProviderDateAndTime() {
        assertEquals(
            BookingRequestResolution.Toast("Select a provider, date, and time slot"),
            resolveBookingRequest(
                state = UiState(activeUserId = "user_2"),
                providerId = " ",
                date = "2026-03-21",
                timeSlot = "09:00",
                note = "Note",
            ),
        )
    }

    @Test
    fun resolveBookingRequest_trimsInputsAndComputesApprovalHint() {
        val resolution = resolveBookingRequest(
            state = UiState(
                activeUserId = "user_2",
                providers = listOf(
                    ServiceProvider(
                        id = "provider_1",
                        name = "Chris Walks",
                        category = "walking",
                        suburb = "Eldon",
                        rating = 4.8,
                        reviewCount = 12,
                        priceFrom = 40,
                        description = "Reliable",
                        ownerUserId = "user_9",
                    ),
                ),
            ),
            providerId = " provider_1 ",
            date = " 2026-03-21 ",
            timeSlot = " 09:00 ",
            note = "  Need help with Maple  ",
        ) as BookingRequestResolution.Submit

        assertEquals("provider_1", resolution.providerId)
        assertEquals("2026-03-21", resolution.date)
        assertEquals("09:00", resolution.timeSlot)
        assertEquals("Need help with Maple", resolution.note)
        assertEquals(" Switch to user_9 to approve this booking.", resolution.approvalHint)
    }

    @Test
    fun resolveBookingRequest_withoutProviderOwnerLeavesHintBlank() {
        val resolution = resolveBookingRequest(
            state = UiState(activeUserId = "user_2"),
            providerId = "provider_1",
            date = "2026-03-21",
            timeSlot = "09:00",
            note = "",
        ) as BookingRequestResolution.Submit

        assertEquals("", resolution.approvalHint)
    }

    @Test
    fun resolveServiceQuoteRequest_requiresCategoryWindowAndPetDetails() {
        assertEquals(
            ServiceQuoteRequestResolution.Toast("Complete category, preferred window, and pet details"),
            resolveServiceQuoteRequest(
                selectedSuburb = "Eldon",
                stagingTestBuild = true,
                category = " ",
                preferredWindow = "Morning",
                petDetails = "Large dog",
                note = "Note",
            ),
        )
    }

    @Test
    fun resolveServiceQuoteRequest_trimsNormalizesAndIncludesSuburbWhenAvailable() {
        val staging = resolveServiceQuoteRequest(
            selectedSuburb = "Eldon",
            stagingTestBuild = true,
            category = " walking ",
            preferredWindow = " morning ",
            petDetails = " energetic dog ",
            note = "  friendly  ",
        ) as ServiceQuoteRequestResolution.Submit
        val prod = resolveServiceQuoteRequest(
            selectedSuburb = "Eldon",
            stagingTestBuild = false,
            category = "dog walking",
            preferredWindow = "morning",
            petDetails = "energetic dog",
            note = "friendly",
        ) as ServiceQuoteRequestResolution.Submit

        assertEquals("dog_walking", staging.category)
        assertEquals("morning", staging.preferredWindow)
        assertEquals("energetic dog", staging.petDetails)
        assertEquals("friendly", staging.note)
        assertEquals("Eldon", staging.suburb)
        assertEquals("dog_walking", prod.category)
        assertEquals("Eldon", prod.suburb)
    }

    @Test
    fun resolveServiceQuoteSuburb_prefersVisibleProviderSuburbForSelectedCategory() {
        val suburb = resolveServiceQuoteSuburb(
            selectedSuburb = "Surry Hills",
            currentLocationSuburb = null,
            profileSuburb = "Carlton",
            recommendationSuburb = "Fitzroy",
            providers = listOf(
                ServiceProvider(
                    id = "provider_1",
                    name = "Chris Walks",
                    category = "dog_walking",
                    suburb = "Eldon",
                    rating = 4.8,
                    reviewCount = 12,
                    priceFrom = 40,
                    description = "Reliable",
                    ownerUserId = "user_9",
                ),
                ServiceProvider(
                    id = "provider_2",
                    name = "Chris Grooms",
                    category = "grooming",
                    suburb = "Richmond",
                    rating = 4.7,
                    reviewCount = 9,
                    priceFrom = 55,
                    description = "Gentle",
                    ownerUserId = "user_8",
                ),
            ),
            category = "dog walking",
        )

        assertEquals("Eldon", suburb)
    }

    @Test
    fun resolveServiceQuoteSuburb_fallsBackWithoutGps() {
        val suburb = resolveServiceQuoteSuburb(
            selectedSuburb = "",
            currentLocationSuburb = null,
            profileSuburb = "Carlton",
            recommendationSuburb = "Fitzroy",
            providers = emptyList(),
            category = "grooming",
        )

        assertEquals("Fitzroy", suburb)
    }

    @Test
    fun resolveServiceQuoteSuburb_returnsNullWhenNoContextExists() {
        val suburb = resolveServiceQuoteSuburb(
            selectedSuburb = " ",
            currentLocationSuburb = null,
            profileSuburb = " ",
            recommendationSuburb = null,
            providers = emptyList(),
            category = "grooming",
        )

        assertNull(suburb)
    }
}
