# Tier Presentation - Workflow and User Journey (feature 03)

Phase 1 BA · Flows executed by `docs/02-architecture-techlead/03-tier-presentation/`. Rules cited as `BR-nn` from `docs/01-requirements-ba/03-tier-presentation/02-user-stories-and-business-rules.md`.

## Pricing page render

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Visitor/Buyer | Opens the pricing page | Two tier cards render from the active catalog + config; term toggle (monthly/yearly) | BR-01, BR-02 |
| 2 | System | Yearly selected | Saving badge renders only when `yearlyDiscount` is set; amount = 12x monthly minus discount to the VND | BR-03, BR-04 |
| 3 | System | Trial eligible surface | Trial CTA shows `trialDays` from config; hidden when unset | BR-03, BR-08 |
| 4 | System | Copy renders | Every sentence resolves to a registry row (spec path + key); drift fails CI | BR-11 |
| 5 | Reviewer | Checks claims | Registry + rendered copy match; stale claim after config change fails CI | BR-11 |

## Upgrade and second profile

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Free creator | Attempts a second profile | Guard counts profiles vs `profiles.limit` from the snapshot; free -> `422 profile_limit_reached` | BR-09, BR-10 |
| 2 | Free creator | Opens the upgrade path | Pricing/checkout (features 33-34) with exact effects; step-up when re-auth stale | BR-06, BR-09 |
| 3 | System | Plan change commits | Snapshot updates; capability flips within `<= 30 s`; second profile then succeeds | BR-09, BR-10 |

## Cancel

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Pro subscriber | Account -> plan -> Cancel | One confirmation states service-to-period-end + exact downgrade effects + export-first | BR-06 |
| 2 | Subscriber | Confirms | Cancel executes via feature 36 mechanics (no retention maze); step-up applies when stale | BR-06 |
| 3 | System | Term ends | Downgrade executes losslessly (data retained; pro-only serving stops per owning features) | BR-07, BR-09 |

## Trial start and expiry

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Eligible user | Starts trial | One trial per user ever; no instrument on one-shot rails; stated day-count from config | BR-08 |
| 2 | System | Trial expires | Lossless downgrade pre-scheduled; conversion nudge states exact continuation price | BR-08, BR-05 |

## Renewal reminder

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System | Reminder window | States buyer's own upcoming amount from the then-active catalog version | BR-05 |
| 2 | Buyer | Opens account plan panel | Sees "your renewal price", period end, and change/cancel paths | BR-05, BR-06 |
