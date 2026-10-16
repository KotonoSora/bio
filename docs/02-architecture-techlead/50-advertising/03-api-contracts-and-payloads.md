# Advertising (Inventory, Serving, Creator Share, Retargeting) - Contracts and Payloads (feature 50) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/50-advertising/`. GATED: none of these routes may appear in OpenAPI, the gateway, or any image until the spec amendment (AC-01). Reuse contracts: `docs/02-architecture-techlead/48-marketing/` (campaign path + eligibility), `docs/02-architecture-techlead/49-affiliate/` (payout runner handoff), `docs/02-architecture-techlead/12-profile-analytics/` (event streams).

## Endpoints (reserved, absent until the amendment)

| Endpoint | Auth | Notes |
|---|---|---|
| `POST /internal/v1/ads/decide` | service JWT | Server-to-server slot decision; eligibility matrix; ≤ 50 ms; empty → collapse |
| `GET /api/a/{ref}` | public (signed ref) | Click redirect, throttled; tampered refs rejected; landing validation |
| `GET /api/v1/ads/disclosure` | public | Why-this-ad: placement, category basis, coarse signals |
| `PUT /api/v1/creator-ads/optin` | creator session + CSRF | Toggle (default OFF) + category declaration; mid-flight opt-out immediate |
| `GET /api/v1/creator-ads/earnings` | creator session | Estimates/confirmed/history, per-flight, filter exclusions, threshold |
| `GET /api/v1/creator-ads/statements/{id}` | creator session | Per-cycle statement; matches email |
| `GET /api/v1/ads/flights/{id}/report` | advertiser session | Own flights only; aggregates + filter transparency |
| `GET /api/v1/ops/ads/flights`, `POST`, `PATCH` | ad-ops role | Draft/launch/pause/end; launch requires caps + window |
| `POST /api/v1/ops/ads/creatives/{id}/approve\|reject\|takedown` | ad-ops role | Pre-approval gate; takedown everywhere ≤ 5 min, audited |
| `GET /api/v1/ops/ad-reports/{flight}` | ad-ops role | Aggregates + exclusion math |
| `POST /api/v1/ads/segments`, `GET /{id}/size` | creator/advertiser session | Versioned definitions; live size with `as of` + labeled lag |
| `POST /api/v1/ads/retarget-flights`, `POST /{id}/pause` | creator/advertiser session | Surface onsite/email; control slice default 10% |
| `GET /api/v1/ads/retarget-flights/{id}/lift` | creator/advertiser session | Lift vs control only; no claim without control |
| `POST /internal/v1/payout-batches/earner-lines` | service JWT | **49 implements at amendment**: earner-type partitioned handoff (`creator_ads`) |

```kotlin
data class DecideRequest(val placementId: String, val segment: Map<String, String>, val visitorHash: String)
data class DecideView(val creative: CreativeView?, val collapse: Boolean)  // null -> collapse, no layout shift
data class CreativeView(val id: String, val assetUrl: String, val headline: String, val ctaUrl: String, val disclosureUrl: String)
data class EarningsView(val estimatedVnd: Long, val confirmedVnd: Long, val thresholdVnd: Long,
  val exclusions: Map<String, Long>, val clawbacks: List<ClawbackLine>)
data class SegmentDefinition(val profileId: String, val eventSet: String, val lookbackDays: Int,
  val membershipCap: Long, val consentBasis: String)
data class LiftReport(val segmentSize: Long, val reached: Long, val conversions: Long,
  val controlConversions: Long, val liftVsControl: Double, val suppressed: Map<String, Long>)
```

## Rules

- Decisioning: no real-time bidding, no third-party scripts; the response either serves one approved creative or collapses — the page never blocks and never shifts layout.
- Measurement: dedicated queue; dedupe 10-min per visitor/slot/creative; raw NDJSON + evidence bundles; filters reported by reason; zero creator-analytics writes exist in this service.
- Earnings: monthly close flips estimates; payout handoff rides 49's runner (no second payout system); quarantined traffic never earns; clawbacks proportional + notified + evidence-linked.
- Reach: onsite via decisioning; email only via 48's campaign path with 48's eligibility arbitration (lifecycle vs marketing per recipient); every message carries cause + one-click stop; off-platform export has no route (404/422).
- No visitor identity, advertiser secrets, or cross-scope data in any payload; advertiser reads scoped to own flights; creators cannot touch flights.
- Config (rates, thresholds, caps, prohibited list) is versioned and ops/finance-owned — zero hardcoded values (grep gate).

## Error catalog (reserved)

| Code | Status | UI Behavior |
|---|---|---|
| `creative_rejected` | 422 | Ops checklist shown; never served |
| `category_conflict` | - | Silent exclusion by rule; filter counts show it |
| `tampered_ref` | 400 | Redirect rejected |
| `segment_export_unsupported` | 404/422 | "Not available." (surface absent) |
| `cap_reached` | - | Exclusion by rule; cap headroom in ops view |
| `frequency_capped` | - | Suppressed with reason in report; never silent |
| `consent_class_denied` | 409 | Lifecycle variant only; reason logged |
| `clawback_notice` | - | Dashboard line + evidence link |
