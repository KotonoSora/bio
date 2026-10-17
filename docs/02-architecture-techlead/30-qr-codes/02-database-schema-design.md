# QR Codes - Database Schema Design (feature 30)

Phase 2 TL · `links_db` migration `V2__qr_codes.sql` (V1 = `V1__short_links_baseline.sql`, feature 29; new `V_n` per change, no ALTER chains). BA sources: `docs/01-requirements-ba/30-qr-codes/` (BR-01..BR-12).

## DDL

```sql
CREATE TABLE links.qr_codes (
  id          TEXT PRIMARY KEY,
  short_code  TEXT NOT NULL REFERENCES links.short_links(code) ON DELETE CASCADE,
  user_id     TEXT NOT NULL,
  pack_ref    TEXT,
  format      TEXT NOT NULL,
  size_class  TEXT NOT NULL,
  error_level TEXT NOT NULL DEFAULT 'M',
  dpi         INTEGER,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_qr_format CHECK (format IN ('png','svg')),
  CONSTRAINT chk_qr_size   CHECK (size_class IN ('s','m','l','poster')),
  CONSTRAINT chk_qr_level  CHECK (error_level IN ('M','Q','H')),
  CONSTRAINT chk_qr_dpi    CHECK (
    (format = 'svg' AND dpi IS NULL) OR
    (format = 'png' AND dpi IN (150,300,600)))
);
CREATE INDEX idx_qr_code ON links.qr_codes (short_code, created_at DESC);
CREATE INDEX idx_qr_user ON links.qr_codes (user_id, created_at DESC);
CREATE INDEX idx_qr_pack ON links.qr_codes (pack_ref) WHERE pack_ref IS NOT NULL;

ALTER TABLE links.qr_codes ENABLE ROW LEVEL SECURITY;
CREATE POLICY q_owner ON links.qr_codes FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY q_ops ON links.qr_codes FOR ALL TO app_role
  USING (current_setting('app.context', true) = 'ops')
  WITH CHECK (current_setting('app.context', true) = 'ops');

GRANT SELECT, INSERT, UPDATE, DELETE ON links.qr_codes TO app_role;
```

## Notes

- Log-not-truth: no destination column (payload is derived at render from the live link) and no lifecycle columns (`status` here would be a dual-write bug farm — its absence is the lifecycle-inheritance guarantee, BA BR-04).
- `pack_ref` is the only addition beyond the legacy sketch: the pack's session id (ULID), written on all N rows of one ops pack so sheets and per-location stats assemble from the log; null for single renders. Pack atomicity = one transaction over the N link mints + N rows.
- Cascade: `short_links` rows are tombstoned, not hard-deleted; the FK cascade fires only for the exceptional purge (hygiene, not a lifecycle path). Tombstoned links keep their render history.
- Files: `links/qr/{userId}/{renderId}.png|.svg` on the uploads volume, 30-day retention sweep; bytes are a re-renderable cache. Success = file stored then row inserted in the same request; file failure → 503 `render_unavailable`, no row.
- Quotas (Redis db 3): `links:qr:{userId}:{yyyymmdd}` render counter (cap 60/day cadence enforced hourly as 60/h bucket), `links:qrpacks:{userId}:{yyyymmdd}` (cap 10/day); both rebuilt from the log if lost (DB count fallback).
- Purge (feature 02 participant, `link-service` row extended): delete rows by `user_id` + files under `links/qr/{userId}/`; counts in the certificate.

## Verification drills

1. Fresh apply V1→V2 on PG `18.6`; `\d` shows CHECKs, FK, three indexes, two policies.
2. Log-not-truth drill: destination edit → old row unchanged; re-render from the old row's code carries the new target (resolution reads the link, never this table).
3. Cascade drill: exceptional hard delete of a link removes its render rows; tombstone does not.
4. RLS drill: foreign `app.user_id` reads zero rows; anonymous reads none; `ops` context reads for audit only.
5. Pack drill: 100-code pack inserts 100 link rows + 100 render rows atomically (partial pack impossible); `idx_qr_pack` serves the sheet.
6. No-duplication drill: `grep -rn "CREATE TABLE links.qr_codes" docs/` returns exactly this file.
