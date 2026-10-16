# QR Codes - UI Data Dictionary (feature 30)

Phase 1 BA · QR tab (link manager) + ops pack screen.

## QR tab (per short link)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Format | radio | yes | PNG (with DPI select) or SVG | PNG |
| DPI | select | PNG only | 150/300/600 | 300 |
| Size class | select | yes | S ≈ 2 cm · M ≈ 4 cm · L ≈ 8 cm · poster ≥ A4 (physical captions) | M |
| Error level | select | - | M default; H auto-required for S/poster with `Outdoor/small needs H` caption | M |
| Contrast check | inline status | - | palette floor ≥ 7:1; below-floor blocked pre-submit with floor named | pass |
| Floor message | inline notice | - | names the violated floor (size/level/contrast) | hidden |
| Download | button | - | streams image + companion label in the same download | - |
| Label preview | text | - | short code + destination preview text | live |
| Re-render current | button | - | regenerates from the LIVE link record; version note `Rendered just now from the current target` | - |
| Render history | list | - | timestamp, format, size, level, download per row | collapsed |
| Staleness banner | banner | - | `May be outdated — re-render` when link `updated_at` > render `created_at` | auto |
| State banner | banner | - | non-live parent: `Suspended — codes resolve to the suspension page`; generation disabled | auto |
| Preview | canvas | - | browser preview only; watermark `PREVIEW — not for print` overlay | - |

## Print sheet

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Codes per sheet | number | yes | 1–24 (multi-up grid) | 6 |
| Label template | select | yes | fixed set (code + preview text) | default |
| Margins | select | - | standard / tight | standard |
| Test-print checklist | notice | - | `scan the test square at arm's length before the full run` | shown once |
| Sheet download | button | - | browser-print SVG; no PDF artifact exists | - |

## Ops pack screen

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Count | number | yes | 1–100 per pack | - |
| Prefix | text | no | code prefix (≤ 8 chars, mints vanity-prefixed codes) | none |
| Size class / level | select | yes | same floors as QR tab | M/M |
| Labels | table/paste | yes | one label per code; 100-row cap with `split into multiple packs` guidance | - |
| Progress | status | - | atomic: success → all renders; failure → `Pack failed, nothing created` | - |
| Results | list | - | per-code download + label sheet + stats-separation note | - |
