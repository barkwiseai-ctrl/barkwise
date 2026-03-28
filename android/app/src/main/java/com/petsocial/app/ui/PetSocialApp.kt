package com.petsocial.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.core.content.ContextCompat
import com.petsocial.app.R
import com.petsocial.app.BuildConfig
import com.petsocial.app.data.ApiService
import com.petsocial.app.data.PetSocialRepository
import com.petsocial.app.location.LocationResolver
import com.petsocial.app.ui.screens.ChatScreen
import com.petsocial.app.ui.screens.CommunityScreen
import com.petsocial.app.ui.screens.MessagesScreen
import com.petsocial.app.ui.screens.ProfileScreen
import com.petsocial.app.ui.screens.ServicesScreen
import com.petsocial.app.ui.components.HeaderRosterChip
import com.petsocial.app.ui.qr.QrPayloadAction
import com.petsocial.app.ui.qr.parseQrPayload
import java.net.URI

private data class TabItem(
    val tab: AppTab,
    val label: String,
)

internal fun isOwnerProdSurface(
    appSurface: String,
    environment: String,
): Boolean {
    return appSurface.equals("owner", ignoreCase = true) &&
        environment.equals("prod", ignoreCase = true)
}

internal fun resolveDefaultTab(
    appSurface: String,
    environment: String,
): AppTab {
    return if (
        appSurface.equals("provider", ignoreCase = true) ||
        appSurface.equals("owner", ignoreCase = true) ||
        isOwnerProdSurface(appSurface, environment)
    ) {
        AppTab.Profile
    } else {
        AppTab.Services
    }
}

internal fun resolveBottomTabs(
    appSurface: String,
    environment: String,
): List<AppTab> {
    return when {
        appSurface.equals("provider", ignoreCase = true) ||
            appSurface.equals("owner", ignoreCase = true) ||
            isOwnerProdSurface(appSurface, environment) -> listOf(
            AppTab.Profile,
            AppTab.Messages,
            AppTab.BarkAI,
            AppTab.Community,
            AppTab.Services,
        )
        else -> listOf(
            AppTab.Services,
            AppTab.Community,
            AppTab.BarkAI,
            AppTab.Messages,
            AppTab.Profile,
        )
    }
}

private fun tabLabel(tab: AppTab, isProviderSurface: Boolean): String {
    return when (tab) {
        AppTab.Profile -> if (isProviderSurface) "Hub" else "Home"
        AppTab.Messages -> if (isProviderSurface) "Inbox" else "Messages"
        AppTab.BarkAI -> "BarkAI"
        AppTab.Community -> "Community"
        AppTab.Services -> "Listings"
    }
}

private fun deepLinkInviteToken(deepLink: String?): String? {
    if (deepLink.isNullOrBlank()) return null
    return when (val action = parseQrPayload(deepLink)) {
        is QrPayloadAction.InviteToken -> action.token
        else -> null
    }
}

private fun deepLinkTestProfileMode(deepLink: String?): String? {
    if (deepLink.isNullOrBlank()) return null
    val uri = runCatching { Uri.parse(deepLink) }.getOrNull() ?: return null
    val mode = uri.getQueryParameter("profile_mode")?.trim()?.lowercase() ?: return null
    return when (mode) {
        "onboarding", "ready" -> mode
        else -> null
    }
}

private fun deepLinkProfileHeaderVisibility(deepLink: String?): Boolean? {
    if (deepLink.isNullOrBlank()) return null
    val uri = runCatching { Uri.parse(deepLink) }.getOrNull() ?: return null
    return when (uri.getQueryParameter("profile_header_mode")?.trim()?.lowercase()) {
        "visible", "show" -> true
        "hidden", "hide" -> false
        else -> null
    }
}

private const val CACHE_PREFS = "petsocial_cache"

@Composable
fun PetSocialApp(initialDeepLink: String? = null) {
    DebugRecomposeCounter(tag = "PetSocialApp")
    val context = LocalContext.current
    val baseUrl = BuildConfig.API_BASE_URL
    val fallbackBaseUrl = remember(baseUrl) {
        val loopbackHost = runCatching { URI(baseUrl.trim()).host?.lowercase() }.getOrNull()
        val useProductionFailover = BuildConfig.ENVIRONMENT.lowercase() != "dev" &&
            loopbackHost in setOf("127.0.0.1", "localhost", "10.0.2.2")
        if (useProductionFailover) BuildConfig.PRODUCTION_API_BASE_URL else null
    }
    val phoneSizeClass = rememberPhoneSizeClass()
    val horizontalPadding = contentHorizontalPadding(phoneSizeClass)
    val api = remember {
        ApiService.create(
            baseUrl = baseUrl,
            fallbackBaseUrl = fallbackBaseUrl,
            authTokenProvider = {
                context.getSharedPreferences(CACHE_PREFS, android.content.Context.MODE_PRIVATE)
                    .getString("auth_token", "")
            },
        )
    }
    val repository = remember { PetSocialRepository(api, baseUrl, fallbackBaseUrl, BuildConfig.MAPS_API_KEY, context) }
    val vm: PetSocialViewModel = viewModel(factory = PetSocialViewModelFactory(repository))
    val shellState by vm.shellUiState.collectAsStateWithLifecycle()
    val authState by vm.authDialogUiState.collectAsStateWithLifecycle()
    val pendingInviteState by vm.pendingInviteUiState.collectAsStateWithLifecycle()
    var locationRetryKey by remember { mutableIntStateOf(0) }
    var showLocationPermissionPrimer by rememberSaveable { mutableStateOf(false) }
    val isProviderSurface = BuildConfig.APP_SURFACE.equals("provider", ignoreCase = true)
    val defaultTab = resolveDefaultTab(
        appSurface = BuildConfig.APP_SURFACE,
        environment = BuildConfig.ENVIRONMENT,
    )

    val snackbarHostState = remember { SnackbarHostState() }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.any { it }) {
            locationRetryKey += 1
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            vm.syncPushToken()
        }
    }

    LaunchedEffect(Unit) {
        vm.loadHomeData(scope = HomeRefreshScope.ActiveTab)
        val notificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        if (notificationGranted) {
            vm.syncPushToken()
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (LocationResolver.hasLocationPermission(context)) {
            // Live location stream starts in LaunchedEffect(locationRetryKey) below.
        } else {
            showLocationPermissionPrimer = true
        }
    }

    LaunchedEffect(locationRetryKey) {
        if (!LocationResolver.hasLocationPermission(context)) return@LaunchedEffect
        LocationResolver.observeLocation(context = context, pollIntervalMs = 30_000L).collect { snapshot ->
            vm.setDetectedLocation(snapshot = snapshot, applyAsSelected = false)
        }
    }

    if (showLocationPermissionPrimer) {
        AlertDialog(
            onDismissRequest = { showLocationPermissionPrimer = false },
            title = { Text("Location permission") },
            text = {
                Text("BarkWise only uses your location to place map pins and local discovery. No background live tracking.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLocationPermissionPrimer = false
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    },
                ) {
                    Text("Allow location")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocationPermissionPrimer = false }) {
                    Text("Not now")
                }
            },
        )
    }

    LaunchedEffect(initialDeepLink) {
        deepLinkTestProfileMode(initialDeepLink)?.let { mode ->
            vm.setTestProfileMode(mode)
        }
        deepLinkProfileHeaderVisibility(initialDeepLink)?.let { visible ->
            vm.setProfileIdentityHeaderVisible(visible)
        }
        val token = deepLinkInviteToken(initialDeepLink)
        if (!token.isNullOrBlank()) {
            vm.resolveInviteToken(token)
        }
    }

    LaunchedEffect(shellState.activeUserId) {
        vm.syncPushToken()
    }

    LaunchedEffect(shellState.toastMessage) {
        val message = shellState.toastMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        vm.consumeToast()
    }

    val tabs = resolveBottomTabs(
        appSurface = BuildConfig.APP_SURFACE,
        environment = BuildConfig.ENVIRONMENT,
    ).map { tab ->
        TabItem(
            tab = tab,
            label = tabLabel(tab = tab, isProviderSurface = isProviderSurface),
        )
    }
    val shouldHandleBack = shellState.selectedProviderDetails != null ||
        shellState.selectedMessageThreadId != null ||
        shellState.selectedTab != defaultTab

    BackHandler(enabled = shouldHandleBack) {
        when {
            shellState.selectedProviderDetails != null -> vm.closeProviderDetails()
            shellState.selectedMessageThreadId != null -> vm.clearMessageThreadSelection()
            shellState.selectedTab != defaultTab -> vm.switchTab(defaultTab)
        }
    }

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
                            selected = shellState.selectedTab == item.tab,
                            onClick = {
                                if (item.tab == AppTab.BarkAI) {
                                    vm.openBarkAiTab()
                                } else {
                                    vm.switchTab(item.tab)
                                    vm.loadHomeData(
                                        showLoadingIndicators = item.tab != AppTab.Services,
                                        scope = HomeRefreshScope.ActiveTab,
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            icon = {
                                val badgeCount = when (item.tab) {
                                    AppTab.Community -> shellState.unreadCommunityNotificationCount
                                    AppTab.Messages -> shellState.unreadMessageNotificationCount
                                    else -> 0
                                }
                                if (badgeCount > 0) {
                                    BadgedBox(
                                        badge = {
                                            Badge {
                                                Text(
                                                    if (badgeCount > 9) "9+" else badgeCount.toString(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            }
                                        },
                                    ) {
                                        Icon(imageVector = icon, contentDescription = item.label)
                                    }
                                } else {
                                    Icon(imageVector = icon, contentDescription = item.label)
                                }
                            },
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
                .padding(horizontal = horizontalPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HeroHeader(
                compact = phoneSizeClass != PhoneSizeClass.Large,
                rosterPet = shellState.headerRosterPet,
                modeSummary = buildModeSummary(BuildConfig.ENVIRONMENT),
            )
            if (shellState.selectedTab == AppTab.Services) {
                SearchScopeBar(
                    selectedSuburb = shellState.selectedSuburb,
                    selectedRangeKm = shellState.serviceMaxDistanceKm,
                    currentLocationSuburb = shellState.currentLocationSuburb,
                    isUsingCurrentLocation = shellState.selectedRangeCenter == "current",
                    showRangeSelector = shellState.selectedTab != AppTab.Services && shellState.selectedTab != AppTab.Community,
                    suburbLocked = BuildConfig.ENVIRONMENT.lowercase() == "staging",
                    onManualSuburbApply = vm::applyManualSuburb,
                    onUseCurrentLocation = {
                        vm.setRangeCenterCurrent(enabled = true)
                        if (LocationResolver.hasLocationPermission(context)) {
                            locationRetryKey += 1
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                        }
                    },
                    onUseManualCenter = { vm.setRangeCenterCurrent(enabled = false) },
                    onRefreshLocation = {
                        vm.setRangeCenterCurrent(enabled = true)
                        locationRetryKey += 1
                    },
                    onRangeSelect = vm::updateServiceRange,
                )
            }

            shellState.error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (shellState.hasPendingSync && shellState.isOfflineMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = vm::retrySync,
                        enabled = !shellState.loading,
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
                when (shellState.selectedTab) {
                    AppTab.Services -> ServicesTabRoute(vm = vm)
                    AppTab.BarkAI -> BarkAiTabRoute(vm = vm)
                    AppTab.Community -> CommunityTabRoute(vm = vm)
                    AppTab.Messages -> MessagesTabRoute(vm = vm)
                    AppTab.Profile -> ProfileTabRoute(vm = vm)
                }
            }
        }
    }

    if (authState.isRequired) {
        var inviteId by rememberSaveable(authState.inviteId) {
            mutableStateOf(
                authState.inviteId.ifBlank { "" },
            )
        }
        var email by rememberSaveable(authState.email) {
            mutableStateOf(
                authState.email.ifBlank { "" },
            )
        }
        var otpCode by rememberSaveable { mutableStateOf("") }
        val canRequestOtp = inviteId.trim().isNotBlank() && email.trim().contains("@") && !authState.inFlight
        val canVerifyOtp = canRequestOtp && otpCode.trim().length >= 4 && authState.otpRequested && !authState.inFlight
        val authTitle = if (isProviderSurface) "Sign in to BW Provider" else "Sign in to BarkWise"
        val authDescription = if (BuildConfig.ENVIRONMENT.equals("prod", ignoreCase = true)) {
            if (isProviderSurface) {
                "Sign in with your invite ID and email OTP. Use the same invite and email as BarkWise if this is the same account."
            } else {
                "Sign in with your invite ID and email OTP. Use the same invite and email in BW Provider if this is the same account."
            }
        } else {
            if (isProviderSurface) {
                "Closed beta access requires an invite ID and email OTP. Use the same invite and email as BarkWise if this is the same account."
            } else {
                "Closed beta access requires an invite ID and email OTP. Use the same invite and email in BW Provider if this is the same account."
            }
        }
        AlertDialog(
            onDismissRequest = {},
            title = { Text(authTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(authDescription)
                    OutlinedTextField(
                        value = inviteId,
                        onValueChange = { inviteId = it },
                        label = { Text("Invite ID") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !authState.inFlight,
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !authState.inFlight,
                    )
                    if (authState.otpRequested) {
                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = { otpCode = it },
                            label = { Text("OTP code") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !authState.inFlight,
                        )
                        authState.otpExpiresAt?.let { expiresAt ->
                            Text(
                                text = "OTP expires at $expiresAt",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    authState.error?.takeIf { it.isNotBlank() }?.let { authError ->
                        Text(
                            text = authError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                if (authState.otpRequested) {
                    Button(
                        enabled = canVerifyOtp,
                        onClick = { vm.verifyAuthOtp(otpCode = otpCode) },
                    ) {
                        Text(if (authState.inFlight) "Verifying..." else "Verify OTP")
                    }
                } else {
                    Button(
                        enabled = canRequestOtp,
                        onClick = {
                            vm.requestAuthOtp(
                                inviteId = inviteId,
                                email = email,
                            )
                        },
                    ) {
                        Text(if (authState.inFlight) "Requesting..." else "Request OTP")
                    }
                }
            },
            dismissButton = {
                if (authState.otpRequested) {
                    TextButton(
                        enabled = !authState.inFlight,
                        onClick = {
                            otpCode = ""
                            vm.resetAuthOtpRequest(
                                inviteId = inviteId,
                                email = email,
                            )
                        },
                    ) {
                        Text("Edit invite/email")
                    }
                } else if (BuildConfig.ALLOW_DEMO_LOGIN) {
                    TextButton(
                        enabled = !authState.inFlight,
                        onClick = { vm.switchAccount("user_2") },
                    ) {
                        Text("Use demo account")
                    }
                }
            },
        )
    }

    if (!authState.isRequired) pendingInviteState.invite?.let { invite ->
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
                    enabled = ownerName.isNotBlank() && dogName.isNotBlank() && !pendingInviteState.loading,
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
private fun ServicesTabRoute(vm: PetSocialViewModel) {
    DebugRecomposeCounter(tag = "ServicesTabRoute")
    val state by vm.servicesUiState.collectAsStateWithLifecycle()
    val shellState by vm.shellUiState.collectAsStateWithLifecycle()
    val isProviderSurface = BuildConfig.APP_SURFACE.equals("provider", ignoreCase = true)
    val visibleProviders = remember(state.providers, shellState.activeUserId, isProviderSurface) {
        if (isProviderSurface) {
            state.providers.filter { provider -> provider.ownerUserId == shellState.activeUserId }
        } else {
            state.providers
        }
    }
    ServicesScreen(
        providers = visibleProviders,
        nearbyPetBusinesses = state.nearbyPetBusinesses,
        groomerPetRosters = state.groomerPetRosters,
        recommendationSuburb = state.recommendationSuburb,
        recommendationSource = state.recommendationSource,
        selectedCategory = state.selectedCategory,
        viewMode = state.viewMode,
        searchQuery = state.searchQuery,
        sortBy = state.sortBy,
        loading = state.loading,
        selectedDetails = state.selectedDetails,
        availableSlots = state.availableSlots,
        availabilityDate = state.availabilityDate,
        minRating = state.minRating,
        maxDistanceKm = state.maxDistanceKm,
        onChangeViewMode = vm::setServicesViewMode,
        onCategorySelect = vm::loadHomeData,
        onSearchQueryChange = vm::updateServicesSearchQuery,
        onSortByChange = vm::updateServicesSortBy,
        onFilterChange = vm::updateServiceFilters,
        onRequestQuote = vm::requestQuote,
        onBook = vm::requestBooking,
        onViewDetails = vm::loadProviderDetails,
        onLoadAvailability = vm::loadAvailability,
        onCloseDetails = vm::closeProviderDetails,
        providerWorkspaceMode = isProviderSurface,
        onOpenProviderHub = { vm.switchTab(AppTab.Profile) },
    )
}

@Composable
private fun BarkAiTabRoute(vm: PetSocialViewModel) {
    DebugRecomposeCounter(tag = "BarkAiTabRoute")
    val state by vm.barkAiUiState.collectAsStateWithLifecycle()
    ChatScreen(
        loading = state.loading,
        chatResponse = state.chatResponse,
        conversation = state.conversation,
        streamingAssistantText = state.streamingAssistantText,
        error = state.error,
        profileSuggestion = state.profileSuggestion,
        a2uiProfileCard = state.a2uiProfileCard,
        a2uiProviderCard = state.a2uiProviderCard,
        barkThreads = state.barkThreads,
        selectedBarkThreadId = state.selectedBarkThreadId,
        onboardingMode = state.onboardingMode,
        onboardingNeedsPhoto = state.onboardingNeedsPhoto,
        onSelectBarkThread = vm::selectBarkThread,
        onNewBarkThread = vm::startNewBarkThread,
        onSend = vm::sendChat,
        onOnboardingPhotoCaptured = { captured, photoUri ->
            vm.submitOnboardingPhotoCapture(photoCaptured = captured, dogPhotoUri = photoUri)
        },
        onCtaClick = vm::handleCta,
        onAcceptProfile = vm::acceptProfileCard,
        onSubmitProvider = vm::submitProviderListing,
    )
}

@Composable
private fun CommunityTabRoute(vm: PetSocialViewModel) {
    DebugRecomposeCounter(tag = "CommunityTabRoute")
    val state by vm.communityUiState.collectAsStateWithLifecycle()
    CommunityScreen(
        activeUserId = state.activeUserId,
        loading = state.loading,
        suburb = state.suburb,
        currentLocationSuburb = state.currentLocationSuburb,
        currentLatitude = state.currentLatitude,
        currentLongitude = state.currentLongitude,
        postsSortBy = state.postsSortBy,
        selectedGroupId = state.selectedGroupId,
        groups = state.groups,
        groupPetRosters = state.groupPetRosters,
        latestGroupInvites = state.latestGroupInvites,
        blockedUserIds = state.blockedUserIds,
        savedPostIds = state.savedPostIds,
        savedEventIds = state.savedEventIds,
        mutedKeywords = state.mutedKeywords,
        followedGroupIds = state.followedGroupIds,
        communityWeather = state.communityWeather,
        autoParkCheckInEnabled = state.autoParkCheckInEnabled,
        autoParkCheckInRequireCrowd = state.autoParkCheckInRequireCrowd,
        autoParkCheckInQuorumCount = state.autoParkCheckInQuorumCount,
        autoParkCheckInQuorumThreshold = state.autoParkCheckInQuorumThreshold,
        autoParkCheckInQuorumWindowMinutes = state.autoParkCheckInQuorumWindowMinutes,
        posts = state.posts,
        postCommentsByPostId = state.postCommentsByPostId,
        loadingCommentPostIds = state.loadingCommentPostIds,
        isCommunityModerator = state.isCommunityModerator,
        events = state.events,
        messageThreads = state.messageThreads,
        onOpenGroup = vm::openCommunityGroup,
        onOpenMessages = { participantUserId ->
            vm.openMessages(
                MessageTarget(
                    userId = participantUserId,
                    source = "community",
                ),
            )
        },
        onDismissSelectedGroup = vm::clearSelectedCommunityGroup,
        onJoinGroup = vm::joinGroup,
        onCreateGroupInvite = vm::createGroupInvite,
        onClearGroupInvite = vm::clearGroupInvite,
        onCreateGroup = { name, createSuburb -> vm.createCommunityGroup(name, createSuburb) },
        onPostsSortChange = vm::updatePostsSortBy,
        onCreateGroupPost = vm::createCommunityGroupPost,
        onCreateLostFound = vm::createLostFoundAlert,
        onCreateSharePoint = vm::createManualSharePoint,
        onCreateEvent = vm::createCommunityEvent,
        onUpdateEvent = vm::updateCommunityEvent,
        onRsvpEvent = vm::rsvpEvent,
        onApproveJoinRequest = vm::approveNextJoinRequest,
        onRejectJoinRequest = vm::rejectNextJoinRequest,
        onApproveEvent = vm::approveEvent,
        onLogCleanupCheckIn = vm::logGroupCleanupCheckIn,
        onResolveLostFound = vm::resolveLostFoundPost,
        onLoadPostComments = vm::loadPostComments,
        onCreatePostComment = vm::createPostComment,
        onModeratePostComment = { commentId, action ->
            vm.moderatePostComment(commentId = commentId, action = action)
        },
        onResolveInviteToken = vm::resolveInviteToken,
        onTrackQrScanOutcome = vm::trackQrScannerOutcome,
        onReportPost = vm::reportCommunityPost,
        onReportEvent = vm::reportCommunityEvent,
        onBlockUser = vm::blockCommunityUser,
        onDeletePost = vm::deleteCommunityPost,
        onToggleSavePost = vm::toggleSaveCommunityPost,
        onToggleSaveEvent = vm::toggleSaveCommunityEvent,
        onSetMutedKeywords = vm::setMutedCommunityKeywords,
        onToggleFollowGroup = vm::toggleFollowGroup,
        onRefreshWeather = vm::refreshCommunityWeather,
        onSetAutoParkCheckInEnabled = vm::setAutoParkCheckInEnabled,
        onSetAutoParkCheckInRequireCrowd = vm::setAutoParkCheckInRequireCrowd,
        onSimulateParkArrival = vm::simulateParkArrivalCheckIn,
    )
}

@Composable
private fun MessagesTabRoute(vm: PetSocialViewModel) {
    DebugRecomposeCounter(tag = "MessagesTabRoute")
    val state by vm.messagesUiState.collectAsStateWithLifecycle()
    MessagesScreen(
        activeUserId = state.activeUserId,
        threads = state.threads,
        mutedThreadIds = state.mutedThreadIds,
        pinnedThreadIds = state.pinnedThreadIds,
        unreadNotificationCount = state.unreadNotificationCount,
        selectedThreadId = state.selectedThreadId,
        messages = state.messages,
        onOpenNotifications = vm::openProfileNotifications,
        onSelectThread = vm::selectMessageThread,
        onBackToThreads = vm::clearMessageThreadSelection,
        onMarkThreadRead = vm::markMessageThreadRead,
        onToggleMuteThread = vm::toggleMuteMessageThread,
        onTogglePinThread = vm::togglePinMessageThread,
        onBlockParticipant = vm::blockCommunityUser,
        onSend = vm::sendDirectMessage,
    )
}

@Composable
private fun ProfileTabRoute(vm: PetSocialViewModel) {
    DebugRecomposeCounter(tag = "ProfileTabRoute")
    val state by vm.profileUiState.collectAsStateWithLifecycle()
    ProfileScreen(
        profileInfo = state.profileInfo,
        hasProviderListings = state.hasProviderListings,
        canLoadProviderInbox = state.canLoadProviderInbox,
        activeUserId = state.activeUserId,
        friendProfiles = state.friendProfiles,
        joinedEvents = state.joinedEvents,
        ownerBookings = state.ownerBookings,
        providerListings = state.providerListings,
        providerBookings = state.providerBookings,
        providerInboxItems = state.providerInboxItems,
        loadingProviderInbox = state.loadingProviderInbox,
        sendingQuoteOfferItemIds = state.sendingQuoteOfferItemIds,
        isSubmittingProviderInboxAction = state.isSubmittingProviderInboxAction,
        calendarEvents = state.calendarEvents,
        notifications = state.notifications,
        activationFunnelMetrics = state.activationFunnelMetrics,
        notifyFollowedGroupAlerts = state.notifyFollowedGroupAlerts,
        notifySavedPostUpdates = state.notifySavedPostUpdates,
        notifySafetyAlerts = state.notifySafetyAlerts,
        showIdentityHeader = state.showIdentityHeader,
        friendQrPayload = state.friendQrPayload,
        friendQrExpiresAt = state.friendQrExpiresAt,
        friendQrLoading = state.friendQrLoading,
        onRefreshFriendQrPayload = vm::refreshFriendQrPayload,
        onResolveFriendQrToken = vm::resolveFriendQrToken,
        onRemoveFriend = vm::removeFriend,
        onOpenFriendMessages = { participantUserId ->
            vm.openMessages(
                MessageTarget(
                    userId = participantUserId,
                    source = "profile_friend",
                ),
            )
        },
        onOpenMessages = { participantUserId, threadId ->
            vm.openMessages(
                MessageTarget(
                    userId = participantUserId,
                    threadId = threadId,
                    source = "profile",
                ),
            )
        },
        onSaveProfile = vm::saveProfileInfo,
        onMarkNotificationRead = vm::markNotificationRead,
        onMarkAllNotificationsRead = vm::markAllNotificationsRead,
        onClearLocalNotifications = vm::clearLocalNotifications,
        onOpenNotificationDeepLink = vm::openNotificationDeepLink,
        onUpdateNotificationPreferences = vm::setNotificationPreferences,
        onResetDeviceSignIn = vm::resetCurrentDeviceSignIn,
        onRefreshActivationDashboard = vm::reloadHomeData,
        onRefreshProviderInbox = vm::refreshProviderInbox,
        onSendQuoteOffer = vm::sendQuoteOfferFromInbox,
        onConfirmProviderBooking = vm::confirmProviderBooking,
        onDeclineProviderBooking = vm::cancelProviderBooking,
        onRescheduleProviderBooking = vm::rescheduleProviderBooking,
        onCreateProviderListing = vm::createProviderListing,
        onEditProviderListing = vm::editProviderListing,
        onCancelProviderListing = vm::cancelProviderListing,
        onRestoreProviderListing = vm::restoreProviderListing,
        onCreateProviderBlackout = vm::createProviderBlackout,
    )
}

@Composable
private fun HeroHeader(
    compact: Boolean,
    rosterPet: PetRosterItem?,
    modeSummary: String,
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
                    Box(
                        modifier = Modifier
                            .size(if (compact) 24.dp else 34.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    listOf(Color(0xFFC9E2D4), Color(0xFFB8D4C3))
                                ),
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground_appicon),
                            contentDescription = "BarkWise logo",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(if (compact) 22.dp else 32.dp),
                        )
                    }
                    Text(
                        "BarkWise",
                        color = Color.White,
                        style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                    )
                }
                Text(
                    "Dog owners, groups, and trusted local care",
                    color = Color.White.copy(alpha = 0.88f),
                    style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                )
                if (modeSummary.isNotBlank()) {
                    Text(
                        text = modeSummary,
                        color = Color.White.copy(alpha = 0.96f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
private fun DebugRecomposeCounter(tag: String) {
    if (!BuildConfig.DEBUG) return
    var recomposeCount by remember(tag) { mutableIntStateOf(0) }
    SideEffect {
        recomposeCount += 1
        Log.d("BarkWiseCompose", "$tag recompositions=$recomposeCount")
    }
}

private fun buildModeSummary(environment: String): String {
    return when (environment.lowercase()) {
        "prod" -> ""
        "staging" -> "Mode: Beta 1"
        else -> ""
    }
}

@Composable
private fun SearchScopeBar(
    selectedSuburb: String,
    selectedRangeKm: Int?,
    currentLocationSuburb: String?,
    isUsingCurrentLocation: Boolean,
    showRangeSelector: Boolean,
    suburbLocked: Boolean,
    onManualSuburbApply: (String) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onUseManualCenter: () -> Unit,
    onRefreshLocation: () -> Unit,
    onRangeSelect: (Int?) -> Unit,
) {
    var rangeMenuExpanded by remember { mutableStateOf(false) }
    var manualSuburbInput by rememberSaveable { mutableStateOf(selectedSuburb) }
    val selectedRangeLabel = selectedRangeKm?.let { "$it km" } ?: "Any range"

    LaunchedEffect(selectedSuburb) {
        manualSuburbInput = selectedSuburb
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Search scope", style = MaterialTheme.typography.labelMedium)
            if (suburbLocked) {
                Text(
                    text = selectedSuburb,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (suburbLocked) return
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            FilterChip(
                selected = !isUsingCurrentLocation,
                onClick = onUseManualCenter,
                label = { Text("Manual suburb") },
                enabled = !suburbLocked,
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
                enabled = !suburbLocked,
            )
            if (!currentLocationSuburb.isNullOrBlank()) {
                FilterChip(
                    selected = false,
                    onClick = onRefreshLocation,
                    label = { Text("Refresh GPS") },
                    enabled = !suburbLocked,
                )
            }
        }
        if (!isUsingCurrentLocation && !suburbLocked) {
            OutlinedTextField(
                value = manualSuburbInput,
                onValueChange = { manualSuburbInput = it },
                label = { Text("Manual suburb") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                val trimmedInput = manualSuburbInput.trim()
                TextButton(
                    onClick = { onManualSuburbApply(trimmedInput) },
                    enabled = trimmedInput.isNotBlank() && trimmedInput != selectedSuburb.trim(),
                ) {
                    Text("Apply suburb")
                }
            }
        }
        if (showRangeSelector) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Range", style = MaterialTheme.typography.labelMedium)
                Box {
                    FilterChip(
                        selected = rangeMenuExpanded,
                        onClick = { rangeMenuExpanded = true },
                        label = { Text(selectedRangeLabel) },
                    )
                    DropdownMenu(
                        expanded = rangeMenuExpanded,
                        onDismissRequest = { rangeMenuExpanded = false },
                    ) {
                        listOf<Int?>(null, 5, 10, 20, 50).forEach { range ->
                            DropdownMenuItem(
                                text = { Text(range?.let { "$it km" } ?: "Any range") },
                                onClick = {
                                    onRangeSelect(range)
                                    rangeMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
