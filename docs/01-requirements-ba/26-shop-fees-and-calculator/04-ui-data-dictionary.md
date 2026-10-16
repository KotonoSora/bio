# Sales Fees and Profit Calculator - UI Data Dictionary (feature 26)

Phase 1 BA · Schedule page, checkout, calculator, tier comparison, scenario list, dashboard decomposition. No field here carries a literal rate value; everything renders from published config.

## Public schedule page

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Version stamp | text | - | `rate_version` shown | from config |
| Tier columns | table columns | - | `free` / `pro` | - |
| Kind rows | table rows | - | seven kinds; link-out row states "no platform fee" | - |
| Platform fee | text | - | rendered from `pct_bp` with the version's rounding rule | - |
| Rail passthrough | text | - | "at provider cost, labeled" | - |
| Payout terms | text | - | delay + minimum from config | - |
| Tips note | notice | - | "Tips follow the standard schedule." | always shown |

## Checkout (surface rules)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Payable total | text | - | one number; never grows at the last step | buyer-facing |
| Fee lines | list | - | seller-side order view only | hidden from buyer |

## Calculator (seller)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Template / variant | selects | yes | from the cost table | first entry |
| Base cost | text | - | contractual label; from cost table | readonly |
| Shipping estimate | numeric input | no | zone-averaged; labeled estimate | 0 |
| Rail estimate | numeric input | no | labeled estimate | 0 |
| Mode | toggle | yes | forward / reverse | forward |
| Desired profit | numeric input | forward only | VND integer ≥ 0 | - |
| Target price | numeric input | reverse only | VND integer; below-cost blocked on publish | - |
| List price | text | - | rounded per version rule | computed |
| Profit / margin | text | - | margin in basis points; formatted for display | computed |
| Line decomposition | list | - | contractual vs estimate labels | always shown |
| Save scenario | button | - | name ≤ 120 chars | - |

## Tier comparison

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Free vs pro profit | two columns | - | identical inputs, both published rows | - |
| Break-even units | text | - | live plan prices (forward 36); "pending" when unpriced | pending |
| Requote banner | notice | - | old-vs-new when the pinned version is stale | when stale |

## Scenario list + dashboard decomposition

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Scenario row | list item | - | name, version, pinned `rate_version`, updated time | newest first |
| Version diff | expander | - | old-vs-new inputs/outputs | on demand |
| Publish price | button | - | writes price + pinned version; below-cost disabled with shortfall | - |
| Gross / fee lines / net | text trio | - | seller order view; labels verbatim from the snapshot | - |
