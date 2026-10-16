# Automate Triggers - User Stories and Business Rules (feature 44)

Phase 1 BA · Enforced by `docs/02-architecture-techlead/44-automate-triggers/`. Scope: `docs/01-requirements-ba/44-automate-triggers/01-document-context-and-scope.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Creator | create keyword rules per post, toggle any-comment catch-alls, preview what a commenter receives | matching stays exact, auditable, and scoped | High |
| US-02 | Commenter | receive a relevant reply plus a single DM with the promised link and then silence | one comment earns exactly what was promised | High |
| US-03 | Creator | compose reply/DM templates with one link slot and watch per-event delivery status | delivery stays restrained and observable | High |
| US-04 | Creator | trust quotas, caps, and quiet hours are enforced before sending | the account never gets banned by volume | High |
| US-05 | Ops | see spikes, reinstate after a fix, and review reported templates | the riskiest surface has a human backstop | Medium |

## Business rules

- **BR-01 Trigger kinds (closed).** `keyword` (single/multi-word exact phrases, case-folded, trimmed) and `any_comment` catch-all; each rule binds to one automation; scope is a media list or account-wide; no fuzzy matching exists in v1.
- **BR-02 Collision rejection.** A new keyword rule whose phrases overlap an existing active rule in the same scope is rejected (`keyword_collision`); overlap checks run server-side at save; watcher evaluation is deterministic (first-match by rule creation order, recorded in the trigger log).
- **BR-03 Rule quotas.** Free: 3 keyword rules + 4 any-comment rules active; pro: unlimited (values in `publish.quotas`); enforced server-side at save and at watcher evaluation; downgrade pauses newest-first with notice (never deletes).
- **BR-04 Connection requirements.** Instagram Business/Creator with comment-management + DM-send scopes and `healthy` state; access loss suspends the automation with creator notice; resume requires a healthy connection.
- **BR-05 Matching discipline.** The watcher evaluates the comment's first observed version exactly once per `(automation, comment)`; creator's own comments never match; blocklisted authors (platform blocks) are skipped; every observed comment appends a trigger-log row with its decision (matched / no_match / self_comment / blocklisted) — auditable end to end.
- **BR-06 Delay.** `delay_s` ≥ 60 (default 60) between match and send; the delay exists to avoid robotic immediacy, never as a drip mechanism.
- **BR-07 Fire caps.** Per automation + media: ≤ `fireCapPerHour` (default 60, range 10..200) sends per rolling hour; overflow queues with backoff and a creator warning — never dropped, never blasted.
- **BR-08 Delivery restraint.** Exactly one public reply + one DM containing exactly one link (https + destination allowlist, enforced at template save and re-checked at send); no follow-ups, no second DM, no drip; silence until the same person comments again (a fresh consent event).
- **BR-09 Window.** Delivery inside the 24 h human-agent window from the triggering comment; after lapse → `window_lapsed`, no retry; the reply/DM never pretends to be a human when it is not (transparency copy required; stealth is a violation).
- **BR-10 Pre-send gating (ordered).** Every send runs: contacts quota (43's single eligibility + quota) → suppression → window → quiet hours → fire cap → pacing; holds are states (`held_quota`, `held_quiet`, `held_cap`), re-evaluated when the hold lifts; none of the gates may be bypassed.
- **BR-11 Quotas (contacts).** Free 100 unique contacts/year; pro 100k/month; enforced pre-send through 43's counting function; over-quota holds as `held_quota` with upgrade-or-wait copy.
- **BR-12 Quiet hours.** Creator-local 22:00–08:00 default (`publish.policy`); sends hold as `held_quiet` and release in order when the window ends; urgent/promised timing never overrides quiet hours.
- **BR-13 Pacing.** Token bucket per connected account (burst ≤ 10/min, sustained ≤ 30/min); enforces human-scale sending regardless of caps.
- **BR-14 Burst-abort.** A per-automation error-rate spike (> 20% failures over 10 minutes, min 20 attempts) auto-pauses the automation, pages ops, and notifies the creator; reinstate is ops-only after review.
- **BR-15 Template checks + review.** Save-time automated checks: exactly one link slot, https + allowlist destination, prohibited-content classes (stealth/hidden sending, misleading claims), length limits; reported templates enter a human review queue with pause-until-decided; every template edit re-runs checks.
- **BR-16 Contact capture.** Capture (43) happens only when a send is accepted; suppressed/stale contacts are skipped (`skipped_suppressed`) without capture; `OPTOUT` is handled by 43's transition, not a DM.
- **BR-17 Idempotency.** One intent per `(automation, comment)`; watcher/delivery consumers are replay-safe; a comment delete with a pending intent cancels it; duplicate webhook deliveries no-op.
- **BR-18 Status honesty (closed).** Intent states: `matched|held_quota|held_quiet|held_cap|sending|replied|dm_sent|failed|skipped_suppressed|window_lapsed|cancelled`; reasons in plain language; the trigger log and intent list show exactly what happened.

## Acceptance criteria pointers

Drills in `docs/01-requirements-ba/44-automate-triggers/06-acceptance-criteria.md`: match, collision, quota, spike, webhook, one-link, window, replay, ban-safety. GWT forms live there.
