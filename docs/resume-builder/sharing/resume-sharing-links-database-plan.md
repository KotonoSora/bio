# Resume Sharing Database Plan (five types, one snapshot — no new tables)

PLAN ref: P2-1 sharing contract (source rule, uniform revocation, unlimited ungated views) + S-1/S-6. BA source: `docs/resume-builder/sharing/resume-sharing-links.md`. Consumed by the sharing backend plan (binding matrix + purge fan-out; splits next to this file). Snapshot rows live in `docs/resume-builder/documents/resume-snapshot-database-plan.md`; profile/block rows in the link-to-bio database plans; short-link rows in `docs/url-shortener/links/short-links-database-plan.md`; custom-host rows in `docs/account/domains/custom-domains.md` (account major, execution triple pending). No migration — this slice creates zero tables.

## Purpose

Define the sharing data contract without owning storage: which rows each of the five link types reads, why share URLs are derived (never stored — a share-URL table would drift from the snapshot it claims to show), the per-host cache-key composition, and the purge fan-out matrix. No `CREATE TABLE` here by design — every persistent fact already has exactly one home elsewhere.

## Users

- Backend developer: implements derivation + purge fan-out reading only the rows below.
- QA: proves text parity across all five types and uniform revocation from a single flip.
- Support: answers "which link shows what version" from the matrix in rule 6.

## Functional requirements

1. Source rule (structural): every type renders the document's `published_version` snapshot row (`resume.snapshots(document_id, version)`), never draft state (drafts live only in editor memory + unsaved form state — no row exists to leak). Snapshot change re-renders all five types identically; layout follows `documents.template_id` (templates plans).
2. Consumed rows per type: standard + SEO (`/:username/cv` — same URL, tags verified): `resume.documents(is_public, published_version)` + version row + `bio.profiles(username, is_public)`; shortened: `links.short_links` row minted with destination = canonical cv URL (never a content copy) + analytics rows owned by the url-shortener plans; custom: `custom_domains{hostname, profile_id, verified}` (account BA — verified-only serving, canonical rewritten to custom host); bio-embed: `profile_blocks(type=resume, payload.document_id)` visibility gating the resume section on `/:username`.
3. Derivation, not storage: share URLs are pure functions of (username | host | slug + selection) — computed at copy time by the helper endpoint, never persisted. A stored share URL would go stale on rename/republish; derivation cannot. Assertion: no share-URL column or table exists in any schema for resumes (review greps it).
4. Cache-key composition: cv HTML keyed per (host + snapshot revision + template revision + username) — host dimension covers custom hosts; revision dimensions cover snapshot/template/username changes. Publish/snapshot/username/domain events purge all affected keys (fan-out matrix owned by the backend plan); miss storms coalesce on the existing single-flight discipline (share-seo pattern, reused not redefined).
5. Uniform revocation: `is_public=false` OR `published_version=NULL` (unpublish) OR profile private → all five types flip within the purge SLA (standard/SEO/custom/bio → 404; shortened → the shortener's safe-error/expiry path, never stale content). Switching the two flags is the ONLY revocation mechanism — per-type kill switches do not exist (five switches would desync; the matrix test proves one flip covers all).
6. Support matrix (one row per type: which URL, which snapshot, what on unpublish, what on rename): standard `/:username/cv` → published version → 404 → 301 old→new; SEO: identical (tags verified, no separate URL); shortened: short URL → same text → safe-error → follows rename (destination re-derived, never re-minted); custom: `host/cv` (path per account Q3 — carried open) → same text → detached serving, DNS untouched, standard link stays live; bio-embed: `/:username#resume` section → same text → section hidden, page intact.
7. QR targets use the shortened URL (never raw cv URL — expiry/revocation propagate through the shortener's lifecycle, which a raw-URL QR would bypass).

## Data / API touches

- DDL: none — assertion: `grep -rn "CREATE TABLE" docs/resume-builder/sharing/` returns nothing (directory holds only this triple + BA). Reads: documents/snapshots + profiles/blocks + short-link/custom rows (their plans own the DDL). Derived artifacts: five URLs (computed, never stored); cache keys per rule 4 (cache layer, not PG).

## Acceptance criteria

- Parity drill: same snapshot text on all five types (curl, no JS); template switch changes layout only; drafts invisible everywhere until Save Snapshot.
- Revocation drill: single flip (unpublish or private) → 404/safe-error/hidden on all five within purge SLA; re-publish restores; rename 301s.
- Derivation drill: zero share-URL rows/columns anywhere (schema + grep proof); rename without re-mint keeps shortened links live.
- Cache drill: per-host keys purge on their events; miss-storm coalescing holds on cv paths.
- RLS drill: owner reads via `user_id`; public via `username + is_public`; cross-user share-helper calls 403.

## Out of scope

- Versioning and publish pointers (snapshot database plan); SSR/tag/cache mechanics (templates + share-seo backend plans); shortener/QR/stats internals (url-shortener plans); domain verification mechanics (account major); block rendering (link-blocks plans); server-side PDF bytes (never — guardrail); plan gates/AI (P3); publishing/agency (P4); replicas/ISR (P5).

## Open questions

- Q1: Single public snapshot (decided single across snapshot plans — picker stays future)?
- Q2: Resume short-link expiry default — persistent (decided here: `expires_at NULL`, application links outlive campaigns; campaign-style use sets expiry per mint) — hold?
- Q3: Custom-host cv path (`creator.com/cv` vs root-mapped) — fixed path or per-profile setting (needs account-major input)?
