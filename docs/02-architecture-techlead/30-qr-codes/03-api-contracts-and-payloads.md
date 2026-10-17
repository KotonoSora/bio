# QR Codes - API Contracts and Payloads (feature 30)

Phase 2 TL · Endpoints in `link-service`; ops routes follow feature 29's `ROLE_OPS` re-verified convention. Security: `docs/02-architecture-techlead/30-qr-codes/06-security-logging-and-rate-limiting.md`.

## Endpoint map

| Method & path | Auth | Request | Success | Errors (code) |
|---|---|---|---|---|
| `POST /api/v1/links/{code}/qr` | session + CSRF + Turnstile | `QrRenderRequest` | 201 `QrRenderView` | 404 `link_not_found`; 409 `qr_parent_not_live`; 422 `qr_below_floor`, `qr_low_contrast`, `validation_failed`; 429; 503 `render_unavailable` |
| `GET /api/v1/links/{code}/qr/renders?cursor=` | session | - | 200 `QrRenderPage` | 404 |
| `GET /api/v1/links/{code}/qr/sheet.svg?per=&sizeClass=&errorLevel=` | session | - | 200 SVG print sheet | 404; 422 `validation_failed` |
| `GET /api/v1/qr/renders/{renderId}/download?grant=&part=image\\|label` | grant (owner-bound) | - | 200 stream (PNG/SVG or label text) | 404 `qr_render_not_found`; 410 `grant_expired` |
| `POST /api/v1/ops/qr-packs` | ops (`ROLE_OPS` re-verified) + CSRF | `QrPackRequest` | 201 `QrPackResponse` | 403 `qr_pack_denied`; 422 `qr_pack_invalid`, `qr_below_floor`; 429; 503 |
| `GET /api/v1/ops/qr-packs/{packRef}/sheet.svg` | ops | - | 200 SVG pack sheet | 404; 403 |

## Kotlin DTOs

```kotlin
data class QrRenderRequest(val format: String, val sizeClass: String,
  val errorLevel: String = "M", val dpi: Int? = 300)
data class QrRenderView(val renderId: String, val shortCode: String, val format: String,
  val sizeClass: String, val errorLevel: String, val dpi: Int?, val createdAt: Instant,
  val downloadUrl: String, val labelUrl: String)
data class QrRenderPage(val items: List<QrRenderView>, val nextCursor: String?)
data class QrPackRequest(val count: Int, val prefix: String? = null, val sizeClass: String,
  val errorLevel: String = "M", val labels: List<String>)
data class QrPackResponse(val packRef: String, val renders: List<QrRenderView>, val sheetUrl: String)
```

## Rules

- Payload is always the canonical short URL (`https://<host>/go/<code>`); no request field can influence it; the property is asserted by a decode test on every export (AC-01).
- Floors (server-authoritative): error level M minimum — requests below the class requirement (`H` for `s`/`poster`) are refused 422 `qr_below_floor`; quiet zone 4 modules, module ≥ 0.4 mm, and contrast ≥ 7:1 are baked/asserted at render (no palette parameter exists in v1; the contrast assertion is renderer-internal and CI-tested against reference images).
- Parent precondition: non-live parent → 409 `qr_parent_not_live` with the state named; nothing is rendered (BA BR-04).
- Download: `downloadUrl`/`labelUrl` carry 15-min owner-bound grants; the label (short code + destination preview, derived from the LIVE link at download time) ships alongside for filing-by-label.
- Re-render is just another render: `POST .../qr` again; the history list serves staleness logic (`updated_at` vs `created_at` compared client-side — no new endpoint).
- Sheet: assembled server-side (grid ≤ 24 per page + label rows), returned as SVG for browser print; no PDF exists anywhere (guardrail).
- Packs: ops-only; `count` ≤ 100, `labels.size == count`; codes minted through feature 29's validation path (prefix honored, collision-checked); all-or-nothing transaction; audit detail carries operator, purpose, `packRef`, code list; creator token → 403 + audit.
- Quotas: 60 renders/h per user; 10 packs/day per operator; 429 past cap.
- Preview: no official client-side generation; the browser preview is watermarked `PREVIEW — not for print` by overlay.

## Forward seams recorded

| Seam | Owner | Fixed contract |
|---|---|---|
| scan-split input | feature 31 | event-pack codes give exact per-location splits; organic scans are 31's labeled heuristic |
| manager handoff | feature 29 | Stats/QR links target this module + 31 (T-19) |
| share QR | feature 10 | stays a client-side utility encoding the canonical profile URL — never this generator, never a short link |
| brand styling | future work (Q1) | any styling must pass the same contrast/size floors; the floors ship now |

## Cross-edits in this batch

| File | Change |
|---|---|
| `docs/02-architecture-techlead/00-platform-baseline/01-architecture-and-service-topology.md` | link-service row narrows to 29-30; analytics-service row gains 31 (pilot boundary map authoritative) |
| `MIGRATION.md` | service rows mirrored; rows 30/31 `done`; batch 8 complete |
| `docs/02-architecture-techlead/12-profile-analytics/` | Redis row gains `links.hits`; V3 inventory note |
| `docs/02-architecture-techlead/29-short-links/02-database-schema-design.md` | raw-archive row clarified (written at resolve; repaired by 31) |
| `docs/02-architecture-techlead/02-account-deletion/` | link-service purge row gains QR rows + `links/qr/` files |

## Governance

- Codes appended to the link catalog; no events emitted by this feature (links' `link.suspended.v1` covers purge propagation).
- Contract tests: decode matrix, floor refusals, pack atomicity, grant expiry, preview watermark, encoder grep gate (server module + feature-10 allowance).
