# Account and Access - Non-Functional Requirements (feature 01)

Phase 1 BA · Quantified thresholds enforced in `docs/02-architecture-techlead/01-account-and-access/`. Baseline envelopes: `docs/02-architecture-techlead/00-platform-baseline/08-security-baseline.md`; scale gates: `docs/02-architecture-techlead/00-platform-baseline/07-scale-out.md`.

| NFR ID | Area | Requirement |
|---|---|---|
| NFR-01 | Login latency | Platform-side callback handling p95 `≤ 300 ms` (IdP leg excluded); sign-in initiation 302 p95 `≤ 100 ms` |
| NFR-02 | Session validation | Edge validation p95 `≤ 5 ms` (single Redis read); cache-miss introspect path p95 `≤ 150 ms` |
| NFR-03 | Revocation | A revoked session stops working within `≤ 60 s` (in-flight JWT TTL bound); immediate for the issuing client |
| NFR-04 | Session lifetimes | Idle timeout 7 days sliding on activity; absolute lifetime 90 days; both enforced server-side, never by cookie TTL alone |
| NFR-05 | Step-up freshness | `≤ 10 min`; stale or absent -> `403 step_up_required`; no action executes partially |
| NFR-06 | Single-use tokens | Auth `state`/PKCE 10 min TTL; recovery invitation 15 min; contact-email code 15 min with 5 attempts; all single-use, replay fails closed |
| NFR-07 | Entitlement staleness | End-to-end `≤ 30 s` (consumer cache `≤ 30 s` + snapshot resolution); any miss clamps to free; `entitlement_snapshot_miss` counted |
| NFR-08 | Role effect | Org role changes effective `≤ 60 s`; measured against the baseline E3 load gate before draft sign-off |
| NFR-09 | Rate envelopes | Auth routes 20 req/min/IP; owner API reads 60 req/min/user; contact-email 5/hour/user; step-up 10/min/user; `429` + `Retry-After` beyond |
| NFR-10 | Enumeration uniformity | Auth responses byte-equivalent across existing/non-existing accounts; interleaved timing p95 difference `≤ 50 ms` |
| NFR-11 | Audit | Append-only retention 24 months (baseline); no UPDATE/DELETE grant for the app role; every listed event carries actor + target |
| NFR-12 | Masking | Session list stores hashed ip-class and coarse device label only; logs carry route/status/trace - never emails, names, tokens, or raw IPs |
| NFR-13 | Fail-closed posture | Session/step-up/RLS checks never fail open; unreachable check -> `401`/`503`, never a degraded owner path; public routes unaffected by auth-layer faults |
| NFR-14 | Localization | All user-visible auth copy in vi and en; error `code` strings stable across locales; no localized text in contracts |
| NFR-15 | Capacity | Login and session paths hold NFR-01/02 bounds at the baseline E3 gate (100k concurrent mix); memory per session key `≤ 256 B` at the edge store |
| NFR-16 | Secret rotation | Provider secrets and JWKS rotate with zero downtime (dual-config, dual-key); no deploy required for provider credential change |
| NFR-17 | Session quota | `≤ 50` active sessions per user; on excess the oldest non-current session is revoked automatically and the event audited |
| NFR-18 | Recovery SLA | Support triggers a recovery invitation within one business day of verified evidence; every assisted recovery audit-logged with the evidence reference |
