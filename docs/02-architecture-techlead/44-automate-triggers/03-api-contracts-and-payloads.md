# Automate Triggers - API Contracts and Payloads (feature 44)

Phase 2 TL · Owner + webhook + ops contracts. Auth/rate rules: `docs/02-architecture-techlead/44-automate-triggers/05-security-logging-and-rate-limiting.md`. Problems follow RFC 9457.

## Endpoints

| Method & path | Auth | Request | Success | Errors (code) |
|---|---|---|---|---|
| `POST /api/v1/automations` | session + CSRF | `AutomationCreateRequest` | `201 AutomationView` | 409 `keyword_collision`, `connection_dead`; 403 `rule_quota_exceeded`; 422 `template_invalid`, `validation_failed`; 429 |
| `GET /api/v1/automations` | session | - | `200 List<AutomationView>` | 401; 429 |
| `PATCH /api/v1/automations/{id}` | session + CSRF | `AutomationPatchRequest` | `200 AutomationView` (re-runs checks) | 404; 409 `keyword_collision`; 422; 429 |
| `DELETE /api/v1/automations/{id}` | session + CSRF | - | `200 {cancelledIntents: Int}` | 404; 429 |
| `POST /api/v1/automations/{id}/pause`, `/resume` | session + CSRF | - | `200 AutomationView` | 404; 409 `automation_paused` (resume while review/burst hold); 429 |
| `POST /api/v1/automations/{id}/preview` | session + CSRF | `{sampleComment}` | `200 PreviewResult` | 404; 422; 429 |
| `GET /api/v1/automations/{id}/trigger-log?cursor=` | session | - | `200 TriggerLogPage` (decisions, masked refs) | 404; 429 |
| `GET /api/v1/automations/{id}/intents?status=&cursor=` | session | - | `200 IntentPage` | 404; 429 |
| `POST /webhooks/instagram` | provider scheme (verified) | comment events | `200` after durable handling | 401 `webhook_invalid`; 5xx transient |
| `GET /api/v1/ops/automate/health` | ops role | - | `200` cards (error rate, pause states, overflow depth) | 403; 429 |
| `POST /api/v1/ops/automate/{id}/reinstate` | ops role | `{reason}` | `200 AutomationView` (audited; resumes in order) | 404; 422 `validation_failed`; 403; 429 |
| `GET /api/v1/ops/automate/templates?state=pending_review` | ops role | - | `200 List<TemplateReviewView>` | 403; 429 |
| `POST /api/v1/ops/automate/templates/{id}/decision` | ops role | `{decision: approve\|reject, reason}` | `200 TemplateReviewView` | 404; 409 `template_conflict`; 403; 429 |

```kotlin
data class AutomationCreateRequest(val accountId: String, val matchType: String,
  val keywords: List<String> = emptyList(), val mediaScope: List<String> = emptyList(),
  val delaySeconds: Int = 60, val timezone: String, val replyTemplate: String,
  val dmTemplate: String, val linkUrl: String)
data class AutomationView(val id: String, val matchType: String, val keywords: List<String>,
  val mediaScope: List<String>, val delaySeconds: Int, val enabled: Boolean,
  val pausedReason: String?, val templateReviewState: String)
data class PreviewResult(val matched: Boolean, val decision: String, val renderedReply: String?,
  val renderedDm: String?, val linkValid: Boolean)
data class TriggerLogPage(val rows: List<TriggerLogRow>, val nextCursor: String?)
data class TriggerLogRow(val id: String, val commentRefMasked: String, val mediaId: String?,
  val decision: String, val observedAt: Instant)
data class IntentPage(val rows: List<IntentView>, val nextCursor: String?)
data class IntentView(val id: String, val commentRefMasked: String, val status: String,
  val holdReason: String?, val errorClass: String?, val matchedAt: Instant, val updatedAt: Instant)
data class TemplateReviewView(val id: String, val automationId: String, val reportClass: String,
  val state: String)
```

## Events (publish-service stream `publish.events`)

| Event | Payload | Emitted by | Consumers |
|---|---|---|---|
| `automation.auto_paused.v1` | `{automationId, profileId, reason, errorRate}` | burst-abort monitor | notification enqueue (creator), ops page |
| `automation.paused.v1` | `{automationId, profileId, reason}` | pause transitions (owner/connection/review/downgrade) | notification enqueue |

Post-commit, at-least-once, idempotent consumers (baseline 03).

## Rules

- Save/edit runs in one transaction: collision check (overlapping phrases vs active rules in scope), rule quota (43-style single counting function for rules), template checks (one link, https + allowlist, prohibited classes), connection health + scopes.
- Preview evaluates a sample comment through the same `MatchEvaluator` (no side effects; no trigger-log row).
- Pause/resume: owner pause stops matching + delivery; resume refused while review/burst holds stand (`automation_paused`); ops reinstate requires reason and resumes held intents in `matched_at` order.
- Webhook: verified over raw bytes → anti-replay persist-first → enqueue → `200`; comment delete events cancel pending intents; edits never re-fire.
- Delivery never runs in request threads; gates in fixed order; `window_lapsed` terminal; one reply + one link-DM per intent, ever.
- Ops surfaces are health + review only — no message bodies, no cross-creator lists.

## Error catalog additions

| Code | Status | UI Behavior |
|---|---|---|
| `keyword_collision` | 409 | Conflicting rule named |
| `rule_quota_exceeded` | 403 | Pause-oldest / upgrade path |
| `template_invalid` | 422 | Exact violation class named |
| `automation_paused` | 409 | Hold reason (review/burst/connection) |
| `template_conflict` | 409 | Already decided — refresh |
| `webhook_invalid` | 401 | None (provider-facing) |

Reused: `throttled`, `validation_failed`, `connection_dead` (41).
