# Deep Links and PWA - UI Data Dictionary (feature 11)

Phase 1 BA · React 19.3 surfaces: the dashboard `Mobile` tab, block-editor preview icons, and PWA install/offline states. The public banner is SSR markup + micro-script (observable contract below). Topology: `docs/02-architecture-techlead/11-deep-links-and-pwa/01-architecture-and-tech-stack.md`.

## Dashboard: `Mobile` tab (read-only)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| BannerEligibilitySummary | stat card | - | share of visits from webview UAs; from audience reads at a stated `generatedAt` | - |
| EscapeTapTrend | small chart | - | `escape_tap` counts; same freshness honesty as analytics widgets | - |
| CopyLinkTester | button + feedback | - | copies the profile URL; confirms clipboard write; no navigation | - |
| BudgetFooter | static text | - | last-green commit hashes for JS budget + tap-target audit (informational; CI holds the teeth) | - |
| NoToggleRule | (absence) | - | zero toggles/switches exist on this tab - configurability does not exist | enforced by review |

## Block editor: preview icons (read-only)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| DeepLinkPreviewIcon | icon + tooltip | - | appears only for YouTube/TikTok/Instagram URLs that passed the `https:` allowlist; shows the computed deep-link form on hover; cached per session | plain links show no icon |
| AllowlistErrorState | inline error | - | invalid URLs show `bad_url_scheme` (owned by blocks); the icon never appears on invalid rows | - |

## PWA install and lifecycle surfaces

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| InstallAppButton | button | - | shown at the deferred install moment (analytics overview); hidden when already installed | hidden |
| IosInstructionCard | dismissable card | - | iOS only (no prompt API); one-time, never nagging | hidden |
| UpdateAvailablePill | pill | - | `Update available - restart`; activation swaps worker + caches atomically | hidden |
| OfflineStaleBanner | persistent banner | - | `Showing last known figures - offline`; stays until online; attached to every number shown offline | hidden online |
| StandalonePadding | layout rule | - | extra bottom padding under `display:standalone` for gesture bars | - |
| ThemeColorMeta | meta | - | matches dashboard chrome (no white flash on launch) | - |

## Manifest contract (fields)

| Field | Value rule |
|---|---|
| `name` / `short_name` | platform brand + `Analytics` |
| `icons` | 192 + 512, maskable |
| `theme_color` / `background_color` | dashboard chrome tokens |
| `display` | `standalone` |
| `start_url` | `/app/analytics` |

## Micro-script observable contract (public page)

| Behavior | Rule |
|---|---|
| Webview class | applied once from `navigator.userAgent` against the shipped pattern list; affects CSS only |
| Banner | revealed only under the `webview` class; primary action wired to the platform-correct escape URL; copy fallback always functional |
| Beacon events | `banner_show` on first reveal; `escape_tap` on action; dedupe via sessionStorage only |
| Size | <= 5 KB, no framework, CSP-compliant, immutable-cached |
| No storage | nothing written except the stated dedupe keys; no cookies; no UA persistence |
