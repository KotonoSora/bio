# Advertising (Inventory, Serving, Creator Share, Retargeting) - UI Data Dictionary (feature 50) - GATED

Phase 1 BA · GATED: no surface below may be built before the amendment. Surfaces: visitor ad unit + disclosure, creator ad settings, creator earnings, advertiser reports, ad-ops console, retargeting builder + lift.

## Visitor unit + disclosure

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `sponsoredUnit` | In-feed card | When served | Image + headline + CTA; "Sponsored" label, visually distinct | Exactly one per creator page; never dismissable-as-content |
| `whyThisAdAction` | Link | Always | Opens disclosure: placement, category basis, coarse signals | No cross-site identity claimed |
| `collapsedSlot` | (absence) | On empty/error | Slot collapses — no blank box, no layout shift | Page never waits on ads (50 ms cap) |
| `adFreeSurfaces` | (assertion) | Always | Checkout/payment flows contain zero ad units | Grep + review gate |

## Creator ad settings

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `optInToggle` | Toggle | Yes | Default OFF; mid-flight opt-out stops serving immediately | No penalty; earnings to date stand |
| `categoryDeclaration` | Select | With opt-in | Feeds targeting + safety matching | Editable anytime |
| `shareFormulaNote` | Note | With opt-in | States the rate is config/versioned; same formula free and pro | No plan-based discrimination |
| `adjacencyBlocks` | Multi-select | Optional | Creator blocks advertiser categories | Enforced in serving |

## Creator earnings

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `estimatedBalance` | Stat | On load | Daily estimates from filtered aggregates | Labeled as estimate |
| `confirmedBalance` | Stat | On load | Confirmed at monthly close | Never mixed with estimates |
| `perFlightBreakdown` | Table | On load | Flight, day, amount, status | One hop to evidence |
| `filterExclusions` | Stat | On load | What was excluded on their pages + why | Transparency minimum |
| `thresholdProgress` | Progress | On load | Toward the config-owned floor | Roll-forward explained |
| `clawbackLines` | Table | On events | Reason + linked evidence + notice | Never silent |

## Advertiser reports / ops console

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `flightReport` | Table | On load | Impressions, clicks, CTR, filter exclusions | Own flights only |
| `evidenceLookup` | Search | On demand | Billed event → bundle (creative, flight, placement, segment, dedupe outcome) | Read-only |
| `flightActions` | Buttons + reason | Yes to act | Draft/approve/launch/pause/takedown; launch requires caps + window | Takedown audited, everywhere ≤ minutes |
| `prohibitedChecklist` | Review form | At approval | Prohibited verticals + category conflicts | Server-enforced, not prose |

## Retargeting builder + lift

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `segmentDefinition` | Form | Yes | Closed event set + lookback + cap; versioned | Running segments never silently change meaning |
| `segmentSize` | Stat | On load | Exact-ish, dedupe-tolerant, with `as of` + labeled lag | Minutes-scale |
| `flightBuilder` | Form | Yes to act | Surface onsite/email; control slice (10% default) | No lift without control |
| `liftReport` | Table | After run | Size, reach, conversion, suppression counts; lift vs control | vs control, never vs zero |
| `exportAttempt` | (absence) | Always | No off-platform export surface exists | 404/422, never stubbed |

## Validation and behavior notes

- Every retargeted message carries its cause line + one-click stop; frequency caps render as recipient-level suppression, not silent drops.
- Consent arbitration is server-side: the builder shows which class each send will use; promo variants never reach transactional-only recipients.
- Empty states: no eligible inventory → collapse; no segments → definition guidance; no earnings → opt-in explanation.
