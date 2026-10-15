# Compose + Schedule (one upload, calendar, quotas)

PLAN ref: P4-2 (composer + per-platform caption limits → `scheduled_posts{video_r2_key, caption_json, run_at, status}`). P4 scope — do NOT build in P1–P3. Media intake follows the validated-upload precedent (caps + magic-byte + random R2 keys).

## Purpose

Make "post everywhere" a single calm flow: one video, one caption, target platforms, now or later — with every platform's limits enforced before anything queues, and a calendar showing what went out and what is next. Quota and limit failures happen in the composer with plain guidance, never as silent per-platform publish failures at 2 AM.

## Users

- Creator: uploads once, writes once, picks platforms + accounts, sends now or schedules, reviews the calendar.
- Planner (same person, weekly view): drags posts across days, spots gaps, avoids double-posting a campaign.
- Quota policy (as a stakeholder): free lifetime cap and pro rolling-cycle fairness enforced at schedule time.

## Functional requirements

1. Media intake (hard gates, verified before upload completes): video only in v1 — max 3 min duration, max 300 MB, accepted containers/codecs per closed allowlist (MP4/H.264 baseline; anything else rejected with the reason, never transcoded server-side in v1 — see Q2). Magic-byte verification + R2 random key, same as all upload pipelines. Thumbnails: auto-poster-frame default, optional custom upload under the same validation.
2. Caption: single composer box, 2,200-character limit; per-platform mapping stored in `caption_json` (base text + per-network trims/overrides where that network's limit is lower — X/Threads get explicit counters in-UI). Link handling per network (first-comment vs caption vs bio-link note, surfaced as guidance, not automation). Hashtag/mention plain text in v1 (no entity-tagging APIs).
3. Targets: per-post selection of network × account from healthy connections only (see `accounts/social-connections.md`); dead/error connections unselectable with the re-connect path one click away. Default target set = all healthy connections (one-click "Post to All" promise kept).
4. Scheduling: send-now or `run_at` (timezone-explicit, Asia/Ho_Chi_Minh default); draft state for unfinished posts; edit/reschedule allowed until dispatch lock (minutes-before-send freeze, stated in-UI); cancel anytime pre-lock. Past-due dispatch (worker outage) delivers late with a `late` flag + notice, never silently and never dropped.
5. Content calendar: month/week views of scheduled + sent posts (status-colored), per-post drill-down, conflict hint when two posts target the same network within a configurable window. Calendar reads post rows only — no separate event store to drift.
6. Quotas (adapted to our free/pro ladder): free = 10 published posts lifetime (scheduled-but-unsent do not count; failed-then-retried counts once per post); pro = unlimited within a 30-day rolling cycle (fair-use velocity guardrails from the abuse budgets still apply — "unlimited" never means unthrottled). Quota state shown in-composer before confirm; over-quota schedules refused with the exact reset/upgrade path.

## Data / API touches

- New tables: `scheduled_posts{post_id, profile_id, video_r2_key, thumb_r2_key?, caption_json, targets[{network, account_id}], run_at?, status (draft|scheduled|sending|partial|sent|failed|cancelled|late), quota_counted}` + standard `user_id` + RLS. Status transitions audited; edits versioned until dispatch lock.
- Endpoints: composer draft CRUD, `POST /api/v1/posts/schedule`, `POST /api/v1/posts/:id/cancel|reschedule`, `GET /api/v1/posts/calendar?month=` (owner-scoped). Validation (duration/size/caption/caps/quota) runs synchronously at schedule time; dispatch itself is async via `SCHEDULER` → `POST_QUEUE` (see delivery doc).
- Upload path: resumable/chunked intake recommended for 300 MB mobile uploads (see Q1); R2 keys server-generated; no client-chosen paths.

## Acceptance criteria

- Gate drill: 3:01 video, 301 MB file, 2,201-char caption, disallowed codec — each rejected pre-queue with the specific limit cited.
- Quota drill: 10th lifetime free post sends; 11th refused with lifetime-exhausted messaging; pro rolling-cycle accounting verified against fixtures (retried failures count once).
- Calendar drill: scheduled/sent/late/failed posts render in correct slots; reschedule moves the slot; cancel pre-lock removes it with no dispatch.
- Lock drill: edit/reschedule inside the freeze window refused; past-due dispatch delivers with `late` flag + creator notice.
- IDOR drill: user A post/calendar CRUD against user B ids returns 403; public has zero access to draft/scheduled content.

## Out of scope

- OAuth vault and connection health (see `accounts/social-connections.md`); fan-out execution, per-platform status polling, retries (see `delivery/fanout-status.md`).
- Image/carousel/text-only posts (v1 is video-only); server-side transcoding (v2 candidate); automations, campaigns, agency (separate P4 scopes).

## Open questions

- Q1: Chunked/resumable upload protocol for 300 MB mobile files — Tus-style or R2 multipart via worker-issued parts?
- Q2: Server-side transcode/normalize in v2 (never in v1) — worth naming the future pipeline, or premature?
