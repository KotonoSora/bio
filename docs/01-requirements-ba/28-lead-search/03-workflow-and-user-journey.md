# Lead Search - Workflow and User Journey (feature 28)

Phase 1 BA · Forward references cite their owning batches; nothing forward is stubbed.

## Creator: search → filter → export

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | opens the contacts view and types 2+ characters | trigram search scoped to their profiles; exact matches first; recent-first ordering | BR-01 |
| 2 | Creator | combines filters (source × status × consent × dates × form × tag) | combinable closed-set filters; count shown live | BR-03/04 |
| 3 | Creator | requests export of the filtered set or checked rows | 202 with job id + frozen row count; job runs async to a streamed CSV | BR-11 |
| 4 | System | job completes | owner-scoped download grant (15 min TTL) surfaces in the UI | BR-11/14 |
| 5 | Creator | downloads | CSV columns exactly the stable schema (consent timestamps + copy version included) | BR-04/11 |
| 6 | System | 7 days later | job + file purge; account deletion purges immediately | BR-11/15 |

## Creator: detail → act → propagate

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | opens a contact | assembled timeline: events, consent history, provenance, notes, tags; forward sections (purchase/booking/automation) only when their seams exist | BR-05 |
| 2 | Creator | adds a note | append-only, attributed, evented | BR-06/13 |
| 3 | Creator | tags (new or existing vocabulary) | assignment + vocabulary auto-add until cap 50; foreign tags invisible | BR-07 |
| 4 | Creator | suppresses with a reason | rank-guarded permanent status; evented; eligibility flips before the next send | BR-08 |
| 5 | Creator | marks read/unread | flag-only mutation; evented | BR-12 |
| 6 | System | next send/broadcast check (forward) | shared eligibility check returns the suppressed state | BR-08 |

## Contact: re-invite → self-manage

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | re-invites a non-suppressed contact | transactional email with a signed 72 h single-purpose token | BR-09 |
| 2 | Contact | opens the link | one-tap unsubscribe (preference center = forward superset when the monetize batch lands) | BR-09 |
| 3 | System | contact unsubscribes | `active→unsubscribed` (rank-guarded); evented; provable in the timeline | BR-08/13 |
| 4 | System | repeated re-invites | rate-limited 3/day per lead | BR-09 |

## Support seat (forward) and purge

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Support seat (45-47 forward) | opens the creator-scoped view; acts | same view; every action attributed `support:{seatId}` | BR-16 |
| 2 | Purge runner | account deletion | notes, tags, vocabulary, queries, export jobs + files removed; counts in the certificate | BR-15 |
