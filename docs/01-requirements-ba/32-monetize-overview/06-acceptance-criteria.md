# Monetize Overview - Acceptance Criteria (feature 32)

Phase 1 BA · BDD; the cross-rail conformance row is the release gate that matters.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Registry drill | the eight-member registry as specified | each member checkout path is reached; a ninth rail without a specified minor + registry entry is offered | the eight paths are reachable; the ninth is not offerable (422 `unknown_provider`) |
| AC-02 | No-default drill | a checkout with omitted or unknown `provider` | create runs; refund routing runs; the CI grep gate scans checkout/refund/plan-purchase code | omitted/unknown → 422 with the eligible list; routing reads the stored provider only; grep finds no default or fallback ordering |
| AC-03 | End-to-end drill per rail (sandbox) | a sandbox order on any rail | quote, double-submit, verified grant with replay, gate reads, expiry/cancel races, full refund run | quote = charged = receipt to the VND; single order row; grant exactly once; ledger visible within SLA; races safe; refund revokes per rule with re-grant refused |
| AC-04 | Baseline probes | cross-user access, rendered errors/quotes/history, anonymous money endpoints, repo/logs/bundles under secrets grep | IDOR, XSS, abuse, and secrets probes run | 403 + RLS deny; XSS-stripped rendering with CSP holding; Turnstile + WAF throttling; secrets grep clean |
| AC-05 | Conformance matrix | all eight rails × the shared drill row | the matrix runs before any rail change ships | every cell green and recorded; any red blocks the rail |
| AC-06 | Kill-switch drill | a rail killed by ops (audited) | the switch flips; in-flight pendings settle | delisted ≤ 5 s; pendings routed with buyer messaging; other rails unaffected |
| AC-07 | Display-order drill | config present, then absent | `payment-methods` renders; screenshot diff taken | API order == rendered order both ways; no re-sorting, no favoritism |
| AC-08 | Notification drill | a grant enqueues a receipt; a lead-bound send targets a suppressed contact; the provider has an outage | sends process | transactional sends dispatch ≤ 60 s; suppressed lead-bound sends are not delivered; grants proceed during outage with backlog visible; no marketing rides the pipeline |
| AC-09 | Container + load drill | the release image | `docker run` + `podman run`; platform mixed-load gate | digest pair boots, compose/rollback green; 100k-concurrent normal-use mix passes |
| AC-10 | Trace drill | any order | support looks it up by `orderCode` | provider refs, status history, ledger, receipts, refund annotations, and audit replay in one trail |
