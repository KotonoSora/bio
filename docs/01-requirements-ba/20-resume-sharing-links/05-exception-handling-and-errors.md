# Resume Sharing Links - Exception Handling and Errors (feature 20)

Phase 1 BA · Failure paths handled in `docs/02-architecture-techlead/20-resume-sharing-links/05-security-logging-and-rate-limiting.md`. Codes are stable contract; problems follow RFC 9457.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Helper call for a foreign document | API | `403` (no state oracle); nothing derived | "You don't have access to this resume." |
| Helper call for an unknown document | API | `404 document_not_found` | "This resume no longer exists." |
| Profile read fails during derivation | API | `503 share_unavailable`; no partial rows | "Couldn't build your links - try again." |
| Short-link mint fails (generation/blocklist) | API | `503 share_unavailable` (or shortener code passthrough); dialog keeps `Mint on first copy` | "Couldn't create the short link - try again." |
| Shortened slug suspended/expired | Public | Shortener branded safe-error (403/410); never stale resume content | "This link is no longer available." (shortener copy) |
| Unverified custom host | Dialog | Custom row shows the connect entry; no URL shown; unverified hosts never render content | "Finish verifying your domain to use it here." |
| Domain downgraded/unlinked after sharing | Reader | Custom serving detaches; standard link stays live; canonical reverts | "This site isn't available." (feature 10 copy) |
| Bio block hidden while the dialog is open | Dialog | Row flips to `hidden` state live; toggle offered | "Currently hidden on your bio page." |
| Document unpublished while the dialog is open | Dialog | Flips to the not-public state live; copy rows removed | "Not public - publish to enable sharing." |
| Save lands mid-A4-view | UI | Bound version unchanged + notice | "Snapshot changed - re-open preview." |
| Rename while short link exists | System | Destination rewritten; if the event is lost the old-username `301` still resolves - one redirect hop, never a dead link | No visible error |
| Repeated first-copy mint attempts (double-click) | API | Idempotent: same slug, `created:false` after the first; no mint loop | No error; single disclosure |
| Anonymous burst on cv/short/custom paths | Public | Owning majors' throttles (Turnstile/WAF/rate rules); legitimate unlimited use unaffected | "Too many requests - try again in {seconds}." |
| SEO tag check fails live | Dialog | SEO row shows `Tags need attention` + the failing checklist item (a real signal, not a hidden state) | "Your CV tags need attention: {item}." |
| Helper rate limit | API | `429 throttled` + `Retry-After` | "Too many requests - try again in {seconds}." |
| Secrets audit (repo/logs/bundles) | Ops | Env/vault only; `.env` and overrides uncommitted; no secret in logs or bundles | Ops-facing build/audit failure |
