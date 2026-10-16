# Resume Sharing Links - Workflow and User Journey (feature 20)

Phase 1 BA · Flows executed by `docs/02-architecture-techlead/20-resume-sharing-links/`. Rules cited as `BR-nn` from `docs/01-requirements-ba/20-resume-sharing-links/02-user-stories-and-business-rules.md`.

## Share dialog

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Job seeker | Opens `Share` on a published document | Helper returns the derived URLs: standard, SEO (same URL), shortened (or `Mint on first copy`), custom (or connect entry), bio anchor with live/dead state | BR-02..BR-06 |
| 2 | Job seeker | Opens `Share` on an unpublished document | Dialog shows `Not public - publish to enable sharing` with the publish entry; zero copy rows (no dead URLs ever) | BR-01 |
| 3 | Job seeker | Copies the shortened row the first time | One disclosure (`Creates a permanent short link on first use`) then the idempotent mint; `created:true` shown once | BR-04 |
| 4 | Job seeker | Copies any other row | Exact URL to clipboard + `Copied`; nothing persisted | BR-08 |
| 5 | Job seeker | Unpublishes while the dialog is open | Dialog flips to the not-public state live; caption states the half-life once (`Copies you already sent stop working within ~2 minutes`) | BR-07 |

## Revocation and rename

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Job seeker | Flips `is_public` or clears the published version | Resolver turns all five types to 404 / safe-error / hidden within the purge SLA | BR-07 |
| 2 | Job seeker | Re-publishes | All five restore with the new snapshot text (short link resolves again - slug unchanged) | BR-07 |
| 3 | Job seeker | Renames the username | Standard/SEO/custom/bio URLs re-derive on next open; the short link's destination is rewritten (`Short links survive renames` caption); the old-username 301 is the lost-event backstop | BR-07 |
| 4 | Support | Reads the matrix | One row per type: which URL, which snapshot, unpublish result, rename result | US-03 |

## Short link lifecycle

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System | First copy of the shortened row | link-service mints a random slug with `destination = canonical cv URL`, tagged as a resume share | BR-04 |
| 2 | Job seeker | Copies again / reopens the dialog | Same slug returned (`created:false`); mint-count asserted `<= 1` per document | BR-04 |
| 3 | Anyone | Opens an expired/suspended slug | Branded safe-error page (410/403); never stale resume content | BR-04 |
| 4 | Anyone | Scans the QR | QR targets the shortened URL; revocation lifecycle applies | BR-04 |

## A4 PDF view

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Job seeker | `Preview A4` beside the template preview | A4 frame binds the frozen snapshot version at open; same print CSS as export | BR-10 |
| 2 | Job seeker | Prints mid-view after a save lands | Bound version unchanged + `Snapshot changed - re-open preview` notice | BR-10 |
| 3 | Job seeker | `Print / Save PDF` | `window.print()` only; zero network calls; zero PDF code in the bundle | BR-10 |

## Bio embed toggle

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Job seeker | Toggles `Show on my bio page` on | Binds the `resume` block payload (`documentId`); preview thumbnail shows the section; public page gains it on next purge | BR-06 |
| 2 | Job seeker | Toggles off | Payload unbound / block hidden; section disappears; surrounding blocks keep rendering | BR-06 |
| 3 | System | Document unpublished while linked | Section hides without breaking the page (resolver rule reaches the block path) | BR-06, BR-07 |

## Privacy probes

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | User A | Requests the share-helper for user B's document | `403`; helper never returns another owner's URLs | BR-08 |
| 2 | Attacker | Injects script/iframe/event-handler in any resume field | Stripped at capture on all render paths; CSP holds | BR-01 |
| 3 | Auditor | Greps schemas for share URLs | Zero share-URL tables/columns exist | BR-08 |
