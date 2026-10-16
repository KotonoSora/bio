# Automate Triggers - Database Schema Design (feature 44)

Phase 2 TL · `publish_db` migration `V4__automate_triggers.sql`. BA sources: `docs/01-requirements-ba/44-automate-triggers/`.

## Flyway V4 (`V4__automate_triggers.sql`)

```sql
CREATE TABLE automations (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  profile_id TEXT NOT NULL,
  account_id TEXT NOT NULL REFERENCES social_accounts(id),
  kind TEXT NOT NULL CHECK (kind IN ('comment')),
  match_type TEXT NOT NULL CHECK (match_type IN ('keyword','any_comment')),
  keywords TEXT[] NOT NULL DEFAULT '{}',
  media_scope TEXT[] NOT NULL DEFAULT '{}',  -- empty = account-wide
  delay_seconds INT NOT NULL DEFAULT 60 CHECK (delay_seconds >= 60),
  timezone TEXT NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT true,
  paused_reason TEXT CHECK (paused_reason IN ('owner','connection','burst_abort','review','downgrade')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_au_keywords CHECK (match_type <> 'keyword' OR array_length(keywords, 1) >= 1)
);
CREATE INDEX idx_au_user ON automations (user_id, enabled);
CREATE INDEX idx_au_account ON automations (account_id);

CREATE TABLE trigger_log (
  id TEXT PRIMARY KEY,
  automation_id TEXT NOT NULL REFERENCES automations(id),
  comment_ref TEXT NOT NULL,
  media_id TEXT,
  decision TEXT NOT NULL CHECK (decision IN ('matched','no_match','self_comment','blocklisted','collision_held')),
  detail JSONB,
  observed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uniq_tl_comment UNIQUE (automation_id, comment_ref)
);
CREATE INDEX idx_tl_recent ON trigger_log (automation_id, observed_at DESC);

CREATE TABLE dm_templates (
  id TEXT PRIMARY KEY,
  automation_id TEXT NOT NULL REFERENCES automations(id),
  reply_template TEXT NOT NULL,
  dm_template TEXT NOT NULL,
  link_url TEXT NOT NULL,
  checks JSONB NOT NULL,  -- checker verdict + version
  review_state TEXT NOT NULL DEFAULT 'approved'
    CHECK (review_state IN ('approved','pending_review','rejected')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_dt_automation ON dm_templates (automation_id);
CREATE INDEX idx_dt_review ON dm_templates (review_state) WHERE review_state = 'pending_review';

CREATE TABLE send_intents (
  id TEXT PRIMARY KEY,
  automation_id TEXT NOT NULL REFERENCES automations(id),
  contact_id TEXT REFERENCES automation_contacts(id),
  comment_ref TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'matched'
    CHECK (status IN ('matched','held_quota','held_quiet','held_cap','sending','replied',
                      'dm_sent','failed','skipped_suppressed','window_lapsed','cancelled')),
  public_reply_ref TEXT,
  dm_ref TEXT,
  attempts INT NOT NULL DEFAULT 0 CHECK (attempts >= 0),
  error_class TEXT CHECK (error_class IN ('transient','permanent','window','unknown')),
  last_error TEXT,
  matched_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uniq_si_comment UNIQUE (automation_id, comment_ref),
  CONSTRAINT chk_si_dm CHECK (status <> 'dm_sent'
    OR (public_reply_ref IS NOT NULL AND dm_ref IS NOT NULL))
);
CREATE INDEX idx_si_due ON send_intents (status, matched_at)
  WHERE status IN ('matched','held_quota','held_quiet','held_cap');
CREATE INDEX idx_si_automation ON send_intents (automation_id, status);
```

## RLS and grants

```sql
ALTER TABLE automations ENABLE ROW LEVEL SECURITY;
CREATE POLICY au_owner ON automations FOR SELECT TO app_role
  USING (user_id = current_setting('app.user_id', true));
CREATE POLICY au_svc ON automations FOR ALL TO app_role
  USING (current_setting('app.context', true) IN ('service','ops'))
  WITH CHECK (current_setting('app.context', true) IN ('service','ops'));
-- trigger_log / dm_templates / send_intents: owner-via-parent EXISTS policies + service/ops ALL
GRANT SELECT, INSERT, UPDATE ON automations, trigger_log, dm_templates, send_intents TO app_role;
```

## Semantics

- Intake (D-1/D-10): `POST /webhooks/instagram` verifies the provider scheme over raw bytes → anti-replay persist-first → XADD to `automate.watch` → `200` after durable handling; duplicate deliveries no-op with the original outcome.
- Match (D-4): the watcher evaluates the first observed version; normalization is case-fold + trim only; scope = media list or account-wide; self/blocklist skip; first-match by rule creation order among collision-checked active rules; `uniq_tl_comment` gives one decision row per (automation, comment); matched → intent `matched` (`uniq_si_comment`) + delay clock.
- Delivery (D-5): due intents selected `FOR UPDATE SKIP LOCKED` (≤ 200/tick); gates in fixed order; holds set the state and a re-evaluation tick; pass → `sending` → public reply → DM (single rendered link) → `replied` → `dm_sent` (+ capture via 43, contact_id backfilled); failures classify; window lapse → `window_lapsed` terminal.
- Caps (D-6): fire counter per automation+media+rolling-hour; overflow holds `held_cap` and releases in order; pacing bucket per account composes with caps; Redis loss rebuilds counters from `send_intents` (`dm_sent` timestamps) — enforcement never silently disappears.
- Burst-abort (D-7): monitor reads intent outcomes; trip flips `enabled=false` + `paused_reason='burst_abort'` + event + page; reinstate (ops, reason) flips back and resumes held intents in order.
- Quiet (D-8): `held_quiet` when creator-local time ∈ [22:00, 08:00); releases at the boundary ±60 s in matched order.
- Templates (D-9): checker verdict stored in `checks`; edits re-run checks; reports set `pending_review` + automation pause; decision approve/reject audited; rejected templates cannot send (delivery refuses).
- Rule quotas + collision checks run in the save transaction against active rules (server-side, bypass-proof); downgrade pauses newest-first (`paused_reason='downgrade'`).
- No comment bodies anywhere: `trigger_log.detail` holds decisions + refs only; zero token material exists in this feature.

## Drills

| Drill | Asserts |
|---|---|
| Fresh apply | V4 after V3; four tables + seven indexes + RLS; no other service touched |
| Match determinism | Fixture comments produce identical decisions across runs; fuzzy/typo inputs never match |
| One intent per comment | Concurrent duplicate webhooks → one trigger-log row, one intent; delete-with-pending cancels |
| Gate order | Fixtures tripping multiple gates hold on the first tripped state; release re-evaluates; zero bypass paths |
| Cap rebuild | Redis wipe mid-storm → counters rebuilt from intents; caps never exceeded |
| Burst-abort timing | Trip ≤ 60 s after breach; reinstate resumes in order |
| Window terminal | Lapse → `window_lapsed`; zero retries exist (grep + fixtures) |
| Template checks | Zero/two links, http, non-allowlisted destinations refused; stealth classes refused; report → pause-until-decided |
| RLS probes | Cross-creator rules/log/intents read zero rows; ops sees health + review queue only |
