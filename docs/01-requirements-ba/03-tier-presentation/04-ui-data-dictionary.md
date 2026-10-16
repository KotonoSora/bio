# Tier Presentation - UI Data Dictionary (feature 03)

Phase 1 BA · Fields the SPA renders on pricing and account screens. Contracts: `docs/02-architecture-techlead/03-tier-presentation/`. Rules: `docs/01-requirements-ba/03-tier-presentation/02-user-stories-and-business-rules.md`.

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Tier card (Free / Pro) | Card | Auto | Two tiers only; claim names from the registry; no third card | Rendered from catalog + registry |
| Term toggle | Segmented control | No | Monthly / Yearly; yearly prices render only from catalog + discount config | Monthly selected |
| Saving badge | Badge | Auto | Shown only when `yearlyDiscount` set; percent from config, value computed server-side to VND | Hidden when unset (absence, never "0%") |
| Trial CTA | Button | Auto | Shown only when `trialDays` set; copies the configured count | Hidden when unset |
| Price display | Text | Auto | VND integer from server; SPA formats only, never computes | - |
| Claim sentences | Text blocks | Auto | Each mapped in the registry (enforcing spec + key); literals fail CI | Rendered from registry |
| Current tier chip | Chip (account screen) | Auto | Free / Pro from the entitlement snapshot; never client-cached beyond 30 s | `Free` |
| Renewal row | Text row | Auto | "Your renewal price" + period end from the subscription projection; absent pre-feature 36 | Whole row omitted when absent |
| Trial state row | Text row | Auto | Config day-count + expiry date; only when a trial is active | Omitted otherwise |
| Cancel button | Button | Auto | One confirmation; states service-to-period-end + downgrade effects; export-first offer | Always visible for paid states |
| Change plan button | Button | Auto | States the no-proration rule + effective dates (upgrade stacks; switch re-prices at renewal) | Visible for all paid states |
| Renewal reminder email | Transactional email | Auto | Buyer's own next amount from the then-active version; no headline price | Sent in the reminder window |
| Downgrade effects list | List | Auto | Lossless wording from the lifecycle reference; serving stops, data retained | Rendered in cancel confirmation |
| Step-up trigger | Dialog (reused) | Yes when stale | Feature 01 step-up contract; applies to cancel and plan change | Silent when re-auth `<= 10 min` old |
