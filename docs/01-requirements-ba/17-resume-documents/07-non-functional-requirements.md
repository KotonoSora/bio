# Resume Documents - Non-Functional Requirements (feature 17)

Phase 1 BA · Quantified thresholds enforced in `docs/02-architecture-techlead/17-resume-documents/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

| NFR ID | Area | Requirement |
|---|---|---|
| NFR-01 | Owner latency | List p95 `<= 200 ms`; snapshot save p95 `<= 500 ms` at the 2MB cap; version read p95 `<= 300 ms` from PG |
| NFR-02 | Capture | Profile read `<= 2 s` timeout (then `503`); capture + sanitize + insert `<= 800 ms` p95; no partial versions ever |
| NFR-03 | Archived retrieval | Stated and delivered `<= 5 s` p95 for archive-served versions; timeout produces a clear error, never a silent miss |
| NFR-04 | Reprint fidelity | Byte-identical reprint of any version (excluding print header/footer), verified per release with the immutability drill |
| NFR-05 | Archive job | Daily 03:20 UTC; single-flight; processes `<= 10k` documents in `<= 5 min`; move-then-delete order; write verified before delete |
| NFR-06 | Sizes | `snapshot_json <= 2MB` (rejected `422` before insert); title `<= 120`; version count unbounded but archived past 30 days (non-published) |
| NFR-07 | Public surface | `/cv` render budget + TTL owned by feature 10 (60 s cache); purge effect `<= 60 s` after publish/unpublish; parity `404` for absent/private |
| NFR-08 | Rates | Create/duplicate 60/h/user; snapshot save 30/h/user; reads 600/min/user; `429 throttled` + `Retry-After` beyond; Turnstile on create hotspots |
| NFR-09 | Audit | Create, save, restore-affecting writes, publish/unpublish, delete, archive moves: append-only audit, 24-month retention |
| NFR-10 | Purge | Feature 02 participant: rows + archive files deleted; certificate counts per table + files; `<= 10 min` for a typical account |
| NFR-11 | Localization | vi/en parity for all client-facing copy; error codes language-neutral |
