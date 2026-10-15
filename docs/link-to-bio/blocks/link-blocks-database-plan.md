# Link Blocks Database Plan (profile_blocks — table written once)

PLAN ref: P1b-1 (frozen 13-type superset) + P1b-2 (ordering, scheduling) + S-1/S-6. BA source: `docs/link-to-bio/blocks/link-blocks.md`. Consumed by `docs/link-to-bio/blocks/link-blocks-backend-plan.md` (validator, service, controller — the only writers). Profile rows live in `docs/link-to-bio/profile/public-profile-database-plan.md` (FK target, never redefined here). No migration chains — the SQL below is the complete table, applied once.

## Purpose

Give every developer the exact block table in one place: what each column stores, why each constraint and index exists, how ordering and scheduling work at the row level, and how the table relates to profiles. Exactly one `CREATE TABLE` for `profile_blocks`; no ALTERs, no versions.

## Users

- Backend developer: runs this script once, then maps `BlockEntity` 1:1.
- QA / reviewer: probes CHECKs, FK cascade, and RLS directly in `psql`.
- Future slices (shop products, course lessons): read this before referencing `block_id` (their rows point here logically, never with cross-module writes except through services).

## Functional requirements

1. Conventions (same as the profile database plan): `TEXT` ids, `TIMESTAMPTZ`, `user_id TEXT NOT NULL` + index + RLS on top of app-level scoping, migration role owns DDL, `app_role` gets table grants.
2. Table `bio.profile_blocks` — every content unit on every page. `id` (ulid PK); `profile_id` (FK → `bio.profiles(id)` `ON DELETE CASCADE` — deleting a profile removes its blocks atomically, no orphan sweeps); `user_id` (denormalized owner for RLS without joining profiles on every write); `type` (frozen 13-value alphabet: link/social/gallery/video/music/event/form/product/course/tip/resume/contact/embed — a 14th value requires a spec amendment, and the CHECK is the backstop); `position` (dense 0-based order within a profile; gaps forbidden by the reorder procedure, enforced in service, not by constraint); `payload_json` (per-type data validated by `BlockPayloadValidator`; renderer owns markup, so no HTML columns exist); `is_visible` + `visible_from/visible_until` (schedule window; NULL means open-ended); timestamps.
3. Constraints and why: `chk_block_type` (closed alphabet — an unknown type can never persist even if the API validator is bypassed); `chk_block_window` (`visible_until > visible_from` when both set — prevents never-visible rows that confuse creators and waste SSR reads). Position density is intentionally NOT a constraint (concurrent reorders serialize in service code; a DB exclusion would turn races into 500s instead of ordered retries).
4. Indexes: `idx_blocks_profile_pos(profile_id, position)` (the public read: whole page in one ordered scan); `idx_blocks_user(user_id)` (owner writes + dashboard lists). Both are exact-match/B-tree — no trigram or full-text indexes in v1.
5. RLS: `b_owner` (all operations, owner-only); `b_public` (select where `is_visible` — schedule-window filtering happens in the query, since RLS cannot see `now()` windows per-row cheaply at scale; the SSR query adds the window predicate explicitly). App-role `psql` with a foreign `app.user_id` sees zero rows.
6. Relationships: `profile_blocks.profile_id → profiles.id` (hard FK, cascade); `payload_json.document_id → documents.id` and product/course/tip references are logical only (cross-module reads via service interfaces, never cross-schema FKs — keeps modules extractable per `docs/platform/architecture/architecture-decision.md`).
7. Growth: blocks dominate page weight (≈ N rows per profile, small each); ordering column stays `INT` (no profile will hold 2B blocks); no partitioning in v1; hidden/expired rows stay (dashboard history) — purged only with the profile.

## Data / API touches

- Full table, applied once (same baseline run as the profile database plan; order: profiles first, then this FK-dependent table):
```sql
CREATE TABLE bio.profile_blocks (
  id TEXT PRIMARY KEY,
  profile_id TEXT NOT NULL REFERENCES bio.profiles(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL,
  type TEXT NOT NULL,
  position INT NOT NULL DEFAULT 0,
  payload_json JSONB NOT NULL DEFAULT '{}',
  is_visible BOOLEAN NOT NULL DEFAULT TRUE,
  visible_from TIMESTAMPTZ,
  visible_until TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_block_type CHECK (type IN ('link','social','gallery','video','music','event','form','product','course','tip','resume','contact','embed')),
  CONSTRAINT chk_block_window CHECK (visible_until IS NULL OR visible_from IS NULL OR visible_until > visible_from)
);
CREATE INDEX idx_blocks_profile_pos ON bio.profile_blocks(profile_id, position);
CREATE INDEX idx_blocks_user ON bio.profile_blocks(user_id);
ALTER TABLE bio.profile_blocks ENABLE ROW LEVEL SECURITY;
CREATE POLICY b_owner ON bio.profile_blocks FOR ALL TO app_role USING (user_id = current_setting('app.user_id', true)) WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY b_public ON bio.profile_blocks FOR SELECT TO app_role USING (is_visible = TRUE);
GRANT SELECT, INSERT, UPDATE, DELETE ON bio.profile_blocks TO app_role;
```
- Consumed by: backend plan (`BlockEntity`, validator values match `chk_block_type` exactly, reorder procedure maintains density); SSR reads (`profile_id` + window + order — one indexed scan). No client writes except through the backend plan's endpoints.

## Acceptance criteria

- Fresh-apply drill: script runs clean after the profile script; `\d bio.profile_blocks` shows FK, both CHECKs, both indexes, both policies.
- Constraint drill: 14th type violates `chk_block_type`; `http:` URL is NOT a DB error (scheme validated in service — DB stores what the validator passed; separation asserted in review); inverted window violates `chk_block_window`.
- Cascade drill: delete a profile → its blocks disappear in the same statement (no orphans).
- RLS drill: foreign `app.user_id` sees zero rows; public select sees only `is_visible` rows.
- No-duplication drill: `grep -rn "CREATE TABLE bio.profile_blocks" docs/` returns exactly this file.

## Out of scope

- Validation logic and endpoints (`docs/link-to-bio/blocks/link-blocks-backend-plan.md`); editor screens (`docs/link-to-bio/blocks/link-blocks-frontend-plan.md`); profile table (profile database plan above); payload rendering; shop/course tables (their own database plans).

## Open questions

- Q1: Position density as a DB exclusion constraint — keep service-serialized (current) or harden later under contention evidence?
- Q2: `payload_json` JSONB validation via `CHECK (jsonb_typeof…)` per type — worth it, or validator-only forever?
