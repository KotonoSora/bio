# Monetize Overview - UI Data Dictionary (feature 32)

Phase 1 BA · Shared money components every money screen composes (never reinvents).

## Shared components

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| ProviderList | list | yes | renders `payment-methods` verbatim — order, names, hints, disabled reasons; zero client re-sorting, zero hardcoded favorites | ops-config order |
| PendingPoll | poller | yes | 3 s interval; error backoff; stops at terminal states; pauses on `document.hidden` | starts on pending page |
| StatusBadge | badge | yes | canonical state → fixed copy: pending `Waiting for payment`, authorizing `Confirming…`, paid `Paid`, failed `Payment failed — try another method`, cancelled `Cancelled`, expired `Link expired — start a new checkout`, refunded `Refunded`, disputed `Under review`, chargeback_reversed `Reversal complete` | from canonical status only |
| ProblemMoney | error block | yes | RFC 9457 `code` → buyer-safe message + next action | - |
| PriceLine | text | yes | VND integer formatting; quote-vs-charged equality display | quoted amount |
| Capability hint | inline text | - | `Available on this device` / `Not on this browser` from server hints — client never decides eligibility | server-supplied |
| Recommended label | badge | no | never client-set; rendered only if the server payload supplies it (position-only otherwise) | absent |

## Pending / return screen

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Order context | text | - | `orderCode`, amount, chosen rail | - |
| Status | StatusBadge | yes | canonical only; provider-native jargon never in the DOM | polling |
| Return context | query params | no | display only — a forged `?status=paid` changes nothing | ignored for state |
| Timeout state | notice | - | expired/cancelled shows re-checkout action | terminal |

## History / plan surfaces

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Order rows | table | - | date, amount_vnd, rail, status badge, receipt link | newest first |
| Empty state | notice | - | `No payments yet` + plan entry (never zeros) | - |
| Plan block | card | - | `plan, status, period_end` from the ledger read (35) | - |
| Money error states | ProblemMoney | - | `provider_required` → provider step focus; `unknown_provider` → list refresh; `amount_changed` → `Price changed — review` + re-quote; 429 → cooldown timer | - |

## Ops config screen (internal)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Eligibility matrix | grid | yes | plan/product × provider toggles | current version |
| Display order | drag list | yes | rail order per surface | config order or API order |
| Kill switches | toggles | yes | per rail; flip reason mandatory | all off |
| Pending TTL | number | - | per-rail minutes (default 30; bank rails 1440) | defaults |
| Flip history | list | - | actor, reason, at, before/after | newest first |
