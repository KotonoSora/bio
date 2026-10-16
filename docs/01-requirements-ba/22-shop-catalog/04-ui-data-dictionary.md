# Shop Catalog - UI Data Dictionary (feature 22)

Phase 1 BA · Dashboard products list + editor, public shop page, block picker.

## Dashboard list

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Status chip | badge | - | `draft` / `live` / `archived` | derived from status |
| Cover thumb | image | - | placeholder tile when none | stored cover, cropped 4:3 |
| Title | text | yes | ≤ 120 chars | required |
| Kind badge | badge | - | one of the seven; localized labels | fixed at create |
| Price | text | yes | VND integer formatting `1.000.000 ₫` (vi) / `1,000,000 VND` (en) | `0` → `Free` (course/tip) or `See partner site` (linkout) |
| Orders count | text | - | read from 32-40 when the major lands | hidden until then |
| Sort handle | drag handle | - | reorder persists `sort_position` | creation order |
| Actions menu | menu | - | Edit; Publish; Archive; Delete | Delete on an ordered product → 409 + Archive CTA |

## Editor fields

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Kind selector | select | yes | disabled after first publish | `digital` |
| Title | text input | yes | ≤ 120; sanitized | - |
| Description | textarea | no | ≤ 2000; sanitized; plain text | - |
| Price (VND) | numeric input | yes | integer ≥ 0; per-kind rule shown inline | 0 |
| External URL | url input | linkout only | required; `https:` only | - |
| Cover | file input | no | ≤ 2 MB; JPEG/PNG/WebP/AVIF; magic-byte checked | placeholder tile |
| Status action | primary button | - | Save draft / Publish / Archive per current state | - |
| Price-change notice | inline notice | - | visible while editing a live price: "Orders keep the price they were placed at" | - |

## Public shop page

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Shop header | profile block | - | display name + avatar + bio excerpt (existing features) | bio parity |
| Product card | card | - | cover, title, kind badge, price | sorted by creator order |
| Buy button | button | - | buyable kinds; deep-links checkout with `productId` | - |
| Link-out button | button + disclosure | - | `Sold elsewhere — buy on partner site` + destination domain named | outbound; beacon first |
| Empty state | text | - | "Nothing for sale yet" | zero live products |
| Unavailable card | placeholder | - | shown on bio blocks for archived/deleted/foreign targets | never 404/500 |

## Block picker (bio editor)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Product picker | select | yes | own products only; non-live marked with a status chip | live products first |
| Product option | row | - | cover thumb + title + price + status chip | - |
| Publish warning | inline notice | - | shown when the picked target is not live: "This item renders as unavailable until it is live" | - |
