# Refunds - UI Data Dictionary (feature 38)

Phase 1 BA · Surfaces: buyer refund request + tracker, ops refund queue, receipt credit-note section (37 renders). All values server-computed.

## Buyer: refund request dialog (order detail, 34's surface)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `eligibility` | State | Yes | Only `paid` shows the action; other statuses show the terminal reason or dispute path | `tip` kind: hidden with "tips are final" copy |
| `reason` | Textarea | Yes | Non-empty, <= 500 chars; plain text | - |
| `turnstile` | Widget | Yes | `challenge_failed` renders inline | - |
| `idempotencyKey` | Hidden | Yes | Minted client-side per dialog open | Double-submit = same request |
| `submitRequest` | Primary button | When eligible | Opens `requested` row; shows tracker | - |
| `existingRequest` | Banner | When a row exists | Links to the tracker; second submit returns the same row | - |

## Buyer: refund tracker

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `status` | Timeline | Yes | `requested → approved → executing → reversed`; or `rejected`/`failed` with plain copy | Polls on focus; no websocket exists |
| `amountVnd` | Text | Yes | Equals the order amount; integer VND | - |
| `provider` | Text | Yes | Rail display name | - |
| `reasonCode` | Label | Yes | Mapped to human copy (`buyer_request`, `courtesy`, booking reasons) | Internal codes never surface |
| `pspRef` | Text | When reversed | Rail reversal reference | Hidden while pending |
| `creditNoteRef` | Link | When reversed | Opens the receipt's credit-note section (37) | - |
| `slaCopy` | Text | Yes | API rails: "within 1 business day of approval"; manual rails: "up to 3 business days after approval" | 25's policy phrasing reused |

## Ops: refund queue

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `queue` | Table | Yes | Rows by status (`requested`, `approved`, `failed`); newest first; receipt id + order link columns | Filters: status, rail, kind |
| `approve` / `reject` | Buttons | `requested` rows | Reject requires a reason; both audited | Two-person notice when the rail is manual |
| `execute` | Button | `approved` rows | Manual rails: `evidenceRef` input required before submit; executor identity recorded; same-person attempt -> 403 `two_person_required` | API rails: no evidence field |
| `evidenceRef` | Text input | Manual rails only | Reversal transfer reference; stored verbatim | Never auto-filled |
| `retry` | Button | `failed` rows | Status-guarded; idempotency key = refund id | Attempts counter shown |
| `pairNotice` | Badge | Manual rails | Shows approver; executor must differ | - |
| `auditTrail` | Drawer | Yes | Actor + timestamp per step | Read-only |

## Receipt: credit-note section (37's print view, data from here)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `creditNotes` | Table | When present | `note_ref`, amount, reason copy, issued date | Append-only chain |
| `remainingBalance` | Text | When present | Original amount minus notes (full-only v1 = zero) | - |
| `linkage` | Text | Yes | "Linked to receipt {RCPT-...}" | - |

Notes: no client payload sets refund status anywhere; money vocabulary (`amount_vnd`) is read-only in every surface; emails carry the same content as the tracker (bilingual per 37's Q-01 rule).
