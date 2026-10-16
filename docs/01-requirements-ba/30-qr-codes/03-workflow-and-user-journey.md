# QR Codes - Workflow and User Journey (feature 30)

Phase 1 BA · Server floors are authoritative; UI mirrors them pre-submit.

## Creator: generate → download → reprint

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | opens a link's QR tab | format/size/level pickers with physical captions; H auto-required for S/poster with a visible caption (never a silent change) | BR-02 |
| 2 | Creator | picks PNG/SVG + size class | floors evaluated; below-floor combos blocked pre-submit with the floor named | BR-02 |
| 3 | System | render accepted | parent link asserted live → payload = short URL → floors enforced → bytes rendered → file stored → render row logged → audit | BR-01/02/10 |
| 4 | Creator | downloads | image + companion label (code + destination preview) | BR-03 |
| 5 | Creator | destination edited later | old download shows `May be outdated — re-render` (render timestamp vs link update) | BR-05 |
| 6 | Creator | re-renders | pipeline reads the LIVE link record; new render row; new download carries the current target | BR-05/11 |

## Print paths

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | builds a multi-up sheet | server assembles the sheet; browser prints it; no PDF artifact exists anywhere | BR-03 |
| 2 | Creator | test-prints | test-scan checklist shown once before the full run | BR-02 |
| 3 | Print vendor (indirect) | receives exports | quiet zones, contrast, and module floors already enforced before bytes existed | BR-02 |

## Ops: event pack (atomic)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Ops | submits pack (count ≤ 100, prefix, size class, labels[]) | all-or-nothing: N distinct short links + N renders + label sheet in one transaction; audit with operator + purpose | BR-07/10 |
| 2 | Ops | partial failure occurs | `Pack failed, nothing created` — no partial measure exists | BR-07 |
| 3 | Ops | downloads the pack sheet | per-location codes resolve distinctly; per-code stats separate in feature 31 | BR-07/08 |

## Lifecycle and scan path

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Scanner | scans an old print | lands on the short URL; link state resolves (live → target; suspended/expired → same page as URL opens) | BR-04/06 |
| 2 | Creator | parent link suspended | QR tab shows the state banner; generation disabled (server mirrors 409) | BR-04 |
| 3 | System | link purged exceptionally | cascade removes render rows; files swept | BR-01/12 |
