# Link.me Parity Implementation (verification execution — no new build)

PLAN ref: P1-exit parity bar (link.me/faq 2026) + P2-exit shop/leads checklist + P4-entry readiness (designed, not built) + S-1…S-7. BA source: `docs/link-to-bio/research/link-me-parity.md` (itemized replication reference: what to match, defer, skip — mapping only, no new tables). Execution phasing: `docs/platform/delivery/execution-overview.md` (B0–B2; buildable content in `docs/link-to-bio/profile/`, `docs/link-to-bio/blocks/`, `docs/link-to-bio/appearance/`, `docs/link-to-bio/public-page/`, `docs/link-to-bio/analytics/`), plus shop/leads/domains and P4-gated publish/automate/agency plans.

## Purpose

Turn the parity inventory into an executable verification plan: who proves each parity claim, with what drill, at which phase exit — without building anything in this doc. Builders check this file before adding or cutting scope; reviewers verify P1-exit parity item by item. No DDL, no endpoints, no Kotlin classes originate here.

## Users

- Business Analyst: scopes phases against the inventory without re-researching link.me.
- Implementer: checks the parity mapping before changing any link-to-bio slice.
- Reviewer: runs the exit checklists below at P1, P2, and P4-entry gates.

## Functional requirements

1. Stack and topology (mandatory, reaffirmed): PostgreSQL LTS `18.6` + Spring Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — inherits all owning slices (monolith-first per `docs/platform/architecture/architecture-decision.md`); this doc adds no topology and proposes no split. Any split question belongs to the owning delivery plan (B0/B1/B2/P4a/P4b/E5), never here.
2. P1-exit parity checklist (all verifiable without link.me access; owner: bio slices):
   - Unlimited links + badge on free; link scheduler windows (`visible_from/until`); animated-link + own-logo pro gates with server-side `403` enforcement (impl: `docs/link-to-bio/appearance/themes-avatar-backend-plan.md`).
   - Basic -> advanced -> realtime analytics tiers with stated quotas; QR = profile URL (impl: `docs/link-to-bio/analytics/profile-analytics-backend-plan.md`, `docs/link-to-bio/analytics/audience-insights-backend-plan.md`, `docs/link-to-bio/analytics/realtime-global-backend-plan.md`).
   - Verification badge + follower display + deep-link escape + mobile-first page present (impl: `docs/link-to-bio/profile/verification-followers-backend-plan.md`, `docs/link-to-bio/public-page/deep-links-mobile-backend-plan.md`).
   - FAQ coverage: every P1 FAQ topic (add-to-profile, customize-look, analytics-learned, QR, schedule-posts) answerable from the BA + impl docs above.
3. P2-exit shop/leads checklist (owner: shop/leads/domains plans): POD merch listing (no inventory) + profit calculator, digital/paywall one-time delivery after paid order, course resume-last-lesson, tips-as-product, paid consultations with 24h policy, fee schedule with quote = charged = ledger identity, leads capture + search + CSV with consent timestamps, custom domains verified + served.
4. P4-entry readiness (designed, not built; owner: P4 plans): publish (3 docs), automations (3 docs), agency (4 docs), campaigns/retargeting/ads each name their target scope with zero placeholder code in earlier phases — grep gate for premature stubs (`docs/platform/delivery/publish-automate.md`, `docs/platform/delivery/agency-teams.md`).
5. Pricing-page fidelity: two tiers vs three, explicit-`provider` billing, yearly ceiling + 7-day trial recommendations in `docs/account/plans/tier-presentation.md`; fee values pending finance — no merch-fee schedule copied without a provider. Source freshness: re-fetch the four link.me source URLs at each phase exit; deltas recorded as spec amendments, not silent drift.
6. Container/gate posture: no image or migration originates here. Phase exits still require same-digest Docker AND Podman boot + rollback green per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`.

7. Frontend verification surfaces: dashboard routes `/app/profile`, `/app/blocks`, `/app/appearance`, and `/app/analytics/*` render from 360px widths to desktop; public `/:username` and `/:username/cv` pass cold-curl tag checks; the PWA installs from Chrome Android and the iOS share-sheet. Each phase exit re-runs this matrix.

## Data / API touches

- Migration: none — mapping only. Parity mapping (link.me capability -> our spec -> status): profile/links/QR/scheduler/themes/avatar -> `docs/link-to-bio/profile/`, `docs/link-to-bio/blocks/`, `docs/link-to-bio/appearance/`, `docs/link-to-bio/public-page/` (P1); basic/advanced/realtime + source/city + QR-as-URL -> `docs/link-to-bio/analytics/` (P1 base + slices); merch/digital/paywall/tips/courses/calls/fees -> `docs/shop/` (P2); capture/search/CSV -> `docs/leads/` (P2, broadcasts P4); Post-to-All + calendar -> `docs/publish/` (P4, deferred); IG automations -> `docs/automate/` (P4, quotas adapted); ads/retargeting -> `docs/monetize/advertising/` (P4-growth gap by design); agency -> `docs/agency/` (P4; skeleton only in P3); plans/trial/cancel/deletion/domains/concierge -> `docs/monetize/plans/`, `docs/monetize/subscription/`, `docs/monetize/billing/`, `docs/account/` (VN-adapted free/pro); custom domains P2-6; native apps -> PWA instead (kept).

## Acceptance criteria

- P1-exit: all checklist items green on the P1 gate (10k concurrent reads p95 <500ms, >95% cache-hit) plus S-1…S-7 probes for the bio surface.
- P2-exit: shop/leads checklist green (paid-download, resume-last-lesson, fee-identity, consent-timestamp, verified-domain drills).
- P4-entry: grep + migration audit confirms zero P4/BACKLOG tables, endpoints, or jobs in E0–E3 images.
- Freshness: source re-fetch logged per phase exit with amendments or explicit no-change notes.

## Out of scope

- All build behavior (owning BA + impl + delivery docs — consumed, never redefined here); native iOS/Android apps (PWA instead); exact link.me pricing/fees/copy (reference only); advertiser self-serve beyond P4-growth design; third-party social review processes (external dependencies).

## Open questions

- Q1: Fee + yearly-discount values — finance ratification blocking checkout fee copy and pricing numbers?
- Q2: POD vendor choice — selection criteria and SLA floor?
