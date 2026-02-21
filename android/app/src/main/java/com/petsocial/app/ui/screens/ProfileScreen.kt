package com.petsocial.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.petsocial.app.BuildConfig
import com.petsocial.app.data.CalendarEvent
import com.petsocial.app.data.Group
import com.petsocial.app.data.AppNotification
import com.petsocial.app.data.ServiceProvider
import com.petsocial.app.ui.OwnerBooking
import com.petsocial.app.ui.ProfileInfo
import com.petsocial.app.ui.ProviderBooking
import com.petsocial.app.ui.ProviderConfig
import com.petsocial.app.ui.ProviderListing
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private data class PendingAction(
    val title: String,
    val message: String,
    val onConfirm: () -> Unit,
)

@Composable
fun ProfileScreen(
    profileInfo: ProfileInfo,
    activeUserId: String,
    allProviders: List<ServiceProvider>,
    ownerBookings: List<OwnerBooking>,
    joinedGroups: List<Group>,
    createdGroups: List<Group>,
    providerListings: List<ProviderListing>,
    providerConfig: ProviderConfig,
    providerBookings: List<ProviderBooking>,
    calendarEvents: List<CalendarEvent>,
    selectedCalendarRole: String,
    notifications: List<AppNotification>,
    onOpenCommunityGroup: (String) -> Unit,
    onSaveProfile: (ProfileInfo) -> Unit,
    onCreateProviderListing: (
        name: String,
        category: String,
        suburb: String,
        description: String,
        priceFrom: Int,
        imageUrls: List<String>,
    ) -> Unit,
    onEditProviderListing: (
        listingId: String,
        name: String,
        description: String,
        priceFrom: Int,
        imageUrls: List<String>,
    ) -> Unit,
    onCancelProviderListing: (String) -> Unit,
    onRestoreProviderListing: (String) -> Unit,
    onSaveProviderConfig: (availableTimeSlots: String, preferredSuburbs: String) -> Unit,
    onConfirmProviderBooking: (String) -> Unit,
    onCancelProviderBooking: (String) -> Unit,
    onCalendarRoleChange: (String) -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    onSwitchAccount: (String) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var displayName by rememberSaveable(profileInfo.displayName) { mutableStateOf(profileInfo.displayName) }
    var email by rememberSaveable(profileInfo.email) { mutableStateOf(profileInfo.email) }
    var phone by rememberSaveable(profileInfo.phone) { mutableStateOf(profileInfo.phone) }
    var bio by rememberSaveable(profileInfo.bio) { mutableStateOf(profileInfo.bio) }
    var suburb by rememberSaveable(profileInfo.suburb) { mutableStateOf(profileInfo.suburb) }
    var favoriteSuburbsText by rememberSaveable(profileInfo.favoriteSuburbs) {
        mutableStateOf(profileInfo.favoriteSuburbs.joinToString(", "))
    }

    var groupsExpanded by rememberSaveable { mutableStateOf(false) }

    var listingsExpanded by rememberSaveable { mutableStateOf(false) }
    var configExpanded by rememberSaveable { mutableStateOf(false) }
    var incomingBookingsExpanded by rememberSaveable { mutableStateOf(false) }
    var providerBookingsExpanded by rememberSaveable { mutableStateOf(false) }
    var calendarExpanded by rememberSaveable { mutableStateOf(false) }
    var testMatrixExpanded by rememberSaveable { mutableStateOf(false) }
    var notificationsExpanded by rememberSaveable { mutableStateOf(false) }
    var appShareExpanded by rememberSaveable { mutableStateOf(false) }
    var testAccountExpanded by rememberSaveable { mutableStateOf(false) }
    var showAccountPicker by rememberSaveable { mutableStateOf(false) }
    var showProfileEditor by rememberSaveable { mutableStateOf(false) }
    var showCreateListingDialog by rememberSaveable { mutableStateOf(false) }
    var showEditListingDialog by rememberSaveable { mutableStateOf(false) }
    var listingName by rememberSaveable { mutableStateOf("") }
    var listingCategory by rememberSaveable { mutableStateOf("dog_walking") }
    var listingSuburb by rememberSaveable(profileInfo.suburb) { mutableStateOf(profileInfo.suburb) }
    var listingDescription by rememberSaveable { mutableStateOf("") }
    var listingPriceFrom by rememberSaveable { mutableStateOf("") }
    var listingImageUrls by rememberSaveable { mutableStateOf("") }
    var editingListingId by rememberSaveable { mutableStateOf("") }
    var editingListingName by rememberSaveable { mutableStateOf("") }
    var editingListingDescription by rememberSaveable { mutableStateOf("") }
    var editingListingPriceFrom by rememberSaveable { mutableStateOf("") }
    var editingListingImageUrls by rememberSaveable { mutableStateOf("") }

    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }
    val testAccounts = listOf(
        "user_1" to "Sesame",
        "user_2" to "Snowy",
        "user_3" to "Anika",
        "user_4" to "Tommy",
    )
    val activeAccountLabel = testAccounts.firstOrNull { it.first == activeUserId }?.second ?: activeUserId
    val listState = rememberLazyListState()
    val incomingStatuses = remember {
        setOf("pending_provider_confirmation", "requested", "rescheduled", "reschedule_requested")
    }
    val incomingProviderBookings = remember(providerBookings) {
        providerBookings.filter { it.status in incomingStatuses }
    }
    val managedProviderBookings = remember(providerBookings) {
        providerBookings.filterNot { it.status in incomingStatuses }
    }
    @OptIn(ExperimentalFoundationApi::class)
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 90.dp),
    ) {
        stickyHeader {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SectionHeader(
                        title = "Test account",
                        expanded = testAccountExpanded,
                        onToggle = { testAccountExpanded = !testAccountExpanded },
                    )
                    if (testAccountExpanded) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Current: $activeAccountLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(onClick = { showAccountPicker = true }) {
                                Text("Switch test account")
                            }
                        }
                        Text(
                            "Profile: ${displayName.ifBlank { "Unnamed" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { showProfileEditor = true }) {
                            Text("Edit my profile")
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = "Notifications (${notifications.count { !it.read }})",
                expanded = notificationsExpanded,
                onToggle = { notificationsExpanded = !notificationsExpanded },
            )
        }
        if (notificationsExpanded) {
            if (notifications.isEmpty()) {
                item { Text("No notifications yet", style = MaterialTheme.typography.bodySmall) }
            } else {
                items(notifications.take(20)) { notification ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(notification.title, style = MaterialTheme.typography.titleSmall)
                            Text(notification.body, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusBadge(if (notification.read) "read" else "unread")
                                if (!notification.read) {
                                    TextButton(onClick = { onMarkNotificationRead(notification.id) }) {
                                        Text("Mark read")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = "Calendar (${calendarEvents.size})",
                expanded = calendarExpanded,
                onToggle = { calendarExpanded = !calendarExpanded },
            )
        }

        if (calendarExpanded) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("all", "owner", "provider").forEach { role ->
                        FilterChip(
                            selected = selectedCalendarRole == role,
                            onClick = { onCalendarRoleChange(role) },
                            label = { Text(role.replaceFirstChar { it.uppercaseChar() }) },
                        )
                    }
                }
            }
            item { CalendarPlanner(events = calendarEvents) }
        }

        item {
            SectionHeader(
                title = "Groups",
                expanded = groupsExpanded,
                onToggle = { groupsExpanded = !groupsExpanded },
            )
        }
        if (groupsExpanded) {
            item { Text("Joined groups", style = MaterialTheme.typography.titleSmall) }
            if (joinedGroups.isEmpty()) {
                item { Text("No joined groups yet") }
            } else {
                items(joinedGroups) { group ->
                    Text(
                        "${group.name} • ${group.memberCount} members",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable { onOpenCommunityGroup(group.id) },
                    )
                }
            }

            item { Text("Created groups", style = MaterialTheme.typography.titleSmall) }
            if (createdGroups.isEmpty()) {
                item { Text("No created groups yet") }
            } else {
                items(createdGroups) { group ->
                    Text(
                        "${group.name} • ${group.suburb}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable { onOpenCommunityGroup(group.id) },
                    )
                }
            }
        }

        item {
            SectionHeader(
                title = "Listings (${providerListings.size})",
                expanded = listingsExpanded,
                onToggle = { listingsExpanded = !listingsExpanded },
            )
        }

        if (listingsExpanded) {
            item {
                Button(onClick = { showCreateListingDialog = true }) {
                    Text("Create listing")
                }
            }
            if (providerListings.isEmpty()) {
                item { Text("No listings yet") }
            } else {
                items(providerListings) { listing ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(listing.title, style = MaterialTheme.typography.titleSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${listing.category} • From \$${listing.priceFrom}")
                                StatusBadge(listing.status)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        editingListingId = listing.id
                                        editingListingName = listing.title
                                        editingListingDescription = listing.description
                                        editingListingPriceFrom = listing.priceFrom.toString()
                                        editingListingImageUrls = listing.imageUrls.joinToString(", ")
                                        showEditListingDialog = true
                                    },
                                    enabled = listing.status != "cancelled",
                                ) {
                                    Text("Edit")
                                }
                                Button(
                                    onClick = if (listing.status == "cancelled") {
                                        { onRestoreProviderListing(listing.id) }
                                    } else {
                                        {
                                            pendingAction = PendingAction(
                                                title = "Cancel listing?",
                                                message = "This listing will be marked as cancelled.",
                                                onConfirm = { onCancelProviderListing(listing.id) },
                                            )
                                        }
                                    },
                                ) {
                                    Text(if (listing.status == "cancelled") "Restore" else "Cancel")
                                }
                            }
                        }
                    }
                }
            }
            item {
                SectionHeader(
                    title = "Listing setup defaults",
                    expanded = configExpanded,
                    onToggle = { configExpanded = !configExpanded },
                )
            }
            if (configExpanded) {
                item {
                    val config = remember(providerConfig) { providerConfig }
                    var availableSlots by rememberSaveable(config.availableTimeSlots) { mutableStateOf(config.availableTimeSlots) }
                    var preferredSuburbs by rememberSaveable(config.preferredSuburbs) { mutableStateOf(config.preferredSuburbs) }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = availableSlots,
                                onValueChange = { availableSlots = it },
                                label = { Text("Available time slots") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = preferredSuburbs,
                                onValueChange = { preferredSuburbs = it },
                                label = { Text("Preferred suburbs") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(onClick = { onSaveProviderConfig(availableSlots.trim(), preferredSuburbs.trim()) }) {
                                Text("Save defaults")
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = "Incoming bookings (${incomingProviderBookings.size})",
                expanded = incomingBookingsExpanded,
                onToggle = { incomingBookingsExpanded = !incomingBookingsExpanded },
            )
        }
        if (incomingBookingsExpanded) {
            if (incomingProviderBookings.isEmpty()) {
                item { Text("No incoming bookings") }
            } else {
                items(incomingProviderBookings) { booking ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${booking.petName} • ${booking.serviceName}", style = MaterialTheme.typography.titleSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${booking.date} ${booking.timeSlot}")
                                StatusBadge(booking.status)
                            }
                            if (booking.ownerUserId.isNotBlank()) {
                                Text("Customer account: ${accountDisplayName(booking.ownerUserId)}", style = MaterialTheme.typography.labelSmall)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onConfirmProviderBooking(booking.id) },
                                    enabled = booking.status in incomingStatuses,
                                ) {
                                    Text("Accept")
                                }
                                Button(
                                    onClick = {
                                        pendingAction = PendingAction(
                                            title = "Cancel incoming booking?",
                                            message = "This booking will be marked as cancelled.",
                                            onConfirm = { onCancelProviderBooking(booking.id) },
                                        )
                                    },
                                    enabled = booking.status != "cancelled_by_provider" && booking.status != "cancelled_by_owner",
                                ) {
                                    Text("Cancel booking")
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = "Bookings (${managedProviderBookings.size})",
                expanded = providerBookingsExpanded,
                onToggle = { providerBookingsExpanded = !providerBookingsExpanded },
            )
        }
        if (providerBookingsExpanded) {
            item {
                Text(
                    "Confirmed bookings are reflected in Calendar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (managedProviderBookings.isEmpty()) {
                item { Text("No managed bookings yet") }
            } else {
                items(managedProviderBookings) { booking ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${booking.petName} • ${booking.serviceName}", style = MaterialTheme.typography.titleSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${booking.date} ${booking.timeSlot}")
                                StatusBadge(booking.status)
                            }
                            if (booking.ownerUserId.isNotBlank()) {
                                Text("Customer account: ${accountDisplayName(booking.ownerUserId)}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = "Share App QR",
                expanded = appShareExpanded,
                onToggle = { appShareExpanded = !appShareExpanded },
            )
        }
        if (appShareExpanded) {
            item {
                val trimmedLink = BuildConfig.INSTALL_PAGE_URL.trim()
                val canRenderQr = trimmedLink.startsWith("http://") || trimmedLink.startsWith("https://")
                val qrUrl = if (canRenderQr) qrImageUrl(trimmedLink) else null
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Install page link")
                        Text(
                            trimmedLink,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { clipboard.setText(AnnotatedString(trimmedLink)) },
                                enabled = canRenderQr,
                            ) {
                                Text("Copy link")
                            }
                            Button(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, trimmedLink)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share BarkWise install link"))
                                },
                                enabled = canRenderQr,
                            ) {
                                Text("Share link")
                            }
                        }
                        if (qrUrl != null) {
                            Text("Scan this QR to open install page", style = MaterialTheme.typography.labelMedium)
                            AsyncImage(
                                model = qrUrl,
                                contentDescription = "App install QR",
                                modifier = Modifier
                                    .size(220.dp)
                                    .align(Alignment.CenterHorizontally),
                            )
                        } else {
                            Text(
                                "Enter a valid http(s) URL to generate QR.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = "Test Matrix",
                expanded = testMatrixExpanded,
                onToggle = { testMatrixExpanded = !testMatrixExpanded },
            )
        }

        if (testMatrixExpanded) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AsyncImage(
                                model = accountPhotoUrl(activeUserId),
                                contentDescription = "${accountDisplayName(activeUserId)} profile photo",
                                modifier = Modifier.size(34.dp),
                            )
                            Text("Current test account: ${accountDisplayName(activeUserId)}", style = MaterialTheme.typography.titleSmall)
                        }
                        Text("Use these mappings to run cross-account booking and approval tests.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Listing ownership map", style = MaterialTheme.typography.titleSmall)
                        if (allProviders.isEmpty()) {
                            Text("No listings loaded. Refresh Listings tab first.", style = MaterialTheme.typography.bodySmall)
                        }
                        allProviders.forEach { provider ->
                            val ownerUserId = provider.ownerUserId
                            val owner = provider.ownerLabel
                                ?: ownerUserId?.let(::accountDisplayName)
                                ?: "Unknown owner"
                            val suggestedBooker = listOf("Sesame", "Snowy", "Anika", "Tommy")
                                .firstOrNull { it != owner } ?: "another account"
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(provider.name, style = MaterialTheme.typography.titleSmall)
                                    Text("Owner: $owner", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "Book as $suggestedBooker, then switch to $owner to Accept.",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    if (!ownerUserId.isNullOrBlank() && ownerUserId != activeUserId) {
                                        Button(onClick = { onSwitchAccount(ownerUserId) }) {
                                            Text("Switch to $owner")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Pending booking actions", style = MaterialTheme.typography.titleSmall)
                        val pendingOwner = ownerBookings.filter { it.status in setOf("requested", "rescheduled", "reschedule_requested") }
                        val pendingProvider = providerBookings.filter { it.status in setOf("requested", "rescheduled", "reschedule_requested") }
                        if (pendingOwner.isEmpty() && pendingProvider.isEmpty()) {
                            Text("No pending booking actions right now.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            pendingOwner.forEach { booking ->
                                Text(
                                    "• ${booking.serviceName}: switch to ${booking.providerAccountLabel} to confirm/decline.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            pendingProvider.forEach { booking ->
                                Text(
                                    "• ${booking.serviceName}: currently on provider side. Customer is ${accountDisplayName(booking.ownerUserId)}.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateListingDialog) {
        AlertDialog(
            onDismissRequest = { showCreateListingDialog = false },
            title = { Text("Create listing") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = listingName,
                        onValueChange = { listingName = it },
                        label = { Text("Listing name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = listingCategory == "dog_walking",
                            onClick = { listingCategory = "dog_walking" },
                            label = { Text("Dog walking") },
                        )
                        FilterChip(
                            selected = listingCategory == "grooming",
                            onClick = { listingCategory = "grooming" },
                            label = { Text("Grooming") },
                        )
                    }
                    OutlinedTextField(
                        value = listingSuburb,
                        onValueChange = { listingSuburb = it },
                        label = { Text("Suburb") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = listingDescription,
                        onValueChange = { listingDescription = it },
                        label = { Text("Description") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = listingPriceFrom,
                        onValueChange = { listingPriceFrom = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Price from") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = listingImageUrls,
                        onValueChange = { listingImageUrls = it },
                        label = { Text("Image URLs (optional, comma-separated)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                val parsedPrice = listingPriceFrom.toIntOrNull()
                val parsedImageUrls = listingImageUrls
                    .split(",", "\n")
                    .map { value -> value.trim() }
                    .filter { value -> value.isNotEmpty() }
                val canSubmit = listingName.isNotBlank() &&
                    listingSuburb.isNotBlank() &&
                    listingDescription.isNotBlank() &&
                    parsedPrice != null &&
                    parsedPrice > 0
                Button(
                    onClick = {
                        onCreateProviderListing(
                            listingName.trim(),
                            listingCategory,
                            listingSuburb.trim(),
                            listingDescription.trim(),
                            parsedPrice ?: 0,
                            parsedImageUrls,
                        )
                        showCreateListingDialog = false
                        listingName = ""
                        listingSuburb = profileInfo.suburb
                        listingDescription = ""
                        listingPriceFrom = ""
                        listingImageUrls = ""
                        listingCategory = "dog_walking"
                    },
                    enabled = canSubmit,
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateListingDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showEditListingDialog) {
        AlertDialog(
            onDismissRequest = { showEditListingDialog = false },
            title = { Text("Edit listing") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editingListingName,
                        onValueChange = { editingListingName = it },
                        label = { Text("Listing name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = editingListingDescription,
                        onValueChange = { editingListingDescription = it },
                        label = { Text("Description") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = editingListingPriceFrom,
                        onValueChange = { editingListingPriceFrom = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Price from") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = editingListingImageUrls,
                        onValueChange = { editingListingImageUrls = it },
                        label = { Text("Image URLs (optional, comma-separated)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                val parsedPrice = editingListingPriceFrom.toIntOrNull()
                val parsedImageUrls = editingListingImageUrls
                    .split(",", "\n")
                    .map { value -> value.trim() }
                    .filter { value -> value.isNotEmpty() }
                val canSubmit = editingListingId.isNotBlank() &&
                    editingListingName.isNotBlank() &&
                    editingListingDescription.isNotBlank() &&
                    parsedPrice != null &&
                    parsedPrice > 0
                Button(
                    onClick = {
                        onEditProviderListing(
                            editingListingId,
                            editingListingName.trim(),
                            editingListingDescription.trim(),
                            parsedPrice ?: 0,
                            parsedImageUrls,
                        )
                        showEditListingDialog = false
                    },
                    enabled = canSubmit,
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditListingDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showAccountPicker) {
        AlertDialog(
            onDismissRequest = { showAccountPicker = false },
            title = { Text("Select test account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    testAccounts.forEach { (id, label) ->
                        Button(
                            onClick = {
                                onSwitchAccount(id)
                                showAccountPicker = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                AsyncImage(
                                    model = accountPhotoUrl(id),
                                    contentDescription = "$label profile photo",
                                    modifier = Modifier.size(26.dp),
                                )
                                Text(if (activeUserId == id) "$label (Current)" else label)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAccountPicker = false }) { Text("Close") }
            },
        )
    }

    if (showProfileEditor) {
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
                        onValueChange = { displayName = it },
                        label = { Text("Display name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = suburb,
                        onValueChange = { suburb = it },
                        label = { Text("Home suburb") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = favoriteSuburbsText,
                        onValueChange = { favoriteSuburbsText = it },
                        label = { Text("Favorite suburbs (comma separated)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it },
                        label = { Text("Bio") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveProfile(
                            ProfileInfo(
                                displayName = displayName.trim(),
                                email = email.trim(),
                                phone = phone.trim(),
                                bio = bio.trim(),
                                suburb = suburb.trim(),
                                favoriteSuburbs = favoriteSuburbsText
                                    .split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                                    .distinct(),
                            )
                        )
                        showProfileEditor = false
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProfileEditor = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(action.title) },
            text = { Text(action.message) },
            confirmButton = {
                Button(
                    onClick = {
                        action.onConfirm()
                        pendingAction = null
                    },
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text("Keep")
                }
            },
        )
    }
}

@Composable
private fun CalendarPlanner(events: List<CalendarEvent>) {
    val today = remember { LocalDate.now() }
    var visibleMonth by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var selectedDate by rememberSaveable { mutableStateOf(today.toString()) }
    val month = remember(visibleMonth) { YearMonth.parse(visibleMonth) }
    val selectedLocalDate = remember(selectedDate) { parseCalendarDate(selectedDate) ?: today }

    val monthLabel = remember(month) {
        month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy"))
    }
    val dayCells = remember(month) { buildMonthGrid(month) }
    val eventsByDate = remember(events) {
        events.groupBy { event -> event.date.toLocalDateString() }
    }
    val selectedEvents = remember(eventsByDate, selectedDate) {
        (eventsByDate[selectedDate] ?: emptyList())
            .sortedBy { it.timeSlot }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = {
                            val prev = month.minusMonths(1)
                            visibleMonth = prev.toString()
                            selectedDate = prev.atDay(1).toString()
                        },
                    ) { Text("Prev") }
                    Text(monthLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    TextButton(
                        onClick = {
                            val next = month.plusMonths(1)
                            visibleMonth = next.toString()
                            selectedDate = next.atDay(1).toString()
                        },
                    ) { Text("Next") }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { day ->
                        Text(
                            day,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                dayCells.chunked(7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        week.forEach { day ->
                            if (day == null) {
                                Spacer(modifier = Modifier.weight(1f))
                            } else {
                                val key = day.toString()
                                val isSelected = key == selectedDate
                                val isToday = day == today
                                val hasEvents = (eventsByDate[key]?.isNotEmpty() == true)
                                val bgColor = when {
                                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                                    isToday -> MaterialTheme.colorScheme.secondaryContainer
                                    else -> MaterialTheme.colorScheme.surface
                                }
                                val textColor = when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    isToday -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                                TextButton(
                                    onClick = { selectedDate = key },
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(bgColor, RoundedCornerShape(10.dp)),
                                ) {
                                    Column(
                                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(day.dayOfMonth.toString(), color = textColor, style = MaterialTheme.typography.bodySmall)
                                        if (hasEvents) {
                                            Spacer(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)),
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.size(5.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    selectedLocalDate.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (selectedEvents.isEmpty()) {
                    Text("No events for this day", style = MaterialTheme.typography.bodySmall)
                } else {
                    selectedEvents.forEach { event ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(event.title, style = MaterialTheme.typography.titleSmall)
                                Text(event.subtitle, style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("${event.date.toLocalDateString()} ${event.timeSlot}")
                                    StatusBadge(event.status)
                                    Text(event.role, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildMonthGrid(month: YearMonth): List<LocalDate?> {
    val first = month.atDay(1)
    val daysInMonth = month.lengthOfMonth()
    val leading = first.dayOfWeek.value - 1
    val cells = mutableListOf<LocalDate?>()
    repeat(leading) { cells += null }
    repeat(daysInMonth) { offset -> cells += month.atDay(offset + 1) }
    while (cells.size % 7 != 0) {
        cells += null
    }
    return cells
}

private fun parseCalendarDate(date: String): LocalDate? = try {
    LocalDate.parse(date.take(10))
} catch (_: DateTimeParseException) {
    null
}

private fun qrImageUrl(url: String): String {
    val encoded = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
    return "https://quickchart.io/qr?size=260&text=$encoded"
}

private fun String.toLocalDateString(): String {
    return if (length >= 10 && this[4] == '-' && this[7] == '-') {
        take(10)
    } else {
        this
    }
}

@Composable
private fun SectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onToggle, modifier = Modifier.wrapContentWidth()) {
            Text(if (expanded) "Hide" else "Show")
        }
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

private fun accountDisplayName(userId: String): String = when (userId) {
    "user_1" -> "Sesame"
    "user_2" -> "Snowy"
    "user_3" -> "Anika"
    "user_4" -> "Tommy"
    else -> userId
}

private fun accountPhotoUrl(userId: String): String = when (userId) {
    "user_1" -> "https://loremflickr.com/640/640/bordoodle,dog?lock=201"
    "user_2" -> "https://loremflickr.com/640/640/black,white,dog?lock=202"
    "user_3" -> "https://loremflickr.com/640/640/cavoodle,dog?lock=203"
    "user_4" -> "https://loremflickr.com/640/640/brown,toy,dog,cavoodle?lock=204"
    else -> "https://images.unsplash.com/photo-1517849845537-4d257902454a"
}
