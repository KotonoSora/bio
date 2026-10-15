# Payment Security Database Plan (enforcement inventory — no new tables)

PLAN ref: S-1…S-7 bound to every `monetize/*` path (single normative baseline; provider slices cite, never restate) + incident-response pointer. BA source: `docs/monetize/payment/payment-security.md`. Consumed by the security backend plan (guards, abuse controls, probe battery; splits next to this file) and every rail adapter (same rules, zero restatement). Enforcement lives in endpoint guards, RLS policies, UNIQUE constraints, and the audit log — all stored in their owning slices. No migration — this slice creates zero tables.

## Purpose

Inventory exactly where each security guarantee is enforced so reviewers verify in one place and no rail re-specifies (and drifts) the baseline: authorization points, amount-integrity checks, anti-replay keys, secret hygiene, abuse counters, audit requirements, and the per-provider probe battery as a gate artifact. No `CREATE TABLE` here by design — a security-owned table would duplicate ownership of rows other slices define.

## Users

- Implementer (any rail path): the checklist that must hold before the path ships.
- Reviewer/auditor: one inventory to verify instead of eight overlapping sections.
- Buyer: protected uniformly regardless of rail.

## Functional requirements

1. Authorization enforcement points (S-1/S-6): every money endpoint scopes by session `user_id` AND table RLS (both, always — policies owned by each table's database plan); public webhook/callback/IPN/sync endpoints authenticate by provider scheme (HMAC/signature/token, per-rail adapter) and resolve ownership from stored orders, never request identity. Cross-user probes 403 at API + deny at DB on all paths (matrix-tested per rail).
2. Amount-integrity points: server re-prices every checkout from canonical catalogs (client amounts informational); every grant path re-checks provider-reported amount vs stored `amount_vnd` (integers end-to-end; VNPay ×100 normalized at the edge). Mismatch → no grant + alert, always (alert metric owned by the guard, threshold by ops).
3. Anti-replay key contract (storage owned by the checkout-session database plan — next in queue): UNIQUE key of (`orderCode` + provider transaction/event id) on the order's confirmation record; guarded terminal transitions (status-model plans). Retries/redeliveries/sweeps are durable no-ops by construction; replayed paid orders succeed-without-effect (never error-retry storms). The contract lives here; the columns live there — drift between the two fails review.
4. Secrets hygiene inventory (names only, never values): per-rail PSP keys/certs via env/vault (local `.dev.vars`, never committed); secret/private keys never reach client bundles (build-time grep over bundles); raw PAN/token/cryptogram never logged/stored beyond PSP references; no per-user CSS/JS near payment pages; CSP `default-src 'self'` on checkout/pending/history. Grep gates run every milestone (patterns: keys, certs, `whsec_`, tokens, PAN shapes).
5. Abuse-control state (cache layer, never truth): Turnstile + per-IP/email buckets on checkout, token-intake, and all public money endpoints; WAF managed + rate rules on anonymous routes from day one; velocity limits tiered (cards tighter than bank rails); quarterly rotation with the P5 drill. Thresholds start from WAF defaults, tuned on abuse data (BA Q1 default), unless explicit budgets land first.
6. Audit/evidence requirement: every grant/transition/refund/dispute/kill-switch-flip/ops-override appends `audit_log{actor, action, target, at}` (storage owned by platform identity); per-order trails replayable; signatures/keys redacted from all logs (redaction asserted in log-capture tests, not by convention).
7. Probe battery matrix (gate artifact, run per provider every public deploy): IDOR matrix, bad-signature/tampered-amount/unknown-ref webhook battery, replay battery, double-submit battery, cancel/expiry races, kill-switch drill, secrets grep, milestone k6 gate. Red anywhere blocks the deploy (tooling-enforced promotion rule, owned by release gates).
8. Incident response pointer: suspected key compromise → rotate affected secret → flip provider kill switch → reconcile via that rail's settlement sweep → re-enable. Order matters (rotate before re-enable, reconcile before traffic) — runbook-owned, drilled quarterly.

## Data / API touches

- DDL: none — assertion: no `CREATE TABLE` for security state anywhere under `docs/monetize/payment/` for this slice (review greps it). Consumed enforcement: RLS policies (table plans), UNIQUE keys (checkout-session plan), `audit_log` (platform identity), rate counters (cache layer), WAF rules (edge config), secret names inventory (appendix at implementation — names only, ever).

## Acceptance criteria

- Battery drill: per-provider matrix green on all eight paths (recorded, not spot-checked).
- Secrets drill: repo/logs/bundles grep clean (patterns above, zero hits).
- WAF drill: abusive bursts throttled, valid traffic unaffected, per-IP/email buckets verified.
- Audit drill: random grants/refunds/disputes/flips all have actor/action/target entries.
- Rotation drill: quarterly, no-downtime, old credentials dead.
- Violation drill: any rail-restated security rule that drifts from this inventory fails review (single-source rule).

## Out of scope

- Guard/abuse/probe implementation (backend plan); secure page patterns (frontend plan); rail verify mechanics (rail slices); dispatcher/kill-switch operation (orchestration plans); status edges (status-model plans); ledger/lifecycle/plans (lifecycle plans); P4 impersonation audit (extends this baseline in P4); P5 scale-out.

## Open questions

- Q1: Rate-limit thresholds — WAF defaults + tuning (current) vs explicit budgets now (BA Q1)?
- Q2: Fraud tooling beyond velocity (Radar/device signals) at launch vs first incident (BA Q2)?
