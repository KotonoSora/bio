# Tier Presentation - Acceptance Criteria (feature 03)

Phase 1 BA · BDD checks gating the release of `docs/02-architecture-techlead/03-tier-presentation/`. Rules cited as `BR-nn` from `docs/01-requirements-ba/03-tier-presentation/02-user-stories-and-business-rules.md`.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Claim-trace drill | Every pricing-page sentence (tiers, saving %, trial days, cancel effect, change effects) | Traced to registry + enforced spec after a config change | Each resolves to an enforcement point + config/catalog key; stale claims fail CI; extra claims fail CI (BR-11) |
| AC-02 | No-literals drill | Copy sources and registry | The drift test runs | Zero hard-typed amounts/discounts/day-counts; values render from config (BR-03) |
| AC-03 | Discount drill | Monthly price + configured discount | Yearly checkout math renders | Charge equals 12x monthly minus discount to the VND; config rejected above 44%; badge omitted when unset (BR-04) |
| AC-04 | Plan read drill | Free account; then pro snapshot; then feature 36 unreachable | `GET /api/v1/plan` is read in each state | Free renders plainly; pro shows tier; subscription rows appear only when the projection answers - never invented (BR-03, BR-05) |
| AC-05 | Capability drill | Free account with one profile; then pro snapshot | A second profile is created | `422 profile_limit_reached` on free; allowed on pro within the 30 s snapshot bound; value never hardcoded (BR-09, BR-10) |
| AC-06 | Cancel drill | Logged-in subscriber (feature 36 fixtures) | Cancel is opened + confirmed | Two clicks total; service honors period end; reminders stop; downgrade pre-scheduled losslessly; no further invoices appear (BR-06) |
| AC-07 | Trial drill | Eligible user; one-shot rails fixture | Trial starts and expires | No instrument required; day-count equals config; expiry is lossless; second-ever trial refused (BR-08) |
| AC-08 | Change drill | Mid-term upgrade; monthly-to-yearly switch | Each change completes | Upgrade stacks a fresh term; switch re-prices at renewal with stated dates; no screen implies proration (BR-07) |
| AC-09 | Absence drill | `yearlyDiscount` and `trialDays` unset | Pricing page renders | Badge and trial CTA are absent (no placeholders); page otherwise complete (BR-03) |
