from app.models import Booking, CommunityEvent, CommunityPost, EventRsvpRecord, Group, GroupJoinRecord, Review, ServiceProvider

KNOWN_SUBURBS = ["Surry Hills", "Newtown", "Redfern", "Sunshine West"]

providers: list[ServiceProvider] = [
    ServiceProvider(
        id="svc_1",
        name="Happy Paws Walkers",
        category="dog_walking",
        suburb="Surry Hills",
        rating=4.8,
        review_count=128,
        price_from=25,
        description="30-60 minute neighborhood walks with photo updates.",
    ),
    ServiceProvider(
        id="svc_2",
        name="Urban Tail Walk Co",
        category="dog_walking",
        suburb="Newtown",
        rating=4.6,
        review_count=74,
        price_from=22,
        description="Reliable weekday walks and weekend pack sessions.",
    ),
    ServiceProvider(
        id="svc_3",
        name="Fresh Fur Groom Studio",
        category="grooming",
        suburb="Redfern",
        rating=4.9,
        review_count=96,
        price_from=45,
        description="Bath, nail trim, and breed-specific grooming.",
    ),
]

reviews: list[Review] = [
    Review(id="r_1", provider_id="svc_1", author="Amy", rating=5, comment="Very caring walker."),
    Review(id="r_2", provider_id="svc_1", author="Liam", rating=4, comment="On-time and friendly."),
    Review(id="r_3", provider_id="svc_3", author="Noah", rating=5, comment="Great groom every time."),
]

bookings: list[Booking] = []

community_posts: list[CommunityPost] = [
    CommunityPost(
        id="p_collenso_annika_1",
        type="group_post",
        created_by="annika",
        title="Collenso day report: Annika",
        body=(
            "Annika (black golden retriever/poodle cross) was goofy and friendly with every dog today. "
            "She kept bouncing into play bows and invited nervous dogs into calmer play loops."
        ),
        suburb="Sunshine West",
        created_at="2026-02-22T08:15:00Z",
        photo_urls=[
            "https://images.unsplash.com/photo-1518717758536-85ae29035b6d",
            "https://images.unsplash.com/photo-1548199973-03cce0bbc87b",
        ],
    ),
    CommunityPost(
        id="p_collenso_snowy_1",
        type="group_post",
        created_by="snowy",
        title="Collenso day report: Snowy",
        body=(
            "Snowy (black and white bull arab) started timid, then jumped into chase play. "
            "He still thinks he is tiny and accidentally trampled a smaller dog during a tight turn."
        ),
        suburb="Sunshine West",
        created_at="2026-02-22T08:40:00Z",
        photo_urls=[
            "https://images.unsplash.com/photo-1530281700549-e82e7bf110d6",
            "https://images.unsplash.com/photo-1517423440428-a5a00ad493e8",
        ],
    ),
    CommunityPost(
        id="p_collenso_sesame_1",
        type="group_post",
        created_by="sesame",
        title="Collenso interaction report: Sesame and Buddy",
        body=(
            "Sesame (brown/black border collie-poodle cross, schnauzer-like face) dominated fetch sprints. "
            "She became defensive when Buddy got near her ball, and both had to be pulled apart after escalation."
        ),
        suburb="Sunshine West",
        created_at="2026-02-22T09:05:00Z",
        photo_urls=[
            "https://images.unsplash.com/photo-1525253013412-55c1a69a5738",
            "https://images.unsplash.com/photo-1522276498395-f4f68f7f8454",
        ],
    ),
    CommunityPost(
        id="p_collenso_pepsi_1",
        type="group_post",
        created_by="pepsi",
        title="Collenso day report: Pepsi",
        body=(
            "Pepsi (brown/tan staffie-jack russell cross) played tough and physical today. "
            "He was wary of men early, then gradually trusted a new male handler after calm introductions."
        ),
        suburb="Sunshine West",
        created_at="2026-02-22T09:35:00Z",
        photo_urls=[
            "https://images.unsplash.com/photo-1601758228041-f3b2795255f1",
            "https://images.unsplash.com/photo-1516734212186-65266f4f17c8",
        ],
    ),
    CommunityPost(
        id="p_collenso_billie_1",
        type="group_post",
        created_by="billie",
        title="Collenso day report: Billie",
        body=(
            "Billie (tan boxer cross) is older but still full of love and joined every play cluster. "
            "She alternated affectionate check-ins with short, energetic bursts."
        ),
        suburb="Sunshine West",
        created_at="2026-02-22T10:00:00Z",
        photo_urls=[
            "https://images.unsplash.com/photo-1517849845537-4d257902454a",
            "https://images.unsplash.com/photo-1543466835-00a7907e9de1",
        ],
    ),
    CommunityPost(
        id="p_collenso_buddy_1",
        type="group_post",
        created_by="buddy",
        title="Collenso day report: Buddy",
        body=(
            "Buddy (mostly black cavoodle with white spots) played socially with most dogs. "
            "He had another escalating conflict cycle with Sesame and neither backed down without intervention."
        ),
        suburb="Sunshine West",
        created_at="2026-02-22T10:20:00Z",
        photo_urls=[
            "https://images.unsplash.com/photo-1507146426996-ef05306b995a",
            "https://images.unsplash.com/photo-1518717758536-85ae29035b6d",
        ],
    ),
    CommunityPost(
        id="p_collenso_newdog_1",
        type="group_post",
        created_by="annika",
        title="Collenso newcomer report: Maple",
        body=(
            "New dog Maple (tan kelpie cross) joined today. "
            "Maple mirrored Annika's play bows, gave Snowy cautious space, and settled into a gentle loop with Billie."
        ),
        suburb="Sunshine West",
        created_at="2026-02-22T10:45:00Z",
        photo_urls=[
            "https://images.unsplash.com/photo-1543466835-00a7907e9de1",
            "https://images.unsplash.com/photo-1537151608828-ea2b11777ee8",
        ],
    ),
    CommunityPost(
        id="p_dogpark_1",
        type="group_post",
        title="Dog park check-in: Luna",
        body="Luna joined the Surry Hills dog park crew this week.",
        suburb="Surry Hills",
        created_at="2026-02-18T08:00:00Z",
    ),
    CommunityPost(
        id="p_dogpark_2",
        type="group_post",
        title="Dog park check-in: Milo",
        body="Milo joined the sunrise zoomie circle at the park.",
        suburb="Surry Hills",
        created_at="2026-02-17T07:30:00Z",
    ),
    CommunityPost(
        id="p_dogpark_3",
        type="group_post",
        title="Dog park check-in: Maple",
        body="Maple is new in town and now part of the local dog park group.",
        suburb="Surry Hills",
        created_at="2026-02-16T16:10:00Z",
    ),
    CommunityPost(
        id="p_dogpark_4",
        type="group_post",
        title="Dog park check-in: Teddy",
        body="Teddy joined for the first fetch meetup of the week.",
        suburb="Surry Hills",
        created_at="2026-02-15T09:20:00Z",
    ),
    CommunityPost(
        id="p_dogpark_5",
        type="group_post",
        title="Dog park check-in: Nala",
        body="Nala joined the small-dogs social hour.",
        suburb="Surry Hills",
        created_at="2026-02-14T11:45:00Z",
    ),
    CommunityPost(
        id="p_dogpark_6",
        type="group_post",
        title="Dog park check-in: Archie",
        body="Archie joined and already made three new dog friends.",
        suburb="Surry Hills",
        created_at="2026-02-13T17:05:00Z",
    ),
    CommunityPost(
        id="p_dogpark_7",
        type="group_post",
        title="Dog park check-in: Poppy",
        body="Poppy joined the evening play session.",
        suburb="Surry Hills",
        created_at="2026-02-12T18:30:00Z",
    ),
    CommunityPost(
        id="p_dogpark_8",
        type="group_post",
        title="Dog park check-in: Biscuit",
        body="Biscuit joined and loves the agility tunnel setup.",
        suburb="Surry Hills",
        created_at="2026-02-11T07:10:00Z",
    ),
    CommunityPost(
        id="p_1",
        type="lost_found",
        title="Lost Beagle near Central Station",
        body="Brown/white beagle, red collar, last seen 7pm yesterday.",
        suburb="Surry Hills",
        created_at="2026-02-16T20:00:00Z",
    ),
    CommunityPost(
        id="p_2",
        type="group_post",
        title="Sunday dog park meetup",
        body="Casual meetup at 9am. Bring water and toys.",
        suburb="Newtown",
        created_at="2026-02-15T18:00:00Z",
    ),
    CommunityPost(
        id="p_3",
        type="group_post",
        title="Puppy social at Prince Alfred Park",
        body="Saturday 10am. Bring treats, leads, and water bowls.",
        suburb="Surry Hills",
        created_at="2026-02-14T08:00:00Z",
    ),
    CommunityPost(
        id="p_4",
        type="lost_found",
        title="Found tabby cat near King Street",
        body="Friendly tabby with blue collar, now safe indoors near Newtown Station.",
        suburb="Newtown",
        created_at="2026-02-13T12:00:00Z",
    ),
    CommunityPost(
        id="p_5",
        type="group_post",
        title="Evening redfern river walk",
        body="Small-group walk Thursday at 6:15pm, all calm dogs welcome.",
        suburb="Redfern",
        created_at="2026-02-12T22:00:00Z",
    ),
    CommunityPost(
        id="p_6",
        type="group_post",
        title="Dog-friendly cafe list update",
        body="Sharing updated list of dog-friendly cafes with shaded seating.",
        suburb="Surry Hills",
        created_at="2026-02-11T07:00:00Z",
    ),
    CommunityPost(
        id="p_7",
        type="lost_found",
        title="Missing French Bulldog 'Mochi'",
        body="Last seen near Cleveland St. Cream colour, harness with paw print tag.",
        suburb="Redfern",
        created_at="2026-02-10T03:00:00Z",
    ),
    CommunityPost(
        id="p_8",
        type="group_post",
        title="Beginner recall practice session",
        body="Sunday 8:30am at Sydney Park. Positive-reinforcement only.",
        suburb="Newtown",
        created_at="2026-02-09T01:00:00Z",
    ),
    CommunityPost(
        id="p_9",
        type="group_post",
        title="Senior dogs slow stroll",
        body="Low-intensity 30-minute walk for senior dogs and owners.",
        suburb="Surry Hills",
        created_at="2026-02-08T11:00:00Z",
    ),
    CommunityPost(
        id="p_10",
        type="lost_found",
        title="Found harness and lead",
        body="Found near Redfern Oval fence. Message with description to claim.",
        suburb="Redfern",
        created_at="2026-02-07T16:00:00Z",
    ),
    CommunityPost(
        id="p_11",
        type="group_post",
        title="Grooming tips swap thread",
        body="Share trusted shampoos, brushes, and de-shedding routines.",
        suburb="Newtown",
        created_at="2026-02-06T15:00:00Z",
    ),
    CommunityPost(
        id="p_12",
        type="group_post",
        title="Rainy day enrichment ideas",
        body="Puzzle toys and scent games list for apartment dogs.",
        suburb="Surry Hills",
        created_at="2026-02-05T10:00:00Z",
    ),
    CommunityPost(
        id="p_13",
        type="lost_found",
        title="Spotted wandering kelpie",
        body="Seen without owner near Waterloo edge; seemed anxious but approachable.",
        suburb="Redfern",
        created_at="2026-02-04T19:00:00Z",
    ),
    CommunityPost(
        id="p_14",
        type="group_post",
        title="Adoption support circle",
        body="New adopters meet-up, Q&A with experienced foster carers.",
        suburb="Newtown",
        created_at="2026-02-03T14:00:00Z",
    ),
    CommunityPost(
        id="p_15",
        type="group_post",
        title="Pet first-aid mini workshop",
        body="Volunteer vet nurse hosting a practical session next Wednesday.",
        suburb="Surry Hills",
        created_at="2026-02-02T13:00:00Z",
    ),
]

groups: list[Group] = [
    Group(id="g_official_surryhills", name="Surry Hills Official Pet Community", suburb="Surry Hills", member_count=342, official=True),
    Group(id="g_official_newtown", name="Newtown Official Pet Community", suburb="Newtown", member_count=221, official=True),
    Group(id="g_official_redfern", name="Redfern Official Pet Community", suburb="Redfern", member_count=167, official=True),
    Group(
        id="g_user_collenso_dogpark",
        name="Collenso Dog Park",
        suburb="Sunshine West",
        member_count=6,
        official=False,
        owner_user_id="annika",
    ),
    Group(id="g_user_dogpark_surry", name="Surry Hills Dog Park Crew", suburb="Surry Hills", member_count=26, official=False, owner_user_id="user_3"),
    Group(id="g_user_1", name="Surry Hills Corgi Club", suburb="Surry Hills", member_count=18, official=False, owner_user_id="user_1"),
    Group(id="g_user_2", name="Inner West Puppy Parents", suburb="Newtown", member_count=46, official=False, owner_user_id="user_2"),
    Group(id="g_user_3", name="Redfern Rescue Dog Crew", suburb="Redfern", member_count=33, official=False, owner_user_id="user_3"),
    Group(id="g_user_4", name="Surry Hills Early Walkers", suburb="Surry Hills", member_count=27, official=False, owner_user_id="user_4"),
    Group(id="g_user_5", name="Newtown Small Dogs Network", suburb="Newtown", member_count=52, official=False, owner_user_id="user_1"),
    Group(id="g_user_6", name="Redfern Cat & Dog Co-op", suburb="Redfern", member_count=21, official=False, owner_user_id="user_2"),
    Group(id="g_user_7", name="Surry Hills Senior Pets", suburb="Surry Hills", member_count=19, official=False, owner_user_id="user_jules"),
    Group(id="g_user_8", name="Weekend Adventure Dogs", suburb="Newtown", member_count=64, official=False, owner_user_id="user_ian"),
    Group(id="g_user_9", name="Redfern Pet Sitters Circle", suburb="Redfern", member_count=29, official=False, owner_user_id="user_zoe"),
]

group_memberships: list[GroupJoinRecord] = [
    GroupJoinRecord(group_id="g_user_collenso_dogpark", user_id="annika", status="member"),
    GroupJoinRecord(group_id="g_user_collenso_dogpark", user_id="snowy", status="member"),
    GroupJoinRecord(group_id="g_user_collenso_dogpark", user_id="sesame", status="member"),
    GroupJoinRecord(group_id="g_user_collenso_dogpark", user_id="pepsi", status="member"),
    GroupJoinRecord(group_id="g_user_collenso_dogpark", user_id="billie", status="member"),
    GroupJoinRecord(group_id="g_user_collenso_dogpark", user_id="buddy", status="member"),
    GroupJoinRecord(group_id="g_user_dogpark_surry", user_id="user_1", status="member"),
    GroupJoinRecord(group_id="g_user_dogpark_surry", user_id="user_2", status="member"),
    GroupJoinRecord(group_id="g_user_dogpark_surry", user_id="user_3", status="member"),
    GroupJoinRecord(group_id="g_user_dogpark_surry", user_id="user_4", status="member"),
    GroupJoinRecord(group_id="g_user_dogpark_surry", user_id="user_alex", status="member"),
    GroupJoinRecord(group_id="g_user_dogpark_surry", user_id="user_amy", status="member"),
    GroupJoinRecord(group_id="g_user_dogpark_surry", user_id="user_benji", status="member"),
    GroupJoinRecord(group_id="g_user_dogpark_surry", user_id="user_chloe", status="member"),
    GroupJoinRecord(group_id="g_user_dogpark_surry", user_id="user_dan", status="member"),
    GroupJoinRecord(group_id="g_user_dogpark_surry", user_id="user_ella", status="member"),
    GroupJoinRecord(group_id="g_user_dogpark_surry", user_id="user_finn", status="member"),
    GroupJoinRecord(group_id="g_user_dogpark_surry", user_id="user_gia", status="member"),
    GroupJoinRecord(group_id="g_user_dogpark_surry", user_id="user_hari", status="member"),
    GroupJoinRecord(group_id="g_user_dogpark_surry", user_id="user_ivy", status="member"),
    GroupJoinRecord(group_id="g_user_dogpark_surry", user_id="user_jasper", status="member"),
    GroupJoinRecord(group_id="g_user_dogpark_surry", user_id="user_kira", status="member"),
    GroupJoinRecord(group_id="g_user_dogpark_surry", user_id="user_leo", status="member"),
    GroupJoinRecord(group_id="g_user_dogpark_surry", user_id="user_maya", status="member"),
    GroupJoinRecord(group_id="g_user_dogpark_surry", user_id="user_nora", status="member"),
    GroupJoinRecord(group_id="g_user_dogpark_surry", user_id="user_owen", status="member"),
    GroupJoinRecord(group_id="g_user_dogpark_surry", user_id="user_piper", status="member"),
    GroupJoinRecord(group_id="g_user_dogpark_surry", user_id="user_quinn", status="member"),
    GroupJoinRecord(group_id="g_user_dogpark_surry", user_id="user_ruby", status="member"),
    GroupJoinRecord(group_id="g_user_dogpark_surry", user_id="user_sam", status="member"),
    GroupJoinRecord(group_id="g_user_dogpark_surry", user_id="user_toby", status="member"),
    GroupJoinRecord(group_id="g_user_dogpark_surry", user_id="user_val", status="pending"),
    GroupJoinRecord(group_id="g_user_1", user_id="user_amy", status="member"),
    GroupJoinRecord(group_id="g_user_2", user_id="user_lara", status="member"),
    GroupJoinRecord(group_id="g_user_3", user_id="user_noah", status="member"),
    GroupJoinRecord(group_id="g_user_4", user_id="user_eva", status="member"),
    GroupJoinRecord(group_id="g_user_5", user_id="user_mia", status="member"),
    GroupJoinRecord(group_id="g_user_6", user_id="user_hugo", status="member"),
    GroupJoinRecord(group_id="g_user_7", user_id="user_jules", status="member"),
    GroupJoinRecord(group_id="g_user_8", user_id="user_ian", status="member"),
    GroupJoinRecord(group_id="g_user_9", user_id="user_zoe", status="member"),
    GroupJoinRecord(group_id="g_user_2", user_id="guest_user", status="pending"),
    GroupJoinRecord(group_id="g_user_4", user_id="guest_user", status="member"),
    GroupJoinRecord(group_id="g_official_surryhills", user_id="guest_user", status="member"),
    GroupJoinRecord(group_id="g_user_5", user_id="user_2", status="member"),
    GroupJoinRecord(group_id="g_official_newtown", user_id="user_2", status="member"),
    GroupJoinRecord(group_id="g_user_1", user_id="user_2", status="pending"),
    GroupJoinRecord(group_id="g_user_2", user_id="user_3", status="pending"),
    GroupJoinRecord(group_id="g_user_3", user_id="user_4", status="pending"),
    GroupJoinRecord(group_id="g_user_4", user_id="user_1", status="pending"),
]

community_events: list[CommunityEvent] = [
    CommunityEvent(
        id="evt_000",
        title="Collenso Structured Play Window",
        description="Ball boundaries, decompression breaks, and supervised partner rotations.",
        suburb="Sunshine West",
        date="2026-02-23T07:30:00Z",
        group_id="g_user_collenso_dogpark",
        attendee_count=6,
        created_by="annika",
    ),
    CommunityEvent(
        id="evt_001",
        title="Surry Hills Puppy Social",
        description="Casual socialization circle at Prince Alfred Park.",
        suburb="Surry Hills",
        date="2026-02-20T10:00:00Z",
        group_id="g_official_surryhills",
        attendee_count=24,
        created_by="user_amy",
    ),
    CommunityEvent(
        id="evt_002",
        title="Newtown Recall Practice",
        description="Positive-reinforcement recall drills for all breeds.",
        suburb="Newtown",
        date="2026-02-23T08:30:00Z",
        group_id="g_user_2",
        attendee_count=17,
        created_by="user_lara",
    ),
    CommunityEvent(
        id="evt_003",
        title="Redfern Evening Pack Walk",
        description="45-minute evening group walk with water break halfway.",
        suburb="Redfern",
        date="2026-02-24T18:15:00Z",
        group_id="g_user_3",
        attendee_count=11,
        created_by="user_noah",
    ),
]

event_rsvps: list[EventRsvpRecord] = [
    EventRsvpRecord(event_id="evt_000", user_id="annika", status="attending"),
    EventRsvpRecord(event_id="evt_000", user_id="snowy", status="attending"),
    EventRsvpRecord(event_id="evt_000", user_id="sesame", status="attending"),
    EventRsvpRecord(event_id="evt_000", user_id="pepsi", status="attending"),
    EventRsvpRecord(event_id="evt_000", user_id="billie", status="attending"),
    EventRsvpRecord(event_id="evt_000", user_id="buddy", status="attending"),
    EventRsvpRecord(event_id="evt_001", user_id="guest_user", status="attending"),
    EventRsvpRecord(event_id="evt_002", user_id="user_2", status="attending"),
]

# In-memory invite token registry used for QR/deep-link onboarding flows.
group_invites: dict[str, dict[str, str]] = {}
