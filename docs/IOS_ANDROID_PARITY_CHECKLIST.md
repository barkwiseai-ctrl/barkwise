# iOS/Android Parity Checklist (BarkWise)

Last updated: 2026-03-11

Goal: ship iOS with the same user-visible journeys and backend behavior as Android, while using native iOS UI patterns.

## Core status

- [x] Auth: password login + invite OTP
- [x] Services: provider list
- [x] Community: groups list + join
- [x] Community: posts list + create lost/found
- [x] BarkAI: request/response chat
- [x] Notifications: list
- [x] Messages: thread list + thread messages + send + mark read

## Remaining parity slices (priority order)

1. Services journey parity (Journey B)
- [x] Provider details + availability slots + owner booking create/list/history + owner cancel
- [x] Provider inbox + quote request/offer actions
- [x] Services search + sort parity (`q`, `sort_by`)
- [x] Services distance/rating filter + recommendations parity

2. Community journey parity (Journey A)
- [x] Events list/create/update/RSVP/approve
- [x] Comments/replies on posts
- [x] Group invite + onboarding completion flow
- [x] Challenge participation

3. BarkAI parity
- [x] Streaming chat (`POST /chat/stream`) with incremental deltas
- [x] Profile acceptance + provider submission CTAs

4. Profile/account parity
- [x] Profile read/update (`GET/PUT /auth/profile`)
- [x] Friend QR issue/verify
- [x] Logout + account delete endpoints

5. Mobile platform parity
- [x] Push token registration on iOS (`/notifications/register-device`, `platform=ios`)
- [x] APNs + foreground/background notification handling
- [x] Offline cache parity for home surfaces

## Notes

- Exact code-level parity with Android is not required; backend contract parity is required.
- iOS should follow Apple-native UX where Android patterns do not translate directly.
