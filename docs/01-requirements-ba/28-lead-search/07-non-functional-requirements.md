# Lead Search - Non-Functional Requirements (feature 28)

Phase 1 BA · Every threshold numeric.

## Performance

| # | Requirement |
|---|---|
| NFR-01 | Search p95 ≤ 400 ms at 100 000 leads per creator (trigram + pre-scoped predicates); count p95 ≤ 300 ms with export-identical predicates. |
| NFR-02 | Detail assembly p95 ≤ 300 ms (timeline + notes + tags). |
| NFR-03 | Export: enqueue ack ≤ 300 ms; 10 000 rows done ≤ 2 min (streamed); no sync export path exists to time out. |
| NFR-04 | Query logging is fire-and-forget (never on the critical path of the search response). |

## Limits and quotas

| # | Requirement |
|---|---|
| NFR-05 | Note ≤ 2 000 chars; tag ≤ 40 chars; vocabulary cap 50 per account; query history 20 per account. |
| NFR-06 | Export quota 20/day per account, 2 concurrent jobs; grant TTL 15 min; job + file retention 7 days then purge. |
| NFR-07 | Re-invite 3/day per lead; token TTL 72 h, single-purpose. |
| NFR-08 | Audit retention 24 months; append-only event tables. |

## Security and data

| # | Requirement |
|---|---|
| NFR-09 | Deny-by-default; `user_id` scoping + RLS on every read/write; cross-creator 403/404 with zero rows; no existence oracle on foreign ids. |
| NFR-10 | Export grants are owner-bound, non-transferable, short-lived; export files are private (service-mediated download, no storage-path exposure); CSV columns fixed (no free-form additions). |
| NFR-11 | Search requires ≥ 2 chars; no wildcard or platform-wide lookup exists; query history is per-account and invisible to others. |
| NFR-12 | PII: export contents are consent-annotated; grants and files purge with the account (feature 02 roster); logs carry ids, never raw emails. |

## Sessions, localization, accessibility

| # | Requirement |
|---|---|
| NFR-13 | Creator surfaces are session + CSRF for mutations; the unsubscribe page is anonymous token-based (POST confirms); downloads are grant-authenticated even for session holders. |
| NFR-14 | vi/en copy; counts and dates localize; CSV stays machine-stable (no localized headers) while the UI localizes. |
| NFR-15 | Results table keyboard-navigable with visible column labels; timeline reads linearly; filter chips announce selection (aria-pressed). |
