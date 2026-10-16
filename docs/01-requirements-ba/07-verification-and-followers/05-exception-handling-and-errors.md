# Verification Badge and Follower Display - Exception Handling and Errors (feature 07)

Phase 1 BA · Path types: Happy / Alternative / Exception. Error `code` values are the API contract; the catalog lives in `docs/02-architecture-techlead/07-verification-and-followers/03-api-contracts-and-payloads.md`.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Application submitted with valid evidence | Happy | `201`; card flips to `pending` | "Submitted - under review." |
| Ops approves | Happy | `verified` + `verified_at`; badge live within purge SLA | badge preview appears |
| Second application while `pending` | Alternative | `409 verify_already_pending`; card collapses to the pending view; no new row | none (pending timeline shown) |
| Rejection | Alternative | `rejected` + reason visible to owner only; re-apply entry unlocks | "Not approved this time - see note." |
| Ops rejects/revokes without a reason | Exception | `422 validation_failed`; blocked client-side AND server-side | "A reason is required." |
| Creator hits ops endpoint | Exception | `403 forbidden`; no queue data in the body | "Ops access required." |
| Evidence upload fails mid-transfer | Exception | Retry offered; the key is never half-sent (no orphan reference) | "Upload failed. Retry." |
| Evidence file wrong type or > 10 MB | Exception | `422 validation_failed` at presign; nothing stored | "PDF, PNG, or JPEG up to 10 MB." |
| Handle markup or `javascript:` | Exception | `422 bad_handle` / `bad_social_url`; row-level error; nothing saved | "That handle/URL is not valid." |
| Self-reported count above cap | Exception | `422 follower_count_capped` with the cap restated | "Max 100,000,000 per network." |
| 10x count jump | Alternative | `follower_jump_review` flag; count stored; ops review; amber notice (copy says the value is shown) | "Count under review." |
| Auto-refresh rate limit hit | Exception | `429 throttled` + `Retry-After` (6/h) | "Refresh limit reached. Try later." |
| Source network unreachable during refresh | Alternative | Last-good total stays; `stale` flag; page never errors or slows | "last updated <time> (stale)." |
| Badge markup pasted into bio/display name | Alternative | Renders as plain text; never the badge component | none |
| Verified profile renamed | Alternative | Status atomically -> `pending`; badge hidden; re-review notice | "Re-review required after rename." |
| Revoke with typed confirmation missing | Exception | Client blocks submit; server re-checks (`422`) | "Type the confirmation to revoke." |
| Cross-user write / IDOR probe | Exception | `403`; RLS denies at DB even without the app check | "You do not have access to this profile." |
| Session expired mid-decide | Exception | `401`; reason text preserved; re-login resumes at the decide panel | "Please sign in to continue." |
| Evidence viewer without role | Exception | `403`; presigned URL not issued (no leak) | "Ops access required." |
