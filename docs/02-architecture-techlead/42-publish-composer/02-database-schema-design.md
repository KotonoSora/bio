# Publish Composer - Database Schema Design (feature 42)

Phase 2 TL · `publish_db` migration `V2__publish_composer.sql`. BA sources: `docs/01-requirements-ba/42-publish-composer/`.

## Flyway V2 (`V2__publish_composer.sql`)

```sql
CREATE TABLE scheduled_posts (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  profile_id TEXT NOT NULL,
  video_key TEXT NOT NULL,
  thumb_key TEXT,
  caption_json JSONB NOT NULL,
  timezone TEXT NOT NULL,
  run_at TIMESTAMPTZ,
  status TEXT NOT NULL DEFAULT 'draft'
    CHECK (status IN ('draft','scheduled','sending','partial','sent','failed','cancelled')),
  late BOOLEAN NOT NULL DEFAULT false,
  late_at TIMESTAMPTZ,
  revision INT NOT NULL DEFAULT 1,
  quota_counted_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_sp_scheduled CHECK (status = 'draft' OR run_at IS NOT NULL),
  CONSTRAINT chk_sp_late CHECK (late = (late_at IS NOT NULL))
);
CREATE INDEX idx_sp_user ON scheduled_posts (user_id, run_at DESC);
CREATE INDEX idx_sp_due ON scheduled_posts (run_at) WHERE status = 'scheduled';

CREATE TABLE post_targets (
  id TEXT PRIMARY KEY,
  post_id TEXT NOT NULL REFERENCES scheduled_posts(id),
  network TEXT NOT NULL,
  account_id TEXT NOT NULL REFERENCES social_accounts(id),
  status TEXT NOT NULL DEFAULT 'queued'
    CHECK (status IN ('queued','sending','published','failed','retrying','cancelled','skipped_dead_connection')),
  provider_ref TEXT,
  attempts INT NOT NULL DEFAULT 0 CHECK (attempts >= 0),
  error_class TEXT CHECK (error_class IN ('transient','permanent','timeout','unknown')),
  last_error TEXT,
  confirm_required BOOLEAN NOT NULL DEFAULT false,
  deadline_at TIMESTAMPTZ,
  published_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uniq_pt_target UNIQUE (post_id, network, account_id),
  CONSTRAINT chk_pt_published CHECK (status <> 'published' OR (provider_ref IS NOT NULL AND published_at IS NOT NULL))
);
CREATE INDEX idx_pt_post ON post_targets (post_id);
CREATE INDEX idx_pt_ops ON post_targets (network, status);

CREATE TABLE publish_events (
  id TEXT PRIMARY KEY,
  target_id TEXT NOT NULL REFERENCES post_targets(id),
  event TEXT NOT NULL,
  detail JSONB,
  at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_pe_target ON publish_events (target_id, at);
```

## RLS and grants

```sql
ALTER TABLE scheduled_posts ENABLE ROW LEVEL SECURITY;
CREATE POLICY sp_owner ON scheduled_posts FOR SELECT TO app_role
  USING (user_id = current_setting('app.user_id', true));
CREATE POLICY sp_svc ON scheduled_posts FOR ALL TO app_role
  USING (current_setting('app.context', true) IN ('service','ops'))
  WITH CHECK (current_setting('app.context', true) IN ('service','ops'));
-- post_targets/publish_events: same owner-via-parent pattern (EXISTS join on scheduled_posts) + service/ops ALL
GRANT SELECT, INSERT, UPDATE ON scheduled_posts, post_targets, publish_events TO app_role;
```

## Semantics

- Dispatch (D-4): the scheduler selects `status='scheduled' AND run_at <= now()` `FOR UPDATE SKIP LOCKED` (≤ 200/tick), flips parent to `sending`, inserts no new rows (targets pre-created at schedule time), XADDs one child per target with `{postId, targetId, leaseRef}`, commits; a crash before XADD leaves a resumable state (next tick re-enqueues `sending` parents whose children are all `queued` — idempotent by `uniq_pt_target`).
- Consumers claim via group `publish-dispatch`; per-target guarded updates (`WHERE id=? AND status IN ('queued','retrying')`); provider call with idempotency key where offered; on success `published` + ref + `published_at`; failures classify (`transient` → `retrying` with backoff, `permanent` → `failed`, deadline passed → `failed` + `timeout` + `unknown` when unverifiable → `confirm_required` for manual retry).
- Rollup (D-5): parent state derives on read — all children terminal → `sent|partial|failed` (mixed = `partial`); any non-terminal → `sending`; `late` overlay set when a publish lands after `run_at + deadline` or the scheduler ran past due; never stored hand-incremented.
- Quota (D-9): `quota_counted_at` set on the first target success (single guarded update per post); the counting function: free = `COUNT(*) WHERE user_id AND quota_counted_at IS NOT NULL`; pro = count within the trailing 30-day window + velocity guardrails; gate, meter, and reconciliation call the same function.
- Lock (D-11): edits/cancel refuse when `now() >= run_at - lockMinutes`; pre-lock cancel flips parent + all `queued` children to `cancelled` in one transaction (no dispatch possible — scheduler only sees `scheduled`).
- Dead connections: `connection.dead.v1`/`connection.disconnected.v1` (41) consumers flip `queued` children to `skipped_dead_connection` + notice; dispatch never calls with dead credentials.
- Outage: `publish.network_pause` fresh-read per dispatch; paused legs hold children `queued` and the parent shows the hold; resume re-enqueues in `run_at` order.
- No tokens anywhere: messages carry ids + `leaseRef` (opaque, resolved in-process by 41's vault).

## Drills

| Drill | Asserts |
|---|---|
| Fresh apply | V2 after V1; three tables + six indexes + RLS; no other service touched |
| Rollup no-drift | Fixture outcomes recompute identically on every read; no stored counter exists (schema grep) |
| Lock boundary | Edit at `run_at − 5:01` allowed, at `run_at − 4:59` refused; pre-lock cancel produces zero dispatch rows |
| Idempotency | Redelivered stream message → one provider call result; `uniq_pt_target` blocks duplicates |
| Quota single function | grep: gate/meter/reconciliation call one SQL function; retried-once counts once |
| Message hygiene | Stream messages contain no token material (scan); `leaseRef` resolves only in-process |
| Pause fresh-read | Pause effect ≤ 5 s; resume order matches `run_at` |
| RLS probes | Cross-user posts/targets/events read zero rows; ops context sees ids + status only |
