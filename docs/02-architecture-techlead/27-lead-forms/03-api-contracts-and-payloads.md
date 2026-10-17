# Lead Forms - API Contracts and Payloads (feature 27)

Phase 2 TL · Endpoints in `profile-service`; forward seams to checkout (33-40), the notification pipeline, and the monetize/audience batch. Security: `docs/02-architecture-techlead/27-lead-forms/05-security-logging-and-rate-limiting.md`.

## Endpoint map

| Method & path | Auth | Request | Success | Errors (code) |
|---|---|---|---|---|
| `POST /api/t/leads/submit/{blockId}` | anonymous + Turnstile-class + gateway rate | `LeadSubmitRequest` | 202 uniform body (accepted/duplicate/unknown block) | 422 `email_invalid`, `validation_failed`; 429 `submit_paused`, `throttled` |
| `GET /api/v1/leads/confirm?token=` | anonymous (token) | - | 200 `ConfirmContext` (read-only; `state: valid\|expired\|invalid`) | 429 |
| `POST /api/v1/leads/confirm` | anonymous (token) | `{token}` | 200 `{status: active\|already_active}` | 422 `confirm_expired`, `confirm_invalid`; 429 |
| `GET /api/v1/leads?form=&unread=&cursor=` | session | - | 200 `LeadPage` (owner-scoped feed) | 401 |
| `POST /internal/v1/leads` | service JWT | `InternalLeadWrite` (checkout opt-in and future consumers) | 200 `LeadView` (idempotent) | 401; 422 `validation_failed` |
| `POST /internal/v1/leads/{leadId}/status` | service JWT | `{status: bounced\|complained, providerEventId}` | 200 `LeadView` | 401; 409 `rank_conflict` (lower rank refused) |
| `GET /internal/v1/leads/check?profileId=&email=` | service JWT | - | 200 `{eligible: bool, status, consentClass}` (send-eligibility seam) | 401 |

## Kotlin DTOs

```kotlin
data class LeadSubmitRequest(val email: String, val name: String? = null, val phone: String? = null,
  val note: String? = null, val marketingConsent: Boolean = false, val copyVersion: String)
data class ConfirmContext(val state: String, val maskedEmail: String?, val formHeadline: String?)
data class LeadView(val id: String, val email: String, val name: String?, val status: String,
  val consentClass: String, val consentAt: Instant, val source: String, val unread: Boolean)
data class LeadPage(val items: List<LeadView>, val nextCursor: String?, val unreadCount: Int)
data class InternalLeadWrite(val profileId: String, val email: String, val name: String? = null,
  val source: String, val marketingConsent: Boolean, val consentCopyVersion: String,
  val sourceRef: String?)   // e.g. order ref for checkout opt-ins
```

## Rules

- Uniform 202 (§BR-10): accepted, duplicate, and unknown-block submissions return the same body (`{"status":"ok"}`) and indistinguishable timing; the consumer drops unknown blocks with an internal metric; malformed email is the only client-visible difference (422 `email_invalid`).
- Submit handler writes only an outbox row (BR-03); consumer performs sanitization re-check, disposable flagging, dedupe/merge, and event appends.
- Confirm: token HMAC `leadId|profileId|exp` (48 h); GET never mutates; POST is idempotent (`active` then `already_active`); expired → clean re-request path (rate-limited 3/h per address).
- Confirm/welcome sends enqueue via the notification pipeline as transactional-class (forward; never marketing-gated).
- Internal writes obey the identical dedupe/merge and rank rules; `rank_conflict` refuses downgrades (strictest-wins is server-enforced, not caller discipline).
- Inbox feed: newest-first, `unread` filter, owner-scoped; detail/search/tags/notes/export are feature 28's surface.

## Forward seams recorded

| Seam | Owner | Fixed contract |
|---|---|---|
| checkout opt-in capture | 33-34 | `POST /internal/v1/leads` with `source=checkout` + order ref; same consent semantics |
| provider bounce/complaint signals | notification pipeline (monetize batch) | `POST /internal/v1/leads/{id}/status`; rank-guarded |
| send-eligibility checks | monetize/audience + 41-44 | `GET /internal/v1/leads/check` (status + consent class only) |
| confirm/welcome sends | notification pipeline | transactional class; template-bound; ≤ 60 s dispatch |
| suppression semantics | this feature (canonical) | consumed, never redefined, by the audience batch |

## Cross-edits landed in this change

| File | Change |
|---|---|
| `docs/02-architecture-techlead/08-link-blocks/03-api-contracts-and-payloads.md` | `form` payload carved out of the generic clause into the closed schema (validator registry) |
| `docs/02-architecture-techlead/06-public-profile/` | migration inventory precision (V2/V3/V4 numbered) |
| `docs/02-architecture-techlead/07-verification-and-followers/`, `08-link-blocks/`, `09-themes-and-avatar/` | profile_db migration renumber to the canonical chain (V4/V5) |
| `docs/02-architecture-techlead/00-platform-baseline/01-architecture-and-service-topology.md` | public-paths row (lead submits) + profile-service row (features 27-28) |
| `docs/02-architecture-techlead/02-account-deletion/` | roster row extended (leads rows + files) |

## Governance

- Codes appended to the platform catalog; forward event/value names recorded above; no event is emitted by this feature (consumers pull).
- Contract tests: uniform-202 matrix, confirm scanner drill, rank guard, internal-write parity, purge counts.
