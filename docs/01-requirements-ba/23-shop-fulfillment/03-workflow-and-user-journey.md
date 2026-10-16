# Shop Fulfillment - Workflow and User Journey (feature 23)

Phase 1 BA · System calls cite owning features; forward contracts cite 32-40.

## Merch: design → dispatch → status

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | uploads artwork; picks template + variants; pins the version on the product payload | design row (versioned); product keeps `{designId, designVersion, template, variants[]}` | BR-01/02 |
| 2 | Buyer | orders a variant with ship-to at checkout | order created (32-40) collecting minimized ship-to | BR-16 |
| 3 | System | grant-confirmed payment (`order.granted.v1`, forward) | job `pending_print` created exactly once per order line | BR-03 |
| 4 | Shop-service | dispatch hook | partner adapter `createJob` (idempotent job key) | adapter interface |
| 5 | Partner | status callbacks | verified → queued → applied transitions; buyer + creator see them | BR-04 |
| 6 | System | pre-print refund/chargeback (`order.refunded.v1` or `order.chargeback_reversed.v1`; forward) | job `cancelled`; adapter `cancelJob` | BR-13 |

## Digital: upload → purchase → download

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | uploads a file version; publishes it | live product now has a `current` version (publish precondition for live) | BR-07 |
| 2 | Buyer | buys the product (checkout 32-40) | grant → entitlement row (30 d, 5 fetches) | BR-08/09 |
| 3 | Buyer | taps Download | `access-link` mints a single-use download grant (≤ 15 min), increments the counter | BR-08 |
| 4 | Buyer | re-downloads while entitled | fresh URL per request; window checked each time | BR-09 |
| 5 | Creator | extends access (courtesy) | audited extension restores the window exactly | BR-09 |
| 6 | System | refund | entitlement revoked; pre-print job cancelled; repurchase = new order | BR-13 |

## Paywall: preview → unlock

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | writes a preview + locked body (text/markdown, optional video) | locked body stored as a versioned payload; preview stored on the product | BR-10 |
| 2 | Visitor | opens `/{username}/shop/{productId}` | SSR preview only; cacheable; zero locked bytes | BR-10 |
| 3 | Buyer | paid → entitlement exists | the session fragment `/api/v1/digital/{productId}/body` renders the locked body | BR-08/10 |
| 4 | Non-buyer | requests the body fragment | 403 `entitlement_required`; nothing cached | BR-10 |

## Anomaly / support

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System | one entitlement, many IP classes | entitlement `flagged`; re-issue paused; ops reviews | BR-12 |
| 2 | Support | verifies the buyer | unflag (audited) or direct to repurchase | BR-12/15 |
| 3 | Purge runner | buyer deletes account | ship-to + buyer id scrubbed from job rows; entitlements of that buyer deleted | BR-16 |
