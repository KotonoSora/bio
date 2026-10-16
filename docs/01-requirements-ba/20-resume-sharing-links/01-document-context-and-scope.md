# Resume Sharing Links - Context and Scope (feature 20)

Phase 1 BA · Feature 20 of 53 (resume) · Implemented by `docs/02-architecture-techlead/20-resume-sharing-links/` (5 files); orchestration in `resume-service` (helper endpoints); the five link types render through their owning paths. No new tables, no new services.

## Purpose

Let one frozen resume snapshot be shared through every link type a job seeker needs - standard, SEO-optimized, shortened, custom-domain, and Link-to-Bio - with identical text everywhere and privacy honored on every path. Each type reuses its owning major's machinery; this feature fixes the resume-specific rules (which URL, which snapshot, what happens on unpublish, what follows a rename) so the five never drift apart.

## Objectives

- O-1 Source rule: all five types render the document's selected public snapshot; draft edits are unreachable on every path (no draft row exists to leak).
- O-2 Uniform revocation: the two-flag flip (`is_public`, `published_version`) plus profile privacy is the only revocation mechanism; one flip degrades all five types correctly within the purge SLA - no per-type kill switches exist.
- O-3 Derivation, not storage: share URLs are pure functions of (username | host | slug + selection), computed on read; no share-URL table or column exists anywhere, ever.
- O-4 Unlimited sharing: no quotas, credits, or plan gates on views, mints for one's own resumes, or browser PDF exports; abuse controls live only on anonymous paths via owning majors.
- O-5 Honest states: the share dialog never shows a URL that 404s - unpublished documents show the publish entry instead of dead rows; shortened links show the shortener's branded safe-error, never stale content.

## Personas and RBAC

| Role | CRUD Permissions | Description |
|---|---|---|
| Job seeker | Read: helper derivation; Create: one short link per own document (idempotent); Update: bio-embed toggle | Copies any link type for applications, prints, or QR codes; revokes by unpublishing. |
| Recruiter / viewer / crawler | Read (anonymous): any type, no login | Opens any type and reads the same snapshot text; crawler sees tags without JS. |
| Support | Read: the support matrix | Answers "which link shows what version" from one table. |
| Attacker (adversarial) | none | Finds no IDOR path, no XSS vector, no bypass of revocation. |

## Resolved open questions

| Q | Decision |
|---|---|
| Q-01 Multiple public resumes | Decided in feature 17: single public snapshot per profile; a picker requires a flagged amendment - carried here, not re-decided. |
| Q-02 Short-link expiry default | Persistent (`expires_at NULL`) for resume shares - application links must outlive campaigns; per-mint expiry remains available for campaign-style use. |
| Q-03 Custom-host cv path | Fixed `https://{host}/cv` in v1, consistent with the standard path; root-mapped resume serving is a flagged amendment (feature 04 pins the host; this feature pins the path). |
| Q-04 QR in the dialog | Static thumbnail that jumps to the shortener's QR flow (pixels are rendered and owned there; no second QR renderer). |
| Q-05 Copy-count analytics | Deferred: no tracking of which type gets copied in v1 (privacy posture beats a vanity metric; an amendment may revisit). |

## In scope

- The read-only share-links helper (derived five URLs) + the idempotent shortened-link mint endpoint (owner-scoped, in `resume-service`).
- Binding rules for the five types: standard `/:username/cv`; SEO = same URL with verified tags; shortened via link-service; custom via verified hosts; bio embed via the `resume` block.
- Uniform revocation mapping, the purge fan-out ownership matrix, the support matrix, and the share-dialog/A4-view/bio-toggle frontend contracts.
- Additive rows landed in this change: link-service resume-ref extension (`V2__resume_refs.sql`, internal mint route, rename consumer) and feature 04's by-user domains read.

## Out of scope

- Owning internals: SSR/tags/cache (feature 10), shortener resolve/QR/stats (29-31), domain verification (04), block rendering (08), snapshot versioning (17), template visuals and print CSS (18).
- Server-side PDF bytes, stored PDF files, per-user CSS/JS (never - guardrail); plan gates and AI tailoring (gated 16); publishing/automation/agency (41-47).

Related: rules in `docs/01-requirements-ba/20-resume-sharing-links/02-user-stories-and-business-rules.md`; contracts in `docs/02-architecture-techlead/20-resume-sharing-links/03-api-contracts-and-payloads.md`; error surface in `docs/01-requirements-ba/20-resume-sharing-links/05-exception-handling-and-errors.md`.
