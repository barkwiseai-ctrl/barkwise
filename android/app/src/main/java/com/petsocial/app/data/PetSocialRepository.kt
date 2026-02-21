package com.petsocial.app.data

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import kotlin.coroutines.resume

class PetSocialRepository(
    private val api: ApiService,
    private val baseUrl: String,
    private val fallbackBaseUrl: String?,
    private val mapsApiKey: String,
    context: Context,
) {
    private var userId = "user_2"
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient.Builder().build()
    private val cachePrefs = context.applicationContext.getSharedPreferences(CACHE_PREFS_NAME, Context.MODE_PRIVATE)
    @Volatile private var authToken: String = cachePrefs.getString(AUTH_TOKEN_KEY, "").orEmpty()

    fun setActiveUser(userId: String) {
        this.userId = userId
    }

    fun activeUserId(): String = userId

    suspend fun loadProviders(
        category: String? = null,
        suburb: String? = null,
        userId: String? = null,
        includeInactive: Boolean = false,
        minRating: Double? = null,
        maxDistanceKm: Double? = null,
        userLat: Double? = null,
        userLng: Double? = null,
        query: String? = null,
        sortBy: String? = null,
    ): List<ServiceProvider> = api.getProviders(
        category = category,
        suburb = suburb,
        userId = userId,
        includeInactive = includeInactive,
        minRating = minRating,
        maxDistanceKm = maxDistanceKm,
        userLat = userLat,
        userLng = userLng,
        query = query,
        sortBy = sortBy,
    )

    suspend fun loadProviderDetails(providerId: String): ServiceProviderDetailsResponse = api.getProviderDetails(providerId)
    suspend fun loadProviderAvailability(providerId: String, date: String): List<ServiceAvailabilitySlot> =
        api.getProviderAvailability(providerId, date)

    suspend fun createServiceProvider(
        name: String,
        category: String,
        suburb: String,
        description: String,
        priceFrom: Int,
        fullDescription: String? = null,
        imageUrls: List<String> = emptyList(),
        latitude: Double? = null,
        longitude: Double? = null,
    ): ServiceProvider = createServiceProviderWithFallback(
        CreateServiceProviderRequest(
            userId = userId,
            name = name,
            category = category,
            suburb = suburb,
            description = description,
            priceFrom = priceFrom,
            fullDescription = fullDescription,
            imageUrls = imageUrls,
            latitude = latitude,
            longitude = longitude,
        ),
    )

    private suspend fun createServiceProviderWithFallback(payload: CreateServiceProviderRequest): ServiceProvider {
        return runCatching { api.createProvider(payload) }
            .recoverCatching { error ->
                val retryableHttp = error as? HttpException
                if (retryableHttp == null || (retryableHttp.code() != 404 && retryableHttp.code() != 405)) {
                    throw error
                }
                postServiceProviderViaFallbackPath(payload)
            }
            .getOrThrow()
    }

    private suspend fun postServiceProviderViaFallbackPath(payload: CreateServiceProviderRequest): ServiceProvider =
        withContext(Dispatchers.IO) {
            val mediaType = "application/json".toMediaType()
            val candidatePaths = listOf(
                "services/providers",
                "services/providers/",
                "services/provider",
                "services/provider/",
                "services/providers/create",
                "services/provider/create",
                "providers",
                "providers/",
                "api/services/providers",
                "api/services/providers/",
                "api/services/provider",
                "api/services/provider/",
                "api/services/providers/create",
                "api/services/provider/create",
            )
            val candidateMethods = listOf("POST", "PUT")
            val baseCandidates = resolveBaseCandidates()
            var lastError: Throwable? = null

            for (base in baseCandidates) {
                val baseHttpUrl = base.toHttpUrlOrNull() ?: continue
                for (path in candidatePaths) {
                    for (method in candidateMethods) {
                        val url = baseHttpUrl.newBuilder()
                            .encodedPath("/")
                            .apply {
                                path.split("/")
                                    .filter { it.isNotBlank() }
                                    .forEach { addPathSegment(it) }
                            }
                            .build()
                        val requestBody = json.encodeToString(payload).toRequestBody(mediaType)
                        val requestBuilder = Request.Builder().url(url)
                        when (method) {
                            "POST" -> requestBuilder.post(requestBody)
                            "PUT" -> requestBuilder.put(requestBody)
                            else -> requestBuilder.post(requestBody)
                        }
                        if (authToken.isNotBlank()) {
                            requestBuilder.header("Authorization", "Bearer $authToken")
                        }
                        val request = requestBuilder.build()
                        val response = try {
                            httpClient.newCall(request).execute()
                        } catch (error: Exception) {
                            lastError = error
                            continue
                        }

                        var tryNextPath = false
                        response.use { raw ->
                            if (raw.isSuccessful) {
                                val rawBody = raw.body?.string().orEmpty()
                                if (rawBody.isBlank()) error("Empty response from ${url.encodedPath}")
                                return@withContext json.decodeFromString<ServiceProvider>(rawBody)
                            }
                            if (raw.code == 404 || raw.code == 405) {
                                lastError = IllegalStateException("HTTP ${raw.code} ($method) at ${url.encodedPath}")
                                tryNextPath = true
                                return@use
                            }
                            error("Create service failed (${raw.code}) at ${url.encodedPath}")
                        }
                        if (tryNextPath) {
                            continue
                        }
                    }
                }
            }
            throw (lastError ?: IllegalStateException("Service create endpoint unavailable"))
        }

    suspend fun updateServiceProvider(
        providerId: String,
        name: String? = null,
        suburb: String? = null,
        description: String? = null,
        priceFrom: Int? = null,
        fullDescription: String? = null,
        imageUrls: List<String>? = null,
        latitude: Double? = null,
        longitude: Double? = null,
    ): ServiceProvider = api.updateProvider(
        providerId = providerId,
        payload = UpdateServiceProviderRequest(
            userId = userId,
            name = name,
            suburb = suburb,
            description = description,
            priceFrom = priceFrom,
            fullDescription = fullDescription,
            imageUrls = imageUrls,
            latitude = latitude,
            longitude = longitude,
        ),
    )

    suspend fun cancelServiceProvider(providerId: String): Boolean = runCatching {
        api.cancelProvider(
            providerId = providerId,
            payload = CancelServiceProviderRequest(userId = userId),
        )
        true
    }.getOrElse { false }

    suspend fun restoreServiceProvider(providerId: String): ServiceProvider = api.restoreProvider(
        providerId = providerId,
        payload = RestoreServiceProviderRequest(userId = userId),
    )

    suspend fun loadGroups(suburb: String?): List<Group> = api.getGroups(suburb = suburb, userId = userId)

    suspend fun loadPosts(
        suburb: String?,
        query: String? = null,
        sortBy: String? = null,
    ): List<CommunityPost> = api.getPosts(
        suburb = suburb,
        userId = userId,
        query = query,
        sortBy = sortBy,
    )

    suspend fun loadEvents(suburb: String?): List<CommunityEvent> = api.getEvents(
        suburb = suburb,
        userId = userId,
    )

    suspend fun createCommunityGroup(name: String, suburb: String): Group = api.createGroup(
        GroupCreateRequest(
            userId = userId,
            name = name,
            suburb = suburb,
        )
    )

    suspend fun applyJoinGroup(groupId: String): Group = api.joinGroup(groupId, GroupJoinRequest(userId = userId))

    suspend fun createGroupInvite(groupId: String): GroupInvite = api.createGroupInvite(
        GroupInviteCreateRequest(
            groupId = groupId,
            inviterUserId = userId,
        ),
    )

    suspend fun resolveGroupInvite(token: String): GroupInvite = api.resolveGroupInvite(token)

    suspend fun completeGroupOnboarding(
        inviteToken: String,
        ownerName: String,
        dogName: String,
        suburb: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        sharePhotoToGroup: Boolean = true,
        photoSource: String? = null,
    ): GroupOnboardingCompleteResponse = api.completeGroupOnboarding(
        GroupOnboardingCompleteRequest(
            inviteToken = inviteToken,
            ownerName = ownerName,
            dogName = dogName,
            suburb = suburb,
            latitude = latitude,
            longitude = longitude,
            sharePhotoToGroup = sharePhotoToGroup,
            photoSource = photoSource,
        ),
    )

    suspend fun loadPendingJoinRequests(groupId: String): List<GroupJoinRequestView> = api.getGroupJoinRequests(
        groupId = groupId,
        requesterUserId = userId,
    )

    suspend fun approveJoinRequest(groupId: String, memberUserId: String): Group = api.moderateGroupJoinRequest(
        groupId = groupId,
        payload = GroupJoinModerationRequest(
            requesterUserId = userId,
            memberUserId = memberUserId,
            action = "approve",
        ),
    )

    suspend fun rejectJoinRequest(groupId: String, memberUserId: String): Group = api.moderateGroupJoinRequest(
        groupId = groupId,
        payload = GroupJoinModerationRequest(
            requesterUserId = userId,
            memberUserId = memberUserId,
            action = "reject",
        ),
    )

    suspend fun sendChat(message: String, suburb: String?): ChatResponse = api.chat(
        ChatRequest(
            userId = userId,
            message = message,
            suburb = suburb,
        )
    )

    suspend fun streamChat(
        message: String,
        suburb: String?,
        onDelta: (String) -> Unit,
    ): ChatResponse = withContext(Dispatchers.IO) {
        val payload = ChatRequest(userId = userId, message = message, suburb = suburb)
        val streamPrimaryBaseUrl = normalizeBaseUrl(baseUrl) ?: DEFAULT_API_BASE_URL
        val streamFallbackBaseUrl = normalizeBaseUrl(fallbackBaseUrl)
        val streamFallbackEnabled =
            streamFallbackBaseUrl != null && streamFallbackBaseUrl != streamPrimaryBaseUrl

        fun readStream(streamBaseUrl: String): ChatResponse {
            val body = json.encodeToString(payload).toRequestBody("application/json".toMediaType())
            val streamUrl = streamBaseUrl.toHttpUrlOrNull()
                ?.newBuilder()
                ?.addPathSegment("chat")
                ?.addPathSegment("stream")
                ?.build()
                ?: error("Invalid stream base URL: '$streamBaseUrl'")

            val request = Request.Builder()
                .url(streamUrl)
                .post(body)
                .build()

            return httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Stream request failed: ${response.code}")
                }

                val responseBody = response.body ?: error("Empty stream response")
                var finalResponse: ChatResponse? = null

                responseBody.source().use { source ->
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: continue
                        if (!line.startsWith("data: ")) continue

                        val data = line.removePrefix("data: ").trim()
                        if (data == "[DONE]") break

                        val event = json.parseToJsonElement(data).jsonObject
                        when (event["type"]?.jsonPrimitive?.contentOrNull) {
                            "delta" -> {
                                val delta = event["delta"]?.jsonPrimitive?.contentOrNull.orEmpty()
                                onDelta(delta)
                            }

                            "final" -> {
                                val finalElement = event["response"] ?: continue
                                finalResponse = json.decodeFromJsonElement<ChatResponse>(finalElement)
                            }
                        }
                    }
                }

                finalResponse ?: error("No final response from stream")
            }
        }

        return@withContext runCatching {
            readStream(streamPrimaryBaseUrl)
        }.recoverCatching { error ->
            if (streamFallbackEnabled && error is IOException) {
                readStream(streamFallbackBaseUrl!!)
            } else {
                throw error
            }
        }.getOrElse {
            // Fallback keeps BarkAI usable in dev/mock mode and when stream endpoint is unavailable.
            val fallback = api.chat(payload)
            onDelta(fallback.answer)
            fallback
        }
    }

    private fun normalizeBaseUrl(candidate: String?): String? {
        val cleaned = candidate
            ?.trim()
            ?.trim('"')
            ?.takeIf { it.isNotBlank() }
            ?.let { value -> if (value.endsWith("/")) value else "$value/" }
        return if (cleaned?.toHttpUrlOrNull() != null) cleaned else null
    }

    private fun resolveBaseCandidates(): List<String> {
        val primary = normalizeBaseUrl(baseUrl) ?: DEFAULT_API_BASE_URL
        val fallback = normalizeBaseUrl(fallbackBaseUrl)
        return listOfNotNull(primary, fallback).distinct()
    }

    suspend fun acceptProfileCard(): ChatResponse = api.acceptProfile(ProfileActionRequest(userId = userId))

    suspend fun submitProviderListing(): ChatResponse = api.submitProvider(ProfileActionRequest(userId = userId))

    suspend fun requestBooking(
        providerId: String,
        date: String,
        timeSlot: String,
        note: String,
    ): BookingResponse = api.createBooking(
        BookingRequest(
            userId = userId,
            providerId = providerId,
            petName = "My Pet",
            date = date,
            timeSlot = timeSlot,
            note = note,
        )
    )

    suspend fun requestServiceQuote(
        category: String,
        suburb: String,
        preferredWindow: String,
        petDetails: String,
        note: String,
    ): ServiceQuoteRequestView = api.requestQuote(
        ServiceQuoteRequestCreate(
            userId = userId,
            category = category,
            suburb = suburb,
            preferredWindow = preferredWindow,
            petDetails = petDetails,
            note = note,
        ),
    )

    suspend fun respondServiceQuote(
        quoteRequestId: String,
        providerId: String,
        decision: String,
        message: String = "",
    ): ServiceQuoteRequestView = api.respondQuoteRequest(
        quoteRequestId = quoteRequestId,
        payload = ServiceQuoteProviderResponseRequest(
            actorUserId = userId,
            providerId = providerId,
            decision = decision,
            message = message,
        ),
    )

    suspend fun createBookingHold(
        providerId: String,
        date: String,
        timeSlot: String,
    ): BookingHoldResponse = api.createBookingHold(
        BookingHoldRequest(
            userId = userId,
            providerId = providerId,
            date = date,
            timeSlot = timeSlot,
        )
    )

    suspend fun loadOwnerBookings(): List<BookingResponse> = api.getBookings(userId = userId, role = "owner")

    suspend fun loadProviderBookings(): List<BookingResponse> = api.getBookings(userId = userId, role = "provider")

    suspend fun cancelOwnerBooking(bookingId: String): BookingResponse = api.updateBookingStatus(
        bookingId = bookingId,
        payload = BookingStatusUpdateRequest(
            actorUserId = userId,
            status = "cancelled_by_owner",
            note = "Cancelled by owner",
        ),
    )

    suspend fun cancelProviderBooking(bookingId: String): BookingResponse = api.updateBookingStatus(
        bookingId = bookingId,
        payload = BookingStatusUpdateRequest(
            actorUserId = userId,
            status = "cancelled_by_provider",
            note = "Cancelled by provider",
        ),
    )

    suspend fun confirmProviderBooking(bookingId: String): BookingResponse = api.updateBookingStatus(
        bookingId = bookingId,
        payload = BookingStatusUpdateRequest(
            actorUserId = userId,
            status = "provider_confirmed",
            note = "Confirmed by provider",
        ),
    )

    suspend fun loadCalendarEvents(
        dateFrom: String = LocalDate.now().minusDays(3).toString(),
        dateTo: String = LocalDate.now().plusDays(30).toString(),
        role: String = "all",
    ): List<CalendarEvent> = api.getCalendarEvents(
        userId = userId,
        dateFrom = dateFrom,
        dateTo = dateTo,
        role = role,
    )

    suspend fun createLostFoundPost(title: String, body: String, suburb: String): CommunityPost =
        createCommunityPostWithFallback(
            CommunityPostCreate(
                type = "lost_found",
                title = title,
                body = body,
                suburb = suburb,
            ),
        )

    suspend fun createCommunityGroupPost(title: String, body: String, suburb: String): CommunityPost =
        createCommunityPostWithFallback(
            CommunityPostCreate(
                type = "group_post",
                title = title,
                body = body,
                suburb = suburb,
            ),
        )

    private suspend fun createCommunityPostWithFallback(payload: CommunityPostCreate): CommunityPost {
        return runCatching { api.createPost(payload) }
            .recoverCatching { error ->
                val retryableHttp = error as? HttpException
                if (retryableHttp == null || (retryableHttp.code() != 404 && retryableHttp.code() != 405)) {
                    throw error
                }
                postCommunityViaFallbackPath(payload)
            }
            .getOrThrow()
    }

    private suspend fun postCommunityViaFallbackPath(payload: CommunityPostCreate): CommunityPost =
        withContext(Dispatchers.IO) {
            val mediaType = "application/json".toMediaType()
            val requestBody = json.encodeToString(payload).toRequestBody(mediaType)
            val candidatePaths = listOf(
                "community/posts",
                "community/posts/",
                "posts",
                "posts/",
                "api/community/posts",
                "api/community/posts/",
            )
            val baseCandidates = resolveBaseCandidates()
            var lastError: Throwable? = null

            for (base in baseCandidates) {
                val baseHttpUrl = base.toHttpUrlOrNull() ?: continue
                for (path in candidatePaths) {
                    val url = baseHttpUrl.newBuilder()
                        .encodedPath("/")
                        .apply {
                            path.split("/")
                                .filter { it.isNotBlank() }
                                .forEach { addPathSegment(it) }
                        }
                        .build()
                    val request = Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .apply {
                            if (authToken.isNotBlank()) {
                                header("Authorization", "Bearer $authToken")
                            }
                        }
                        .build()

                    val response = try {
                        httpClient.newCall(request).execute()
                    } catch (error: Exception) {
                        lastError = error
                        continue
                    }

                    var tryNextPath = false
                    response.use { raw ->
                        if (raw.isSuccessful) {
                            val rawBody = raw.body?.string().orEmpty()
                            if (rawBody.isBlank()) error("Empty response from ${url.encodedPath}")
                            return@withContext json.decodeFromString<CommunityPost>(rawBody)
                        }
                        if (raw.code == 404 || raw.code == 405) {
                            lastError = IllegalStateException("HTTP ${raw.code} at ${url.encodedPath}")
                            tryNextPath = true
                            return@use
                        }
                        error("Community post failed (${raw.code}) at ${url.encodedPath}")
                    }
                    if (tryNextPath) {
                        continue
                    }
                }
            }
            throw (lastError ?: IllegalStateException("Community post endpoint unavailable"))
        }

    suspend fun createCommunityEvent(
        title: String,
        description: String,
        suburb: String,
        date: String,
        groupId: String? = null,
    ): CommunityEvent = createCommunityEventWithFallback(
        CommunityEventCreateRequest(
            userId = userId,
            title = title,
            description = description,
            suburb = suburb,
            date = date,
            groupId = groupId,
        ),
    )

    private suspend fun createCommunityEventWithFallback(payload: CommunityEventCreateRequest): CommunityEvent {
        return runCatching { api.createEvent(payload) }
            .recoverCatching { error ->
                val retryableHttp = error as? HttpException
                if (retryableHttp == null || (retryableHttp.code() != 404 && retryableHttp.code() != 405)) {
                    throw error
                }
                postCommunityEventViaFallbackPath(payload)
            }
            .getOrThrow()
    }

    private suspend fun postCommunityEventViaFallbackPath(payload: CommunityEventCreateRequest): CommunityEvent =
        withContext(Dispatchers.IO) {
            val mediaType = "application/json".toMediaType()
            val requestBody = json.encodeToString(payload).toRequestBody(mediaType)
            val candidatePaths = listOf(
                "community/events",
                "community/events/",
                "events",
                "events/",
                "api/community/events",
                "api/community/events/",
            )
            val baseCandidates = resolveBaseCandidates()
            var lastError: Throwable? = null

            for (base in baseCandidates) {
                val baseHttpUrl = base.toHttpUrlOrNull() ?: continue
                for (path in candidatePaths) {
                    val url = baseHttpUrl.newBuilder()
                        .encodedPath("/")
                        .apply {
                            path.split("/")
                                .filter { it.isNotBlank() }
                                .forEach { addPathSegment(it) }
                        }
                        .build()
                    val request = Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .apply {
                            if (authToken.isNotBlank()) {
                                header("Authorization", "Bearer $authToken")
                            }
                        }
                        .build()
                    val response = try {
                        httpClient.newCall(request).execute()
                    } catch (error: Exception) {
                        lastError = error
                        continue
                    }

                    var tryNextPath = false
                    response.use { raw ->
                        if (raw.isSuccessful) {
                            val rawBody = raw.body?.string().orEmpty()
                            if (rawBody.isBlank()) error("Empty response from ${url.encodedPath}")
                            return@withContext json.decodeFromString<CommunityEvent>(rawBody)
                        }
                        if (raw.code == 404 || raw.code == 405) {
                            lastError = IllegalStateException("HTTP ${raw.code} at ${url.encodedPath}")
                            tryNextPath = true
                            return@use
                        }
                        error("Community event failed (${raw.code}) at ${url.encodedPath}")
                    }
                    if (tryNextPath) {
                        continue
                    }
                }
            }
            throw (lastError ?: IllegalStateException("Community event endpoint unavailable"))
        }

    suspend fun rsvpCommunityEvent(eventId: String, attending: Boolean): CommunityEvent = api.rsvpEvent(
        eventId = eventId,
        payload = CommunityEventRsvpRequest(
            userId = userId,
            status = if (attending) "attending" else "none",
        ),
    )

    suspend fun approveCommunityEvent(eventId: String): CommunityEvent = api.approveEvent(
        eventId = eventId,
        requesterUserId = userId,
    )

    suspend fun authenticateAsUser(userId: String, password: String = "petsocial-demo"): Boolean = runCatching {
        val response = api.login(AuthLoginRequest(userId = userId, password = password))
        authToken = response.accessToken
        cachePrefs.edit().putString(AUTH_TOKEN_KEY, authToken).apply()
        true
    }.getOrElse { false }

    suspend fun loadNotifications(unreadOnly: Boolean = false): List<AppNotification> =
        api.getNotifications(userId = userId, unreadOnly = unreadOnly)

    suspend fun markNotificationRead(notificationId: String): AppNotification =
        api.markNotificationRead(notificationId = notificationId, userId = userId)

    suspend fun registerDeviceToken(deviceToken: String): Boolean = runCatching {
        api.registerDevice(DeviceTokenRegisterRequest(userId = userId, deviceToken = deviceToken))
        true
    }.getOrElse { false }

    suspend fun syncDevicePushToken(): Boolean {
        val token = fetchFirebaseToken() ?: return false
        return registerDeviceToken(token)
    }

    suspend fun loadNearbyPetBusinesses(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = 3000,
    ): List<NearbyPetBusiness> = withContext(Dispatchers.IO) {
        if (!latitude.isFinite() || !longitude.isFinite()) return@withContext emptyList()
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return@withContext emptyList()
        if (mapsApiKey.isBlank()) return@withContext emptyList()

        val types = listOf(
            "pet_store",
            "veterinary_care",
            "pet_groomer",
            "dog_trainer",
            "pet_boarding_service",
        )
        val url = buildString {
            append("https://maps.googleapis.com/maps/api/place/nearbysearch/json")
            append("?location=$latitude,$longitude")
            append("&radius=${radiusMeters.coerceIn(500, 50000)}")
            append("&type=")
            append(types.joinToString("|"))
            append("&key=$mapsApiKey")
        }

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val bodyText = response.body?.string().orEmpty()
                if (bodyText.isBlank()) return@use emptyList()

                val root = json.parseToJsonElement(bodyText).jsonObject
                val status = root["status"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (status != "OK" && status != "ZERO_RESULTS") return@use emptyList()

                val results = root["results"] as? JsonArray ?: return@use emptyList()
                results.mapNotNull { item ->
                    val obj = item.jsonObject
                    val geometry = obj["geometry"] as? JsonObject ?: return@mapNotNull null
                    val location = geometry["location"] as? JsonObject ?: return@mapNotNull null
                    val lat = location["lat"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                    val lng = location["lng"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                    if (!lat.isFinite() || !lng.isFinite()) return@mapNotNull null
                    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return@mapNotNull null

                    val typesArray = obj["types"] as? JsonArray
                    NearbyPetBusiness(
                        placeId = obj["place_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                        name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                        latitude = lat,
                        longitude = lng,
                        vicinity = obj["vicinity"]?.jsonPrimitive?.contentOrNull,
                        primaryType = typesArray
                            ?.firstOrNull()
                            ?.jsonPrimitive
                            ?.contentOrNull,
                        rating = obj["rating"]?.jsonPrimitive?.doubleOrNull,
                        userRatingsTotal = obj["user_ratings_total"]?.jsonPrimitive?.intOrNull,
                        openNow = obj["opening_hours"]
                            ?.let { it as? JsonObject }
                            ?.get("open_now")
                            ?.jsonPrimitive
                            ?.contentOrNull
                            ?.toBooleanStrictOrNull(),
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    fun saveHomeCache(snapshot: HomeCacheSnapshot) {
        val encoded = runCatching { json.encodeToString(snapshot) }.getOrNull() ?: return
        cachePrefs.edit()
            .putString(cacheKeyForUser(userId), encoded)
            .apply()
    }

    fun loadHomeCache(): HomeCacheSnapshot? {
        val raw = cachePrefs.getString(cacheKeyForUser(userId), null) ?: return null
        return runCatching { json.decodeFromString<HomeCacheSnapshot>(raw) }.getOrNull()
    }

    private fun cacheKeyForUser(userId: String): String = "home_snapshot_$userId"

    private companion object {
        const val CACHE_PREFS_NAME = "petsocial_cache"
        const val AUTH_TOKEN_KEY = "auth_token"
        const val DEFAULT_API_BASE_URL = "https://api.barkwise.app/"
    }

    fun currentAuthToken(): String = authToken

    private suspend fun fetchFirebaseToken(): String? = suspendCancellableCoroutine { cont ->
        runCatching {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    if (cont.isActive) cont.resume(token?.trim().takeUnless { it.isNullOrBlank() })
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume(null)
                }
        }.onFailure {
            if (cont.isActive) cont.resume(null)
        }
    }
}
