# Checkout Session - UI Data Dictionary (feature 33)

Phase 1 BA · Checkout-specific surfaces; shared money components (ProviderList, PendingPoll, StatusBadge, ProblemMoney, PriceLine) are defined in `docs/01-requirements-ba/32-monetize-overview/04-ui-data-dictionary.md` and reused.

## Checkout page

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Intent summary | card | yes | plan name + `term` (plan intents) or product title + kind; from quote/catalog context only | - |
| PriceLine | text | yes | server quote `amountVnd`; client never computes | quoted amount |
| Coupon input | text+apply | no | applies server-side; invalid/expired → inline `ProblemMoney` | empty |
| ProviderList | list | yes | rendered verbatim from `payment-methods` (order + hints + disabled reasons) | config order or API order |
| Email field | input | conditional | required for guests and receipts; malformed → 422 `email_required` inline | session email prefilled when known |
| Ship-to block (name/phone/address) | inputs | conditional | required when kind is `merch`; validated shapes; never logged | hidden for non-merch |
| Opt-in checkbox | checkbox | no | unchecked by default; explicit consent only; copy names the sender + `source=checkout` | off |
| Pay CTA | button | yes | disabled until provider chosen; idempotent click (client key + fingerprint) | - |
| Quote drift notice | notice | - | shown when create returns `amount_changed`/`quote_expired`: `Price changed — review` + re-quote action | - |

## Pending / return page (reuses 32 components)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Order context | text | yes | `orderCode`, amount, rail — display only | from create/URL |
| StatusBadge | badge | yes | canonical only; return query params never render as state | polling (3 s, backoff, hidden-pause) |
| Rail handoff re-open | link | conditional | url/sheet kinds re-open the handoff; qr renders server payload | - |
| Expired state | notice | - | `Link expired — start a new checkout` + re-entry action | terminal |
| Cancelled state | notice | - | `Cancelled` + re-checkout action | terminal |
| Support hint | text | - | `Contact support with order code <orderCode>` on unknown/failed states | - |
| Guest receipt notice | notice | conditional | guests: "Receipt sent to your email" (masked address) | - |

## Support/ops lookups

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Order lookup | search | yes | by `orderCode` only; foreign/absent → uniform not-found | - |
| Cancel action | button | conditional | pending only (`authorizing` hides it — capture in flight, stated); reason captured in audit; `cancel_conflict` explained | - |
| Eligibility panel | list | - | config version + per-rail state (enabled/disabled + reason) | - |
| Guest row marker | tag | - | `guest` rows show claim-bound status (no account) | - |
