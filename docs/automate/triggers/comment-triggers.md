# Comment Triggers (keyword + any-comment rules)

PLAN ref: P4-3 (`automations{kind: comment|dm, match, reply_templates, delay_s}` + watcher). P4 scope — do NOT build in P1–P3. Instagram Business/Creator accounts only (API limitation, stated pre-config); contact capture downstream in `contacts/contact-lists.md`, sending in `delivery/dm-delivery.md`.

## Purpose

Define what the watcher listens for: exact-keyword triggers ("comment TOUR → get the ticket link") and catch-all any-comment triggers, each bound to one automation with explicit match rules. Triggers are the consent boundary — a comment is the user's raised hand — so matching must be exact, auditable, and scoped to the media the creator chose. No fuzzy matching that DMs strangers over a typo.

## Users

- Creator: *As a Creator, I want to create keyword rules per post, toggle any-comment catch-alls, and preview what a commenter will receive so that matching stays exact, auditable, and scoped.* Creates keyword rules per post (or account-wide), toggles any-comment catch-alls, previews what a commenter will receive.
- Commenter (Instagram user): *As a Commenter, I want to comment and get a public reply plus exactly one DM so that my raised hand earns one reply with no drip.* Comments, gets a public reply and exactly one DM — no follow-up drip, no subscription.
- Ops/abuse reviewer: *As an Ops/abuse reviewer, I want to inspect match logs when a creator is reported so that spammy automation is reviewable.* Inspects match logs when a creator is reported for spammy automation.

## Functional requirements

1. Trigger kinds (closed set): `keyword` (case-insensitive exact token match on the comment text, e.g. TOUR; multi-keyword rules supported as an OR list) and `any-comment` (fires on every top-level comment not from the creator). One automation owns one kind; a post may carry multiple keyword automations with disjoint keyword sets (overlap rejected at save with the collision named).
2. Scope: triggers bind to selected media ids (per-post) or account-wide (any-comment typically); binding requires a healthy IG connection from `publish/accounts/social-connections.md` with comment-manage + DM-send scopes. Triggers on media the connection loses access to suspend automatically with creator notice.
3. Match semantics: keyword match on normalized text (trimmed, case-folded, single-token; multi-word phrases allowed as exact phrases); creator's own comments and replies never trigger; edited comments re-evaluate once (delete-then-DM races resolved by acting on the first observed version, logged as such). `delay_s` per automation (minimum 60 s default — instant DMs read as bots and trip platform spam filters).
4. Quota model (adapted to our free/pro ladder — the middle Plus rung 5/10 from link.me is intentionally not copied): keyword automations — free 3 active, pro unlimited; any-comment automations — free 4 active, pro unlimited. Counts are active-rules, not lifetime; over-cap saves refused with the trim-or-upgrade path. Counts enforce server-side at save and at watcher-evaluation time (downgrade mid-campaign pauses over-cap rules newest-first, never silently).
5. Safety rails: per-post per-hour fire caps (platform spam protection — a viral post does not DM ten thousand people in an hour; overflow queues with backoff, creator warned); blocklist (creator-defined ignored keywords/users) evaluated before any trigger; trigger logs retained for dispute review.
6. Transparency: automation target posts should carry a pinned comment or caption line stating the trigger ("Comment TOUR and I'll DM you the link") — platform-provided suggested copy, creator-editable. Stealth DM automation (no public disclosure) is a review violation.

## Data / API touches

- New table `automations{automation_id, profile_id, kind (keyword|any_comment), keywords[], scope (media_ids[]|account), account_id FK, reply_template_ref, dm_template_ref, delay_s, status (active|paused|over_quota|suspended)}` + `trigger_log{automation_id, media_id, comment_id, commenter_ref, matched_keyword?, fired_at}` (append-only evidence). Standard `user_id` + RLS; all PG via `worker/db/*`.
- Watcher input: IG comment webhooks for subscribed media/accounts (verified per standard webhook discipline) → match evaluation in a queue consumer (never in the webhook handler); evaluation emits send intents to the delivery queue (see delivery doc) subject to quota pre-checks.
- Endpoints: automation CRUD + preview (owner, quota-checked), trigger-log reads (owner, per-automation), ops suspend/reinstate (audited).

## Acceptance criteria

- Match drill: exact/folded/phrase keywords fire; near-miss typos, creator self-comments, and blocklisted users do not (fixture matrix green). *GWT: Given exact/folded/phrase keywords vs typos, self-comments, and blocklisted users; When matching evaluates; Then only exact matches fire and the fixture matrix stays green.*
- Collision drill: overlapping keyword sets on one post rejected at save naming both automations. *GWT: Given overlapping keyword sets on one post; When saving; Then the save is rejected naming both automations.*
- Quota drill: 3rd/4th free rules save, next refused with upgrade path; downgrade pauses newest-first; watcher skips paused/over-quota rules even under queued backlog. *GWT: Given free-rule counts and a downgrade; When saving or the watcher evaluates; Then the 3rd/4th save, the next is refused with upgrade path, downgrades pause newest-first, and the watcher skips paused/over-quota rules.*
- Spike drill: viral-post comment flood obeys per-hour caps with queued overflow + creator warning (no platform-ban-shaped traffic burst). *GWT: Given a viral-post comment flood; When fire caps apply; Then per-hour caps hold with queued overflow plus creator warning and no ban-shaped burst.*
- Webhook drill: tampered comment events rejected (401), no intent emitted; replayed events dedupe to one intent. *GWT: Given tampered or replayed comment events; When the webhook consumer evaluates; Then tampered events are rejected 401 with no intent and replays dedupe to one intent.*

## Out of scope

- Reply + DM sending and contact quotas (see `delivery/dm-delivery.md`); contact lists and lead capture (see `contacts/contact-lists.md`).
- OAuth connections and token vault (`publish/accounts/social-connections.md`); broadcast campaigns (`monetize/marketing/`); agency-managed automations (P4 agency scope).

## Open questions

- Q1: Multi-word/phrase keywords in v1, or single-token only until match-abuse evidence demands phrases?
- Q2: Per-hour fire-cap values (platform-safe defaults per network behavior) — fixed platform constants or creator-tunable within a ceiling?
