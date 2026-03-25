package com.petsocial.app.ui.screens

import com.petsocial.app.ui.OwnerBooking
import com.petsocial.app.ui.ProviderBooking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookingMessageTargetTest {

    @Test
    fun ownerBooking_resolveMessageTarget_prefersCanonicalThreadAndUserIds() {
        val booking = OwnerBooking(
            id = "booking_1",
            serviceName = "Walk",
            providerId = "provider_9",
            providerUserId = "user_chris",
            threadId = "dm_user_eldon_user_chris",
            providerAccountLabel = "Chris",
            date = "2026-03-20",
            timeSlot = "09:00",
            status = "pending",
        )

        val target = booking.resolveMessageTarget()

        assertEquals("user_chris", target.userId)
        assertEquals("dm_user_eldon_user_chris", target.threadId)
    }

    @Test
    fun ownerBooking_resolveMessageTarget_doesNotNeedProviderLookupWhenOnlyThreadExists() {
        val booking = OwnerBooking(
            id = "booking_2",
            serviceName = "Groom",
            providerId = "provider_missing_from_list",
            providerUserId = "",
            threadId = "dm_user_eldon_user_chris",
            providerAccountLabel = "Chris",
            date = "2026-03-21",
            timeSlot = "11:00",
            status = "confirmed",
        )

        val target = booking.resolveMessageTarget()

        assertNull(target.userId)
        assertEquals("dm_user_eldon_user_chris", target.threadId)
    }

    @Test
    fun providerBooking_resolveMessageTarget_usesOwnerCanonicalIds() {
        val booking = ProviderBooking(
            id = "booking_3",
            petName = "Milo",
            ownerUserId = "user_eldon",
            threadId = "dm_user_eldon_user_chris",
            providerId = "provider_9",
            serviceName = "Boarding",
            date = "2026-03-22",
            timeSlot = "14:00",
            status = "pending",
        )

        val target = booking.resolveMessageTarget()

        assertEquals("user_eldon", target.userId)
        assertEquals("dm_user_eldon_user_chris", target.threadId)
    }

    @Test
    fun bookingMessageTarget_returnsUnavailableWhenCanonicalIdsMissing() {
        val booking = OwnerBooking(
            id = "booking_4",
            serviceName = "Walk",
            providerId = "provider_9",
            providerUserId = "",
            threadId = null,
            providerAccountLabel = "Chris",
            date = "2026-03-23",
            timeSlot = "16:00",
            status = "pending",
        )

        val target = booking.resolveMessageTarget()

        assertNull(target.userId)
        assertNull(target.threadId)
    }
}
