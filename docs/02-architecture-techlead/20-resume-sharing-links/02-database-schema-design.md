# Resume Sharing Links - Data and Cache Design (feature 20)

Phase 2 TL · **Zero migrations in this feature's scope**: the only schema change is the additive link-service extension (`V2__resume_refs.sql`, defined in feature 29's schema file in this change). BA sources: `docs/01-requirements-ba/20-resume-sharing-links/` (BR-04, BR-07, BR-08).

## No-DDL assertion (this feature)

- This directory defines no `CREATE TABLE`; the sharing orchestration stores nothing. Consumed rows only.
- No share-URL table or column exists in any schema (BR-08): a stored URL would go stale on rename/republish; derivation cannot. Review-grepped across all schema files.

## Consumed rows per type

| Type | Reads |
|---|---|
| Standard + SEO (`/:username/cv`, one URL) | `resume.documents(is_public, published_version)` + version row (feature 17); profile state via render path (feature 10) |
| Shortened | `links.short_links` row tagged `resume_ref_user_id/resume_ref_document_id` (feature 29 extension); analytics rows owned by 29-31 |
| Custom | `profile_db custom_domains{hostname, status=verified}` via the by-user read (feature 04 row landed in this change); serving + canonical per feature 04 |
| Bio embed | `profile_blocks(type=resume, payload.documentId)` visibility via the by-document read (feature 08/17 row) |

## Link extension (landed in feature 29's schema file)

`V2__resume_refs.sql`: nullable `resume_ref_user_id`, `resume_ref_document_id` columns + partial unique index on the pair; resume shares keep `kind='random'`. Resume-share mints are quota-exempt (bounded to one row per document by the unique). Idempotent mint route `POST /internal/v1/links/resume-share`; rename consumer rewrites `destination` and purges `links:code:{code}`.

## Cache-key composition (owned by feature 10; consumed here)

`ssr:{host}:{slug}:cv` - host dimension covers custom hosts; slug+host cover username changes; revision changes purge via events (matrix in TL 01). Shortener resolve cache `links:code:{code}` 300 s owns its own invalidation (purged on suspend + destination rewrite).

## Uniform revocation mapping (single resolver -> five renderings)

| Flip | Standard/SEO | Shortened | Custom | Bio embed |
|---|---|---|---|---|
| `is_public=false` or `published_version=NULL` | parity `404` | slug resolves -> safe-error (destination target 404s; shortener status untouched) | serving continues host-wise; cv path `404` | section hidden, page intact |
| Profile private/hidden | parity `404` | same safe-error behavior | host serving follows profile rules (feature 04) | page itself hides per profile rules |
| Re-publish | restores | resolves again (same slug) | restores | section restores |

One flip is the only control: per-type kill switches do not exist (five switches would desync; the matrix test proves coverage).

## Support matrix (the answer sheet)

| Type | URL | Snapshot | On unpublish | On rename |
|---|---|---|---|---|
| Standard | `https://{host}/{username}/cv` | published version | `404` | re-derives; old URL `301`s |
| SEO | identical URL (tags verified) | same | `404` | same as standard |
| Shortened | short URL | same | safe-error path (never stale) | destination re-derived, slug stable; 301 backstop |
| Custom | `https://{host}/cv` (verified host) | same | `404` on the host path | re-derives via host serving |
| Bio embed | `https://{host}/{username}#resume` | same | section hidden | anchor re-derives |

## Verification drills

1. Derivation drill: grep proves zero share-URL tables/columns; renaming and republishing change helper output with zero row writes.
2. Mint drill: second `resume-share` call returns the same code (`created:false`); partial unique holds under racing double-requests; no quota counter increments.
3. Rename drill: rewrite lands on the event; with the event suppressed the old-username 301 resolves - never a dead link.
4. Revocation drill: one flip turns all five within `<= 60 s`; re-publish restores; matrix table above matches live behavior.
5. Cache drill: per-host keys purge on their events; miss-storm single-flight holds on cv paths (feature 10 mechanism, re-verified).
6. RLS drill: helper scopes by session `user_id`; cross-user calls `403`; direct PG as app role denied.
