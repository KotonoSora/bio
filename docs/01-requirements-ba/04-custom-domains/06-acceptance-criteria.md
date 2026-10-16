# Custom Domains - Acceptance Criteria (feature 04)

Phase 1 BA · BDD checks gating the release of `docs/02-architecture-techlead/04-custom-domains/`. Rules cited as `BR-nn` from `docs/01-requirements-ba/04-custom-domains/02-user-stories-and-business-rules.md`.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Takeover drill | An unbound hostname; then one bound elsewhere | Both are requested; TXT control changes on the second | Neutral error serves for both until full re-verification; transfer completes only through re-verify with audit and notice (BR-01, BR-03) |
| AC-02 | Lapse drill | A live binding; TXT removed | The periodic recheck runs | Row flips `unverified` within 24 h with serving halt + notice; restoration re-verifies cleanly (BR-08) |
| AC-03 | Downgrade drill | Pro lapse with a verified binding | The entitlement window elapses; then re-upgrade inside the hold | Custom-host serving stops `<= 60 s` while `/:username` stays live; DNS untouched; re-upgrade restores without re-verification (BR-06, BR-07) |
| AC-04 | Cache drill | A publish on a bound host; a scan of unknown hosts | Cache and resolver run | Host-namespaced keys purge via the username tag; slug+host matrix green; unknown-host negatives cached with no DB amplification (BR-04, BR-05) |
| AC-05 | IDOR drill | User A CRUD against user B's domains; Host-header probing at a private profile | Requests resolve | `403` mutations, `404` unknown; private profiles and deactivated accounts never surface on any host (BR-11) |
| AC-06 | Verify-flow drill | Fresh pro account | Add -> TXT -> verify (on-demand and worker paths) | `verified` both ways; serving starts after TLS; unverified rows never served at any intermediate point (BR-01, BR-10) |
| AC-07 | Normalization drill | Mixed-case, IDN, trailing-dot, wildcard, over-long inputs | Add is attempted | Normalization/punycode correct; wildcard and over-long rejected `422 hostname_invalid`; denylisted rejected `422 domain_denied` (BR-02) |
| AC-08 | Removal drill | Verified binding | Owner removes it | Deleted immediately; serving halts at once; re-bind later requires full re-verify (BR-07) |
| AC-09 | Gate drill | Free account; then pro snapshot; then snapshot outage | Add/serve are attempted in each state | Free denied `403 domain_forbidden`; pro serves within 30 s; outage stops serving (fail-closed) within 60 s (BR-06) |
| AC-10 | Abuse drill | Cycling adds/verifies; a phishing report | Runs | Cycling throttled `429`; phishing report suspends serving, profile stays live on `/:username`; account untouched (BR-09) |
| AC-11 | Reclaim drill | A disputed binding with documented evidence | Ops forces a round; claimant proves DNS control; then a failing round | Success transfers in one audited transaction with notices; failure changes nothing (BR-12) |
| AC-12 | Serving-parity drill | A verified host | Home, `/cv`, and `/shop` are crawled | Same tags as `/:username`; canonical = custom host; `/cv` keeps `noindex` + canonical-to-bio (BR-04) |
