# Verification Badge and Follower Display - Database Schema Design (feature 07)

Phase 2 TL · Same database `profile_db` (PostgreSQL 18.6), owned exclusively by `profile-service`. Implements the `BR-*` rules of `docs/01-requirements-ba/07-verification-and-followers/02-user-stories-and-business-rules.md`. Trust columns on `profile.profiles` already exist in `V1__profile_baseline.sql` (`docs/02-architecture-techlead/06-public-profile/02-database-schema-design.md`) - this feature adds two tables and never alters V1.

## Migration

`V4__verification_baseline.sql` applies on boot after V3 (canonical chain: V1 baseline, V2 feature 02 tombstone, V3 feature 04 domains); no ALTER chains, no redefinitions. Later migrations follow the same `V_n` discipline.

## DDL

```sql
CREATE TABLE profile.verification_requests (
  id TEXT PRIMARY KEY,
  profile_id TEXT NOT NULL REFERENCES profile.profiles(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL,
  status TEXT NOT NULL,
  evidence_ref TEXT,
  reviewer TEXT,
  decided_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_verif_req CHECK (status IN ('none','pending','verified','rejected','revoked'))
);
CREATE INDEX idx_verif_profile ON profile.verification_requests (profile_id);
CREATE INDEX idx_verif_user ON profile.verification_requests (user_id);

CREATE TABLE profile.follower_count_history (
  id TEXT PRIMARY KEY,
  profile_id TEXT NOT NULL REFERENCES profile.profiles(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL,
  reason TEXT NOT NULL,
  total BIGINT NOT NULL,
  breakdown_json JSONB NOT NULL DEFAULT '{}',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_follower_reason CHECK (reason IN ('self_reported','auto_refresh')),
  CONSTRAINT chk_follower_total CHECK (total >= 0)
);
CREATE INDEX idx_follower_hist_profile ON profile.follower_count_history (profile_id);

ALTER TABLE profile.verification_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE profile.follower_count_history ENABLE ROW LEVEL SECURITY;
CREATE POLICY v_owner ON profile.verification_requests FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY f_owner ON profile.follower_count_history FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));

GRANT SELECT, INSERT, UPDATE, DELETE ON profile.verification_requests, profile.follower_count_history TO app_role;
```

## Design notes

| Element | Rule | Why |
|---|---|---|
| `verification_requests.profile_id` | FK to `profile.profiles`, `ON DELETE CASCADE` | deleting a profile purges its verification history; evidence blobs follow via retention job |
| `status` alphabet | mirrored 5-state machine | the profile row's `verification_status` is the read cache for SSR; this table is how it got there |
| Transition legality | service-enforced (`VerificationService`), DB enforces alphabet only | transitions need role + rename context the DB cannot see |
| `evidence_ref` | blob key only (`verification/{ulid}.ext`) | evidence never inline, never in PG |
| Ops decisions | execute via a dedicated audited service path; `reviewer` + audit row with actor + reason | ops accountability lives in the audit log, not in RLS |
| `follower_count_history.reason` | `self_reported` / `auto_refresh` | versioning for count-integrity review (BR-06) |
| Caps | `total >= 0` at DB; 100,000,000 cap enforced in service | DB guards sanity, service owns the business cap |

Indexes: `idx_verif_profile` serves history + ops queue; `idx_verif_user` serves owner status reads; `idx_follower_hist_profile` serves per-profile history. No further indexes until a query proves itself.

## Growth and operations

- `verification_requests`: single-digit rows per profile; history is the audit feature (no archiving in v1; cap reviewed per BA Q-04).
- `follower_count_history`: one row per edit/refresh; refresh cadence bounds growth (~365/prof/network/year worst case at daily; actual cadence 24 h -> review at 100M rows).
- Backups: platform PITR; restore drill quarterly. Cascades asserted in drills below.

## Verification drills

1. Fresh apply: `flyway migrate` clean (V1 then V2) on an empty 18.6 container; `\d` shows FK, CHECKs, indexes, policies on both tables.
2. Alphabet drill: a 6th `status` value violates `chk_verif_req`; negative total violates `chk_follower_total`.
3. Cascade drill: delete a profile -> both tables' rows disappear; the retention job purges evidence blobs (asserted in its test).
4. RLS drill: foreign `app.user_id` sees zero rows on both tables; owner sees only own history.
5. No-duplication drill: `grep -rn "CREATE TABLE profile.verification_requests" docs/` and `... follower_count_history` return exactly this file.
