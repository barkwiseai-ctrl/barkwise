package com.petsocial.app.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petsocial.app.data.AppNotification
import com.petsocial.app.ui.OwnerBooking
import com.petsocial.app.testing.ComposeTestActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationsToAppointmentFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun bookingNotification_opensExactAppointmentPopup() {
        composeRule.setContent {
            MaterialTheme {
                profileScreenForTest(
                    ownerBookings = listOf(
                        ownerBooking(id = "booking_1", providerUserId = "user_a", threadId = "thread_a"),
                        ownerBooking(id = "booking_2", providerUserId = "user_b", threadId = "thread_b"),
                    ),
                    notifications = listOf(
                        notification(title = "Booking update", deepLink = "booking:booking_2"),
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Notifications").performClick()
        composeRule.onNodeWithText("Booking update").performClick()
        composeRule.onNodeWithText("Appointment booking_2").assertIsDisplayed()
    }

    @Test
    fun quoteNotification_opensExactBookingNotFirstBooking() {
        composeRule.setContent {
            MaterialTheme {
                profileScreenForTest(
                    ownerBookings = listOf(
                        ownerBooking(id = "booking_1", providerUserId = "user_a", threadId = "thread_a", date = "2026-03-19"),
                        ownerBooking(id = "booking_2", providerUserId = "user_b", threadId = "thread_b", date = "2026-03-25"),
                    ),
                    notifications = listOf(
                        notification(title = "Quote ready", deepLink = "quote:booking_2"),
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Notifications").performClick()
        composeRule.onNodeWithText("Quote ready").performClick()
        composeRule.onNodeWithText("Appointment booking_2").assertIsDisplayed()
    }

    private fun notification(title: String, deepLink: String): AppNotification {
        return AppNotification(
            id = title,
            userId = "user_2",
            title = title,
            body = "Body",
            category = "service_quote",
            read = false,
            createdAt = "2026-03-20T00:00:00Z",
            deepLink = deepLink,
        )
    }

    private fun ownerBooking(
        id: String,
        providerUserId: String,
        threadId: String,
        date: String = "2026-03-20",
    ): OwnerBooking {
        return OwnerBooking(
            id = id,
            serviceName = "Walk",
            providerId = "provider_1",
            providerUserId = providerUserId,
            threadId = threadId,
            providerAccountLabel = "Provider",
            date = date,
            timeSlot = "09:00",
            status = "pending",
        )
    }
}
