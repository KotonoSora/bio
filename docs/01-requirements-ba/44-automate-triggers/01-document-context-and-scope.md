# Automate Triggers - Document Context and Scope (feature 44)

Phase 1 BA · Feature 44 of 53 — publish/automate 4/4. The watcher listens for exact-keyword and any-comment triggers; delivery answers with one public reply plus one DM containing one link, inside the platform window, with quota enforced pre-send. Sources: `automate/triggers/comment-triggers.md`, `automate/delivery/dm-delivery.md`, `platform/delivery/publish-automate.md` (FR-5). Implemented by `docs/02-architecture-techlead/44-automate-triggers/`. Service: publish-service (port 8089, `publish_db`, Redis db 11).

## Context

This is the highest-ban-risk surface in the major: unsolicited-DM enforcement kills connected accounts, so restraint is the feature — one link, no follow-ups, no drip. Instagram Business/Creator accounts only (API limitation, stated pre-config). Matching is the consent boundary: a comment is the user's raised hand, so matching must be exact, auditable, and scoped to media the creator chose — no fuzzy matching that DMs strangers over a typo. The legacy worker model (`automations` + watcher + `POST_QUEUE`) maps to the mandated stack: verified webhook intake, Redis Streams with consumer groups, in-service watcher and delivery consumers.

## Objectives

| ID | Objective |
|---|---|
| OBJ-01 | Exact, auditable matching: keyword (single/multi-word phrases) and any-comment catch-alls, scoped per media or account-wide |
| OBJ-02 | Rules server-side enforced: quotas, collision rejection, delay ≥ 60 s, healthy IG connection with comment-manage + DM-send scopes |
| OBJ-03 | Delivery restraint: one public reply + one DM with one link, inside the 24 h window, then silence until a fresh comment |
| OBJ-04 | Pre-send gating: contacts quota, suppression, quiet hours, fire caps, and pacing checked before sending — never apologized for after |
| OBJ-05 | Ban-safety: per-post fire caps with overflow queue, per-account pacing, burst-abort on error spikes, template review, transparency copy |
| OBJ-06 | Observable delivery: per-intent status with honest reasons; trigger log shows every observed comment's match decision |

## Scope

In scope (SC-01..SC-07): `automations`/`trigger_log`/`dm_templates`/`send_intents` (V4); IG comment webhook intake (verified, queue-mediated); match evaluator; rule builder + preview; delivery pipeline (reply + DM); quotas + caps + pacing + quiet hours; burst-abort; template checks + review queue; ops health/reinstate; contact capture handoff (43).

Out of scope: other networks' comment automations (v1 is Instagram-only per API); drip/follow-up sequences (never in v1); broadcast campaigns (gated 48–53); contact list management (43); publishing (41/42).

## Personas and permissions

| Role | CRUD Permissions | Description |
|---|---|---|
| Creator (owner) | C/R/U/D own automations + templates; R trigger log + intents; U pause/resume | Creates keyword rules, toggles catch-alls, previews, watches delivery |
| Commenter (IG user) | C consent via comment; U own state via `OPTOUT` | Receives exactly the promised reply + DM, then silence |
| Ops | R health; U reinstate auto-paused automations; U template review decisions | Recovers from spikes; guards the ban-risk surface |
| System (watcher/delivery) | U intent states via guarded transitions | Bounded, idempotent, gated sends |

## Open-question resolutions

| # | Question (source) | Resolution |
|---|---|---|
| Q-01 | Multi-word phrases in v1 (triggers Q1) | Allowed as exact phrases (case-folded, trimmed); no fuzzy/typo matching exists in v1 |
| Q-02 | Fire-cap values (triggers Q2) | Fixed platform constants in `publish.policy` (`fireCapPerHour` default 60, range 10..200 per automation+media); not creator-tunable |
| Q-03 | Template review (dm Q1) | Automated checks gate at save (one link, https + destination allowlist, prohibited-content classes); reported templates enter a human review queue with pause-until-decided |
| Q-04 | Quiet hours (dm Q2) | Creator-local fixed default 22:00–08:00 (`publish.policy`); recipient-local used only where the platform provides timezone — it does not for IG DMs, so creator-local fails safer |
| Q-05 | Comment edits/deletes | The first observed version acts; one intent per comment; edits never re-fire; a delete with a pending intent cancels before send |
| Q-06 | Stealth/hidden DMs | Prohibited: transparency copy required; hidden sending is a spec violation (template checks + support audit) |

## Constraints carried

`delay_s` ≥ 60 (minimum, enforced server-side); one link slot per DM template (https + allowlist); no follow-ups after the first DM; 24 h human-agent window (no retry after lapse); contact capture only on accepted sends (43); opaque comment/user refs only; zero token material anywhere in this feature.
