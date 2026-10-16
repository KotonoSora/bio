# Custom Domains - Database Schema Design (feature 04)

Phase 2 TL · `profile_db` migration `V3__custom_domains.sql` (V1 baseline feature 06; V2 feature 02's `retired_usernames`). BA sources: `docs/01-requirements-ba/04-custom-domains/` (BR-01, BR-02, BR-03, BR-08).

## DDL

```sql
CREATE TABLE profile.custom_domains (
  id                  TEXT PRIMARY KEY,
  hostname            TEXT NOT NULL UNIQUE,   -- normalized lowercase punycode; <= 253
  display_host        TEXT NOT NULL,          -- original (unicode) form for UI
  profile_id          TEXT NOT NULL REFERENCES profile.profiles(id) ON DELETE CASCADE,
  user_id             TEXT NOT NULL,
  status              TEXT NOT NULL CHECK (status IN ('pending','verified','unverified','suspended')),
  challenge_hash      TEXT NOT NULL,          -- sha256(token); token shown once, never stored
  challenge_issued_at TIMESTAMPTZ NOT NULL,   -- 7-day window; round counter implied by reissue
  attempt_round       INT NOT NULL DEFAULT 1,
  verified_at         TIMESTAMPTZ,
  last_recheck_at     TIMESTAMPTZ,
  last_recheck_ok     BOOLEAN,
  suspended_at        TIMESTAMPTZ,            -- lapse or abuse suspension
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE profile.domain_denylist (
  id         TEXT PRIMARY KEY,
  hostname   TEXT NOT NULL UNIQUE,   -- exact host or suffix pattern (for example `*.brand.com`), normalized
  reason     TEXT NOT NULL,          -- platform | idp | lookalike | abuse
  added_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_domains_sweep  ON profile.custom_domains (status, last_recheck_at);   -- worker sweep
CREATE INDEX idx_domains_owner  ON profile.custom_domains (user_id);
```

## RLS and access paths

- `custom_domains`: owner predicate `user_id = app.user_id` for all CRUD; `app.context = 'svc'` for the resolve read (lookup by `hostname`, returns status + profile id only) and the participant purge (delete by `user_id`); ops scope for reclaim/denylist routes (audited).
- `domain_denylist`: ops-only writes via route; resolve/recheck read with `svc` context; no browser path.
- Concurrent takeover safety: the `hostname UNIQUE` constraint is the transfer guard; reclaim runs in one transaction (old row deleted or updated, audit row written) - no split state.

## Transitions (enforced in service code, proven by drill)

| From | To | Trigger |
|---|---|---|
| pending | verified | TXT match (on-demand or sweep) + TLS queued |
| pending | pending | window expiry -> regenerate path |
| verified | unverified | recheck: TXT missing/moved; denylist drift re-evaluates separately (-> suspended) |
| verified | suspended | entitlement lapse; phishing report |
| suspended | verified | re-upgrade with `last_recheck_ok` inside the 30-day hold |
| suspended | unverified | beyond the 30-day hold, or failing control |
| unverified | verified | full re-verify (fresh round) |
| unverified | deleted | failing control for 14 days (auto-release) |
| any | deleted | owner removal (immediate, serving halts) |
| any | deleted | feature 02 purge (account deletion) |

## Verification drills

1. Fresh apply: `V3` clean on 18.6; unique constraint, CHECK, both indexes, RLS policies present.
2. Takeover constraint: inserting a second row for the same hostname fails; reclaim transfer leaves exactly one row + audit.
3. RLS drill: userA context sees zero userB rows; `svc` context returns only hostname lookup fields.
4. Sweep drill: only `verified` rows past 24 h enter recheck; only `pending` rows past their backoff enter polling; expired rounds leave `pending` with regenerate state.
