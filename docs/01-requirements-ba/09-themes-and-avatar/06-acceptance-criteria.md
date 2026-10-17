# Themes and Avatar - Acceptance Criteria (feature 09)

Phase 1 BA · BDD form. Every AC runs in the drill suite (task T-09 of `docs/02-architecture-techlead/09-themes-and-avatar/05-step-by-step-implementation-plan.md`).

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Token-only drill | UI, API, and schema of this feature | A grep + ArchUnit pass looks for user-styling fields (`customCss`, `fontUrl`, `headHtml`, color inputs) | Zero occurrences; the only styling input is `theme_id` |
| AC-02 | Hostile upload matrix | Renamed script, SVG, HTML, forged `image/png`, 5 MB file, MP4 without `ftyp` | Each is uploaded | Exact `422` code per case; file-store mock shows zero writes for every rejection |
| AC-03 | Plan gating | Free-plan account and a `proOnly` theme | Direct `PUT /api/v1/profile/theme`; then entitlement service unreachable | `403 theme_forbidden` both ways (fail closed); standard theme switch still succeeds; after upgrade the pro save succeeds |
| AC-04 | Video matrix | Theme with `videoAvatar=true` and one without | Valid <= 10 s loop; > 10 s loop; MP4 on the non-video theme | Allowed on the video theme; `avatar_video_too_long` / `avatar_video_not_allowed` respectively, zero writes |
| AC-05 | Delete idempotency | Account with an avatar, then one whose file is already missing | `DELETE /api/v1/profile/avatar` twice | Both return `204`; row key cleared; default glyph rendered |
| AC-06 | Orphan sweep | One unreferenced file > 24 h old and one referenced name | Sweep job runs (dry-run metric first) | Exactly the orphan is deleted; referenced names untouched |
| AC-07 | Cache headers | Uploaded avatar and theme thumbnail | URLs fetched | `Cache-Control: public, max-age=31536000, immutable` on both; theme switch leaves the avatar URL unchanged (no re-fetch) |
| AC-08 | Publish drill | Saved theme change and avatar change | Public page read while purge chip pending | Change visible <= 2 min; chip clears; event-dropped path heals via TTL (06 BR-09) |
| AC-09 | Renderer contract | Public-site service with a warm catalog cache | Render `/:username`; then force an unknown `themeId` | Tokens come from `GET /internal/v1/themes` (`registryVersion` present); unknown id falls back to the default theme; page never breaks |
| AC-10 | Key randomness | 100 consecutive uploads | Keys inspected; neighbor-key guesses attempted | 100 unique non-sequential ULID keys; guessing returns nothing enumerable (no listing endpoint) |
| AC-11 | No-table drill | Schema before/after this feature | Diff computed; grep run | Zero new tables; a hypothetical theme/avatar table fails review by rule (BR-11) |
| AC-12 | Abuse throttling | Burst of uploads/theme switches | Rate rules + Turnstile evaluate | `429 throttled` with `Retry-After`; no partial writes; audit shows denials |
| AC-13 | Preview honesty | Free account previewing a pro theme; unsaved standard theme | Inspect rendered preview; attempt save flows | Pro preview = static thumbnail only (no live recolor); unsaved preview shows the `Unsaved` pill and persists nothing until `PUT` returns `200` |
