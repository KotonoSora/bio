# Payment Rails - Acceptance Criteria (feature 40)

Phase 1 BA · BDD; each AC verifies against `docs/02-architecture-techlead/40-payment-rails/`. Drill IDs referenced from TL 04's task table.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Signature conformance per rail | The conformance suite runs against the compose stack with fixture rails | Each of the eight intakes receives valid, tampered, and malformed deliveries | Valid verifies; tampered/malformed refused with the frozen rail code; zero order/ledger writes on refusals |
| AC-02 | Replay collapse | A settled `event_ref` for payos, sepay, vnpay, zalopay, napas, stripe, or wallet PSP | The identical delivery is posted again (and concurrently 10×) | Exactly one grant ever; every redelivery returns the original outcome; `provider_events` holds one row per ref |
| AC-03 | Amount binding | A pending order with a frozen amount | Deliveries arrive with +1 VND, −1 VND, and matching amount | Mismatches refuse with no state change + quarantine metric; the match grants exactly once |
| AC-04 | Wallet double-submit | One wallet order mid `authorizing` | Two authorize calls race (idempotency key = orderCode) | One PSP charge; one grant; loser reads the winner's outcome; no orphan `authorizing` after recovery |
| AC-05 | Sweep settlement | Webhook delivery is suppressed for a paid redirect-rail order | The 15-min sweeper runs | The order settles via verify ≤ one tick after the 30-min stale threshold; rerun writes nothing |
| AC-06 | Exception queue intake | A sepay transfer with an unknown reference and one with a wrong amount | Both webhooks arrive | Two `rail_exceptions` rows (correct reasons); rails acked; zero grants; console shows oldest-first |
| AC-07 | Ops link within grace | An `order_expired` exception inside the 24 h grace with a matchable order | Ops links it with a reason | Grant lands through 34's guard; audit shows ops actor + reason; exception `linked`; repeat link 409s |
| AC-08 | Late money never auto-grants | A verified payment for an order expired 5 minutes ago | Delivery + 3 sweep ticks pass | No automatic transition; only the exception row exists; auto-grant counter is zero |
| AC-09 | Dispute lifecycle | A paid stripe order | dispute.open → lost; then a second order dispute.open → won | Lost: `chargeback_reversed`, plan revoked with `plan.chargeback_reversed` audit, one `order.chargeback_reversed.v1`; won: restored to `paid`, no entitlement change; freeze held during `disputed` |
| AC-10 | Kill switch | A live rail | Ops flips the switch; a buyer starts checkout; an in-flight payment lands | Method delisted ≤ 5 s; new attempt refused; in-flight payment verifies + grants normally; flip audited |
| AC-11 | Probe battery gate | A rail build candidate | The pre-deploy battery runs per BR-15 | All probes green → release allowed; any red → release blocked; battery report archived per run |
| AC-12 | Secrets discipline | The repo + runtime logs under scan | CI runs the secrets grep + a rotation dry-run occurs | Zero secrets in repo/logs/audits; rotation completes with dual-read window; post-rotation verify drill green |
| AC-13 | On-demand verify | A stale `pending` order and a promo 0-VND order | Ops triggers verify on both | Stale order settles via the guard (audited); promo order returns 409 `order_not_verifiable`; foreignteam order 404; throttle at 1/30 s enforced |
| AC-14 | Response contracts | Each rail's contract table (TL 03) | A settled event, a validation-final refusal, and a transient failure are replayed | Settled → success code, no-op; final refusal → frozen refusal code, no redelivery expected; transient → redelivery-inviting code; redelivery settles once |
| AC-15 | Wallet timeout recovery | A wallet charge that timed out at the PSP | The status query sweep runs | Verdict applied exactly once; no second charge attempt exists (charge-call counter = 1) |
| AC-16 | Grace boundary | Exceptions at 23 h 59 m and 24 h 01 m since expiry | Ops attempts link on both | First links; second returns 409 `grace_expired`; refund/dismiss remain available on both |
