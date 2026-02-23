package com.petsocial.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
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
                "https://loremflickr.com/640/640/bordoodle,dog?lock=101",
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
                "https://loremflickr.com/640/640/dog,walking,city?lock=102",
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
                "https://loremflickr.com/640/640/dog,park,walk?lock=103",
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
                "https://loremflickr.com/640/640/grooming,dog,newtown?lock=104",
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
                "https://loremflickr.com/640/640/dog,redfern,walk?lock=105",
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
                "https://loremflickr.com/640/640/grooming,senior,dog?lock=106",
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
                "https://loremflickr.com/640/640/brown,toy,dog,cavoodle?lock=107",
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
                "https://loremflickr.com/640/640/dog,walk,park?lock=108",
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
                "https://loremflickr.com/640/640/dog,sunshine,west?lock=109",
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
                "https://loremflickr.com/640/640/dog,grooming,melbourne?lock=110",
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
    private var bookingCounter = 6
    private var holdCounter = 1
    private var postCounter = 10
    private var eventCounter = 6
    private var groupCounter = 6
    private var blackoutCounter = 3
    private var quoteCounter = 4
    private var vetCoachSessionCounter = 3
    private var vetVerificationCounter = 3
    private var moderationReportCounter = 1
    private val blockedUsersByUser = mutableMapOf<String, MutableSet<String>>()
    private val moderationReports = mutableListOf<CommunityReport>()
    private val analyticsEvents = mutableListOf<Pair<Instant, CommunityAnalyticsEventCreateRequest>>()

    private fun derivePostOwner(post: CommunityPost): String {
        val explicit = post.createdBy?.trim().orEmpty()
        if (explicit.isNotEmpty()) return explicit
        val slot = kotlin.math.abs(post.id.hashCode()) % 4
        return "user_${slot + 1}"
    }

    private fun suburbCenter(suburb: String): Pair<Double, Double>? = when (suburb.lowercase()) {
        "surry hills" -> -33.8886 to 151.2094
        "newtown" -> -33.8981 to 151.1742
        "redfern" -> -33.8928 to 151.2040
        else -> null
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
            matchesSuburb && matchesPostType && matchesAlertType && matchesAlertStatus && matchesOpenOnly && matchesRecency && matchesQuery && visibleForViewer
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
        val created = CommunityPost(
            id = "post_${postCounter++}",
            type = payload.type,
            createdBy = owner,
            title = payload.title,
            body = payload.body,
            suburb = payload.suburb,
            createdAt = Instant.now().toString(),
            alertType = payload.alertType,
            alertStatus = if (payload.type == "lost_found") "open" else null,
            petName = payload.petName,
            petTraits = payload.petTraits,
            lastSeenAt = payload.lastSeenAt,
            lastSeenLocation = payload.lastSeenLocation,
            contactPref = payload.contactPref,
            photoUrls = payload.photoUrls,
            latitude = payload.latitude,
            longitude = payload.longitude,
            resolvedAt = null,
            resolvedNote = null,
            followUpDueAt = if (payload.type == "lost_found") Instant.now().plus(12, ChronoUnit.HOURS).toString() else null,
            expiresAt = if (payload.type == "lost_found") Instant.now().plus(72, ChronoUnit.HOURS).toString() else null,
        )
        val enriched = withDerivedPostFields(created)
        posts.add(0, enriched)
        return enriched
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
            photoUrls = payload.photoUrls ?: current.photoUrls,
            latitude = payload.latitude ?: current.latitude,
            longitude = payload.longitude ?: current.longitude,
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
        val adminUsers = setOf("admin", "user_1", "user_3")
        if (requesterUserId !in adminUsers) error("Only moderators can view report queue")
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

    override suspend fun createCommunityDiagnosticEvent(payload: CommunityDiagnosticEventCreateRequest): Map<String, String> {
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
