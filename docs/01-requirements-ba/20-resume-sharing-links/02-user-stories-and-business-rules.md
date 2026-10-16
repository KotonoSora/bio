# Resume Sharing Links - User Stories and Business Rules (feature 20)

Phase 1 BA · Enforced by `docs/02-architecture-techlead/20-resume-sharing-links/`. Scope: `docs/01-requirements-ba/20-resume-sharing-links/01-document-context-and-scope.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Job seeker | copy any link type for applications, prints, or QR codes and revoke by unpublishing | one snapshot shares everywhere and revocation is one action | High |
| US-02 | Recruiter / viewer / crawler | open any type with no login and read the same snapshot text | review works without JS and without surprises | High |
| US-03 | Support | answer "which link shows what version" from one matrix | sharing questions resolve consistently | Medium |

## Business rules

- **BR-01 Source rule.** Every type renders the document's `published_version` snapshot row; a snapshot change re-renders all five identically (text parity); layout follows `template_id`; draft edits stay invisible everywhere until Save Snapshot.
- **BR-02 Standard link.** `/:username/cv` SSR with the feature 10 cache envelope; private, unlinked, or deleted documents -> parity `404`; a username change `301`s old -> new per profile rules.
- **BR-03 SEO link.** The same `/:username/cv` URL with the full tag set (title/meta/canonical/OG/Twitter + JSON-LD `Person`, feature 10); no separate SEO URL exists; the dialog re-verifies the tag checklist live on each open (an SEO row that merely assumes tags is how tag regressions hide); canonical rewrites per serving host.
- **BR-04 Shortened link.** Mint destination = the canonical cv URL (never a second copy of content); idempotent per `(owner, document)` - the second request returns the same slug (`created:false`), never a mint loop; slugs carry no PII; the dialog discloses the permanent mint once on first copy; expiry default persistent (Q-02); suspended/expired slugs show the shortener's branded safe-error, never stale resume content; QR codes target the shortened URL.
- **BR-05 Custom link.** Verified hosts only (feature 04 machinery; unverified hosts never render any resume content); URL shape fixed `https://{host}/cv` (Q-03); canonical = custom host; downgrade/unlink detaches serving with DNS untouched and the standard link stays live.
- **BR-06 Link-to-Bio link.** The `resume` block embeds the resume section on `/:username`; the dialog row reflects linkage live (visible / hidden / not linked); hiding the block or unpublishing the document removes the section without breaking the page; toggle binds/unbinds through the block linkage routes (feature 17/08).
- **BR-07 Uniform revocation.** `is_public=false` OR `published_version=NULL` OR profile private -> all five types flip within the purge SLA (`<= 60 s`): standard/SEO/custom/bio `404` or hidden; shortened safe-error. Re-publishing restores all types. Renames keep shortened links working (destination re-derived; the old-username `301` is the backstop if the rewrite event is lost). No per-type revocation switches exist.
- **BR-08 Derivation, not storage.** Share URLs are never persisted: no share-URL table or column exists in any schema (review-grepped); a stored URL would go stale on rename/republish - derivation cannot.
- **BR-09 Unlimited sharing.** No quotas, credits, or plan gates on views, mints for one's own resumes, or browser PDF exports; anonymous-path abuse controls (Turnstile, WAF, rate rules) live in the owning majors and never touch legitimate unlimited use.
- **BR-10 In-browser A4 PDF view.** The dashboard A4 view binds the frozen snapshot at open (re-open notice on save mid-view, feature 18 pattern) and prints via `window.print()` with the print CSS; no server-side PDF engine, no stored PDF bytes, no client PDF library (grep gates shared with feature 18); draft edits never leak into the frame.

## Constraints carried

- The two flags are the revocation mechanism; any per-type flag would desync five systems - the matrix test proves one flip covers all.
- Cross-service reads only via internal APIs (profile by-user, domains by-user, blocks by-document, link mint); no cross-database access.
- Secrets via environment only (the legacy wrangler-era secret stores are superseded by the containerized baseline); `.env` and compose overrides stay uncommitted.
- No pricing or plan values appear here; nothing in this feature is gated.
