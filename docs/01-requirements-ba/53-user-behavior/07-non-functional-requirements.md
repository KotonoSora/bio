# User Behavior (Journeys, Conversion, Retention) - Non-Functional Requirements (feature 53) - GATED

Phase 1 BA · GATED: NFR-01/NFR-02 are live now (gate enforcement); the rest bind the future build at the amendment. Floors, windows, and thresholds are config-owned (`analytics.kFloor`, `retention.*`) — never invented here.

## Gate enforcement (live now)

| ID | Requirement | Threshold |
|---|---|---|
| NFR-01 | Absence verification | Every release: schema diff + OpenAPI diff + source/bundle grep return zero behavior surface; violation blocks promotion by tooling (AC-01) |
| NFR-02 | No-stub discipline | No journey table, replay UI, friction job, or risk-band code may exist in any other feature or release before the amendment |

## Future build (at amendment)

| ID | Requirement | Threshold |
|---|---|---|
| NFR-03 | Assembly cadence | Journey assembly batch on the analytics cadence (hourly-or-better freshness); never in-request |
| NFR-04 | Stitching determinism | Same events → same journey grouping; joins only on `(user_id or email hash) + order/proforma context`; zero fingerprinting code (grep gate) |
| NFR-05 | K-anonymity enforcement | Floor default 10 journeys (config); sub-floor collapse automatic in every readout; no raw export path exists |
| NFR-06 | Closure sweeps | Stale trails finalized within the sweep window (Q-02: 14 days inactivity; open proforma extends to 33's expiry); zero orphans past retention |
| NFR-07 | Privacy propagation | Deletion clears raw trails within the documented window (52's classes); behavioral reads provably absent from gating/pricing/fraud dependencies (review gate) |
| NFR-08 | Ranking inputs | Monthly backlog computed from aggregates only; baselines attached to every score; revenue-at-stake as ranges with labeled uncertainty |
| NFR-09 | Segment integrity | Closed cut set only; renames versioned like KPI changes with overlap reporting; k-floor in every output |
| NFR-10 | Retention-loop freshness | Adoption flags and risk bands refreshed on the analytics cadence; band reasons always present (explainability requirement) |
| NFR-11 | Send-path gating | Win-back and rescue messages ride 36/48 paths only; consent classes arbitrated at send time by 48's eligibility; frequency caps inherited |
| NFR-12 | Rate limits | Behavior readouts 300/h/role; internal risk-band reads 600/h/service; experiment handoff 30/h; 429 with `Retry-After` |
| NFR-13 | Audit retention | Segment-definition versions, playbook links, experiment handoffs, deletion propagations, band-logic versions: 24 months minimum |
| NFR-14 | Observability | Metrics: journey counts by stage, marker rates, sub-floor suppression counts, closure sweep outcomes, adoption/band distributions, win-back responses — no buyer labels |
