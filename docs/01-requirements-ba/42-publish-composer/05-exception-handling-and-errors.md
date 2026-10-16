# Publish Composer - Exception Handling and Errors (feature 42)

Phase 1 BA · Problems follow RFC 9457 (`code` field). Media gate failures cite the exact limit; provider failures cite the provider reason in plain language.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| 3:01 video / 301 MB file / 2,201-char caption / disallowed codec | API (pre-queue) | 422 `media_invalid` / `caption_too_long` with the specific limit; nothing queues | "Video is 3:01 — the limit is 3:00." (limit-specific copy) |
| Upload interrupted mid-multipart | Upload | Parts resumable; incomplete upload expires unpublishable | "Upload paused — resume when ready." |
| Bad magic bytes / spoofed extension | API | 422 `media_invalid`; file never stored as a valid object | "This file type isn't supported." |
| Dead connection selected | API | 409 `connection_dead` at schedule time (fail closed) | "Reconnect {network} before it can publish." |
| Over quota (free 11th lifetime; pro velocity trip) | API | 403 `quota_exhausted` with reset/upgrade path | "Your free posts are used up. Upgrade or wait for reset." |
| Edit/reschedule inside the 5-min lock | API | 409 `lock_window` | "Edits freeze 5 minutes before send." |
| Cancel after dispatch started | API | 409 `dispatch_started`; per-target cancellation only where not yet sent | "This post is already going out." |
| Provider rate-limit / 5xx | Dispatch | Auto-retry (`retrying`) with backoff; capped attempts | Status shows retrying; creator informed if it fails out |
| Provider auth failure | Dispatch | Permanent `failed`; reconnect guidance; no futile retries | "Reconnect {network} — its login expired." |
| Provider policy/takedown refusal | Dispatch | Permanent `failed` with provider reason; media/caption guidance | "The platform refused this post: {reason}." |
| Ambiguous timeout (no provider verdict) | Dispatch | Poll-then-ask: reconcile; if unverifiable → `unknown` + confirm gate before repost | "We couldn't confirm — check the platform, then confirm a repost." |
| Provider-side duplicate risk (no idempotency key) | Retry | Dedupe record + explicit confirm gate; automation never double-posts | Same confirm-gate copy |
| Network leg paused (ops) | Dispatch | Jobs held in order; posts `queued_behind_outage` + notice | "Publishing to {network} is paused — we'll send when it's back." |
| Poison job (N redeliveries) | Queue | Dead-letter + ops page; replay tool | None (creator sees honest failed status if terminal) |
| Scheduler outage → past-due | System | Delivers on recovery with `late` flag + notice | "Sent late at {time} — {network} had an outage." |
| Calendar conflict (same network twice) | UI | Advisory hint only | "Two posts to {network} close together." |
| Cross-user post/calendar access | API | 403/404 per owner scoping; zero rows for foreign ids | "Not found." |
| Public access to drafts/scheduled content | API | Zero access exists (by absence) | None |
| Quota counting disagreement (retried failure) | System | One counting function; retried-once counts once; reconciliation drill | None |
