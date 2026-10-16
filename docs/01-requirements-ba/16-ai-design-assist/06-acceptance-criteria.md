# AI Design Assist - Acceptance Criteria (feature 16) - GATED

Phase 1 BA · GATED: AC-01 runs every release (live now); AC-02..AC-12 run only at/after the spec amendment. Drills reference `docs/02-architecture-techlead/16-ai-design-assist/04-step-by-step-implementation-plan.md`.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Absence drill (LIVE) | Any release candidate while gated | Schema diff, OpenAPI diff, source + dashboard-bundle grep run | Zero assist surface found (`assist/` routes, `suggestion_draft`, `kind='design-assist'`, panel strings); a hit blocks promotion |
| AC-02 | Guardrail drill | Adversarial prompt demanding custom CSS/JS/font-URL | Assistant responds | Only gallery-valid suggestions or `refused:true`; validator-failing output never renders as applicable |
| AC-03 | Human-loop drill | Staged suggestion; publish evaluated | Apply + publish path inspected | Nothing reaches the public page without explicit Apply + publish; audit shows actor = user on every applied change |
| AC-04 | Quota drill | Exhausted AI credits | Generation requested; editors exercised | Generation blocked with upsell-safe message; theme/block/profile editing fully functional |
| AC-05 | Injection drill | Bio text containing instruction-override attempts | Processed | Treated as data; system behavior unchanged; output stays within the closed suggestion set |
| AC-06 | IDOR drill | User A assist calls targeting user B's profile | Evaluated; apply attempted | `403` on the API; RLS denies at the database on apply |
| AC-07 | Cost drill | Per-user/day burst | Generations run | KV caps hold; ledger counters consistent; dashboard render never blocked (async progress state) |
| AC-08 | Diff drill | Each suggestion surface rendered | Previewed | Every suggestion shows before/after; low-confidence flagged with fewer options; no AI-authority styling |
| AC-09 | Outage drill | Model capability unavailable; navigation mid-generation | Panel used | `Assistant unavailable` + Retry; navigation cancels cleanly; no cached suggestion ever renders |
| AC-10 | Authority drill | Dashboard bundle and codebase | Grepped | No auto-apply or auto-publish control exists anywhere |
| AC-11 | Locale drill | VN and EN creators | Generation requested | Output language matches the creator locale with EN fallback; copy localized |
| AC-12 | Gate-promotion drill | Spec amendment approved | Before first build ships | Amendment recorded; AC-02..AC-11 planned into the release gate; absence drift check flips to surface-drift check |
