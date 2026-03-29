# Executive Summary

Last updated: 2026-03-28

Primary launch plan: [MVP_PLAY_BETA_LAUNCH_PLAN.md](/Users/yingxu/public-repos/pet-social-app/MVP_PLAY_BETA_LAUNCH_PLAN.md)

MVP scope freeze (Day 1):
- In scope journey 1: Meet up at a local park to socialize dogs.
  - Discover a local group.
  - View upcoming park meetup events.
  - RSVP to meetup.
  - Privacy-safe check-in (member group only, no precise location sharing, quorum-gated).
  - Group follow-up via community feed/messages.
- In scope journey 2: Book a groomer.
  - Discover groomers.
  - View details/availability.
  - Request/confirm booking.
  - Track booking status and receive notifications.

Out of scope until MVP ship:
- New marketplace categories beyond dog walking/grooming.
- New social game systems not directly supporting the two journeys.
- Advanced growth experiments unrelated to meetup or grooming booking completion.
- New platform surfaces that do not improve reliability/safety for the in-scope journeys.

MVP acceptance targets:
- Journey 1 can be completed end-to-end with safe defaults enabled.
- Journey 2 can be completed end-to-end from discovery to confirmed booking.
- No critical blockers in auth/session, RSVP/check-in, booking, or notification delivery.
- BarkWiseAI contentious-topic guardrails (crating, corporal punishment, ute-tray transport, outdoor restraint) are active and passing policy regression tests.

Completed major features:
- Services marketplace for dog walking and grooming, including category filtering, provider details, and service listing onboarding from chat.
- Services recommendations with suburb inference for quote requests and park/group membership signals.
- Services quote request workflow with top-provider targeting, provider response handling, reminder notifications, and quote sprint/social proof metrics with review display.
- Provider listing management for owners, including create/edit/cancel/restore flows, owner-only permissions, and inactive listing visibility.
- AI chat assistant with memory-backed conversation history, intent routing and tool-calling, safety guardrails, pet profile capture with suggested profile card, provider onboarding flow, and SSE streaming responses.
- FAQ-guided pet care answers with vetted citations for common dog-care topics and safety triage.
- Community hub with official and user-created groups, nearby group discovery, join/apply flows, and lost/found post drafting.
- Community thread comments and replies with API-backed discussion endpoints.
- Community engagement expansion with events (create/approve/RSVP), group invite links/QR sharing, and the "Then vs Now" shareable pet growth card flow.
- Community safety and lost/found escalation suite with detailed alert fields, photo uploads, resolution follow-ups, saved/muted/blocked controls, moderation reporting, and funnel analytics.
- Cooperative community rewards with group challenges, member contribution tracking, badges, and cooperative score visibility.
- Community state persistence via database snapshots for groups, memberships, posts, events, invites, comments, reports, and rewards across restarts.
- Services booking workflow with availability slots, booking holds, owner/provider status updates, booking calendar events, and reviews surfaced in the marketplace UI.
- Vet coach economy for verified vets with coaching session tracking, spotlight minutes accrual, and spotlight activation controls.
- RAG grounding for pet guidance that blends trusted dog-care knowledge sources with in-app providers, groups, posts, and events for context-aware responses.
- Backend-served web beta client for iPhone/desktop testing with login, services booking, community browsing, chat, and notifications.
- Android MVP app with core chat/community/services/profile/messaging screens, mock vs live API support, and offline cached home data fallback.
- Persistent user profile platform, including backend-stored profile fields, migration-backed schema updates, and repaired Android profile save behavior.
- Dual human-dog profile model and editor, including expanded human/pet fields, visibility controls, profile completeness indicators, and backend/API persistence updates validated by API tests.
- Android QR onboarding suite with invite/install QR generation plus in-app camera scanning to resolve invite tokens or links.
- Signed friend QR authentication endpoints for secure in-person adds, including short-lived HMAC tokens, authenticated issue/verify APIs, and self-add prevention.
- Dedicated Android Provider app lane with separate provider flavors (`providerStaging`/`providerProd`), provider deep-link scheme, and phone install automation for side-by-side owner/provider testing.
- Railway-hosted Android installer with a stable landing page, always-latest APK URL, and versioned release metadata for QA distribution.
- Backend production readiness: auth/session hardening, notifications feed with device registration, search/sort API upgrades, and deployable Docker/Render setup with CI smoke tests.
- Beta invite and OTP authentication flow with admin-issued invites, rate limiting, and delete-account cleanup.
- Direct messaging system with threaded conversations, unread counts, and Android inbox tooling for mute/pin/block workflows.
- Security rate-limiting and audit operations suite, including sliding-window throttles across auth/chat/community/notifications, persistent rate-limit metrics, admin audit/reset endpoints, and snapshot/threshold tooling.
- Synthetic API bot suite for QA/monitoring with scripted user journeys, seeded activity helpers, and scheduled loop automation for continuous API health checks.
- Google Play compliance web surface with dedicated privacy policy and account deletion pages hosted under the backend web client.
- Cross-surface calendar draft integration for Android, including "Add to calendar" actions for community meetups and service bookings with recurrence/reminder defaults.
- Child safety standards web page under the backend compliance surface, linked from privacy pages for Google Play policy readiness.
- Trusted-device authentication and dual-phone MVP bootstrap flow, including OTP-linked trusted device registration, `/auth/device/login` and `/auth/device/reset` endpoints, and Android auth-session seeding scripts for owner/provider installs.
- BarkWiseAI canine symptom triage hardening with taxonomy-backed symptom detection, conservative monitor/urgent/emergency severity classification, immediate-steps response formatting, and provider-mode toggle tooling.
- Android deterministic state-resolution layer for onboarding completion, notification/deep-link routing, provider inbox visibility, and exact booking/quote open flows, reinforced by new unit and Compose regression test suites.
- Provider workspace scheduling suite in Android profile/workspace surfaces, including appointment overview cards, day/week schedule views, booking actions (confirm/decline/reschedule), and calendar handoff for provider bookings.
- Transcript-based BarkAI chat contract across backend and Android, including multi-turn `messages` payload support, streaming/non-streaming response alignment, and readiness/status wiring for the simplified chat service.
- Simplified BarkAI service architecture with a single OpenAI-backed chat path replacing orchestration/RAG branching, while preserving legacy profile-accept/provider-submit actions and adding focused API/SSE regression coverage.
