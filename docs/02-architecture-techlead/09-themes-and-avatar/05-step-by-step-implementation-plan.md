# Themes and Avatar - Step-by-Step Implementation Plan (feature 09)

Phase 2 TL · Dependency-ordered chain; each task's acceptance goal gates the next. Subsystems: BE, FE, DevOps. Criteria cited from `docs/01-requirements-ba/09-themes-and-avatar/06-acceptance-criteria.md`.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `ThemeTokens` + `ThemeRegistry` (8 themes, exactly 1 `videoAvatar=true`, proOnly flags) with boot validation (unique ids, every token renders in the fragment fixture) | feature 06 module | Registry test green; `videoAvatar` gate data available |
| T-02 | BE | `GET /api/v1/themes` + internal `GET /internal/v1/themes` (`registryVersion`, 300 s cache headers) | T-01 | AC-09 catalog side green; zero PG reads (memory map) |
| T-03 | BE | `PlanGate` (cached snapshot `<= 30 s` via the account-service entitlement API; fail closed for `proOnly`) + `ThemeService.setTheme` (lookup -> gate -> row update -> audit -> event) | T-02 | AC-03 green incl. entitlement-outage drill |
| T-04 | BE | `AvatarService` validation: bounded read (2 MB + 1), magic-byte sniff (JPEG/PNG/WebP/MP4 `ftyp`), video theme + <= 10 s checks; exact codes | T-01 | AC-02/AC-04 rejection matrix green with zero file writes |
| T-05 | BE | Accept path: name `profile/avatars/{ulid}.{ext}` -> file write -> row update -> previous-file delete -> audit -> event; idempotent delete | T-04 | AC-05, AC-07 green; crash drill leaves exactly one orphan |
| T-06 | BE | `AppearanceController` + DTOs + `@ControllerAdvice` (RFC 9457); session `userId` only | T-05 | Every code contract-tested exactly |
| T-07 | BE | Orphan sweep job (`APP_AVATAR_SWEEP_CRON`): 24 h unreferenced `profile/avatars/` files, dry-run metric first | T-05 | AC-06 green; referenced keys untouched |
| T-08 | DevOps | Gateway wiring: Turnstile + rate rules on upload/theme endpoints | T-06 | AC-12 green (`429` + `Retry-After`) |
| T-09 | BE | Test suite: upload matrix (file-store mock asserts zero writes per rejection), gating incl. fail-closed, delete idempotency, ArchUnit (no `customCss|fontUrl|headHtml` identifiers; no new `CREATE TABLE` for theme/avatar), OpenAPI additive diff | T-03..T-08 | AC-01..AC-13 green; `./gradlew build` green |
| T-10 | FE | `/app/appearance` gallery: server-driven cards, pro badge, upgrade sheet, token preview via CSS variables, `Unsaved` pill, save + rollback | T-06 | Preview/rollback drills green (AC-13 preview side) |
| T-11 | FE | Uploader: accept rules per theme, 2 MB pre-check, multipart progress, inline errors, delete + glyph restore | T-10 | Upload/delete drills green |
| T-12 | FE | Publish integration: shared purge chip (feature 06 component, not re-implemented), preview iframe hook to feature 10 `private, no-store` mode | T-11 | AC-08 UI path green; honesty rules asserted |
| T-13 | BE | Observability: metrics `appearance.theme.switch.total`, `appearance.gate.denied`, `appearance.avatar.rejected{code}`, `appearance.orphan.swept.total`; logs without bytes | T-07 | Dashboards + alerts live |
| T-14 | DevOps | Image + release gate: digest pin, SBOM, new env (`APP_AVATAR_SWEEP_CRON`), blue/green deploy, rollback drill | T-09, T-12 | `docker run` + `podman run` cold boot; rollback <= 10 min |

Definition of done: AC-01..AC-13 green, five storage drills green, ArchUnit + grep gates green, release gate passes, legacy themes-avatar docs deleted (git history is the archive).
