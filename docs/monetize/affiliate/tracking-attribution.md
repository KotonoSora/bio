# Affiliate Tracking + Attribution (links, windows, precedence)

PLAN ref: none — new scope beyond PLAN P0–P5, flagged (see `affiliate/affiliate-program.md` for placement). Enrollment/tiers there; computation/payout in `affiliate/commission-payout.md`.

## Purpose

Define exactly which affiliate gets credit for which paid order: signed referral links, cookie + code binding, attribution windows, precedence when signals conflict, and reversal on refund/dispute. Attribution arguments are the number-one affiliate dispute category industry-wide — this doc makes every credit decision reproducible from stored evidence instead of debatable from memory.

## Users

- Referred buyer: *As a Referred buyer, I want to click a link or use an affiliate code; buy at normal prices with no visible tracking friction beyond standard consent so that exactly which affiliate gets credit for which paid order as defined here.* clicks a link or uses an affiliate code; buys at normal prices with no visible tracking friction beyond standard consent.
- Affiliate: *As an Affiliate, I want to see clicks, conversion, and pending/confirmed commission states in near-real time so that exactly which affiliate gets credit for which paid order as defined here.* sees clicks, conversions, and pending/confirmed commission states in near-real time.
- Dispute reviewer: *As a Dispute reviewer, I want to replay any attribution from stored evidence (link click, code, timestamps) so that exactly which affiliate gets credit for which paid order as defined here.* replays any attribution from stored evidence (link click, code, timestamps).

## Functional requirements

1. Referral links: `https://<host>/r/<affiliate_slug>-<random>` (unguessable suffix, no sequential ids) setting a first-party, `HttpOnly`, `Secure`, `SameSite=Lax` cookie (`aff_id`, signed value, 30-day default window per Q1) on landing; deep-link targets preserved through the redirect to profile/checkout pages. Link slugs are vanity-stable; the random suffix rotates on affiliate request after leaks.
2. Binding precedence (closed, server-enforced): an affiliate promo code entered at checkout beats cookie attribution (explicit buyer intent); cookie beats nothing (direct traffic unattributed); most recent qualifying click wins among multiple cookies. The winning `affiliate_id` stamps the `orders` row at creation — attribution is fixed pre-payment and never re-pointed afterward.
3. Attribution window: qualifying purchases within the window of the click (default 30 days) attribute; outside, they don't. Window counted from click timestamp stored server-side (cookie is a pointer, not the record — clearing cookies mid-window keeps server-side click logs as fallback evidence for review, never for silent auto-credit).
4. Commissionable-state machine: click → `pending` on order creation → `confirmed` on paid grant past the refund window → `reversed` on refund/dispute-loss (full or proportional to the refunded amount). Trial starts, 0-VND orders, cancelled/expired orders, and self-purchases (affiliate = buyer identity/signal match) never leave `pending`.
5. Anti-gaming: cookie-stuffing signals ( unattributed drops, iframe-hidden sets) rejected at ingest; self-referral voids the commission and flags the affiliate (per program terms ladder); attribution rate-limits mirror checkout budgets so click floods can't skew dashboards.
6. Privacy and consent: tracking is first-party only (no third-party pixels in v1); cookie banner consent class documented; buyers can shop unattributed by clearing/declining (declined-consent traffic simply unattributed — checkout never blocked); affiliate-visible stats are aggregates + own referrals, never other buyers' PII.

## Data / API touches

- New tables `affiliate_clicks{id, affiliate_id, landing, clicked_at, ip_hash, ua_hash}` + `order_attributions{order_id UNIQUE, affiliate_id, source (link|code), clicked_at?, window_days, rate_snapshot, status}` with standard RLS posture (affiliates read own rows; buyers see nothing). Click logs retained per the privacy window (see Q2), attribution rows follow order retention. All PG via `worker/db/*`.
- Endpoints: public redirect `/r/:code` (fast, cached affiliate lookup, signed-cookie set), checkout reads the cookie/code and stamps attribution, affiliate `GET /api/v1/affiliate/stats` (clicks/conversions/pending/confirmed). No affiliate-writable attribution fields.
- Evidence bundle per attribution (click id, code if any, timestamps, rate snapshot, order ref) stored immutably for dispute replay.

## Acceptance criteria

- Precedence drill: cookie A + code B at checkout → B credited; two cookies → most recent wins; direct purchase → unattributed; all reproducible from evidence bundles.
  *GWT: Given Precedence drill: cookie A + code B at checkout; When the transition executes; Then B credited; two cookies → most recent wins; direct purchase → unattributed; all reproducible from evidence bundles.*
- Window drill: purchase on day 29 attributes, day 31 doesn't (configurable window respected to the second).
  *GWT: Given Window drill context; When the checked action occurs; Then purchase on day 29 attributes, day 31 doesn't (configurable window respected to the second).*
- Reversal drill: refund/dispute-loss flips confirmed → reversed (proportional on partials); self-purchase voids with affiliate flag raised.
  *GWT: Given Reversal drill: refund/dispute-loss flips confirmed; When the transition executes; Then reversed (proportional on partials); self-purchase voids with affiliate flag raised.*
- Stuffing drill: hidden-iframe click floods rejected at ingest, dashboards unskewed, affiliate warned per terms ladder.
  *GWT: Given Stuffing drill context; When the checked action occurs; Then hidden-iframe click floods rejected at ingest, dashboards unskewed, affiliate warned per terms ladder.*
- Privacy drill: declined-consent buyer completes checkout normally with zero attribution; affiliate stats contain no buyer PII.
  *GWT: Given Privacy drill context; When declined-consent buyer completes checkout normally with zero attribution; Then affiliate stats contain no buyer PII.*
- Auth probe: affiliate A reading affiliate B clicks/attributions → 403; RLS denies cross-affiliate rows.
  *GWT: Given Auth probe: affiliate A reading affiliate B clicks/attributions; When the transition executes; Then 403; RLS denies cross-affiliate rows.*

## Out of scope

- Enrollment, tiers, and terms (see `affiliate/affiliate-program.md`); commission math and payouts (see `affiliate/commission-payout.md`).
- Influencer batch supply mechanics (`promo-code/code-generation.md` — consumed here as codes, not redefined); referral pro-day credits (`subscription/trials-promotions.md`); P5.

## Open questions

- Q1: Attribution window — 30-day default acceptable, or shorter/longer for considered B2B purchases?
- Q2: Click-log retention — privacy window length before aggregation-only (align with R2/beacon TTL precedents)?
