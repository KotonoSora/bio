# Refunds - Security, Logging, and Rate Limiting (feature 38)

Phase 2 TL · BA sources: `docs/01-requirements-ba/38-refunds/` (NFR-05..NFR-16, BR-06..BR-18). Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | Scoping: buyer routes owner-scoped (RLS + server-set `user_id`); `refund_absent`/403 parity with 34 for foreign reads; ops routes re-check the restricted role per request | AC-06; RLS probes |
| S-2 | Role separation: approve/reject/execute all require the ops role; two-person enforced at the DB (`chk_rf_manual`) for manual rails; the same identity can never pair with itself | AC-02; constraint drill |
| S-3 | Evidence ordering: manual rails can only reach `executing` with `evidence_ref` recorded; evaluation order in code matches the constraint (no window where money moved pre-evidence) | AC-02; state-walk test |
| S-4 | Idempotency: `UNIQUE` idempotency key + guarded transitions + PSP idempotency keys (or query-first retries) — retries and races collapse to one reversal and one credit note | AC-03; burst probe |
| S-5 | Money integrity: amount assertion at two points; `uniq_rf_order` blocks stacking; no code path mutates `orders.amount_vnd`; refund rows append-only (no DELETE grant) | AC-11; drill |
| S-6 | No-default gate: strategy lookup by stored `provider` only; unknown = reject + alert; CI grep blocks defaults/fallbacks (32's gate extended); cross-rail execution impossible by construction | AC-05; grep gate |
| S-7 | Data hygiene: no PAN/card/bank material in rows, logs, events, notes; `psp_ref`/`evidence_ref` are transaction references; reason text plain; emails masked in logs | Grep + log audit |
| S-8 | Event integrity: publish post-commit only; frozen shape; consumers dedupe by `refundId`; reconciliation re-emits marker-less rows once; a forged event source cannot exist (Redis db 10, service-side writes only) | AC-09; replay drill |
| S-9 | Revocation scoping: the matcher reads audits by `user_id` + subscription id only; ops period-fix compensation is itself audited (36's route) | AC-07; audit review |
| S-10 | Secret hygiene: no PSP secrets in this module (API calls delegate to 40's member clients, which own their secrets per environment/vault) | Config grep |

## Rate limits (gateway rules; Redis-backed counters)

| Route | Limit | On breach |
|---|---|---|
| `POST /api/v1/billing/orders/{orderCode}/refund-request` | 3/day/order + 10/day/IP/user | 429 `throttled` + `Retry-After` |
| `GET /api/v1/billing/refunds*` | 60/h/user | 429 `throttled` |
| `GET /api/v1/ops/refunds*` | 120/h/operator | 429 `throttled` |
| `POST /api/v1/ops/refunds/{id}/approve|reject|execute` | 30/h/operator each | 429 `throttled` |
| `POST /internal/v1/billing/refunds` | per-caller service budget (25's T-04 pacing) | 429 `throttled` |
| Retry executor | ≤ 5 attempts / ≤ 24 h per refund; backoff caps 6 h | - |

## Degrade matrix

| Failure | Behavior |
|---|---|
| PSP/rail API down | Row `failed` + alert; backoff retry; manual rails unaffected (bank transfer path needs no PSP) |
| Event bus (Redis db 10) down | Reversal finality commits; publish queued with bounded retry; reconciliation heals; 23/24/25 delayed, never forked |
| Notification pipeline down | Credit-note email retries via backlog; receipt chain still renders (row truth) |
| `billing-service` down | All routes 503; no partial states exist by construction |
| 36's lifecycle job down | Plan revocation still executes in the refund transaction (it does not depend on the job) |
| Ops queue unattended | SLA-age metric alerts; system-class API-rail rows self-execute; manual rows wait by design (evidence is human) |

## Audit and observability

Audits (24-month spine): `refund.requested`, `refund.approved`, `refund.rejected`, `refund.executing`, `refund.reversed` (with `pspRef`), `refund.failed` (reason), `refund.emitted`, `plan.refunded`, `credit_note.issued`; two-person pairs provable by actor identities. Metrics: `refund_requests`, `refund_approved/rejected`, `refund_reversed{rail}`, `refund_failed{rail}`, `refund_retry_attempts`, `refund_queue_age` (SLA gauge), `refund_emitted_mismatch`, `credit_note_issued`. No PII labels; amounts only as aggregates.

## Support answers

"Where is this buyer's money?" -> refund row status + `psp_ref`/`evidence_ref` + credit note; rail statement joins by `evidence_ref`. "Why was this refused?" -> eligibility reason (terminal state, `tip_final`, existing decision). "Why did access drop?" -> `plan.refunded` + the matched origin/extension order; compensation path = 36's period-fix.
