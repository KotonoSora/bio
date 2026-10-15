# Subscription Plans (free vs pro + AI credits)

PLAN ref: P3-3. DEVIATION FLAGGED: hard PayOS-default reading removed per correction — checkout mechanics in `checkout/checkout-session.md` with explicit provider from `checkout/provider-selection.md` (all eight rails); grants via each member's webhook/callback/IPN into `billing/entitlement-ledger.md`. No provider is the default for plans.

## Purpose

Define what money buys: a two-tier plan matrix (free vs pro) with server-enforced gates, plus a capped AI JD-tailoring allowance tracked as generation credits. UI badges and upsells may suggest the tiers, but every gate is re-checked on the server so direct API calls cannot bypass them.

## Users

| Role | Permissions | Notes |
|---|---|---|
| Free user | One profile, unlimited links, tips, basic resume, platform badge | Knows what money buys before upgrading |
| Pro subscriber | Unlimited profiles, custom domain, realtime analytics, premium templates/logo, AI JD-tailor allowance | Gates removed; every gate re-checked on the server |
| Buyer of single products (course/digital/tip/paywall) | Purchases unaffected by plan tier except where shop major explicitly grants plan-holder discounts (none in v1) | Single products stay separate from plans in v1 |

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Free user | Have one profile, unlimited links, tips, basic resume, and platform badge | I know what money buys before upgrading | Must |
| US-02 | Pro subscriber | Have removed gates: unlimited profiles, custom domain, realtime analytics, premium templates/logo, AI JD-tailor allowance | Every gate is re-checked on the server | Must |
| US-03 | Buyer of single products | Have my purchase unaffected by plan tier except where the shop major explicitly grants plan-holder discounts | Single products stay separate from plans in v1 | Must |

## Functional requirements

1. Plan matrix v1 — free: 1 profile, unlimited links, tips enabled, basic resume templates, badge required. Pro: unlimited profiles, custom domain (verified via `_cf-challenge TXT`, specified in its own major), realtime-30-min analytics, premium templates + own logo, AI JD-tailor credits.
2. Server-side gating: every gated read/write (domain attach, realtime endpoint, premium `template_id`, AI generation) checks the caller's current plan from the entitlement row. UI-only hiding is never the enforcement.
3. AI JD-tailor: input = resume snapshot + pasted job description; output = tailored suggestions (and optional rewritten bullets pending Q1). Execution via Workers `AI` binding; per-plan credit cap enforced through a KV counter with PG-backed `ai_generations{user_id, credits_used, period}` ledger.
4. Credit lifecycle: monthly reset, no rollover in v1; over-quota requests rejected with an upgrade prompt; each generation logged for abuse review. Prompts and outputs never contain secrets; JD pastes are user-provided and sanitized.
5. Downgrade/cancel: pro features freeze at period end (profiles beyond the free quota become read-only/hidden per Q2 decision, never deleted silently); badge reappears.
6. Entitlement source of truth: the paid-grant rows from any member webhook/callback/IPN grant (all eight rails write `billing/entitlement-ledger.md`); plan checks read them, never a client claim or cookie flag.

## Data / API touches

- Reads `orders{plan, status=paid}`-derived entitlement (or a `subscriptions{user_id, plan, period_start, period_end, status}` view if introduced — same `user_id` + RLS rule). AI ledger `ai_generations{user_id, credits_used, period}` with standard `user_id TEXT NOT NULL` + index + RLS.
- Endpoints: `GET /api/v1/plan` (current tier + quotas), gated endpoints return 403 with `upgrade_required` code when the tier fails. AI endpoint `POST /api/v1/ai/tailor-resume` debits one credit atomically before invoking `AI`.
- KV counters (`RATE_LIMIT` namespace) provide the fast pre-check; PG ledger is the reconciled truth on period rollover.

## Acceptance criteria

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Free user calling each pro endpoint directly (domain attach, realtime, premium template, AI over quota): 403 + `upgrade_required`; nothing applied | A free user calling each pro endpoint directly | Domain attach, realtime, premium template, or AI over quota is called | The API returns 403 with `upgrade_required` and nothing is applied |
| AC-02 | AI over-quota and period-reset behavior verified by advancing the test clock; ledger and KV agree after reconciliation | AI usage over quota and a period reset | The test clock is advanced | Over-quota requests reject with an upgrade prompt and ledger and KV agree after reconciliation |
| AC-03 | Downgrade simulation: excess profiles hidden/read-only per the decided rule; no data loss; badge restored | A pro downgrade | Downgrade executes | Excess profiles are hidden/read-only per the decided rule with no data loss and badge restored |
| AC-04 | IDOR probe: user A cannot read or consume user B plan/credits; RLS denies at DB | User A reading or consuming user B plan/credits | The operation is attempted | Access is denied and RLS denies at DB |
| AC-05 | Prompt-injection probe in JD/resume fields: sanitized, no system-prompt leakage, CSP holds on result rendering | Prompt-injection content in JD/resume fields | The tailor request runs | Inputs are sanitized with no system-prompt leakage and CSP holds on result rendering |
| AC-06 | k6 gate (P3 exit, 100k concurrent normal-use mix) passes with AI calls capped and non-AI reads p95 under 500 ms | The P3-exit 100k concurrent normal-use mix | The k6 gate runs | It passes with AI calls capped and non-AI reads p95 under 500 ms |

## Out of scope

- Checkout and webhook internals (see `checkout/`, `payment/`, and each member minor); agency seats/roles (see `organizations/agency-skeleton.md`).
- Plan purchase paths per rail (see `plans/plan-purchase-options.md`); provider mechanics stay in their minors — all eight specified rails are purchasable plan options with no default (Stripe where enabled for non-VND/international; never a VND default).
- Custom-domain verification mechanics, shop delivery, leads campaigns (own majors); Post-to-All/IG automations/broadcast/agency roster (P4); replicas/ISR (P5).

## Open questions

- Q1: AI output shape v1 — suggestions only, or one-click apply of rewritten bullets into a draft snapshot?
- Q2: Downgrade UX for excess profiles — hide newest, pick-one-to-keep, or read-only until re-upgrade?
