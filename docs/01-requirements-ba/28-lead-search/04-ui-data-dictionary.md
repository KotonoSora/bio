# Lead Search - UI Data Dictionary (feature 28)

Phase 1 BA · Contacts workspace (dashboard); unsubscribe landing page.

## Search and filters

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Search input | search input | no | min 2 chars when used; typo-tolerant; exact-first ranking | empty; recent queries offered (last 20) |
| Source filter | multi-select chips | no | `form|checkout|import|ops|automation` | all |
| Status filter | multi-select chips | no | `pending|active|unsubscribed|suppressed|bounced|complained` | all |
| Consent filter | select | no | `lifecycle|marketing` | all |
| Date range | date range picker | no | captured date; inclusive bounds | all time |
| Form filter | select | no | creator's own form blocks | all |
| Tag filter | select | no | creator's own vocabulary | all |
| Count line | text | - | "N contacts" using export-identical predicates | live |
| Recent queries | dropdown | no | per-account, last 20, private | collapsed |

## Results and detail

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Result row | table row | - | email, name, status badge, consent chip, tags, source, captured time, unread dot | newest captured first |
| Detail panel | side panel | - | assembled timeline with actor + timestamp per entry | - |
| Note composer | textarea + button | - | ≤ 2 000 chars; append-only; sanitized | empty |
| Tag editor | combo box | - | pick from vocabulary or create until cap 50; tag ≤ 40 chars | - |
| Suppress dialog | modal | reason required | ≤ 200 chars; states permanence | closed |
| Re-invite button | button | - | hidden for `suppressed`; 3/day per lead | - |
| Read toggle | checkbox | - | flag only; evented | from row state |

## Export

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Selection mode | radio | yes | "filtered set" or "checked rows" | filtered set |
| Count | text (read-only) | - | frozen at enqueue; matches exported rows | from count line |
| Job status | badge | - | `queued|running|done|failed` | - |
| Download link | link | - | owner-scoped grant; 15-min expiry shown | hidden until done |
| Quota notice | text | - | shown near quota (20/day, 2 concurrent) | hidden |

## Unsubscribe page (email link)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Context | text | - | creator name + masked email | - |
| Unsubscribe | button | yes | explicit POST; the only state change | - |
| Expired state | notice | - | explains link expiry (72 h) | - |
