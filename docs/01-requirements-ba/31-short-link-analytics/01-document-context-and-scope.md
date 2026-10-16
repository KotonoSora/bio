# Short-Link Analytics - Context and Scope (feature 31)

Phase 1 BA · Feature 31 of 53 — url-shortener 3/3. Legacy sources: `url-shortener/tracking/link-analytics.md` plus its database/backend/frontend plans (folded; deleted with this batch). Realized by `docs/02-architecture-techlead/31-short-link-analytics/`; an extension of `analytics-service` per the pilot's boundary map (`docs/02-architecture-techlead/29-short-links/01-architecture-and-tech-stack.md`). Short-link truth: feature 29; QR generation: feature 30.

## Purpose

Turn short-link opens into trustworthy counts: the hit pipeline, bot/fraud filtering, per-link readouts, and the separation from profile analytics. Advertisers, affiliates, and campaign owners make spend decisions on these numbers, and the redirect hot path must stay millisecond-fast — so counting is asynchronous, filtered, and dispute-replayable, never inline and never approximate.

## Objectives

| # | Objective |
|---|---|
| O-1 | Hot path stays untouched: resolve validates → redirects → enqueues; counting never blocks or slows a redirect. |
| O-2 | Honest numbers: dedupe, bot/crawler exclusion, prefetch suppression, and velocity quarantine happen before aggregates; exclusion counts are first-class readout data. |
| O-3 | Replayable evidence: any reported figure decomposes to retained raw hits within the retention window (`raw − excluded = reported` holds exactly). |
| O-4 | Clean separation: short-link hits never enter profile analytics (two services, two databases — no cross-write path exists); the bio-block dual-count case is labeled, not suppressed. |
| O-5 | Operations stay possible: floods are quarantined with an ops case, not silently deleted; release/drop decisions are human and audited. |

## Personas / RBAC

| Role | CRUD Permissions | Description |
|---|---|---|
| Link owner (creator/operator) | R own stats + own CSV | sees opens, uniques, devices, referrers, scan-vs-click splits per link with exclusion transparency |
| Dispute reviewer (ops) | R replay evidence; C quarantine decisions | replays any billed/reported count from stored evidence within TTL |
| Visitor | none (counted indirectly) | counted once per genuine open; never tracked across links into a behavioral profile |
| Affiliate/ad consumer (forward) | R scoped stats (own codes) | same aggregates through their own scoped endpoints when those majors land |

## In scope

Hit pipeline (enqueue → consumer → aggregates), visitor dedupe, fraud filters in fixed order, scan-vs-click estimation + exact event-pack splits, daily aggregates + segment breakdowns, exclusion counts per reason, velocity quarantine + ops decisions, owner stats + CSV, ops dispute replay, raw NDJSON evidence lifecycle, repair from raw, retention/prune + purge participation, separation guarantees.

## Out of scope

Minting, namespaces, redirect semantics (feature 29); QR rendering (feature 30); profile analytics content (feature 12's tables — separation is the contract, not overlap); affiliate attribution and ad measurement internals (their majors consume these aggregates); custom short domains (named future work); realtime (non-aggregated) short-link views (deferred, backend Q2).

## Open questions (resolved)

| Q | Resolution |
|---|---|
| Q1 bot-exclusion posture | Money-tied traffic (billed, affiliate-, ad-attributed) counts under the strictest exclusion posture; the shared posture matrix is recorded once. Divergences from creator-analytics defaults are explicit and labeled — never silent. |
| Q2 quarantine release | Human-only release in v1 (auto-release on velocity decay deferred; trigger: ops review backlog sustained over the SLA). Release replays the held batch through the normal pipeline (dedupe still applies); drop keeps evidence until the TTL. |
| Q3 sweep-repair window | Repair reads raw for the full raw retention (90 days). Beyond it, reported figures stand as reported — evidence aged out by design, and the aging is documented in the readout footnotes. |
| Q4 execution plans folded | Database/backend/frontend plans folded into this feature's TL. DEVIATION CARRIED (pilot boundary map): the legacy monolith verdict maps to `analytics-service` consuming `link.hit.v1`; producer-side hot-path rules and queue contracts are feature 29's and unchanged. |
