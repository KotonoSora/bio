# QR Codes - Non-Functional Requirements (feature 30)

Phase 1 BA · Every threshold numeric.

## Performance

| # | Requirement |
|---|---|
| NFR-01 | Single render p95 ≤ 2 s end-to-end (render → file store → log row); 100-code pack p95 ≤ 30 s; both include file persistence. |
| NFR-02 | Downloads stream (no in-memory assembly of packs client-side); sheet builder paginates label rows at 25. |
| NFR-03 | Scan resolution adds zero QR-specific latency: scans ride the short-URL redirect SLA from feature 29 (unchanged). |

## Quality floors (hard)

| # | Requirement |
|---|---|
| NFR-04 | Error-correction ≥ M everywhere; H required for S and poster classes. |
| NFR-05 | Quiet zone ≥ 4 modules on every export; module size ≥ 0.4 mm at the declared physical size; contrast ratio ≥ 7:1, dark-on-light only. |
| NFR-06 | Floor violations are refused pre-render (422) — zero below-floor bytes ever exist. |

## Limits and retention

| # | Requirement |
|---|---|
| NFR-07 | Pack size ≤ 100 codes; sheet ≤ 24 codes per page; labels required 1:1 with codes. |
| NFR-08 | Render files live 30 days (re-renderable cache), then sweep; render log rows live with the link's tombstone (purge cascade on exceptional delete); downloads via grants with 15-min TTL. |
| NFR-09 | Quotas: owner renders 60/h; ops packs 10/day; both 429 past cap with audit on ops. |

## Security and privacy

| # | Requirement |
|---|---|
| NFR-10 | Owner-scoped reads/writes (RLS + scoping); cross-user QR of someone else's link → 403/404 with zero rows. |
| NFR-11 | No visitor data exists in this feature: QR targets are fixed URLs; scan counting and any signals belong to feature 31 (hashed only). |
| NFR-12 | Preview path never produces downloadable artifacts; server is the only renderer of official codes (encoder grep gate). |

## Localization and accessibility

| # | Requirement |
|---|---|
| NFR-13 | vi/en copy; physical-size captions localized; floor messages name numeric floors. |
| NFR-14 | Picker flows keyboard-operable; floor messaging announced (aria-live); labels readable at print size (≥ 8 pt equivalent in sheet SVG). |
