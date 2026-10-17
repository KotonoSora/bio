# Resume Documents - Acceptance Criteria (feature 17)

Phase 1 BA · BDD checks gating the release of `docs/02-architecture-techlead/17-resume-documents/`. Rules cited as `BR-nn` from `docs/01-requirements-ba/17-resume-documents/02-user-stories-and-business-rules.md`.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Immutability drill | A saved resume snapshot | Profile bio/links are edited and the saved version is reprinted | Output is byte-identical (excluding print header/footer); edits require a new snapshot to appear (BR-01) |
| AC-02 | Draft discipline drill | An open editor | Autosave bursts run, then Save Snapshot is clicked | Autosave creates zero versions (network-log proof); the explicit save creates exactly one (BR-02) |
| AC-03 | Persistence drill | A reorder/save session | The dashboard is reloaded | The linked block and selected snapshot persist (BR-04, BR-10) |
| AC-04 | Public drill | Public and private documents | `/:username/cv` is curled cold (no JS) | Public returns snapshot content with title/meta/canonical/OG + JSON-LD `Person`; private/draft-only returns parity `404` (BR-06) |
| AC-05 | IDOR drill | User B's documents | User A issues `GET/PUT/DELETE /api/v1/documents/<B-id>` and direct PG as app role | API denies (`403`/`404` per contract); RLS denies the direct query (BR-07) |
| AC-06 | XSS drill | Script/iframe/event-handler payloads in any resume field | Saved, then rendered on dashboard and CV | Stripped at capture; CSP holds on both surfaces (BR-07) |
| AC-07 | Archive drill | A 31-day-old non-published version | The archive job runs; the version is then retrieved | Moved to the archive file (write verified) then deleted from PG; retrieval returns `archived: true`; published + recent stay in PG (BR-05) |
| AC-08 | Single-public drill | A public document | Another document is published; a race is forced | Old clears, new sets, partial unique holds under race; displaced document named in copy (BR-03) |
| AC-09 | Delete matrix drill | Documents with: no link, hidden link, visible link | Delete is attempted with and without confirm; a crash is forced between unlink and delete | `422 delete_needs_confirm` without confirm; confirmed path hides/unlinks then deletes; crash leaves hidden block + document only - no visible dangling link (BR-04) |
| AC-10 | Restore drill | A version in the timeline | Restore as draft, then save | History intact; restore does not rewrite history; the save appends vN+1 (BR-01) |
