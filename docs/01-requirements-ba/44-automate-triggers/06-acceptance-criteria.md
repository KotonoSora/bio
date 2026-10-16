# Automate Triggers - Acceptance Criteria (feature 44)

Phase 1 BA · BDD; drills run against `docs/02-architecture-techlead/44-automate-triggers/` implementations.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Match drill | Keyword (single + phrase), any-comment, self-comment, blocklisted, and out-of-scope comments | The watcher evaluates all | Exact matches fire; no fuzzy/typo match exists; self/blocklisted/out-of-scope log decisions with zero intents |
| AC-02 | Collision drill | An active keyword rule in scope | An overlapping phrase is saved; a disjoint phrase is saved | Overlap → 409 `keyword_collision`; disjoint saves; evaluation stays deterministic (first-match by creation order, logged) |
| AC-03 | Quota drill | Free 3 keyword / 4 catch-all active rules | A 4th keyword rule is saved; a downgrade occurs | Save refused with pause-oldest path; downgrade pauses newest-first with notice and zero deletions |
| AC-04 | Ban-safety drill | A comment storm (200 matches) | Delivery runs | Fire cap holds overflow (default 60/h); pacing bucket respected (≤ 10/min burst); quiet hours hold; one reply + one link-DM each, no drip; nothing exceeds caps |
| AC-05 | Spike drill | A 25% failure rate over 10 minutes | The monitor evaluates | Automation auto-pauses; ops paged; creator notified; reinstate requires ops + reason; paused intents resume in order |
| AC-06 | Webhook drill | Valid, tampered, and replayed comment webhooks | Each hits intake | Valid queues once; tampered refused (401); replay no-ops with the original outcome |
| AC-07 | One-link drill | Templates with zero, one, and two links; a non-allowlisted destination | Save + send paths run | Only the one-link https allowlisted template saves and sends; violations refused at save with the class |
| AC-08 | Window drill | A comment whose send is held past 24 h (quota + quiet fixtures) | The window check runs | `window_lapsed` with no retry; nothing sent after lapse |
| AC-09 | Replay drill | A redelivered watch/delivery message; concurrent duplicates | Consumers process | One intent per (automation, comment); one reply + one DM; no double-send |
| AC-10 | Gating-order drill | Fixtures tripping quota, suppression, quiet, and cap simultaneously | A send is attempted | Gates apply in the fixed order; the intent holds with the first tripped state and re-evaluates; zero bypass paths exist |
| AC-11 | Opt-out drill | A commenter replies `OPTOUT`; later comments match again | Watcher + delivery run | Suppression lands via 43; later matches `skipped_suppressed`; no capture, no send |
| AC-12 | Transparency drill | Any saved template | Copy is inspected | Automated-reply transparency present; stealth/hidden-sending classes refused at save; support audit finds no hidden sends |
