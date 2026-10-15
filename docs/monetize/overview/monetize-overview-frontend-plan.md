# Monetize Overview Frontend Plan (shared buyer-experience contract — UI build steps)

PLAN ref: P3 buyer UX (consistent provider choice, pending/return, status vocabulary, error states across all rails). BA source: `docs/monetize/overview/monetize-overview.md`. Server truth in `docs/monetize/overview/monetize-overview-backend-plan.md` (registry, lifecycle, codes — never redefined here).

## Purpose

Give the frontend developer the shared contract behind every money screen: provider-list rendering rules, the single pending/return experience, the canonical status vocabulary, and the error/empty states reused by checkout, history, and plan surfaces. Follow steps 1–6 in order. Slice screens (session forms, receipts, plan displays) compose these components — they never reinvent them.

## Users

- Frontend developer: builds money screens from the shared component library defined here.
- QA: executes the verbatim-order, vocabulary, and no-PayOS-favoritism drills.
- Buyer: picks any rail once, pays, lands on one consistent experience; sees the same words for the same states everywhere.

## Functional requirements

1. Shared components (one library, all money screens): `ProviderList` (renders `payment-methods` verbatim — order, names, hints, disabled reasons; zero client re-sorting, zero hardcoded favorites), `PendingPoll` (polls order status, renders canonical states, transitions nothing), `StatusBadge` (canonical status → fixed copy + color per state, no provider-native jargon anywhere), `ProblemMoney` (RFC 9457 `code` → buyer-safe message + next action), `PriceLine` (amount formatting VND integers, quote-vs-charged equality display). A screen inventing its own provider order or status words is a contract violation, grep-verified in review (no `payos-first`-shaped constants in client code).
2. Step 1 — provider-choice flow: checkout screen loads eligible methods → renders verbatim with capability hints (wallet buttons show `Available on this device` / `Not on this browser` from server hints — the client never decides eligibility) → buyer picks → explicit `provider` sent back (no preselected default highlighted beyond list position — position is ops config, not endorsement; copy never says `recommended` unless the payload says so).
3. Step 2 — pending/return flow: post-handoff land on `/app/billing/pending/:orderCode` → `PendingPoll` (3s interval, backoff on errors, stops at terminal states) renders `pending|authorizing|paid|failed|cancelled|expired` from canonical status only → provider return/cancel redirects land here too (query params read for display context only — a forged `?status=paid` changes nothing, and the UI states `Confirming with provider…` until the polled status says otherwise).
4. Step 3 — vocabulary flow: `StatusBadge` maps each canonical state to fixed buyer copy (pending `Waiting for payment`, authorizing `Confirming…`, paid `Paid`, failed `Payment failed — try another method`, cancelled `Cancelled`, expired `Link expired — start a new checkout`, refunded `Refunded`, disputed `Under review`). Provider-native codes (VNPay response codes, Stripe intent states) never reach the DOM (asserted in tests against captured payloads).
5. Step 4 — money-screen states: loading skeletons (never blank pending pages — blank reads as broken money); empty history (`No payments yet` + plan entry, not zeros); error mapping (`provider_required` → provider step focus; `unknown_provider` → list refresh; amount-mismatch → `Price changed — review` with re-quote action; `429` → cooldown + retry timer).
6. Step 5 — SEO/speed note (honest scope): money screens are authenticated SPA (`noindex`, no SEO); speed discipline = lazy billing chunk, no interval polling except pending/realtime surfaces, poll backoff + stop rules (a forgotten 3s poller on a background tab is a self-DDoS — `document.hidden` pauses all money polls, asserted in request logs).
7. Step 6 — no-favoritism rule: no rail gets visual prominence beyond list order (no badges, colors, or `popular` tags unless server-supplied); checkout A/B copy changes go through the display-order/experiment decision (backend Q2), never designer fiat.

## Data / API touches

- Screens → endpoints map: choice → `GET payment-methods` + `POST quote`; pending → `GET orders/:orderCode` (poll); history/plan → their slices' reads. All session cookie + CSRF; problem codes from the backend plan's catalog.
- Type mirror: `Provider {provider, display_name, handoff_kind, disabled_reason?}`; `OrderView {orderCode, amount_vnd, status, status_detail}`. No client eligibility logic exists (grep-verified).

## Acceptance criteria

- Verbatim drill: API order == rendered order with config present and absent (screenshot diff, no re-sort).
- Vocabulary drill: every canonical state renders its fixed copy on every money screen; zero provider-native strings in DOM snapshots.
- Forgery drill: `?status=paid` without grant changes nothing (still polling, still `Confirming…`).
- Favoritism drill: no rail prominence beyond order (visual review + constant grep).
- Poll drill: background tabs silent; terminal states stop polling; backoff on errors.
- Security drill: no `user_id`/amounts trusted from client (re-price verified server-side in fixtures).

## Out of scope

- Registry/conformance/ops-config logic (backend plan above); lifecycle map (database plan above); session forms/receipts/plan specifics (their frontend plans); rail handoff widgets (provider minors); P4/P5.

## Open questions

- Q1: `recommended` labeling — ever server-supplied, or position-only forever?
- Q2: Pending poll interval 3s — hold under P5 scale, or adaptive?
