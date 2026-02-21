package com.petsocial.app.ui.screens

import android.app.DatePickerDialog
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
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
import kotlinx.coroutines.launch

@Composable
fun ServicesScreen(
    providers: List<ServiceProvider>,
    nearbyPetBusinesses: List<NearbyPetBusiness>,
    groomerPetRosters: Map<String, List<PetRosterItem>>,
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
    onCreateService: () -> Unit,
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
            onCreateService = onCreateService,
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

@Composable
private fun ServicesListPage(
    providers: List<ServiceProvider>,
    nearbyPetBusinesses: List<NearbyPetBusiness>,
    groomerPetRosters: Map<String, List<PetRosterItem>>,
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
    onCreateService: () -> Unit,
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
    val coroutineScope = rememberCoroutineScope()
    val fastResponderCount = providers.count { provider ->
        val responseTime = provider.responseTimeMinutes ?: return@count false
        responseTime <= 30
    }
    val groupTrustedCount = providers.count { provider -> provider.sharedGroupBookers > 0 }
    val localFavoriteCount = providers.count { provider -> provider.localBookersThisMonth >= 5 }
    val challengeScore = (fastResponderCount * 2) + groupTrustedCount + localFavoriteCount
    val challengeGoal = maxOf(6, minOf(18, providers.size * 3))
    val challengeProgress = if (challengeGoal == 0) 0f else challengeScore.toFloat() / challengeGoal.toFloat()
    var quoteCategory by rememberSaveable(selectedCategory) {
        mutableStateOf(
            when (selectedCategory) {
                "dog_walking", "grooming" -> selectedCategory
                else -> "dog_walking"
            },
        )
    }
    var quoteWindow by rememberSaveable { mutableStateOf("Weekday mornings") }
    var quotePetDetails by rememberSaveable { mutableStateOf("") }
    var quoteNote by rememberSaveable { mutableStateOf("") }
    val listHasScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 20
        }
    }
    var filtersExpanded by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(viewMode) {
        if (viewMode == "map") {
            filtersExpanded = true
        }
    }
    LaunchedEffect(viewMode, listHasScrolled) {
        if (viewMode == "list" && listHasScrolled) {
            filtersExpanded = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "Trusted local listings",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onCreateService) {
                    Text("Create listing")
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Neighborhood momentum", style = MaterialTheme.typography.titleSmall)
                Text(
                    "$challengeScore / $challengeGoal trust points",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                LinearProgressIndicator(
                    progress = { challengeProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Fast responders: $fastResponderCount · Group-trusted: $groupTrustedCount · Local favorites: $localFavoriteCount",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Request quote from top 3 providers", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                OutlinedButton(
                    onClick = {
                        onRequestQuote(
                            quoteCategory,
                            quoteWindow,
                            quotePetDetails,
                            quoteNote,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading,
                ) {
                    Text("Send quote request")
                }
            }
        }

        if (viewMode == "list" && !filtersExpanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        filtersExpanded = true
                        coroutineScope.launch { listState.animateScrollToItem(0) }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Refine search")
                }
                CompactFilterDropdown(
                    modifier = Modifier.weight(1f),
                    selectedLabel = maxDistanceKm?.let { "$it km" } ?: "Any distance",
                    options = distanceOptions.map { option -> option to (option?.let { "$it km" } ?: "Any distance") },
                    onSelect = { option -> onFilterChange(minRating, option) },
                )
            }
        }

        AnimatedVisibility(visible = viewMode == "map" || filtersExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = viewMode == "list",
                        onClick = { onChangeViewMode("list") },
                        label = { Text("List") },
                        enabled = !loading,
                    )
                    FilterChip(
                        selected = viewMode == "map",
                        onClick = { onChangeViewMode("map") },
                        label = { Text("Map") },
                        enabled = !loading,
                    )
                    if (viewMode == "list") {
                        FilterChip(
                            selected = false,
                            onClick = { filtersExpanded = false },
                            label = { Text("Hide filters") },
                            enabled = !loading,
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    categories.forEach { (key, label) ->
                        FilterChip(
                            onClick = { onCategorySelect(key) },
                            label = { Text(label) },
                            selected = selectedCategory == key,
                            enabled = !loading,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilterDropdown(
                        modifier = Modifier.weight(1f),
                        label = "Rating",
                        selectedLabel = minRating?.let { "${it}+ rating" } ?: "Any rating",
                        options = ratingOptions.map { option -> option to (option?.let { "${it}+ rating" } ?: "Any rating") },
                        onSelect = { option -> onFilterChange(option, maxDistanceKm) },
                    )
                    FilterDropdown(
                        modifier = Modifier.weight(1f),
                        label = "Distance",
                        selectedLabel = maxDistanceKm?.let { "$it km" } ?: "Any distance",
                        options = distanceOptions.map { option -> option to (option?.let { "$it km" } ?: "Any distance") },
                        onSelect = { option -> onFilterChange(minRating, option) },
                    )
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    label = { Text("Search listings") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading,
                    singleLine = true,
                )

                FilterDropdown(
                    label = "Sort",
                    selectedLabel = sortOptions.firstOrNull { it.first == sortBy }?.second ?: "Best match",
                    options = sortOptions,
                    onSelect = onSortByChange,
                )

                if (activeFilterCount > 0) {
                    TextButton(
                        onClick = {
                            onSortByChange("relevance")
                            onFilterChange(null, null)
                        },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text("Clear $activeFilterCount active filter(s)")
                    }
                }
            }
        }

        if (viewMode == "map") {
            ServicesMapPanel(
                providers = providers,
                nearbyPetBusinesses = nearbyPetBusinesses,
                onViewDetails = onViewDetails,
            )
        } else {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 90.dp),
            ) {
                if (providers.isEmpty()) {
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
    }
}

@Composable
private fun ServicesMapPanel(
    providers: List<ServiceProvider>,
    nearbyPetBusinesses: List<NearbyPetBusiness>,
    onViewDetails: (String) -> Unit,
) {
    val usableProviders = providers.filter { provider ->
        provider.hasRenderableCoordinates()
    }
    val usableNearbyBusinesses = nearbyPetBusinesses.filter { place ->
        place.latitude.isFinite() &&
            place.longitude.isFinite() &&
            place.latitude in -90.0..90.0 &&
            place.longitude in -180.0..180.0
    }
    if (usableProviders.isEmpty() && usableNearbyBusinesses.isEmpty()) {
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
    LaunchedEffect(usableProviders, usableNearbyBusinesses) {
        val boundsBuilder = LatLngBounds.Builder()
        usableProviders.forEach { provider ->
            boundsBuilder.include(LatLng(provider.latitude, provider.longitude))
        }
        usableNearbyBusinesses.forEach { place ->
            boundsBuilder.include(LatLng(place.latitude, place.longitude))
        }
        val bounds = boundsBuilder.build()
        val update = if (usableProviders.size + usableNearbyBusinesses.size == 1) {
            val target = usableProviders.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
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
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
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
                    .clip(RoundedCornerShape(14.dp)),
                cameraPositionState = cameraPositionState,
            ) {
                usableProviders.forEach { provider ->
                    Marker(
                        state = MarkerState(position = LatLng(provider.latitude, provider.longitude)),
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
            usableProviders.take(8).forEach { provider ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                    TextButton(onClick = { onViewDetails(provider.id) }) { Text("Open") }
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
            OutlinedButton(
                onClick = { onViewDetails(provider.id) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("View details") }
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

private fun ServiceProvider.hasRenderableCoordinates(): Boolean {
    val lat = latitude
    val lng = longitude
    if (!lat.isFinite() || !lng.isFinite()) return false
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return false
    return lat != 0.0 || lng != 0.0
}

private fun String?.markerHue(): Float = when {
    this == null -> BitmapDescriptorFactory.HUE_YELLOW
    contains("veterinary", ignoreCase = true) -> BitmapDescriptorFactory.HUE_GREEN
    contains("groom", ignoreCase = true) -> BitmapDescriptorFactory.HUE_ORANGE
    contains("pet_store", ignoreCase = true) -> BitmapDescriptorFactory.HUE_ROSE
    else -> BitmapDescriptorFactory.HUE_YELLOW
}
