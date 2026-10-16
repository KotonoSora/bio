# Order Status and Orchestration - Exception Handling and Errors (feature 34)

Phase 1 BA · Codes extend the 32 catalog (`docs/01-requirements-ba/32-monetize-overview/05-exception-handling-and-errors.md`); nothing here is redefined, only added (`order_not_found`). Buyer copy is vi/en.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Late confirmation on `cancelled|expired` | async → refused | no grant; `transition.late` logged; alert at ≥ 5 in 60 min per rail; order unchanged | "This order is closed — start a new checkout." |
| Amount mismatch on verified confirmation | async → quarantine | no grant; evidence to ops; order lands `failed` via the rail settlement sweep; audit appended | "We couldn't confirm this payment — contact support with your order code." (32 copy) |
| Concurrent confirmations (webhook + sweep) | race → one winner | loser is success-without-effect; one grant, one audit chain | none — status page shows the outcome |
| Cancel vs confirmation race | race → one winner | confirm wins → cancel gets 409 `cancel_conflict` (33); cancel wins → late-money refusal path | "This order can no longer be cancelled." / status outcome |
| Illegal edge attempted (defense) | defect → refused | rejected + counted (`transition.rejected{reason}`); no partial write; non-zero in production alerts | none — internal only |
| Duplicate/unknown provider event | provider op → 200 ack | logged, no transition (40's intake is forward-compatible) | none |
| Verified provider decline | async → `failed` | buyer-safe reason into `status_detail`; retry means new checkout | "Payment failed — try another method." |
| Fulfillment status read (23) down | degrade | detail renders money fields; fulfillment block shows unavailable | "Fulfillment status unavailable — refresh." |
| Delete-guard read timeout | degrade → 503 | 22 blocks the delete (`orders_check_unavailable`); nothing deleted | "Can't check order history — try again shortly." (22's copy) |
| Grant event publish failure | degrade | bounded retry → DLQ + alert; `paid` stands; reconciliation re-emits | none — buyer unaffected |
| Sessionless guest order read | client error → 401 | no sessionless money reads; the receipt magic link (37) is the guest path | "Open the receipt link from your email." |
| Dispute event replay | race → idempotent | one edge fire; redelivery is a no-op | none |
| Read rate breach | abuse → 429 | read envelopes + poller backoff in the client | `throttled` — "Too many attempts — try again in a moment." |
| Order absent / foreign | client error | absent → 404; foreign → 403 + RLS deny; no existence leak across users | `order_not_found` — "This order wasn't found." |
