# AI Design Assist - Step-by-Step Implementation Plan (feature 16) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/16-ai-design-assist/` (ACs in `06-acceptance-criteria.md`). Contracts: `docs/02-architecture-techlead/16-ai-design-assist/03-api-contracts-and-payloads.md`. GATED: T-01/T-02 run now; every `AT PLAN UPDATE` task is blocked until the spec amendment (BR-01) and must not ship in any current batch.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | DevOps | LIVE: absence-drill tooling - CI step asserting zero assist surface (schema diff, OpenAPI diff, source + bundle grep: `assist/`, `suggestion_draft`, `kind='design-assist'`, panel strings) | - | AC-01 green; violation blocks promotion by tooling |
| T-02 | DevOps | LIVE: release-checklist entry: assist remains gated; an amendment record is required before any build task starts | T-01 | Gate cannot be lifted silently; promotion review cites the amendment |
| T-03 | BE | AT PLAN UPDATE: `AssistController` (three endpoints), owner scoping, challenge + rate limiting | amendment; 01 D-1 | AC-06, AC-07 scaffolding |
| T-04 | BE | AT PLAN UPDATE: `AssistGuard` tokens-only enforcement (gallery id, shared validators, reorder ops; `refused:true` path) | T-03 | AC-02; validator-failing output never renders |
| T-05 | BE | AT PLAN UPDATE: `AssistQuota` consuming feature 35's credit model (`kind='design-assist'`); `429 assist_quota_exhausted`; caps as ledger counters | T-03; feature 35 contract | AC-04, AC-07 |
| T-06 | BE | AT PLAN UPDATE: `ModelPort` + `AssistAudit` (platform text capability, env/vault keys, whitelist DTO, PII-minimized logs) | T-03 | AC-05; NFR-08 |
| T-07 | FE | AT PLAN UPDATE: `AssistPanel` theme flow (quiz max 3, ranked picks + reasons, gallery preview reuse, Apply via theme PUT) | T-04 | AC-08; AC-03 for theme changes |
| T-08 | FE | AT PLAN UPDATE: bio flow (tone, 3 drafts, diff vs current, character count, low-confidence fewer options) | T-04 | AC-08; AC-11 |
| T-09 | FE | AT PLAN UPDATE: ordering flow (before/after list, per-row accept, `Apply selected` via reorder endpoint with standard rollback) | T-04 | AC-03; conflict parity |
| T-10 | FE | AT PLAN UPDATE: quota/outage states (banner collapse, retry, navigation cancel, zero client caching) | T-05, T-07..T-09 | AC-09; NFR-11 |
| T-11 | DevOps | AT PLAN UPDATE: drill battery: guardrail, human-loop, quota, injection, IDOR, cost, diff, outage, authority (no auto-apply), locale | T-03..T-10 | AC-02..AC-11 green |
| T-12 | DevOps | AT PLAN UPDATE: container parity (`docker run` + `podman run`), release gates; migration audit flips from absence to surface-drift checking | T-11 | AC-12; digest pair boots |

## Notes

- T-01/T-02 are the only live work: they enforce the gate; they never create assist scope.
- At the amendment, feature 35 (plans slice) must first land the credit model + period window; T-05's port has no local fallback by design.
- No new dependency, container, queue, or migration appears in this plan while gated; `profile-service` stays byte-identical to its features 06-09 state until the amendment.
- Q-01 (free vs pro availability), Q-02 (wallet shape), and Q-04 (drafts table) are resolved in the amendment record, not in code.
