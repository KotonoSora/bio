# Resume Templates - API Contracts and Payloads (feature 18)

Phase 2 TL · One new read endpoint + two additive codes on feature 17's write path. JSON only; problems follow RFC 9457. Auth/rate rules: `docs/02-architecture-techlead/18-resume-templates/05-security-logging-and-rate-limiting.md`.

## Endpoints

| Method & path | Auth | Success | Errors (code) |
|---|---|---|---|
| `GET /api/v1/resume-templates` | session | 200 list of `ResumeTemplateResponse` (registry order; `classic` first) | 401; 429 |

Selection is not a new endpoint: `PUT /api/v1/documents/{id}` (feature 17's contract) gains registry validation and the premium gate:

| Existing route (feature 17) | Addition (this feature) | Codes |
|---|---|---|
| `PUT /api/v1/documents/{id}` with `{templateId}` | validate against the registry; check `premium` against the feature 01 entitlement snapshot (fail-closed to free); on success emit `resume.cv_changed.v1` | `422 unknown_template`; `403 template_forbidden` |

## Kotlin DTOs (source of truth for the SPA type mirror)

```kotlin
data class ResumeTemplateResponse(
    val id: String, val name: String, val premium: Boolean,
    val tokens: TemplateTokens, val thumbnailUrl: String,
)
data class TemplateTokens(val font: String, val scale: String, val accent: String, val density: String)
```

## Code catalog

| `code` | HTTP | UI mapping |
|---|---|---|
| `unknown_template` | 422 | gallery refresh + inline notice |
| `template_forbidden` | 403 | upgrade sheet + selection rollback |
| `throttled` | 429 | cooldown (write envelope owned by feature 17) |

Both codes are legacy-named and rename-frozen; they live on feature 17's route table with this feature as the owner of their semantics.

## Semantics (normative)

- Catalog is deploy-versioned: ids are stable forever; removal keeps read fallback (`classic` + metric); ids are never reused for a different design.
- Feature 19 extends selection: `template_id = "fmt:{formatId}"` selects a cloned community format when a clone row exists for the caller (`422 unknown_template` otherwise); registry ids behave unchanged.
- Premium check uses the 30 s entitlement snapshot contract (`docs/02-architecture-techlead/01-account-and-access/03-api-contracts-and-payloads.md`); snapshot miss = deny premium, allow non-premium (fail-closed to free); no per-request account call.
- Downgrade fallback (36): rendering a document whose stored `template_id` is `premium` while the owner's snapshot resolves free falls back to the closest free token set at render time; the stored id is preserved - re-upgrade restores it byte-identical; selection stays fail-closed.
- Render inputs are `snapshot_json + templateId (+ registry tokens)` only; user fields never supply style, class, or markup; escaped as text.
- `resume.cv_changed.v1` (feature 17's event) fires on a template switch; payload unchanged; consumers purge as usual.

## Governance

- Catalog additive-only; a breaking token-shape change requires a flagged amendment.
- Launch registry: three free token templates (Q-01); zero `premium=true` entries (Q-03); adding a premium entry requires the claims-registry update first.
- Cross-edits in this change: feature 17's PUT row (codes) and event trigger list (template switch); feature 10's `/cv` row (text-parity clause).
