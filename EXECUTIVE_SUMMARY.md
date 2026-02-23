# Executive Summary

Last updated: 2026-02-22

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

Completed major features:
- Services marketplace for dog walking and grooming, including category filtering, provider details, and service listing onboarding from chat.
- Services quote request workflow with top-provider targeting, provider response handling, reminder notifications, and quote sprint/social proof metrics with review display.
- Provider listing management for owners, including create/edit/cancel/restore flows, owner-only permissions, and inactive listing visibility.
- AI chat assistant with memory-backed conversation history, intent routing and tool-calling, safety guardrails, pet profile capture with suggested profile card, provider onboarding flow, and SSE streaming responses.
- Community hub with official and user-created groups, nearby group discovery, join/apply flows, and lost/found post drafting.
- Community engagement expansion with events (create/approve/RSVP), group invite links/QR sharing, and the "Then vs Now" shareable pet growth card flow.
- Community safety and lost/found escalation suite with detailed alert fields, photo uploads, resolution follow-ups, saved/muted/blocked controls, moderation reporting, and funnel analytics.
- Cooperative community rewards with group challenges, member contribution tracking, badges, and cooperative score visibility.
- Services booking workflow with availability slots, booking holds, owner/provider status updates, booking calendar events, and reviews surfaced in the marketplace UI.
- Vet coach economy for verified vets with coaching session tracking, spotlight minutes accrual, and spotlight activation controls.
- RAG grounding for pet guidance that blends trusted dog-care knowledge sources with in-app providers, groups, posts, and events for context-aware responses.
- Backend-served web beta client for iPhone/desktop testing with login, services booking, community browsing, chat, and notifications.
- Android MVP app with core chat/community/services/profile/messaging screens, mock vs live API support, and offline cached home data fallback.
- Railway-hosted Android installer with a stable landing page, always-latest APK URL, and versioned release metadata for QA distribution.
- Backend production readiness: auth/session hardening, notifications feed with device registration, search/sort API upgrades, and deployable Docker/Render setup with CI smoke tests.
- Security rate-limiting and audit operations suite, including sliding-window throttles across auth/chat/community/notifications, persistent rate-limit metrics, admin audit/reset endpoints, and snapshot/threshold tooling.
- Synthetic API bot suite for QA/monitoring with scripted user journeys, seeded activity helpers, and scheduled loop automation for continuous API health checks.
