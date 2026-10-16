# Promo Codes - User Stories and Business Rules (feature 39)

Phase 1 BA · Enforced by `docs/02-architecture-techlead/39-promo-codes/`. Scope: `docs/01-requirements-ba/39-promo-codes/01-document-context-and-scope.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Buyer | enter a code and see the discounted total before paying, with a clear correction path | discounts never compound into unintended free pro | High |
| US-02 | Marketing/ops | launch a campaign, watch burn-down, and pause a leak instantly | marketing intent becomes enforceable guardrails | High |
| US-03 | Finance | see committed vs redeemed exposure per campaign | codes never become untracked liabilities | High |
| US-04 | Advocate/goodwill recipient | have credits applied as pro-days automatically | referral or goodwill stays time, never cash | Medium |
| US-05 | Fraud reviewer | see flagged patterns as signals and counts | containment without raw-log archaeology or auto-bans | Medium |
| US-06 | Reviewer | prove exposure never exceeds caps and rejections leak nothing | the discount surface is auditable and oracle-free | High |

## Business rules

- **BR-01 Kind semantics.** `percent` = 1..100, discount `floor(amount × value ÷ 100)`; `fixed_vnd` = ≥ 1 VND, discount `min(value, amount)`; `term_extend` = ≥ 1 days added via the credits ledger at grant, no price effect. Discounts never exceed the amount; final is always `max(0, catalog − discount)`, integer VND.
- **BR-02 0-VND path.** A fully discounted order skips provider handoff entirely: `provider='promo'` (server-minted literal; never client-accepted — clients sending it get `unknown_provider`), handoff kind `none`, immediate grant through the canonical amount-checked path, receipts render normally.
- **BR-03 Campaigns are bounded.** Create requires name, kind scope, value, `budget_cap_vnd`, `max_redemptions`, `per_user_limit`, target (`new|winback|all|invite_only`), and a window (`ends_at > starts_at`); unbounded campaigns are impossible at the column level.
- **BR-04 Lifecycle.** `draft → scheduled → live → (paused ↔ live) → ended`; ops transitions audited with actor + reason; scheduled→live and live/paused→ended run in 36's daily job (audits `campaign.started`/`campaign.ended`); `ended` terminal.
- **BR-05 Budget is DB-enforced.** Creates decrement `redeemed_vnd` + `redemption_count` behind a guarded update (`≤ cap` in the WHERE); exposure can never exceed the cap (`chk_campaign_cap` backs it); 80% burn alerts; 100% auto-pauses (audited system action). Orders ending without a grant restore the decrement once (terminal-transition guarded).
- **BR-06 Targeting.** `new` = no prior paid order in history; `winback` = resolver-free at evaluation; `all`; `invite_only` = the code is bound to a specific user (`invited_user_id`). Eligibility is checked at validation, create, and grant.
- **BR-07 In-flight inviolability.** A pending quoted order (≤ TTL) keeps its quote: pause/void/expiry between quote and payment never strands money — a verified payment of the frozen amount grants (`coupon.in_flight_honored` audited); new validations/creates block immediately.
- **BR-08 Pause latency.** Pausing halts new redemptions within ≤ 5 s (no status caches; fresh row reads); resume reopens immediately.
- **BR-09 Code format + storage.** `PREFIX-` + 13 suffix chars from a 31-symbol alphabet (no `0/O/1/I/L`), ≥ 64-bit entropy; stored only as HMAC-SHA256 hashes (`COUPON_HASH_KEY`); plaintext exists once, in the batch-create response; prefix `[A-Z2-9]{2,10}`, unique among non-ended campaigns.
- **BR-10 Batch generation.** Atomic per batch, actor-stamped; `{campaign_id, channel?, count, use_limit, per_user_limit, expires_at ≤ campaign end}`; one-time export (download → acknowledge, audited); hashes only afterward; collisions regenerate (negligible).
- **BR-11 Use semantics.** Single-use codes flip `redeemed` on first grant (bound via attribution columns); multi-use codes increment `uses` per grant, flipping at `use_limit`; concurrent redemptions serialize — exactly one wins per unit.
- **BR-12 Void.** Bulk-void flips only unredeemed codes (batch or campaign), audited with actor, reason, and count; redeemed discounts always stand; voided codes reject cleanly afterward.
- **BR-13 Stacking matrix (closed).** Exactly one coupon per order; a second code replaces the first only with explicit buyer confirmation; coupon + active trial = allowed (discounts the first paid term; trial unchanged); coupon + credit-days = allowed (days extend the discounted term); coupon + renewal = re-prices from the active catalog; the coupon applies only within its window ("one-time discount" copy).
- **BR-14 Rejections are oracle-free.** `invalid|expired|exhausted|not_eligible|already_redeemed` all return one neutral `coupon_invalid` at normalized timing (≤ 50 ms spread) except expiry (`coupon_expired`); brute force gets throttled.
- **BR-15 Quote integrity.** Validate responses are quotes, never reservations; the quoted total is frozen for the pending window and re-verified at grant; single-use consumption happens only at grant.
- **BR-16 Credits are pro-days.** `credits_ledger` rows (`days ≥ 1`, `source coupon|referral|goodwill`) are applied as term extensions by 36's daily job to `active|grace` rows: unconsumed days add to `period_end`, marked consumed with the subscription id, audited `plan.credits_applied`; credits never expire, never pay, never cash out.
- **BR-17 Goodwill issuance.** Ops-only, audited, within the configured per-case cap (`billing.promo.goodwillMaxDays`; absent config refuses issuance); referral vesting (post-refund-window paid grant) recorded for the gated referral surface, not built here.
- **BR-18 Attribution.** Orders carry `campaign_id`, `coupon_id`, `discount_vnd`; the granting subscription stamps `source='coupon'` + `source_ref = campaign_id` (V3's reserved vocabulary) — support answers "why is this user pro" from ledger + audit alone.
- **BR-19 Leak response.** One control pauses; optional bulk void (unredeemed only); audited; nothing else rotates (codes are already random).
- **BR-20 Abuse containment.** Velocity anomalies (codes/identity, identities/device) flag metrics and auto-pause the campaign (audited system action); no auto-bans in v1; self-referral voiding recorded for the gated surface.
- **BR-21 Rate limits + challenges.** Validate throttled (shared checkout budgets) + Turnstile for guests; ops actions rate-limited; 429 with `Retry-After`.
- **BR-22 Scoping.** No buyer reads of campaigns/codes exist (validation only; no oracle via timing, errors, or counts); ops routes role-gated; credits owner-read only.
- **BR-23 Config.** `billing.promo` `{goodwillMaxDays, referralDays}` via 32's audited ops flips; absence refuses the dependent paths (`promo_config_absent`); no default numbers exist.
- **BR-24 Attribution-safe metrics.** Campaign/channel readouts are aggregates only; zero buyer PII in partner-visible output.
