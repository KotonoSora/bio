# Marketing Consent and Broadcasts - Contracts and Payloads (feature 48) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/48-marketing/`. GATED: none of these routes may appear in OpenAPI, the gateway, or any image until the spec amendment (AC-01). Reuse contracts: `docs/02-architecture-techlead/27-lead-forms/` (lead shape + export), `docs/02-architecture-techlead/32-monetize-overview/03-api-contracts-and-payloads.md` (pipeline enqueue).

## Endpoints (reserved, absent until the amendment)

| Endpoint | Auth | Notes |
|---|---|---|
| `GET /api/v1/marketing/preferences/{token}` | signed token (no login) | Per-class toggles + honest consequences; expired → neutral page |
| `POST /api/v1/marketing/preferences/{token}` | signed token | Save toggles; immediate effect + confirmation receipt; appends history |
| `POST /api/v1/marketing/unsubscribe/{token}` | signed token | One-click out; effective before the next send |
| `GET/POST /api/v1/marketing/preferences` | session | In-account mirror; own consent history export |
| `POST /api/v1/marketing/re-consent/{token}` | signed token | Explicit re-consent flow only; fresh grant + receipt; 3/address/year |
| `POST /api/v1/ops/marketing/audience/import` | ops role | Validated CSV; row-level report (imported/duplicate/invalid/suppressed) |
| `GET /api/v1/ops/marketing/audience/health` | ops role | Active share, bounce/complaint rates vs config threshold |
| `POST /api/v1/ops/marketing/audience/export` | ops role | Consent columns; audited; suppressed full data needs a case |
| `GET /api/v1/ops/marketing/proof/{addressHash}` | support/ops role | Full consent history; export ≤ 5 min; audited |
| `POST /api/v1/ops/marketing/campaigns` | ops/marketing role | Draft create; sanitized template ref |
| `POST /api/v1/ops/marketing/campaigns/{id}/test-send` | ops/marketing role | Operator addresses only |
| `GET /api/v1/ops/marketing/campaigns/{id}/review` | ops/marketing role | Exact eligible count; exclusions applied by query |
| `POST /api/v1/ops/marketing/campaigns/{id}/confirm` | ops/marketing role + CSRF | Two-step confirm; send-now only; no single action sends |
| `POST /api/v1/ops/marketing/campaigns/{id}/pause` | ops/marketing role | Halts ≤ 60 s; resume never double-sends |
| `GET /api/v1/ops/marketing/campaigns/{id}/stats` | ops/marketing role | Delivered/open/click/bounce/complaint |
| `POST /webhooks/resend` | signature verified | Bounce/complaint/stats events; idempotent by event id; stats-only |
| `GET /internal/v1/marketing/eligibility` | service JWT | **Mandatory** for every marketing send: class + status + suppression → `{eligible, reason}` |

```kotlin
data class EligibilityView(val eligible: Boolean, val reason: String?)  // reason: suppressed|withdrawn|bounced|complained|parked|not_marketing_class
data class PreferenceSaveRequest(val lifecycle: Boolean, val marketing: Boolean)  // transactional fixed on
data class ImportReport(val imported: Int, val duplicates: Int, val invalid: Int, val suppressed: Int, val rowErrors: List<RowError>)
data class CampaignReview(val eligibleCount: Int, val excludedByReason: Map<String, Int>)
```

## Pipeline extension (billing-service, at amendment)

The notification pipeline's `sendClass` handling gains: `marketing` → calls `GET /internal/v1/marketing/eligibility` per recipient before enqueue (confirm AND send); refusal reasons map to skip-with-reason, never silent drops. Today the class refuses `marketing_not_accepted` — unchanged while gated.

## Rules

- One eligibility function: no sender may enqueue a marketing class without it (review gate + grep gate in CI).
- Tokens: signed, single-purpose (preference / unsubscribe / re-consent), expiring; hashed at rest; no enumeration on expired pages.
- Suppression beats any grant in every path; re-consent is the only lift, through its own flow.
- v1 exclusions (segments, scheduling, A/B, co-sends) have no routes — attempts 404/422, never silent no-ops.
- Webhooks: signature verified, idempotent, stats-only (no grant-like side effects).
- No address plaintext, tokens, or consent internals in payloads beyond the caller's own scope; proof reads audited.

## Error catalog (reserved)

| Code | Status | UI Behavior |
|---|---|---|
| `marketing_not_accepted` | 422 | Exists today (pipeline gate); unchanged while gated |
| `consent_withdrawn` | 409 | "You've opted out of promo emails." |
| `suppressed_address` | 409 | Re-consent path explained |
| `token_expired` | 410 | Neutral page; fresh link path |
| `reconsent_throttled` | 429 | "Too many attempts — try again later." |
| `export_case_required` | 403 | "A support case is required for this export." |
| `campaign_paused` | 409 | "Sends paused — complaint rate above threshold." |
| `class_mismatch` | 422 | Ops review: fix classification before send |
