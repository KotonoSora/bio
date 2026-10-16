# Order Status and Orchestration - Workflow and User Journey (feature 34)

Phase 1 BA · The post-create half of the money lifecycle: verified confirmations → grant, reads, late money, disputes. Creation/quote/cancel mechanics live in `docs/01-requirements-ba/33-checkout-session/`; rail verification in 40.

## Provider → grant (the money path)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Provider handler (40's route) | verified confirmation arrives (webhook/callback/IPN/query/sweep verify) | the guard resolves the order, then asserts edge, actor, and amount | BR-02/03/04 |
| 2 | System (guard) | amount matches `amount_vnd` | row-locked transition + `psp_ref` first-write + audit, one transaction | BR-04/13 |
| 3 | System | commit | `order.granted.v1` emits post-commit; retry loop, then DLQ on failure | BR-07 |
| 4 | Buyer surfaces | next poll | `paid` visible ≤ 200 ms; receipt queued (37 via 32's pipeline); ledger ≤ 60 s (35) | O-5 |
| 5 | Shop registrar (23/24/25) | consumes the event | one job per merch line / enrollment / booking CAS — dedupe on consumer keys | BR-07 |

## Buyer: pending → terminal (read side)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Buyer | lands on the pending page (33's return contract) | polls `{status, status_detail}`; forged params change nothing | BR-08 |
| 2 | Buyer | sees `authorizing` | `Confirming…`; no cancel (capture in flight, stated); expiry covers abandonment | BR-02/03 |
| 3 | Buyer | order reaches `paid` | poll stops; receipt link + what-happens-next; no re-pay path exists | BR-08/09 |
| 4 | Buyer | `failed|cancelled|expired` | reason copy + new-checkout action; expired offers no pay-this-link affordance | BR-09 |
| 5 | Buyer | opens history or order detail | canonical rows, lines, fulfillment block for merch, receipt link | BR-08 |

## Late money and killed rails

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Provider handler | confirmation for a `cancelled|expired` order | refused + logged; ops alert threshold; buyer order unchanged | BR-06 |
| 2 | Ops (bank rails only) | audited exception re-link with reason | re-link via 40's ops route; audit appended; never automatic | BR-06 |
| 3 | System | a rail is killed mid-flow | its pendings settle or expire; no auto-migration to another rail | BR-12 |

## Disputes (card rails)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Settlement handler (40) | verified dispute event | edge `paid → disputed`; frozen buyer UX (support contact only) | BR-10 |
| 2 | Ops | contest with evidence before the deadline (40) | win → `paid` restored; loss → `chargeback_reversed` | BR-10 |
| 3 | Books (35/38) | effects land in their slices | entitlement flag/revoke + credit note; period math is 35's | BR-10 |

## Release: conformance drills

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | QA | edge matrix per rail mapping fires every legal edge and every illegal attempt | legal edges apply; illegal edges refuse; non-terminal native states transition nothing | BR-02/11 |
| 2 | QA | late-confirmation drill across all eight rails | zero grants, all logged | BR-06 |
| 3 | QA | replay + race drills (double webhook, sweep race, cancel race) | single grant, single audit chain, one terminal outcome | BR-05 |
| 4 | QA | projection drill on payloads and screens | zero provider-native strings anywhere | BR-01/09 |
