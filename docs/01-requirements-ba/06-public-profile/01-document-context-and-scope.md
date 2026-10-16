# Public Profile - Context and Scope (feature 06)

Phase 1 BA · Feature 06 of 53 (link-to-bio) · Implemented by `docs/02-architecture-techlead/06-public-profile/` (5 files). Consumers: features 07-15, 29 (reserved namespace), 17 (resume entry point).

## Business objectives

| # | Objective | Measure |
|---|---|---|
| BO-1 | One stable public identity per user: a unique lowercase username addressing `/:username`. | One row per user in v1; duplicate claim rejected at API and DB (AC-02). |
| BO-2 | Naming safety: reserved, retired, and squatted names cannot shadow routes or hijack pages. | Reserved slugs rejected twice (app + DB); retired names permanently unclaimable (AC-03). |
| BO-3 | Share links never break: renames keep old URLs working. | Old `/:username` 301s forever; QR and print links survive renames (AC-04). |
| BO-4 | Honest privacy: private means 404 everywhere public. | `is_public=0` -> public 404 + sitemap exclusion; dashboard still edits (AC-06). |
| BO-5 | Rich-surface ready: theme, SEO, badge, and resume data ready in one row read. | Sibling features 07-10 consume the row without joins (AC-11). |

## In scope

- Claim (first-run create) and edit of profile identity fields: username, displayName, bio, seoTitle, seoDesc.
- Visibility toggle (`is_public`) and resume-section toggle (`show_resume`).
- Username change with permanent old-to-new 301 mapping and permanent retirement of old names.
- Server-side sanitization, field limits, and the public read contract behind `/:username`.

## Out of scope

- Block list management (feature 08); themes and avatar pipeline (feature 09); SSR tags, cache, sitemap mechanics (feature 10); analytics (features 12-15).
- Auth mechanics (feature 01 owns SSO; this feature consumes the session).
- Account deletion flow (feature 02 owns the 30-day grace; the profile deactivates on its signal).
- Resume document model (features 17-21); shop/leads/custom domains (their majors).

## Personas and RBAC matrix

| Role | CRUD Permissions | Description |
|---|---|---|
| Creator | Read + update own profile (create on first claim) | Claims a username, edits identity, toggles visibility, renames safely. |
| Visitor | Read public profile only | Opens `/:username` without login; sees content only when `is_public=1`. |
| Returning visitor / QR scanner | Read via 301 | Old usernames after a rename land on the new URL. |
| Public-site service | Internal read (public subset) | Serves `/:username` SSR from the internal read API; never writes. |

## Open questions (non-blocking; v1 defaults)

| # | Question | v1 default | Revisit trigger |
|---|---|---|---|
| Q-01 | Second profile on paid tiers | one profile per user, `profile_limit_reached` guard | feature 03 ships second-profile entitlement |
| Q-02 | Retention of retired usernames | permanent reservation | legal or abuse review requests release |
| Q-03 | Redirect write enforcement (DB policy is permissive by necessity) | service-side ownership check + audit | trigger-based enforcement proves simpler |

Related specs: implementation `docs/02-architecture-techlead/06-public-profile/`; namespace dependency `docs/01-requirements-ba/29-short-links/02-user-stories-and-business-rules.md` (BR-01).
