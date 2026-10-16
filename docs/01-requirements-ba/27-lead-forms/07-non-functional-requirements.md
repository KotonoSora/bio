# Lead Forms - Non-Functional Requirements (feature 27)

Phase 1 BA · Every threshold numeric.

## Performance

| # | Requirement |
|---|---|
| NFR-01 | Submit ack p95 ≤ 300 ms (outbox write only); inbox landing p95 ≤ 5 s after ack; confirm POST p95 ≤ 300 ms. |
| NFR-02 | Consumer batches ≤ 50 rows every 2 s; retry backoff 2 s → 4 s → 8 s → … → 24 h drop with metric. |
| NFR-03 | Inbox feed p95 ≤ 300 ms for 100 000 leads per creator (newest-first btree); export surfaces are feature 28's budgets. |
| NFR-04 | Double opt-in email dispatch ≤ 60 s after capture (transactional class, forward pipeline). |

## Limits and quotas

| # | Requirement |
|---|---|
| NFR-05 | Velocity cap 100 submissions/min per block per source → 15-min pause; re-request cap 3/h per address; abuse-signal TTL 24 h. |
| NFR-06 | Field caps: email ≤ 254, name ≤ 80, phone ≤ 20, note ≤ 500, headline ≤ 120, offerCopy ≤ 280, submitLabel ≤ 40, successMessage ≤ 200. |
| NFR-07 | Token TTL 48 h; token is single-purpose (confirm only); outbox retention 24 h (queued) / 30 days (done). |
| NFR-08 | Audit retention 24 months; append-only events. |

## Security and data

| # | Requirement |
|---|---|
| NFR-09 | Deny-by-default; owner scoping + RLS on every read; IDOR 403 with zero rows; no cross-profile leakage. |
| NFR-10 | Anti-oracle: uniform 202 body/timing for accepted/duplicate/unknown; enumeration analysis shows no distinguishably-correct request. |
| NFR-11 | Sanitization before storage; no reflection of visitor input in success states; CSP applies to the capture surface. |
| NFR-12 | PII minimization: email is the only mandatory personal field; IP addresses are stored hashed for abuse signals only (24 h TTL); secrets env/vault only. |

## Sessions, localization, accessibility

| # | Requirement |
|---|---|
| NFR-13 | Capture surface is anonymous (no session); the confirm page carries the token in the URL and POSTs it; creator surfaces are session + CSRF. |
| NFR-14 | vi/en copy; error and success strings localized; the form block inherits page locale. |
| NFR-15 | Form fields keyboard-operable with visible labels; success state announced (aria-live); confirm page readable without JS beyond the POST button. |
