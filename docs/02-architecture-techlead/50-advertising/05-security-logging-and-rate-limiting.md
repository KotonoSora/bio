# Advertising (Inventory, Serving, Creator Share, Retargeting) - Security, Logging, and Rate Limiting (feature 50) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/50-advertising/`. GATED: S-1 is live now; all other controls bind the future build at the spec amendment. Baseline controls: `docs/02-architecture-techlead/00-platform-baseline/`.

## Controls

| # | Control | Rule |
|---|---|---|
| S-1 | Gate enforcement (LIVE) | Tooling asserts zero ad surface each release (schema/OpenAPI/source/bundle); a hit blocks promotion — the gate is tool-enforced, not policy |
| S-2 | Separation integrity | Ads and analytics are separate databases and code paths; cross-reference probes run in CI; ad traffic cannot pollute creator analytics and vice versa |
| S-3 | Brand safety | Prohibited verticals + category conflicts enforced server-side (approval + serving); takedown is one audited action, everywhere ≤ 5 min; never silently served |
| S-4 | Counting integrity | Queue-only writes; 10-min dedupe; viewport verification; bot exclusion; click landing validation; velocity quarantine excludes from billing aggregates; evidence bundles immutable |
| S-5 | Money integrity | Integer VND; versioned rate snapshots (forward-only); monthly close; clawbacks proportional to tainted share with evidence + notice; payout via 49's runner only (no second system) |
| S-6 | Privacy | First-party only; coarse targeting only; no behavioral profiling, no cross-site identity; segment membership TTL is the privacy mechanism; opaque per-creator refs; no visitor PII in advertiser payloads |
| S-7 | Consent arbitration | Email reach rides 48's eligibility per recipient; lifecycle vs marketing decided at send time; every message carries cause + one-click stop; frequency caps enforced |
| S-8 | Click hygiene | Signed creative refs (tampered rejected), throttled redirect, noopenener/sponsored/ugc rel on CTA links, https allowlist |
| S-9 | IDOR + RLS | Advertiser own-flights reads; creator own opt-in/earnings; creators cannot touch flights; privacy-reviewer read-only on segments; cross-scope probes → 403/404 with zero rows |
| S-10 | No export, no third-party | Off-platform export surface absent (404/422); no third-party pixels or SDKs exist; any future lift requires an amendment + privacy review |

## Rate limits (at amendment; Redis db 14)

| Surface | Limit | On breach |
|---|---|---|
| Click redirect | 600/h/IP (edge budgets) | 429 / drop |
| Internal decide | 3000/h/service | 429 |
| Segment preview/size | 120/h/creator | 429 `throttled` |
| Ops actions (flights, creatives, reports) | 120/h/operator | 429 |
| Retarget flight launch/pause | 60/h/creator | 429 |

## Degrade matrix (at amendment)

| Failure | Behavior |
|---|---|
| Decision service down/timeout | Slot collapses ≤ 50 ms; page never waits; zero layout shift |
| Measurement outage | Bounded-retry queue + sweep-and-repair; no loss, no doubles |
| Redis down | Decision cache misses → conservative eligibility recompute or collapse; dedupe degrades to short fixed windows |
| Segment evaluator lag | Sizes show `as of` + labeled lag; serving uses last membership snapshot |
| 48's eligibility down | Email reach fails closed (no send); onsite unaffected |
| 49's runner down | Creator payables queue in the next cycle; ledger truth unaffected; nothing double-pays |
| Notification pipeline down | Statements queue with backlog; ledger unaffected |

## Audit and observability (at amendment)

Audits (24-month): flight lifecycle, creative approvals/rejections/takedowns, payout handoffs, clawbacks, segment definition changes, opt-in changes. Metrics: impressions/clicks/CTR, filter counts by reason, quarantine volumes, decision latency, collapse rate, earnings/confirmed/clawbacks, segment sizes, frequency suppressions — zero visitor labels.

## Support answers (at amendment)

"Why did my report drop?" → filter transparency: exclusions by reason + dedupe math, replayable from evidence bundles. "Why don't I see ads?" → opt-in state (default OFF) + category declarations. "Why did my earnings drop?" → dashboard: clawback lines with reasons + evidence links; estimates vs confirmed never mixed. "Why can't I retarget off-platform?" → v1 boundary: first-party only; surface absent by design. "Why did my page show no ad?" → empty inventory or decision timeout collapses — by rule, never a blank box.
