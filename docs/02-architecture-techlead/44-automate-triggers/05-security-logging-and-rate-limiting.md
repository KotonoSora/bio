# Automate Triggers - Security, Logging, and Rate Limiting (feature 44)

Phase 2 TL · BA sources: `docs/01-requirements-ba/44-automate-triggers/` (BR-01..BR-18, NFR-01..NFR-15). Baseline: `docs/02-architecture-techlead/00-platform-baseline/`. Highest-ban-risk surface in the major: restraint and verification are the controls.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | Scoping: owner RLS on automations/log/templates/intents; ops sees health + review queue only; cross-creator reads zero rows | AC-10-style IDOR probes |
| S-2 | Webhook discipline: signature verified over raw bytes before any parse; anti-replay insert-first; delete/edited events handled without re-fire | AC-06, AC-09 |
| S-3 | Match integrity: normalization is case-fold + trim only (no fuzzy logic exists — grep); collision checks server-side; decisions always logged | AC-01, AC-02 |
| S-4 | Gate integrity: fixed order (43 quota → suppression → window → quiet → cap → pacing); no sender path bypasses; holds re-evaluate | AC-10 + grep gate |
| S-5 | Ban-safety: fire caps (60/h default), pacing bucket (10/min burst, 30/min sustained), quiet hours, burst-abort (> 20%/10 min, min 20 attempts), one reply + one link-DM, no drip (grep: no second-send code path exists) | AC-04, AC-05, AC-07 |
| S-6 | Template safety: one link, https + allowlist, prohibited classes refused; reported → pause-until-decided; transparency copy required (stealth refused) | AC-07, AC-12 |
| S-7 | Quota integrity: contacts via 43's single function; rules via the save-time counting function; downgrade pauses (never deletes); unique recipient counts once | AC-03 |
| S-8 | Window truth: 24 h from comment, minute precision; `window_lapsed` terminal (no retry code exists) | AC-08 |
| S-9 | Privacy: opaque refs; comment bodies never stored (decisions + refs only); zero token material; RLS owner-scoped; ops zero bodies | Data-minimization audit |
| S-10 | Credential confinement: sends resolve credentials via 41's in-process leases; stream messages carry ids only | Message-hygiene scan |

## Rate limits (gateway + app; Redis db 11 `pub:rl:*`)

| Surface | Limit | On breach |
|---|---|---|
| `POST /webhooks/instagram` | 600/min/IP + 120/min per app key | Refusal + clamp |
| Automation create/patch/delete | 60/h/user | 429 `throttled` |
| Preview | 120/h/user | 429 |
| Trigger-log / intent reads | 600/h/user | 429 |
| Ops reinstate / template decisions | 60/h/operator | 429 |
| Send pacing (enforced in delivery) | burst 10/min, sustained 30/min per account | Holds (`held_cap` / pacing) |

## Degrade matrix

| Failure | Behavior |
|---|---|
| Provider DM API down | Intents retry bounded then `failed` honestly; matching continues; no drip backlog exists |
| Redis down | Watcher + delivery halt loudly (no DB-poll fallback fork); caps rebuild from intents on recovery |
| 43's gate down | Sends refuse (fail closed); nothing sent without quota + suppression checks |
| Webhook outage | Nothing queues; on recovery, anti-replay keeps deliveries exactly-once |
| Burst monitor down | Caps + pacing still bound volume; alert after 2 missed windows |
| Notification pipeline down | Pause/abort notices queue with backlog; enforcement unaffected |

## Audit and observability

Audits (24-month): `automation.created/paused/resumed/deleted`, `template.saved/reported/decided`, `automation.auto_paused`, `rule.collision_refused`, `rule.quota_refused`, intent transitions via the append-only spine. Metrics: `matches{kind}`, `holds{reason}`, `sends`, `failures{class}`, `cap_overflow_depth`, `auto_pauses`, `review_queue_age`, `window_lapsed`. Zero comment bodies, zero token labels.

## Support answers

"Why didn't everyone get a DM?" → intent statuses: caps/quiet/quota holds with release estimates, window lapses, suppression skips — never "should have". "Did you send to a stranger?" → trigger log shows the exact matched keyword + scope; no-match cases named. "Why is it paused?" → burst-abort reason + ops status. "Why only one link?" → platform-restraint policy: one comment earns exactly what was promised. "Was this a bot?" → transparency copy is required on every template.
