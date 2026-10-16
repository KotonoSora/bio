# Lead Forms - Workflow and User Journey (feature 27)

Phase 1 BA · System calls cite owning features; forward contracts cite 32-40 and the monetize batch.

## Visitor: submit → (confirm) → active

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Visitor | opens a bio page with a `form` block | the block renders: headline, offer copy, fields, submit label; marketing box unchecked | BR-01/05 |
| 2 | Visitor | types an email (one tap) and submits | client validates inline; server re-validates; Turnstile-class check passes | BR-02/11 |
| 3 | System | submit accepted | uniform 202; one capture-outbox row written (no lead write yet) | BR-03/10 |
| 4 | System | consumer processes (≤ 5 s p95) | lead upserted with source + consent class + `consent_at` + copy version; inbox unread; `captured` event | BR-03/04/05 |
| 5 | System | double-opt-in ON | confirm email sent (transactional, 48 h token) | BR-07 |
| 6 | Visitor | taps the email link and confirms | explicit POST flips `pending→active`; single welcome fires | BR-07/08 |
| 7 | Visitor | never confirms | row stays `pending`, ineligible for sends, visible in the inbox | BR-07 |

## Creator: configure → watch → follow up

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | adds a `form` block | closed-schema payload validated; `copyVersion` stamped | BR-01 |
| 2 | Creator | edits fields/copy | new `copyVersion`; prior submissions keep their recorded version | BR-01/05 |
| 3 | Creator | opens the inbox | per-form feed newest-first, unread markers | BR-09 |
| 4 | Creator | opens the unified contact view | delegated to feature 28 | (28) |

## Abuse and edge paths

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Bot | bursts submits on one block | per-block cap 100/min/source → form pauses 15 min; creator notice; visitors see `submit_paused` | BR-11 |
| 2 | Bot | repeats an identical submit | one lead row; within 60 s fully coalesced; uniform 202 either way | BR-04/10 |
| 3 | Bot | probes an unknown block id | identical 202; consumer drops with internal metric | BR-10 |
| 4 | Script | injects markup in name/note | stripped before storage; success state never reflects input | BR-12 |
| 5 | Scanner | prefetches the confirm link | GET shows the confirm page only; nothing confirms until the POST | BR-07 (Q3) |

## Forward consumers and purge

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Checkout (33-34, forward) | order opt-in checked | writes a lead via the internal capture API with the same shape (`source=checkout`), same dedupe/merge | BR-04/05 |
| 2 | Notification pipeline (forward) | provider bounce/complaint | `status_changed` to `bounced`/`complained` per strictest-wins | BR-06 |
| 3 | Purge runner | creator account deletion | leads + events + outbox rows removed; counts in the purge certificate | BR-15 |
