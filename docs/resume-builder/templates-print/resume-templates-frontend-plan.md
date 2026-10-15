# Resume Templates Frontend Plan (gallery + preview + print — UI build steps)

PLAN ref: P2-1 screen + print UX (gallery, interactive preview, A4 print, `window.print()` only). BA source: `docs/resume-builder/templates-print/resume-templates.md`. Server truth in `docs/resume-builder/templates-print/resume-templates-backend-plan.md` (registry, render split, gating — never redefined here).

## Purpose

Give the frontend developer an ordered UI procedure for template experience: gallery selection with instant re-render, interactive screen preview, print-preview fidelity, and the print action that always emits the frozen snapshot. Follow steps 1–6 in order. Styling work can never corrupt saved data — preview and persistence are separate actions on separate endpoints.

## Users

- Frontend developer: builds the template gallery, preview panes, and print flow.
- QA: executes the parity, print-source, and gating drills.
- Job seeker: previews templates, prints or saves PDF for applications.

## Functional requirements

1. Surfaces: template gallery strip (thumbnail + name + premium badge, from `GET` registry read — gallery renders from the server list so a code-shipped template appears with no client change); screen preview pane (interactive: collapse/zoom/density); print preview mode (A4 frame, exact print CSS, no dashboard chrome); `Print / Save PDF` button (`window.print()` and nothing else).
2. Step 1 — gallery/select flow: open picker → thumbnails (placeholder-rendered images from the server, never the user's snapshot in the gallery strip) → click sets draft `templateId` and re-renders preview instantly (unsaved-preview pill, same pattern as link-to-bio themes) → `Save` calls `PUT documents/:id {template_id}` → `200` clears pill + sets purge-pending chip (cv SSR re-renders downstream) → `403 template_forbidden` rolls back with upgrade prompt → `422 unknown_template` refreshes the gallery.
3. Step 2 — screen preview flow: preview binds the FROZEN snapshot selected in the version timeline (or latest saved — never the draft editor state, with the source labeled `Previewing Snapshot vN` and a one-click jump to that timeline entry). Section collapse/zoom/density are view-only transforms (no state writes, no persistence — closing preview loses nothing because there was nothing to save).
4. Step 3 — print flow: `Print` switches to the print tree (separate read-only render of the same frozen snapshot: A4 pages, exact colors, unbroken experience entries) → calls `window.print()` → browser dialog (user picks Save-as-PDF or printer). The client holds zero PDF code (no jsPDF/html2canvas — their absence is a requirement, grep-verified). Draft edits made after opening print preview do NOT leak into the print tree (print binds the frozen snapshot id at open time; a `Snapshot changed — re-open preview` notice appears if a save lands mid-preview).
5. Step 4 — draft/preview separation (the critical honesty rule): the editor's draft and the preview's snapshot are different objects with different pills (`Draft` vs `Snapshot vN · frozen`). Printing from draft state is impossible — the print button lives on the frozen preview, and opening it without a saved snapshot offers `Save Snapshot first` (one click, then print continues).
6. Step 5 — premium template UX: premium cards show the badge + upgrade entry (free click opens upgrade, never calls PUT — the server 403 is the gate, the sheet is honesty); post-upgrade, selection persists normally.
7. Step 6 — edge cases: print dialog cancelled (nothing persisted, nothing lost); snapshot deleted mid-preview (preview shows `Version no longer exists` + timeline jump); expired session (re-login preserves gallery selection + draft, per the snapshot frontend plan's session rules).

## Data / API touches

- Screen → endpoint map: gallery → registry read; select → `PUT /api/v1/documents/:id {template_id}`; preview binds snapshot reads (same version endpoints as the snapshot frontend plan); print → `window.print()` only (no endpoint, no bytes uploaded, no PDF stored).
- Type mirror: `Template {id, name, tokens, thumbnail, premium}`. Code → UI map: `unknown_template` → gallery refresh; `template_forbidden` → upgrade + rollback.

## Acceptance criteria

- Gallery drill: new code-shipped template appears with no client deploy; premium on free opens upgrade only.
- Preview drill: preview labels its snapshot version; draft edits never appear in preview until saved.
- Print-source drill: printed output equals the frozen snapshot text (diff against version payload); mid-preview save triggers the re-open notice.
- No-PDF drill: client bundle contains no PDF libraries (grep); print path issues zero network calls.
- Gating drill: forced-403 rolls back selection with upgrade prompt.

## Out of scope

- Registry/render/SSR logic (backend plan above); storage contracts (database plan above); version timeline and save flows (snapshot frontend plan); five-link matrix (sharing plans); backup/adapters (portability plans).

## Open questions

- Q1: Gallery placement — embedded strip in the editor vs full-page gallery route?
- Q2: Density toggle — per-template setting persisted, or view-only like zoom?
