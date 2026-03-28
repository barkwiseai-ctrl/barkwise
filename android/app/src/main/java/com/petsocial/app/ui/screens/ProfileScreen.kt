package com.petsocial.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.petsocial.app.BuildConfig
import com.petsocial.app.data.CalendarEvent
import com.petsocial.app.data.CommunityActivationFunnel
import com.petsocial.app.data.AppNotification
import com.petsocial.app.data.ProviderInboxItem
import com.petsocial.app.ui.FriendProfile
import com.petsocial.app.ui.JoinedEvent
import com.petsocial.app.ui.OwnerBooking
import com.petsocial.app.ui.ProfileInfo
import com.petsocial.app.ui.ProviderBooking
import com.petsocial.app.ui.ProviderListing
import com.petsocial.app.ui.calendar.calendarEventToCalendarDraft
import com.petsocial.app.ui.calendar.openCalendarDraft
import com.petsocial.app.ui.calendar.ownerBookingToCalendarDraft
import com.petsocial.app.ui.calendar.providerBookingToCalendarDraft
import com.petsocial.app.ui.qr.QrPayloadAction
import com.petsocial.app.ui.qr.QrScannerSheet
import com.petsocial.app.ui.qr.generateQrImageBitmap
import com.petsocial.app.ui.qr.parseQrPayload
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ProfileScreen(
    profileInfo: ProfileInfo,
    hasProviderListings: Boolean,
    canLoadProviderInbox: Boolean,
    activeUserId: String,
    friendProfiles: List<FriendProfile>,
    joinedEvents: List<JoinedEvent>,
    ownerBookings: List<OwnerBooking>,
    providerListings: List<ProviderListing>,
    providerBookings: List<ProviderBooking>,
    providerInboxItems: List<ProviderInboxItem>,
    loadingProviderInbox: Boolean,
    sendingQuoteOfferItemIds: Set<String>,
    isSubmittingProviderInboxAction: Boolean,
    calendarEvents: List<CalendarEvent>,
    notifications: List<AppNotification>,
    activationFunnelMetrics: CommunityActivationFunnel?,
    notifyFollowedGroupAlerts: Boolean,
    notifySavedPostUpdates: Boolean,
    notifySafetyAlerts: Boolean,
    showIdentityHeader: Boolean,
    friendQrPayload: String,
    friendQrExpiresAt: String?,
    friendQrLoading: Boolean,
    onRefreshFriendQrPayload: () -> Unit,
    onResolveFriendQrToken: (String) -> Unit,
    onRemoveFriend: (String) -> Unit,
    onOpenFriendMessages: (String) -> Unit,
    onOpenMessages: (String?, String?) -> Unit,
    onSaveProfile: (ProfileInfo) -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    onMarkAllNotificationsRead: () -> Unit,
    onClearLocalNotifications: () -> Unit,
    onOpenNotificationDeepLink: (AppNotification) -> Unit,
    onUpdateNotificationPreferences: (followedGroupAlerts: Boolean, savedPostUpdates: Boolean, safetyAlerts: Boolean) -> Unit,
    onResetDeviceSignIn: () -> Unit,
    onRefreshActivationDashboard: () -> Unit,
    onRefreshProviderInbox: () -> Unit,
    onSendQuoteOffer: (String, Int, String, String, String, String) -> Unit,
    onConfirmProviderBooking: (String) -> Unit,
    onDeclineProviderBooking: (String) -> Unit,
    onRescheduleProviderBooking: (String, String, String, String) -> Unit,
    onCreateProviderListing: (String, String, String, String, Int) -> Unit,
    onEditProviderListing: (String, String, String, String, Int) -> Unit,
    onCancelProviderListing: (String) -> Unit,
    onRestoreProviderListing: (String) -> Unit,
    onCreateProviderBlackout: (String, String, String, String) -> Unit,
) {
    val context = LocalContext.current
    var displayName by rememberSaveable(profileInfo.displayName) { mutableStateOf(profileInfo.displayName) }
    var email by rememberSaveable(profileInfo.email) { mutableStateOf(profileInfo.email) }
    var phone by rememberSaveable(profileInfo.phone) { mutableStateOf(profileInfo.phone) }
    var humanPronouns by rememberSaveable(profileInfo.humanPronouns) { mutableStateOf(profileInfo.humanPronouns) }
    var humanRoleLabel by rememberSaveable(profileInfo.humanRoleLabel) { mutableStateOf(profileInfo.humanRoleLabel) }
    val (initialDogYears, initialDogMonths) = remember(profileInfo.dogAgeMonths) {
        splitAgeToYearsMonths(profileInfo.dogAgeMonths)
    }
    val (initialSecondaryDogYears, initialSecondaryDogMonths) = remember(profileInfo.secondaryDogAgeMonths) {
        splitAgeToYearsMonths(profileInfo.secondaryDogAgeMonths)
    }
    var dogName by rememberSaveable(profileInfo.dogName) { mutableStateOf(profileInfo.dogName) }
    var dogAgeYearsText by rememberSaveable(profileInfo.dogAgeMonths) {
        mutableStateOf(initialDogYears)
    }
    var dogAgeRemainderMonthsText by rememberSaveable(profileInfo.dogAgeMonths) {
        mutableStateOf(initialDogMonths)
    }
    var dogBreedMix by rememberSaveable(profileInfo.dogBreedMix) { mutableStateOf(profileInfo.dogBreedMix) }
    var dogGender by rememberSaveable(profileInfo.dogGender) {
        mutableStateOf(normalizeDogGender(profileInfo.dogGender).ifBlank { "male" })
    }
    var dogWeightKg by rememberSaveable(profileInfo.dogWeightKg) { mutableStateOf(profileInfo.dogWeightKg) }
    var dogPhotoUrlsText by rememberSaveable(profileInfo.dogPhotoUrls) {
        mutableStateOf(profileInfo.dogPhotoUrls.joinToString("\n"))
    }
    var secondaryDogName by rememberSaveable(profileInfo.secondaryDogName) { mutableStateOf(profileInfo.secondaryDogName) }
    var secondaryDogAgeYearsText by rememberSaveable(profileInfo.secondaryDogAgeMonths) {
        mutableStateOf(initialSecondaryDogYears)
    }
    var secondaryDogAgeRemainderMonthsText by rememberSaveable(profileInfo.secondaryDogAgeMonths) {
        mutableStateOf(initialSecondaryDogMonths)
    }
    var secondaryDogGender by rememberSaveable(profileInfo.secondaryDogGender) {
        mutableStateOf(normalizeDogGender(profileInfo.secondaryDogGender).ifBlank { "male" })
    }
    var secondaryDogWeightKg by rememberSaveable(profileInfo.secondaryDogWeightKg) { mutableStateOf(profileInfo.secondaryDogWeightKg) }
    var showSecondDog by rememberSaveable(
        profileInfo.secondaryDogName,
        profileInfo.secondaryDogAgeMonths,
        profileInfo.secondaryDogGender,
        profileInfo.secondaryDogWeightKg,
    ) {
        mutableStateOf(
            profileInfo.secondaryDogName.isNotBlank() ||
                profileInfo.secondaryDogAgeMonths > 0 ||
                profileInfo.secondaryDogGender.isNotBlank() ||
                profileInfo.secondaryDogWeightKg.isNotBlank(),
        )
    }
    var bio by rememberSaveable(profileInfo.bio) { mutableStateOf(profileInfo.bio) }
    var suburb by rememberSaveable(profileInfo.suburb) { mutableStateOf(profileInfo.suburb) }
    var favoriteSuburbsText by rememberSaveable(profileInfo.favoriteSuburbs) {
        mutableStateOf(profileInfo.favoriteSuburbs.joinToString(", "))
    }
    var playEnergyLevel by rememberSaveable(profileInfo.playEnergyLevel) { mutableStateOf(profileInfo.playEnergyLevel) }
    var playStyle by rememberSaveable(profileInfo.playStyle) { mutableStateOf(profileInfo.playStyle) }
    var socialConfidence by rememberSaveable(profileInfo.socialConfidence) { mutableStateOf(profileInfo.socialConfidence) }
    var triggerNotes by rememberSaveable(profileInfo.triggerNotes) { mutableStateOf(profileInfo.triggerNotes) }
    var idealMatch by rememberSaveable(profileInfo.idealMatch) { mutableStateOf(profileInfo.idealMatch) }
    var walkPreferences by rememberSaveable(profileInfo.walkPreferences) { mutableStateOf(profileInfo.walkPreferences) }
    var trainingStyle by rememberSaveable(profileInfo.trainingStyle) { mutableStateOf(profileInfo.trainingStyle) }
    var feedingRules by rememberSaveable(profileInfo.feedingRules) { mutableStateOf(profileInfo.feedingRules) }
    var consentBoundaries by rememberSaveable(profileInfo.consentBoundaries) { mutableStateOf(profileInfo.consentBoundaries) }
    var vaccinationStatus by rememberSaveable(profileInfo.vaccinationStatus) { mutableStateOf(profileInfo.vaccinationStatus) }
    var microchipped by rememberSaveable(profileInfo.microchipped) { mutableStateOf(profileInfo.microchipped) }
    var recallTrained by rememberSaveable(profileInfo.recallTrained) { mutableStateOf(profileInfo.recallTrained) }
    var leashReliability by rememberSaveable(profileInfo.leashReliability) { mutableStateOf(profileInfo.leashReliability) }
    var emergencyContactName by rememberSaveable(profileInfo.emergencyContactName) { mutableStateOf(profileInfo.emergencyContactName) }
    var emergencyContactPhone by rememberSaveable(profileInfo.emergencyContactPhone) { mutableStateOf(profileInfo.emergencyContactPhone) }
    var suburbVisibility by rememberSaveable(profileInfo.fieldVisibility["suburb"]) {
        mutableStateOf(profileInfo.fieldVisibility["suburb"] ?: "group")
    }
    var dogNameVisibility by rememberSaveable(profileInfo.fieldVisibility["dog_name"]) {
        mutableStateOf(profileInfo.fieldVisibility["dog_name"] ?: "friends")
    }
    var triggerNotesVisibility by rememberSaveable(profileInfo.fieldVisibility["trigger_notes"]) {
        mutableStateOf(profileInfo.fieldVisibility["trigger_notes"] ?: "private")
    }

    var showDogEditor by rememberSaveable { mutableStateOf(false) }
    var showHumanEditor by rememberSaveable { mutableStateOf(false) }
    var showSocialSheet by rememberSaveable { mutableStateOf(false) }
    var showFriendQrDialog by rememberSaveable { mutableStateOf(false) }
    var showFriendQrScanner by rememberSaveable { mutableStateOf(false) }
    var friendQrStatusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showNotificationsSheet by rememberSaveable { mutableStateOf(false) }
    var showPlansSheet by rememberSaveable { mutableStateOf(false) }
    var showActivationSheet by rememberSaveable { mutableStateOf(false) }
    var plansSheetSectionKey by rememberSaveable { mutableStateOf(PlansSheetSection.ALL.key) }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var showHelpDialog by rememberSaveable { mutableStateOf(false) }
    var showInstallQrDialog by rememberSaveable { mutableStateOf(false) }
    var showSecurityDetails by rememberSaveable { mutableStateOf(false) }
    var biometricLockEnabled by rememberSaveable { mutableStateOf(false) }
    var selectedAppointment by remember { mutableStateOf<AppointmentPopupState?>(null) }
    var offerDialogItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var offerPriceAud by rememberSaveable { mutableStateOf("65.00") }
    var offerDate by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
    var offerTimeSlot by rememberSaveable { mutableStateOf("09:00") }
    var offerExpiryDate by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(2).toString()) }
    var offerExpiryTime by rememberSaveable { mutableStateOf("09:00") }
    var offerNote by rememberSaveable { mutableStateOf("") }
    var rescheduleDialogItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var rescheduleDate by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
    var rescheduleTimeSlot by rememberSaveable { mutableStateOf("09:00") }
    var rescheduleNote by rememberSaveable { mutableStateOf("") }
    var listingDialogListingId by rememberSaveable { mutableStateOf<String?>(null) }
    var listingName by rememberSaveable { mutableStateOf("") }
    var listingCategory by rememberSaveable { mutableStateOf("grooming") }
    var listingSuburb by rememberSaveable(profileInfo.suburb) { mutableStateOf(profileInfo.suburb.ifBlank { "Surry Hills" }) }
    var listingDescription by rememberSaveable { mutableStateOf("") }
    var listingPriceText by rememberSaveable { mutableStateOf("55") }
    var blackoutListingId by rememberSaveable { mutableStateOf<String?>(null) }
    var blackoutDate by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
    var blackoutTimeSlot by rememberSaveable { mutableStateOf("09:00") }
    var blackoutReason by rememberSaveable { mutableStateOf("") }
    var providerScheduleFilterKey by rememberSaveable { mutableStateOf(ProviderScheduleFilter.TODAY.key) }
    var showProviderCalendarSheet by rememberSaveable { mutableStateOf(false) }
    var providerCalendarViewKey by rememberSaveable { mutableStateOf(ProviderCalendarView.WEEK.key) }
    var providerCalendarAnchorDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val settingsPrefs = remember(context) {
        context.getSharedPreferences(HOME_SETTINGS_PREFS, Context.MODE_PRIVATE)
    }
    val defaultThemeMode = remember(context) {
        val isDarkMode =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (isDarkMode) HOME_THEME_MODE_DARK else HOME_THEME_MODE_LIGHT
    }
    var themeMode by rememberSaveable {
        mutableStateOf(
            settingsPrefs.getString(HOME_THEME_MODE_KEY, defaultThemeMode)
                ?.takeIf { storedMode -> storedMode == HOME_THEME_MODE_LIGHT || storedMode == HOME_THEME_MODE_DARK }
                ?: defaultThemeMode,
        )
    }
    val activeAccountLabel = activeUserId
    val profileDogPhotoUrls = remember(profileInfo.dogPhotoUrls) {
        profileInfo.dogPhotoUrls
            .asSequence()
            .map { url -> url.trim() }
            .filter { url -> url.isNotBlank() }
            .distinct()
            .toList()
    }
    val profileCompletionRatio = remember(profileInfo, profileDogPhotoUrls) {
        profileCompletenessRatio(profileInfo, profileDogPhotoUrls)
    }
    val humanProfileCompletion = remember(profileInfo) {
        completionRatio(
            profileInfo.displayName.isNotBlank(),
            profileInfo.email.isNotBlank(),
            profileInfo.phone.isNotBlank(),
            profileInfo.suburb.isNotBlank(),
            profileInfo.bio.isNotBlank(),
            profileInfo.emergencyContactName.isNotBlank() && profileInfo.emergencyContactPhone.isNotBlank(),
        )
    }
    val dogProfileCompletion = remember(profileInfo, profileDogPhotoUrls) {
        completionRatio(
            profileInfo.dogName.isNotBlank(),
            profileInfo.dogAgeMonths > 0,
            profileInfo.dogBreedMix.isNotBlank(),
            profileInfo.playEnergyLevel.isNotBlank(),
            profileInfo.socialConfidence.isNotBlank(),
            profileDogPhotoUrls.isNotEmpty(),
        )
    }
    val totalFriendCount = remember(friendProfiles) {
        friendProfiles.count { profile -> profile.isFriend }
    }
    val unreadNotificationsCount = remember(notifications) {
        notifications.count { notification -> !notification.read }
    }
    val totalNotificationsCount = notifications.size
    val upcomingJoinedEvents = remember(joinedEvents) {
        joinedEvents
            .filter(::isJoinedEventUpcoming)
            .sortedBy { event -> event.date }
    }
    val profileRoleLabel = remember(profileInfo.email, profileInfo.humanRoleLabel) {
        profileInfo.humanRoleLabel.ifBlank {
            if (profileInfo.email.isBlank()) "Guest" else "Member"
        }
    }
    val profileInitial = remember(profileInfo.displayName, activeAccountLabel) {
        (profileInfo.displayName.ifBlank { activeAccountLabel })
            .trim()
            .firstOrNull()
            ?.uppercase()
            ?: "B"
    }
    val totalActiveBookings = ownerBookings.size + providerBookings.size
    val pendingProviderQuoteRequests = remember(providerInboxItems) {
        providerInboxItems.count { item -> item.itemType == "quote_request" && item.status == "pending" }
    }
    val today = LocalDate.now()
    val selectedProviderScheduleFilter = remember(providerScheduleFilterKey) {
        ProviderScheduleFilter.fromKey(providerScheduleFilterKey)
    }
    val selectedProviderCalendarView = remember(providerCalendarViewKey) {
        ProviderCalendarView.fromKey(providerCalendarViewKey)
    }
    val providerCalendarAnchor = parseCalendarDate(providerCalendarAnchorDate) ?: today
    val activeProviderBookings = remember(providerBookings, today) {
        providerBookings
            .filter { booking ->
                val bookingDate = booking.scheduleDate() ?: return@filter false
                !bookingDate.isBefore(today) && !isBookingResolvedStatus(booking.status)
            }
            .sortedWith(compareBy<ProviderBooking> { booking -> booking.scheduleDate() ?: LocalDate.MAX }.thenBy { booking -> booking.timeSlot })
    }
    val providerSchedulePreviewBookings = remember(activeProviderBookings, today) {
        val todaysBookings = activeProviderBookings.filter { booking -> booking.scheduleDate() == today }
        (if (todaysBookings.isNotEmpty()) todaysBookings else activeProviderBookings).take(3)
    }
    val providerScheduleGroups = remember(activeProviderBookings, selectedProviderScheduleFilter, today) {
        activeProviderBookings
            .filter { booking -> booking.matchesProviderScheduleFilter(selectedProviderScheduleFilter, today) }
            .groupBy { booking -> booking.scheduleDate() ?: today }
            .toSortedMap()
            .map { (date, bookings) -> ProviderScheduleDayGroup(date = date, bookings = bookings) }
    }
    val todayProviderBookingCount = remember(activeProviderBookings, today) {
        activeProviderBookings.count { booking -> booking.scheduleDate() == today }
    }
    val nextWeekProviderBookingCount = remember(activeProviderBookings, today) {
        val weekEnd = today.plusDays(6)
        activeProviderBookings.count { booking ->
            val bookingDate = booking.scheduleDate() ?: return@count false
            !bookingDate.isBefore(today) && !bookingDate.isAfter(weekEnd)
        }
    }
    val pendingProviderBookingCount = remember(providerBookings) {
        providerBookings.count { booking ->
            booking.status.lowercase() in setOf("requested", "reschedule_requested")
        }
    }
    val nextCalendarEventCount = remember(calendarEvents, today) {
        val weekEnd = today.plusDays(6)
        calendarEvents.count { event ->
            val eventDate = parseCalendarDate(event.date) ?: return@count false
            !eventDate.isBefore(today) && !eventDate.isAfter(weekEnd)
        }
    }
    val providerBookingsByDate = remember(providerBookings) {
        providerBookings
            .mapNotNull { booking -> booking.scheduleDate()?.let { date -> date to booking } }
            .groupBy(
                keySelector = { (date, _) -> date },
                valueTransform = { (_, booking) -> booking },
            )
    }
    val providerCalendarEventsByDate = remember(calendarEvents) {
        calendarEvents
            .mapNotNull { event -> parseCalendarDate(event.date)?.let { date -> date to event } }
            .groupBy(
                keySelector = { (date, _) -> date },
                valueTransform = { (_, event) -> event },
            )
    }
    val nextProviderBooking = activeProviderBookings.firstOrNull()
    val communitySubtitle = remember(upcomingJoinedEvents) {
        buildCommunityPlansSubtitle(upcomingJoinedEvents.size)
    }
    val listingsSubtitle = remember(totalActiveBookings, pendingProviderQuoteRequests) {
        buildListingsPlansSubtitle(totalActiveBookings, pendingProviderQuoteRequests)
    }
    val activationSummary = remember(activationFunnelMetrics) {
        buildActivationQaSummary(activationFunnelMetrics)
    }
    val isProviderSurface = remember {
        BuildConfig.APP_SURFACE.equals("provider", ignoreCase = true)
    }
    val showActivationQa = BuildConfig.ENVIRONMENT.equals("staging", ignoreCase = true) &&
        !BuildConfig.ONBOARD_SCRIPT_ENABLED
    val installPageUrl = remember {
        val configured = BuildConfig.INSTALL_PAGE_URL.trim()
        when {
            configured.isBlank() -> PLAY_CLOSED_TESTING_URL
            configured == LEGACY_INSTALL_URL -> PLAY_CLOSED_TESTING_URL
            else -> configured
        }
    }
    val installQrBitmap = remember(installPageUrl) {
        generateQrImageBitmap(
            content = installPageUrl,
            sizePx = 720,
        )
    }
    val friendQrPayloadValue = remember(friendQrPayload) { friendQrPayload.trim() }
    val friendQrBitmap = remember(friendQrPayloadValue) {
        if (friendQrPayloadValue.isBlank()) {
            null
        } else {
            generateQrImageBitmap(
                content = friendQrPayloadValue,
                sizePx = 720,
            )
        }
    }
    val friendProfilesOnly = remember(friendProfiles) {
        friendProfiles
            .asSequence()
            .filter { profile -> profile.isFriend }
            .sortedBy { profile -> profile.humanName.lowercase() }
            .toList()
    }
    val normalizedProviderListings = remember(providerListings) {
        providerListings
            .sortedWith(compareBy<ProviderListing> { listing -> listing.status == "cancelled" }.thenBy { listing -> listing.title.lowercase() })
    }
    val activeProviderListings = remember(normalizedProviderListings) {
        normalizedProviderListings.filter { listing -> listing.status != "cancelled" }
    }
    val primaryProviderListing = remember(normalizedProviderListings, activeProviderListings) {
        activeProviderListings.firstOrNull() ?: normalizedProviderListings.firstOrNull()
    }
    val providerBusinessName = remember(primaryProviderListing, profileInfo.displayName) {
        primaryProviderListing?.title?.trim().orEmpty().ifBlank {
            profileInfo.displayName.trim().ifBlank { "Set your business profile" }
        }
    }
    val plansSheetSection = remember(plansSheetSectionKey) { PlansSheetSection.fromKey(plansSheetSectionKey) }
    val openCreateListingDialog = {
        listingDialogListingId = ""
        listingName = ""
        listingCategory = "grooming"
        listingSuburb = profileInfo.suburb.ifBlank { "Surry Hills" }
        listingDescription = ""
        listingPriceText = "55"
    }
    val openEditListingDialog: (ProviderListing) -> Unit = { listing ->
        listingDialogListingId = listing.id
        listingName = listing.title
        listingCategory = canonicalProviderListingCategory(listing.category)
        listingSuburb = listing.suburb.ifBlank { profileInfo.suburb.ifBlank { "Surry Hills" } }
        listingDescription = listing.description
        listingPriceText = listing.priceFrom.toString()
    }
    val openBlackoutDialog: (ProviderListing) -> Unit = { listing ->
        blackoutListingId = listing.id
        blackoutDate = LocalDate.now().plusDays(1).toString()
        blackoutTimeSlot = "09:00"
        blackoutReason = ""
    }
    val applyThemeMode: (String) -> Unit = remember(settingsPrefs) {
        { mode ->
            val normalizedMode = if (mode == HOME_THEME_MODE_DARK) HOME_THEME_MODE_DARK else HOME_THEME_MODE_LIGHT
            if (themeMode != normalizedMode) {
                settingsPrefs.edit().putString(HOME_THEME_MODE_KEY, normalizedMode).apply()
                themeMode = normalizedMode
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showIdentityHeader) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Text(
                    "Signed in as ${displayName.ifBlank { activeAccountLabel }}",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (isProviderSurface) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Provider Hub", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${normalizedProviderListings.count { it.status != "cancelled" }} live listings • ${pendingProviderQuoteRequests} quotes waiting",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(onClick = openCreateListingDialog) {
                                Text("New listing")
                            }
                            AssistChip(
                                onClick = onRefreshProviderInbox,
                                enabled = !loadingProviderInbox,
                                label = { Text(if (loadingProviderInbox) "Refreshing" else "Refresh inbox") },
                            )
                            if (pendingProviderQuoteRequests > 0) {
                                AssistChip(onClick = {}, enabled = false, label = { Text("$pendingProviderQuoteRequests pending quotes") })
                            }
                            AssistChip(
                                onClick = {
                                    plansSheetSectionKey = PlansSheetSection.LISTINGS.key
                                    showPlansSheet = true
                                },
                                label = { Text("Bookings workspace") },
                            )
                        }
                    }
                }

                ProviderBusinessProfileCard(
                    businessName = providerBusinessName,
                    onClick = {
                        primaryProviderListing?.let(openEditListingDialog) ?: openCreateListingDialog()
                    },
                )

                Text(
                    "Create listings, respond to quotes, and keep availability current without leaving Hub.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                ProviderScheduleOverviewCard(
                    todayAppointmentCount = todayProviderBookingCount,
                    pendingConfirmationCount = pendingProviderBookingCount,
                    nextWeekAppointmentCount = nextWeekProviderBookingCount,
                    nextCalendarEventCount = nextCalendarEventCount,
                    nextAppointment = nextProviderBooking,
                    today = today,
                    onOpenCalendar = {
                        providerCalendarViewKey = ProviderCalendarView.WEEK.key
                        providerCalendarAnchorDate = today.toString()
                        showProviderCalendarSheet = true
                    },
                )

                Text(
                    if (todayProviderBookingCount > 0) "Today's appointments" else "Next appointments",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (providerSchedulePreviewBookings.isEmpty()) {
                    HomeEmptyCard("Upcoming provider bookings will appear here once owners request a slot.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        providerSchedulePreviewBookings.forEach { booking ->
                            ProviderAppointmentCard(
                                booking = booking,
                                today = today,
                                busy = isSubmittingProviderInboxAction,
                                onOpenDetails = {
                                    selectedAppointment = booking.toAppointmentPopupState(sourceLabel = "Provider bookings")
                                },
                                onAddToCalendar = {
                                    providerBookingToCalendarDraft(booking)?.let { draft ->
                                        context.openCalendarDraft(draft)
                                    }
                                },
                                onConfirm = if (booking.status.lowercase() in setOf("requested", "reschedule_requested")) {
                                    { onConfirmProviderBooking(booking.id) }
                                } else {
                                    null
                                },
                                onDecline = if (booking.status.lowercase() in setOf("requested", "reschedule_requested")) {
                                    { onDeclineProviderBooking(booking.id) }
                                } else {
                                    null
                                },
                                onReschedule = if (isBookingResolvedStatus(booking.status)) {
                                    null
                                } else {
                                    {
                                        rescheduleDialogItemId = booking.id
                                        rescheduleDate = booking.date.toLocalDateString()
                                        rescheduleTimeSlot = booking.timeSlot
                                        rescheduleNote = ""
                                    }
                                },
                                onMessage = booking.messageActionOrNull(onOpenMessages),
                            )
                        }
                    }
                }

                Text("Listings", style = MaterialTheme.typography.titleSmall)
                if (normalizedProviderListings.isEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HomeEmptyCard("No provider listings yet.")
                        Button(onClick = openCreateListingDialog) {
                            Text("Create first listing")
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        normalizedProviderListings.take(4).forEach { listing ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Text(listing.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                            Text(
                                                "${listing.category.toReadableLabel()} • ${listing.suburb.ifBlank { "Suburb pending" }} • From $${listing.priceFrom}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        StatusBadge(listing.status)
                                    }
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Button(onClick = { openEditListingDialog(listing) }) {
                                            Text("Edit")
                                        }
                                        if (listing.status == "cancelled") {
                                            TextButton(onClick = { onRestoreProviderListing(listing.id) }) {
                                                Text("Restore")
                                            }
                                        } else {
                                            TextButton(onClick = { onCancelProviderListing(listing.id) }) {
                                                Text("Pause")
                                            }
                                            TextButton(onClick = { openBlackoutDialog(listing) }) {
                                                Text("Block slot")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Inbox", style = MaterialTheme.typography.titleSmall)
                    if (pendingProviderQuoteRequests > 0) {
                        StatusBadge("$pendingProviderQuoteRequests waiting")
                    }
                }
                if (loadingProviderInbox) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (providerInboxItems.isEmpty()) {
                    HomeEmptyCard("Provider inbox will appear when your listings receive quotes or bookings.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        providerInboxItems.take(4).forEach { item ->
                            val isSendingQuoteOffer = item.id in sendingQuoteOfferItemIds
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            item.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        StatusBadge(item.status)
                                    }
                                    Text(
                                        "${item.providerName} • ${item.subtitle}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (item.itemType == "quote_request" && !item.quoteRequestId.isNullOrBlank()) {
                                            Button(
                                                onClick = {
                                                    offerDialogItemId = item.id
                                                    offerPriceAud = "65.00"
                                                    offerDate = LocalDate.now().plusDays(1).toString()
                                                    offerTimeSlot = "09:00"
                                                    offerExpiryDate = LocalDate.now().plusDays(2).toString()
                                                    offerExpiryTime = "09:00"
                                                    offerNote = ""
                                                },
                                                enabled = !isSendingQuoteOffer && !isSubmittingProviderInboxAction,
                                            ) {
                                                Text(if (isSendingQuoteOffer) "Sending..." else "Create offer")
                                            }
                                        }
                                        if (item.itemType == "booking" && !item.bookingId.isNullOrBlank()) {
                                            if (item.status == "requested" || item.status == "reschedule_requested") {
                                                Button(
                                                    onClick = { onConfirmProviderBooking(item.bookingId) },
                                                    enabled = !isSubmittingProviderInboxAction,
                                                ) {
                                                    Text("Confirm")
                                                }
                                                TextButton(
                                                    onClick = { onDeclineProviderBooking(item.bookingId) },
                                                    enabled = !isSubmittingProviderInboxAction,
                                                ) {
                                                    Text("Decline")
                                                }
                                            }
                                        }
                                        TextButton(
                                            onClick = { onOpenMessages(item.customerUserId, null) },
                                            enabled = !item.customerUserId.isNullOrBlank(),
                                        ) {
                                            Text("Message")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (showActivationQa) {
                    HomeTileCard(
                        title = "Activation QA",
                        subtitle = activationSummary.subtitle,
                        preview = activationSummary.preview,
                        badgeText = activationSummary.failedCount.takeIf { count -> count > 0 }?.toString(),
                        icon = { Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(28.dp)) },
                        onClick = { showActivationSheet = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val dogSummary = buildDogSummary(profileInfo)
                        HomeTileCard(
                            title = "Dog",
                            subtitle = profileInfo.dogName.ifBlank { "Dog name not set" },
                            preview = dogSummary,
                            onClick = { showDogEditor = true },
                            contentPadding = PaddingValues(start = 10.dp, top = 10.dp, end = 48.dp, bottom = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                        val humanSubtitle = buildHumanSubtitle(profileInfo, profileRoleLabel)
                        val humanSummary = buildHumanSummary(profileInfo)
                        HomeTileCard(
                            title = "Human",
                            subtitle = profileInfo.displayName.ifBlank { activeAccountLabel },
                            preview = "$humanSubtitle\n$humanSummary",
                            onClick = { showHumanEditor = true },
                            contentPadding = PaddingValues(start = 48.dp, top = 10.dp, end = 10.dp, bottom = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                    IdentityPhotoThumb(
                        photoUrl = profileDogPhotoUrls.firstOrNull(),
                        fallbackLabel = profileInfo.dogName.take(1).ifBlank { profileInitial },
                        width = 84.dp,
                        height = 108.dp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .zIndex(2f),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HomeTileCard(
                        title = "Social",
                        subtitle = "$totalFriendCount friends • QR add",
                        preview = "Friend QR + network",
                        badgeText = totalFriendCount.takeIf { it > 0 }?.toString(),
                        icon = { Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(36.dp)) },
                        onClick = { showSocialSheet = true },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    HomeTileCard(
                        title = "Notifications",
                        subtitle = "$totalNotificationsCount updates",
                        preview = "Review now",
                        badgeText = unreadNotificationsCount.takeIf { it > 0 }?.toString(),
                        icon = { Icon(Icons.Default.NotificationsNone, contentDescription = null, modifier = Modifier.size(36.dp)) },
                        onClick = { showNotificationsSheet = true },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Plans", style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            HomeTileCard(
                                title = "Community",
                                subtitle = communitySubtitle,
                                preview = "See meetup plans",
                                badgeText = upcomingJoinedEvents.size.takeIf { it > 0 }?.toString(),
                                icon = { Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(24.dp)) },
                                onClick = {
                                    plansSheetSectionKey = PlansSheetSection.COMMUNITY.key
                                    showPlansSheet = true
                                },
                                contentPadding = PaddingValues(8.dp),
                                compact = true,
                                previewMaxLines = 1,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                            HomeTileCard(
                                title = "Listings",
                                subtitle = listingsSubtitle,
                                preview = "Manage bookings",
                                badgeText = (ownerBookings.size + providerBookings.size).takeIf { it > 0 }?.toString(),
                                icon = { Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(24.dp)) },
                                onClick = {
                                    plansSheetSectionKey = PlansSheetSection.LISTINGS.key
                                    showPlansSheet = true
                                },
                                contentPadding = PaddingValues(8.dp),
                                compact = true,
                                previewMaxLines = 1,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }
                    }
                }

                if (showActivationQa) {
                    HomeTileCard(
                        title = "Activation QA",
                        subtitle = activationSummary.subtitle,
                        preview = activationSummary.preview,
                        badgeText = activationSummary.failedCount.takeIf { count -> count > 0 }?.toString(),
                        icon = { Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(28.dp)) },
                        onClick = { showActivationSheet = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HomeActionRow(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    title = "Settings",
                    subtitle = "Theme, privacy, security",
                    onClick = { showSettingsSheet = true },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                HomeActionRow(
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    title = "Help",
                    subtitle = "Get support",
                    onClick = { showHelpDialog = true },
                )
            }
        }
    }

    if (showSocialSheet) {
        ModalBottomSheet(onDismissRequest = { showSocialSheet = false }) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                item {
                    Text("Social", style = MaterialTheme.typography.titleLarge)
                }
                item {
                    Text(
                        "For privacy and safety, add friends by scanning each other's QR in person.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        AssistChip(
                            onClick = {
                                onRefreshFriendQrPayload()
                                showFriendQrDialog = true
                            },
                            label = { Text("My Friend QR") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.People,
                                    contentDescription = null,
                                )
                            },
                        )
                        AssistChip(
                            onClick = { showFriendQrScanner = true },
                            label = { Text("Scan Friend QR") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                }
                friendQrStatusMessage?.let { statusMessage ->
                    item {
                        StatusBadge(statusMessage)
                    }
                }
                if (friendProfilesOnly.isEmpty()) {
                    item { HomeEmptyCard("No friends added yet. Scan a friend QR to connect.") }
                } else {
                    items(friendProfilesOnly, key = { profile -> profile.userId }) { profile ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AsyncImage(
                                    model = profile.dogPhotoUrl,
                                    contentDescription = "${profile.dogName} photo",
                                    modifier = Modifier.size(64.dp),
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(profile.humanName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        profile.dogName.ifBlank { "Dog" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    StatusBadge("friend")
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(onClick = { onOpenFriendMessages(profile.userId) }) {
                                        Text("Message")
                                    }
                                    TextButton(onClick = { onRemoveFriend(profile.userId) }) {
                                        Text("Remove")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFriendQrDialog) {
        AlertDialog(
            onDismissRequest = { showFriendQrDialog = false },
            title = { Text("Add Friend via QR") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Scan this on another phone to add you as a friend.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (friendQrLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    friendQrBitmap?.let { bitmap ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "Friend QR code",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                            )
                        }
                    } ?: run {
                        if (!friendQrLoading) {
                            Text(
                                "QR unavailable right now. Tap refresh and try again.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        "${profileInfo.displayName.ifBlank { "Member" }} • ${profileInfo.dogName.ifBlank { "Dog" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    formatIsoDateTimeLabel(friendQrExpiresAt)?.let { expiresLabel ->
                        Text(
                            "Expires: $expiresLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFriendQrDialog = false }) {
                    Text("Done")
                }
            },
        )
    }

    if (showFriendQrScanner) {
        QrScannerSheet(
            onDetected = { rawValue ->
                showFriendQrScanner = false
                when (val action = parseQrPayload(rawValue)) {
                    is QrPayloadAction.FriendToken -> {
                        friendQrStatusMessage = "Verifying friend QR..."
                        onResolveFriendQrToken(action.token)
                    }

                    is QrPayloadAction.FriendConnection -> {
                        friendQrStatusMessage = "Unsigned friend QR not accepted"
                    }

                    is QrPayloadAction.InviteToken -> {
                        friendQrStatusMessage = "Invite QR detected. Use Community scanner."
                    }

                    is QrPayloadAction.OpenUrl -> {
                        friendQrStatusMessage = "URL QR detected. Use a friend profile QR."
                    }

                    QrPayloadAction.Invalid -> {
                        friendQrStatusMessage = "QR payload not recognized"
                    }
                }
            },
            onDismiss = { showFriendQrScanner = false },
            sheetTitle = "Scan Friend QR",
            permissionDescription = "Camera permission is required to scan friend QR codes.",
            hintDescription = "Point the camera at your friend's BarkWise profile QR.",
        )
    }

    if (showNotificationsSheet) {
        ModalBottomSheet(onDismissRequest = { showNotificationsSheet = false }) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                item {
                    Text("Notifications", style = MaterialTheme.typography.titleLarge)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        AssistChip(onClick = onMarkAllNotificationsRead, label = { Text("Mark all read") })
                        AssistChip(onClick = onClearLocalNotifications, label = { Text("Clear local") })
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        FilterChip(
                            selected = notifyFollowedGroupAlerts,
                            onClick = {
                                onUpdateNotificationPreferences(
                                    !notifyFollowedGroupAlerts,
                                    notifySavedPostUpdates,
                                    notifySafetyAlerts,
                                )
                            },
                            label = { Text("Groups") },
                        )
                        FilterChip(
                            selected = notifySavedPostUpdates,
                            onClick = {
                                onUpdateNotificationPreferences(
                                    notifyFollowedGroupAlerts,
                                    !notifySavedPostUpdates,
                                    notifySafetyAlerts,
                                )
                            },
                            label = { Text("Saved") },
                        )
                        FilterChip(
                            selected = notifySafetyAlerts,
                            onClick = {
                                onUpdateNotificationPreferences(
                                    notifyFollowedGroupAlerts,
                                    notifySavedPostUpdates,
                                    !notifySafetyAlerts,
                                )
                            },
                            label = { Text("Safety") },
                        )
                    }
                }
                if (notifications.isEmpty()) {
                    item { HomeEmptyCard("No notifications yet.") }
                } else {
                    items(notifications.take(30), key = { notification -> notification.id }) { notification ->
                        Card(
                            onClick = {
                                val appointment = resolveAppointmentFromNotification(
                                    notification = notification,
                                    ownerBookings = ownerBookings,
                                    providerBookings = providerBookings,
                                )
                                if (appointment != null) {
                                    selectedAppointment = appointment
                                    showNotificationsSheet = false
                                    if (!notification.read) onMarkNotificationRead(notification.id)
                                } else {
                                    onOpenNotificationDeepLink(notification)
                                }
                            },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(notification.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text(
                                    notification.body,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (!notification.read) {
                                        TextButton(
                                            onClick = { onMarkNotificationRead(notification.id) },
                                            modifier = Modifier.wrapContentWidth(),
                                        ) { Text("Mark read") }
                                    }
                                    if (notification.read) {
                                        StatusBadge("read")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPlansSheet) {
        val showCommunitySection = plansSheetSection != PlansSheetSection.LISTINGS
        val showListingsSection = plansSheetSection != PlansSheetSection.COMMUNITY
        ModalBottomSheet(onDismissRequest = { showPlansSheet = false }) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                item {
                    Text("Plans & bookings", style = MaterialTheme.typography.titleLarge)
                }
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        FilterChip(
                            selected = plansSheetSection == PlansSheetSection.ALL,
                            onClick = { plansSheetSectionKey = PlansSheetSection.ALL.key },
                            label = { Text("All") },
                        )
                        FilterChip(
                            selected = plansSheetSection == PlansSheetSection.COMMUNITY,
                            onClick = { plansSheetSectionKey = PlansSheetSection.COMMUNITY.key },
                            label = { Text("Community") },
                        )
                        FilterChip(
                            selected = plansSheetSection == PlansSheetSection.LISTINGS,
                            onClick = { plansSheetSectionKey = PlansSheetSection.LISTINGS.key },
                            label = { Text("Listings") },
                        )
                    }
                }
                item {
                    Text(
                        "Community events and listings bookings in one workspace.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showCommunitySection) {
                    item { Text("Community events", style = MaterialTheme.typography.titleSmall) }
                    if (upcomingJoinedEvents.isEmpty()) {
                        item { HomeEmptyCard("No upcoming community events.") }
                    } else {
                        items(upcomingJoinedEvents.take(12), key = { event -> event.id }) { event ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(event.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                    Text(
                                        "${event.date.toLocalDateString()} • ${event.suburb}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                if (showListingsSection) {
                    if (isProviderSurface) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Appointments", style = MaterialTheme.typography.titleSmall)
                                AssistChip(
                                    onClick = {
                                        providerScheduleFilterKey = ProviderScheduleFilter.NEXT_7_DAYS.key
                                    },
                                    label = { Text("${nextWeekProviderBookingCount} next 7 days") },
                                )
                            }
                        }
                        item {
                            Text(
                                "Review incoming bookings, confirm requests, and keep your calendar in sync.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                            ) {
                                ProviderScheduleFilter.values().forEach { filter ->
                                    FilterChip(
                                        selected = selectedProviderScheduleFilter == filter,
                                        onClick = { providerScheduleFilterKey = filter.key },
                                        label = { Text(filter.label) },
                                    )
                                }
                            }
                        }
                        if (providerScheduleGroups.isEmpty()) {
                            item {
                                val emptyMessage = if (activeProviderBookings.isEmpty()) {
                                    "No provider appointments are booked yet."
                                } else {
                                    "No appointments match this schedule filter."
                                }
                                HomeEmptyCard(emptyMessage)
                            }
                        } else {
                            items(providerScheduleGroups, key = { group -> "provider_schedule_${group.date}" }) { group ->
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        buildProviderScheduleDayLabel(group.date, today),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    group.bookings.forEach { booking ->
                                        ProviderAppointmentCard(
                                            booking = booking,
                                            today = today,
                                            busy = isSubmittingProviderInboxAction,
                                            onOpenDetails = {
                                                selectedAppointment = booking.toAppointmentPopupState(sourceLabel = "Provider bookings")
                                            },
                                            onAddToCalendar = {
                                                providerBookingToCalendarDraft(booking)?.let { draft ->
                                                    context.openCalendarDraft(draft)
                                                }
                                            },
                                            onConfirm = if (booking.status.lowercase() in setOf("requested", "reschedule_requested")) {
                                                { onConfirmProviderBooking(booking.id) }
                                            } else {
                                                null
                                            },
                                            onDecline = if (booking.status.lowercase() in setOf("requested", "reschedule_requested")) {
                                                { onDeclineProviderBooking(booking.id) }
                                            } else {
                                                null
                                            },
                                            onReschedule = if (isBookingResolvedStatus(booking.status)) {
                                                null
                                            } else {
                                                {
                                                    rescheduleDialogItemId = booking.id
                                                    rescheduleDate = booking.date.toLocalDateString()
                                                    rescheduleTimeSlot = booking.timeSlot
                                                    rescheduleNote = ""
                                                }
                                            },
                                            onMessage = booking.messageActionOrNull(onOpenMessages),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (isProviderSurface) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Provider listings", style = MaterialTheme.typography.titleSmall)
                                AssistChip(
                                    onClick = openCreateListingDialog,
                                    label = { Text("New listing") },
                                )
                            }
                        }
                    }
                    if (isProviderSurface) {
                        item {
                            Text(
                                "Create, pause, and update listing availability without leaving this workspace.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (normalizedProviderListings.isEmpty()) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    HomeEmptyCard("No provider listings yet.")
                                    Button(onClick = openCreateListingDialog) {
                                        Text("Create first listing")
                                    }
                                }
                            }
                        } else {
                            items(normalizedProviderListings, key = { listing -> "listing_${listing.id}" }) { listing ->
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                Text(listing.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                                Text(
                                                    "${listing.category.toReadableLabel()} • ${listing.suburb.ifBlank { "Suburb pending" }} • From $${listing.priceFrom}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            StatusBadge(listing.status)
                                        }
                                        if (listing.description.isNotBlank()) {
                                            Text(
                                                listing.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Button(onClick = { openEditListingDialog(listing) }) {
                                                Text("Edit")
                                            }
                                            if (listing.status == "cancelled") {
                                                TextButton(onClick = { onRestoreProviderListing(listing.id) }) {
                                                    Text("Restore")
                                                }
                                            } else {
                                                TextButton(onClick = { onCancelProviderListing(listing.id) }) {
                                                    Text("Pause")
                                                }
                                                TextButton(onClick = { openBlackoutDialog(listing) }) {
                                                    Text("Block slot")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Provider inbox", style = MaterialTheme.typography.titleSmall)
                                AssistChip(
                                    onClick = onRefreshProviderInbox,
                                    enabled = !loadingProviderInbox,
                                    label = { Text(if (loadingProviderInbox) "Refreshing" else "Refresh") },
                                )
                            }
                        }
                        item {
                            Text(
                                "Quote requests and bookings from your listings in one queue.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (loadingProviderInbox) {
                            item {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        if (canLoadProviderInbox && providerInboxItems.isEmpty()) {
                            val emptyInboxMessage = if (hasProviderListings) {
                                "Provider inbox will appear when your listings receive quotes or bookings."
                            } else {
                                "Create your first listing to start receiving quotes and bookings."
                            }
                            item { HomeEmptyCard(emptyInboxMessage) }
                        } else if (canLoadProviderInbox) {
                            items(providerInboxItems.take(20), key = { item -> "provider_inbox_${item.id}" }) { item ->
                                val isSendingQuoteOffer = item.id in sendingQuoteOfferItemIds
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                item.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                            )
                                            StatusBadge(item.status)
                                        }
                                        Text(
                                            "${item.providerName} • ${item.subtitle}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        val dueLabel = formatIsoDateTimeLabel(item.dueAt)
                                        val meta = listOfNotNull(
                                            item.priority.takeIf { value -> value.equals("high", ignoreCase = true) }?.let { "High priority" },
                                            dueLabel?.let { "Due $it" },
                                        ).joinToString(" • ")
                                        if (meta.isNotBlank()) {
                                            Text(
                                                meta,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            if (item.itemType == "quote_request" && !item.quoteRequestId.isNullOrBlank()) {
                                                Button(
                                                    onClick = {
                                                        offerDialogItemId = item.id
                                                        offerPriceAud = "65.00"
                                                        offerDate = LocalDate.now().plusDays(1).toString()
                                                        offerTimeSlot = "09:00"
                                                        offerExpiryDate = LocalDate.now().plusDays(2).toString()
                                                        offerExpiryTime = "09:00"
                                                        offerNote = ""
                                                    },
                                                    enabled = !isSendingQuoteOffer && !isSubmittingProviderInboxAction,
                                                ) {
                                                    Text(if (isSendingQuoteOffer) "Sending..." else "Create offer")
                                                }
                                            }
                                            if (item.itemType == "booking" && !item.bookingId.isNullOrBlank()) {
                                                if (item.status == "requested" || item.status == "reschedule_requested") {
                                                    Button(
                                                        onClick = { onConfirmProviderBooking(item.bookingId) },
                                                        enabled = !isSubmittingProviderInboxAction,
                                                    ) {
                                                        Text("Confirm")
                                                    }
                                                    TextButton(
                                                        onClick = { onDeclineProviderBooking(item.bookingId) },
                                                        enabled = !isSubmittingProviderInboxAction,
                                                    ) {
                                                        Text("Decline")
                                                    }
                                                }
                                                if (item.status.lowercase() !in setOf("cancelled", "completed")) {
                                                    TextButton(
                                                        onClick = {
                                                            rescheduleDialogItemId = item.bookingId
                                                            rescheduleDate = firstIsoDateFromText(item.subtitle)
                                                                ?: LocalDate.now().plusDays(1).toString()
                                                            rescheduleTimeSlot = firstTimeSlotFromText(item.subtitle) ?: "09:00"
                                                            rescheduleNote = ""
                                                        },
                                                        enabled = !isSubmittingProviderInboxAction,
                                                    ) {
                                                        Text("Reschedule")
                                                    }
                                                }
                                            }
                                            TextButton(
                                                onClick = { onOpenMessages(item.customerUserId, null) },
                                                enabled = !item.customerUserId.isNullOrBlank(),
                                            ) {
                                                Text("Message")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { Text("Calendar", style = MaterialTheme.typography.titleSmall) }
                    if (calendarEvents.isEmpty()) {
                        item { HomeEmptyCard("No calendar events yet.") }
                    } else {
                        items(calendarEvents.take(20), key = { event -> "calendar_${event.id}" }) { event ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(event.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                        Text(
                                            "${event.date.toLocalDateString()} ${event.timeSlot}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            calendarEventToCalendarDraft(event)?.let { draft ->
                                                context.openCalendarDraft(draft)
                                            }
                                        },
                                        modifier = Modifier.wrapContentWidth(),
                                    ) {
                                        Text("Add")
                                    }
                                }
                            }
                        }
                    }

                    item { Text("Owner bookings", style = MaterialTheme.typography.titleSmall) }
                    item {
                        Text(
                            "Bookings you made as an owner",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (ownerBookings.isEmpty()) {
                        item { HomeEmptyCard("No owner bookings yet.") }
                    } else {
                        items(ownerBookings.take(20), key = { booking -> booking.id }) { booking ->
                            Card(
                                onClick = {
                                    selectedAppointment = booking.toAppointmentPopupState(sourceLabel = "Owner bookings")
                                },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(booking.serviceName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                        Text(
                                            "${booking.date.toLocalDateString()} ${booking.timeSlot}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        TextButton(
                                            onClick = {
                                                ownerBookingToCalendarDraft(booking)?.let { draft ->
                                                    context.openCalendarDraft(draft)
                                                }
                                            },
                                            modifier = Modifier.wrapContentWidth(),
                                        ) {
                                            Text("Add")
                                        }
                                        StatusBadge(booking.status)
                                    }
                                }
                            }
                        }
                    }
                    if (isProviderSurface) {
                        item { Text("Provider bookings", style = MaterialTheme.typography.titleSmall) }
                        item {
                            Text(
                                "Bookings requested from your provider listings",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (providerBookings.isEmpty()) {
                            item { HomeEmptyCard("No provider bookings yet.") }
                        } else {
                            items(providerBookings.take(20), key = { booking -> "provider_booking_${booking.id}" }) { booking ->
                                ProviderAppointmentCard(
                                    booking = booking,
                                    today = today,
                                    busy = isSubmittingProviderInboxAction,
                                    onOpenDetails = {
                                        selectedAppointment = booking.toAppointmentPopupState(sourceLabel = "Provider bookings")
                                    },
                                    onAddToCalendar = {
                                        providerBookingToCalendarDraft(booking)?.let { draft ->
                                            context.openCalendarDraft(draft)
                                        }
                                    },
                                    onConfirm = if (booking.status.lowercase() in setOf("requested", "reschedule_requested")) {
                                        { onConfirmProviderBooking(booking.id) }
                                    } else {
                                        null
                                    },
                                    onDecline = if (booking.status.lowercase() in setOf("requested", "reschedule_requested")) {
                                        { onDeclineProviderBooking(booking.id) }
                                    } else {
                                        null
                                    },
                                    onReschedule = if (isBookingResolvedStatus(booking.status)) {
                                        null
                                    } else {
                                        {
                                            rescheduleDialogItemId = booking.id
                                            rescheduleDate = booking.date.toLocalDateString()
                                            rescheduleTimeSlot = booking.timeSlot
                                            rescheduleNote = ""
                                        }
                                    },
                                    onMessage = booking.messageActionOrNull(onOpenMessages),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showProviderCalendarSheet) {
        ModalBottomSheet(onDismissRequest = { showProviderCalendarSheet = false }) {
            ProviderCalendarSheet(
                today = today,
                currentView = selectedProviderCalendarView,
                anchorDate = providerCalendarAnchor,
                bookingsByDate = providerBookingsByDate,
                calendarEventsByDate = providerCalendarEventsByDate,
                isSubmittingProviderAction = isSubmittingProviderInboxAction,
                onSelectView = { view -> providerCalendarViewKey = view.key },
                onMoveWindow = { step ->
                    providerCalendarAnchorDate = selectedProviderCalendarView
                        .moveAnchor(anchor = providerCalendarAnchor, step = step)
                        .toString()
                },
                onJumpToToday = {
                    providerCalendarAnchorDate = today.toString()
                },
                onSelectDate = { date ->
                    providerCalendarAnchorDate = date.toString()
                },
                onOpenBooking = { booking ->
                    showProviderCalendarSheet = false
                    selectedAppointment = booking.toAppointmentPopupState(sourceLabel = "Provider bookings")
                },
                onAddBookingToCalendar = { booking ->
                    providerBookingToCalendarDraft(booking)?.let { draft ->
                        context.openCalendarDraft(draft)
                    }
                },
                onConfirmBooking = { booking -> onConfirmProviderBooking(booking.id) },
                onDeclineBooking = { booking -> onDeclineProviderBooking(booking.id) },
                onRescheduleBooking = { booking ->
                    showProviderCalendarSheet = false
                    rescheduleDialogItemId = booking.id
                    rescheduleDate = booking.date.toLocalDateString()
                    rescheduleTimeSlot = booking.timeSlot
                    rescheduleNote = ""
                },
                onMessageBooking = { booking ->
                    booking.messageActionOrNull(onOpenMessages)?.invoke()
                },
                onAddCalendarEvent = { event ->
                    calendarEventToCalendarDraft(event)?.let { draft ->
                        context.openCalendarDraft(draft)
                    }
                },
            )
        }
    }

    if (showActivationQa && showActivationSheet) {
        ModalBottomSheet(onDismissRequest = { showActivationSheet = false }) {
            val lastEventLabel = activationFunnelMetrics?.lastEventAt?.toLocalDateString().orEmpty()
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                item { Text("Activation QA", style = MaterialTheme.typography.titleLarge) }
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        AssistChip(onClick = onRefreshActivationDashboard, label = { Text("Refresh") })
                        if (lastEventLabel.isNotBlank()) {
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                label = { Text("Last event $lastEventLabel") },
                            )
                        }
                    }
                }
                if (activationFunnelMetrics == null || activationFunnelMetrics.activationEventCount <= 0) {
                    item { HomeEmptyCard("No activation analytics yet. Run QR invite + OTP flow, then refresh.") }
                } else {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    "Events: ${activationFunnelMetrics.activationEventCount} • Diagnostics: ${activationFunnelMetrics.activationDiagnosticCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    "Users: ${activationFunnelMetrics.uniqueUserCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    item { Text("Stage counts", style = MaterialTheme.typography.titleSmall) }
                    items(
                        activationFunnelMetrics.byStage
                            .toList()
                            .sortedByDescending { (_, count) -> count }
                            .take(6),
                        key = { (stage, _) -> "stage_$stage" },
                    ) { (stage, count) ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stage.toReadableLabel(), style = MaterialTheme.typography.bodyMedium)
                                StatusBadge(count.toString())
                            }
                        }
                    }
                    item { Text("Recent failures", style = MaterialTheme.typography.titleSmall) }
                    if (activationFunnelMetrics.topFailures.isEmpty()) {
                        item { HomeEmptyCard("No activation failures recorded.") }
                    } else {
                        items(
                            activationFunnelMetrics.topFailures.take(5),
                            key = { failure -> "${failure.createdAt}_${failure.event}" },
                        ) { failure ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(failure.event.toReadableLabel(), style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        failure.error.ifBlank { "Unknown error" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        failure.createdAt.toLocalDateString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        ModalBottomSheet(onDismissRequest = { showSettingsSheet = false }) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                item { Text("Settings", style = MaterialTheme.typography.titleLarge) }
                item {
                    Text(
                        "Appearance",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        FilterChip(
                            selected = themeMode == HOME_THEME_MODE_LIGHT,
                            onClick = { applyThemeMode(HOME_THEME_MODE_LIGHT) },
                            label = { Text("Light") },
                        )
                        FilterChip(
                            selected = themeMode == HOME_THEME_MODE_DARK,
                            onClick = { applyThemeMode(HOME_THEME_MODE_DARK) },
                            label = { Text("Dark") },
                        )
                    }
                }
                item {
                    Text(
                        "Distribution",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                item {
                    Card(
                        onClick = { showInstallQrDialog = true },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Install QR", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Scan on second phone to open Play closed testing",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    Text(
                        "Security",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                item {
                    Card(
                        onClick = { showSecurityDetails = true },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Session protection", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "Invite + OTP is enabled for closed testing.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    Card(
                        onClick = onResetDeviceSignIn,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Reset device sign-in", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "Require invite + OTP again on this phone.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Biometric lock", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Preview for closed testing (local only).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = biometricLockEnabled,
                                onCheckedChange = { biometricLockEnabled = it },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showInstallQrDialog) {
        AlertDialog(
            onDismissRequest = { showInstallQrDialog = false },
            title = { Text("Install BarkWise") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Scan this QR on your second phone to open the Play closed-testing page.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    installQrBitmap?.let { bitmap ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "Install BarkWise QR",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                            )
                        }
                    } ?: Text(
                        "QR unavailable. Open link directly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        installPageUrl,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(installPageUrl),
                                ),
                            )
                        }
                    },
                ) {
                    Text("Open link")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInstallQrDialog = false }) {
                    Text("Close")
                }
            },
        )
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Help") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Need help during closed testing?")
                    Text(
                        "Use this area for FAQ and support contact actions. Dev contact actions will be wired next.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Close")
                }
            },
        )
    }

    if (showSecurityDetails) {
        AlertDialog(
            onDismissRequest = { showSecurityDetails = false },
            title = { Text("Security details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Current protections")
                    Text(
                        "Invite + OTP sign-in, authenticated API calls, and server-side session enforcement are enabled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Biometric unlock is staged for a later release.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSecurityDetails = false }) {
                    Text("Done")
                }
            },
        )
    }

    selectedAppointment?.let { appointment ->
        val canMessage = !appointment.messageUserId.isNullOrBlank() || !appointment.messageThreadId.isNullOrBlank()
        val canOpenListings = appointment.bookingId == null && appointment.deepLink?.startsWith("quote:") == true
        AlertDialog(
            onDismissRequest = { selectedAppointment = null },
            title = { Text(appointment.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        appointment.scheduleLabel,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "${appointment.counterpartLabel} • ${appointment.statusLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    appointment.description?.takeIf { it.isNotBlank() }?.let { description ->
                        Text(
                            description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    appointment.bookingId?.let { bookingId ->
                        Text(
                            "Appointment $bookingId",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                when {
                    canOpenListings -> {
                        Button(
                            onClick = {
                                val quoteDeepLink = appointment.deepLink ?: return@Button
                                selectedAppointment = null
                                showPlansSheet = false
                                showNotificationsSheet = false
                                onOpenNotificationDeepLink(
                                    AppNotification(
                                        id = "quote_popup_route",
                                        userId = activeUserId,
                                        title = appointment.title,
                                        body = appointment.description.orEmpty(),
                                        category = "booking",
                                        read = true,
                                        createdAt = "",
                                        deepLink = quoteDeepLink,
                                    ),
                                )
                            },
                        ) {
                            Text("Open listings")
                        }
                    }
                    canMessage -> {
                        Button(
                            onClick = {
                                selectedAppointment = null
                                showPlansSheet = false
                                showNotificationsSheet = false
                                onOpenMessages(appointment.messageUserId, appointment.messageThreadId)
                            },
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Comment,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text("Message", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedAppointment = null }) {
                    Text("Close")
                }
            },
        )
    }

    val offerDialogItem = offerDialogItemId?.let { id ->
        providerInboxItems.firstOrNull { item -> item.id == id }
    }
    if (offerDialogItem != null) {
        val normalizedPriceCents = parseAudPriceToCents(offerPriceAud)
        val validOfferDate = isIsoDateInput(offerDate)
        val validOfferTime = isTimeSlotInput(offerTimeSlot)
        val validExpiryDate = isIsoDateInput(offerExpiryDate)
        val validExpiryTime = isTimeSlotInput(offerExpiryTime)
        val expiresAtIso = buildIsoDateTime(offerExpiryDate, offerExpiryTime)
        val isSubmitting = offerDialogItem.id in sendingQuoteOfferItemIds
        val canSendOffer = normalizedPriceCents != null &&
            validOfferDate &&
            validOfferTime &&
            validExpiryDate &&
            validExpiryTime &&
            !expiresAtIso.isNullOrBlank() &&
            !isSubmitting
        AlertDialog(
            onDismissRequest = {
                if (!isSubmitting) offerDialogItemId = null
            },
            title = { Text("Send quote offer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${offerDialogItem.providerName} • ${offerDialogItem.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = offerPriceAud,
                        onValueChange = { offerPriceAud = it },
                        label = { Text("Price (AUD)") },
                        singleLine = true,
                        isError = offerPriceAud.isNotBlank() && normalizedPriceCents == null,
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = offerDate,
                        onValueChange = { offerDate = it },
                        label = { Text("Proposed date (YYYY-MM-DD)") },
                        singleLine = true,
                        isError = offerDate.isNotBlank() && !validOfferDate,
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = offerTimeSlot,
                        onValueChange = { offerTimeSlot = it },
                        label = { Text("Proposed time (HH:MM)") },
                        singleLine = true,
                        isError = offerTimeSlot.isNotBlank() && !validOfferTime,
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = offerExpiryDate,
                        onValueChange = { offerExpiryDate = it },
                        label = { Text("Expiry date (YYYY-MM-DD)") },
                        singleLine = true,
                        isError = offerExpiryDate.isNotBlank() && !validExpiryDate,
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = offerExpiryTime,
                        onValueChange = { offerExpiryTime = it },
                        label = { Text("Expiry time (HH:MM)") },
                        singleLine = true,
                        isError = offerExpiryTime.isNotBlank() && !validExpiryTime,
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = offerNote,
                        onValueChange = { offerNote = it },
                        label = { Text("Note (optional)") },
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = canSendOffer,
                    onClick = {
                        val price = normalizedPriceCents ?: return@Button
                        val expiresAt = expiresAtIso ?: return@Button
                        onSendQuoteOffer(
                            offerDialogItem.id,
                            price,
                            offerDate.trim(),
                            offerTimeSlot.trim(),
                            expiresAt,
                            offerNote.trim(),
                        )
                        offerDialogItemId = null
                    },
                ) {
                    Text(if (isSubmitting) "Sending..." else "Send offer")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isSubmitting,
                    onClick = { offerDialogItemId = null },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    val rescheduleDialogBooking = rescheduleDialogItemId?.let { id ->
        providerBookings.firstOrNull { booking -> booking.id == id }
    }
    if (rescheduleDialogBooking != null) {
        val validDate = isIsoDateInput(rescheduleDate)
        val validTimeSlot = isTimeSlotInput(rescheduleTimeSlot)
        val canSubmitReschedule = validDate && validTimeSlot && !isSubmittingProviderInboxAction
        AlertDialog(
            onDismissRequest = {
                if (!isSubmittingProviderInboxAction) rescheduleDialogItemId = null
            },
            title = { Text("Reschedule booking") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${rescheduleDialogBooking.serviceName} • Pet ${rescheduleDialogBooking.petName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = rescheduleDate,
                        onValueChange = { rescheduleDate = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        singleLine = true,
                        isError = rescheduleDate.isNotBlank() && !validDate,
                        enabled = !isSubmittingProviderInboxAction,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = rescheduleTimeSlot,
                        onValueChange = { rescheduleTimeSlot = it },
                        label = { Text("Time (HH:MM)") },
                        singleLine = true,
                        isError = rescheduleTimeSlot.isNotBlank() && !validTimeSlot,
                        enabled = !isSubmittingProviderInboxAction,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = rescheduleNote,
                        onValueChange = { rescheduleNote = it },
                        label = { Text("Reason (optional)") },
                        enabled = !isSubmittingProviderInboxAction,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = canSubmitReschedule,
                    onClick = {
                        onRescheduleProviderBooking(
                            rescheduleDialogBooking.id,
                            rescheduleDate.trim(),
                            rescheduleTimeSlot.trim(),
                            rescheduleNote.trim(),
                        )
                        rescheduleDialogItemId = null
                    },
                ) {
                    Text(if (isSubmittingProviderInboxAction) "Saving..." else "Reschedule")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isSubmittingProviderInboxAction,
                    onClick = { rescheduleDialogItemId = null },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    val listingDialogIsCreate = listingDialogListingId == ""
    val listingDialogVisible = listingDialogListingId != null
    if (listingDialogVisible) {
        val normalizedListingPrice = parseListingPrice(raw = listingPriceText)
        val canSubmitListing = listingName.trim().isNotBlank() &&
            listingSuburb.trim().isNotBlank() &&
            listingDescription.trim().length >= 8 &&
            normalizedListingPrice != null
        AlertDialog(
            onDismissRequest = { listingDialogListingId = null },
            title = { Text(if (listingDialogIsCreate) "Create provider listing" else "Edit provider listing") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = listingName,
                        onValueChange = { listingName = it },
                        label = { Text("Listing name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        FilterChip(
                            selected = listingCategory == "grooming",
                            onClick = { if (listingDialogIsCreate) listingCategory = "grooming" },
                            enabled = listingDialogIsCreate,
                            label = { Text("Grooming") },
                        )
                        FilterChip(
                            selected = listingCategory == "dog_walking",
                            onClick = { if (listingDialogIsCreate) listingCategory = "dog_walking" },
                            enabled = listingDialogIsCreate,
                            label = { Text("Dog walking") },
                        )
                    }
                    OutlinedTextField(
                        value = listingSuburb,
                        onValueChange = { listingSuburb = it },
                        label = { Text("Suburb") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = listingPriceText,
                        onValueChange = { listingPriceText = it },
                        label = { Text("Price from (AUD)") },
                        singleLine = true,
                        isError = listingPriceText.isNotBlank() && normalizedListingPrice == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = listingDescription,
                        onValueChange = { listingDescription = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = canSubmitListing,
                    onClick = {
                        val price = normalizedListingPrice ?: return@Button
                        if (listingDialogIsCreate) {
                            onCreateProviderListing(
                                listingName.trim(),
                                listingCategory,
                                listingSuburb.trim(),
                                listingDescription.trim(),
                                price,
                            )
                        } else {
                            val listingId = listingDialogListingId?.takeIf { it.isNotBlank() } ?: return@Button
                            onEditProviderListing(
                                listingId,
                                listingName.trim(),
                                listingSuburb.trim(),
                                listingDescription.trim(),
                                price,
                            )
                        }
                        listingDialogListingId = null
                    },
                ) {
                    Text(if (listingDialogIsCreate) "Create" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { listingDialogListingId = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    val blackoutListing = blackoutListingId?.let { id -> normalizedProviderListings.firstOrNull { listing -> listing.id == id } }
    if (blackoutListing != null) {
        val validBlackoutDate = isIsoDateInput(blackoutDate)
        val validBlackoutTime = isTimeSlotInput(blackoutTimeSlot)
        AlertDialog(
            onDismissRequest = { blackoutListingId = null },
            title = { Text("Block provider slot") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        blackoutListing.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = blackoutDate,
                        onValueChange = { blackoutDate = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        singleLine = true,
                        isError = blackoutDate.isNotBlank() && !validBlackoutDate,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = blackoutTimeSlot,
                        onValueChange = { blackoutTimeSlot = it },
                        label = { Text("Time (HH:MM)") },
                        singleLine = true,
                        isError = blackoutTimeSlot.isNotBlank() && !validBlackoutTime,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = blackoutReason,
                        onValueChange = { blackoutReason = it },
                        label = { Text("Reason (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = validBlackoutDate && validBlackoutTime,
                    onClick = {
                        onCreateProviderBlackout(
                            blackoutListing.id,
                            blackoutDate.trim(),
                            blackoutTimeSlot.trim(),
                            blackoutReason.trim(),
                        )
                        blackoutListingId = null
                    },
                ) {
                    Text("Block slot")
                }
            },
            dismissButton = {
                TextButton(onClick = { blackoutListingId = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showHumanEditor) {
        val normalizedDisplayName = displayName.trim()
        val normalizedEmail = email.trim()
        val normalizedPhone = phone.trim()
        val normalizedHumanPronouns = humanPronouns.trim()
        val normalizedHumanRoleLabel = humanRoleLabel.trim()
        val normalizedSuburb = suburb.trim()
        val normalizedFavoriteSuburbs = parseCommaOrNewlineValues(favoriteSuburbsText)
            .distinct()
            .take(8)
        val isEmailValid = normalizedEmail.isBlank() || isLikelyEmail(normalizedEmail)
        val isPhoneValid = normalizedPhone.isBlank() || isLikelyPhoneNumber(normalizedPhone)
        val canSaveHuman = normalizedDisplayName.isNotBlank() && isEmailValid && isPhoneValid
        AlertDialog(
            onDismissRequest = { showHumanEditor = false },
            title = { Text("Edit human profile") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it.take(48) },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = suburb,
                        onValueChange = { suburb = it.take(48) },
                        label = { Text("Suburb") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = humanRoleLabel,
                        onValueChange = { humanRoleLabel = it.take(48) },
                        label = { Text("Role") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = humanPronouns,
                        onValueChange = { humanPronouns = it.take(32) },
                        label = { Text("Pronouns") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Optional contact", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.take(72) },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = normalizedEmail.isNotBlank() && !isEmailValid,
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it.take(24) },
                        label = { Text("Phone") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = normalizedPhone.isNotBlank() && !isPhoneValid,
                    )
                    OutlinedTextField(
                        value = favoriteSuburbsText,
                        onValueChange = { favoriteSuburbsText = it.take(180) },
                        label = { Text("Favorite suburbs (comma/newline)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!isEmailValid) {
                        Text(
                            "Email format looks invalid.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (!isPhoneValid) {
                        Text(
                            "Phone format looks invalid.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = canSaveHuman,
                    onClick = {
                        onSaveProfile(
                            ProfileInfo(
                                displayName = normalizedDisplayName,
                                email = normalizedEmail,
                                phone = normalizedPhone,
                                humanPronouns = normalizedHumanPronouns,
                                humanRoleLabel = normalizedHumanRoleLabel,
                                serviceProviderMode = profileInfo.serviceProviderMode,
                                dogName = profileInfo.dogName,
                                dogAgeMonths = profileInfo.dogAgeMonths,
                                dogBreedMix = profileInfo.dogBreedMix,
                                dogGender = profileInfo.dogGender,
                                dogWeightKg = profileInfo.dogWeightKg,
                                dogPhotoUrls = profileInfo.dogPhotoUrls,
                                secondaryDogName = profileInfo.secondaryDogName,
                                secondaryDogAgeMonths = profileInfo.secondaryDogAgeMonths,
                                secondaryDogGender = profileInfo.secondaryDogGender,
                                secondaryDogWeightKg = profileInfo.secondaryDogWeightKg,
                                bio = profileInfo.bio,
                                suburb = normalizedSuburb,
                                favoriteSuburbs = normalizedFavoriteSuburbs,
                                playEnergyLevel = profileInfo.playEnergyLevel,
                                playStyle = profileInfo.playStyle,
                                socialConfidence = profileInfo.socialConfidence,
                                triggerNotes = profileInfo.triggerNotes,
                                idealMatch = profileInfo.idealMatch,
                                walkPreferences = profileInfo.walkPreferences,
                                trainingStyle = profileInfo.trainingStyle,
                                feedingRules = profileInfo.feedingRules,
                                consentBoundaries = profileInfo.consentBoundaries,
                                vaccinationStatus = profileInfo.vaccinationStatus,
                                microchipped = profileInfo.microchipped,
                                recallTrained = profileInfo.recallTrained,
                                leashReliability = profileInfo.leashReliability,
                                emergencyContactName = profileInfo.emergencyContactName,
                                emergencyContactPhone = profileInfo.emergencyContactPhone,
                                fieldVisibility = profileInfo.fieldVisibility,
                            ),
                        )
                        showHumanEditor = false
                    },
                ) {
                    Text("Save human")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHumanEditor = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showDogEditor) {
        val normalizedDisplayName = displayName.trim()
        val normalizedEmail = email.trim()
        val normalizedPhone = phone.trim()
        val normalizedHumanPronouns = humanPronouns.trim()
        val normalizedHumanRoleLabel = humanRoleLabel.trim()
        val normalizedDogName = dogName.trim()
        val normalizedDogAgeYears = dogAgeYearsText.trim().toIntOrNull()
        val normalizedDogRemainderMonths = dogAgeRemainderMonthsText.trim().toIntOrNull()
        val isDogAgeValid =
            (dogAgeYearsText.trim().isBlank() || normalizedDogAgeYears != null) &&
                (dogAgeRemainderMonthsText.trim().isBlank() || normalizedDogRemainderMonths != null) &&
                (normalizedDogRemainderMonths == null || normalizedDogRemainderMonths in 0..11)
        // TODO(public-testing): Revisit age representation before public launch.
        val normalizedDogAgeMonths = ((normalizedDogAgeYears ?: 0).coerceAtLeast(0) * 12) +
            (normalizedDogRemainderMonths ?: 0).coerceIn(0, 11)
        val normalizedDogBreedMix = dogBreedMix.trim()
        val normalizedDogGender = normalizeDogGender(dogGender).ifBlank { "male" }
        val normalizedDogWeightKg = dogWeightKg.trim()
        val isDogWeightValid = isDogWeightKgFormat(normalizedDogWeightKg)
        val normalizedSuburb = suburb.trim()
        val normalizedSecondaryDogName = if (showSecondDog) secondaryDogName.trim() else ""
        val normalizedSecondaryDogAgeYears = if (showSecondDog) secondaryDogAgeYearsText.trim().toIntOrNull() else 0
        val normalizedSecondaryDogRemainderMonths =
            if (showSecondDog) secondaryDogAgeRemainderMonthsText.trim().toIntOrNull() else 0
        val isSecondaryDogAgeValid = !showSecondDog || (
            (secondaryDogAgeYearsText.trim().isBlank() || normalizedSecondaryDogAgeYears != null) &&
                (secondaryDogAgeRemainderMonthsText.trim().isBlank() || normalizedSecondaryDogRemainderMonths != null) &&
                (normalizedSecondaryDogRemainderMonths == null || normalizedSecondaryDogRemainderMonths in 0..11)
            )
        val normalizedSecondaryDogAgeMonths = if (!showSecondDog) {
            0
        } else {
            ((normalizedSecondaryDogAgeYears ?: 0).coerceAtLeast(0) * 12) +
                (normalizedSecondaryDogRemainderMonths ?: 0).coerceIn(0, 11)
        }
        val normalizedSecondaryDogGender = if (showSecondDog) normalizeDogGender(secondaryDogGender).ifBlank { "male" } else ""
        val normalizedSecondaryDogWeightKg = if (showSecondDog) secondaryDogWeightKg.trim() else ""
        val isSecondaryDogWeightValid = !showSecondDog || isDogWeightKgFormat(normalizedSecondaryDogWeightKg)
        val parsedDogPhotoUrls = parseDogPhotoValues(dogPhotoUrlsText)
        val validDogPhotoUrls = parsedDogPhotoUrls
            .filter(::isValidProfilePhotoUrl)
            .distinct()
            .take(8)
        val hasInvalidDogPhotoUrls = parsedDogPhotoUrls.any { value -> !isValidProfilePhotoUrl(value) }
        val normalizedPlayEnergyLevel = playEnergyLevel.trim()
        val normalizedPlayStyle = playStyle.trim()
        val normalizedSocialConfidence = socialConfidence.trim()
        val normalizedTriggerNotes = triggerNotes.trim()
        val normalizedIdealMatch = idealMatch.trim()
        val normalizedWalkPreferences = walkPreferences.trim()
        val normalizedTrainingStyle = trainingStyle.trim()
        val normalizedFeedingRules = feedingRules.trim()
        val normalizedConsentBoundaries = consentBoundaries.trim()
        val normalizedVaccinationStatus = vaccinationStatus.trim()
        val normalizedLeashReliability = leashReliability.trim()
        val normalizedEmergencyContactName = emergencyContactName.trim()
        val normalizedEmergencyContactPhone = emergencyContactPhone.trim()
        val normalizedVisibility = profileInfo.fieldVisibility.toMutableMap().apply {
            put("suburb", normalizeVisibilityValue(suburbVisibility))
            put("dog_name", normalizeVisibilityValue(dogNameVisibility))
            put("trigger_notes", normalizeVisibilityValue(triggerNotesVisibility))
        }.mapValues { (_, value) -> normalizeVisibilityValue(value) }
        val isEmergencyPhoneValid =
            normalizedEmergencyContactPhone.isBlank() || isLikelyPhoneNumber(normalizedEmergencyContactPhone)
        val canSaveProfile = normalizedDogName.isNotBlank() &&
            isDogAgeValid &&
            isDogWeightValid &&
            isSecondaryDogAgeValid &&
            isSecondaryDogWeightValid &&
            isEmergencyPhoneValid &&
            !hasInvalidDogPhotoUrls
        val appendDogPhotoUrl: (String) -> Unit = append@{ rawUrl ->
            val normalizedUrl = rawUrl.trim()
            if (normalizedUrl.isBlank()) {
                return@append
            }
            val updatedUrls = (parseDogPhotoValues(dogPhotoUrlsText) + normalizedUrl)
                .map { value -> value.trim() }
                .filter { value -> value.isNotBlank() }
                .distinct()
                .take(8)
            dogPhotoUrlsText = updatedUrls.joinToString("\n")
        }
        val photoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (_: SecurityException) {
                    // Best effort only; some providers do not support persistable permissions.
                }
                appendDogPhotoUrl(uri.toString())
            }
        }
        val cameraLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicturePreview(),
        ) { bitmap ->
            if (bitmap != null) {
                saveProfilePhotoBitmap(context, bitmap)?.let { savedUri ->
                    appendDogPhotoUrl(savedUri.toString())
                }
            }
        }
        val cameraPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                runCatching { cameraLauncher.launch(null) }
            }
        }
        AlertDialog(
            onDismissRequest = { showDogEditor = false },
            title = { Text("Edit dog profile") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    OutlinedTextField(
                        value = dogName,
                        onValueChange = { dogName = it.take(48) },
                        label = { Text("Dog name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = dogAgeYearsText,
                            onValueChange = { dogAgeYearsText = it.filter { ch -> ch.isDigit() }.take(2) },
                            label = { Text("Age years") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = dogAgeRemainderMonthsText,
                            onValueChange = { dogAgeRemainderMonthsText = it.filter { ch -> ch.isDigit() }.take(2) },
                            label = { Text("Age months") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = dogBreedMix,
                        onValueChange = { dogBreedMix = it.take(64) },
                        label = { Text("Dog breed / mix") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DogGenderSwitch(
                        label = "Dog gender",
                        gender = dogGender,
                        onGenderChange = { dogGender = it },
                    )
                    OutlinedTextField(
                        value = dogWeightKg,
                        onValueChange = { dogWeightKg = it.filter { ch -> ch.isDigit() || ch == '.' }.take(4) },
                        label = { Text("Weight (kg, example 12.5)") },
                        placeholder = { Text("12.5") },
                        singleLine = true,
                        supportingText = { Text("Format: 2 digits, decimal, 1 digit") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Dog photos", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val permissionState = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA,
                                )
                                if (permissionState == PackageManager.PERMISSION_GRANTED) {
                                    runCatching { cameraLauncher.launch(null) }
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            enabled = parsedDogPhotoUrls.size < 8,
                        ) {
                            Text(if (parsedDogPhotoUrls.size < 8) "Take photo" else "Photo limit reached")
                        }
                        Button(
                            onClick = { photoPickerLauncher.launch(arrayOf("image/*")) },
                            enabled = parsedDogPhotoUrls.size < 8,
                        ) {
                            Text(if (parsedDogPhotoUrls.size < 8) "Upload photo" else "Photo limit reached")
                        }
                    }
                    if (parsedDogPhotoUrls.isEmpty()) {
                        Text(
                            "No photos yet. Upload from your phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            parsedDogPhotoUrls.forEachIndexed { index, photoUrl ->
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        AsyncImage(
                                            model = photoUrl,
                                            contentDescription = "Dog photo ${index + 1}",
                                            modifier = Modifier.size(88.dp),
                                        )
                                        if (index == 0) {
                                            Text(
                                                "Main photo",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        } else {
                                            TextButton(
                                                onClick = {
                                                    val reordered = buildList {
                                                        add(photoUrl)
                                                        parsedDogPhotoUrls
                                                            .filterIndexed { i, _ -> i != index }
                                                            .forEach(::add)
                                                    }
                                                    dogPhotoUrlsText = reordered.joinToString("\n")
                                                },
                                                modifier = Modifier.wrapContentWidth(),
                                            ) { Text("Set as main") }
                                        }
                                        TextButton(
                                            onClick = {
                                                val updatedUrls = parsedDogPhotoUrls
                                                    .filterIndexed { i, _ -> i != index }
                                                    .joinToString("\n")
                                                dogPhotoUrlsText = updatedUrls
                                            },
                                            modifier = Modifier.wrapContentWidth(),
                                        ) { Text("Remove") }
                                    }
                                }
                            }
                        }
                    }
                    if (!showSecondDog) {
                        Button(
                            onClick = {
                                showSecondDog = true
                                if (secondaryDogGender.isBlank()) {
                                    secondaryDogGender = "male"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Add second dog")
                        }
                    } else {
                        Text("Second dog", style = MaterialTheme.typography.labelMedium)
                        OutlinedTextField(
                            value = secondaryDogName,
                            onValueChange = { secondaryDogName = it.take(48) },
                            label = { Text("Second dog name") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedTextField(
                                value = secondaryDogAgeYearsText,
                                onValueChange = { secondaryDogAgeYearsText = it.filter { ch -> ch.isDigit() }.take(2) },
                                label = { Text("Age years") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = secondaryDogAgeRemainderMonthsText,
                                onValueChange = {
                                    secondaryDogAgeRemainderMonthsText = it.filter { ch -> ch.isDigit() }.take(2)
                                },
                                label = { Text("Age months") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        DogGenderSwitch(
                            label = "Second dog gender",
                            gender = secondaryDogGender,
                            onGenderChange = { secondaryDogGender = it },
                        )
                        OutlinedTextField(
                            value = secondaryDogWeightKg,
                            onValueChange = { secondaryDogWeightKg = it.filter { ch -> ch.isDigit() || ch == '.' }.take(4) },
                            label = { Text("Second dog weight (kg, 12.5)") },
                            placeholder = { Text("12.5") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(
                            onClick = {
                                showSecondDog = false
                                secondaryDogName = ""
                                secondaryDogAgeYearsText = ""
                                secondaryDogAgeRemainderMonthsText = ""
                                secondaryDogGender = ""
                                secondaryDogWeightKg = ""
                            },
                            modifier = Modifier.wrapContentWidth(),
                        ) {
                            Text("Remove second dog")
                        }
                    }
                    Text("Play compatibility", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = playEnergyLevel,
                        onValueChange = { playEnergyLevel = it.take(48) },
                        label = { Text("Energy level") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = playStyle,
                        onValueChange = { playStyle = it.take(72) },
                        label = { Text("Play style") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = socialConfidence,
                        onValueChange = { socialConfidence = it.take(72) },
                        label = { Text("Social confidence") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = triggerNotes,
                        onValueChange = { triggerNotes = it.take(220) },
                        label = { Text("Trigger or accessibility notes") },
                        supportingText = {
                            Text("Add needs like blind or low-vision handling cues, sound sensitivity, or touch boundaries.")
                        },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = idealMatch,
                        onValueChange = { idealMatch = it.take(120) },
                        label = { Text("Ideal dog match") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Care preferences", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = walkPreferences,
                        onValueChange = { walkPreferences = it.take(120) },
                        label = { Text("Walk preferences") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = trainingStyle,
                        onValueChange = { trainingStyle = it.take(120) },
                        label = { Text("Training style") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = feedingRules,
                        onValueChange = { feedingRules = it.take(120) },
                        label = { Text("Feeding rules") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = consentBoundaries,
                        onValueChange = { consentBoundaries = it.take(160) },
                        label = { Text("Consent boundaries") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Safety and trust", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = vaccinationStatus,
                        onValueChange = { vaccinationStatus = it.take(60) },
                        label = { Text("Vaccination status") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = microchipped,
                            onClick = { microchipped = !microchipped },
                            label = { Text("Microchipped") },
                        )
                        FilterChip(
                            selected = recallTrained,
                            onClick = { recallTrained = !recallTrained },
                            label = { Text("Recall trained") },
                        )
                    }
                    OutlinedTextField(
                        value = "",
                        onValueChange = {},
                        enabled = false,
                        label = { Text("Microchip number upload") },
                        placeholder = { Text("Example: 985141000123456") },
                        supportingText = {
                            Text("Greyed out for closed testing. Will be enabled for public testing.")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = leashReliability,
                        onValueChange = { leashReliability = it.take(80) },
                        label = { Text("Leash reliability") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = emergencyContactName,
                        onValueChange = { emergencyContactName = it.take(64) },
                        label = { Text("Emergency contact name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = emergencyContactPhone,
                        onValueChange = { emergencyContactPhone = it.take(24) },
                        label = { Text("Emergency contact phone") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Field visibility", style = MaterialTheme.typography.labelMedium)
                    VisibilitySelector(
                        label = "Suburb visibility",
                        selected = suburbVisibility,
                        onSelect = { suburbVisibility = it },
                    )
                    VisibilitySelector(
                        label = "Dog name visibility",
                        selected = dogNameVisibility,
                        onSelect = { dogNameVisibility = it },
                    )
                    VisibilitySelector(
                        label = "Trigger notes visibility",
                        selected = triggerNotesVisibility,
                        onSelect = { triggerNotesVisibility = it },
                    )
                    if (!isDogAgeValid) {
                        Text(
                            "Dog age must be numeric. Months must be 0-11.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (!isSecondaryDogAgeValid) {
                        Text(
                            "Second dog age must be numeric. Months must be 0-11.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (!isDogWeightValid) {
                        Text(
                            "Dog weight must match the format 12.5",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (!isSecondaryDogWeightValid) {
                        Text(
                            "Second dog weight must match the format 12.5",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (!isEmergencyPhoneValid) {
                        Text(
                            "Emergency contact phone format looks invalid.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (hasInvalidDogPhotoUrls) {
                        Text(
                            "Dog photo URLs must start with http://, https://, file://, or content://",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = canSaveProfile,
                    onClick = {
                        onSaveProfile(
                            ProfileInfo(
                                displayName = normalizedDisplayName,
                                email = normalizedEmail,
                                phone = normalizedPhone,
                                humanPronouns = normalizedHumanPronouns,
                                humanRoleLabel = normalizedHumanRoleLabel,
                                serviceProviderMode = profileInfo.serviceProviderMode,
                                dogName = normalizedDogName,
                                dogAgeMonths = normalizedDogAgeMonths,
                                dogBreedMix = normalizedDogBreedMix,
                                dogGender = normalizedDogGender,
                                dogWeightKg = normalizedDogWeightKg,
                                dogPhotoUrls = validDogPhotoUrls,
                                secondaryDogName = normalizedSecondaryDogName,
                                secondaryDogAgeMonths = normalizedSecondaryDogAgeMonths,
                                secondaryDogGender = normalizedSecondaryDogGender,
                                secondaryDogWeightKg = normalizedSecondaryDogWeightKg,
                                bio = profileInfo.bio,
                                suburb = normalizedSuburb,
                                favoriteSuburbs = profileInfo.favoriteSuburbs,
                                playEnergyLevel = normalizedPlayEnergyLevel,
                                playStyle = normalizedPlayStyle,
                                socialConfidence = normalizedSocialConfidence,
                                triggerNotes = normalizedTriggerNotes,
                                idealMatch = normalizedIdealMatch,
                                walkPreferences = normalizedWalkPreferences,
                                trainingStyle = normalizedTrainingStyle,
                                feedingRules = normalizedFeedingRules,
                                consentBoundaries = normalizedConsentBoundaries,
                                vaccinationStatus = normalizedVaccinationStatus,
                                microchipped = microchipped,
                                recallTrained = recallTrained,
                                leashReliability = normalizedLeashReliability,
                                emergencyContactName = normalizedEmergencyContactName,
                                emergencyContactPhone = normalizedEmergencyContactPhone,
                                fieldVisibility = normalizedVisibility,
                            )
                        )
                        showDogEditor = false
                    },
                ) {
                    Text("Save profile")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDogEditor = false }) {
                    Text("Cancel")
                }
            },
        )
    }

}

@Composable
private fun DogGenderSwitch(
    label: String,
    gender: String,
    onGenderChange: (String) -> Unit,
) {
    val normalized = normalizeDogGender(gender)
    val isFemale = normalized == "female"
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Male",
                style = MaterialTheme.typography.bodySmall,
                color = if (!isFemale) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Switch(
                checked = isFemale,
                onCheckedChange = { checked ->
                    onGenderChange(if (checked) "female" else "male")
                },
            )
            Text(
                "Female",
                style = MaterialTheme.typography.bodySmall,
                color = if (isFemale) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IdentityPhotoThumb(
    photoUrl: String?,
    fallbackLabel: String,
    width: androidx.compose.ui.unit.Dp = 56.dp,
    height: androidx.compose.ui.unit.Dp = 56.dp,
    modifier: Modifier = Modifier,
) {
    val cleaned = photoUrl?.trim().orEmpty()
    if (cleaned.isNotBlank()) {
        Card(
            shape = RoundedCornerShape(percent = 50),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = modifier.size(width = width, height = height),
        ) {
            AsyncImage(
                model = cleaned,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else {
        Card(
            shape = RoundedCornerShape(percent = 50),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1F22)),
            modifier = modifier.size(width = width, height = height),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    fallbackLabel.take(1).ifBlank { "B" }.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun HomeTileCard(
    title: String,
    subtitle: String,
    preview: String? = null,
    badgeText: String? = null,
    icon: (@Composable () -> Unit)? = null,
    topTrailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(10.dp),
    compact: Boolean = false,
    previewMaxLines: Int = 2,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
        ) {
            val showHeaderRow = icon != null || topTrailing != null || !badgeText.isNullOrBlank()
            if (showHeaderRow) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) {
                        icon()
                    } else {
                        Spacer(modifier = Modifier.size(24.dp))
                    }
                    if (topTrailing != null) {
                        topTrailing()
                    }
                    if (!badgeText.isNullOrBlank()) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Text(
                                badgeText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!preview.isNullOrBlank()) {
                Text(
                    preview,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = previewMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ProviderBusinessProfileCard(
    businessName: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "Business profile",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    businessName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HomeActionRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icon()
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private enum class PlansSheetSection(val key: String) {
    ALL("all"),
    COMMUNITY("community"),
    LISTINGS("listings"),
    ;

    companion object {
        fun fromKey(raw: String): PlansSheetSection {
            return entries.firstOrNull { option -> option.key == raw } ?: ALL
        }
    }
}

private data class ActivationQaSummary(
    val subtitle: String,
    val preview: String,
    val failedCount: Int,
)

internal data class AppointmentPopupState(
    val bookingId: String?,
    val title: String,
    val scheduleLabel: String,
    val counterpartLabel: String,
    val statusLabel: String,
    val description: String? = null,
    val messageUserId: String? = null,
    val messageThreadId: String? = null,
    val deepLink: String? = null,
)

private data class ProviderScheduleDayGroup(
    val date: LocalDate,
    val bookings: List<ProviderBooking>,
)

private enum class ProviderScheduleFilter(
    val key: String,
    val label: String,
) {
    TODAY("today", "Today"),
    TOMORROW("tomorrow", "Tomorrow"),
    NEXT_7_DAYS("next_7_days", "Next 7 days"),
    ALL_UPCOMING("all_upcoming", "All upcoming"),
    ;

    companion object {
        fun fromKey(key: String): ProviderScheduleFilter {
            return values().firstOrNull { filter -> filter.key == key } ?: TODAY
        }
    }
}

private enum class ProviderCalendarView(
    val key: String,
    val label: String,
) {
    MONTH("month", "Month"),
    WEEK("week", "Week"),
    SCHEDULE("schedule", "Schedule"),
    ;

    companion object {
        fun fromKey(key: String): ProviderCalendarView {
            return values().firstOrNull { view -> view.key == key } ?: WEEK
        }
    }
}

internal data class BookingMessageTarget(
    val userId: String? = null,
    val threadId: String? = null,
)

internal fun resolveAppointmentFromNotification(
    notification: AppNotification,
    ownerBookings: List<OwnerBooking>,
    providerBookings: List<ProviderBooking>,
): AppointmentPopupState? {
    val deepLink = notification.deepLink?.trim().orEmpty()
    if (deepLink.isBlank()) return null
    if (deepLink.startsWith("booking:")) {
        val bookingId = deepLink.removePrefix("booking:").trim()
        ownerBookings.firstOrNull { booking -> booking.id == bookingId }?.let { booking ->
            return booking.toAppointmentPopupState(sourceLabel = notification.title.ifBlank { "Appointment" })
        }
        providerBookings.firstOrNull { booking -> booking.id == bookingId }?.let { booking ->
            return booking.toAppointmentPopupState(sourceLabel = notification.title.ifBlank { "Appointment" })
        }
        return AppointmentPopupState(
            bookingId = bookingId.ifBlank { null },
            title = notification.title.ifBlank { "Appointment update" },
            scheduleLabel = "Open Listings for latest status.",
            counterpartLabel = "Booking",
            statusLabel = "Updated",
            description = notification.body.takeIf { text -> text.isNotBlank() },
            messageUserId = null,
            messageThreadId = null,
            deepLink = deepLink,
        )
    }
    if (deepLink.startsWith("quote:")) {
        val quoteTargetId = deepLink.removePrefix("quote:").trim()
        val candidate = ownerBookings
            .firstOrNull { booking -> quoteTargetId.isNotBlank() && booking.id == quoteTargetId }
            ?.toAppointmentPopupState(sourceLabel = "Quote response")
            ?: providerBookings
                .firstOrNull { booking -> quoteTargetId.isNotBlank() && booking.id == quoteTargetId }
                ?.toAppointmentPopupState(sourceLabel = "Quote response")
        return candidate?.copy(deepLink = deepLink) ?: AppointmentPopupState(
            bookingId = null,
            title = notification.title.ifBlank { "Quote response received" },
            scheduleLabel = "Appointment not scheduled yet.",
            counterpartLabel = "Listings",
            statusLabel = "Awaiting booking",
            description = notification.body.takeIf { text -> text.isNotBlank() },
            messageUserId = null,
            messageThreadId = null,
            deepLink = deepLink,
        )
    }
    return null
}

private fun OwnerBooking.toAppointmentPopupState(sourceLabel: String): AppointmentPopupState {
    val messageTarget = resolveMessageTarget()
    return AppointmentPopupState(
        bookingId = id,
        title = "$sourceLabel • $serviceName",
        scheduleLabel = "${date.toLocalDateString()} $timeSlot",
        counterpartLabel = providerAccountLabel.ifBlank { "Provider" },
        statusLabel = status.toReadableLabel(),
        description = note.takeIf { value -> value.isNotBlank() },
        messageUserId = messageTarget.userId,
        messageThreadId = messageTarget.threadId,
        deepLink = "booking:$id",
    )
}

private fun ProviderBooking.toAppointmentPopupState(sourceLabel: String): AppointmentPopupState {
    val messageTarget = resolveMessageTarget()
    return AppointmentPopupState(
        bookingId = id,
        title = "$sourceLabel • $serviceName",
        scheduleLabel = "${date.toLocalDateString()} $timeSlot",
        counterpartLabel = "Owner ${ownerUserId.ifBlank { petName }}",
        statusLabel = status.toReadableLabel(),
        description = "Pet: $petName",
        messageUserId = messageTarget.userId,
        messageThreadId = messageTarget.threadId,
        deepLink = "booking:$id",
    )
}

private fun ProviderBooking.scheduleDate(): LocalDate? = parseCalendarDate(date)

private fun ProviderBooking.matchesProviderScheduleFilter(
    filter: ProviderScheduleFilter,
    today: LocalDate,
): Boolean {
    val bookingDate = scheduleDate() ?: return false
    return when (filter) {
        ProviderScheduleFilter.TODAY -> bookingDate == today
        ProviderScheduleFilter.TOMORROW -> bookingDate == today.plusDays(1)
        ProviderScheduleFilter.NEXT_7_DAYS -> !bookingDate.isBefore(today) && !bookingDate.isAfter(today.plusDays(6))
        ProviderScheduleFilter.ALL_UPCOMING -> !bookingDate.isBefore(today)
    }
}

internal fun OwnerBooking.resolveMessageTarget(): BookingMessageTarget {
    return BookingMessageTarget(
        userId = providerUserId.takeIf { value -> value.isNotBlank() },
        threadId = threadId?.takeIf { value -> value.isNotBlank() },
    )
}

internal fun ProviderBooking.resolveMessageTarget(): BookingMessageTarget {
    return BookingMessageTarget(
        userId = ownerUserId.takeIf { value -> value.isNotBlank() },
        threadId = threadId?.takeIf { value -> value.isNotBlank() },
    )
}

private fun ProviderBooking.messageActionOrNull(
    onOpenMessages: (String?, String?) -> Unit,
): (() -> Unit)? {
    val target = resolveMessageTarget()
    if (target.userId.isNullOrBlank() && target.threadId.isNullOrBlank()) return null
    return {
        onOpenMessages(target.userId, target.threadId)
    }
}

private fun String.toReadableLabel(): String {
    return split("_")
        .filter { part -> part.isNotBlank() }
        .joinToString(" ") { part ->
            part.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
        }
        .ifBlank { this }
}

private fun isBookingResolvedStatus(status: String): Boolean {
    return status.lowercase() in setOf(
        "cancelled",
        "cancelled_by_owner",
        "cancelled_by_provider",
        "provider_declined",
        "completed",
    )
}

private fun parseCalendarDate(date: String): LocalDate? = try {
    LocalDate.parse(date.take(10))
} catch (_: DateTimeParseException) {
    null
}

private fun buildProviderScheduleDayLabel(
    date: LocalDate,
    today: LocalDate,
): String {
    val relativeLabel = when {
        date == today -> "Today"
        date == today.plusDays(1) -> "Tomorrow"
        else -> null
    }
    return listOfNotNull(relativeLabel, date.toString())
        .joinToString(" • ")
}

private fun ProviderCalendarView.moveAnchor(
    anchor: LocalDate,
    step: Int,
): LocalDate {
    return when (this) {
        ProviderCalendarView.MONTH -> anchor.plusMonths(step.toLong()).withDayOfMonth(1)
        ProviderCalendarView.WEEK -> anchor.plusWeeks(step.toLong())
        ProviderCalendarView.SCHEDULE -> anchor.plusDays(step * 7L)
    }
}

private fun LocalDate.startOfWeek(): LocalDate {
    return minusDays((dayOfWeek.value - 1).toLong())
}

private fun buildMonthGrid(anchor: LocalDate): List<List<LocalDate?>> {
    val monthStart = anchor.withDayOfMonth(1)
    val leadingEmptyDays = monthStart.dayOfWeek.value - 1
    val daysInMonth = monthStart.lengthOfMonth()
    val cells = buildList<LocalDate?> {
        repeat(leadingEmptyDays) { add(null) }
        repeat(daysInMonth) { offset ->
            add(monthStart.plusDays(offset.toLong()))
        }
        while (size % 7 != 0) {
            add(null)
        }
    }
    return cells.chunked(7)
}

private fun String.toLocalDateString(): String {
    return if (length >= 10 && this[4] == '-' && this[7] == '-') {
        take(10)
    } else {
        this
    }
}

private fun formatIsoDateTimeLabel(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val trimmed = raw.trim()
    return if (trimmed.length >= 16 && trimmed[10] == 'T') {
        trimmed.substring(0, 16).replace('T', ' ')
    } else {
        trimmed.take(16)
    }
}

private fun parseAudPriceToCents(raw: String): Int? {
    val cleaned = raw.trim().removePrefix("$")
    val amount = cleaned.toDoubleOrNull() ?: return null
    if (amount <= 0.0) return null
    return (amount * 100.0).roundToInt()
}

private fun parseListingPrice(raw: String): Int? {
    val cleaned = raw.trim().removePrefix("$")
    val amount = cleaned.toDoubleOrNull() ?: return null
    if (amount <= 0.0) return null
    return amount.roundToInt()
}

private fun canonicalProviderListingCategory(raw: String): String {
    return when (raw.trim().lowercase().replace(' ', '_')) {
        "dogwalking" -> "dog_walking"
        "dog_walking" -> "dog_walking"
        else -> "grooming"
    }
}

private fun isIsoDateInput(raw: String): Boolean {
    return Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(raw.trim())
}

private fun isTimeSlotInput(raw: String): Boolean {
    val match = Regex("^(\\d{2}):(\\d{2})$").matchEntire(raw.trim()) ?: return false
    val hour = match.groupValues[1].toIntOrNull() ?: return false
    val minute = match.groupValues[2].toIntOrNull() ?: return false
    return hour in 0..23 && minute in 0..59
}

private fun buildIsoDateTime(date: String, time: String): String? {
    if (!isIsoDateInput(date) || !isTimeSlotInput(time)) return null
    return "${date.trim()}T${time.trim()}:00"
}

private fun firstIsoDateFromText(raw: String): String? {
    return Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b")
        .find(raw)
        ?.value
}

private fun firstTimeSlotFromText(raw: String): String? {
    val match = Regex("\\b\\d{2}:\\d{2}\\b")
        .find(raw)
        ?.value
        ?: return null
    return if (isTimeSlotInput(match)) match else null
}

@Composable
private fun HomeEmptyCard(
    message: String,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Text(
            message,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ProviderScheduleOverviewCard(
    todayAppointmentCount: Int,
    pendingConfirmationCount: Int,
    nextWeekAppointmentCount: Int,
    nextCalendarEventCount: Int,
    nextAppointment: ProviderBooking?,
    today: LocalDate,
    onOpenCalendar: () -> Unit,
) {
    Card(
        onClick = onOpenCalendar,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Booking calendar", style = MaterialTheme.typography.titleSmall)
                Text(
                    nextAppointment?.let { booking ->
                        val dateLabel = booking.scheduleDate()?.let { date ->
                            buildProviderScheduleDayLabel(date, today)
                        } ?: booking.date.toLocalDateString()
                        "Next up: ${booking.serviceName} • $dateLabel ${booking.timeSlot}"
                    } ?: "No upcoming appointments booked yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                ProfileStatChip(label = "Today", value = todayAppointmentCount.toString())
                ProfileStatChip(label = "Pending", value = pendingConfirmationCount.toString())
                ProfileStatChip(label = "Next 7 days", value = nextWeekAppointmentCount.toString())
                ProfileStatChip(label = "Calendar items", value = nextCalendarEventCount.toString())
            }
            Button(onClick = onOpenCalendar) {
                Text("Open calendar")
            }
        }
    }
}

@Composable
private fun ProviderCalendarSheet(
    today: LocalDate,
    currentView: ProviderCalendarView,
    anchorDate: LocalDate,
    bookingsByDate: Map<LocalDate, List<ProviderBooking>>,
    calendarEventsByDate: Map<LocalDate, List<CalendarEvent>>,
    isSubmittingProviderAction: Boolean,
    onSelectView: (ProviderCalendarView) -> Unit,
    onMoveWindow: (Int) -> Unit,
    onJumpToToday: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onOpenBooking: (ProviderBooking) -> Unit,
    onAddBookingToCalendar: (ProviderBooking) -> Unit,
    onConfirmBooking: (ProviderBooking) -> Unit,
    onDeclineBooking: (ProviderBooking) -> Unit,
    onRescheduleBooking: (ProviderBooking) -> Unit,
    onMessageBooking: (ProviderBooking) -> Unit,
    onAddCalendarEvent: (CalendarEvent) -> Unit,
) {
    val weekStart = anchorDate.startOfWeek()
    val windowLabel = when (currentView) {
        ProviderCalendarView.MONTH -> {
            val month = anchorDate.withDayOfMonth(1).month.name.lowercase()
                .replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
            "$month ${anchorDate.year}"
        }
        ProviderCalendarView.WEEK -> "${weekStart} to ${weekStart.plusDays(6)}"
        ProviderCalendarView.SCHEDULE -> "From $anchorDate"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Provider calendar", style = MaterialTheme.typography.titleLarge)
        Text(
            "Switch views the same way you would in Google Calendar, but keep booking actions close at hand.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            ProviderCalendarView.values().forEach { view ->
                FilterChip(
                    selected = currentView == view,
                    onClick = { onSelectView(view) },
                    label = { Text(view.label) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(windowLabel, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onMoveWindow(-1) }) {
                    Text("Prev")
                }
                TextButton(onClick = onJumpToToday) {
                    Text("Today")
                }
                TextButton(onClick = { onMoveWindow(1) }) {
                    Text("Next")
                }
            }
        }
        when (currentView) {
            ProviderCalendarView.MONTH -> {
                val monthGrid = buildMonthGrid(anchorDate)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { label ->
                        Text(
                            label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    monthGrid.forEach { week ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            week.forEach { date ->
                                if (date == null) {
                                    Spacer(modifier = Modifier.weight(1f).height(76.dp))
                                } else {
                                    ProviderCalendarMonthDayCell(
                                        date = date,
                                        today = today,
                                        selected = date == anchorDate,
                                        bookingCount = bookingsByDate[date].orEmpty().size,
                                        calendarEventCount = calendarEventsByDate[date].orEmpty().size,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onSelectDate(date) },
                                    )
                                }
                            }
                        }
                    }
                }
                ProviderCalendarDayAgenda(
                    date = anchorDate,
                    today = today,
                    bookings = bookingsByDate[anchorDate].orEmpty(),
                    calendarEvents = calendarEventsByDate[anchorDate].orEmpty(),
                    isSubmittingProviderAction = isSubmittingProviderAction,
                    onOpenBooking = onOpenBooking,
                    onAddBookingToCalendar = onAddBookingToCalendar,
                    onConfirmBooking = onConfirmBooking,
                    onDeclineBooking = onDeclineBooking,
                    onRescheduleBooking = onRescheduleBooking,
                    onMessageBooking = onMessageBooking,
                    onAddCalendarEvent = onAddCalendarEvent,
                )
            }
            ProviderCalendarView.WEEK -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(7) { offset ->
                        val date = weekStart.plusDays(offset.toLong())
                        ProviderCalendarDayAgenda(
                            date = date,
                            today = today,
                            bookings = bookingsByDate[date].orEmpty(),
                            calendarEvents = calendarEventsByDate[date].orEmpty(),
                            isSubmittingProviderAction = isSubmittingProviderAction,
                            onOpenBooking = onOpenBooking,
                            onAddBookingToCalendar = onAddBookingToCalendar,
                            onConfirmBooking = onConfirmBooking,
                            onDeclineBooking = onDeclineBooking,
                            onRescheduleBooking = onRescheduleBooking,
                            onMessageBooking = onMessageBooking,
                            onAddCalendarEvent = onAddCalendarEvent,
                        )
                    }
                }
            }
            ProviderCalendarView.SCHEDULE -> {
                val scheduleDays = (0..13)
                    .map { offset -> anchorDate.plusDays(offset.toLong()) }
                    .filter { date ->
                        bookingsByDate[date].orEmpty().isNotEmpty() || calendarEventsByDate[date].orEmpty().isNotEmpty()
                    }
                if (scheduleDays.isEmpty()) {
                    HomeEmptyCard("No provider bookings or calendar items in the next two weeks.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        scheduleDays.forEach { date ->
                            ProviderCalendarDayAgenda(
                                date = date,
                                today = today,
                                bookings = bookingsByDate[date].orEmpty(),
                                calendarEvents = calendarEventsByDate[date].orEmpty(),
                                isSubmittingProviderAction = isSubmittingProviderAction,
                                onOpenBooking = onOpenBooking,
                                onAddBookingToCalendar = onAddBookingToCalendar,
                                onConfirmBooking = onConfirmBooking,
                                onDeclineBooking = onDeclineBooking,
                                onRescheduleBooking = onRescheduleBooking,
                                onMessageBooking = onMessageBooking,
                                onAddCalendarEvent = onAddCalendarEvent,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ProviderCalendarMonthDayCell(
    date: LocalDate,
    today: LocalDate,
    selected: Boolean,
    bookingCount: Int,
    calendarEventCount: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val containerColor = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        date == today -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelLarge)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (bookingCount > 0) {
                    Text("$bookingCount appt", style = MaterialTheme.typography.labelSmall)
                }
                if (calendarEventCount > 0) {
                    Text("$calendarEventCount item", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ProviderCalendarDayAgenda(
    date: LocalDate,
    today: LocalDate,
    bookings: List<ProviderBooking>,
    calendarEvents: List<CalendarEvent>,
    isSubmittingProviderAction: Boolean,
    onOpenBooking: (ProviderBooking) -> Unit,
    onAddBookingToCalendar: (ProviderBooking) -> Unit,
    onConfirmBooking: (ProviderBooking) -> Unit,
    onDeclineBooking: (ProviderBooking) -> Unit,
    onRescheduleBooking: (ProviderBooking) -> Unit,
    onMessageBooking: (ProviderBooking) -> Unit,
    onAddCalendarEvent: (CalendarEvent) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(buildProviderScheduleDayLabel(date, today), style = MaterialTheme.typography.titleSmall)
                val summary = listOfNotNull(
                    bookings.size.takeIf { count -> count > 0 }?.let { "$it bookings" },
                    calendarEvents.size.takeIf { count -> count > 0 }?.let { "$it calendar" },
                ).joinToString(" • ")
                if (summary.isNotBlank()) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (bookings.isEmpty() && calendarEvents.isEmpty()) {
                Text(
                    "Nothing scheduled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                bookings
                    .sortedBy { booking -> booking.timeSlot }
                    .forEach { booking ->
                        ProviderAppointmentCard(
                            booking = booking,
                            today = today,
                            busy = isSubmittingProviderAction,
                            onOpenDetails = { onOpenBooking(booking) },
                            onAddToCalendar = { onAddBookingToCalendar(booking) },
                            onConfirm = if (booking.status.lowercase() in setOf("requested", "reschedule_requested")) {
                                { onConfirmBooking(booking) }
                            } else {
                                null
                            },
                            onDecline = if (booking.status.lowercase() in setOf("requested", "reschedule_requested")) {
                                { onDeclineBooking(booking) }
                            } else {
                                null
                            },
                            onReschedule = if (isBookingResolvedStatus(booking.status)) {
                                null
                            } else {
                                { onRescheduleBooking(booking) }
                            },
                            onMessage = booking.messageActionOrNull { userId, threadId ->
                                onMessageBooking(booking.copy(ownerUserId = userId ?: booking.ownerUserId, threadId = threadId))
                            },
                        )
                    }
                calendarEvents
                    .sortedWith(compareBy<CalendarEvent> { event -> event.timeSlot.orEmpty() }.thenBy { event -> event.title })
                    .forEach { event ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(event.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                    Text(
                                        listOfNotNull(event.timeSlot, event.status.toReadableLabel()).joinToString(" • "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { onAddCalendarEvent(event) }) {
                                    Text("Add")
                                }
                            }
                        }
                    }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ProviderAppointmentCard(
    booking: ProviderBooking,
    today: LocalDate,
    busy: Boolean,
    onOpenDetails: () -> Unit,
    onAddToCalendar: () -> Unit,
    onConfirm: (() -> Unit)?,
    onDecline: (() -> Unit)?,
    onReschedule: (() -> Unit)?,
    onMessage: (() -> Unit)?,
) {
    val scheduleLabel = booking.scheduleDate()?.let { date ->
        "${buildProviderScheduleDayLabel(date, today)} • ${booking.timeSlot}"
    } ?: "${booking.date.toLocalDateString()} • ${booking.timeSlot}"
    val metadata = buildList {
        add("Pet ${booking.petName}")
        booking.ownerUserId
            .takeIf { value -> value.isNotBlank() }
            ?.let { ownerId -> add("Owner $ownerId") }
    }.joinToString(" • ")
    val canRespond = onConfirm != null && onDecline != null
    Card(
        onClick = onOpenDetails,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(booking.serviceName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(
                        scheduleLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        metadata,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusBadge(booking.status)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                if (canRespond) {
                    Button(
                        onClick = { onConfirm?.invoke() },
                        enabled = !busy,
                    ) {
                        Text("Confirm")
                    }
                    TextButton(
                        onClick = { onDecline?.invoke() },
                        enabled = !busy,
                    ) {
                        Text("Decline")
                    }
                }
                onReschedule?.let { action ->
                    TextButton(onClick = action, enabled = !busy) {
                        Text("Reschedule")
                    }
                }
                TextButton(onClick = onAddToCalendar) {
                    Text("Calendar")
                }
                onMessage?.let { action ->
                    TextButton(onClick = action) {
                        Text("Message")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileStatChip(
    label: String,
    value: String,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(value, style = MaterialTheme.typography.labelLarge)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProfileDetailSection(
    title: String,
    details: List<String?>,
) {
    val visibleDetails = details
        .mapNotNull { detail -> detail?.trim() }
        .filter { detail -> detail.isNotBlank() }
    if (visibleDetails.isEmpty()) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            visibleDetails.forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProfileTrustBadges(
    vaccinationStatus: String,
    microchipped: Boolean,
    recallTrained: Boolean,
    leashReliability: String,
    emergencyContactReady: Boolean,
) {
    val badges = buildList {
        add("Vaccination: ${vaccinationStatus.ifBlank { "Not set" }}")
        add(if (microchipped) "Microchipped" else "Microchip not confirmed")
        add(if (recallTrained) "Recall trained" else "Recall training in progress")
        add("Leash: ${leashReliability.ifBlank { "Not set" }}")
        add(if (emergencyContactReady) "Emergency contact ready" else "Emergency contact missing")
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Safety + trust", style = MaterialTheme.typography.labelLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                badges.forEach { badge ->
                    AssistChip(onClick = {}, label = { Text(badge) })
                }
            }
        }
    }
}

@Composable
private fun VisibilitySelector(
    label: String,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            VISIBILITY_OPTIONS.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    label = { Text(option.replaceFirstChar { it.uppercase() }) },
                )
            }
        }
    }
}

private fun profileCompletenessRatio(
    profileInfo: ProfileInfo,
    dogPhotoUrls: List<String>,
): Float {
    val checks = listOf(
        profileInfo.displayName.isNotBlank(),
        profileInfo.dogName.isNotBlank(),
        profileInfo.suburb.isNotBlank(),
        profileInfo.bio.isNotBlank(),
        dogPhotoUrls.isNotEmpty(),
    )
    return checks.count { passed -> passed }.toFloat() / checks.size.toFloat()
}

private fun completionRatio(vararg checks: Boolean): Float {
    if (checks.isEmpty()) return 0f
    return checks.count { passed -> passed }.toFloat() / checks.size.toFloat()
}

private fun buildCommunityPlansSubtitle(upcomingCount: Int): String {
    return if (upcomingCount > 0) {
        "$upcomingCount plans upcoming"
    } else {
        "No plans upcoming"
    }
}

private fun buildListingsPlansSubtitle(
    totalActiveBookings: Int,
    pendingProviderQuoteRequests: Int,
): String {
    return when {
        pendingProviderQuoteRequests > 0 ->
            "$totalActiveBookings active bookings • $pendingProviderQuoteRequests quotes waiting"
        totalActiveBookings > 0 -> "$totalActiveBookings active bookings"
        else -> "No active bookings"
    }
}

private fun buildActivationQaSummary(
    activationFunnelMetrics: CommunityActivationFunnel?,
): ActivationQaSummary {
    val activationEventCount = activationFunnelMetrics?.activationEventCount ?: 0
    val activationSucceededCount = activationFunnelMetrics?.byStatus?.get("succeeded") ?: 0
    val activationFailedCount = activationFunnelMetrics?.byStatus?.get("failed") ?: 0
    val activationTopStage = activationFunnelMetrics
        ?.byStage
        ?.maxByOrNull { entry -> entry.value }
        ?.key
        ?.toReadableLabel()
        ?: "No stage data"

    return if (activationEventCount > 0) {
        ActivationQaSummary(
            subtitle = "$activationEventCount activation events",
            preview = "Success $activationSucceededCount • Fail $activationFailedCount • Top: $activationTopStage",
            failedCount = activationFailedCount,
        )
    } else {
        ActivationQaSummary(
            subtitle = "No activation events yet",
            preview = "Open to view latest QR/auth onboarding telemetry.",
            failedCount = 0,
        )
    }
}

private fun buildDogSummary(profileInfo: ProfileInfo): String {
    return buildList {
        add(profileInfo.dogBreedMix.takeIf { it.isNotBlank() } ?: "Breed not set")
        profileInfo.dogAgeMonths.takeIf { it > 0 }?.let { months -> add("${months}m old") }
        add(
            profileInfo.playStyle
                .takeIf { it.isNotBlank() }
                ?: profileInfo.playEnergyLevel.ifBlank { "Energy not set" },
        )
        profileInfo.socialConfidence.takeIf { it.isNotBlank() }?.let { confidence ->
            add(confidence)
        }
        profileInfo.triggerNotes
            .takeIf { notes ->
                notes.contains("blind", ignoreCase = true) ||
                    notes.contains("low vision", ignoreCase = true) ||
                    notes.contains("vision", ignoreCase = true)
            }
            ?.let { add("Accessibility needs noted") }
    }.joinToString(" • ")
}

private fun buildHumanSubtitle(
    profileInfo: ProfileInfo,
    fallbackRoleLabel: String,
): String {
    return listOf(
        profileInfo.humanRoleLabel.takeIf { it.isNotBlank() },
        profileInfo.humanPronouns.takeIf { it.isNotBlank() },
    ).joinToString(" • ").ifBlank { fallbackRoleLabel }
}

private fun buildHumanSummary(profileInfo: ProfileInfo): String {
    return buildList {
        add("Suburb: ${profileInfo.suburb.ifBlank { "Not set" }}")
        if (profileInfo.email.isNotBlank() || profileInfo.phone.isNotBlank()) {
            add("Contact details added")
        } else {
            add("Contact details optional")
        }
        profileInfo.favoriteSuburbs
            .takeIf { it.isNotEmpty() }
            ?.let { suburbs -> add("Around ${suburbs.take(2).joinToString(", ")}") }
    }.joinToString(" • ")
}

private const val HOME_SETTINGS_PREFS = "home_settings"
private const val HOME_THEME_MODE_KEY = "theme_mode"
private const val HOME_THEME_MODE_LIGHT = "light"
private const val HOME_THEME_MODE_DARK = "dark"
private const val PLAY_CLOSED_TESTING_URL = "https://play.google.com/apps/testing/com.barkwise.app"
private const val LEGACY_INSTALL_URL = "https://api.barkwiseai.com/web/"
private val VISIBILITY_OPTIONS = listOf("public", "group", "friends", "private")

private fun normalizeVisibilityValue(raw: String): String {
    val normalized = raw.trim().lowercase()
    return normalized.takeIf { value -> value in VISIBILITY_OPTIONS } ?: "private"
}

private fun parseCommaOrNewlineValues(raw: String): List<String> {
    return raw
        .split(",", "\n")
        .map { value -> value.trim() }
        .filter { value -> value.isNotBlank() }
}

private fun parseDogPhotoValues(raw: String): List<String> {
    return raw
        .lines()
        .map { value -> value.trim() }
        .filter { value -> value.isNotBlank() }
}

private fun isJoinedEventUpcoming(event: JoinedEvent): Boolean {
    val eventDate = parseCalendarDate(event.date) ?: return false
    return !eventDate.isBefore(LocalDate.now())
}

private fun saveProfilePhotoBitmap(context: Context, bitmap: Bitmap): Uri? {
    return runCatching {
        val directory = File(context.filesDir, "profile_dog_photos")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val file = File(directory, "profile_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) {
                error("Failed to encode image")
            }
        }
        Uri.fromFile(file)
    }.getOrNull()
}

private fun isValidProfilePhotoUrl(value: String): Boolean {
    val normalized = value.trim().lowercase()
    return normalized.startsWith("https://") ||
        normalized.startsWith("http://") ||
        normalized.startsWith("file://") ||
        normalized.startsWith("content://")
}

private fun isLikelyEmail(value: String): Boolean {
    return Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$").matches(value)
}

private fun isLikelyPhoneNumber(value: String): Boolean {
    return Regex("^\\+?[0-9()\\-\\s]{6,20}$").matches(value)
}

private fun splitAgeToYearsMonths(totalMonths: Int): Pair<String, String> {
    if (totalMonths <= 0) return "" to ""
    val years = totalMonths / 12
    val months = totalMonths % 12
    return years.toString() to months.toString()
}

private fun isDogWeightKgFormat(value: String): Boolean {
    return Regex("^\\d{2}\\.\\d$").matches(value.trim())
}

private fun normalizeDogGender(raw: String): String {
    return when (raw.trim().lowercase()) {
        "female", "f", "girl" -> "female"
        "male", "m", "boy" -> "male"
        else -> ""
    }
}

@Composable
private fun StatusBadge(status: String) {
    val normalized = status.lowercase()
    val (bg, textColor) = when (normalized) {
        "provider_confirmed", "confirmed", "active", "member", "completed" -> Color(0xFFD8F5DE) to Color(0xFF1B5E20)
        "pending", "requested", "change requested", "reschedule_requested", "rescheduled", "held" ->
            Color(0xFFFFF2CC) to Color(0xFF7A5A00)
        "cancelled", "cancelled_by_owner", "cancelled_by_provider", "provider_declined", "blackout" ->
            Color(0xFFFFDAD6) to Color(0xFF8C1D18)
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Text(
        text = status.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
        style = MaterialTheme.typography.labelSmall,
        color = textColor,
        modifier = Modifier
            .background(bg, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
