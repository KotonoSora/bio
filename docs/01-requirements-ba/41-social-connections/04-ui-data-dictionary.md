# Social Connections - UI Data Dictionary (feature 41)

Phase 1 BA · Surfaces: creator connections page (settings), composer target picker (consumed by 42), ops support read. Token columns never appear on any surface, export, or debug view.

## Creator surfaces

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `networkCard` | Card per network | Yes | Seven fixed cards; IG copy states Business/Creator-only requirement pre-config | Personal IG connect attempt refused with explanation |
| `accountRef` | Text | Yes | Provider handle/label (non-secret); masked middle where long | Read-only |
| `healthChip` | Status chip | Yes | Enum: `healthy, expiring, expired, revoked, error` | Color + label; dead states get reconnect CTA |
| `capCounter` | Meta | Yes | "n of cap per platform" (free 1 / pro 10 from `publish.quotas`) | Connect disabled at cap with upgrade path |
| `connectAction` | Button | Yes | Starts OAuth; rate-limited | Disabled at cap or during an in-flight dance |
| `reconnectAction` | Button | On dead states | Fresh OAuth dance | Replaces connect for existing rows |
| `disconnectAction` | Button + confirm | Yes to act | Confirm names the queued-cancel consequence | Deletes row + cancels queued targets + notice |
| `expiresAt` | Meta | When known | Provider-reported token expiry | Shown as relative time |
| `lastHealthyAt` | Meta | Yes | Last successful refresh/check | Ops + creator trust signal |
| `scopesSummary` | Meta (collapsed) | Yes | Plain-language list of granted publish scopes | No raw scope strings |

## Composer consumption (42)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `targetPicker` | Checkbox list | Yes | Only `healthy|expiring` accounts selectable; dead rows unselectable with inline reconnect | Default = all healthy ("Post to All") |
| `deadTargetNotice` | Inline notice | On dead selection attempt | Names the account + reconnect path | Never a silent skip |

## Ops support read

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `healthTable` | Table (restricted view) | On load | Columns: profile ref (masked), network, account ref, health, expires, last check | Token columns do not exist in the view (DB-level exclusion) |
| `revokeLog` | Table (read-only) | On load | Detected revokes + disconnect events | Aggregates + ids only |
| `rotationStatus` | Card | On load | Active key version, re-wrap progress %, last rotation date | Warn > 80 days since rotation |

## Validation and behavior notes

- No surface ever renders, copies, or exports token values; the restricted view makes token-column selection impossible for ops queries.
- All creator mutations are session + CSRF protected; connect-start is rate-limited (redirector abuse).
- Health copy is honest: `expiring` warns but stays selectable; `expired|revoked|error` block publishing and say why in plain language.
- Empty state: zero connections → guidance card for the seven networks with plan cap context.
