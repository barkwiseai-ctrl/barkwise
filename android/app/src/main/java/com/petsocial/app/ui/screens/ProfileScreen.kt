package com.petsocial.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.petsocial.app.data.CalendarEvent
import com.petsocial.app.data.Group
import com.petsocial.app.data.AppNotification
import com.petsocial.app.data.ServiceProvider
import com.petsocial.app.ui.JoinedEvent
import com.petsocial.app.ui.OwnerBooking
import com.petsocial.app.ui.ProfileInfo
import com.petsocial.app.ui.ProviderBooking
import com.petsocial.app.ui.ProviderConfig
import com.petsocial.app.ui.ProviderListing
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
    isServiceProvider: Boolean,
    activeUserId: String,
    allProviders: List<ServiceProvider>,
    ownerBookings: List<OwnerBooking>,
    joinedGroups: List<Group>,
    createdGroups: List<Group>,
    joinedEvents: List<JoinedEvent>,
    favouriteProviders: List<ServiceProvider>,
    providerListings: List<ProviderListing>,
    providerConfig: ProviderConfig,
    providerBookings: List<ProviderBooking>,
    calendarEvents: List<CalendarEvent>,
    selectedCalendarRole: String,
    notifications: List<AppNotification>,
    onSaveProfile: (ProfileInfo) -> Unit,
    onToggleServiceProvider: (Boolean) -> Unit,
    onEditOwnerBooking: (String) -> Unit,
    onCancelOwnerBooking: (String) -> Unit,
    onLeaveEvent: (String) -> Unit,
    onRemoveFavourite: (String) -> Unit,
    onEditProviderListing: (String) -> Unit,
    onCancelProviderListing: (String) -> Unit,
    onSaveProviderConfig: (availableTimeSlots: String, preferredSuburbs: String) -> Unit,
    onConfirmProviderBooking: (String) -> Unit,
    onCancelProviderBooking: (String) -> Unit,
    onCalendarRoleChange: (String) -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    onSwitchAccount: (String) -> Unit,
) {
    var displayName by rememberSaveable(profileInfo.displayName) { mutableStateOf(profileInfo.displayName) }
    var email by rememberSaveable(profileInfo.email) { mutableStateOf(profileInfo.email) }
    var phone by rememberSaveable(profileInfo.phone) { mutableStateOf(profileInfo.phone) }
    var bio by rememberSaveable(profileInfo.bio) { mutableStateOf(profileInfo.bio) }
    var suburb by rememberSaveable(profileInfo.suburb) { mutableStateOf(profileInfo.suburb) }
    var favoriteSuburbsText by rememberSaveable(profileInfo.favoriteSuburbs) {
        mutableStateOf(profileInfo.favoriteSuburbs.joinToString(", "))
    }

    var ownerExpanded by rememberSaveable { mutableStateOf(true) }
    var bookingsExpanded by rememberSaveable { mutableStateOf(true) }
    var groupsExpanded by rememberSaveable { mutableStateOf(false) }
    var eventsExpanded by rememberSaveable { mutableStateOf(false) }
    var favouritesExpanded by rememberSaveable { mutableStateOf(false) }

    var providerExpanded by rememberSaveable { mutableStateOf(true) }
    var listingsExpanded by rememberSaveable { mutableStateOf(true) }
    var configExpanded by rememberSaveable { mutableStateOf(false) }
    var incomingBookingsExpanded by rememberSaveable { mutableStateOf(false) }
    var calendarExpanded by rememberSaveable { mutableStateOf(true) }
    var testMatrixExpanded by rememberSaveable { mutableStateOf(true) }
    var notificationsExpanded by rememberSaveable { mutableStateOf(true) }

    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 90.dp),
    ) {
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
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Test account", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("user_1" to "Account A", "user_2" to "Account B", "user_3" to "Account C", "user_4" to "Account D")
                            .forEach { (id, label) ->
                            FilterChip(
                                selected = activeUserId == id,
                                onClick = { onSwitchAccount(id) },
                                label = { Text(label) },
                            )
                        }
                    }
                    Text("My Profile", style = MaterialTheme.typography.titleMedium)
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
                        },
                    ) {
                        Text("Save profile")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Enable service provider profile")
                        Switch(
                            checked = isServiceProvider,
                            onCheckedChange = onToggleServiceProvider,
                        )
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = "Pet Owner",
                expanded = ownerExpanded,
                onToggle = { ownerExpanded = !ownerExpanded },
            )
        }

        if (ownerExpanded) {
            item {
                SectionHeader(
                    title = "Manage bookings (${ownerBookings.size})",
                    expanded = bookingsExpanded,
                    onToggle = { bookingsExpanded = !bookingsExpanded },
                )
            }
            if (bookingsExpanded) {
                if (ownerBookings.isEmpty()) {
                    item { Text("No bookings yet") }
                } else {
                    items(ownerBookings) { booking ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(booking.serviceName, style = MaterialTheme.typography.titleSmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("${booking.date} ${booking.timeSlot}")
                                    StatusBadge(booking.status)
                                }
                                if (booking.providerAccountLabel.isNotBlank()) {
                                    Text("Provider: ${booking.providerAccountLabel}", style = MaterialTheme.typography.labelSmall)
                                }
                                if (booking.note.isNotBlank()) {
                                    Text(booking.note, style = MaterialTheme.typography.bodySmall)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { onEditOwnerBooking(booking.id) }, enabled = booking.status != "cancelled") {
                                        Text("Edit")
                                    }
                                    Button(
                                        onClick = {
                                            pendingAction = PendingAction(
                                                title = "Cancel booking?",
                                                message = "This will mark the booking as cancelled.",
                                                onConfirm = { onCancelOwnerBooking(booking.id) },
                                            )
                                        },
                                        enabled = booking.status != "cancelled",
                                    ) {
                                        Text("Cancel")
                                    }
                                }
                            }
                        }
                    }
                }
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
                        Text("${group.name} • ${group.memberCount} members", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                item { Text("Created groups", style = MaterialTheme.typography.titleSmall) }
                if (createdGroups.isEmpty()) {
                    item { Text("No created groups yet") }
                } else {
                    items(createdGroups) { group ->
                        Text("${group.name} • ${group.suburb}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Joined events (${joinedEvents.size})",
                    expanded = eventsExpanded,
                    onToggle = { eventsExpanded = !eventsExpanded },
                )
            }
            if (eventsExpanded) {
                if (joinedEvents.isEmpty()) {
                    item { Text("No joined events") }
                } else {
                    items(joinedEvents) { event ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(event.title, style = MaterialTheme.typography.titleSmall)
                                Text("${event.date} • ${event.suburb}")
                                Button(
                                    onClick = {
                                        pendingAction = PendingAction(
                                            title = "Remove event?",
                                            message = "This event will be removed from your joined list.",
                                            onConfirm = { onLeaveEvent(event.id) },
                                        )
                                    },
                                ) {
                                    Text("Remove")
                                }
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Favourites (${favouriteProviders.size})",
                    expanded = favouritesExpanded,
                    onToggle = { favouritesExpanded = !favouritesExpanded },
                )
            }
            if (favouritesExpanded) {
                if (favouriteProviders.isEmpty()) {
                    item { Text("No favourites yet") }
                } else {
                    items(favouriteProviders) { provider ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(provider.name, style = MaterialTheme.typography.titleSmall)
                                Text("${provider.suburb} • ${provider.category.replace("_", " ")}")
                                Button(
                                    onClick = {
                                        pendingAction = PendingAction(
                                            title = "Remove favourite?",
                                            message = "This provider will be removed from favourites.",
                                            onConfirm = { onRemoveFavourite(provider.id) },
                                        )
                                    },
                                ) {
                                    Text("Remove")
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
                title = "Service Provider",
                expanded = providerExpanded,
                onToggle = { providerExpanded = !providerExpanded },
            )
        }

        if (providerExpanded) {
            if (!isServiceProvider) {
                item {
                    Text(
                        "Turn on service provider profile above to manage listings, booking slots, suburbs, and incoming bookings.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                item {
                    SectionHeader(
                        title = "Manage service listings (${providerListings.size})",
                        expanded = listingsExpanded,
                        onToggle = { listingsExpanded = !listingsExpanded },
                    )
                }

                if (listingsExpanded) {
                    if (providerListings.isEmpty()) {
                        item { Text("No service listings yet") }
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
                                            onClick = { onEditProviderListing(listing.id) },
                                            enabled = listing.status != "cancelled",
                                        ) {
                                            Text("Edit")
                                        }
                                        Button(
                                            onClick = {
                                                pendingAction = PendingAction(
                                                    title = "Cancel listing?",
                                                    message = "This listing will be marked as cancelled.",
                                                    onConfirm = { onCancelProviderListing(listing.id) },
                                                )
                                            },
                                            enabled = listing.status != "cancelled",
                                        ) {
                                            Text("Cancel")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    SectionHeader(
                        title = "Booking configuration",
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
                                    Text("Save configuration")
                                }
                            }
                        }
                    }
                }

                item {
                    SectionHeader(
                        title = "Incoming bookings (${providerBookings.size})",
                        expanded = incomingBookingsExpanded,
                        onToggle = { incomingBookingsExpanded = !incomingBookingsExpanded },
                    )
                }

                if (incomingBookingsExpanded) {
                    if (providerBookings.isEmpty()) {
                        item { Text("No incoming bookings") }
                    } else {
                        items(providerBookings) { booking ->
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
                                            enabled = booking.status == "requested" || booking.status == "rescheduled",
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
                        Text("Current test account: ${accountDisplayName(activeUserId)}", style = MaterialTheme.typography.titleSmall)
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
                        Text("Service ownership map", style = MaterialTheme.typography.titleSmall)
                        if (allProviders.isEmpty()) {
                            Text("No services loaded. Refresh Services tab first.", style = MaterialTheme.typography.bodySmall)
                        }
                        allProviders.forEach { provider ->
                            val ownerUserId = provider.ownerUserId
                            val owner = provider.ownerLabel
                                ?: ownerUserId?.let(::accountDisplayName)
                                ?: "Unknown owner"
                            val suggestedBooker = listOf("Account A", "Account B", "Account C", "Account D")
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
    "user_1" -> "Account A"
    "user_2" -> "Account B"
    "user_3" -> "Account C"
    "user_4" -> "Account D"
    else -> userId
}
