# Link Blocks - Non-Functional Requirements (feature 08)

Phase 1 BA · Thresholds are v1 gates; changing one requires a spec revision with a measured trigger.

## Performance (client-observed)

| NFR | Target |
|---|---|
| NFR-01 `GET /api/v1/blocks` (owner list, up to 250 rows) | p95 <= 200 ms |
| NFR-02 create / patch | p95 <= 300 ms |
| NFR-03 delete | p95 <= 200 ms |
| NFR-04 reorder (250 rows, one transaction) | p95 <= 400 ms |
| NFR-05 internal windowed read for SSR | p95 <= 100 ms; adds <= 50 ms to the public resolve path |
| NFR-06 mutation -> public visibility change | <= 2 min (60 s cache TTL + purge event) |

## Scale

- Blocks are the dominant table by row count: average ~20 blocks/profile, v1 cap 250; growth is linear with profiles. `position` stays `INT`; no partitioning, no full-text or trigram indexes in v1.
- Hidden/expired rows are retained as dashboard history and are purged only with their profile (FK cascade).
- Reorders serialize per profile (one in-flight commit; bursts queue client-side).

## Availability

- Dashboard endpoints: 99.95% monthly; mutations fail closed (no partial position writes ever - reorder is all-or-nothing).
- Public page depends only on the cached windowed read; a database incident degrades the dashboard, and the public page serves from cache until its TTL expires.

## Security and privacy

- Sanitization at write for every payload string (script/iframe/event-handler stripped); no HTML/JS/CSS fields exist by design.
- Owner scoping in service plus RLS; public reads additionally filtered by the window predicate server-side (never client-side).
- CSRF on all mutations. Rate limits: block writes 120/h/user; reorder 60/h/user; owner list reads 300/h/user; breach -> `429 throttled` + `Retry-After`.
- Payload bodies are never logged; audit rows carry actor + operation + block id only.

## Accessibility

- Drag reorder has keyboard equivalents (move up/down controls) with ARIA live announcements of the new position; dialogs trap focus and restore it on close.

## Localization and observability

- All UI strings resolve from stable codes; dates render locale-aware (`Goes live <local time>`).
- Metrics: `blocks.write.total{op}`, `blocks.reorder.latency`, `blocks.reorder.mismatch.total`, `blocks.limit_reached.total`, `blocks.sanitized.total`.
- Alerts: reorder mismatch rate > 1% over 10 min (client staleness bug); reorder p95 > 400 ms for 10 min; cascade/orphan check job reports any `profile_id` without parent (must be zero).
