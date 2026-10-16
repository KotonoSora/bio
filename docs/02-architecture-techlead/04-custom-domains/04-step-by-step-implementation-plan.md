# Custom Domains - Step-by-Step Implementation Plan (feature 04)

Phase 2 TL · Dependency-ordered chain; each task's acceptance goal gates the next. Criteria cited from `docs/01-requirements-ba/04-custom-domains/06-acceptance-criteria.md`.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `V3__custom_domains.sql`: `custom_domains` + `domain_denylist` + RLS + indexes | Feature 06 T-02 (profile_db) | TL 02 drills 1-3 green |
| T-02 | BE | Owner endpoints + `HostnameNormalizer` + denylist check + pro gate (snapshot, fail-closed) + add quota + throttles | T-01 | AC-07, AC-09 (request half) green |
| T-03 | BE | Worker: `DomainVerifier` (DoH TXT), poll backoff, 24 h staggered recheck, transitions (>pending/verified/unverified/suspended), auto-release 14 d, hold 30 d | T-01 | AC-02, AC-03 (state half) green |
| T-04 | DevOps/BE | Gateway Host routing: non-platform Host -> resolve; `blocked`/`unknown` short-circuit to the neutral error; edge caches 30 s/60 s | T-02 | AC-01, AC-04 (negative-cache half) green |
| T-05 | BE | Serve parity: public-site mounts with host context; canonical host-aware (feature 10 row); `ssr:{host}:{path}` keys username-tagged; purge on publish + `domain.state_changed.v1` | T-04 | AC-12, AC-04 (purge half) green |
| T-06 | BE | Takeover + reclaim: transfer transaction (delete/update + audit + notices), `409 domain_taken` path, ops route | T-03 | AC-01, AC-11 green |
| T-07 | DevOps | TLS pipeline: issuance on `verified`, auto-renew with 14-day window, withhold-serving on failure + ops page | T-03 | AC-06, NFR-06 green |
| T-08 | BE | Plan-gate wiring: snapshot consume (30 s cache), lose -> suspended `<= 60 s`, restore path with recheck gate | T-03 | AC-09 (serving half), AC-03 green |
| T-09 | FE | Domains settings: add input with normalization preview, TXT display, status chips, lapse/verify notices, remove dialog | T-02 | AC-06 UI half, AC-08 UI half green |
| T-10 | DevOps/BE | Abuse + drills: cycling counters, phishing suspension path, denylist ops routes, AC-01..AC-12 wired to gates | T-04..T-09 | G5 probes + G7 spot-check green |

## Notes

- The feature 02 purge participant entry for `profile-service` (docs/02-architecture-techlead/02-account-deletion/03-api-contracts-and-payloads.md) covers `custom_domains`; no separate deletion path exists here.
- Feature 10's tag-contract row (canonical) gains the host-aware case in this change; its crawler/sitemap stays platform-host-only by design (custom hosts serve, sitemap lists `/{username}` - no duplicate-indexing change).
- Denylist drift: rechecks re-evaluate the list; a hit moves a live binding to `suspended` pending abuse review - never a silent delete.
- Rollout order: T-01..T-03 ship dark (no routing), T-04/T-05 flip serving behind the plan gate; a pro account with an unverified host never sees content at any intermediate deploy.
