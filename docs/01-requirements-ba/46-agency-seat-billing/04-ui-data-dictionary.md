# Agency Seat Billing - UI Data Dictionary (feature 46)

Phase 1 BA · Surfaces: agency billing overview (contact/owner), counts-only staff view, transfer dialog, cancel flow, ops reconciliation. Amounts appear only on contact/owner surfaces.

## Billing overview (contact/owner)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `seatCount` | Stat | On load | Active billable seats from the latest snapshot | Links to seat history; clients never counted |
| `seatPrice` | Stat | On load | Catalog `agencySeatVnd` (VND integer) | Absent catalog → subscribe path refuses `catalog_incomplete` |
| `basePrice` | Stat | On load | Catalog `agencyBaseVnd` | Same refusal posture |
| `projectedTotal` | Stat | On load | Base + seat count × price + projected true-up | States the catalog version used |
| `trueUpLine` | Meta | On load | Day-weighted delta vs prepaid (positive line / negative credit) | Snapshot basis linked |
| `snapshotHistory` | Table | On load | Dated rows: day, seats, by-role breakdown | Immutable closed periods |
| `subscribeAction` | Button + provider picker | Yes to act | Explicit provider (eight rails, no default) | 33's front door checkout |
| `cancelAction` | Button + dialog | Yes to act | Cancellation at cycle end; no-hostage copy | Agency-wide effect stated plainly |
| `invoiceList` | Table | On load | Lines with one-hop snapshot trace | 37's receipts, filtered to the agency subscription |
| `planStateChip` | Chip | Yes | `active/grace/lapsed/cancelled` | Staff-power freeze explained in copy |

## Staff counts-only view (manager/member)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `seatHolders` | Table | On load | Who holds seats (name + role) — never amounts | Managers see counts-only by rule |
| `planStatusChip` | Chip | Yes | Active / frozen tooling state | No instruments, no totals |

## Transfer dialog

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `transferTarget` | Member picker | Yes | Active staff only; never clients | Pending until both confirmations |
| `outgoingConfirm` / `incomingConfirm` | Confirm steps | Both required | Each side confirms separately | Single-sided → billing untouched + audit |
| `transferState` | Chip | Yes | `pending/outgoing_confirmed/completed/expired` | Expires 7 days; restart allowed |

## Ops reconciliation

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `mismatchTable` | Table | On load | org, billed seats, roster seats, day, delta | Daily job output |
| `correctAction` | Button + reason | Yes to act | Forward-only correction; audited | Closed periods never rewritten |
| `evidenceLink` | Link | Yes | Snapshot + roster history side by side | Finance/support resolution path |

## Validation and behavior notes

- Every amount renders as a VND integer with thousands separators; no fractional VND exists anywhere.
- Subscribe requires an explicit provider selection; the picker has no preselected default (eight equal rails).
- Cancel copy: "Your clients keep everything — profiles, pages, content. Only agency tooling pauses."
- Empty states: no subscription → overview shows catalog prices + subscribe CTA; no snapshots yet → "Seats start counting when staff join."
