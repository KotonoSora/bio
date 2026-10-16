# Affiliate Tracking, Program, and Payouts - Security, Logging, and Rate Limiting (feature 49) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/49-affiliate/`. GATED: S-1 is live now; all other controls bind the future build at the spec amendment. Baseline controls: `docs/02-architecture-techlead/00-platform-baseline/`.

## Controls

| # | Control | Rule |
|---|---|---|
| S-1 | Gate enforcement (LIVE) | Tooling asserts zero affiliate surface each release (schema/OpenAPI/source/bundle); a hit blocks promotion — the gate is tool-enforced, not policy; no auto-approval code path may exist |
| S-2 | Attribution integrity | Resolver is the only credit path; precedence closed and server-enforced; winning id + evidence ref stamp orders at creation — no re-pointing route exists anywhere |
| S-3 | Evidence integrity | Clicks/attributions append-only; evidence bundles immutable; dispute replay reproduces the outcome from stored inputs alone |
| S-4 | Anti-gaming | Stuffing signals rejected at ingest; self-referral voids + flags; attribution rate budgets mirror checkout budgets; click floods cannot skew dashboards |
| S-5 | Privacy | First-party tracking only; declined consent unattributed without friction; aggregates + own referrals only; no buyer PII in affiliate payloads; hashes for ip/ua |
| S-6 | Money discipline | Integer VND; per-order rate snapshots immutable; rounding favors affiliate + logged; rates/thresholds/caps config-owned (zero hardcoded values — grep gate) |
| S-7 | Payout safety | Vault-encrypted payout details (never logged, never rendered back in full); dual-control batch execution; batch-line idempotency; failures open exactly one case; no silent bank pulls ever |
| S-8 | Due process | Conduct ladder: notice + appeal window + audit before forfeiture; forfeits only fraud-tainted rows; terms versions acceptance-logged; adverse material changes re-require acceptance |
| S-9 | IDOR + RLS | Own-affiliate reads only; ops/finance scoped roles; cross-affiliate probes → 403/404 with zero rows; no affiliate-writable rate or attribution fields exist |

## Rate limits (at amendment; Redis db 13)

| Surface | Limit | On breach |
|---|---|---|
| `/r/` redirects | 600/h/IP (edge budgets mirror checkout) | 429 / drop by rule |
| Application intake | 5/h/IP + Turnstile | 429 |
| Affiliate stats/earnings/statements | 300/h | 429 `throttled` |
| Ops actions (ladder, tiers, evidence) | 120/h/operator | 429 |
| Payout batch endpoints | 30/h/finance-ops | 429 |

## Degrade matrix (at amendment)

| Failure | Behavior |
|---|---|
| Resolver down | Checkout proceeds unattributed (never blocked, never guessed); alerts; no retroactive credit |
| Redirect cache down | Redirect falls back to direct lookup; latency degrades, correctness unchanged |
| Billing events backlog | Ledger states lag honestly (no fabricated states); affiliate sees last confirmed truth |
| Payout rail down | Batch stays `instructed`; nothing double-executes; ops case per failure |
| Notification pipeline down | Statements queue with backlog; ledger truth unaffected |
| Redis down | Rate limits degrade to conservative fixed windows; cookie signing unaffected (stateless) |

## Audit and observability (at amendment)

Audits (24-month): enrollment decisions, ladder actions with notice/appeal, tier config appends, batch freeze/instruct/confirm, reversals, forfeitures, evidence lookups, link rotations. Metrics: clicks, conversion rate, pending/confirmed/reversed counts, stuffing rejects, batch outcomes, exposure vs cap — zero buyer PII labels.

## Support answers (at amendment)

"Where is my commission?" → one ledger: attribution evidence + line state + linked reversal if any. "Why was I not credited?" → evidence bundle replay (precedence, window, or strict-state reason) — never memory. "Where is my money?" → statement id → batch → rail reference; failures show the ops case. "Why is my balance held?" → review state + appeal path; earned history untouched without review. "Why can't I get paid?" → tax fields fix-it notice; earnings intact.
