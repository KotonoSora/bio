# Tier Presentation - Context and Scope (feature 03)

Phase 1 BA · Feature 03 of 53 (account) · Implemented by `docs/02-architecture-techlead/03-tier-presentation/` (5 files); reads extend `account-service`. Consumers: feature 06 (`profiles.limit` capability), features 04, 09, 13-16 (plan via entitlement snapshot), features 32-40 (catalog/checkout/lifecycle own the mechanics presented here).

## Purpose

State what the pricing page and account screens promise — and make each promise traceable to an enforcing spec: two tiers (not three), monthly/yearly billing with a stated annual saving, a trial, one-click cancel, and mid-term changes with exact effects. Buyers never discover a smaller truth behind a bigger headline.

## Objectives

- O-1 Two tiers, named plainly: Free and Pro (the link.me-style third tier is intentionally not copied).
- O-2 Every claim resolves: each pricing-page sentence maps to an enforcing spec + a config value; amounts, discounts, and day-counts are never hand-typed.
- O-3 Account-screen honesty: current tier, renewal date, and "your renewal price" always reflect the buyer's own terms, not the headline price.
- O-4 One entitlement key registered: `profiles.limit` (`"1"` free / `"unlimited"` pro) inside the feature 01 snapshot — the single mechanism feature 06's guard consumes.
- O-5 Absence is the design: unset config renders no claim (no placeholder numbers, no defaults).

## Personas and RBAC

| Role | CRUD Permissions | Description |
|---|---|---|
| Buyer | Read plans and own plan; Create checkout/change via features 33-36; Update own choice; Delete (cancel) own subscription | Compares tiers, tries pro, pays yearly with known savings, cancels or changes mid-term without fear. |
| Free creator | Read plans; Create upgrade | Sees exact loss/keep effects before upgrading. |
| Marketing/support | Read the claim registry; Update nothing | Quotes only guaranteed claims; answers "will I lose X?" from the lifecycle reference. |
| Reviewer | Read registry + specs; Update nothing | Verifies every claim resolves to an enforcement point; drift fails CI. |
| Attacker (adversarial) | none | Cannot fake entitlement via client flags; display values come from the server. |

## Resolved open questions

| Q | Decision |
|---|---|
| Q-01 Yearly discount value | Config key `yearlyDiscount` with a hard `<= 44%` validation guardrail (parity ceiling). Finance ratifies the exact figure before launch copy; until set, the saving badge is omitted — never a placeholder. |
| Q-02 Trial length | Config key `trialDays`, launch value 7 (full-pro). If config ships 14, the page states the configured value; the number is never hand-typed. One trial per user ever (feature 36 enforces). |
| Q-03 Pro claim set | Pro = unlimited profiles, custom domain, realtime analytics, AI allowances, lower sales fees — as *claim names*. The feature-capability matrix truth is features 32-40's catalog; fee/allowance values are finance-owned and never restated here. |
| Q-04 Second-profile direction | `profiles.limit` registered in the feature 01 capability map: free `"1"`, pro `"unlimited"`; feature 06's `profile_limit_reached` guard consumes it (06 Q-01 seam resolved). |

## In scope

- Pricing-page presentation contract: tiers, term toggle, saving badge, trial CTA, cancel/change effect copy.
- Account-screen plan read (`GET /api/v1/plan`) contract: tier + own-terms fields; forward subscription projection from feature 36.
- Claim registry + CI drift test; config keys and their validation guardrails; absence rules.
- `profiles.limit` capability registration and its consumer contract with feature 06.

## Out of scope

- Catalog truth, prices, checkout, payments, subscriptions, trials, cancel/change execution (features 32-40 own every mechanic; this feature presents them).
- Gate enforcement (each feature's own file; gates read the feature 01 snapshot).
- Fee values and AI credit allowances (finance-owned config in features 22-26/35).
- Seat billing (features 46-47) and the gated backlog's monetization surfaces (48-53).

Related: rules in `docs/01-requirements-ba/03-tier-presentation/02-user-stories-and-business-rules.md`; contracts in `docs/02-architecture-techlead/03-tier-presentation/03-api-contracts-and-payloads.md`; error surface in `docs/01-requirements-ba/03-tier-presentation/05-exception-handling-and-errors.md`.
