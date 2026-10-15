# Ad Inventory (placements, formats, brand safety)

PLAN ref: none — new scope beyond PLAN P0–P5, flagged explicitly. Proposed as P4-growth alongside affiliate/agency; needs a plan update before build. Distinct from affiliate per-sale commissions (`affiliate/`) and promo discounts (`promo-code/`): this minor sells attention (placements/impressions), not outcomes.

## Purpose

Define what the platform sells to advertisers: the placement catalog (platform-owned slots + creator opt-in units), formats, targeting dimensions, and the brand-safety rules that keep ads off pages they shouldn't touch. Without an explicit inventory, ad serving becomes improvised embeds negotiated per deal — unmeasurable, unbrand-safe, and unscalable. v1 scope is direct/sponsored deals served by our own decisioning; third-party ad-network SDKs and self-serve advertiser tooling are explicitly deferred.

## Users

- Advertiser (direct deal): *As an Advertiser, I want to buy placements for a period/audience, receive performance reports so that what the platform sells to advertisers as defined here.* buys placements for a period/audience, receives performance reports.
- Creator: *As a Creator, I want to opt page inventory in or out, keep full control of adjacency (category blocks) so that what the platform sells to advertisers as defined here.* opts page inventory in or out, keeps full control of adjacency (category blocks).
- Visitor: *As a Visitor, I want to see clearly labeled sponsored units that never impersonate creator content so that what the platform sells to advertisers as defined here.* sees clearly labeled sponsored units that never impersonate creator content.

## Functional requirements

1. Placement catalog (config, versioned): platform slots (theme-gallery features, discovery/directory boosts where they exist, post-checkout thank-you surface — never inside the checkout/payment flow itself) and creator-page units (single in-feed slot with fixed position options: below-profile-header or end-of-blocks; exactly one ad unit per page in v1). Each placement declares format (image + headline + CTA link; no autoplay video, no interstitials, no pop-unders in v1), size constraints, and labeling ("Sponsored", visually distinct, non-dismissable-as-content).
2. Targeting dimensions (v1, privacy-conservative): page category/language, device class, coarse geo (country/city from existing analytics signals — no new tracking). No behavioral profiling, no cross-site identity, no health/finance/children targeting (see brand safety). All targeting attributes buyer-visible to the advertiser pre-purchase.
3. Brand-safety rules (server-enforced, not policy prose): category exclusion lists both directions (advertiser blocks categories; creators block advertiser categories), prohibited verticals (adult, gambling, misleading earning claims, crypto-yield promises — VN-sensitive list, see Q1), creative pre-approval by ops for every flight (no self-serve upload in v1), and instant takedown (single audited action pauses a creative everywhere within minutes).
4. Flight model: `ad_flights{advertiser, placements[], creatives[], starts_at, ends_at, cap_impressions?, cap_spend?, status (draft|scheduled|live|paused|ended)}` with the same lifecycle discipline as promo campaigns (launch requires caps + window; pause is instant; ended terminal). Pricing in v1 is flat-fee per flight (CPM/CPC accounting deferred to Q2 — measurement below already counts both, so the model can switch without re-instrumentation).
5. Creator opt-in: per-profile toggle (default OFF), plus per-page category declaration that feeds both targeting and safety matching; opting out mid-flight stops serving immediately with no penalty. Free-plan creators may opt in (ad revenue is separate from plan gates); pro gets no forced ads, ever.
6. Separation guarantee: ad impressions/clicks flow through the ad measurement pipeline only (`advertising/ad-serving-measurement.md`) — creator `profile_views`/`link_clicks` analytics are never polluted by ad traffic.

- 7. Edge handling (derived from rules 3-5 above): prohibited-vertical creatives and category-block conflicts are rejected at approval or excluded from serving and never served; a takedown pauses the creative everywhere with an audited action — no silent serve and no partial states.

## Data / API touches

- New tables `ad_placements{catalog}` (config) + `ad_flights{...}` + `ad_creatives{flight_id, assets, headline, cta_url, approval}` with RLS posture (advertiser reads own flights; creators read own opt-in state; config restricted to ad-ops role). Creative assets in R2 with the same validation discipline as avatars (size/type/magic-byte). All PG via `worker/db/*`.
- Endpoints: ops flight/creative CRUD + approve/pause (audited), creator opt-in toggle + category declaration, public ad-choice/why-this-ad disclosure endpoint. No advertiser self-serve writes in v1.
- CTA links: `https:` allowlist + `rel="noopener sponsored ugc"`, click routed through the measurement redirect (never direct).

## Acceptance criteria

- Catalog drill: each placement renders its exact format/position/labeling on desktop + mobile; checkout/payment surfaces provably ad-free (grep + review gate).
  *GWT: Given Catalog drill context; When each placement renders its exact format/position/labeling on desktop + mobile; Then checkout/payment surfaces provably ad-free (grep + review gate).*
- Safety drill: prohibited-vertical creative rejected at approval; creator category-block respected in serving; takedown pauses a live creative everywhere within minutes.
  *GWT: Given Safety drill context; When prohibited-vertical creative rejected at approval; Then takedown pauses a live creative everywhere within minutes.*
- Opt-in drill: default-off for new profiles; mid-flight opt-out stops serving immediately; pro pages never forced.
  *GWT: Given Opt-in drill context; When default-off for new profiles; Then pro pages never forced.*
- Separation drill: ad traffic produces zero rows in creator analytics tables under load.
  *GWT: Given Separation drill context; When the checked action occurs; Then ad traffic produces zero rows in creator analytics tables under load.*
- Auth probe: advertiser A reading advertiser B flights → 403; creators cannot touch flights; RLS denies cross-scope rows.
  *GWT: Given Auth probe: advertiser A reading advertiser B flights; When the transition executes; Then 403; creators cannot touch flights; RLS denies cross-scope rows.*

## Out of scope

- Serving, counting, and fraud filtering (see `advertising/ad-serving-measurement.md`); creator earnings and payouts (see `advertising/creator-revenue-share.md`).
- Affiliate commissions (`affiliate/`); promo discounts (`promo-code/`); creator analytics (`docs/link-to-bio/`); third-party ad-network SDKs and self-serve advertiser tooling (deferred); P5.

## Open questions

- Q1: Prohibited-vertical list — finalize with legal for VN sensitivity (finance claims, health, gaming)?
- Q2: Pricing model at launch — flat-fee per flight confirmed, or CPM/CPC from day one?
