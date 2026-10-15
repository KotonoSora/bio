# Provider Selection (explicit choice across eight rails)

PLAN ref: P3-1 (multi-provider checkout; PayOS sandbox named as PLAN path). DEVIATION FLAGGED: PLAN's PayOS-default reading is not implemented — no provider is the default; the buyer (or caller) always selects explicitly from the registry below. Session lifecycle in `checkout/checkout-session.md`; dispatch in `payment/checkout-orchestration.md`; statuses in `payment/order-status-model.md`; baseline in `payment/payment-security.md`.

## Purpose

Name the complete set of payment rails checkout serves and lock the no-default contract: eight specified providers, explicit `provider` on every create, config-driven eligibility and display order, and a registry-driven path for adding a ninth rail without touching selection logic. If a provider is not in this registry, checkout cannot offer it — that is the accommodation guarantee.

## Users

| Role | Permissions | Notes |
|---|---|---|
| Buyer | See every eligible rail, choose one, pay through its handoff | Checkout cannot offer what is not in the registry |
| Frontend checkout | Render the eligible list verbatim, send chosen `provider` back explicitly | Buyer always selects explicitly from the registry |
| Ops/support | Enable/disable rails and tune display order without deploys | Answers "why isn't X offered" from config + capability rules |

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Buyer | See every eligible rail for my intent, choose one, and pay through its handoff | Checkout cannot offer what is not in the registry | Must |
| US-02 | Frontend checkout | Render the eligible list verbatim and send the chosen `provider` back explicitly | The buyer always selects explicitly from the registry | Must |
| US-03 | Ops/support | Enable/disable rails and tune display order without deploys | I can answer "why isn't X offered" from config + capability rules | Must |

## Functional requirements

1. Registry (closed set, eight members): `payos` (`payos/checkout-orders.md`, payment-link URL), `sepay` (`sepay/checkout-orders.md`, bank-transfer/QR), `vnpay` (`vnpay/checkout-orders.md`, gateway URL), `zalopay` (`zalopay/checkout-orders.md`, gateway order), `napas` (`napas/checkout-orders.md`, dynamic VietQR), `googlepay` (`google-pay/checkout.md`, tokenized sheet), `applepay` (`apple-pay/checkout.md`, tokenized sheet), `stripe` (`stripe/checkout-orders.md`, PaymentIntent — non-VND/international use; never the VND default). Each member's request/response/verification mechanics live in its minor; this doc owns only membership and selection.
2. Explicit-choice contract: `provider` is required on `POST /api/billing/checkout`; omitted or unknown → 422 with `{code: provider_required|unknown_provider, eligible[]}`. No silent fallback to any member, including PayOS. Quote accepts an optional `provider` (single-rail preview) or none (all-eligible preview) and never creates rows.
3. Eligibility matrix: per-request eligible set = registry ∩ plan/product × provider config ∩ kill-switch survivors ∩ capability filter (e.g. Google Pay/Apple Pay only on capable devices/browsers, card rails only where enabled). Filtering is server-side; the client renders what the API returns, in the returned order.
4. Display order (no default): sourced from ops config (named ordering profile, adjustable without deploy) or request-time sort input; when config is absent the API returns a stable order and the client renders it verbatim. No code-level provider-first constant exists anywhere (grep-gated).
5. Capability hints: `GET /api/billing/payment-methods` returns per-member `{provider, display_name, handoff_kind: url|qr|sheet|token, disabled_reason?}` so the client can show device-gated wallets correctly without deciding eligibility itself.
6. Degradation: per-provider kill switch removes the member from the eligible set within seconds; in-flight pendings follow that member's settlement path with buyer messaging (owned by orchestration + member minor). All flips audited.
7. Ninth-rail path: adding a provider means a new specified minor + one registry entry + eligibility-config rows + conformance to the uniform `next` shape and status map — no selection/dispatcher contract change.

## Data / API touches

- No new tables. Reads: provider registry (code constant), eligibility config, kill-switch state (config/KV fast, audited), plan/product catalogs for matrix cells. Writes: none (selection creates nothing; creation belongs to `checkout/checkout-session.md`). All PG via `worker/db/*`.
- Endpoints: `GET /api/billing/payment-methods` (eligible list + order + hints; Turnstile/rate-limited where anonymous), `POST /api/billing/quote` (preview), `POST /api/billing/checkout` (explicit `provider` enforced). Provider callbacks keep their member-minor URLs.
- Secrets: per-member PSP keys via `wrangler secret` (`.dev.vars` locally; never committed/logged/bundled); `.gitignore` guards `.wrangler/`, `.dev.vars*`. VND amounts stay integer throughout (VNPay ×100 normalized at the edge per security baseline).

## Acceptance criteria

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Eight-rail matrix drill: each plan/product × registry-member cell allowed or rejected per config, verified via API on all eight checkout docs' paths; no member reachable when ineligible | Plan/product × registry-member config | Each cell is verified via API on all eight checkout paths | Allowed cells pass, rejected cells fail, and no ineligible member is reachable |
| AC-02 | No-default drill: omitted `provider` on create returns 422 with eligible list on every intent type; unknown code returns 422; grep gate finds no hardcoded provider default or fallback ordering in checkout code | A create with omitted or unknown `provider` on any intent type | Checkout is created and the grep gate scans checkout code | Omitted `provider` returns 422 with the eligible list, unknown codes return 422, and the grep gate finds no hardcoded default or fallback ordering |
| AC-03 | Order-verbatim drill: with display config absent, client renders API order exactly (no re-sort to a favorite); with config present, rendered order matches config | Display config absent or present | The eligible list is rendered | Absent config renders API order exactly and present config renders the configured order |
| AC-04 | Capability drill: incapable device/browser excludes wallet members server-side; capable one includes them with correct `handoff_kind` | An incapable or capable device/browser | Eligibility is evaluated | The incapable device excludes wallet members server-side and the capable one includes them with correct `handoff_kind` |
| AC-05 | Kill-switch drill: disabling any one of the eight removes it from the list within seconds with audited flip and buyer messaging intact | Any one of the eight disabled | The list is read | It is removed within seconds with audited flip and buyer messaging intact |
| AC-06 | Baseline: IDOR 403 cross-user on member-scoped order reads/cancels with RLS deny (S-1/S-6); error/quote rendering XSS-stripped with CSP holding (S-2); anonymous endpoints Turnstile + WAF throttled (S-5); secrets grep clean with quarterly rotation (P5-4) | Cross-user member-scoped order reads/cancels, rendered error/quote output, anonymous endpoints, and repo under secrets grep | IDOR, XSS, abuse, and secrets probes run | Cross-user access returns 403 with RLS deny (S-1/S-6), rendering is XSS-stripped with CSP holding (S-2), anonymous endpoints are Turnstile + WAF throttled (S-5), and secrets grep stays clean with quarterly rotation |

## Out of scope

- Member handoff build/verify/grant/sweep mechanics (each member minor); dispatcher internals beyond selection (see `payment/checkout-orchestration.md`); canonical transitions (see `payment/order-status-model.md`); probe battery (see `payment/payment-security.md`).
- Session quote/create/expire/return lifecycle (see `checkout/checkout-session.md`); ledger/lifecycle/receipts (`billing/`); catalog/coupons (`subscription/`, `promo-code/`); P4/P5.

## Open questions

- Q1: Display-order sourcing — static ops-configured order vs experiment-driven order, and the empty-config behavior (proposed: API order verbatim)?
- Q2: Wallet capability detection — user-agent + PaymentRequest API signals, or explicit client capability hints (and who is trusted)?
- Q3: Ninth-rail acceptance bar — specified minor + matrix conformance + probe battery green before registry entry, or provisional entry behind kill switch?
