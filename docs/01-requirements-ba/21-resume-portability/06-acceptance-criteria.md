# Resume Portability - Acceptance Criteria (feature 21)

Phase 1 BA · BDD checks gating the release of `docs/02-architecture-techlead/21-resume-portability/`. Rules cited as `BR-nn` from `docs/01-requirements-ba/21-resume-portability/02-user-stories-and-business-rules.md`.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Round-trip drill | The fixture plus one real resume | Exported, imported into a fresh account, re-exported | Diff shows only ids/timestamps changed; full version history present; `published_version` re-resolved by version number (BR-02) |
| AC-02 | Rejection drill | Malformed, oversized, and newer-version files | Import is attempted for each | Exact codes returned; zero rows written on both services (count-asserted before/after) (BR-04) |
| AC-03 | Draft-only drill | A LinkedIn export file and a GitHub handle | Both adapters run | Drafts only (`is_public=false`, no published snapshot); `/:username/cv` curl unchanged until Save Snapshot + publish; `review_required` flags on adapter-filled fields (BR-07) |
| AC-04 | IDOR drill | User B's documents | User A attempts export/import against B's ids; direct PG as app role | `403` / RLS denial; no cross-profile parameter exists (BR-03) |
| AC-05 | XSS drill | Malicious strings in an imported payload | Rendered on dashboard preview, public CV, and re-export | Stripped on all three surfaces (double sanitize holds) (BR-04) |
| AC-06 | Fixture gate drill | A schema or envelope change | CI runs the build; then a deliberately broken fixture is pushed | Clean change: fixture imports on an empty database every build; broken change: build fails naming the fixture (BR-10) |
| AC-07 | Scope drill | A profile with shop/leads/domain rows | Export payload inspected | Zero non-resume data; structural (query path), not policy-based (BR-09) |
| AC-08 | SSRF drill | Adapter fetch pointed at private-range targets and redirect chains | GitHub adapter runs | Fetch fails closed (private-range block + redirect cap); abuse counter increments; no token input exists (BR-08) |
| AC-09 | Public-conflict drill | A target account with an existing public CV | A restore carrying `is_public=true` documents runs | Conflicting documents land private with the `public_conflict` warning naming both; zero overwrites; fresh-account restore honors `is_public` (BR-06) |
| AC-10 | Atomicity drill | A crash forced between the document phase and the block-relink phase | The import retries | No block ever points at a missing document; retry completes relink without duplicating documents; zero staging tables exist (BR-05) |
| AC-11 | Audit drill | One export, one restore, one adapter run | Audit trail is read | Three rows with actor + action + target + timestamp (BR-03) |
| AC-12 | Wizard-recovery drill | A session expiring mid-wizard | Re-login and re-submit | Uploaded file preserved client-side (`< 2MB`); import completes; no duplicate dry-run artifacts (BR-04) |
