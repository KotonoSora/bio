# Concierge Managed Service - Context and Scope (feature 05)

Phase 1 BA · Feature 05 of 53 (account) · Implemented by `docs/02-architecture-techlead/05-concierge-managed-service/` (5 files); extends `account-service` (no new service). Execution powers consumed from features 45-47; dispatch gating registered forward with features 38-44; invoicing via features 32-40.

## Purpose

Sell outcomes, not seats: for a retainer, our team handles a client's setup, campaigns, posts, emails, automations, and reporting - six surfaces - under one SLA with visible work logs. Concierge is delivered by people using the same agency tooling as any power user (Act-as-user, step-up, full audit), plus runbooks and staffing this feature requires. If the software cannot show the work, the service cannot sell it.

## Objectives

- O-1 Closed menu: a per-client SOW picks menu items and volumes; anything off-menu is refused with a referral path, stated before payment.
- O-2 No backdoor: operators act exclusively through agency mechanics (assignment, Act-as-user, step-up, audit); every action is visible in the client's history.
- O-3 Approval gates: setup plan approved before build; every campaign/post-batch/automation approved per item before dispatch; approval records immutable; unapproved work never ships.
- O-4 SLA honesty: the clock runs on task timestamps; missed windows surface the contract's credit path - never silently.
- O-5 Clean exit: pause halts work with content live; exit delivers a handoff inventory and severs agency links with client assets intact.
- O-6 Staffing gate: nothing can be sold before named operators, SOW templates, and the runbook exist - presence checks, not policy text.

## Personas and RBAC

| Role | CRUD Permissions | Description |
|---|---|---|
| Concierge client | Create task requests; read own contract/tasks/work log; Update: approve items, request pause/exit | Buys the retainer, approves a setup plan, receives scheduled work + reports, exits with a clean handoff. |
| Concierge operator (staff) | Update assigned task states only, via agency-gated endpoints (Act-as-user, step-up, audit) | Executes setup/campaign/post/email/automation/report tasks inside scoped agency powers; logs every action. |
| Agency owner (internal) | Full CRUD on contracts/tasks/staffing register (internal roles) | Staffs, reviews quality, confirms pause/exit, offboards clients. |
| Client's customers (indirect) | none | Are never contacted outside approved surfaces. |
| Attacker (adversarial) | none | Finds no unapproved dispatch path and no unassigned operator access. |

## Resolved open questions

| Q | Decision |
|---|---|
| Q-01 SLA tiers and retainer pricing | Staffing/finance-owned config: SLA tier maps to response window, remediation window, and credit-rule keys; retainer price is an invoice amount. Values are decided with the staffing cost model before any sale (launch blocker); the mechanism is fixed here, no numbers invented. |
| Q-02 Trial/pilot motion | Retainer-only at launch; the contract status enum has no trial state. A paid-onboarding pilot motion requires a flagged amendment. |
| Q-03 Delivery prerequisite | Activation is refused by presence checks unless at least one active SOW template, one active operator, and one agency owner exist in the staffing register (BR-08). |

## In scope

- Contract lifecycle: draft, activation (staffing-gated), pause/exit (dual-confirmed), handoff inventory.
- SOW storage (menu items + volumes), task board (requested -> approved -> in progress -> done/refused), immutable approvals.
- Work-log reads for the client; SLA clock on timestamps; credit path surfacing.
- Boundaries enforcement (billing, policy promises, client-customer contact, prohibited content) and the dispatch-gate contract registration.

## Out of scope

- Agency mechanics themselves (features 45-47: org membership, assignment, Act-as-user, audit); execution primitives for campaigns/posts/emails/automations/reports (their own features - operated here, never redefined).
- Retainer invoicing mechanics (features 32-40; this feature emits the activation/state events they consume).
- Ad-buy fund management (client ad spend never passes through platform billing in v1); legal/compliance advisory (referred out, always); concierge self-serve purchase UI (sales-assisted; no pricing-page claim exists for it).
- Gated backlog items (48-53) stay untouched by this feature.

Related: rules in `docs/01-requirements-ba/05-concierge-managed-service/02-user-stories-and-business-rules.md`; contracts in `docs/02-architecture-techlead/05-concierge-managed-service/03-api-contracts-and-payloads.md`; error surface in `docs/01-requirements-ba/05-concierge-managed-service/05-exception-handling-and-errors.md`.
