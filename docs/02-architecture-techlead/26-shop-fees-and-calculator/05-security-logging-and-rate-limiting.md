# Sales Fees and Profit Calculator - Security, Logging, and Rate Limiting (feature 26)

Phase 2 TL · BA sources: `docs/01-requirements-ba/26-shop-fees-and-calculator/` (BR-02..BR-17, NFR-05..NFR-11). Baseline: `docs/02-architecture-techlead/00-platform-baseline/08-security-baseline.md`; gates: `docs/02-architecture-techlead/00-platform-baseline/06-release-gates.md`.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | Dual control: DB CHECK `requested_by <> approved_by` + route check + role check (`finance_role_required`); any self-approval attempt refused and alerted | AC-11; direct-SQL probe |
| S-2 | Config write lockdown: fee/cost tables read-only to app paths; writes only via `svc`-context ops routes; no session token can mint a rate | TL 02 drill 4; API probes |
| S-3 | No-invention: zero literal rates in code, env, seeds; quotes refuse `schedule_not_published` pre-publication; CI grep gate on this directory | AC-12 |
| S-4 | Math integrity: integer basis points + long arithmetic; overflow guards at feature 22 amount bounds; zero floats (static check) | AC-01, AC-06 |
| S-5 | Display honesty: buyer one-total drift test across schedule/checkout/dashboard surfaces | AC-02, AC-04 |
| S-6 | Immutability: published versions and order snapshots are append-only (no UPDATE policy path; corrections = new versions) | AC-03, AC-07; SQL probes |
| S-7 | IDOR + RLS: scenarios owner-scoped; publish-price owner-scoped; foreign reads 403 + zero rows | AC-10 |
| S-8 | Cost integrity: cost rows ops-entered, audited, never defaulted; `cost_unknown` blocks math | AC-06 (cost half); audit review |
| S-9 | Audit: drafts/approvals/publishes, cost updates, publish-price, scenario writes append-only, 24-month retention; ids + deltas only, never copy | NFR-09 |
| S-10 | Secrets: ops route credentials env/vault only; no `.env`; schedule endpoint exposes config rows only (no internal ids beyond `rate_version`) | secrets audit |

## Rate envelopes

| Route class | Envelope | On breach |
|---|---|---|
| Quote endpoints (seller + internal) | 120/min per caller | 429 |
| Calculator saves / publish-price | 60/h per seller | 429 |
| Ops rate/cost routes | 30/h per operator | 429 + alert |
| Schedule read | 60/h per IP | 429 (cached downstream) |

## Degrade matrix (fail safe, never fail open)

| Condition | Behavior |
|---|---|
| No published version | Quotes 409 `schedule_not_published`; no copy renders; checkout fee-snapshot stays release-gated off |
| Plan-prices seam down (36) | Break-even shows "pending"; quotes unaffected |
| Ops routes down | No rate or cost change; existing published version serves; no default fallback exists |
| Checkout/ledger down (32-40) | Quotes still serve sellers; no order snapshot exists without them (their domain) |
| Schedule cache down | Direct reads (DB) serve; 60 s cache miss only |
| DB read replica lag | None in this feature (single shop_db; no replicas) |

## Threat checklist

Self-approval of rates, silent rate edits after publish, session-context config writes, float drift in money math, copy drift between schedule and checkout, buyer-visible fee leaks (display violation), below-cost publish bypass, scenario IDOR, cost-table tampering (audited ops only), stale break-even from cached plan prices, historical rewrite attempts.

## Gates

- G4: OpenAPI diff additive; codes registered; schedule route public-allowlisted; ops routes internal-only (service JWT + role).
- G5: S-1 self-approval drill, S-2 write-lockdown probes, S-4 overflow/rounding boundary matrix, S-5 drift test.
- G7: AC-01..AC-12 attach to the tag; container drill (Docker + Podman) per baseline release gates; release gate: checkout fee-snapshot enablement requires a published version.
- Greps: zero fee literals in this directory and defaults; zero floats in the engine; secrets clean.
