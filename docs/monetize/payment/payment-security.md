# Payment Security (shared baseline for all money paths)

PLAN ref: Security checklist S-1…S-7 (this doc normatively binds them to every `monetize/*` provider path; provider docs cite it instead of restating). PLAN-conformant, no deviation.

## Purpose

State once, enforce everywhere: the non-negotiable security rules for all eight payment paths. Today each provider doc repeats its own IDOR/secret/replay lines with slight drift; this doc is the single normative baseline — provider specs reference it, implementation checks against it, and pre-deploy probes verify it per provider.

## Users

- Implementer (any provider path): the checklist that must hold before the path ships.
- Reviewer/auditor: one document to verify instead of eight overlapping sections.
- Buyer: protected by uniform guarantees no matter which rail they pay on.

## Functional requirements

1. Authorization (S-1/S-6): every money endpoint scopes by session `user_id` AND the table RLS policy (both, always); public webhook/callback/IPN/sync endpoints authenticate by their provider scheme (HMAC/signature/token) and resolve ownership from stored orders, never from request identity. Cross-user access probes must 403 at API and deny at DB on all paths.
2. Amount integrity: server re-prices every checkout from canonical tables; client amounts are informational only. Every grant path re-checks provider-reported amount against stored `amount_vnd` (zero-decimal integers throughout; only VNPay's ×100 wire format differs, normalized at the edge). Mismatch → no grant + alert, always.
3. Replay and double-spend: every provider path carries a UNIQUE anti-replay key (`orderCode` + provider transaction/event id) and a guarded terminal transition (see `payment/order-status-model.md`); retries, redeliveries, and sweeps are durable no-ops by construction. Replayed paid orders return success-without-effect, never error-retry storms.
4. Secrets and card-data hygiene: all keys/certs/tokens via `wrangler secret` (`.dev.vars` locally, never committed); secret keys and private keys never reach the client bundle; raw PAN/token/cryptogram material never logged, never stored beyond PSP references; no per-user CSS/JS anywhere near payment pages; CSP `default-src 'self'` on checkout/pending/history pages. Grep gates on every milestone.
5. Abuse controls: Turnstile + per-IP/email rate limits on checkout, token-intake, and all public money endpoints; WAF managed + rate rules on anonymous routes from day one; velocity limits proportionate to cards (tighter) vs bank rails. Secrets rotate quarterly together with the P5 rotation drill.
6. Audit and evidence: every grant, transition, refund, dispute action, kill-switch flip, and ops override appends append-only `audit_log{actor, action, target, at}`; per-order trails replayable; signatures/keys redacted from all logs.
7. Pre-deploy probe battery (run per provider, every public deploy): IDOR matrix, bad-signature/tampered-amount/unknown-ref webhook battery, replay battery, double-submit battery, cancel/expiry races, kill-switch drill, secrets grep, plus the milestone k6 gate. Red anywhere blocks the deploy.

## Data / API touches

- No new tables: enforcement lives in endpoint guards, RLS policies, UNIQUE constraints, and the audit log (all defined in their own docs). This doc constrains them; conformance tests assert them.
- Rate-limit counters in KV (`RATE_LIMIT`); WAF/rate rules in Cloudflare config; secret names inventoried in one checklist appendix at implementation (no values, ever).
- Incident response pointer: on suspected key compromise, rotate the affected secret, flip the provider kill switch (see `payment/checkout-orchestration.md`), and reconcile via that provider's settlement sweep before re-enabling.

## Acceptance criteria

- Per-provider probe battery green on all eight paths (matrix recorded, not spot-checked): IDOR 403s, bad-signature rejections, tampered-amount no-grants, replay no-ops, double-submit single rows, cancel/expiry races safe.
- Secrets grep across repo, logs, and client bundles: zero hits (keys, certs, `whsec_`, tokens, PAN patterns).
- WAF + rate-rule simulation: abusive bursts throttled, valid checkout traffic unaffected, per-IP/email buckets verified.
- Audit completeness drill: random sample of grants/refunds/disputes/kill-switch flips — every one has its audit entry with actor/action/target.
- Rotation drill (quarterly, with P5): all money secrets rotated without downtime; old credentials dead.

## Out of scope

- Provider verification mechanics (their own minors); dispatcher and kill-switch operation (see `payment/checkout-orchestration.md`); status transitions (see `payment/order-status-model.md`).
- Entitlement ledger and lifecycle (`billing/`); plan matrix (`plans/`); P4 agency impersonation audit (extends this baseline in P4); P5 scale-out.

## Open questions

- Q1: Rate-limit thresholds per endpoint — start from WAF defaults and tune on abuse data, or set explicit budgets now?
- Q2: Fraud tooling beyond velocity (Radar/device signals) — enable at launch on card rails, or add on first fraud incident?
