from app.models import Booking, CommunityEvent, CommunityPost, EventRsvpRecord, Group, GroupJoinRecord, Review, ServiceProvider

KNOWN_SUBURBS = ["Surry Hills", "Newtown", "Redfern", "Sunshine West"]

providers: list[ServiceProvider] = []
reviews: list[Review] = []
bookings: list[Booking] = []
community_posts: list[CommunityPost] = []
groups: list[Group] = []
group_memberships: list[GroupJoinRecord] = []
community_events: list[CommunityEvent] = []
event_rsvps: list[EventRsvpRecord] = []
group_invites: dict[str, dict[str, str]] = {}
