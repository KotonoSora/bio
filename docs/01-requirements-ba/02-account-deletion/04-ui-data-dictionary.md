# Account Deletion - UI Data Dictionary (feature 02)

Phase 1 BA · Fields the SPA renders for request, grace, and support screens. Contracts: `docs/02-architecture-techlead/02-account-deletion/`. Rules: `docs/01-requirements-ba/02-account-deletion/02-user-stories-and-business-rules.md`.

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Delete entry point | Settings menu item | Auto | Equally discoverable as plan settings; no hidden placement, no support-gate | Always visible in account settings |
| Consequences - vanishes list | Static list | Auto | Rendered from the retained-set contract (BR-05); never softened wording | Full list shown, scrolled before confirm |
| Consequences - stays list | Static list | Auto | Financial lines, legal-hold evidence, audit + deletion record; states hashing for buyer email | Includes export-first offers above the confirm control |
| Export offer buttons | Buttons (3) | No | Profile JSON (features 17-21), leads CSV (27-28), invoices (37); links only | Rendered only for accounts where each surface exists |
| Step-up trigger | Dialog (reused) | Yes when stale | Feature 01 step-up contract; names the action | Silent when re-auth is `<= 10 min` old |
| Typed confirmation input | Text field | Yes | Must equal the username exactly, else the literal `DELETE`; case-sensitive | Empty; confirm disabled until exact match |
| Delete (final) button | Button | Yes | Enabled only after exact confirmation; CSRF on submit | Visually destructive styling; no pre-checked state |
| Status panel | Panel | Auto | `grace` countdown to purge date; states that login + confirm restores | Shown after request; also reachable from settings |
| Restore button | Button | Yes to restore | Login + confirm (no step-up); states billing does not auto-resume | Primary action on the restore screen |
| Status chip (settings) | Chip | Auto | Values: none / in grace (countdown) / cancelled | Hidden when no request exists |
| Reminder email | Transactional email | Auto | Day 23 only; exact purge date interpolated; no marketing content | One send; bounce flagged to support |
| Purge certificate view (support) | Read-only rows | Auto | Counts per table + objects + run id + timestamps; no user content | Reached via audited route; every read logged |
| Re-registration notice | Inline notice | Auto | Post-purge signup: email accepted, username retired, history reset | Appears at the username step of signup |
