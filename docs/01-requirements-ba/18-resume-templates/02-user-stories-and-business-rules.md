# Resume Templates - User Stories and Business Rules (feature 18)

Phase 1 BA · Enforced by `docs/02-architecture-techlead/18-resume-templates/`. Scope: `docs/01-requirements-ba/18-resume-templates/01-document-context-and-scope.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Job seeker | preview templates on screen and print / save PDF for applications | I can apply with a frozen snapshot that looks right | High |
| US-02 | Recruiter / viewer | read the public `/:username/cv` SSR page or a printed PDF | I can review the resume wherever it lands | High |
| US-03 | Template contributor (internal) | add a token-based template without touching user data code | the gallery grows safely | Medium |

## Business rules

- **BR-01 Token-only templates.** A registry entry carries `{id, name, tokens{font, scale, accent, density}, premium}` plus renderer components and a thumbnail - nothing else. No per-user CSS/JS exists; style attributes from user fields are dropped; every declared token must be consumed by both screen and print renderers (an unused token fails the registry test).
- **BR-02 Same snapshot, text parity.** Switching `templateId` re-renders the same snapshot with identical extracted text across all templates; layout-only differences; selection never mutates snapshot rows and never creates a version.
- **BR-03 Print contract.** Print is a separate read-only tree derived from the snapshot at print time: A4 pages, single-column flow, `print-color-adjust: exact`, no dashboard chrome, page breaks avoiding split experience entries where feasible; Chrome + Safari print-preview fixtures gate each template.
- **BR-04 Print binds frozen state.** The print tree binds the frozen snapshot id at preview-open time; draft edits made afterwards never leak in; a save landing mid-preview shows a `Snapshot changed - re-open preview` notice; the print button lives on frozen previews only (no snapshot -> one-click save-first flow).
- **BR-05 No server PDF, no client PDF code.** Export is `window.print()` - user picks Save-as-PDF. No PDF engine/headless browser exists in any server dependency set and no PDF library exists in the client bundle (both are grep gates; print path issues zero network calls).
- **BR-06 cv SSR text parity.** `/:username/cv` renders the published snapshot with the featured template's layout cues so curl-visible text matches the printed content (text parity, not pixel parity); tags follow feature 10 (`noindex`, canonical-to-bio, full meta + JSON-LD); private/unpublished stays parity `404`.
- **BR-07 Registry truth.** Unknown `templateId` on write -> `422 unknown_template`; unknown or renamed id on read -> fallback to `classic` + `template.fallback` metric - never a 500 and never a blank page.
- **BR-08 Premium gate mechanism.** The registry carries a `premium` flag checked server-side against the feature 01 entitlement snapshot (fail-closed to free) at selection and render; free accounts get `403 template_forbidden`; UI hiding is honesty, never the gate. Zero premium templates ship at launch (Q-03).
- **BR-09 Placeholder thumbnails.** Gallery thumbnails render with placeholder content only, produced by the same pipeline as the template; a real user's snapshot never appears in gallery assets; no PII enters thumbnail render inputs.
- **BR-10 Persistence discipline.** `templateId` persists only through feature 17's document update; selection survives snapshot saves (metadata, not content); density and zoom are view-only transforms that write nothing.

## Constraints carried

- Screen markup is interactive (collapse/zoom/density) but read-only over data; print markup is a separate tree - edit state and print markup stay separate at every layer.
- Catalog and renderer bundle ship together: a CI consistency gate requires every registry id to have a renderer + thumbnail in the same bundle (SPA/print) - a registry id the running bundle cannot render is a build failure, not a runtime surprise.
- No plan values, prices, or claim text appear here; premium enablement defers to feature 03's claim registry process.
