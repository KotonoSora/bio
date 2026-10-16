# Agency Seat Billing - Exception Handling and Errors (feature 46)

Phase 1 BA · Problems follow RFC 9457 (`code` field). No instrument data, tokens, or amounts leak into errors beyond the caller's own scope.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Catalog lacks `agencyBaseVnd`/`agencySeatVnd` | API (subscribe/overview) | 409 `catalog_incomplete`; no values invented | "Agency pricing isn't configured yet — contact support." |
| No provider chosen at checkout | API | 422 `provider_required`; no default rail exists | "Choose a payment method." |
| Subscribe while already subscribed | API | 409 `already_subscribed`; overview link | "This org already has an active agency subscription." |
| Cancel/renewal with no subscription | API | 409 `not_subscribed` | "No agency subscription is active." |
| Owner subscribes while holding pro | System | Plan switch via 36's supersede path (close/open); agency ⊇ pro for the contact | None (continuity; invoices unchanged) |
| Checkout abandoned | Flow | No grant; nothing changes; retry offered | "Payment wasn't completed — nothing was charged." |
| Renewal payment fails | System | 36's dunning + grace; entitlement flips at grace end | Banners: "Update payment to keep agency tools." |
| Lapse: staff invites / act-as start | API | 409 `agency_subscription_required` (fail closed for staff powers) | "Agency tools are paused — the subscription lapsed." |
| Lapse: client surface probe | System | Client pages stay live; zero client-visible change | None |
| Seat-count dispute | Flow | One-hop snapshot trace from the line; closed periods immutable | Evidence view: "These are the days each seat was active." |
| Mid-cycle removal credit | System | Negative true-up lands as account credit on the next renewal | "Credit applied to your next invoice." |
| Refund requested | Flow | 38's machinery on request; no fees invented here | Standard refund copy (38) |
| Transfer single-sided | API | Pending state; billing untouched + audit | "Waiting for both confirmations." |
| Transfer to a client or inactive member | API | 422 `validation_failed` (target class) | "Choose an active staff member." |
| Cap flag set (future) | API | Invites over cap refused with the add-seat/upgrade path (off by default) | "Seat limit reached — add seats or upgrade." |
| Reconciliation mismatch | Job | Alert + forward-only correction with reason; audit | Ops view only |
| Closed-period rewrite attempt | System | Refused by design (immutability) | Ops: "Closed periods can't be changed — correct forward." |
| Manager requests amounts | API | Counts-only response shape; instruments never serialized | "You can see seat counts, not billing amounts." |
| Client requests billing surface | System | No route exists for clients (by absence) | None |
| Cross-org billing access | API | 403/404 by RLS + contact scoping; zero rows | "Not found." |
| Duplicate grant callback (provider) | System | Idempotent grant (existing machinery); one activation | None |
