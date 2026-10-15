# Concierge Managed Service (done-for-you operations)

PLAN ref: none — new scope beyond PLAN P0–P5, flagged explicitly. The parity doc shelved this as "service business, not software" — this doc scopes it as exactly that: a staffed offering with software touchpoints, proposed as P4-revenue (needs a plan update + staffing before any sale). Nothing here builds until operators exist to deliver it.

## Purpose

Sell outcomes, not seats: for a retainer, our team handles a client's setup, campaigns, posts, emails, automations, and reporting — the six surfaces named in the request — under one SLA with visible work logs. Concierge is delivered by people using the same agency tooling as any power user (Act-as-user, reports, schedulers), plus runbooks and staffing this doc requires. If the software cannot show the work, the service cannot sell it.

## Users

| Role | Permissions | Notes |
| --- | --- | --- |
| Concierge client | Approve setup plan + per-item work (owner-scoped); pause/exit own contract | Buys the retainer, approves a setup plan, receives scheduled work + reports, exits with a clean handoff. |
| Concierge operator (staff) | Execute assigned tasks only via agency-gated endpoints (Act-as-user with step-up, full audit; no backdoor edits) | Executes setup/campaign/post/email/automation/report tasks inside scoped agency powers, logs every action. |
| Agency owner (internal) | Contract/task CRUD (internal roles); staff, pause, offboard clients | Staffs, reviews quality, pauses or offboards clients. |

| Story ID | As a | I want to | So that | Priority |
| --- | --- | --- | --- | --- |
| US-01 | Concierge client | approve a setup plan and receive scheduled work plus reports with a clean handoff on exit | I buy outcomes with visible work logs under one SLA | Low |
| US-02 | Concierge operator | execute setup/campaign/post/email/automation/report tasks inside scoped agency powers with every action logged | the work is visible and auditable | Low |
| US-03 | Agency owner | staff, review quality, and pause or offboard clients | the service stays staffed and quality-controlled | Low |

## Functional requirements

1. Scope of work (closed menu, per-client SOW picks items + volumes): profile/page setup (theme, blocks, bio, QR), broadcast campaigns (copy + audience + send, within the P4 campaign mechanics), publish scheduling (composer + calendar targets the client approves), lifecycle/marketing emails owned by the campaign surface, IG automations (trigger design + templates within platform policy), and periodic reporting (agency report views + narrative). Anything off-menu (custom code, ad-buy management, legal/compliance advice) is refused with a referral path — the SOW states this before payment.
2. Staffing + powers: operators act exclusively through `docs/agency/` mechanics (org membership, assigned profiles, Act-as-user sessions with step-up, full audit) — no backdoor admin editing of client content, ever. Client-side visibility is the default: every operator action appears in the client's history; a shared task board (see §5 in Data) tracks requested → approved → done per item.
3. Approval gates: setup plan approved before build; each campaign/post-batch/automation approved before send/publish (explicit per-item approve in-dashboard or signed email confirmation — never implied consent); reports delivered on the SOW cadence. Unapproved work never ships; approval records are immutable.
4. SLA + quality: response times per priority (see Q1 for launch numbers), monthly work log + results review call, error remediation (mistaken send/post fixed + disclosed within the SLA window; service credits per contract, never silent). Client may pause the retainer (work halts, access powers suspend, content stays live) or exit anytime with the handoff below.
5. Boundaries (hard): operators never touch billing instruments beyond the SOW retainer itself; never promise platform-policy exceptions (spam/review outcomes, verification badges, deliverability guarantees); never contact the client's customers outside approved surfaces; prohibited-content bar identical to ads/broadcasts (refuse the task, keep the client).
6. Exit/handoff: offboarding delivers credentials inventory, content ownership confirmation (everything already user-owned per the roster invariant), scheduled-work inventory (transferred or cancelled at client choice), and a final report. Post-exit, agency links sever per the no-hostage guarantee — the client's pages, lists, and history remain intact.

## Data / API touches

- Minimal new software: `concierge_contracts{org/client_ref, sow_json (menu items + volumes), status (trial|active|paused|exited), sla_tier}` + `concierge_tasks{contract_id, kind, status (requested|approved|in_progress|done|refused), approvals[], work_refs[]}` (approvals immutable; work_refs link to the post/campaign/automation/report rows the agency tooling already creates). Billing is retainer invoicing through the standard receipt pipeline (service product, not a subscription seat). All PG via `worker/db/*`; RLS (clients see own contract/tasks; operators see assigned).
- Endpoints: contract/task CRUD (internal roles), client approval actions (owner-scoped), work-log reads (client-visible), pause/exit (dual-confirmed). No new sending, publishing, or editing primitives — all execution flows through existing agency-gated endpoints.
- Staffing prerequisite (non-software, blocking): named operators, SOW templates, SLA clock tooling (task timestamps suffice v1), and the support runbook covering refusal/disclosure/remediation scripts. Selling before these exist is prohibited by this doc.

## Acceptance criteria

- Powers drill: operator without assignment/approval cannot touch client content (403s across all surfaces); every executed task links to approval + audit rows visible to the client. *GWT: Given an operator without assignment or approval; When they attempt client content actions; Then all surfaces return 403 and executed tasks link to approval plus audit rows visible to the client.*
- Gate drill: unapproved campaign/post-batch/automation refuses dispatch (verified at the send/publish endpoints, not by operator discipline). *GWT: Given unapproved campaign/post-batch/automation work; When dispatch is attempted at send/publish endpoints; Then dispatch is refused.*
- Pause/exit drill: paused retainer halts work with content live; exited contract completes handoff inventory with agency links severed and client assets intact. *GWT: Given a paused or exited retainer; When pause or exit completes; Then paused work halts with content live, and exited contracts complete handoff inventory with links severed and assets intact.*
- SLA drill: fixture tasks measure response/remediation against the SOW clock; missed SLA shows service-credit path per contract. *GWT: Given fixture tasks under the SOW clock; When response/remediation misses the SLA; Then the service-credit path per contract is shown.*
- No-sale-before-staffing drill: contract creation attempted without assigned operators/SOW templates is refused (presence checks, not policy text). *GWT: Given no assigned operators or SOW templates; When contract creation is attempted; Then creation is refused by presence checks.*

## Out of scope

- Agency mechanics themselves (`docs/agency/`); campaign/publish/automation/report execution rules (their own docs — operated here, never redefined).
- Self-serve agency tiers (see `docs/agency/billing/seat-billing.md`); ad-buy fund management (client ad spend never passes through platform billing in v1); legal/compliance advisory (referred out, always).

## Open questions

- Q1: SLA tiers and retainer pricing (per-SOW vs tiered packages) — pricing decision with staffing costs attached?
- Q2: Concierge trial/pilot (one-setup-fee onboarding) — launch motion, or retainer-only from day one?
