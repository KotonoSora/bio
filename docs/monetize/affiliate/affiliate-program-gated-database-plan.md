# Affiliate Program Gated Database Plan (affiliates + tiers + terms — designed, not built)

PLAN ref: none — P4-growth, GATED (money-to-strangers begins here; eligibility/tiers/termination specified up front). BA source: `docs/monetize/affiliate/affiliate-program.md`. Consumed by the program backend plan (enrollment, tiers, ladder; splits next to this file). Attribution evidence in the tracking plans; computation in the commission plans (same major). No migration — nothing is created until the plan update.

## Purpose

Hold the buildable program schema: audited enrollment with manual approval, versioned commission tiers, terms acceptance log, and the exposure cap that pauses accrual review instead of silently underpaying. Exactly the future `CREATE TABLE`s, written once at plan update; zero tables now. Stranger-payout fraud starts at signup — auto-approval has no code path by design.

## Users

- Backend developer (future): runs these scripts once at plan update; enrollment writes only through application→manual-approval.
- QA (now): absence drill; (future) enrollment/tier/conduct/cap drills.
- Affiliate/operator (future): enrolls under versioned terms; tiers step next-month-only.

## Functional requirements

1. Present state: zero affiliate tables, zero enrollment endpoints. Absence verified per release.
2. Table `aff.affiliates` (future; schema `aff` — affiliate module, extractable with its tables): `id`, `user_id?` (nullable — applicants may not be platform users yet; attribution joins on affiliate id, never on user presence), `display_name`, `status` (`applied|active|suspended|terminated`), `tier`, `payout_details_enc` (encrypted at rest, same vault discipline as P4 OAuth tokens — never logged, rotation-safe), `terms_version` + `terms_accepted_at` (acceptance-logged; material adverse changes require re-acceptance, defined in terms). Statuses: suspension freezes new attribution, preserves earned-unpaid balances for review; termination forfeits ONLY fraud-tainted earnings per terms (legitimate history untouchable without review — forfeiture scope is a column rule on tainted lines, not a balance wipe).
3. Table `aff.commission_tiers` (future): versioned rate rows (default rate per plan term + product category; volume tiers stepping next month on monthly referred revenue — never retroactive within a month, keeping computation auditable). Rate changes apply to attributions after the effective date; in-flight windows keep the old rate (rate snapshot stamped per attribution at decision time — history never reprices).
4. Attribution basis (rule, enforced in tracking slice): commissionable = paid plan grants + paid product orders (refunded/disputed reverse); trials, 0-VND promos, self-purchases never commission. Commission base = net collected VND (gross vs net-of-fees decided once at plan update per Q2, disclosed in terms — one base everywhere, no per-rail exceptions).
5. Program economics guardrail: total commission exposure cap per period in config (percentage of referred net revenue); breach pauses NEW accrual review (earned balances intact and visible — pausing review is honest throttling; silent underpayment is theft, structurally prevented by keeping earned lines untouched).
6. Relationship boundary (data-explicit): no discounting authority, no support obligations, no sub-affiliate tiers in v1 (no parent-affiliate column exists — MLM-shaped structures unrepresentable, not just disallowed).
7. Buyer-price rule: referred checkout totals identical to direct totals (margin-funded, never surcharged — enforced by the absence of any affiliate-price-adjustment path in checkout code, asserted in review).

## Data / API touches

- DDL now: none — assertion: pre-update tags show zero affiliate tables/endpoints (schema + OpenAPI + source grep). Future: two tables per rules 2–3 + terms-acceptance columns (full `CREATE TABLE`s written at plan update from this design). RLS (future): affiliates read own rows only; program config restricted to ops. Payout details encrypted (vault, never logged).

## Acceptance criteria

- Absence drill (now): zero affiliate surface every release.
- Enrollment drill (at build): application → manual approval → attributable; auto-approval impossible (no code path exists — asserted, not configured-off).
- Tier drill (at build): mid-month threshold steps next month only; per-order rate stamps auditable.
- Conduct drill (at build): stuffing → suspension (attribution frozen, balance held) → upheld termination forfeits tainted-only with notice + appeal log.
- Cap drill (at build): breach pauses accrual review; earned balances intact and visible.
- Auth drill (at build): cross-affiliate 403 + RLS deny.

## Out of scope

- Enrollment/tier/ladder flows (backend plan); affiliate screens (frontend plan); link/code mechanics + windows (tracking plans); computation/payouts (commission plans); referral pro-day credits; influencer batches; agency seats; P5.

## Open questions

- Q1: Launch rates — flat opening vs tiered day one (BA Q1, finance + margin model)?
- Q2: Commission base net-of-fees vs gross (BA Q2 — decide once, disclose)?
