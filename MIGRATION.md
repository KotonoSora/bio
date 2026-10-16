# MIGRATION — legacy docs to numbered tree

Status tracker for the docs migration (rules: `AGENTS.md`). Batches run in order; a feature is `done` only after validation passes and its legacy files are deleted.

## Batch order

1. Pilot (done): feature 29
2. link-to-bio (06–16) — user priority; **complete (11/11)**
3. `00-platform-baseline` — **complete (8 files)**
4. account (01–05) — **complete (5/5)** · 5. resume (17–21) — **complete (5/5)** · 6. shop (22–26) — **complete (5/5)** · 7. leads (27–28) — **complete (2/2)** · 8. url-shortener (30–31) — **complete (2/2)** · 9. monetize (32–40) — **complete (9/9)** · 10. publish/automate (41–44) — **complete (4/4)** · 11. agency (45–47) — **complete (3/3)** · 12. gated backlog (48–53) — **complete (6/6)**

## Service map (working; extends as features migrate)

| Service | Features | Port | Database | Redis db | Notes |
|---|---|---|---|---|---|
| account-service | 01–03, 05, 45 | 8081 | account_db | 7 | SSO login + linked accounts; sessions + step-up; entitlement snapshot; agency roster + Act-as sessions (session keys in platform db 2) |
| profile-service | 04, 06, 07, 08, 09, 27, 28 | 8082 | profile_db | 4 | username namespace authority; block authoring; theme/avatar pipeline; leads capture + contact workspace; internal read APIs for the public-site service |
| link-service | 29–30 | 8083 | links_db | 3 | single redirector; short links + QR renders; consumes profile-service namespace rules |
| analytics-service | 12–15, 31, 47 | 8085 | analytics_db | 6 | beacon + link-hit intake (Redis streams), hourly rollup, owner reads, short-link stats, agency reporting; no cross-service FKs |
| resume-service | 17–21 | 8086 | resume_db | 8 | resume documents + immutable snapshot versions; blob archive; `/cv` public read; AI JD-tailor endpoint (35) (18–21 extend) |
| shop-service | 22–26 | 8087 | shop_db | 9 | uniform product catalog (7 kinds); storefront reads; fulfillment, courses, tips, fees extend (23–26) |
| billing-service | 32–40, 46 | 8088 | billing_db | 10 | money kernel: orders + checkout + verified grants + plan ledger + lifecycle jobs + receipts + refunds + promos + eight PSP rails + notification pipeline + agency seat billing |
| publish-service | 41–44 | 8089 | publish_db | 11 | connections vault + OAuth; composer + calendar + dispatch fan-out; automations + contacts + DM delivery; in-service worker pools behind DispatchPort/WatchPort |
| public-site-service | 10, 11 | 8084 | — (stateless) | 5 | SSR public pages, escape banner + micro-script, mobile template; cache + purge, sitemap; consumes internal read APIs; no database |

## Feature register and status

| # | Feature (dir name) | Legacy sources | Status |
|---|---|---|---|
| 00 | platform-baseline | platform/{architecture,containers,stack}/*, delivery/{execution-overview,release-gates,scaffold-platform,scale-out}.md | **done** (8 files; supersedes monolith-first doctrine) |
| 01 | account-and-access | account/access/{sso-login,authorization-model}.md, platform/delivery/account-access.md; password-security.md archived (SSO-only supersedes) | **done** (account 1/5) |
| 02 | account-deletion | account/lifecycle/account-deletion.md | **done** (account 2/5) |
| 03 | tier-presentation | account/plans/tier-presentation.md | **done** (account 3/5) |
| 04 | custom-domains | account/domains/custom-domains.md | **done** (account 4/5) |
| 05 | concierge-managed-service | account/concierge/managed-service.md | **done** (account 5/5) |
| 06 | public-profile | link-to-bio/profile/public-profile*.md | **done** (link-to-bio 1/11) |
| 07 | verification-and-followers | link-to-bio/profile/verification-followers*.md | **done** (link-to-bio 2/11) |
| 08 | link-blocks | link-to-bio/blocks/link-blocks*.md | **done** (link-to-bio 3/11) |
| 09 | themes-and-avatar | link-to-bio/appearance/themes-avatar*.md | **done** (link-to-bio 4/11) |
| 10 | share-and-seo | link-to-bio/public-page/share-seo*.md, research/link-me-parity*.md (folded) | **done** (link-to-bio 5/11) |
| 11 | deep-links-and-pwa | link-to-bio/public-page/deep-links-mobile*.md + link-to-bio/analytics/mobile-pwa*.md | **done** (link-to-bio 6/11) |
| 12 | profile-analytics | link-to-bio/analytics/profile-analytics*.md | **done** (link-to-bio 7/11) |
| 13 | audience-insights | link-to-bio/analytics/audience-insights*.md | **done** (link-to-bio 8/11) |
| 14 | realtime-global | link-to-bio/analytics/realtime-global*.md | **done** (link-to-bio 9/11) |
| 15 | earnings-dashboard | link-to-bio/analytics/earnings-dashboard*.md | **done** (link-to-bio 10/11) |
| 16 | ai-design-assist | link-to-bio/appearance/ai-design-assist* (gated) | **done** (link-to-bio 11/11; gated design-only) |
| 17 | resume-documents | resume-builder/documents/resume-snapshot*.md, platform/delivery/resume-documents.md | **done** (resume 1/5) |
| 18 | resume-templates | resume-builder/templates-print/resume-templates*.md, platform/delivery/resume-render-share.md | **done** (resume 2/5) |
| 19 | resume-format-sharing | resume-builder/templates-print/resume-format-sharing*.md, platform/delivery/resume-render-share.md | **done** (resume 3/5) |
| 20 | resume-sharing-links | resume-builder/sharing/resume-sharing-links*.md, platform/delivery/resume-render-share.md | **done** (resume 4/5) |
| 21 | resume-portability | resume-builder/import-export/resume-portability*.md + fixture-resume.json, platform/delivery/resume-portability.md | **done** (resume 5/5) |
| 22 | shop-catalog | shop/products/product-catalog.md, platform/delivery/shop-catalog.md (S0 plan, folded across 22-26) | **done** (shop 1/5) |
| 23 | shop-fulfillment | shop/merch/pod-fulfillment.md, shop/digital/digital-delivery.md | **done** (shop 2/5) |
| 24 | shop-courses | shop/courses/course-builder.md | **done** (shop 3/5) |
| 25 | shop-consultations-and-tips | shop/services/paid-consultations.md, shop/tips/tip-jar.md | **done** (shop 4/5) |
| 26 | shop-fees-and-calculator | shop/pricing/sales-fees.md, shop/merch/profit-calculator.md | **done** (shop 5/5) |
| 27 | lead-forms | leads/capture/lead-forms.md, platform/delivery/leads-capture.md | **done** (leads 1/2) |
| 28 | lead-search | leads/manage/lead-search.md | **done** (leads 2/2) |
| 29 | short-links | url-shortener/links/short-links{,,-database-plan,-backend-plan,-frontend-plan}.md | **done (pilot)** |
| 30 | qr-codes | url-shortener/links/qr-codes*.md | **done** (url-shortener 2/3) |
| 31 | short-link-analytics | url-shortener/tracking/link-analytics*.md | **done** (url-shortener 3/3) |
| 32 | monetize-overview | monetize/overview/* | **done** (monetize 1/9) |
| 33 | checkout-session | monetize/checkout/{checkout-session,provider-selection}.md | **done** (monetize 2/9) |
| 34 | order-status-and-orchestration | monetize/payment/{order-status-model,checkout-orchestration}*.md | **done** (monetize 3/9) |
| 35 | entitlement-and-plans | monetize/billing/entitlement-ledger*.md, monetize/plans/*, monetize/subscription/plan-catalog.md | **done** (monetize 4/9) |
| 36 | subscription-lifecycle | monetize/subscription/{recurring-billing,trials-promotions}.md, monetize/billing/subscription-lifecycle* | **done** (monetize 5/9) |
| 37 | invoices-and-receipts | monetize/invoices/*, monetize/billing/invoices-receipts.md | **done** (monetize 6/9) |
| 38 | refunds | monetize/refunds/refund-processing.md | **done** (monetize 7/9) |
| 39 | promo-codes | monetize/promo-code/* | **done** (monetize 8/9) |
| 40 | payment-rails | monetize/{payos,sepay,vnpay,zalopay,napas,google-pay,apple-pay,stripe}/*, monetize/payment/payment-security*.md | **done** (monetize 9/9) |
| 41 | social-connections | publish/accounts/social-connections.md | **done** (publish/automate 1/4) |
| 42 | publish-composer | publish/composer/compose-schedule.md, publish/delivery/fanout-status.md, platform/delivery/publish-automate.md | **done** (publish/automate 2/4) |
| 43 | automate-contacts | automate/contacts/contact-lists.md | **done** (publish/automate 3/4) |
| 44 | automate-triggers | automate/triggers/comment-triggers.md, automate/delivery/dm-delivery.md | **done** (publish/automate 4/4) |
| 45 | agency-roster | agency/roster/team-roster.md, agency/access/act-as-user.md, platform/delivery/agency-teams.md | **done** (agency 1/3) |
| 46 | agency-seat-billing | agency/billing/seat-billing.md, monetize/organizations/agency-skeleton.md | **done** (agency 2/3) |
| 47 | agency-client-reporting | agency/reporting/client-reporting.md | **done** (agency 3/3) |
| 48 | marketing | monetize/marketing/* (gated) | **done** (gated backlog 1/6; gated design-only) |
| 49 | affiliate | monetize/affiliate/* (gated) | **done** (gated backlog 2/6; gated design-only) |
| 50 | advertising | monetize/advertising/* (gated) | **done** (gated backlog 3/6; gated design-only) |
| 51 | sales | monetize/sales/* (gated) | **done** (gated backlog 4/6; gated design-only) |
| 52 | data-analysis | monetize/data-analysis/* (gated) | **done** (gated backlog 5/6; gated design-only) |
| 53 | user-behavior | monetize/user-behavior/* (gated) | **done** (gated backlog 6/6; gated design-only) |

## Open mapping notes

- Per-domain delivery plans fold into their feature's implementation-plan file; global delivery docs live in `00-platform-baseline`. Resume's R0-R2 plans are folded (batch 5) and deleted; the remainder fold with their features.
- `platform/stack/*` pins migrated to `docs/02-architecture-techlead/00-platform-baseline/02-stack-and-build-pins.md` (batch complete); legacy stack files deleted.
- Cross-links inside migrated files use final numbered paths; `00-platform-baseline` refs resolved (batch complete).
- `password-security.md` superseded by SSO-only; session-hygiene material migrated into feature 01 (Q-07: idle 7 d sliding, absolute 90 d, step-up 10 min); file deleted with the access sources.
- Gated backlog 48–53 landed as design-only mirrors of feature 16: full 12-file sets, "- GATED" titles, absence drills live in every release. Future services (`marketing-service`, `affiliate-service`, `ads-service`) and billing/analytics extensions are recorded in each feature's TL01 cross-edit tables and build only at their spec amendment — nothing is stubbed into earlier features.
