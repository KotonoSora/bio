# Shop Fulfillment - Acceptance Criteria (feature 23)

Phase 1 BA · BDD; legacy drills preserved by name.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | No-dispatch drill | unpaid and cancelled merch orders | the dispatch hook runs | zero print jobs exist; the partner mock receives nothing |
| AC-02 | Idempotency drill | a duplicate grant delivery for one order line | dispatch runs | exactly one job exists; the unique `(order_id, order_line)` key holds |
| AC-03 | Status drill | a partner callback sequence ending in `delivered`; plus a tampered callback | callbacks arrive | valid ones advance buyer-visible status end-to-end; the tampered one is rejected 401 with zero state change |
| AC-04 | Upload drill (merch) | non-image, oversized (> 20 MB), and SVG/HTML artwork | validation runs | all rejected before any file write with the exact codes |
| AC-05 | Upload drill (digital) | oversized (> 500 MB), fake-mime, and scriptable-type files | validation runs | all rejected before any file write; the allowlist holds |
| AC-06 | Grant drill | grant-confirmed, pre-payment, and refunded-buyer states | a download grant is requested | only grant-confirmed mints; others 403 with no URL; minted grants are single-use and ≤ 15 min |
| AC-07 | Expiry drill | lapsed and over-count entitlements; a courtesy extension | access is requested before and after | both refuse with the correct codes; the extension restores exactly the granted window |
| AC-08 | Leak drill | preview HTML/JSON, guessed file names, a shared grant URL | surfaces are inspected | zero locked bytes in previews; guessed names do not resolve; the shared link fails after first use/expiry |
| AC-09 | Version drill | a creator re-upload with `free` and with `paid` upgrade policy | old and new versions are accessed | old-version buyer access is preserved; `free` grants the new version; `paid` routes to repurchase |
| AC-10 | Precondition drill | a digital product with no current file version | publish is attempted | 409 `publish_precondition_unmet`; product stays draft |
| AC-11 | Anomaly drill | one entitlement minted from many IP classes | minting continues | the entitlement flags; further mints 403 `entitlement_flagged`; ops unflag audits |
| AC-12 | Purge/scrub drill | a seller account with jobs; a buyer account with jobs + entitlements | each purge runs | seller: rows + stored files deleted with counts; buyer: ship-to + buyer id scrubbed, entitlements deleted |
