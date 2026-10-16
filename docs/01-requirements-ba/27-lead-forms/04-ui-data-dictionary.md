# Lead Forms - UI Data Dictionary (feature 27)

Phase 1 BA · Capture surface (SSR block), form config (dashboard block editor), dashboard inbox.

## Form block (public capture surface)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Headline | text | yes | ≤ 120 chars (creator copy) | - |
| Offer copy | text | no | ≤ 280 chars: what the visitor gets | - |
| Email | email input | yes | native keyboard + autofill; format validated inline; server re-validates | autofocused |
| Name | text input | config-dependent | ≤ 80 chars | off by default |
| Phone | tel input | config-dependent | ≤ 20 chars | off by default |
| Note | textarea | config-dependent | ≤ 500 chars | off by default |
| Marketing consent box | checkbox | - | default UNCHECKED; unchecked = lifecycle-only | unchecked |
| Submit | button | yes | label from config (≤ 40) | - |
| Success state | inline notice | - | creator `successMessage` (≤ 200), no redirect required | replaces form |
| Redirect | - | no | own page or link-block target; skipped when absent | - |
| Turnstile | widget | yes | invisible-class challenge; no third-party scripts beyond the challenge | gateway-wired |

## Form config (dashboard, block editor)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Field toggles | switches | - | name/phone/note independently; email always on | all off |
| Double opt-in | switch | - | ON by default; copy states the tradeoff | ON |
| Copy version | text (read-only) | - | server-stamped hash on every save | auto |
| Pause notice | banner | - | visible while the form is velocity-paused | hidden |

## Dashboard inbox

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Form filter | select | - | "all forms" or one block | all |
| Submission row | list item | - | email, name, source chip, status chip, unread dot, captured time | newest first |
| Status chip | badge | - | `pending|active|unsubscribed|suppressed|bounced|complained` | derived |
| Disposable flag | badge | - | shown when `disposable_flag` (hygiene hint, not a block) | hidden |
| Detail link | link | - | opens feature 28's contact view | - |
| Export link | link | - | opens feature 28's export flow | - |

## Confirm page (email link target)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Context line | text | - | form headline + masked email | - |
| Confirm | button | yes | explicit POST; the only way to activate | - |
| Expired state | notice | - | offers clean re-request (rate-limited) | - |
