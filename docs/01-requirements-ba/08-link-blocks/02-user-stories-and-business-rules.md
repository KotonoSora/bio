# Link Blocks - User Stories and Business Rules (feature 08)

Phase 1 BA · Stories seed `06-acceptance-criteria.md`; rules `BR-xx` are quoted by every later file.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Creator | add a block by picking one of the 13 types and filling its form | my page gets content without a later type migration | Must |
| US-02 | Creator | edit a block's payload without changing its type | fixes never reorder my page | Must |
| US-03 | Creator | drag blocks into order and trust it persists | my page reads the way I arranged it | Must |
| US-04 | Creator | hide a block or schedule a live window | content appears and disappears on time | Must |
| US-05 | Creator | delete or duplicate a block with honest feedback | maintenance is quick and predictable | Must |
| US-06 | Visitor | see only visible, currently-scheduled blocks in order | hidden content never leaks | Must |
| US-07 | Creator | point a `resume` block at one of my resume documents | my page shows my CV without duplicating it | Should |
| US-08 | Public-site service | read the windowed block list in one call | SSR renders the page without server-side filtering logic | Must |

## Business rules

- **BR-01 Frozen superset.** Exactly 13 types: `link|social|gallery|video|music|event|form|product|course|tip|resume|contact|embed`. Unknown values rejected at the API and by the DB CHECK; adding a 14th type requires a spec amendment.
- **BR-02 Data-only payloads.** No raw HTML/JS/CSS fields exist; `embed` stores `url` + `provider`; the renderer decides markup. Strings are sanitized (script/iframe/event-handler stripped) at write.
- **BR-03 URL scheme.** Every URL in any payload must be `https:` with a non-empty host; `javascript:`/`data:`/`http:` rejected with `422 bad_url_scheme`.
- **BR-04 Ordering.** Positions are dense, 0-based, server-assigned; the client never sends position values. Reorder is one transaction: the submitted id set must equal the server's block set exactly, else `422 reorder_mismatch` with zero writes.
- **BR-05 Visibility and scheduling.** Public reads require `is_visible AND now in [visible_from, visible_until)` (NULLs open-ended). Outside the window the block is hidden publicly without deletion; the dashboard shows `hiddenReason` (`hidden`, `scheduled_future`, `expired`).
- **BR-06 Resume pointer.** A `resume` block carries `{documentId}` only; resume content, templates, and rendering belong to features 17-21 (logical reference, no cross-service FK).
- **BR-07 Block budget.** 250 blocks per profile in v1 (`422 block_limit_reached` beyond); plan-configurable later.
- **BR-08 Scoping.** Owner actions scope by session `user_id` plus RLS; cross-user mutations return 403 and are denied at the DB.
- **BR-09 Duplicate semantics.** Duplicate inserts a copy directly below the source (server computes position); undo-after-delete re-creates a new row from the cached payload - stated honestly in the UI, never a true undelete.
- **BR-10 Type immutability.** Type is fixed at create; changing it means delete + create.
- **BR-11 Publication.** Every mutation publishes `profile.published.v1`; caches (owned by feature 10) are bounded by a 60 s TTL, so public freshness holds within 2 min even if an event is lost.
- **BR-12 Rate limits.** Write endpoints are rate-limited per user and CSRF-protected; reorder bursts queue client-side behind one in-flight commit.
