package com.petsocial.app.ui.screens

import android.app.DatePickerDialog
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.petsocial.app.data.NearbyPetBusiness
import com.petsocial.app.data.Review
import com.petsocial.app.data.ServiceAvailabilitySlot
import com.petsocial.app.data.ServiceProvider
import com.petsocial.app.data.ServiceProviderDetailsResponse
import com.petsocial.app.ui.PetRosterItem
import com.petsocial.app.ui.components.PetRosterShowcase
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ServicesSpaceXs = 8.dp
private val ServicesSpaceSm = 10.dp
private val ServicesSpaceMd = 12.dp

@Composable
fun ServicesScreen(
    providers: List<ServiceProvider>,
    nearbyPetBusinesses: List<NearbyPetBusiness>,
    groomerPetRosters: Map<String, List<PetRosterItem>>,
    recommendationSuburb: String?,
    recommendationSource: String,
    selectedCategory: String?,
    viewMode: String,
    searchQuery: String,
    sortBy: String,
    loading: Boolean,
    selectedDetails: ServiceProviderDetailsResponse?,
    availableSlots: List<ServiceAvailabilitySlot>,
    availabilityDate: String?,
    minRating: Float?,
    maxDistanceKm: Int?,
    onChangeViewMode: (String) -> Unit,
    onCategorySelect: (String?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSortByChange: (String) -> Unit,
    onFilterChange: (Float?, Int?) -> Unit,
    onRequestQuote: (category: String, preferredWindow: String, petDetails: String, note: String) -> Unit,
    onBook: (providerId: String, date: String, timeSlot: String, note: String) -> Unit,
    onViewDetails: (String) -> Unit,
    onLoadAvailability: (providerId: String, date: String) -> Unit,
    onCloseDetails: () -> Unit,
) {
    if (selectedDetails == null) {
        ServicesListPage(
            providers = providers,
            nearbyPetBusinesses = nearbyPetBusinesses,
            groomerPetRosters = groomerPetRosters,
            recommendationSuburb = recommendationSuburb,
            recommendationSource = recommendationSource,
            selectedCategory = selectedCategory,
            viewMode = viewMode,
            searchQuery = searchQuery,
            sortBy = sortBy,
            loading = loading,
            minRating = minRating,
            maxDistanceKm = maxDistanceKm,
            onChangeViewMode = onChangeViewMode,
            onCategorySelect = onCategorySelect,
            onSearchQueryChange = onSearchQueryChange,
            onSortByChange = onSortByChange,
            onFilterChange = onFilterChange,
            onRequestQuote = onRequestQuote,
            onViewDetails = onViewDetails,
        )
    } else {
        ServiceDetailsPage(
            details = selectedDetails,
            availableSlots = availableSlots,
            availabilityDate = availabilityDate,
            onBack = onCloseDetails,
            onLoadAvailability = onLoadAvailability,
            onBook = onBook,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServicesListPage(
    providers: List<ServiceProvider>,
    nearbyPetBusinesses: List<NearbyPetBusiness>,
    groomerPetRosters: Map<String, List<PetRosterItem>>,
    recommendationSuburb: String?,
    recommendationSource: String,
    selectedCategory: String?,
    viewMode: String,
    searchQuery: String,
    sortBy: String,
    loading: Boolean,
    minRating: Float?,
    maxDistanceKm: Int?,
    onChangeViewMode: (String) -> Unit,
    onCategorySelect: (String?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSortByChange: (String) -> Unit,
    onFilterChange: (Float?, Int?) -> Unit,
    onRequestQuote: (category: String, preferredWindow: String, petDetails: String, note: String) -> Unit,
    onViewDetails: (String) -> Unit,
) {
    val categories = listOf(
        null to "All",
        "dog_walking" to "Dog Walking",
        "grooming" to "Grooming",
    )
    val ratingOptions = listOf<Float?>(null, 4.0f, 4.5f)
    val distanceOptions = listOf<Int?>(null, 2, 5, 10)
    val sortOptions = listOf(
        "relevance" to "Best match",
        "distance" to "Nearest",
        "rating" to "Top rated",
        "price_low" to "Price: low-high",
        "price_high" to "Price: high-low",
    )
    val activeFilterCount = listOf(minRating != null, maxDistanceKm != null, sortBy != "relevance").count { it }
    val listState = rememberLazyListState()
    var quoteCategory by rememberSaveable(selectedCategory) {
        mutableStateOf(
            when (selectedCategory) {
                "dog_walking", "grooming" -> selectedCategory
                else -> "dog_walking"
            },
        )
    }
    val recommendationLabel = remember(recommendationSuburb, recommendationSource) {
        when {
            recommendationSuburb.isNullOrBlank() || recommendationSource == "none" -> null
            recommendationSource == "dog_park_membership" -> "Recommendations centered on $recommendationSuburb from your dog park activity."
            recommendationSource == "group_membership" -> "Recommendations centered on $recommendationSuburb from your joined groups."
            recommendationSource == "explicit_suburb" -> "Recommendations centered on $recommendationSuburb."
            else -> "Recommendations centered on $recommendationSuburb."
        }
    }
    var quoteWindow by rememberSaveable { mutableStateOf("Weekday mornings") }
    var quotePetDetails by rememberSaveable { mutableStateOf("") }
    var quoteNote by rememberSaveable { mutableStateOf("") }
    var showQuoteSheet by rememberSaveable { mutableStateOf(false) }
    var showRefineSheet by rememberSaveable { mutableStateOf(false) }
    var refineCategory by rememberSaveable(selectedCategory) { mutableStateOf(selectedCategory) }
    var refineMinRating by rememberSaveable(minRating) { mutableStateOf(minRating) }
    var refineMaxDistance by rememberSaveable(maxDistanceKm) { mutableStateOf(maxDistanceKm) }
    var refineSearchQuery by rememberSaveable(searchQuery) { mutableStateOf(searchQuery) }
    var refineSortBy by rememberSaveable(sortBy) { mutableStateOf(sortBy) }
    var lastQuoteSubmitAt by rememberSaveable { mutableStateOf(0L) }
    var lastRefineSubmitAt by rememberSaveable { mutableStateOf(0L) }
    var isMapGestureActive by remember { mutableStateOf(false) }
    var mapGestureHeartbeatAt by remember { mutableStateOf(0L) }
    val canSendQuote = quoteWindow.trim().isNotBlank() && quotePetDetails.trim().length >= 3
    LaunchedEffect(viewMode) {
        if (viewMode != "map") {
            isMapGestureActive = false
        }
    }
    LaunchedEffect(isMapGestureActive, mapGestureHeartbeatAt) {
        if (!isMapGestureActive) return@LaunchedEffect
        delay(320)
        if (SystemClock.elapsedRealtime() - mapGestureHeartbeatAt >= 300L) {
            isMapGestureActive = false
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.testTag("services_results_list"),
            state = listState,
            userScrollEnabled = !isMapGestureActive,
            verticalArrangement = Arrangement.spacedBy(ServicesSpaceSm),
            contentPadding = PaddingValues(
                top = 0.dp,
                bottom = 90.dp,
            ),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(ServicesSpaceMd),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "Trusted local listings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        recommendationLabel?.let { text ->
                            Text(
                                text,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(ServicesSpaceXs)) {
                                FilterChip(
                                    modifier = Modifier.testTag("services_view_mode_list_chip"),
                                    selected = viewMode == "list",
                                    onClick = { onChangeViewMode("list") },
                                    label = { Text("List") },
                                    enabled = !loading,
                                )
                                FilterChip(
                                    modifier = Modifier.testTag("services_view_mode_map_chip"),
                                    selected = viewMode == "map",
                                    onClick = { onChangeViewMode("map") },
                                    label = { Text("Map") },
                                    enabled = !loading,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    enabled = !loading,
                                    onClick = { showQuoteSheet = true },
                                ) {
                                    Icon(Icons.Default.RequestQuote, contentDescription = "Request quote from top 3")
                                }
                                IconButton(
                                    enabled = !loading,
                                    onClick = { showRefineSheet = true },
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = "Refine search")
                                }
                            }
                        }
                    }
                }
            }

            item {
                if (activeFilterCount > 0 || searchQuery.isNotBlank() || selectedCategory != null) {
                    Text(
                        "Filters applied • Tap search to refine",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (viewMode == "map") {
                item {
                    ServicesMapPanel(
                        providers = providers,
                        nearbyPetBusinesses = nearbyPetBusinesses,
                        onViewDetails = onViewDetails,
                        onMapGestureActiveChange = { active ->
                            if (active) {
                                mapGestureHeartbeatAt = SystemClock.elapsedRealtime()
                            }
                            isMapGestureActive = active
                        },
                    )
                }
            } else if (loading && providers.isEmpty()) {
                items(3, key = { index -> "provider_skeleton_$index" }) {
                    ServicesProviderSkeletonCard()
                }
            } else if (providers.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "No providers match your filters yet. Try broadening distance or rating.",
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                items(
                    items = providers,
                    key = { provider -> provider.id },
                ) { provider ->
                    ProviderCard(
                        provider = provider,
                        onViewDetails = onViewDetails,
                        groomerRoster = groomerPetRosters[provider.id].orEmpty(),
                    )
                }
            }
        }

    }

    if (showQuoteSheet) {
        ModalBottomSheet(onDismissRequest = { showQuoteSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(ServicesSpaceXs),
            ) {
                Text("Request quote from top 3", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(ServicesSpaceXs)) {
                    FilterChip(
                        selected = quoteCategory == "dog_walking",
                        onClick = { quoteCategory = "dog_walking" },
                        label = { Text("Dog Walking") },
                        enabled = !loading,
                    )
                    FilterChip(
                        selected = quoteCategory == "grooming",
                        onClick = { quoteCategory = "grooming" },
                        label = { Text("Grooming") },
                        enabled = !loading,
                    )
                }
                OutlinedTextField(
                    value = quoteWindow,
                    onValueChange = { quoteWindow = it },
                    label = { Text("Preferred window") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !loading,
                )
                OutlinedTextField(
                    value = quotePetDetails,
                    onValueChange = { quotePetDetails = it },
                    label = { Text("Pet details") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading,
                    minLines = 2,
                )
                OutlinedTextField(
                    value = quoteNote,
                    onValueChange = { quoteNote = it },
                    label = { Text("Extra note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading,
                    minLines = 2,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastQuoteSubmitAt < 900L) return@Button
                            lastQuoteSubmitAt = now
                            onRequestQuote(
                                quoteCategory,
                                quoteWindow,
                                quotePetDetails,
                                quoteNote,
                            )
                            showQuoteSheet = false
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !loading && canSendQuote,
                    ) {
                        Text("Send")
                    }
                    OutlinedButton(
                        onClick = { showQuoteSheet = false },
                        modifier = Modifier.weight(1f),
                        enabled = !loading,
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }

    if (showRefineSheet) {
        ModalBottomSheet(onDismissRequest = { showRefineSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ServicesSpaceSm),
            ) {
                Text("Refine search", style = MaterialTheme.typography.titleMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ServicesSpaceXs),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    categories.forEach { (key, label) ->
                        FilterChip(
                            onClick = { refineCategory = key },
                            label = { Text(label) },
                            selected = refineCategory == key,
                            enabled = !loading,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilterDropdown(
                        modifier = Modifier.weight(1f),
                        label = "Rating",
                        selectedLabel = refineMinRating?.let { "${it}+ rating" } ?: "Any rating",
                        options = ratingOptions.map { option -> option to (option?.let { "${it}+ rating" } ?: "Any rating") },
                        onSelect = { option -> refineMinRating = option },
                    )
                    FilterDropdown(
                        modifier = Modifier.weight(1f),
                        label = "Distance",
                        selectedLabel = refineMaxDistance?.let { "$it km" } ?: "Any distance",
                        options = distanceOptions.map { option -> option to (option?.let { "$it km" } ?: "Any distance") },
                        onSelect = { option -> refineMaxDistance = option },
                    )
                }
                OutlinedTextField(
                    value = refineSearchQuery,
                    onValueChange = { refineSearchQuery = it },
                    label = { Text("Search listings") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading,
                    singleLine = true,
                )
                FilterDropdown(
                    label = "Sort",
                    selectedLabel = sortOptions.firstOrNull { it.first == refineSortBy }?.second ?: "Best match",
                    options = sortOptions,
                    onSelect = { selected -> refineSortBy = selected },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(ServicesSpaceXs), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        enabled = !loading,
                        onClick = {
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastRefineSubmitAt < 900L) return@Button
                            lastRefineSubmitAt = now
                            onCategorySelect(refineCategory)
                            onFilterChange(refineMinRating, refineMaxDistance)
                            onSortByChange(refineSortBy)
                            onSearchQueryChange(refineSearchQuery)
                            showRefineSheet = false
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Apply")
                    }
                    OutlinedButton(
                        enabled = !loading,
                        onClick = {
                            refineCategory = null
                            refineMinRating = null
                            refineMaxDistance = null
                            refineSearchQuery = ""
                            refineSortBy = "relevance"
                            onCategorySelect(null)
                            onFilterChange(null, null)
                            onSortByChange("relevance")
                            onSearchQueryChange("")
                            showRefineSheet = false
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Clear")
                    }
                }
            }
        }
    }
}

@Composable
private fun ServicesProviderSkeletonCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ServicesSpaceMd),
            verticalArrangement = Arrangement.spacedBy(ServicesSpaceXs),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp),
            ) {}
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(12.dp),
            ) {}
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(12.dp),
            ) {}
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ServicesMapPanel(
    providers: List<ServiceProvider>,
    nearbyPetBusinesses: List<NearbyPetBusiness>,
    onViewDetails: (String) -> Unit,
    onMapGestureActiveChange: (Boolean) -> Unit,
) {
    val providerMarkers = providers.mapNotNull { provider ->
        provider.renderableLatLngOrNull()?.let { latLng -> ProviderMarker(provider = provider, latLng = latLng) }
    }
    val usableNearbyBusinesses = nearbyPetBusinesses.filter { place ->
        place.latitude.isFinite() &&
            place.longitude.isFinite() &&
            place.latitude in -90.0..90.0 &&
            place.longitude in -180.0..180.0
    }
    if (providerMarkers.isEmpty() && usableNearbyBusinesses.isEmpty()) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Text(
                text = "No location data to render map yet.",
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    val cameraPositionState = rememberCameraPositionState()
    var userHasInteractedWithMap by remember { mutableStateOf(false) }
    var lastAutoFitDataSignature by remember { mutableStateOf<String?>(null) }
    val mapDataSignature = remember(providerMarkers, usableNearbyBusinesses) {
        buildServicesMapDataSignature(providerMarkers, usableNearbyBusinesses)
    }
    val mapUiSettings = remember {
        MapUiSettings(
            scrollGesturesEnabled = true,
            zoomGesturesEnabled = true,
            rotationGesturesEnabled = true,
            tiltGesturesEnabled = true,
            scrollGesturesEnabledDuringRotateOrZoom = true,
        )
    }
    LaunchedEffect(mapDataSignature) {
        if (!shouldAutoFitServicesMap(
                lastAutoFitDataSignature = lastAutoFitDataSignature,
                currentDataSignature = mapDataSignature,
                userHasInteractedWithMap = userHasInteractedWithMap,
            )
        ) {
            return@LaunchedEffect
        }
        val boundsBuilder = LatLngBounds.Builder()
        providerMarkers.forEach { marker ->
            boundsBuilder.include(marker.latLng)
        }
        usableNearbyBusinesses.forEach { place ->
            boundsBuilder.include(LatLng(place.latitude, place.longitude))
        }
        val bounds = boundsBuilder.build()
        val update = if (providerMarkers.size + usableNearbyBusinesses.size == 1) {
            val target = providerMarkers.firstOrNull()?.latLng
                ?: usableNearbyBusinesses.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
                ?: return@LaunchedEffect
            CameraUpdateFactory.newLatLngZoom(
                target,
                14f,
            )
        } else {
            CameraUpdateFactory.newLatLngBounds(bounds, 80)
        }
        cameraPositionState.animate(update)
        lastAutoFitDataSignature = mapDataSignature
        userHasInteractedWithMap = false
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("services_map_panel"),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Nearby map", style = MaterialTheme.typography.titleSmall)
            GoogleMap(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .pointerInteropFilter { event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN,
                            MotionEvent.ACTION_MOVE,
                            MotionEvent.ACTION_POINTER_DOWN -> {
                                userHasInteractedWithMap = true
                                onMapGestureActiveChange(true)
                            }
                            MotionEvent.ACTION_UP,
                            MotionEvent.ACTION_POINTER_UP,
                            MotionEvent.ACTION_CANCEL,
                            MotionEvent.ACTION_OUTSIDE -> onMapGestureActiveChange(false)
                        }
                        false
                    },
                cameraPositionState = cameraPositionState,
                uiSettings = mapUiSettings,
            ) {
                providerMarkers.forEach { marker ->
                    val provider = marker.provider
                    Marker(
                        state = MarkerState(position = marker.latLng),
                        title = provider.name,
                        snippet = provider.distanceKm?.let {
                            String.format(Locale.getDefault(), "%.1f km • %s", it, provider.suburb)
                        } ?: provider.suburb,
                        onClick = {
                            onViewDetails(provider.id)
                            true
                        },
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                    ) {
                    }
                }
                usableNearbyBusinesses.forEach { place ->
                    Marker(
                        state = MarkerState(position = LatLng(place.latitude, place.longitude)),
                        title = place.name,
                        snippet = buildString {
                            append(place.vicinity ?: "Nearby pet business")
                            place.rating?.let { rating ->
                                append(" • ")
                                append(
                                    String.format(
                                        Locale.getDefault(),
                                        "%.1f★",
                                        rating,
                                    ),
                                )
                            }
                        },
                        icon = BitmapDescriptorFactory.defaultMarker(place.primaryType.markerHue()),
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                Text("Map key:", style = MaterialTheme.typography.labelSmall)
                Text("Blue = BarkWise provider", style = MaterialTheme.typography.labelSmall)
                Text("Green = Vet", style = MaterialTheme.typography.labelSmall)
                Text("Orange = Grooming", style = MaterialTheme.typography.labelSmall)
                Text("Rose = Pet store", style = MaterialTheme.typography.labelSmall)
                Text("Yellow = Other pet biz", style = MaterialTheme.typography.labelSmall)
            }
            providerMarkers.map { marker -> marker.provider }.distinctBy { provider -> provider.id }.take(8).forEach { provider ->
                Card(
                    onClick = { onViewDetails(provider.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
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
                            Text(provider.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                provider.distanceKm?.let { String.format(Locale.getDefault(), "%.1f km • %s", it, provider.suburb) }
                                    ?: provider.suburb,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Text("View", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> FilterDropdown(
    modifier: Modifier = Modifier,
    label: String,
    selectedLabel: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(selectedLabel, modifier = Modifier.weight(1f))
            Text("▼")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun <T> CompactFilterDropdown(
    modifier: Modifier = Modifier,
    selectedLabel: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(selectedLabel, modifier = Modifier.weight(1f))
            Text("▼")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ProviderCard(
    provider: ServiceProvider,
    groomerRoster: List<PetRosterItem>,
    onViewDetails: (String) -> Unit,
) {
    Card(
        onClick = { onViewDetails(provider.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(provider.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${provider.suburb} • ${provider.category.replace("_", " ")}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Text(
                        text = "${provider.rating}★",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            provider.ownerLabel?.let { owner ->
                Text("Offered by $owner", style = MaterialTheme.typography.labelSmall)
            }
            provider.distanceKm?.let { distance ->
                Text(
                    String.format(Locale.getDefault(), "%.1f km away", distance),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                if (provider.vetChecked) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    ) {
                        Text(
                            text = "Vet-Checked",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (provider.quoteSprintTier != "none") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    ) {
                        Text(
                            text = "Quote Sprint ${provider.quoteSprintTier.replaceFirstChar { it.uppercase() }}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (!provider.highlightedVet.isNullOrBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Text(
                            text = "Highlighted Vet Owner",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                provider.responseTimeMinutes?.let { responseTime ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Text(
                            text = "Responds in ~$responseTime min",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (provider.localBookersThisMonth >= 5) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    ) {
                        Text(
                            text = "Local favorite",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (provider.sharedGroupBookers > 0) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    ) {
                        Text(
                            text = "Group-trusted",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            provider.socialProof.take(3).forEach { socialProofLine ->
                Text(
                    text = "• $socialProofLine",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(provider.description, style = MaterialTheme.typography.bodyMedium)
            if (provider.category == "grooming" && groomerRoster.isNotEmpty()) {
                PetRosterShowcase(
                    title = "Recently groomed this week",
                    pets = groomerRoster,
                )
            }
            Text("From $${provider.priceFrom}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ServiceDetailsPage(
    details: ServiceProviderDetailsResponse,
    availableSlots: List<ServiceAvailabilitySlot>,
    availabilityDate: String?,
    onBack: () -> Unit,
    onLoadAvailability: (providerId: String, date: String) -> Unit,
    onBook: (providerId: String, date: String, timeSlot: String, note: String) -> Unit,
) {
    var bookingDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var selectedSlot by rememberSaveable { mutableStateOf<String?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val visibleSlots = remember(availableSlots, bookingDate, availabilityDate) {
        if (availabilityDate == bookingDate) {
            availableSlots.filter { it.available }
        } else {
            emptyList()
        }
    }
    val initialDate = runCatching { LocalDate.parse(bookingDate) }.getOrElse { LocalDate.now() }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .padding(bottom = 80.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        TextButton(onClick = onBack) { Text("Back to listings") }
        Text(details.provider.name, style = MaterialTheme.typography.headlineSmall)
        Text("${details.provider.suburb} • ${details.provider.category.replace("_", " ")} • ${details.provider.rating}★")
        details.provider.ownerLabel?.let { owner ->
            Text("Provider account: $owner", style = MaterialTheme.typography.labelMedium)
        }

        if (details.provider.imageUrls.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(details.provider.imageUrls) { imageUrl ->
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Listing image",
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .padding(vertical = 2.dp),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }

        Text(details.provider.fullDescription.ifBlank { details.provider.description })
        Text("Past reviews", style = MaterialTheme.typography.titleMedium)
        if (details.reviews.isEmpty()) {
            Text("No reviews yet")
        } else {
            details.reviews.forEach { review -> ReviewLine(review) }
        }

        Text("Book appointment", style = MaterialTheme.typography.titleMedium)
        Text("Step 1: Choose date")
        OutlinedButton(
            onClick = {
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        bookingDate = LocalDate.of(year, month + 1, dayOfMonth).toString()
                        selectedSlot = null
                    },
                    initialDate.year,
                    initialDate.monthValue - 1,
                    initialDate.dayOfMonth,
                ).show()
            },
        ) {
            Text("Selected date: $bookingDate")
        }
        TextButton(onClick = {
            selectedSlot = null
            onLoadAvailability(details.provider.id, bookingDate)
        }) {
            Text("Load time slots")
        }

        Text("Step 2: Select time")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            visibleSlots.forEach { slot ->
                FilterChip(
                    selected = selectedSlot == slot.timeSlot,
                    onClick = { selectedSlot = slot.timeSlot },
                    label = { Text(slot.timeSlot) },
                )
            }
        }
        if (availabilityDate != bookingDate) {
            Text(
                "Load slots for selected date to see availability",
                style = MaterialTheme.typography.labelSmall,
            )
        }

        Text("Step 3: Comments")
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Comments") },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedButton(
            onClick = {
                val slot = selectedSlot ?: return@OutlinedButton
                onBook(details.provider.id, bookingDate, slot, note)
            },
            enabled = selectedSlot != null,
        ) {
            Text("Confirm booking")
        }
    }
}

@Composable
private fun ReviewLine(review: Review) {
    Text("${review.author}: ${review.rating}★ - ${review.comment}")
}

private data class ProviderMarker(
    val provider: ServiceProvider,
    val latLng: LatLng,
)

internal fun shouldAutoFitServicesMap(
    lastAutoFitDataSignature: String?,
    currentDataSignature: String,
    userHasInteractedWithMap: Boolean,
): Boolean {
    if (!userHasInteractedWithMap) return true
    return lastAutoFitDataSignature != currentDataSignature
}

private fun buildServicesMapDataSignature(
    providerMarkers: List<ProviderMarker>,
    nearbyPetBusinesses: List<NearbyPetBusiness>,
): String {
    val providerEntries = providerMarkers
        .map { marker ->
            val provider = marker.provider
            buildString {
                append("p:")
                append(provider.id)
                append(':')
                append("%.5f".format(Locale.US, marker.latLng.latitude))
                append(',')
                append("%.5f".format(Locale.US, marker.latLng.longitude))
            }
        }
        .sorted()
    val nearbyEntries = nearbyPetBusinesses
        .map { place ->
            buildString {
                append("n:")
                append(place.placeId)
                append(':')
                append("%.5f".format(Locale.US, place.latitude))
                append(',')
                append("%.5f".format(Locale.US, place.longitude))
            }
        }
        .sorted()
    return (providerEntries + nearbyEntries).joinToString(separator = "|")
}

private fun ServiceProvider.renderableLatLngOrNull(): LatLng? {
    val lat = latitude
    val lng = longitude
    if (lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0 && (lat != 0.0 || lng != 0.0)) {
        return LatLng(lat, lng)
    }
    return suburbCenterLatLng(suburb)
}

private fun suburbCenterLatLng(suburb: String): LatLng? {
    val key = suburb.trim().lowercase()
    return when (key) {
        "surry hills" -> LatLng(-33.8889, 151.2111)
        "newtown" -> LatLng(-33.8981, 151.1742)
        "redfern" -> LatLng(-33.8928, 151.2040)
        "sunshine west" -> LatLng(-37.7910, 144.8150)
        "melbourne" -> LatLng(-37.8136, 144.9631)
        "sydney" -> LatLng(-33.8688, 151.2093)
        else -> null
    }
}

private fun String?.markerHue(): Float = when {
    this == null -> BitmapDescriptorFactory.HUE_YELLOW
    contains("veterinary", ignoreCase = true) -> BitmapDescriptorFactory.HUE_GREEN
    contains("groom", ignoreCase = true) -> BitmapDescriptorFactory.HUE_ORANGE
    contains("pet_store", ignoreCase = true) -> BitmapDescriptorFactory.HUE_ROSE
    else -> BitmapDescriptorFactory.HUE_YELLOW
}
