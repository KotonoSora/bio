# Resume Templates - Workflow and User Journey (feature 18)

Phase 1 BA · Flows executed by `docs/02-architecture-techlead/18-resume-templates/`. Rules cited as `BR-nn` from `docs/01-requirements-ba/18-resume-templates/02-user-stories-and-business-rules.md`.

## Gallery, select, save

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Job seeker | Opens the template picker in the editor | Gallery strip rendered from the registry catalog (thumbnail + name + badge); ids the bundle cannot render are blocked by the CI gate, not shown broken | BR-01, BR-07 |
| 2 | Job seeker | Clicks a template | Draft `templateId` set; preview re-renders instantly with an unsaved-preview pill; snapshots untouched | BR-02, BR-10 |
| 3 | Job seeker | Saves selection | `PUT` validates the registry id (+ premium gate); column updates; purge event re-renders `/cv` | BR-02, BR-08 |
| 4 | Job seeker | Clicks a premium template while unentitled | Upgrade promise shown; no write is attempted (server `403` is the real gate) | BR-08 |
| 5 | System | Unknown/renamed id appears on a read | Falls back to `classic` + `template.fallback` metric | BR-07 |

## Preview

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Job seeker | Opens preview | Binds the frozen snapshot (latest saved or timeline-selected), labeled `Previewing Snapshot vN` | BR-04 |
| 2 | Job seeker | Collapses sections / zooms / toggles density | View-only transforms; nothing persisted; closing loses nothing | BR-10 |
| 3 | System | A save lands mid-preview | `Snapshot changed - re-open preview` notice; preview keeps its bound version | BR-04 |

## Print

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Job seeker | Clicks Print on a frozen preview | Print tree built from the bound snapshot; A4 flow, exact colors, no chrome | BR-03, BR-04 |
| 2 | Job seeker | Browser dialog opens | `window.print()` only; user picks printer or Save-as-PDF; zero network calls from the print path | BR-05 |
| 3 | Job seeker | Prints without a saved snapshot | Print button is unavailable on draft; `Save Snapshot first` one-click flow | BR-04 |
| 4 | Job seeker | Cancels the dialog | Nothing persisted, nothing lost | BR-03 |

## Public CV

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Viewer/recruiter | Opens `/:username/cv` (or curls it) | Published snapshot rendered with the featured template's layout cues; text matches the printed content; tags + `noindex` + canonical per feature 10 | BR-06 |
| 2 | Viewer/recruiter | Opens a printed PDF from the same template | Text identical to the SSR view (text parity, not pixel parity) | BR-06 |
| 3 | Viewer/recruiter | Requests an unpublished/private cv | Parity `404`, same shape as a missing profile | BR-06 |

## Contributor flow

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Template contributor | Adds registry entry + screen/print renderers + thumbnail (code change) | Boot validation passes (unique id; every token consumed); CI consistency gate finds renderer + thumbnail | BR-01 |
| 2 | Template contributor | Ships a template with placeholder-rendered thumbnail | Pixel-scan proves no PII in the asset; pipeline rejects real snapshot inputs by construction | BR-09 |
