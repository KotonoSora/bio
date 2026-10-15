# Monetize Lifecycle (execution plan M2 — plans, renewals, receipts, coupons, refunds)

PLAN ref: P3-3 (plan matrix + gates) + P3-4 (org skeleton) + P3-1/P3-2 settlement evidence + S-1…S-7. BA source: `docs/monetize/plans/subscription-plans.md`, `docs/monetize/plans/plan-purchase-options.md`, `docs/monetize/billing/subscription-lifecycle.md`, `docs/monetize/billing/invoices-receipts.md`, `docs/monetize/invoices/vat-einvoices.md`, `docs/monetize/invoices/proforma-invoices.md`, `docs/monetize/invoices/credit-notes.md`, `docs/monetize/promo-code/campaign-management.md`, `docs/monetize/promo-code/code-generation.md`, `docs/monetize/promo-code/redemption-rules.md`, `docs/monetize/refunds/refund-processing.md`, `docs/monetize/organizations/agency-skeleton.md`, plus `docs/monetize/subscription/plan-catalog.md` and `docs/monetize/subscription/trials-promotions.md` where present. Builds on `docs/platform/delivery/monetize-foundation.md` and `docs/platform/delivery/monetize-rails.md`. Growth minors (`docs/monetize/affiliate/*`, `docs/monetize/advertising/*`, `docs/monetize/sales/*`, `docs/monetize/marketing/*`, `docs/monetize/data-analysis/*`, `docs/monetize/user-behavior/*`) stay build-gated backlog — design-complete, not built here.

## Purpose

Finish the money story after the grant: what the buyer owns, how long it lasts, how it renews, what proves it, what discounts it, how it reverses, and which org skeleton it attaches to. M2 exits with the P3 gate (100k concurrent normal-use mix) green on a single image carrying kernel + eight rails + this lifecycle.

## Users

- Buyer: sees plan, renews, downloads receipts, redeems coupons, requests refunds.
- Support/ops/finance: approves refunds, fixes periods (audited), reconciles PSP references against ledger and credit notes.
- Agency preinstall: org skeleton exists with no orphan queries (full roster deferred to P4).

## Functional requirements

1. Stack and topology (mandatory): PG `18.6`, Boot `4.1.1`, Kotlin `2.4.21`, Gradle `9.8.1`. Architecture verdict for M2 — monolith with scheduled jobs in-process. Pros: renewal/expiry/coupon-budget/refund transitions share the order/ledger transaction and one audit log; no distributed scheduler needed at P3 volume. Cons accepted: job execution shares app CPU (mitigated by idempotent per-row jobs + short transactions + scheduler lock so two replicas never double-fire). Microservices/split verdict: no extract in M2; first candidate is a worker service for expiry/reminder/refund-retry only if P3-gate job lag fires the scaling-asymmetry trigger — recorded, not built.
2. Plans and gates: two-tier matrix (free: 1 profile, unlimited links, tips, basic templates, badge; pro: unlimited profiles, verified custom domain, realtime-30min, premium templates + own logo, AI credits) enforced server-side from the `subscriptions` current row (`GET /api/v1/plan` → `{plan, status, period_end, quotas}`); UI hiding never the enforcement. Purchase paths per rail with explicit `provider`, no default. 422 `upgrade_required` on gated access without plan.
3. Lifecycle jobs: terms monthly default (yearly only if ledger Q1 says so); renewal is explicit re-purchase (early renewal extends from later of now/`period_end`); reminders at −7d/−1d via transactional channel deep-linking plan-preselected checkout; idempotent expiry job `active → grace` at `period_end`, `grace → expired` after window (7-day default), opening fresh `free/active`; grace passes gates with renew banner, no AI refill; downgrade lossless (extra profiles hidden + read-only, domain detached without DNS change, template fallback preserving stored `template_id`, badge back); re-upgrade restores byte-identical. Cancel sets `cancelled_at`, runs to `period_end`, stops reminders; Stripe-native (if enabled) also cancels at PSP.
4. Receipts and invoices: uniform receipt `RCPT-<orderCode>` (line items, `amount_vnd`, provider + PSP reference, ISO8601 grant time, support contact; footer states non-VAT-invoice plainly); history page `/app/billing/history` with print-CSS A4 re-download; grant/refund-triggered email via transactional channel (queued, never blocks grant; marketing-unsubscribed users still receive it); refunds append linked credit notes, never edit history. Full VAT e-invoice issuance stays named future work.
5. Coupons and trials: one coupon per order (second replaces first with confirmation); coupon + trial/credit-days combine per stacking matrix; validate endpoint is quote-only with oracle-free neutral errors + normalized timing + throttling; campaign pause/expiry between quote and pay fails grant with full-price one-click offer; velocity anomalies auto-pause campaign + open review (never auto-ban); single-use consumption atomic at grant.
6. Refunds router: eligibility `paid`-only (partial keeps `paid` + ledger entry; full moves `paid → refunded` terminal; `disputed` goes to dispute path); execution dispatches on stored `provider` to that rail's mechanism (bank rails manual/ops-evidenced with two-person rule; card rails via PSP APIs) — unknown provider rejected, cross-rail execution impossible; buyer request (owner-scoped, Turnstile + rate limits, idempotency key) + restricted ops approve/execute with backoff retries; revocation per lifecycle (plan downgrade lossless, product claims invalidated); re-grant of `refunded` refused. Org skeleton tables (`orgs/org_members`) created with membership checks, no P4 roster behavior.

## Data / API touches

- Tables: `subscriptions` mutated by grant/lifecycle/cancel jobs (`reminded_7d/1d` flags for idempotency); `coupons`/`promo_campaigns` read at validate, budget decremented atomically at grant; `refunds{order_id, user_id TEXT NOT NULL + index + RLS, provider, amount_vnd INT, status requested|approved|executing|reversed|failed, psp_ref?, idempotency_key UNIQUE, actor, at}`; receipts render from `orders` + `subscriptions` + refund annotations (no v1 `receipts` table). All PG via repositories, least-privilege role, RLS both levels.
- Endpoints: `GET /api/v1/plan`, `POST /api/v1/plan/cancel`, `POST /api/billing/coupons/validate`, checkout with `coupon_code`, `GET /api/billing/history`, `GET /api/billing/receipts/:orderCode`, `POST /api/billing/orders/:orderCode/refund-request`, `GET /api/billing/refunds/:id`, ops `POST /api/v1/ops/refunds/:id/approve|execute|reject` (restricted role, audited) + audited manual period-fix endpoint. Email send is a queued side-effect.
- Jobs: expiry, reminders, refund-retry, coupon-budget reconciliation — Spring-scheduled with DB lock, idempotent per row, visible in ops metrics.

## Acceptance criteria

- Lifecycle drill (test clock): grant → −7d/−1d reminders → grace (gates pass + banner, no AI refill) → expiry lossless downgrade → re-upgrade restores; early renewal extends from `period_end`; cancel runs to term then downgrades; job rerun is a no-op.
- Receipt drill: any-rail grant → email within SLA + history match + clean A4 print + non-VAT footer; refund appends credit note + email, original never edited; email outage never blocks grants, backlog visible.
- Coupon drill: stacking matrix exact; oracle drill (timing/text indistinguishable); quote/grant race fails safe with full-price offer; abuse ring voids credits, preserves paid orders, pauses campaign without auto-ban.
- Refund drill: eight-rail request → approve → execute, each `refunded` with member reference, entitlements revoked, re-grant refused; partials ledger without status change; non-`paid`/`disputed` routed correctly; same-key retry single reversal; routing reads stored `provider` only (grep gate clean).
- Gate drill: server-side 403 `upgrade_required` on every pro gate for free users; IDOR 403 + RLS deny on plan/refund/receipt reads; S-2/S-5/security probes green; P3 exit 100k mixed gate passes.
- Container drill: M2 tag runs under Docker AND Podman from empty PG via Flyway from V1; backlog majors have zero tables/endpoints/jobs in the image (migration + grep audit).

## Out of scope

- Kernel and rail mechanics (see `docs/platform/delivery/monetize-foundation.md`, `docs/platform/delivery/monetize-rails.md`); per-release gate mechanics (see `docs/platform/delivery/release-gates.md`); shop delivery, P4 agency roster/automations, P5 scale-out; affiliate/advertising/sales/marketing/data-analysis/user-behavior builds (backlog).

## Open questions

- Q1: Grace length and mid-term cancel refund policy (strict no-refund vs goodwill window)?
- Q2: Receipt localization — Vietnamese-first or bilingual v1?
- Q3: Buyer self-service refund execution window vs ops-only execution in v1?
