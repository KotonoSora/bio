# Resume Sharing Links - UI Data Dictionary (feature 20)

Phase 1 BA · Share dialog, A4 view, and bio toggle in the SPA. Contracts: `docs/02-architecture-techlead/20-resume-sharing-links/`. Rules: `docs/01-requirements-ba/20-resume-sharing-links/02-user-stories-and-business-rules.md`.

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Share button | Button | Auto | On the document editor and list rows; opens the dialog | Disabled while the helper call is in flight |
| Share dialog | Dialog | Auto | Five rows via one `ShareLinkRow` component (URL + copy + status caption + type explainer) | Row states derive from the helper response; no local truth |
| Unpublished state | Dialog state | Auto | `Not public - publish to enable sharing` + publish entry; copy rows absent (not disabled-mysteriously) | Shown when `unpublished: true` |
| Standard row | ShareLinkRow | Auto | `/:username/cv`; caption `Readable by anyone with the link` | Always first |
| SEO row | ShareLinkRow | Auto | Same URL; `Tags verified` chip re-checked live per dialog open (fetch + parse the SSR tag checklist); failure shows `Tags need attention` + the failing item | Same URL, never a second URL |
| Shortened row | ShareLinkRow | Auto | Short URL when minted; `Mint on first copy` affordance when not; first copy discloses `Creates a permanent short link on first use` then copies; caption `Short links survive renames` | Absent for unpublished |
| QR affordance | Thumbnail button | No | Beside the shortened row; jumps to the shortener QR flow preselected; pixels render there | Hidden until a slug exists |
| Custom row | ShareLinkRow | Auto | `https://{host}/cv` when a verified host exists; otherwise `Connect a custom domain` entry linking the account domain flow (handoff, never embedded) | Hidden for unpublished |
| Bio-embed row | ShareLinkRow + toggle | Auto | `/:username#resume`; live/hidden/not-linked state from block linkage; toggle binds/unbinds and shows a section preview thumbnail | Caption states effect before saving |
| Revocation caption | Text | Auto | Shown once per open: `Copies you already sent stop working within ~2 minutes of unpublishing` | Static copy, no per-type variants |
| Rename caption | Text | Auto | On the shortened row: `Short links survive renames`; other rows re-derive on next open | Static |
| A4 view | Frame | No | Beside the template preview; binds the frozen snapshot at open; `Snapshot changed - re-open preview` notice pattern | Latest saved snapshot |
| Print / Save PDF button | Button | No | A4 view only; calls `window.print()`; zero client PDF code (grep-verified) | - |
| `View live CV` link | Link | No | Jumps to the standard URL | Editor toolbar |

Copy discipline: no dead URLs ever (unpublished hides rows); no analytics/tracking surfaces (Q-05); no plan/quota language (BR-09); captions stay type-specific and honest (half-life, permanent mint, rename survival).
