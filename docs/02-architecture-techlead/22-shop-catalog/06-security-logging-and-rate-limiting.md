# Shop Catalog - Security, Logging, and Rate Limiting (feature 22)

Phase 2 TL · BA sources: `docs/01-requirements-ba/22-shop-catalog/` (BR-12..BR-15, NFR-05..NFR-13). Baseline: `docs/02-architecture-techlead/00-platform-baseline/08-security-baseline.md`; gates: `docs/02-architecture-techlead/00-platform-baseline/06-release-gates.md`.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | IDOR + RLS: owner scope by server-set `user_id`; no route accepts a foreign `profile_id` for writes; foreign ids → 403 + RLS denial | AC-05; API + direct-PG probes |
| S-2 | XSS: sanitize on write (title/description/payload strings), escape on render (shop + blocks); CSP at the edge; no raw payload echo | AC-06 |
| S-3 | Upload safety: 2 MB bounded read; magic-byte sniff (JPEG/PNG/WebP/AVIF); random names; served as static files only by the Nginx edge; no SVG | AC-07 |
| S-4 | Money integrity: zero fee values in this feature; creator-set VND integer prices; checkout snapshots price at order time (seam); ledger binding 32-40; client-supplied prices ignored everywhere | AC-03 seam tests; fee-literal grep |
| S-5 | Abuse: anonymous shop page is edge-cached SSR; beacon throttled (feature 12); owner CRUD 120/h; publish/archive 60/h; uploads 30/h | AC-11; burst drill |
| S-6 | Audit: create/transition/price/cover/delete rows append-only, 24-month retention; price history separate append-only table | AC-12 |
| S-7 | Purge: participant counts rows + files; zero cross-user deletes; cover cleanup on delete/replace | AC-09 |
| S-8 | Scope guard: no build dependency on money services (HTTP seams only); grep gate: zero fee percentages/literals in this tree | Money-copy grep + dependency review |
| S-9 | Secrets: env/vault only; no `.env`; storage access confined to shop-service (volume mount only) | Secrets audit |

## Rate envelopes

| Route class | Envelope | On breach |
|---|---|---|
| Owner CRUD | 120/h/user | 429 + `Retry-After` |
| Publish/archive/store transitions | 60/h/user | 429 |
| Cover uploads | 30/h/user | 429 |
| Shop page SSR | Nginx edge + 60 s cache; origin render single-flight per slug | stale/503 path |
| `products:public` + `by-profile` | service-level budget | 429 + trace |

## Degrade matrix (fail safe, never fail open)

| Condition | Behavior |
|---|---|
| `shop_db` down | Owner CRUD 503; SSR serves stale within SWR; blocks render unavailable state on miss |
| public-site cache down | Origin render; shop page stays correct (cache is acceleration only) |
| shop-service unreachable from public-site | stale within SWR or 503 branded page (`origin_unavailable`); never partial shop HTML |
| analytics down | Link-out clicks dropped silently (fire-and-forget); pages unaffected |
| `has-product-orders` seam timeout | Delete refused `503 orders_check_unavailable`; nothing deleted |
| Cover storage volume down | Upload 503; existing covers unaffected |
| Event bus down | Purge heals via TTL ≤ 60 s; no shop outage |

## Threat checklist

IDOR (owner scope + RLS), XSS (write sanitize + render escape + CSP), upload abuse (bounded read + sniff + random keys), price manipulation (server-side re-read at the checkout seam; client price ignored), link-out scheme abuse (`https:` allowlist + disclosure, no redirect parameter), enumeration (public reads by profile id only; drafts never renderable), purge completeness, secrets (env only), fee-literal creep (grep gate).

## Gates

- G4: OpenAPI diff additive; codes registered; route precedence vs reserved slugs.
- G5: S-1..S-3 probes + upload matrix + CSP header check.
- G7: AC-01..AC-12 drill set attaches to the tag; container drill (Docker + Podman) per baseline release gates.
- Greps: zero fee percentages in this directory; no client-chosen file paths; secrets clean.
