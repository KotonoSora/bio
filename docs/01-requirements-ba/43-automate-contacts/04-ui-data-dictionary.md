# Automate Contacts - UI Data Dictionary (feature 43)

Phase 1 BA · Surfaces: creator contacts list (in the automations area), removal center (public, tokenized), ops aggregates. No message bodies, no token material, no usernames beyond opaque refs.

## Creator contacts list

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `contactRow` | Table row | On load | Columns: first seen, opaque ref (shortened), source automation, keyword, media, status, last interaction | Sorted newest first; filter automation/keyword/media/status |
| `statusChip` | Chip | Yes | Enum: `active, suppressed, blocked_platform, stale` | `stale`/`suppressed` excluded from send-eligible counts |
| `keywordFilter` | Dropdown | No | Source keywords actually observed | Multi-select |
| `mediaFilter` | Dropdown | No | Source posts actually observed | Multi-select |
| `suppressAction` | Button + confirm | Yes to act | Owner-initiated suppression with reason | Lands the same `suppressed` transition; audited |
| `exportCsvAction` | Button + dialog | Yes to act | Filter-aware; rate-limited; audited | Produces expiring signed download ≤ 24 h |
| `leadsHandoffAction` | Button + dialog | Yes to act | Only rows with email + real consent class are eligible | Result counts: imported / skipped-ineligible |
| `quotaMeter` | Meter | Yes | Free: contacts this year of 100; pro: this month of 100k | Reads the shared counting function; over-quota shows upgrade-or-wait |
| `consentClassBadge` | Badge | Yes | Shows actual class (transactional by default) + `consent_at` | Never inferred, never upgraded in any view |
| `removalCenterLink` | Copyable link | Yes | Public signed-token page (no account needed) | Per-contact or per-profile link |

## Removal center (public, tokenized)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `tokenState` | Internal | Yes | Signed, expiring; invalid/expired → honest page | No enumeration exists (invalid = neutral) |
| `suppressAction` | Button | Yes | One action → `suppressed` + reason `removal_center` | Confirmation copy states "no more messages from this creator" |
| `doneState` | Confirmation | Yes | Idempotent — repeat visits show already-suppressed | No login required |

## Ops aggregates (no raw lists)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `quotaDashboard` | Charts | On load | Sends held by quota, quota refusals by period | Aggregates only |
| `hygieneCounters` | Cards | On load | Suppressed / blocked / stale counts by network | Aggregates only |
| `exportAudit` | Table (read-only) | On load | Export + handoff events: actor, count, filter class | No row contents |

## Validation and behavior notes

- All creator reads are owner-scoped (RLS); the same IG user may appear under multiple creators — no cross-creator view exists.
- Copy is consent-honest: "They commented once — you can reply once. Marketing needs their explicit yes."
- Exports and handoffs carry the actual consent class + `consent_at`; UI never offers a "mark all marketing-consented" shortcut.
- Empty states: no contacts → "No one has triggered your automations yet" with a link to 44's rule builder; filtered-empty → clear-filters CTA.
