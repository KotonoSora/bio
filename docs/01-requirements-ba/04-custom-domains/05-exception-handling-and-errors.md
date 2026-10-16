# Custom Domains - Exception Handling and Errors (feature 04)

Phase 1 BA · Failure paths handled in `docs/02-architecture-techlead/04-custom-domains/05-security-logging-and-rate-limiting.md`. Codes are stable contract; problems follow RFC 9457.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Wildcard or malformed hostname | API | `422 hostname_invalid`; nothing persisted | "Enter a hostname like example.com or blog.example.com (no wildcards)." |
| Denylisted hostname | API | `422 domain_denied`; added to abuse counters | "This domain can't be added." |
| Hostname bound elsewhere | API | `409 domain_taken`; dispute path offered | "This domain is already connected. If it's yours, contact support with DNS proof." |
| Free account adds a domain | API | `403 domain_forbidden` (fail-closed snapshot); no partial row | "Custom domains are a Pro feature." + upgrade path |
| TXT not set yet / wrong value | Verify | `200` with status `pending` + attempts info; no error inflation | "We couldn't find the record yet. It can take a few minutes to propagate." |
| Challenge window expired (7 days) | Verify | Status `pending` with `challengeExpired` flag | "This verification window expired - generate a new record." |
| TLS issuance fails on verified host | Worker | Serving withheld (fail-safe); retry with backoff; owner notice | "Setting up your domain's certificate - we'll notify you." |
| Entitlement snapshot unreachable on resolve | Serving | Fail-closed to free: custom-host serving stops; `/:username` unaffected; counter alertable | Neutral error page (same as unverified) |
| Lapse detected | Serving | `suspended` + serving stop `<= 60 s`; DNS untouched; notice | Owner: "Your Pro plan ended - your domain is paused. Your DNS records were not changed." |
| TXT removed/moved on live binding | Recheck | `unverified` within 24 h; serving halts; notice | Owner: "We lost control of <host>. Re-verify or it frees up in 14 days." |
| `unverified` for 14 days | Worker | Auto-release (row deleted); hostname addable | Owner notice: "<host> has been released." |
| Phishing report on bound host | Abuse | Serving suspended pending review; account untouched | Visitor: neutral error. Owner notice on suspension |
| Unknown/unverified host requested | Serving | Neutral `404` page, edge-cached negative 60 s; no profile query, no redirect | "This site isn't available." |
| Rapid add/verify cycling | API | `429 throttled` + `Retry-After`; abuse counters | "Too many attempts. Try again in {seconds}." |
| Private profile / deactivated account on a bound host | Serving | Neutral error (same as missing); no leakage, no timing class difference | "This site isn't available." |
| Reclaim round failure | Ops | No change to existing binding; both parties unchanged; audited | Support-facing: "Verification did not succeed." |
