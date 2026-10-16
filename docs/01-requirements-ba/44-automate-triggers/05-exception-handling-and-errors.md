# Automate Triggers - Exception Handling and Errors (feature 44)

Phase 1 BA · Problems follow RFC 9457 (`code` field). Comment bodies never appear in error payloads; refs stay opaque.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Webhook signature invalid | Intake | 401; nothing read before verification; metric + clamp | None (rail-facing) |
| Replayed webhook (same event ref) | Intake | Anti-replay persist-first; original outcome returned; zero side effects | None |
| Keyword overlaps an active rule in scope | API (save) | 409 `keyword_collision` with the conflicting rule named | "This phrase is already used by another rule on this scope." |
| Rule quota exceeded (free 3/4) | API (save) | 403 `rule_quota_exceeded`; pause-oldest path offered | "Your plan allows {n} active rules — pause one or upgrade." |
| DM template with zero or two links | API (save/edit) | 422 `template_invalid` with the exact violation | "The DM needs exactly one link." |
| Non-https / non-allowlisted destination | API | 422 `template_invalid` (destination class) | "That link destination isn't allowed." |
| Prohibited content class (stealth/hidden sending, misleading claims) | API | 422 `template_invalid`; refused with reason class | "This template isn't allowed: {class}." |
| Connection unhealthy / scopes lost | System | Automation suspends; notice; paused intents hold | "Reconnect Instagram — the automation is paused." |
| Comment from creator or platform-blocked author | Watcher | `self_comment` / `blocklisted` decisions logged; no intent | None |
| Comment edited after first observation | Watcher | First version stands; edits never re-fire; one intent per comment | None |
| Comment deleted with pending intent | Watcher | Intent cancelled before send; nothing sent | None |
| Fire cap tripped | Delivery | `held_cap` + overflow queue + creator warning; releases in order | "Sends are paced — {n} queued." |
| Quiet hours | Delivery | `held_quiet`; releases at window end in order | "Held until 08:00 — quiet hours." |
| Contacts quota exhausted | Delivery | `held_quota` (43's counting function); upgrade-or-wait | "Contact limit reached — upgrade or wait." |
| Suppressed contact matches | Delivery | `skipped_suppressed`; nothing sent, nothing captured | None |
| 24 h window lapsed before send | Delivery | `window_lapsed`; no retry exists | "Too late to reply — Instagram's window closed." |
| Transient send failure | Delivery | Bounded retry; `failed` if exhausted with class | "Delivery failed — retrying." then honest failure |
| Permanent send failure (policy/block) | Delivery | `failed` terminal with reason; no futile retries | "Instagram refused the message: {reason}." |
| Error spike (> 20% over 10 min) | Monitor | Auto-pause + ops page + creator notice | "Paused automatically after errors — we're on it." |
| Template reported | Review | Pause-until-decided; ops queue | "Under review — sending is paused." |
| Ops reinstate without reason | API | 422 `validation_failed` (reason mandatory) | "A reason is required." |
| Cross-creator rule/intent access | API | 403/404 by owner scoping; zero rows | "Not found." |
