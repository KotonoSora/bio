# Verification Followers Database Plan (verification_requests — table written once)

PLAN ref: P1a-2 trust extension + S-1/S-6. BA source: `docs/link-to-bio/profile/verification-followers.md`. Consumed by the verification backend plan (services are the only writers; splits next to it). Profile trust columns live in `docs/link-to-bio/profile/public-profile-database-plan.md` (single `CREATE TABLE`, never altered here). No migration chains — the SQL below is complete, applied once.

## Purpose

Give every developer the exact trust table in one place: what a verification request row stores, how it links to profiles, why evidence stays out of PG, and which policies guard reviewer actions. Exactly one `CREATE TABLE` for `verification_requests`; no ALTERs, no versions.

## Users

- Backend developer: runs this script once, maps `VerificationRequestEntity` 1:1.
- QA / reviewer: probes state transitions and RLS directly in `psql`.
- Ops: understands where evidence references resolve (blob keys, never inline).

## Functional requirements

1. Conventions (same as the profile database plan): `TEXT` ids, `TIMESTAMPTZ`, `user_id TEXT NOT NULL` + index + RLS plus app-level scoping, migration role owns DDL, `app_role` table grants.
2. Table `bio.verification_requests` — one row per badge application or revocation record. `id` (ulid PK); `profile_id` (FK → `bio.profiles(id)` `ON DELETE CASCADE` — deleting a profile purges its verification history with it, per retention); `user_id` (owner, for RLS without joining profiles); `status` (alphabet none|pending|verified|rejected|revoked mirrored from the profile row — the profile row is the read cache for SSR, this table is the audit trail of how it got there); `evidence_ref` (blob key only, e.g. `verification/{ulid}.pdf` — files never inline, never in PG); `reviewer` (ops actor id, NULL before decision); `decided_at`, `created_at`. Badge state itself is read from `profiles.verification_status`; counting rows here answers "show me the history", never "show the badge".
3. No status-transition constraint in SQL (alphabet only): legal transitions (none→pending→verified/rejected, verified→pending on rename, any→revoked by ops) are enforced in `VerificationService` where the rename hook and reviewer role live; the DB guarantees the alphabet, the service guarantees the path. Rationale: transitions need role + rename context the DB cannot see.
4. Indexes: `idx_verif_profile(profile_id)` (history per profile, ops queue per profile); `idx_verif_user(user_id)` (owner status reads). Both B-tree exact-match.
5. RLS: `v_owner` (all operations, owner-only) — creators see only their own applications (never another profile's evidence refs); ops decisions execute with a reviewer role that bypasses RLS through a dedicated, audited service path (every decide writes `audit_log` with actor + reason — the audit row, not RLS, is the ops accountability).
6. Relationships: requests → profile (hard FK, cascade); evidence_ref → blob object (logical, lifecycle-managed: purge with profile deletion per retention); reviewer → ops identity (logical, external directory).
7. Growth: rows per verified-aspiring profile are single-digit; no partitioning, no archiving in v1 — history is the audit feature.

## Data / API touches

- Full table, applied once (same baseline run, after the profile script):
```sql
CREATE TABLE bio.verification_requests (
  id TEXT PRIMARY KEY,
  profile_id TEXT NOT NULL REFERENCES bio.profiles(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL,
  status TEXT NOT NULL,
  evidence_ref TEXT,
  reviewer TEXT,
  decided_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_verif_req CHECK (status IN ('none','pending','verified','rejected','revoked'))
);
CREATE INDEX idx_verif_profile ON bio.verification_requests(profile_id);
CREATE INDEX idx_verif_user ON bio.verification_requests(user_id);
ALTER TABLE bio.verification_requests ENABLE ROW LEVEL SECURITY;
CREATE POLICY v_owner ON bio.verification_requests FOR ALL TO app_role USING (user_id = current_setting('app.user_id', true)) WITH CHECK (user_id = current_setting('app.user_id', true));
GRANT SELECT, INSERT, UPDATE, DELETE ON bio.verification_requests TO app_role;
```
- Consumed by: verification backend plan (request/status/decide flows; the only writers); ops review reads. SSR never reads this table (it reads the cached `profiles` badge columns).

## Acceptance criteria

- Fresh-apply drill: script runs clean after the profile script; `\d` shows FK, CHECK, both indexes, policy.
- Alphabet drill: 6th status value violates `chk_verif_req`.
- Cascade drill: delete a profile → its requests disappear (evidence blobs purged by the retention job, asserted in its test).
- RLS drill: foreign `app.user_id` sees zero rows; owner sees only own history.
- No-duplication drill: `grep -rn "CREATE TABLE bio.verification_requests" docs/` returns exactly this file.

## Out of scope

- Profile columns (profile database plan); review logic and follower totals (verification backend plan); review screens (verification frontend plan); OAuth vault and P4 automations (never here).

## Open questions

- Q1: Keep full request history forever, or cap rows per profile (e.g. latest 20)?
- Q2: Evidence retention after purge — blob TTL equal to row cascade, or longer legal hold?
