# Social Connections - Workflow and User Journey (feature 41)

Phase 1 BA · W-1..W-4. Rules: `docs/01-requirements-ba/41-social-connections/02-user-stories-and-business-rules.md`. Contracts: `docs/02-architecture-techlead/41-social-connections/03-api-contracts-and-payloads.md`.

## W-1 Connect (per network)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | picks a network, taps Connect | server checks plan cap server-side → OAuth start with `state` + PKCE; refusal cites cap + upgrade path | BR-01, BR-02, BR-04 |
| 2 | Creator | authorizes on the provider page | provider redirects to the callback route | scopes publish-minimal |
| 3 | System | callback arrives | verify `state` + PKCE → exchange code → encrypt tokens → row created `healthy` | BR-02, BR-03 |
| 4 | System | audit | `connection.created` (network, account ref; never tokens) | BR-12 |
| 5 | Creator | sees the account | health chip + account ref; selectable as a composer target | BR-05 |

## W-2 Refresh + health cycle

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Scheduler | skew window reached (`expiring`) | proactive refresh; success rewrites expiry silently | BR-06 |
| 2 | System | refresh fails with auth error | status `expired`/`revoked`; queued targets for the account cancel with notice | BR-05, BR-07 |
| 3 | System | provider webhook (revoke) where offered | verified → status flips within the next cycle | BR-07 |
| 4 | System | health read | states visible per account; dead ones unselectable with one-click reconnect | BR-05 |

## W-3 Disconnect / provider revoke

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | taps Disconnect (confirm) | vault row deleted immediately; remote revoke attempted where offered | BR-08 |
| 2 | System | queued targets exist | each cancels with `skipped_dead_connection` semantics (42's state machine) + creator notice | BR-08 |
| 3 | System | audit | `connection.disconnected` + queued-cancel count | BR-12 |
| 4 | Creator | reconnects later | fresh OAuth dance; no old material resurrected | BR-08 |

## W-4 Key rotation (quarterly)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Ops | deploys new master key version | new writes use the new key; dual-read serves both | BR-09 |
| 2 | Migration job | re-wrap pass | decrypt-with-old → encrypt-with-new per row, batched, idempotent, audited | BR-09 |
| 3 | System | during the window | zero publish failures; refresh unaffected | BR-09 |
| 4 | Ops | confirms completion | old key retired after full re-wrap verification | BR-09 |
