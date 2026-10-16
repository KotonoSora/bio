# Entitlement and Plans - UI Data Dictionary (feature 35)

Phase 1 BA · Surfaces: billing plan page, shared plan badge, AI tailor workspace, ops catalog console. Pricing page chrome belongs to `docs/01-requirements-ba/03-tier-presentation/` (its shape is served from here); checkout chrome belongs to `docs/01-requirements-ba/33-checkout-session/`.

## Billing plan page (`/app/billing/plan`)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Current plan | Status badge (`PlanBadge`) | Yes | One of: free, pro, pro-trial, pro-grace, pro-cancelled, expired | Renders from `GET /api/v1/billing/plan` only; never computed client-side |
| Status line | Text | Yes | `active`, `trial`, `grace`, `cancelled`, `expired` mapped to plain-language copy | Grace shows "renew by {graceEndsAt}" |
| Period end | Date text | For pro rows | ISO date -> local display; hidden for free | From the ledger row; never estimated |
| AI quota meter | Progress + counts | For pro rows | Exact counts `{used} / {total}` (no buckets); 0 total hides the meter | Server truth only; refetch after any generation |
| Remaining credits | Number | For pro rows | `remaining = total - used`; render as-is | "0 left" renders the upgrade CTA |
| Renew/upgrade CTA | Button | Yes | Free -> pricing page; cancelled -> renew (36's checkout path) | Single primary CTA; no retention maze |
| Cancel path | Link | For active pro | Opens 36's cancel confirmation; copy states service-to-period-end | Absent for free/expired |
| Plan history | List | No | Rows: plan, term, period, provider, status | Read-only; newest first; no export |
| Catalog version of current term | Footnote text | No | `m{monthlyVer}y{yearlyVer}` token when known | Hidden if unavailable |

## Plan badge states (shared component)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Free | Badge variant | Yes | Neutral styling; no claims beyond the matrix | Shown when effective plan is free |
| Pro | Badge variant | Yes | Accent styling | `active` pro rows |
| Pro (trial) | Badge variant | Yes | Accent styling; tooltip = trial end | `trial` rows; copy "Trial - ends {date}" |
| Pro (grace) | Badge variant | Yes | Warning styling; tooltip = renew-by date | `grace` rows |
| Pro (cancelled) | Badge variant | Yes | Muted accent; tooltip = service-to date | `cancelled` rows before `period_end` |
| Expired | Badge variant | Yes | Neutral; upsell on click | Effective free with history present |

## AI tailor workspace (resume-service surface)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Document picker | Select | Yes | Only the user's own documents; absent -> picker with create link | Last-used document remembered per session |
| Job description | Textarea | Yes | 1..10 000 chars; trimmed; plain text only | Empty blocks submit |
| Generate | Button | Yes | Enabled only when pro + credits >= 1 + document chosen; disabled state shows why | Double-submit locked; request is keyed |
| Credits remaining | Text | Yes | Rendered from the debit response; exact count | Updated after every generation/return |
| Suggestions | Ordered list | Yes | Max 8 items, each <= 500 chars, rendered as escaped text | Copy-to-clipboard per item; no auto-apply |
| Credit-failure notice | Inline notice | On failure | "Credit returned - nothing charged"; only when a return fired | Auto-dismiss after render |
| Upgrade prompt | Inline CTA | On 403/429 | `403 upgrade_required` -> pricing; `429` -> reset date + pricing | Never blocks reading old results |

## Ops catalog console (`/ops/billing/plans`)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Terms table | Table | Yes | Columns: plan, term, version, amountVnd, aiCredits, status, activatedAt, retiredAt | Drafts highlighted; no inline editing |
| Draft form - planCode | Select | Yes | Paid plans only (`pro`); `free` rejected | No default; explicit choice |
| Draft form - term | Select | Yes | `monthly`/`yearly` | Explicit choice |
| Draft form - amountVnd | Integer input | Monthly only | Integral VND, `> 0`; yearly is derived and shown read-only | Yearly preview computed live from active monthly + config |
| Draft form - aiCredits | Integer input | Yes | `>= 0`; integral | No default (explicit) |
| Draft form - reason | Text | No (draft) / Yes (activate, retire) | 3..200 chars when required | Audit copy |
| Activate / Retire | Buttons + confirm | Yes | Activate: finance role; confirm states conflict rules; Retire: blocked while pendings reference | Reason input inside the confirm dialog |
| Config panel | Read-only card | Yes | Shows `yearlyDiscount` (%), `trialDays` from `ops_config`; link to 32's config flip route | Absence renders "not set" |

## Checkout plan intent (33's surface - term addition)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Intent summary | Card | Yes | Plan name + `term` label (monthly/yearly) + price line | Term comes from the selected quote; read-only here |
| Term | Text | For plan intents | `monthly`/`yearly` only | Changed on the plan page, not in checkout |
