-- profile-service feature 02 extension (account deletion).
-- Sources: docs/02-architecture-techlead/02-account-deletion/02-database-schema-design.md,
-- docs/02-architecture-techlead/06-public-profile/02-database-schema-design.md
-- ("Feature 02 extension"). The feature-06 spec numbered the tombstone V2, but the
-- E0 scaffold already owns V2-V4 (profiles, internal read scope, RLS policies);
-- it lands as V5 with identical content. DEVIATION CARRIED (numbering only).

-- Permanent retirement tombstones: inserted by the purge handler in the same
-- transaction as the profile delete; the PK makes reservation permanent
-- (re-use of a retired name fails as username_retired, BR-07/AC-05).
CREATE TABLE IF NOT EXISTS retired_usernames (
    username   CITEXT PRIMARY KEY,
    retired_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE retired_usernames ENABLE ROW LEVEL SECURITY;
ALTER TABLE retired_usernames FORCE ROW LEVEL SECURITY;
-- Namespace data only (no per-user rows, no PII): availability truth for every reader.
DROP POLICY IF EXISTS retired_read ON retired_usernames;
CREATE POLICY retired_read ON retired_usernames FOR SELECT TO profile_app USING (true);
-- Only the feature-02 purge handler writes tombstones (server-set svc scope).
DROP POLICY IF EXISTS retired_write ON retired_usernames;
CREATE POLICY retired_write ON retired_usernames FOR INSERT TO profile_app
  WITH CHECK (current_setting('app.context', true) = 'svc');

-- Purge deletes profiles under the svc scope with an explicit user predicate;
-- the owner path (owner_isolation) is unchanged.
DROP POLICY IF EXISTS svc_purge_scope ON profiles;
CREATE POLICY svc_purge_scope ON profiles FOR DELETE TO profile_app
  USING (current_setting('app.context', true) = 'svc');

-- Purge audit rides the svc scope (actor/target/runId only, no PII). Owner append unchanged.
DROP POLICY IF EXISTS audit_append ON audit_log;
CREATE POLICY audit_append ON audit_log FOR INSERT TO profile_app
  WITH CHECK (user_id = current_setting('app.user_id', true)
             OR current_setting('app.context', true) IN ('svc', 'ops'));
