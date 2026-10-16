# Public Profile - Acceptance Criteria (feature 06)

Phase 1 BA · BDD form. Every AC runs in the drill suite (task T-13 of `docs/02-architecture-techlead/06-public-profile/04-step-by-step-implementation-plan.md`).

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Username shape | Claim inputs: `Ad Min`, `ab`, `a!`, 31 chars | Each is submitted | `422` with `username_bad_chars` / `username_too_short` / `username_too_long` respectively; normalized echo was shown first |
| AC-02 | Case-insensitive uniqueness | Existing `alice` | `Alice` is claimed and a direct SQL insert of `ALICE` runs | `422 username_taken`; DB unique violation (citext) for the bypass insert |
| AC-03 | Reservation and retirement | Reserved slug `shop`; renamed `alice` -> `alice-dev` | `shop` claimed; `alice` re-claimed | `422 username_reserved`; `422 username_retired`; both never re-issuable |
| AC-04 | Rename atomicity and 301 | Live profile `alice` with history | Rename to `alice-dev`; `GET /alice`; concurrent double-rename attempted | One transaction wins; `/alice` returns `301` to `/alice-dev`; the loser gets `422 username_taken`; no partial state |
| AC-05 | Claim race | Two sessions claim `bob` at the same instant | Both submit | Exactly one `200`; the other `422 username_taken`; one redirect-free row |
| AC-06 | Visibility honesty | `isPublic=false` saved | Public `GET /:username` fetched; sitemap generated | `404` (same body/timing as unknown); absent from sitemap; dashboard `GET /api/v1/profile` still `200` |
| AC-07 | RBAC and IDOR | Sessions A and B with one profile each | A calls `GET`/`PUT` with B's identifiers and via direct DB as `app_role` with `app.user_id='A'` | `403` from API; zero B rows from DB (RLS); audit shows no false mutation |
| AC-08 | Sanitization and XSS | Payloads: `<script>`, `<iframe>`, `onerror=`, `javascript:` link | Saved and rendered | Markup stripped, `javascript:` rejected; stored links are `https:`; public anchors carry `rel="noopener ugc"`; CSP holds on both surfaces |
| AC-09 | Publication latency | Any successful mutation | Public URL polled until fresh | Fresh content visible within 2 min; the 60 s TTL alone (event lost) still satisfies this drill |
| AC-10 | Offline and session edges | Airplane mode; expired session mid-wizard | Save attempted; re-login performed | Submit disabled with `You're offline`; no phantom success; wizard input preserved and resumes at screen 1 |
| AC-11 | Sibling contracts | Public resolve API and the profile row | Feature 10 resolves `/:username`; theme/SEO fields read | `result=profile/redirect/missing` honored; `themeId`, `seoTitle`, `seoDesc` served without joins |
| AC-12 | Contracts and audit | Any mutation or rejection | Responses and audit log inspected | RFC 9457 problem `code` exact; OpenAPI diff additive-only; audit row per mutation (actor, timestamp, 24-month retention) |
