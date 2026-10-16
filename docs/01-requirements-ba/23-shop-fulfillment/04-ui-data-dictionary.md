# Shop Fulfillment - UI Data Dictionary (feature 23)

Phase 1 BA · Design manager, job queue, buyer surfaces, paywall editor.

## Design manager (dashboard)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Artwork upload | file input | yes | ≤ 20 MB; PNG/JPEG/WebP/PDF; magic-byte checked | first upload = v1 |
| Design version | text | - | `v{n}`; new upload → `v{n+1}` | latest highlighted |
| Template picker | card row | yes | `tee|hoodie|mug|tote` | tee |
| Variant matrix | checkbox grid | yes | partner-driven availability; stockout variants disabled with label | all offered |
| Pin version | select | yes | pins `designVersion` on the product payload | latest |
| AI generate button | - | - | not rendered while feature 16 is gated; never stubbed | absent |

## Job queue (dashboard)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Job row | table row | - | order ref, design `v`, template, variant, qty, status chip | newest first |
| Status chip | badge | - | the six statuses; `failed` opens detail | derived |
| Partner SLA date | text | - | partner-provided value; labeled "partner-provided" | hidden when absent |
| Base cost | text | - | partner-billed VND; display only | hidden until billed |
| Reprint action | button | - | allowed only on `failed`/defect flag; new linked job marked non-billable | - |

## Buyer surfaces (order page, feature 34)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Status trail | timeline | - | the six statuses; partner name shown; dates labeled partner-provided | - |
| Download button | button | - | minted per click; shows remaining downloads + days | disabled when expired → re-purchase CTA |
| Download counter | text | - | `n of 5 used` | server truth |
| Unlock region (paywall) | container | - | rendered only after the body fragment returns 200 | preview visible when locked |

## Paywall editor

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Preview body | textarea | yes | ≤ 5000 chars; sanitized; SSR-visible | - |
| Locked body | textarea | yes | ≤ 50 000 chars; sanitized markdown; versioned on save | - |
| Attached video | file input | no | video allowlist; ≤ 500 MB; magic-byte checked | none |
| Upgrade policy | select | - | `free|paid` for the next version | free |

## Extend-access dialog (creator)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Buyer reference | text | yes | buyer id from the order row | prefilled |
| Extend days | numeric input | no | 1-90 | 30 |
| Add downloads | numeric input | no | 0-20 | 5 |
| Confirm | button | - | audited courtesy action | - |
