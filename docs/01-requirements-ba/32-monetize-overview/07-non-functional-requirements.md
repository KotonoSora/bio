# Monetize Overview - Non-Functional Requirements (feature 32)

Phase 1 BA · Every threshold numeric.

## Performance

| # | Requirement |
|---|---|
| NFR-01 | Quote p95 ≤ 300 ms (side-effect-free); payment-methods p95 ≤ 150 ms; checkout create p95 ≤ 500 ms; cancel p95 ≤ 300 ms; order status poll p95 ≤ 200 ms. |
| NFR-02 | Entitlement/ledger visible ≤ 60 s p95 after a verified confirmation; audit append in the same transaction as the transition. |
| NFR-03 | Config flips effective ≤ 5 s p95; reads cached ≤ 5 s; kill-switch delisting ≤ 5 s. |
| NFR-04 | Pending poll cadence 3 s with backoff; paused on hidden tabs; terminal states stop polling. |
| NFR-05 | Notification dispatch ≤ 60 s p95; backlog alert at 5 min lag; grants never wait on sends. |

## Correctness and evidence

| # | Requirement |
|---|---|
| NFR-06 | Conformance: all eight rails × the shared row green before any rail change ships; results recorded per release. |
| NFR-07 | Idempotency: key reuse across intents 422; keyless double-click collapses within 90 s; replay of a verified confirmation is a no-op 200. |
| NFR-08 | Audit retention 24 months; per-`orderCode` trail replayable; secrets/signatures redacted in trail output. |
| NFR-09 | VND integers end-to-end; statement timeout 5 s on money statements; no floating-point columns. |

## Security and secrets

| # | Requirement |
|---|---|
| NFR-10 | Session scoping + RLS on every money read/write; cross-user 403 + RLS deny; anonymous money endpoints Turnstile + per-IP/email throttle. |
| NFR-11 | Secrets via environment/vault only; CI secrets grep (keys, certs, `whsec_`, tokens, PAN patterns) over repo, logs, bundles; quarterly no-downtime rotation. |
| NFR-12 | CSP `default-src 'self'` on checkout/pending/history; provider-native codes and raw payloads never rendered. |

## Operations and localization

| # | Requirement |
|---|---|
| NFR-13 | Degrade: provider rail down → that rail's pendings settle/sweep, other rails unaffected; email provider down → backlog visible, money unaffected; config store down → cached last-good config, flips queue. |
| NFR-14 | vi/en buyer copy; amounts stay numeric (localized formatting only in display); receipts machine-stable. |
| NFR-15 | Accessibility: provider list keyboard-navigable with visible focus; status changes announced (aria-live) on the pending page. |
