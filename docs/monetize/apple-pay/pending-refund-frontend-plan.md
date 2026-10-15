# Apple Pay Pending Refund Frontend Plan (confirming + refund + dispute UX — UI build steps)

PLAN ref: P3-1/P3-2 gap-fill UX (no-limbo confirming, check-again, refund flows, dispute freeze copy). BA source: `docs/monetize/apple-pay/pending-refund.md`. Server truth in `docs/monetize/apple-pay/pending-refund-backend-plan.md` (verify, sweep, refunds, disputes — never redefined here).

## Purpose

Give the frontend developer the post-sheet UX procedure: confirming states with exits, on-demand recheck, refund request/refund-state rendering, and dispute-frozen copy. Follow steps 1–6 in order. No buyer ever sits in silent limbo — every ambiguous state carries its exits and its honesty copy.

## Users

- Frontend developer: builds confirming/refund/dispute surfaces + ops console screens.
- QA: executes abandon, recheck, refund-state, and freeze-copy drills (PSP sandbox).
- Buyer/support: resolves instead of waiting; refunds with references; disputes with deadlines visible.

## Functional requirements

1. Confirming states (shared pending page extended): `authorizing` + reason renders `Confirming with your bank…` (3DS/review variants name the specific wait — `Waiting for your bank's approval` vs `Verification delayed — we're checking`) with TTL-aware copy (`Usually resolves in minutes` + elapsed timer, never a spinner without context) and TWO exits always visible: `Check again` (on-demand verify call, throttled client-side with `Last checked HH:MM:SS` + cooldown to match server verify budget) and `Contact support` (deep-linked with orderCode prefilled — support entry with context, not a bare link).
2. Step 1 — recheck flow: `Check again` → verify call → non-terminal result updates reason + TTL display (waiting continues, honestly) → grant result transitions to paid rendering (shared success path — no settlement-specific success screen to drift) → cooldown disables recheck with countdown (client mirrors server verify budget; hammering disabled structurally, not just rate-limited).
3. Step 2 — sheet-closed flow: sheet dismissed without authorization → order reusable `pending` → UI offers `Try Apple Pay again` (fresh sheet, same order — stated as reusable, proven by the no-new-row behavior) alongside sibling options (abandonment never strands intent).
4. Step 3 — refund flow (per policy Q2): buyer-eligible → `Request refund` (reason select + confirm stating scope: full vs partial per policy) → submitted state (`Refund requested — reference pending`, never instant-granted copy) → gateway reference + `refunded` rendering with credit-note entry (original receipt annotated, never edited — shared with the receipts frontend pattern); ops-initiated refunds render identically (same components — buyer and ops see one truth).
5. Step 4 — dispute-frozen UX: `disputed` renders `Payment under review` + support contact + receipt references with ZERO payment actions (no retry, no cross-sell, no new-checkout nudges on frozen money — restraint rendered, matching the status-model frozen rule); evidence-deadline awareness is ops-side (buyers see `We're handling it`, not countdown pressure they cannot act on).
6. Step 5 — ops console screens (role-gated route): live order view (order + PSP live status + audit trail in one screen — no console-hopping during incidents), force-verify action (with verify-budget display), refund action (amount + reason + dual-confirm for full/partial), evidence upload (deadline clock prominent, pack-template guided per Q3). Non-ops route access → 403 page (no queue data in the error body).
7. Step 6 — sweep-transparency copy: auto-resolved states (sweep-expired/failed) render with `Resolved automatically` annotation + timestamp (machine-resolved money states labeled as such — users distinguish system actions from their own, support-ready).

## Data / API touches

- Screen → endpoint map: confirming → pending poll + on-demand verify; refund → refund request/detail reads; dispute → status reads (buyer) + evidence/verify/refund ops endpoints (console). Session cookie + CSRF; ops calls role-gated.
- Type mirror: `SettlementView {status, confirmingReason?, ttlAt?, verifyBudget{remaining, resetsAt}, refund{ref?, state?}, dispute{frozen, supportRef}}`.

## Acceptance criteria

- Limbo drill: every ambiguous state shows exits + TTL-aware copy (screenshot matrix, no bare spinners).
- Recheck drill: cooldown mirrors server budget; non-terminal updates reason display; grant transitions to shared success.
- Sheet-closed drill: reusable-pending offer with siblings; no stranded intent.
- Refund drill: request → submitted → referenced states exact; buyer/ops render identically.
- Freeze drill: disputed views show support + receipts with zero payment actions.
- Console drill: live view unifies order + PSP + audit; force actions budget-aware; non-ops 403 clean.

## Out of scope

- Sweep/verify/refund/dispute logic (backend plan above); settlement contracts (database plan above); sheet/verdict UX (authorization frontend plan); button gating (checkout frontend plan); plan downgrade UX (plans slices); bank-rail consoles.

## Open questions

- Q1: Recheck cooldown length — mirror server budget exactly, or gentler client pacing?
- Q2: Sweep-resolved annotation copy — `Resolved automatically` sufficient, or link the sweep event?
