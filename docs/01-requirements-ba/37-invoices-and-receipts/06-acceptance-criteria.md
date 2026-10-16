# Invoices and Receipts - Acceptance Criteria (feature 37)

Phase 1 BA · BDD checks mapped to `docs/02-architecture-techlead/37-invoices-and-receipts/04-step-by-step-implementation-plan.md`.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Grant yields receipt | A paid grant on any rail | Grant commits | Receipt email enqueues within the delivery budget; ids, amounts, and provider references match the order; history row links the receipt |
| AC-02 | Print view | An open receipt | The print view renders and prints | Clean A4 output; bilingual blocks; frozen disclaimer shown; page `no-store` |
| AC-03 | Tamper/IDOR probe | Two users, one guest order | Cross-user fetch and tampered tokens are attempted | 403/404 parity; token tamper = 401; RLS denies cross-user rows; no enumeration response differs |
| AC-04 | Guest round trip | A guest product order | Link opens, 31 days pass, resend submits the right then wrong email | Renders; then 401 + resend; always-202; only the matching email received a fresh link |
| AC-05 | Unsubscribed buyer | Marketing opt-out set | A grant occurs | Transactional receipt still sends; no marketing class involved |
| AC-06 | Outage drill | Email pipeline down during grants | Grants proceed | Sends queue with backoff; backlog visible; zero grants blocked |
| AC-07 | Proforma conversion | An open proforma, two rails attempted | Both orders reach grant | Both orders link the proforma; exactly one `converted` status; single grant each; amounts match to the dong |
| AC-08 | Price drift | Catalog price changes mid-validity | Checkout runs with the ref | Live amount charged; proforma superseded; `proformaRepriced=true`; one-click reissue visible |
| AC-09 | Expiry drill | A lapsed proforma | Payment quotes its ref | Converts nothing; rail's unmatched-transfer flow applies (40); support can read the lapsed doc |
| AC-10 | Duplicate request | Open proforma in scope | Second request | Existing document returned; no duplicate rows |
| AC-11 | Seller guard | `billing.seller` unset | Issuance attempted | 409 `seller_profile_absent`; no letterhead renders anywhere |
| AC-12 | Words + no-PAN gates | Formatter table + rendered artifacts | Tests run | All table rows match (0, 15, 100, 1 000, 1 000 000, 999 999 999, random); grep finds zero card/bank identifiers in templates |
| AC-13 | Credit-note rendition (38-dependent) | A refunded order after 38 ships | Receipt reopens | Linked credit notes + running balance render append-only; original bytes never change |
