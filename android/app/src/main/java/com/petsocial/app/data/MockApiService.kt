package com.petsocial.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MockApiService private constructor() : ApiService {
    private val providers = mutableListOf(
        ServiceProvider(
            id = "provider_1",
            name = "Sesame Suds Studio",
            category = "grooming",
            suburb = "Surry Hills",
            rating = 4.9,
            reviewCount = 124,
            priceFrom = 62,
            description = "Full groom + de-shed packages for doodles and double coats.",
            fullDescription = "Mobile grooming van with calm, low-stress sessions and photo updates.",
            imageUrls = listOf(
                "https://loremflickr.com/640/640/bordoodle,dog?lock=101",
            ),
            latitude = -33.8842,
            longitude = 151.2106,
            ownerUserId = "user_1",
            ownerLabel = "Sesame",
            responseTimeMinutes = 18,
            localBookersThisMonth = 14,
            sharedGroupBookers = 5,
            socialProof = listOf(
                "Used by 14 pet owners in Surry Hills this month",
                "5 members from your groups booked this provider",
                "Typically responds in about 18 min",
            ),
        ),
        ServiceProvider(
            id = "provider_2",
            name = "Sesame Gentle Trim Co.",
            category = "grooming",
            suburb = "Darlinghurst",
            rating = 4.7,
            reviewCount = 88,
            priceFrom = 58,
            description = "Express tidy trims, wash and blow-dry.",
            fullDescription = "Fast turnarounds for regular coat maintenance and hygiene clips.",
            imageUrls = listOf(
                "https://loremflickr.com/640/640/doodle,dog,grooming?lock=102",
            ),
            latitude = -33.8777,
            longitude = 151.2219,
            ownerUserId = "user_1",
            ownerLabel = "Sesame",
            responseTimeMinutes = 31,
            localBookersThisMonth = 9,
            sharedGroupBookers = 3,
            socialProof = listOf(
                "Used by 9 pet owners in Darlinghurst this month",
                "3 members from your groups booked this provider",
                "Typically responds in about 31 min",
            ),
        ),
        ServiceProvider(
            id = "provider_3",
            name = "Tommy Tiny Paws Grooming",
            category = "grooming",
            suburb = "Redfern",
            rating = 4.8,
            reviewCount = 102,
            priceFrom = 67,
            description = "Toy-breed specialist grooming with coat-safe products.",
            fullDescription = "Gentle handling for small breeds, face tidy, nails, and paw care.",
            imageUrls = listOf(
                "https://loremflickr.com/640/640/brown,toy,dog,cavoodle?lock=103",
            ),
            latitude = -33.8935,
            longitude = 151.2048,
            ownerUserId = "user_4",
            ownerLabel = "Tommy",
            responseTimeMinutes = 22,
            localBookersThisMonth = 11,
            sharedGroupBookers = 4,
            socialProof = listOf(
                "Used by 11 pet owners in Redfern this month",
                "4 members from your groups booked this provider",
                "Typically responds in about 22 min",
            ),
        ),
        ServiceProvider(
            id = "provider_4",
            name = "Tommy Cocoa Coat Spa",
            category = "grooming",
            suburb = "Surry Hills",
            rating = 4.6,
            reviewCount = 71,
            priceFrom = 64,
            description = "Brown-coat brightening wash, conditioning, and tidy styling.",
            fullDescription = "Salon-style finish for toy cavoodles and poodle mixes.",
            imageUrls = listOf(
                "https://loremflickr.com/640/640/toy,cavoodle,dog?lock=104",
            ),
            latitude = -33.8860,
            longitude = 151.2101,
            ownerUserId = "user_4",
            ownerLabel = "Tommy",
            responseTimeMinutes = 44,
            localBookersThisMonth = 7,
            sharedGroupBookers = 2,
            socialProof = listOf(
                "Used by 7 pet owners in Surry Hills this month",
                "2 members from your groups booked this provider",
                "Typically responds in about 44 min",
            ),
        ),
    )
    private val quoteRequests = mutableMapOf<String, ServiceQuoteRequestView>()
    private val vetProfiles = mutableMapOf<String, VetCoachProfile>()
    private val providerVetVerifications = mutableMapOf<String, VetGroomerVerification>()
    private val groupBadges = mutableMapOf<String, MutableSet<String>>()
    private val groupMemberRewardPoints = mutableMapOf<Pair<String, String>, MutableMap<String, Int>>()
    private val groupChallengeContributions = mutableMapOf<Triple<String, String, String>, Int>()
    private val reviewsByProvider = mutableMapOf(
        "provider_1" to mutableListOf(
            Review("review_1", "provider_1", "Sam", 5, "Excellent groom. Coat came back fluffy and even."),
            Review("review_2", "provider_1", "June", 5, "Great updates and really gentle handling."),
        ),
        "provider_2" to mutableListOf(
            Review("review_3", "provider_2", "Mia", 4, "Good tidy trim and easy booking flow."),
        ),
        "provider_3" to mutableListOf(
            Review("review_4", "provider_3", "Harper", 5, "Perfect for small dogs, very patient staff."),
            Review("review_5", "provider_3", "Noah", 4, "Great cut and nails, pickup was on time."),
        ),
        "provider_4" to mutableListOf(
            Review("review_6", "provider_4", "Ava", 5, "My toy cavoodle looked amazing after the spa package."),
        ),
    )
    private val groupMembers = mutableMapOf(
        "group_1" to mutableSetOf("user_1", "user_2"),
        "group_2" to mutableSetOf("user_3"),
    )
    private val groupPendingMembers = mutableMapOf(
        "group_1" to mutableSetOf("user_4"),
        "group_2" to mutableSetOf<String>(),
    )
    private val groupInvites = mutableMapOf<String, GroupInvite>()
    private val groups = mutableListOf(
        Group(
            id = "group_1",
            name = "Surry Hills Dog Parents",
            suburb = "Surry Hills",
            memberCount = 2,
            official = true,
            ownerUserId = "user_1",
        ),
        Group(
            id = "group_2",
            name = "Redfern Weekend Walks",
            suburb = "Redfern",
            memberCount = 1,
            ownerUserId = "user_3",
        ),
    )
    private val posts = mutableListOf(
        CommunityPost(
            id = "post_1",
            type = "lost_found",
            title = "Found leash near Prince Alfred Park",
            body = "Blue leash dropped at the off-leash area. Message me to claim.",
            suburb = "Surry Hills",
            createdAt = Instant.now().minus(2, ChronoUnit.HOURS).toString(),
        ),
    )
    private val events = mutableListOf(
        CommunityEvent(
            id = "event_1",
            title = "Morning Social Walk",
            description = "Easy-paced dog walk and coffee meetup.",
            suburb = "Surry Hills",
            date = LocalDate.now().plusDays(2).toString(),
            groupId = "group_1",
            attendeeCount = 1,
            createdBy = "user_1",
            status = "approved",
        ),
    )
    private val eventAttendees = mutableMapOf(
        "event_1" to mutableSetOf("user_2"),
    )
    private val bookings = mutableListOf<BookingResponse>()
    private val providerBlackouts = mutableMapOf<String, MutableList<ProviderBlackout>>()
    private val conversationByUser = mutableMapOf<String, MutableList<ChatTurn>>()
    private val notifications = mutableListOf(
        AppNotification(
            id = "notif_1",
            userId = "user_2",
            title = "Booking updated",
            body = "Your groomer confirmed tomorrow's booking.",
            category = "booking",
            read = false,
            createdAt = Instant.now().minus(1, ChronoUnit.HOURS).toString(),
            deepLink = "messages",
        ),
    )
    private var bookingCounter = 1
    private var holdCounter = 1
    private var postCounter = 2
    private var eventCounter = 2
    private var groupCounter = 3
    private var blackoutCounter = 1
    private var quoteCounter = 1
    private var vetCoachSessionCounter = 1
    private var vetVerificationCounter = 1

    override suspend fun getProviders(
        category: String?,
        suburb: String?,
        userId: String?,
        includeInactive: Boolean,
        minRating: Double?,
        maxDistanceKm: Double?,
        userLat: Double?,
        userLng: Double?,
        query: String?,
        sortBy: String?,
    ): List<ServiceProvider> {
        val filtered = providers
            .asSequence()
            .filter {
                val isActive = it.status == "active"
                isActive || (includeInactive && !userId.isNullOrBlank() && it.ownerUserId == userId)
            }
            .filter { category.isNullOrBlank() || it.category == category }
            .filter { suburb.isNullOrBlank() || it.suburb.equals(suburb, ignoreCase = true) }
            .filter { minRating == null || it.rating >= minRating }
            .filter {
                query.isNullOrBlank() ||
                    it.name.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true)
            }
            .map { provider ->
                if (userLat != null && userLng != null) {
                    provider.copy(distanceKm = distanceKm(userLat, userLng, provider.latitude, provider.longitude))
                } else {
                    provider.copy(distanceKm = null)
                }
            }
            .filter { maxDistanceKm == null || (it.distanceKm ?: 0.0) <= maxDistanceKm }
            .toList()
        return when (sortBy) {
            "rating" -> filtered.sortedByDescending { it.rating }
            "price_low" -> filtered.sortedBy { it.priceFrom }
            "price_high" -> filtered.sortedByDescending { it.priceFrom }
            "distance" -> filtered.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
            else -> filtered
        }
    }

    override suspend fun getProviderDetails(providerId: String): ServiceProviderDetailsResponse {
        val provider = providers.firstOrNull { it.id == providerId } ?: error("Provider not found: $providerId")
        return ServiceProviderDetailsResponse(
            provider = provider,
            reviews = reviewsByProvider[providerId].orEmpty(),
        )
    }

    override suspend fun createProvider(payload: CreateServiceProviderRequest): ServiceProvider {
        val ownerLabel = when (payload.userId) {
            "user_1" -> "Sesame"
            "user_2" -> "Snowy"
            "user_3" -> "Anika"
            "user_4" -> "Tommy"
            else -> payload.userId
        }
        val provider = ServiceProvider(
            id = "provider_${providers.size + 1}",
            name = payload.name,
            category = payload.category,
            suburb = payload.suburb,
            rating = 5.0,
            reviewCount = 0,
            priceFrom = payload.priceFrom,
            description = payload.description,
            fullDescription = payload.fullDescription ?: payload.description,
            imageUrls = payload.imageUrls.takeIf { it.isNotEmpty() }
                ?: listOf("https://loremflickr.com/640/640/dog,pet?lock=${providers.size + 101}"),
            latitude = payload.latitude ?: -33.8889,
            longitude = payload.longitude ?: 151.2111,
            ownerUserId = payload.userId,
            ownerLabel = ownerLabel,
        )
        providers += provider
        reviewsByProvider[provider.id] = mutableListOf()
        return provider
    }

    override suspend fun updateProvider(
        providerId: String,
        payload: UpdateServiceProviderRequest,
    ): ServiceProvider {
        val index = providers.indexOfFirst { it.id == providerId }
        if (index < 0) error("Provider not found: $providerId")
        val existing = providers[index]
        if (existing.ownerUserId != payload.userId) {
            error("Only provider owner can edit listing")
        }
        val updated = existing.copy(
            name = payload.name?.takeIf { it.isNotBlank() } ?: existing.name,
            suburb = payload.suburb?.takeIf { it.isNotBlank() } ?: existing.suburb,
            description = payload.description?.takeIf { it.isNotBlank() } ?: existing.description,
            priceFrom = payload.priceFrom ?: existing.priceFrom,
            fullDescription = payload.fullDescription?.takeIf { it.isNotBlank() }
                ?: payload.description?.takeIf { it.isNotBlank() }
                ?: existing.fullDescription,
            imageUrls = payload.imageUrls
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.takeIf { it.isNotEmpty() }
                ?: existing.imageUrls,
            latitude = payload.latitude ?: existing.latitude,
            longitude = payload.longitude ?: existing.longitude,
            status = existing.status,
        )
        providers[index] = updated
        return updated
    }

    override suspend fun cancelProvider(
        providerId: String,
        payload: CancelServiceProviderRequest,
    ): Map<String, String> {
        val index = providers.indexOfFirst { it.id == providerId }
        if (index < 0) error("Provider not found: $providerId")
        if (providers[index].ownerUserId != payload.userId) {
            error("Only provider owner can cancel listing")
        }
        providers[index] = providers[index].copy(status = "cancelled")
        return mapOf("status" to "cancelled", "provider_id" to providerId)
    }

    override suspend fun restoreProvider(
        providerId: String,
        payload: RestoreServiceProviderRequest,
    ): ServiceProvider {
        val index = providers.indexOfFirst { it.id == providerId }
        if (index < 0) error("Provider not found: $providerId")
        if (providers[index].ownerUserId != payload.userId) {
            error("Only provider owner can restore listing")
        }
        providers[index] = providers[index].copy(status = "active")
        return providers[index]
    }

    override suspend fun requestQuote(payload: ServiceQuoteRequestCreate): ServiceQuoteRequestView {
        val preferredSuburbMatches = providers
            .asSequence()
            .filter { it.status == "active" }
            .filter { it.category == payload.category }
            .filter { it.ownerUserId != payload.userId }
            .filter { it.suburb.equals(payload.suburb, ignoreCase = true) }
            .toList()
        val fallbackMatches = providers
            .asSequence()
            .filter { it.status == "active" }
            .filter { it.category == payload.category }
            .filter { it.ownerUserId != payload.userId }
            .toList()
        val selected = (preferredSuburbMatches.ifEmpty { fallbackMatches }).take(3)
        if (selected.isEmpty()) error("No matching providers found")

        val now = Instant.now()
        val quoteId = "quote_${quoteCounter++}"
        val targets = selected.map { provider ->
            ServiceQuoteTarget(
                providerId = provider.id,
                providerName = provider.name,
                ownerUserId = provider.ownerUserId.orEmpty(),
                status = "pending",
                responseMessage = "",
                createdAt = now.toString(),
                respondedAt = null,
                reminder15Sent = false,
                reminder60Sent = false,
            )
        }
        val requestView = ServiceQuoteRequestView(
            quoteRequest = ServiceQuoteRequest(
                id = quoteId,
                userId = payload.userId,
                category = payload.category,
                suburb = payload.suburb,
                preferredWindow = payload.preferredWindow,
                petDetails = payload.petDetails,
                note = payload.note,
                status = "pending",
                createdAt = now.toString(),
                updatedAt = now.toString(),
            ),
            targets = targets,
        )
        quoteRequests[quoteId] = requestView
        targets.forEach { target ->
            notifications.add(
                0,
                AppNotification(
                    id = "notif_quote_${Instant.now().toEpochMilli()}_${target.providerId}",
                    userId = target.ownerUserId,
                    title = "New quote request",
                    body = "${payload.category.replace("_", " ")} in ${payload.suburb} (${payload.preferredWindow})",
                    category = "booking",
                    read = false,
                    createdAt = Instant.now().toString(),
                    deepLink = "quote:$quoteId",
                ),
            )
        }
        return requestView
    }

    override suspend fun respondQuoteRequest(
        quoteRequestId: String,
        payload: ServiceQuoteProviderResponseRequest,
    ): ServiceQuoteRequestView {
        val existing = quoteRequests[quoteRequestId] ?: error("Quote request not found")
        val target = existing.targets.firstOrNull { it.providerId == payload.providerId }
            ?: error("Quote target not found")
        if (target.ownerUserId != payload.actorUserId) error("Only listing owner can respond to this quote")
        if (target.status != "pending") error("Quote target already responded")

        val now = Instant.now()
        val updatedTarget = target.copy(
            status = payload.decision,
            responseMessage = payload.message,
            respondedAt = now.toString(),
        )
        val updatedTargets = existing.targets.map { row ->
            if (row.providerId == payload.providerId) updatedTarget else row
        }
        val nextStatus = when {
            updatedTargets.all { it.status == "declined" } -> "closed"
            updatedTargets.any { it.status == "accepted" || it.status == "declined" } -> "responded"
            else -> "pending"
        }
        val updatedView = ServiceQuoteRequestView(
            quoteRequest = existing.quoteRequest.copy(
                status = nextStatus,
                updatedAt = now.toString(),
            ),
            targets = updatedTargets,
        )
        quoteRequests[quoteRequestId] = updatedView

        val elapsedMinutes = runCatching {
            val created = Instant.parse(existing.quoteRequest.createdAt)
            val delta = ChronoUnit.MINUTES.between(created, now).toInt()
            if (delta < 1) 1 else delta
        }.getOrDefault(1)
        val providerIndex = providers.indexOfFirst { it.id == payload.providerId }
        if (providerIndex >= 0) {
            val current = providers[providerIndex]
            val sprintStats = computeQuoteSprintStats(providerId = payload.providerId)
            providers[providerIndex] = current.copy(
                responseTimeMinutes = elapsedMinutes,
                quoteResponseRatePct = sprintStats.responseRatePct,
                quoteResponseStreak = sprintStats.responseStreak,
                quoteSprintTier = sprintStats.tier,
                socialProof = buildSocialProof(
                    suburb = current.suburb,
                    localBookers = current.localBookersThisMonth,
                    sharedGroupBookers = current.sharedGroupBookers,
                    responseTimeMinutes = elapsedMinutes,
                    quoteSprintTier = sprintStats.tier,
                    quoteResponseRatePct = sprintStats.responseRatePct,
                    quoteResponseStreak = sprintStats.responseStreak,
                    vetChecked = current.vetChecked,
                    vetCheckedUntil = current.vetCheckedUntil,
                    highlightedVetUntil = current.highlightedVetUntil,
                ),
            )
        }
        notifications.add(
            0,
            AppNotification(
                id = "notif_quote_resp_${Instant.now().toEpochMilli()}",
                userId = existing.quoteRequest.userId,
                title = "Quote response received",
                body = "A provider ${payload.decision} your quote request in ${existing.quoteRequest.suburb}.",
                category = "booking",
                read = false,
                createdAt = now.toString(),
                deepLink = "quote:${existing.quoteRequest.id}",
            ),
        )
        return updatedView
    }

    override suspend fun getVetCoachProfile(userId: String): VetCoachProfile {
        if (!isVetUser(userId)) error("Only verified vets can access coach profile")
        return ensureVetProfile(userId)
    }

    override suspend fun submitVetCoachSession(payload: VetCoachSessionRequest): VetCoachSessionResult {
        if (!isVetUser(payload.actorUserId)) error("Only verified vets can submit coach sessions")
        val existing = ensureVetProfile(payload.actorUserId)
        val minutesEarned = maxOf(1, ((payload.durationMinutes * (0.6 + payload.qualityScore))).toInt())
        val updated = existing.copy(
            spotlightMinutes = existing.spotlightMinutes + minutesEarned,
            coachingMinutes = existing.coachingMinutes + payload.durationMinutes,
            coachingSessions = existing.coachingSessions + 1,
            coachQualityScore = if (existing.coachingSessions <= 0) {
                payload.qualityScore
            } else {
                ((existing.coachQualityScore * existing.coachingSessions) + payload.qualityScore) /
                    (existing.coachingSessions + 1)
            },
            badgeTier = resolveVetBadgeTier(
                sessions = existing.coachingSessions + 1,
                qualityScore = if (existing.coachingSessions <= 0) {
                    payload.qualityScore
                } else {
                    ((existing.coachQualityScore * existing.coachingSessions) + payload.qualityScore) /
                        (existing.coachingSessions + 1)
                },
            ),
        )
        vetProfiles[payload.actorUserId] = updated
        return VetCoachSessionResult(
            sessionId = "mock_vcs_${vetCoachSessionCounter++}",
            minutesEarned = minutesEarned,
            profile = updated,
        )
    }

    override suspend fun activateVetSpotlight(payload: VetSpotlightActivateRequest): VetSpotlightActivationResult {
        if (!isVetUser(payload.actorUserId)) error("Only verified vets can activate spotlight")
        val existing = ensureVetProfile(payload.actorUserId)
        if (existing.spotlightMinutes < payload.minutes) error("Insufficient spotlight minutes")
        val now = Instant.now()
        val currentUntil = existing.highlightedUntil
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?.takeIf { it.isAfter(now) }
            ?: now
        val nextUntil = currentUntil.plus(payload.minutes.toLong(), ChronoUnit.MINUTES)
        val updated = existing.copy(
            spotlightMinutes = existing.spotlightMinutes - payload.minutes,
            highlightedUntil = nextUntil.toString(),
        )
        vetProfiles[payload.actorUserId] = updated
        providers.replaceAll { provider ->
            if (provider.ownerUserId == payload.actorUserId) {
                provider.copy(
                    highlightedVet = provider.ownerLabel ?: payload.actorUserId,
                    highlightedVetUntil = nextUntil.toString(),
                    socialProof = buildSocialProof(
                        suburb = provider.suburb,
                        localBookers = provider.localBookersThisMonth,
                        sharedGroupBookers = provider.sharedGroupBookers,
                        responseTimeMinutes = provider.responseTimeMinutes,
                        quoteSprintTier = provider.quoteSprintTier,
                        quoteResponseRatePct = provider.quoteResponseRatePct,
                        quoteResponseStreak = provider.quoteResponseStreak,
                        vetChecked = provider.vetChecked,
                        vetCheckedUntil = provider.vetCheckedUntil,
                        highlightedVetUntil = nextUntil.toString(),
                    ),
                )
            } else {
                provider
            }
        }
        return VetSpotlightActivationResult(
            minutesSpent = payload.minutes,
            profile = updated,
        )
    }

    override suspend fun verifyGroomerByVet(
        providerId: String,
        payload: VetGroomerVerificationRequest,
    ): VetGroomerVerificationResult {
        if (!isVetUser(payload.actorUserId)) error("Only verified vets can review groomers")
        val providerIndex = providers.indexOfFirst { it.id == providerId }
        if (providerIndex < 0) error("Provider not found")
        val provider = providers[providerIndex]
        if (provider.category != "grooming") error("Vet verification is only available for grooming providers")
        if (provider.ownerUserId == payload.actorUserId) error("Vets cannot verify their own listing")

        val now = Instant.now()
        val validUntil = if (payload.decision == "approved") now.plus(90, ChronoUnit.DAYS).toString() else null
        val spotlightMinutesEarned = if (payload.decision == "approved") {
            12 + (payload.confidenceScore * 8).toInt()
        } else {
            4 + (payload.confidenceScore * 4).toInt()
        }
        val verification = VetGroomerVerification(
            id = "mock_vver_${vetVerificationCounter++}",
            providerId = providerId,
            vetUserId = payload.actorUserId,
            decision = payload.decision,
            confidenceScore = payload.confidenceScore,
            note = payload.note,
            createdAt = now.toString(),
            validUntil = validUntil,
            spotlightMinutesEarned = spotlightMinutesEarned,
        )
        providerVetVerifications[providerId] = verification

        val profile = ensureVetProfile(payload.actorUserId)
        val updatedProfile = profile.copy(spotlightMinutes = profile.spotlightMinutes + spotlightMinutesEarned)
        vetProfiles[payload.actorUserId] = updatedProfile

        val updatedProvider = provider.copy(
            vetChecked = payload.decision == "approved",
            vetCheckedUntil = validUntil,
            vetCheckedBy = payload.actorUserId,
            socialProof = buildSocialProof(
                suburb = provider.suburb,
                localBookers = provider.localBookersThisMonth,
                sharedGroupBookers = provider.sharedGroupBookers,
                responseTimeMinutes = provider.responseTimeMinutes,
                quoteSprintTier = provider.quoteSprintTier,
                quoteResponseRatePct = provider.quoteResponseRatePct,
                quoteResponseStreak = provider.quoteResponseStreak,
                vetChecked = payload.decision == "approved",
                vetCheckedUntil = validUntil,
                highlightedVetUntil = provider.highlightedVetUntil,
            ),
        )
        providers[providerIndex] = updatedProvider
        return VetGroomerVerificationResult(
            verification = verification,
            provider = updatedProvider,
            vetProfile = updatedProfile,
        )
    }

    override suspend fun getProviderAvailability(providerId: String, date: String): List<ServiceAvailabilitySlot> {
        val slots = listOf("09:00", "11:00", "13:00", "15:00", "17:00")
        val blackouts = providerBlackouts[providerId].orEmpty()
            .filter { it.date == date }
            .map { it.timeSlot }
            .toSet()
        val taken = bookings
            .filter { it.providerId == providerId && it.date == date && !it.status.startsWith("cancelled") }
            .map { it.timeSlot }
            .toSet()
        return slots.map { slot ->
            val blocked = slot in blackouts || slot in taken
            ServiceAvailabilitySlot(
                date = date,
                timeSlot = slot,
                available = !blocked,
                reason = if (blocked) "Unavailable" else null,
            )
        }
    }

    override suspend fun createBooking(payload: BookingRequest): BookingResponse {
        val booking = BookingResponse(
            id = "booking_${bookingCounter++}",
            ownerUserId = payload.userId,
            providerId = payload.providerId,
            petName = payload.petName,
            date = payload.date,
            timeSlot = payload.timeSlot,
            note = payload.note,
            status = "pending_provider_confirmation",
        )
        bookings += booking
        return booking
    }

    override suspend fun createBookingHold(payload: BookingHoldRequest): BookingHoldResponse {
        return BookingHoldResponse(
            id = "hold_${holdCounter++}",
            providerId = payload.providerId,
            ownerUserId = payload.userId,
            date = payload.date,
            timeSlot = payload.timeSlot,
            expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES).toString(),
        )
    }

    override suspend fun updateBookingStatus(
        bookingId: String,
        payload: BookingStatusUpdateRequest,
    ): BookingResponse {
        val index = bookings.indexOfFirst { it.id == bookingId }
        if (index < 0) error("Booking not found: $bookingId")
        val updated = bookings[index].copy(
            status = payload.status,
            note = payload.note,
        )
        bookings[index] = updated
        return updated
    }

    override suspend fun getBookings(userId: String?, role: String?): List<BookingResponse> {
        val providerOwnerById = providers.associate { it.id to (it.ownerUserId ?: "") }
        return bookings.filter { booking ->
            when (role) {
                "owner" -> userId == null || booking.ownerUserId == userId
                "provider" -> userId == null || providerOwnerById[booking.providerId] == userId
                else -> {
                    userId == null || booking.ownerUserId == userId || providerOwnerById[booking.providerId] == userId
                }
            }
        }
    }

    override suspend fun getCalendarEvents(
        userId: String,
        dateFrom: String,
        dateTo: String,
        role: String,
    ): List<CalendarEvent> {
        val from = LocalDate.parse(dateFrom)
        val to = LocalDate.parse(dateTo)
        val providerOwnerById = providers.associate { it.id to (it.ownerUserId ?: "") }
        return bookings
            .filter { booking ->
                val bookingDate = runCatching { LocalDate.parse(booking.date) }.getOrNull() ?: return@filter false
                if (bookingDate < from || bookingDate > to) return@filter false
                when (role) {
                    "owner" -> booking.ownerUserId == userId
                    "provider" -> providerOwnerById[booking.providerId] == userId
                    else -> booking.ownerUserId == userId || providerOwnerById[booking.providerId] == userId
                }
            }
            .map { booking ->
                val providerName = providers.firstOrNull { it.id == booking.providerId }?.name ?: "Provider"
                CalendarEvent(
                    id = "calendar_${booking.id}",
                    type = "booking",
                    role = if (booking.ownerUserId == userId) "owner" else "provider",
                    title = providerName,
                    subtitle = booking.petName,
                    date = booking.date,
                    timeSlot = booking.timeSlot,
                    status = booking.status,
                    providerId = booking.providerId,
                    bookingId = booking.id,
                )
            }
    }

    override suspend fun createProviderBlackout(
        providerId: String,
        payload: ProviderBlackoutRequest,
    ): ProviderBlackout {
        val blackout = ProviderBlackout(
            id = "blackout_${blackoutCounter++}",
            providerId = providerId,
            date = payload.date,
            timeSlot = payload.timeSlot,
            reason = payload.reason,
        )
        providerBlackouts.getOrPut(providerId) { mutableListOf() }.add(blackout)
        return blackout
    }

    override suspend fun getProviderBlackouts(providerId: String): List<ProviderBlackout> {
        return providerBlackouts[providerId].orEmpty()
    }

    override suspend fun chat(payload: ChatRequest): ChatResponse {
        val conversation = conversationByUser.getOrPut(payload.userId) { mutableListOf() }
        conversation += ChatTurn(role = "user", content = payload.message)
        val suburbHint = payload.suburb?.let { " (suburb: $it)" }.orEmpty()
        val answer = "Mock mode only. This build does not use the real LLM.$suburbHint"
        conversation += ChatTurn(role = "assistant", content = answer)
        return ChatResponse(
            answer = answer,
            conversation = conversation.toList(),
        )
    }

    override suspend fun acceptProfile(payload: ProfileActionRequest): ChatResponse {
        return ChatResponse(
            answer = "Mock profile saved for ${payload.userId}.",
            conversation = listOf(ChatTurn(role = "assistant", content = "Profile accepted.")),
        )
    }

    override suspend fun submitProvider(payload: ProfileActionRequest): ChatResponse {
        return ChatResponse(
            answer = "Mock provider listing submitted for ${payload.userId}.",
            conversation = listOf(ChatTurn(role = "assistant", content = "Provider listing submitted.")),
        )
    }

    override suspend fun getGroups(suburb: String?, userId: String?): List<Group> {
        return groups
            .filter { suburb.isNullOrBlank() || it.suburb.equals(suburb, ignoreCase = true) }
            .map { group ->
                val members = groupMembers[group.id].orEmpty()
                val pending = groupPendingMembers[group.id].orEmpty()
                decorateGroupForUser(
                    group = group.copy(
                    memberCount = members.size,
                    membershipStatus = if (userId != null && userId in members) {
                        "member"
                    } else if (userId != null && userId in pending) {
                        "pending"
                    } else {
                        "none"
                    },
                    isAdmin = userId != null && group.ownerUserId == userId,
                    pendingRequestCount = if (userId != null && group.ownerUserId == userId) pending.size else 0,
                    ),
                    userId = userId,
                )
            }
    }

    override suspend fun createGroup(payload: GroupCreateRequest): Group {
        val group = Group(
            id = "group_${groupCounter++}",
            name = payload.name,
            suburb = payload.suburb,
            memberCount = 1,
            ownerUserId = payload.userId,
            membershipStatus = "member",
            isAdmin = true,
        )
        groups += group
        groupMembers[group.id] = mutableSetOf(payload.userId)
        groupPendingMembers[group.id] = mutableSetOf()
        ensureChallenges(group)
        rewardPoints(group.id, payload.userId)
        return decorateGroupForUser(group, payload.userId)
    }

    override suspend fun joinGroup(groupId: String, payload: GroupJoinRequest): Group {
        val pending = groupPendingMembers.getOrPut(groupId) { mutableSetOf() }
        val members = groupMembers.getOrPut(groupId) { mutableSetOf() }
        if (payload.userId !in members) {
            pending += payload.userId
        }
        val group = groups.firstOrNull { it.id == groupId } ?: error("Group not found: $groupId")
        if (payload.userId in members) {
            applyGroupGrowthReward(groupId = groupId, contributorUserId = payload.userId, memberAddedUserId = payload.userId, contributionCount = 1)
        }
        return decorateGroupForUser(group.copy(
            memberCount = members.size,
            membershipStatus = if (payload.userId in members) "member" else "pending",
            isAdmin = group.ownerUserId == payload.userId,
            pendingRequestCount = if (group.ownerUserId == payload.userId) pending.size else 0,
        ), payload.userId)
    }

    override suspend fun getGroupChallenges(groupId: String, userId: String?): List<GroupChallengeView> {
        val group = groups.firstOrNull { it.id == groupId } ?: error("Group not found: $groupId")
        val challenges = ensureChallenges(group)
        return challenges.map { challenge ->
            GroupChallengeView(
                challenge = challenge,
                myContributionCount = groupChallengeContributions[Triple(groupId, challenge.type, userId.orEmpty())] ?: 0,
            )
        }
    }

    override suspend fun participateGroupChallenge(
        groupId: String,
        payload: GroupChallengeParticipationRequest,
    ): GroupChallengeParticipationResult {
        val group = groups.firstOrNull { it.id == groupId } ?: error("Group not found: $groupId")
        val members = groupMembers[groupId].orEmpty()
        if (payload.userId !in members) error("Only members can contribute to group challenges")
        val challenges = ensureChallenges(group)
        val challenge = challenges.firstOrNull { it.type == payload.challengeType }
            ?: error("Challenge not found")
        val contributionKey = Triple(groupId, payload.challengeType, payload.userId)
        val previousContribution = groupChallengeContributions[contributionKey] ?: 0
        groupChallengeContributions[contributionKey] = previousContribution + payload.contributionCount
        rewardPoints(groupId, payload.userId)[payload.challengeType] =
            (rewardPoints(groupId, payload.userId)[payload.challengeType] ?: 0) + payload.contributionCount

        val refreshed = ensureChallenges(group).first { it.id == challenge.id }
        val unlockedBadges = mutableListOf<String>()
        if (refreshed.status == "completed") {
            val badge = if (refreshed.type == "pack_builder") "Pack Builder" else "Clean Park Collective"
            val set = groupBadges.getOrPut(groupId) { mutableSetOf() }
            if (set.add(badge)) {
                unlockedBadges += badge
            }
        }
        val myContributionCount = groupChallengeContributions[contributionKey] ?: 0
        val rewardUnlocked = unlockedBadges.isNotEmpty() || (myContributionCount > 0 && myContributionCount % 5 == 0)
        return GroupChallengeParticipationResult(
            challenge = refreshed,
            myContributionCount = myContributionCount,
            contributionCount = payload.contributionCount,
            rewardUnlocked = rewardUnlocked,
            unlockedBadges = unlockedBadges,
        )
    }

    override suspend fun createGroupInvite(payload: GroupInviteCreateRequest): GroupInvite {
        val group = groups.firstOrNull { it.id == payload.groupId } ?: Group(
            id = payload.groupId,
            name = "Dog Park Group",
            suburb = "Surry Hills",
            memberCount = groupMembers[payload.groupId]?.size ?: 0,
            official = false,
            ownerUserId = payload.inviterUserId,
        )
        val token = "inv_mock_${Instant.now().toEpochMilli()}"
        val invite = GroupInvite(
            token = token,
            groupId = group.id,
            groupName = group.name,
            suburb = group.suburb,
            inviterUserId = payload.inviterUserId,
            expiresAt = Instant.now().plus(48, ChronoUnit.HOURS).toString(),
            inviteUrl = "barkwise://join?invite_token=$token&group_id=${group.id}",
        )
        groupInvites[token] = invite
        return invite
    }

    override suspend fun resolveGroupInvite(token: String): GroupInvite {
        return groupInvites[token] ?: error("Invite not found")
    }

    override suspend fun completeGroupOnboarding(payload: GroupOnboardingCompleteRequest): GroupOnboardingCompleteResponse {
        val invite = groupInvites[payload.inviteToken] ?: error("Invite not found")
        val userId = "user_join_${Instant.now().toEpochMilli().toString().takeLast(6)}"
        val members = groupMembers.getOrPut(invite.groupId) { mutableSetOf() }
        members += userId
        val group = groups.firstOrNull { it.id == invite.groupId } ?: Group(
            id = invite.groupId,
            name = invite.groupName,
            suburb = invite.suburb,
            memberCount = members.size,
            ownerUserId = invite.inviterUserId,
        ).also { groups += it }
        if (group != null) {
            val idx = groups.indexOfFirst { it.id == group.id }
            if (idx >= 0) {
                groups[idx] = group.copy(memberCount = members.size)
            } else {
                groups += group.copy(memberCount = members.size)
            }
        }
        applyGroupGrowthReward(
            groupId = invite.groupId,
            contributorUserId = invite.inviterUserId,
            memberAddedUserId = userId,
            contributionCount = 1,
        )
        var createdPostId: String? = null
        if (payload.sharePhotoToGroup) {
            createdPostId = "post_${postCounter++}"
            posts.add(
                0,
                CommunityPost(
                    id = createdPostId,
                    type = "group_post",
                    title = "Dog park check-in: ${payload.dogName}",
                    body = "${payload.ownerName} joined ${invite.groupName} and shared a dog photo.",
                    suburb = payload.suburb ?: invite.suburb,
                    createdAt = Instant.now().toString(),
                ),
            )
        }
        return GroupOnboardingCompleteResponse(
            userId = userId,
            groupId = invite.groupId,
            membershipStatus = "member",
            createdPostId = createdPostId,
        )
    }

    override suspend fun getGroupJoinRequests(
        groupId: String,
        requesterUserId: String,
    ): List<GroupJoinRequestView> {
        val group = groups.firstOrNull { it.id == groupId } ?: return emptyList()
        if (group.ownerUserId != requesterUserId) return emptyList()
        return groupPendingMembers[groupId].orEmpty().map { userId ->
            GroupJoinRequestView(
                groupId = groupId,
                userId = userId,
                status = "pending",
            )
        }
    }

    override suspend fun moderateGroupJoinRequest(
        groupId: String,
        payload: GroupJoinModerationRequest,
    ): Group {
        val group = groups.firstOrNull { it.id == groupId } ?: error("Group not found: $groupId")
        if (group.ownerUserId != payload.requesterUserId) return group
        val pending = groupPendingMembers.getOrPut(groupId) { mutableSetOf() }
        val members = groupMembers.getOrPut(groupId) { mutableSetOf() }
        pending.remove(payload.memberUserId)
        if (payload.action == "approve") {
            members += payload.memberUserId
            applyGroupGrowthReward(
                groupId = groupId,
                contributorUserId = payload.requesterUserId,
                memberAddedUserId = payload.memberUserId,
                contributionCount = 1,
            )
        }
        return decorateGroupForUser(group.copy(
            memberCount = members.size,
            isAdmin = true,
            membershipStatus = "member",
            pendingRequestCount = pending.size,
        ), payload.requesterUserId)
    }

    override suspend fun getPosts(
        suburb: String?,
        userId: String?,
        query: String?,
        sortBy: String?,
    ): List<CommunityPost> {
        val filtered = posts.filter { post ->
            val matchesSuburb = suburb.isNullOrBlank() || post.suburb.equals(suburb, ignoreCase = true)
            val matchesQuery = query.isNullOrBlank() ||
                post.title.contains(query, ignoreCase = true) ||
                post.body.contains(query, ignoreCase = true)
            matchesSuburb && matchesQuery
        }
        return when (sortBy) {
            "newest" -> filtered.sortedByDescending { it.createdAt.orEmpty() }
            else -> filtered
        }
    }

    override suspend fun createPost(payload: CommunityPostCreate): CommunityPost {
        val created = CommunityPost(
            id = "post_${postCounter++}",
            type = payload.type,
            title = payload.title,
            body = payload.body,
            suburb = payload.suburb,
            createdAt = Instant.now().toString(),
        )
        posts.add(0, created)
        return created
    }

    override suspend fun getEvents(suburb: String?, userId: String?): List<CommunityEvent> {
        return events
            .filter { suburb.isNullOrBlank() || it.suburb.equals(suburb, ignoreCase = true) }
            .map { event ->
                val attendees = eventAttendees[event.id].orEmpty()
                event.copy(
                    attendeeCount = attendees.size,
                    rsvpStatus = if (userId != null && userId in attendees) "attending" else "none",
                )
            }
    }

    override suspend fun createEvent(payload: CommunityEventCreateRequest): CommunityEvent {
        val event = CommunityEvent(
            id = "event_${eventCounter++}",
            title = payload.title,
            description = payload.description,
            suburb = payload.suburb,
            date = payload.date,
            groupId = payload.groupId,
            attendeeCount = 0,
            createdBy = payload.userId,
            status = if (payload.groupId == null) "approved" else "pending",
        )
        events.add(0, event)
        return event
    }

    override suspend fun rsvpEvent(
        eventId: String,
        payload: CommunityEventRsvpRequest,
    ): CommunityEvent {
        val event = events.firstOrNull { it.id == eventId } ?: error("Event not found: $eventId")
        val attendees = eventAttendees.getOrPut(eventId) { mutableSetOf() }
        if (payload.status == "attending") {
            attendees += payload.userId
        } else {
            attendees -= payload.userId
        }
        return event.copy(
            attendeeCount = attendees.size,
            rsvpStatus = if (payload.status == "attending") "attending" else "none",
        )
    }

    override suspend fun approveEvent(eventId: String, requesterUserId: String): CommunityEvent {
        val index = events.indexOfFirst { it.id == eventId }
        if (index < 0) error("Event not found: $eventId")
        val event = events[index]
        if (event.createdBy != requesterUserId) return event
        val approved = event.copy(status = "approved")
        events[index] = approved
        return approved
    }

    override suspend fun login(payload: AuthLoginRequest): AuthLoginResponse {
        return AuthLoginResponse(
            accessToken = "mock-token-${payload.userId}",
            tokenType = "bearer",
            userId = payload.userId,
            expiresAt = Instant.now().plus(7, ChronoUnit.DAYS).toString(),
        )
    }

    override suspend fun getNotifications(userId: String, unreadOnly: Boolean): List<AppNotification> {
        return notifications.filter { notification ->
            notification.userId == userId && (!unreadOnly || !notification.read)
        }
    }

    override suspend fun registerDevice(payload: DeviceTokenRegisterRequest): Map<String, String> {
        return mapOf(
            "status" to "registered",
            "user_id" to payload.userId,
        )
    }

    override suspend fun markNotificationRead(notificationId: String, userId: String): AppNotification {
        val index = notifications.indexOfFirst { it.id == notificationId && it.userId == userId }
        if (index < 0) error("Notification not found: $notificationId")
        val updated = notifications[index].copy(read = true)
        notifications[index] = updated
        return updated
    }

    private data class QuoteSprintStats(
        val responseRatePct: Int,
        val responseStreak: Int,
        val tier: String,
    )

    private fun isVetUser(userId: String): Boolean {
        val normalized = userId.lowercase()
        return userId in setOf("user_1", "user_3") || normalized.startsWith("vet_") || normalized.endsWith("_vet")
    }

    private fun ensureVetProfile(userId: String): VetCoachProfile {
        return vetProfiles.getOrPut(userId) {
            VetCoachProfile(
                userId = userId,
                spotlightMinutes = 0,
                coachingMinutes = 0,
                coachingSessions = 0,
                coachQualityScore = 0.0,
                highlightedUntil = null,
                badgeTier = "none",
            )
        }
    }

    private fun resolveVetBadgeTier(sessions: Int, qualityScore: Double): String = when {
        sessions >= 20 && qualityScore >= 0.90 -> "platinum"
        sessions >= 10 && qualityScore >= 0.85 -> "gold"
        sessions >= 5 && qualityScore >= 0.75 -> "silver"
        sessions >= 2 && qualityScore >= 0.60 -> "bronze"
        else -> "none"
    }

    private fun computeQuoteSprintStats(providerId: String): QuoteSprintStats {
        val targets = quoteRequests.values
            .flatMap { it.targets }
            .filter { it.providerId == providerId }
        if (targets.isEmpty()) {
            return QuoteSprintStats(responseRatePct = 0, responseStreak = 0, tier = "none")
        }
        val sorted = targets.sortedByDescending { it.createdAt }
        val responded = sorted.count { it.status == "accepted" || it.status == "declined" || it.respondedAt != null }
        val rate = ((responded.toDouble() / sorted.size.toDouble()) * 100).toInt().coerceIn(0, 100)
        var streak = 0
        for (target in sorted) {
            if (target.status == "accepted" || target.status == "declined" || target.respondedAt != null) {
                streak += 1
            } else {
                break
            }
        }
        val avgResponseMins = sorted.mapNotNull { target ->
            val respondedAt = target.respondedAt ?: return@mapNotNull null
            val created = runCatching { Instant.parse(target.createdAt) }.getOrNull() ?: return@mapNotNull null
            val respondedTs = runCatching { Instant.parse(respondedAt) }.getOrNull() ?: return@mapNotNull null
            ChronoUnit.MINUTES.between(created, respondedTs).toInt().coerceAtLeast(1)
        }.let { values ->
            if (values.isEmpty()) null else values.sum() / values.size
        }
        val tier = when {
            sorted.size < 3 -> "none"
            rate >= 95 && (avgResponseMins ?: 999) <= 15 && streak >= 5 -> "platinum"
            rate >= 90 && (avgResponseMins ?: 999) <= 20 && streak >= 3 -> "gold"
            rate >= 75 && (avgResponseMins ?: 999) <= 35 -> "silver"
            rate >= 60 && (avgResponseMins ?: 999) <= 60 -> "bronze"
            else -> "none"
        }
        return QuoteSprintStats(responseRatePct = rate, responseStreak = streak, tier = tier)
    }

    private fun rewardPoints(groupId: String, userId: String): MutableMap<String, Int> {
        return groupMemberRewardPoints.getOrPut(groupId to userId) {
            mutableMapOf("pack_builder" to 0, "clean_park_streak" to 0)
        }
    }

    private fun groupCooperativeScore(groupId: String): Int {
        return groupMemberRewardPoints.entries
            .filter { entry -> entry.key.first == groupId }
            .sumOf { entry -> entry.value.values.sum() }
    }

    private fun contributionSum(groupId: String, challengeType: String): Int {
        return groupChallengeContributions
            .filter { entry -> entry.key.first == groupId && entry.key.second == challengeType }
            .values
            .sum()
    }

    private fun ensureChallenges(group: Group): List<GroupChallenge> {
        val effectiveMemberCount = groupMembers[group.id]?.size ?: group.memberCount
        val weekCycle = "${LocalDate.now().year}W${LocalDate.now().dayOfYear / 7}"
        val monthCycle = "${LocalDate.now().year}${"%02d".format(LocalDate.now().monthValue)}"
        val monthStart = LocalDate.now().withDayOfMonth(1)
        val monthEnd = monthStart.plusMonths(1)
        val weekStart = LocalDate.now().minusDays(LocalDate.now().dayOfWeek.value.toLong() - 1)
        val weekEnd = weekStart.plusDays(7)

        fun build(
            challengeType: String,
            cycle: String,
            title: String,
            description: String,
            rewardLabel: String,
            targetCount: Int,
            startAt: String,
            endAt: String,
        ): GroupChallenge {
            val progress = contributionSum(group.id, challengeType)
            return GroupChallenge(
                id = "mock_gc_${challengeType}_${group.id}_$cycle",
                groupId = group.id,
                type = challengeType,
                title = title,
                description = description,
                targetCount = targetCount,
                progressCount = progress,
                status = if (progress >= targetCount) "completed" else "active",
                rewardLabel = rewardLabel,
                startAt = startAt,
                endAt = endAt,
            )
        }

        val packBuilder = build(
            challengeType = "pack_builder",
            cycle = monthCycle,
            title = "Pack Builder",
            description = "Grow the group together by welcoming new members.",
            rewardLabel = "Group badge: Pack Builder",
            targetCount = maxOf(5, minOf(30, effectiveMemberCount / 4 + 3)),
            startAt = monthStart.toString(),
            endAt = monthEnd.toString(),
        )
        val cleanPark = build(
            challengeType = "clean_park_streak",
            cycle = weekCycle,
            title = "Clean Park Streak",
            description = "Log cleanup check-ins to keep local parks clean.",
            rewardLabel = "Group badge: Clean Park Collective",
            targetCount = maxOf(8, minOf(40, effectiveMemberCount / 3 + 6)),
            startAt = weekStart.toString(),
            endAt = weekEnd.toString(),
        )
        return listOf(packBuilder, cleanPark)
    }

    private fun decorateGroupForUser(group: Group, userId: String?): Group {
        val points = userId?.let { rewardPoints(group.id, it) }
        val badges = groupBadges[group.id].orEmpty().sorted()
        return group.copy(
            groupBadges = badges,
            cooperativeScore = groupCooperativeScore(group.id),
            myPackBuilderPoints = points?.get("pack_builder") ?: 0,
            myCleanParkPoints = points?.get("clean_park_streak") ?: 0,
        )
    }

    private fun applyGroupGrowthReward(
        groupId: String,
        contributorUserId: String?,
        memberAddedUserId: String?,
        contributionCount: Int,
    ) {
        if (contributorUserId != null) {
            val key = Triple(groupId, "pack_builder", contributorUserId)
            groupChallengeContributions[key] = (groupChallengeContributions[key] ?: 0) + contributionCount
            rewardPoints(groupId, contributorUserId)["pack_builder"] =
                (rewardPoints(groupId, contributorUserId)["pack_builder"] ?: 0) + contributionCount
        }
        if (memberAddedUserId != null) {
            rewardPoints(groupId, memberAddedUserId)["pack_builder"] =
                (rewardPoints(groupId, memberAddedUserId)["pack_builder"] ?: 0) + 1
        }
        val group = groups.firstOrNull { it.id == groupId } ?: return
        val packChallenge = ensureChallenges(group).firstOrNull { it.type == "pack_builder" } ?: return
        if (packChallenge.status == "completed") {
            groupBadges.getOrPut(groupId) { mutableSetOf() }.add("Pack Builder")
        }
    }

    private fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (earthRadiusKm * c * 10.0).toInt() / 10.0
    }

    private fun buildSocialProof(
        suburb: String,
        localBookers: Int,
        sharedGroupBookers: Int,
        responseTimeMinutes: Int?,
        quoteSprintTier: String = "none",
        quoteResponseRatePct: Int = 0,
        quoteResponseStreak: Int = 0,
        vetChecked: Boolean = false,
        vetCheckedUntil: String? = null,
        highlightedVetUntil: String? = null,
    ): List<String> {
        val lines = mutableListOf<String>()
        if (vetChecked && !vetCheckedUntil.isNullOrBlank()) {
            lines += "Vet-checked until ${vetCheckedUntil.take(10)}"
        }
        if (quoteSprintTier != "none") {
            lines += "Quote Sprint ${quoteSprintTier.replaceFirstChar { it.uppercase() }} • $quoteResponseRatePct% response rate • $quoteResponseStreak streak"
        }
        if (localBookers > 0) lines += "Used by $localBookers pet owners in $suburb this month"
        if (sharedGroupBookers > 0) lines += "$sharedGroupBookers members from your groups booked this provider"
        if (responseTimeMinutes != null) lines += "Typically responds in about $responseTimeMinutes min"
        if (!highlightedVetUntil.isNullOrBlank()) lines += "Highlighted vet owner until ${highlightedVetUntil.take(10)}"
        return lines
    }

    companion object {
        fun create(): ApiService = MockApiService()
    }
}
