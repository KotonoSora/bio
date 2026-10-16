# Verification Badge and Follower Display - UI Data Dictionary (feature 07)

Phase 1 BA · React 19.3 components for `/app/trust` plus the ops-only review route. Public badge/total rendering is SSR-owned by feature 10; the same `VerifiedBadge` SVG component is shared. Topology: `docs/02-architecture-techlead/07-verification-and-followers/01-architecture-and-tech-stack.md`.

## Screen: Trust dashboard `/app/trust`

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| VerificationCard | card | - | renders one of 5 states with the exact copy set | state from `verify-status` |
| StatusChip | badge | - | `none`, `pending`, `verified`, `rejected`, `revoked`; explanatory popover | - |
| RequestButton | primary button | - | hidden while `pending`/`verified`; re-apply entry on `rejected` | "Request verification" |
| EvidenceFileInput | file input | Yes | single PDF/PNG/JPEG, <= 10 MB; progress bar on upload | empty |
| PendingTimeline | stepper | - | submitted -> under review; polls every 60 s while pending | - |
| VerifiedBadgePreview | shared SVG component | - | renders only when `status=verified`; identical asset to SSR | hidden otherwise |
| RereviewNotice | static text | - | exact copy: "Re-review required after rename - badge hidden meanwhile." | shown when rename-flipped |
| RevocationNotice | static text | - | exact copy: "Verification was revoked by platform review." | shown when `revoked` |
| SocialsEditor | card | - | one row per network; add/remove rows | current rows |
| NetworkSelect | select | Yes | Instagram, TikTok, YouTube, generic URL | current network |
| HandleInput | text input | Yes | per-network pattern; no markup; server verdict final | current handle |
| UrlInput | url input | Yes | `https:` only, <= 2048 chars | current URL |
| SelfCountInput | number input | No | 0 to 100,000,000; cap hint shown as `max 100,000,000` | current value |
| JumpReviewNotice | amber inline notice | - | "Count under review - the value is still shown." (count applied) | hidden |
| StaleChip | chip | - | `last updated <relative> (stale)` on refresh failure | hidden |
| BreakdownTooltip | tooltip | - | per-network values + `self-reported` / `auto` labels | on hover/focus |
| RefreshButton | secondary button | - | disabled while pending; `429` -> cooldown text | "Refresh counts" |

## Route: Ops review `/app/ops/trust` (role-gated; invisible to creators)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| QueueTable | table | - | rows: profile, status, age, evidence link, history excerpt; 403 shows no queue data | role-gated |
| EvidenceLink | link + viewer | - | opens sandboxed viewer via short-lived download grant; never inline render | disabled without role |
| DecidePanel | form | - | approve / reject / revoke | - |
| ReasonTextbox | textarea | Yes | mandatory client-side AND server-side; <= 500 chars | empty |
| RevokeConfirm | text input | Yes for revoke | typed confirmation phrase required | empty |
| AuditExcerpt | list | - | last decisions with actor + reason | read-only |

All mutations use session cookie + CSRF; expired session mid-decide preserves the reason text and resumes after re-login. No evidence bytes ever enter client state (keys/URLs only; grep-gated).
