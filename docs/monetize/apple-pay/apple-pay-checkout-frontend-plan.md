# Apple Pay Checkout Frontend Plan (button gating + sheet flow — UI build steps)

PLAN ref: P3-1 checkout UX (capability-gated button, validated sheet, verdict-driven close, sibling fallback). BA source: `docs/monetize/apple-pay/checkout.md`. Server truth in `docs/monetize/apple-pay/apple-pay-checkout-backend-plan.md` (order creation, sheet params, intake validation — never redefined here).

## Purpose

Give the frontend developer an ordered UI procedure for Apple Pay checkout: capability detection with graceful fallback, the validated sheet sequence, verdict-driven close, and pending handoff. Follow steps 1–6 in order. The button appears only where it can work — a dead Apple Pay button is a conversion and trust bug, not a cosmetic one.

## Users

- Frontend developer: builds the Apple Pay button + sheet orchestration + pending handoff.
- QA: executes capable/incapable-device, tamper-display, and double-click drills (Apple sandbox + PSP sandbox).
- Buyer: pays by biometrics on capable devices; gets working alternatives everywhere else.

## Functional requirements

1. Capability contract: render the Apple Pay button ONLY when `ApplePaySession.canMakePayment()` (prefer `canMakePaymentsWithActiveCard` where available for the `Set up Apple Pay` vs `Pay` distinction). Detection runs at checkout-mount and on visibility regain (device capability doesn't change mid-session, but browser context can — re-check, don't cache forever). Incapable contexts render sibling-provider options with zero Apple Pay chrome, zero console errors (asserted in test with capability mocked off).
2. Step 1 — button flow: eligible intent + capable device → button (label `Pay with Apple Pay`, amount from the server quote — displayed total comes from quote responses, never page props) → click disables the button immediately (double-submit discipline starts here; server idempotency is the backstop, not the plan).
3. Step 2 — sheet flow: build `ApplePayPaymentRequest` EXACTLY from the checkout response (`countryCode: VN`, `currencyCode: VND`, capabilities, networks, `total` — no client-side amount assembly; tampered totals die server-side, but the client never tampers in the first place) → `session.begin()` → `onvalidatemerchant` calls OUR validation endpoint with Apple's `validationURL` untouched → `completeMerchantValidation` with the opaque server session → buyer authenticates (biometrics — our UI shows nothing during the sheet; overlaying the sheet is forbidden by Apple and by this plan).
4. Step 3 — authorize flow: `onpaymentauthorized` posts `{orderCode, token}` to the authorize endpoint → server verdict drives `completePayment(SUCCESS|FAILURE)` (verdict mapping: grant → SUCCESS; decline/validation-failure → FAILURE with the server's buyer-safe reason rendered beside the button; transport error → FAILURE + `Check status` deep-link to pending — never an optimistic SUCCESS).
5. Step 4 — pending handoff: sheet close (any outcome) routes to `/app/billing/pending/:orderCode` (shared `PendingPoll` from the overview frontend plan — no Apple-specific pending screen exists to drift). Forged success states impossible: the page polls truth, the sheet delivered only a token.
6. Step 5 — sibling fallback flow: incapable device OR `canMakePayment` false OR Apple Pay killed by ops (absent from `payment-methods`) → provider step shows siblings with unchanged intent (plan/product/coupon preserved across the switch — intent loss on fallback is a conversion bug, tested by switching mid-flow in fixtures).
7. Step 6 — edge cases: sheet dismissed without payment (order stays `pending`, reusable for a fresh sheet — stated as `You can try again`); validation-session expiry mid-sheet (server fails closed → FAILURE + `Try again`, never a hung sheet); expired session mid-flow (re-login preserves intent, requires sheet restart — tokens don't survive relogin by design).

## Data / API touches

- Screen → endpoint map: intent → `POST quote`; button render → capability APIs (client) + `GET payment-methods` (server filter is the gate); pay → `POST checkout` → sheet params; merchant validation → `POST validate-merchant`; token → `POST authorize`; landing → `GET orders/:orderCode` (poll). Session cookie + CSRF + Turnstile on pay.
- Type mirror: `ApplePayParams {merchant_id, country_code, currency_code, total, merchant_capabilities, supported_networks}` (rendered, never edited); `TokenPost {orderCode, token{paymentData, transactionIdentifier, paymentMethod}}` (posted once, never stored client-side beyond the call).

## Acceptance criteria

- Gating drill: capable → button; incapable → siblings, zero errors (mocked both ways).
- Sheet drill: request built verbatim from server params (diffed in test); validation always server-side (no direct Apple calls in bundle — grep).
- Verdict drill: grant→SUCCESS, decline→FAILURE+reason, transport→FAILURE+pending-link (all three mapped, none defaulted).
- Pending drill: all outcomes land on the shared pending page polling truth.
- Fallback drill: mid-flow switch preserves intent (fixture test).
- Security drill: no amounts assembled client-side (tamper fixture changes nothing); no token persisted (storage inspection clean).

## Out of scope

- Order/intake/validation logic (backend plan above); column/file contracts (database plan above); merchant validation + charge mechanics (authorization plans); pending/sweep/refund UI (pending-refund frontend plan); plan matrix; shop delivery; other rails.

## Open questions

- Q1: `canMakePaymentsWithActiveCard` gating for the button label — worth the extra check latency?
- Q2: Sheet `total.label` copy — merchant name only, or plan/product descriptor (localization call)?
