# AI Design Assist - Exception Handling and Errors (feature 16) - GATED

Phase 1 BA · GATED: future-build rows are prefixed `(future)`; the gate rows are live now. `(inherited)` marks existing handling reused unchanged.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Assist surface present before amendment | Gate (live) | Tooling blocks promotion: schema/OpenAPI/source/bundle diffs must show zero assist surface | (build failure, not UI) |
| Custom CSS/JS/HTML/font demand (incl. adversarial prompts) | Reads (future) | Output constrained to legal API inputs; inexpressible -> `200 {suggestions:[], refused:true}` | "No safe suggestion - try different answers" |
| Output failing validators (XSS/length/unknown theme id) | Reads (future) | Rejected before display; never rendered as applicable | (nothing renders) |
| Prompt-injection attempt in user content | Reads (future) | Content treated as data; system behavior unchanged; output stays in the closed set | (nothing - behavior unchanged) |
| Low-confidence generation | Reads (future) | Flagged; fewer options shown; no invented filler | "Low confidence" |
| Quota exhausted | Reads (future) | `429 assist_quota_exhausted`; upsell-safe; manual editing fully intact | "You are out of AI suggestions for now - continue manually" |
| Credit burst (cost attack) | Reads (future) | Per-user/day caps + rate limit + challenge hold the line; counted, alerted | "Too many requests - retry in {n}s" |
| AI/model outage | Reads (future) | Panel-only degradation: `Assistant unavailable` + Retry; editors untouched; no cache fallback | "Assistant unavailable" |
| Navigation mid-generation | Reads (future) | Fetch cancels cleanly; no orphaned job, no partial apply | (n/a) |
| Cross-profile assist call | Reads (future) | `403 foreign_profile` (inherited); RLS denies at apply | "You do not have access to this profile" |
| Stale suggestion replay | Reads (future) | Unrepresentable: suggestions are never cached client-side; staging state is session-scoped | (n/a) |
| Drafts expiry (if Q-04 table ships) | Reads (future) | Expired drafts vanish silently; re-request is a new generation (counts anew) | (n/a) |
| Model input scope creep | Reads (future) | Port accepts a compile-time whitelist DTO (quiz answers, bio, titles); wider data is unrepresentable | (n/a) |
| Session expired | Reads (future) | Redirect to SSO; return to the same editor (inherited) | (auth-owned) |
