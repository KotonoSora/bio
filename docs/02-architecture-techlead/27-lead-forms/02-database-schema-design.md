# Lead Forms - Database Schema Design (feature 27)

Phase 2 TL · `profile_db` migration `V6__leads_baseline.sql` (chain: V1 baseline feature 06; V2 feature 02 tombstone; V3 feature 04 domains; V4 feature 07 verification; V5 feature 08 blocks — numbering fixed in this batch). BA sources: `docs/01-requirements-ba/27-lead-forms/` (BR-01..BR-16).

## DDL

```sql
CREATE TABLE profile.leads (
  id                   TEXT PRIMARY KEY,
  profile_id           TEXT NOT NULL,
  user_id              TEXT NOT NULL,
  email                CITEXT NOT NULL,
  name                 VARCHAR(80),
  phone                VARCHAR(20),
  note                 VARCHAR(500),
  source               TEXT NOT NULL,
  source_block_id      TEXT,
  consent_class        TEXT NOT NULL,
  consent_at           TIMESTAMPTZ NOT NULL,
  consent_copy_version TEXT NOT NULL,
  status               TEXT NOT NULL DEFAULT 'pending',
  disposable_flag      BOOLEAN NOT NULL DEFAULT false,
  unread               BOOLEAN NOT NULL DEFAULT true,
  created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_lead_email UNIQUE (profile_id, email),
  CONSTRAINT chk_lead_source CHECK (source IN ('form','checkout','import','ops','automation')),
  CONSTRAINT chk_lead_block CHECK (source <> 'form' OR source_block_id IS NOT NULL),
  CONSTRAINT chk_lead_consent CHECK (consent_class IN ('lifecycle','marketing')),
  CONSTRAINT chk_lead_status CHECK (status IN ('pending','active','unsubscribed','suppressed','bounced','complained'))
);
CREATE INDEX idx_leads_user  ON profile.leads (user_id, created_at DESC);
CREATE INDEX idx_leads_block ON profile.leads (source_block_id, created_at DESC);
CREATE INDEX idx_leads_form_feed ON profile.leads (profile_id, source_block_id, created_at DESC);

CREATE TABLE profile.lead_events (
  id          TEXT PRIMARY KEY,
  lead_id     TEXT NOT NULL,
  user_id     TEXT NOT NULL,
  event       TEXT NOT NULL,
  actor       TEXT NOT NULL,          -- visitor | creator:{userId} | system | provider
  detail_json JSONB,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_leadevent CHECK (event IN ('captured','resubmitted','confirmed','status_changed','note_added','tag_added','tag_removed','suppressed','reinvited','exported','read'))
);
CREATE INDEX idx_leadevents_lead ON profile.lead_events (lead_id, created_at);

CREATE TABLE profile.lead_outbox (
  id           TEXT PRIMARY KEY,
  user_id      TEXT NOT NULL,
  profile_id   TEXT NOT NULL,
  block_id     TEXT NOT NULL,
  payload_json JSONB NOT NULL,        -- validated, sanitized capture payload
  status       TEXT NOT NULL DEFAULT 'queued',
  attempts     INTEGER NOT NULL DEFAULT 0,
  next_try_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  done_at      TIMESTAMPTZ,
  CONSTRAINT chk_outbox_status CHECK (status IN ('queued','done','dropped'))
);
CREATE INDEX idx_outbox_due ON profile.lead_outbox (next_try_at) WHERE status = 'queued';

ALTER TABLE profile.leads       ENABLE ROW LEVEL SECURITY;
ALTER TABLE profile.lead_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE profile.lead_outbox ENABLE ROW LEVEL SECURITY;

CREATE POLICY lead_owner ON profile.leads FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY lead_svc ON profile.leads FOR ALL TO app_role
  USING (current_setting('app.context', true) = 'svc')
  WITH CHECK (current_setting('app.context', true) = 'svc');

CREATE POLICY levent_owner ON profile.lead_events FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY levent_svc ON profile.lead_events FOR ALL TO app_role
  USING (current_setting('app.context', true) = 'svc')
  WITH CHECK (current_setting('app.context', true) = 'svc');

CREATE POLICY outbox_svc ON profile.lead_outbox FOR ALL TO app_role
  USING (current_setting('app.context', true) = 'svc')
  WITH CHECK (current_setting('app.context', true) = 'svc');

GRANT SELECT, INSERT, UPDATE, DELETE ON profile.leads, profile.lead_events, profile.lead_outbox TO app_role;
```

## Notes

- Dedupe: `uq_lead_email` on `(profile_id, email)` (CITEXT, lowercase-normalized); the consumer upserts and coalesces identical `(block, email)` submissions within 60 s into the existing `captured` event; later submissions append `resubmitted`.
- Strictest-wins: the consumer and every writer compare the status rank (`suppressed > complained > bounced > unsubscribed > active > pending`); a write may only move rank up, never down; consent class may upgrade (checked submit) but never downgrade by omission.
- Consent birth: `consent_at` = server receive time; `consent_copy_version` = the block's stamped hash at submission time.
- `lead_events` is append-only (no UPDATE/DELETE paths in app code; insert-only policies practical note: owner policy is FOR ALL for DDL simplicity — app grants scoping; append-only enforced by code + review, alert on UPDATE/DELETE statements).
- Outbox rows carry resolved `user_id`/`profile_id` (the handler resolves the block → profile server-side); consumer marks `done`/`dropped`; queued rows older than 24 h drop with a metric.
- Disposable flag: curated domain list + role-prefix detection at consumer time; never blocks.
- Redis db 4 keys: `lead:cap:{blockId}:{ipHash}:{minute}` TTL 120 s; `lead:pause:{blockId}` TTL 15 min; `lead:confirmreq:{emailHash}` TTL 20 min.
- Search indexes (pg_trgm) arrive with feature 28's `V7` (search feature owns them).

## Verification drills

1. Fresh apply V5→V6 on PG `18.6`; `\d` shows constraints, partial index, policies.
2. Dedupe drill: same `(profile_id, email)` twice → one row; within 60 s no `resubmitted` event; after it, one.
3. Rank drill: suppressed + resubmit → status untouched; active + checked submit → consent upgraded, status untouched.
4. RLS drill: creator A sees zero of creator B's rows; svc context inserts outbox rows; owner context cannot read the outbox.
5. Outbox drill: poison payload retries with backoff then drops at 24 h with a metric.
6. Purge drill: user delete removes leads/events/outbox rows by `user_id`; counts returned.
