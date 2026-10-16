# Promo Codes - Non-Functional Requirements (feature 39)

Phase 1 BA · Numbers with units; enforcement: `docs/02-architecture-techlead/39-promo-codes/05-security-logging-and-rate-limiting.md`.

| ID | Requirement | Threshold |
|---|---|---|
| NFR-01 | Validate latency | p95 <= 150 ms server-side (Turnstile excluded); hash lookup never scans |
| NFR-02 | Timing normalization | Non-expired rejection classes indistinguishable: response-time spread <= 50 ms measured over interleaved trials |
| NFR-03 | Pause latency | Pausing blocks new redemptions within <= 5 s (no status cache; fresh row read per request) |
| NFR-04 | Cap integrity | Exposure never exceeds `budget_cap_vnd` or `max_redemptions` under any concurrency (DB CHECK + guarded updates; race drill proves it) |
| NFR-05 | Generation scale | Batch up to 100k codes in <= 60 s; atomic all-or-nothing; zero collisions at 31-symbol 13-char alphabet |
| NFR-06 | Entropy | >= 64 bits per code; stored only as salted HMAC-SHA256 hashes; plaintext exists once |
| NFR-07 | Export discipline | One plaintext export per batch, acknowledged + audited; zero plaintext persisted server-side |
| NFR-08 | Redemption atomicity | Budget decrement at create and use-consumption at grant each commit with their order/ledger writes or not at all |
| NFR-09 | Compensation exactness | A coupon order that ends without a grant restores its decrement exactly once (guarded by the terminal transition; sweep reruns restore nothing twice) |
| NFR-10 | Credits application | Unconsumed days apply <= 24 h after eligibility arises (daily job); idempotent on rerun; consumed once with subscription linkage |
| NFR-11 | Audit retention | Campaign/coupon/goodwill/credit lifecycle rows retained 24 months with actor identity; auto-pauses record the system actor |
| NFR-12 | Privacy | Buyer PII never in partner/marketing aggregates; codes hashed; emails masked in logs; fraud signals count-based |
| NFR-13 | Scoping | Ops routes role-checked per request; credits owner-read only; no buyer enumeration surface exists (by absence) |
| NFR-14 | Rate limits | Validate bounded by shared checkout budgets (per-IP/email); ops actions 60/h/operator; 429 with `Retry-After` |
| NFR-15 | Notification | Credit-application and campaign alerts <= 60 s via pipeline metric classes; transactional receipts per 37/38 rules |
| NFR-16 | Localization | Buyer-facing copy bilingual EN+VI; refusal copy plain-language; figures + words on VN surfaces |
| NFR-17 | Degrade | Job down -> pauses still block (fresh reads), credit days wait, coupon expiry lags only its sweep; Redis down -> throttles degrade to conservative fixed windows, validation still correct; no state forks exist |
| NFR-18 | Observability | Metrics: validations by outcome class, redemptions by campaign/channel, burn %, in-flight honored, voids, credits applied, auto-pauses — zero PII labels |
