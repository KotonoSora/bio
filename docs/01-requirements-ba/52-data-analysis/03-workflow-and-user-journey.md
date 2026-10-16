# Data Analysis (Pipeline Standards, Experiments, Metrics) - Workflow and User Journey (feature 52) - GATED

Phase 1 BA · W-1..W-6. GATED: all flows are design; nothing executes until the amendment. Rules: `docs/01-requirements-ba/52-data-analysis/02-user-stories-and-business-rules.md`. Contracts: `docs/02-architecture-techlead/52-data-analysis/03-api-contracts-and-payloads.md`.

## W-1 New event onboarding

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Implementer | registers the event (owner, retention class, properties schema) | versioned registry entry; review gate before emission | BR-02 |
| 2 | System | edge receives an unregistered event | rejected (loud in TEST; sampled + alerted in production) | BR-02 |
| 3 | System | registered but malformed payload | quarantined; owner notified; quality dashboard counts it | BR-02, BR-04 |
| 4 | System | events flow | validate → queue → dedupe (event_id) → aggregates; raw to R2 per class TTL | BR-03 |

## W-2 Rebuild / backfill

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Ops | aggregate is suspect or lost | rebuild from raw + ledger sources, keyed by grain + window | BR-06 |
| 2 | System | rebuild completes | byte-identical figures vs pre-loss snapshot (drill-verified); dual-report check during definition migrations | BR-06 |
| 3 | System | retention sweep runs | expired raw classes deleted on schedule; storage audit proves it; aggregates survive with provenance | BR-05 |

## W-3 Experiment lifecycle

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Experiment designer | registers the test pre-launch | hypothesis, variants, unit = user_id, primary metric + MDE, guardrails, duration, ship rule recorded | BR-08 |
| 2 | System | unregistered pricing change attempted | blocked from experiment surfaces by review gate | BR-08 |
| 3 | System | user encounters a surface | deterministic sticky assignment (server-side); variant pinned to the order at creation | BR-10 |
| 4 | System | guardrail breach (conversion floor, dispute ceiling, error spike) | experiment auto-pauses; ops paged with the offending slice; finance figures + grandfathered terms provably untouched | BR-11 |
| 5 | Designer/operator | fixed window closes | readout uses framework KPIs only; decision logged with evidence; ship via catalog versioning | BR-13 |

## W-4 Promo holdout

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Operator | launches a major promo campaign | holdout group reserved (eligible non-recipients), sized by the power rule | BR-12 |
| 2 | System | campaign runs | holdout buyers experience normal UX — never degraded treatment | BR-12 |
| 3 | Operator | reads results | true lift vs raw redemptions; feeds the campaign readout | BR-12 |

## W-5 Metric version change

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Analyst/finance | proposes a redefinition | new version appended with effective_from; overlap period shows old vs new side by side | BR-17 |
| 2 | System | finance close runs | names the definition version used; closed snapshots byte-identical | BR-15, BR-17 |
| 3 | System | post-close adjustments arrive | they net in the posting period with restatement notes; history never rewritten | BR-15 |
| 4 | System | consumers read | single library propagates the change; per-dashboard SQL edits never required | BR-18 |

## W-6 KPI consumption

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Finance | reads MRR/NRR/ARPU | identical figure everywhere (library-computed, versioned) | BR-14, BR-18 |
| 2 | Operator | drills into a figure | decomposition to ledger lines; KPI snapshot carries version | BR-14 |
| 3 | Earner-scoped caller | probes business KPIs | 403 — scoped dashboards keep their own ledgers | BR-16 |
| 4 | System | quarterly review | definitions reviewed with finance; unused metrics archived, never deleted | BR-19 |
