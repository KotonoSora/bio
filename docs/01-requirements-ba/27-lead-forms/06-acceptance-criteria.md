# Lead Forms - Acceptance Criteria (feature 27)

Phase 1 BA · BDD; legacy L0 drills preserved by name.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Capture drill | a valid submit and a duplicate (double-tap/replay) | the capture consumer processes | the valid submit lands in the inbox within seconds with source + consent class + copy version intact; duplicates produce exactly one lead row |
| AC-02 | Consent drill | an unchecked marketing box and a checked one | leads are recorded | unchecked yields lifecycle-only; checked yields marketing class; both provable per-address in minutes (consent_at + copy version retrievable) |
| AC-03 | Double-opt-in drill | a pending row, a valid token, an expired token | eligibility and confirm run | pending is send-ineligible; the valid POST flips to active; the expired token offers clean re-request with no duplicate rows |
| AC-04 | Abuse drill | burst submits, script payloads, disposable domains, an unknown block | submit validates | bursts pause the form with a creator notice; scripts strip; disposable domains flag-but-accept; unknown blocks return the identical 202 |
| AC-05 | IDOR drill | creator A reading creator B's inbox/lead rows | access is attempted | 403 + RLS denies; zero rows; public submit reveals nothing about existing leads (uniform body + timing) |
| AC-06 | Uniform-timing drill | accepted, duplicate, and unknown-block submits | responses are compared | byte-identical bodies and indistinguishable timing; malformed email alone differs (422) |
| AC-07 | No-resurrect drill | a suppressed lead | it is resubmitted (checked and unchecked) | `resubmitted` is recorded; status stays `suppressed`; consent never downgrades |
| AC-08 | Scanner drill | a mail scanner fetching the confirm link | the link is GET-fetched repeatedly | nothing confirms; state changes only on an explicit POST |
| AC-09 | Welcome-once drill | an activation | welcome sends | exactly one sends; no drip exists |
| AC-10 | Merge drill | a checkout opt-in (forward shape) and a form lead sharing an email | both process under one profile | one row per strictest-wins; source history shows both provenances in events |
| AC-11 | Purge drill | a creator with leads, events, and outbox rows | account deletion runs | rows removed with counts in the purge certificate; no cross-tenant residue |
| AC-12 | Surface-review drill | the codebase and API surface | consent-grant paths are searched | zero endpoints/UI paths can set consent outside a checked submission; grep gate passes |
