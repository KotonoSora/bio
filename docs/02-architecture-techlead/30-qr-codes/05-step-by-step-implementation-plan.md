# QR Codes - Step-by-Step Implementation Plan (feature 30)

Phase 2 TL · Dependency-ordered; acceptance goals cite `docs/01-requirements-ba/30-qr-codes/06-acceptance-criteria.md`.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `V2__qr_codes.sql` + RLS (`q_owner`, `q_ops`) + grants + partial pack index | Feature 29 T-01 | TL 02 drills 1-6 green |
| T-02 | BE | Renderer core: zxing dependency, canonical payload builder, floor enforcement (level/quiet/module/contrast), PNG DPI + SVG paths, palette assertion | T-01 | AC-01, AC-02, AC-04 |
| T-03 | BE | Generate endpoint + file storage (`links/qr/{userId}/`) + row semantics + download grants + label derivation | T-02 | AC-01, AC-08 |
| T-04 | BE | Renders history + re-render flow (live-record read, no cache path) | T-03 | AC-06 |
| T-05 | BE | Sheet assembly (SVG multi-up + labels), per-link and per-pack routes | T-03 | BA 04 sheet states; AC-05 (sheet half) |
| T-06 | BE | Ops packs: route + `ROLE_OPS` re-verify + atomic transaction (N mints + N rows) + audit + pack sheet | T-03, feature 29 mint path | AC-05, AC-09 |
| T-07 | BE | Quotas (Redis db 3 counters + DB fallback), Turnstile wiring, purge participant extension | T-03 | NFR-09, purge drill |
| T-08 | FE | QR tab (pickers, floor messaging, download + label, history, staleness banner, state banner), print sheet print flow + checklist, ops pack screen, watermarked preview | T-03..T-06 | BA 04 states render |
| T-09 | DevOps/QA | Decode CI on every export fixture; device × size × level scan matrix (incl. physical reference prints); contrast reference images; container drill (Docker + Podman; V1→V2 replay; rollback); native-dep + encoder greps | T-02..T-08 | G4 + G5 + G7; AC-10 |

## Notes

- Build-order gate: none — everything ships inside link-service; scan-split statistics arrive with feature 31 (this feature's output feeds it).
- The decode test runs on every export in CI (fixture set × formats × classes) — encode-short-only is asserted, not assumed.
- Physical reference prints are a one-time manual gate per size class; CI keeps the digital matrix (reference images).
- Files are cache: any missing file re-renders from the log row's code; the 30-day sweep never removes rows.
- Brand styling (Q1) ships later only by passing the same floors — no code path is stubbed now.
