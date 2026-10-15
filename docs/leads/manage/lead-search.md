# Lead Search (find, inspect, and work contacts)

PLAN ref: P2-4 (extends the leads shape captured in `capture/lead-forms.md`). P2 scope. Lifecycle/hygiene/export mechanics in `monetize/marketing/audience-lists.md` (consumed here, never redefined); automation contacts in `docs/automate/contacts/contact-lists.md` appear read-only-merged where noted.

## Purpose

Give creators a working view of their contacts: search by email or name, filter by source/status/form/campaign, open a full per-lead timeline, and act (tag, note, suppress, export) — without ever seeing another creator's data or touching platform-wide audiences. Search is the daily tool; the audience doc is the machinery underneath.

## Users

- Creator: finds "the buyer from Saturday's drop," checks what they consented to, adds a note, tags for follow-up, suppresses on request.
- Support (creator-side, future agency seat): same view under the creator's scope, every action attributed.
- Contact (indirect): benefits from accurate records — suppression requests honored, history provable.

## Functional requirements

1. Search: substring match on email + name (trigram-indexed, min 2 chars, typo-tolerant ranking with exact-match first); recent-first default ordering; history of the creator's own recent queries (per-account, never shared). No wildcard/platform-wide lookup — every query is pre-scoped to the creator's profiles.
2. Filters (closed set, combinable): source (form block, checkout opt-in, automation, import, ops-added), status (active/unsubscribed/suppressed/bounced/complained/pending), consent class, date range, form/campaign of origin, tag. Filter combinations produce countable results with the count shown before export (same confirm-the-count discipline as broadcasts).
3. Lead detail: single timeline merging `lead_events` + capture provenance + consent history + purchase/booking links (read-only references to order rows where the email matches — no order mutation from this view) + automation events where the address overlaps automation contacts. Every entry timestamped with actor.
4. Creator actions (all audited, all owner-scoped): freeform notes (append-only, timestamped, attributed), tags (creator-defined vocabulary, closed per account to prevent tag-sprawl), manual suppression (with reason — honors opt-out requests phoned in or DM'd), re-invite to preference center (sends the signed-link email for the contact to self-manage). No manual consent grants — consent only ever comes from the contact's own action.
5. Automation-contact merge: where an email matches an automation contact row, the detail view shows both provenances side by side (form-capture + automation history) without merging the rows — each system keeps its own record, the UI correlates. Suppression in either surface enforces in both (shared eligibility check).
6. Export entry: selection (filtered set or checked rows) flows to the audited CSV/leads-handoff export owned by the audience doc — this view composes the selection, never the file. Bulk-message actions are absent in v1 (broadcasts own sending; no ad-hoc "email these 50 people" bypass).

## Data / API touches

- No new tables. Reads: `leads` + `lead_events` + consent history + (correlated) automation contacts; writes: notes/tags (new `lead_notes{lead_id, author, body}` append-only + `lead_tags{lead_id, tag}` with per-account vocabulary cap) + suppression transitions via the shared state machine. Trigram index (`pg_trgm`) on email/name for search; all queries pre-scoped `WHERE user_id = session.userId` + RLS.
- Endpoints: `GET /api/v1/leads/search?q=&filters=` (paginated, count-included, latency-budgeted), `GET /api/v1/leads/:id` (assembled timeline), notes/tags/suppress actions, export-handoff (delegating). All PG via `worker/db/*`.
- Performance: search p95 budget documented (see Q1); count queries use the same predicates as the export selection (no count/export skew).

## Acceptance criteria

- Search drill: exact email ranks first; typo'd query still surfaces the row; 2-char minimum enforced; cross-creator rows never appear (leak probe with overlapping fixtures returns empty).
- Filter drill: combined source × status × date filters reconcile exactly with direct row counts; count-before-export matches exported rows.
- Timeline drill: capture, consent change, purchase reference, automation event, and note render in one chronological view with actors.
- Action drill: note/tag/suppress append audit rows; manual consent-grant attempts impossible (no endpoint exists — verified by surface review); suppression propagates to send eligibility before the next broadcast.
- Performance drill: 100k-lead fixture search within p95 budget; export selection of 10k rows completes without timeout (async file path where needed).

## Out of scope

- Capture mechanics (see `capture/lead-forms.md`); lifecycle, hygiene, CSV file mechanics (`monetize/marketing/audience-lists.md`); consent classes (`monetize/marketing/consent-preferences.md`); broadcasts (`monetize/marketing/campaign-broadcasts.md`, P4).
- Retargeting segments and ad audiences (see `monetize/advertising/retargeting-reach.md`); agency seats (P4 agency scope).

## Open questions

- Q1: Search latency budget at 100k rows/creator (e.g. p95 < 400 ms) — and async-handoff threshold for giant selections?
- Q2: Tag vocabulary cap per account (e.g. 50) — enough for organizers without becoming a second taxonomy?
