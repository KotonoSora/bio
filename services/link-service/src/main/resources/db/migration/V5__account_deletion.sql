-- link-service feature 02 extension (account deletion purge participant).
-- Sources: docs/02-architecture-techlead/02-account-deletion/03-api-contracts-and-payloads.md
-- (participant table), docs/02-architecture-techlead/29-short-links/02-database-schema-design.md.
-- The feature-29 spec numbers its migration independently; the E0 scaffold owns
-- V1/V2/V4 here, so the purge scope lands as V5. DEVIATION CARRIED (numbering only).

-- Purge reads rows (slug list for cache purge) then deletes them under the
-- server-set svc scope, always with an explicit user predicate (NFR-09).
DROP POLICY IF EXISTS svc_read_scope ON short_links;
CREATE POLICY svc_read_scope ON short_links FOR SELECT TO link_app
  USING (current_setting('app.context', true) = 'svc');
DROP POLICY IF EXISTS svc_purge_scope ON short_links;
CREATE POLICY svc_purge_scope ON short_links FOR DELETE TO link_app
  USING (current_setting('app.context', true) = 'svc');

-- Purge audit rides the svc scope (actor/target/runId only, no PII). Owner append unchanged.
DROP POLICY IF EXISTS audit_append ON audit_log;
CREATE POLICY audit_append ON audit_log FOR INSERT TO link_app
  WITH CHECK (user_id = current_setting('app.user_id', true)
             OR current_setting('app.context', true) IN ('svc', 'ops'));
