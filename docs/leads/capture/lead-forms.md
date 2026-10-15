# Lead Forms (form blocks + one-tap capture to dashboard)

PLAN ref: P2-4 (`form` block + Connect → `leads{profile_id, email, source, consent_at}`). P2 scope — buildable once profiles/blocks ship; campaigns stay P4-gated (see `monetize/marketing/campaign-broadcasts.md`). Block type enum in `link-to-bio/blocks/link-blocks.md`; list lifecycle in `monetize/marketing/audience-lists.md`.

## Purpose

Turn visitors into contacts at the point of interest: a form block on the bio page (or shop, or course page) captures an email — plus optional name — into the creator's dashboard inbox in seconds, with consent recorded at the moment of capture. Capture is the top of every downstream funnel (broadcasts, retargeting, sales follow-up), so it must be frictionless for genuine visitors and worthless to bots.

## Users

- Visitor: submits email in one tap (two at most), understands exactly what they signed up for, receives confirmation.
- Creator: configures fields and offer copy per form, watches submissions land in the dashboard, follows up.
- Bot/spammer (adversarial): finds no open relay — submissions are throttled, verified, and never reflected unsanitized.

## Functional requirements

1. Form configuration (per `form`-type block, dashboard): field set from a closed menu (email required; name, phone, note optional — no arbitrary field builder in v1, so exports stay tabular); headline + offer copy (what the visitor gets); submit label; success message + optional redirect (own page or link-block target); per-form toggle for double opt-in (confirmation email before `active` status — recommended default where broadcasts are planned, since confirmed lists protect sender reputation).
2. One-tap capture: email-only forms submit in one tap (native email keyboard, autocomplete honored); social/one-tap autofill where the browser offers it — no platform SDKs or third-party scripts on the capture surface in v1. Inline validation (format, obvious typos with did-you-mean for major domains) before submit; server re-validates everything (client checks are UX only).
3. Connect semantics: submission writes `leads{profile_id, email, source=form:{block_id}, consent_at, consent_copy_version, status}` through the same dedupe/merge discipline as the audience doc (strictest-state-wins; re-submission never resurrects suppression). Every row carries source + consent class + copy version from birth — proof-of-consent is automatic, not reconstructed later.
4. Confirmation + welcome: immediate on-page success state (no redirect required); optional double-opt-in email (transactional class, signed token, 48 h expiry) flipping `pending` → `active`; optional single welcome message (one, template-bound, no drip — drip is P4 campaign territory). Unconfirmed rows age out of send eligibility but remain visible as pending in the dashboard.
5. Abuse budgets (shared posture with all public writes): Turnstile + WAF/rate rules on submit; per-form velocity caps (a form receiving 100 submissions/minute from one source pauses with creator notice); disposable-email policy per Q2; submission content (name/note) sanitized like all bio payloads (XSS strip + CSP, no reflection).
6. Dashboard inbox: per-form submission feed (newest first, unread markers, per-lead detail linking to the unified contact view in `manage/lead-search.md`); CSV entry point delegates to the audited export in the audience doc. Creators see only their own profiles' leads (owner scoping, no cross-profile leakage).

## Data / API touches

- Tables: `leads` (P2-4 shape + `status` + `consent_copy_version` + `form_block_id`, standard `user_id` + RLS) + `lead_events` (append-only, per audience doc). Form config lives in the block's `payload_json` (closed schema: fields[], copy, double_opt_in flag, redirect?) validated against the frozen block enum — no new config table.
- Endpoints: public `POST /api/t/forms/:blockRef/submit` (throttled, Turnstile-verified, queue-mediated write like the beacon — never direct PG writes from the submit handler); owner `GET /api/v1/leads?form=` (inbox feed), form-config via existing block CRUD; double-opt-in confirm (signed token, no login). All PG via `worker/db/*`.
- Queue: submissions flow through a capture queue with the same dedupe/idempotency pattern as beacons (double-tap submits create one lead).

## Acceptance criteria

- Capture drill: valid submit lands in the creator inbox within seconds with source + consent class + copy version intact; duplicate submit (double-tap, replay) creates exactly one lead row.
- Consent drill: unchecked marketing box (default) yields lifecycle-only consent; checked yields marketing class; both provable per-address in minutes.
- Double-opt-in drill: pending row ineligible for broadcasts; token confirm flips to active; expired token offers clean re-request (no duplicate rows).
- Abuse drill: burst submits throttled with creator-visible pause notice; script payloads in name/note stripped; disposable-domain policy enforced as decided.
- IDOR drill: creator A lead reads/exports against creator B `profile_id` return 403; public submit cannot enumerate existing leads (uniform success timing).

## Out of scope

- List lifecycle, hygiene, CSV mechanics (see `monetize/marketing/audience-lists.md`); consent classes and preference center (see `monetize/marketing/consent-preferences.md`); broadcasts (see `monetize/marketing/campaign-broadcasts.md`, P4).
- Lead search, detail, and tagging (see `manage/lead-search.md`); automation-built contacts (see `docs/automate/`); checkout opt-ins (their shop docs, same lead shape).

## Open questions

- Q1: Double opt-in default — on for all forms (reputation-first) or creator choice with strong recommendation?
- Q2: Disposable/role-address policy — block, flag-but-accept, or accept-and-let-hygiene-handle?
