# Resume Templates - Non-Functional Requirements (feature 18)

Phase 1 BA · Quantified thresholds enforced in `docs/02-architecture-techlead/18-resume-templates/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

| NFR ID | Area | Requirement |
|---|---|---|
| NFR-01 | Gallery | Catalog read p95 `<= 150 ms`; gallery first paint `<= 300 ms` on the dashboard baseline; memory-cached client-side, no Redis |
| NFR-02 | Preview | Template switch re-render `<= 150 ms` for a 2MB snapshot on the baseline machine; zoom/collapse `<= 50 ms` interactions |
| NFR-03 | Print | Print tree build `<= 500 ms` for a 2MB snapshot; print path issues zero network calls; A4 margins 12-18 mm |
| NFR-04 | SSR | `/cv` render rides feature 10's budget (p95 `<= 300 ms` cached, 5 s total deadline); text parity diff clean per release |
| NFR-05 | Assets | Thumbnails `<= 100 KB` each; template assets fingerprinted + immutable (1-year cache); no per-user asset generation |
| NFR-06 | Registry | Boot validation `<= 100 ms`; unused token or duplicate id fails boot; catalog and renderers ship in one bundle (CI consistency gate) |
| NFR-07 | Accessibility | Token palettes meet contrast `>= 4.5:1` for body text on screen; print colors legible in grayscale (spot-check per template) |
| NFR-08 | Rates | Catalog read 600/min/user; template selection rides feature 17's write envelope (`PUT` 60/h/user); `429 throttled` beyond |
| NFR-09 | Grading | Each template's Chrome + Safari print fixtures archived per release; a template without fixtures cannot ship |
| NFR-10 | Localization | vi/en parity for gallery/preview/print copy; template names are brand-neutral |
