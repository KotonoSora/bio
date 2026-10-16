# QR Codes - Exception Handling and Errors (feature 30)

Phase 1 BA · Codes are stable; UI copy localizes vi/en. Both layers (UI pre-submit + server) use the same floor messages.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Parent link not live (suspended/expired/pending) | API | 409 `qr_parent_not_live` with the link state named; nothing rendered | "This link is suspended — codes would resolve to the suspension page." |
| Below module/size floor | API | 422 `qr_below_floor` naming the floor | "This size can't scan at arm's length — choose M or larger." |
| Below contrast floor | API | 422 `qr_low_contrast` naming the floor | "This palette is below the scan-contrast floor." |
| Invalid size class / format | API | 422 `validation_failed` | inline field hint |
| Foreign link / render | API | 404 `link_not_found` / `qr_render_not_found` (no existence oracle) | "Not found." |
| Pack count beyond cap | API | 422 `qr_pack_invalid` | "Packs are limited to 100 codes — split into multiple packs." |
| Creator token on pack route | API | 403 `qr_pack_denied` + audit | "Ops only." |
| Partial pack failure | System | transaction rolls back; nothing created | "Pack failed, nothing created." |
| Quota exceeded (renders/packs) | API | 429 `throttled` | "Too many renders — try later." |
| Download grant expired | API | 410 `grant_expired`; re-download from the render row | "Download link expired — download again." |
| Blob storage unavailable | System | 503 `render_unavailable`; no log row without stored bytes | "Rendering is briefly unavailable." |
| Preview used for print | UI | watermark overlay `PREVIEW — not for print`; downloads always server-rendered | (visual) |
| Scan of old print after destination edit | System | resolves the CURRENT target (encode-short guarantee) | - |
| Scan of suspended link's old print | System | same suspension page as URL opens | - |
