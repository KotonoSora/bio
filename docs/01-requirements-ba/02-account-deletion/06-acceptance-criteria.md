# Account Deletion - Acceptance Criteria (feature 02)

Phase 1 BA · BDD checks gating the release of `docs/02-architecture-techlead/02-account-deletion/`. Rules cited as `BR-nn` from `docs/01-requirements-ba/02-account-deletion/02-user-stories-and-business-rules.md`.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Flow drill | An account with profile, links, sessions, queued work, and a subscription fixture | Request -> deactivate -> login within grace -> restore | Immediate 404s + sitemap drop + session death + work cancellation; restore is byte-identical; all transitions audited (BR-01, BR-02, BR-03) |
| AC-02 | Billing drill | An account in grace with an active subscription fixture | Billing runs; user restores; user re-subscribes | No charges during grace; paid-term service honored; restore never auto-recharges; fresh checkout required (BR-03) |
| AC-03 | Retention drill | A purged account with orders/invoices, legal-hold evidence, audit rows | Probe suite spot-checks every participant and storage namespace | Only financial lines (hashed per config), held evidence, audit rows, deletion record + certificate survive; everything else gone; probes green (BR-05, BR-10) |
| AC-04 | Collateral drill | A deleter sharing org rows, buyer receipts, and support cases | Purge runs | Co-members, buyers, and counterparts are unaffected at row level; org path follows feature 45 rules (BR-06) |
| AC-05 | Cooldown drill | A held username during grace; a retired username after purge | Squat and reuse attempts run | Grace attempt -> `username_taken`; post-purge -> `username_retired`; email reusable; no consent/suppression resurrection (BR-07) |
| AC-06 | Certificate drill | Pre-purge inventory counts; a purged account | Certificate is fetched through the audited route | Counts reconcile with inventory; certificate emitted only on full success; support read is audited (BR-08, BR-12) |
| AC-07 | Failure drill | One participant service made to fail mid-purge | Purge runs twice | Status stays `grace`; retry is idempotent by `runId`; no partial "purged"; 24 h stuck pages ops (BR-09) |
| AC-08 | Public-surface drill | Account in grace | `/username`, `/cv`, `/shop`, a custom domain, and the sitemap are requested | All resolve `missing`/404 within `<= 60 s`; cache bound documented; restore returns them within `<= 60 s` (BR-02, BR-03) |
| AC-09 | No-shortcut drill | Support/ops role and a foreign user session | Purge/cancel attempts from both | Both denied and audited; owner-only flow is the sole path (BR-11) |
| AC-10 | Gate drill | A session older than 10 min re-auth | Request is attempted with wrong typed confirmation, then correct | First: `422 confirmation_mismatch`; correct value after step-up: request commits; stale step-up alone: `403` (BR-01) |
