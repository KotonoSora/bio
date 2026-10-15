# Short Links Database Plan (short_links — table written once)

PLAN ref: P2 delivery per `docs/PLAN.md` (short links/QR in P2 scope; BA proposes P4-growth — SCOPE NOTE: PLAN governs scope per `AGENTS.md`, so this slice ships as P2 buildable; affiliate/ad/campaign consumers stay gated behind their own plan updates) + S-1/S-6. BA source: `docs/url-shortener/links/short-links.md`. Consumed by the short-links backend plan (the only writer; splits next to this file). Namespace rule (`go, r, a` reserved) enforced in `docs/link-to-bio/profile/public-profile-database-plan.md` (`chk_username_reserved` — referenced, never redefined here). No migration chains — the SQL below is complete, applied once.

## Purpose

Give every developer the exact short-link table in one place: code identity, destination editability without code recycling, lifecycle states, tombstone-instead-of-delete, and the policies that let owners manage own links while ops manages all. Exactly one `CREATE TABLE`; no ALTERs, no versions.

## Users

- Backend developer: runs this script once, maps `ShortLinkEntity` 1:1, deletes only through tombstoning.
- QA: probes collision isolation, lifecycle transitions, and RLS directly.
- Abuse reviewer: suspends destinations knowing resolution dies while evidence rows survive.

## Functional requirements

1. Conventions (repo-wide): `TEXT` keys, `TIMESTAMPTZ`, `user_id TEXT NOT NULL` + index + RLS plus app scoping, migration role owns DDL, `app_role` grants, schema `links` (new per-module schema — links are a module like `resume`/`bio`, extractable with their tables).
2. Table `links.short_links` — one row per minted link (identity + routing truth). `code TEXT PRIMARY KEY` (base62, 7+ chars, random non-sequential; vanity codes share the same PK space — availability IS the PK collision check, so double-mint races fail closed); `user_id` (owner; ops act through the audited ops path, not ownership); `destination TEXT NOT NULL` (editable — the code stays, the target moves; reprints keep working); `kind` (`random|vanity`); `campaign_id TEXT NULL` (logical reference to future campaign rows — no FK, campaigns are P4-gated and must never block this table); `expires_at TIMESTAMPTZ NULL` (campaign-scoped expiry defaults set in service); `fallback_url TEXT NULL` (declared fallback for dormant-target behavior); `status` (`live|suspended|expired|dormant`); timestamps.
3. Constraints and why: `chk_link_kind` + `chk_link_status` (closed alphabets — a 6th status cannot persist past a bypassed API); `chk_dest_scheme` (`destination LIKE 'https://%'` — static backstop; the dynamic blocklist lives in service + short-TTL cache, unreachable by CHECK); vanity trademark posture is procedural (per-account scoping + Q1 dispute process), not a constraint — the DB guarantees uniqueness, humans guarantee fairness.
4. Lifecycle as data (no separate state table): mint → `live`; report → `suspended` (one audited action, resolution dies, row + clicks preserved as evidence); time passes `expires_at` → `expired` (computed at resolve AND materialized by a sweep — both, so expiry holds even if the sweep stalls); target deleted with fallback → `dormant` (fallback declared) else resolve-time gone-page; owner delete → tombstone: `expires_at = now()` + row retained (never hard-deleted while click history exists — history lives in the analytics tables keyed by code, and a hard delete would orphan their meaning).
5. Indexes: `idx_links_user(user_id)` (owner lists); code PK serves resolve + mint-collision checks (single-row exact match — the hot path is one PK lookup, cache-fronted). No destination index (destinations are never looked up by value in v1; blocklist-by-destination scans belong to the abuse tooling, not OLTP).
6. RLS: `l_owner` (all operations, owner-only); `l_public_resolve` (anonymous `SELECT` of `live`-row code+destination ONLY via the resolve function path — the public never lists links); ops act through the service role path with audit (not through RLS bypass stapled onto creators).
7. Growth: one row per minted link (small); click volume never touches this table (counts live in the analytics tables + blob logs). Mint quotas (per-account) + tombstone-instead-of-delete bound growth from both ends; no partitioning in v1.

## Data / API touches

- Full table, applied once (same baseline run; schema `links` created here):
```sql
CREATE SCHEMA IF NOT EXISTS links;
CREATE TABLE links.short_links (
  code TEXT PRIMARY KEY, user_id TEXT NOT NULL,
  destination TEXT NOT NULL, kind TEXT NOT NULL DEFAULT 'random',
  campaign_id TEXT, expires_at TIMESTAMPTZ, fallback_url TEXT,
  status TEXT NOT NULL DEFAULT 'live',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_link_kind CHECK (kind IN ('random','vanity')),
  CONSTRAINT chk_link_status CHECK (status IN ('live','suspended','expired','dormant')),
  CONSTRAINT chk_dest_scheme CHECK (destination LIKE 'https://%')
);
CREATE INDEX idx_links_user ON links.short_links(user_id);
ALTER TABLE links.short_links ENABLE ROW LEVEL SECURITY;
CREATE POLICY l_owner ON links.short_links FOR ALL TO app_role USING (user_id = current_setting('app.user_id', true)) WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY l_public_resolve ON links.short_links FOR SELECT TO app_role USING (status = 'live');
GRANT SELECT, INSERT, UPDATE, DELETE ON links.short_links TO app_role;
```
- Consumed by: short-links backend plan (only writer); resolve path (PK lookup, cache-fronted); QR renders (FK target, next to this file); analytics joins by code (logical).

## Acceptance criteria

- Fresh-apply drill: script runs clean; `\d` shows PK, 3 CHECKs, index, both policies.
- Collision drill: double-mint same code → one winner (PK violation mapped, never 500); vanity availability IS the PK check.
- Lifecycle drill: each transition persisted and resolve-visible; tombstoned row + its clicks survive (history intact).
- Scheme drill: `http:` destination violates `chk_dest_scheme` even past a bypassed API.
- RLS drill: foreign `app.user_id` manages zero rows; anonymous resolves live rows only through the resolve path.
- No-duplication drill: `grep -rn "CREATE TABLE links.short_links" docs/` returns exactly this file.

## Out of scope

- Mint/resolve/suspend flows (backend plan); link-manager screens (frontend plan); QR render log (qr database plan); hit aggregates (analytics database plan); username namespace rules (profile database plan); affiliate attribution/ad measurement (consumers, own minors); custom short domains (named future work); P4/P5.

## Open questions

- Q1: Vanity trademark disputes — first-come vs claim-review (BA Q1)?
- Q2: Interstitial warning pages for unlisted-but-untrusted destinations — worth the friction, or blocklist-only v1 (BA Q2)?
