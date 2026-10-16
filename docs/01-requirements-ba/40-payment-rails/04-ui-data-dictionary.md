# Payment Rails - UI Data Dictionary (feature 40)

Phase 1 BA · Surfaces: buyer confirming states (return page + order status), ops rail console (exceptions queue, dispute cases, verify action, rail health). Enforcement: `docs/02-architecture-techlead/40-payment-rails/`. Buyer copy is rail-neutral; rails themselves are never named in refusal copy.

## Buyer surfaces

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `orderStatus` | Status chip | Yes | Enum: `pending, authorizing, paid, expired, cancelled, failed, refunded, disputed, chargeback_reversed` | Rendered from server status only; never client-inferred |
| `confirmingCopy` | Text | Yes | Rail-family variants: redirect "Confirming with your provider…", bank "Waiting for your transfer…", wallet "Contacting your provider…" | Distinct copies prevent wrong retry behavior |
| `pollInterval` | Internal | Yes | 5 s while `pending|authorizing`; stops on terminal | Backoff x2 after 2 min; max 30 s |
| `railReturnNotice` | Banner | No | Return URL seen but verify not yet landed | "Payment received — confirming…" (never claims success) |
| `paidNotice` | Banner | On terminal success | Only after server `paid` | Receipt link (37) + entitlement confirmation |
| `failureCopy` | Banner | On `failed` | Symmetric across rails and classes (BR-24) | "Payment didn't complete." + retry action |
| `expiredCopy` | Banner | On `expired` | Bank rails: notes late-transfer grace exists (support can help) | No promise of auto-credit |
| `disputeNotice` | Banner | On `disputed` | Honest: payment under review; access continues until resolution | No entry in financial history yet |

## Ops rail console

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `exceptionsList` | Table | On load | Columns: opened, provider (`sepay|napas`), rail ref, amount VND, content (masked), reason, status, age | Sorted oldest-open first; filter provider/status/reason |
| `exceptionReason` | Chip | Yes | Enum: `unknown_ref, amount_mismatch, order_cancelled, order_expired, late_transfer, duplicate_ref` | From server; not editable |
| `linkedOrder` | Link | No | Set by link resolution; shows order status + buyer (masked) | Null until linked |
| `linkAction` | Button + confirm | Yes to act | Enabled when: reason ∈ (unknown_ref, amount_mismatch, late_transfer) AND within grace AND order matchable | Opens order picker; runs grant via 34's guard; reason required |
| `graceClock` | Inline meta | Yes | Time left of `lateTransferGraceHours` (24 h default) for expired orders | Beyond grace the action disables ("Grace elapsed — refund path only") |
| `refundRecordAction` | Button + form | Yes to act | Records the bank return: bank ref, date, operator second-party confirm | Status → `refunded`; audit with both actors |
| `dismissAction` | Button + form | Yes to act | Reason ≥ 10 chars; requires ops role | Status → `dismissed`; audited |
| `resolutionRef` | Text | On refund | Bank return reference | Immutable after save |
| `disputesList` | Table | On load | Columns: opened, provider (`stripe|googlepay|applepay`), order, rail dispute ref, status (`open, evidence_submitted, won, lost`), deadline, loss VND | Deadline countdown highlighted ≤ 72 h |
| `deadlineAt` | Timestamp | Mandatory while open | Provider-set; never editable | Overdue escalates (alert + auto-loss handling) |
| `evidenceRef` | Text + upload link | Mandatory to submit | External evidence bundle reference | `evidence_submitted` sets audit; immutable |
| `lossVnd` | Integer VND | On `lost` | Provider-reported loss | Recorded on case; feeds finance readout |
| `verifyAction` | Button | Yes to act | Per order; throttled (1/30 s/order; 30/h operator) | Runs the rail verify adapter; outcome shown + audited |
| `railHealth` | Cards | On load | Per rail: kill-switch state, last sweep at, stale count, verify failures 24 h, exception backlog | Zero-PII aggregates only |
| `killSwitch` | Toggle + reason | Yes to flip | Reason mandatory; audited | Flip via 32's config path; delist ≤ 5 s |
| `sweepLog` | Table (read-only) | On load | Columns: rail, ran at, stale scanned, settled, errors | Idempotent runs; no PII |
| `secretAge` | Meta | On load | Days since last rotation per rail secret | Warn > 80 days; drill quarterly (BR-14) |

## Validation and behavior notes

- All ops actions require the restricted ops role per request (deny-by-default, S-1 in TL 05); every mutation writes the audit spine with actor + reason.
- Money fields are integer VND throughout; no formatted decimals exist on these surfaces.
- Buyer surfaces never expose rail refs, provider names in refusal copy, exception rows, or dispute internals; support macros read the audit trail, not the buyer UI.
- Masking: rail refs shown to ops only (full); content snippets masked to first/last 4 chars; buyer identity masked except on linked-order detail.
- Empty states: queue empty = "No open exceptions"; disputes empty = "No open disputes"; rail with backlog age > 24 h shows a warning tone but serves.
