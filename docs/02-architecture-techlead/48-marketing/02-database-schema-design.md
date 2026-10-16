# Marketing Consent and Broadcasts - Database Schema Design (feature 48) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/48-marketing/`. GATED: present state is **zero** — no marketing table exists in any database, and none may be added before the amendment (BR-01, NFR-02). This file designs the future `marketing_db` migration `V1__marketing.sql`; the amendment record starts the chain.

## Present state: zero

| Probe | Assertion |
|---|---|
| Schema diff | No `consents`, `suppressions`, `audience`, `campaigns`, or `campaign_events` table in any database |
| Migration chain | `marketing_db` does not exist; no `V*__marketing*.sql` file exists |
| Source grep | `EligibilityCheck`, `PreferenceCenter`, `CampaignService` absent from all source and bundles |
| Pipeline | `sendClass='marketing'` still refuses `marketing_not_accepted` |

## Future Flyway V1 (`V1__marketing.sql`, at amendment)

```sql
CREATE TABLE consents (
  id TEXT PRIMARY KEY,
  owner_key TEXT NOT NULL,          -- user/profile/lead scope; 'platform' for ops-captured
  class TEXT NOT NULL CHECK (class IN ('transactional','lifecycle','marketing')),
  status TEXT NOT NULL CHECK (status IN ('granted','withdrawn')),
  captured_at TIMESTAMPTZ NOT NULL,
  source TEXT NOT NULL,             -- form_block | checkout | import | ops | preference_center
  copy_version TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_consents_key ON consents (owner_key, class, captured_at DESC);
-- append-only: no UPDATE/DELETE grants

CREATE TABLE suppressions (
  address_hash TEXT PRIMARY KEY,    -- SHA-256(normalized email); no plaintext here
  reason TEXT NOT NULL CHECK (reason IN ('unsubscribed','hard_bounce','soft_bounce_strike','complaint','deletion','re_consent_pending')),
  at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audience (
  id TEXT PRIMARY KEY,
  email_norm TEXT NOT NULL,
  owner_key TEXT NOT NULL,          -- silo key (Q-04)
  status TEXT NOT NULL CHECK (status IN ('active','unsubscribed','suppressed','bounced','complained')),
  source TEXT NOT NULL,
  consent_class TEXT NOT NULL DEFAULT 'marketing',
  first_seen TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_engagement TIMESTAMPTZ,
  parked_at TIMESTAMPTZ,
  CONSTRAINT uniq_aud_owner_email UNIQUE (owner_key, email_norm)
);
CREATE INDEX idx_aud_status ON audience (owner_key, status);

CREATE TABLE lead_events (
  id TEXT PRIMARY KEY,
  audience_id TEXT NOT NULL REFERENCES audience(id),
  transition TEXT NOT NULL,         -- captured | unsubscribed | suppressed | bounced | complained | parked | unparked | re_consented
  actor TEXT NOT NULL,
  at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_le_audience ON lead_events (audience_id, at DESC);

CREATE TABLE campaigns (
  id TEXT PRIMARY KEY,
  subject TEXT NOT NULL,
  body_ref TEXT NOT NULL,           -- versioned sanitized template ref (R2)
  audience_snapshot_count INT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('draft','sending','sent','paused')),
  stats_json JSONB NOT NULL DEFAULT '{}',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE campaign_events (
  event_id TEXT PRIMARY KEY,        -- provider event id (idempotency)
  campaign_id TEXT NOT NULL REFERENCES campaigns(id),
  kind TEXT NOT NULL CHECK (kind IN ('delivered','open','click','bounce','complaint')),
  at TIMESTAMPTZ NOT NULL
);
```

## RLS and grants (at amendment)

```sql
-- consents: owner_key = app.user_id for own reads; support role reads for proof (audited); service ALL
-- audience/lead_events: owner-scoped reads; marketing role reads aggregates + active rows for sends
-- campaigns/campaign_events: marketing/ops roles only
GRANT SELECT, INSERT ON consents, lead_events, campaign_events TO app_role;  -- append-only
GRANT SELECT, INSERT, UPDATE, DELETE ON suppressions TO app_role;             -- service-managed
GRANT SELECT, INSERT, UPDATE ON audience, campaigns TO app_role;
```

## Semantics

- Eligibility: `EligibilityCheck(owner_key, class, address_hash)` → true only when the class is `marketing` and a `granted` consent is current, `suppressions` has no row, and `audience.status='active'` with no `parked_at`; `transactional` never gated; `lifecycle` skips suppression unless withdrawn/deleted.
- Re-consent (Q-02): explicit signed flow inserts a fresh `granted` row + `re_consented` event; suppression row deleted only on that path; attempts rate-limited 3/address/year.
- Hygiene (BR-08): webhook transitions write `lead_events` + update `audience.status` synchronously and upsert `suppressions` for hard bounce/complaint; parking job sets `parked_at` after 12 months without engagement; engagement clears it.
- Strictest-wins merge (BR-06/BR-07): ingest upserts never move a strict state to a looser one; conflicting rows resolve to `complained > suppressed > bounced > unsubscribed > active`.
- Stats (BR-11): `campaign_events` idempotent by provider event id; `campaigns.stats_json` is a derived aggregate, recomputable from the log.
- No address plaintext in `suppressions` or logs; `audience.email_norm` is the operational copy, RLS-scoped.

## Drills (at amendment)

| Drill | Asserts |
|---|---|
| Fresh apply | V1 creates six tables + indexes + RLS; append-only grants on consents/lead_events/campaign_events |
| Eligibility | Suppressed + stale grant → false; every sender path calls the single function (grep gate) |
| Strictest-wins | Import fixtures never loosen states; complaint terminal; re-capture no resurrection |
| Idempotency | Replayed webhook events leave stats unchanged; resumed dispatch never double-sends |
| Absence (live) | Until amendment: zero marketing tables in every schema diff (AC-01) |
