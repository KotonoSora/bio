# Lead Search - API Contracts and Payloads (feature 28)

Phase 2 TL · Endpoints in `profile-service`; forward seams to orders (32-40), bookings (25), automation (41-44), the monetize batch, and agency seats (45-47). Security: `docs/02-architecture-techlead/28-lead-search/05-security-logging-and-rate-limiting.md`.

## Endpoint map

| Method & path | Auth | Request | Success | Errors (code) |
|---|---|---|---|---|
| `GET /api/v1/leads/search?q=&source=&status=&consent=&from=&to=&blockId=&tag=&cursor=` | session | - | 200 `LeadSearchPage` | 422 `search_too_short`, `filter_invalid`; 429 |
| `GET /api/v1/leads/{leadId}` | session | - | 200 `LeadDetail` | 404 `lead_not_found`; 429 |
| `POST /api/v1/leads/{leadId}/notes` | session + CSRF | `{body}` | 201 `NoteView` | 422 `note_too_long`; 404 |
| `POST /api/v1/leads/{leadId}/tags` | session + CSRF | `{tag}` | 201 `{tag}` | 422 `tag_invalid`, `tag_limit_reached`; 404 |
| `DELETE /api/v1/leads/{leadId}/tags/{tag}` | session + CSRF | - | 200 | 404 |
| `POST /api/v1/leads/{leadId}/suppress` | session + CSRF | `{reason}` | 200 `LeadView` | 422 `suppress_reason_required`; 404 |
| `POST /api/v1/leads/{leadId}/reinvite` | session + CSRF | - | 202 | 422 `reinvite_blocked_suppressed`; 429 `reinvite_limited` |
| `POST /api/v1/leads/{leadId}/read` | session + CSRF | `{read: bool}` | 200 | 404 |
| `POST /api/v1/leads/export` | session + CSRF | `ExportRequest` | 202 `{jobId, rowCount}` | 422 `export_invalid`; 429 `export_quota` |
| `GET /api/v1/leads/export/{jobId}` | session | - | 200 `ExportJobView` | 404 `export_not_found` |
| `GET /api/v1/leads/export/{jobId}/download?grant=` | grant (owner-bound) | - | 200 CSV stream | 409 `export_not_ready`; 410 `grant_expired`; 404 |
| `POST /api/v1/leads/unsubscribe` | anonymous (token) | `{token}` | 200 `{status: unsubscribed\\|already_unsubscribed}` | 422 `unsubscribe_expired`, `unsubscribe_invalid`; 429 |

## Kotlin DTOs

```kotlin
data class LeadSearchItem(val id: String, val email: String, val name: String?, val status: String,
  val consentClass: String, val tags: List<String>, val source: String, val sourceRef: String?,
  val capturedAt: Instant, val unread: Boolean)
data class LeadSearchPage(val items: List<LeadSearchItem>, val count: Long, val nextCursor: String?)
data class TimelineEntry(val kind: String, val at: Instant, val actor: String, val summary: String)
data class NoteView(val id: String, val authorKind: String, val body: String, val createdAt: Instant)
data class LeadDetail(val id: String, val email: String, val name: String?, val status: String,
  val consentClass: String, val consentAt: Instant, val consentCopyVersion: String,
  val disposableFlag: Boolean, val source: String, val sourceBlockId: String?,
  val tags: List<String>, val notes: List<NoteView>, val timeline: List<TimelineEntry>,
  val forwardSections: List<String>)   // e.g. ["orders","bookings","automation"] when connected
data class ExportRequest(val mode: String, val filters: JsonObject? = null, val leadIds: List<String>? = null)
data class ExportJobView(val id: String, val status: String, val rowCount: Int?,
  val grantUrl: String?, val expiresAt: Instant?)
```

## Rules

- Count discipline (BR-04): `LeadSearchPage.count` and the export `rowCount` derive from the same predicate builder; `mode=ids` freezes the checked list.
- CSV columns (fixed order, BR-11): `email, name, phone, source, source_ref, consent_class, consent_at, consent_copy_version, status, created_at, tags`; streamed; never localized (UI localizes, the file does not).
- Grant: HMAC `jobId|userId|exp`, TTL 15 min, owner-bound; the download is service-mediated (no bucket URLs); re-grant available while the job lives (7 days).
- Timeline: assembled per request; forward sections appear only when connected (checked via the owning seam) and are labeled otherwise; no placeholder rows.
- Suppression: rank-guarded (409-equivalent handled as idempotent 200 + no duplicate event); reason required; propagates through the shared eligibility check (`GET /internal/v1/leads/check`, feature 27).
- Re-invite: minted token (`leadId|profileId|exp|unsub`), 72 h; dispatch enqueued to the notification pipeline (transactional class, forward); suppressed leads refused; 3/day per lead.
- Unsubscribe: idempotent; `active→unsubscribed` via the shared machine; evented; the preference-center superset extends this page when the monetize batch lands.

## Forward seams recorded

| Seam | Owner | Fixed contract |
|---|---|---|
| purchase references in the timeline | 32-40 | read-only order matches by normalized email; no order mutation from this view |
| booking references | 25 | read-only booking matches; same normalized-email correlation |
| automation contacts merge | 41-44 | correlated side-by-side (both provenances), rows never merged; shared eligibility check enforces suppression in both surfaces |
| preference center superset | monetize batch | extends the unsubscribe page (classes, frequency); token purpose string reserved |
| support seat | 45-47 | same view; `actor=support:{seatId}`; seat scoping added by that batch |
| export surface | monetize batch | consumes this export (column-stable); no second exporter may be built (surface review) |

## Cross-edits in this batch

The full batch cross-edit list (feature 08 payload carve-out and renumber fixes in features 06-09, baseline rows, feature 02 roster, MIGRATION) is logged in `docs/02-architecture-techlead/27-lead-forms/03-api-contracts-and-payloads.md`; this feature adds no further edits beyond the MIGRATION row and the legacy-source deletion.

## Governance

- Codes appended to the platform catalog; no events emitted (consumers pull; the only push is the pipeline send).
- Contract tests: count/export parity, scope leak probes, grant drill, rank guard, token expiry, surface review (AC-10).
