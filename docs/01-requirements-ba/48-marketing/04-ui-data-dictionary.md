# Marketing Consent and Broadcasts - UI Data Dictionary (feature 48) - GATED

Phase 1 BA · GATED: no surface below may be built before the amendment. Surfaces: public preference center, in-account preferences, ops audience health, campaign composer/review, proof lookup.

## Public preference center (signed token, no login)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `classToggles` | Toggle list | Yes | One per class: transactional (fixed on, explained), lifecycle (opt-out), marketing (opt-in) | Honest consequences stated beside each |
| `unsubscribeAllAction` | Button | Yes | One click; effective immediately | Beats the complaint form to the punch |
| `confirmationReceipt` | Note | After save | States exactly what changed + when | The change itself is confirmable evidence |
| `expiredTokenState` | Page state | On expiry | Neutral page; request a fresh link | No enumeration, no address echo |

## In-account preferences

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `prefsMirror` | Toggle list | Yes | Same model as public center | Values identical both places |
| `proofRequest` | Button | Optional | Exports own consent history | Rate-limited; audit-logged |

## Ops audience health

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `healthStats` | Stat row | On load | Active share, suppressed/bounced/complained counts, complaint rate vs threshold | Threshold is config-owned |
| `importDialog` | Upload + report | Yes to act | CSV validated; row-level report (imported/duplicate/invalid/suppressed) | Never loosens a stricter state |
| `exportAction` | Button | Yes to act | Consent columns included; audited; rate-limited | Suppressed full data needs a support case |
| `proofLookup` | Search + history | On demand | Address → full consent history (grants, withdrawals, sources, copy versions) | Export within minutes; audited |

## Campaign composer / review / stats

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `subjectField` / `preheaderField` | Input | Yes | Honest subject rules; no deceptive RE:/FWD: | Review checklist states them |
| `bodyEditor` | Sanitized template editor | Yes | No raw HTML paste, no scriptable content | Shared template system |
| `testSendAction` | Button | Yes to act | Operator addresses only | Proves rendering before review |
| `audienceCount` | Stat | At review | Exact eligible count by query; exclusions applied | Operator confirms the exact count |
| `confirmSendAction` | Two-step confirm | Yes to act | No single action sends; send-now only (no scheduling control exists) | Paused mid-batch halts within seconds |
| `statsPanel` | Table | After send | Delivered/open/click/bounce/complaint | Reconciled from idempotent webhook events |
| `pausedBanner` | Banner | On breach | Complaint-rate breach: sends paused, ops paged | Transactional delivery shown unaffected |

## Validation and behavior notes

- v1 exclusions are absent, not hidden: segment, schedule, and A/B controls do not exist (attempts 404/422, not silent no-ops).
- Suppression states render as terminal with the re-consent path explained — never a "try again" button.
- Copy: "Off means no promo emails. Receipts and security notices continue." / "One click and you're out — no login needed."
- Empty states: no campaigns yet → compose CTA; no audience → capture points explained (27's forms, checkout).
