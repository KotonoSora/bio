# Creator Revenue Share (opt-in earnings and payouts)

PLAN ref: none — new scope beyond PLAN P0–P5, flagged (see `advertising/ad-inventory.md` for placement). Inventory there; counting in `advertising/ad-serving-measurement.md`.

## Purpose

Define how creators earn from ads on their pages and get paid: opt-in economics, earnings computation off filtered measurement only, and payouts reusing the affiliate payout discipline. Creators supply the audience; without a transparent, predictable share, opt-in stays at zero and the inventory is theoretical. Every dong a creator sees must trace to a counted, fraud-filtered event.

## Users

- Creator: opts in, watches estimated vs confirmed earnings, meets a threshold, receives payout with a statement.
- Program operator: sets rev-share rates, runs payout cycles, handles invalid-traffic clawbacks fairly.
- Finance: books rev-share expense off the same ledger pattern as affiliate commissions.

## Functional requirements

1. Opt-in economics: per-creator toggle (default off, see inventory doc); rev-share rate config-driven and versioned (e.g. fixed percent of attributable flight spend/impression value — rate model follows the inventory pricing decision in that doc's Q2; rate changes apply to future events only, never rewrite history). Earnings accrue only on fraud-filtered, counted events — quarantined traffic never earns, even if later cleared (cleared traffic re-enters aggregates, not earnings, to keep payouts dispute-proof).
2. Earnings ledger (mirrors the affiliate commission discipline): `creator_ad_earnings{day, profile_id, flight_id, amount_vnd, status (estimated|confirmed|paid|clawed_back)}`. Daily estimates from filtered aggregates; `confirmed` at flight/invoice close (or monthly close, see Q1); `paid` on payout-rail confirmation. Invalid-traffic findings post-close move lines to `clawed_back` with the linked evidence and creator notice — never silent, proportional to the tainted share only.
3. Payout cycle: shared mechanics with affiliate payouts (monthly cycle, minimum threshold per Q2, sub-threshold roll-forward, audited batches, VN bank-rail transfers with dual control, per-cycle statements in dashboard + email). One payout system serves both affiliates and creators where possible — a single `payout_batches` runner with earner-type partitioning, not two finance processes.
4. Thresholds and eligibility: earnings payout requires completed tax/identity fields (same boundary as affiliate payouts — paperwork deferred to finance/legal, never improvised); self-view/click farming (creator inflating own slots) voids the tainted earnings and triggers the same warn-then-restrict ladder as affiliate gaming, up to opt-in revocation.
5. Transparency minimums: creator dashboard shows estimated vs confirmed balances, per-flight breakdown, filter-exclusion counts affecting their pages, threshold progress, and payout history with references. "Why did my earnings drop" must be answerable from the dashboard (clawback lines carry reasons + evidence links).
6. Plan interplay: ad earnings never gate features and feature gates never force ads — free creators earn the same share formula as pro (no plan-based rate discrimination in v1); payout thresholds identical across plans.

## Data / API touches

- New table `creator_ad_earnings` (schema above) + reuse of shared payout-batch infrastructure (earner-type partitioned). RLS: creators read own lines/statements; batch execution restricted to finance-ops role. All PG via `worker/db/*`.
- Endpoints: creator `GET /api/v1/creator-ads/earnings` (estimates/confirmed/history), `GET /api/v1/creator-ads/statements/:id`, opt-in toggle (inventory doc); ops batch confirm + clawback actions (audited). All financial actions append `audit_log`.
- Rate config versioned alongside catalog discipline (effective-dated rows; events stamped with the applicable version).

## Acceptance criteria

- Earnings drill: counted events accrue estimates; flight/month close flips them confirmed; payout cycle pays with matching statements; numbers reconcile event→aggregate→ledger→statement end to end.
- Clawback drill: post-close invalid-traffic finding moves only the tainted share to `clawed_back` with notice + evidence; legitimate earnings untouched and still payable.
- Self-farming drill: creator inflating own slots voids tainted earnings, triggers the warn/restrict ladder, preserves legitimate history.
- Shared-runner drill: affiliate + creator lines settle in one audited batch run with correct per-earner statements and no cross-visibility.
- Auth probe: creator A reading creator B earnings → 403; RLS denies cross-creator rows.

## Out of scope

- Inventory, formats, and brand safety (see `advertising/ad-inventory.md`); decisioning, counting, and fraud filters (see `advertising/ad-serving-measurement.md`).
- Affiliate commissions (`affiliate/` — separate earner type, shared runner); promo discounts (`promo-code/`); creator analytics (`docs/link-to-bio/`); third-party ad-tech; P5.

## Open questions

- Q1: Earnings confirmation point — flight close vs fixed monthly close (which simplifies statements)?
- Q2: Payout threshold for creators — same floor as affiliates, or lower to encourage early opt-in?
