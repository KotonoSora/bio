# Entitlement and Plans - Security, Logging, and Rate Limiting (feature 35)

Phase 2 TL · Deny-by-default, service-side scoping, per-DB RLS, audited ops actions. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`. BA sources: `docs/01-requirements-ba/35-entitlement-and-plans/` (NFR-10..13).

## Access model

| Surface | Authentication | Authorization |
|---|---|---|
| `GET /api/v1/plans` | anonymous | Active rows only; drafts/retired never served; cache-first at the edge |
| `GET /api/v1/billing/plan`, tailor | session cookie `rb_session` + CSRF (tailor) | Owner-scoped reads; guests `401` (plan surfaces are login-only) |
| Ops catalog routes | session + CSRF | `billing_ops` role for draft/list/retire; `finance` role additionally for activation; reason mandatory; every action audited |
| Internal plan/prices/debit/return | gateway RS256 service JWT (`<= 60 s`) | Service identity only; `userId` in payload is trusted solely because the caller is an internal service |
| Grant ledger writes | 34's verified-grant actor | Runs inside the grant transaction under the ops context; webhooks never write `subscriptions` directly |

## RLS matrix (V2 policies)

| Table | Owner context (`app.user_id`) | Ops context (`app.context='ops'`) | Notes |
|---|---|---|---|
| `plans`, `plan_terms` | read | read + write | Catalog is shared data; writes only via ops routes |
| `subscriptions` | read (own rows) | read + write | Grant/lifecycle write under ops; no owner write path |
| `ai_credit_periods` | read + write (own rows) | read + write | Debit/return run under the user's context via internal calls |
| `ai_credit_events` | read + insert (own rows) | read + insert | Append-only grants; no UPDATE/DELETE path anywhere |

- Deletion: none (financial records); PII scrub per `docs/02-architecture-techlead/02-account-deletion/03-api-contracts-and-payloads.md` (billing roster row already covers 35-40 extensions).

## Threats and drills

| # | Threat | Control | Drill |
|---|---|---|---|
| S-1 | IDOR on plan/quota reads | Owner RLS + session-user binding; foreign reads return 404 with no existence leak | Foreign `app.user_id` reads zero rows; session/user mismatch refused |
| S-2 | Ops privilege creep | Draft/retire need `billing_ops`; activation needs `finance` too; both audited with reason | Non-ops and ops-without-finance drills: 403 each; audit rows present |
| S-3 | Client price tampering | Amounts are never accepted from the client on plan intents: quote derives from active rows; create re-verifies the quote token + fingerprint covers `plan|term|amountVnd` (33) | Tampered amount -> `409 amount_changed`; tampered term -> `term_invalid`/409 |
| S-4 | Debit double-spend | Keyed idempotency (UNIQUE `idempotency_key`) + conditional counter update in one transaction | Replay returns the original result; parallel debits never exceed the total |
| S-5 | Return abuse | One return per debit (UNIQUE `return_of`); returns audited; `ai.return` ratio alert | Second return -> `409 return_conflict`; anomalous return rate pages ops |
| S-6 | Prompt injection via JD | Fixed system prompt; JD passed as data; output escaped text only; no tools; suggestions never auto-applied | Injected instructions/markup in the JD change nothing; output renders escaped |
| S-7 | Secret leakage | AI provider key via env/vault (`AI_PROVIDER_KEY`); no PSP secrets in this feature; internal JWT verification constant-time | Grep: no key literal in code/logs; long lines scanned |
| S-8 | Content in logs | JD text, prompts, and outputs never logged; metrics/logs carry ids, counts, latencies only | Log drill across debit/return/tailor paths |
| S-9 | Degradation honesty | Billing down -> snapshot miss -> consumers free-clamp; AI down -> `503` + credit returned; no silent pro on failure | Degrade matrix below executed with billing and adapter stubs |

## Rate envelopes

| Endpoint | Limit | Response |
|---|---|---|
| `POST /api/v1/ai/tailor-resume` | 10/min/user | `429 throttled` |
| `GET /api/v1/billing/plan` | 60/min/user | `429 throttled` |
| `GET /api/v1/plans` | 120/min/IP at the edge (plus 300 s cache) | `429 throttled` |
| Ops catalog routes | 20/min/operator | `429 throttled` |
| `POST /internal/v1/billing/ai/debit` | 60/min/user; returns 20/min/user | `429` (metric, never a retry loop at the caller) |

## Degrade matrix

| Failure | Effect | Behavior |
|---|---|---|
| Billing-service down | Snapshot resolution fails | Consumers resolve free (fail closed); `entitlement_snapshot_miss` alertable; self-heals |
| AI adapter down/slow | Tailor generation fails | `503 ai_unavailable`; debit returned exactly once; `ai.return` alertable if elevated |
| Config store (`billing.catalog`) invalid value | Boot/activation | Refused (`<= 44%` rule); cached reads keep serving the last good version `<= 5 s` |
| Ledger row gap detected | Corruption | Loud `500` + `plan.missing_row` page (never silent free or pro) |
| Redis down | No effect on this feature | 35 owns no Redis keys; streams belong to 32/34 |

## Audit and observability

- Write audit inside the same transaction: `plan.granted`, `plan.superseded`, `plan.term.draft|activate|retire`, `ai.debit`, `ai.return` (`actor`, `target`, `detail`, `at`); append-only grants make history tamper-evident.
- Metrics: `ledger.grant`, `ledger.superseded`, `plan.missing_row` (page), `entitlement_snapshot_miss`, `ai.debit`, `ai.return` (ratio > 5%/15 min pages), `ai.return_conflict`, `tailor.latency`, `catalog.flip`.
- Redaction rules: capabilities maps and JD content never enter logs; quota metrics carry numbers, not text; all user references are ids.
