# Marketing Consent and Broadcasts - Security, Logging, and Rate Limiting (feature 48) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/48-marketing/`. GATED: S-1 is live now; all other controls bind the future build at the spec amendment. Baseline controls: `docs/02-architecture-techlead/00-platform-baseline/`.

## Controls

| # | Control | Rule |
|---|---|---|
| S-1 | Gate enforcement (LIVE) | Tooling asserts zero marketing surface each release (schema/OpenAPI/source/bundle); a hit blocks promotion — the gate is tool-enforced, not policy. The pipeline keeps refusing `marketing_not_accepted` |
| S-2 | Enforcement architecture | One `EligibilityCheck` at confirm AND at send; suppression beats any grant; new senders fail review without the call — unconsented sends are structurally impossible, not merely discouraged |
| S-3 | Proof integrity | Consent history append-only (no UPDATE/DELETE grants); every grant carries timestamp, source, copy version; proof exports audited and rate-limited |
| S-4 | Token discipline | Preference/unsubscribe/re-consent tokens signed, single-purpose, expiring, hashed at rest; expired pages are neutral (no enumeration, no address echo) |
| S-5 | Webhook integrity | Resend events signature-verified, idempotent by provider event id, stats-only; invalid signatures dropped + alerted; bounce/complaint transitions sync to suppression ≤ 60 s |
| S-6 | Abuse defense | Import/export/re-consent rate budgets; public capture shares Turnstile/rate limits with other public writes; re-consent 3/address/year |
| S-7 | Reputation isolation | Dedicated marketing subdomain; complaint-rate breach auto-pauses sends + pages ops; transactional/lifecycle delivery provably unaffected |
| S-8 | Data minimization | Suppression keyed by address hash; zero plaintext addresses/tokens in logs beyond hashed refs; proof retention per policy; account deletion withdraws + suppresses with audit-minimal rows kept |
| S-9 | IDOR + RLS | Owner-scoped consents/audience reads; support proof reads audited; marketing role reads aggregates + active rows only; creator silo by owner key |

## Rate limits (at amendment; Redis db 12)

| Surface | Limit | On breach |
|---|---|---|
| Preference center reads/saves | 60/h/token | 429 `throttled` |
| Unsubscribe | 30/h/token | 429 |
| Re-consent attempts | 3/address/year | 429 `reconsent_throttled` |
| Import | 10/h/operator | 429 |
| Export / proof lookup | 5/h/operator; 30/h/support | 429 |
| Campaign confirm/pause | 30/h/operator | 429 |

## Degrade matrix (at amendment)

| Failure | Behavior |
|---|---|
| `EligibilityCheck` down | Marketing sends refuse (fail closed); transactional/lifecycle unaffected |
| Resend webhook backlog | Stats lag honestly (no fabricated numbers); states transition on delivery |
| Hygiene job down | Parking pauses; eligibility unaffected (strict states still apply); alert after 2 misses |
| Preference center down | Unsubscribe links still route (static fallback mailto); saves retried |
| Redis down | Rate limits degrade to conservative fixed windows; token verification unaffected (stateless signatures) |

## Audit and observability (at amendment)

Audits (24-month): consent changes (append-only history is the record), imports/exports, proof lookups, campaign confirm/pause, webhook rejections, re-consents. Metrics: audience states by class, campaign delivered/open/click rates, complaint rate vs threshold, pause events, eligibility refusals by reason — zero address labels.

## Support answers (at amendment)

"Why did I get this email?" → consent history + class + copy version, exportable in minutes. "How do I stop promo emails?" → one click, no login, effective before the next send. "Why did sends pause?" → complaint-rate breach + ops page trail; transactional delivery confirmed unaffected. "Why can't I email this address?" → suppression reason + re-consent path only. "Why can't I segment/schedule?" → v1 restraint; named future docs hold the method.
