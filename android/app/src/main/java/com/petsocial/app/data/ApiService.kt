package com.petsocial.app.data

import android.util.Log
import com.petsocial.app.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.PUT
import retrofit2.http.Query
import java.io.IOException
import java.util.Locale

interface ApiService {
    @GET("services/recommendations")
    suspend fun getRecommendations(
        @Query("user_id") userId: String? = null,
        @Query("category") category: String? = null,
        @Query("suburb") suburb: String? = null,
        @Query("min_rating") minRating: Double? = null,
        @Query("max_distance_km") maxDistanceKm: Double? = null,
        @Query("user_lat") userLat: Double? = null,
        @Query("user_lng") userLng: Double? = null,
    ): ServiceRecommendationsResponse

    @GET("services/providers")
    suspend fun getProviders(
        @Query("category") category: String? = null,
        @Query("suburb") suburb: String? = null,
        @Query("user_id") userId: String? = null,
        @Query("include_inactive") includeInactive: Boolean = false,
        @Query("min_rating") minRating: Double? = null,
        @Query("max_distance_km") maxDistanceKm: Double? = null,
        @Query("user_lat") userLat: Double? = null,
        @Query("user_lng") userLng: Double? = null,
        @Query("q") query: String? = null,
        @Query("sort_by") sortBy: String? = null,
    ): List<ServiceProvider>

    @GET("services/providers/{providerId}")
    suspend fun getProviderDetails(@Path("providerId") providerId: String): ServiceProviderDetailsResponse

    @POST("services/providers")
    suspend fun createProvider(@Body payload: CreateServiceProviderRequest): ServiceProvider

    @POST("services/providers/{providerId}/update")
    suspend fun updateProvider(
        @Path("providerId") providerId: String,
        @Body payload: UpdateServiceProviderRequest,
    ): ServiceProvider

    @POST("services/providers/{providerId}/cancel")
    suspend fun cancelProvider(
        @Path("providerId") providerId: String,
        @Body payload: CancelServiceProviderRequest,
    ): Map<String, String>

    @POST("services/providers/{providerId}/restore")
    suspend fun restoreProvider(
        @Path("providerId") providerId: String,
        @Body payload: RestoreServiceProviderRequest,
    ): ServiceProvider

    @POST("services/quotes/request")
    suspend fun requestQuote(@Body payload: ServiceQuoteRequestCreate): ServiceQuoteRequestView

    @POST("services/quotes/{quoteRequestId}/respond")
    suspend fun respondQuoteRequest(
        @Path("quoteRequestId") quoteRequestId: String,
        @Body payload: ServiceQuoteProviderResponseRequest,
    ): ServiceQuoteRequestView

    @POST("services/quotes/{quoteRequestId}/offer")
    suspend fun createQuoteOffer(
        @Path("quoteRequestId") quoteRequestId: String,
        @Body payload: ServiceQuoteOfferCreateRequest,
    ): ServiceQuoteOffer

    @GET("services/provider/inbox")
    suspend fun getProviderInbox(
        @Query("actor_user_id") actorUserId: String,
        @Query("include_resolved") includeResolved: Boolean = false,
        @Query("limit") limit: Int = 50,
    ): ProviderInboxResponse

    @GET("services/vet-coach/profile")
    suspend fun getVetCoachProfile(@Query("user_id") userId: String): VetCoachProfile

    @POST("services/vet-coach/sessions")
    suspend fun submitVetCoachSession(@Body payload: VetCoachSessionRequest): VetCoachSessionResult

    @POST("services/vet-coach/spotlight/activate")
    suspend fun activateVetSpotlight(@Body payload: VetSpotlightActivateRequest): VetSpotlightActivationResult

    @POST("services/providers/{providerId}/vet-verify")
    suspend fun verifyGroomerByVet(
        @Path("providerId") providerId: String,
        @Body payload: VetGroomerVerificationRequest,
    ): VetGroomerVerificationResult

    @GET("services/providers/{providerId}/availability")
    suspend fun getProviderAvailability(
        @Path("providerId") providerId: String,
        @Query("date") date: String,
    ): List<ServiceAvailabilitySlot>

    @POST("services/bookings")
    suspend fun createBooking(@Body payload: BookingRequest): BookingResponse

    @POST("services/bookings/holds")
    suspend fun createBookingHold(@Body payload: BookingHoldRequest): BookingHoldResponse

    @POST("services/bookings/{bookingId}/status")
    suspend fun updateBookingStatus(
        @Path("bookingId") bookingId: String,
        @Body payload: BookingStatusUpdateRequest,
    ): BookingResponse

    @GET("services/bookings")
    suspend fun getBookings(
        @Query("user_id") userId: String? = null,
        @Query("role") role: String? = null,
    ): List<BookingResponse>

    @GET("services/bookings/{bookingId}/history")
    suspend fun getBookingStatusHistory(
        @Path("bookingId") bookingId: String,
        @Query("requester_user_id") requesterUserId: String,
    ): List<BookingStatusHistoryEntry>

    @GET("services/calendar/events")
    suspend fun getCalendarEvents(
        @Query("user_id") userId: String,
        @Query("date_from") dateFrom: String,
        @Query("date_to") dateTo: String,
        @Query("role") role: String = "all",
    ): List<CalendarEvent>

    @POST("services/providers/{providerId}/blackouts")
    suspend fun createProviderBlackout(
        @Path("providerId") providerId: String,
        @Body payload: ProviderBlackoutRequest,
    ): ProviderBlackout

    @GET("services/providers/{providerId}/blackouts")
    suspend fun getProviderBlackouts(@Path("providerId") providerId: String): List<ProviderBlackout>

    @POST("chat")
    suspend fun chat(@Body payload: ChatRequest): ChatResponse

    @POST("chat/profile/accept")
    suspend fun acceptProfile(@Body payload: ProfileActionRequest): ChatResponse

    @POST("chat/provider/submit")
    suspend fun submitProvider(@Body payload: ProfileActionRequest): ChatResponse

    @GET("community/groups")
    suspend fun getGroups(
        @Query("suburb") suburb: String? = null,
        @Query("user_id") userId: String? = null,
    ): List<Group>

    @POST("community/groups")
    suspend fun createGroup(@Body payload: GroupCreateRequest): Group

    @POST("community/groups/{groupId}/join")
    suspend fun joinGroup(@Path("groupId") groupId: String, @Body payload: GroupJoinRequest): Group

    @GET("community/groups/{groupId}/challenges")
    suspend fun getGroupChallenges(
        @Path("groupId") groupId: String,
        @Query("user_id") userId: String? = null,
    ): List<GroupChallengeView>

    @POST("community/groups/{groupId}/challenges/participate")
    suspend fun participateGroupChallenge(
        @Path("groupId") groupId: String,
        @Body payload: GroupChallengeParticipationRequest,
    ): GroupChallengeParticipationResult

    @POST("community/invites")
    suspend fun createGroupInvite(@Body payload: GroupInviteCreateRequest): GroupInvite

    @GET("community/invites/{token}")
    suspend fun resolveGroupInvite(@Path("token") token: String): GroupInvite

    @POST("community/onboarding/complete")
    suspend fun completeGroupOnboarding(@Body payload: GroupOnboardingCompleteRequest): GroupOnboardingCompleteResponse

    @GET("community/groups/{groupId}/join-requests")
    suspend fun getGroupJoinRequests(
        @Path("groupId") groupId: String,
        @Query("requester_user_id") requesterUserId: String,
    ): List<GroupJoinRequestView>

    @POST("community/groups/{groupId}/join-requests")
    suspend fun moderateGroupJoinRequest(
        @Path("groupId") groupId: String,
        @Body payload: GroupJoinModerationRequest,
    ): Group

    @GET("community/posts")
    suspend fun getPosts(
        @Query("suburb") suburb: String? = null,
        @Query("post_type") postType: String? = null,
        @Query("user_id") userId: String? = null,
        @Query("q") query: String? = null,
        @Query("sort_by") sortBy: String? = null,
        @Query("alert_type") alertType: String? = null,
        @Query("alert_status") alertStatus: String? = null,
        @Query("open_only") openOnly: Boolean? = null,
        @Query("recent_hours") recentHours: Int? = null,
        @Query("center_lat") centerLat: Double? = null,
        @Query("center_lng") centerLng: Double? = null,
        @Query("max_distance_km") maxDistanceKm: Double? = null,
    ): List<CommunityPost>

    @POST("community/posts")
    suspend fun createPost(@Body payload: CommunityPostCreate): CommunityPost

    @GET("community/posts/{postId}/comments")
    suspend fun getPostComments(
        @Path("postId") postId: String,
        @Query("user_id") userId: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("include_removed") includeRemoved: Boolean = false,
    ): List<CommunityComment>

    @POST("community/posts/{postId}/comments")
    suspend fun createPostComment(
        @Path("postId") postId: String,
        @Body payload: CommunityCommentCreateRequest,
    ): CommunityComment

    @POST("community/comments/{commentId}/moderate")
    suspend fun moderatePostComment(
        @Path("commentId") commentId: String,
        @Body payload: CommunityCommentModerationRequest,
    ): CommunityComment

    @POST("community/posts/{postId}/resolve")
    suspend fun resolvePost(
        @Path("postId") postId: String,
        @Body payload: CommunityPostResolveRequest,
    ): CommunityPost

    @PATCH("community/posts/{postId}")
    suspend fun updatePost(
        @Path("postId") postId: String,
        @Body payload: CommunityPostUpdateRequest,
    ): CommunityPost

    @DELETE("community/posts/{postId}")
    suspend fun deletePost(
        @Path("postId") postId: String,
        @Query("requester_user_id") requesterUserId: String,
    ): Map<String, String>

    @POST("community/posts/uploads")
    suspend fun uploadCommunityPostPhoto(
        @Body payload: CommunityPostPhotoUploadRequest,
    ): CommunityPostPhotoUploadResponse

    @POST("community/moderation/reports")
    suspend fun createModerationReport(@Body payload: CommunityReportCreateRequest): CommunityReport

    @GET("community/moderation/reports")
    suspend fun getModerationReports(
        @Query("requester_user_id") requesterUserId: String,
        @Query("include_resolved") includeResolved: Boolean = false,
    ): List<CommunityReport>

    @POST("community/moderation/reports/{reportId}/resolve")
    suspend fun resolveModerationReport(
        @Path("reportId") reportId: String,
        @Body payload: CommunityReportResolveRequest,
    ): CommunityReport

    @POST("community/moderation/blocks")
    suspend fun blockUser(@Body payload: CommunityBlockUserRequest): CommunityBlockUserResponse

    @DELETE("community/moderation/blocks")
    suspend fun unblockUser(
        @Query("requester_user_id") requesterUserId: String,
        @Query("target_user_id") targetUserId: String,
    ): CommunityBlockUserResponse

    @GET("community/moderation/blocks")
    suspend fun getBlockedUsers(@Query("requester_user_id") requesterUserId: String): CommunityBlockUserResponse

    @POST("community/analytics/events")
    suspend fun createCommunityAnalyticsEvent(@Body payload: CommunityAnalyticsEventCreateRequest): Map<String, String>

    @GET("community/analytics/funnel")
    suspend fun getCommunityFunnel(
        @Query("requester_user_id") requesterUserId: String? = null,
        @Query("window_hours") windowHours: Int = 168,
    ): CommunityFunnelMetrics

    @GET("community/analytics/activation")
    suspend fun getCommunityActivationFunnel(
        @Query("requester_user_id") requesterUserId: String? = null,
        @Query("window_hours") windowHours: Int = 72,
    ): CommunityActivationFunnel

    @POST("community/diagnostics/events")
    suspend fun createCommunityDiagnosticEvent(@Body payload: CommunityDiagnosticEventCreateRequest): Map<String, String>

    @GET("community/events")
    suspend fun getEvents(
        @Query("suburb") suburb: String? = null,
        @Query("user_id") userId: String? = null,
    ): List<CommunityEvent>

    @POST("community/events")
    suspend fun createEvent(@Body payload: CommunityEventCreateRequest): CommunityEvent

    @PUT("community/events/{eventId}")
    suspend fun updateEvent(
        @Path("eventId") eventId: String,
        @Body payload: CommunityEventUpdateRequest,
    ): CommunityEvent

    @POST("community/events/{eventId}/rsvp")
    suspend fun rsvpEvent(
        @Path("eventId") eventId: String,
        @Body payload: CommunityEventRsvpRequest,
    ): CommunityEvent

    @POST("community/events/{eventId}/approve")
    suspend fun approveEvent(
        @Path("eventId") eventId: String,
        @Query("requester_user_id") requesterUserId: String,
    ): CommunityEvent

    @POST("auth/login")
    suspend fun login(@Body payload: AuthLoginRequest): AuthLoginResponse

    @POST("auth/invite")
    suspend fun createAuthInvite(@Body payload: AuthInviteCreateRequest): AuthInviteResponse

    @POST("auth/otp/request")
    suspend fun requestOtp(@Body payload: AuthOtpRequest): AuthOtpRequestResponse

    @POST("auth/otp/verify")
    suspend fun verifyOtp(@Body payload: AuthOtpVerifyRequest): AuthOtpVerifyResponse

    @POST("auth/device/login")
    suspend fun trustedDeviceLogin(@Body payload: AuthTrustedDeviceLoginRequest): AuthLoginResponse

    @POST("auth/device/reset")
    suspend fun resetTrustedDevice(@Body payload: AuthTrustedDeviceResetRequest): AuthLogoutResponse

    @POST("auth/friend-qr")
    suspend fun issueFriendQr(): AuthFriendQrIssueResponse

    @POST("auth/friend-qr/verify")
    suspend fun verifyFriendQr(@Body payload: AuthFriendQrVerifyRequest): AuthFriendQrVerifyResponse

    @POST("auth/logout")
    suspend fun logout(): AuthLogoutResponse

    @DELETE("auth/me")
    suspend fun deleteAccount(@Query("user_id") userId: String): AuthDeleteResponse

    @GET("auth/profile")
    suspend fun getUserProfile(@Query("user_id") userId: String): UserProfileResponse

    @PUT("auth/profile")
    suspend fun upsertUserProfile(@Body payload: UserProfileUpsertRequest): UserProfileResponse

    @GET("messages/threads")
    suspend fun getMessageThreads(
        @Query("user_id") userId: String,
        @Query("limit") limit: Int = 50,
    ): List<ApiMessageThread>

    @GET("messages/threads/{threadId}")
    suspend fun getThreadMessages(
        @Path("threadId") threadId: String,
        @Query("user_id") userId: String,
        @Query("limit") limit: Int = 100,
    ): List<ApiDirectMessage>

    @POST("messages/threads/{threadId}/messages")
    suspend fun sendThreadMessage(
        @Path("threadId") threadId: String,
        @Body payload: MessageSendRequest,
    ): ApiDirectMessage

    @POST("messages/threads/{threadId}/read")
    suspend fun markThreadRead(
        @Path("threadId") threadId: String,
        @Body payload: MessageMarkReadRequest,
    ): Map<String, String>

    @GET("notifications")
    suspend fun getNotifications(
        @Query("user_id") userId: String,
        @Query("unread_only") unreadOnly: Boolean = false,
    ): List<AppNotification>

    @POST("notifications/register-device")
    suspend fun registerDevice(@Body payload: DeviceTokenRegisterRequest): Map<String, String>

    @POST("notifications/{notificationId}/read")
    suspend fun markNotificationRead(
        @Path("notificationId") notificationId: String,
        @Query("user_id") userId: String,
    ): AppNotification

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private const val DEFAULT_API_BASE_URL = "https://api.barkwiseai.com/"

        private fun normalizeBaseUrl(candidate: String?): String? {
            val cleaned = candidate
                ?.trim()
                ?.trim('"')
                ?.takeIf { it.isNotBlank() }
                ?.let { value -> if (value.endsWith("/")) value else "$value/" }
            return if (cleaned?.toHttpUrlOrNull() != null) cleaned else null
        }

        fun create(
            baseUrl: String,
            authTokenProvider: (() -> String?)? = null,
            fallbackBaseUrl: String? = null,
        ): ApiService {
            val resolvedFallbackBaseUrl = normalizeBaseUrl(fallbackBaseUrl)
            val resolvedBaseUrl = normalizeBaseUrl(baseUrl)
                ?: resolvedFallbackBaseUrl
                ?: DEFAULT_API_BASE_URL
            val primaryUrl = resolvedBaseUrl.toHttpUrlOrNull()
            val fallbackUrl = resolvedFallbackBaseUrl?.toHttpUrlOrNull()
            val authInterceptor = Interceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                val token = authTokenProvider?.invoke().orEmpty().trim()
                if (token.isNotBlank()) {
                    requestBuilder.header("Authorization", "Bearer $token")
                }
                chain.proceed(requestBuilder.build())
            }
            val failoverInterceptor = Interceptor { chain ->
                val request = chain.request()
                try {
                    chain.proceed(request)
                } catch (error: IOException) {
                    val primary = primaryUrl
                    val fallback = fallbackUrl
                    if (primary == null || fallback == null || request.url.host != primary.host) {
                        throw error
                    }
                    val fallbackRequest = request.newBuilder()
                        .url(
                            request.url.newBuilder()
                                .scheme(fallback.scheme)
                                .host(fallback.host)
                                .port(fallback.port)
                                .build()
                        )
                        .build()
                    chain.proceed(fallbackRequest)
                }
            }
            val metricsInterceptor = Interceptor { chain ->
                val request = chain.request()
                val startedAtNs = System.nanoTime()
                try {
                    val response = chain.proceed(request)
                    if (BuildConfig.DEBUG) {
                        val durationMs = (System.nanoTime() - startedAtNs) / 1_000_000.0
                        val sizeBytes = response.body?.contentLength()?.takeIf { it >= 0 } ?: -1L
                        Log.d(
                            "BarkWiseApi",
                            "${request.method} ${request.url.encodedPath} -> ${response.code} in " +
                                String.format(Locale.US, "%.1f", durationMs) + "ms size=$sizeBytes",
                        )
                    }
                    response
                } catch (error: IOException) {
                    if (BuildConfig.DEBUG) {
                        val durationMs = (System.nanoTime() - startedAtNs) / 1_000_000.0
                        Log.w(
                            "BarkWiseApi",
                            "${request.method} ${request.url.encodedPath} failed in " +
                                String.format(Locale.US, "%.1f", durationMs) + "ms: ${error.message}",
                        )
                    }
                    throw error
                }
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(failoverInterceptor)
                .addInterceptor(metricsInterceptor)
                .build()
            val retrofit = Retrofit.Builder()
                .baseUrl(resolvedBaseUrl)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            return retrofit.create(ApiService::class.java)
        }
    }
}
