# Advertising (Inventory, Serving, Creator Share, Retargeting) - Document Context and Scope (feature 50) - GATED

Phase 1 BA · Feature 50 of 53 — gated backlog 3/6. GATED: design-complete only — no table, route, job, or UI string ships until a spec amendment approves it (BR-01, AC-01). Sources: `advertising/ad-inventory.md`, `advertising/ad-serving-measurement.md`, `advertising/creator-revenue-share.md`, `advertising/retargeting-reach.md`. Implemented (future) by `docs/02-architecture-techlead/50-advertising/`.

## Context

Four coupled scopes: what the platform sells to advertisers (placement catalog, formats, targeting, brand safety); how an ad gets chosen and counted honestly (cached decisioning, fraud-filtered measurement, full separation from creator analytics); how creators earn from ads on their pages (opt-in economics, earnings ledger, payouts on the shared runner); and how first-party attention compounds (real-time segments, consented reach, holdout-first lift). v1 sells attention on our own surfaces via direct/sponsored deals served by our own decisioning — third-party ad-network SDKs and self-serve advertiser tooling are explicitly deferred. The future host is `ads-service` (port 8093, `ads_db`, Redis db 14); payouts hand off to the shared earner-type-partitioned runner (49). Nothing here is built while gated.

## Objectives

| ID | Objective |
|---|---|
| OBJ-01 | Explicit inventory: versioned placement catalog, one labeled unit per creator page, checkout/payment surfaces provably ad-free |
| OBJ-02 | Honest counting: queue-based measurement, dedupe + viewport + bot filters, evidence bundles, filter transparency |
| OBJ-03 | Full separation: ad traffic never pollutes creator analytics, in either direction |
| OBJ-04 | Brand safety enforced in code: category blocks both directions, creative pre-approval, instant takedown |
| OBJ-05 | Creator share traceable event → aggregate → ledger → statement; clawbacks proportional, notified, evidence-linked |
| OBJ-06 | First-party retargeting only: versioned segments, consent-arbitrated reach, holdout-first lift, no off-platform export in v1 |

## Scope

In scope (at amendment, SC-01..SC-06): placement catalog + flights + creatives; decisioning + measurement pipeline + fraud filters; creator opt-in + earnings ledger; segments + retarget flights + lift reports; shared payout handoff; ad-choice disclosure.

Out of scope: affiliate commissions (49 — separate earner type, shared runner); promo discounts (39); creator analytics (12/31 — untouched); broadcast mechanics (48 — consumed for email reach); third-party pixels, data-broker integrations, cross-site tracking (never in v1); ad-network SDKs + self-serve tooling + off-platform audience export (deferred with their own reviews).

## Personas and permissions

| Role | CRUD Permissions | Description |
|---|---|---|
| Advertiser (direct deal) | R own flights + reports | Buys placements for a period/audience; reads trustworthy performance reports |
| Creator | U own opt-in + category declaration; R own earnings/statements | Controls adjacency; earns from opted-in pages |
| Visitor | R (sees labeled units + why-this-ad) | Never impersonated-into creator content; never cross-site pursued |
| Ad-ops | C/R/U flights + creatives (approve/pause/takedown) | Runs the catalog and safety gates; no self-serve upload in v1 |
| Finance | R ledger; C payout batches (dual control) | Books rev-share expense off the same ledger pattern as 49 |
| Privacy reviewer | R segment definitions + consent bases | Verifies first-party boundary before a segment serves |

## Open-question resolutions

| # | Question (source) | Resolution |
|---|---|---|
| Q-01 | Prohibited verticals (inventory Q1) | Legal-finalized, config-owned (`ads.prohibited`); v1 seed: adult, gambling, misleading earning claims, crypto-yield promises; finance/health/children targeting excluded — list finalized by legal at the amendment |
| Q-02 | Pricing model (inventory Q2) | Flat-fee per flight confirmed for launch; measurement counts impressions + clicks from day one so CPM/CPC can switch without re-instrumentation; pricing values finance-owned |
| Q-03 | Bot-exclusion posture (serving Q1) | One posture, stricter at the billing boundary: known bots excluded (analytics-aligned) and velocity anomalies quarantined from billing aggregates pending review |
| Q-04 | SSR time budget (serving Q2) | Fixed 50 ms cap with collapse-on-timeout — the page never waits on ads |
| Q-05 | Earnings confirmation (rev-share Q1) | Fixed monthly close (one cadence, simpler statements); flight close does not force separate statements |
| Q-06 | Creator payout threshold (rev-share Q2) | Same floor as affiliates by default, config-owned; one runner, one threshold surface |
| Q-07 | Real-time bar (retargeting Q1) | Minutes-scale confirmed, with the lag labeled honestly in-UI and `as of` sizes |
| Q-08 | Off-platform export (retargeting Q2) | Refused in v1 (surface absent, not stubbed); any future lift requires an amendment + privacy review — first-party-only stands |

## Constraints carried

Targeting is privacy-conservative (page category/language, device class, coarse geo from existing signals — no behavioral profiling, no cross-site identity); ad impressions/clicks flow through ad tables only; quarantined traffic never earns even if later cleared; segments never pool across creators (opaque per-creator membership refs, TTL expiry as the privacy mechanism); every retargeted message carries its cause + one-click stop; no lift claim without its control slice.
