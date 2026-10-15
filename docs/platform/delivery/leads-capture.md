# Leads Capture (execution plan L0 — forms, inbox, consent, CSV)

PLAN ref: P2-4 (`form` block + Connect → `leads{profile_id, email, source, consent_at}` + CSV export; campaigns deferred P4) + S-1/S-2/S-5. BA source: `docs/leads/capture/lead-forms.md`, `docs/leads/manage/lead-search.md`. Builds on B0 blocks; list lifecycle/campaigns stay P4-gated. DEVIATION CARRIED: Spring Boot service + containerized PG replace Worker/queue defaults; capture queue becomes a durable in-service outbox + scheduled consumer (same dedupe/idempotency pattern); contract frozen.

## Purpose

Turn visitors into provable contacts: closed-menu form blocks with one-tap capture, consent-at-birth rows, double-opt-in activation, an owner-scoped inbox with unified contact view, and audited CSV export. L0 exits with capture, consent, idempotency, and export drills green — and zero campaign machinery.

## Users

- Visitor: submits email in one tap, sees exactly what they joined, confirms via double-opt-in where enabled.
- Creator: configures forms, triages the inbox, searches contacts, exports CSV with consent timestamps.
- Bot/spammer: meets throttles, verification, and sanitization — no open relay, no reflection, no enumeration.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6`, Boot `4.1.1`, Kotlin `2.4.21`, Gradle `9.8.1`. Architecture verdict for L0 — monolith `leads` module, not a capture service. Monolith pros: form config (block `payload_json`) + lead write + dedupe/merge share one transaction and the `bio` RLS baseline; queue-mediated submit uses an outbox row + scheduled consumer in the same deployable (no broker to operate at P2 volume). Microservices cons avoided: a capture-service split would separate the write from the dedupe/merge truth (double-row risk under retry) and add anonymous-surface auth plumbing for a low-volume async path. Split trigger recorded: extraction behind a real broker only on sustained submit-backlog evidence at P4.
2. Form kernel: closed field menu (email required; name/phone/note optional — no arbitrary builder, exports stay tabular); headline + offer copy + submit label + success message + optional redirect (own page or link-block target); per-form double-opt-in toggle (recommended default where broadcasts are planned). Native email keyboard + autofill, no third-party SDKs/scripts on the capture surface. Inline validation client-side; server re-validates everything.
3. Capture pipeline: public `POST /api/t/forms/:blockRef/submit` (Turnstile + WAF/rate rules, per-form velocity caps with creator-visible pause at flood) writes via outbox with beacon-style dedupe (double-tap/replay → one lead). Row shape: `leads{profile_id, user_id + RLS, email, source=form:{block_id}, consent class + consent_copy_version, status pending|active|suppressed, form_block_id}` + append-only `lead_events`. Strictest-state-wins merge (re-submit never resurrects suppression); proof-of-consent automatic from birth. Double-opt-in email (transactional class, signed 48h token) flips `pending`→`active`; single template-bound welcome at most (no drip — P4). Unconfirmed rows stay visible as pending, ineligible for sends.
4. Inbox and search: per-form feed (newest first, unread markers, per-lead detail → unified contact view with tags/notes, source history, suppression state); full-text/campaign-source search with owner scoping (no cross-profile leakage); audited CSV export (column-stable tabular schema incl. consent timestamps + copy version; large exports streamed server-side, download via short-lived owner-scoped grant). Public submit cannot enumerate existing leads (uniform success timing/wording).
5. Security baseline: creator-lead IDOR 403 + RLS deny (S-1/S-6); name/note XSS strip + CSP, no reflection (S-2); disposable/role-address policy enforced as decided (block vs flag vs accept-and-hygiene); secrets via environment only; import/export events in `audit_log`.
6. Container per release: L0 tag runs via `docker run` and `podman run` against empty PG `18.6` per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`.

## Data / API touches

- Tables: `leads` (P2-4 shape + status + consent_copy_version + form_block_id) + `lead_events` (append-only) + capture outbox; form config in block `payload_json` closed schema (no new config table). All PG via repositories; RLS + `WHERE user_id = session.userId` on owner reads.
- Endpoints: public throttled submit + double-opt-in confirm (signed token, no login); owner `GET /api/v1/leads?form=`, search/detail/tag endpoints, audited CSV export; form config via existing block CRUD.
- Retention: unconfirmed aging, suppression permanence, and account-deletion purge per retention policy; raw abuse signals age out on TTL.

## Acceptance criteria

- Capture drill: valid submit in inbox within seconds with source + consent class + copy version; double-tap/replay yields exactly one row.
- Consent drill: unchecked box → lifecycle-only class; checked → marketing class; per-address proof retrievable in minutes; pending rows broadcast-ineligible; expired token offers clean re-request without duplicates.
- Search/export drill: cross-profile isolation holds; CSV matches inbox cell-for-cell incl. consent columns; streaming export completes without timeouts.
- Abuse drill: bursts throttled with pause notice; script payloads stripped; disposable policy enforced; enumeration analysis shows no oracle.
- Security drill: cross-user lead access 403 + RLS deny; secrets grep clean.
- Container drill: digest pair boots, compose green, rollback green, OpenAPI diff clean; no campaign/broadcast tables or jobs in the image (grep + migration audit).

## Out of scope

- List lifecycle/hygiene, consent preference center, broadcasts (P4 — campaigns never stubbed here); automation-built contacts (see `docs/platform/delivery/publish-automate.md`); checkout opt-ins (same lead shape, owned by shop docs); replicas/ISR (P5).

## Open questions

- Q1: Double opt-in default — on for all forms or creator choice with recommendation?
- Q2: Disposable/role-address policy — block, flag-but-accept, or accept-and-hygiene?
