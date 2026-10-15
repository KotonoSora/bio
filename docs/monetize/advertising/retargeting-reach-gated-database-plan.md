# Retargeting Reach Gated Database Plan (segments + membership + flights — designed, not built)

PLAN ref: none — P4-growth, GATED (first-party only; no third-party pixels/uploads/deals in v1). BA source: `docs/monetize/advertising/retargeting-reach.md`. Consumed by the retargeting backend plan (evaluator, eligibility, flights, lift reporting; splits next to this file). Inventory/decisioning/earnings live in the sibling advertising plans (consumed, never redefined); broadcasts in `docs/monetize/marketing/campaign-broadcasts.md` (gated backlog); consent classes in `docs/monetize/marketing/consent-preferences.md` (gated backlog). No migration — nothing is created until the plan update.

## Purpose

Hold the buildable retargeting schema: versioned segment definitions, TTL'd membership (expiry as the privacy mechanism), retarget flights with holdout discipline, and the per-creator isolation that makes platform-wide pooling impossible. Exactly the future `CREATE TABLE`s, written once at plan update; zero tables now.

## Users

- Backend developer (future): runs these scripts once at plan update; evaluator writes membership only through TTL'd rows.
- QA (now): absence drill; (future) segment/consent/export-refusal/fatigue/lift drills.
- Privacy reviewer (future): verifies consent basis + first-party boundary per segment before serving.

## Functional requirements

1. Present state: zero segment tables, zero evaluator, zero retarget flights. Absence verified per release.
2. Table `ads.segments` (future) — `segment_id`, `profile_id` (per-creator membership ONLY — no platform-wide pooling; the column's presence with a required profile scope is the structural guarantee), `definition_version` (running segments never silently change meaning — definition edits version, membership recomputes), `event_set` (closed v1: page/profile viewers, product viewers, preview-finishers, checkout abandoners, past buyers), `lookback_days` (7/30 windows + membership cap), `consent_basis` (lifecycle vs marketing — behavioral membership never overrides consent class; eligibility arbitrated per recipient at send time).
3. Table `ads.segment_membership` (future) — `segment_id`, `member_ref` (opaque per-creator key — no cross-creator joinable identity; refs are meaningless outside their creator scope by construction), `entered_at`, `expires_at` (TTL'd rows — expiry IS the privacy mechanism, not a cleanup chore; lookback end deletes the row, it doesn't flag it).
4. Table `ads.retarget_flights` (future) — `flight_id`, `segment_id`, `surface` (`onsite|email`), `control_pct` (holdout slice — no lift claim without its control; control members are never contacted, enforced in send eligibility, not in reporting math), `status` (draft|live|paused|ended). Off-platform audience export has NO table and NO endpoint in v1 (attempted custom-audience upload → 404/422 by absence — deferred to a reviewed v2).
5. Evaluator inputs (existing pipelines, no new ingestion): beacon aggregates + order events consumed on minutes cadence ("abandoned 10 minutes ago" reachable, lag labeled honestly in-UI). No per-hit synchronous writes (evaluator is a scheduled/queue consumer like all pipelines). Segment sizes exact-ish (dedupe-tolerant) with `as of` timestamps (precision labeled, never implied).
6. Frequency + fatigue (data-backed): per-segment contact caps (email + onsite caps per recipient per week, creator-tunable within platform ceilings — counters, not prose); exclusion segments (past buyers excluded from acquisition by default); cause + one-click stop carried per message (template fields, asserted in send tests).
7. Measurement rule: holdout-first reporting (flight reserves no-contact control; lift vs control, never vs zero); reports show size, reach, conversion, suppression counts, reconciling to underlying events within dedupe tolerance.

## Data / API touches

- DDL now: none — assertion: pre-update tags show zero segment/evaluator/flight surface (schema + OpenAPI + source grep). Future: three tables per rules 2–4 + evaluator over existing event streams (minutes cadence) + membership reads at decision time (cache short TTL, PG truth). RLS (future): per-creator scoping throughout; ops sees platform aggregates only.

## Acceptance criteria

- Absence drill (now): zero retargeting surface every release.
- Segment drill (at build): fixture streams produce exact membership (entry/expiry/exclusion verified).
- Consent drill (at build): opted-out abandoner gets lifecycle reminder only; promo refused with logged reason.
- Export-refusal drill (at build): custom-audience upload → 404/422 by absence.
- Fatigue drill (at build): caps + exclusions + cause + stop verified across surfaces.
- Lift drill (at build): holdout lift reconciles within dedupe tolerance.

## Out of scope

- Evaluator/eligibility/flight/lift implementation (backend plan); segment/flight screens (frontend plan); inventory/decisioning/counting/earnings (sibling plans); broadcasts (marketing backlog); consent classes (marketing backlog); leads capture; third-party pixels/brokers/cross-site tracking (never v1); P5.

## Open questions

- Q1: Real-time bar — minutes-scale confirmed, or hourly enough (BA Q1)?
- Q2: Off-platform export ever (hashed uploads under per-use consent) vs first-party-only permanently (BA Q2)?
