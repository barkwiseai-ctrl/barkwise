package com.petsocial.app.data

import android.content.Context
import android.provider.Settings
import com.google.firebase.messaging.FirebaseMessaging
import com.petsocial.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.decodeFromString
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
    private val appContext = context.applicationContext
    private val cachePrefs = appContext.getSharedPreferences(CACHE_PREFS_NAME, Context.MODE_PRIVATE)
    private var userId: String = cachePrefs.getString(ACTIVE_USER_ID_KEY, "user_2").orEmpty().ifBlank { "user_2" }
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient.Builder().build()
    @Volatile private var authToken: String = cachePrefs.getString(AUTH_TOKEN_KEY, "").orEmpty()

    fun setActiveUser(userId: String) {
        val normalized = userId.trim().ifBlank { "user_2" }
        this.userId = normalized
        cachePrefs.edit().putString(ACTIVE_USER_ID_KEY, normalized).apply()
    }

    fun activeUserId(): String = userId

    fun testProfileMode(): String {
        val defaultMode = TEST_PROFILE_MODE_READY
        val raw = cachePrefs.getString(TEST_PROFILE_MODE_KEY, defaultMode)
            .orEmpty()
            .trim()
            .lowercase()
        return when (raw) {
            TEST_PROFILE_MODE_ONBOARDING -> TEST_PROFILE_MODE_ONBOARDING
            else -> TEST_PROFILE_MODE_READY
        }
    }

    fun setTestProfileMode(mode: String) {
        val normalized = when (mode.trim().lowercase()) {
            TEST_PROFILE_MODE_ONBOARDING -> TEST_PROFILE_MODE_ONBOARDING
            else -> TEST_PROFILE_MODE_READY
        }
        cachePrefs.edit().putString(TEST_PROFILE_MODE_KEY, normalized).apply()
    }

    fun isTestProfileHeaderVisible(): Boolean {
        val raw = cachePrefs.getString(TEST_PROFILE_HEADER_MODE_KEY, TEST_PROFILE_HEADER_MODE_HIDDEN)
            .orEmpty()
            .trim()
            .lowercase()
        return raw == TEST_PROFILE_HEADER_MODE_VISIBLE
    }

    fun setTestProfileHeaderVisible(visible: Boolean) {
        val mode = if (visible) TEST_PROFILE_HEADER_MODE_VISIBLE else TEST_PROFILE_HEADER_MODE_HIDDEN
        cachePrefs.edit().putString(TEST_PROFILE_HEADER_MODE_KEY, mode).apply()
    }

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

    suspend fun loadRecommendedProviders(
        category: String? = null,
        suburb: String? = null,
        minRating: Double? = null,
        maxDistanceKm: Double? = null,
        userLat: Double? = null,
        userLng: Double? = null,
    ): ServiceRecommendationsResponse = api.getRecommendations(
        userId = userId,
        category = category,
        suburb = suburb,
        minRating = minRating,
        maxDistanceKm = maxDistanceKm,
        userLat = userLat,
        userLng = userLng,
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
    ): ServiceProvider = api.createProvider(
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
        postType: String? = null,
        query: String? = null,
        sortBy: String? = null,
        alertType: String? = null,
        alertStatus: String? = null,
        openOnly: Boolean? = null,
        recentHours: Int? = null,
        centerLat: Double? = null,
        centerLng: Double? = null,
        maxDistanceKm: Double? = null,
    ): List<CommunityPost> = api.getPosts(
        suburb = suburb,
        postType = postType,
        userId = userId,
        query = query,
        sortBy = sortBy,
        alertType = alertType,
        alertStatus = alertStatus,
        openOnly = openOnly,
        recentHours = recentHours,
        centerLat = centerLat,
        centerLng = centerLng,
        maxDistanceKm = maxDistanceKm,
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

    suspend fun loadGroupChallenges(groupId: String): List<GroupChallengeView> = api.getGroupChallenges(
        groupId = groupId,
        userId = userId,
    )

    suspend fun participateGroupChallenge(
        groupId: String,
        challengeType: String,
        contributionCount: Int = 1,
        note: String = "",
    ): GroupChallengeParticipationResult = api.participateGroupChallenge(
        groupId = groupId,
        payload = GroupChallengeParticipationRequest(
            userId = userId,
            challengeType = challengeType,
            contributionCount = contributionCount,
            note = note,
        ),
    )

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

    fun loadBarkAiConversation(targetUserId: String = userId): List<ChatTurn> {
        val raw = cachePrefs.getString(barkAiConversationKeyForUser(targetUserId), null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<ChatTurn>>(raw) }.getOrDefault(emptyList())
    }

    fun saveBarkAiConversation(conversation: List<ChatTurn>, targetUserId: String = userId) {
        val normalizedUserId = targetUserId.trim().ifBlank { userId }
        val encoded = json.encodeToString(conversation)
        cachePrefs.edit().putString(barkAiConversationKeyForUser(normalizedUserId), encoded).apply()
    }

    suspend fun sendChat(messages: List<ChatTurn>): ChatResponse = api.chat(
        ChatRequest(
            userId = userId,
            messages = messages
                .takeLast(20)
                .map { ChatMessage(role = it.role, content = it.content) },
        )
    )

    suspend fun streamChat(
        messages: List<ChatTurn>,
        onDelta: (String) -> Unit,
    ): ChatResponse = withContext(Dispatchers.IO) {
        val payload = ChatRequest(
            userId = userId,
            messages = messages
                .takeLast(20)
                .map { ChatMessage(role = it.role, content = it.content) },
        )
        val streamPrimaryBaseUrl = normalizeBaseUrl(baseUrl) ?: DEFAULT_API_BASE_URL

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
                .apply {
                    if (authToken.isNotBlank()) {
                        header("Authorization", "Bearer $authToken")
                    }
                }
                .build()

            return httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Stream request failed: ${response.code}")
                }

                val responseBody = response.body ?: error("Empty stream response")
                var finalResponse: ChatResponse? = null
                var streamError: String? = null
                var streamErrorType: String? = null

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

                            "error" -> {
                                streamError = event["error"]?.jsonPrimitive?.contentOrNull ?: "BarkAI could not reply."
                                streamErrorType = event["error_type"]?.jsonPrimitive?.contentOrNull
                            }
                        }
                    }
                }

                if (!streamError.isNullOrBlank()) {
                    error(listOfNotNull(streamErrorType, streamError).joinToString(": "))
                }
                finalResponse ?: error("No final response from stream")
            }
        }

        return@withContext readStream(streamPrimaryBaseUrl)
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
        suburb: String? = null,
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

    suspend fun loadPostComments(
        postId: String,
        limit: Int = 50,
        offset: Int = 0,
        includeRemoved: Boolean = false,
    ): List<CommunityComment> = api.getPostComments(
        postId = postId,
        userId = userId,
        limit = limit,
        offset = offset,
        includeRemoved = includeRemoved,
    )

    suspend fun createPostComment(
        postId: String,
        body: String,
        parentCommentId: String? = null,
    ): CommunityComment = api.createPostComment(
        postId = postId,
        payload = CommunityCommentCreateRequest(
            userId = userId,
            body = body,
            parentCommentId = parentCommentId,
        ),
    )

    suspend fun moderatePostComment(
        commentId: String,
        action: String,
        note: String = "",
    ): CommunityComment = api.moderatePostComment(
        commentId = commentId,
        payload = CommunityCommentModerationRequest(
            requesterUserId = userId,
            action = action,
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

    suspend fun createServiceQuoteOffer(
        quoteRequestId: String,
        providerId: String,
        priceCents: Int,
        proposedDate: String,
        proposedTimeSlot: String,
        expiresAt: String,
        currency: String = "AUD",
        note: String = "",
    ): ServiceQuoteOffer = api.createQuoteOffer(
        quoteRequestId = quoteRequestId,
        payload = ServiceQuoteOfferCreateRequest(
            actorUserId = userId,
            providerId = providerId,
            priceCents = priceCents,
            currency = currency,
            proposedDate = proposedDate,
            proposedTimeSlot = proposedTimeSlot,
            expiresAt = expiresAt,
            note = note,
        ),
    )

    suspend fun loadProviderInbox(
        includeResolved: Boolean = false,
        limit: Int = 50,
    ): ProviderInboxResponse = api.getProviderInbox(
        actorUserId = userId,
        includeResolved = includeResolved,
        limit = limit,
    )

    suspend fun loadVetCoachProfile(): VetCoachProfile = api.getVetCoachProfile(userId = userId)

    suspend fun submitVetCoachSession(
        durationMinutes: Int,
        qualityScore: Double,
        topic: String = "",
        note: String = "",
    ): VetCoachSessionResult = api.submitVetCoachSession(
        VetCoachSessionRequest(
            actorUserId = userId,
            durationMinutes = durationMinutes,
            qualityScore = qualityScore,
            topic = topic,
            note = note,
        ),
    )

    suspend fun activateVetSpotlight(minutes: Int): VetSpotlightActivationResult = api.activateVetSpotlight(
        VetSpotlightActivateRequest(
            actorUserId = userId,
            minutes = minutes,
        ),
    )

    suspend fun verifyGroomerByVet(
        providerId: String,
        decision: String,
        confidenceScore: Double = 0.8,
        note: String = "",
    ): VetGroomerVerificationResult = api.verifyGroomerByVet(
        providerId = providerId,
        payload = VetGroomerVerificationRequest(
            actorUserId = userId,
            decision = decision,
            confidenceScore = confidenceScore,
            note = note,
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

    suspend fun loadBookingStatusHistory(bookingId: String): List<BookingStatusHistoryEntry> =
        api.getBookingStatusHistory(
            bookingId = bookingId,
            requesterUserId = userId,
        )

    suspend fun cancelOwnerBooking(bookingId: String): BookingResponse = api.updateBookingStatus(
        bookingId = bookingId,
        payload = BookingStatusUpdateRequest(
            actorUserId = userId,
            status = "cancelled_by_owner",
            note = "Cancelled by owner",
        ),
    )

    suspend fun requestOwnerBookingReschedule(
        bookingId: String,
        note: String = "",
    ): BookingResponse = api.updateBookingStatus(
        bookingId = bookingId,
        payload = BookingStatusUpdateRequest(
            actorUserId = userId,
            status = "reschedule_requested",
            note = note.ifBlank { "Owner requested a different time slot" },
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

    suspend fun rescheduleProviderBooking(
        bookingId: String,
        date: String,
        timeSlot: String,
        note: String = "",
    ): BookingResponse = api.updateBookingStatus(
        bookingId = bookingId,
        payload = BookingStatusUpdateRequest(
            actorUserId = userId,
            status = "rescheduled",
            note = note.ifBlank { "Rescheduled to $date $timeSlot" },
            date = date,
            timeSlot = timeSlot,
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

    suspend fun loadProviderBlackouts(providerId: String): List<ProviderBlackout> =
        api.getProviderBlackouts(providerId = providerId)

    suspend fun createProviderBlackout(
        providerId: String,
        date: String,
        timeSlot: String,
        reason: String = "",
    ): ProviderBlackout = api.createProviderBlackout(
        providerId = providerId,
        payload = ProviderBlackoutRequest(
            actorUserId = userId,
            date = date,
            timeSlot = timeSlot,
            reason = reason,
        ),
    )

    suspend fun createLostFoundPost(title: String, body: String, suburb: String): CommunityPost =
        createLostFoundPost(
            CommunityPostCreate(
                type = "lost_found",
                title = title,
                body = body,
                suburb = suburb,
            ),
        )

    suspend fun createLostFoundPost(payload: CommunityPostCreate): CommunityPost =
        createCommunityPostWithFallback(payload.copy(type = "lost_found", userId = payload.userId ?: userId))

    suspend fun createCommunityPost(payload: CommunityPostCreate): CommunityPost =
        createCommunityPostWithFallback(payload.copy(userId = payload.userId ?: userId))

    suspend fun createCommunityGroupPost(
        title: String,
        body: String,
        suburb: String,
        photoUrls: List<String> = emptyList(),
    ): CommunityPost =
        createCommunityPostWithFallback(
            CommunityPostCreate(
                type = "group_post",
                userId = userId,
                title = title,
                body = body,
                suburb = suburb,
                photoUrls = photoUrls,
            ),
        )

    suspend fun resolveLostFoundPost(postId: String, status: String, note: String = ""): CommunityPost =
        api.resolvePost(
            postId = postId,
            payload = CommunityPostResolveRequest(
                requesterUserId = userId,
                status = status,
                note = note,
            ),
        )

    suspend fun deleteCommunityPost(postId: String): Map<String, String> =
        api.deletePost(
            postId = postId,
            requesterUserId = userId,
        )

    suspend fun reportCommunityTarget(
        targetType: String,
        targetId: String,
        reason: String,
        details: String = "",
    ): CommunityReport =
        api.createModerationReport(
            CommunityReportCreateRequest(
                reporterUserId = userId,
                targetType = targetType,
                targetId = targetId,
                reason = reason,
                details = details,
            )
        )

    suspend fun reportCommunityPost(postId: String, reason: String, details: String = ""): CommunityReport =
        reportCommunityTarget(
            targetType = "post",
            targetId = postId,
            reason = reason,
            details = details,
        )

    suspend fun reportCommunityEvent(eventId: String, reason: String, details: String = ""): CommunityReport =
        reportCommunityTarget(
            targetType = "event",
            targetId = eventId,
            reason = reason,
            details = details,
        )

    suspend fun blockCommunityUser(targetUserId: String): CommunityBlockUserResponse =
        api.blockUser(
            CommunityBlockUserRequest(
                requesterUserId = userId,
                targetUserId = targetUserId,
            )
        )

    suspend fun unblockCommunityUser(targetUserId: String): CommunityBlockUserResponse =
        api.unblockUser(
            requesterUserId = userId,
            targetUserId = targetUserId,
        )

    suspend fun loadBlockedUsers(): CommunityBlockUserResponse = api.getBlockedUsers(requesterUserId = userId)

    suspend fun loadModerationReports(includeResolved: Boolean = false): List<CommunityReport> =
        api.getModerationReports(
            requesterUserId = userId,
            includeResolved = includeResolved,
        )

    suspend fun resolveModerationReport(reportId: String, action: String, note: String = ""): CommunityReport =
        api.resolveModerationReport(
            reportId = reportId,
            payload = CommunityReportResolveRequest(
                requesterUserId = userId,
                action = action,
                note = note,
            ),
        )

    suspend fun uploadLostFoundDemoPhoto(): CommunityPostPhotoUploadResponse = withContext(Dispatchers.IO) {
        val filename = "lost-found-${System.currentTimeMillis()}.png"
        val pixelPng = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x02, 0x00, 0x00, 0x00, 0x90.toByte(), 0x77, 0x53, 0xDE.toByte(),
            0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, 0x54, 0x08, 0xD7.toByte(),
            0x63, 0xF8.toByte(), 0xCF.toByte(), 0xC0.toByte(), 0x00, 0x00, 0x04, 0xBF.toByte(), 0x01, 0x7F,
            0xA7.toByte(), 0x89.toByte(), 0x81.toByte(), 0x2E,
            0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
        )
        api.uploadCommunityPostPhoto(
            CommunityPostPhotoUploadRequest(
                requesterUserId = userId,
                filename = filename,
                contentType = "image/png",
                dataBase64 = java.util.Base64.getEncoder().encodeToString(pixelPng),
            )
        )
    }

    suspend fun trackCommunityAnalytics(
        event: String,
        category: String = "community",
        metadata: Map<String, String> = emptyMap(),
        durationMs: Int? = null,
    ) {
        runCatching {
            api.createCommunityAnalyticsEvent(
                CommunityAnalyticsEventCreateRequest(
                    userId = userId,
                    event = event,
                    category = category,
                    metadata = metadata,
                    durationMs = durationMs,
                )
            )
        }
    }

    suspend fun loadCommunityFunnel(windowHours: Int = 168): CommunityFunnelMetrics =
        api.getCommunityFunnel(
            requesterUserId = userId,
            windowHours = windowHours,
        )

    suspend fun loadCommunityActivationFunnel(windowHours: Int = 72): CommunityActivationFunnel =
        api.getCommunityActivationFunnel(
            requesterUserId = userId,
            windowHours = windowHours,
        )

    suspend fun trackCommunityDiagnostic(
        kind: String,
        message: String,
        context: Map<String, String> = emptyMap(),
        durationMs: Int? = null,
    ) {
        runCatching {
            api.createCommunityDiagnosticEvent(
                CommunityDiagnosticEventCreateRequest(
                    userId = userId,
                    kind = kind,
                    message = message,
                    context = context,
                    durationMs = durationMs,
                )
            )
        }
    }

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
        locationName: String? = null,
        locationLatitude: Double? = null,
        locationLongitude: Double? = null,
        recurrence: String = "none",
        recurrenceInterval: Int = 1,
    ): CommunityEvent = createCommunityEventWithFallback(
        CommunityEventCreateRequest(
            userId = userId,
            title = title,
            description = description,
            suburb = suburb,
            date = date,
            groupId = groupId,
            locationName = locationName,
            locationLatitude = locationLatitude,
            locationLongitude = locationLongitude,
            recurrence = recurrence.lowercase().ifBlank { "none" },
            recurrenceInterval = recurrenceInterval.coerceIn(1, 30),
        ),
    )

    suspend fun updateCommunityEvent(
        eventId: String,
        title: String,
        description: String,
        date: String,
        groupId: String? = null,
        locationName: String? = null,
        locationLatitude: Double? = null,
        locationLongitude: Double? = null,
        clearLocation: Boolean = false,
        recurrence: String = "none",
        recurrenceInterval: Int = 1,
    ): CommunityEvent = api.updateEvent(
        eventId = eventId,
        payload = CommunityEventUpdateRequest(
            userId = userId,
            title = title,
            description = description,
            date = date,
            groupId = groupId,
            locationName = locationName,
            locationLatitude = locationLatitude,
            locationLongitude = locationLongitude,
            clearLocation = clearLocation,
            recurrence = recurrence.lowercase().ifBlank { "none" },
            recurrenceInterval = recurrenceInterval.coerceIn(1, 30),
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
        setActiveUser(response.userId)
        true
    }.getOrElse { false }

    suspend fun createAuthInvite(
        email: String,
        targetUserId: String? = null,
        ttlMinutes: Int = 60,
    ): AuthInviteResponse = api.createAuthInvite(
        AuthInviteCreateRequest(
            requesterUserId = userId,
            email = email,
            userId = targetUserId,
            ttlMinutes = ttlMinutes,
        ),
    )

    suspend fun requestOtp(inviteId: String, email: String): AuthOtpRequestResponse = api.requestOtp(
        AuthOtpRequest(
            inviteId = inviteId,
            email = email,
        ),
    )

    suspend fun verifyOtp(inviteId: String, email: String, otpCode: String): AuthOtpVerifyResponse {
        val response = api.verifyOtp(
            AuthOtpVerifyRequest(
                inviteId = inviteId,
                email = email,
                otpCode = otpCode,
                deviceId = currentDeviceId(),
            ),
        )
        authToken = response.accessToken
        cachePrefs.edit().putString(AUTH_TOKEN_KEY, authToken).apply()
        setActiveUser(response.userId)
        return response
    }

    suspend fun tryTrustedDeviceLogin(): AuthLoginResponse {
        val response = api.trustedDeviceLogin(
            AuthTrustedDeviceLoginRequest(deviceId = currentDeviceId()),
        )
        authToken = response.accessToken
        cachePrefs.edit().putString(AUTH_TOKEN_KEY, authToken).apply()
        setActiveUser(response.userId)
        return response
    }

    suspend fun issueFriendQr(): AuthFriendQrIssueResponse = api.issueFriendQr()

    suspend fun verifyFriendQr(friendToken: String): AuthFriendQrVerifyResponse = api.verifyFriendQr(
        AuthFriendQrVerifyRequest(friendToken = friendToken),
    )

    suspend fun logout(): Boolean = runCatching {
        api.logout()
        authToken = ""
        cachePrefs.edit().putString(AUTH_TOKEN_KEY, "").apply()
        true
    }.getOrElse { false }

    suspend fun resetTrustedDevice(): Boolean = runCatching {
        api.resetTrustedDevice(
            AuthTrustedDeviceResetRequest(deviceId = currentDeviceId()),
        )
        authToken = ""
        cachePrefs.edit().putString(AUTH_TOKEN_KEY, "").apply()
        true
    }.getOrElse { false }

    suspend fun deleteAccount(targetUserId: String = userId): AuthDeleteResponse {
        val response = api.deleteAccount(userId = targetUserId)
        if (targetUserId == userId) {
            authToken = ""
            cachePrefs.edit().putString(AUTH_TOKEN_KEY, "").apply()
        }
        return response
    }

    suspend fun loadUserProfile(): UserProfileResponse = api.getUserProfile(userId = userId)

    suspend fun saveUserProfile(
        displayName: String,
        email: String,
        phone: String,
        humanPronouns: String,
        humanRoleLabel: String,
        serviceProviderMode: Boolean,
        dogName: String,
        dogAgeMonths: Int,
        dogBreedMix: String,
        dogSexNeuter: String,
        dogWeightClass: String,
        dogPhotoUrls: List<String>,
        secondaryDogName: String,
        secondaryDogAgeMonths: Int,
        secondaryDogPhotoUrl: String,
        secondaryDogGender: String,
        secondaryDogWeightKg: String,
        bio: String,
        suburb: String,
        favoriteSuburbs: List<String>,
        playEnergyLevel: String,
        playStyle: String,
        socialConfidence: String,
        triggerNotes: String,
        idealMatch: String,
        walkPreferences: String,
        trainingStyle: String,
        feedingRules: String,
        consentBoundaries: String,
        vaccinationStatus: String,
        microchipped: Boolean,
        recallTrained: Boolean,
        leashReliability: String,
        emergencyContactName: String,
        emergencyContactPhone: String,
        fieldVisibility: Map<String, String>,
    ): UserProfileResponse = api.upsertUserProfile(
        UserProfileUpsertRequest(
            requesterUserId = userId,
            displayName = displayName,
            email = email,
            phone = phone,
            humanPronouns = humanPronouns,
            humanRoleLabel = humanRoleLabel,
            serviceProviderMode = serviceProviderMode,
            dogName = dogName,
            dogAgeMonths = dogAgeMonths,
            dogBreedMix = dogBreedMix,
            dogSexNeuter = dogSexNeuter,
            dogWeightClass = dogWeightClass,
            dogPhotoUrls = dogPhotoUrls,
            secondaryDogName = secondaryDogName,
            secondaryDogAgeMonths = secondaryDogAgeMonths,
            secondaryDogPhotoUrl = secondaryDogPhotoUrl,
            secondaryDogGender = secondaryDogGender,
            secondaryDogWeightKg = secondaryDogWeightKg,
            bio = bio,
            suburb = suburb,
            favoriteSuburbs = favoriteSuburbs,
            playEnergyLevel = playEnergyLevel,
            playStyle = playStyle,
            socialConfidence = socialConfidence,
            triggerNotes = triggerNotes,
            idealMatch = idealMatch,
            walkPreferences = walkPreferences,
            trainingStyle = trainingStyle,
            feedingRules = feedingRules,
            consentBoundaries = consentBoundaries,
            vaccinationStatus = vaccinationStatus,
            microchipped = microchipped,
            recallTrained = recallTrained,
            leashReliability = leashReliability,
            emergencyContactName = emergencyContactName,
            emergencyContactPhone = emergencyContactPhone,
            fieldVisibility = fieldVisibility,
        ),
    )

    suspend fun loadMessageThreads(limit: Int = 50): List<ApiMessageThread> =
        api.getMessageThreads(
            userId = userId,
            limit = limit,
        )

    suspend fun loadThreadMessages(threadId: String, limit: Int = 100): List<ApiDirectMessage> =
        api.getThreadMessages(
            threadId = threadId,
            userId = userId,
            limit = limit,
        )

    suspend fun sendThreadMessage(
        threadId: String,
        recipientUserId: String,
        body: String,
    ): ApiDirectMessage = api.sendThreadMessage(
        threadId = threadId,
        payload = MessageSendRequest(
            userId = userId,
            recipientUserId = recipientUserId,
            body = body,
        ),
    )

    suspend fun markThreadRead(threadId: String): Boolean = runCatching {
        api.markThreadRead(
            threadId = threadId,
            payload = MessageMarkReadRequest(userId = userId),
        )
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
        val discoverySlice = HomeDiscoveryCacheSlice(
            providers = snapshot.providers,
            ownerListingProviders = snapshot.ownerListingProviders,
            nearbyPetBusinesses = snapshot.nearbyPetBusinesses,
            groups = snapshot.groups,
            posts = snapshot.posts,
            events = snapshot.events,
        )
        val bookingsSlice = HomeBookingsCacheSlice(
            ownerBookings = snapshot.ownerBookings,
            providerBookings = snapshot.providerBookings,
        )
        val calendarSlice = HomeCalendarCacheSlice(
            calendarEvents = snapshot.calendarEvents,
        )
        val sessionSlice = HomeSessionCacheSlice(
            providerInboxItems = snapshot.providerInboxItems,
            messageThreads = snapshot.messageThreads,
            selectedMessageThreadId = snapshot.selectedMessageThreadId,
            selectedThreadMessages = snapshot.selectedThreadMessages,
            notifications = snapshot.notifications,
            profileInfo = snapshot.profileInfo,
            blockedUserIds = snapshot.blockedUserIds,
            moderationReports = snapshot.moderationReports,
        )
        val encodedDiscovery = runCatching { json.encodeToString(discoverySlice) }.getOrNull() ?: return
        val encodedBookings = runCatching { json.encodeToString(bookingsSlice) }.getOrNull() ?: return
        val encodedCalendar = runCatching { json.encodeToString(calendarSlice) }.getOrNull() ?: return
        val encodedSession = runCatching { json.encodeToString(sessionSlice) }.getOrNull() ?: return
        cachePrefs.edit()
            .putString(cacheKeyForUser(userId, HOME_CACHE_DISCOVERY_SUFFIX), encodedDiscovery)
            .putString(cacheKeyForUser(userId, HOME_CACHE_BOOKINGS_SUFFIX), encodedBookings)
            .putString(cacheKeyForUser(userId, HOME_CACHE_CALENDAR_SUFFIX), encodedCalendar)
            .putString(cacheKeyForUser(userId, HOME_CACHE_SESSION_SUFFIX), encodedSession)
            .remove(legacyHomeCacheKeyForUser(userId))
            .apply()
    }

    fun loadHomeCache(): HomeCacheSnapshot? {
        val discovery = cachePrefs.getString(cacheKeyForUser(userId, HOME_CACHE_DISCOVERY_SUFFIX), null)
            ?.let { raw -> runCatching { json.decodeFromString<HomeDiscoveryCacheSlice>(raw) }.getOrNull() }
        val bookings = cachePrefs.getString(cacheKeyForUser(userId, HOME_CACHE_BOOKINGS_SUFFIX), null)
            ?.let { raw -> runCatching { json.decodeFromString<HomeBookingsCacheSlice>(raw) }.getOrNull() }
        val calendar = cachePrefs.getString(cacheKeyForUser(userId, HOME_CACHE_CALENDAR_SUFFIX), null)
            ?.let { raw -> runCatching { json.decodeFromString<HomeCalendarCacheSlice>(raw) }.getOrNull() }
        val session = cachePrefs.getString(cacheKeyForUser(userId, HOME_CACHE_SESSION_SUFFIX), null)
            ?.let { raw -> runCatching { json.decodeFromString<HomeSessionCacheSlice>(raw) }.getOrNull() }
            ?: HomeSessionCacheSlice()
        if (discovery != null && bookings != null && calendar != null) {
            return HomeCacheSnapshot(
                providers = discovery.providers,
                ownerListingProviders = discovery.ownerListingProviders,
                nearbyPetBusinesses = discovery.nearbyPetBusinesses,
                groups = discovery.groups,
                posts = discovery.posts,
                events = discovery.events,
                ownerBookings = bookings.ownerBookings,
                providerBookings = bookings.providerBookings,
                calendarEvents = calendar.calendarEvents,
                providerInboxItems = session.providerInboxItems,
                messageThreads = session.messageThreads,
                selectedMessageThreadId = session.selectedMessageThreadId,
                selectedThreadMessages = session.selectedThreadMessages,
                notifications = session.notifications,
                profileInfo = session.profileInfo,
                blockedUserIds = session.blockedUserIds,
                moderationReports = session.moderationReports,
            )
        }
        val legacyRaw = cachePrefs.getString(legacyHomeCacheKeyForUser(userId), null) ?: return null
        val legacySnapshot = runCatching { json.decodeFromString<HomeCacheSnapshot>(legacyRaw) }.getOrNull() ?: return null
        saveHomeCache(legacySnapshot)
        return legacySnapshot
    }

    fun saveUserUiPrefs(snapshot: UserUiPrefsSnapshot) {
        val normalizedUserId = snapshot.userId.trim().ifBlank { userId }
        val encoded = runCatching {
            json.encodeToString(
                snapshot.copy(
                    userId = normalizedUserId,
                    favoriteProviderIds = snapshot.favoriteProviderIds.distinct(),
                    pendingLocalProviders = snapshot.pendingLocalProviders.distinctBy { provider -> provider.id },
                ),
            )
        }.getOrNull() ?: return
        cachePrefs.edit()
            .putString(userUiPrefsKeyForUser(normalizedUserId), encoded)
            .apply()
    }

    fun loadUserUiPrefs(targetUserId: String = userId): UserUiPrefsSnapshot {
        val normalizedUserId = targetUserId.trim().ifBlank { userId }
        val raw = cachePrefs.getString(userUiPrefsKeyForUser(normalizedUserId), null)
        val decoded = raw?.let { value ->
            runCatching { json.decodeFromString<UserUiPrefsSnapshot>(value) }.getOrNull()
        }
        return decoded?.copy(
            userId = normalizedUserId,
            favoriteProviderIds = decoded.favoriteProviderIds.distinct(),
            pendingLocalProviders = decoded.pendingLocalProviders.distinctBy { provider -> provider.id },
        ) ?: UserUiPrefsSnapshot(userId = normalizedUserId)
    }

    fun clearUserUiPrefs(targetUserId: String = userId) {
        val normalizedUserId = targetUserId.trim().ifBlank { userId }
        cachePrefs.edit()
            .remove(userUiPrefsKeyForUser(normalizedUserId))
            .apply()
    }

    private fun cacheKeyForUser(userId: String, suffix: String): String = "home_snapshot_${suffix}_$userId"
    private fun legacyHomeCacheKeyForUser(userId: String): String = "home_snapshot_$userId"
    private fun barkAiConversationKeyForUser(userId: String): String = "bark_ai_conversation_$userId"
    private fun userUiPrefsKeyForUser(userId: String): String = "user_ui_prefs_$userId"

    private companion object {
        const val CACHE_PREFS_NAME = "petsocial_cache"
        const val AUTH_TOKEN_KEY = "auth_token"
        const val ACTIVE_USER_ID_KEY = "active_user_id"
        const val TEST_PROFILE_MODE_KEY = "test_profile_mode"
        const val TEST_PROFILE_HEADER_MODE_KEY = "test_profile_header_mode"
        const val TEST_PROFILE_MODE_READY = "ready"
        const val TEST_PROFILE_MODE_ONBOARDING = "onboarding"
        const val TEST_PROFILE_HEADER_MODE_VISIBLE = "visible"
        const val TEST_PROFILE_HEADER_MODE_HIDDEN = "hidden"
        const val HOME_CACHE_DISCOVERY_SUFFIX = "discovery"
        const val HOME_CACHE_BOOKINGS_SUFFIX = "bookings"
        const val HOME_CACHE_CALENDAR_SUFFIX = "calendar"
        const val HOME_CACHE_SESSION_SUFFIX = "session"
        const val DEFAULT_API_BASE_URL = "https://api.barkwiseai.com/"
    }

    fun currentAuthToken(): String = authToken

    fun currentDeviceId(): String {
        return Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ANDROID_ID,
        )?.trim().orEmpty().ifBlank { "unknown-device" }
    }

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
