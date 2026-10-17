-- services/shop-service RLS policies (V4): owner isolation, deny-by-default when app.user_id unset.
-- Server-set request user only (handlers derive user_id from the JWT, never bodies).
-- FORCE: even table owners obey; migrator never serves traffic.

ALTER TABLE products ENABLE ROW LEVEL SECURITY;
ALTER TABLE products FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS owner_isolation ON products;
CREATE POLICY owner_isolation ON products FOR ALL TO shop_app
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));

ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS audit_append ON audit_log;
CREATE POLICY audit_append ON audit_log FOR INSERT TO shop_app
  WITH CHECK (user_id = current_setting('app.user_id', true));
DROP POLICY IF EXISTS audit_read_own ON audit_log;
CREATE POLICY audit_read_own ON audit_log FOR SELECT TO shop_app
  USING (user_id = current_setting('app.user_id', true));
-- No UPDATE/DELETE policy: audit rows are append-only, 24-month retention via the ops-owned purge job.
