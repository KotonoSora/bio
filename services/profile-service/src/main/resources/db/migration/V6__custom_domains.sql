-- profile-service feature 04 extension (custom domains).
-- Sources: docs/02-architecture-techlead/04-custom-domains/02-database-schema-design.md.
-- The feature spec numbered this file V3 (profile_db had V1-V2 then); the E0
-- scaffold already owns V1-V5 (profiles, internal read scope, RLS policies,
-- account deletion). It lands as V6 with identical content.
-- DEVIATION CARRIED (numbering only).

-- Binding lifecycle rows (BR-01/BR-02/BR-03/BR-08). Serves only in `verified`
-- state; the unique hostname is the takeover guard (S-3).
CREATE TABLE IF NOT EXISTS custom_domains (
    id                  TEXT PRIMARY KEY,
    hostname            TEXT NOT NULL UNIQUE,   -- normalized lowercase punycode; <= 253
    display_host        TEXT NOT NULL,          -- original (unicode) form for UI
    profile_id          TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    user_id             TEXT NOT NULL,
    status              TEXT NOT NULL CHECK (status IN ('pending','verified','unverified','suspended')),
    challenge_hash      TEXT NOT NULL,          -- sha256(token); token shown once, never stored
    challenge_issued_at TIMESTAMPTZ NOT NULL,   -- 7-day window; round counter implied by reissue
    attempt_round       INT NOT NULL DEFAULT 1,
    verified_at         TIMESTAMPTZ,
    last_recheck_at     TIMESTAMPTZ,
    last_recheck_ok     BOOLEAN,
    suspended_at        TIMESTAMPTZ,            -- lapse or abuse suspension
    suspension_reason   TEXT,                   -- DEVIATION FLAGGED (DDL addition): distinguish lapse holds
                                                -- (inside the 30-day restore path, BR-07) from abuse-review
                                                -- holds (recheck never restores them, S-7) - the serving gate
                                                -- (status=verified) is untouched; `entitlement_lapsed` |
                                                -- `abuse_phishing_review` | `denylist_hit`.
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Denylist rows (Q-01): exact hosts or suffix patterns (`*.brand.com`),
-- re-evaluated at add AND on every recheck. Maintained via the ops route.
CREATE TABLE IF NOT EXISTS domain_denylist (
    id         TEXT PRIMARY KEY,
    hostname   TEXT NOT NULL UNIQUE,   -- exact host or suffix pattern, normalized
    reason     TEXT NOT NULL,          -- platform | idp | lookalike | abuse
    added_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_domains_sweep  ON custom_domains (status, last_recheck_at);  -- worker sweep
CREATE INDEX IF NOT EXISTS idx_domains_owner  ON custom_domains (user_id);

ALTER TABLE custom_domains ENABLE ROW LEVEL SECURITY;
ALTER TABLE custom_domains FORCE ROW LEVEL SECURITY;

-- Owner predicate for all owner CRUD (BR-11: owner-scoped; foreign 403,
-- unknown 404 server-side).
DROP POLICY IF EXISTS owner_isolation ON custom_domains;
CREATE POLICY owner_isolation ON custom_domains FOR ALL TO profile_app
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));

-- Mesh/worker scope (feature 04 D-2/D-3): resolve lookup by hostname, worker
-- sweeps, reclaim transfers, and the participant purge run under the svc
-- context with explicit predicates - the context alone is never an
-- authorization (baseline 08).
DROP POLICY IF EXISTS svc_service_scope ON custom_domains;
CREATE POLICY svc_service_scope ON custom_domains FOR SELECT TO profile_app
  USING (current_setting('app.context', true) = 'svc');
DROP POLICY IF EXISTS svc_transfer_scope ON custom_domains;
CREATE POLICY svc_transfer_scope ON custom_domains FOR UPDATE TO profile_app
  USING (current_setting('app.context', true) = 'svc')
  WITH CHECK (current_setting('app.context', true) = 'svc');
DROP POLICY IF EXISTS svc_release_scope ON custom_domains;
CREATE POLICY svc_release_scope ON custom_domains FOR DELETE TO profile_app
  USING (current_setting('app.context', true) = 'svc');

-- Audit appends for domain actions ride the owner predicate (V4 policy) and
-- the svc/ops variants already granted there.

ALTER TABLE domain_denylist ENABLE ROW LEVEL SECURITY;
ALTER TABLE domain_denylist FORCE ROW LEVEL SECURITY;
-- Namespace-only data (no PII): the add/recheck reads it in any app context.
DROP POLICY IF EXISTS denylist_read ON domain_denylist;
CREATE POLICY denylist_read ON domain_denylist FOR SELECT TO profile_app USING (true);
-- Ops-maintained list: writes only via the audited ops route (svc context).
DROP POLICY IF EXISTS denylist_write ON domain_denylist;
CREATE POLICY denylist_write ON domain_denylist FOR ALL TO profile_app
  USING (current_setting('app.context', true) = 'svc')
  WITH CHECK (current_setting('app.context', true) = 'svc');

-- Q-01 maintained list seed rows: platform-owned hosts, identity-provider
-- domains, classic lookalikes/brand patterns. Ops extends via
-- POST /internal/v1/domains/denylist (audited); history is reconstructible.
-- The insert runs as the migrator (table owner) while FORCE RLS is lifted for
-- exactly this statement: no app-role policy can write seed data, and the
-- policies above stay unchanged for every runtime connection.
ALTER TABLE domain_denylist NO FORCE ROW LEVEL SECURITY;
INSERT INTO domain_denylist (id, hostname, reason) VALUES
    ('dnl_platform_apex',   'rb.build',                  'platform'),
    ('dnl_platform_wild',   '*.rb.build',                'platform'),
    ('dnl_platform_api',    'api.rb.build',              'platform'),
    ('dnl_idp_google',      'accounts.google.com',       'idp'),
    ('dnl_idp_google_wild', '*.accounts.google.com',     'idp'),
    ('dnl_idp_apple',       'appleid.com',               'idp'),
    ('dnl_idp_apple_wild',  '*.appleid.com',             'idp'),
    ('dnl_idp_facebook',    'facebook.com',              'idp'),
    ('dnl_idp_facebook_w',  '*.facebook.com',            'idp'),
    ('dnl_idp_linkedin',    'linkedin.com',              'idp'),
    ('dnl_idp_linkedin_w',  '*.linkedin.com',            'idp'),
    ('dnl_idp_msft',        'login.microsoftonline.com', 'idp'),
    ('dnl_look_g00gle',     'g00gle.com',                'lookalike'),
    ('dnl_look_g00gle_w',   '*.g00gle.com',              'lookalike'),
    ('dnl_look_paypa1',     'paypa1.com',                'lookalike'),
    ('dnl_look_paypa1_w',   '*.paypa1.com',              'lookalike'),
    ('dnl_look_faceb00k',   'faceb00k.com',              'lookalike'),
    ('dnl_look_arpple',     'arpple.com',                'lookalike')
ON CONFLICT (hostname) DO NOTHING;
ALTER TABLE domain_denylist FORCE ROW LEVEL SECURITY;
