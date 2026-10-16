# Agency Seat Billing - Workflow and User Journey (feature 46)

Phase 1 BA · W-1..W-5. Rules: `docs/01-requirements-ba/46-agency-seat-billing/02-user-stories-and-business-rules.md`. Contracts: `docs/02-architecture-techlead/46-agency-seat-billing/03-api-contracts-and-payloads.md`.

## W-1 Subscribe → renew

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Owner | opens agency billing overview | seats, unit price, projected total, snapshot link shown; catalog values resolved | BR-02, BR-10 |
| 2 | Owner | subscribes with an explicit provider | 33's front door checkout (no default rail); grant activates `plan='agency'` for the contact | BR-04 |
| 3 | System | renewal approaches | snapshot job keeps daily rows; renewal order carries seat line data (seats charged + catalog version) | BR-01, BR-02 |
| 4 | System | renewal granted (36) | next cycle prepaid at renewal-day seat count; true-up of the past cycle computed from snapshots | BR-02, BR-03 |
| 5 | Owner | reads the invoice | lines cite snapshots; one-hop trace from overview | BR-03 |

## W-2 Seat churn → true-up

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Staff | invite accepted / member suspended / removed (45) | next daily snapshot reflects the change (acceptance day / removal day) | BR-01, BR-05 |
| 2 | System | cycle ends | true-up = day-weighted charge − prepaid seat charge; positive → next invoice line; negative → account credit | BR-02, BR-05 |
| 3 | Owner | views upcoming total | projected true-up shown with its snapshot basis | BR-03 |
| 4 | System | closed period passes | snapshots + invoice immutable; later roster changes never rewrite them | BR-03 |

## W-3 Billing-contact transfer

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Current contact/owner | initiates transfer to an active staff member | pending transfer (outgoing + incoming confirmations required); audit | BR-06, Q-04 |
| 2 | Outgoing | confirms | first confirmation recorded; billing unchanged | BR-06 |
| 3 | Incoming | confirms | both present → contact switches; instruments visibility moves; audit | BR-06 |
| 4 | System | single-sided attempt | billing untouched + audit | BR-06 |

## W-4 Lapse → cure (no-hostage)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System | renewal payment fails | 36's dunning + grace; agency entitlement flips to inactive at grace end | BR-07 |
| 2 | System | staff powers evaluate | new invites + Act-as-user starts refuse `agency_subscription_required`; roster readable | BR-07 |
| 3 | System | client surfaces evaluate | client profiles/pages stay live; nothing darkens | BR-07, BR-12 |
| 4 | Owner | cures payment | entitlement active again; held operations resume in order | BR-07 |

## W-5 Cancel / org delete

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Owner | cancels | cancellation at cycle end (36's lifecycle); copy states agency-wide effect + no-hostage | BR-04, BR-07 |
| 2 | System | cycle ends | staff powers evaporate; links sever per 45; client profiles untouched | BR-07 |
| 3 | Owner | deletes the org (45's flow) | subscription mapping closes; snapshots retained for audit; clients keep everything | BR-03, BR-07 |
| 4 | System | reconciliation runs | final counts recorded; zero client-side impact exists | BR-08 |
