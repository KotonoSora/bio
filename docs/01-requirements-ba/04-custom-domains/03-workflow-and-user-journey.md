# Custom Domains - Workflow and User Journey (feature 04)

Phase 1 BA · Flows executed by `docs/02-architecture-techlead/04-custom-domains/`. Rules cited as `BR-nn` from `docs/01-requirements-ba/04-custom-domains/02-user-stories-and-business-rules.md`.

## Add and verify

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator (pro) | Settings -> Domains -> adds a hostname | Normalized + punycode-stored; denylist + plan checked; row `pending` with a fresh challenge round; TXT name/value shown once | BR-01, BR-02, BR-06 |
| 2 | Creator | Sets `_rb-challenge.<host>` TXT | - (owner-side action) | BR-01 |
| 3 | Creator | Clicks Verify | On-demand check runs immediately; success flips `verified`, serving starts after TLS issuance | BR-01, BR-10 |
| 4 | System (worker) | Polls pending rounds (30 s, 2 m, 5 m, 15 m, 1 h, then 6 h) | Success as above; 7-day window expiry leaves `pending` with regenerate guidance | BR-01 |
| 5 | System | Verified | Name reserved globally; recheck schedule starts (24 h staggered); owner notice; audit row | BR-03, BR-08 |

## Serving

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Visitor | Opens the custom host | Host resolved (30 s positive cache): `profile` -> SSR as `/:username` with canonical = custom host; `unverified`/unknown -> neutral error page, 60 s negative cache | BR-04, BR-11 |
| 2 | System | Publish happens | Host-namespaced keys purge via the username tag (same purge-on-publish as `/:username`) | BR-05 |
| 3 | Visitor | Opens `host/cv` or `host/shop` | Mounted per profile settings; `/cv` keeps `noindex` + canonical-to-bio semantics | BR-04 |

## Lapse, restore, and removal

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System | Entitlement lapses | Serving stops within `<= 60 s`; binding `suspended`; DNS untouched; notice with restore path | BR-06, BR-07 |
| 2 | Creator | Re-upgrades within 30 days | Passing recheck restores `verified` without re-verification; serving returns `<= 60 s` | BR-07 |
| 3 | Creator | Re-upgrades after 30 days | Binding is `unverified`; fresh challenge round required | BR-07 |
| 4 | Creator | Removes the domain | Binding deleted immediately; serving halts at once; re-bind later is a full re-verify | BR-07 |

## Control loss and disputes

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System (recheck) | TXT missing/moved on a live binding | `verified` -> `unverified` within 24 h; serving halts; notice with re-verify path | BR-08 |
| 2 | System | `unverified` with failing control for 14 days | Auto-release: row deleted; hostname addable again | BR-08 |
| 3 | Disputant | Claims a host bound elsewhere | `409 domain_taken`; support runbook: evidence + ops reclaim round | BR-03, BR-12 |
| 4 | Support/ops | Triggers reclaim | Fresh challenge round forced; success transfers ownership (audited, notices to both); failure changes nothing | BR-12 |

## Abuse paths

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Abuser | Adds/verifies hosts in a loop | Throttled (`429 throttled`); counters alert abuse tooling | BR-09 |
| 2 | Anyone | Reports phishing on a bound host | Serving suspended pending review; profile stays live on `/:username` | BR-09 |
| 3 | Abuser | Points unknown hosts at the edge | Neutral error only; negatives cached (no DB amplification) | BR-04, BR-11 |
