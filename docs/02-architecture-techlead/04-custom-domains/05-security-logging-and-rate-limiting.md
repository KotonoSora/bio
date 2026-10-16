# Custom Domains - Security, Logging, and Rate Limiting (feature 04)

Phase 2 TL · BA sources: `docs/01-requirements-ba/04-custom-domains/` (BR-01, BR-02, BR-03, BR-09, BR-11, BR-12). Baseline: `docs/02-architecture-techlead/00-platform-baseline/08-security-baseline.md`; gates: `docs/02-architecture-techlead/00-platform-baseline/06-release-gates.md`.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | Wrong-site invariant: serving requires `status=verified`; every other state renders the neutral error; one code path decides (no fallback), no redirect surface | AC-01; state-matrix probe over all six states |
| S-2 | Challenge integrity: 32-byte random token, stored hashed, single-use per round, 7-day window, shown once; no token appears in logs or subsequent responses | AC-06; log scan + re-issue probe |
| S-3 | One host, one profile: DB unique constraint is the transfer guard; reclaim/transfer runs in one audited transaction with notices to both parties; ops never binds as a user | AC-01, AC-11; constraint probe |
| S-4 | Normalization + denylist: IDNA2008 punycode, wildcard/length rejection, denylist at add and on every recheck; denylist writes are ops-only, audited | AC-07; seeded denylist drift probe |
| S-5 | IDOR and parity: owner-scoped CRUD (`403` foreign, `404` unknown); private profiles and deactivated accounts render the same neutral shape/timing as unknown on any host | AC-05; interleaved class probe |
| S-6 | Gate fail-closed: pro required from the 30 s snapshot; snapshot miss -> no serving; lapse stops `<= 60 s`; `/:username` unaffected | AC-09; outage drill |
| S-7 | Abuse: add/verify throttles + cycling counters; phishing report suspends serving pending review; domain suspension never touches the account | AC-10; report drill |
| S-8 | TLS fail-safe: no serving without valid managed certificate; renewal 14-day window; failure withholds serving and pages ops | NFR-06; forced-expiry drill |
| S-9 | Audit + logging: add/verify/recheck transitions/lapse/restore/removal/reclaim/suspension audited with actor + target; logs carry hostname + state + trace, never TXT tokens or DNS responses in full | AC-11; audit reconstruction |
| S-10 | Reclaim discipline: ops scope, documented evidence reference required, result audited, both-party notices; no other ops path can change a binding | AC-11; route-scope probe |

## Rate envelopes

| Route class | Envelope | On breach |
|---|---|---|
| Domain add | 10/day/user + quota 10 total | `429 throttled` / `422 domain_quota_reached`; cycling counters |
| Verify / recheck (owner) | 10/min/user | `429`; no silent backlog |
| List | 60/min/user | `429` |
| Internal resolve | service budget, trace-attributed | `429` + trace; gateway circuit-breaker to neutral error |
| Denylist/reclaim (ops) | 30/min/operator, audited | `429`; anomaly alert |

## Degrade matrix

| Condition | Behavior |
|---|---|
| Entitlement snapshot unreachable | Serving stops (fail-closed to free); `/:username` unaffected; `entitlement_snapshot_miss` alert |
| DNS resolver (DoH) failure | Verify attempts stay `pending`; no verification ever succeeds without a TXT match (fail-closed); worker backlog alert |
| TLS issuance/renewal failure | Serving withheld for that host; owner notice; ops page |
| Edge resolve cache down | Direct internal resolve per request (latency rise, correct results); circuit-breaker to neutral error under overload |
| Abuse tooling down | Suspensions still possible via ops route (audited); counters queue |
| account-service outage | Deactivated accounts stay hidden via feature 06's `profile.acctstat` bound; domains serve per snapshot cache until it expires, then fail closed |

## Logging

- Fields: hostname, binding state, transition reason, attempt round, trace; never TXT token values, never full DNS payloads, no user PII beyond `user_id` (internal).
- Alertable: verification success rate drop, recheck failure spikes, denylist hits on live bindings, cycling counters, TLS failures, resolve error rate.

## Gates

- G4: endpoints additive-only; resolve contract rename-frozen (feature 10 consumer).
- G5: S-1, S-2, S-3, S-5, S-6 probes run per release.
- G7: drill set AC-01..AC-12 attaches to the tag; feature 02 purge drill includes a bound host in its collateral fixture.
