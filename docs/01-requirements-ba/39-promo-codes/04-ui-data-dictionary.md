# Promo Codes - UI Data Dictionary (feature 39)

Phase 1 BA · Surfaces: checkout code field, billing-page credits row, ops campaign console, ops batch console, ops goodwill form. All values server-computed; campaign internals never reach buyers.

## Checkout: code field (33's create surface)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `codeInput` | Text input | No | Uppercase-normalized client-side for display; server normalizes again; keyboard reachable | Optional unless the buyer has a code |
| `applyButton` | Secondary button | No | Calls validate; never grants | Shows the line-item math on success |
| `mathLine` | Text | On success | "{catalog} − {discount} = {charge} VND" in figures + words (VN) | Plain language; no dark patterns |
| `discountKind` | Label | On success | `percent`/`fixed_vnd` show discount copy; `term_extend` shows "+{days} days after payment" | - |
| `errorMessage` | Inline error | On failure | One neutral message; only expiry states itself | Announced to screen readers |
| `replacementConfirm` | Dialog | When replacing a code | Explicit confirmation before the second code applies | No silent stacking |
| `turnstile` | Widget | Guest only | `challenge_failed` inline | Session users: throttles only |

## Billing page: credits row

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `unconsumedDays` | Text | Yes | Integer days from the owner's ledger; never cash-formatted | "{n} bonus days will apply to your next period" |
| `appliedDays` | Line | When present | Days already applied, with period end | Read-only |

## Ops: campaign console

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `createForm` | Form | Yes | Name, kind scope, value, budget cap, max redemptions, per-user limit, target, window; `ends_at > starts_at` enforced | Unbounded submission impossible |
| `launch` / `pause` / `resume` / `end` | Buttons | Per status | Audited with actor + reason; illegal moves 409 `campaign_conflict` | - |
| `burndown` | Panel | Yes | `redeemed_vnd` vs `budget_cap_vnd`, `redemption_count` vs `max_redemptions`, percentages; 80%/100% markers | Finance-visible readout |
| `autoPauseBanner` | Notice | When auto-paused | Shows the system actor + reason + time | Buyer impact: new redemptions blocked |

## Ops: batch console

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `generateForm` | Form | Yes | Campaign, channel?, count, use limit, per-user limit, expiry ≤ campaign end; `invite_only` requires per-invitee binding | Atomic per batch |
| `exportPayload` | Download | Once | Plaintext list returned exactly at creation; closing without saving warns once | Acknowledge stamps the audit |
| `ackExport` | Button | After export | Sets `export_acked_at`; further export attempts 409 `export_acked` | - |
| `voidUnredeemed` | Destructive button | Yes | Requires reason; audited with affected count; redeemed codes never included | - |
| `stats` | Panel | Yes | Generated / active / redeemed / voided / expired counts; channel aggregates, zero PII | Partner-safe output |

## Ops: goodwill form

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `userId` | Read-only | Yes | Prefilled from the support trace | - |
| `days` | Number input | Yes | 1..configured cap; absent config -> form disabled with `promo_config_absent` copy | No defaults invented |
| `reason` | Textarea | Yes | Non-empty, <= 500 chars; audited | - |
| `issue` | Primary button | Yes | Ops role enforced server-side | Ledger row + audit |
