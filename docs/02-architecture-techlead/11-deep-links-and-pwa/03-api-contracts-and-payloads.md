# Deep Links and PWA - Contracts and Payloads (feature 11)

Phase 2 TL · BA sources: `docs/01-requirements-ba/11-deep-links-and-pwa/`. This feature adds no API endpoints. Contracts are markup, artifacts, headers, and one forward event contract. Governance: additive-only; any cache-key or endpoint drift is a `DEVIATION FLAGGED`.

## Anchor contract (public SSR)

| Item | Rule |
|---|---|
| Markup | `<a href="{canonical}" target="_blank" rel="noopener ugc">` on every block link |
| Rewrite table | YouTube (`youtube.com|youtu.be`) -> `https://www.youtube.com/watch?v={id}`; TikTok (`tiktok.com|vm.tiktok.com`) -> `https://www.tiktok.com/@{user}/video/{id}`; Instagram -> `https://www.instagram.com/{p|reel}/{id}`. Canonical hosts are registered App/Universal Links, so installed apps open natively |
| Tracking params | `utm_*`, `fbclid`, `igsh` stripped before emit |
| Forbidden sources | `javascript:`, `data:`, blob, custom and `intent:` schemes from input; rejected at feature 08 (`bad_url_scheme`); AnchorBuilder re-asserts allowlist, else renders plain text |
| Degrade | Any rewrite failure emits the plain `https:` canonical; never a broken primary |

## Banner + micro-script contract

| Item | Rule |
|---|---|
| SSR fragment | Banner markup + copy-link fallback present in every page payload (byte-identical) |
| Detection | Micro-script applies one boolean `webview` class on `<html>` when `navigator.userAgent` matches the shipped pattern list (Instagram, TikTok, Facebook, Zalo, Line) |
| CSS | Banner hidden by default, visible only under `.webview`; no layout shift (reserved space <= 1 row) |
| Primary action | iOS webviews: `href` swapped to `x-safari-https://{url}`; otherwise canonical `https:` |
| Copy fallback | `navigator.clipboard` with manual-select fallback text; always functional |
| Artifact | `GET /public/micro-script.<hash>.js`; <= 5 KB uncompressed; `Cache-Control: public, max-age=31536000, immutable`; CSP `script-src 'self'` safe (no inline) |
| Events | `banner_show` on first reveal; `escape_tap{result}` per action; sessionStorage dedupe keys only, no cookies |

## Beacon event contract (forward: features 12-15)

| Event | When | Payload |
|---|---|---|
| `banner_show` | Banner first revealed in a webview | `{slug, ts}` |
| `escape_tap` | Primary or copy action | `{slug, result: native|browser|copy, ts}` |
| `prompt_shown` / `prompt_accepted` / `prompt_dismissed` | Install flow outcomes | `{account-scope, ts}` |

Transport: existing `POST /api/t/:username` (analytics-owned). This feature never adds an endpoint.

## Headers matrix (edge / origin)

| Path | Cache-Control | Other |
|---|---|---|
| `/public/*` | `public, max-age=31536000, immutable` | `Content-Type: text/javascript` |
| `/app/sw.js` | `no-cache` | `Service-Worker-Allowed: /app/` |
| `/app/manifest.webmanifest` | `public, max-age=86400` | `Content-Type: application/manifest+json` |
| `/app/**` (HTML) | `no-cache` | `X-Robots-Tag: noindex`; CSP includes `worker-src 'self'` |
| `/assets/*` | `public, max-age=31536000, immutable` | unchanged (feature 10) |
| `/:username` | per feature 10 | unchanged; `Vary: Cookie` owner-mode only |

## Fallback routing (tests pin feature 10's order)

1. `/app/*` -> SPA origin; `sw.js` and manifest served before the fallback (never swallowed).
2. Unknown public slug -> 404 HTML; SPA HTML is never served for slugs.
3. `?mode=owner` renders the owner toolbar server-side; suspended/retired profiles behave per feature 10.
