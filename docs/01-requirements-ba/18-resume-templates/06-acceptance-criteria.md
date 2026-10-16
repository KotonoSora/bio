# Resume Templates - Acceptance Criteria (feature 18)

Phase 1 BA · BDD checks gating the release of `docs/02-architecture-techlead/18-resume-templates/`. Rules cited as `BR-nn` from `docs/01-requirements-ba/18-resume-templates/02-user-stories-and-business-rules.md`.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Parity drill | A fixed snapshot | Rendered across every template (screen) | Extracted text is identical; only layout differs (BR-02) |
| AC-02 | Print drill | A frozen snapshot | Print preview is opened on A4 in Chrome + Safari | Single-column flow, no dashboard chrome, colors preserved (`print-color-adjust: exact`), experience entries not split (BR-03) |
| AC-03 | Print-source drill | A frozen snapshot plus unsaved draft edits | `window.print()` is invoked; then a save lands mid-preview | Printed output equals the frozen snapshot (draft never leaks); the re-open notice appears (BR-04) |
| AC-04 | Curl drill | A published resume | `/:username/cv` is fetched cold (no JS) | Resume text + full tag set + JSON-LD `Person` present; `noindex` + canonical-to-bio hold (BR-06) |
| AC-05 | Injection drill | Script/iframe/event-handler payloads in every resume field | Rendered on screen, print tree, and SSR | Stripped/neutralized on all three; CSP `default-src 'self'` holds (BR-01) |
| AC-06 | No-PDF drill | Server dependency set + client bundle | Scan runs; print path is executed | Zero PDF/headless-browser packages server-side; zero PDF libs client-side; print path issues zero network calls (BR-05) |
| AC-07 | Registry drill | A renamed/removed template id; an unknown id on write | Read and write paths run | Read falls back to `classic` + metric; write `422 unknown_template`; no 500s (BR-07) |
| AC-08 | Gate drill | A test registry entry with `premium=true`; a free account; a snapshot outage | Selection is attempted in each state | Free -> `403 template_forbidden` with rollback; outage -> fail-closed deny; entitled selection passes; launch registry has zero premium entries (BR-08) |
| AC-09 | Thumbnail drill | All gallery assets | Pixel-scan + input-type review runs | Placeholder content only; no PII keys; `<= 100 KB` each (BR-09) |
| AC-10 | Consistency drill | Registry ids vs bundle renderers/thumbnails | CI consistency gate runs with a deliberately removed renderer | Build fails naming the id; runtime never serves an unrenderable card (BR-01) |
| AC-11 | Persistence drill | A template switch | Snapshot save follows; page reloads | Selection persists; versions unaffected; density/zoom write nothing (BR-10) |
