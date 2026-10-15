# Order Status Model Frontend Plan (canonical state rendering — UI build steps)

PLAN ref: P3 buyer-facing state vocabulary (one consistent rendering per canonical state on pending, history, and receipt surfaces). BA source: `docs/monetize/payment/order-status-model.md`. Server truth in `docs/monetize/payment/order-status-model-backend-plan.md` (edges, authority — never redefined here). Shared components in `docs/monetize/overview/monetize-overview-frontend-plan.md` (`StatusBadge`, `PendingPoll`, `ProblemMoney` — composed here, never redefined).

## Purpose

Give the frontend developer the per-state rendering specification for orders: what each canonical state looks like on pending/history/receipt surfaces, which actions each state offers, and the proof that provider-native jargon never reaches buyers. Follow steps 1–5 in order. States render from `{status, status_detail}` only — the client holds no transition logic and no native-code dictionary.

## Users

- Frontend developer: renders order states with shared components + this state map.
- QA: executes the per-state rendering drill across all nine states.
- Buyer/support: sees and explains the same words for the same states everywhere.

## Functional requirements

1. State map (nine states, three surfaces — pending page, history rows, receipt views): `pending` (waiting copy + active poll + cancel action); `authorizing` (confirming copy + active poll, no cancel — card capture in flight, stated); `paid` (success + receipt link + what-happens-next per intent: plan → pro features note, product → delivery note); `failed` (decline copy + `Try another method` deep-linking provider choice with the same intent preserved); `cancelled` (`Cancelled` + `Start a new checkout`); `expired` (`Link expired` + new-checkout CTA, never a pay-this-link affordance); `refunded` (refund copy + credit-note link, original receipt annotated not edited); `disputed` (`Under review` + support contact, no pay/retry actions — frozen means frozen in UI too); `chargeback_reversed` (reversal copy + support contact). Each state maps to fixed copy (overview `StatusBadge` tokens) + exactly the actions listed (an action outside this map — e.g. retry on `expired` — is a contract violation).
2. Step 1 — pending/history binding: screens poll/read `{status, status_detail}` and render the map; `status_detail` human strings render verbatim (server-composed, never client-templated from codes — templating would reintroduce jargon drift).
3. Step 2 — terminal-state discipline: terminal states stop polling (no background pollers on dead orders — the self-DDoS rule from the overview plan applies per order page); `paid` additionally stops re-quote affordances (no double-pay path from a success screen).
4. Step 3 — jargon quarantine: provider-native codes (VNPay response codes, ZaloPay query results, Stripe intent states, rail sync notes) are test-asserted absent from DOM snapshots on every surface (captured-payload rendering test). `status_detail` is the ONLY dynamic string near money states, and it is server-composed.
5. Step 4 — dispute-frozen UX: `disputed`/`chargeback_reversed` surfaces show support contact + receipt references with zero payment actions (no retry, no new-checkout cross-sell on frozen money — restraint is the requirement).
6. Step 5 — accessibility: state changes announced to screen readers (poll-driven updates use live regions, not silent swaps); color never the sole state signal (badge copy always present).

## Data / API touches

- Screens read `{status, status_detail}` from order endpoints (owned by the checkout-session slice). No client state machine exists (grep-verified: no `status ===` transition logic in client code — rendering switch only).
- Type mirror: `OrderState` nine-value union matching the canonical enum (drift fails the rendering test, which enumerates the union exhaustively).

## Acceptance criteria

- Map drill: all nine states render specified copy + actions on all three surfaces (screenshot matrix).
- Terminal drill: polling stops on terminal states (request-log proof); success screens offer no re-pay path.
- Jargon drill: zero provider-native strings in DOM snapshots across surfaces.
- Frozen drill: disputed views show support + receipts with zero payment actions.
- Exhaustiveness drill: adding a tenth canonical state breaks the rendering test until this map covers it (compiler-assisted completeness).

## Out of scope

- Edges/authority/guard (backend plan above); enum contracts (database plan above); shared components (overview frontend plan); pending-page polling mechanics (checkout-session frontend plan); receipt content (invoices-receipts plans).

## Open questions

- Q1: `authorizing` cancel action — ever allowed (void-while-capturing rails), or never (current)?
- Q2: Expired-state win-back copy (discount nudge) — conversion lever or restraint (current: plain new-checkout)?
