# Lead Search - Acceptance Criteria (feature 28)

Phase 1 BA · BDD; legacy drills preserved by name.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Search drill | exact, typo'd, short, and cross-creator queries | search runs | exact ranks first; typos still surface their row; the 2-char minimum enforces; cross-creator rows never appear (overlapping fixtures return empty) |
| AC-02 | Filter drill | combined source × status × date filters | filtered results are counted and exported | counts reconcile exactly with direct row counts; count-before-export matches the exported rows |
| AC-03 | Timeline drill | capture, consent change, purchase reference (forward-available), automation event (forward-available), and a note | the detail view renders | one chronological timeline shows all available entries with actor + timestamp; absent forward seams render as disconnected, never faked |
| AC-04 | Action drill | note/tag/suppress actions and a consent-grant attempt | actions run | notes/tags/suppression append actor-stamped events; no consent-grant endpoint exists (surface review); suppression propagates to eligibility before the next send |
| AC-05 | Performance drill | a 100 000-lead fixture and a 10 000-row export selection | search and export run | search stays within p95 ≤ 400 ms; the async export completes without timeout and downloads within the grant TTL |
| AC-06 | Re-invite drill | a non-suppressed contact and a suppressed one | re-invite runs | the email carries a 72 h signed token; one-tap unsubscribe flips `active→unsubscribed` and is evented; suppressed contacts get 422 `reinvite_blocked_suppressed`; 3/day cap holds |
| AC-07 | Export-grant drill | a completed job | the download link is probed: non-owner, expired, re-downloaded | non-owner 404/403 with zero file access; expired → 410; re-grant possible while the job lives; the file is private (no storage-path exposure) |
| AC-08 | Vocabulary drill | 50 tags in use | a 51st is attempted | 422 `tag_limit_reached`; foreign vocabularies invisible; assignments are per-lead and attributed |
| AC-09 | Purge drill | an account with notes, tags, vocabulary, queries, jobs, and files | account deletion runs | all rows + files removed with counts in the purge certificate; storage paths and grants dead afterward |
| AC-10 | Surface-review drill | the codebase and API surface | bulk-message and consent-grant paths are searched | zero bulk-message/bypass actions exist; export (audited) is the only egress; no manual consent write exists |
