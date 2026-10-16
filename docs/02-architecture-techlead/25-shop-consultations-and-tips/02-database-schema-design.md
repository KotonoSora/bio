# Consultations and Tips - Database Schema Design (feature 25)

Phase 2 TL · `shop_db` migration `V4__shop_consultations_and_tips.sql` (extends V3 from feature 24). BA sources: `docs/01-requirements-ba/25-shop-consultations-and-tips/` (BR-01..BR-20).

## DDL

```sql
CREATE TABLE shop.booking_windows (
  id         TEXT PRIMARY KEY,
  user_id    TEXT NOT NULL,
  profile_id TEXT NOT NULL,
  weekday    INTEGER NOT NULL CHECK (weekday BETWEEN 0 AND 6),  -- 0 = Sunday
  start_time TIME NOT NULL,
  end_time   TIME NOT NULL,
  tz         TEXT NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_window_range CHECK (start_time < end_time),
  CONSTRAINT uq_window UNIQUE (user_id, weekday, start_time)
);

CREATE TABLE shop.booking_overrides (
  id         TEXT PRIMARY KEY,
  user_id    TEXT NOT NULL,
  profile_id TEXT NOT NULL,
  on_date    DATE NOT NULL,
  kind       TEXT NOT NULL,
  start_time TIME,
  end_time   TIME,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_override_kind CHECK (kind IN ('closed','window')),
  CONSTRAINT chk_override_range CHECK (kind = 'closed'
    OR (start_time IS NOT NULL AND end_time IS NOT NULL AND start_time < end_time)),
  CONSTRAINT uq_override UNIQUE (user_id, on_date, kind, start_time)
);

CREATE TABLE shop.bookings (
  id               TEXT PRIMARY KEY,
  product_id       TEXT NOT NULL,
  user_id          TEXT NOT NULL,             -- creator
  buyer_user_id    TEXT,                      -- session buyer
  buyer_email_hash TEXT,                      -- guest binding (hashed; never raw)
  slot_start       TIMESTAMPTZ NOT NULL,
  slot_end         TIMESTAMPTZ NOT NULL,
  status           TEXT NOT NULL DEFAULT 'held',
  hold_expires_at  TIMESTAMPTZ,
  order_id         TEXT,
  meeting_link     TEXT,
  policy_version   TEXT NOT NULL,
  reschedule_count INTEGER NOT NULL DEFAULT 0 CHECK (reschedule_count >= 0),
  cancel_reason    TEXT,
  reminded_24h_at  TIMESTAMPTZ,
  reminded_1h_at   TIMESTAMPTZ,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_booking_status CHECK (status IN ('held','confirmed','cancelled','completed','no_show','expired')),
  CONSTRAINT chk_booking_buyer CHECK (buyer_user_id IS NOT NULL OR buyer_email_hash IS NOT NULL),
  CONSTRAINT chk_booking_range CHECK (slot_start < slot_end)
);

CREATE UNIQUE INDEX uq_booking_slot ON shop.bookings (user_id, slot_start)
  WHERE status IN ('held','confirmed');
CREATE INDEX idx_bookings_buyer   ON shop.bookings (buyer_user_id, slot_start DESC);
CREATE INDEX idx_bookings_owner   ON shop.bookings (user_id, slot_start DESC);
CREATE INDEX idx_bookings_remind  ON shop.bookings (slot_start) WHERE status = 'confirmed'
  AND (reminded_24h_at IS NULL OR reminded_1h_at IS NULL);
CREATE INDEX idx_windows_owner    ON shop.booking_windows (user_id, weekday);
CREATE INDEX idx_overrides_owner  ON shop.booking_overrides (user_id, on_date);

CREATE TABLE shop.tip_messages (
  tip_order_id  TEXT PRIMARY KEY,
  product_id    TEXT NOT NULL,
  user_id       TEXT NOT NULL,               -- creator
  buyer_user_id TEXT,                        -- null for guests
  display_name  VARCHAR(60),
  message       VARCHAR(280),
  status        TEXT NOT NULL DEFAULT 'pending',
  thanked_at    TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_msg_status CHECK (status IN ('pending','approved','rejected'))
);

CREATE INDEX idx_tipmsg_owner ON shop.tip_messages (user_id, status, created_at DESC);

ALTER TABLE shop.booking_windows   ENABLE ROW LEVEL SECURITY;
ALTER TABLE shop.booking_overrides ENABLE ROW LEVEL SECURITY;
ALTER TABLE shop.bookings          ENABLE ROW LEVEL SECURITY;
ALTER TABLE shop.tip_messages      ENABLE ROW LEVEL SECURITY;

CREATE POLICY windows_owner ON shop.booking_windows FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY overrides_owner ON shop.booking_overrides FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));

CREATE POLICY bookings_owner ON shop.bookings FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY bookings_buyer ON shop.bookings FOR SELECT TO app_role
  USING (buyer_user_id = current_setting('app.user_id', true));
CREATE POLICY bookings_svc ON shop.bookings FOR ALL TO app_role
  USING (current_setting('app.context', true) = 'svc')
  WITH CHECK (current_setting('app.context', true) = 'svc');

CREATE POLICY tipmsg_owner ON shop.tip_messages FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY tipmsg_svc ON shop.tip_messages FOR SELECT TO app_role
  USING (current_setting('app.context', true) = 'svc');   -- wall read (approved only filter in SQL)

GRANT SELECT, INSERT, UPDATE, DELETE ON shop.booking_windows, shop.booking_overrides,
  shop.bookings, shop.tip_messages TO app_role;
```

## Notes

- Slot computation: expand windows/overrides in the creator timezone, subtract buffers and active bookings, emit the 15-min grid within the horizon; the unique index is the final arbiter (D-2).
- Expiry sweep: `held` rows past `hold_expires_at` → `expired` (every 60 s); reminder sweep every 5 min (D-5); auto-complete `confirmed` rows 72 h past `slot_end`.
- Tokens are stateless (D-4); nothing to clean up.
- Guest buyer binding: `buyer_email_hash` (salted hash; raw email never stored in shop_db); the checkout/receipt pipeline owns raw email (32-40).
- Tip finality: no refund code path exists here; the router (32-40) branches on the order's snapshotted kind.
- Wall reads filter `status = 'approved'` in SQL; `pending|rejected` rows never leave the owner/svc contexts.
- Purge: seller deletes windows/overrides/bookings-as-owner/tip messages; buyer/guest scrub per D-9 (named approved → display name nulled + `message` kept; anonymous approved stays anonymous).

## Verification drills

1. Fresh apply V3→V4 on PG `18.6`; `\d` shows the partial unique index, CHECKs, policies.
2. Race drill: two concurrent holds on one slot → one row; loser 409.
3. Expiry drill: sweep flips stale holds; a re-hold on the freed slot succeeds.
4. Buyer RLS drill: foreign buyer reads zero bookings; svc reads for token flows; guest (hash) rows invisible to sessions.
5. Misc status drill: illegal transitions (e.g. `expired → confirmed`) rejected by the CAS guard.
6. Purge drill: guest authored-and-approved named message scrub semantics (D-9) verified.
