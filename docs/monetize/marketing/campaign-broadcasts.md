# Campaign Broadcasts (Resend sends to active leads)

PLAN ref: P4-4 — P4 scope, flagged explicitly. Do NOT build in P1–P3 (per AGENTS.md, P4 items are never stubbed into earlier specs); this doc is the gated design waiting for its phase. Audience states in `marketing/audience-lists.md`; consent classes in `marketing/consent-preferences.md`.

## Purpose

Define the platform's one marketing send path: Resend broadcasts to active leads with recorded delivery stats — and nothing more in v1. PLAN deliberately withholds segments and scheduling; this doc honors that restraint while making the allowed path (compose → review → send-now → measure) safe, consent-clean, and abuse-proof. Campaigns are the highest-abuse surface in the product (spam complaints kill sender reputation for transactional mail too), so restraint here protects receipts, grants, and reminders alongside marketing itself.

## Users

- Operator/marketer: composes a broadcast, previews it, sends now to the active audience, reads delivered/open/click stats.
- Lead/recipient: receives wanted mail, unsubscribes in one click when it stops being wanted.
- Deliverability (as a stakeholder): shared Resend reputation shielded by consent hygiene, throttling, and complaint handling.

## Functional requirements

1. Audience rule (absolute): sends go to `active` leads only (see audience doc) with marketing consent on record — suppressed, unsubscribed, bounced, and complained addresses are excluded at send time by query, not by trust. Pre-send count shown before confirm; operator confirms the exact count.
2. Compose-and-review flow: subject + preheader + HTML/plain-text body (shared sanitized template system — no raw HTML paste, no scriptable content), test-send to operator addresses, link tracking on (rewritten through the measurement redirect for click stats), then explicit two-step confirm (compose → review-with-count → send). No scheduling control in v1 (send-now only, per PLAN); drafts persist for review.
3. Content rules (server-enforced where possible): honest subject (no deceptive RE:/FWD:, no false urgency), physical/contact footer with sender identity, one-click List-Unsubscribe (mailto + URL) on every send, VN-compliant sender identification in copy. Prohibited content mirrors the ad-inventory prohibited list (adult, gambling, misleading earning claims) — platform broadcasts hold the same bar as sold inventory.
4. Stats via webhook: Resend event webhook records `campaigns{stats_json}` (delivered/open/clicked/bounced/complained per campaign) through the standard verified-webhook discipline (signature check, idempotent event ids, no grant-like side effects — stats only). Bounces and complaints auto-transition the address state in the audience doc (hard bounce → suppressed; complaint → complained + suppressed).
5. Reputation guardrails: per-campaign and per-day volume caps (config), complaint-rate threshold that auto-pauses further sends and pages ops, dedicated sending domain/subdomain separated from transactional receipts where deliverability practice demands (see Q2). A burned marketing sender must never take down grant/receipt delivery.
6. v1 exclusions (stated plainly per PLAN): no audience segments (active-all only), no scheduled/drip sends, no A/B subject testing (see experimentation doc for the future method), no affiliate/creator co-sends. Each exclusion names the future doc that will lift it.

## Data / API touches

- New tables `campaigns{id, subject, body_ref, audience_snapshot_count, status (draft|sending|sent|paused), stats_json}` + Resend event log (idempotent event ids); bodies in R2 (versioned, reviewed). Audience reads from the audience doc's tables at send time (live query, never a stale CSV). All PG via `worker/db/*`; RLS restricts to marketing/ops roles.
- Endpoints: ops campaign CRUD + test-send + confirm-send, `GET /api/v1/ops/campaigns/:id/stats`; public one-click unsubscribe (signed token, no login) + Resend event webhook (verified, stats-only). Sends execute via a queue worker with per-recipient idempotency (resumed batches never double-send).
- Suppression enforcement point: a single send-eligibility function shared by broadcasts and any future sender — new senders inherit suppression automatically.

## Acceptance criteria

- Eligibility drill: suppressed/unsubscribed/bounced/complained addresses excluded even when hand-added to a send list; pre-send count matches delivered-to set exactly.
- Two-step drill: no single action sends (confirm requires review state); paused mid-batch halts within seconds; resumed batch never double-sends (per-recipient idempotency).
- Stats drill: seeded deliveries/opens/clicks/bounces/complaints reconcile event-to-aggregate exactly; bounce auto-suppresses; complaint auto-suppresses + pages.
- Reputation drill: complaint-rate breach auto-pauses sends and pages; transactional receipt delivery provably unaffected during the pause.
- v1-exclusion drill: segment/schedule/A-B controls absent from UI and API (attempts 404/422, not silent no-ops).
- Unsubscribe drill: one click, no login, effective before the next send (verified by immediate re-query).

## Out of scope

- Audience states and list management (see `marketing/audience-lists.md`); consent classes and preference center (see `marketing/consent-preferences.md`).
- Transactional receipts and lifecycle reminders (their own docs/channels — shared suppression only); promo/affiliate/advertising content (their own minors); segments/scheduling/A-B (named future work); P5.

## Open questions

- Q1: Launch volume caps — per-campaign and per-day ceilings for the reputation runway?
- Q2: Sending-domain separation — dedicated marketing subdomain from day one, or shared until volume justifies it?
