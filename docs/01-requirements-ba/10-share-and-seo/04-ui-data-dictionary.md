# Share and SEO - UI Data Dictionary (feature 10)

Phase 1 BA · Dashboard-side React 19.3 surfaces (the public pages themselves are zero-JS SSR HTML). Topology: `docs/02-architecture-techlead/10-share-and-seo/01-architecture-and-tech-stack.md`.

## Surfaces (dashboard SPA)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| PreviewLauncher | button (in editors) | - | opens the sandboxed preview for the owner's username | disabled when offline |
| PreviewIframe | iframe | - | `sandbox="allow-same-origin"`; loads `/app/preview/:username`; renders saved owner-scoped state; `Preview - not public` badge overlay; never cacheable | hidden until opened |
| PreviewBadge | overlay chip | - | exact copy `Preview - not public`; contrast-checked | visible in preview |
| PurgeChip | shared status chip | - | reused component (profile/blocks/themes editors); `publishing...` while pending; clears <= 2 min; one lifecycle across tabs | hidden |
| SeoChecklist | read-only panel | - | rows: Title, Description, Canonical, OG, Twitter, JSON-LD, In sitemap; green/amber per head model; no editing here | collapsed |
| ChecklistRowFixLink | deep link | - | amber rows link to the exact owning field (e.g. `no description` -> profile `seoDesc`) | hidden when green |
| PrivateInfoState | informational row | - | `Hidden from search (private)` - informational, never styled as an error | hidden |
| SharePanel | panel | - | `Copy link` + `Show QR`; both encode the canonical `/{username}` URL only | - |
| QrCodeView | canvas/image | - | QR of the canonical URL; shown in a modal; no server round-trip required | hidden |
| StillPublishingNotice | inline text | - | after the 2 min poll window: `Still publishing - safe to leave.`; poll stops | hidden |
| OfflineState | inline notice | - | publish disabled with `You're offline`; no queued mutations | - |
| PublishButton | primary button | - | enabled when dirty; calls the owning editor's save first (never bypasses save) | - |

## Constraints

- The checklist reads booleans from the preview head model (`title, description, canonical, og, twitter, jsonLd, inSitemap`); it never recomputes tags from draft state.
- No cache-admin control exists in the UI; no purge endpoint is callable from the client (BR-11).
- Poll cadence: HEAD of the public URL every 5 s while the chip is pending; stops at clear or the 2 min timeout.
- The iframe never executes scripts from draft content; only the page's own micro-script runs inside it.
