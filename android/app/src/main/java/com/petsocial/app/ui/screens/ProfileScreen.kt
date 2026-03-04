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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.petsocial.app.data.CalendarEvent
import com.petsocial.app.data.AppNotification
import com.petsocial.app.ui.FriendProfile
import com.petsocial.app.ui.JoinedEvent
import com.petsocial.app.ui.OwnerBooking
import com.petsocial.app.ui.ProfileInfo
import com.petsocial.app.ui.ProviderBooking
import com.petsocial.app.ui.ProviderListing
import com.petsocial.app.ui.calendar.calendarEventToCalendarDraft
import com.petsocial.app.ui.calendar.openCalendarDraft
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.io.File
import java.io.FileOutputStream

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ProfileScreen(
    profileInfo: ProfileInfo,
    activeUserId: String,
    friendProfiles: List<FriendProfile>,
    joinedEvents: List<JoinedEvent>,
    ownerBookings: List<OwnerBooking>,
    providerListings: List<ProviderListing>,
    providerBookings: List<ProviderBooking>,
    calendarEvents: List<CalendarEvent>,
    notifications: List<AppNotification>,
    notifyFollowedGroupAlerts: Boolean,
    notifySavedPostUpdates: Boolean,
    notifySafetyAlerts: Boolean,
    showIdentityHeader: Boolean,
    onAddFriend: (String) -> Unit,
    onRemoveFriend: (String) -> Unit,
    onOpenFriendMessages: (String) -> Unit,
    onOpenMessages: (String?) -> Unit,
    onSaveProfile: (ProfileInfo) -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    onMarkAllNotificationsRead: () -> Unit,
    onClearLocalNotifications: () -> Unit,
    onOpenNotificationDeepLink: (AppNotification) -> Unit,
    onUpdateNotificationPreferences: (followedGroupAlerts: Boolean, savedPostUpdates: Boolean, safetyAlerts: Boolean) -> Unit,
) {
    val context = LocalContext.current
    var displayName by rememberSaveable(profileInfo.displayName) { mutableStateOf(profileInfo.displayName) }
    var email by rememberSaveable(profileInfo.email) { mutableStateOf(profileInfo.email) }
    var phone by rememberSaveable(profileInfo.phone) { mutableStateOf(profileInfo.phone) }
    var humanPronouns by rememberSaveable(profileInfo.humanPronouns) { mutableStateOf(profileInfo.humanPronouns) }
    var humanRoleLabel by rememberSaveable(profileInfo.humanRoleLabel) { mutableStateOf(profileInfo.humanRoleLabel) }
    var dogName by rememberSaveable(profileInfo.dogName) { mutableStateOf(profileInfo.dogName) }
    var dogAgeMonthsText by rememberSaveable(profileInfo.dogAgeMonths) {
        mutableStateOf(profileInfo.dogAgeMonths.takeIf { it > 0 }?.toString().orEmpty())
    }
    var dogBreedMix by rememberSaveable(profileInfo.dogBreedMix) { mutableStateOf(profileInfo.dogBreedMix) }
    var dogSexNeuter by rememberSaveable(profileInfo.dogSexNeuter) { mutableStateOf(profileInfo.dogSexNeuter) }
    var dogWeightClass by rememberSaveable(profileInfo.dogWeightClass) { mutableStateOf(profileInfo.dogWeightClass) }
    var dogPhotoUrlsText by rememberSaveable(profileInfo.dogPhotoUrls) {
        mutableStateOf(profileInfo.dogPhotoUrls.joinToString("\n"))
    }
    var secondaryDogName by rememberSaveable(profileInfo.secondaryDogName) { mutableStateOf(profileInfo.secondaryDogName) }
    var secondaryDogAgeMonthsText by rememberSaveable(profileInfo.secondaryDogAgeMonths) {
        mutableStateOf(profileInfo.secondaryDogAgeMonths.takeIf { it > 0 }?.toString().orEmpty())
    }
    var secondaryDogPhotoUrl by rememberSaveable(profileInfo.secondaryDogPhotoUrl) { mutableStateOf(profileInfo.secondaryDogPhotoUrl) }
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
    var emailVisibility by rememberSaveable(profileInfo.fieldVisibility["email"]) {
        mutableStateOf(profileInfo.fieldVisibility["email"] ?: "private")
    }
    var phoneVisibility by rememberSaveable(profileInfo.fieldVisibility["phone"]) {
        mutableStateOf(profileInfo.fieldVisibility["phone"] ?: "private")
    }
    var suburbVisibility by rememberSaveable(profileInfo.fieldVisibility["suburb"]) {
        mutableStateOf(profileInfo.fieldVisibility["suburb"] ?: "group")
    }
    var dogNameVisibility by rememberSaveable(profileInfo.fieldVisibility["dog_name"]) {
        mutableStateOf(profileInfo.fieldVisibility["dog_name"] ?: "friends")
    }
    var triggerNotesVisibility by rememberSaveable(profileInfo.fieldVisibility["trigger_notes"]) {
        mutableStateOf(profileInfo.fieldVisibility["trigger_notes"] ?: "private")
    }

    var friendSearchQuery by rememberSaveable { mutableStateOf("") }
    var friendFilter by rememberSaveable { mutableStateOf("suggested") }
    var showProfileEditor by rememberSaveable { mutableStateOf(false) }
    var showSocialSheet by rememberSaveable { mutableStateOf(false) }
    var showNotificationsSheet by rememberSaveable { mutableStateOf(false) }
    var showPlansSheet by rememberSaveable { mutableStateOf(false) }
    var plansSheetSection by rememberSaveable { mutableStateOf("all") }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var showHelpDialog by rememberSaveable { mutableStateOf(false) }
    var showSecurityDetails by rememberSaveable { mutableStateOf(false) }
    var biometricLockEnabled by rememberSaveable { mutableStateOf(false) }
    var selectedAppointment by remember { mutableStateOf<AppointmentPopupState?>(null) }
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
    val communitySubtitle = if (upcomingJoinedEvents.isNotEmpty()) {
        "${upcomingJoinedEvents.size} plans upcoming"
    } else {
        "No plans upcoming"
    }
    val listingsSubtitle = if (totalActiveBookings > 0) {
        "$totalActiveBookings active bookings"
    } else {
        "No active bookings"
    }
    val filteredFriendProfiles = remember(friendProfiles, friendSearchQuery, friendFilter) {
        val normalizedQuery = friendSearchQuery.trim().lowercase()
        friendProfiles
            .asSequence()
            .filter { profile ->
                when (friendFilter) {
                    "friends" -> profile.isFriend
                    "suggested" -> !profile.isFriend
                    else -> true
                }
            }
            .filter { profile ->
                normalizedQuery.isBlank() ||
                    profile.humanName.lowercase().contains(normalizedQuery) ||
                    profile.dogName.lowercase().contains(normalizedQuery)
            }
            .toList()
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

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Card(
                onClick = { showProfileEditor = true },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        profileDogPhotoUrls.firstOrNull()?.let { photoUrl ->
                            AsyncImage(
                                model = photoUrl,
                                contentDescription = "Primary dog profile photo",
                                modifier = Modifier.size(92.dp),
                            )
                        } ?: Card(
                            shape = CircleShape,
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1F22)),
                            modifier = Modifier.size(92.dp),
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    profileInitial,
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = Color.White,
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                profileInfo.displayName.ifBlank { activeAccountLabel },
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                profileRoleLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            Text(
                                profileInfo.dogName.ifBlank { "Add your dog's name" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                profileInfo.suburb.ifBlank { "Set your suburb" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (profileDogPhotoUrls.size > 1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            profileDogPhotoUrls.drop(1).take(3).forEachIndexed { index, url ->
                                AsyncImage(
                                    model = url,
                                    contentDescription = "Dog photo preview ${index + 2}",
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }
                    }
                    AdaptiveSplitCards(
                        first = { modifier ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = modifier,
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text("Human profile", style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        profileInfo.displayName.ifBlank { activeAccountLabel },
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        listOf(
                                            profileInfo.humanPronouns.takeIf { it.isNotBlank() },
                                            profileInfo.humanRoleLabel.takeIf { it.isNotBlank() },
                                        ).joinToString(" • ").ifBlank { "Role not set" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "Suburb: ${profileInfo.suburb.ifBlank { "Not set" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "Human completeness ${(humanProfileCompletion * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    LinearProgressIndicator(
                                        progress = { humanProfileCompletion },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        },
                        second = { modifier ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = modifier,
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text("Dog profile", style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        profileInfo.dogName.ifBlank { "Dog name not set" },
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        listOf(
                                            profileInfo.dogBreedMix.takeIf { it.isNotBlank() },
                                            profileInfo.dogSexNeuter.takeIf { it.isNotBlank() },
                                            profileInfo.dogWeightClass.takeIf { it.isNotBlank() },
                                        ).joinToString(" • ").ifBlank { "Breed / size not set" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    val ageLabel = profileInfo.dogAgeMonths
                                        .takeIf { it > 0 }
                                        ?.let { months -> "$months months old" }
                                        ?: "Age not set"
                                    Text(
                                        ageLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    profileInfo.secondaryDogName.takeIf { it.isNotBlank() }?.let { secondDog ->
                                        Text(
                                            "Companion dog: $secondDog",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Text(
                                        "Dog completeness ${(dogProfileCompletion * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    LinearProgressIndicator(
                                        progress = { dogProfileCompletion },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        },
                    )
                    ProfileDetailSection(
                        title = "Play compatibility",
                        details = listOf(
                            "Energy: ${profileInfo.playEnergyLevel.ifBlank { "Not set" }}",
                            "Style: ${profileInfo.playStyle.ifBlank { "Not set" }}",
                            "Social confidence: ${profileInfo.socialConfidence.ifBlank { "Not set" }}",
                            "Ideal match: ${profileInfo.idealMatch.ifBlank { "Not set" }}",
                            profileInfo.triggerNotes.takeIf { it.isNotBlank() }?.let { "Triggers: $it" },
                        ),
                    )
                    ProfileDetailSection(
                        title = "Care preferences",
                        details = listOf(
                            "Walks: ${profileInfo.walkPreferences.ifBlank { "Not set" }}",
                            "Training: ${profileInfo.trainingStyle.ifBlank { "Not set" }}",
                            "Feeding: ${profileInfo.feedingRules.ifBlank { "Not set" }}",
                            "Boundaries: ${profileInfo.consentBoundaries.ifBlank { "Not set" }}",
                        ),
                    )
                    ProfileTrustBadges(
                        vaccinationStatus = profileInfo.vaccinationStatus,
                        microchipped = profileInfo.microchipped,
                        recallTrained = profileInfo.recallTrained,
                        leashReliability = profileInfo.leashReliability,
                        emergencyContactReady = profileInfo.emergencyContactName.isNotBlank() && profileInfo.emergencyContactPhone.isNotBlank(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProfileStatChip(label = "Friends", value = totalFriendCount.toString())
                        ProfileStatChip(label = "Photos", value = profileDogPhotoUrls.size.toString())
                        ProfileStatChip(label = "Unread", value = unreadNotificationsCount.toString())
                    }
                    LinearProgressIndicator(
                        progress = { profileCompletionRatio },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HomeTileCard(
                    title = "Social",
                    subtitle = "$totalFriendCount friends",
                    preview = "Open your network",
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
                            icon = { Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(28.dp)) },
                            onClick = {
                                plansSheetSection = "community"
                                showPlansSheet = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                        HomeTileCard(
                            title = "Listings",
                            subtitle = listingsSubtitle,
                            preview = "Manage bookings",
                            badgeText = (ownerBookings.size + providerBookings.size).takeIf { it > 0 }?.toString(),
                            icon = { Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(28.dp)) },
                            onClick = {
                                plansSheetSection = "listings"
                                showPlansSheet = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
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
                    OutlinedTextField(
                        value = friendSearchQuery,
                        onValueChange = { friendSearchQuery = it },
                        label = { Text("Find by human or dog name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        FilterChip(
                            selected = friendFilter == "friends",
                            onClick = { friendFilter = "friends" },
                            label = { Text("Friends") },
                        )
                        FilterChip(
                            selected = friendFilter == "suggested",
                            onClick = { friendFilter = "suggested" },
                            label = { Text("Suggested") },
                        )
                        FilterChip(
                            selected = friendFilter == "all",
                            onClick = { friendFilter = "all" },
                            label = { Text("All") },
                        )
                    }
                }
                if (filteredFriendProfiles.isEmpty()) {
                    item { HomeEmptyCard("No matching profiles.") }
                } else {
                    items(filteredFriendProfiles, key = { profile -> profile.userId }) { profile ->
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
                                    StatusBadge(if (profile.isFriend) "friend" else "suggested")
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (profile.isFriend) {
                                        Button(onClick = { onOpenFriendMessages(profile.userId) }) {
                                            Text("Message")
                                        }
                                        TextButton(onClick = { onRemoveFriend(profile.userId) }) {
                                            Text("Remove")
                                        }
                                    } else {
                                        Button(onClick = { onAddFriend(profile.userId) }) {
                                            Text("Add")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
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
        val showCommunitySection = plansSheetSection != "listings"
        val showListingsSection = plansSheetSection != "community"
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
                            selected = plansSheetSection == "all",
                            onClick = { plansSheetSection = "all" },
                            label = { Text("All") },
                        )
                        FilterChip(
                            selected = plansSheetSection == "community",
                            onClick = { plansSheetSection = "community" },
                            label = { Text("Community") },
                        )
                        FilterChip(
                            selected = plansSheetSection == "listings",
                            onClick = { plansSheetSection = "listings" },
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
                                    StatusBadge(booking.status)
                                }
                            }
                        }
                    }
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
                            Card(
                                onClick = {
                                    selectedAppointment = booking.toAppointmentPopupState(sourceLabel = "Provider bookings")
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
                                    StatusBadge(booking.status)
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
                Button(
                    onClick = {
                        selectedAppointment = null
                        showPlansSheet = false
                        showNotificationsSheet = false
                        onOpenMessages(appointment.messageUserId)
                    },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Comment,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text("Message", modifier = Modifier.padding(start = 6.dp))
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedAppointment = null }) {
                    Text("Close")
                }
            },
        )
    }

    if (showProfileEditor) {
        val normalizedDisplayName = displayName.trim()
        val normalizedEmail = email.trim()
        val normalizedPhone = phone.trim()
        val normalizedHumanPronouns = humanPronouns.trim()
        val normalizedHumanRoleLabel = humanRoleLabel.trim()
        val normalizedDogName = dogName.trim()
        val normalizedDogAgeMonths = dogAgeMonthsText.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
        val isDogAgeValid = dogAgeMonthsText.trim().isBlank() || dogAgeMonthsText.trim().toIntOrNull() != null
        val normalizedDogBreedMix = dogBreedMix.trim()
        val normalizedDogSexNeuter = dogSexNeuter.trim()
        val normalizedDogWeightClass = dogWeightClass.trim()
        val normalizedSuburb = suburb.trim()
        val normalizedBio = bio.trim()
        val normalizedSecondaryDogName = secondaryDogName.trim()
        val normalizedSecondaryDogAgeMonths = secondaryDogAgeMonthsText.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
        val isSecondaryDogAgeValid =
            secondaryDogAgeMonthsText.trim().isBlank() || secondaryDogAgeMonthsText.trim().toIntOrNull() != null
        val normalizedSecondaryDogPhotoUrl = secondaryDogPhotoUrl.trim()
        val parsedDogPhotoUrls = parseDogPhotoValues(dogPhotoUrlsText)
        val validDogPhotoUrls = parsedDogPhotoUrls
            .filter(::isValidProfilePhotoUrl)
            .distinct()
            .take(8)
        val hasInvalidDogPhotoUrls = parsedDogPhotoUrls.any { value -> !isValidProfilePhotoUrl(value) }
        val normalizedFavoriteSuburbs = parseCommaOrNewlineValues(favoriteSuburbsText)
            .distinct()
            .take(8)
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
        val normalizedVisibility = buildMap {
            put("email", normalizeVisibilityValue(emailVisibility))
            put("phone", normalizeVisibilityValue(phoneVisibility))
            put("suburb", normalizeVisibilityValue(suburbVisibility))
            put("dog_name", normalizeVisibilityValue(dogNameVisibility))
            put("trigger_notes", normalizeVisibilityValue(triggerNotesVisibility))
        }
        val isEmailValid = normalizedEmail.isBlank() || isLikelyEmail(normalizedEmail)
        val isPhoneValid = normalizedPhone.isBlank() || isLikelyPhoneNumber(normalizedPhone)
        val isEmergencyPhoneValid =
            normalizedEmergencyContactPhone.isBlank() || isLikelyPhoneNumber(normalizedEmergencyContactPhone)
        val isSecondaryPhotoValid = normalizedSecondaryDogPhotoUrl.isBlank() || isValidProfilePhotoUrl(normalizedSecondaryDogPhotoUrl)
        val canSaveProfile = isEmailValid &&
            isPhoneValid &&
            isEmergencyPhoneValid &&
            isDogAgeValid &&
            isSecondaryDogAgeValid &&
            isSecondaryPhotoValid &&
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
            onDismissRequest = { showProfileEditor = false },
            title = { Text("Edit profile") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it.take(48) },
                        label = { Text("Display name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.take(96) },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it.take(24) },
                        label = { Text("Phone") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = humanPronouns,
                        onValueChange = { humanPronouns = it.take(32) },
                        label = { Text("Pronouns") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = humanRoleLabel,
                        onValueChange = { humanRoleLabel = it.take(48) },
                        label = { Text("Role label (e.g. Member, Provider + Parent)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = dogName,
                        onValueChange = { dogName = it.take(48) },
                        label = { Text("Dog name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = dogAgeMonthsText,
                        onValueChange = { dogAgeMonthsText = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text("Dog age (months)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = dogBreedMix,
                        onValueChange = { dogBreedMix = it.take(64) },
                        label = { Text("Dog breed / mix") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = dogSexNeuter,
                        onValueChange = { dogSexNeuter = it.take(64) },
                        label = { Text("Dog sex + neuter status") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = dogWeightClass,
                        onValueChange = { dogWeightClass = it.take(48) },
                        label = { Text("Dog weight class") },
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
                    Text("Companion dog (optional)", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = secondaryDogName,
                        onValueChange = { secondaryDogName = it.take(48) },
                        label = { Text("Second dog name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = secondaryDogAgeMonthsText,
                        onValueChange = { secondaryDogAgeMonthsText = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text("Second dog age (months)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = secondaryDogPhotoUrl,
                        onValueChange = { secondaryDogPhotoUrl = it.take(320) },
                        label = { Text("Second dog photo URL") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = suburb,
                        onValueChange = { suburb = it.take(48) },
                        label = { Text("Home suburb") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = favoriteSuburbsText,
                        onValueChange = { favoriteSuburbsText = it.take(180) },
                        label = { Text("Favorite suburbs (comma/newline)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it.take(260) },
                        label = { Text("Bio") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
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
                        label = { Text("Trigger notes") },
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
                        label = "Email visibility",
                        selected = emailVisibility,
                        onSelect = { emailVisibility = it },
                    )
                    VisibilitySelector(
                        label = "Phone visibility",
                        selected = phoneVisibility,
                        onSelect = { phoneVisibility = it },
                    )
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
                    Text(
                        "Bio ${normalizedBio.length}/260",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    if (!isDogAgeValid) {
                        Text(
                            "Dog age must be numeric.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (!isSecondaryDogAgeValid) {
                        Text(
                            "Second dog age must be numeric.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (!isSecondaryPhotoValid) {
                        Text(
                            "Second dog photo URL must start with http://, https://, file://, or content://",
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
                                dogName = normalizedDogName,
                                dogAgeMonths = normalizedDogAgeMonths,
                                dogBreedMix = normalizedDogBreedMix,
                                dogSexNeuter = normalizedDogSexNeuter,
                                dogWeightClass = normalizedDogWeightClass,
                                dogPhotoUrls = validDogPhotoUrls,
                                secondaryDogName = normalizedSecondaryDogName,
                                secondaryDogAgeMonths = normalizedSecondaryDogAgeMonths,
                                secondaryDogPhotoUrl = normalizedSecondaryDogPhotoUrl,
                                bio = normalizedBio,
                                suburb = normalizedSuburb,
                                favoriteSuburbs = normalizedFavoriteSuburbs,
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
                        showProfileEditor = false
                    },
                ) {
                    Text("Save profile")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProfileEditor = false }) {
                    Text("Cancel")
                }
            },
        )
    }

}

@Composable
private fun AdaptiveSplitCards(
    surfaceOnly: Boolean = false,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val splitCards = maxWidth >= 640.dp
        if (splitCards) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                val firstModifier = Modifier.weight(1f)
                val secondModifier = Modifier.weight(1f)
                first(firstModifier)
                second(secondModifier)
            }
        } else {
            val spacing = if (surfaceOnly) 8.dp else 10.dp
            Column(verticalArrangement = Arrangement.spacedBy(spacing), modifier = Modifier.fillMaxWidth()) {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun HomeSummaryCard(
    title: String,
    primary: String,
    secondary: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                primary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            secondary?.let { secondaryText ->
                Text(
                    secondaryText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
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
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icon()
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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

@Composable
private fun PlanSnapshotCard(
    title: String,
    primary: String,
    secondary: String,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(primary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                secondary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class AppointmentPopupState(
    val bookingId: String?,
    val title: String,
    val scheduleLabel: String,
    val counterpartLabel: String,
    val statusLabel: String,
    val description: String? = null,
    val messageUserId: String? = null,
)

private fun resolveAppointmentFromNotification(
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
        )
    }
    if (deepLink.startsWith("quote:")) {
        val candidate = ownerBookings
            .sortedBy { booking -> "${booking.date}_${booking.timeSlot}" }
            .firstOrNull()
            ?.toAppointmentPopupState(sourceLabel = "Quote response")
            ?: providerBookings
                .sortedBy { booking -> "${booking.date}_${booking.timeSlot}" }
                .firstOrNull()
                ?.toAppointmentPopupState(sourceLabel = "Quote response")
        return candidate ?: AppointmentPopupState(
            bookingId = null,
            title = notification.title.ifBlank { "Quote response received" },
            scheduleLabel = "Appointment not scheduled yet.",
            counterpartLabel = "Listings",
            statusLabel = "Awaiting booking",
            description = notification.body.takeIf { text -> text.isNotBlank() },
            messageUserId = null,
        )
    }
    return null
}

private fun OwnerBooking.toAppointmentPopupState(sourceLabel: String): AppointmentPopupState {
    return AppointmentPopupState(
        bookingId = id,
        title = "$sourceLabel • $serviceName",
        scheduleLabel = "${date.toLocalDateString()} $timeSlot",
        counterpartLabel = providerAccountLabel.ifBlank { "Provider" },
        statusLabel = status.toReadableLabel(),
        description = note.takeIf { value -> value.isNotBlank() },
        messageUserId = providerUserId.takeIf { value -> value.isNotBlank() },
    )
}

private fun ProviderBooking.toAppointmentPopupState(sourceLabel: String): AppointmentPopupState {
    return AppointmentPopupState(
        bookingId = id,
        title = "$sourceLabel • $serviceName",
        scheduleLabel = "${date.toLocalDateString()} $timeSlot",
        counterpartLabel = "Owner ${ownerUserId.ifBlank { petName }}",
        statusLabel = status.toReadableLabel(),
        description = "Pet: $petName",
        messageUserId = ownerUserId.takeIf { value -> value.isNotBlank() },
    )
}

private fun String.toReadableLabel(): String {
    return split("_")
        .filter { part -> part.isNotBlank() }
        .joinToString(" ") { part ->
            part.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
        }
        .ifBlank { this }
}

private fun parseCalendarDate(date: String): LocalDate? = try {
    LocalDate.parse(date.take(10))
} catch (_: DateTimeParseException) {
    null
}

private fun String.toLocalDateString(): String {
    return if (length >= 10 && this[4] == '-' && this[7] == '-') {
        take(10)
    } else {
        this
    }
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

private const val HOME_SETTINGS_PREFS = "home_settings"
private const val HOME_THEME_MODE_KEY = "theme_mode"
private const val HOME_THEME_MODE_LIGHT = "light"
private const val HOME_THEME_MODE_DARK = "dark"
private val VISIBILITY_OPTIONS = listOf("public", "group", "friends", "private")

private fun normalizeVisibilityValue(raw: String): String {
    val normalized = raw.trim().lowercase()
    return normalized.takeIf { value -> value in VISIBILITY_OPTIONS } ?: "private"
}

private fun profilePlayStyleText(
    bio: String,
    dogName: String,
): String {
    val normalizedBio = bio.lowercase()
    if ("social" in normalizedBio || "group" in normalizedBio) {
        return "Social and group-friendly"
    }
    if ("calm" in normalizedBio || "chill" in normalizedBio) {
        return "Calm, slower-paced outings"
    }
    return if (dogName.isBlank()) {
        "Add a short line about how your dog likes to play."
    } else {
        "$dogName enjoys local walks and friendly meetups."
    }
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
