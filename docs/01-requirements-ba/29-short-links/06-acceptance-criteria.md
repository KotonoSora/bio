# Short Links - Acceptance Criteria (feature 29)

Phase 1 BA · BDD form. Every AC runs in the drill suite (task T-16 of `docs/02-architecture-techlead/29-short-links/06-step-by-step-implementation-plan.md`).

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Namespace isolation | Reserved slugs `go/r/a` plus one minted code | A username claim for `go` is submitted and both URLs are opened | Claim rejected; `/go/<code>` resolves as a link; profile paths never resolve as links |
| AC-02 | Counting honesty | 1,000 opens of a live 302 link and one first-open of a 301 link | Resolves complete | Hit events = 1,000 for the 302 link; the 301 counts once, later opens add nothing |
| AC-03 | Mint safety | Destination is on the blocklist | Mint attempted | 422 `destination_blocked`, nothing persisted, audit line written |
| AC-04 | Resolve safety | Link minted before its destination was listed | Destination re-listed, then link opened | 403 suspension page; no redirect leak; owner notified; evidence rows retained |
| AC-05 | Suspension speed and scope | Two links point to the same malicious destination | Reviewer confirms suspend | Both dead within 5 min; one audit event per link |
| AC-06 | Abuse budgets | One account, one IP, fresh quota | 21 mints inside one minute | 429 after the 20th; no rows beyond accepted mints |
| AC-07 | RBAC and IDOR | Creator A and B own one link each | A reads/patches/deletes B's code, via API directly | 403/404 with no data; database RLS denies even without owner context |
| AC-08 | Hot path | Production-shaped load, 10,000 rps resolve mix | 5-minute run | p95 <= 100 ms, p99 <= 250 ms; zero database writes on resolve (query log proof) |
| AC-09 | Contracts | Any error response | Response inspected | RFC 9457 shape with a stable `code`; OpenAPI diff additive-only; quota headers on every mint/list |
| AC-10 | Edit and tombstone | Live link with history | Destination edited, then tombstoned | Code unchanged; row + stats kept; resolve serves the gone page |
| AC-11 | Quota UX | Used = limit - 1 | Table loaded, then one mint completes | Meter shows reset date; submit disables only on server response, never hidden |
| AC-12 | Degraded states | Redis unavailable | A live code is opened | 302 still served via direct database path (p95 <= 300 ms); mint rejects with retry copy |
