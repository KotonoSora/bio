# Order Status and Orchestration - Acceptance Criteria (feature 34)

Phase 1 BA · BDD; per-rail rows mirror the shared conformance matrix (32 BR-11). AC IDs are stable.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Cross-provider lifecycle drill | one order per rail | each is driven through success, cancel, expiry, and (where applicable) refund/dispute | all land on the specified canonical states with complete from→to audit trails |
| AC-02 | Illegal-edge drill | edges like `expired → paid` direct and `failed → authorizing` | the attempts run on every rail path | refused at the transition layer; only the audited bank-rail exception flows can move terminal rail orders |
| AC-03 | Late-confirmation drill | confirmations for `cancelled`/`expired` orders | they arrive across all eight rails | zero grants, all logged, alert threshold honored |
| AC-04 | Status-enum probe | the canonical set | any endpoint tries an off-enum or illegal write | refused at guard + CHECK; `grep` confirms the enum appears once per layer |
| AC-05 | Buyer rendering drill | each canonical state | pending, history, and detail render | specified copy + actions from the BA 04 map; zero provider-native strings in payloads or DOM |
| AC-06 | Grant identity drill | a verified confirmation | amounts match and then mismatch | match → single grant; mismatch → no grant + quarantine + `failed` via sweep; never a different amount granted |
| AC-07 | Replay drill | double webhook, sweep racing the webhook, disputed-event redelivery | events replay | single grant, single audit chain, success-without-effect on redelivery, idempotent DLQ replay |
| AC-08 | Race drill | cancel concurrent with confirmation | both run | exactly one terminal outcome; no resurrection out of a terminal state |
| AC-09 | Reads drill | owner history and detail, foreign and absent probes | reads run under poll load | poll p95 ≤ 200 ms; cursor pages; foreign → 403 + RLS deny; absent → 404 `order_not_found`; terminal pages stop polling (request logs) |
| AC-10 | Fulfillment composite drill | a `merch` order with jobs; feature 23 then down | detail renders | job statuses shown (status fields only; no ship-to); outage degrades to `unavailable` with money fields intact; no 5xx |
| AC-11 | Delete-guard drill | product with and without orders; then the seam down | 22's delete runs | exists → 409 archive; absent → hard delete allowed; seam unreachable → 503 `orders_check_unavailable`, nothing deleted |
| AC-12 | Event drill | a grant | `order.granted.v1` emits | frozen shape matches (lines, shipTo, extras, paidAt); 23/24/25 dedupe to one job/enrollment/CAS confirmation; DLQ replay re-delivers idempotently |
| AC-13 | Conformance gate | the 8×N matrix | a rail change is proposed | its row is green before ship; the artifact is recorded per release |
| AC-14 | Dispute drill | a verified dispute on a card rail | the settlement flow runs | `disputed` frozen UX; win restores `paid`; loss lands `chargeback_reversed`; ledger effects verified in 35's slice |
