# QR Codes Database Plan (qr render log — table written once)

PLAN ref: P2 delivery (QR for short URLs per `docs/PLAN.md`) + render-log retention. BA source: `docs/url-shortener/links/qr-codes.md`. Consumed by the qr backend plan (the only writer; splits next to this file). Link truth lives in `docs/url-shortener/links/short-links-database-plan.md` (FK target, never redefined here). No migration chains — the SQL below is complete, applied once.

## Purpose

Give every developer the exact QR table in one place, with its subordinate role made explicit: this table is a render log, never the truth (truth stays in `short_links`). Reprints re-render from the live link record — a cached image is never the source. Exactly one `CREATE TABLE`; no ALTERs, no versions.

## Users

- Backend developer: runs this script once, logs renders 1:1, re-renders from live records.
- QA: proves lifecycle inheritance (pixels don't change; resolution does) and bulk-pack uniqueness.
- Print vendor (indirect): receives files whose quality floors were enforced before bytes existed.

## Functional requirements

1. Conventions (repo-wide): `TEXT` ids, `TIMESTAMPTZ`, `user_id` + index + RLS plus app scoping, migration role owns DDL, `app_role` grants, schema `links` (same module as short links).
2. Table `links.qr_codes` — one row per generated export (the log of what was rendered, for reprints and audits). `id` (ulid PK); `short_code` (FK → `short_links(code)` `ON DELETE CASCADE` — link rows are tombstoned, not hard-deleted, so the cascade is hygiene for the exceptional purge, not a lifecycle path); `user_id` (requester, mirrors the parent link's owner at render time — denormalized so RLS needs no join); `format` (`png|svg`); `size_class` (declared print size, e.g. `S/M/L/poster` — the contract the scan-floor check validated against); `error_level` (`M|Q|H` — floor M, H for outdoor/small); `created_at`. No destination column (payload is ALWAYS the short URL, derived at render — storing it would fork the truth the day a destination edits).
3. No lifecycle columns by design: expiry/suspension live on the parent link and resolve at scan time. A `status` here would need syncing with the link (dual-write bug farm) — its absence is the lifecycle-inheritance guarantee made structural.
4. Indexes: `idx_qr_code(short_code, created_at DESC)` (re-render latest + per-link history); `idx_qr_user(user_id)` (my exports). No payload index (payloads aren't stored).
5. RLS: `q_owner` (all operations, owner-only, matching the parent link's owner — cross-user QR of someone else's link → 403 + deny); no public policy (QR images download through owner-scoped endpoints; scanners use the short URL, never this table).
6. Bulk packs: event-pack generations insert one row per code (100-code pack = 100 rows + 100 short links, one audited ops action wrapping both — pack atomicity: all-or-nothing per pack, so per-location measurement never half-exists).
7. Growth: rows per export (small metadata, images live in blob storage keyed by render id, short TTL for intermediates, pinned for downloads); no partitioning in v1 — exports are operator-paced, scans don't write here.

## Data / API touches

- Full table, applied once (same baseline run, after `short_links`):
```sql
CREATE TABLE links.qr_codes (
  id TEXT PRIMARY KEY, short_code TEXT NOT NULL REFERENCES links.short_links(code) ON DELETE CASCADE,
  user_id TEXT NOT NULL, format TEXT NOT NULL, size_class TEXT NOT NULL, error_level TEXT NOT NULL DEFAULT 'M',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_qr_format CHECK (format IN ('png','svg')),
  CONSTRAINT chk_qr_level CHECK (error_level IN ('M','Q','H'))
);
CREATE INDEX idx_qr_code ON links.qr_codes(short_code, created_at DESC);
CREATE INDEX idx_qr_user ON links.qr_codes(user_id);
ALTER TABLE links.qr_codes ENABLE ROW LEVEL SECURITY;
CREATE POLICY q_owner ON links.qr_codes FOR ALL TO app_role USING (user_id = current_setting('app.user_id', true)) WITH CHECK (user_id = current_setting('app.user_id', true));
GRANT SELECT, INSERT, UPDATE, DELETE ON links.qr_codes TO app_role;
```
- Consumed by: qr backend plan (only writer); re-render reads (latest row per code + live link record). Images: blob objects keyed by render id (metadata here, bytes there).

## Acceptance criteria

- Fresh-apply drill: script runs clean; `\d` shows FK, CHECKs, indexes, policy.
- Log-not-truth drill: destination edit → old QR row unchanged, yet scans resolve new target (resolution reads the link, never this table — proven by reprint rendering current target from an old row's code).
- Bulk drill: 100-code pack inserts 100 link rows + 100 qr rows atomically (partial pack impossible).
- RLS drill: foreign `app.user_id` renders zero rows; anonymous reads none.
- No-duplication drill: `grep -rn "CREATE TABLE links.qr_codes" docs/` returns exactly this file.

## Out of scope

- Generation/quality/bulk flows (backend plan); download/print-sheet screens (frontend plan); link minting/resolve (short-links plans); hit counting and scan-split (analytics plans); profile QR conventions (link-to-bio — same URL there); payment QR payloads (provider majors, never this generator); P4/P5.

## Open questions

- Q1: Brand-styled QRs (logos/colors) above a strict floor, or plain-only v1 (BA Q1)?
- Q2: Print-size intent presets (poster vs receipt) driving defaults, or manual picker only (BA Q2)?
