# Shop Fulfillment - Security, Logging, and Rate Limiting (feature 23)

Phase 2 TL · BA sources: `docs/01-requirements-ba/23-shop-fulfillment/` (BR-12..BR-16, NFR-05..NFR-13). Baseline: `docs/02-architecture-techlead/00-platform-baseline/08-security-baseline.md`; gates: `docs/02-architecture-techlead/00-platform-baseline/06-release-gates.md`.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | IDOR + RLS: owner scope by `user_id`; buyer scope by `buyer_user_id`; `svc` context only on server-side paths (dispatch, mint file read, callback worker) | AC-06; API + direct-PG probes |
| S-2 | Preview leak: automated byte check — preview HTML/JSON of a paywall product contains zero locked-body bytes; guessed blob keys do not resolve; no bucket listing | AC-08 |
| S-3 | Upload safety: bounded reads, magic-byte sniff, random keys, no SVG/HTML/JS/exe; bodies are sanitized markdown only | AC-04, AC-05 |
| S-4 | Signed URLs: single-use, ≤ 15 min, bound to order + file version + buyer; never stored, never logged; no PostgreSQL write on the byte path | AC-06, AC-08 |
| S-5 | Callback safety: signature verify before enqueue; dedupe by event id (24 h); queue-mediated — no direct DB writes; tamper → 401 with zero state change | AC-03 |
| S-6 | Anomaly + quotas: mint 10/min + 100/day per buyer; many-IPs-one-entitlement flags the entitlement; flagged → 403 + review; unflag audited | AC-11 |
| S-7 | Audit: dispatch, transitions, extensions, revocations, version publishes append-only, 24-month retention; ids only, never content | BR-15 |
| S-8 | PII: ship-to minimized + encrypted at rest; never in logs, creator surfaces, or internal status reads; buyer purge scrubs (D-8) | AC-12 |
| S-9 | Scope guard: no fee values invented here (26); no AI credential paths (16 gated); secrets env/vault only | Fee/AI greps; secrets audit |

## Rate envelopes

| Route class | Envelope | On breach |
|---|---|---|
| Design/file uploads | 30/h/user | 429 + `Retry-After` |
| Access-link mints | 10/min + 100/day per buyer | 429; anomaly rules still apply |
| Extensions | 20/day/user | 429 |
| Partner callbacks | 120/min per partner | 429; partner retries with backoff |
| Internal reads | service-level budget | 429 + trace |

## Degrade matrix (fail safe, never fail open)

| Condition | Behavior |
|---|---|
| Partner selected later | Dispatch paused by config: jobs stay `pending_print`; no order is blocked or faked |
| Partner unreachable | Bounded retries (3) → `failed` + creator alert; buyer sees the trail |
| Redis (db 9) down | Callbacks answer 503 (partner retries); mint single-flight degrades to direct mint (entitlement check remains); dispatch rides event redelivery |
| MinIO down | Uploads 503; downloads fail closed (no URL minted) |
| Event bus down | No dispatch/refund consumption; orders unaffected; jobs appear after redelivery |
| Analytics down | Nothing in this feature depends on analytics |

## Threat checklist

Tampered/replayed callbacks, signed-URL sharing, guessed or generic download endpoints, IDOR on entitlements/jobs, version confusion (old-version access after supersede), paywall preview leakage, PII in logs, partner impersonation, refund-then-redownload (revoked), queue poisoning (signature first, `jobRef` validated in the worker).

## Gates

- G4: OpenAPI diff additive; codes registered; callback route allowlisted in the gateway and nowhere else.
- G5: S-2 byte check, S-3 upload matrix, S-4 URL matrix, S-5 tamper + replay drill.
- G7: AC-01..AC-12 attach to the tag; container drill (Docker + Podman) per baseline release gates.
- Greps: zero fee literals in this directory; zero `source='ai'`; secrets clean; ship-to absent from logs and creator DTOs.
