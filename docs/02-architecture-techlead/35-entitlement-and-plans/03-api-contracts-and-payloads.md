# Entitlement and Plans - API Contracts and Payloads (feature 35)

Phase 2 TL · Endpoints in `billing-service` (the tailor executes in `resume-service`). Conventions from `docs/02-architecture-techlead/32-monetize-overview/03-api-contracts-and-payloads.md`. Security: `docs/02-architecture-techlead/35-entitlement-and-plans/05-security-logging-and-rate-limiting.md`. BA sources: `docs/01-requirements-ba/35-entitlement-and-plans/`.

## Endpoint map (browser-facing)

| Method & path | Auth | Request | Success | Errors (code) |
|---|---|---|---|---|
| `GET /api/v1/plans` | public, cache-first | - | 200 `PlansResponse` (shape frozen by `docs/02-architecture-techlead/03-tier-presentation/03-api-contracts-and-payloads.md`) | 404 `plan_not_published`; 429 |
| `GET /api/v1/billing/plan` | session | - | 200 `BillingPlanView` (36 adds `graceEndsAt`, `trialUsed`) | 401 |
| `POST /api/v1/ai/tailor-resume` (resume-service) | session + CSRF | `TailorRequest` + `Idempotency-Key` | 200 `TailorResponse` | 401; 403 `upgrade_required`; 404 `resume_absent` (17); 422 `validation_failed`; 429 `tailor_quota_exhausted`, `throttled`; 503 `ai_unavailable` |

## Endpoint map (ops + internal)

| Method & path | Auth | Request | Success | Errors (code) |
|---|---|---|---|---|
| `GET /api/v1/ops/billing/plans/terms?status=` | ops | - | 200 `TermPage` | 403 `billing_ops_required`; 429 |
| `POST /api/v1/ops/billing/plans/terms` | ops + CSRF | `TermDraftRequest` | 201 `TermView` (draft) | 403; 409 `term_pair_incomplete`; 422 `term_invalid`; 429 |
| `POST /api/v1/ops/billing/plans/terms/{termId}/activate` | ops + finance + CSRF | `{reason}` | 200 `TermView` | 403 `finance_role_required`; 404; 409 `term_activation_conflict`; 422 `validation_failed`; 429 |
| `POST /api/v1/ops/billing/plans/terms/{termId}/retire` | ops + CSRF | `{reason}` | 200 `TermView` | 403; 404; 409 `term_retire_blocked`; 422 `validation_failed`; 429 |
| `GET /internal/v1/billing/plan/{userId}` | service JWT | - | 200 `EffectivePlanView` | 401; 500 `plan.missing_row` (corruption only) |
| `GET /internal/v1/billing/plans/current-prices` | service JWT | - | 200 `{terms: List<PriceTermView>}` (empty = "pending") | 401 |
| `POST /internal/v1/billing/ai/debit` | service JWT | `DebitRequest` | 200 `DebitResult` | 401; 403 `upgrade_required`; 422 `kind_unknown`; 429 `quota_exhausted` |
| `POST /internal/v1/billing/ai/credit-return` | service JWT | `CreditReturnRequest` | 200 `DebitResult` | 401; 404 `event_absent`; 409 `return_conflict` |

## Kotlin DTOs

```kotlin
data class BillingPlanView(val plan: String, val status: String, val term: String?,
  val periodStart: Instant?, val periodEnd: Instant?, val quotas: QuotaView,
  val capabilities: Map<String, String>, val catalogVersion: String?,
  val graceEndsAt: Instant? = null, val trialUsed: Boolean = false)  // 36-additive
data class QuotaView(val aiCreditsTotal: Int, val aiCreditsUsed: Int, val aiCreditsRemaining: Int)
data class EffectivePlanView(val plan: String, val status: String, val term: String?,
  val periodStart: Instant?, val periodEnd: Instant?, val quotas: QuotaView,
  val capabilities: Map<String, String>)                       // superset for gates + 16's period read
data class PriceTermView(val plan: String, val term: String, val amountVnd: Long, val version: Int, val aiCredits: Int)
data class TermDraftRequest(val planCode: String, val term: String, val amountVnd: Long?, val aiCredits: Int, val reason: String? = null)
data class TermView(val id: String, val planCode: String, val term: String, val amountVnd: Long, val aiCredits: Int,
  val version: Int, val status: String, val activatedAt: Instant?, val retiredAt: Instant?)
data class TermPage(val terms: List<TermView>)
data class DebitRequest(val userId: String, val kind: String, val idempotencyKey: String)
data class CreditReturnRequest(val eventId: Long, val reason: String)
data class DebitResult(val eventId: Long, val kind: String, val periodStart: Instant, val periodEnd: Instant?,
  val creditsTotal: Int, val creditsUsed: Int, val remaining: Int)
data class TailorRequest(val documentId: String, val jobDescription: String)            // resume-service
data class TailorResponse(val suggestions: List<TailorSuggestion>, val creditsRemaining: Int, val periodEnd: Instant?)
data class TailorSuggestion(val id: String, val text: String)
```

`PlansResponse` values from active rows: `monthlyVnd`/`yearlyVnd` = active pro rows; `yearlyDiscountPct` derived from the pair (omitted when 0); `version` = `m{monthlyVer}y{yearlyVer}`; `trialDays` from `billing.catalog` config (omitted when unset); tiers/claims from `plans.features_json`.

## Rules

- Pricing ladder for 33 (extensions): plan intent -> `term` required (`422 term_required`) and valid (`422 term_invalid`) -> `free` refused (`422 plan_not_purchasable`) -> active row lookup (`409 plan_not_published`) -> amount snapshots into the quote; create stamps `orders.plan_term_version`; the 33 fingerprint covers `plan|term|amountVnd`.
- Catalog: monthly drafts take integral `amountVnd > 0`; yearly drafts derive from the active monthly + `yearlyDiscount` (no client amount); activation requires finance + reason; retire requires reason and zero pendings; no edit path — corrections are new versions; every action audited; reads flip `<= 5 s`.
- Debit: effective plan must be `pro` (else `403 upgrade_required`); window = the current subscription period (stamped `credits_total`); counter row materialized lazily; conditional update under the window key; keyed replay returns the original `DebitResult`; `429 quota_exhausted` carries `periodEnd`. Return: one per event, restores counters, audited (`ai.return`, ratio-alerted).
- Tailor flow: load the document locally -> debit keyed by request id -> AI adapter (fixed system prompt; JD passed as data, `1..10 000` chars else `422 validation_failed`) -> suggestions (`<= 8`, each `<= 500` chars, escaped, no auto-apply) + remaining; provider failure or 30 s overrun -> `503 ai_unavailable` + credit-return; logs and metrics carry ids and counts only.
- Reads: `GET /api/v1/billing/plan` is owner-scoped (guests `401` — plan surfaces are login-only); public catalog `Cache-Control: public, max-age=300`; the internal plan read serves gates and is cached in-service `<= 5 s`, cleared on ledger writes.
- Errors: RFC 9457; additions below; reused codes keep their owners (17 `resume_absent`, 26 `finance_role_required`, 32 `throttled`).

## Money error catalog (this feature's additions)

| Code | HTTP | Meaning |
|---|---|---|
| `term_required` / `term_invalid` | 422 | plan intent term missing / unknown or invalid draft value |
| `plan_not_purchasable` | 422 | `free` (or retired plan) intent |
| `plan_not_published` | 409 (quote) / 404 (public read) | no active term row |
| `term_pair_incomplete` | 409 | yearly draft without an active monthly row |
| `term_activation_conflict` | 409 | another active row for (plan, term) |
| `term_retire_blocked` | 409 | pending orders reference the version |
| `billing_ops_required` | 403 | ops role missing |
| `quota_exhausted` / `tailor_quota_exhausted` | 429 | internal / browser debit past the window total |
| `upgrade_required` | 403 | plan gate failed |
| `kind_unknown` | 422 | credit kind not in the registry |
| `event_absent` / `return_conflict` | 404 / 409 | return of unknown debit / second return |
| `ai_unavailable` | 503 | provider failure or timeout (credit returned) |
