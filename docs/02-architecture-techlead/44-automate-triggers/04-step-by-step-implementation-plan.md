# Automate Triggers - Step-by-Step Implementation Plan (feature 44)

Phase 2 TL · Package `com.app.publish.automate`. BA sources: `docs/01-requirements-ba/44-automate-triggers/`. Order: schema + match evaluator first; delivery gates second (ban-safety cannot ship without them); review/ops third.

## Tasks

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `V4__automate_triggers.sql`: four tables + indexes + RLS + drills | 43's V3 | V4 drills green |
| T-02 | BE | `WebhookIntake`: signature verification, anti-replay persist-first, enqueue to `automate.watch`, delete-event handling | T-01 | AC-06, AC-09 |
| T-03 | BE | `MatchEvaluator` + `WatcherConsumer`: normalization, scope, self/blocklist, collision-ordered first match, trigger log, intent creation | T-01 | AC-01, AC-02 |
| T-04 | BE | Rule save path: collision check, rule quota (single counting function), connection health/scopes; downgrade pause newest-first | T-03 | AC-02, AC-03 |
| T-05 | BE | `TemplateChecker` + save/edit re-checks + report → `pending_review` pause; ops decision routes | T-01 | AC-07, AC-12 |
| T-06 | BE | `DeliveryConsumer`: fixed gate order (43 quota → suppression → window → quiet → fire cap → pacing), reply-then-DM, capture backfill, failure classes | T-03 | AC-10, AC-11 |
| T-07 | BE | `CapBuckets` + `BurstMonitor`: Redis counters with rebuild-from-intents, token bucket, auto-pause + event + page | T-06 | AC-04, AC-05 |
| T-08 | BE | Quiet hours from automation timezone; release-in-order tick ±60 s | T-06 | AC-04 |
| T-09 | BE | `WatchPort`/delivery seams + DLQ + replay-safety review | T-06, T-07 | AC-09 |
| T-10 | FE | Rule builder: kinds, keyword tags, scope picker, delay, templates + live preview, quota meter, enable toggle | T-04, T-05 | UI flows complete |
| T-11 | FE | Trigger log + intent list: decisions, hold reasons, release estimates, fire-cap meter | T-06 | AC-10 |
| T-12 | FE | Ops: health cards, reinstate flow, template review queue + decisions | T-05, T-07 | Ops flows complete |
| T-13 | BE/DevOps | Cross-edits: baseline 01 public paths (`POST /webhooks/instagram`); MIGRATION at batch tail | T-02 | Diff review green |
| T-14 | BE/DevOps | Drills: match determinism, collision, quotas, storm/ban-safety, spike timing, webhook battery, one-link, window, replay, gate order, opt-out, transparency | T-01..T-12 | AC-01..AC-12 |
| T-15 | DevOps | Metrics + alerts: matches by kind, holds, failures by class, overflow depth, auto-pauses, review queue age | T-06..T-14 | NFR-15 |

## Sequencing notes

1. T-01/T-03 first: no rule may exist before the evaluator is deterministic and logged.
2. T-06's gate order is frozen — review against 43's eligibility owner; a bypass is a release blocker.
3. T-07 is the ban-safety core: storm drill (200 matches) and Redis-wipe rebuild are release gates.
4. T-05's review queue ships with pause-until-decided — never a queue that keeps sending.
5. Feature gate: E4 scope audit — zero publish tables/jobs/endpoints in E0–E3 images (release-gate grep).

## Definition of done

AC-01..AC-12 green; gate-order + window-terminal + template-check greps green in CI; full-tree validation (word caps + backticked path resolution) green; `MIGRATION.md` row 44 flips to `done` at the batch tail with `automate/triggers/comment-triggers.md` and `automate/delivery/dm-delivery.md` deleted.
