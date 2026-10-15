# Link.me Feature Inventory (replication reference)

Sources (fetched Oct 2026): `link.me/` homepage, `link.me/faq`, `link.me/pricing`, `link.me/business`. FAQ answer bodies render client-side (only questions verified); facts below come from page copy verified in those fetches. Re-verify before build if link.me copy changes.

## Purpose

Give builders the complete link.me capability list in one place, each item mapped to our spec coverage, so replication scope is decided from evidence instead of memory. Our PLAN already cites link.me parity as the P1 bar; this doc is the itemized version of that bar — what to match, what to defer, and what to deliberately skip.

## Users

- Business Analyst: scopes phases against the inventory without re-researching link.me.
- Implementer: checks the parity mapping before adding or cutting scope in any link-to-bio doc.
- Reviewer: verifies P1-exit parity claims item by item.

## Functional requirements

Link.me capabilities by area (verified wording condensed; quotas exact where published):

1. Profile + page: customizable link-in-bio profile + mini website; unlimited links; social/content/product unification; data ownership + per-click tracking claims; trackable links + profile QR code; mobile-first design; AI-powered design assistance; modern themes plus fully-custom creative freedom; app integrations (Instagram, TikTok, YouTube); link scheduler (start/end dates per link); animated links and own-logo branding (paid); deeplink technology (open in Safari/Chrome, not in-app); verification badge; total-followers display.
2. Analytics: basic tier (profile visits + link clicks); advanced tier (audience/click insights); real-time global tier (live clicks, visitors, locations, sales performance); per-link and source/city views; earnings + engagement in one dashboard; full-featured mobile apps carrying the same analytics.
3. Monetize: link-out product sales (external checkout); merch creation with AI designs + print/ship fulfillment (POD, no inventory; lower fees on higher tiers); digital downloads (upload, price, sell); paywalled content; tips (Buy-Me-a-Coffee replacement); course builder (free + paid courses with hosted content); paid calls; merch profit calculator; sales-fee question ("are there fees when I sell" — fee schedule per tier).
4. Publish (Post to All): one upload + caption (2,200-char caption box observed) to 7 platforms (YouTube Shorts, Instagram Reel, TikTok, Facebook Reel, X, Threads, LinkedIn); background publishing with per-platform status ("7 of 7 published"); quotas — free 10 posts lifetime, Plus/Pro unlimited per 30-day rolling cycle; video 3 min / 300 MB; accounts per platform 1 (Pro 10); content calendar planning view.
5. Automations: Instagram keyword-triggered comment→DM with link delivery (e.g. comment TOUR → ticket-page DM); any-comment triggers; quotas — keyword 3/5/unlimited, any-comment 4/10/unlimited, contacts 100/yr, 1k/mo, 100k/mo across tiers; contact-list building into leads.
6. Leads + email: forms + one-tap capture into dashboard; lead search, broadcast campaigns, CSV export; business-grade retargeting via integrated ads platform (reach + retarget in real time).
7. Teams + agencies: multi-profile management under a master account; roles (owner/manager-style Users/Managers tabs); Act-as-user profile editing; per-profile views/clicks/sales with combined reporting; subscription management per seat.
8. Plans + account: Free / Plus / Pro with monthly/yearly billing (up to 44% yearly saving); 7-day free trial; 1-click cancel; plan change mid-term; password reset; account deletion flow; custom domain on business/concierge tiers; concierge managed service (team runs setup, campaigns, posts, emails, automations, reporting).

## Data / API touches

Parity mapping — link.me capability → our spec → status (no new tables here; mapping only — refreshed to current tree: 25 docs across 6 newer majors since first writing):

- Profile/links/QR/scheduler/themes/avatar → `link-to-bio/profile/`, `blocks/`, `appearance/`, `public-page/` — specced (P1), plus gap-closers: verification badge + follower display (`profile/verification-followers.md`), deep links + mobile-first page (`public-page/deep-links-mobile.md`); AI design assistance (`appearance/ai-design-assist.md`, flagged new scope, design-complete).
- Basic/advanced/realtime analytics + source/city + QR-as-URL → `link-to-bio/analytics/` — specced (P1 base in `profile-analytics.md`); advanced audience/click insights (`audience-insights.md`), realtime global tier (`realtime-global.md`, pro-gated KV window), earnings + engagement unification (`earnings-dashboard.md`, phase-gated strips), mobile delivery as PWA (`mobile-pwa.md`, native apps not built — PLAN decision kept).
- Merch/digital/paywall/tips/courses/paid-calls → `docs/shop/` (8 docs, P2 scope, specced): catalog (`products/product-catalog.md`), POD fulfillment (`merch/pod-fulfillment.md`, AI designs + partner adapter flagged), profit calculator (`merch/profit-calculator.md`), digital + paywall (`digital/digital-delivery.md`), tips (`tips/tip-jar.md`), courses (`courses/course-builder.md`), consultations (`services/paid-consultations.md`, `service` kind flagged as PLAN-enum addition), fee schedule (`pricing/sales-fees.md`, values pending finance).
- Email capture + campaigns + CSV → `docs/leads/` (P2, specced): forms + one-tap capture (`capture/lead-forms.md`), search + working view (`manage/lead-search.md`); broadcasts stay P4 (`monetize/marketing/campaign-broadcasts.md`); consent + hygiene + exports in `monetize/marketing/` (specced).
- Post to All (7-platform fan-out, scheduler, status polling) + content calendar → `docs/publish/` (3 docs, P4 scope, design-complete): connections vault (`accounts/social-connections.md`), composer + calendar + quotas (`composer/compose-schedule.md`), fan-out + N-of-7 status (`delivery/fanout-status.md`) — deferred, never stubbed earlier.
- IG automations (keyword/any-comment, contact quotas) → `docs/automate/` (3 docs, P4 scope, design-complete): triggers (`triggers/comment-triggers.md`), reply + 1-link DM (`delivery/dm-delivery.md`), contact lists (`contacts/contact-lists.md`) — quotas adapted to free/pro (middle rung not copied).
- Integrated ads platform + retargeting → `monetize/advertising/` (inventory, serving, revenue share — reference design, P4-growth) + first-party retargeting (`advertising/retargeting-reach.md`, no third-party pixels/uploads in v1) — gap by design until plan update.
- Agency roster/roles/Act-as-user/leaderboard → `docs/agency/` (4 docs, P4 scope, design-complete): roster (`roster/team-roster.md`), Act-as-user (`access/act-as-user.md`), reporting (`reporting/client-reporting.md`), seat billing (`billing/seat-billing.md`); `orgs` skeleton only in P3 (`monetize/organizations/agency-skeleton.md`).
- Free/Plus/Pro matrix, trial, cancel, account deletion → `monetize/plans/` + `monetize/subscription/` + `monetize/billing/` (mechanics, VN-adapted: free/pro, Plus naming intentionally not copied) + `docs/account/` (5 docs): tier presentation (`plans/tier-presentation.md`, yearly ≤44% + 7-day trial recommendations), password/sessions (`access/password-security.md`, P1a-1), deletion (`lifecycle/account-deletion.md`, 30-day + purge certificate), custom domains (`domains/custom-domains.md`, P2-6 specced), concierge (`concierge/managed-service.md`, scoped as staffed P4-revenue service — no longer out of scope).
- Custom domains → specced (P2-6, see above); native mobile apps → PWA instead (PLAN decision, kept, delivered in `mobile-pwa.md`).

## Acceptance criteria

- P1-exit parity checklist (all verifiable without link.me access): unlimited links + badge on free; link scheduler windows; animated-link + own-logo pro gates with server-side enforcement; basic→advanced→realtime analytics tiers with the stated quotas; QR = profile URL; verification badge + follower display + deep-link escape + mobile-first page present; link.me-FAQ question coverage — every P1 FAQ topic (add-to-profile, customize-look, analytics-learned, QR, schedule-posts) answerable from our specs.
- P2-exit shop/leads checklist: POD merch listing (no inventory) + profit calculator, digital/paywall one-time delivery after paid order, course resume-last-lesson, tips-as-product, paid consultations with 24 h policy, fee schedule with quote = charged = ledger identity, leads capture + search + CSV with consent timestamps, custom domains verified + served.
- P4-entry readiness (designed, not built): publish (3 docs), automations (3 docs), agency (4 docs), campaigns/retargeting/ads each name their target scope with no placeholder code in earlier phases — grep gate for premature stubs.
- Pricing-page fidelity: two tiers vs three, PayOS-first billing, yearly ≤44% ceiling + 7-day trial recommendations recorded in `account/plans/tier-presentation.md`, fee values pending finance — no merch-fee schedule copied without a provider.
- Source freshness: re-fetch the four source URLs at each phase exit; deltas recorded as spec amendments, not silent drift.

## Out of scope

- Native iOS/Android apps (PWA install prompt instead, per PLAN — delivered via `mobile-pwa.md`).
- Exact link.me pricing, fee percentages, and copy (reference only — our matrix is VN-adapted with its own providers; fee values await finance ratification per `shop/pricing/sales-fees.md`).
- Advertiser-side self-serve tooling beyond our P4-growth ads design; third-party social APIs' own review/approval processes (Business-account review etc. — external dependencies, noted in P4 specs).

## Open questions

- Q1: Fee + yearly-discount values — finance ratification blocking checkout fee copy and pricing-page numbers (see `shop/pricing/sales-fees.md` Q1, `account/plans/tier-presentation.md` Q1)?
- Q2: Fulfillment partner choice (POD vendor with API + webhooks) — selection criteria and SLA floor (see `shop/merch/pod-fulfillment.md` Q1)?
