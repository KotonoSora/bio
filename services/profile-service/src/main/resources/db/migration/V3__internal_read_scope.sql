-- services/profile-service V3: mesh read scope for internal handoff reads
-- (feature 01 T-10, additive row in 06-public-profile API contracts).
-- Runs only when the server sets app.context='svc' (RlsUserHolder.asService);
-- the handler still applies an explicit user_id predicate - context alone is
-- never an authorization. Owner path (app.user_id) unchanged; both unset = deny.

DROP POLICY IF EXISTS svc_read_scope ON profiles;
CREATE POLICY svc_read_scope ON profiles FOR SELECT TO profile_app
  USING (current_setting('app.context', true) = 'svc');
