# Monetize Overview - Exception Handling and Errors (feature 32)

Phase 1 BA · Buyer copy is vi/en; every code below is a stable RFC 9457 `code` string (additive-only). Rail mechanics (40) and lifecycle (36-38) extend this catalog — never redefine these.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Create without `provider` | client error → 422 | validation rejects before any side effect; response carries the eligible provider list | `provider_required` — "Choose a payment method." |
| Unknown or delisted `provider` | client error → 422 | registry + eligibility checked; nothing created | `unknown_provider` — "This method isn't available right now." |
| Idempotency key reused across intents | client error → 422 | stored intent mismatch detected; existing order untouched | `idempotency_key_reuse` — "This request conflicts with an earlier one — start a new checkout." |
| Quote expired (> 15 min) | client error → 410 | no side effects; fresh quote required | `quote_expired` — "This price has expired — review the new price." |
| Amount changed between quote and create | client error → 409 | create refused; buyer re-quotes | `amount_changed` — "The price changed — review before paying." |
| Double-submit (keyless, ≤ 90 s fingerprint) | idempotent success | collapsed to the single existing order; same handoff returned | none — same pending page |
| Rail killed mid-flow | availability → delist ≤ 5 s | in-flight pendings route to that rail's settlement path with support copy; no silent loss | `rail_unavailable` on new attempts — "This method is temporarily unavailable; your payment status is safe — check your order." |
| Verified confirmation fails amount check or signature | ops + buyer-safe → no grant | confirmation quarantined to ops with evidence; buyer order lands `failed` via sweep; audit appended | "We couldn't confirm this payment — contact support with your order code." |
| Webhook with bad signature | provider error → 401 | rejected, replay-proof; provider-health metric + audit | none (provider-facing) |
| Provider handoff timeout | transient → retry or other rail | order stays pending; sweep owns expiry; buyer may retry or switch method | `handoff_failed` — "The payment could not start — try again or choose another method." |
| Rate limited (quote/create/poll abuse) | abuse → 429 | per-IP/session throttle; Turnstile on anonymous surfaces | `throttled` — "Too many attempts — try again in a moment." (timer shown) |
| Config store down | degrade | cached last-good config serves; flips queue until restore; ops banner shows the stale version | none (user-invisible) |
| Notification pipeline outage | degrade | grants proceed; send backlog visible to ops; nothing blocks on sends | none |
| Secrets rotation drift | ops | rail auth errors detected → kill switch flipped + ops page; rotation remains no-downtime | delisted rail renders `rail_unavailable` copy |
