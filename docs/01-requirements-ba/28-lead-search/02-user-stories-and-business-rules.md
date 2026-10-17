# Lead Search - User Stories and Business Rules (feature 28)

Phase 1 BA · Realized by `docs/02-architecture-techlead/28-lead-search/` (BR-01..BR-16). Status machine, consent classes, and suppression rank are feature 27's canonical contract and are consumed, never redefined: `docs/01-requirements-ba/27-lead-forms/02-user-stories-and-business-rules.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Creator | find a contact by email or name, even with a typo | I can work contacts fast | Must |
| US-02 | Creator | filter and see how many contacts match before exporting | I export exactly what I mean | Must |
| US-03 | Creator | see one honest timeline per contact | I trust the record when it matters | Must |
| US-04 | Creator | note, tag, and suppress with attribution | follow-up and opt-outs are provable | Must |
| US-05 | Contact | have my suppression honored and my history provable | my choice sticks | Must |
| US-06 | Support (future seat) | the same creator-scoped view with attributed actions | help stays in scope | Should |

## Business rules

- BR-01 Search: substring match on email + name via trigram indexes; minimum 2 characters; ranking exact-match first, then typo-tolerant; default ordering newest-captured first; every query is pre-scoped to the creator's own profiles (`user_id` + RLS); no wildcard, platform-wide, or cross-account lookup exists.
- BR-02 Query history: the creator's own recent queries (last 20 per account) are offered as a convenience; never shared, never shown to other users.
- BR-03 Filters (closed set, combinable): source (`form|checkout|import|ops|automation`), status (`pending|active|unsubscribed|suppressed|bounced|complained`), consent class, date range (captured), form block, tag; campaign-of-origin is a recorded slot that populates only when broadcasts ship.
- BR-04 Count discipline: results are countable with the same predicates as the export selection; the count is shown before export; count and exported rows reconcile exactly (drill).
- BR-05 Detail timeline: one chronological view merging `lead_events`, consent history (captured/resubmitted/status_changed), capture provenance, notes, and tags; read-only references to purchase/booking rows and automation contacts render only where their forward seams exist — never stubbed; every entry carries timestamp + actor.
- BR-06 Notes: append-only, ≤ 2 000 chars, attributed (author + kind), sanitized before storage; no edit or delete path.
- BR-07 Tags: creator-defined vocabulary, closed per account (cap 50; tag ≤ 40 chars); assigning a new tag adds it to the vocabulary until the cap; no rename/delete in v1; assignments are per-lead; foreign tags are invisible.
- BR-08 Suppression: manual suppression requires a reason (≤ 200 chars), is permanent, and is rank-guarded by feature 27's strictest-wins machine; it propagates to send eligibility before the next send (shared eligibility check).
- BR-09 Re-invite: sends a transactional-class email carrying a signed single-purpose token (72 h) that lets the contact self-manage (one-tap unsubscribe; the full preference center is the monetize batch's superset of the same page); rate-limited 3/day per lead; suppressed contacts cannot be re-invited.
- BR-10 No consent grants: no endpoint, import, or UI path can set a consent class (surface-review gate, same as feature 27); no bulk-message action exists in v1 — export is the only egress.
- BR-11 Export: selection = the filtered set or explicitly checked rows; always an async job (count-frozen at enqueue); column-stable CSV (`email, name, phone, source, source_ref, consent_class, consent_at, consent_copy_version, status, created_at, tags`); streamed server-side; download via a short-lived owner-scoped grant (15 min); job + file live 7 days then purge; quota 20/day per account, 2 concurrent jobs.
- BR-12 Read state: opening and explicitly marking read/unread mutates only the `unread` flag; the action is evented.
- BR-13 Audit: notes, tags, suppression, re-invites, exports, and read-state changes append actor-stamped events (24-month retention); UPDATE/DELETE on event tables alerts.
- BR-14 Security: owner scoping + RLS on every read/write; cross-creator access 403 with zero rows; export grants are owner-bound and non-transferable; export files are private (no storage-path exposure).
- BR-15 Purge: account deletion removes notes, tag assignments, vocabulary, query history, export jobs, and export files along with feature 27's rows; counts appear in the purge certificate.
- BR-16 Attribution forward: every action records actor (`creator:{userId}` today; `support:{seatId}` when agency seats land) — no anonymous writes exist.
