# Resume Portability - Non-Functional Requirements (feature 21)

Phase 1 BA · Quantified thresholds enforced in `docs/02-architecture-techlead/21-resume-portability/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

| NFR ID | Area | Requirement |
|---|---|---|
| NFR-01 | Export streaming | Time-to-first-byte `<= 500 ms` p95; chunked with backpressure - a 10-year history never materializes in memory; abort-safe (client disconnect leaves zero partial files) |
| NFR-02 | Import throughput | Dry-run p95 `<= 2 s` for a 2MB file (bounded by the cap); confirmed write p95 `<= 5 s`; validation rejections return `<= 300 ms` after parse failure |
| NFR-03 | Size/parse safety | 2MB cap enforced pre-parse; JSON parse timeout 5 s; parse bombs (deep nesting) rejected as `import_malformed` without OOM |
| NFR-04 | Adapter fetch guardrails | 10 s timeout, 3-redirect cap, 1MB response cap, private-range egress blocked (SSRF); fetch failure counts `import.rejected{reason}` |
| NFR-05 | Quotas | Imports + adapters: 20/day/user shared (`429 import_quota_exhausted`); exports: none (unlimited by design, audit-only) |
| NFR-06 | Idempotency | Retry of a partially completed restore completes relink without duplicating documents (envelope `export_id` keyed) |
| NFR-07 | Fixture gate | Every build: fixture import on an empty database `<= 30 s`; round-trip diff asserted clean (ids/timestamps excepted) |
| NFR-08 | Audit | Export/import/adapter rows append-only, 24-month retention; export rows counted for exfiltration forensics |
| NFR-09 | Localization | vi/en parity for wizard, review, and error copy; envelope codes language-neutral |
| NFR-10 | Accessibility | Wizard steps keyboard-completable; warnings list readable without pointer (text labels, not color-only); file dropzone has a button equivalent |
