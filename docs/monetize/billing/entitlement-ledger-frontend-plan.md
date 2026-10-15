# Entitlement Ledger Frontend Plan (plan status + gates UX — UI build steps)

PLAN ref: P3-3 plan UX (one plan answer everywhere: status, period end, quotas, renew entry; no missing-row states). BA source: `docs/monetize/billing/entitlement-ledger.md`. Server truth in `docs/monetize/billing/entitlement-ledger-backend-plan.md` (writer, resolver, free hook — never redefined here).

## Purpose

Give the frontend developer the plan-status rendering contract: one `PlanBadge` + one `GET /api/v1/plan` read shared by every gated surface, period/quota display, and the proof that missing-row states never render. Follow steps 1–5 in order. Gates read the resolver; screens render the resolver — no screen computes plan state from anything else.

## Users

- Frontend developer: binds every gated surface to one plan read.
- QA: executes the single-answer, free-default, and no-missing-row drills.
- Creator: sees one consistent plan status (never "pro here, free there").

## Functional requirements

1. Shared read (one hook, all surfaces): `usePlan()` → `GET /api/v1/plan` once per session (+ refetch on `PlanChanged`-class events: post-checkout return, post-publish of plan-affecting changes) → `{plan, status, period_end, quotas}` shared across domain attach, realtime tab, template gallery, AI surfaces (one fetch, many readers — N gated surfaces never means N plan calls; request deduplication in the hook).
2. Step 1 — `PlanBadge` flow: renders `Free` / `Pro (until <date>)` / `Grace (renew by <date>)` / `Cancelled (runs to <date>)` / `Expired` from `{plan, status, period_end}` (fixed copy per state — the vocabulary rule from the overview frontend plan applies; no screen invents plan words). Grace renders the renew banner entry (persistent, linking checkout with plan preselected — generosity converts, lockouts don't).
3. Step 2 — quota display flow: quotas object drives feature affordances (realtime tab, AI credits, premium templates, domain attach) — locked affordances link their unlock path (realtime → plan page; AI → credits explainer), never dead-end `Pro only` text without a next step.
4. Step 3 — consistency rule: all gated surfaces read the same hook response (same `generatedAt` per session fetch — a domain page saying pro while analytics says free is a P0 rendering bug, tested by multi-surface fixture).
5. Step 4 — missing-row impossibility: no `No plan found` UI state exists anywhere (the backend fails closed + alerts; the client treats plan-fetch failure as error-with-retry, never as free-tier fallback — falling back to free on error would unlock nothing but would mislead; falling back to pro would grant. Error copy: `Couldn't load your plan — retry`).
6. Step 5 — post-grant refresh: pending-page success triggers plan refetch before navigating onward (grant visibility within SLA starts at the resolver; the UI must not outrun it — navigate-after-refetch ordering, tested with grant-latency fixtures).

## Data / API touches

- Screen → endpoint map: all gated surfaces → `GET /api/v1/plan` (shared hook, deduped). No `PUT` exists (plan changes only via grants/lifecycle/ops — the client holds no plan-write affordance to audit).
- Type mirror: `Plan {plan: free|pro, status, period_end?, quotas}`. No client plan computation exists (grep-verified).

## Acceptance criteria

- Single-answer drill: multi-surface fixture shows one plan everywhere at one fetch.
- Badge drill: all five states render specified copy + renew entries where applicable.
- Missing-row drill: fetch failure renders retryable error, never a tier fallback.
- Refresh drill: post-grant navigation waits for refetch (latency-fixture proof).
- Quota drill: locked affordances link unlock paths (no dead ends).

## Out of scope

- Writer/resolver/hook logic (backend plan above); table DDL (database plan above); periods/renewal/grace/downgrade screens (lifecycle frontend plan); receipts/history (invoices-receipts frontend plan); rail mechanics.

## Open questions

- Q1: Quota display granularity — exact counts vs `X left` buckets (privacy vs usefulness)?
- Q2: Refetch triggers — session-fetch + post-grant enough, or also on window focus?
