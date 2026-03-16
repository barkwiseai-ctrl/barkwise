package com.petsocial.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.Base64
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MockApiService private constructor() : ApiService {
    private val now = Instant.now()
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
                "https://images.unsplash.com/photo-1580467277788-c6e040296602?auto=format&fit=crop&w=1200&q=80",
            ),
            latitude = -33.8842,
            longitude = 151.2106,
            ownerUserId = "user_1",
            ownerLabel = "Sesame",
            responseTimeMinutes = 18,
            localBookersThisMonth = 14,
            sharedGroupBookers = 5,
            quoteSprintTier = "gold",
            quoteResponseRatePct = 93,
            quoteResponseStreak = 4,
            vetChecked = true,
            vetCheckedUntil = now.plus(82, ChronoUnit.DAYS).toString(),
            vetCheckedBy = "user_3",
            socialProof = listOf(
                "Vet-checked until ${now.plus(82, ChronoUnit.DAYS).toString().take(10)}",
                "Quote Sprint Gold • 93% response rate • 4 streak",
                "Used by 14 pet owners in Surry Hills this month",
                "5 members from your groups booked this provider",
                "Typically responds in about 18 min",
            ),
        ),
        ServiceProvider(
            id = "provider_2",
            name = "Sesame City Striders",
            category = "dog_walking",
            suburb = "Surry Hills",
            rating = 4.8,
            reviewCount = 91,
            priceFrom = 29,
            description = "Daily solo and pair walks with route photos.",
            fullDescription = "Structured 30-60 minute walks with post-walk behavior notes.",
            imageUrls = listOf(
                "https://images.unsplash.com/photo-1580467277788-c6e040296602?auto=format&fit=crop&w=1200&q=80",
            ),
            latitude = -33.8854,
            longitude = 151.2131,
            ownerUserId = "user_1",
            ownerLabel = "Sesame",
            responseTimeMinutes = 15,
            localBookersThisMonth = 18,
            sharedGroupBookers = 7,
            quoteSprintTier = "platinum",
            quoteResponseRatePct = 97,
            quoteResponseStreak = 6,
            socialProof = listOf(
                "Quote Sprint Platinum • 97% response rate • 6 streak",
                "Used by 18 pet owners in Surry Hills this month",
                "7 members from your groups booked this provider",
                "Typically responds in about 15 min",
            ),
        ),
        ServiceProvider(
            id = "provider_3",
            name = "Snowy Neighborhood Walk Co",
            category = "dog_walking",
            suburb = "Newtown",
            rating = 4.7,
            reviewCount = 84,
            priceFrom = 26,
            description = "Reliable weekday pack walks for social dogs.",
            fullDescription = "Small-group walks with hydration checks and photo updates.",
            imageUrls = listOf(
                "https://images.unsplash.com/photo-1580467277788-c6e040296602?auto=format&fit=crop&w=1200&q=80",
            ),
            latitude = -33.8990,
            longitude = 151.1764,
            ownerUserId = "user_2",
            ownerLabel = "Snowy",
            responseTimeMinutes = 28,
            localBookersThisMonth = 10,
            sharedGroupBookers = 4,
            socialProof = listOf(
                "Used by 10 pet owners in Newtown this month",
                "4 members from your groups booked this provider",
                "Typically responds in about 28 min",
            ),
        ),
        ServiceProvider(
            id = "provider_4",
            name = "Snowy Gentle Groom Lab",
            category = "grooming",
            suburb = "Newtown",
            rating = 4.6,
            reviewCount = 66,
            priceFrom = 54,
            description = "Express tidy trims and sensitive-skin wash plans.",
            fullDescription = "Fast but gentle sessions for routine coat maintenance.",
            imageUrls = listOf(
                "https://images.unsplash.com/photo-1580467277788-c6e040296602?auto=format&fit=crop&w=1200&q=80",
            ),
            latitude = -33.8976,
            longitude = 151.1783,
            ownerUserId = "user_2",
            ownerLabel = "Snowy",
            responseTimeMinutes = 34,
            localBookersThisMonth = 8,
            sharedGroupBookers = 2,
            socialProof = listOf(
                "Used by 8 pet owners in Newtown this month",
                "2 members from your groups booked this provider",
                "Typically responds in about 34 min",
            ),
        ),
        ServiceProvider(
            id = "provider_5",
            name = "Anika Redfern Rover Routes",
            category = "dog_walking",
            suburb = "Redfern",
            rating = 4.9,
            reviewCount = 119,
            priceFrom = 31,
            description = "Morning and evening walks with behavior notes.",
            fullDescription = "Consistent solo walks for energetic and anxious dogs.",
            imageUrls = listOf(
                "https://images.unsplash.com/photo-1580467277788-c6e040296602?auto=format&fit=crop&w=1200&q=80",
            ),
            latitude = -33.8927,
            longitude = 151.2037,
            ownerUserId = "user_3",
            ownerLabel = "Anika",
            responseTimeMinutes = 19,
            localBookersThisMonth = 16,
            sharedGroupBookers = 6,
            quoteSprintTier = "silver",
            quoteResponseRatePct = 81,
            quoteResponseStreak = 3,
            highlightedVet = "Anika",
            highlightedVetUntil = now.plus(4, ChronoUnit.DAYS).toString(),
            socialProof = listOf(
                "Quote Sprint Silver • 81% response rate • 3 streak",
                "Used by 16 pet owners in Redfern this month",
                "6 members from your groups booked this provider",
                "Typically responds in about 19 min",
                "Highlighted vet owner until ${now.plus(4, ChronoUnit.DAYS).toString().take(10)}",
            ),
        ),
        ServiceProvider(
            id = "provider_6",
            name = "Anika Calm Coat Studio",
            category = "grooming",
            suburb = "Redfern",
            rating = 4.8,
            reviewCount = 97,
            priceFrom = 59,
            description = "Low-stress grooming for seniors and anxious pets.",
            fullDescription = "One-on-one appointments with coat and skin notes every visit.",
            imageUrls = listOf(
                "https://images.unsplash.com/photo-1580467277788-c6e040296602?auto=format&fit=crop&w=1200&q=80",
            ),
            latitude = -33.8918,
            longitude = 151.2055,
            ownerUserId = "user_3",
            ownerLabel = "Anika",
            responseTimeMinutes = 23,
            localBookersThisMonth = 12,
            sharedGroupBookers = 5,
            vetChecked = true,
            vetCheckedUntil = now.plus(64, ChronoUnit.DAYS).toString(),
            vetCheckedBy = "user_1",
            highlightedVet = "Anika",
            highlightedVetUntil = now.plus(4, ChronoUnit.DAYS).toString(),
            socialProof = listOf(
                "Vet-checked until ${now.plus(64, ChronoUnit.DAYS).toString().take(10)}",
                "Used by 12 pet owners in Redfern this month",
                "5 members from your groups booked this provider",
                "Typically responds in about 23 min",
                "Highlighted vet owner until ${now.plus(4, ChronoUnit.DAYS).toString().take(10)}",
            ),
        ),
        ServiceProvider(
            id = "provider_7",
            name = "Tommy Tiny Paws Grooming",
            category = "grooming",
            suburb = "Surry Hills",
            rating = 4.7,
            reviewCount = 108,
            priceFrom = 63,
            description = "Toy-breed specialist grooming with coat-safe products.",
            fullDescription = "Face tidy, nails, and paw care for small breeds.",
            imageUrls = listOf(
                "https://images.unsplash.com/photo-1580467277788-c6e040296602?auto=format&fit=crop&w=1200&q=80",
            ),
            latitude = -33.8862,
            longitude = 151.2104,
            ownerUserId = "user_4",
            ownerLabel = "Tommy",
            responseTimeMinutes = 27,
            localBookersThisMonth = 13,
            sharedGroupBookers = 3,
            quoteSprintTier = "bronze",
            quoteResponseRatePct = 66,
            quoteResponseStreak = 2,
            socialProof = listOf(
                "Quote Sprint Bronze • 66% response rate • 2 streak",
                "Used by 13 pet owners in Surry Hills this month",
                "3 members from your groups booked this provider",
                "Typically responds in about 27 min",
            ),
        ),
        ServiceProvider(
            id = "provider_8",
            name = "Tommy Park Pack Walks",
            category = "dog_walking",
            suburb = "Redfern",
            rating = 4.5,
            reviewCount = 62,
            priceFrom = 25,
            description = "Budget-friendly weekday and weekend park walks.",
            fullDescription = "Short and medium route options with simple status updates.",
            imageUrls = listOf(
                "https://images.unsplash.com/photo-1580467277788-c6e040296602?auto=format&fit=crop&w=1200&q=80",
            ),
            latitude = -33.8940,
            longitude = 151.2022,
            ownerUserId = "user_4",
            ownerLabel = "Tommy",
            responseTimeMinutes = 38,
            localBookersThisMonth = 6,
            sharedGroupBookers = 2,
            socialProof = listOf(
                "Used by 6 pet owners in Redfern this month",
                "2 members from your groups booked this provider",
                "Typically responds in about 38 min",
            ),
        ),
        ServiceProvider(
            id = "provider_9",
            name = "West Paws Walk Club",
            category = "dog_walking",
            suburb = "Sunshine West",
            rating = 4.6,
            reviewCount = 41,
            priceFrom = 24,
            description = "Local weekday walk club for Sunshine West families.",
            fullDescription = "Recurring slots focused on predictable routines and safety checks.",
            imageUrls = listOf(
                "https://images.unsplash.com/photo-1580467277788-c6e040296602?auto=format&fit=crop&w=1200&q=80",
            ),
            latitude = -37.7921,
            longitude = 144.8169,
            ownerUserId = "user_2",
            ownerLabel = "Snowy",
            responseTimeMinutes = 32,
            localBookersThisMonth = 5,
            sharedGroupBookers = 1,
            socialProof = listOf(
                "Used by 5 pet owners in Sunshine West this month",
                "1 members from your groups booked this provider",
                "Typically responds in about 32 min",
            ),
        ),
        ServiceProvider(
            id = "provider_10",
            name = "Sunshine Coat Care",
            category = "grooming",
            suburb = "Sunshine West",
            rating = 4.8,
            reviewCount = 55,
            priceFrom = 52,
            description = "Neighbourhood grooming studio with gentle handling.",
            fullDescription = "Wash, dry, tidy trim, and coat-care notes in every session.",
            imageUrls = listOf(
                "https://images.unsplash.com/photo-1580467277788-c6e040296602?auto=format&fit=crop&w=1200&q=80",
            ),
            latitude = -37.7914,
            longitude = 144.8158,
            ownerUserId = "user_4",
            ownerLabel = "Tommy",
            responseTimeMinutes = 29,
            localBookersThisMonth = 7,
            sharedGroupBookers = 2,
            vetChecked = true,
            vetCheckedUntil = now.plus(43, ChronoUnit.DAYS).toString(),
            vetCheckedBy = "user_1",
            socialProof = listOf(
                "Vet-checked until ${now.plus(43, ChronoUnit.DAYS).toString().take(10)}",
                "Used by 7 pet owners in Sunshine West this month",
                "2 members from your groups booked this provider",
                "Typically responds in about 29 min",
            ),
        ),
    )
    private val reviewsByProvider = mutableMapOf(
        "provider_1" to mutableListOf(
            Review("review_1", "provider_1", "Sam", 5, "Excellent groom. Coat came back fluffy and even."),
            Review("review_2", "provider_1", "June", 5, "Great updates and really gentle handling."),
        ),
        "provider_2" to mutableListOf(
            Review("review_3", "provider_2", "Mia", 5, "Great walker and quick updates after every walk."),
            Review("review_4", "provider_2", "Leo", 4, "Reliable timing for morning walks."),
        ),
        "provider_3" to mutableListOf(
            Review("review_5", "provider_3", "Harper", 5, "Pack walks are calm and well supervised."),
            Review("review_6", "provider_3", "Noah", 4, "Good communication and route photos."),
        ),
        "provider_4" to mutableListOf(
            Review("review_7", "provider_4", "Ava", 5, "Quick tidy clip and easy handover."),
        ),
        "provider_5" to mutableListOf(
            Review("review_8", "provider_5", "Ivy", 5, "Best walker in Redfern for energetic dogs."),
            Review("review_9", "provider_5", "Piper", 4, "Great behavior notes."),
        ),
        "provider_6" to mutableListOf(
            Review("review_10", "provider_6", "Nora", 5, "Handled our senior dog gently."),
            Review("review_11", "provider_6", "Ben", 5, "Excellent de-shed and coat advice."),
        ),
        "provider_7" to mutableListOf(
            Review("review_12", "provider_7", "Luca", 4, "Solid groom and nail work."),
        ),
        "provider_8" to mutableListOf(
            Review("review_13", "provider_8", "Maya", 4, "Good value walks for weekdays."),
        ),
        "provider_9" to mutableListOf(
            Review("review_14", "provider_9", "Elise", 5, "Great with shy rescue dogs."),
        ),
        "provider_10" to mutableListOf(
            Review("review_15", "provider_10", "Jules", 5, "Calm grooming setup and clear updates."),
        ),
    )
    private val quoteRequests = mutableMapOf(
        "quote_1" to ServiceQuoteRequestView(
            quoteRequest = ServiceQuoteRequest(
                id = "quote_1",
                userId = "user_2",
                category = "grooming",
                suburb = "Surry Hills",
                preferredWindow = "Weekday mornings",
                petDetails = "Toy poodle, sensitive skin",
                note = "Needs gentle products",
                status = "responded",
                createdAt = now.minus(3, ChronoUnit.DAYS).toString(),
                updatedAt = now.minus(3, ChronoUnit.DAYS).plus(40, ChronoUnit.MINUTES).toString(),
            ),
            targets = listOf(
                ServiceQuoteTarget(
                    providerId = "provider_1",
                    providerName = "Sesame Suds Studio",
                    ownerUserId = "user_1",
                    status = "accepted",
                    responseMessage = "Yes, we can do morning pickup.",
                    createdAt = now.minus(3, ChronoUnit.DAYS).toString(),
                    respondedAt = now.minus(3, ChronoUnit.DAYS).plus(18, ChronoUnit.MINUTES).toString(),
                    reminder15Sent = true,
                    reminder60Sent = false,
                ),
                ServiceQuoteTarget(
                    providerId = "provider_7",
                    providerName = "Tommy Tiny Paws Grooming",
                    ownerUserId = "user_4",
                    status = "declined",
                    responseMessage = "No suitable slots this week.",
                    createdAt = now.minus(3, ChronoUnit.DAYS).toString(),
                    respondedAt = now.minus(3, ChronoUnit.DAYS).plus(41, ChronoUnit.MINUTES).toString(),
                    reminder15Sent = true,
                    reminder60Sent = false,
                ),
            ),
        ),
        "quote_2" to ServiceQuoteRequestView(
            quoteRequest = ServiceQuoteRequest(
                id = "quote_2",
                userId = "user_4",
                category = "dog_walking",
                suburb = "Redfern",
                preferredWindow = "Evening slots",
                petDetails = "2-year old lab, high energy",
                note = "",
                status = "responded",
                createdAt = now.minus(2, ChronoUnit.DAYS).toString(),
                updatedAt = now.minus(2, ChronoUnit.DAYS).plus(22, ChronoUnit.MINUTES).toString(),
            ),
            targets = listOf(
                ServiceQuoteTarget(
                    providerId = "provider_5",
                    providerName = "Anika Redfern Rover Routes",
                    ownerUserId = "user_3",
                    status = "accepted",
                    responseMessage = "Happy to take this request.",
                    createdAt = now.minus(2, ChronoUnit.DAYS).toString(),
                    respondedAt = now.minus(2, ChronoUnit.DAYS).plus(12, ChronoUnit.MINUTES).toString(),
                    reminder15Sent = false,
                    reminder60Sent = false,
                ),
                ServiceQuoteTarget(
                    providerId = "provider_8",
                    providerName = "Tommy Park Pack Walks",
                    ownerUserId = "user_4",
                    status = "accepted",
                    responseMessage = "Can do weekend backup slot.",
                    createdAt = now.minus(2, ChronoUnit.DAYS).toString(),
                    respondedAt = now.minus(2, ChronoUnit.DAYS).plus(21, ChronoUnit.MINUTES).toString(),
                    reminder15Sent = true,
                    reminder60Sent = false,
                ),
            ),
        ),
        "quote_3" to ServiceQuoteRequestView(
            quoteRequest = ServiceQuoteRequest(
                id = "quote_3",
                userId = "user_1",
                category = "grooming",
                suburb = "Newtown",
                preferredWindow = "Saturday afternoon",
                petDetails = "Senior corgi, anxiety around dryers",
                note = "",
                status = "pending",
                createdAt = now.minus(45, ChronoUnit.MINUTES).toString(),
                updatedAt = now.minus(45, ChronoUnit.MINUTES).toString(),
            ),
            targets = listOf(
                ServiceQuoteTarget(
                    providerId = "provider_4",
                    providerName = "Snowy Gentle Groom Lab",
                    ownerUserId = "user_2",
                    status = "pending",
                    createdAt = now.minus(45, ChronoUnit.MINUTES).toString(),
                    respondedAt = null,
                    reminder15Sent = true,
                    reminder60Sent = false,
                ),
            ),
        ),
    )
    private val vetProfiles = mutableMapOf(
        "user_1" to VetCoachProfile(
            userId = "user_1",
            spotlightMinutes = 86,
            coachingMinutes = 210,
            coachingSessions = 11,
            coachQualityScore = 0.88,
            highlightedUntil = now.plus(3, ChronoUnit.DAYS).toString(),
            badgeTier = "gold",
        ),
        "user_3" to VetCoachProfile(
            userId = "user_3",
            spotlightMinutes = 64,
            coachingMinutes = 154,
            coachingSessions = 8,
            coachQualityScore = 0.83,
            highlightedUntil = now.plus(4, ChronoUnit.DAYS).toString(),
            badgeTier = "silver",
        ),
    )
    private val providerVetVerifications = mutableMapOf(
        "provider_6" to VetGroomerVerification(
            id = "mock_vver_1",
            providerId = "provider_6",
            vetUserId = "user_1",
            decision = "approved",
            confidenceScore = 0.92,
            note = "Consistent hygiene process and low-stress handling.",
            createdAt = now.minus(26, ChronoUnit.DAYS).toString(),
            validUntil = now.plus(64, ChronoUnit.DAYS).toString(),
            spotlightMinutesEarned = 19,
        ),
        "provider_10" to VetGroomerVerification(
            id = "mock_vver_2",
            providerId = "provider_10",
            vetUserId = "user_3",
            decision = "approved",
            confidenceScore = 0.86,
            note = "Strong handling quality for anxious dogs.",
            createdAt = now.minus(10, ChronoUnit.DAYS).toString(),
            validUntil = now.plus(43, ChronoUnit.DAYS).toString(),
            spotlightMinutesEarned = 17,
        ),
    )
    private val groupBadges = mutableMapOf<String, MutableSet<String>>(
        "group_1" to mutableSetOf("Pack Builder"),
        "group_5" to mutableSetOf("Clean Park Collective"),
    )
    private val groupMemberRewardPoints = mutableMapOf<Pair<String, String>, MutableMap<String, Int>>(
        ("group_1" to "user_1") to mutableMapOf("pack_builder" to 4, "clean_park_streak" to 2),
        ("group_1" to "user_2") to mutableMapOf("pack_builder" to 2, "clean_park_streak" to 1),
        ("group_2" to "user_3") to mutableMapOf("pack_builder" to 3, "clean_park_streak" to 4),
        ("group_3" to "user_2") to mutableMapOf("pack_builder" to 2, "clean_park_streak" to 2),
        ("group_5" to "user_3") to mutableMapOf("pack_builder" to 3, "clean_park_streak" to 5),
    )
    private val groupChallengeContributions = mutableMapOf<Triple<String, String, String>, Int>(
        Triple("group_1", "pack_builder", "user_1") to 4,
        Triple("group_1", "clean_park_streak", "user_1") to 2,
        Triple("group_1", "pack_builder", "user_2") to 2,
        Triple("group_2", "clean_park_streak", "user_3") to 4,
        Triple("group_3", "pack_builder", "user_2") to 2,
        Triple("group_5", "clean_park_streak", "user_3") to 5,
        Triple("group_5", "pack_builder", "user_1") to 3,
    )
    private val groupMembers = mutableMapOf(
        "group_1" to mutableSetOf("user_1", "user_2", "user_4", "user_jules", "user_ivy", "user_omar"),
        "group_2" to mutableSetOf("user_3", "user_1", "user_mina", "user_ken"),
        "group_3" to mutableSetOf("user_2", "user_3", "user_lara", "user_zoe", "user_hugo"),
        "group_4" to mutableSetOf("user_4", "user_2", "user_amy"),
        "group_5" to mutableSetOf("user_3", "user_1", "user_2", "user_nia", "user_toby"),
    )
    private val groupPendingMembers = mutableMapOf(
        "group_1" to mutableSetOf("user_5"),
        "group_2" to mutableSetOf("user_6"),
        "group_3" to mutableSetOf("user_7"),
        "group_4" to mutableSetOf<String>(),
        "group_5" to mutableSetOf("user_8", "user_9"),
    )
    private val groupInvites = mutableMapOf<String, GroupInvite>()
    private val groups = mutableListOf(
        Group(
            id = "group_1",
            name = "Surry Hills Dog Parents",
            suburb = "Surry Hills",
            memberCount = 6,
            official = true,
            ownerUserId = "user_1",
        ),
        Group(
            id = "group_2",
            name = "Redfern Weekend Walks",
            suburb = "Redfern",
            memberCount = 4,
            ownerUserId = "user_3",
        ),
        Group(
            id = "group_3",
            name = "Newtown Puppy Parents",
            suburb = "Newtown",
            memberCount = 5,
            official = true,
            ownerUserId = "user_2",
        ),
        Group(
            id = "group_4",
            name = "Inner West Grooming Tips",
            suburb = "Newtown",
            memberCount = 3,
            ownerUserId = "user_4",
        ),
        Group(
            id = "group_5",
            name = "Surry Hills Clean Park Crew",
            suburb = "Surry Hills",
            memberCount = 5,
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
            createdAt = now.minus(2, ChronoUnit.HOURS).toString(),
        ),
        CommunityPost(
            id = "post_2",
            type = "group_post",
            title = "Dog park check-in: Luna",
            body = "Luna joined the Saturday fetch circle.",
            suburb = "Surry Hills",
            createdAt = now.minus(5, ChronoUnit.HOURS).toString(),
        ),
        CommunityPost(
            id = "post_3",
            type = "group_post",
            title = "Then vs Now card for Milo",
            body = "Milo's confidence is way up after 6 months of training.",
            suburb = "Redfern",
            createdAt = now.minus(1, ChronoUnit.DAYS).toString(),
        ),
        CommunityPost(
            id = "post_4",
            type = "lost_found",
            title = "Missing terrier near King Street",
            body = "Brown terrier with green harness, last seen near station.",
            suburb = "Newtown",
            createdAt = now.minus(1, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS).toString(),
        ),
        CommunityPost(
            id = "post_5",
            type = "group_post",
            title = "Cleanup check-in at Redfern Oval",
            body = "Group logged 9 cleanup bags this week.",
            suburb = "Redfern",
            createdAt = now.minus(2, ChronoUnit.DAYS).toString(),
        ),
        CommunityPost(
            id = "post_6",
            type = "group_post",
            title = "Best rainy-day enrichment toys?",
            body = "Share puzzle feeders and scent-game ideas for apartment dogs.",
            suburb = "Newtown",
            createdAt = now.minus(3, ChronoUnit.DAYS).toString(),
        ),
        CommunityPost(
            id = "post_7",
            type = "lost_found",
            title = "Found harness at Sydney Park gate",
            body = "Black harness with yellow tag now at cafe counter nearby.",
            suburb = "Newtown",
            createdAt = now.minus(4, ChronoUnit.DAYS).toString(),
        ),
        CommunityPost(
            id = "post_8",
            type = "group_post",
            title = "Pack Builder milestone hit",
            body = "Surry Hills Dog Parents welcomed 5 new members this month.",
            suburb = "Surry Hills",
            createdAt = now.minus(5, ChronoUnit.DAYS).toString(),
        ),
        CommunityPost(
            id = "post_9",
            type = "group_post",
            title = "Groomer shortlist thread",
            body = "Posting vet-reviewed groomers and response times.",
            suburb = "Redfern",
            createdAt = now.minus(6, ChronoUnit.DAYS).toString(),
        ),
    )
    private val commentsByPost = mutableMapOf(
        "post_1" to mutableListOf(
            CommunityComment(
                id = "comment_1",
                postId = "post_1",
                userId = "user_2",
                body = "I can confirm it's still near the west gate.",
                createdAt = now.minus(110, ChronoUnit.MINUTES).toString(),
            ),
            CommunityComment(
                id = "comment_2",
                postId = "post_1",
                userId = "user_1",
                body = "Thanks, I can grab it this afternoon.",
                parentCommentId = "comment_1",
                createdAt = now.minus(96, ChronoUnit.MINUTES).toString(),
            ),
        ),
        "post_2" to mutableListOf(
            CommunityComment(
                id = "comment_3",
                postId = "post_2",
                userId = "user_3",
                body = "Luna looked so happy today.",
                createdAt = now.minus(4, ChronoUnit.HOURS).toString(),
            ),
            CommunityComment(
                id = "comment_4",
                postId = "post_2",
                userId = "user_4",
                body = "Removed by moderator for mock policy test.",
                createdAt = now.minus(3, ChronoUnit.HOURS).toString(),
                status = "removed_by_moderator",
                moderatedAt = now.minus(2, ChronoUnit.HOURS).toString(),
                moderatedBy = "user_1",
                moderationNote = "Mock moderation sample",
            ),
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
            attendeeCount = 4,
            createdBy = "user_1",
            status = "approved",
        ),
        CommunityEvent(
            id = "event_2",
            title = "Recall Basics in Newtown",
            description = "Trainer-led recall workshop for adolescent dogs.",
            suburb = "Newtown",
            date = LocalDate.now().plusDays(4).toString(),
            groupId = "group_3",
            attendeeCount = 5,
            createdBy = "user_2",
            status = "approved",
        ),
        CommunityEvent(
            id = "event_3",
            title = "Redfern Twilight Walk",
            description = "40-minute walk with rest and hydration stop.",
            suburb = "Redfern",
            date = LocalDate.now().plusDays(1).toString(),
            groupId = "group_2",
            attendeeCount = 3,
            createdBy = "user_3",
            status = "approved",
        ),
        CommunityEvent(
            id = "event_4",
            title = "Community Clean Park Hour",
            description = "Group cleanup plus dog social check-ins.",
            suburb = "Surry Hills",
            date = LocalDate.now().plusDays(6).toString(),
            groupId = "group_5",
            attendeeCount = 6,
            createdBy = "user_3",
            status = "approved",
        ),
        CommunityEvent(
            id = "event_5",
            title = "Grooming Demo Night",
            description = "Live demo on coat-safe brushing routines.",
            suburb = "Newtown",
            date = LocalDate.now().plusDays(7).toString(),
            groupId = "group_4",
            attendeeCount = 2,
            createdBy = "user_4",
            status = "pending",
        ),
    )
    private val eventAttendees = mutableMapOf(
        "event_1" to mutableSetOf("user_2", "user_4", "user_jules", "user_ivy"),
        "event_2" to mutableSetOf("user_1", "user_3", "user_lara", "user_zoe", "user_hugo"),
        "event_3" to mutableSetOf("user_1", "user_mina", "user_ken"),
        "event_4" to mutableSetOf("user_1", "user_2", "user_3", "user_nia", "user_toby", "user_omar"),
        "event_5" to mutableSetOf("user_4", "user_2"),
    )
    private val bookings = mutableListOf(
        BookingResponse(
            id = "booking_1",
            ownerUserId = "user_2",
            providerId = "provider_1",
            petName = "Milo",
            date = LocalDate.now().plusDays(1).toString(),
            timeSlot = "09:00",
            note = "Anxious with dryers, please go slow.",
            status = "requested",
        ),
        BookingResponse(
            id = "booking_2",
            ownerUserId = "user_1",
            providerId = "provider_8",
            petName = "Luna",
            date = LocalDate.now().plusDays(2).toString(),
            timeSlot = "11:00",
            note = "Prefer shaded route.",
            status = "provider_confirmed",
        ),
        BookingResponse(
            id = "booking_3",
            ownerUserId = "user_4",
            providerId = "provider_3",
            petName = "Maple",
            date = LocalDate.now().minusDays(1).toString(),
            timeSlot = "15:00",
            note = "Completed smoothly.",
            status = "completed",
        ),
        BookingResponse(
            id = "booking_4",
            ownerUserId = "user_3",
            providerId = "provider_4",
            petName = "Scout",
            date = LocalDate.now().plusDays(3).toString(),
            timeSlot = "13:00",
            note = "Needs reschedule due to work conflict.",
            status = "reschedule_requested",
        ),
        BookingResponse(
            id = "booking_5",
            ownerUserId = "user_2",
            providerId = "provider_6",
            petName = "Nala",
            date = LocalDate.now().toString(),
            timeSlot = "17:00",
            note = "Sensitive skin package.",
            status = "in_progress",
        ),
    )
    private val bookingStatusHistoryByBookingId = mutableMapOf<String, MutableList<BookingStatusHistoryEntry>>(
        "booking_1" to mutableListOf(
            BookingStatusHistoryEntry(
                id = "bsh_1",
                bookingId = "booking_1",
                actorUserId = "user_2",
                fromStatus = "none",
                toStatus = "requested",
                note = "booking requested",
                createdAt = now.minus(2, ChronoUnit.HOURS).toString(),
            ),
        ),
        "booking_2" to mutableListOf(
            BookingStatusHistoryEntry(
                id = "bsh_2",
                bookingId = "booking_2",
                actorUserId = "user_1",
                fromStatus = "none",
                toStatus = "requested",
                note = "booking requested",
                createdAt = now.minus(27, ChronoUnit.HOURS).toString(),
            ),
            BookingStatusHistoryEntry(
                id = "bsh_3",
                bookingId = "booking_2",
                actorUserId = "user_4",
                fromStatus = "requested",
                toStatus = "provider_confirmed",
                note = "confirmed by provider",
                createdAt = now.minus(24, ChronoUnit.HOURS).toString(),
            ),
        ),
        "booking_3" to mutableListOf(
            BookingStatusHistoryEntry(
                id = "bsh_4",
                bookingId = "booking_3",
                actorUserId = "user_4",
                fromStatus = "none",
                toStatus = "requested",
                note = "booking requested",
                createdAt = now.minus(3, ChronoUnit.DAYS).toString(),
            ),
            BookingStatusHistoryEntry(
                id = "bsh_5",
                bookingId = "booking_3",
                actorUserId = "user_2",
                fromStatus = "requested",
                toStatus = "provider_confirmed",
                note = "confirmed by provider",
                createdAt = now.minus(2, ChronoUnit.DAYS).toString(),
            ),
            BookingStatusHistoryEntry(
                id = "bsh_6",
                bookingId = "booking_3",
                actorUserId = "user_2",
                fromStatus = "provider_confirmed",
                toStatus = "completed",
                note = "service completed",
                createdAt = now.minus(1, ChronoUnit.DAYS).toString(),
            ),
        ),
        "booking_4" to mutableListOf(
            BookingStatusHistoryEntry(
                id = "bsh_7",
                bookingId = "booking_4",
                actorUserId = "user_3",
                fromStatus = "none",
                toStatus = "requested",
                note = "booking requested",
                createdAt = now.minus(8, ChronoUnit.HOURS).toString(),
            ),
            BookingStatusHistoryEntry(
                id = "bsh_8",
                bookingId = "booking_4",
                actorUserId = "user_3",
                fromStatus = "requested",
                toStatus = "reschedule_requested",
                note = "needs reschedule",
                createdAt = now.minus(5, ChronoUnit.HOURS).toString(),
            ),
        ),
        "booking_5" to mutableListOf(
            BookingStatusHistoryEntry(
                id = "bsh_9",
                bookingId = "booking_5",
                actorUserId = "user_2",
                fromStatus = "none",
                toStatus = "requested",
                note = "booking requested",
                createdAt = now.minus(9, ChronoUnit.HOURS).toString(),
            ),
            BookingStatusHistoryEntry(
                id = "bsh_10",
                bookingId = "booking_5",
                actorUserId = "user_3",
                fromStatus = "requested",
                toStatus = "provider_confirmed",
                note = "confirmed by provider",
                createdAt = now.minus(7, ChronoUnit.HOURS).toString(),
            ),
            BookingStatusHistoryEntry(
                id = "bsh_11",
                bookingId = "booking_5",
                actorUserId = "user_3",
                fromStatus = "provider_confirmed",
                toStatus = "in_progress",
                note = "service in progress",
                createdAt = now.minus(35, ChronoUnit.MINUTES).toString(),
            ),
        ),
    )
    private val providerBlackouts = mutableMapOf(
        "provider_1" to mutableListOf(
            ProviderBlackout(
                id = "blackout_1",
                providerId = "provider_1",
                date = LocalDate.now().plusDays(4).toString(),
                timeSlot = "13:00",
                reason = "Clinic training block",
            ),
        ),
        "provider_4" to mutableListOf(
            ProviderBlackout(
                id = "blackout_2",
                providerId = "provider_4",
                date = LocalDate.now().plusDays(2).toString(),
                timeSlot = "15:00",
                reason = "Equipment maintenance",
            ),
        ),
    )
    private val conversationByUser = mutableMapOf<String, MutableList<ChatTurn>>()
    private val notifications = mutableListOf(
        AppNotification(
            id = "notif_1",
            userId = "user_2",
            title = "Quote response received",
            body = "Sesame Suds Studio accepted your grooming quote request.",
            category = "booking",
            read = false,
            createdAt = now.minus(1, ChronoUnit.HOURS).toString(),
            deepLink = "quote:quote_1",
        ),
        AppNotification(
            id = "notif_2",
            userId = "user_1",
            title = "New booking request",
            body = "Milo requested 09:00 tomorrow for Sesame Suds Studio.",
            category = "booking",
            read = false,
            createdAt = now.minus(2, ChronoUnit.HOURS).toString(),
            deepLink = "booking:booking_1",
        ),
        AppNotification(
            id = "notif_3",
            userId = "user_3",
            title = "Group challenge completed",
            body = "Surry Hills Clean Park Crew unlocked a community badge.",
            category = "community",
            read = false,
            createdAt = now.minus(4, ChronoUnit.HOURS).toString(),
            deepLink = "group:group_5",
        ),
        AppNotification(
            id = "notif_4",
            userId = "user_4",
            title = "Listing reviewed by vet",
            body = "Sunshine Coat Care is now Vet-Checked.",
            category = "booking",
            read = true,
            createdAt = now.minus(1, ChronoUnit.DAYS).toString(),
            deepLink = "provider:provider_10",
        ),
        AppNotification(
            id = "notif_5",
            userId = "user_2",
            title = "Event RSVP update",
            body = "3 more members are attending Recall Basics in Newtown.",
            category = "community",
            read = true,
            createdAt = now.minus(1, ChronoUnit.DAYS).toString(),
            deepLink = "event:event_2",
        ),
        AppNotification(
            id = "notif_6",
            userId = "user_1",
            title = "Community reward unlocked",
            body = "You hit 5 Clean Park points in Surry Hills Dog Parents.",
            category = "community",
            read = false,
            createdAt = now.minus(3, ChronoUnit.DAYS).toString(),
            deepLink = "group:group_1",
        ),
        AppNotification(
            id = "notif_7",
            userId = "user_3",
            title = "Vet spotlight active",
            body = "Your highlighted vet badge is now live across your listings.",
            category = "system",
            read = true,
            createdAt = now.minus(2, ChronoUnit.DAYS).toString(),
            deepLink = "profile",
        ),
        AppNotification(
            id = "notif_8",
            userId = "user_4",
            title = "Quote request reminder",
            body = "Please respond to quote request for Tommy Tiny Paws Grooming.",
            category = "booking",
            read = false,
            createdAt = now.minus(44, ChronoUnit.MINUTES).toString(),
            deepLink = "quote:quote_3",
        ),
    )
    private val authInvitesById = mutableMapOf<String, AuthInviteResponse>()
    private val otpCodeByInviteEmail = mutableMapOf<String, String>()
    private val otpExpiresByInviteEmail = mutableMapOf<String, Instant>()
    private val userProfilesByUserId = mutableMapOf(
        "user_1" to UserProfileResponse(
            userId = "user_1",
            displayName = "Sesame",
            email = "user_1@barkwise.test",
            phone = "+61 400 001 001",
            humanPronouns = "they/them",
            humanRoleLabel = "Provider + Pet parent",
            dogName = "Luna",
            dogAgeMonths = 42,
            dogBreedMix = "Labradoodle",
            dogSexNeuter = "Female, desexed",
            dogWeightClass = "Medium (10-25kg)",
            dogPhotoUrls = listOf("https://images.unsplash.com/photo-1580467277788-c6e040296602?auto=format&fit=crop&w=1200&q=80"),
            secondaryDogName = "Poppy",
            secondaryDogAgeMonths = 18,
            secondaryDogGender = "female",
            secondaryDogWeightKg = "12.0",
            bio = "Groomer and dog parent.",
            suburb = "Surry Hills",
            favoriteSuburbs = listOf("Surry Hills", "Newtown"),
            playEnergyLevel = "High",
            playStyle = "Chase and fetch",
            socialConfidence = "Confident with new dogs",
            triggerNotes = "Prefers slow intro with larger intact males.",
            idealMatch = "Playful medium-energy dogs",
            walkPreferences = "Off-peak parks, 30-45 mins",
            trainingStyle = "Positive reinforcement, marker word",
            feedingRules = "No chicken treats",
            consentBoundaries = "Ask before giving treats or off-lead time.",
            vaccinationStatus = "Up to date",
            microchipped = true,
            recallTrained = true,
            leashReliability = "Reliable on leash",
            emergencyContactName = "Jordan",
            emergencyContactPhone = "+61 400 555 001",
            fieldVisibility = mapOf("phone" to "friends", "email" to "private", "suburb" to "group"),
            updatedAt = now.minus(2, ChronoUnit.DAYS).toString(),
        ),
        "user_2" to UserProfileResponse(
            userId = "user_2",
            displayName = "Alex Wong",
            email = "user_2@barkwise.test",
            phone = "+61 412 345 678",
            humanPronouns = "she/her",
            humanRoleLabel = "Member",
            dogName = "Milo",
            dogAgeMonths = 30,
            dogBreedMix = "Cavoodle",
            dogSexNeuter = "Male, desexed",
            dogWeightClass = "Small (0-10kg)",
            dogPhotoUrls = listOf("https://images.unsplash.com/photo-1580467277788-c6e040296602?auto=format&fit=crop&w=1200&q=80"),
            bio = "Pet parent of Milo. Loves social dog walks and local events.",
            suburb = "Surry Hills",
            favoriteSuburbs = listOf("Newtown", "Redfern"),
            playEnergyLevel = "Medium",
            playStyle = "Sniff and mingle",
            socialConfidence = "Friendly but cautious in crowds",
            idealMatch = "Calm to medium-energy dogs",
            walkPreferences = "Morning and sunset",
            trainingStyle = "Reward-based",
            vaccinationStatus = "Up to date",
            microchipped = true,
            recallTrained = false,
            leashReliability = "Good on leash",
            emergencyContactName = "Chris",
            emergencyContactPhone = "+61 400 555 002",
            fieldVisibility = mapOf("phone" to "private", "email" to "private", "suburb" to "group"),
            updatedAt = now.minus(1, ChronoUnit.DAYS).toString(),
        ),
    )
    private val threadParticipants = mutableMapOf(
        canonicalThreadId("user_1", "user_2") to ("user_1" to "user_2"),
        canonicalThreadId("user_2", "user_3") to ("user_2" to "user_3"),
        canonicalThreadId("user_2", "user_4") to ("user_2" to "user_4"),
    )
    private val messagesByThread = mutableMapOf(
        canonicalThreadId("user_1", "user_2") to mutableListOf(
            ApiDirectMessage(
                id = "msg_1",
                threadId = canonicalThreadId("user_1", "user_2"),
                senderUserId = "user_1",
                recipientUserId = "user_2",
                body = "Hey, I can take the 09:00 slot tomorrow.",
                createdAt = now.minus(4, ChronoUnit.HOURS).toString(),
            ),
            ApiDirectMessage(
                id = "msg_2",
                threadId = canonicalThreadId("user_1", "user_2"),
                senderUserId = "user_2",
                recipientUserId = "user_1",
                body = "Perfect, locked in. Thanks!",
                createdAt = now.minus(3, ChronoUnit.HOURS).toString(),
            ),
        ),
        canonicalThreadId("user_2", "user_3") to mutableListOf(
            ApiDirectMessage(
                id = "msg_3",
                threadId = canonicalThreadId("user_2", "user_3"),
                senderUserId = "user_3",
                recipientUserId = "user_2",
                body = "Can you approve the next join request?",
                createdAt = now.minus(95, ChronoUnit.MINUTES).toString(),
            ),
            ApiDirectMessage(
                id = "msg_4",
                threadId = canonicalThreadId("user_2", "user_3"),
                senderUserId = "user_2",
                recipientUserId = "user_3",
                body = "Yes, I’ll handle it tonight.",
                createdAt = now.minus(80, ChronoUnit.MINUTES).toString(),
            ),
        ),
        canonicalThreadId("user_2", "user_4") to mutableListOf(
            ApiDirectMessage(
                id = "msg_5",
                threadId = canonicalThreadId("user_2", "user_4"),
                senderUserId = "user_4",
                recipientUserId = "user_2",
                body = "I can cover the weekend walk if needed.",
                createdAt = now.minus(45, ChronoUnit.MINUTES).toString(),
            ),
            ApiDirectMessage(
                id = "msg_6",
                threadId = canonicalThreadId("user_2", "user_4"),
                senderUserId = "user_4",
                recipientUserId = "user_2",
                body = "Let me know by 8pm so I can confirm routes.",
                createdAt = now.minus(30, ChronoUnit.MINUTES).toString(),
            ),
            ApiDirectMessage(
                id = "msg_7",
                threadId = canonicalThreadId("user_2", "user_4"),
                senderUserId = "user_2",
                recipientUserId = "user_4",
                body = "That works, please lock it in.",
                createdAt = now.minus(20, ChronoUnit.MINUTES).toString(),
            ),
        ),
    )
    private val threadReadMarkers = mutableMapOf(
        readMarkerKey("user_2", canonicalThreadId("user_1", "user_2")) to now.minus(2, ChronoUnit.HOURS),
        readMarkerKey("user_2", canonicalThreadId("user_2", "user_3")) to now.minus(70, ChronoUnit.MINUTES),
    )
    private var bookingCounter = 6
    private var bookingStatusHistoryCounter = 12
    private var holdCounter = 1
    private var postCounter = 10
    private var eventCounter = 6
    private var groupCounter = 6
    private var blackoutCounter = 3
    private var quoteCounter = 4
    private var quoteOfferCounter = 1
    private var commentCounter = 5
    private var vetCoachSessionCounter = 3
    private var vetVerificationCounter = 3
    private var moderationReportCounter = 1
    private var authInviteCounter = 1
    private var directMessageCounter = 8
    private var authSessionUserId = "user_2"
    private val blockedUsersByUser = mutableMapOf<String, MutableSet<String>>()
    private val moderationReports = mutableListOf<CommunityReport>()
    private val analyticsEvents = mutableListOf<Pair<Instant, CommunityAnalyticsEventCreateRequest>>()
    private val diagnosticEvents = mutableListOf<Pair<Instant, CommunityDiagnosticEventCreateRequest>>()
    private val quoteOffers = mutableListOf<ServiceQuoteOffer>()
    private val communityAdminUsers = setOf("admin", "user_1", "user_3")

    private fun derivePostOwner(post: CommunityPost): String {
        val explicit = post.createdBy?.trim().orEmpty()
        if (explicit.isNotEmpty()) return explicit
        val slot = kotlin.math.abs(post.id.hashCode()) % 4
        return "user_${slot + 1}"
    }

    private fun usersShareGroupMembership(userA: String, userB: String): Boolean {
        val a = userA.trim()
        val b = userB.trim()
        if (a.isBlank() || b.isBlank()) return false
        if (a == b) return true
        return groupMembers.values.any { members -> a in members && b in members }
    }

    private fun applySharePointPrecision(latitude: Double, longitude: Double, precision: String): Pair<Double, Double> {
        return if (precision == "exact") {
            latitude to longitude
        } else {
            // ~100m precision by rounding to 3 decimals.
            kotlin.math.round(latitude * 1000.0) / 1000.0 to kotlin.math.round(longitude * 1000.0) / 1000.0
        }
    }

    private fun suburbCenter(suburb: String): Pair<Double, Double>? = when (suburb.lowercase()) {
        "surry hills" -> -33.8886 to 151.2094
        "newtown" -> -33.8981 to 151.1742
        "redfern" -> -33.8928 to 151.2040
        else -> null
    }

    private fun inferUserFocusSuburb(userId: String?): Pair<String?, String> {
        val normalizedUserId = userId?.trim().orEmpty()
        if (normalizedUserId.isBlank()) {
            return null to "none"
        }
        val scoredGroups = groups
            .filter { group -> normalizedUserId in groupMembers[group.id].orEmpty() }
            .mapNotNull { group ->
                val suburb = group.suburb.trim()
                if (suburb.isBlank()) {
                    null
                } else {
                    val groupName = group.name.lowercase()
                    val isDogPark = "dog park" in groupName || "dogpark" in groupName
                    val score = (if (isDogPark) 2000 else 0) + (if (group.official) 200 else 0) + group.memberCount
                    val source = if (isDogPark) "dog_park_membership" else "group_membership"
                    Triple(score, suburb, source)
                }
            }
        val best = scoredGroups.maxWithOrNull(compareBy<Triple<Int, String, String>> { it.first }.thenBy { it.second })
            ?: return null to "none"
        return best.second to best.third
    }

    private fun defaultUserProfile(userId: String): UserProfileResponse {
        val normalized = userId.trim().ifBlank { "user_2" }
        val suburb = inferUserFocusSuburb(normalized).first ?: "Surry Hills"
        val displayName = normalized
            .replace("_", " ")
            .trim()
            .split(" ")
            .filter { token -> token.isNotBlank() }
            .joinToString(" ") { token -> token.replaceFirstChar { it.uppercase() } }
            .ifBlank { normalized }
        return UserProfileResponse(
            userId = normalized,
            displayName = displayName,
            email = "$normalized@barkwise.test",
            phone = "",
            humanRoleLabel = "Member",
            dogName = "",
            dogPhotoUrls = emptyList(),
            bio = "",
            suburb = suburb,
            favoriteSuburbs = listOf(suburb),
            fieldVisibility = mapOf("phone" to "private", "email" to "private", "suburb" to "group"),
            updatedAt = Instant.now().toString(),
        )
    }

    private fun findCommentById(commentId: String): Triple<String, Int, CommunityComment>? {
        commentsByPost.entries.forEach { (postId, comments) ->
            val index = comments.indexOfFirst { comment -> comment.id == commentId }
            if (index >= 0) {
                return Triple(postId, index, comments[index])
            }
        }
        return null
    }

    private fun withDerivedPostFields(post: CommunityPost): CommunityPost {
        val owner = derivePostOwner(post)
        if (post.latitude != null && post.longitude != null && post.createdBy != null) {
            return post
        }
        val center = suburbCenter(post.suburb)
        val lat = post.latitude ?: center?.first
        val lng = post.longitude ?: center?.second
        return post.copy(
            createdBy = owner,
            latitude = lat,
            longitude = lng,
        )
    }

    override suspend fun getRecommendations(
        userId: String?,
        category: String?,
        suburb: String?,
        minRating: Double?,
        maxDistanceKm: Double?,
        userLat: Double?,
        userLng: Double?,
    ): ServiceRecommendationsResponse {
        val explicitSuburb = suburb?.trim()?.ifBlank { null }
        val (inferredSuburb, inferredSource) = if (explicitSuburb == null) {
            inferUserFocusSuburb(userId)
        } else {
            null to "explicit_suburb"
        }
        val effectiveSuburb = explicitSuburb ?: inferredSuburb
        val suburbSource = if (explicitSuburb != null) "explicit_suburb" else inferredSource
        val recommended = getProviders(
            category = category,
            suburb = effectiveSuburb,
            userId = userId,
            includeInactive = false,
            minRating = minRating,
            maxDistanceKm = maxDistanceKm,
            userLat = userLat,
            userLng = userLng,
            query = null,
            sortBy = "relevance",
        ).let { localMatches ->
            if (effectiveSuburb != null && localMatches.isEmpty()) {
                getProviders(
                    category = category,
                    suburb = null,
                    userId = userId,
                    includeInactive = false,
                    minRating = minRating,
                    maxDistanceKm = maxDistanceKm,
                    userLat = userLat,
                    userLng = userLng,
                    query = null,
                    sortBy = "relevance",
                )
            } else {
                localMatches
            }
        }.take(6)
        return ServiceRecommendationsResponse(
            providers = recommended,
            inferredSuburb = effectiveSuburb,
            suburbSource = suburbSource,
        )
    }

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
                ?: listOf("https://images.unsplash.com/photo-1580467277788-c6e040296602?auto=format&fit=crop&w=1200&q=80"),
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
        val requestedSuburb = payload.suburb?.trim()?.ifBlank { null }
            ?: inferUserFocusSuburb(payload.userId).first
            ?: providers
                .asSequence()
                .filter { it.status == "active" }
                .filter { it.category == payload.category }
                .filter { it.ownerUserId != payload.userId }
                .firstOrNull()
                ?.suburb
            ?: "Surry Hills"
        val preferredSuburbMatches = providers
            .asSequence()
            .filter { it.status == "active" }
            .filter { it.category == payload.category }
            .filter { it.ownerUserId != payload.userId }
            .filter { it.suburb.equals(requestedSuburb, ignoreCase = true) }
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
                suburb = requestedSuburb,
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
                    body = "${payload.category.replace("_", " ")} in $requestedSuburb (${payload.preferredWindow})",
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

    override suspend fun createQuoteOffer(
        quoteRequestId: String,
        payload: ServiceQuoteOfferCreateRequest,
    ): ServiceQuoteOffer {
        val existing = quoteRequests[quoteRequestId] ?: error("Quote request not found")
        val target = existing.targets.firstOrNull { it.providerId == payload.providerId }
            ?: error("Quote target not found")
        if (target.ownerUserId != payload.actorUserId) error("Only listing owner can submit quote offer")
        if (target.status != "pending") error("Quote target already responded")
        if (payload.priceCents <= 0) error("price_cents must be greater than 0")

        val now = Instant.now()
        val normalizedCurrency = payload.currency.trim().uppercase().ifBlank { "AUD" }
        val offer = ServiceQuoteOffer(
            id = "quote_offer_${quoteOfferCounter++}",
            quoteRequestId = quoteRequestId,
            providerId = payload.providerId,
            actorUserId = payload.actorUserId,
            priceCents = payload.priceCents,
            currency = normalizedCurrency,
            proposedDate = payload.proposedDate,
            proposedTimeSlot = payload.proposedTimeSlot,
            expiresAt = payload.expiresAt,
            note = payload.note,
            status = "active",
            createdAt = now.toString(),
        )
        quoteOffers.add(0, offer)

        val summary = if (payload.note.isBlank()) {
            "Offer ${offer.currency} ${"%.2f".format(offer.priceCents / 100.0)} for ${offer.proposedDate} ${offer.proposedTimeSlot}."
        } else {
            payload.note
        }
        val updatedTarget = target.copy(
            status = "accepted",
            responseMessage = summary,
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
        quoteRequests[quoteRequestId] = ServiceQuoteRequestView(
            quoteRequest = existing.quoteRequest.copy(
                status = nextStatus,
                updatedAt = now.toString(),
            ),
            targets = updatedTargets,
        )

        notifications.add(
            0,
            AppNotification(
                id = "notif_quote_offer_${Instant.now().toEpochMilli()}",
                userId = existing.quoteRequest.userId,
                title = "New quote offer",
                body = "Provider offered ${offer.currency} ${"%.2f".format(offer.priceCents / 100.0)} for ${offer.proposedDate} ${offer.proposedTimeSlot}.",
                category = "booking",
                read = false,
                createdAt = now.toString(),
                deepLink = "quote:${existing.quoteRequest.id}",
            ),
        )
        return offer
    }

    override suspend fun getProviderInbox(
        actorUserId: String,
        includeResolved: Boolean,
        limit: Int,
    ): ProviderInboxResponse {
        val providerIdsForActor = providers
            .asSequence()
            .filter { provider -> provider.ownerUserId == actorUserId }
            .map { provider -> provider.id }
            .toSet()
        val providerById = providers.associateBy { provider -> provider.id }

        val quoteItems = quoteRequests.values
            .flatMap { requestView ->
                requestView.targets
                    .filter { target ->
                        target.ownerUserId == actorUserId &&
                            (includeResolved || target.status == "pending")
                    }
                    .map { target ->
                        val provider = providerById[target.providerId]
                        ProviderInboxItem(
                            id = "quote:${requestView.quoteRequest.id}:${target.providerId}",
                            itemType = "quote_request",
                            providerId = target.providerId,
                            providerName = provider?.name ?: target.providerName,
                            status = target.status,
                            title = "Quote request • ${requestView.quoteRequest.category.replace("_", " ")}",
                            subtitle = "${requestView.quoteRequest.preferredWindow} • ${requestView.quoteRequest.suburb}",
                            priority = if (target.status == "pending") "high" else "normal",
                            createdAt = target.createdAt,
                            dueAt = runCatching {
                                Instant.parse(target.createdAt).plus(15, ChronoUnit.MINUTES).toString()
                            }.getOrNull(),
                            quoteRequestId = requestView.quoteRequest.id,
                            customerUserId = requestView.quoteRequest.userId,
                        )
                    }
            }

        val bookingItems = bookings
            .asSequence()
            .filter { booking -> booking.providerId in providerIdsForActor }
            .filter { booking ->
                includeResolved || booking.status in setOf(
                    "requested",
                    "provider_confirmed",
                    "in_progress",
                    "reschedule_requested",
                    "rescheduled",
                )
            }
            .map { booking ->
                val provider = providerById[booking.providerId]
                ProviderInboxItem(
                    id = "booking:${booking.id}",
                    itemType = "booking",
                    providerId = booking.providerId,
                    providerName = provider?.name ?: "Provider",
                    status = booking.status,
                    title = "Booking • ${booking.petName}",
                    subtitle = "${booking.date} ${booking.timeSlot}",
                    priority = if (booking.status in setOf("requested", "reschedule_requested")) "high" else "normal",
                    createdAt = bookingStatusHistoryByBookingId[booking.id]
                        ?.maxByOrNull { entry -> entry.createdAt }
                        ?.createdAt
                        ?: now.toString(),
                    bookingId = booking.id,
                    customerUserId = booking.ownerUserId,
                )
            }
            .toList()

        val merged = (quoteItems + bookingItems)
            .sortedByDescending { item -> item.createdAt }
            .take(limit.coerceAtLeast(1))

        return ProviderInboxResponse(
            actorUserId = actorUserId,
            total = merged.size,
            items = merged,
        )
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
        bookingStatusHistoryByBookingId
            .getOrPut(booking.id) { mutableListOf() }
            .add(
                BookingStatusHistoryEntry(
                    id = "bsh_${bookingStatusHistoryCounter++}",
                    bookingId = booking.id,
                    actorUserId = payload.userId,
                    fromStatus = "none",
                    toStatus = booking.status,
                    note = "booking requested",
                    createdAt = Instant.now().toString(),
                )
            )
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
        if (
            payload.status != "rescheduled" &&
            (!payload.date.isNullOrBlank() || !payload.timeSlot.isNullOrBlank())
        ) {
            error("date/time_slot can only be provided when status is rescheduled")
        }
        if (payload.status == "rescheduled" && (payload.date.isNullOrBlank() || payload.timeSlot.isNullOrBlank())) {
            error("Rescheduled status requires date and time_slot")
        }
        val previousStatus = bookings[index].status
        val updated = bookings[index].copy(
            date = payload.date?.trim()?.ifBlank { bookings[index].date } ?: bookings[index].date,
            timeSlot = payload.timeSlot?.trim()?.ifBlank { bookings[index].timeSlot } ?: bookings[index].timeSlot,
            status = payload.status,
            note = payload.note,
        )
        bookings[index] = updated
        bookingStatusHistoryByBookingId
            .getOrPut(bookingId) { mutableListOf() }
            .add(
                BookingStatusHistoryEntry(
                    id = "bsh_${bookingStatusHistoryCounter++}",
                    bookingId = bookingId,
                    actorUserId = payload.actorUserId,
                    fromStatus = previousStatus,
                    toStatus = payload.status,
                    note = payload.note,
                    createdAt = Instant.now().toString(),
                )
            )
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

    override suspend fun getBookingStatusHistory(
        bookingId: String,
        requesterUserId: String,
    ): List<BookingStatusHistoryEntry> {
        val booking = bookings.firstOrNull { row -> row.id == bookingId } ?: error("Booking not found: $bookingId")
        val providerOwnerUserId = providers.firstOrNull { provider -> provider.id == booking.providerId }?.ownerUserId.orEmpty()
        if (requesterUserId != booking.ownerUserId && requesterUserId != providerOwnerUserId) {
            error("Only booking owner or provider can view booking history")
        }
        return bookingStatusHistoryByBookingId[bookingId]
            .orEmpty()
            .sortedBy { row -> row.createdAt }
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
        val text = payload.message.lowercase()
        val suburbHint = payload.suburb?.let { " in $it" }.orEmpty()
        val isFaq = listOf("vaccine", "vaccination", "booster", "groom", "how often").any { token ->
            text.contains(token)
        }
        val isRag = listOf("poison", "xylitol", "grape", "parvo", "vomit", "diarrhea", "diarrhoea").any { token ->
            text.contains(token)
        }
        val answer = when {
            isFaq -> {
                "Mock FAQ answer$suburbHint: for common dog care questions, keep routines consistent and confirm timing with your vet."
            }
            isRag -> {
                "Mock RAG answer$suburbHint: I pulled grounded safety context. If toxin exposure is possible, contact a vet immediately."
            }
            else -> {
                "Mock GPT fallback$suburbHint: I can help with practical next steps for your dog, services, or community questions."
            }
        }
        val answerSource = when {
            isFaq -> "faq"
            isRag -> "rag"
            else -> "gpt_fallback"
        }
        val answerBadges = when {
            isFaq -> listOf("FAQ QA", "BarkWise QA")
            isRag -> listOf("RAG Grounded", "BarkWise AI")
            else -> listOf("GPT Fallback", "Mock")
        }
        val citations = when {
            isFaq -> listOf(
                ChatCitation(
                    title = "Canine Vaccination Guidelines",
                    source = "AAHA",
                    url = "https://www.aaha.org/resources/2022-aaha-canine-vaccination-guidelines/",
                ),
            )
            isRag -> listOf(
                ChatCitation(
                    title = "Animal Poison Control Guidance",
                    source = "ASPCA Animal Poison Control",
                    url = "https://www.aspca.org/pet-care/animal-poison-control",
                ),
            )
            else -> emptyList()
        }
        conversation += ChatTurn(
            role = "assistant",
            content = answer,
            answerSource = answerSource,
            answerBadges = answerBadges,
            citations = citations,
        )
        return ChatResponse(
            answer = answer,
            conversation = conversation.toList(),
            answerSource = answerSource,
            answerBadges = answerBadges,
            citations = citations,
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
        val idx = groups.indexOfFirst { it.id == group.id }
        if (idx >= 0) {
            groups[idx] = group.copy(memberCount = members.size)
        } else {
            groups += group.copy(memberCount = members.size)
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
        postType: String?,
        userId: String?,
        query: String?,
        sortBy: String?,
        alertType: String?,
        alertStatus: String?,
        openOnly: Boolean?,
        recentHours: Int?,
        centerLat: Double?,
        centerLng: Double?,
        maxDistanceKm: Double?,
    ): List<CommunityPost> {
        val blocked = userId?.let { blockedUsersByUser[it].orEmpty() }.orEmpty()
        val cutoff = recentHours?.let { Instant.now().minus(it.toLong(), ChronoUnit.HOURS) }
        val nowInstant = Instant.now()
        val viewer = userId?.trim().orEmpty()
        val filtered = posts.map { post -> withDerivedPostFields(post) }.filter { post ->
            val owner = derivePostOwner(post)
            val matchesSuburb = suburb.isNullOrBlank() || post.suburb.equals(suburb, ignoreCase = true)
            val matchesPostType = postType.isNullOrBlank() || post.type == postType
            val matchesAlertType = alertType.isNullOrBlank() || post.alertType.equals(alertType, ignoreCase = true)
            val matchesAlertStatus = alertStatus.isNullOrBlank() || post.alertStatus.equals(alertStatus, ignoreCase = true)
            val matchesOpenOnly = openOnly != true || post.type != "lost_found" || (post.alertStatus ?: "open") == "open"
            val matchesRecency = cutoff == null || runCatching { Instant.parse(post.createdAt.orEmpty()) }.getOrNull()?.isAfter(cutoff) == true
            val matchesQuery = query.isNullOrBlank() ||
                post.title.contains(query, ignoreCase = true) ||
                post.body.contains(query, ignoreCase = true)
            val visibleForViewer = owner !in blocked
            val shareScope = post.shareScope?.lowercase() ?: "friends"
            val shareStarted = post.lastSeenAt.isNullOrBlank() || !parseInstantValue(post.lastSeenAt).isAfter(nowInstant)
            val sharePointStillActive = post.type != "share_point" ||
                post.expiresAt.isNullOrBlank() ||
                parseInstantValue(post.expiresAt).isAfter(nowInstant)
            val sharePointVisibleToViewer = post.type != "share_point" ||
                (
                    (shareStarted || owner == viewer) &&
                        when (shareScope) {
                            "community" -> true
                            else -> viewer.isNotBlank() && (viewer == owner || usersShareGroupMembership(viewer, owner))
                        }
                    )
            matchesSuburb &&
                matchesPostType &&
                matchesAlertType &&
                matchesAlertStatus &&
                matchesOpenOnly &&
                matchesRecency &&
                matchesQuery &&
                visibleForViewer &&
                sharePointStillActive &&
                sharePointVisibleToViewer
        }
        val distanceFiltered = if (maxDistanceKm != null && centerLat != null && centerLng != null) {
            filtered.filter { post ->
                val lat = post.latitude ?: return@filter false
                val lng = post.longitude ?: return@filter false
                distanceKm(centerLat, centerLng, lat, lng) <= maxDistanceKm
            }
        } else {
            filtered
        }
        return when (sortBy) {
            "newest" -> distanceFiltered.sortedByDescending { it.createdAt.orEmpty() }
            "lost_found" -> distanceFiltered
                .filter { it.type == "lost_found" }
                .sortedWith(
                    compareBy<CommunityPost> { post -> (post.alertStatus ?: "open") != "open" }
                        .thenByDescending { it.createdAt.orEmpty() },
                )
            else -> distanceFiltered
        }
    }

    override suspend fun createPost(payload: CommunityPostCreate): CommunityPost {
        val owner = payload.userId?.takeIf { it.isNotBlank() } ?: "guest_user"
        val createdAtInstant = Instant.now()
        val isSharePoint = payload.type == "share_point"
        if (isSharePoint && (payload.latitude == null || payload.longitude == null)) {
            error("share_point requires latitude and longitude")
        }
        val shareMode = if (!isSharePoint) {
            payload.contactPref
        } else if (payload.contactPref.equals("share_at", ignoreCase = true)) {
            "share_at"
        } else {
            "share_now"
        }
        val shareScope = if (!isSharePoint) {
            null
        } else if (payload.shareScope.equals("community", ignoreCase = true)) {
            "community"
        } else {
            "friends"
        }
        val sharePrecision = if (!isSharePoint) {
            null
        } else if (payload.sharePrecision.equals("exact", ignoreCase = true)) {
            "exact"
        } else {
            "approximate"
        }
        val resolvedShareAt = if (!isSharePoint) {
            payload.lastSeenAt
        } else if (shareMode == "share_now") {
            createdAtInstant.toString()
        } else {
            val scheduledAt = parseInstantOrNull(payload.lastSeenAt)
                ?: error("share_at requires ISO datetime in last_seen_at")
            if (scheduledAt.isAfter(createdAtInstant.plus(24, ChronoUnit.HOURS))) {
                error("share_at must be within 24 hours")
            }
            scheduledAt.toString()
        }
        val roundedCoordinates = if (isSharePoint) {
            applySharePointPrecision(
                latitude = payload.latitude ?: 0.0,
                longitude = payload.longitude ?: 0.0,
                precision = sharePrecision ?: "approximate",
            )
        } else {
            null
        }
        val shareExpiresAt = if (!isSharePoint) {
            null
        } else {
            val shareAtInstant = parseInstantOrNull(resolvedShareAt) ?: createdAtInstant
            val maxWindow = createdAtInstant.plus(24, ChronoUnit.HOURS)
            val oneHourAfterShare = shareAtInstant.plus(1, ChronoUnit.HOURS)
            if (oneHourAfterShare.isBefore(maxWindow)) oneHourAfterShare.toString() else maxWindow.toString()
        }
        val created = CommunityPost(
            id = "post_${postCounter++}",
            type = payload.type,
            createdBy = owner,
            title = payload.title,
            body = payload.body,
            suburb = payload.suburb,
            createdAt = createdAtInstant.toString(),
            alertType = payload.alertType,
            alertStatus = if (payload.type == "lost_found") "open" else null,
            petName = payload.petName,
            petTraits = payload.petTraits,
            lastSeenAt = resolvedShareAt,
            lastSeenLocation = payload.lastSeenLocation,
            contactPref = shareMode,
            shareScope = shareScope,
            sharePrecision = sharePrecision,
            photoUrls = payload.photoUrls,
            latitude = roundedCoordinates?.first ?: payload.latitude,
            longitude = roundedCoordinates?.second ?: payload.longitude,
            resolvedAt = null,
            resolvedNote = null,
            followUpDueAt = if (payload.type == "lost_found") createdAtInstant.plus(12, ChronoUnit.HOURS).toString() else null,
            expiresAt = when {
                payload.type == "lost_found" -> createdAtInstant.plus(72, ChronoUnit.HOURS).toString()
                isSharePoint -> shareExpiresAt
                else -> null
            },
        )
        val enriched = withDerivedPostFields(created)
        posts.add(0, enriched)
        return enriched
    }

    override suspend fun getPostComments(
        postId: String,
        userId: String?,
        limit: Int,
        offset: Int,
        includeRemoved: Boolean,
    ): List<CommunityComment> {
        if (posts.none { post -> post.id == postId }) error("Post not found: $postId")
        val blocked = userId?.let { blockedUsersByUser[it].orEmpty() }.orEmpty()
        val isAdmin = !userId.isNullOrBlank() && userId in communityAdminUsers
        val comments = commentsByPost[postId]
            .orEmpty()
            .sortedBy { comment -> comment.createdAt }
            .filter { comment ->
                if (comment.userId in blocked) return@filter false
                val isAuthor = !userId.isNullOrBlank() && comment.userId == userId
                if (comment.status != "active") {
                    if (!includeRemoved && !isAuthor && !isAdmin) return@filter false
                    if (includeRemoved && !isAuthor && !isAdmin) return@filter false
                }
                true
            }
        val safeOffset = offset.coerceAtLeast(0)
        val safeLimit = limit.coerceIn(1, 100)
        return comments.drop(safeOffset).take(safeLimit)
    }

    override suspend fun createPostComment(
        postId: String,
        payload: CommunityCommentCreateRequest,
    ): CommunityComment {
        val post = posts.firstOrNull { row -> row.id == postId }?.let { withDerivedPostFields(it) }
            ?: error("Post not found: $postId")
        val body = payload.body.trim()
        if (body.isBlank()) error("Comment body is required")
        if (body.length > 500) error("Comment body exceeds 500 characters")

        val comments = commentsByPost.getOrPut(postId) { mutableListOf() }
        val parentCommentId = payload.parentCommentId?.trim()?.ifBlank { null }
        val parentComment = if (parentCommentId != null) {
            comments.firstOrNull { comment -> comment.id == parentCommentId } ?: error("Parent comment not found")
        } else {
            null
        }
        if (parentComment != null && parentComment.status != "active") {
            error("Cannot reply to removed comment")
        }

        val created = CommunityComment(
            id = "comment_${commentCounter++}",
            postId = postId,
            userId = payload.userId,
            body = body,
            parentCommentId = parentCommentId,
            createdAt = Instant.now().toString(),
        )
        comments += created

        val postOwnerId = post.createdBy?.trim().orEmpty()
        if (postOwnerId.isNotBlank() && postOwnerId != payload.userId) {
            notifications.add(
                0,
                AppNotification(
                    id = "notif_comment_${Instant.now().toEpochMilli()}_${created.id}",
                    userId = postOwnerId,
                    title = "New comment on your post",
                    body = "${payload.userId} commented on \"${post.title}\"",
                    category = "community",
                    read = false,
                    createdAt = Instant.now().toString(),
                    deepLink = "post:$postId",
                ),
            )
        }
        if (parentComment != null && parentComment.userId != payload.userId && parentComment.userId != postOwnerId) {
            notifications.add(
                0,
                AppNotification(
                    id = "notif_comment_reply_${Instant.now().toEpochMilli()}_${created.id}",
                    userId = parentComment.userId,
                    title = "New reply to your comment",
                    body = "${payload.userId} replied in ${post.suburb}",
                    category = "community",
                    read = false,
                    createdAt = Instant.now().toString(),
                    deepLink = "post:$postId",
                ),
            )
        }
        return created
    }

    override suspend fun moderatePostComment(
        commentId: String,
        payload: CommunityCommentModerationRequest,
    ): CommunityComment {
        if (payload.requesterUserId !in communityAdminUsers) error("Only moderators can moderate comments")
        val normalizedAction = payload.action.trim().lowercase()
        if (normalizedAction !in setOf("remove", "restore")) error("Invalid moderation action")
        val (postId, commentIndex, comment) = findCommentById(commentId) ?: error("Comment not found: $commentId")
        val updated = comment.copy(
            status = if (normalizedAction == "remove") "removed_by_moderator" else "active",
            moderatedAt = Instant.now().toString(),
            moderatedBy = payload.requesterUserId,
            moderationNote = payload.note.trim().ifBlank { null },
        )
        commentsByPost[postId]?.set(commentIndex, updated)

        if (updated.userId != payload.requesterUserId) {
            notifications.add(
                0,
                AppNotification(
                    id = "notif_comment_mod_${Instant.now().toEpochMilli()}_${updated.id}",
                    userId = updated.userId,
                    title = "Comment moderation update",
                    body = "Your comment is now ${updated.status.replace("_", " ")}.",
                    category = "community",
                    read = false,
                    createdAt = Instant.now().toString(),
                    deepLink = "comment:${updated.id}",
                ),
            )
        }
        return updated
    }

    override suspend fun resolvePost(postId: String, payload: CommunityPostResolveRequest): CommunityPost {
        val postIndex = posts.indexOfFirst { post -> post.id == postId }
        if (postIndex < 0) error("Post not found: $postId")
        val current = withDerivedPostFields(posts[postIndex])
        if (current.type != "lost_found") error("Only lost/found posts can be resolved")
        val requester = payload.requesterUserId?.takeIf { it.isNotBlank() } ?: "guest_user"
        if (derivePostOwner(current) != requester) error("Only post owner can resolve this alert")
        val updated = current.copy(
            alertStatus = payload.status,
            resolvedAt = Instant.now().toString(),
            resolvedNote = payload.note.ifBlank { null },
        )
        posts[postIndex] = updated
        return updated
    }

    override suspend fun updatePost(postId: String, payload: CommunityPostUpdateRequest): CommunityPost {
        val index = posts.indexOfFirst { it.id == postId }
        if (index < 0) error("Post not found: $postId")
        val current = withDerivedPostFields(posts[index])
        val requester = payload.requesterUserId?.takeIf { it.isNotBlank() } ?: "guest_user"
        if (derivePostOwner(current) != requester) error("Only post owner can update this post")
        val nextPrecision = when {
            payload.sharePrecision.equals("exact", ignoreCase = true) -> "exact"
            payload.sharePrecision.equals("approximate", ignoreCase = true) -> "approximate"
            else -> current.sharePrecision
        }
        val nextLatitude = payload.latitude ?: current.latitude
        val nextLongitude = payload.longitude ?: current.longitude
        val adjustedCoordinates = if (
            current.type == "share_point" &&
            nextLatitude != null &&
            nextLongitude != null &&
            nextPrecision == "approximate"
        ) {
            applySharePointPrecision(nextLatitude, nextLongitude, "approximate")
        } else {
            null
        }
        val updated = current.copy(
            title = payload.title?.takeIf { it.isNotBlank() } ?: current.title,
            body = payload.body?.takeIf { it.isNotBlank() } ?: current.body,
            petName = payload.petName ?: current.petName,
            petTraits = payload.petTraits ?: current.petTraits,
            lastSeenAt = when {
                payload.clearLastSeenAt -> null
                payload.lastSeenAt != null -> payload.lastSeenAt
                else -> current.lastSeenAt
            },
            lastSeenLocation = payload.lastSeenLocation ?: current.lastSeenLocation,
            contactPref = payload.contactPref ?: current.contactPref,
            shareScope = payload.shareScope ?: current.shareScope,
            sharePrecision = nextPrecision,
            photoUrls = payload.photoUrls ?: current.photoUrls,
            latitude = adjustedCoordinates?.first ?: nextLatitude,
            longitude = adjustedCoordinates?.second ?: nextLongitude,
        )
        posts[index] = updated
        return updated
    }

    override suspend fun deletePost(postId: String, requesterUserId: String): Map<String, String> {
        val index = posts.indexOfFirst { it.id == postId }
        if (index < 0) error("Post not found: $postId")
        val current = withDerivedPostFields(posts[index])
        if (derivePostOwner(current) != requesterUserId) error("Only post owner can delete this post")
        posts.removeAt(index)
        commentsByPost.remove(postId)
        return mapOf("status" to "deleted", "post_id" to postId)
    }

    override suspend fun uploadCommunityPostPhoto(payload: CommunityPostPhotoUploadRequest): CommunityPostPhotoUploadResponse {
        val size = runCatching { java.util.Base64.getDecoder().decode(payload.dataBase64).size }.getOrDefault(0)
        return CommunityPostPhotoUploadResponse(
            url = "https://mock.barkwise.app/uploads/${payload.requesterUserId}/${payload.filename}",
            contentType = payload.contentType,
            sizeBytes = size,
        )
    }

    override suspend fun createModerationReport(payload: CommunityReportCreateRequest): CommunityReport {
        val report = CommunityReport(
            id = "report_${moderationReportCounter++}",
            reporterUserId = payload.reporterUserId,
            targetType = payload.targetType,
            targetId = payload.targetId,
            reason = payload.reason,
            details = payload.details,
            status = "pending",
            createdAt = Instant.now().toString(),
        )
        moderationReports.add(0, report)
        return report
    }

    override suspend fun getModerationReports(requesterUserId: String, includeResolved: Boolean): List<CommunityReport> {
        if (requesterUserId !in communityAdminUsers) error("Only moderators can view report queue")
        return if (includeResolved) moderationReports.toList() else moderationReports.filter { it.status == "pending" }
    }

    override suspend fun resolveModerationReport(
        reportId: String,
        payload: CommunityReportResolveRequest,
    ): CommunityReport {
        val index = moderationReports.indexOfFirst { it.id == reportId }
        if (index < 0) error("Report not found: $reportId")
        val updated = moderationReports[index].copy(
            status = payload.action,
            resolvedAt = Instant.now().toString(),
            resolvedBy = payload.requesterUserId,
            resolutionNote = payload.note.ifBlank { null },
        )
        moderationReports[index] = updated
        return updated
    }

    override suspend fun blockUser(payload: CommunityBlockUserRequest): CommunityBlockUserResponse {
        val blocked = blockedUsersByUser.getOrPut(payload.requesterUserId) { mutableSetOf() }
        blocked += payload.targetUserId
        return CommunityBlockUserResponse(
            requesterUserId = payload.requesterUserId,
            blockedUserIds = blocked.toList().sorted(),
        )
    }

    override suspend fun unblockUser(requesterUserId: String, targetUserId: String): CommunityBlockUserResponse {
        val blocked = blockedUsersByUser.getOrPut(requesterUserId) { mutableSetOf() }
        blocked -= targetUserId
        return CommunityBlockUserResponse(
            requesterUserId = requesterUserId,
            blockedUserIds = blocked.toList().sorted(),
        )
    }

    override suspend fun getBlockedUsers(requesterUserId: String): CommunityBlockUserResponse {
        val blocked = blockedUsersByUser.getOrPut(requesterUserId) { mutableSetOf() }
        return CommunityBlockUserResponse(
            requesterUserId = requesterUserId,
            blockedUserIds = blocked.toList().sorted(),
        )
    }

    override suspend fun createCommunityAnalyticsEvent(payload: CommunityAnalyticsEventCreateRequest): Map<String, String> {
        analyticsEvents += Instant.now() to payload
        return mapOf("status" to "ok")
    }

    override suspend fun getCommunityFunnel(requesterUserId: String?, windowHours: Int): CommunityFunnelMetrics {
        val cutoff = Instant.now().minus(windowHours.toLong(), ChronoUnit.HOURS)
        val recent = analyticsEvents.filter { (createdAt, _) -> createdAt.isAfter(cutoff) }.map { it.second }
        fun count(name: String): Int = recent.count { it.event == name }
        val attempts = count("lost_found_create_attempted")
        val successes = count("lost_found_create_succeeded")
        val conversion = if (attempts > 0) (successes * 100.0) / attempts else 0.0
        return CommunityFunnelMetrics(
            windowHours = windowHours,
            communityFeedViews = count("community_feed_viewed"),
            lostFoundFeedViews = count("lost_found_feed_viewed"),
            lostFoundCreateAttempts = attempts,
            lostFoundCreateSuccesses = successes,
            lostFoundResolutionActions = count("lost_found_resolved"),
            moderationReportsSubmitted = count("moderation_report_submitted"),
            blocksSubmitted = count("community_block_submitted"),
            lostFoundCreateConversionPct = conversion,
        )
    }

    override suspend fun getCommunityActivationFunnel(
        requesterUserId: String?,
        windowHours: Int,
    ): CommunityActivationFunnel {
        val cutoff = Instant.now().minus(windowHours.toLong(), ChronoUnit.HOURS)
        val scopedAnalytics = analyticsEvents.filter { (createdAt, event) ->
            createdAt.isAfter(cutoff) &&
                event.event.startsWith("activation_") &&
                (requesterUserId.isNullOrBlank() || event.userId == requesterUserId)
        }
        val scopedDiagnostics = diagnosticEvents.filter { (createdAt, event) ->
            createdAt.isAfter(cutoff) &&
                event.message.startsWith("activation_") &&
                (requesterUserId.isNullOrBlank() || event.userId == requesterUserId)
        }

        val byEvent = mutableMapOf<String, Int>()
        val byStage = mutableMapOf<String, Int>()
        val byStatus = mutableMapOf<String, Int>()

        scopedAnalytics.forEach { (_, payload) ->
            byEvent[payload.event] = (byEvent[payload.event] ?: 0) + 1
            val suffix = payload.event.removePrefix("activation_")
            val pivot = suffix.lastIndexOf('_')
            if (pivot <= 0) {
                val stage = suffix.ifBlank { "unknown" }
                byStage[stage] = (byStage[stage] ?: 0) + 1
            } else {
                val stage = suffix.substring(0, pivot).ifBlank { "unknown" }
                val status = suffix.substring(pivot + 1).ifBlank { "unknown" }
                byStage[stage] = (byStage[stage] ?: 0) + 1
                byStatus[status] = (byStatus[status] ?: 0) + 1
            }
        }

        val topFailures = scopedAnalytics
            .asSequence()
            .filter { (_, payload) -> payload.event.endsWith("_failed") }
            .sortedByDescending { (createdAt, _) -> createdAt }
            .take(25)
            .map { (createdAt, payload) ->
                CommunityActivationFailure(
                    event = payload.event,
                    createdAt = createdAt.toString(),
                    userId = payload.userId,
                    error = payload.metadata["error"].orEmpty(),
                )
            }
            .toList()

        val uniqueUsers = scopedAnalytics
            .asSequence()
            .map { (_, payload) -> payload.userId.trim() }
            .filter { userId -> userId.isNotBlank() }
            .distinct()
            .sorted()
            .toList()

        return CommunityActivationFunnel(
            windowHours = windowHours,
            requesterUserId = requesterUserId,
            activationEventCount = scopedAnalytics.size,
            activationDiagnosticCount = scopedDiagnostics.size,
            uniqueUsers = uniqueUsers,
            uniqueUserCount = uniqueUsers.size,
            lastEventAt = scopedAnalytics.maxOfOrNull { (createdAt, _) -> createdAt.toString() },
            byEvent = byEvent.toSortedMap(),
            byStage = byStage.toSortedMap(),
            byStatus = byStatus.toSortedMap(),
            topFailures = topFailures,
        )
    }

    override suspend fun createCommunityDiagnosticEvent(payload: CommunityDiagnosticEventCreateRequest): Map<String, String> {
        diagnosticEvents += Instant.now() to payload
        return mapOf("status" to "ok")
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
            locationName = payload.locationName,
            locationLatitude = payload.locationLatitude,
            locationLongitude = payload.locationLongitude,
            recurrence = payload.recurrence,
            recurrenceInterval = payload.recurrenceInterval,
            attendeeCount = 0,
            createdBy = payload.userId,
            status = if (payload.groupId == null) "approved" else "pending_approval",
        )
        events.add(0, event)
        return event
    }

    override suspend fun updateEvent(
        eventId: String,
        payload: CommunityEventUpdateRequest,
    ): CommunityEvent {
        val index = events.indexOfFirst { it.id == eventId }
        if (index < 0) error("Event not found: $eventId")
        val current = events[index]
        if (current.createdBy != payload.userId) error("Only event owner can edit this event")
        val updated = current.copy(
            title = payload.title ?: current.title,
            description = payload.description ?: current.description,
            date = payload.date ?: current.date,
            groupId = payload.groupId,
            locationName = when {
                payload.clearLocation -> null
                payload.locationName != null -> payload.locationName
                else -> current.locationName
            },
            locationLatitude = when {
                payload.clearLocation -> null
                payload.locationLatitude != null -> payload.locationLatitude
                else -> current.locationLatitude
            },
            locationLongitude = when {
                payload.clearLocation -> null
                payload.locationLongitude != null -> payload.locationLongitude
                else -> current.locationLongitude
            },
            recurrence = payload.recurrence ?: current.recurrence,
            recurrenceInterval = payload.recurrenceInterval ?: current.recurrenceInterval,
        )
        events[index] = updated
        return updated
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
        authSessionUserId = payload.userId.trim().ifBlank { authSessionUserId }
        return AuthLoginResponse(
            accessToken = "mock-token-${payload.userId}",
            tokenType = "bearer",
            userId = payload.userId,
            expiresAt = Instant.now().plus(7, ChronoUnit.DAYS).toString(),
        )
    }

    override suspend fun createAuthInvite(payload: AuthInviteCreateRequest): AuthInviteResponse {
        val email = payload.email.trim().lowercase()
        if (email.isBlank() || "@" !in email) error("Valid email is required")
        val inviteId = "ainv_${authInviteCounter++}"
        val fallbackUserId = email
            .substringBefore("@")
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "beta_user_${authInviteCounter}" }
        val targetUserId = payload.userId
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "beta_$fallbackUserId"
        val expiresAt = Instant.now()
            .plus(payload.ttlMinutes.coerceIn(5, 24 * 60).toLong(), ChronoUnit.MINUTES)
            .toString()
        val invite = AuthInviteResponse(
            inviteId = inviteId,
            userId = targetUserId,
            email = email,
            expiresAt = expiresAt,
        )
        authInvitesById[inviteId] = invite
        return invite
    }

    override suspend fun requestOtp(payload: AuthOtpRequest): AuthOtpRequestResponse {
        val invite = authInvitesById[payload.inviteId] ?: error("Invite not found")
        val email = payload.email.trim().lowercase()
        if (invite.email.lowercase() != email) error("Invite email mismatch")
        if (parseInstantValue(invite.expiresAt).isBefore(Instant.now())) error("Invite expired")
        val key = otpKey(payload.inviteId, email)
        val code = (((key.hashCode().toUInt().toLong() and 0x7FFFFFFF) % 900_000L) + 100_000L).toString()
        val expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES)
        otpCodeByInviteEmail[key] = code
        otpExpiresByInviteEmail[key] = expiresAt
        return AuthOtpRequestResponse(
            status = "otp_sent",
            expiresAt = expiresAt.toString(),
        )
    }

    override suspend fun verifyOtp(payload: AuthOtpVerifyRequest): AuthOtpVerifyResponse {
        val invite = authInvitesById[payload.inviteId] ?: error("Invite not found")
        val email = payload.email.trim().lowercase()
        if (invite.email.lowercase() != email) error("Invite email mismatch")
        if (parseInstantValue(invite.expiresAt).isBefore(Instant.now())) error("Invite expired")
        val key = otpKey(payload.inviteId, email)
        val expected = otpCodeByInviteEmail[key] ?: error("OTP not requested")
        val expiresAt = otpExpiresByInviteEmail[key] ?: Instant.EPOCH
        if (expiresAt.isBefore(Instant.now())) error("OTP expired")
        if (payload.otpCode.trim() != expected) error("Invalid OTP")
        otpCodeByInviteEmail.remove(key)
        otpExpiresByInviteEmail.remove(key)
        authSessionUserId = invite.userId
        return AuthOtpVerifyResponse(
            accessToken = "mock-otp-token-${invite.userId}",
            tokenType = "bearer",
            userId = invite.userId,
            expiresAt = Instant.now().plus(7, ChronoUnit.DAYS).toString(),
        )
    }

    override suspend fun issueFriendQr(): AuthFriendQrIssueResponse {
        val activeUserId = authSessionUserId.trim().ifBlank { "user_2" }
        val profile = userProfilesByUserId[activeUserId]
        val humanName = profile?.displayName?.trim().orEmpty().ifBlank { activeUserId }
        val dogName = profile?.dogName?.trim().orEmpty().ifBlank { "Dog" }
        val expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES).toString()
        val payload = listOf(
            activeUserId,
            humanName.replace("|", " "),
            dogName.replace("|", " "),
            expiresAt,
        ).joinToString("|")
        val token = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payload.toByteArray(Charsets.UTF_8))
        return AuthFriendQrIssueResponse(
            friendToken = token,
            friendUrl = "barkwise://friend?friend_token=$token",
            expiresAt = expiresAt,
        )
    }

    override suspend fun verifyFriendQr(payload: AuthFriendQrVerifyRequest): AuthFriendQrVerifyResponse {
        val token = payload.friendToken.trim()
        if (token.isBlank()) error("Friend token required")
        val decoded = runCatching {
            val bytes = Base64.getUrlDecoder().decode(token)
            String(bytes, Charsets.UTF_8)
        }.getOrElse { throw IllegalArgumentException("Invalid friend QR token") }
        val parts = decoded.split("|")
        if (parts.size < 4) error("Invalid friend QR token")
        val userId = parts[0].trim()
        val humanName = parts[1].trim().ifBlank { "BarkWise member" }
        val dogName = parts[2].trim().ifBlank { "Dog" }
        val expiresAt = parts[3].trim()
        if (userId.isBlank()) error("Invalid friend QR token")
        if (parseInstantValue(expiresAt).isBefore(Instant.now())) error("Friend QR expired")
        if (userId == authSessionUserId) error("Cannot add yourself")
        return AuthFriendQrVerifyResponse(
            userId = userId,
            humanName = humanName,
            dogName = dogName,
            expiresAt = expiresAt,
        )
    }

    override suspend fun logout(): AuthLogoutResponse {
        return AuthLogoutResponse(status = "ok")
    }

    override suspend fun deleteAccount(userId: String): AuthDeleteResponse {
        notifications.removeAll { notification -> notification.userId == userId }
        userProfilesByUserId.remove(userId)
        val inviteIds = authInvitesById.values
            .filter { invite -> invite.userId == userId }
            .map { invite -> invite.inviteId }
        inviteIds.forEach { inviteId ->
            authInvitesById.remove(inviteId)
            val otpKeys = otpCodeByInviteEmail.keys.filter { key -> key.startsWith("$inviteId:") }
            otpKeys.forEach { key ->
                otpCodeByInviteEmail.remove(key)
                otpExpiresByInviteEmail.remove(key)
            }
        }
        val threadIdsToDelete = threadParticipants
            .filterValues { participants ->
                participants.first == userId || participants.second == userId
            }
            .keys
            .toList()
        threadIdsToDelete.forEach { threadId ->
            threadParticipants.remove(threadId)
            messagesByThread.remove(threadId)
        }
        val readKeysToDelete = threadReadMarkers.keys
            .filter { key ->
                key.startsWith("$userId|") || threadIdsToDelete.any { threadId -> key.endsWith("|$threadId") }
            }
            .toList()
        readKeysToDelete.forEach { key -> threadReadMarkers.remove(key) }
        return AuthDeleteResponse(status = "deleted", userId = userId)
    }

    override suspend fun getUserProfile(userId: String): UserProfileResponse {
        val normalized = userId.trim()
        if (normalized.isBlank()) error("user_id is required")
        return userProfilesByUserId.getOrPut(normalized) { defaultUserProfile(normalized) }
    }

    override suspend fun upsertUserProfile(payload: UserProfileUpsertRequest): UserProfileResponse {
        val normalizedUserId = payload.requesterUserId.trim()
        if (normalizedUserId.isBlank()) error("requester_user_id is required")
        val normalizedEmail = payload.email.trim().lowercase()
        if (normalizedEmail.isNotBlank() && !normalizedEmail.contains("@")) error("Invalid email")
        val profile = UserProfileResponse(
            userId = normalizedUserId,
            displayName = payload.displayName.trim(),
            email = normalizedEmail,
            phone = payload.phone.trim(),
            humanPronouns = payload.humanPronouns.trim(),
            humanRoleLabel = payload.humanRoleLabel.trim(),
            dogName = payload.dogName.trim(),
            dogAgeMonths = payload.dogAgeMonths.coerceAtLeast(0),
            dogBreedMix = payload.dogBreedMix.trim(),
            dogSexNeuter = payload.dogSexNeuter.trim(),
            dogWeightClass = payload.dogWeightClass.trim(),
            dogPhotoUrls = payload.dogPhotoUrls
                .map { value -> value.trim() }
                .filter { value -> value.isNotBlank() }
                .distinct()
                .take(8),
            secondaryDogName = payload.secondaryDogName.trim(),
            secondaryDogAgeMonths = payload.secondaryDogAgeMonths.coerceAtLeast(0),
            secondaryDogPhotoUrl = payload.secondaryDogPhotoUrl.trim(),
            secondaryDogGender = payload.secondaryDogGender.trim().lowercase(),
            secondaryDogWeightKg = payload.secondaryDogWeightKg.trim(),
            bio = payload.bio.trim(),
            suburb = payload.suburb.trim(),
            favoriteSuburbs = payload.favoriteSuburbs
                .map { value -> value.trim() }
                .filter { value -> value.isNotBlank() }
                .distinct()
                .take(8),
            playEnergyLevel = payload.playEnergyLevel.trim(),
            playStyle = payload.playStyle.trim(),
            socialConfidence = payload.socialConfidence.trim(),
            triggerNotes = payload.triggerNotes.trim(),
            idealMatch = payload.idealMatch.trim(),
            walkPreferences = payload.walkPreferences.trim(),
            trainingStyle = payload.trainingStyle.trim(),
            feedingRules = payload.feedingRules.trim(),
            consentBoundaries = payload.consentBoundaries.trim(),
            vaccinationStatus = payload.vaccinationStatus.trim(),
            microchipped = payload.microchipped,
            recallTrained = payload.recallTrained,
            leashReliability = payload.leashReliability.trim(),
            emergencyContactName = payload.emergencyContactName.trim(),
            emergencyContactPhone = payload.emergencyContactPhone.trim(),
            fieldVisibility = payload.fieldVisibility
                .mapKeys { (key, _) -> key.trim().lowercase() }
                .mapValues { (_, value) -> value.trim().lowercase() }
                .filter { (key, value) -> key.isNotBlank() && value.isNotBlank() }
                .toMap(),
            updatedAt = Instant.now().toString(),
        )
        userProfilesByUserId[normalizedUserId] = profile
        return profile
    }

    override suspend fun getMessageThreads(userId: String, limit: Int): List<ApiMessageThread> {
        return threadParticipants
            .mapNotNull { (threadId, participants) ->
                val participantUserId = when (userId) {
                    participants.first -> participants.second
                    participants.second -> participants.first
                    else -> return@mapNotNull null
                }
                val messages = messagesByThread[threadId]
                    .orEmpty()
                    .sortedBy { message -> parseInstantValue(message.createdAt) }
                val lastMessage = messages.lastOrNull()
                val readMarker = threadReadMarkers[readMarkerKey(userId, threadId)]
                val unreadCount = messages.count { message ->
                    message.recipientUserId == userId &&
                        (readMarker == null || parseInstantValue(message.createdAt).isAfter(readMarker))
                }
                ApiMessageThread(
                    id = threadId,
                    participantUserId = participantUserId,
                    lastMessage = lastMessage?.body.orEmpty(),
                    lastMessageAt = lastMessage?.createdAt ?: now.toString(),
                    unreadCount = unreadCount,
                )
            }
            .sortedByDescending { thread -> parseInstantValue(thread.lastMessageAt) }
            .take(limit.coerceIn(1, 300))
    }

    override suspend fun getThreadMessages(threadId: String, userId: String, limit: Int): List<ApiDirectMessage> {
        val participants = threadParticipants[threadId] ?: return emptyList()
        if (userId != participants.first && userId != participants.second) return emptyList()
        val messages = messagesByThread[threadId].orEmpty().sortedBy { message -> parseInstantValue(message.createdAt) }
        val cappedLimit = limit.coerceIn(1, 500)
        return messages.takeLast(cappedLimit)
    }

    override suspend fun sendThreadMessage(threadId: String, payload: MessageSendRequest): ApiDirectMessage {
        val expectedThreadId = canonicalThreadId(payload.userId, payload.recipientUserId)
        if (threadId != expectedThreadId) error("thread_id does not match participants")
        val body = payload.body.trim()
        if (body.isBlank()) error("Message body cannot be empty")
        threadParticipants.putIfAbsent(
            expectedThreadId,
            orderedParticipants(payload.userId, payload.recipientUserId),
        )
        val message = ApiDirectMessage(
            id = "msg_${directMessageCounter++}",
            threadId = expectedThreadId,
            senderUserId = payload.userId,
            recipientUserId = payload.recipientUserId,
            body = body,
            createdAt = Instant.now().toString(),
        )
        val threadMessages = messagesByThread.getOrPut(expectedThreadId) { mutableListOf() }
        threadMessages += message
        notifications.add(
            0,
            AppNotification(
                id = "notif_msg_${directMessageCounter}",
                userId = payload.recipientUserId,
                title = "New message",
                body = "${payload.userId}: ${body.take(80)}",
                category = "message",
                read = false,
                createdAt = message.createdAt,
                deepLink = "thread:$expectedThreadId",
            ),
        )
        return message
    }

    override suspend fun markThreadRead(threadId: String, payload: MessageMarkReadRequest): Map<String, String> {
        val participants = threadParticipants[threadId] ?: return mapOf("status" to "ok", "read_seq" to "0")
        if (payload.userId != participants.first && payload.userId != participants.second) {
            return mapOf("status" to "ok", "read_seq" to "0")
        }
        threadReadMarkers[readMarkerKey(payload.userId, threadId)] = Instant.now()
        val readSeq = messagesByThread[threadId].orEmpty().size
        return mapOf(
            "status" to "ok",
            "read_seq" to readSeq.toString(),
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

    private fun canonicalThreadId(userA: String, userB: String): String {
        val ordered = listOf(userA.trim(), userB.trim()).sorted()
        return "dm_${ordered[0]}_${ordered[1]}"
    }

    private fun orderedParticipants(userA: String, userB: String): Pair<String, String> {
        val ordered = listOf(userA.trim(), userB.trim()).sorted()
        return ordered[0] to ordered[1]
    }

    private fun otpKey(inviteId: String, email: String): String {
        return "${inviteId.trim()}:${email.trim().lowercase()}"
    }

    private fun readMarkerKey(userId: String, threadId: String): String {
        return "${userId.trim()}|${threadId.trim()}"
    }

    private fun parseInstantValue(raw: String): Instant {
        return runCatching { Instant.parse(raw) }
            .recoverCatching { OffsetDateTime.parse(raw).toInstant() }
            .getOrElse { Instant.EPOCH }
    }

    private fun parseInstantOrNull(raw: String?): Instant? {
        val clean = raw?.trim().orEmpty()
        if (clean.isBlank()) return null
        return runCatching { Instant.parse(clean) }
            .recoverCatching { OffsetDateTime.parse(clean).toInstant() }
            .getOrNull()
    }

    companion object {
        fun create(): ApiService = MockApiService()
    }
}
