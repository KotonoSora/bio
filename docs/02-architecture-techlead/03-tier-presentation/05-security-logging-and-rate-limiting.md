# Tier Presentation - Security, Logging, and Rate Limiting (feature 03)

Phase 2 TL · BA sources: `docs/01-requirements-ba/03-tier-presentation/` (BR-03, BR-04, BR-09, BR-11). Consumed controls: `docs/02-architecture-techlead/01-account-and-access/05-security-logging-and-rate-limiting.md`; baseline: `docs/02-architecture-techlead/00-platform-baseline/08-security-baseline.md`.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | Presentation never gates: every capability check is server-side against the feature 01 snapshot, fail-closed; client flags are never trusted | AC-05; tampered-flag probe |
| S-2 | No literals: copy renders only from registry + config; literal-number scan and extra-claim check fail CI | AC-02 drift test |
| S-3 | Config guardrail: `yearlyDiscountPct <= 44` validated at load; invalid config refuses boot; amounts are integral VND | AC-03 boot drill |
| S-4 | Money display integrity: amounts travel as server-computed VND integers; the SPA formats only - no client-side proration, discount, or percentage math | Render fixtures compare byte-for-byte against server values |
| S-5 | Billing-change gating: cancel/change are sensitive actions (feature 01 step-up when re-auth is stale); nothing applies on `403 step_up_required` | AC-06/AC-08; stale-step-up probe |
| S-6 | Entitlement fail-closed: snapshot miss -> free clamp (feature 01 contract); the pricing page never widens access | AC-04/AC-05 outage drill |
| S-7 | Public plans read: cache-first, versioned payload, no per-user data; baseline anonymous envelope applies | Rate probe under burst |
| S-8 | Registry integrity: a copy change and its registry row move in the same change; review checklist requires the mapping | AC-01 claim-trace |
| S-9 | Degrade honesty: outages render absence (no stale prices beyond 300 s, no placeholders); alerts on catalog/projection failures | AC-04, AC-09; outage drill |

## Rate envelopes

| Route class | Envelope | On breach |
|---|---|---|
| `GET /api/v1/plans` (public) | anonymous public-read class (120 req/min/IP baseline); served from edge cache | `429 throttled` + `Retry-After` |
| `GET /api/v1/plan` (account) | 60 req/min/user | `429` |
| Internal projections (subscription, entitlement) | service budget, trace-attributed | `429` + trace; caller circuit-breaker |

## Degrade matrix

| Condition | Behavior |
|---|---|
| Entitlement snapshot unreachable | Free clamp (feature 01); gated features deny with their own codes; counter `entitlement_snapshot_miss` |
| Subscription projection timeout | Plan read returns tier only; renewal/trial rows absent; no invented values |
| Catalog unreachable | Cached payload serves (300 s bound); empty cache renders names + claims without prices and a soft retry line |
| Config unset/invalid | Unset: claim omitted. Invalid: boot refused (deploy-time, never a live page) |

## Logging

- Fields: route, status, timing, trace, `plan` value, cache hit/miss, config version; never user PII or payment data (features 32-40 own any money logging).
- Alertable: catalog fetch failures, projection timeouts, cache hit ratio below target, drift test failures in CI.

## Gates

- G4: `/api/v1/plans` and `/api/v1/plan` additive-only; the frozen payload shape is rename-protected.
- G7: registry export + rendered snapshot + drift result attach to the tag; feature 06's second-profile drill result referenced.
