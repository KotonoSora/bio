# Resume Format Sharing - Acceptance Criteria (feature 19)

Phase 1 BA · BDD checks gating the release of `docs/02-architecture-techlead/19-resume-format-sharing/`. Rules cited as `BR-nn` from `docs/01-requirements-ba/19-resume-format-sharing/02-user-stories-and-business-rules.md`.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Scrub drill | A publish attempt with snapshot/personal keys, inline styles, or scripts | Validation runs; then the weekly re-scan runs | Rejected with field-mapped codes and zero rows published; the re-scan of all published rows finds zero personal-data keys (BR-01, BR-06) |
| AC-02 | Placeholder drill | An author who owns a fully populated resume | Builder preview and gallery thumbnails render | Only fictional placeholder content appears; the preview endpoint accepts no snapshot input (BR-02) |
| AC-03 | Attribution drill | A published format; then a rename; then a deactivation; then a purge | Gallery card/detail and clone `based-on` lines render in each state | Live link, 301 target, frozen non-linked string, and vanished rows respectively (BR-05) |
| AC-04 | Clone drill | A published format vN | A reuser clones it; the author edits, unpublishes, then is taken down | The clone renders the reuser's snapshot text in the cloned layout throughout; author-side changes never propagate; RESTRICT blocks hard-delete (BR-04) |
| AC-05 | IDOR drill | User B's formats | User A attempts edit/publish/clone-as-owner and direct database access as app role | `403` / RLS denial; the public gallery stays anonymously readable within rate limits (BR-07) |
| AC-06 | XSS/CSP drill | Format-field injection attempts | Rendered on builder, gallery, dashboard preview, and cv surfaces | Stripped on all four; CSP holds (BR-01) |
| AC-07 | Abuse drill | Anonymous gallery bursts; a report; an ops takedown | Rate rules and moderation run | Bursts throttled; takedown hides gallery-wide within the purge SLA; clones unaffected; audit row with reason (BR-06) |
| AC-08 | Version drill | A published vN with a newer draft | Publish runs; then an edit runs | vN row bytes unchanged; v{N+1} appears; published rows never update (BR-03) |
| AC-09 | Selection drill | A cloned format | The reuser selects `fmt:{formatId}` on a document; a foreign id is also attempted | Own selection renders through the layout interpreter; foreign/unknown -> `422 unknown_template` (BR-08) |
| AC-10 | Unlimited drill | Preview, print, and clone paths | Usage is exercised beyond any quota-shaped threshold | No gates appear; print rides `window.print()` A4; no counters/credits exist anywhere (BR-09) |
| AC-11 | Ungoverned-surface drill | The codebase | Grep runs for raw-JSON editing, thumbnail upload, and paywall hooks | None exist (no JSON textarea; no upload route; no gate code) (BR-01, Q-06) |
