# Automate Contacts - Security, Logging, and Rate Limiting (feature 43)

Phase 2 TL · BA sources: `docs/01-requirements-ba/43-automate-contacts/` (BR-01..BR-12, NFR-01..NFR-11). Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | Scoping: owner RLS on contacts/events; ops aggregates only (raw access absent by design); removal-center tokens are per-contact and expiring | AC-05 + RLS probes |
| S-2 | Single gate: `EligibilityCheck` is the only sender gate (status + consent class); grep gate blocks parallel checks | Single-gate drill |
| S-3 | Consent immutability: automation flows write `transactional` only; no code path sets `marketing`; exports/handoffs copy actual values | Consent-immutability grep + fixtures |
| S-4 | Suppression integrity: one transition, three mechanisms, idempotent, audited; exclusion fresh-read ≤ 5 s everywhere | AC-02 |
| S-5 | Privacy: opaque refs; masked in APIs; raw refs/emails only in audited CSV/handoff paths; no username lookup exists; PII minimization enforced at schema (no extra fields) | Data-minimization audit |
| S-6 | Export discipline: rate-limited, audited with count + filter, signed links ≤ 24 h, files deleted at expiry | AC-03 + expiry drill |
| S-7 | Handoff discipline: idempotent per row, paced, consent copied verbatim, skips counted with reasons; no bulk marketing upgrade exists | AC-03 |
| S-8 | Token hygiene: removal-center tokens signed + expiring; invalid states neutral (no enumeration); no token material in logs | Token probes |
| S-9 | Audit: capture/suppression/merge/prune/park/export/handoff append to the service's `audit_log` (ids + counts; never message bodies) | Audit completeness drill |

## Rate limits (gateway + app; Redis db 11 `pub:rl:*`)

| Surface | Limit | On breach |
|---|---|---|
| Contacts list reads | 600/h/user | 429 `throttled` |
| Suppress (owner) | 120/h/user | 429 |
| Export / handoff | 6/h + 30/day per creator | 429 `export_throttled` |
| Quota read | 120/h/user | 429 |
| Removal center (GET/POST) | 30/h/IP per token | Neutral state + clamp |
| Ops aggregates | 120/h/operator | 429 |

## Degrade matrix

| Failure | Behavior |
|---|---|
| Provider signals down | Suppression still works via keyword + removal center; provider-button path retries on webhook recovery |
| Redis down | Rate limits degrade to conservative fixed windows; correctness unchanged |
| Hygiene job down | Rows stay active; sends still gated by quota + suppression; alert after 2 missed runs |
| MinIO down | Exports fail honestly; contact data unaffected |
| 27's lead endpoint down | Handoff reports skipped-with-reason (retryable); no partial consent writes |
| Notification pipeline down | Suppression confirmations queue with backlog; state truth unaffected |

## Audit and observability

Audits (24-month): `contacts.captured`, `contacts.suppressed` (mechanism + actor), `contacts.merged`, `contacts.pruned`, `contacts.parked`, `contacts.reactivated`, `contacts.exported`, `contacts.handoff` (imported/skipped). Metrics: `contacts{status,network}`, `suppressions{mechanism}`, `export_jobs`, `handoff_imported/skipped`, `quota_holds`, `hygiene_counts`. Zero PII labels.

## Support answers

"Who did my automation reach?" → owner list with provenance filters. "How do I stop messages?" → any of the three mechanisms; all land the same transition. "Why can't I email them?" → consent class explanation: a comment is consent for the reply, marketing needs an explicit yes. "Why did my export stop?" → rate-limit window + expiry policy. "Why is a contact gone?" → merge/prune/park reason from the event trail.
