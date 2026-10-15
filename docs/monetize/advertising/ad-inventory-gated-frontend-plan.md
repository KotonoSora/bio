# Ad Inventory Gated Frontend Plan (gallery + opt-in + takedown UX — buildable at plan update)

PLAN ref: none — P4-growth, GATED. BA source: `docs/monetize/advertising/ad-inventory.md`. Server truth in `docs/monetize/advertising/ad-inventory-gated-backend-plan.md` (catalog, lifecycle, safety — never redefined here).

## Purpose

Hold the buildable gallery/opt-in UX: placement cards with honest labeling, creator opt-in toggle with category declaration, flight status visibility, and report-abuse entry. Nothing here renders before the plan update.

## Users

- Frontend developer (future): builds gallery + opt-in + ops review screens.
- QA (now): asserts zero ad UI exists; (future) labeling/opt-in/takedown drills.
- Advertiser/creator/visitor (future): buys, opts in, sees labeled units that never impersonate content.

## Functional requirements

1. Surfaces (future): public gallery (placement cards: format, sizes, labeling preview, category tags); creator `Advertising` tab (opt-in toggle default OFF + category declaration multi-select + earnings entry linking revenue-share screens); ops review route (approvals, takedown — role-gated). No advertiser self-serve builder in v1 (absence asserted — no draft-flight UI exists to audit).
2. Step 1 — gallery flow: cards render exact format/position/labeling per placement version (thumbnail + `Sponsored` specimen + size caps stated); category tags visible pre-purchase (buyer-visible targeting attributes — what you see is what matching uses).
3. Step 2 — opt-in flow: toggle (default OFF, explicit `Off` label — never ambiguous blank) → category declaration required before first serve (`Declare your page categories to finish opt-in` blocks serving, not the toggle — toggle without categories leaves state `opted-in-undeclared = not served`, stated plainly) → mid-flight opt-out stops serving immediately with `No penalty — already-served reporting stays` copy.
4. Step 3 — labeling honesty (visitor-facing): every served unit carries the `Sponsored` label, visually distinct from creator blocks, never dismissable-as-content (dismiss hides the unit, never re-labels it); ad-choice/why-this-ad disclosure one tap away (targeting dimensions shown in plain words).
5. Step 4 — takedown/report UX: `Report this ad` on every unit (Turnstile inline, reason select) → `Report received` (no tracking theater); taken-down creatives vanish everywhere (detail URLs show `removed`, booked placements show `under review` with rebook guidance).
6. Step 5 — modern UI discipline: gallery responsive-first (360px baseline, 44px targets); flight status chips share the canonical status styling (no ad-specific color language that implies guarantees); empty states (`No placements in this category yet`, not zeros).

## Data / API touches

- Screens → endpoints map (all GATED, absent now): gallery → public placement reads; opt-in → toggle + category endpoints; ops → review/decide endpoints. Session cookie + CSRF; Turnstile on report submits.

## Acceptance criteria

- Absence drill (now): zero ad UI in dashboard/public bundles (grep) and zero ad routes.
- Labeling drill (at build): every unit labeled distinct; disclosure one tap; no impersonation possible by configuration.
- Opt-in drill (at build): default OFF; undeclared blocks serving with plain copy; mid-flight opt-out immediate + penalty-free.
- Takedown drill (at build): report completes; removal gallery-wide within SLA.
- Security drill (at build): no self-serve builder exists (grep); ops route role-gated.

## Out of scope

- Catalog/flight/safety logic (backend plan above); table DDL (database plan above); decisioning/counting (serving-measurement plans); earnings/payouts (revenue-share plans); affiliate/promo; creator analytics.

## Open questions

- Q1: Gallery filters (category/format/size) — needed at launch catalog size?
- Q2: Opt-in category taxonomy — who owns the list (platform fixed vs creator free-tag)?
