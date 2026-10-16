# Link Blocks - Acceptance Criteria (feature 08)

Phase 1 BA · BDD form. Every AC runs in the drill suite (task T-10 of `docs/02-architecture-techlead/08-link-blocks/04-step-by-step-implementation-plan.md`).

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Reorder persistence | 10 blocks in a known order | Creator drags rows, reloads dashboard and public page | Order is identical on both surfaces; concurrent reorders from two tabs serialize with no duplicate positions; the losing commit gets `422 reorder_mismatch` and rolls back |
| AC-02 | Scheduling drill | Blocks set hidden, future-window, and expired | Public page and dashboard render | Block is absent from public HTML/JSON and present in the dashboard with the correct `hiddenReason` and date; boundary semantics: `visible_from` inclusive, `visible_until` exclusive |
| AC-03 | Validation drill | Create/update payloads carrying an unknown type, `http:`/`javascript:` URL, inverted window, oversized payload | Each is submitted | Exact `422` code per row (`unknown_block_type`, `bad_url_scheme`, `bad_schedule_window`, `payload_too_long`), nothing written |
| AC-04 | IDOR and RLS | Creator A and Creator B's profile/block ids | A mutates B's block; direct `psql` with A's `app.user_id` reads B's blocks | API `403`; RLS returns zero rows; no audit writes beyond A's own |
| AC-05 | XSS probe | `script`/`iframe`/event-handler strings in title, alt, or any payload field | Saved and rendered | Stripped at write; render is inert; anchors carry `rel="noopener ugc"`; CSP headers hold |
| AC-06 | Ordering ties | Two blocks sharing a `position` value (legacy/imported data) | Public and dashboard render | Order is `position ASC, id ASC` - ties break stably and identically on both surfaces |
| AC-07 | Duplicate and undo | Existing block B at position n | Creator duplicates B; then deletes another block and presses `Undo` within 5 s | Copy appears directly below B with a server-assigned position; undo re-creates a NEW row (new id) from the cached payload - copy states this honestly |
| AC-08 | Block budget | A profile with 250 blocks | Creator (or direct API) attempts a 251st create | `422 block_limit_reached`; UI disables `Add block` and restates the cap; zero writes |
| AC-09 | Contracts and payload hygiene | Dashboard list, public/internal read | OpenAPI diff computed; payload fields inspected | Diff additive-only; public payload carries no `user_id`, `is_visible`, `hiddenReason`, or owner fields; every `code` exact per contract tests |
| AC-10 | Public read path | Public traffic while blocks change | SSR (feature 10) renders page for a username | Blocks come from one internal call, windowed and ordered server-side; no draft filtering logic in the renderer; added latency within budget (NFR-05) |
| AC-11 | Type immutability | Existing `link` block | `PATCH` carrying `type: "social"` (API forced) | `422 validation_failed`, zero writes; edit dialog hides the type field |
| AC-12 | Publication freshness | A mutation commits | The purge event is intentionally dropped | Public page still catches up within 2 min via the 60 s cache TTL; exactly one event is emitted per reorder commit |
| AC-13 | Position ownership | Any client | Create, duplicate, reorder requests inspected | No request DTO carries a position value; server assigns dense 0-based positions; reload always shows the server sequence |
