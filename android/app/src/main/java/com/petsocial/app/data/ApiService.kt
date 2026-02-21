package com.petsocial.app.data

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException

interface ApiService {
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
        @Query("user_id") userId: String? = null,
        @Query("q") query: String? = null,
        @Query("sort_by") sortBy: String? = null,
    ): List<CommunityPost>

    @POST("community/posts")
    suspend fun createPost(@Body payload: CommunityPostCreate): CommunityPost

    @GET("community/events")
    suspend fun getEvents(
        @Query("suburb") suburb: String? = null,
        @Query("user_id") userId: String? = null,
    ): List<CommunityEvent>

    @POST("community/events")
    suspend fun createEvent(@Body payload: CommunityEventCreateRequest): CommunityEvent

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
        private const val DEFAULT_API_BASE_URL = "https://api.barkwise.app/"

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
            val client = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(failoverInterceptor)
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
