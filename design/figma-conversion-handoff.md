# BarkWise Figma Conversion Handoff

Last verified from source: 2026-03-28

This handoff translates the current Android Compose UI into a Figma-ready design package.

Figma MCP is connected in this session and the active destination file is:
- `BarkWise Mobile App (Pro)`
- `https://www.figma.com/design/Rg3s0TQ70DRduAp4CpI61c`

This handoff now points at the Professional-plan build target used for the current conversion run.

## Source of truth

- App shell and shared chrome: `android/app/src/main/java/com/petsocial/app/ui/PetSocialApp.kt`
- Theme tokens: `android/app/src/main/java/com/petsocial/app/ui/BarkWiseTheme.kt`
- Responsive sizing: `android/app/src/main/java/com/petsocial/app/ui/DeviceProfile.kt`
- App state and tab variants: `android/app/src/main/java/com/petsocial/app/ui/PetSocialViewModel.kt`
- Shared pet roster UI: `android/app/src/main/java/com/petsocial/app/ui/components/PetRosterComponents.kt`
- Listings: `android/app/src/main/java/com/petsocial/app/ui/screens/ServicesScreen.kt`
- Community: `android/app/src/main/java/com/petsocial/app/ui/screens/CommunityScreen.kt`
- BarkAI: `android/app/src/main/java/com/petsocial/app/ui/screens/ChatScreen.kt`
- Messages: `android/app/src/main/java/com/petsocial/app/ui/screens/MessagesScreen.kt`
- Home and provider hub: `android/app/src/main/java/com/petsocial/app/ui/screens/ProfileScreen.kt`

## Product framing

Brand: BarkWise

Primary MVP journeys:
- Journey A: meet up at a park to socialize dogs
- Journey B: book a groomer

Primary owner tabs:
- Listings
- Community
- BarkAI
- Messages
- Home

Provider shell labels:
- Hub
- Inbox
- BarkAI
- Community
- Listings

Provider shell ordering in code:
- Hub
- Inbox
- BarkAI
- Community
- Listings

## Figma file recommendation

Create one design file named `BarkWise Mobile App`.

Recommended pages:
- `00 Foundations`
- `01 App Shell`
- `02 Listings`
- `03 Community`
- `04 BarkAI`
- `05 Messages`
- `06 Home`
- `07 Provider Hub`
- `08 Components`
- `09 Assets`

Recommended mobile frame set:
- `Mobile / Compact / 360 x 800`
- `Mobile / Standard / 390 x 844`
- `Mobile / Large / 430 x 932`

Use `390 x 844` as the primary owner-artboard size.

Responsive horizontal padding from code:
- Compact: `8dp`
- Standard: `12dp`
- Large: `20dp`

Common vertical rhythm from code:
- `8dp`
- `10dp`
- `12dp`

Common radii from code:
- `12dp`
- `14dp`
- `16dp`
- `20dp`
- Pill: `999dp`

## Foundations

### Color tokens

Light theme:
- Primary: `#6EA887`
- On primary: `#F7FCF8`
- Primary container: `#8FBFA3`
- On primary container: `#102219`
- Secondary: `#8FBFA3`
- Secondary container: `#D9EBDD`
- Tertiary: `#A38FBF`
- Tertiary container: `#F0ECF7`
- Background: `#F2F8F4`
- Surface: `#FFFFFF`
- Surface variant: `#B8D8C5`
- On surface: `#111111`
- On surface variant: `#344238`
- Outline: `#BFA38F`
- Outline variant: `#E8DCCF`

Dark theme:
- Background: `#0D110F`
- Surface: `#171D1A`
- Surface variant: `#2E3933`
- Surface container lowest: `#0A0E0C`
- Surface container low: `#151B18`
- Surface container: `#1B231F`
- Surface container high: `#25302B`
- Surface container highest: `#303B35`
- On background: `#E7EFE9`
- On surface variant: `#C7D6CC`
- Primary: `#8FBFA3`
- Primary container: `#3A6A53`
- Tertiary: `#BEAFD3`
- Tertiary container: `#4A395D`

Status colors called out in profile flows:
- Positive surface: `#D8F5DE`
- Positive text: `#1B5E20`
- Warning surface: `#FFF2CC`
- Warning text: `#7A5A00`
- Negative surface: `#FFDAD6`
- Negative text: `#8C1D18`

### Signature gradients

Hero header:
- `#6EA887 -> #8FBFA3 -> #B8D8C5`

Header roster chip:
- `#C9E2D4 -> #B8D4C3`

### Visual language

- Calm green-first palette with lilac reserved for conversational and AI surfaces
- Rounded cards layered on pale mint backgrounds
- Compact mobile density rather than airy spacing
- Material 3 base components, but with BarkWise gradients, photo surfaces, and badge-heavy cards
- Repeating pet-photo motif in header chips, groomer proof, and social surfaces

## App Shell

### Global shell anatomy

Top to bottom:
1. Gradient hero header
2. Listings-only search scope block
3. Active tab content
4. Five-item bottom navigation

### Hero header

Built from `HeroHeader` in the shell.

Elements:
- Rounded gradient container
- Circular mini-badge with launcher art
- `BarkWise` wordmark
- Subtitle: `Dog owners, groups, and trusted local care`
- Optional mode summary such as `Mode: Beta 1`
- Optional right-aligned roster chip with pet thumbnail and `New this week`

Sizing cues:
- Compact radius: `14dp`
- Large radius: `20dp`
- Compact logo badge: `24dp`
- Large logo badge: `34dp`

### Search scope bar

Visible under the header on Listings surfaces.

Elements:
- Section label: `Search scope`
- Current suburb label
- Manual suburb and current-location chips
- Optional `Refresh GPS` chip
- Optional manual suburb text field plus `Apply suburb`
- Range selector chip and dropdown

### Bottom navigation

Icons from code:
- Listings: service icon
- Community: people icon
- BarkAI: auto-awesome icon
- Messages or Inbox: chat bubble icon
- Home or Hub: person icon

Selected state:
- Primary container indicator
- Primary icon color
- Primary label color

Unselected state:
- On-surface-variant icon color
- On-surface-variant label color

Notification badge behavior:
- Community and Messages can show numeric badges
- Badge caps at `9+`

## Screen Inventory

### 1. Listings

Main frames to create:
- `Listings / List`
- `Listings / Map`
- `Listings / Details`
- `Listings / Quote Sheet`
- `Listings / Refine Sheet`
- `Listings / Empty`
- `Listings / Loading`
- `Listings / Provider Workspace`

List screen structure:
- Intro card with `Trusted local listings` or `Your business listings`
- Recommendation subtitle when suburb is inferred
- List and map mode chips
- Quote and refine icon actions
- Optional applied-filter helper text
- Provider results feed

Map mode:
- Same intro card and controls
- Large embedded map panel with provider markers
- Markers open provider details

Quote sheet:
- Title: `Request quote to up to 3 providers`
- Service category chips
- Preferred window field
- Pet details field
- Extra note field
- `Send` and `Close` actions

Refine sheet:
- Category chip row
- Rating dropdown
- Distance dropdown
- Search field
- Sort dropdown
- `Apply` and `Clear` actions

Provider card anatomy:
- Provider name
- Suburb and normalized category label
- Rating badge
- Optional owner label
- Optional distance label
- Horizontal badge row:
  - Vet-Checked
  - Quote Sprint tier
  - Highlighted Vet Owner
  - Responds in time
  - Local favorite
  - Group-trusted
- Up to three social proof lines
- Body copy
- Optional `Recently groomed this week` pet roster showcase
- Price line `From $X`

Details screen:
- Back text button
- Provider title and meta line
- Optional provider account label
- Horizontal image gallery
- Long description
- Review list
- Booking module:
  - Date picker button
  - `Load time slots`
  - Slot selection
  - Booking note
  - Confirm booking CTA

Provider workspace variant:
- Replaces list/map toggle with `Open Hub`
- Empty state points back to Hub listing creation

### 2. Community

Main frames to create:
- `Community / Feed`
- `Community / Feed Settings Sheet`
- `Community / Group Discovery`
- `Community / Create Group Dialog`
- `Community / Meetup Planner`
- `Community / Group Detail Sheet`
- `Community / Event Detail Sheet`
- `Community / Post Detail Sheet`
- `Community / Create Post`
- `Community / Lost + Found`
- `Community / Invite QR Scanner`
- `Community / Loading`

Feed screen order from code:
1. Meetup hero card
2. Compact action row
3. Optional scanner status label
4. Featured group priority cards
5. Feed header with sort dropdown and settings icon
6. Lens chips for `Posts` and `Lost & Found`
7. Feed cards or empty/loading state

Compact action row buttons:
- Scan invite QR
- Create group
- Find your groups
- Meetup planner

Meetup hero card:
- Title `Dog community in {suburb}`
- Stats line for groups, posts, and events
- Joined-group encouragement copy

Weather and privacy card:
- Live park weather summary
- Updated-at label
- `Refresh weather`
- `Simulate park arrival`
- Auto check-in state chips
- Safety/privacy explanation

Meetup planner card:
- Window filter chip row
- Area filter chip row
- Next event summary
- RSVP or `You are going` chip

Community stat pill:
- Compact centered numeric pill in `surfaceContainerHighest`

Group priority card:
- Group name and member count
- `Open` or `Join`
- Events lane with compact event cards
- Posts lane with compact post cards

Event discovery and feed cards:
- Event title
- Relative date and formatted date-time
- Suburb and attendance
- Recurrence and location chips
- Description excerpt
- RSVP and open-group actions
- Event-detail and report affordances in richer states

Discussion feed card expectations:
- Post type emphasis
- Title and body excerpt
- Meta row with time, group, and social/friend cues
- Save, report, block, and message affordances

Important overlays and dialogs:
- Group discovery sheet
- Feed settings sheet
- Event editor dialog
- Group detail bottom sheet
- Event detail bottom sheet
- Post detail bottom sheet
- Invite QR scanner sheet

Lost and found cues:
- Dedicated lens chip
- Urgency copy
- Safety reporting path

### 3. BarkAI

Main frames to create:
- `BarkAI / Empty`
- `BarkAI / Active Conversation`
- `BarkAI / Streaming`
- `BarkAI / Error`
- `BarkAI / Suggestion Card`
- `BarkAI / Onboarding Reply`

Screen structure:
- Full-height rounded conversation container in `surfaceContainerLowest`
- Scrollable list of message bubbles
- Error banner bubble when present
- Composer card anchored at bottom in `primaryContainer`

Bubble anatomy:
- Assistant bubble uses `surfaceContainer`
- User bubble uses `tertiaryContainer`
- Rounded `14dp` corners
- Max content width about `90%`

Composer:
- Outlined text field
- Send button with fixed visual width
- Onboarding label changes to `Reply to BarkWiseAI`

Supporting states in code:
- Empty intro card: `Start a conversation with BarkAI.`
- Profile suggestion card
- Provider suggestion card
- Bark thread switcher and new-thread affordance
- Onboarding dog-photo capture support

### 4. Messages

Main frames to create:
- `Messages / Thread List`
- `Messages / Search Results`
- `Messages / Empty Search`
- `Messages / Thread Detail`

Thread list layout:
- Icon-only filter chip row: all, unread, pinned, muted
- Notification bell with badge
- Search field
- Swipe hint text
- Rounded conversation list container

Conversation row anatomy:
- Avatar with unread border state
- Account label
- Pet names
- Last message preview
- Unread count badge
- Optional `Pinned` chip
- Optional `Muted` chip

Swipe backgrounds:
- Start-to-end: secondary-container pin action
- End-to-start: tertiary-container mute action

Thread detail screen:
- Back arrow
- Large avatar
- Account label
- Optional pet names
- Thread title
- Action chip row: read, pin/unpin, mute/unmute, block
- Bubble transcript
- Bottom composer with filled send icon button

Message bubble styles:
- Mine: primary-container, right-aligned
- Other: surface, left-aligned with avatar

### 5. Home

Main frames to create:
- `Home / Owner Hub`
- `Home / Friend QR`
- `Home / Notifications Sheet`
- `Home / Plans Sheet`
- `Home / Settings Sheet`
- `Home / Activation Sheet`
- `Home / Appointment Popup`
- `Home / Empty State`

Owner-home content themes from code:
- Signed-in identity banner when enabled
- Human profile summary
- Dog profile summary
- Completion and trust signals
- Friend count and social access points
- Joined-event planning cues
- Booking and notification summaries
- Home tile cards that open deeper sheets

Shared home tiles:
- Rounded, compact, surface-container-high cards
- Title
- Subtitle
- Optional preview
- Optional small badge
- Optional leading icon and top-right adornment

Expected owner overlays:
- Friend QR dialog and scanner
- Notifications sheet
- Plans sheet
- Settings sheet
- Help dialog
- Install QR dialog
- Security details dialog

### 6. Provider Hub

Main frames to create:
- `Provider / Hub`
- `Provider / Listings`
- `Provider / Inbox`
- `Provider / Booking Calendar`
- `Provider / Create Listing`
- `Provider / Edit Listing`
- `Provider / Blackout Dialog`
- `Provider / Quote Offer Dialog`
- `Provider / Reschedule Dialog`

Provider hub top section:
- `Provider Hub` summary card
- Listing and pending-quote counts
- Horizontal CTA row:
  - New listing
  - Refresh inbox
  - Pending quotes chip
  - Bookings workspace chip

Provider business profile card:
- Label `Business profile`
- Business name
- Chevron

Provider schedule overview:
- `Booking calendar` title
- Next appointment summary
- Stat chip row:
  - Today
  - Pending
  - Next 7 days
  - Calendar items
- `Open calendar` CTA

Provider listings lane:
- Listing title
- Category, suburb, and price
- Status badge
- `Edit`
- `Pause` or `Restore`
- `Block slot`

Provider inbox lane:
- Item title
- Provider and subtitle line
- Status badge
- Quote and booking action states

Provider appointment card:
- Booking name
- Date and time
- Owner or pet label
- Status
- Calendar, confirm, decline, reschedule, and message actions depending on state

Provider calendar sheet states:
- Month
- Week
- Schedule

## Shared Component Inventory

Build these as reusable Figma components before composing screens:
- App hero header
- Header roster chip
- Bottom navigation item with badge
- Search scope bar
- Filter chip
- Assist chip
- Status badge
- Stat pill
- Provider card
- Review row
- Pet roster thumbnail
- Pet roster showcase
- Meetup hero card
- Weather and privacy card
- Meetup planner card
- Group priority card
- Event card
- Discussion card
- Conversation row
- Conversation avatar
- Message bubble / user
- Message bubble / assistant
- Home tile card
- Provider business profile card
- Provider schedule overview card
- Provider appointment card
- Empty-state card

## Asset Inventory

Local assets worth importing into Figma:
- `android/app/src/main/res/drawable-nodpi/ic_launcher_foreground_appicon.png`
- `android/app/src/main/assets/demo/schnauzer_01.png`
- `android/app/src/main/assets/demo/schnauzer_02.png`
- `android/app/src/main/assets/demo/schnauzer_03.png`

Additional brand exploration assets:
- `design/logo-concepts/`
- `design/logo_explorations/`

## Recommended Build Order Once Figma Is Connected

1. Create the file and page structure above.
2. Add color variables for light and dark themes.
3. Add spacing, radius, and elevation tokens.
4. Build shell components first:
   - Hero header
   - Header roster chip
   - Bottom nav
   - Search scope bar
5. Build the shared cards, chips, badges, and avatars.
6. Compose Listings list, map, details, and sheets.
7. Compose Community feed, group, and detail overlays.
8. Compose BarkAI and Messages.
9. Compose Home owner surfaces and Provider Hub variants.
10. Add dark-theme copies of the shell and the major content containers.

## Notes For The Next Connected Figma Run

- Prefer componentized mobile screens over isolated one-off artboards.
- Keep owner and provider surfaces distinct because the tab order and labels differ.
- Preserve the BarkWise mint/green/lilac palette rather than default Material colors.
- Keep density compact. This app is card-dense and action-heavy.
- Reuse pet-photo treatments across Listings, Community, and Home.
- Validate sheets and dialogs as separate frames, not just inline annotations.
- Start from the already-created file `BarkWise Mobile App (Pro)` instead of creating a new duplicate.
- This document remains the source map for future refinement passes in the Professional-plan file.
