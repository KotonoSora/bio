# Resume Templates + Print

PLAN ref: P2-1 (screen + print). Data model lives in `documents/resume-snapshot.md`.

## Purpose

Define how resumes look on screen and on paper from a single frozen snapshot. Screen preview is interactive dashboard UX; print output is a strict A4 rendering used for PDF-via-print and recruiter review. Edit state and print markup stay separate so styling work can never corrupt saved data.

## Users

- Job seeker: previews templates on screen, prints / saves PDF for applications.
- Recruiter / viewer: reads the public `/:username/cv` SSR page or a printed PDF.
- Template contributor (internal): adds a token-based template without touching user data code.

## Functional requirements

1. Template gallery in dashboard (`/app/*`): thumbnail, name, `template_id`; switching `template_id` re-renders the same snapshot, never mutates it.
2. Templates live in `src/templates/` and consume only design tokens (font, scale, accent). No per-user CSS/JS upload; no inline `style` from user fields.
3. Screen markup is interactive (section collapse, zoom, density toggle). Print markup is a separate read-only tree derived from the snapshot at print time.
4. Print CSS: `@media print` A4 pages, `print-color-adjust: exact`, no reliance on dashboard nav/chrome. Page breaks avoid splitting experience entries where feasible.
5. Export action is `window.print()` only (user picks Save-as-PDF). No server-side PDF engine; no Puppeteer, `fs`, or LibreOffice in `worker/`.
6. Public `/:username/cv` SSR reuses the same template + snapshot on the server so curl-visible HTML matches the printed content (text parity, not pixel parity).
7. Pro gating (if applied): animated/premium templates gated server-side by plan check, not UI-only hiding.

## Data / API touches

- No new tables. Reads `documents.snapshot_json + template_id`; writes only `template_id` selection via existing `PUT /api/v1/documents/:id`.
- Client assets: `src/templates/<template_id>/` (screen component + print stylesheet + thumbnail). Worker SSR imports the same template renderer in a Workers-safe way (no Node-only libs).
- Cache: public `/:username/cv` HTML follows the shared cache-first rule (`public, s-maxage=60-300, stale-while-revalidate=600`; purge on publish/snapshot change). Dashboard preview is uncached SPA state.

## Acceptance criteria

- Same snapshot across all templates: text content identical, only layout changes.
- `@media print` on A4: single-column flow, no dashboard chrome, colors preserved with `print-color-adjust: exact`; tested in Chrome + Safari print preview.
- `window.print()` from dashboard produces the frozen snapshot, not live draft edits, unless user explicitly saved first.
- Cold `curl` on `/:username/cv` (no JS) contains resume text + per-profile title/meta/canonical/OG/Twitter + JSON-LD `Person`.
- Custom CSS/JS injection attempt via any resume field: stripped or neutralized; page CSP `default-src 'self'` holds.
- No `worker/` import of Node-only or headless-browser packages (grep gate).

## Out of scope

- Snapshot versioning and block linkage (see `documents/resume-snapshot.md`).
- JSON backup/restore and third-party adapters (see `import-export/resume-portability.md`).
- Avatar upload pipeline, themes for link-in-bio pages, shop/course/tip rendering (other majors).
- AI JD-tailor and plan gates (P3); publishing/automation/campaigns/agency (P4); ISR pre-render of top profiles and replicas (P5).

## Open questions

- Q1: v1 template count — 3 classic layouts or single layout + density options?
- Q2: Is cover-letter print a second page of the same template or a separate `template_id`?
