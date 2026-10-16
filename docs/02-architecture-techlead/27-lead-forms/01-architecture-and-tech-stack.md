# Lead Forms - Architecture and Tech Stack (feature 27)

Phase 2 TL · Feature 27 of 53 — leads 1/2; a module of `profile-service` (port 8082, `profile_db`, Redis db 4) — no new service. Implements `docs/01-requirements-ba/27-lead-forms/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`; host features: `docs/02-architecture-techlead/06-public-profile/`, `docs/02-architecture-techlead/08-link-blocks/`.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | DEVIATION CARRIED (L0): the capture module lives inside `profile-service` — form config (`payload_json`), lead write, and dedupe share one transaction domain and the bio RLS baseline; the legacy Worker/queue defaults become a durable in-service outbox + scheduled consumer (same dedupe/idempotency contract). Split trigger recorded: extraction only behind a real broker on sustained submit-backlog evidence, never speculatively. | L0's own architecture verdict, mapped to the numbered topology; no broker to operate at this volume. |
| D-2 | Capture pipeline: the public submit handler writes exactly one `lead_outbox` row and never touches `leads` directly; a scheduled consumer (batch ≤ 50 every 2 s, backoff retries, 24 h drop + metric) performs dedupe/merge; landing p95 ≤ 5 s. Unknown blocks are dropped by the consumer after a uniform 202. | Queue-mediated by construction; anti-oracle holds at the edge. |
| D-3 | Confirm tokens are stateless HMAC (`leadId|profileId|exp`), single-purpose, 48 h; `GET` returns read-only confirm context only; the state change requires an explicit `POST`. | Mail-scanner safe (Q3); nothing to purge on expiry. |
| D-4 | The status machine and strictest-wins rank (`suppressed > complained > bounced > unsubscribed > active > pending`) live here as the canonical contract; forward consumers (audience batch, notification pipeline, feature 28) consume it and never redefine it. Suppression is permanent; re-submission never resurrects it. | The audience doc is not yet migrated; the first-migrated owner fixes the contract. |
| D-5 | Consent classes (`lifecycle|marketing`) and `copyVersion` are canonical here: `copyVersion` is server-stamped on every block save; marketing requires the checked box (default unchecked); no endpoint or UI path can grant consent (BR-16, surface-review gate). | Proof-of-consent at birth is structural, not procedural. |
| D-6 | Abuse: gateway Turnstile-class challenge + WAF/rate rules on `/api/t/leads/*`; per-block velocity counters + 15-min pause keys in Redis db 4 (`lead:cap:*`, `lead:pause:*`); disposable/role list ships as a reviewed code asset; raw signals TTL 24 h; IPs stored only as salted hashes. | No new infrastructure; Redis db 4 is the host service's existing database. |
| D-7 | The `form` block payload is carved out of feature 08's validator registry (cross-edit): closed schema `{headline, offerCopy?, fields[], submitLabel, successMessage, redirect?, doubleOptIn, copyVersion}`; unknown keys reject; no arbitrary field builder. | One validator, one source of truth (feature 08 owns block payloads). |
| D-8 | Capture surface: SSR-rendered by the existing block renderer (feature 10, untouched); submission is same-origin fetch to `POST /api/t/leads/submit/{blockId}`; no third-party scripts beyond the gateway challenge. | No new render path; CSP stays intact. |
| D-9 | Purge: rides feature 02's account-deletion participant — leads, events, and outbox rows delete by `user_id` (roster row extended), counts in the purge certificate; Redis cap/pause keys are TTL-bound, nothing to clean. | Same posture as every owner-scoped table. |

## Component map

| Piece | Home | Note |
|---|---|---|
| Form payload validation | `profile-service` | feature 08 registry hook + copyVersion stamping |
| Submit handler (public) | `profile-service` | uniform 202; outbox row only |
| Capture consumer | `profile-service` | scheduled; dedupe/merge; events append |
| Confirm flow | `profile-service` | HMAC token; GET read-only / POST confirm |
| Inbox feed | `profile-service` | owner-scoped, newest-first |
| Abuse caps + pause | `profile-service` + Redis db 4 | counters, pause keys, notice state |
| Confirm/welcome email | notification pipeline (forward) | transactional class, ≤ 60 s |

## Boundaries

Owns: form payload schema semantics, capture pipeline, consent-at-birth, status machine, confirm/welcome triggers, inbox feed, abuse caps, disposable flags, purge. Does not own: block CRUD/rendering (08/10), contact workspace (28), list hygiene/broadcasts/preference center (monetize batch), checkout opt-ins (33-34 consume the internal write seam), automation contacts (41-44), campaigns (gated 48-53).

## Consumers

| Consumer | Contract |
|---|---|
| checkout 33-34 (forward) | `POST /internal/v1/leads` — same shape, same dedupe/merge |
| notification pipeline (forward) | confirm/welcome sends; provider signals via internal status write |
| monetize/audience batch (forward) | `leads`/`lead_events` internal reads + suppression semantics consumed, never redefined |
| feature 28 | owner reads/writes on `leads`/`lead_events` + notes/tags |
