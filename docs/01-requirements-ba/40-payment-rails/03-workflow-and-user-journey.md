# Payment Rails - Workflow and User Journey (feature 40)

Phase 1 BA · W-1..W-6. Rules: `docs/01-requirements-ba/40-payment-rails/02-user-stories-and-business-rules.md`. Contract: `docs/02-architecture-techlead/40-payment-rails/03-api-contracts-and-payloads.md`.

## W-1 Redirect rail settlement (vnpay; zalopay/payos analogous)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Buyer | pays on the rail page | rail collects; browser returns to `/app/billing/return` | return is UX-only (BR-01) |
| 2 | Buyer UI | shows confirming + polls order status | honest state; no client-side grant trust | US-02 |
| 3 | Rail server | calls the IPN/callback endpoint | verify signature over raw bytes → persist `(provider, event_ref)` → resolve order → amount assert | BR-01, BR-02, BR-03 |
| 4 | System | grant through 34's guard | `pending → paid` + entitlement + `psp_ref` first-write + post-commit event | BR-04, BR-20 |
| 5 | System | responder | rail-contract success code only after step 4 commits | BR-05 |
| 6 | Sweeper | no notification within 30 min (stale pending) | on-demand verify against the rail API; settle exactly once | BR-06 |

## W-2 Bank-rail transfer (sepay; napas analogous)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Buyer | scans/enters the transfer with the order ref in the content | bank moves money; the rail pushes a webhook/sync | content template owned by 33 |
| 2 | Rail | posts the transfer notification | verify HMAC/bearer → persist event → match ref + amount | BR-01, BR-02 |
| 3 | System | exact match, order pending | grant via the guard (same as W-1 step 4) | BR-03, BR-04 |
| 4 | System | any mismatch (unknown ref, amount, cancelled, expired, duplicate) | open `rail_exceptions` row; ack the rail; no state change | BR-07, BR-08 |
| 5 | Ops | reviews the queue | link (grant via guard, within grace), refund (bank return, evidence), or dismiss — reason + audit | BR-08, BR-09 |
| 6 | Sweeper | hourly re-scan (48 h lookback) | misses become queue rows; nothing drops silently | BR-06 |

## W-3 Wallet charge (google-pay; apple-pay analogous)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Buyer | picks the wallet rail at checkout | handoff kind `token`; sheet opens client-side (apple: merchant session validated server-side first) | BR-17 |
| 2 | Buyer | authorizes in the sheet | client posts the token/cryptogram to the authorize endpoint | ownership from stored order only (BR-21) |
| 3 | System | PSP charge call (idempotency key = orderCode) | serialize per order; `authorizing` state visible | BR-17, BR-18 |
| 4 | PSP | confirms the charge | grant synchronously through the guard; buyer sees paid | Q-05 |
| 5 | PSP | timeout/ambiguous | recover via PSP status query — never a second charge | BR-17 |
| 6 | PSP | posts its webhook later | confirmation/audit path; duplicate delivery is a no-op | BR-05 |

## W-4 Sweep settlement (any rail)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Sweeper | 15-min tick (per-rail lock) | selects stale pendings (≤ 200) per rail | BR-06 |
| 2 | Adapter | queries the rail/PSP for each order | paid → settle via guard; failed/expired → rail-appropriate terminal edge (33's sweep vocabulary) | never late-auto-grant (BR-09) |
| 3 | System | settles/redelivers | idempotent; reruns write nothing | BR-05 |
| 4 | System | rail API error | backoff + metric; order stays stale, next tick retries | NFR |

## W-5 Dispute case (stripe; wallet disputes analogous)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Provider | posts `charge.dispute.*` (verified) | open `dispute_cases` + `→ disputed` via guard + freeze grants | BR-10 |
| 2 | Ops | assembles evidence before the deadline | submits via the rail adapter; case `evidence_submitted` + audit | BR-10 |
| 3 | Provider | dispute won | `disputed → paid`; case `won` | BR-11 |
| 4 | Provider | dispute lost | `disputed → chargeback_reversed`; plan revocation (38 helper) + claims event | BR-12 |
| 5 | Ops | deadline missed | loss path auto-applies per provider rules; case records who/what | BR-10 |

## W-6 Kill-switch incident (any rail)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Ops | flips the rail's kill switch (32's config path) | delisted from checkout ≤ 5 s; audited with reason | BR-13 |
| 2 | System | buyer attempts checkout on the rail | refused with the delisted copy; other seven rails untouched | BR-13, BR-16 |
| 3 | System | in-flight payments land | intake + sweeps + grants continue normally | BR-13 |
| 4 | Ops | rotates the secret (if compromised) + runs reconcile sweep | evidence trail; rotation drill on schedule | BR-14 |
| 5 | Ops | re-enables after reconcile | audited; metric notes the window | BR-14 |
