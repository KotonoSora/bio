# Checkout Session - Non-Functional Requirements (feature 33)

Phase 1 BA · Every threshold numeric.

## Performance

| # | Requirement |
|---|---|
| NFR-01 | Quote p95 ≤ 300 ms; create p95 ≤ 500 ms; payment-methods p95 ≤ 150 ms; cancel p95 ≤ 300 ms; status poll p95 ≤ 200 ms (platform envelope from 32). |
| NFR-02 | Expiry sweep cadence ≤ 5 min; a lapsed order is flipped within cadence + 60 s p95; sweep rerun is a no-op by construction. |
| NFR-03 | Eligibility/kill-switch effect ≤ 5 s from flip to `payment-methods` output. |
| NFR-04 | Lead opt-in write dispatch ≤ 60 s p95 post-commit; never blocks create. |

## Correctness guarantees

| # | Requirement |
|---|---|
| NFR-05 | Quote-charged identity holds to the VND across all cells; any drift refuses at create (409) — a different amount is never charged. |
| NFR-06 | Idempotency: keyed creates never duplicate (UNIQUE); keyless collapse within 90 s; key reuse across intents 422; all three properties drilled per handoff kind. |
| NFR-07 | Race safety: cancel, expiry, and grant can settle each order exactly once; no terminal state ever resurrects (drilled with concurrent schedules). |
| NFR-08 | Transition authority: no HTTP surface except create/cancel and no param except verified confirmation (34) changes a row; audit replayable per `orderCode`. |

## Security and privacy

| # | Requirement |
|---|---|
| NFR-09 | Session + RLS scoping on every order read/write; guest rows carry `guest:` ids and are invisible to sessions; cross-user probes denied at API (403) and DB (RLS). |
| NFR-10 | Guest email: stored RLS-protected, masked in logs (never plaintext), HMAC pepper via env/vault with a documented rotation runbook; scrubbed on deletion (02 roster). |
| NFR-11 | Turnstile on anonymous surfaces; rate envelopes (32) enforce quote/create/cancel throttling; secrets grep clean; CSP `default-src 'self'` on checkout/pending. |
| NFR-12 | Ship-to data never appears in logs, list responses, or non-`merch` flows; it crosses to shop only via `order.granted.v1` (23). |

## Operations and localization

| # | Requirement |
|---|---|
| NFR-13 | Degrade: shop context down → product checkout 503 with plan checkout unaffected; fees service down → creation blocked (409 passthrough), no partial rows; sweep down → alert, grants still refuse on lapsed orders. |
| NFR-14 | vi/en copy; amounts numeric (VND integers only); provider names rendered verbatim from `displayName`. |
| NFR-15 | Support drill: any pending order explainable and cancellable from `orderCode` alone within one screen. |
