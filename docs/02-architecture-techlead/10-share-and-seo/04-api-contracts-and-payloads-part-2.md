# Share and SEO - API Contracts and Payloads (part 2: internal consumption and governance)

Phase 2 TL · Part 1 (`03-api-contracts-and-payloads-part-1.md`): public surfaces. This part: what the public-site service consumes, the additive owner-mode extension, events, and governance.

## Internal consumption map (service JWT; shapes owned by the source features)

| Call | Owner | Purpose | Cache |
|---|---|---|---|
| `GET /internal/v1/profiles/{username}` | feature 06 | resolve: `profile` / `redirect` / `missing`; private = `missing` parity | `ssr:*` key |
| `GET /internal/v1/profiles:public?cursor=&limit=` | feature 06 | sitemap page source (public only) | `sitemap:xml` 1 h |
| `GET /internal/v1/profiles/{username}/blocks` | feature 08 | windowed render-ready blocks | `ssr:*` key |
| `GET /internal/v1/themes` | feature 09 | theme catalog + `registryVersion` | `themes:catalog` 300 s |
| `GET /internal/v1/shops/by-profile/{profile_id}/products` | feature 22 | live catalog for the shop page (sorted) | `ssr:*:shop` key |
| `GET /internal/v1/products:public?ids=` | feature 22 | bio product-block availability (<= 50 ids; omissions render the unavailable state) | `ssr:*` key |
| `GET /internal/v1/certificates/{code}` | feature 24 | verifier payload (name, course, date; 404 unknown/revoked) | `ssr:cert:{code}` |
| `GET /internal/v1/courses/by-product/{productId}/syllabus` | feature 24 | preview syllabus for course product pages (no gated media) | `ssr:*:shop:{productId}` key |
| `GET /internal/v1/tips/{productId}/supporters` | feature 25 | approved-only wall rows for tip product pages | `ssr:*:shop:{productId}` key |
| `GET /internal/v1/resumes/{username}/published` | feature 17 (consumed) | `/cv` snapshot payload; `404 resume_absent` parity when none/unpublished | `ssr:*:cv` key |

Invariants: <= 3 calls per render (resolve -> [redirect] -> blocks; catalog cached); 2 s per-call timeout; 5 s render deadline; sequential only when dependent; all DTO fields enumerated (no wildcard projections).

## Additive owner-mode extension (governance: additive-only)

Preview needs owner-scoped reads of non-public state. This feature adds one query parameter:

| Extension | Owner | Semantics |
|---|---|---|
| `GET /internal/v1/profiles/{username}?mode=owner` | feature 06 | honored only when the service JWT `sub` equals the profile owner; `is_public` is ignored; suspended/retired still return `missing`; otherwise byte-identical to public mode |
| `GET /internal/v1/profiles/{username}/blocks?mode=owner` | feature 08 | same ownership check; returns the same windowed projection (hidden blocks stay hidden - preview shows exactly what the public page will show after purge) |

The owning services validate the parameter; public-site-service forwards the JWT and never passes owner identity in query strings. When the owning features next publish their contracts, this extension is recorded there; until then this file is the reference and T-10 contract-tests it.

## Events

| Event | Source | Effect |
|---|---|---|
| `profile.published.v1` (`username, updatedAt`) | features 06-09 (all mutations) | delete `ssr:{host}:{slug}`, `ssr:{host}:{slug}:cv`; delete `sitemap:xml` on visibility changes |
| `username.renamed.v1` | feature 06 | delete slug keys for old and new usernames |
| `shop.catalog_changed.v1` (`username, at`) | feature 22 | delete `ssr:{host}:{slug}:shop` and `ssr:{host}:{slug}` (block availability) |
| `shop.tip_wall_changed.v1` (`username, productId, at`) | feature 25 | delete `ssr:{host}:{slug}:shop:{productId}` (wall freshness) |

The public-site service publishes nothing (no events, no writes). Snapshot-save purge hooks are added when features 17-21 define their event; the listener has one extension point.

## Failure mapping (external surface stays minimal)

| Internal outcome | With stale available | Without stale |
|---|---|---|
| resolve/blocks timeout or 5xx | serve stale within the SWR window | `503` branded page (`origin_timeout` / `origin_unavailable` logged only) |
| render deadline exceeded | same | `503` (`render_timeout` logged) |
| `missing` result from any read | `404` parity (never `503` for a missing profile) | - |

Only `200`, `301`, `404`, `503` (+`401` on preview without session) ever reach a public client; internal codes never leak (no oracle).

## Governance

- OpenAPI additive-only; route-precedence tests cover `/api|/app|/go|/assets|single-segment` collisions against the reserved-slug list owned by feature 06.
- Fragment tests pin the head/blocks/seo-tags templates; the `<!-- ssr -->` marker fails builds if a public 200 ships without tags.
- Contract tests: resolve result enum, owner-mode denial, block window parity, catalog fallback, resume absence (`404` parity).
