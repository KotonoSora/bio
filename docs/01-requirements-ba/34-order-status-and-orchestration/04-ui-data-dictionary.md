# Order Status and Orchestration - UI Data Dictionary (feature 34)

Phase 1 BA · Order surfaces compose the 32 shared components (StatusBadge, PendingPoll, ProblemMoney) and extend 33's pending page; nothing here is redefined.

## Pending page: state rendering map (nine states)

| State | Badge copy | Actions offered | Behavior |
|---|---|---|---|
| pending | `Waiting for payment` | cancel (pending only); re-open handoff (url/sheet) | active poll (3 s, backoff) |
| authorizing | `Confirming…` | none — capture in flight, stated | active poll |
| paid | `Paid` | receipt link (37); what-happens-next note (plan/product) | poll stops |
| failed | `Payment failed — try another method` | new checkout with the same intent | terminal |
| cancelled | `Cancelled` | start a new checkout | terminal |
| expired | `Link expired — start a new checkout` | new checkout only (no win-back nudge) | terminal |
| refunded | `Refunded` | credit note (37); support link | terminal |
| disputed | `Under review` | support contact only | frozen |
| chargeback_reversed | `Reversal complete` | support contact only | frozen |

An action outside this map (e.g. retry on `expired`) is a contract violation; provider-native codes never appear in the DOM on any surface.

## History page (`/app/billing/history`)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Order row | table row | yes | short `orderCode`, date, item title (fallback label when title resolution degrades), kind label, `amountVnd`, StatusBadge, receipt link (`paid` and later) | newest first, cursor page |
| Empty state | notice | - | `No payments yet` + plan entry (never zeros) | 32 copy |
| Need help | link | - | carries receipt id / order code to support | - |

## Order detail

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Status header | badge + text | yes | canonical badge + server-composed `status_detail` verbatim; screen-reader announcement on change (aria-live) | polls while non-terminal |
| Lines block | list | yes | title (fallback `Item <id>` when shop/plan title resolution degrades), kind, quantity, amount | single-line v1 |
| Fulfillment block | list | conditional | `merch` orders only; from 23 status fields mapped to `Preparing|Printing|Shipped|Delivered|Issue|Cancelled`; never shows ship-to | degrade: `Fulfillment status unavailable — refresh` (never blocks money fields) |
| Receipt + support | links | conditional | receipt (37) when `paid` and later; support always carries `orderCode` | - |
| Disputed variant | notice | conditional | support contact only; zero payment actions (frozen means frozen in UI) | - |

## Support/ops

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Order lookup | search | yes | by `orderCode` only via 32's trace route; foreign/absent → uniform denial | - |
| Transition timeline | list | yes | from→to + triggering event id + actor, redacted | replayable per order |
| Exception re-link | action | conditional | bank rails only; `ROLE_OPS` + mandatory reason; audited (40's route) | hidden for card rails |
