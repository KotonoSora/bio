# Account Deletion - User Stories and Business Rules (feature 02)

Phase 1 BA · Enforced by `docs/02-architecture-techlead/02-account-deletion/`. Scope: `docs/01-requirements-ba/02-account-deletion/01-document-context-and-scope.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Leaving user | request deletion with a clear vanishes-vs-stays explanation and full undo within grace | I can leave completely and verifiably | High |
| US-02 | Staying contact/buyer | my transactional receipts to survive minimized with no collateral purge | my records remain intact when another user leaves | High |
| US-03 | Finance/legal/auditor | the mandated financial footprint retained with nothing more | tax/audit duties are met without excess retention | High |
| US-04 | Leaving user | a day-23 reminder naming the exact purge date | my account never disappears unannounced | Medium |
| US-05 | Support/ops | verify a completed purge from a certificate | "is it gone?" is answerable without data access | Medium |

## Business rules

- **BR-01 Request gating.** Step-up re-auth (`<= 10 min`, feature 01) + typed confirmation (username when one exists, else the literal `DELETE`) + CSRF. Entry point as discoverable as plan settings: no support ticket, no retention maze, no dark pattern.
- **BR-02 Immediate deactivation.** On request commit: all sessions revoked everywhere; public surfaces (`/:username`, `/cv`, `/shop`, custom domains) 404 and drop from the sitemap within `<= 60 s`; queued posts/automations/campaigns cancelled (features 41-44 consume the event); broadcasts exclude the addresses on next eligibility check; billing stops (feature 36 consumes the event; no new charges during grace).
- **BR-03 Grace (30 days).** Restore = login + confirm; restoration is byte-identical (nothing degrades during grace); exactly one reminder email at day 23 naming the purge date; reactivation after purge or after cancel never auto-recharges — a fresh checkout is required.
- **BR-04 Purge (day 30).** Daily idempotent job purges owner-scoped rows across every registered service; participant responses supply per-table counts; the certificate is emitted only when every participant reported success.
- **BR-05 Minimized survival (exhaustive).** Vanishes: profile rows, blocks/links, documents, products, leads, contacts, media files, OAuth link rows, sessions, preference rows. Stays: order/invoice financial lines (amounts/dates intact; buyer email hashed per Q-01 config), dispute evidence under legal hold, append-only audit rows, the deletion record + certificate. Nothing else survives.
- **BR-06 Collateral boundary.** Purge scopes strictly to the deleter's owned rows: org rosters, co-members' content, buyers' receipts, and support cases persist (org-owner departure is features 45-47's transfer/dissolution path, never a cascade).
- **BR-07 Cooling and reuse.** During grace the username stays held (a squat attempt fails as `username_taken`); at purge it is permanently retired (`username_retired` on reuse attempt; release only via legal/abuse review). Email is reusable immediately post-purge; a fresh account starts with zero history — no old consent/suppression rows resurrect; suppression-beats-grant stands.
- **BR-08 Certificate.** Per-account JSON (per-table counts + file counts + run id + timestamps) emitted only on full success, retained as proof (24-month baseline), readable by support through an audited route.
- **BR-09 Failure handling.** A participant failure keeps the account in `grace` with `purge_started_at` set; the job retries (bounded, idempotent by `runId`); a run stuck over 24 h pages ops; no partial "purged" claim is ever user-visible.
- **BR-10 Residual probes.** Weekly per-account probe suite queries every participant for residual rows/files; any non-zero result pages ops with the certificate diff.
- **BR-11 No shortcut.** Deletion executes only through the owner flow; support cannot trigger, accelerate, or bypass it; org dissolution follows feature 45 rules instead of deleting on a shared account's behalf.
- **BR-12 Audit.** Request, cancel/restore, purge start, per-participant results, certificate emission, and support reads are append-only audit events with actor + target.

## Constraints carried

- Step-up and CSRF contract consumed from `docs/02-architecture-techlead/01-account-and-access/03-api-contracts-and-payloads.md`; no second gating mechanism is invented.
- Events follow the platform rule `<domain>.<fact>.v<major>`, emitted after commit, at-least-once, idempotent consumers; lost events self-heal via the 60 s status-cache bound on the consumer side.
- No money values are computed here; the only money facts are survival rules for rows owned by features 32-40.
