# Checkout Session - Workflow and User Journey (feature 33)

Phase 1 BA · The uniform session flow; dispatch/status expansion lives in feature 34, rail mechanics in 40.

## Buyer: intent → paid-once (session or guest)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Buyer | opens checkout for a plan or product | quote renders: exact `amountVnd`, eligible methods in config order, coupon applied; zero side effects | BR-01/02 |
| 2 | Buyer | picks a rail (or resumes with a saved choice) | explicit `provider`; capability-gated members show `disabledReason`, never hidden silently | BR-03/10 |
| 3 | Buyer | submits | server validates (eligibility, coupon, email, URLs, ship-to), re-prices, pins the fee snapshot, creates one order (`pending`), and returns the handoff `next` | BR-03/05/06 |
| 4 | System | handoff is a URL/QR/sheet/token | buyer pays at the provider; the order stays `pending` | BR-06 |
| 5 | Buyer | returns via provider redirect | `/app/billing/pending/{orderCode}` polls canonical status; forged params change nothing | BR-09 |
| 6 | System | verified confirmation arrives | grant per 34; receipt email queued (pipeline); buyer sees `paid` | BR-09/16 |

## Guest product / tip path

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Guest buyer | enters via a product page or tip jar | tip jars call shop's endpoint first (bounds, Turnstile); product checkout goes straight here | 25's flow |
| 2 | System | create runs | guest `user_id` minted (`guest:` + HMAC), email mandatory, order created like any other | BR-11 |
| 3 | Guest buyer | returns/pays | same pending page; receipt emailed; no account, no claim machinery | BR-11 |

## Expiry and cancel

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System | sweep runs (≤ 5 min cadence) | lapsed `pending|authorizing` → `expired` once; rerun no-op | BR-07 |
| 2 | Buyer/support | cancels a pending order | `cancelled` once; already cancelled → 200 idempotent; other terminal → 409 `cancel_conflict` | BR-08 |
| 3 | System | late money hits `expired|cancelled` | rejected + logged; audited exception re-link only on rail providers (40) | BR-07 |

## Ops: selection control

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Ops | flips eligibility/display/kill switch (reason mandatory) | config version bumps; effect ≤ 5 s; audited | 32 BR-06 |
| 2 | Ops | kills a rail | delisted from `payment-methods` ≤ 5 s; in-flight pendings settle via that rail's path with buyer messaging | BR-10 |
| 3 | Support | answers "why isn't X offered" | from config + capability rules, never code | BR-10 |

## Release: shared-contract drills

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | QA | runs the identity drill across cells | quote = charged to the VND; tamper re-priced and logged | BR-02 |
| 2 | QA | runs double-submit per handoff kind | single row + same handoff; key reuse across intents 422; keyless collapse | BR-04 |
| 3 | QA | races cancel against grant; reruns sweeps | exactly one terminal outcome; no resurrection | BR-07/08/16 |

## Lead opt-in (checkout capture)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Buyer | checks the opt-in box (unchecked by default) | consent recorded with `source=checkout` + `sourceRef=orderCode` | BR-13 |
| 2 | System | order created | post-commit lead write with bounded retry; order never blocks or fails on it | BR-13 |
