# Publish Automate (execution plan P4a — cross-posting and IG automations)

PLAN ref: P4-1 (per-network OAuth + encrypted vault + plan connection caps) + P4-2 (composer → `scheduled_posts` via scheduler/fan-out, polled status) + P4-3 (IG automations + watcher + contacts quota) + S-1/S-3/S-6. BA source: `docs/publish/accounts/social-connections.md`, `docs/publish/composer/compose-schedule.md`, `docs/publish/delivery/fanout-status.md`, `docs/automate/triggers/comment-triggers.md`, `docs/automate/delivery/dm-delivery.md`, `docs/automate/contacts/contact-lists.md`. GATED: never stubbed into E0–E3 images (grep + migration audit). DEVIATION CARRIED: Spring Boot service + containerized PG replace Worker/queue defaults (vault via envelope encryption, scheduler + outbox fan-out); per-platform status polling (never `await` publish in-request) retained; contract frozen.

## Purpose

Ship "post everywhere + auto-reply" as one gated phase: healthy-connection vault, calm composer with pre-queue limit enforcement, async fan-out with per-platform status, and quota-bound IG comment→DM automations feeding contact lists. P4a exits with vault, quota, late-flag, and quota-block drills green — and with the first-split evaluation recorded on measured backlog.

## Users

- Creator: connects networks, uploads once, schedules everywhere, watches N-of-7 status, builds keyword automations.
- Commenter/follower: triggers a rule and receives exactly one public reply plus at most one link-DM.
- Ops: rotates vault keys, pauses automations platform-wide, reconciles stuck dispatches from status rows.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6`, Boot `4.1.1`, Kotlin `2.4.21`, Gradle `9.8.1`. Architecture verdict for P4a — modular monolith with the first real split evaluation, decided here per use case. Connections vault + composer + calendar stay in-monolith (CRUD volume, shared RLS/audit with `bio`). Fan-out dispatch + automation watcher are the designated split candidates: monolith pros (one status machine `draft|scheduled|sending|partial|sent|failed|cancelled|late`, one quota ledger, one audit log) vs microservices pros (bursty network-bound publishers scale independently of the API; watcher long-polls without stealing request threads). Verdict: build in-monolith behind `DispatchPort`/`WatchPort` seams with worker-thread pools and outbox tables; extract to worker services only if ≥2 framework triggers fire on measured P4a backlog (sustained past-due dispatches, watcher poll lag) — recorded with the gate numbers, rollback to the monolith image demonstrated on staging. AI design assist stays backlog (needs plan update, no stubs).
2. Connections vault: per-network OAuth (YT/IG-TikTok/FB/X/Threads/LinkedIn; IG = Business) with encrypted token storage (envelope encryption, `TOKEN_ENC_KEY`-equivalent via vault/env, rotation without downtime), per-plan connection caps, health checks marking dead connections unselectable with one-click reconnect; tokens never logged, refresh transparent; scopes minimal per network.
3. Composer and calendar: video-only v1 (≤3 min, ≤300MB, closed codec/container allowlist, magic-byte + random blob key, poster-frame thumbnail); single 2,200-char box mapped to per-network trims with explicit X/Threads counters; targets restricted to healthy connections (default = all healthy = "Post to All"); timezone-explicit scheduling (Asia/Ho_Chi_Minh default), drafts, pre-lock edit/reschedule, pre-lock cancel, minutes-before-send dispatch freeze; past-due dispatch delivers with `late` flag + notice (never silent, never dropped). Calendar month/week views read post rows only (status-colored, conflict hints on same-network proximity). Quotas: free 10 lifetime published posts (unsent uncounted, retried-once counts once), pro 30-day rolling unlimited with velocity guardrails; in-composer quota state, over-quota refused with reset/upgrade path.
4. Fan-out: schedule-time validation synchronous; dispatch async via scheduler → per-target outbox jobs with backoff/retries; per-platform status polling (never blocking the request); N-of-7 status rollup (`partial` on mixed outcomes); failed posts retryable with state preserved; stuck dispatches reconcilable from status rows + provider refs.
5. IG automations: watcher on comments; rule kinds keyword + any-comment with quotas adapted to free/pro ladder (keyword 3/5/unlimited-class, any-comment 4/10/unlimited-class, contacts 100/yr–100k/mo-class per tier); per rule: match expression, reply templates, delay bounds; action = exactly one public reply + at most one 1-link DM → `leads` (same lead shape/consent as L0); contacts quota enforced pre-send (over-quota blocks with 100/yr-style messaging); sudden rule edits versioned; platform-wide kill switch + per-rule pause, all audited.
6. Security baseline: owner-scoped post/rule/contact access 403 + RLS deny (S-1/S-6); signed payload verification on provider callbacks (S-3 bad-sig/replay/tamper battery per network); upload S-4 mechanics; Turnstile + rate limits on composer/automation endpoints (S-5); tokens/secrets never in repo/logs/bundles with quarterly rotation.
7. Container per release: P4a tag runs via `docker run` and `podman run` against PG `18.6` with scheduler + worker pools enabled per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md` (E4 scope audit: zero P4 tables/jobs/endpoints in E0–E3 images).

## Data / API touches

- Tables: `social_connections{user_id + RLS, network, account_ref, enc_tokens, health, caps}` (encrypted columns, never logged), `scheduled_posts{post_id, profile_id, user_id + RLS, video_key, thumb_key?, caption_json, targets[{network, account_id}], run_at?, status, quota_counted}` (+ dispatch outbox/status rows), `automation_rules{…match, reply_templates, delay_s, status}`, `automation_events{…}`, `contact_lists{…quota counters}`. All PG via repositories; RLS throughout.
- Endpoints: connect/disconnect/reconnect, health reads, composer draft CRUD, schedule/cancel/reschedule, calendar reads, fan-out status reads, rule CRUD + pause, contact-list reads. Publish execution never in-request.
- Scheduler: dispatch, status-poll, quota-rollover, watcher tick — DB-locked, idempotent per row, visible in ops metrics; past-due items flagged `late`.

## Acceptance criteria

- Vault drill: connect → healthy; token refresh invisible; dead connection unselectable with reconnect path; rotation without downtime; token grep clean.
- Composer drill: 3:01/301MB/2,201-char/bad-codec each rejected pre-queue with the limit cited; quota (10th sends, 11th refused; pro rolling verified); lock-window edits refused; past-due delivers `late` + notice; calendar slots correct.
- Fan-out drill: N-of-7 mixed outcome rolls up `partial` with per-platform refs; failed post retries to terminal state; stuck dispatch reconciled from rows.
- Automation drill: keyword match → one public reply + one link-DM → lead row; contacts over-quota blocks pre-send; rule pause/kill effective within seconds; edit versioning intact.
- Security drill: cross-user post/rule access 403 + RLS deny; provider callback forgery battery green; throttles hold; audit covers vault/role/dispatch/pause events.
- Container drill: digest pair boots with scheduler green, compose green, rollback green, OpenAPI diff clean; E4-grep audit on E0–E3 tags still clean.

## Out of scope

- Broadcast campaigns/retargeting (P4-growth marketing — separate plan update); agency roster/Act-as-user/reporting/seats (see `docs/platform/delivery/agency-teams.md`); AI surfaces beyond closed composer validations; replicas/ISR (see `docs/platform/delivery/scale-out.md`).

## Open questions

- Q1: Chunked/resumable upload protocol for 300MB mobile files — Tus-style or blob-multipart via issued parts?
- Q2: First split — fan-out workers or automation watcher (decide at P4a exit on measured backlog)?
