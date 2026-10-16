# Promo Codes - Architecture and Tech Stack (feature 39)

Phase 2 TL · Service: `billing-service` (port 8088, `billing_db`, Redis db 10). Implements `docs/01-requirements-ba/39-promo-codes/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Service boundary

| Owns | Consumes (contract -> owner) |
|---|---|
| The promo tables (V6): campaigns, batches, coupons, `credits_ledger`; the validation + pricing service; the 0-VND promo minting branch; batch generation/export/void; goodwill issuance; attack-signal metrics; the credits application pass inside 36's job | Checkout create/repricing + idempotency + expiry sweep -> 33 (calls this feature's pricing service); grant transaction + subscription writes + `source` stamping -> 34/35; daily job host + lifecycle audits -> 36; order terminal transitions (compensation triggers) -> 33/34; receipt rendering of promo orders -> 37; notification pipeline -> 32; config flips -> 32's `ops_config` |

No new service, no new Redis keys (`bill:rl:*` reused); one buyer endpoint (validate) + one credits read; everything else is ops-side.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | Module `com.app.billing.promo`: `CouponPricingService` (validate + price + decrement), `CampaignAdmin`, `BatchAdmin`, `CreditsAdmin`; pricing is the only path that can change an order's payable amount | One repricing authority; checkout never computes discounts itself |
| D-2 | `V6__promo_codes.sql`: `promo_campaigns`, `coupon_batches`, `coupons`, `credits_ledger` + `orders` ALTERs (`campaign_id`, `coupon_id`, `discount_vnd`); `orders.provider` needs no DDL change — `promo` is a documented server-minted literal (V1 has no provider CHECK) | Single-DDL-owner; the reserved ninth literal stays reserved |
| D-3 | Code storage: HMAC-SHA256(`COUPON_HASH_KEY`, normalized code) as the lookup key (`UNIQUE`); 13-char suffix from the 31-symbol alphabet; plaintext only in the batch-create response; one ack, audited | Unguessable at rest; deterministic lookup; no plaintext persistence |
| D-4 | Budget discipline: decrement at create behind a guarded `UPDATE` (`status='live' AND now() BETWEEN starts_at AND ends_at AND redeemed_vnd + d <= budget_cap_vnd AND redemption_count + 1 <= max_redemptions`); restore on terminal-without-grant (fires once per order from the terminal transition); 100% -> auto-pause (audited) | Exposure never exceeds the cap; DB CHECK backs the code path |
| D-5 | In-flight inviolability: grant re-checks eligibility; campaign pause/void/expiry mid-flight does not block a verified frozen-amount payment (audit `coupon.in_flight_honored`); new validates/creates read status fresh (no cache) | No surprise charge, no stranded money; leaks still stop within its 5 s budget |
| D-6 | Redemption at grant: guarded `uses` increment + `redeemed` flip at limit + `term_extend` -> `credits_ledger` insert + attribution already stamped at create; per-user races serialized by `pg_advisory_xact_lock(hash(user, coupon))` inside the create transaction | Exactly-once consumption; multi-use safe |
| D-7 | 0-VND branch: pricing returning 0 makes create mint `provider='promo'` + handoff kind `none` + immediate grant call through the canonical amount-checked path; clients can never send `promo` (`unknown_provider`) | Zero rails touched for free orders; the registry stays 8 for buyers |
| D-8 | Credits are one ledger: `source IN (coupon, referral, goodwill)`; application runs as a pass in 36's daily job over `active`/`grace` rows (all unconsumed days, one txn per user, guarded `consumed` flag, audit `plan.credits_applied`); the `referral` source is reserved for the gated referral surface | Term extension reuses 36's period math; no grant-path edits to money-critical code |
| D-9 | Campaign windows run in the same job: `scheduled -> live` at `starts_at`, `live`/`paused` -> `ended` at `ends_at`, plus active-coupon expiry sweep (one summary audit per run, count-based) | No new scheduler; bounded automation |
| D-10 | Rejections are computed identically: one code path evaluates all classes, returns the same neutral problem except expiry, and pads no branches (constant-work comparison on the hash) | Oracle-free by construction; timing test enforces |
| D-11 | Config: `billing.promo` `{goodwillMaxDays, referralDays}` in 32's `ops_config` (5 s flips, audited); absent -> 409 `promo_config_absent` on dependent paths | No invented numbers; launch switch by construction |

## Cross-edits applied with this feature

| Target | Edit |
|---|---|
| `docs/02-architecture-techlead/32-monetize-overview/02-database-schema-design.md` | Chain row V6 expanded to the full table set; `billing.promo` row added to config surfaces |
| `docs/02-architecture-techlead/32-monetize-overview/03-api-contracts-and-payloads.md` | Registry rows: validate (expanded), credits read, ops campaign/batch/goodwill routes |
| `docs/02-architecture-techlead/33-checkout-session/03-api-contracts-and-payloads.md` | Rules: coupon pricing delegation, 0-VND branch (`provider='promo'`, Handoff `none`), discount rounding, compensation note |
| `docs/02-architecture-techlead/34-order-status-and-orchestration/01-architecture-and-tech-stack.md` | D-3 append: coupon consumption + term-extend ledger insert + `source='coupon'` stamping inside the grant transaction |
| `docs/02-architecture-techlead/34-order-status-and-orchestration/02-database-schema-design.md` | Grant semantics: coupon confirmation bullets + `coupon.*` audits |
| `docs/02-architecture-techlead/36-subscription-lifecycle/01-architecture-and-tech-stack.md` | D-1 append: job gains 39's passes (campaign windows, credit application, coupon expiry) |
| `docs/02-architecture-techlead/36-subscription-lifecycle/02-database-schema-design.md` | Semantics: new job passes + audits (`plan.credits_applied`, `campaign.started/ended`, `coupon.expired` summary) |
| 35 / 37 | No edits: ledger writer, resolver, and receipt rendering consume the recorded contracts unchanged |
