# Payment Rails - Exception Handling and Errors (feature 40)

Phase 1 BA · Rail-facing refusal codes are rail-contract values (TL 03 table); buyer copy is symmetric (BR-24). Ops problems are RFC 9457 (`code` field).

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Webhook signature/token invalid (any rail) | Rail intake | 401 (or rail contract refusal code); persist nothing past the metric; rate-clamp the source | None (rail-facing); buyer unaffected |
| Signed payload edited after signing | Rail intake | Signature mismatch → same as above; `rail.verify_failed` metric + alert on burst | None |
| Replayed delivery (same `event_ref`) | Rail intake | Read original outcome; return the same response; zero side effects | None |
| Valid event, unknown order ref (bank rails) | Rail intake | Ack + `rail_exceptions` row (`unknown_ref`); no grant | Buyer sees "Waiting for your transfer…" while pending; support resolves |
| Valid event, amount ≠ order (either direction) | Rail intake | Ack + exception (`amount_mismatch`); hold per BR-08; never auto-grant | Buyer: no misleading success; support contacts via exception resolution |
| Event for a cancelled order | Rail intake | Ack + exception (`order_cancelled`); refund path recommended | "Payment didn't complete." if buyer polls; support-driven refund |
| Event for an expired order, within 24 h grace | Rail intake | Ack + exception (`order_expired`/`late_transfer`); ops link eligible | "This payment arrived after expiry — support can help." |
| Late transfer beyond grace | Rail intake | Exception only `refund`/`dismiss` eligible | Same copy; resolution is a bank return |
| Duplicate-content transfer (two transfers, same ref) | Rail intake | First settles/opens; second opens `duplicate_ref` exception | None until resolution |
| Delivery during rail outage/5xx | Rail intake | Transient response invites redelivery; sweeps catch the rest within 15 min | Buyer: confirming continues |
| Webhook never arrives; buyer paid | Sweep | 30-min stale threshold → rail verify → settle via guard | Buyer: paid once verified (W-1 step 6) |
| Sweep finds payment for expired order | Sweep | Never auto-grants; opens/updates exception; ops path per grace | Copy as above |
| Rail/PSP API down on ops verify | Ops | 502 `verify_unavailable`; audit attempt; retry allowed | Ops console: "Rail unavailable — retry." |
| Ops verify on an order with no rail reference | Ops | 409 `order_not_verifiable` (e.g., promo 39, or never handed off) | "Nothing to verify for this order." |
| Exception already resolved (link/refund/dismiss raced) | Ops | 409 `exception_conflict`; console refreshes state | "Already resolved — refresh." |
| Link attempt past grace | Ops | 409 `grace_expired`; refund/dismiss remain | "Grace elapsed — refund path only." |
| Dispute opened on paid order | Provider → guard | `→ disputed`; buyer notice; grants frozen; no refund router contact (38 refuses terminal `disputed`) | "This payment is under review." |
| Dispute deadline approaching (≤ 72 h) | System | Alert + case highlight; overdue → auto-loss handling per provider rules | Ops console escalation |
| Evidence submit after case closed | Ops | 409 `dispute_conflict` | "Case already resolved — refresh." |
| Dispute won | Provider → guard | `disputed → paid`; case `won`; no entitlement change | "Review complete — payment stands." |
| Dispute lost | Provider → guard | `→ chargeback_reversed`; plan revocation via 38's helper (audit `plan.chargeback_reversed`); `order.chargeback_reversed.v1` emitted (claims invalidate) | Buyer: access ends per plan rules; support copy reads device truth |
| Wallet charge timeout (PSP ambiguous) | Wallet path | Hold `authorizing`; recover via PSP status query; never a second charge | "Contacting your provider…" then honest verdict |
| Wallet sheet abandoned | Wallet path | Local expiry/cancel per 33's sweep; no charge exists | "Payment didn't complete." |
| Apple merchant session expired/reused | Wallet path | Validate-merchant refuses; single-use TTL enforced | Sheet fails cleanly; retry allowed |
| Kill switch flipped mid-checkout | Any | Existing checkout completes; new attempts refused (33 surfaces delist ≤ 5 s) | "This payment method is temporarily unavailable." |
| Secret rotation mid-flight | System | Dual-read window per rotation runbook; settled events unaffected; in-flight retried by rail | None (rail-side); ops sees rotation audit |
| `provider_events` insert conflict (concurrent duplicates) | System | Second waits on the unique index, then no-ops with the original outcome | None |
| Sweep crash mid-batch | System | Next tick resumes; idempotent; alert after 2 missed ticks | Finance sees the gap in `sweepLog` |
