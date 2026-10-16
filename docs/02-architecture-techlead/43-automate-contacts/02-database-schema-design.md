# Automate Contacts - Database Schema Design (feature 43)

Phase 2 TL · `publish_db` migration `V3__automate_contacts.sql`. BA sources: `docs/01-requirements-ba/43-automate-contacts/`.

## Flyway V3 (`V3__automate_contacts.sql`)

```sql
CREATE TABLE automation_contacts (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  profile_id TEXT NOT NULL,
  network TEXT NOT NULL CHECK (network IN ('instagram')),
  network_user_ref TEXT NOT NULL,
  first_automation_id TEXT NOT NULL,
  first_keyword TEXT,
  source_media_id TEXT,
  consent_class TEXT NOT NULL DEFAULT 'transactional'
    CHECK (consent_class IN ('transactional','marketing')),
  consent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  status TEXT NOT NULL DEFAULT 'active'
    CHECK (status IN ('active','suppressed','blocked_platform','stale')),
  suppressed_at TIMESTAMPTZ,
  suppression_reason TEXT CHECK (suppression_reason IN
    ('optout_keyword','removal_center','platform_button','owner_action')),
  email TEXT,
  first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uniq_ac_identity UNIQUE (profile_id, network, network_user_ref),
  CONSTRAINT chk_ac_suppressed CHECK (status <> 'suppressed' OR suppressed_at IS NOT NULL)
);
CREATE INDEX idx_ac_user ON automation_contacts (user_id, status);
CREATE INDEX idx_ac_stale ON automation_contacts (last_seen_at) WHERE status = 'active';
CREATE INDEX idx_ac_kw ON automation_contacts (profile_id, first_keyword);

CREATE TABLE contact_events (
  id TEXT PRIMARY KEY,
  contact_id TEXT NOT NULL REFERENCES automation_contacts(id),
  event TEXT NOT NULL,
  detail JSONB,
  at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ce_contact ON contact_events (contact_id, at);

CREATE FUNCTION contacts_counted(p_profile TEXT, p_since TIMESTAMPTZ) RETURNS INT AS
  $$ SELECT COUNT(*)::INT FROM automation_contacts
     WHERE profile_id = p_profile AND first_seen_at >= p_since $$ LANGUAGE sql STABLE;
```

## RLS and grants

```sql
ALTER TABLE automation_contacts ENABLE ROW LEVEL SECURITY;
CREATE POLICY ac_owner ON automation_contacts FOR SELECT TO app_role
  USING (user_id = current_setting('app.user_id', true));
CREATE POLICY ac_svc ON automation_contacts FOR ALL TO app_role
  USING (current_setting('app.context', true) IN ('service','ops'))
  WITH CHECK (current_setting('app.context', true) IN ('service','ops'));
-- contact_events: owner-via-parent EXISTS policy + service/ops ALL
GRANT SELECT, INSERT, UPDATE ON automation_contacts, contact_events TO app_role;
GRANT EXECUTE ON FUNCTION contacts_counted(TEXT, TIMESTAMPTZ) TO app_role;
```

## Semantics

- Capture (D-1): inside the send-acceptance transaction — `INSERT ... ON CONFLICT (profile_id, network, network_user_ref) DO UPDATE SET last_seen_at = now()`; conflict appends a `contact_events` row (`comment_seen`) instead of duplicating; emits `contact.captured.v1` only on the insert branch. Failed sends write nothing.
- Eligibility (D-1): `EligibilityCheck(profileId, ref, neededClass)` = row exists AND `status='active'` AND (`neededClass = 'transactional'` OR `consent_class='marketing'`); indexed single lookup ≤ 50 ms; used by 44's pre-send gate and by export-for-sending; no other gate exists (grep gate).
- Suppression (D-4): one method `suppress(contactId, reason)` flips status + `suppressed_at` + reason + event + post-commit `contact.suppressed.v1`; all three mechanisms call it; idempotent (repeat = no-op with 200).
- Quota (D-3): gate = `contacts_counted(profile, window_start) < ladder`; window = rolling year (free) / rolling month (pro); `first_seen_at` is the counting anchor so a repeat recipient never double-counts; meter and reconciliation call the same function.
- Export (D-5): CSV job selects owner-scoped rows (filter-aware), writes to MinIO, records audit + expiring link; handoff iterates eligible rows (email NOT NULL AND status='active') calling 27's internal write idempotently; skipped rows counted with reasons; consent values copied verbatim.
- Hygiene (D-7): daily — platform-block signals prune; duplicate identities merge (strictest class, earliest provenance, events concatenated); `last_seen_at < now() - staleContactMonths` parks to `stale`; a fresh comment capture on a stale row reactivates (`status='active'`, new provenance event).
- Purge (02's duty): profile deletion removes rows + events; export files expire ≤ 24 h; handoff refs orphan harmlessly.
- No Redis keys beyond `pub:rl:*`; no token material exists in this feature.

## Drills

| Drill | Asserts |
|---|---|
| Fresh apply | V3 after V2; two tables + four indexes + function + RLS | 
| Single gate | grep: only `EligibilityCheck` consults status/consent for sends; 44's consumer imports it |
| Counting function | gate/meter/reconciliation agree on fixtures; repeat recipient counts once; period boundaries minute-exact |
| Suppression unity | all three mechanisms land identical transitions; idempotent repeats; ≤ 5 s exclusion |
| Merge rules | duplicates collapse with strictest consent + earliest provenance; events preserved |
| Stale boundary | 12-month-idle parks; fresh comment reactivates; parked never send-eligible |
| Consent immutability | no UPDATE path sets `consent_class='marketing'`; grep + fixtures |
| RLS probes | cross-creator reads zero rows; ops context cannot select `email`/`network_user_ref` raw (aggregate-only grant path) |
