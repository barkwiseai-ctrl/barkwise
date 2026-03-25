package com.petsocial.app.ui

import com.petsocial.app.data.ProviderInboxItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderInboxQuoteResolutionTest {

    @Test
    fun resolveQuoteOfferSubmission_blankInboxIdIsIgnored() {
        assertEquals(
            QuoteOfferSubmissionResolution.Ignore,
            resolveQuoteOfferSubmission(
                providerInboxItems = listOf(sampleQuoteRequest()),
                sendingQuoteOfferItemIds = emptySet(),
                inboxItemId = "   ",
                priceCents = 6500,
                proposedDate = "2026-03-21",
                proposedTimeSlot = "09:00",
                expiresAt = "2026-03-22T00:00:00Z",
            ),
        )
    }

    @Test
    fun resolveQuoteOfferSubmission_rejectsInvalidPrice() {
        assertEquals(
            QuoteOfferSubmissionResolution.Toast("Offer price must be greater than zero"),
            resolveQuoteOfferSubmission(
                providerInboxItems = listOf(sampleQuoteRequest()),
                sendingQuoteOfferItemIds = emptySet(),
                inboxItemId = "item_1",
                priceCents = 0,
                proposedDate = "2026-03-21",
                proposedTimeSlot = "09:00",
                expiresAt = "2026-03-22T00:00:00Z",
            ),
        )
    }

    @Test
    fun resolveQuoteOfferSubmission_requiresDateTimeAndExpiry() {
        assertEquals(
            QuoteOfferSubmissionResolution.Toast("Offer date, time, and expiry are required"),
            resolveQuoteOfferSubmission(
                providerInboxItems = listOf(sampleQuoteRequest()),
                sendingQuoteOfferItemIds = emptySet(),
                inboxItemId = "item_1",
                priceCents = 6500,
                proposedDate = " ",
                proposedTimeSlot = "09:00",
                expiresAt = "2026-03-22T00:00:00Z",
            ),
        )
    }

    @Test
    fun resolveQuoteOfferSubmission_missingItemShowsNotFound() {
        assertEquals(
            QuoteOfferSubmissionResolution.Toast("Inbox item not found"),
            resolveQuoteOfferSubmission(
                providerInboxItems = emptyList(),
                sendingQuoteOfferItemIds = emptySet(),
                inboxItemId = "item_1",
                priceCents = 6500,
                proposedDate = "2026-03-21",
                proposedTimeSlot = "09:00",
                expiresAt = "2026-03-22T00:00:00Z",
            ),
        )
    }

    @Test
    fun resolveQuoteOfferSubmission_nonQuoteItemsCannotSendOffers() {
        assertEquals(
            QuoteOfferSubmissionResolution.Toast("Only quote requests can receive offers"),
            resolveQuoteOfferSubmission(
                providerInboxItems = listOf(sampleNonQuoteItem()),
                sendingQuoteOfferItemIds = emptySet(),
                inboxItemId = "item_1",
                priceCents = 6500,
                proposedDate = "2026-03-21",
                proposedTimeSlot = "09:00",
                expiresAt = "2026-03-22T00:00:00Z",
            ),
        )
    }

    @Test
    fun resolveQuoteOfferSubmission_duplicateSendIsIgnored() {
        assertEquals(
            QuoteOfferSubmissionResolution.Ignore,
            resolveQuoteOfferSubmission(
                providerInboxItems = listOf(sampleQuoteRequest()),
                sendingQuoteOfferItemIds = setOf("item_1"),
                inboxItemId = " item_1 ",
                priceCents = 6500,
                proposedDate = "2026-03-21",
                proposedTimeSlot = "09:00",
                expiresAt = "2026-03-22T00:00:00Z",
            ),
        )
    }

    @Test
    fun resolveQuoteOfferSubmission_validQuoteRequestProducesNormalizedPayload() {
        val resolution = resolveQuoteOfferSubmission(
            providerInboxItems = listOf(sampleQuoteRequest()),
            sendingQuoteOfferItemIds = emptySet(),
            inboxItemId = " item_1 ",
            priceCents = 6500,
            proposedDate = " 2026-03-21 ",
            proposedTimeSlot = " 09:00 ",
            expiresAt = " 2026-03-22T00:00:00Z ",
            note = "  Friendly note  ",
        ) as QuoteOfferSubmissionResolution.Submit

        assertEquals("item_1", resolution.inboxItemId)
        assertEquals("quote_1", resolution.quoteRequestId)
        assertEquals("provider_1", resolution.providerId)
        assertEquals("Chris Walks", resolution.providerName)
        assertEquals(6500, resolution.priceCents)
        assertEquals("2026-03-21", resolution.proposedDate)
        assertEquals("09:00", resolution.proposedTimeSlot)
        assertEquals("2026-03-22T00:00:00Z", resolution.expiresAt)
        assertEquals("Friendly note", resolution.note)
    }

    @Test
    fun shouldRefreshProviderInbox_onlyTrueWhenCapabilityEnabled() {
        assertFalse(shouldRefreshProviderInbox(false))
        assertTrue(shouldRefreshProviderInbox(true))
    }

    private fun sampleQuoteRequest(): ProviderInboxItem {
        return ProviderInboxItem(
            id = "item_1",
            itemType = "quote_request",
            providerId = "provider_1",
            providerName = "Chris Walks",
            status = "pending",
            title = "Quote request",
            subtitle = "Need a walk",
            createdAt = "2026-03-20T00:00:00Z",
            quoteRequestId = " quote_1 ",
        )
    }

    private fun sampleNonQuoteItem(): ProviderInboxItem {
        return ProviderInboxItem(
            id = "item_1",
            itemType = "booking_update",
            providerId = "provider_1",
            providerName = "Chris Walks",
            status = "pending",
            title = "Booking update",
            subtitle = "Need a reply",
            createdAt = "2026-03-20T00:00:00Z",
            quoteRequestId = null,
        )
    }
}
