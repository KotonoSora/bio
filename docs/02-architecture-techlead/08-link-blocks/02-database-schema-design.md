# Link Blocks - Database Schema Design (feature 08)

Phase 2 TL · Same database `profile_db` (PostgreSQL 18.6), owned exclusively by `profile-service`. Implements the `BR-*` rules of `docs/01-requirements-ba/08-link-blocks/02-user-stories-and-business-rules.md`. FK target `profile.profiles` lives in `V1__profile_baseline.sql` (`docs/02-architecture-techlead/06-public-profile/02-database-schema-design.md`); `V4` is feature 07's verification baseline.

## Migration

`V5__blocks_baseline.sql` applies on boot after V4; the table is written once, no ALTER chains.

## DDL

```sql
CREATE TABLE profile.profile_blocks (
  id TEXT PRIMARY KEY,
  profile_id TEXT NOT NULL REFERENCES profile.profiles(id) ON DELETE CASCADE,
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
CREATE INDEX idx_blocks_profile_pos ON profile.profile_blocks (profile_id, position);
CREATE INDEX idx_blocks_user ON profile.profile_blocks (user_id);

ALTER TABLE profile.profile_blocks ENABLE ROW LEVEL SECURITY;
CREATE POLICY b_owner ON profile.profile_blocks FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY b_public ON profile.profile_blocks FOR SELECT TO app_role
  USING (is_visible = TRUE);

GRANT SELECT, INSERT, UPDATE, DELETE ON profile.profile_blocks TO app_role;
```

## Design notes

| Element | Rule | Why |
|---|---|---|
| `profile_id` FK | `ON DELETE CASCADE` | deleting a profile removes its blocks atomically; no orphan sweeps |
| `user_id` denormalized | `TEXT NOT NULL` + index | RLS without joining `profiles` on every write |
| `type` CHECK | 13-value closed alphabet | a bypassed validator can never persist a 14th type; additions require a spec amendment |
| `position` | dense 0-based; **not** a DB constraint | reorders serialize in service code; a DB exclusion would turn races into 500s instead of ordered retries |
| Window CHECK | `until > from` when both set | prevents never-visible rows that confuse creators and waste SSR reads |
| `b_public` policy | `is_visible = TRUE` only | RLS cannot see per-row `now()` windows cheaply; the SSR/internal query adds the window predicate explicitly |
| Logical refs | `payload_json.documentId` (resume) and product/course/tip ids (feature 22) | cross-module references by id only, never cross-schema FKs |
| Thumbnails | `thumbnailKey` opaque blob key inside `payload_json` | asset pipeline owned by feature 09 |

Indexes: `idx_blocks_profile_pos` serves the ordered public read (whole page, one scan) and dashboard lists; `idx_blocks_user` serves owner writes. No trigram/full-text in v1.

## Growth and operations

- Dominant table by rows: ~20 blocks/profile average, cap 250; growth linear with profiles. `INT` positions suffice; no partitioning in v1; hidden/expired rows stay until profile deletion.
- Backups: platform PITR; quarterly restore drill includes this table.

## Verification drills

1. Fresh apply: `flyway migrate` clean (V1 -> V2 -> V3) on empty 18.6; `\d` shows FK, both CHECKs, both indexes, both policies.
2. Constraint drill: a 14th `type` violates `chk_block_type`; inverted window violates `chk_block_window`; `http:` URL is NOT a DB error (scheme is service-validated - asserted in review).
3. Cascade drill: delete a profile -> blocks disappear in the same statement.
4. RLS drill: foreign `app.user_id` sees zero rows; `b_public` select sees only `is_visible = TRUE` rows.
5. No-duplication drill: `grep -rn "CREATE TABLE profile.profile_blocks" docs/` returns exactly this file.
