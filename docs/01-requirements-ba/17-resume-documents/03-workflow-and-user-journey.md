# Resume Documents - Workflow and User Journey (feature 17)

Phase 1 BA · Flows executed by `docs/02-architecture-techlead/17-resume-documents/`. Rules cited as `BR-nn` from `docs/01-requirements-ba/17-resume-documents/02-user-stories-and-business-rules.md`.

## Create, edit, save

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Job seeker | `New resume` (title) | Document row created (v0, no snapshot); editor opens with empty draft | BR-10 |
| 2 | Job seeker | Edits fields (basics/experience/education/skills/links) | Draft updates client-side only; no version is ever created by editing | BR-02 |
| 3 | Job seeker | Clicks Save Snapshot | Draft + server-filled profile fields captured, sanitized, closed-schema validated; version N+1 inserted; timeline prepends it | BR-01, BR-08 |
| 4 | System | Capture fails (profile read unavailable) | Save refused `503 capture_unavailable`; draft intact client-side; no partial version | BR-08 |
| 5 | Job seeker | Duplicates the document | New document, `{title} copy`, latest snapshot copied as v1 (or empty draft) | BR-10 |

## Timeline, restore

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Job seeker | Opens a version | Frozen read-only view (fields locked, source rev shown) | BR-01 |
| 2 | Job seeker | Restore as draft | Version content copied into the draft; history untouched; saving later appends vN+1 | BR-01 |
| 3 | Job seeker | Opens a version older than 30 days, not published | Read routed to the blob archive; `archived: true` + slower-load notice stated upfront | BR-05 |

## Publish, unpublish

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Job seeker | Publish version V | Confirm copy names any displaced public document; transaction clears other public docs, sets `is_public + published_version=V`; event purges `/:username/cv` | BR-03, BR-06 |
| 2 | Job seeker | Publish again (same or different version) | Idempotent-safe; displaced document named when applicable | BR-03 |
| 3 | Job seeker | Unpublish | Both flags cleared; `/cv` parity `404` within `<= 60 s`; copy states this before confirm | BR-03 |
| 4 | Viewer/recruiter | Opens `/:username/cv` | Published snapshot renders SSR with tags (`noindex`, canonical-to-bio); absent/private/draft-only -> parity `404` | BR-06 |

## Link, delete

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Job seeker | Binds a document to a `resume` block | Block payload gains `documentId`; profile page re-renders on next purge | BR-04 |
| 2 | Job seeker | Deletes a document with a linked visible block | `422 delete_needs_confirm` + blocking confirm naming the consequence ("also hides the resume section") | BR-04 |
| 3 | Job seeker | Confirms delete | Block hidden/unlinked first (idempotent), then document + versions deleted; one reload shows all three gone | BR-04 |
| 4 | System | Crash between unlink and delete | Block hidden, document present; retry completes the delete - no visible dangling link at any point | BR-04 |

## Archive job

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System (daily) | Snapshots `> 30 days` and not published | Append to `resume-archives/{document_id}.ndjson` (write verified), then delete moved rows from PG | BR-05 |
| 2 | System | Archive write fails | Nothing is deleted; retry next run; metric alerts | BR-05 |
| 3 | Job seeker | Retrieves an archived version | Served from the blob with `archived: true`; PG holds published + recent only | BR-05 |

## Security probes (operator-facing)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | User A | `GET/PUT/DELETE` user B's document by id | `403`; direct database access as app role denied by RLS | BR-07 |
| 2 | Attacker | Injects script/iframe/event-handler into any resume field | Stripped at capture; stored snapshots are clean; CSP holds on both surfaces | BR-07 |
| 3 | Job seeker | Opens `/:username/cv` for a private or draft-only document | Parity `404` with identical shape/timing as a missing profile | BR-06 |
