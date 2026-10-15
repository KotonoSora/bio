# DM Delivery (public reply + 1-link DM, quota pre-send)

PLAN ref: P4-3 (public-reply + 1-link DM → leads; contacts quota enforced pre-send). P4 scope — do NOT build in P1–P3. Matching in `triggers/comment-triggers.md`; captured contacts land in `contacts/contact-lists.md`.

## Purpose

Deliver exactly what the comment promised and nothing more: one public reply plus one DM containing one link, inside the platform's messaging window, with contact quotas enforced before sending rather than apologized for after. This is the highest-ban-risk surface in P4 (unsolicited-DM enforcement kills connected accounts), so restraint is the feature: one link, no follow-ups, no drip.

## Users

- Commenter: *As a Commenter, I want to receive a relevant reply plus a single DM with the promised link and then silence so that one comment earns exactly what was promised.* Receives a relevant reply + single DM with the promised link, then silence unless they comment again.
- Creator: *As a Creator, I want to compose reply/DM templates with one link slot and watch delivery status per trigger event so that delivery stays restrained and observable.* Composes reply/DM templates with one link slot, watches delivery status per trigger event.
- Platform integrity (as a stakeholder): *As a Platform integrity, I want window compliance, rate discipline, and template review so that account bans are avoided.* Account bans avoided through window compliance, rate discipline, and template review.

## Functional requirements

1. Send unit (closed shape): per fired trigger, at most one public reply (from `reply_templates`, short, no link required) + one DM containing exactly one link (URL validated `https:`, allowlisted against the same destination policy as short links). Templates support `{keyword}` and `{link}` slots only — no freeform per-recipient scripting in v1.
2. Messaging-window compliance: IG's 24-hour human-agent window honored (send only within window of the triggering comment; expired windows mark the intent `window_lapsed` with no retry — re-commenting re-arms naturally). Platform opt-out signals (blocks, report-spam, `stop`-class replies) suppress that recipient permanently (see contacts doc suppression states).
3. Contact-quota pre-send (adapted ladder — Plus-rung 1k/month not copied): free 100 contacted users per year; pro 100k per month. Quota check runs inside the send consumer immediately before dispatch (a contact added mid-burst still counts); over-quota intents queue as `quota_held` with creator notice (upgrade-or-wait path), never sent, never dropped silently. New-comment intents from already-contacted users in-window do not double-count (contact = unique recipient per period).
4. Rate discipline: per-account send pacing (token-bucket shared with the trigger fire-caps), `delay_s` honored per automation (minimum 60 s), quiet hours creator-configurable (no DMs 22:00–08:00 recipient-local by default; held intents deliver after). Burst-abort: platform error-rate spike auto-pauses the automation and pages ops before the account is banned.
5. Content rules: one link means one — templates with extra URLs rejected at save; link previews render from the destination's own metadata (no cloaking); prohibited-content policy mirrors the ads/broadcast bar (adult, gambling, misleading earning claims refused at template review).
6. Status per intent: `queued|held_quota|held_quiet_hours|sent|window_lapsed|failed|suppressed` visible per trigger event; creator sees reply + DM delivery separately (a sent reply with a lapsed DM is shown as exactly that, never rounded up to success).

## Data / API touches

- New tables: `dm_templates{automation_id, reply_text, dm_text, link_url, status (draft|approved|rejected)}` (template review at save; edits re-review) + `send_intents{intent_id, automation_id, commenter_ref, reply_status, dm_status, held_reason?, sent_at?}` (idempotent on `(automation_id, comment_id)` — replays never double-send). Tokens reach the send consumer via opaque leases only (vault pattern from `publish/accounts/`). All PG via `worker/db/*`.
- Queue: delivery intents flow through a dedicated queue (separate from `POST_QUEUE` fan-out) with per-account pacing keys; consumers perform quota check → window check → suppression check → send, in that order.
- Endpoints: template CRUD + preview (owner), intent status reads (owner, per-automation), ops pause/reinstate + template reject (audited).

## Acceptance criteria

- One-link drill: template with zero or two+ links rejected at save; sent DMs contain exactly the approved link (provider-mock inspection). *GWT: Given a template with zero or two-plus links or an approved send; When saved or inspected via provider-mock; Then invalid templates are rejected at save and sent DMs contain exactly the approved link.*
- Window drill: intent older than 24 h marks `window_lapsed` with no send attempt; fresh re-comment creates a new intent normally. *GWT: Given an intent older than 24 h or a fresh re-comment; When the window check runs; Then stale intents mark `window_lapsed` with no send and re-comments create a new intent normally.*
- Quota drill: contact 101 in the free year holds with upgrade notice; pro monthly reset verified against fixtures; already-contacted re-comments do not inflate counts. *GWT: Given free-year and pro-monthly quota state; When the 101st contact or a re-comment arrives or the month resets; Then over-quota holds with upgrade notice, resets verify against fixtures, and re-comments do not inflate counts.*
- Replay drill: duplicate queue delivery of one intent sends once (idempotency key holds). *GWT: Given one intent delivered twice via the queue; When the consumer processes both; Then exactly one send occurs via the idempotency key.*
- Ban-safety drill: simulated platform error spike auto-pauses within budget; quiet-hours holds release after 08:00; opt-out recipient never messaged again across automations. *GWT: Given an error spike, quiet-hours holds, or an opt-out recipient; When delivery evaluates; Then spikes auto-pause within budget, holds release after 08:00, and opt-outs are never messaged again.*

## Out of scope

- Trigger matching and watcher (see `triggers/comment-triggers.md`); contact storage, suppression states, lead export (see `contacts/contact-lists.md`).
- Token vault mechanics (`publish/accounts/social-connections.md`); broadcast campaigns (`monetize/marketing/`); multi-step drip sequences (explicitly never in v1).

## Open questions

- Q1: Template review posture — automated checks only at launch, or human review queue for link-heavy templates?
- Q2: Quiet-hours default timezone (recipient-local inference vs creator-local fixed) — which fails safer?
