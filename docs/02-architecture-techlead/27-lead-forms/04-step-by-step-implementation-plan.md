# Lead Forms - Step-by-Step Implementation Plan (feature 27)

Phase 2 TL · Dependency-ordered; acceptance goals cite `docs/01-requirements-ba/27-lead-forms/06-acceptance-criteria.md`.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `V6__leads_baseline.sql` + RLS + grants + partial index | Feature 08 T-01 (profile_db chain) | TL 02 drills 1-6 green |
| T-02 | BE | Feature 08 registry hook: closed `form` payload schema + server-stamped `copyVersion` | T-01 | BR-01; config save drill |
| T-03 | BE | Public submit handler: resolve block → profile, sanitize, uniform 202, outbox write only | T-01 | AC-04 (uniform half), AC-06 green |
| T-04 | BE | Capture consumer: 2 s poll, batch ≤ 50, dedupe/merge, rank guard, events, disposable flag, drop metric | T-03 | AC-01, AC-02, AC-07, AC-10 green |
| T-05 | BE | Double opt-in: HMAC token issue, GET read-only context, idempotent POST confirm, re-request path (rate-limited) | T-04 | AC-03, AC-08, AC-09 green |
| T-06 | BE | Inbox feed endpoint + unread counters; internal writes (`/internal/v1/leads`, status, check) with identical merge rules | T-04 | AC-05 (feed half), AC-10 green |
| T-07 | BE | Abuse budgets: Redis counters + 15-min pause + notice state; disposable/role list asset; hashed IP signals | T-03 | AC-04 green |
| T-08 | FE | Form block renderer states (success/error/paused), dashboard form-config panel (toggles, opt-in, copy version), inbox feed + unread, confirm page (POST button, expired state) | T-02..T-07 | BA 04 states render |
| T-09 | DevOps/QA | Drills AC-01..AC-12; surface-review gate (no consent-grant endpoint); enumeration analysis run; container drill (Docker + Podman; V5→V6 replay; rollback); secrets grep | T-04..T-08 | G4 + G5 + G7 green |

## Notes

- Build-order gate: confirm/welcome dispatch and provider-signal ingestion enable when the notification pipeline lands (monetize batch); the capture→confirm loop is fully testable with the pipeline mocked at the queue boundary (recorded, not stubbed in production paths).
- No SDKs: the only third-party asset on the capture surface is the gateway Turnstile-class challenge (baseline stack pin).
- Surface review (AC-12) blocks any future "grant consent" helper by design; BR-16 is a review gate, not an honor system.
- The colocated host features remain untouched: block CRUD (08) and SSR rendering (10) gain only the payload-schema carve-out.
- Retention: done outbox rows 30 days; metrics counters short-lived; nothing user-visible is retained from abuse signals beyond the 24 h hashed TTL.
