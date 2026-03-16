# Ferrari v1 Workboard

Last updated: 2026-03-09

## Work Packages

Legend:
- Stream `A` = BarkWise Standard hardening
- Stream `B` = Shared Core
- Stream `C` = Ferrari v1 provider OS

| ID | Stream | Work package | Depends on | Guided Codex hours | Status |
|---|---|---|---|---:|---|
| A1 | A | Booking/quote regression suite expansion (API + Android) | - | 45-70 | Ready |
| A2 | A | Notification dedupe and booking-status consistency checks | A1 | 30-55 | Ready |
| A3 | A | Owner journey performance instrumentation and p95 alerts | A1 | 28-45 | Ready |
| A4 | A | Standard flow release gate script for services journey | A1, A2 | 32-55 | Ready |
| B1 | B | Event outbox schema and write-path integration | - | 55-90 | Ready |
| B2 | B | Idempotency keys for booking/quote/status mutations | B1 | 48-80 | Ready |
| B3 | B | Role and permission hardening for owner/provider actions | - | 36-62 | Ready |
| B4 | B | Real-time sync contract (SSE/WebSocket payload schema) | B1 | 42-70 | Ready |
| B5 | B | Conflict resolution rules and replay tests | B2, B4 | 38-58 | Ready |
| C1 | C | Provider inbox domain model + API contract | B3 | 40-66 | Ready |
| C2 | C | Structured offer response model (price/slot/expiry) | B2, B3 | 52-86 | Ready |
| C3 | C | Availability rules v2 (recurrence/buffer/lead-time) | B2 | 58-95 | Ready |
| C4 | C | Provider inbox Android UI and interaction flows | C1 | 70-120 | Ready |
| C5 | C | Job execution protocol (check-in/checklist/completion) | B2 | 42-78 | Ready |
| C6 | C | Provider analytics v1 endpoints + cards | C1 | 38-65 | Ready |
| C7 | C | Ferrari feature flags and staged rollout controls | B3 | 20-35 | Ready |

## Sprint Mapping (Initial)

| Sprint | Focus | Target packages |
|---|---|---|
| Sprint 1 | Core safety rails + baseline tests | A1, B1, B3, C7 |
| Sprint 2 | Convergence and transactional reliability | A2, A4, B2, B4 |
| Sprint 3 | Ferrari core workflows v1 | C1, C2, C3 |
| Sprint 4 | UX delivery + execution protocol + analytics | C4, C5, C6, B5 |

## Critical Path

1. B1 -> B2 -> C2
2. B1 -> B4 -> B5
3. B3 -> C1 -> C4

## Acceptance Checklist

- [ ] Owner and provider both converge to identical booking state under retries.
- [ ] Duplicate API submissions do not create duplicate booking/quote mutations.
- [ ] Structured offers can be accepted and converted to booking through one flow.
- [ ] Provider inbox shows quote, booking request, and follow-up tasks in one queue.
- [ ] Existing BarkWise standard booking path passes full regression after Ferrari toggles on.

## Tracking Notes

- Keep Ferrari features behind explicit staging flags until A4 and B5 are green.
- Treat Stream A as non-negotiable quality floor.
- If schedule compresses, defer `C6` analytics before deferring `B2` idempotency.
