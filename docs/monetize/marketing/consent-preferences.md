# Consent + Preferences (marketing permission model)

PLAN ref: P2-4 (`consent_at` precedent) and P4-4 (broadcasts consume this model). Enforcement point for every sender in the product; preference UX lives here, capture mechanics stay in their own majors.

## Purpose

Define the single permission model every marketing send in the product obeys: consent classes, capture proof, a preference center, and enforcement that makes unconsented sends structurally impossible rather than merely discouraged. Receipts, reminders, broadcasts, win-back, and promos each already claim "consent-gated" in their own docs — without this shared model those claims mean different things per sender, and the first complaint investigation finds gaps instead of proof.

## Users

- Lead/buyer: *As a Lead/buyer, I want to give, refine, or withdraws marketing permission in plain language, with proof available on request so that the single permission model every marketing send in the product obeys as defined here.* gives, refines, or withdraws marketing permission in plain language, with proof available on request.
- Marketer/operator: *As a Marketer/operator, I want to send only to the provably willing, and can prove it per address on demand so that the single permission model every marketing send in the product obeys as defined here.* sends only to the provably willing, and can prove it per address on demand.
- Complaint investigator: *As a Complaint investigator, I want to replay any address's consent history as evidence so that the single permission model every marketing send in the product obeys as defined here.* replays any address's consent history as evidence.

## Functional requirements

1. Consent classes (closed, product-wide): `transactional` (receipts, grant confirmations, security notices — never gated, never unsubscribable, narrowly scoped to the triggering event), `lifecycle` (reminders, trial/expiry/dunning notices tied to the user's own account — opt-out allowed, account-deletion ends them), `marketing` (broadcasts, promos, win-back, affiliate/program news — explicit opt-in required, one-click out). Every send in the product declares exactly one class; misclassified sends are treated as consent violations in review.
2. Capture and proof: marketing opt-in captured with timestamp, source (form block, checkout checkbox — unchecked by default, never pre-ticked), and copy version shown; stored as `consents{owner_key, class, status (granted|withdrawn), captured_at, source, copy_version}` (append-only history, never overwritten). Checkout and lead forms default to unchecked marketing boxes; withdrawing is always easier than granting (one click vs a form).
3. Preference center (no login required via signed token, plus in-account view): per-class toggles with honest consequences stated ("off = no promo emails; receipts and security notices continue"), effective immediately on save, with a confirmation receipt of the change itself. Account deletion withdraws everything and suppresses the address (re-opt-in starts clean history, never restores old grants).
4. Enforcement architecture: the single send-eligibility function (owned here, used by broadcasts and all future senders) checks class + status + suppression at send time; new senders cannot ship without calling it (review gate). Campaign-time re-checks (eligibility at confirm AND at send — consent withdrawn between quote and send is honored).
5. Complaint and proof flow: one-click unsubscribe beats the complaint form to the punch; every complaint triggers automatic suppression + a consent-history review case; proof-of-consent export per address available to support within minutes (timestamps, sources, copy versions).
6. Cross-border posture: the model defaults to the strictest applicable posture (explicit opt-in for marketing, provable records, honored withdrawals) without naming a specific statute in code — legal maps jurisdictions to this model once, and the model enforces uniformly afterward.

- 7. Performance/scale NFR (derived from rules 3-5 above): preference saves take effect immediately with a confirmation receipt, proof-of-consent exports complete within minutes, and send eligibility is re-checked at confirm and at send — no new thresholds.

## Data / API touches

- New tables `consents` (append-only history per class) + `suppressions{address_hash, reason, at}` (global, checked by every sender). Preference tokens signed, single-purpose, expiring. All PG via `worker/db/*`; RLS posture (owners manage own consents; support reads proof; marketing reads aggregates only).
- Endpoints: public preference center (signed token) + one-click unsubscribe (signed, no login), in-account preferences API, ops proof-of-consent lookup, send-eligibility check (internal, mandatory). All consent changes append history + audit.
- Suppression precedence: suppression beats any grant (a suppressed address with a stale `granted` row still sends nothing); re-opt-in lifts suppression only through the explicit re-consent flow, never automatically.

## Acceptance criteria

- Classification drill: sample sends across all channels carry exactly one correct class; misclassified send caught by review checklist with a defined remediation.
  *GWT: Given Classification drill context; When sample sends across all channels carry exactly one correct class; Then misclassified send caught by review checklist with a defined remediation.*
- Proof drill: any address's full consent history (grants, withdrawals, sources, copy versions) exportable in minutes; complaint case resolves from history alone.
  *GWT: Given Proof drill context; When any address's full consent history (grants, withdrawals, sources, copy versions) exportable in minutes; Then complaint case resolves from history alone.*
- Withdrawal drill: one-click out effective before the next send (immediate re-query proof); pre-ticked boxes absent from every form (UI + API review gate).
  *GWT: Given Withdrawal drill context; When one-click out effective before the next send (immediate re-query proof); Then pre-ticked boxes absent from every form (UI + API review gate).*
- Enforcement drill: new sender integrated without the eligibility call fails review; suppressed addresses receive nothing from any sender under load test.
  *GWT: Given Enforcement drill context; When new sender integrated without the eligibility call fails review; Then suppressed addresses receive nothing from any sender under load test.*
- Deletion drill: account deletion withdraws all classes, suppresses the address, and preserves only the audit-minimal proof rows the retention policy names.
  *GWT: Given Deletion drill context; When the checked action occurs; Then account deletion withdraws all classes, suppresses the address, and preserves only the audit-minimal proof rows the retention policy names.*

## Out of scope

- Broadcast mechanics and stats (see `marketing/campaign-broadcasts.md`); audience states and lists (see `marketing/audience-lists.md`).
- Lead capture UX (their own majors — this doc constrains their consent fields); transactional receipt content (their docs); legal jurisdiction mapping (legal-owned input); P4/P5.

## Open questions

- Q1: Lifecycle opt-out granularity — one lifecycle toggle, or per-topic (reminders vs trials vs dunning) from day one?
- Q2: Re-consent after suppression — cooling period before an address may re-opt-in (abuse vs UX trade-off)?
