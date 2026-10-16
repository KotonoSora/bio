# Checkout Session - Exception Handling and Errors (feature 33)

Phase 1 BA · Codes extend the 32 catalog (`docs/01-requirements-ba/32-monetize-overview/05-exception-handling-and-errors.md`); nothing here is redefined, only added. Buyer copy is vi/en.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Unknown plan or product id | client error → 404 | validation before any row; no catalog internals in the response | `product_absent` / `plan_not_found` — "This item isn't available." |
| Plan intent without or with an unknown `term` | client error → 422 | validation before pricing | `term_required` / `term_invalid` — "Choose a billing term." |
| `free` plan intent | client error → 422 | the free plan is never purchasable | `plan_not_purchasable` — "That plan can't be purchased." |
| No active term row for the plan/term | client error → 409 | pricing closed; no row created | `plan_not_published` — "Plan pricing isn't available yet." |
| Product exists but not live | passthrough → 409 | shop context refuses (`product_not_live`); nothing created | "This item isn't on sale right now." |
| Fee schedule unpublished | passthrough → 409 | fees quote refuses (`schedule_not_published`); order creation blocked before insert | "Checkout is temporarily unavailable — try again shortly." |
| Ineligible provider for the intent | client error → 422 | matrix + kill-switch check; eligible list returned | `unknown_provider` — "This method isn't available for this item." |
| Coupon invalid or expired | client error → 422 | coupon validated server-side; order not created | `coupon_invalid` / `coupon_expired` — "This code isn't valid — remove it and try again." |
| Malformed/missing buyer email (guests) | client error → 422 | guests require a deliverable address; no row created | `email_required` — "Enter an email so we can send your receipt." |
| Off-allowlist return/cancel URL | client error → 422 | validated at create, never at redirect; nothing created | `return_url_invalid` — generic; details logged, never shown |
| Missing ship-to for merch | client error → 422 | required when kind is `merch`; nothing created | `ship_to_required` — "This item ships — add your delivery details." |
| Quote token expired (> 15 min) | client error → 410 | token rejected; fresh quote required; no row | `quote_expired` — "This price has expired — review the new price." |
| Price changed vs quote token | client error → 409 | create refused; logged; nothing charged | `amount_changed` — "The price changed — review before paying." |
| Same key, different intent | client error → 422 | existing order untouched; attempt logged | `idempotency_key_reuse` — "This request conflicts with an earlier one — start a new checkout." |
| Double-submit (same key/fp, keyless ≤ 90 s) | idempotent success | one row; same handoff re-issued | none — same pending page |
| Handoff build failure | transient → 502 | order stays `pending`; retry allowed; provider-health metric bumped | `handoff_failed` — "The payment could not start — try again or choose another method." |
| Cancel of a terminal or `authorizing` order | client error → 409 | only `pending` cancels (authorizing → 409 — capture in flight); already cancelled → 200 | `cancel_conflict` — "This order can no longer be cancelled." |
| Cancel racing grant | race → one winner | exactly one terminal outcome per 34's edges; loser gets the current status | none — status page shows the outcome |
| Late money after expiry/cancel | ops + buyer-safe | rejected + logged per the late-event rule; never auto-granted; rail-provider re-link only via audited exception (40) | "We received a late payment — contact support with your order code." |
| Sweep down > 15 min | degrade | expiry lags; orders remain `pending`; grant attempts on lapsed orders still refuse at 34; ops alert | none (user-invisible) |
| Rate limited | abuse → 429 | per-IP/session envelopes; Turnstile on anonymous surfaces | `throttled` — "Too many attempts — try again in a moment." (timer) |
| Lead opt-in write fails | degrade | bounded retry; failure surfaced in ops metrics only; order unaffected | none |
