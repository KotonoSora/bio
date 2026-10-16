# Invoices and Receipts - Exception Handling and Errors (feature 37)

Phase 1 BA · Failure and edge behavior. Rules: `docs/01-requirements-ba/37-invoices-and-receipts/02-user-stories-and-business-rules.md`. Codes follow RFC 9457; exact JSON in `docs/02-architecture-techlead/37-invoices-and-receipts/03-api-contracts-and-payloads.md`.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Receipt requested for a pending/failed/cancelled order | Error | 409 `receipt_unavailable` | "Your receipt appears once payment is confirmed." |
| Receipt requested for another user's order (session) | Error | 403/404 parity with 34 (`order_not_found`); no existence leak | "We couldn't find that receipt." |
| Guest token expired or tampered | Error | 401 `receipt_link_expired`; resend form offered | "This link has expired - request a new one." |
| Guest token for an order that later refunded | Happy edge | Renders with the credit-note section (38) | - |
| Resend with a non-matching email | Degrade | Always 202; nothing sends; metric only | "If that email matches our records, we've sent the link." |
| Resend without Turnstile / challenge failure | Error | 422 `challenge_failed` | "Please complete the check and try again." |
| Resend burst | Error | 429 `throttled` + `Retry-After` | "Too many attempts - try again later." |
| Receipt email recipient unresolvable (no email on order, seam down) | Degrade | Skip + `receipt_notify_failed` metric; no guessed address; print link still reachable by owner | - |
| Email pipeline down at grant | Degrade | Enqueue retries with backoff; grant and receipt rendering unaffected; backlog metric alerts | - |
| Proforma issuance with no seller config | Error | 409 `seller_profile_absent` | "Quotes aren't available right now." |
| Proforma for an unpublished term/absent product | Error | Passthroughs: 409 `plan_not_published` / 404 `product_absent` | "That item isn't available for a quote." |
| Second quote request while one is open in scope | Happy edge | 200 with the existing document (no duplicate) | "You already have an open quote." |
| Proforma print view requested by a foreign session | Error | 404 `proforma_absent` | "We couldn't find that quote." |
| Checkout with an expired/foreign/absent `proformaRef` | Degrade | Ref ignored; checkout proceeds at live price; no enumeration signal | - (silent by design) |
| Checkout amount drifted from the quote | Edge | Proforma superseded at create; `proformaRepriced=true`; live amount charged with a visible notice | "The price changed since your quote - this is the current price." |
| Refund lands after a proforma payment | Edge | Conversion stands (first grant won); credit note path proceeds normally | - |
| Two orders race to convert one proforma | Edge | Row lock + status guard: first grant flips to `converted`; later flips are no-ops | - |
| Expiry job crashes mid-batch | Degrade | Per-row updates committed stay; remainder next run; zero duplicate audits | - |
| VAT requested (no flow exists) | Error | 404 (no route ships while gated) | - (design-only until prerequisites sign off) |
