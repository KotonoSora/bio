# Order Status Model (canonical payment state machine)

PLAN ref: P3-1/P3-2 (unifies the per-provider `status` enums into one canonical machine). Provider events drive transitions; this doc owns the map.

## Purpose

Define the single order lifecycle every provider path shares: one canonical `status` enum, the legal transitions, and exactly which actor may fire each one. Each provider minor carries its own status list today, risking drift (is a late SePay transfer `expired` or linkable? does a Stripe dispute freeze or reverse?). This doc is the arbiter — provider docs map their native states onto it, and any new state must be added here first.

## Users

- Provider grant/sweep/ops handlers: transition orders only along legal edges.
- Buyer-facing pending page, history page, and receipts: render one consistent vocabulary.
- Support/ops: reads any order and knows precisely what can still happen to it.

## Functional requirements

1. Canonical enum on `orders.status`: `pending` (created, awaiting money) → `authorizing` (charge/sheet in flight, card/wallet rails) → `paid` (terminal success) | `failed` (terminal, provider-declined) | `cancelled` (terminal, buyer/ops cancel) | `expired` (terminal, validity lapsed unpaid) | `refunded` (terminal, money returned) | `disputed` (frozen, chargeback/dispute open) → `chargeback_reversed` (terminal, dispute lost). `paid` additionally feeds entitlements; `disputed` freezes them.
2. Transition map (legal edges only): `pending → authorizing|paid|failed|cancelled|expired`; `authorizing → paid|failed|expired`; `paid → refunded|disputed`; `disputed → paid|chargeback_reversed` (won/lost); every other edge is illegal and rejected in code + tests. Terminal states (`paid` except toward refund/dispute, `failed`, `cancelled`, `expired`, `refunded`, `chargeback_reversed`) never re-enter `pending`/`authorizing`.
3. Actor authority: `pending → paid` only via verified provider confirmations (webhook/callback/IPN/sync grant or status-query/sweep verify) through each provider's atomic, amount-checked transition; `→ cancelled` via buyer/ops cancel endpoints (plus provider-side cancels mirrored in); `→ expired` via the lifecycle/sweep jobs; `→ refunded|disputed|chargeback_reversed` via each provider's settlement handlers. The buyer client and return pages transition nothing — they poll and render.
4. Late-event rule (uniform): confirmations arriving for terminal orders (`cancelled`/`expired`) are rejected with logging, never granted — ops may re-link only through the audited exception flows of the rail providers (SePay/NAPAS), never automatically.
5. Provider native-state mapping table (lives in code, specified here): each provider minor documents how its native states (e.g. VNPay response codes, ZaloPay query results, Stripe intent statuses, rail sync outcomes) map onto the canonical enum — including which native states are non-terminal and therefore never transition anything.
6. Audit: every transition writes `audit_log{actor, action, target, at}` with from→to + triggering event id; the full per-order trail is replayable for support.

## Data / API touches

- Constrains `orders.status` (check constraint or application-guard + migration test asserting no off-enum values). Transition attempts run inside the same transactions as the provider grant/sweep/ops writes. All PG via `worker/db/*`; RLS preserved.
- Buyer-visible mapping: `GET /api/billing/orders/:orderCode` exposes `{status, status_detail}` where `status` is canonical and `status_detail` is a human string — provider-native codes never leak to the client.
- Conformance test: a matrix test fires every legal edge per provider mapping and asserts every illegal edge is refused — required to pass before any provider-minor change ships.

## Acceptance criteria

- Cross-provider drill: one order per provider driven through success, cancel, expiry, and (where applicable) refund/dispute — all land on the specified canonical states with complete audit trails.
- Illegal-edge drill (e.g. `expired → paid` direct, `failed → authorizing`): refused at the transition layer on every provider path; only the audited exception flows can move terminal rail orders.
- Late-confirmation drill on `cancelled`/`expired` orders across all eight providers: zero grants, all logged.
- Status-enum probe: no off-enum value writable via any endpoint (constraint + test).
- Buyer-page drill: each canonical state renders the specified UX (pending/confirms/success/failure/expired/refunded/disputed) with no provider-native jargon.

## Out of scope

- Provider verification and grant mechanics (their own minors); dispatcher and provider selection (see `payment/checkout-orchestration.md`); shared security rules (see `payment/payment-security.md`).
- Entitlement rows and periods (`billing/`); plan matrix (`plans/`); receipts (`billing/invoices-receipts.md`); P4/P5.

## Open questions

- Q1: `disputed → paid` (dispute won) — restore original `period_end`, or extend by the frozen duration?
- Q2: Do we need a `partially_refunded` state in v1, or does the refunds ledger cover partials without a status?
