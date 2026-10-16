# Resume Sharing Links - Acceptance Criteria (feature 20)

Phase 1 BA · BDD checks gating the release of `docs/02-architecture-techlead/20-resume-sharing-links/`. Rules cited as `BR-nn` from `docs/01-requirements-ba/20-resume-sharing-links/02-user-stories-and-business-rules.md`.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Parity drill | One frozen snapshot | All five types are fetched with curl (no JS) after a template switch with unsaved draft edits | Snapshot text matches on all five; layout-only changes apply; draft edits stay invisible (BR-01) |
| AC-02 | Privacy/revocation drill | A published document | Unpublish, then re-publish; then rename the username | All five flip to 404/safe-error/hidden within `<= 60 s`, then restore; rename `301`s old -> new; one flip covers all five (BR-07) |
| AC-03 | Short-link drill | A published document | The shortened row is copied twice; expiry/disable is forced; the slug is inspected | First call mints (`created:true`); second returns the same slug (`created:false`, mint-count asserted `<= 1`); expired/disabled shows the branded safe-error, never stale content; slug carries no PII (BR-04) |
| AC-04 | Rename drill | A minted short link | The username is renamed; the rewrite event is delivered, then suppressed in a second run | Destination re-derives with no re-mint; with the event suppressed the old-username 301 still resolves - never a dead link (BR-07) |
| AC-05 | IDOR drill | User B's documents | User A calls the helper and the mint for B's ids; direct PG as app role is attempted | `403` / RLS denial on both; no URL derivation leak (BR-08) |
| AC-06 | XSS/CSP drill | Script/iframe/event-handler payloads in every resume field | Rendered on all five paths | Stripped at capture on all five; CSP holds (BR-01) |
| AC-07 | Abuse drill | Anonymous bursts on cv/short/custom paths; legitimate heavy use | Rate rules run | Bursts throttled; unlimited legitimate views/exports unaffected; zero quotas appear anywhere (BR-09) |
| AC-08 | Derivation drill | All schemas | Grep + review run | Zero share-URL tables/columns exist; helper output changes with rename/republish without any row write (BR-08) |
| AC-09 | Dialog-honesty drill | A published document; then an unpublished one; then a first short copy | The dialog is opened in each state | Five live rows with verified tag state / not-public state with zero rows / single permanent-mint disclosure - no dead URL is ever shown (BR-02..BR-04) |
| AC-10 | A4 drill | A frozen snapshot in the A4 frame | A save lands mid-view; print is invoked | Bound version prints (draft never leaks) with the re-open notice shown; `window.print()` only; zero PDF code both sides (grep, shared with feature 18) (BR-10) |
| AC-11 | Bio-embed drill | A published document with no block link | The toggle binds, then unbinds; then the document is unpublished while linked | Section appears/disappears with the preview thumbnail; page never breaks; unpublish hides the section via the resolver rule (BR-06, BR-07) |
| AC-12 | Support-matrix drill | Every type | The matrix is walked against live infrastructure | Each row's URL, snapshot, unpublish result, and rename result match reality (US-03) |
