# Payment Security Frontend Plan (secure money-page patterns — UI build steps)

PLAN ref: S-2/S-5 buyer-side (XSS-free money rendering, CSP, abuse-shaped widgets) + secrets-never-in-bundle. BA source: `docs/monetize/payment/payment-security.md`. Server truth in `docs/monetize/payment/payment-security-backend-plan.md` (guards, abuse tiers, battery — never redefined here). Shared components in `docs/monetize/overview/monetize-overview-frontend-plan.md` (composed here).

## Purpose

Give the frontend developer the secure patterns for money pages: no sensitive data in DOM or bundles, XSS-free rendering of amounts/errors/receipts, abuse widgets on the right actions, and the proof that forged params and tampered payloads change nothing client-side. Follow steps 1–5 in order. The client is a renderer with server-verified inputs — never a validator, never a secret holder.

## Users

- Frontend developer: applies the five patterns to every money screen.
- QA: executes the DOM/bundle/forgery drills.
- Buyer: sees amounts, errors, and receipts that cannot leak or mislead.

## Functional requirements

1. Step 1 — zero sensitive data client-side: no PSP keys, private keys, tokens, or client secrets in bundles (build-time bundle-content test per release — a key-shaped string in any chunk fails the build); no PAN/cryptogram/token material in DOM, state, logs, or error reports (rail SDKs own card/wallet capture inside their iframes/sheets — our DOM never hosts card fields; an input named `cardNumber` in our code is a violation, grep-verified); receipt rendering uses provider references (`orderCode`, `vnp_TransactionNo`-class ids), never raw payment instruments.
2. Step 2 — XSS-free money rendering: amounts (integer VND formatting only — no HTML in price strings), error/quote/history text (server-composed strings rendered as text, never `innerHTML` — `status_detail`, problem details, and receipt line items all render through text bindings), CSP `default-src 'self'` on checkout/pending/history (worker-src/connect-src locked per the PWA backend pattern). A rendered-scripts test asserts zero script execution from hostile fixtures on every money screen.
3. Step 3 — abuse widgets on abuse-shaped actions: Turnstile on checkout submit, token-intake, report-abuse, and import-adjacent money actions (invisible widget, blocking submit until verified — failed verification disables pay with `Verification needed`, never a silent drop); rate-limit `429` surfaces as cooldown + retry timer (shared with the overview `ProblemMoney` component).
4. Step 4 — forgery inertness: return/query params never drive state (pending renders poll truth only — overview contract, reasserted here because money screens are the forgery target); client amount displays re-fetched from quote responses (no prop-drilled prices); disabled/ineligible rails never client-enabled (no devtools affordance — server decides, UI reflects).
5. Step 5 — receipt/history honesty: history rows render canonical statuses via `StatusBadge` (no rail jargon); receipt re-download opens print-friendly HTML (same no-server-PDF guardrail as resumes); support links carry the receipt id (`RCPT-<orderCode>`) so disputes start traced.
6. Build gates: bundle secret-grep + card-field-name grep + rendered-scripts test run in CI per release (three green checks, not review items).

## Data / API touches

- No new endpoints (patterns apply to existing money screens' calls). Type rule: no `cardNumber|pan|cvv|cryptogram|clientSecret`-persisted client types beyond the transient rail-SDK handoff (handoff values live in SDK callbacks, never in app state — asserted by state-shape test).

## Acceptance criteria

- Bundle drill: secret patterns + card-field names absent from all chunks (CI gate).
- DOM drill: hostile fixtures render inert on checkout/pending/history/receipt screens; CSP holds.
- Widget drill: Turnstile present on pay/token-intake/report submits; failure disables with reason.
- Forgery drill: param tampering alters zero rendered state (poll-truth proof).
- Receipt drill: references without instruments; support links carry receipt ids.

## Out of scope

- Guards/abuse/battery logic (backend plan above); enforcement inventory (database plan above); shared components (overview frontend plan); state rendering map (status-model frontend plan); rail SDK internals; P4/P5.

## Open questions

- Q1: Turnstile invisible-only, or visible challenge on repeated failures (escalating friction)?
- Q2: Receipt PII minimization — account email always shown, or masked with reveal (privacy vs support speed)?
