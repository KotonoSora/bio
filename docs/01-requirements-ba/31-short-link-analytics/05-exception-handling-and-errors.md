# Short-Link Analytics - Exception Handling and Errors (feature 31)

Phase 1 BA · Codes are stable; UI copy localizes vi/en.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Stats for foreign/unknown code | API | 404 `link_not_found` (no existence oracle) | "Not found." |
| Stats for tombstoned link | API | 200 with history (aggregates survive tombstones) | `This link is retired — history shown` note |
| No hits yet | Readout | empty state distinguishing no-hits from lag | "No opens yet" |
| Consumer lag above SLA | Readout | `generated_at` + lag footnote; values still served | "Numbers may lag by ~N minutes." |
| Queue outage (no new counts) | System | enqueue retries at producer; repair after recovery; resolve unaffected | (footnote) |
| Raw gap detected | System | `rawgap` alert; affected day flagged in footnote | "Evidence incomplete for this day." |
| Replay beyond TTL | API | 410 `evidence_expired` | "Evidence has aged out — figures stand as reported." |
| Quarantine case not found / foreign | API | 404 `quarantine_not_found` | "Not found." |
| Decide without reason / bad action | API | 422 `decide_invalid` | "Add a reason and choose release or drop." |
| Creator hits ops routes | API | 403 + audit | "Ops only." |
| Stats rate cap / CSV cap | API | 429 `throttled` | "Too many requests — try later." |
| CSV generation failure mid-stream | System | stream aborts with the standard error envelope; nothing partial is presented as complete | "Export failed — try again." |
| Bio-embedded link confusion (double-billing question) | UI | dual-count explainer with matched windows | (info line) |
