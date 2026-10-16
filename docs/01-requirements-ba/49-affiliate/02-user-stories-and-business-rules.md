# Affiliate Tracking, Program, and Payouts - User Stories and Business Rules (feature 49) - GATED

Phase 1 BA · GATED: design-only; AC-01 (absence) runs every release, all other drills run at/after the amendment. Scope: `docs/01-requirements-ba/49-affiliate/01-document-context-and-scope.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Affiliate | share tracked links/codes and watch pending/confirmed commission states in near-real time | I trust the numbers before payout day | High |
| US-02 | Referred buyer | click a link or use a code and buy at normal prices | tracking never costs me money or friction | High |
| US-03 | Program operator | approve affiliates, set tiers, terminate abusers, answer "where is my commission" | the program stays out of fraud and brand trouble | High |
| US-04 | Finance | run the payout cycle and book commission expense off one ledger | payouts are boring, predictable, auditable | High |
| US-05 | Dispute reviewer | replay any attribution from stored evidence | credit arguments end in facts, not memory | Medium |

## Business rules

- **BR-01 Gate (LIVE).** Design-complete only: no affiliate table, route, job, chunk, or UI string may exist in any image or bundle until a spec amendment; absence drills run every release and a hit blocks promotion. No auto-approval code path may exist anywhere.
- **BR-02 Referral links.** `https://<host>/r/<affiliate_slug>-<random>` (unguessable suffix, no sequential ids) sets a first-party `HttpOnly`, `Secure`, `SameSite=Lax` cookie (`aff_id`, signed) on landing; deep-link targets are preserved through the redirect; slugs are vanity-stable and the random suffix rotates on affiliate request after leaks.
- **BR-03 Binding precedence (closed, server-enforced).** An affiliate promo code entered at checkout beats cookie attribution (explicit buyer intent); among multiple cookies the most recent qualifying click wins; direct traffic is unattributed. The winning `affiliate_id` stamps the order at creation — fixed pre-payment, never re-pointed afterward.
- **BR-04 Window.** Qualifying purchases within the window (default 30 days, config) attribute; outside, they don't — counted to the second from server-side click timestamps. The cookie is a pointer, not the record: server-side click logs are fallback evidence for review, never silent auto-credit.
- **BR-05 Commissionable state machine.** click → `pending` on order creation → `confirmed` on paid grant past the refund window → `reversed` on refund/dispute-loss (full or proportional to the refunded amount). Trial starts, 0-VND orders, cancelled/expired orders, and self-purchases (identity/signal match) never leave `pending`.
- **BR-06 Anti-gaming.** Cookie-stuffing signals (unattributed drops, hidden-iframe sets) rejected at ingest; self-referral voids the commission and flags the affiliate per the terms ladder; attribution rate-limits mirror checkout budgets so click floods cannot skew dashboards.
- **BR-07 Privacy.** First-party tracking only (no third-party pixels in v1); declined-consent traffic is simply unattributed — checkout is never blocked; affiliate-visible stats are aggregates + own referrals, never other buyers' PII.
- **BR-08 Enrollment (audited).** Application with identity + payout details + channel description; manual approval only in v1; statuses `applied|active|suspended|terminated`; suspension freezes new attribution while preserving earned-but-unpaid balances for review; termination forfeits only fraud-tainted earnings per the terms — never legitimate history without review.
- **BR-09 Tiers.** Config-driven and versioned (`affiliate.rates`); volume thresholds step next month's rate only, never retroactively within a month; rate changes apply to attributions after the effective date; in-flight attribution windows keep the old rate.
- **BR-10 Commission basis.** Commissionable events are paid plan grants and paid product orders; computed on the frozen rate snapshot stamped at attribution, on the finance-decided basis version; multi-item and partial-refund orders compute per-line and net down proportionally; integer VND; rounding (if any) favors the affiliate and is logged per line.
- **BR-11 Terms and conduct.** Terms versioned and acceptance-logged per affiliate; prohibited conduct (cookie stuffing, brand-bidding on trademarked keywords, spam, self-referral, pricing misrepresentation) triggers the suspension/termination ladder with written notice and an appeal window before forfeiture; material adverse changes trigger re-acceptance.
- **BR-12 Exposure guardrail.** Total commission exposure per period is capped in config (percentage of referred net revenue); a breach pauses new-accrual review — earned balances stay intact and visible; never silently underpays.
- **BR-13 Payout ledger.** Per-line states `pending → payable → in_payout → paid | failed` (+ `reversed`); monthly cycle: freeze eligible lines → instruct → execute → reconcile → mark paid; any failed line stays `in_payout` with an ops case, blocking nothing else; retries never duplicate payouts (batch-line idempotency).
- **BR-14 Payout rail (v1).** VN bank transfers from the operating account via the finance ops flow (batch instruction file + dual-control approval — same discipline as manual bank refunds); no new rail integration in v1; e-wallet/foreign rails are named future work with their own compliance review.
- **BR-15 Tax boundary.** Required identity/tax fields collected at enrollment; per-affiliate annual summaries produced; actual rates, forms, and filing are finance/legal confirmation — an affiliate without complete tax fields cannot enter a payout batch (blocked with a fix-it notice, earnings intact).
- **BR-16 Statements and support.** Per-cycle statement (lines, rates, reversals, threshold progress, payout reference) in dashboard + email; "missing payout" support starts from the statement id and ends in the ledger; post-payout reversals are notified debits against future payables — never a silent bank pull.
- **BR-17 Relationship boundary.** Affiliates are independent referrers: no discounting authority, no support obligations, no sub-affiliate tiers in v1.

## Acceptance criteria pointers

Drills in `docs/01-requirements-ba/49-affiliate/06-acceptance-criteria.md`: absence (live), precedence, window, reversal, stuffing, privacy, auth, enrollment, tier, conduct, buyer-price, cap, computation, cycle, duplicate-execution, tax. GWT forms live there.
