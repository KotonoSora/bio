# Advertising (Inventory, Serving, Creator Share, Retargeting) - Workflow and User Journey (feature 50) - GATED

Phase 1 BA · W-1..W-6. GATED: all flows are design; nothing executes until the amendment. Rules: `docs/01-requirements-ba/50-advertising/02-user-stories-and-business-rules.md`. Contracts: `docs/02-architecture-techlead/50-advertising/03-api-contracts-and-payloads.md`.

## W-1 Flight lifecycle

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Ad-ops | drafts a flight (placements, creatives, window, caps) | draft state; creatives queued for pre-approval | BR-05 |
| 2 | Ad-ops | approves creatives | prohibited/conflicting creatives rejected; approved set ready | BR-04 |
| 3 | Ad-ops | launches | live window + caps + eligibility matrix enforced; status `live` | BR-05, BR-08 |
| 4 | Ad-ops | pauses or ends | instant pause; ended terminal; takedown variant pauses everywhere ≤ minutes + audit | BR-04, BR-05 |

## W-2 Serve → count

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Visitor | loads a page with a slot | decision ≤ 50 ms from cache (eligibility matrix) or slot collapses with zero layout shift | BR-08 |
| 2 | Visitor | sees the unit | viewport-verified impression ping → queue → dedupe (10-min) → aggregate | BR-09, BR-10 |
| 3 | Visitor | clicks | redirect through `/api/a/{ref}` (signed ref, throttled); dedupe + landing validation | BR-09, BR-10 |
| 4 | Advertiser | reads the report | aggregates + filter-exclusion transparency; own flights only | BR-09 |
| 5 | System | anomalies | velocity floods quarantined from billing aggregates pending review | BR-10 |

## W-3 Creator opt-in

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | opens ad settings | toggle default OFF + per-page category declaration | BR-06 |
| 2 | Creator | opts in | units eligible on declared pages only; share formula shown (same for free and pro) | BR-06 |
| 3 | Creator | opts out mid-flight | serving stops immediately, no penalty; earnings to date stand | BR-06 |
| 4 | System | pro page probe | never forced ads — no path exists | BR-06 |

## W-4 Earnings → payout

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System | filtered events accrue daily | estimates appear in the dashboard (per flight) | BR-12 |
| 2 | System | monthly close | estimates → `confirmed`; statement lines freeze | BR-12, Q-05 |
| 3 | Finance | runs the shared payout runner | creator lines settle (earner-type partition) with dual control + statements | BR-14 |
| 4 | System | post-close invalid traffic found | only the tainted share → `clawed_back` with evidence + notice | BR-13 |
| 5 | Creator | reads "why did earnings drop" | dashboard answers with reasons + evidence links | BR-15 |

## W-5 Segment → reach

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator/advertiser | defines a segment (event set + lookback + cap) | versioned definition; live size with `as of` + labeled lag | BR-16 |
| 2 | System | evaluator consumes event streams (minutes cadence) | membership TTL rows enter/expire per lookback; per-creator only | BR-16 |
| 3 | System | onsite flight targets the segment | decisioning gains segment dimension; frequency caps enforced | BR-17 |
| 4 | System | email reach considered | eligibility arbitrates per recipient: lifecycle vs marketing per consent class | BR-17 |
| 5 | System | flight runs with holdout | no-contact control slice; lift reported vs control only | BR-18 |

## W-6 Takedown / abuse

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Ad-ops | takedown a creative | paused everywhere ≤ minutes; audited single action | BR-04 |
| 2 | System | self-farming detected | tainted earnings voided; warn-then-restrict ladder up to opt-in revocation | BR-13 |
| 3 | Privacy reviewer | audits a segment | consent basis + first-party boundary verified pre-serve | BR-17 |
| 4 | Anyone | attempts off-platform export | 404/422 — surface absent in v1 | BR-17, Q-08 |
