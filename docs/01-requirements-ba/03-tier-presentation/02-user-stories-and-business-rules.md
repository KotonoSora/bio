# Tier Presentation - User Stories and Business Rules (feature 03)

Phase 1 BA · Enforced by `docs/02-architecture-techlead/03-tier-presentation/`. Scope: `docs/01-requirements-ba/03-tier-presentation/01-document-context-and-scope.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Buyer | compare two tiers, try pro, pay yearly with known savings, and cancel or change mid-term with exact effects | no headline hides a smaller truth | High |
| US-02 | Marketing/support | quote only guaranteed claims and answer "will I lose X?" from the lifecycle reference | buyers get traceable promises | High |
| US-03 | Reviewer | see every pricing-page claim resolve to an enforcement point | each promise is checkable and drift fails CI | High |
| US-04 | Free creator | understand exactly what a second profile costs before creating it | no surprise refusals at the create button | Medium |
| US-05 | Pro subscriber | see my own renewal price, not the headline price | renewals are never a surprise | High |

## Business rules

- **BR-01 Two tiers.** Free and Pro only; no third tier is presented (the parity doc's Plus is intentionally not copied). Tier names, order, and claim names are fixed by the registry; copy may rephrase, never re-claim.
- **BR-02 Matrix truth.** The plan-feature matrix lives in features 32-40's catalog; this page is its presentation. Any drift between registry and catalog fails review and CI.
- **BR-03 No literals.** Amounts, discounts, and day-counts render from config + catalog rows at request/build time; hand-typed values in copy fail the drift test. Absence rules: unset config omits the claim (no placeholder, no default).
- **BR-04 Yearly math.** Yearly = 12x monthly minus the configured discount, computed and displayed to the exact VND; the discount config is validated `<= 44%` at load; the engine reads config, never copy.
- **BR-05 Renewal honesty.** Renewal reminders and the account screen state the buyer's own next amount from the then-active catalog version; grandfathering is presented as "your renewal price"; price changes never surface only at charge time.
- **BR-06 One-click cancel.** Account -> plan -> Cancel: one confirmation stating service-to-period-end and exact downgrade effects; no retention maze, no support ticket; export-first offer before term ends. Cancel is a billing action: step-up applies when re-auth is older than 10 min (feature 01).
- **BR-07 Mid-term changes (narrow, presented honestly).** Upgrades stack a fresh term; plan switches re-price at next renewal; no proration in v1 (the change screen states this with effective dates); monthly-to-yearly applies the discount at next renewal.
- **BR-08 Trial.** One trial per user ever; configured length rendered from `trialDays`; no payment instrument on one-shot rails; expiry is lossless (downgrade pre-scheduled, nothing deleted).
- **BR-09 Presentation never gates.** All plan gates are server-side and read the feature 01 entitlement snapshot (fail-closed); UI hiding is honesty, not enforcement.
- **BR-10 `profiles.limit` capability.** Registered in the snapshot map: free `"1"`, pro `"unlimited"`. Feature 06's create guard counts existing profiles against it and returns `422 profile_limit_reached` beyond; the value is never hardcoded in feature 06.
- **BR-11 Claim registry.** Every pricing-page sentence has a registry row: claim text pattern, enforcing spec path, config/catalog key. The registry is the review artifact; CI renders copy from it and fails on stale or extra claims.
- **BR-12 Money display.** All amounts are VND integers from the server; the SPA formats and never computes its own math beyond display formatting; percentages shown only when the config value exists.

## Constraints carried

- Step-up, entitlement snapshot, and throttle contracts are consumed from `docs/02-architecture-techlead/01-account-and-access/03-api-contracts-and-payloads.md`; nothing is re-decided.
- Fee percentages, AI allowances, and yearly discount final values are finance-pending; the `<= 44%` ceiling is the only numeric guardrail stated here.
