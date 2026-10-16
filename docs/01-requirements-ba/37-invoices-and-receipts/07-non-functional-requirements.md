# Invoices and Receipts - Non-Functional Requirements (feature 37)

Phase 1 BA · Numbers with units; enforcement: `docs/02-architecture-techlead/37-invoices-and-receipts/05-security-logging-and-rate-limiting.md`.

| ID | Requirement | Threshold |
|---|---|---|
| NFR-01 | Receipt email enqueue | <= 60 s after grant commit; delivery best-effort via the pipeline with backoff retries |
| NFR-02 | Receipt render latency | p95 <= 250 ms server-side; proforma render p95 <= 250 ms |
| NFR-03 | Proforma issuance latency | p95 <= 300 ms (excluding nothing; no provider calls exist) |
| NFR-04 | Token validity | Guest receipt link 30 days exactly; resend mints a fresh 30-day link; no longer-lived token exists |
| NFR-05 | Proforma validity | `validityDays` default 7, validated 1..30; expiry pass runs daily with per-row audits |
| NFR-06 | Print output | A4 via `window.print()` only; zero server-side PDF/generation dependencies (grep gate) |
| NFR-07 | Localization | Every artifact renders EN+VI stacked blocks; no client strings; sentence order preserved per language |
| NFR-08 | Privacy | Page `no-store`; receipt URLs non-guessable (non-sequential order code + owner scope or signed token); logs mask emails (`a***@domain`) and never log tokens |
| NFR-09 | No sensitive data | Zero card/bank/PAN fields exist anywhere; templates grep clean each build |
| NFR-10 | Disclaimer integrity | Frozen verbatim copy in one source module; both languages; changing it requires a spec amendment |
| NFR-11 | Anti-enumeration | Resend always-202; generic copy; timing asymmetry < 50 ms between match and no-match paths (constant work) |
| NFR-12 | Rate limits | Resend: 3/h/order + 5/h/IP; proforma create: 10/h/user; receipt reads: 60/h/user; all 429 with `Retry-After` |
| NFR-13 | Retention | Receipts follow order retention (02's roster); proformas retained while their orders/reference value holds; `no-store` never caches |
| NFR-14 | Audit | Proforma lifecycle (`proforma.created`, `proforma.superseded`, `proforma.converted`, `proforma.expired`) audited 24 months; receipt sends logged per-send (to masked, template, orderCode, result) |
| NFR-15 | Idempotency | Receipt enqueue and proforma expiry are rerun-safe; duplicate refund webhooks (38) mint no duplicate notes |
| NFR-16 | Degrade | Email seam down, pipeline down, or job down: money truth unaffected; receipts always renderable by owners |
