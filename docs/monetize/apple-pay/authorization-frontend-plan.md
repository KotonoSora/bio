# Apple Pay Authorization Frontend Plan (sheet states + verdict mapping — UI build steps)

PLAN ref: P3-2 sheet UX (validated session, biometric auth, verdict-driven close, failure copy without gateway codes). BA source: `docs/monetize/apple-pay/authorization.md`. Server truth in `docs/monetize/apple-pay/authorization-backend-plan.md` (validation, charge, webhook — never redefined here).

## Purpose

Give the frontend developer the sheet-state procedure: validating → authenticating → authorized → verdict-close, with exact failure copy per outcome and the proof that no gateway jargon reaches buyers. Follow steps 1–5 in order. Our UI shows nothing during biometric auth (overlaying Apple's sheet is forbidden by Apple and by this plan) — before and after the sheet is where our UX lives.

## Users

- Frontend developer: wires sheet events to server verdicts with buyer-safe copy.
- QA: executes validation-failure, verdict-mapping, and timeout UX drills (Apple sandbox).
- Buyer: authenticates once, sees success or an actionable failure — never a hung sheet or a gateway code.

## Functional requirements

1. Sheet-state map (our side of the sheet lifecycle): `requesting-validation` (spinner copy `Contacting Apple Pay…`, cancellable — cancel aborts to provider choice, no order harmed) → `authenticating` (silent — sheet owned by Apple, our DOM shows the pre-sheet state frozen, no skeletons swapping underneath) → `authorized-pending-verdict` (brief `Confirming payment…` — bounded by transport timeout, then failure path, never indefinite) → closed with verdict.
2. Step 1 — validation-failure flow: bad cert/expired session/slow validation → server fails closed → `completePayment` never reached → UI shows `Apple Pay isn't available right now — try another method` with sibling options (failure of validation is presented as unavailability, never as user error — blaming the buyer for our cert is the trust-killer this copy avoids).
3. Step 2 — verdict-mapping flow: grant → `completePayment(SUCCESS)` → pending page (shared component — no Apple success screen); decline → `completePayment(FAILURE)` + inline reason from the server's buyer-safe allowlist (`Card declined`, `Amount mismatch — please retry`, never raw gateway codes — allowlist enforced by rendering `reason` verbatim from a closed server set); transport/timeout → `completePayment(FAILURE)` + `Couldn't confirm — check status` deep-linking pending (ambiguity routed to truth, not guessed).
4. Step 3 — timeout UX: authorize call bounded client-side (matching server timeout + margin) → on timeout, FAILURE + pending-link (the order sits `pending`/`authorizing` for sweep resolution — the UI says `We're still checking — see live status`, never `failed` and never `paid`).
5. Step 4 — double-event discipline: `onpaymentauthorized` fires once per sheet by Apple contract; our handler is idempotent anyway (disabled state + single post; server dedupes regardless — both layers, same guarantee).
6. Step 5 — accessibility: sheet lifecycle changes announced (validation failure, verdict) via live regions on OUR page (the sheet itself is Apple's accessibility surface, not ours); focus returns to a sensible control after sheet close in all outcomes (no focus loss into the void).

## Data / API touches

- Sheet events → endpoints map: `onvalidatemerchant` → `POST validate-merchant`; `onpaymentauthorized` → `POST authorize` (once); close → pending poll. Verdict enum consumed: `SUCCESS|FAILURE` + server reason string (closed set, rendered verbatim).
- No client session/token caching exists (sheet-scoped values die with the sheet — storage inspection clean).

## Acceptance criteria

- States drill: all five sheet states render specified copy; biometric phase shows frozen pre-sheet UI.
- Failure drill: validation failure → unavailability copy + siblings (never user-blame); verdicts map exactly (grant/decline/timeout).
- Timeout drill: bounded FAILURE + pending-link; no hung sheet (timer proof).
- Jargon drill: zero gateway codes in DOM across outcomes (snapshot test).
- Focus drill: sensible focus restoration on every close path.

## Out of scope

- Validation/charge/webhook logic (backend plan above); evidence contracts (database plan above); button gating + token posting (checkout frontend plan); pending/sweep/refund UI (pending-refund frontend plan).

## Open questions

- Q1: `Contacting Apple Pay…` copy — brand naming required by Apple guidelines review?
- Q2: Retry affordance on decline — immediate re-sheet button, or route through provider choice?
