# Plan MVP and Google Play Beta Launch

Last updated: 2026-02-27

## Goal

Ship a stable closed beta on Google Play for the MVP journeys while enforcing BarkWiseAI safety and welfare policy guardrails.

## Scope

In scope for launch:
- Journey A: local dog social meetup flow (discover group -> RSVP -> check-in -> follow-up).
- Journey B: groomer booking flow (discover -> details/availability -> request/confirm -> status updates).
- BarkWiseAI policy safety baseline for high-risk and contentious dog-care topics.

Out of scope until after closed beta:
- New marketplace categories not required for Journey A/B completion.
- Net-new social experiments not tied to reliability or safety.
- Policy expansion areas not yet implemented (for example, euthanasia/adoption dangerous-dog special handlers).

## BarkWiseAI Safety Workstream (Integrated)

Current policy baseline in backend:
- High-risk safe mode routes medical-risk prompts to trusted sources first and restricts unsafe response patterns.
- Crating policy uses discourage-first guidance, blocks long-duration/long-term plans, blocks young-puppy urine-holding guidance, and gives a de-escalation ladder toward no routine crating where possible.
- Corporal punishment policy blocks pain/fear-based guidance and redirects to humane, reward-based approaches.
- Ute/truck tray transport policy is country-aware:
  - AU/NZ mode acknowledges working-dog reality but discourages routine open-tray short-lead restraint and prioritizes transition to safer standards.
  - Global mode uses stricter default language against routine open-tray/truck-bed restraint.
- Outdoor restraint policy is firm and global: no long-term tethering/chaining; temporary restraint only for immediate safety and shortest duration.

Core behavior rule:
- Welfare-first, least-restrictive, de-escalation-downward by default.

## Closed Beta Gating

Automated gates:
1. Android release and closed-beta readiness report:
   - `./android/scripts/play_closed_beta_checklist.sh`
2. BarkWiseAI policy regression checks:
   - `backend/.venv/bin/python -m pytest backend/tests/test_ai_orchestrator_rag_gate.py`
   - optional focused run:
   - `backend/.venv/bin/python -m pytest backend/tests/test_ai_orchestrator_rag_gate.py -k "crate or policy or ute or tether or country"`
3. Chat API response contract sanity:
   - `backend/.venv/bin/python -m pytest backend/tests/test_api.py -k "chat_"`

Manual gates:
1. Play Console closed track setup, content declarations, and rollout.
2. Physical-device install + invite QR validation.
3. Closed-beta policy prompt sampling across high-risk and contentious topics.

## Closed Beta Iteration Loop (Model + Policy)

Yes, BarkWiseAI can be updated continuously during closed testing.

Safe update loop:
1. Collect tester prompts/feedback by policy area (crate, transport, restraint, punishment, medical high-risk).
2. Apply small policy/config/data updates (JSON resources, FAQ/routing guards, trusted-source weighting).
3. Run policy and chat regression tests.
4. Tag and log a `policy_version` in release notes for cohort comparisons.
5. Promote only when no safety regressions are observed.

## BarkWiseAI Control-Plane Alignment (vs AI control-plane + low-token target)

Compared to the proposed "AI control-plane + low-token" architecture, current BarkWiseAI is partially aligned.

| Area | Current status | Evidence | Gap vs target |
|---|---|---|---|
| Action-first orchestration | Partial | Tools + executor exist (`backend/app/services/ai_orchestrator.py:53`, `backend/app/services/ai_orchestrator.py:2000`) | Tool surface is narrow: no AI tools for booking/availability/calendar/messages despite APIs existing (`android/app/src/main/java/com/petsocial/app/data/ApiService.kt:99`, `android/app/src/main/java/com/petsocial/app/data/ApiService.kt:123`, `android/app/src/main/java/com/petsocial/app/data/ApiService.kt:335`, `android/app/src/main/java/com/petsocial/app/data/ApiService.kt:305`) |
| Small router, big model only on fallback | Weak | Two model calls per turn when LLM is on: planner + answer (`backend/app/services/ai_orchestrator.py:1556`, `backend/app/services/ai_orchestrator.py:2396`) | Opposite of cost-minimizing routing |
| Structured I/O | Strong | Strict JSON planner prompt + sanitization + arg allowlist + max tool calls (`backend/app/services/ai_orchestrator.py:1533`, `backend/app/services/ai_orchestrator.py:1603`, `backend/app/services/ai_orchestrator.py:114`) | Good foundation |
| Context diet | Partial | Sends only recent conversation slice (last 8) and can summarize RAG for symptom flows (`backend/app/services/ai_orchestrator.py:2382`, `backend/app/services/ai_orchestrator.py:2372`) | Still sends large payloads often (`tool_results`, profile, `rag_context`) |
| Caching/precompute | Weak | Retrieval recomputes each request, including provider queries (`backend/app/services/rag_retriever.py:68`, `backend/app/services/rag_retriever.py:461`) | No response cache / retrieval pack cache / cost cache |
| Workflow state machines | Partial | Provider onboarding has explicit state machine (`backend/app/services/ai_orchestrator.py:1452`) | No equivalent state machine for booking, RSVP, messaging |
| Token/cost guardrails | Weak | Request rate limits exist (`backend/app/routers/chat.py:19`); text/tool arg length guards exist (`backend/app/services/ai_orchestrator.py:115`, `backend/app/services/ai_orchestrator.py:2889`) | No token usage tracking, per-intent budgeting, or max-output controls on model calls |

Net: BarkWise already has the right orchestration skeleton, but not yet the high-function, low-token control plane.

Biggest blockers:
1. Limited AI tool coverage for transactional actions.
2. Two-model-call pattern on most turns.
3. No caching/token observability layer.

Decision point (using the 3-rule prioritization):
1. Expand BarkWiseAI actions to booking/availability/calendar/messages (best for AI-to-booking completion).
2. Cut token burn first via one-call/zero-call paths for APP intents + caching (best for scalability while preserving trust).
3. Community automation actions next (best for community retention).

## Launch Readiness Status

- MVP journey implementation: in progress and testable.
- Google Play closed-beta automation: in place (`android/scripts/play_closed_beta_checklist.sh`).
- BarkWiseAI safety baseline: in place and integrated into launch gating.
- Remaining pre-launch focus: tester cohort execution, policy telemetry review, and Play Console submission.
