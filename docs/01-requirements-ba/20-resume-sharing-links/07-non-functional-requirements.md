# Resume Sharing Links - Non-Functional Requirements (feature 20)

Phase 1 BA · Quantified thresholds enforced in `docs/02-architecture-techlead/20-resume-sharing-links/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

| NFR ID | Area | Requirement |
|---|---|---|
| NFR-01 | Helper latency | `GET share-links` p95 `<= 300 ms` (derivation is pure computation over three cached internal reads); mint endpoint p95 `<= 400 ms` |
| NFR-02 | Mint idempotency | At most one short-link row per `(owner, document)` ever; second mint returns the same code with `created:false`; mint-count asserted in the drill |
| NFR-03 | Purge SLA | One-flag revocation degrades all five types within `<= 60 s` end-to-end; re-publish restores within the same bound |
| NFR-04 | Rename | Destination rewrite lands `<= 60 s` after the event; lost-event backstop = the old-username 301 (one extra hop, never a dead link) |
| NFR-05 | Standard surface | `/cv` render budget/TTL owned by feature 10 (60 s cache); custom hosts ride feature 04's serving-stop bound (`<= 60 s`, fail-closed) |
| NFR-06 | Unlimited | No counters, credits, or gates exist on views, own-resume mints, or exports (structural: no quota code path for authenticated resume shares); anonymous abuse controls ride owning majors |
| NFR-07 | A4 view | Frame binds at open (`<= 200 ms` for a 2MB snapshot); print path issues zero network calls; zero PDF code both sides (grep gates shared with feature 18) |
| NFR-08 | Audit | Mint, revocation-affecting flips, domain serving changes, block link toggles: existing owning-feature audit rows (24-month retention); this feature adds no new audit table |
| NFR-09 | Localization | vi/en parity for dialog, captions, and error copy; URL shapes are language-free |
| NFR-10 | Accessibility | Copy buttons `>= 44 px` touch targets; state captions present as text (not color-only); the dialog is keyboard-traversable in row order |
