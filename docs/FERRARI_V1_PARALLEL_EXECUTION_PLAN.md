# Ferrari v1 Parallel Execution Plan

Last updated: 2026-03-09

## Objective

Prepare and deliver `Ferrari v1` (provider-first operating system) in parallel with `BarkWise Standard` reliability hardening, without splitting backend truth.

## Product Strategy

- One backend, one booking/payment/message truth.
- Two frontends with role-specific UX depth.
- Shared domain contracts first, then provider-specific power features.

## Parallel Workstreams

### Stream A: BarkWise Standard Hardening

Purpose: protect current owner and baseline provider journey quality while Ferrari modules are built.

Scope:
- Booking/quote flow reliability and regression coverage.
- Latency and sync consistency for existing services surfaces.
- Existing notification, booking history, and calendar correctness.
- Android owner-facing UX stability and instrumentation.

Estimated guided Codex hours: 180-320

### Stream B: Shared Core for Dual-App Future

Purpose: establish common primitives both standard and Ferrari rely on.

Scope:
- Role-aware auth and permission tightening for owner/provider actor paths.
- Event outbox + idempotency keys for booking/quote/status writes.
- Real-time update channel contract (SSE/WebSocket + push fallback semantics).
- Shared domain schema extension points for structured offers and provider operations.

Estimated guided Codex hours: 220-360

### Stream C: Ferrari v1 Provider OS

Purpose: build provider-side "cannot-live-without" workflows.

Scope:
- Unified provider inbox (quotes, booking requests, status tasks).
- Structured offer responses (price, slot options, expiration, message).
- Availability rules v2 (recurrence, lead time, buffers, max jobs/day).
- Job execution flow (check-in, checklist, completion, proof artifacts).
- Provider analytics v1 (response SLA, conversion, repeat booking).

Estimated guided Codex hours: 300-520

## Dependency Rules

- Stream A and Stream B can start immediately.
- Stream C starts in parallel but ships modules behind feature flags.
- Stream C production release depends on Stream B contracts for eventing/idempotency.
- Stream A regression suites gate every Stream C release candidate.

## 6-Week Start Sequence

### Weeks 1-2

- Freeze shared contract decisions for booking, quote, and status events.
- Implement event outbox skeleton and idempotency handling.
- Define provider inbox data contract and Ferrari feature flags.
- Add baseline reliability test gates for current standard booking and quote paths.

### Weeks 3-4

- Deliver structured offer API and storage model.
- Deliver provider inbox API and Android data-layer integration stubs.
- Deliver availability rules v2 API with compatibility fallbacks to current slots.
- Add end-to-end sync tests for owner-provider state convergence.

### Weeks 5-6

- Ship provider inbox UI v1 and structured offer interactions.
- Ship job execution protocol v1 (check-in -> in-progress -> completion evidence).
- Add provider analytics summary endpoint and first dashboard cards.
- Run stability pass focused on notification dedupe and conflict handling.

## Ferrari v1 Release Gates

- No booking integrity regressions in existing owner flow.
- Owner and provider views converge to same booking state under retries and duplicate requests.
- Structured offer path can convert quote to booking without manual data fixes.
- Provider inbox p95 load time under agreed threshold in staging.
- Event dedupe and replay tests pass for all booking/quote/status write endpoints.

## Resourcing Model

Recommended allocation:
- 35% Stream A
- 30% Stream B
- 35% Stream C

Total guided Codex hours (v1 prep + delivery target): 700-1,200

## Immediate Next Actions

1. Approve API contract additions listed in `docs/FERRARI_V1_WORKBOARD.md`.
2. Start Stream B eventing/idempotency tasks and Stream A regression tasks in the same sprint.
3. Enable Ferrari feature flags in staging only.
4. Start Stream C inbox + structured offers behind flags after Stream B schema migration lands.
