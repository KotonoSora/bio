# QR Codes - Acceptance Criteria (feature 30)

Phase 1 BA · BDD; legacy drills preserved by name.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Encode drill | an exported QR for a short URL | it is decoded; destination is edited; a reprint is rendered | every export decodes to the short URL (never the destination); the reprint resolves the new target; old prints follow the link's current state |
| AC-02 | Scan drill | the device × size × error-level matrix (iOS/Android native cameras + major bank/wallet scanners) | minimum-size exports are scanned; below-floor requests are requested | minimum-size exports scan at arm's length; below-floor requests are refused with the floor named |
| AC-03 | Lifecycle drill | a suspended/expired parent link | its QR is opened | the same gone/suspended behavior as URL opens; no orphan resolves exist |
| AC-04 | Contrast drill | below-floor palettes and normal exports | validation runs; CI reference images compare | below-floor requests are rejected; all exported codes pass the contrast/scan-ability check |
| AC-05 | Bulk drill | a 100-code event-pack request | generation completes | unique per-location links, correct labels, per-code stats separation (via feature 31), atomic all-or-nothing |
| AC-06 | Reprint-staleness drill | a download rendered before a destination edit | the QR tab renders | the download is flagged `May be outdated`; re-render carries the new target from the live record |
| AC-07 | Preview drill | a browser preview is produced | the preview is inspected; the codebase is grepped for encoder use | preview carries the `PREVIEW — not for print` watermark; no client-side official generation exists (encoder grep-gated) |
| AC-08 | Grant drill | a render download | non-owner probe, expired grant, re-download | non-owner denied (404/403) with zero file access; expired 410; re-download works from the render row while it lives |
| AC-09 | Ops-auth drill | a creator token against the pack route; a partial-failure simulation | requests run | creator → 403 `qr_pack_denied` + audit; partial failure creates nothing (`Pack failed, nothing created`) |
| AC-10 | Container drill | the release image | docker run + podman run; native-dep grep | digest pair boots, compose green, rollback green; no native QR/image dependencies beyond the flagged pure-Java encoder |
