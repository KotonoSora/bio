# Earnings Dashboard - Exception Handling and Errors (feature 15)

Phase 1 BA · Read-only unification slice: no public intake, so visitor-facing errors do not exist. `(inherited)` marks feature 12-14 handling. Dashboard messages are exact user-visible text.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Cross-user `profile` param (JSON + CSV) | Reads | `403 foreign_profile` (inherited); owner check precedes any source fan-out | "You do not have access to this profile" |
| Out-of-plan range (incl. direct API) | Reads | `422 range_clamped` + `allowed`; both endpoints clamp identically; probing counted, not punished | "Available on your plan: 7/30/90d" |
| No shipped revenue source | Reads | Money keys absent from the payload; engagement-only render; zero stubs in DOM | (nothing - design, BR-04) |
| Revenue source timeout (flag set) | Reads | Cell group listed in `unavailableCells`; rest of the view renders; retry refetches the view once | "Temporarily unavailable" |
| Unknown future flag | Reads | Ignored (closed set); a new strip requires a spec amendment, never an inferred panel | (nothing) |
| CSV export flood | Reads | Stricter owner throttle; `429 rate_limited` + `Retry-After`; streaming keeps memory flat | "Too many exports - retry in {n}s" |
| `clicks = 0` on a row | Reads | `rpc` null; em-dash renders; not an error | "-" |
| CTR under 30 views | Reads | `ctrX1000` null -> `not enough data` (13's floor, inherited) | "Not enough data (needs 30+ views)" |
| Gross-only revenue source | Reads | No net column rendered; label stays `gross`; absent, not zero | (label: gross) |
| Deleted block in funnel | Reads | `title:null, deleted:true` -> `Deleted link` (inherited) | "Deleted link" |
| Partial money totals | Reads | `moneyComplete:false`; money totals hidden (never summed across missing rows) | (totals row omits money) |
| Interval polling attempt | Reads | Client disallows interval refetch (hourly grain); idle screens issue zero requests | (none) |
| Live feed divergence from unified figures | Reads | Different grains by design; each labeled; no server mixing; disputes resolve to source aggregates | (both labeled) |
| Session expired | Reads | Redirect to SSO; return to the same range and screen | (auth-owned) |
