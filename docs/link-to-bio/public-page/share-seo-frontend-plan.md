# Share SEO Frontend Plan (preview + publish feedback — UI build steps)

PLAN ref: P1c-2 publish→visible contract (dashboard side). BA source: `docs/link-to-bio/public-page/share-seo.md`. Server truth in `docs/link-to-bio/public-page/share-seo-backend-plan.md` (routes, headers, purge — never redefined here). No dashboard form owns the public page — this file owns preview, publish feedback, and the SEO checklist.

## Purpose

Give the frontend developer an ordered UI procedure for the publish side of SEO: preview iframe, purge-pending feedback, and the read-only tag checklist. Follow steps 1–5 in order. The frontend never purges cache itself — it only reflects backend purge state honestly.

## Users

- Frontend developer: builds preview/publish/checklist UI against the backend plan's contracts.
- QA: executes the publish-visibility and checklist drills.
- Creator: publishes and sees changes live within the purge SLA without understanding cache.

## Functional requirements

1. Surfaces: dashboard preview iframe (`/app/preview/:username`, backend `preview` mode, `private, no-store` — renders draft tokens + draft avatar + draft blocks, visually badged `Preview — not public`); shared `PurgeChip` component (used by profile/blocks/themes editors — one component, reused, not reimplemented); `SeoChecklist` panel (read-only tag presence per profile).
2. Step 1 — preview flow: editor opens preview → iframe loads `preview` URL with session cookie → renders current drafts (unsaved theme selection and unpublished blocks visible here and ONLY here) → closing preview discards nothing (drafts live in editor state, preview is a lens). Preview iframe is sandboxed (`sandbox="allow-same-origin"`, no scripts from draft content executing beyond the page's own micro-script).
3. Step 2 — publish flow: `Publish` button (enabled when dirty) → calls the owning editor's save first (profile/blocks/theme endpoints — publish never bypasses save) → save `200` sets `purgePending=true` and starts the HEAD poll of the public URL every 5s → chip clears when the live page reflects the change (backend purge done + edge revalidated) or after 2min shows `Still publishing — safe to leave` (poll stops, change is queued server-side, nothing is lost).
4. Step 3 — checklist flow: `SeoChecklist` reads the SSR head model (title/description/canonical/OG/Twitter/JSON-LD booleans + sitemap inclusion state) → green/amber rows with fix links (amber `no description` deep-links to the profile form's seo_desc field). Private profiles show `Hidden from search (private)` as an informational state, never an error.
5. Step 4 — failure presentation: save failure → publish stays disabled with the owning editor's field error (this screen adds no error of its own); poll timeout → `still publishing` copy (never `failed` — only the backend can declare failure, via a later read); offline → publish disabled with `You're offline`.
6. Step 5 — multi-editor coordination: purge chip state lives in the shared shell store (any editor's mutation sets it; any successful poll clears it). Two editors publishing in sequence produce one chip lifecycle, not stacked chips.

## Data / API touches

- UI → server map: preview → SSR `preview` mode GET (cookie, no-store); publish → owning editors' endpoints (profile/blocks/theme), then HEAD poll of `/:username`; checklist → SSR head-model GET. No new endpoints; no cache-admin calls from the client (no purge API exists for the dashboard by design).
- State: `purgePending: bool`, `lastPublishedAt`, `checklist {title, description, canonical, og, twitter, jsonLd, inSitemap}`. All derived from server responses, never computed from draft state except the preview itself.

## Acceptance criteria

- Preview drill: unsaved theme + unpublished block visible in iframe, absent on the public URL; iframe badged `Preview`.
- Publish drill: save → chip appears → clears when live (<2min); timeout shows `still publishing`, change intact.
- Checklist drill: missing description shows amber with deep-link to the exact field; private profile shows informational hidden-state.
- Coordination drill: edits in two tabs produce one chip lifecycle.
- Security drill: preview iframe sandboxed; no purge-admin endpoint callable from client (probe 404s).

## Out of scope

- SSR/tag/cache implementation (backend plan above); read-pattern contracts (database plan above); editor forms (their own frontend plans); beacon/aggregates; resume snapshot UI.

## Open questions

- Q1: HEAD-poll interval 5s — gentler 10s to spare edge quota?
- Q2: Checklist as a panel vs a pre-publish gate blocking publish on amber?
