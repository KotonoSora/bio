# Short-Link Analytics - UI Data Dictionary (feature 31)

Phase 1 BA · Stats route (`/app/links/{code}/stats`) + ops dispute view.

## Stats dashboard (owner)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Opens/uniques curve | chart | - | daily grain; `generated_at` + lag footnote | last 30 days |
| Top referrers | table | - | `referrerHost` keys; top 10 + other | collapsed |
| Device split | donut | - | device dimension | summary |
| Geo-country | map/list | - | country codes only (no city) | collapsed |
| Scan share | stat + label | - | `estimated` badge when heuristic-derived; event-pack codes render unlabeled-exact | - |
| Exclusion panel | table | - | counts per reason (bot, prefetch, dedupe, quarantine) + equation `raw − excluded = reported` | inline section |
| Quarantine banner | banner | - | `traffic held for review` + case reference; owners never see silent gaps | auto |
| Export CSV | button | - | streamed; includes exclusion columns + `generated_at` | - |
| Dual-count explainer | info line | - | `Opens here count short-link opens; bio clicks count block taps — both true` + matched-window links | on bio-embedded links |

## Ops dispute view

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Figure selector | form | yes | day + code within TTL window | - |
| Evidence sample | list | - | raw-hit sample (hashed ids only), filter decision per class | paged |
| Quarantine rows | table | - | held count, reason, status, reviewer trail | - |
| Decide dialog | modal | reason required | release / drop; ≤ 200 chars | - |
| Expired evidence | notice | - | `evidence aged out` when beyond 90 days | - |

## Labels and states

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Estimated badge | tag | - | data-driven (`estimated: true`), never a tooltip-only caveat | - |
| Freshness footnote | text | - | `generated_at` + consumer lag | every readout |
| Empty state | notice | - | no hits yet vs not-yet-processed (lag shown) | - |
