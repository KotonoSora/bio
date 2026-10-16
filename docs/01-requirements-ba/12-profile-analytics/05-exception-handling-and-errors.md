# Profile Analytics - Exception Handling and Errors (feature 12)

Phase 1 BA · Beacon copy is invisible to visitors (fire-and-forget); dashboard messages are exact user-visible text. `(owned)` marks another feature's message.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Malformed beacon body | Intake | `400`, no write, counted `beacon.rejected{shape}` | (none; micro-script stays silent) |
| Beacon throttled | Intake | `429` + `Retry-After`; counted `beacon.rejected{throttled}` | (none) |
| Crawler UA detected | Intake | Rejected + counted `beacon.rejected{bot}`; never stored | (none) |
| Tampered/unknown username or blockId | Intake | Dropped after `202`; counted `unknown_target`; no oracle, no write | (none) |
| Queue unavailable (Redis down) | Intake | `202` still returned; counted `beacon.rejected{backend}`; alert fires; page unaffected | (none) |
| Storage volume down | Rollup | Aggregates proceed from the queue; raw NDJSON flush retried next drain | (ops alert) |
| Consumer partial batch failure | Rollup | The hour is retried; additive upsert math keeps retries correct | (ops alert) |
| Consumer lag > 90 min | Dashboard | Stale banner + chip; numbers remain visible; never fabricated fresh | "Data delayed - last updated {time}" |
| Replayed queue message | Rollup | Single increment via natural-key upsert (asserted, not hopeful) | (none) |
| Foreign profile param | Reads | `403 foreign_profile`; RLS additionally denies rows at the DB | "You do not have access to this profile" |
| Out-of-plan range | Reads | `422 range_clamped` + `allowed`; picker snaps back | "Available on your plan: 7/30/90d" |
| Reads rate-limited | Reads | Cooldown with retry timer | "Too many requests - retry in {n}s" |
| Session expired | Reads | Redirect to SSO sign-in; return to the same route | (auth-owned) |
| Profile with zero data | Dashboard | Illustrated empty + CTA; no zero-charts | "Share your page to start collecting views" |
| Deleted block in history | Dashboard | Historical counts preserved; title fallback label | "Deleted link" |
