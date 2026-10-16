# Tier Presentation - Exception Handling and Errors (feature 03)

Phase 1 BA · Failure paths handled in `docs/02-architecture-techlead/03-tier-presentation/05-security-logging-and-rate-limiting.md`. Codes are stable contract; problems follow RFC 9457.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Entitlement snapshot unreachable on a gated action | API | Consumer fails closed to free; `entitlement_snapshot_miss` counted (feature 01 contract) | The gated feature's own denial/upsell message |
| Subscription projection (feature 36) timeout | API | Plan read returns tier with subscription fields absent; no invented amounts | Renewal/trial rows omitted; tier still shown |
| Catalog unreachable on pricing page | Public page | Cached catalog serves (cache-first, 300 s bound); if empty, page renders tier names + claims without prices and a soft retry line | "Prices are refreshing - try again in a moment." |
| `yearlyDiscount` unset | Render | Saving badge omitted; yearly price shows plain 12x monthly until finance sets the value | No badge |
| `yearlyDiscount` > 44% at config load | Boot/config | Configuration rejected; service refuses to start with that config | None user-visible (deploy-time failure) |
| `trialDays` unset | Render | Trial CTA omitted; no default invented | None |
| Trial already used | Checkout (feature 36) | Refused there; this page shows no trial CTA for ineligible users | Feature 36's message (e.g., "Trial already used") |
| Change/cancel attempted with stale step-up | API (feature 36, gated by 01) | `403 step_up_required`; nothing applied | "Confirm it's you to continue." |
| Cancel while a change is pending | API (feature 36) | Lifecycle rules decide ordering; this page never promises an effect the lifecycle cannot honor | Feature 36's stated rule + effective dates |
| Copy with a hard-typed amount/percent/day-count | CI | Drift test fails the build; registry and copy must match | None (build-time failure) |
| Claim present in copy but missing in registry | CI | Same drift failure (extra-claim check) | None (build-time failure) |
| Client-supplied plan flag tampering | API | Ignored: gates read the server-side snapshot only; UI flags are never trusted | Gates behave per entitlement truth |
