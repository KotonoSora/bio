# Promo Codes - Exception Handling and Errors (feature 39)

Phase 1 BA · Failure and edge behavior. Rules: `docs/01-requirements-ba/39-promo-codes/02-user-stories-and-business-rules.md`. Codes follow RFC 9457; exact JSON in `docs/02-architecture-techlead/39-promo-codes/03-api-contracts-and-payloads.md`.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Unknown code | Error | 422 `coupon_invalid` (neutral; same timing as all failures) | "This code can't be applied." |
| Expired code | Error | 422 `coupon_expired` (the one safe hint) | "This code has expired." |
| Exhausted code (uses = limit) | Error | 422 `coupon_invalid`; no hint | "This code can't be applied." |
| Not eligible (targeting / per-user limit) | Error | 422 `coupon_invalid`; no hint (eligibility reasons never surface) | "This code can't be applied." |
| Already redeemed by this user (single-use) | Error | 422 `coupon_invalid`; no hint | "This code can't be applied." |
| Campaign paused / not live at validation | Error | 422 `coupon_invalid` | "This code can't be applied." |
| Budget or redemption cap reached at create | Error | 422 `coupon_invalid` to the buyer; auto-pause to ops | "This code can't be applied." |
| Campaign dies between quote and payment | Edge | In-flight inviolability: verified frozen-amount payment grants; `coupon.in_flight_honored` audited; new creates blocked | - |
| Order expires/cancels without a grant after a coupon create | Edge | Budget decrement restored exactly once (guarded by the terminal transition) | - |
| Second code entered while one applied | Edge | Replacement confirmation required | "Replace {code} with {new code}?" |
| Coupon + trial or credit-days | Happy edge | Matrix allows both; trial unchanged; days extend the discounted term | - |
| Renewal after campaign window | Edge | Renewal re-prices from the active catalog; coupon rejected (window gone) with honest copy | "Your one-time discount doesn't apply to renewals." |
| 0-VND result while a provider was selected | Edge | Handoff skipped (kind `none`); provider overridden to `promo` server-side; immediate grant | Order shows as promotion; no charge |
| Client sends `provider='promo'` directly | Error | 422 `unknown_provider` (reserved literal) | "That payment method isn't available." |
| Multi-use code race (concurrent grants) | Edge | Guarded `uses` update: exactly one wins per unit; loser's order path unaffected (already paid at quote) | - |
| Per-user coupon race (double create) | Edge | Advisory lock per (user, coupon) serializes; limit holds | - |
| Validate brute force | Error | 429 `throttled` after shared budget exhaustion; timing stays normalized | "Too many attempts — try again later." |
| Ops voids unredeemed codes mid-flight | Edge | In-flight pending orders keep their quote; new creates blocked | - |
| Goodwill issuance with cap config absent | Error | 409 `promo_config_absent`; nothing minted | Ops: "Configure promo policy before issuing credits." |
| Goodwill above the configured cap / malformed | Error | 422 `validation_failed` with bounds | Ops form validation |
| Non-ops caller on campaign/batch/goodwill routes | Error | 403 `billing_ops_required` | "You don't have access to promo operations." |
| Illegal lifecycle move (e.g., launch an ended campaign) | Error | 409 `campaign_conflict` | Ops: current state shown; no partial write |
| Batch export requested after acknowledgment | Error | 409 `export_acked`; hashes only exist | "This batch's plaintext export was already acknowledged." |
| Buyer tries to list campaigns/codes | Error | No route exists; IDOR probes hit validation-neutral errors only | - |
