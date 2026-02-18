package com.petsocial.app.ui

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.HomeRepairService
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.petsocial.app.R
import com.petsocial.app.BuildConfig
import com.petsocial.app.data.ApiService
import com.petsocial.app.data.MockApiService
import com.petsocial.app.data.PetSocialRepository
import com.petsocial.app.location.LocationResolver
import com.petsocial.app.ui.screens.ChatScreen
import com.petsocial.app.ui.screens.CommunityScreen
import com.petsocial.app.ui.screens.MessagesScreen
import com.petsocial.app.ui.screens.ProfileScreen
import com.petsocial.app.ui.screens.ServicesScreen
import com.petsocial.app.ui.components.HeaderRosterChip

private data class TabItem(
    val tab: AppTab,
    val label: String,
)

private fun deepLinkInviteToken(deepLink: String?): String? {
    if (deepLink.isNullOrBlank()) return null
    return runCatching { Uri.parse(deepLink).getQueryParameter("invite_token") }.getOrNull()
}

@Composable
fun PetSocialApp(initialDeepLink: String? = null) {
    val context = LocalContext.current
    val baseUrl = BuildConfig.API_BASE_URL
    val api = remember {
        if (BuildConfig.USE_MOCK_DATA) {
            MockApiService.create()
        } else {
            ApiService.create(
                baseUrl = baseUrl,
                authTokenProvider = {
                    context.getSharedPreferences("petsocial_cache", android.content.Context.MODE_PRIVATE)
                        .getString("auth_token", "")
                },
            )
        }
    }
    val repository = remember { PetSocialRepository(api, baseUrl, BuildConfig.MAPS_API_KEY, context) }
    val vm: PetSocialViewModel = viewModel(factory = PetSocialViewModelFactory(repository))
    val state by vm.uiState.collectAsStateWithLifecycle()
    var locationRetryKey by remember { mutableIntStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.any { it }) {
            locationRetryKey += 1
        }
    }

    LaunchedEffect(Unit) {
        vm.loadHomeData()
        vm.syncPushToken()
        if (LocationResolver.hasLocationPermission(context)) {
            val snapshot = LocationResolver.detectLocation(context)
            if (snapshot != null) {
                vm.setDetectedLocation(snapshot = snapshot, applyAsSelected = true)
            }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    LaunchedEffect(locationRetryKey) {
        if (locationRetryKey == 0) return@LaunchedEffect
        if (LocationResolver.hasLocationPermission(context)) {
            val snapshot = LocationResolver.detectLocation(context)
            if (snapshot != null) {
                vm.setDetectedLocation(snapshot = snapshot, applyAsSelected = state.selectedRangeCenter == "current")
            }
        }
    }

    LaunchedEffect(initialDeepLink) {
        val token = deepLinkInviteToken(initialDeepLink)
        if (!token.isNullOrBlank()) {
            vm.resolveInviteToken(token)
        }
    }

    LaunchedEffect(state.activeUserId) {
        vm.syncPushToken()
    }

    LaunchedEffect(state.toastMessage) {
        val message = state.toastMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        vm.consumeToast()
    }

    val tabs = listOf(
        TabItem(AppTab.Services, "Services"),
        TabItem(AppTab.Community, "Community"),
        TabItem(AppTab.BarkAI, "BarkAI"),
        TabItem(AppTab.Messages, "Messages"),
        TabItem(AppTab.Profile, "Profile"),
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!imeVisible) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    tabs.forEach { item ->
                        val icon = when (item.tab) {
                            AppTab.Services -> Icons.Default.HomeRepairService
                            AppTab.Community -> Icons.Default.People
                            AppTab.BarkAI -> Icons.Default.AutoAwesome
                            AppTab.Messages -> Icons.Default.ChatBubble
                            AppTab.Profile -> Icons.Default.Person
                        }
                        NavigationBarItem(
                            selected = state.selectedTab == item.tab,
                            onClick = {
                                vm.switchTab(item.tab)
                                vm.loadHomeData()
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            icon = { Icon(imageVector = icon, contentDescription = item.label) },
                            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HeroHeader(
                compact = true,
                rosterPet = state.headerRosterPet,
            )
            if (state.selectedTab == AppTab.Services || state.selectedTab == AppTab.Community) {
                SearchScopeBar(
                    selectedSuburb = state.selectedSuburb,
                    homeSuburb = state.profileInfo.suburb,
                    favoriteSuburbs = state.profileInfo.favoriteSuburbs,
                    selectedRangeKm = state.serviceMaxDistanceKm,
                    currentLocationSuburb = state.currentLocationSuburb,
                    isUsingCurrentLocation = state.selectedRangeCenter == "current",
                    onSuburbSelect = { suburb ->
                        vm.updateSuburb(suburb)
                        vm.loadHomeData(state.selectedCategory)
                    },
                    onUseCurrentLocation = {
                        if (LocationResolver.hasLocationPermission(context)) {
                            locationRetryKey += 1
                            vm.setRangeCenterCurrent(enabled = true)
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                        }
                    },
                    onUseManualCenter = { vm.setRangeCenterCurrent(enabled = false) },
                    onRefreshLocation = { locationRetryKey += 1 },
                    onRangeSelect = { range ->
                        vm.updateServiceFilters(state.serviceMinRating, range)
                    },
                )
            }

            state.error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (state.hasPendingSync) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (state.isOfflineMode) {
                            "Offline mode: showing cached data"
                        } else {
                            "Sync pending"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = vm::retrySync,
                        enabled = !state.loading,
                    ) {
                        Text("Retry sync")
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (state.selectedTab) {
                    AppTab.Services -> ServicesScreen(
                        providers = state.providers,
                        nearbyPetBusinesses = state.nearbyPetBusinesses,
                        groomerPetRosters = state.groomerPetRosters,
                        selectedCategory = state.selectedCategory,
                        viewMode = state.servicesViewMode,
                        searchQuery = state.servicesSearchQuery,
                        sortBy = state.servicesSortBy,
                        loading = state.loading,
                        selectedDetails = state.selectedProviderDetails,
                        availableSlots = state.availableSlots,
                        availabilityDate = state.availabilityDate,
                        minRating = state.serviceMinRating,
                        maxDistanceKm = state.serviceMaxDistanceKm,
                        onChangeViewMode = vm::setServicesViewMode,
                        onCategorySelect = vm::loadHomeData,
                        onSearchQueryChange = vm::updateServicesSearchQuery,
                        onSortByChange = vm::updateServicesSortBy,
                        onFilterChange = vm::updateServiceFilters,
                        onBook = vm::requestBooking,
                        onViewDetails = vm::loadProviderDetails,
                        onLoadAvailability = vm::loadAvailability,
                        onCloseDetails = vm::closeProviderDetails,
                    )

                    AppTab.BarkAI -> ChatScreen(
                        loading = state.loading,
                        chatResponse = state.chat,
                        conversation = state.conversation,
                        streamingAssistantText = state.streamingAssistantText,
                        profileSuggestion = state.profileSuggestion,
                        a2uiProfileCard = state.a2uiProfileCard,
                        a2uiProviderCard = state.a2uiProviderCard,
                        onSend = vm::sendChat,
                        onCtaClick = vm::handleCta,
                        onAcceptProfile = vm::acceptProfileCard,
                        onSubmitProvider = vm::submitProviderListing,
                    )

                    AppTab.Community -> CommunityScreen(
                        loading = state.loading,
                        suburb = state.selectedSuburb,
                        postsSortBy = state.postsSortBy,
                        groups = state.groups,
                        groupPetRosters = state.groupPetRosters,
                        latestGroupInvites = state.latestGroupInvites,
                        posts = state.posts,
                        events = state.communityEvents,
                        onJoinGroup = vm::joinGroup,
                        onCreateGroupInvite = vm::createGroupInvite,
                        onClearGroupInvite = vm::clearGroupInvite,
                        onPostsSortChange = vm::updatePostsSortBy,
                        onCreateLostFound = vm::createLostFoundPost,
                        onCreateEvent = vm::createCommunityEvent,
                        onRsvpEvent = vm::rsvpEvent,
                        onApproveJoinRequest = vm::approveNextJoinRequest,
                        onRejectJoinRequest = vm::rejectNextJoinRequest,
                        onApproveEvent = vm::approveEvent,
                    )

                    AppTab.Messages -> MessagesScreen(
                        activeUserId = state.activeUserId,
                        threads = state.messageThreads,
                        selectedThreadId = state.selectedMessageThreadId,
                        messages = state.directMessages,
                        onSelectThread = vm::selectMessageThread,
                        onBackToThreads = vm::clearMessageThreadSelection,
                        onSend = vm::sendDirectMessage,
                    )

                    AppTab.Profile -> ProfileScreen(
                        profileInfo = state.profileInfo,
                        isServiceProvider = state.isServiceProvider,
                        activeUserId = state.activeUserId,
                        allProviders = state.providers,
                        ownerBookings = state.ownerBookings,
                        joinedGroups = state.groups.filter { group -> group.membershipStatus == "member" },
                        createdGroups = state.groups.filter { group -> group.ownerUserId == state.activeUserId },
                        joinedEvents = state.joinedEvents,
                        favouriteProviders = state.providers.filter { provider -> state.favoriteProviderIds.contains(provider.id) },
                        providerListings = state.providerListings,
                        providerConfig = state.providerConfig,
                        providerBookings = state.providerBookings,
                        calendarEvents = state.calendarEvents,
                        selectedCalendarRole = state.selectedCalendarRole,
                        notifications = state.notifications,
                        onSaveProfile = vm::saveProfileInfo,
                        onToggleServiceProvider = vm::setServiceProviderMode,
                        onEditOwnerBooking = vm::requestBookingEdit,
                        onCancelOwnerBooking = vm::cancelOwnerBooking,
                        onLeaveEvent = vm::leaveEvent,
                        onRemoveFavourite = vm::removeFavourite,
                        onEditProviderListing = vm::editProviderListing,
                        onCancelProviderListing = vm::cancelProviderListing,
                        onSaveProviderConfig = vm::saveProviderConfig,
                        onConfirmProviderBooking = vm::confirmProviderBooking,
                        onCancelProviderBooking = vm::cancelProviderBooking,
                        onCalendarRoleChange = vm::setCalendarRole,
                        onMarkNotificationRead = vm::markNotificationRead,
                        onSwitchAccount = vm::switchAccount,
                    )
                }
            }
        }
    }

    state.pendingInvite?.let { invite ->
        var ownerName by rememberSaveable(invite.token) { mutableStateOf("") }
        var dogName by rememberSaveable(invite.token) { mutableStateOf("") }
        var sharePhoto by rememberSaveable(invite.token) { mutableStateOf(true) }
        var photoCaptured by rememberSaveable(invite.token) { mutableStateOf(false) }
        val photoCaptureLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicturePreview(),
        ) { bitmap ->
            if (bitmap != null) {
                photoCaptured = true
            }
        }

        AlertDialog(
            onDismissRequest = vm::dismissPendingInvite,
            title = { Text("Join ${invite.groupName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Finish setup to join this dog park community.")
                    OutlinedTextField(
                        value = ownerName,
                        onValueChange = { ownerName = it },
                        label = { Text("Your name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = dogName,
                        onValueChange = { dogName = it },
                        label = { Text("Dog name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ),
                                )
                            },
                        ) { Text("Allow location") }
                        TextButton(onClick = { photoCaptureLauncher.launch(null) }) {
                            Text(if (photoCaptured) "Dog photo added" else "Take dog photo")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = sharePhoto,
                            onClick = { sharePhoto = !sharePhoto },
                            label = { Text("Share dog photo to group") },
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = ownerName.isNotBlank() && dogName.isNotBlank() && !state.loading,
                    onClick = {
                        vm.completeInviteOnboarding(
                            ownerName = ownerName,
                            dogName = dogName,
                            sharePhotoToGroup = sharePhoto,
                            photoCaptured = photoCaptured,
                        )
                    },
                ) { Text("Join group") }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissPendingInvite) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun HeroHeader(
    compact: Boolean,
    rosterPet: PetRosterItem?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(
                    listOf(Color(0xFF6EA887), Color(0xFF8FBFA3), Color(0xFFB8D8C5))
                ),
                shape = RoundedCornerShape(if (compact) 14.dp else 20.dp),
            )
            .padding(if (compact) 10.dp else 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_barkwise_mark),
                        contentDescription = "BarkWise logo",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(if (compact) 28.dp else 42.dp),
                    )
                    Text(
                        "BarkWise",
                        color = Color.White,
                        style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                    )
                }
                if (!compact) {
                    Text(
                        "Ask, book, and connect locally",
                        color = Color.White.copy(alpha = 0.88f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            rosterPet?.let { pet ->
                HeaderRosterChip(pet = pet)
            }
        }
    }
}

@Composable
private fun SearchScopeBar(
    selectedSuburb: String,
    homeSuburb: String,
    favoriteSuburbs: List<String>,
    selectedRangeKm: Int?,
    currentLocationSuburb: String?,
    isUsingCurrentLocation: Boolean,
    onSuburbSelect: (String) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onUseManualCenter: () -> Unit,
    onRefreshLocation: () -> Unit,
    onRangeSelect: (Int?) -> Unit,
) {
    val suburbOptions = buildList {
        if (homeSuburb.isNotBlank()) add(homeSuburb.trim())
        favoriteSuburbs.forEach { suburb ->
            val cleaned = suburb.trim()
            if (cleaned.isNotBlank()) add(cleaned)
        }
        val selected = selectedSuburb.trim()
        if (selected.isNotBlank()) add(selected)
    }.distinct()

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Search scope", style = MaterialTheme.typography.labelMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            FilterChip(
                selected = !isUsingCurrentLocation,
                onClick = onUseManualCenter,
                label = { Text("Manual suburb") },
            )
            FilterChip(
                selected = isUsingCurrentLocation,
                onClick = onUseCurrentLocation,
                label = {
                    Text(
                        if (currentLocationSuburb.isNullOrBlank()) "Use current location"
                        else "Near $currentLocationSuburb",
                    )
                },
            )
            if (!currentLocationSuburb.isNullOrBlank()) {
                FilterChip(
                    selected = false,
                    onClick = onRefreshLocation,
                    label = { Text("Refresh GPS") },
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            suburbOptions.forEach { suburb ->
                FilterChip(
                    selected = selectedSuburb == suburb,
                    onClick = { onSuburbSelect(suburb) },
                    label = { Text(suburb) },
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            listOf<Int?>(null, 5, 10, 20, 50).forEach { range ->
                FilterChip(
                    selected = selectedRangeKm == range,
                    onClick = { onRangeSelect(range) },
                    label = { Text(range?.let { "$it km" } ?: "Any range") },
                )
            }
        }
    }
}
