# Shop Fulfillment - Context and Scope (feature 23)

Phase 1 BA · Feature 23 of 53 — shop 2/5. Legacy sources: `shop/merch/pod-fulfillment.md` and `shop/digital/digital-delivery.md` (folded here; deleted with this batch). Realized by `docs/02-architecture-techlead/23-shop-fulfillment/`; extends shop-service (feature 22).

## Purpose

Two fulfillment lanes behind the uniform catalog: merch sells with zero inventory (AI-free design → print templates → partner prints and ships; the platform holds files, routing, and the status trail) and digital/paywall sells one secure pattern (pay first, personal expiring download link; paywalled content unlocks in place).

## Objectives

| # | Objective |
|---|---|
| O-1 | Merch: creator uploads/versions artwork, pins a design version to a product, and every paid order dispatches exactly one print job to the partner. |
| O-2 | Partner status flows back through verified, queue-mediated callbacks: `pending_print → printing → shipped → delivered | failed | cancelled`. |
| O-3 | Digital: paid grant → entitlement → per-request single-use download grants; windows 30 days + 5 fetches, creator-extendable. |
| O-4 | Paywall: SSR preview indexable; locked body unlocks only after the server-side entitlement check. |
| O-5 | No-inventory invariant: zero stock counts, zero invented SLAs, every date labeled partner-provided. |

## Personas / RBAC

| Role | CRUD Permissions | Description |
|---|---|---|
| Creator (owner) | C/R/U on own designs; R on own jobs; upload/publish file versions; courtesy extensions | sets up merch + digital delivery |
| Buyer | R own entitlements; mint own access links; R own order status | pays, downloads, re-accesses while entitled |
| Fulfillment partner (integration) | W callbacks only (signature-verified) | receives print jobs, reports status, bills base costs |
| Order page (34, consumer) | R job status via internal read | buyer-facing status trail |
| Anonymous | none; previews only | paywall preview visible; locked bytes never |

## In scope

Merch design uploads + versioning; template/variant matrix; print-job dispatch + status trail + reprint/cancel rules; partner adapter interface; digital file upload/version/publish; entitlements + download-grant minting; paywall preview/locked rendering; courtesy extensions; anomaly flagging; purge + buyer-side ship-to scrub.

## Out of scope

Catalog/listing/pricing (22); AI design generation (gated feature 16 — nothing stubbed); profit math (26); checkout/orders/refunds/receipts (32-40); course progress (24); tips/consultations (25); subscription-gated paywall (v2, 36); PDF watermarking (v2, named not promised); guest/email claims (not v1).

## Open questions (resolved)

| Q | Resolution |
|---|---|
| Q1 partner choice + SLA floor | Partner selection stays open; the vendor-neutral adapter interface ships now and exactly one implementation lands at selection (mock used by tests). No SLA floor is invented: displayed dates are partner-provided and partner-labeled. |
| Q2 AI design | Gated: ships only with feature 16 (and its `ai_generations` credit pool); designs are upload-only until then — DEVIATION FLAGGED vs the legacy design-complete intent. |
| Q3 digital file cap | 500 MB per file; no total per-creator quota in v1 (storage monitored; tier quotas arrive with 35/36). |
| Q4 watermarking | v2, named not promised; v1 relies on single-use expiring URLs. |
| Q5 buyer identity | Entitlements are account-bound in v1 (SSO sessions); guest/email claims do not ship. |
| Q6 paywall formats | Text/markdown long-form body + optional video file; both behind the same entitlement check. |
