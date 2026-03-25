package com.petsocial.app.ui.screens

import com.petsocial.app.data.AppNotification
import com.petsocial.app.ui.OwnerBooking
import com.petsocial.app.ui.ProviderBooking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationAppointmentResolutionTest {

    @Test
    fun resolveAppointmentFromNotification_matchesBookingDeepLinkById() {
        val notification = notification(deepLink = "booking:booking_2")
        val ownerBookings = listOf(
            ownerBooking(id = "booking_1", userId = "user_chris", threadId = "thread_1"),
            ownerBooking(id = "booking_2", userId = "user_pat", threadId = "thread_2"),
        )

        val resolved = resolveAppointmentFromNotification(
            notification = notification,
            ownerBookings = ownerBookings,
            providerBookings = emptyList(),
        )

        requireNotNull(resolved)
        assertEquals("booking_2", resolved.bookingId)
        assertEquals("user_pat", resolved.messageUserId)
        assertEquals("thread_2", resolved.messageThreadId)
    }

    @Test
    fun resolveAppointmentFromNotification_matchesQuoteDeepLinkByExactBookingId() {
        val notification = notification(deepLink = "quote:booking_2")
        val ownerBookings = listOf(
            ownerBooking(id = "booking_1", userId = "user_chris", threadId = "thread_1", date = "2026-03-19"),
            ownerBooking(id = "booking_2", userId = "user_pat", threadId = "thread_2", date = "2026-03-25"),
        )

        val resolved = resolveAppointmentFromNotification(
            notification = notification,
            ownerBookings = ownerBookings,
            providerBookings = emptyList(),
        )

        requireNotNull(resolved)
        assertEquals("booking_2", resolved.bookingId)
        assertEquals("user_pat", resolved.messageUserId)
        assertEquals("thread_2", resolved.messageThreadId)
    }

    @Test
    fun resolveAppointmentFromNotification_fallsBackGracefullyWhenQuoteIdMissing() {
        val notification = notification(deepLink = "quote:")
        val ownerBookings = listOf(
            ownerBooking(id = "booking_1", userId = "user_chris", threadId = "thread_1", date = "2026-03-19"),
            ownerBooking(id = "booking_2", userId = "user_pat", threadId = "thread_2", date = "2026-03-25"),
        )

        val resolved = resolveAppointmentFromNotification(
            notification = notification,
            ownerBookings = ownerBookings,
            providerBookings = emptyList(),
        )

        requireNotNull(resolved)
        assertNull(resolved.bookingId)
        assertEquals("Quote update", resolved.title)
        assertEquals("Appointment not scheduled yet.", resolved.scheduleLabel)
        assertEquals("Listings", resolved.counterpartLabel)
        assertEquals("Awaiting booking", resolved.statusLabel)
        assertEquals("A provider responded", resolved.description)
        assertNull(resolved.messageUserId)
        assertNull(resolved.messageThreadId)
    }

    @Test
    fun resolveAppointmentFromNotification_doesNotHijackUnrelatedBookingForQuoteDeepLink() {
        val notification = notification(deepLink = "quote:qr_0663a77d")
        val ownerBookings = listOf(
            ownerBooking(id = "booking_1", userId = "user_chris", threadId = "thread_1", date = "2026-03-19"),
            ownerBooking(id = "booking_2", userId = "user_pat", threadId = "thread_2", date = "2026-03-25"),
        )

        val resolved = resolveAppointmentFromNotification(
            notification = notification,
            ownerBookings = ownerBookings,
            providerBookings = emptyList(),
        )

        requireNotNull(resolved)
        assertNull(resolved.bookingId)
        assertEquals("Quote update", resolved.title)
        assertEquals("Awaiting booking", resolved.statusLabel)
        assertNull(resolved.messageUserId)
        assertNull(resolved.messageThreadId)
    }

    @Test
    fun resolveAppointmentFromNotification_returnsPlaceholderWhenBookingMissing() {
        val notification = notification(deepLink = "booking:missing_booking")

        val resolved = resolveAppointmentFromNotification(
            notification = notification,
            ownerBookings = emptyList(),
            providerBookings = emptyList(),
        )

        requireNotNull(resolved)
        assertEquals("missing_booking", resolved.bookingId)
        assertNull(resolved.messageUserId)
        assertNull(resolved.messageThreadId)
    }

    @Test
    fun resolveAppointmentFromNotification_supportsProviderBookingTargets() {
        val notification = notification(deepLink = "quote:provider_booking_2")
        val providerBookings = listOf(
            providerBooking(id = "provider_booking_1", ownerUserId = "user_eldon", threadId = "thread_a"),
            providerBooking(id = "provider_booking_2", ownerUserId = "user_liz", threadId = "thread_b"),
        )

        val resolved = resolveAppointmentFromNotification(
            notification = notification,
            ownerBookings = emptyList(),
            providerBookings = providerBookings,
        )

        requireNotNull(resolved)
        assertEquals("provider_booking_2", resolved.bookingId)
        assertEquals("user_liz", resolved.messageUserId)
        assertEquals("thread_b", resolved.messageThreadId)
    }

    private fun notification(deepLink: String): AppNotification {
        return AppNotification(
            id = "notif_1",
            userId = "user_2",
            title = "Quote update",
            body = "A provider responded",
            category = "service_quote",
            read = false,
            createdAt = "2026-03-20T00:00:00Z",
            deepLink = deepLink,
        )
    }

    private fun ownerBooking(
        id: String,
        userId: String,
        threadId: String,
        date: String = "2026-03-20",
    ): OwnerBooking {
        return OwnerBooking(
            id = id,
            serviceName = "Walk",
            providerId = "provider_1",
            providerUserId = userId,
            threadId = threadId,
            providerAccountLabel = "Provider",
            date = date,
            timeSlot = "09:00",
            status = "pending",
        )
    }

    private fun providerBooking(
        id: String,
        ownerUserId: String,
        threadId: String,
    ): ProviderBooking {
        return ProviderBooking(
            id = id,
            petName = "Milo",
            ownerUserId = ownerUserId,
            threadId = threadId,
            providerId = "provider_1",
            serviceName = "Walk",
            date = "2026-03-20",
            timeSlot = "09:00",
            status = "pending",
        )
    }
}
