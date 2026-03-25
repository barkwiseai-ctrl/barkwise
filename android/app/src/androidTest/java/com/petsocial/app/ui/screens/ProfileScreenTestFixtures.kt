package com.petsocial.app.ui.screens

import androidx.compose.runtime.Composable
import com.petsocial.app.data.AppNotification
import com.petsocial.app.data.CalendarEvent
import com.petsocial.app.data.ProviderInboxItem
import com.petsocial.app.ui.FriendProfile
import com.petsocial.app.ui.JoinedEvent
import com.petsocial.app.ui.OwnerBooking
import com.petsocial.app.ui.ProfileInfo
import com.petsocial.app.ui.ProviderBooking
import com.petsocial.app.ui.ProviderListing

@Composable
internal fun profileScreenForTest(
    profileInfo: ProfileInfo = ProfileInfo(),
    hasProviderListings: Boolean = false,
    canLoadProviderInbox: Boolean = false,
    ownerBookings: List<OwnerBooking> = emptyList(),
    providerListings: List<ProviderListing> = emptyList(),
    providerBookings: List<ProviderBooking> = emptyList(),
    providerInboxItems: List<ProviderInboxItem> = emptyList(),
    notifications: List<AppNotification> = emptyList(),
) {
    ProfileScreen(
        profileInfo = profileInfo,
        hasProviderListings = hasProviderListings,
        canLoadProviderInbox = canLoadProviderInbox,
        activeUserId = "user_2",
        friendProfiles = emptyList<FriendProfile>(),
        joinedEvents = emptyList<JoinedEvent>(),
        ownerBookings = ownerBookings,
        providerListings = providerListings,
        providerBookings = providerBookings,
        providerInboxItems = providerInboxItems,
        loadingProviderInbox = false,
        sendingQuoteOfferItemIds = emptySet(),
        isSubmittingProviderInboxAction = false,
        calendarEvents = emptyList<CalendarEvent>(),
        notifications = notifications,
        activationFunnelMetrics = null,
        notifyFollowedGroupAlerts = true,
        notifySavedPostUpdates = true,
        notifySafetyAlerts = true,
        showIdentityHeader = false,
        friendQrPayload = "",
        friendQrExpiresAt = null,
        friendQrLoading = false,
        onRefreshFriendQrPayload = {},
        onResolveFriendQrToken = {},
        onRemoveFriend = {},
        onOpenFriendMessages = {},
        onOpenMessages = { _, _ -> },
        onSaveProfile = {},
        onMarkNotificationRead = {},
        onMarkAllNotificationsRead = {},
        onClearLocalNotifications = {},
        onOpenNotificationDeepLink = {},
        onUpdateNotificationPreferences = { _, _, _ -> },
        onResetDeviceSignIn = {},
        onRefreshActivationDashboard = {},
        onRefreshProviderInbox = {},
        onSendQuoteOffer = { _, _, _, _, _, _ -> },
        onConfirmProviderBooking = {},
        onDeclineProviderBooking = {},
        onRescheduleProviderBooking = { _, _, _, _ -> },
        onCreateProviderListing = { _, _, _, _, _ -> },
        onEditProviderListing = { _, _, _, _, _ -> },
        onCancelProviderListing = {},
        onRestoreProviderListing = {},
        onCreateProviderBlackout = { _, _, _, _ -> },
    )
}
