# Publish Composer - User Stories and Business Rules (feature 42)

Phase 1 BA · Enforced by `docs/02-architecture-techlead/42-publish-composer/`. Scope: `docs/01-requirements-ba/42-publish-composer/01-document-context-and-scope.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Creator | upload once, write once, pick platforms plus accounts, send now or schedule | "post everywhere" is one calm flow | High |
| US-02 | Creator | watch per-platform status live and retry failed targets individually | partial failure is visible and fixable, never a lying spinner | High |
| US-03 | Planner | see scheduled/sent/late/failed posts on a calendar and deconflict the week | no double-posting a campaign | High |
| US-04 | Quota stakeholder | have free-lifetime and pro rolling fairness enforced at schedule time | quota failures happen in the composer with guidance | High |
| US-05 | Ops | pause a failing network leg and replay poisoned jobs in order | publishing recovers without improvised scripts | Medium |
| US-06 | Creator | never get a silent drop or double-post | launches are trustworthy | High |

## Business rules

- **BR-01 Media gates (pre-queue, hard).** Video only v1: ≤ 3 min duration, ≤ 300 MB, closed codec/container allowlist (MP4/H.264 baseline); anything else rejected with the specific reason, never transcoded server-side. Magic-byte verification + MinIO server-generated random keys; poster-frame thumbnail default, optional custom upload under the same gates.
- **BR-02 Upload.** MinIO multipart with server-issued parts (resumable for 300 MB mobile files); no client-chosen object paths; incomplete uploads expire unpublishable.
- **BR-03 Caption.** Single 2,200-char composer box; per-network trims/overrides stored in `caption_json`; X/Threads show explicit counters; hashtags/mentions are plain text v1 (no entity APIs).
- **BR-04 Targets.** Network × account from healthy connections only; dead connections unselectable with one-click reconnect; default target set = all healthy connections ("Post to All").
- **BR-05 Scheduling.** Send-now or `run_at` (timezone-explicit, Asia/Ho_Chi_Minh default); drafts; edit/reschedule until the 5-minute dispatch lock (stated in-UI); cancel anytime pre-lock. Past-due dispatch (outage) delivers late with a `late` flag + creator notice — never silent, never dropped.
- **BR-06 Calendar.** Month/week views of scheduled + sent posts, status-colored, per-post drill-down; conflict hint when two posts target the same network within the configured window; reads post rows only.
- **BR-07 Quotas.** Free = 10 published posts lifetime (scheduled-but-unsent uncounted; a failed-then-retried post counts once); pro = 30-day rolling unlimited with velocity guardrails (never unthrottled). Quota state shown in-composer before confirm; over-quota refused with the exact reset/upgrade path. Values in `publish.quotas`.
- **BR-08 Dispatch.** Schedule-time validation is synchronous and complete; dispatch is async: scheduler fires at `run_at` → one child job per target on the dispatch stream → per-network consumers publish using in-process vault leases; request handlers never await a publish.
- **BR-09 Per-target status (closed).** `queued|sending|published|failed|retrying|cancelled|skipped_dead_connection`; parent aggregates `sending|partial|sent|failed|cancelled|late`; the N-of-7 counter derives from children — stored as a computed view, never a hand-incremented column.
- **BR-10 Confirmation + polling.** Per-network status confirmation via status/media-id polling with backoff; per-target deadline 15 min → `failed` with `timeout` reason; provider webhooks where offered are verified per the standard discipline; pollers are idempotent.
- **BR-11 Exactly-once intent.** Dedupe record per `(post_id, network, account_id)`; idempotency keys used where the provider offers them; where it does not, a retry after an ambiguous timeout requires explicit creator confirmation (`unknown — confirm before repost`) — automation never double-posts a launch video.
- **BR-12 Retry classes.** Transient (rate-limit, 5xx) auto-retries with capped attempts + exponential backoff; permanent (auth, policy/takedown, invalid media) fails immediately with the provider's reason in plain language + fix guidance (reconnect, trim caption, replace media); per-target manual retry for eligible states.
- **BR-13 Outage handling.** Per-network global pause (ops) stops that leg; affected posts mark `queued_behind_outage` + creator notice; resume replays in original order; poison jobs quarantine after N redeliveries with an ops page; queue lag/depth dashboards page before creators notice.
- **BR-14 Honest states.** A sent reply/published leg is shown exactly as it is; the status view never rounds up; the calendar reflects the same rows the status view reads.

## Acceptance criteria pointers

Drills in `docs/01-requirements-ba/42-publish-composer/06-acceptance-criteria.md`: gates, quota, calendar, lock, IDOR, honesty (5-of-7), idempotency, retry classes, outage, load. GWT forms live there.
