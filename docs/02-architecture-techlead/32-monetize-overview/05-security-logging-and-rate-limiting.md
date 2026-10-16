# Monetize Overview - Security, Logging, and Rate Limiting (feature 32)

Phase 2 TL · BA sources: `docs/01-requirements-ba/32-monetize-overview/` (BR-05/09/10/11/12, NFR-10..NFR-12). Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | Scoping: session + RLS on every money read/write; cross-user 403 + RLS deny; webhooks resolve ownership from stored rows only — request-supplied identity is ignored by construction | AC-04; RLS probes |
| S-2 | No-default integrity: closed registry + CI grep gate + unit tests red on any injected default, fallback, or favorite ordering | AC-02; gate drill |
| S-3 | Secrets: PSP/email keys via environment/vault only; CI secrets grep over repo/logs/bundles; quarterly no-downtime rotation; never logged or embedded | AC-04; rotation drill |
| S-4 | Config flips: ops role re-verified per request; mandatory reason; before/after audit; rate-limited; effect ≤ 5 s | flip drill |
| S-5 | Trace route: ops-scoped; output redacts secrets/signatures; no card data exists to leak (never stored) | AC-10 |
| S-6 | Notification pipeline: transactional class only (`marketing` → 422); template-bound payloads (no free-form body); recipient validation; lead-bound sends honor 27's suppression; provider events signature-verified | AC-08; send probes |
| S-7 | Money integrity: server re-prices before create (quote = charged); amount tampering rejected at confirmation (40); client amounts never trusted | AC-03 |
| S-8 | Audit integrity: append-only (`app_role` gets no update/delete grant on `audit_log`); redaction at write time | schema attr |
| S-9 | Abuse: Turnstile on anonymous money surfaces; per-IP/session throttles; WAF; idempotency abuse bounded by rate + fingerprint windows | AC-04 |
| S-10 | Logging: ids/counters/outcomes only; no secrets; recipient addresses hashed in send logs | log greps |

## Rate envelopes

| Route class | Envelope | On breach |
|---|---|---|
| Quote/create | 30/min per session; 10/min per IP (anonymous surfaces) | 429 |
| Status poll | 120/min per order | 429 |
| Payment-methods | 60/min per session | 429 |
| Ops config flip | 30/h per operator | 429 + audit |
| Internal notifications | 600/min per caller service | 429 |

## Degrade matrix

| Condition | Behavior |
|---|---|
| Config store (DB) down | last-good config served from cache; flips impossible; ops banner shows the stale version |
| Redis down | in-process last-good memo (≤ 5 s) serves; flips queue; visible alert |
| Notification provider down | backlog metric; grants/money unaffected |
| `billing_db` down | money routes 503; recovery replays orders/events (orders are the truth) |
| Conformance harness down | no rail changes ship (fail-closed) |
| Config flip invalid/unauthorized | 403/422 `flip_invalid`; nothing applied; attempt audited |

## Gates

- G4: OpenAPI additive diff; ops routes role-guarded; internal routes service-JWT only.
- G5: IDOR/XSS/abuse/secrets probes; RLS; flip and kill-switch drills.
- G7: AC-01..AC-10; container pair; secrets grep green; registry/no-default greps green.
