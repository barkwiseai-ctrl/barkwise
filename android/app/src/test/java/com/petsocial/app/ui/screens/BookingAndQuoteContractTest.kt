package com.petsocial.app.ui.screens

import com.petsocial.app.data.AppNotification
import com.petsocial.app.ui.OwnerBooking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookingAndQuoteContractTest {

    @Test
    fun ownerBooking_withThreadIdOnly_stillResolvesMessagingTarget() {
        val booking = OwnerBooking(
            id = "booking_1",
            serviceName = "Walk",
            providerId = "provider_1",
            providerUserId = "",
            threadId = "thread_1",
            providerAccountLabel = "Chris",
            date = "2026-03-20",
            timeSlot = "09:00",
            status = "pending",
        )

        val target = booking.resolveMessageTarget()
        assertNull(target.userId)
        assertEquals("thread_1", target.threadId)
    }

    @Test
    fun ownerBooking_withUserIdOnly_resolvesUserFlow() {
        val booking = OwnerBooking(
            id = "booking_1",
            serviceName = "Walk",
            providerId = "provider_1",
            providerUserId = "user_chris",
            threadId = null,
            providerAccountLabel = "Chris",
            date = "2026-03-20",
            timeSlot = "09:00",
            status = "pending",
        )

        val target = booking.resolveMessageTarget()
        assertEquals("user_chris", target.userId)
        assertNull(target.threadId)
    }

    @Test
    fun quoteDeepLink_resolvesExactBookingId() {
        val resolved = resolveAppointmentFromNotification(
            notification = AppNotification(
                id = "notif_1",
                userId = "user_1",
                title = "Quote",
                body = "Quote ready",
                category = "service_quote",
                read = false,
                createdAt = "2026-03-20T00:00:00Z",
                deepLink = "quote:booking_2",
            ),
            ownerBookings = listOf(
                OwnerBooking(
                    id = "booking_1",
                    serviceName = "Walk",
                    providerId = "provider_1",
                    providerUserId = "user_a",
                    threadId = "thread_a",
                    providerAccountLabel = "A",
                    date = "2026-03-19",
                    timeSlot = "09:00",
                    status = "pending",
                ),
                OwnerBooking(
                    id = "booking_2",
                    serviceName = "Boarding",
                    providerId = "provider_2",
                    providerUserId = "user_b",
                    threadId = "thread_b",
                    providerAccountLabel = "B",
                    date = "2026-03-25",
                    timeSlot = "11:00",
                    status = "pending",
                ),
            ),
            providerBookings = emptyList(),
        )

        assertEquals("booking_2", resolved?.bookingId)
        assertEquals("thread_b", resolved?.messageThreadId)
    }

    @Test
    fun missingCanonicalIds_degradeGracefully() {
        val booking = OwnerBooking(
            id = "booking_1",
            serviceName = "Walk",
            providerId = "provider_1",
            providerUserId = "",
            threadId = null,
            providerAccountLabel = "Chris",
            date = "2026-03-20",
            timeSlot = "09:00",
            status = "pending",
        )

        val target = booking.resolveMessageTarget()
        assertNull(target.userId)
        assertNull(target.threadId)
    }
}
