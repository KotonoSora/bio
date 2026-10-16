# Consultations and Tips - Acceptance Criteria (feature 25)

Phase 1 BA · BDD; legacy drills preserved by name.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Double-book drill | concurrent holds on one slot | both hold requests race | exactly one booking survives (partial unique index); the loser gets 409 `slot_taken` |
| AC-02 | Hold-expiry drill | an unpaid hold past TTL; a grant racing the TTL | expiry + grant run | slot frees at TTL with no orphan locks; post-expiry grant changes nothing and auto-refunds with re-book guidance |
| AC-03 | Policy drill | ≥ 24 h cancel, < 24 h cancel, no-show, creator cancel | each runs | ≥ 24 h full refund (`cancel_ge_24h`); < 24 h and no-show refused with citation; creator cancel auto-refunds (`creator_cancel`) + notice |
| AC-04 | Link drill | a non-https meeting link; a signed reschedule token | save + use | link rejected 422; token works without login and expires 24 h after the slot |
| AC-05 | IDOR drill | buyer B booking/availability; creator B windows | A attempts operations | 403 + RLS denies; zero rows touched |
| AC-06 | Floor/ceiling drill | below-floor, above-ceiling, and preset tip amounts | checkout runs | out-of-range 422 with bounds stated; presets settle end-to-end in sandbox |
| AC-07 | Guest drill | a guest tip with email; one without | checkout completes | with email: receipt delivered; without: 422 `email_required` with a clear message |
| AC-08 | Message drill | pending, approved, and rejected messages | the wall renders | pending invisible publicly; approved plain text (links/markup stripped); rejected private to creator + tipper |
| AC-09 | Finality drill | a tip-order refund request; an unauthorized-payment claim | each runs | refund refused with policy reason; the claim routes to the provider flow (32-40) |
| AC-10 | Abuse drill | rapid-fire tips; foreign jar config edits | attempts run | throttled 429 + Turnstile on the public surface; IDOR 403 |
| AC-11 | Reminder drill | a confirmed booking | T-24 h and T-1 h pass | transactional reminders sent (never in unsubscribe lists); reschedule re-anchors; cancel drops them |
| AC-12 | Purge drill | a creator with availability + bookings + messages; a guest tipper | each purge runs | creator rows deleted with counts; guest authorship scrubbed (approved anonymous pins stay anonymous) |

## Notes

- The legacy acceptance set (double-book, hold-expiry, policy, link, IDOR, floor/ceiling, guest, message, finality, abuse) is fully carried as AC-01..AC-10.
- AC-01's race is exercised at the DB layer (unique index) and at the API layer (409 mapping), matching the legacy wording.
