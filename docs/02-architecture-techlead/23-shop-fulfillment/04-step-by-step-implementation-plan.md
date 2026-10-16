# Shop Fulfillment - Step-by-Step Implementation Plan (feature 23)

Phase 2 TL · Dependency-ordered; acceptance goals cite `docs/01-requirements-ba/23-shop-fulfillment/06-acceptance-criteria.md`.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `V2__shop_fulfillment.sql` + RLS + grants; partner adapter interface + mock implementation (test tree only) | Feature 22 T-01 | TL 02 drills 1-6 green |
| T-02 | BE | Design upload + versioning (bounded read, magic-byte, random `shop-designs` keys); product payload binding `{designId, designVersion, template, variants[]}` added to the feature 22 registry | T-01 | AC-04 green |
| T-03 | BE | Dispatch: consume `order.granted.v1` (forward), create jobs exactly-once, adapter `createJob`, bounded retry then `failed`; refund consumer cancels pre-print | T-01; 32-40 forward seams | AC-01, AC-02 green |
| T-04 | BE | Callback route (signature verify, dedupe, stream `merch:callbacks`) + worker: compare-and-set transitions, terminal states, `reprint_of` linking | T-03 | AC-03 green |
| T-05 | BE | Digital versions: upload (allowlist, 500 MB), publish flips `current` under the partial index, publish precondition hook (feature 22 cross-edit) | T-01 | AC-05, AC-10 green |
| T-06 | BE | Entitlements: grant consumption, mint endpoint (buyer + service contexts), counter increments, extensions (audited), anomaly flagging + unflag | T-05 | AC-06, AC-07, AC-11 green |
| T-07 | BE | Paywall: body-role objects, fragment endpoint (`private, no-store`), preview/host separation byte check | T-05 | AC-07 (fragment half), AC-08 green |
| T-08 | FE | Design manager (template/variant matrix), job queue + status chips, paywall editor, download panel (counter + window), extend dialog; no AI affordance rendered | T-02..T-07 | BA 04 states render; AC-04/AC-05 UI halves |
| T-09 | DevOps/QA | Drill wiring AC-01..AC-12; container drill (Docker + Podman; V1→V2 replay; rollback); OpenAPI diff; secrets grep | T-03..T-08 | G4 + G5 + G7 green |

## Notes

- Build-order gate: dispatch + refund consumers enable only when the 32-40 seams (`order.granted.v1`, `order.refunded.v1`, checkout ship-to) land — the same change that makes orders exist; nothing is stubbed meanwhile (feature 22's seam-absent rationale).
- Feature 16 stays gated: no design-generation endpoint, no credit path, no `source='ai'` value in this change.
- PII: ship-to flows buyer → checkout → shop-service → adapter; creator surfaces and internal status reads never show it; buyer purge scrubs (TL 01 D-8).
- Retry policy: adapter failures retry 3x with backoff, then `failed` + creator alert (NFR-14); callbacks get non-2xx (503) only when the queue is down — the partner retries, and dedupe makes replays free.
- Redis db 9 keys ship here (stream + dedupe + mint single-flight); alert on stream depth 1 000.
- Legacy S0 exit-drill mapping: paid-download drill → AC-06/AC-08; resume-last-lesson drill → feature 24; fee-identity drill → AC-03 seam (feature 22) + feature 26.
