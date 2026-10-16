# Affiliate Tracking, Program, and Payouts - Exception Handling and Errors (feature 49) - GATED

Phase 1 BA · GATED: at amendment; problems follow RFC 9457 (`code` field). No buyer PII, payout details, or cross-affiliate data leak beyond the caller's own scope.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Rates catalog absent | API (activation) | 409 `rates_incomplete`; nothing invented | Ops: "Set affiliate rates before activating." |
| Purchase outside the window | System | Unattributed; evidence retained for review only | None (silent, by rule) |
| Direct purchase | System | Unattributed — never guessed | None |
| Cookie cleared mid-window | Review | Server-side click logs serve as fallback evidence for disputes; never silent auto-credit | Reviewer view only |
| Declined-consent buyer | Checkout | Unattributed; checkout proceeds normally, zero friction | None |
| Self-purchase detected | System | Commission voided; affiliate flagged per terms ladder | Affiliate: "Self-referrals don't earn commission." |
| Cookie-stuffing signals | Ingest | Rejected; dashboards unskewed; warning per ladder | Affiliate notice per terms |
| Click flood | Edge | Rate budgets mirror checkout budgets; 429 | None (throttle) |
| Affiliate reads another affiliate's rows | API | 403/404 via RLS; zero rows | "Not found." |
| Affiliate attempts to set rates/tiers | API | No route exists (by absence) | "Not available." |
| Application without payout/tax fields | Flow | Accepted as `applied`; activation blocked until complete | "Add payout and tax details to go live." |
| Suspension with unpaid balance | System | Balance held and visible; new attribution frozen | "Your account is under review." |
| Refund after confirmation | System | Line `reversed` (proportional on partials) with linked reference | Affiliate sees the reversal line |
| Post-payout reversal | System | Notified debit against future payables — never a silent bank pull | "A refund reversed a paid commission; see statement." |
| Tax fields incomplete at batch time | Batch | Line blocked from the batch with fix-it notice; earnings intact | "Complete tax details to receive payout." |
| Rail failure per line | Batch | Stays `in_payout` + exactly one ops case; batch continues | Ops view only |
| Duplicate batch confirm | API | Idempotent no-op; double-instruct impossible while frozen | None |
| Exposure-cap breach | System | New-accrual review pauses; earned balances visible | Ops page; affiliates see no silent change |
| Terms version change (material adverse) | System | Re-acceptance required before further earnings accrue | "Review the updated program terms." |
| Buyer probes affiliate data | API | No buyer-visible affiliate surface exists | None |
