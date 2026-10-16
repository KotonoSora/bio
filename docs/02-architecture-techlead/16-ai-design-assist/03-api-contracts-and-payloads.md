# AI Design Assist - Contracts and Payloads (feature 16) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/16-ai-design-assist/` (BR-02..BR-11, `05-exception-handling-and-errors.md`). GATED: none of these routes may appear in OpenAPI, the gateway, or any image until the spec amendment (AC-01). Reuse contracts: `docs/02-architecture-techlead/09-themes-and-avatar/`, `docs/02-architecture-techlead/08-link-blocks/`, `docs/02-architecture-techlead/06-public-profile/`.

## Endpoints (reserved, absent until the amendment)

| Endpoint | Auth | Notes |
|---|---|---|
| `POST /api/v1/assist/theme` | session -> internal JWT | Quiz/auto -> ranked gallery picks + reasons; quota + challenge + rate limited |
| `POST /api/v1/assist/bio` | session -> internal JWT | 3 drafts (fewer when low confidence); tone selector; shared length/XSS validators |
| `POST /api/v1/assist/ordering` | session -> internal JWT | Proposed positions + title edits; from click history where available |

All three: owner-scoped (server-set user; a cross-profile target -> `403 foreign_profile`, inherited), quota-checked (`429 assist_quota_exhausted` + `Retry-After`), challenge-gated, prompts treated as data. Apply paths add no endpoints: existing theme PUT (09), profile PUT (06/07), and blocks reorder (08) do the writes with unchanged validators, RLS, and audit.

## DTOs (design-complete)

```kotlin
data class AssistThemeRequest(val mode: String, val quizAnswers: Map<String, String>? = null)
data class AssistThemeResponse(val suggestions: List<ThemeSuggestion>, val refused: Boolean = false)
data class ThemeSuggestion(val themeId: String, val reasons: List<String>, val confidence: String)

data class AssistBioRequest(val tone: String, val field: String, val currentText: String? = null)
data class AssistBioResponse(val suggestions: List<BioDraft>, val refused: Boolean = false)
data class BioDraft(val text: String, val confidence: String)

data class AssistOrderingResponse(
  val suggestions: List<OrderingProposal>, val refused: Boolean = false)
data class OrderingProposal(val blockId: String, val position: Int, val title: String? = null)
```

`confidence` vocabulary: `standard | low` (low -> fewer options in UI). `refused:true` means the request could only be satisfied by inexpressible output (custom CSS/JS/HTML/fonts); it is a `200`, never an error-styled state.

## Semantics

| Rule | Definition |
|---|---|
| Tokens-only | `themeId` must resolve in the gallery registry; text must pass the same validators as manual input; `position` must be a legal reorder op; the guard drops anything else before response assembly |
| No write surface | Responses are proposals only; nothing persists except optional short-TTL drafts (02); applied changes are ordinary audited writes |
| Privacy | Model input comes from a whitelist DTO (quiz answers, current field text, link titles); no follower evidence, tokens, or cross-user data is expressible |
| Disclosure | Dashboard captions `Suggested by assistant`; no visitor-facing AI markup exists in any contract |
| Drafts | Ephemeral client state; the optional TTL table is read/write only through the assist flow and never exposed as an API |

## Errors (RFC 9457 ProblemDetail, stable codes)

```json
{"type":"about:blank","title":"Forbidden","status":403,"code":"foreign_profile"}
{"type":"about:blank","title":"Out of credits","status":429,"code":"assist_quota_exhausted"}
{"type":"about:blank","title":"Too many requests","status":429,"code":"rate_limited"}
```

`assist_quota_exhausted` is defined here; `foreign_profile` and `rate_limited` are inherited. Validator-rejected model output is never a client error - it is dropped server-side before display.

## Metrics and governance (at the amendment)

- Metrics: `assist.generated{kind}`, `assist.refused`, `assist.quota_exhausted`, `assist.cost_units`.
- `/api/v1` additive-only with the OpenAPI diff gate once live; these paths are reserved now and absent now.
- Grain/ownership: credits key off feature 35's period window (no local period math); plan availability (Q-01) resolved per request via the plan snapshot.
