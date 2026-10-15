# Checkout Orchestration Frontend Plan (checkout + handoff + pending — UI build steps)

PLAN ref: P3 checkout UX (one endpoint, verbatim list, per-kind handoff render, one pending page, cancel-then-new switching). BA source: `docs/monetize/payment/checkout-orchestration.md`. Server truth in `docs/monetize/payment/checkout-orchestration-backend-plan.md` (eligibility, idempotency, kill switches — never redefined here). Shared components in `docs/monetize/overview/monetize-overview-frontend-plan.md` (`ProviderList`, `PendingPoll`, `StatusBadge`, `ProblemMoney` — composed here).

## Purpose

Give the frontend developer an ordered UI procedure for checkout: intent → verbatim provider list → per-kind handoff → pending poll → cancel/switch discipline. Follow steps 1–6 in order. The client renders eligibility, never decides it; sends explicit `provider`, never assumes one.

## Users

- Frontend developer: builds `/app/billing/checkout/*` + `/app/billing/pending/:orderCode`.
- QA: executes the matrix-display, handoff-kind, kill-switch-UX, and double-submit-button drills.
- Buyer: picks once, pays through the handoff, lands on one clear pending state.

## Functional requirements

1. Flow skeleton: intent screen (plan/product summary + server-quoted price via `POST quote` — displayed price comes from quote responses, never from page props) → provider step (`ProviderList` verbatim) → handoff step (per-`kind` renderer: `url` → continue-in-new-tab button + copy; `qr` → QR image + scan polling (the poll watches order status, not scan events); `sheet` → embedded rail sheet (Google/Apple Pay buttons appear ONLY when capability hints allow — no dead wallet buttons); `token` → client-secret handoff to the rail SDK) → pending redirect (`/app/billing/pending/:orderCode`).
2. Step 1 — quote display flow: intent changes re-quote (debounced; amounts render from responses; tamper-proofing is server-side — the client holds no price authority and shows `Final price confirmed at checkout` microcopy so quote-vs-charge equality is a promise the server keeps).
3. Step 2 — provider-step flow: `ProviderList` with disabled reasons (`Not available for this plan`, `Unavailable on this device`, `Temporarily unavailable` for killed rails — killed rails vanish from the list within seconds of a flip; a cached list mid-session refreshes on next fetch, never patched client-side). Omitted selection blocks continue (explicit choice is structural — no default selected, no first-item auto-pick; auto-pick would BE a default).
4. Step 3 — handoff flow per kind: `url` (open + copy + `I already paid` fast-path to pending); `qr` (render + polling starts immediately — payment may complete before any scan confirmation UI); `sheet` (rail sheet callbacks drive to pending on completion/cancel); `token` (SDK confirm → pending). All kinds converge on the pending page (no rail-specific success screens — convergence asserted by route absence).
5. Step 4 — pending flow: `PendingPoll` (3s, backoff, hidden-tab pause, terminal stop) renders canonical states via `StatusBadge`; provider return URLs land here (params ignored as state); cancel action visible on `pending|authorizing` → confirm → cancel response carries `next_action: new-checkout` (the switch path is guided, not discovered).
6. Step 5 — double-submit discipline: pay button disables on first click + sends `Idempotency-Key` (UUID per intent, regenerated per new intent — key reuse across intents is a client bug the server 422s, so the client scopes keys strictly); keyless fallback relies on server fingerprint dedupe (documented, not duplicated client-side).
7. Step 6 — return-URL safety: `returnUrl`/`cancelUrl` built same-origin under `/app/billing/{return,cancel}` with `orderCode` only (client builder function centralized — one function, reviewed once).

## Data / API touches

- Screen → endpoint map: intent → `POST quote`; choice → `GET payment-methods`; pay → `POST checkout` (+ `Idempotency-Key`); pending → `GET orders/:orderCode` (poll); cancel → `POST orders/:orderCode/cancel`. Session cookie + CSRF + Turnstile on pay.
- Type mirror: `CheckoutReq {provider, plan?|product_id?, buyer_email?, coupon_code?, returnUrl?, cancelUrl?}`; `Next {kind: url|qr|sheet|token, payload}`; `OrderView` (overview plan). No client eligibility matrix exists (grep-verified).

## Acceptance criteria

- Matrix-display drill: rendered list == API list verbatim across configs/devices (no auto-pick, no re-sort, no dead wallets).
- Handoff drill: all four kinds render and converge on pending (route matrix, no rail success screens).
- Kill-switch drill: mid-session flip removes the rail on next fetch with messaging intact.
- Double-submit drill: rapid clicks → one key, one row server-side (network proof).
- Forgery drill: return params never alter rendered status (poll truth only).
- Security drill: no amounts/eligibility trusted from client (fixture-tamper test).

## Out of scope

- Dispatch/idempotency/kill-switch logic (backend plan above); read contracts (database plan above); shared components (overview frontend plan); quote math/catalog/coupons; rail SDK internals; receipts/history/plan screens; P4/P5.

## Open questions

- Q1: QR handoff polling start — immediate (current) or post-scan-confirm?
- Q2: Expired-pending CTA copy — plain new-checkout or win-back variant?
