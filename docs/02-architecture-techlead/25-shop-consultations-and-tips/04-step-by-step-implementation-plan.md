# Consultations and Tips - Step-by-Step Implementation Plan (feature 25)

Phase 2 TL · Dependency-ordered; acceptance goals cite `docs/01-requirements-ba/25-shop-consultations-and-tips/06-acceptance-criteria.md`.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `V4__shop_consultations_and_tips.sql` + RLS + grants; partial unique index verified under race | Feature 24 T-01 | TL 02 drills 1, 2, 5 green |
| T-02 | BE | Availability CRUD + slot computation (windows/overrides, buffers, 15-min grid, 2 h notice, 60-day horizon) + public slots query | T-01 | AC-01 (slot half), BA 04 slot grid |
| T-03 | BE | Holds + booking lifecycle: hold TTL, CAS confirm/cancel/expire, expiry sweep (60 s), auto-complete sweep (72 h), no-show mark | T-02 | AC-01, AC-02 (non-grant half), AC-03 (lifecycle half) |
| T-04 | BE | Grant/refund consumers (forward 32-40): confirm by `bookingId`, post-expiry auto-refund (`hold_expired`), reason codes (`cancel_ge_24h`, `creator_cancel`) | T-03; 32-40 seams | AC-02 (grant half), AC-03 green |
| T-05 | BE | Signed tokens + confirmation payload (both timezones, link, tokens) + reminder sweep (5 min, sent-flags, transactional enqueue forward) | T-03 | AC-04, AC-11 green |
| T-06 | BE | Tip jar config checks (bounds, one-jar, presets) + tip checkout surface (session + guest, Turnstile, email hash) + message write/sanitize | Feature 22 T-02 | AC-06, AC-07 green |
| T-07 | BE | Moderation CAS + wall reads (owner, public, internal) + `shop.tip_wall_changed.v1` emit + feature 10 purge row | T-06 | AC-08, AC-12 (scrub half) green |
| T-08 | BE | Refund router seams: `tip_final` / reason codes recorded (forward 38) + courtesy-refund audit action | T-04 | AC-09 green |
| T-09 | FE | Availability editor, slot picker (hold timer, policy checkbox), booking lists + reschedule/cancel dialogs, tip modal (guest email + Turnstile), jar config, moderation queue, wall section | T-02..T-08 | BA 04 states render; AC-05 UI half |
| T-10 | DevOps/QA | Drill wiring AC-01..AC-12; container drill (Docker + Podman; V3→V4 replay; rollback); OpenAPI diff; secrets grep; Turnstile config | T-04..T-09 | G4 + G5 + G7 green |

## Notes

- Build-order gate: grant/reminder/refund seams enable only when 32-40 ships them; holds, availability, and tip-message moderation ship earlier (free halves testable).
- Guest tips: the only sessionless money surface (D-6); guest bookings do not exist (BR-16) — no sessionless booking code path is written.
- Zero finance math: no fee values; money movement and receipts entirely 32-40; refund reasons are strings, never amounts.
- Legacy acceptance set fully carried (AC-01..AC-10 names preserved); legacy open questions resolved in BA 01 (calendar sync → amendment; late-arrival → copy only).
- Redis db 9: no new keys required (sweeps are DB-driven; D-5); the feature adds none by design.
