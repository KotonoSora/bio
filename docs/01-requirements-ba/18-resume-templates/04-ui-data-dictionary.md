# Resume Templates - UI Data Dictionary (feature 18)

Phase 1 BA · Gallery, preview, and print surfaces in the SPA. Contracts: `docs/02-architecture-techlead/18-resume-templates/`. Rules: `docs/01-requirements-ba/18-resume-templates/02-user-stories-and-business-rules.md`.

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Gallery strip | Horizontal list | Auto | Embedded in the editor (Q-04); cards from the registry catalog; scroll-snap on small screens | First render shows registry order |
| Template card | Card | Auto | Thumbnail (placeholder content), name, premium badge when flagged; selection ring on current | `classic` selected by default |
| Premium badge | Badge | Auto | Shown only when `premium=true` (zero at launch); click opens upgrade, never calls `PUT` | Hidden |
| Unsaved-preview pill | Chip | Auto | Appears when draft `templateId` differs from saved; same pattern as feature 09's theme preview | Hidden when clean |
| Preview pane | Canvas | Auto | Binds a frozen snapshot; label `Previewing Snapshot vN` with jump-to-timeline | Latest saved snapshot |
| Zoom control | Slider | No | View-only; no persistence; resets on reopen | 100% |
| Density toggle | Toggle | No | View-only (Q-05); compact/spacious rendering of the same snapshot | Template default |
| Section collapse | Disclosure rows | No | View-only | All expanded |
| Print / Save PDF button | Button | No | Lives on frozen previews only; unavailable on draft; click builds the print tree then calls `window.print()` | Hidden in draft editor |
| Print tree | Read-only render | Auto | A4 single-column, exact colors, no chrome; bound snapshot id fixed at open | Instantiated per print action |
| Snapshot-changed notice | Banner | Auto | `Snapshot changed - re-open preview` when a save lands mid-preview | Hidden |
| Save-first prompt | Inline action | Auto | Shown when printing without a snapshot: `Save Snapshot first` one-click continues to print | Contextual |
| Fallback notice | Inline notice | Auto | `Showing the classic layout - this template is no longer available.` when read fallback fires | Hidden |
| Upgrade entry | Sheet | Auto | Premium click target; explains the gate; no write attempted | Only when a premium card exists |
| Thumbnail asset | Image | Auto | Placeholder-rendered only; `<= 100 KB`; fingerprinted URL; no PII by construction | Registry asset |

Copy discipline: no pixel-parity promises for SSR (text parity stated); no print-quality guarantees beyond A4 flow rules.
