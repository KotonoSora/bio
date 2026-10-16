# Concierge Managed Service - Security, Logging, and Rate Limiting (feature 05)

Phase 2 TL · BA sources: `docs/01-requirements-ba/05-concierge-managed-service/` (BR-02, BR-03, BR-04, BR-07, BR-08, BR-10). Baseline: `docs/02-architecture-techlead/00-platform-baseline/08-security-baseline.md`; gates: `docs/02-architecture-techlead/00-platform-baseline/06-release-gates.md`.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | No backdoor: operators act only through agency-gated endpoints (assignment + Act-as-user + step-up + audit); concierge routes expose no direct content-edit path; attempts are denied + audited | AC-01; route inventory probe + backdoor attempt drill |
| S-2 | Approval integrity: append-only grants on `concierge_approvals`; scope recorded; supersede-only corrections; no update/delete route exists | AC-02; grant probe + supersede drill |
| S-3 | Dispatch gate fail-closed: `origin=concierge` without a valid `approvalRef` never dispatches; verification timeout/non-200 -> refuse; enforced at consumers (features 38-44) | AC-02; forced-outage drill |
| S-4 | Boundaries: no billing-instrument access exists in any concierge route; policy-exception promises impossible (no such capability); client-customer contact surfaces are not granted; prohibited-content refusals recorded | AC-06; capability inventory |
| S-5 | Staffing presence gate: activation query (>= 1 template/operator/owner) with auditable register; removal guards (`last_owner_forbidden`) | AC-05; empty-register drill |
| S-6 | Dual-confirm integrity: pending transition is durable (columns), counterpart confirm is identity-checked (client session or owner role), initiator cancel is the only unilateral move | AC-03; concurrent-confirm drill |
| S-7 | Scoping + RLS: owner predicate on all five tables; operators see assigned contracts only; no client content in concierge rows beyond ids/refs | AC-01, AC-07; RLS probe |
| S-8 | Audit + logging: every transition (create, activate, approve, status, refuse, pause, exit, staff changes) audited with actor + target + trace; logs carry ids/states only, never client content or signed-link tokens | AC-07; audit reconstruction |
| S-9 | Signed-link hygiene: signed-email confirmations are JWT `<= 15 min`, single-use jti (Redis db 7), bound to task + scope; replay -> `signature_invalid` | Replay probe |
| S-10 | Exit integrity: handoff checklist completion is a precondition of `exited`; agency links sever; no route can hold client artifacts hostage (references remain the client's own) | AC-03; exit drill |

## Rate envelopes

| Route class | Envelope | On breach |
|---|---|---|
| Client task request | 20/day/contract | `429 throttled` + `Retry-After` |
| Client approve / pause / exit | 60/min/user | `429` |
| Internal ops (task/status/staff/templates) | 120/min/operator, audited | `429`; anomaly alert |
| Approval check (internal) | service budget per dispatch endpoint, trace-attributed | `429`; consumer treats as invalid (fail-closed) |

## Degrade matrix

| Condition | Behavior |
|---|---|
| Agency tooling (45-47) down | No execution (fail-closed); board still readable; tasks queue in `approved` |
| Approval-check route down | Dispatch refuses (`<= 100 ms` budget); no unapproved send; alert |
| Invoicing consumers down | Activation stands; event retried; invoice reconcilable from the event; no blocking |
| Scheduler consumers down | Pause/exit halt depends on their own queues; concierge state is authoritative; halt reconciles on recovery |
| account-service down | Board unavailable; dispatch verification fails closed; no work executes |
| Staff register emptied mid-contract | Serving/execution continues for existing contracts (assignment checks still gate actions); activation refuses new ones; alert |

## Logging

- Fields: contract id, task id, transition, actor id + role, trace; never client content, never signed-link tokens.
- Alertable: dispatch refusals due to invalid approvals, signature replays, last-owner attempts, rate-limit breaches, staffing-register empties.

## Gates

- G4: endpoints additive-only; `ApprovalCheckResponse` rename-frozen (features 38-44 consumer).
- G5: S-1, S-2, S-3, S-5, S-9 probes run per release.
- G7: drill set AC-01..AC-08 attaches to the tag; go-to-market gate: AC-05 + Q-01 values resolved before any sale (launch blocker, staffing/finance-owned).
