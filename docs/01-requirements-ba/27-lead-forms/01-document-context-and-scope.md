# Lead Forms - Context and Scope (feature 27)

Phase 1 BA · Feature 27 of 53 — leads 1/2. Legacy sources: `leads/capture/lead-forms.md` and `platform/delivery/leads-capture.md` (L0 execution plan folded here; deleted with this batch). Realized by `docs/02-architecture-techlead/27-lead-forms/`; a module of `profile-service` (the blocks host). Contact workspace (search, timeline, notes, tags, export) is feature 28: `docs/01-requirements-ba/28-lead-search/`.

## Purpose

Turn visitors into provable contacts at the point of interest: a `form` block on the bio page captures email (plus optional name, phone, note) into the creator's dashboard inbox in seconds, with consent recorded at the moment of capture. Capture is the top of every downstream funnel, so it must be frictionless for genuine visitors and worthless to bots: throttled, verified, queue-mediated, and never reflected unsanitized.

## Objectives

| # | Objective |
|---|---|
| O-1 | Closed-menu form blocks (no arbitrary builder) configured in the dashboard; exports stay tabular. |
| O-2 | One-tap capture: email-only forms submit in one tap; server re-validates everything; no third-party scripts on the capture surface. |
| O-3 | Consent at birth: every row carries source, consent class, `consent_at`, and `consent_copy_version` — proof-of-consent is automatic, never reconstructed. |
| O-4 | Dedupe/merge discipline: double-tap or replay creates exactly one lead; re-submission never resurrects suppression (strictest-state-wins). |
| O-5 | Double opt-in: pending rows are send-ineligible; a signed 48 h token flips them to active via an explicit confirm action. |
| O-6 | Abuse economics: Turnstile-class verification, per-form velocity caps with creator-visible pause, disposable-address flagging, uniform success timing (no enumeration oracle). |

## Personas / RBAC

| Role | CRUD Permissions | Description |
|---|---|---|
| Visitor | C own submission only | submits email in one tap, understands exactly what they joined, confirms via double opt-in where enabled |
| Creator | R/U own form config (block CRUD); R own inbox feed | configures fields and copy, watches submissions land, follows up |
| Bot/spammer (adversarial) | none | finds no open relay: throttled, verified, sanitized, non-enumerable |
| Ops | R audit | investigates abuse reports, never edits leads |

## In scope

`form` block payload schema (closed field menu, copy, redirect, double opt-in toggle); public throttled submit; capture outbox + scheduled consumer; `leads` + `lead_events` rows with consent-at-birth; dedupe/merge; double-opt-in confirm (signed token) + single welcome; dashboard inbox feed (unread, per-form); abuse caps + pause notices; disposable/role-address flagging; owner-scoped reads; purge participant.

## Out of scope

Contact workspace: search, filters, timeline, notes, tags, export mechanics (feature 28); list lifecycle/hygiene, consent preference center, broadcasts (monetize batch: `monetize/marketing/*`); automation-built contacts (41-44); checkout opt-ins (33-34 consume this capture contract, same lead shape); CSV file generation (28 owns the job; this feature owns the row contract); campaigns (gated backlog 48-53).

## Open questions (resolved)

| Q | Resolution |
|---|---|
| Q1 double opt-in default | Default ON for new forms (reputation-first) with a creator toggle; the toggle copy states the sender-reputation tradeoff. |
| Q2 disposable/role addresses | Flag-but-accept: `disposable_flag` recorded (curated domain list + role-prefix detection), dashboard shows the flag, nothing blocks capture; hygiene consumption lands with the audience batch. |
| Q3 confirm UX | The email link opens a page that requires an explicit confirm tap (POST); a bare GET never confirms (mail-scanner safe). |
| Q4 success timing | Uniform 202 (body + timing) for accepted/duplicate/unknown-block submissions; only malformed email is 422 — no row-existence oracle exists. |
| Q5 velocity caps | 100 submissions/minute per block per source → 15-minute form pause + creator notice; raw abuse signals age out on a 24 h TTL. |
| Q6 welcome | At most one template-bound welcome on activation; no drip, no sequence (campaign territory). |
| Q7 L0 architecture verdict | DEVIATION CARRIED: the legacy Worker/queue defaults become a durable in-service outbox + scheduled consumer (same dedupe/idempotency contract); the capture module lives inside `profile-service` (block `payload_json` + lead write + dedupe share one transaction and the bio RLS baseline), with the split trigger recorded: extraction only behind a real broker on sustained submit-backlog evidence. |
