# Affiliate Tracking Attribution Gated Database Plan (clicks + attributions — designed, not built)

PLAN ref: none — P4-growth, GATED (every credit reproducible from stored evidence; disputes settled from rows, not memory). BA source: `docs/monetize/affiliate/tracking-attribution.md`. Consumed by the tracking backend plan (links, precedence, windows, anti-gaming; splits next to this file). Enrollment/tiers live in the program plans; computation in the commission plans (same major). Influencer codes consumed from `promo-code/code-generation.md` (backlog); referral credits explicitly excluded (never cash). No migration — nothing is created until the plan update.

## Purpose

Hold the buildable attribution schema: signed-cookie referral links, server-side click logs with privacy windows, order-stamped attributions with frozen rate snapshots, the closed precedence rule, and evidence bundles that end arguments. Exactly the future `CREATE TABLE`s, written once at plan update; zero tables now. Attribution disputes are the industry's top affiliate fight category — reproducibility is the product.

## Users

- Backend developer (future): runs these scripts once at plan update; attribution fixed pre-payment, never re-pointed.
- QA (now): absence drill; (future) precedence/window/reversal/stuffing/privacy drills.
- Dispute reviewer (future): replays any credit from its evidence bundle.

## Functional requirements

1. Present state: zero tracking tables, zero redirect endpoints, zero attribution writes. Absence verified per release.
2. Table `aff.affiliate_clicks` (future) — server-side click log (the cookie is a pointer, not the record): `id`, `affiliate_id`, `landing` (deep-link target preserved through redirect), `clicked_at`, `ip_hash` + `ua_hash` (hashed, for stuffing analysis + dedupe — never raw IPs, same structural privacy as analytics pipelines). Retained per the privacy window (Q2 — aligned with beacon/R2 TTL precedents at plan update); aggregation-only thereafter. Clearing cookies mid-window keeps these rows as fallback evidence FOR REVIEW, never for silent auto-credit (fallback needs human eyes — automation here would credit ghosts).
3. Table `aff.order_attributions` (future) — one row per attributed order: `order_id UNIQUE` (one attribution per order, ever — uniqueness IS the double-credit guard), `affiliate_id`, `source` (`link|code` — code beats cookie per precedence, recorded not assumed), `clicked_at?`, `window_days` (qualifying window, default 30d per Q1), `rate_snapshot` (frozen tier + base at decision — history never reprices), `status` (`pending|confirmed|reversed`). Attribution stamps at order CREATION (pre-payment, fixed forever after — post-payment re-pointing does not exist as a code path).
4. Binding precedence (closed, server-enforced, stored per row): affiliate promo code at checkout beats cookie (explicit buyer intent wins); cookie beats nothing (direct traffic unattributed — unattributed is a valid outcome, not a gap); most recent qualifying click wins among cookies. The winning `affiliate_id` + `source` + evidence refs store on the attribution row (decisions reproducible without re-running logic).
5. Commissionable-state machine: click → `pending` at order creation → `confirmed` at paid grant past the refund window → `reversed` on refund/dispute-loss (full or proportional). Trials, 0-VND orders, cancelled/expired orders, self-purchases (affiliate≡buyer identity/signal match) never leave `pending` (filtered at creation, not cleaned later — pending rows for non-qualifying events are still stored for audit, visibly non-payable).
6. Anti-gaming storage: stuffing signals (unattributed drops, iframe-hidden sets) rejected at ingest with signal rows for the terms ladder (rejected clicks ARE logged as rejected — the ladder needs evidence, and absence of logs would acquit stuffers); self-referral voids commission + raises the affiliate flag (per program ladder).
7. Privacy: first-party only (no third-party pixels v1); cookie `aff_id` signed, `HttpOnly`+`Secure`+`SameSite=Lax`, 30d default; declined-consent buyers check out normally with zero attribution (checkout never blocked — consent refusal degrades to unattributed, tested); affiliate stats show aggregates + own referrals, never buyer PII (stats queries structurally exclude buyer columns — same no-PII-column discipline as analytics tables).

## Data / API touches

- DDL now: none — assertion: pre-update tags show zero tracking tables/redirects (schema + OpenAPI + source grep). Future: two tables per rules 2–3 (full `CREATE TABLE`s at plan update) + signed-cookie contract + evidence bundles (click id, code?, timestamps, rate snapshot, order ref — stored immutably with the attribution row). RLS (future): affiliates read own clicks/attributions; buyers see nothing.

## Acceptance criteria

- Absence drill (now): zero tracking surface every release.
- Precedence drill (at build): code-beats-cookie, most-recent-wins, direct-unattributed — all reproducible from bundles.
- Window drill (at build): day-29 attributes, day-31 doesn't (configurable window respected to the second).
- Reversal drill (at build): confirmed→reversed full/proportional; self-purchase voids + flag.
- Stuffing drill (at build): floods rejected at ingest, dashboards unskewed, ladder warned.
- Privacy drill (at build): declined-consent checkout normal, zero attribution; stats PII-free.
- Auth drill (at build): cross-affiliate 403 + RLS deny.

## Out of scope

- Link/precedence/window/gaming implementation (backend plan); affiliate stats screens (frontend plan); enrollment/tiers (program plans); computation/payouts (commission plans); influencer batch supply (consumed as codes); referral credits; P5.

## Open questions

- Q1: Window length — 30d default vs shorter/longer (BA Q1)?
- Q2: Click-log retention window (BA Q2 — align with beacon precedents)?
