# Consultations and Tips - UI Data Dictionary (feature 25)

Phase 1 BA · Availability editor, buyer slot picker, booking list, tip jar config, tip modal, wall, moderation queue.

## Availability editor (dashboard)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Timezone | select | yes | default `Asia/Ho_Chi_Minh` | VN default |
| Weekly window | time range row | - | start < end; multiple per weekday | empty |
| Override row | date + closed/window | - | date in future 60 days | closed type |
| Buffer defaults hint | text | - | shown from session products | informational |

## Session product (dashboard)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Title | text input | yes | ≤ 120 chars | - |
| Duration | select | yes | 15-240 min | 30 |
| Buffer before/after | numeric inputs | no | 0-120 min each | 0 |
| Meeting link | url input | yes | `https:` only | - |
| Price (VND) | numeric input | yes | > 0 integer | - |

## Buyer slot picker (product page + SPA)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Slot list | button grid | - | open slots only; buyer-local times + creator-tz hint | 60-day horizon |
| Hold timer | countdown | - | 10 min TTL, visible | starts on hold |
| Policy block | text + checkbox | yes | acknowledgment required pre-payment | unchecked |
| Confirmation card | summary | - | both timezones + link + signed tokens | - |

## Booking list (both sides)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Status chip | badge | - | `held|confirmed|cancelled|completed|no_show|expired` | derived |
| Slot | text | - | creator + buyer timezones | - |
| Actions | menu | - | reschedule/cancel per policy window; no-show (creator) | - |

## Tip jar config (dashboard)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Presets | chip editor | yes | 3-6 entries, each within floor/ceiling | 10k/20k/50k |
| Floor / ceiling | numeric inputs | yes | defaults 10k / 5M; floor ≤ ceiling | defaults |
| Wall toggle | switch | - | off by default | off |
| Tip button | preview | - | shows on shop page + product page | - |

## Tip modal (fan)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Amount | preset buttons + custom input | yes | within floor/ceiling; VND integer | first preset |
| Message | textarea | no | ≤ 280 chars; links/markup stripped | empty |
| Display name | radio | no | named / anonymous | named |
| Email | email input | guest only | mandatory for guests | - |
| Turnstile | widget | yes (guest) | challenge + throttle | - |

## Moderation queue + wall

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Message row | list item | - | pending filter default; approve/reject buttons | pending |
| Thank mark | button | - | owner-only, feed organization | unthanked |
| Wall section | list (public) | - | approved only; plain text; name choice honored | hidden when off |
