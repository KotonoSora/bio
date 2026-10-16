# AI Design Assist - Security, Logging, and Rate Limiting (feature 16) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/16-ai-design-assist/` (BR-01, BR-02, BR-05..BR-11). GATED: S-1 is live now; all other controls bind the future build at the spec amendment. Baseline controls: `docs/02-architecture-techlead/00-platform-baseline/`; inherited surfaces: `docs/02-architecture-techlead/06-public-profile/`, `08-link-blocks/`, `09-themes-and-avatar/`.

## Controls

| # | Control | Rule |
|---|---|---|
| S-1 | Gate enforcement (LIVE) | Tooling asserts zero assist surface each release (schema/OpenAPI/source/bundle); a hit blocks promotion - the gate is tool-enforced, not policy |
| S-2 | Injection boundary | User content is data; system instructions are never overridable; model input arrives via a compile-time whitelist DTO; output is constrained to the closed suggestion set |
| S-3 | Cost-abuse defense | Challenge + rate limit on all suggestion endpoints; per-user/day caps as ledger counters; bursts alert; prompt cost is the bot model, so throttling is a security control, not just fairness |
| S-4 | Output safety | Every applicable suggestion must pass the same validators as manual input (gallery theme id, text XSS/length, legal reorder ops); inexpressible output is dropped server-side before display - never a client-side filter |
| S-5 | IDOR + RLS | Owner scoping on every assist call; cross-profile -> `403 foreign_profile`; apply flows run through existing endpoints where RLS and validators are unchanged |
| S-6 | Human control | Zero auto-apply/auto-publish paths; audit actor = user on 100% of applied changes; no write surface exists on the assist controller itself |
| S-7 | Secret handling | Model keys via environment/vault only; calls are server-side; no key, endpoint credential, or raw prompt ever reaches the browser or the repo |
| S-8 | Logging minimization | Prompts/outputs logged PII-minimized for safety review; log access is audited ops; no follower evidence, tokens, or cross-user data may appear by construction |
| S-9 | Failure isolation | Outage/quota degrades only the assist panel; no cached suggestion is ever displayed as fresh (client caching forbidden); navigation cancels cleanly |

## Threat checklist

| Threat | Mitigation |
|---|---|
| Prompt injection via bio/titles/quiz answers | S-2: data-not-instructions boundary + whitelist DTO + closed output set; drills AC-05 |
| Cost attack (scripted generations) | S-3: challenge + rate limits + ledger caps; alerting on burst; AC-07 |
| Output smuggling (custom CSS/JS via "suggestion") | S-4: guard rejects inexpressible output; validator parity with manual input; AC-02 |
| AI-authority dark patterns (auto-apply) | S-6: the control does not exist; grep gate AC-10 |
| Cross-profile reads/apply | S-5: owner check + inherited RLS; AC-06 |
| Secret leakage via client or repo | S-7: server-side calls, env/vault keys; repo grep gates |
| Stale suggestion replay | S-9: no client caching; staging is session-scoped; replay is unrepresentable |
| PII creep into model input or logs | S-8 + whitelist DTO; forbidden-forever list in `02-database-schema-design.md` |

## Logging and degrade

- Metrics (at amendment): `assist.generated{kind}`, `assist.refused`, `assist.quota_exhausted`, `assist.cost_units`; alerts: burst beyond caps, refusal-rate anomaly, cost-unit anomaly.
- Logs: PII-minimized prompts/outputs for safety review with audited access; never follower evidence, tokens, or cross-user data.
- Degrade matrix: quota exhausted -> panel banner, editors intact; model outage -> `Assistant unavailable` + Retry, editors intact; guard refusal -> `refused:true` inline state; assist subsystem failure can never block profile/theme/block editing paths.
