# Apple Pay Pending Refund Backend Plan (sweep + reversals — server build steps)

PLAN ref: P3-1/P3-2 gap-fill (settle the ambiguous middle, reverse what should not stand, contest chargebacks with evidence) + S-1/S-5/S-6/S-7. BA source: `docs/monetize/apple-pay/pending-refund.md`. Phasing per `docs/platform/delivery/execution-overview.md` (E3). Contracts in `docs/monetize/apple-pay/pending-refund-database-plan.md` (confirming convention, sweep ownership, refund/dispute recording — never redefined here). Confirming/refund/dispute screens in the frontend plan (splits next to this file).

## Purpose

Give the backend developer an ordered procedure for closing every gap the sheet-charge round-trip leaves: on-demand verify, reconciliation sweep, full/partial refunds with revocation, dispute freeze/contest with evidence deadlines, and card-proportionate fraud hygiene. Follow steps 1–8 in order. Ambiguity resolves by status query through guarded transitions — never by assumption, never by auto-grant.

## Users

- Backend developer: executes steps 1–8 in package `com.app.billing.rail.applepay.settle`.
- QA: runs abandon, suppression, refund, dispute, and auth drills (PSP sandbox).
- Buyer/support/system: gets resolution instead of limbo; refunds with references; evidence with deadlines.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — monolith settlement (in-process with order + ledger writes). Monolith pros: verify/sweep/refund/dispute share the guarded-transition + audit baseline with charge/webhook paths (uniformity by shared code, not parallel implementations); row-locked per-order serialization extends to settlement. Microservices cons avoided: splitting settlement from grant opens verify-vs-grant races across a network call. Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; none fire. Starters: web, validation, data-jpa, security, scheduling, actuator, micrometer. Load posture: verify throttled per order (bounded PSP calls — buyer "check again" cannot fan out to PSP storms); sweep scans indexed status queries in bounded batches (no full-table scans — `WHERE status IN (…) AND updated_at < threshold` on the status/updated index); dispute webhooks throttled like all webhooks.
2. Step 1 — confirming sub-state: PSP-reported review/3DS maps to `authorizing` + `confirming_reason` + TTL (convention from the database plan); buyer page offers "check again" (on-demand verify) + support escalation (no silent limbo — limbo with an expiry date and two exits is a state, not a failure).
3. Step 2 — on-demand verify (past a short threshold): call PSP adapter `status(orderCode | psp_txn_id)` → apply authoritative result through the SAME atomic, amount-checked, idempotent grant logic as charge path (shared method, not a copy — divergence between verify-grant and charge-grant would be a double-grant seam). Throttled per order (verify budget per order per window, counted).
4. Step 3 — reconciliation sweep `@Scheduled` (cadence + stale threshold per Q1): scan `authorizing`/stale-`pending` → status-query each → settle (`paid` on captured with idempotent grant; `expired`/`failed` otherwise; already-`paid` skipped) → metric `sweep.settled{outcome}`. Rerun-safe (idempotent transitions make the second run a no-op — asserted by double-run test).
5. Step 4 — refunds (policy WHO per Q2, mechanics here): `POST` refund (authenticated buyer-per-policy or `ROLE_OPS`, audited) → PSP adapter `refund(psp_txn_id, amount)` (full or partial) → record gateway refund reference → full: status `refunded`; partial: `paid` + partial-ledger entry (refunds slice) → revoke entitlement (plan downgrade per plans rule; product claim invalidated in shop) → audit (actor, action, target, at). Re-grant on refunded rows impossible (terminal guard, tested).
6. Step 5 — disputes (card-only risk): dispute webhook → `disputed` (grant frozen — no new entitlements; existing access flagged per plans/shop rules) + evidence task with deadline clock (deadline from PSP payload; ops metric on approaching deadlines) → contest with evidence pack (order logs + delivery proofs + buyer history per Q3 template decision) → win restores prior state; loss → `chargeback_reversed` + entitlement revoked + fraud-review entry. Repeat-offender signals feed a HUMAN-APPROVED blocklist proposal (no auto-ban code exists — asserted by absence).
7. Step 6 — fraud hygiene: per-IP/email velocity on checkout + validate + authorize (shared baseline); 3DS outcomes honored end-to-end (step-up bypass impossible — no bypass path coded); device/session anomalies logged for review (sheet opened here, token posted there — mismatch rows for humans, never automatic blocks).
8. Step 7 — ops endpoints (`ROLE_OPS`, rate-limited, audited): `POST /api/v1/ops/applepay/verify` (force verify), `GET /api/v1/ops/applepay/orders/:orderCode` (live view: order + PSP status + audit trail), `POST /api/v1/ops/applepay/refund`, dispute evidence upload. Non-ops → 403 (role test, not just route hiding).
9. Step 8 — tests + container: 3DS-abandon matrix (park → non-terminal verify → sweep expiry; sheet-closed stays reusable pending; zero grants); suppression test (query grants once, redelivery no-ops); refund test (full + partial with references, revocations, no re-grant); dispute test (freeze → evidence → win/loss exact); auth test (non-ops 403 + RLS deny). Same image via `docker run` + `podman run` per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`.

## Data / API touches

- Schema: database plan above (no tables here; mutations over `orders` + entitlement/claim rows + partial-ledger rows + `audit_log`). PSP APIs server-side only (status/refund/dispute; charge owned by authorization slice; keys in env/vault). Ops endpoints per step 7. Metrics: `sweep.settled{outcome}`, verify budget counters, dispute deadline gauges, PSP auth-failure paging.

## Acceptance criteria

- Abandon drill: full matrix green, zero grants, reusable pendings.
- Suppression drill: exactly-once verify grant; redelivery no-op.
- Refund drill: references recorded; revocations per rule; no re-grant.
- Dispute drill: freeze → evidence → win/loss exact; blocklist human-gated.
- Auth drill: non-ops 403 on all ops endpoints + RLS deny.
- Container drill: digest pair boots, compose green, rollback green.

## Out of scope

- Settlement contracts (database plan above); confirming/refund/dispute screens (frontend plan); validation/charge/grant (authorization plans); session config/intake (checkout plans); `orders`/entitlement/ledger DDL; plan downgrade UX; bank-rail exceptions; accounting reports; P4/P5.

## Open questions

- Q1: Sweep cadence + threshold (see database plan Q1)?
- Q2: Self-service vs ops-only refunds; partials at launch (see database plan Q2)?
- Q3: Evidence pack template timing (see database plan Q3)?
