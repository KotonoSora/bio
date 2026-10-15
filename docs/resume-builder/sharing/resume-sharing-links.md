# Resume Sharing Links (five link types, one snapshot)

PLAN ref: P2-1 (documents/snapshot source) + P1c-1/P1c-2 (SSR tags + cache-first + purge). Link mechanics owned elsewhere: SEO in `docs/link-to-bio/public-page/share-seo.md`, shortened in `docs/url-shortener/links/short-links.md`, custom hosts in `docs/account/domains/custom-domains.md`, bio embedding via the `resume` block (`documents/resume-snapshot.md` + `docs/link-to-bio/blocks/link-blocks.md`). This doc binds the five into one resume-sharing contract — no new link system is introduced here.

## Purpose

Let one frozen resume snapshot be shared through every link type a job seeker needs — standard, SEO-optimized, shortened, custom-domain, and Link-to-Bio — with identical text everywhere and privacy honored on every path. Each type reuses its owning major's machinery; this doc fixes the resume-specific rules (which URL, which snapshot, what happens on unpublish) so the five never drift apart.

## Users

- Job seeker: copies any link type for applications, prints, or QR codes; revokes by unpublishing.
- Recruiter/viewer/crawler: opens any type with no login and reads the same snapshot text (crawler sees tags without JS).
- Support: answers "which link shows what version" from one matrix.

## Functional requirements

1. Source rule: every link type renders the document's selected public snapshot (`snapshot_json`), never draft edits. Snapshot change re-renders all five types identically (text parity; layout follows the chosen `template_id` per `templates-print/resume-templates.md`).
2. Standard link: `/:username/cv` (Worker SSR, same caching as `/:username`: `public, s-maxage=60-300, SWR=600`, purge on snapshot/template/username change). Private, unlinked, or deleted documents → 404; username change → 301 old→new per profile rules.
3. SEO-optimized link: the same `/:username/cv` URL with full crawler tags (title/meta/canonical/OG/Twitter + JSON-LD `Person`, per `docs/link-to-bio/public-page/share-seo.md`), canonical rewritten per serving host. No separate SEO URL exists — "SEO link" is the standard link with its tags verified.
4. Shortened link: mint via the url-shortener service with destination = the canonical cv URL (never a second copy of the content). Slugs carry no PII; expiry per shortener policy; disabled/expired slugs → the shortener's safe-error page, never a stale resume. QR targets use the shortened URL.
5. Custom link: verified custom host serving the same cv SSR (`docs/account/domains/custom-domains.md` — `_cf-challenge TXT`, verified-only serving, canonical = custom host). Unverified hosts never render any resume. Downgrade/unlink detaches serving with DNS untouched; the standard link stays live.
6. Link-to-Bio link: the `resume` block (`payload_json.document_id`) embeds the resume section on the public `/:username` page alongside other blocks. Hiding the block or unpublishing the document removes it without breaking the page.
7. Visibility and revocation (uniform): all types require `is_public=1` + a selected public snapshot; flipping either turns every type to 404/safe-error within the purge SLA. Sharing is unlimited — no quotas, credits, or plan gates on views, mints for own resumes, or browser PDF exports.
8. In-browser A4 PDF view: dashboard preview renders the frozen snapshot in an A4 frame with the same `@media print` CSS used for export, verified in Chrome/Safari/Firefox/Edge print preview. Guardrail holds: `window.print()` only — no server-side PDF engine, no Puppeteer/LibreOffice, no stored PDF bytes in v1.

## Data / API touches

- No new tables. Reads `documents{snapshot_json, template_id}` + `profiles{username, is_public}` + `profile_blocks(type=resume)`; short-link rows and `custom_domains` rows owned by their majors. All PG via `worker/db/*`; dashboard scopes by session `user_id`, public scopes by `username + is_public=1`, standard `user_id TEXT NOT NULL` + index + RLS throughout.
- Endpoints (owned elsewhere, consumed here): share-URL derivation (no dedicated share-create API — URLs are derived from username/host/slug + selection), shortener mint/stats, domain bind/verify, block link-document. New read-only helper `GET /api/v1/documents/:id/share-links` returns the five derived URLs for dashboard copy buttons (owner-scoped).
- Cache: cv HTML keyed per host+snapshot revision; publish/snapshot/username/domain changes purge all affected keys (KV single-flight on miss storms preserved).

## Acceptance criteria

- Parity drill: same snapshot text on all five types (curl, no JS); template switch changes layout only; draft edits invisible until Save Snapshot.
- Privacy drill: private/unlinked document → 404 on standard/SEO/custom/bio and safe-error on shortened; re-publish restores within purge SLA; username change 301s.
- Short-link drill: mint → resolves to canonical cv URL; expiry/disabled → safe error, never stale content; slug contains no personal data.
- IDOR probe (S-1/S-6): user A share-link reads/writes against user B documents → 403; direct PG as app role denied by RLS.
- XSS/CSP drill (S-2): script/iframe/event-handler in any resume field stripped on all five render paths; CSP holds.
- Abuse drill (S-5): anonymous bursts on cv/short/custom paths throttled (Turnstile + WAF + rate rules); unlimited legitimate views/exports unaffected.
- Secrets drill: no secrets in repo/logs/bundles; `wrangler secret` only; `.gitignore` guards `.wrangler/`, `.dev.vars*`. (Avatar pipeline N/A here — owned by P1b-4.)

## Out of scope

- Snapshot versioning and block linkage (see `documents/resume-snapshot.md`); template visuals and print CSS internals (see `templates-print/resume-templates.md`); JSON backup/adapters (see `import-export/resume-portability.md`).
- Shortener namespace/analytics/abuse internals, domain verification mechanics, bio-block rendering (owning majors — consumed, never redefined).
- Server-side PDF bytes, stored PDF files, per-user CSS/JS (never — guardrail); plan gates and AI tailoring (P3); publish/automations/campaigns/agency (P4); replicas/ISR (P5).

## Open questions

- Q1: One public snapshot per profile in v1, or a picker (consumes snapshot-doc Q1 — decided once there)?
- Q2: Short-link/QR expiry defaults for resumes — persistent application links or expiring campaign-style links?
- Q3: Custom-host cv path (`creator.com/cv` vs root-mapped resume) — fixed path or per-profile setting?
