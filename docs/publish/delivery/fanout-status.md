# Fan-out + Status (background publishing, 7-of-7 tracking)

PLAN ref: P4-2 (`SCHEDULER` + `POST_QUEUE` fan-out, poll status, never `await` publish in request). P4 scope — do NOT build in P1–P3. No `setTimeout` delays, no direct DB writes from request handlers, no token material in queue messages.

## Purpose

Deliver the "7 of 7 published" promise honestly: background fan-out across seven platforms with per-platform status a creator can watch, retry, and trust. Publishing is the least reliable input in the product (seven foreign APIs with seven moods), so this doc makes partial failure a first-class visible state with per-target retry — never a spinner that lies, never a silent 6-of-7.

## Users

- Creator: watches per-platform status live, retries failed targets individually, sees exactly where a post stands ("7 of 7 published" or precisely which leg failed and why).
- Ops: sees provider-side failure clusters, pauses a network leg globally during an outage, replays poisoned jobs after a fix.
- Buyer/visitor: unaffected — publishing never touches public pages.

## Functional requirements

1. Dispatch flow: `SCHEDULER` fires at `run_at` → parent job enqueues one child message per target (network × account) onto `POST_QUEUE` → per-network consumers publish via that network's API using short-lived leased credentials (opaque lease ids in messages; consumers resolve via the vault module — tokens never serialized into queues). Request handlers never `await` a publish; the schedule call returns once the parent job is queued.
2. Per-target status machine (closed states): `queued|sending|published|failed|retrying|cancelled|skipped_dead_connection`. Parent post aggregates to `sending|partial|sent|failed` from children; the "N of 7 published" counter derives live from children — stored as a computed view, never a hand-incremented column that can drift.
3. Polling + callbacks: per-network status confirmation via that network's status/media-id polling (backoff schedule, deadline per target after which `failed` with `timeout` reason); provider webhooks where offered, verified per the standard webhook discipline. Pollers are idempotent (provider-side duplicate posts prevented via idempotency keys where supported, client-side dedupe record otherwise — see §5).
4. Retry: per-target manual retry (creator, from the status view) + automatic retry for transient classes (rate-limit, 5xx) with capped attempts and exponential backoff; permanent classes (auth, policy/takedown, invalid media) fail immediately with the provider's reason surfaced in plain language + fix guidance (re-connect, trim caption, replace media).
5. Exactly-once intent: dedupe record per `(post_id, network, account_id)` — a retried or redelivered queue message never creates a second provider-side post where the API offers idempotency keys; where it does not, retry requires explicit creator confirmation after an ambiguous-timeout (`unknown — confirm before repost` state) so automation never double-posts a launch video.
6. Outage handling: per-network global pause switch (ops) stops dispatch for that leg with affected posts marked `queued_behind_outage` + creator notice; resume replays in original order. Poison-message quarantine after N redeliveries with ops alert; queue lag/depth dashboards page before creators notice.

## Data / API touches

- New tables: `post_targets{post_id, network, account_id, status, provider_ref?, attempts, last_error, idempotency_key}` (unique `(post_id, network, account_id)`; status transitions audited) + `publish_events{target_id, event, at, detail}` (append-only trace feeding the status view). Queue messages carry `{post_id, target_id, lease_id}` only. All PG via `worker/db/*`; RLS owner-scoped reads.
- Endpoints: `GET /api/v1/posts/:id/status` (live N-of-7 + per-target rows, owner-scoped, short-pollable), `POST /api/v1/posts/targets/:id/retry` (eligibility-checked), ops `POST /api/v1/ops/publish/:network/{pause,resume}` + poison replay (audited, dual-control on mass-replay).
- Never in this path: token material in messages/logs, `await`ed provider calls in request handlers, direct PG writes from pollers (all via queue-mediated consumers).

## Acceptance criteria

- Honesty drill: fixture run with 5 success + 1 fail + 1 timeout shows exactly "5 of 7 published" with the two legs named, reasoned, and individually retryable.
- Idempotency drill: redelivered queue message + ambiguous-timeout retry flow produce exactly one provider-side post (or an explicit confirm gate where the API cannot guarantee it).
- Retry-class drill: rate-limit failure auto-retries to success; revoked-token failure lands `failed` with re-connect guidance and no futile retries.
- Outage drill: network pause halts only that leg; resume replays in order; poison message quarantines with ops page after N redeliveries.
- Load drill: full 7-target fan-out p95 dispatch-to-first-status within budget; queue depth pages before creator-visible staleness.

## Out of scope

- Connection establishment and token storage (see `accounts/social-connections.md`); composer, media validation, calendar, quotas (see `composer/compose-schedule.md`).
- Comment/DM automations, broadcast campaigns, agency publishing (separate P4 scopes); provider API review processes (external).

## Open questions

- Q1: Ambiguous-timeout UX — creator confirm-gate per target, or platform-side provider-status reconciliation first (poll-then-ask)?
- Q2: Per-network dispatch deadlines (how long before `timeout`) — uniform (e.g. 15 min) or per-network budgets from observed API latency?
