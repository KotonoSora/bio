# Apple Pay Checkout Database Plan (orders columns + association file — no new tables)

PLAN ref: P3-1 adapted (tokenized sheet per explicit request; same `orders` table + plan/product flow) + S-1/S-6. BA source: `docs/monetize/apple-pay/checkout.md`. Consumed by the apple-pay checkout backend plan (order creation + sheet params + token intake; splits next to this file). `orders` DDL lives in the checkout-session database plan (next in queue — `provider`, `psp_txn_id`, `psp_name`, `provider_ref` columns defined once there; this file constrains their applepay values, never redefines them). Charge/grant rows in the authorization plans; settlement rows in the pending-refund plans. No migration — this slice creates zero tables.

## Purpose

Define the Apple Pay checkout data contract without owning storage: which order columns the rail sets, the domain-association static file Apple verifiers require, the ephemeral merchant-session rule (Apple-issued, single-use, minutes TTL — never persisted), and the one-charge-in-flight serialization that makes double-clicks collapse. No `CREATE TABLE` here by design — the rail writes rows owned by the session slice.

## Users

- Backend developer: sets `provider="applepay"` + PSP references through the session insert path; serves the association file byte-identical.
- QA: proves no-dead-button gating, amount binding, single-row double-click, and association-file reachability.
- Ops: runs the Apple-side launch checklist (Merchant ID, certificates, domain verification) outside code.

## Functional requirements

1. Consumed columns (owned by the checkout-session database plan): `orders.provider = "applepay"` (immutable after insert — rail switching is cancel-then-new, never an update); `psp_txn_id` (PSP charge reference, UNIQUE — duplicate-charge detection); `psp_name` (which PSP settled, for joint Google/Apple adapter accounting per Q1); `provider_ref` (anti-replay key component with `orderCode`); `amount_vnd INT` (integer VND, no floats — sheet `total.amount` string formatted from it, normalization confirmed in sandbox).
2. `orderCode` generation: numeric int64 unique (`Date.now()*1000 + rand` per BA, uniqueness enforced by the UNIQUE constraint with insert-retry, not by algorithm trust — clock-skew collisions fail closed into retry, never into duplicates).
3. Domain-association file (static, not DB): `/.well-known/apple-developer-merchantid-domain-association` served byte-identical, HTTP 200, no redirect, reachable by Apple verifiers (SPA fallback exclusion asserted — the fallback swallowing this path is the classic launch failure). Content managed as a verified static asset per environment (prod + staging domains registered and verified; re-confirmed after every deploy touching routing).
4. Merchant sessions: ephemeral and Apple-issued (per-transaction, single-use, minutes TTL) — never stored in PG, never cached beyond the sheet's lifetime (a persisted session would be a replayable payment capability; absence from storage is the guarantee).
5. Serialization rule: one charge attempt in flight per order (concurrent authorize calls serialize — second caller waits on or observes the first, never fires a parallel charge). Enforced in service with row locking (owned by the authorization slice; the rule lives here because double-click starts at checkout).
6. Buyer-email claim: `buyer_email` column on the order (owned by session DDL) binds course/digital delivery in the shop major (delivery reads the paid grant, never the checkout payload).
7. STACK NOTE (containerized correction to BA text): secrets/certificates via environment/vault, never `wrangler secret`/`.dev.vars` (those belong to the superseded Workers default). PSP keys + Merchant Identity Certificate rotate per the shared rotation drill.

## Data / API touches

- DDL: none — assertion: no `CREATE TABLE` for checkout state anywhere under `docs/monetize/apple-pay/` for this slice (review greps it). Consumed columns: session slice's `orders` (rule 1). Static asset: association file (rule 3). Ephemeral: merchant sessions (rule 4, never persisted).

## Acceptance criteria

- Column drill: applepay orders carry immutable `provider` + PSP refs; double-click yields one row, one `orderCode`.
- Association drill: byte-identical 200, no redirect, SPA fallback excluded; domain Verified in Apple tooling per environment.
- Binding drill: client-altered totals rejected before any PSP call (server binding, logged).
- Token-hygiene drill: raw token material absent from logs/DB-beyond-reference/client storage (grep + review).
- Stuck-authorizing drill: recoverable via settlement status path, never double-charged.
- IDOR drill: cross-user order ops 403 + RLS deny. Secrets drill: no PSP keys/certs/private keys in repo/logs/bundle.

## Out of scope

- Validation/charge/grant mechanics (authorization plans); pending/sweep/refund/dispute (pending-refund plans); `orders` DDL (session database plan); plan matrix/AI caps/agency skeleton (shared, untouched); shop delivery; receipt emails; other rails; multi-provider checkout UI; P5.

## Open questions

- Q1: WHICH PSP (tokens + VND settlement to VN business) — joint with Google Pay, one adapter family (BA Q1)?
- Q2: Guest product checkout in v1 vs login-required everywhere (BA Q2 — single session decision)?
