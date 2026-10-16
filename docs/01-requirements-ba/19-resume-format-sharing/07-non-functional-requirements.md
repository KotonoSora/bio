# Resume Format Sharing - Non-Functional Requirements (feature 19)

Phase 1 BA · Quantified thresholds enforced in `docs/02-architecture-techlead/19-resume-format-sharing/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

| NFR ID | Area | Requirement |
|---|---|---|
| NFR-01 | Gallery reads | List p95 `<= 200 ms` origin (edge-cached typical); detail p95 `<= 250 ms`; anonymous burst 600/min/IP with `429` beyond |
| NFR-02 | Writes | Draft save p95 `<= 300 ms`; publish (validate + scrub + insert + audit) p95 `<= 500 ms` atomic |
| NFR-03 | Clone | p95 `<= 300 ms`; idempotent; copy byte-compare asserted at clone time |
| NFR-04 | Re-scan job | Weekly (Sunday 04:40 UTC); `<= 10 min` for `<= 50k` published rows; hit -> flag + metric, no auto-action |
| NFR-05 | Moderation SLA | Takedown hides gallery-wide `<= 60 s` end-to-end (event purge + edge TTL); reporter identity never surfaces |
| NFR-06 | Layout bounds | `layout_json <= 64 KB`; sections `<= 20`; name `<= 120`; tokens from allowlists only |
| NFR-07 | Thumbnails | Auto placeholder renders; `<= 100 KB` each; random blob keys; no user content (upload route absent by design) |
| NFR-08 | Print parity | Shared formats print through the feature 18 pipeline; Chrome/Safari/Firefox/Edge preview fixtures per release |
| NFR-09 | Attribution freshness | Live link resolution `<= 30 s` stale bound (profile resolve cache); frozen string immutable forever once account is hidden/purged |
| NFR-10 | Audit | Publish, unpublish, clone, report intake, ops removal: append-only, 24-month retention, actor + reason for ops |
| NFR-11 | Localization | vi/en parity for builder, gallery, and attribution copy; format names user-authored (no translation) |
