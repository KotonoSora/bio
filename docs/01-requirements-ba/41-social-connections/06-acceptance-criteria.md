# Social Connections - Acceptance Criteria (feature 41)

Phase 1 BA · BDD; drills run against `docs/02-architecture-techlead/41-social-connections/` implementations.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Vault drill | A connected account after connect + publish + refresh flows | Dumps and logs are inspected via the CI grep gate | Dumps show ciphertext only (GCM envelope); logs, error payloads, queue messages, and analytics contain zero token material |
| AC-02 | Cap drill | Free and pro connect attempts | Cap checks run server-side | Second free account on one platform → 403 `cap_exceeded` with upgrade path; direct API call cannot bypass; pro connects ten, eleventh refused |
| AC-03 | Downgrade drill | A pro user with 10 accounts per platform downgraded to free | The user opens composer and tries to connect | Over-cap accounts read-only visible; new connects and publishing from over-cap accounts blocked until trimmed; no silent deletion |
| AC-04 | Refresh drill | Near-expiry tokens; a provider-side revoked account | Refresh and health cycles run | Near-expiry refreshes silently (no failed publish); revoked flips `revoked` by the next cycle with queued targets cancelled and notice |
| AC-05 | Disconnect drill | A disconnect with queued targets | Disconnect runs | Vault row deleted, remote revoke attempted, queued targets cancelled with creator notice, audit written |
| AC-06 | Rotation drill | A master-key rotation | Dual-read window + re-wrap job run | All rows re-wrap with zero publish failures; old key retires only after verification; rotation audited |
| AC-07 | Callback drill | Valid, replayed, and tampered callbacks | Each hits the callback route | Valid creates one row; replayed/tampered refused (`oauth_state_invalid`) with zero side effects |
| AC-08 | Ops visibility drill | Ops health query | The restricted view is queried | Health metadata returns; token-column selection is impossible at the DB level; no export path includes tokens |
| AC-09 | Fail-closed drill | A dead connection targeted by a scheduled post | Dispatch validates targets | Target fails loudly with `connection_dead` + notice; never a silent drop; zero provider calls with dead credentials |
