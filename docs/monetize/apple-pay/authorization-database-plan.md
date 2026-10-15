# Apple Pay Authorization Database Plan (grant evidence — no new tables)

PLAN ref: P3-2 adapted (server-validated merchant session + PSP-confirmed charge + idempotent grant; PSP webhook as confirmation/audit path) + transition audit. BA source: `docs/monetize/apple-pay/authorization.md`. Consumed by the authorization backend plan (validation, charge, webhook; splits next to this file). Order/entitlement rows live in the session/ledger database plans (next in queue — `psp_txn_id`, anti-replay keys, entitlement writes owned there, never here). No migration — this slice creates zero tables.

## Purpose

Define the authorization evidence contract without owning storage: what a merchant session is (and why it is never persisted), the idempotency keys that make charge/webhook/retry collapse, the amount-binding rule, and the audit fields every step emits. No `CREATE TABLE` here by design — persistence lives in order/entitlement/audit rows owned elsewhere; this slice owns the trust ceremony around them.

## Users

- Backend developer: implements validation/charge/webhook against these evidence rules.
- QA: proves forged/replayed/mismatched money never grants, from row state.
- Ops: relies on idempotency during retries, duplicate webhooks, and incident recovery.

## Functional requirements

1. Merchant sessions (ephemeral, Apple-issued): per-transaction, single-use, minutes TTL, fetched server-side with the Merchant Identity Certificate (TLS client identity) using Apple's `validationURL` untouched. Never stored in PG, never cached beyond the sheet (a persisted session is a replayable payment capability — absence from storage is the guarantee; the minutes TTL is Apple's, the non-persistence is ours).
2. Idempotency keys (stored on rows owned by the session database plan): charge calls carry `idempotency_key = orderCode` to the PSP (safe charge retry); local `psp_txn_id UNIQUE` makes duplicate tokens/redelivered webhooks durable no-ops; `transactionIdentifier` freshness rejects cross-order identifier reuse (reused identifier → reject + log, never charge). Replay of a paid `orderCode` returns success with zero side effects (terminal fast-path, owned by the status-model guard).
3. Amount binding: charged == stored `amount_vnd` (integer comparison, no float tolerance band to hide behind); PSP-reported divergence → `completePayment(FAILURE)` + alert, no grant. Unknown `orderCode` → failure without disclosing valid codes (no existence oracle on money paths).
4. Grant atomicity (single transaction): `authorizing → paid` + `psp_txn_id` write + entitlement write + `audit_log` append (actor, action, target, at) — all four or none. The authorize response verdict derives from the committed outcome (verdict follows truth, never predicts it).
5. Failure-state rows: PSP-declined → `failed` (+ buyer-safe reason, no gateway codes leaked); transport/timeout → back to `pending` (sweep-resolvable — never assume paid, never assume failed; ambiguity resolves by status query, owned by the pending-refund slice). Non-terminal PSP events (review/pending) transition nothing (mapping discipline owned by the status-model plans).
6. Webhook evidence: public endpoint (no session, HTTPS only), PSP-scheme verification first (signature/HMAC per provider docs — bare POSTs never trusted), terminal confirmations through the guarded transition, unknown refs logged-rejected without leak. Redelivery is the normal case (webhooks redeliver by design — the pipeline treats first delivery and fifth identically).
7. Observability fields (log shape, secrets redacted): per validation/authorize/webhook `{orderCode, psp_txn_id, result, latency}` with tokens/signatures/keys redacted (redaction asserted in log-capture tests). Alerts on consecutive Apple-validation failures, PSP verification failures, already-paid hit spikes.
8. Cryptography storage: Merchant Identity Certificate + PSP webhook secrets via env/vault only (containerized correction — never `wrangler secret`); TEST-vs-production credentials strictly separated (separation asserted in config review, not just documented).

## Data / API touches

- DDL: none — assertion: no `CREATE TABLE` for authorization state anywhere under `docs/monetize/apple-pay/` for this slice (review greps it). Consumed rows: `orders` (status/psp refs/amount), entitlement rows (ledger slice), `audit_log` (platform identity) — all owned elsewhere. Ephemeral: merchant sessions (rule 1, never persisted).

## Acceptance criteria

- Session drill: bad cert/expired session fails closed pre-charge; per-transaction freshness (no session reuse across sheets — reuse attempt rejected).
- Replay drill: same token twice + redelivered webhook → single transition/entitlement/audit; cross-order identifier reuse rejected.
- Tamper drill: forged token, altered amount, unknown orderCode → failures without grant or leak.
- Timeout drill: silent PSP → order sweep-recoverable; exactly-once preserved by the idempotency key.
- Webhook drill: bogus bursts throttled (WAF/rate), valid traffic unaffected; unknown refs logged-rejected.
- Absence drill: no decryption code, no persisted sessions, no token material in storage (grep + review).

## Out of scope

- Validation/charge/webhook implementation (backend plan); sheet/token UX (checkout frontend plan); session config/gating/intake (checkout plans); pending/sweep/refund/dispute (pending-refund plans); `orders`/entitlement DDL (their database plans); plan matrix/AI/agency; shop delivery; P4/P5.

## Open questions

- Q1: Buyer-visible state on charge response vs webhook lag — `paid` now or `confirming` until check (BA Q1, shared plans-SLA decision)?
- Q2: Certificate storage/rotation mechanics — design now or at implementation (BA Q2)?
