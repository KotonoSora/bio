# Publish Composer - Acceptance Criteria (feature 42)

Phase 1 BA · BDD; drills run against `docs/02-architecture-techlead/42-publish-composer/` implementations.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Gate drill | 3:01 video, 301 MB file, 2,201-char caption, disallowed codec | Scheduling validates | Each rejected pre-queue with the specific limit cited; zero jobs queued |
| AC-02 | Quota drill | Free lifetime counts and pro rolling-cycle fixtures | The 10th, 11th, and a retried-failure post send | 10th sends; 11th refused with lifetime-exhausted messaging; retried failures count once; pro rolling accounting matches fixtures |
| AC-03 | Calendar drill | Scheduled/sent/late/failed posts | Calendar renders; reschedule and pre-lock cancel run | Posts render in correct slots; reschedule moves the slot; cancel removes with no dispatch |
| AC-04 | Lock drill | An edit inside the 5-min freeze; a past-due dispatch | Each runs | Freeze-window edits refused (`lock_window`); past-due delivers with `late` flag + creator notice |
| AC-05 | IDOR drill | User A post/calendar CRUD against user B ids; public draft access | Requested | 403/zero rows; public has zero access to draft/scheduled content |
| AC-06 | Honesty drill | A fixture run with 5 success + 1 fail + 1 timeout | The status view renders | Exactly "5 of 7 published" with both legs named, reasoned, and individually retryable |
| AC-07 | Idempotency drill | A redelivered queue message; an ambiguous-timeout retry flow | Dispatch resolves | Exactly one provider-side post (or the explicit confirm gate holds where the API cannot guarantee it) |
| AC-08 | Retry-class drill | Rate-limit failure vs revoked-token failure | Retry classifies | Rate-limit auto-retries to success; revoked lands `failed` with reconnect guidance and no futile retries |
| AC-09 | Outage drill | A network pause; poison redeliveries | Pause/resume and quarantine run | Only that leg halts (`queued_behind_outage`); resume replays in order; poison quarantines with ops page after N redeliveries |
| AC-10 | Load drill | A full 7-target fan-out; queue depth growth | Dispatch runs | p95 dispatch-to-first-status within budget; depth pages before creator-visible staleness |
| AC-11 | Upload drill | A 300 MB upload interrupted mid-multipart | The client resumes | Upload completes from the last confirmed part; no client-chosen object keys exist; incomplete uploads expire unpublishable |
| AC-12 | Rollup drill | Concurrent mixed outcomes across targets | Status reads run at multiple points | N-of-7 and parent state are computed from children every read (no drift); never hand-incremented |
