# Payment Rails - Security, Logging, and Rate Limiting (feature 40)

Phase 2 TL · BA sources: `docs/01-requirements-ba/40-payment-rails/` (BR-01..BR-24, NFR-01..NFR-18). Inherits the platform baseline (`payment-security.md`'s checklist migrates here as the probe battery). Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | Scoping: buyers/users resolve from stored orders only, never request identity; ops routes re-check the restricted role per request; no buyer-facing event enumeration exists (absence); exceptions owner-read where attributable | Ownership drills + absence audit |
| S-2 | Signature-first: raw-byte verification, constant-time compare, timestamp tolerance (stripe), no JSON parse before verification, fail-closed on absent config (never a permissive default) | Tamper matrix × 8 rails |
| S-3 | Anti-replay: insert-first `(provider, event_ref)` UNIQUE; redelivery reads the original outcome; concurrent duplicates block then no-op | AC-02 replay drill |
| S-4 | Amount binding: provider + amount + currency asserted before any edge; ±1 VND refuses with a quarantine metric | AC-03 |
| S-5 | Single writer: no rail code writes `orders.status`; every settle/dispute edge runs 34's guard with actor + assertions | Grep gate + drill |
| S-6 | Wallet hygiene: tokens/cryptograms never stored or logged (PSP references only); merchant sessions single-use; exactly one charge attempt per order (idempotency key = orderCode) | AC-04, AC-15 + log scan |
| S-7 | Ops authorization: link runs the guard with ops actor + reason; refund-record demands evidence ref + second operator; dismiss demands reason; every mutation audited | AC-07 + audit probes |
| S-8 | Kill-switch discipline: delists new checkouts/charges only; intake, sweeps, verifies, refunds never blocked; flips audited via 32's config path | AC-10 |
| S-9 | Secrets: per-rail secrets/certs via vault/env; never logged/echoed; rotation with dual-read ≥ 24 h; quarterly drill; secrets grep gates CI | AC-12 |
| S-10 | Probe battery: nine probe classes per rail run pre-deploy; any red blocks release; reports archived per run | AC-11 |
| S-11 | Privacy: payloads persist as refs + hashes only; PAN/token/cryptogram/secret material zero in logs/audits/metrics; PII masked (first/last 4) outside ops case detail | Privacy scans |
| S-12 | Dispute integrity: deadline mandatory while open; evidence immutable once submitted; auto-loss handling bounded ≤ 15 min after deadline; win/loss edges guard-serialized | AC-09 + deadline drill |

## Probe battery (per rail, pre-deploy, archived report)

| Probe | Expected |
|---|---|
| IDOR: foreign order in payload/session | No ownership change, no settle, 404-class outcome |
| Bad signature/token (each scheme) | Refusal code, zero writes, metric |
| Tampered amount (±1 VND) | Refusal/mismatch hold, zero grant |
| Unknown reference | Bank: exception row; hosted: durable no-op |
| Replay (same event ref, 1× and 10× concurrent) | Single effect, identical responses |
| Double-submit (wallet) | One PSP charge, one grant |
| Cancel/expiry race (event lands mid-sweep) | Guard decides once; no double edge |
| Kill-switch flip mid-checkout | Delist ≤ 5 s; in-flight settles |
| Secrets grep (repo + logs + audit rows) | Zero material |

## Rate limits (gateway + app; Redis-backed `bill:rl:*`)

| Surface | Limit | On breach |
|---|---|---|
| `/webhooks/*` intake | 600/min/IP + 120/min per rail key, burst 60/s | 429/frozen refusal code; redelivery invited |
| Signature-mismatch clamp | 20/min/IP per rail | Refusal code + alert on burst |
| Wallet authorize | 5/h/order + 20/h/user | 429 `throttled`; sheet honest failure |
| Ops verify | 1/30 s/order + 30/h/operator | 429 `throttled` |
| Ops reads | 120/h/operator | 429 |
| Ops mutations | 60/h/operator (link/refund/dismiss/evidence) | 429 |

## Degrade matrix

| Failure | Behavior |
|---|---|
| Rail/PSP API down | Sweeps back off ≤ 4× tick + alert; on-demand verify 502 `verify_unavailable`; intake unaffected |
| Redis down | Throttles degrade to conservative fixed windows; correctness unchanged; mismatch clamps stay |
| `billing_db` down | Refuse acks (transient) rather than settle unguarded; no partial writes exist |
| Apple cert expired | Wallet rail refused at validate-merchant; ops delists via kill switch; existing payments still settle |
| Notification pipeline down | Alerts queue with backlog; settlement unaffected |
| Sweeper down | Stale pendings accumulate; on-demand verify still works; alert after 2 missed ticks |

## Audit and observability

Audits (24-month spine): `rail.event_rejected` (class only, no payload), `rail.exception_opened/linked/refunded/dismissed`, `dispute.opened/evidence_submitted/won/lost`, `plan.chargeback_reversed` (38's helper), kill-switch flips + `billing.rails` flips (32's config audit), rotation completions. `provider_events` rows are the delivery ledger. Metrics: `rail_verify{provider,outcome}`, `rail_duplicates`, `sweep_settled/stale{provider}`, `exceptions_open{provider,reason}` + age, `disputes{status}`, `grace_links`, `wallet_charge{outcome}`, `kill_switch_flips`, `secret_age_days{provider}`. Zero PII labels; refusal classes internal-only.

## Support answers

"Why is my payment not showing?" → order trace + provider_events + sweep log; confirming copy until a verdict exists. "I typed the wrong content/wrong amount" → exception row present, ops resolution path; never promise auto-credit. "I paid after it expired" → grace window explainer + link/refund outcome. "Why reversed?" → dispute case facts (deadline, status), device-truth copy only. "Why is the rail missing?" → kill-switch state + re-enable ETA.
