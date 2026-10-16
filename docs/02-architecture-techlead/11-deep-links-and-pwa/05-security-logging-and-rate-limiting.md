# Deep Links and PWA - Security, Logging, and Rate Limiting (feature 11)

Phase 2 TL · BA sources: `docs/01-requirements-ba/11-deep-links-and-pwa/` (BR-02, BR-04, BR-07, BR-09, BR-11). Baseline controls: `docs/02-architecture-techlead/00-platform-baseline/`. Envelopes inherit feature 10 unchanged.

## Controls

| # | Control | Rule |
|---|---|---|
| S-1 | Scheme allowlist | Only `https:` is emitted from user input; feature 08 rejects at authoring (`bad_url_scheme`); AnchorBuilder re-asserts at render; forbidden: `javascript:`, `data:`, blob, custom and `intent:` schemes |
| S-2 | Worker scope | `/app/sw.js` with `Service-Worker-Allowed: /app/`; root scope forbidden; CI asserts the scope string in the built artifact |
| S-3 | Worker cache honesty | `/api/*` network-only via allowlist (unlisted defaults to network); cache writes restricted to shell routes and `/assets/*`; no credential-bearing response is ever cache-written |
| S-4 | CSP | Public: `script-src 'self'` (micro-script only; no inline). Dashboard: `script-src 'self'`, `worker-src 'self'`, `connect-src 'self'`; no third-party origins |
| S-5 | Headers | `/app/*` `X-Robots-Tag: noindex` + meta; manifest MIME pinned; `/public/*` immutable; `sw.js` `no-cache` |
| S-6 | UA privacy | UA used request-scope only; never persisted, never logged on the public route, never a cache-key dimension |
| S-7 | Beacon hygiene | Events sent by the micro-script; no cookies; sessionStorage dedupe only; bot filtering + rate limits owned by analytics (12-15); payloads carry no PII |
| S-8 | Escape integrity | `x-safari-https://` swap happens client-side only on the iOS webview class; never emitted into SSR HTML; the copy fallback URL is always the canonical `https:` URL |
| S-9 | Install UX | Prompt only after engagement; no dark patterns; outcomes beaconed; iOS card shown once and dismissable |
| S-10 | Review gates | Absence drill (no new tables/endpoints/toggles); invariance drill; a mobile-specific aggregation surface fails review |

## Threat checklist

| Threat | Mitigation |
|---|---|
| Escape-banner spoof via malicious interstitial | Banner is SSR markup on the real origin; no third-party script; CSP `'self'` |
| Scheme injection through block URLs | S-1 (+ AC-05 fixtures) |
| Worker scope creep intercepting public SSR | S-2 + AC-11 routing drill |
| Cache fragmentation / per-visitor variants for phishing mirrors | Byte-identical rule + invariance drill (AC-07) |
| Dashboard figures leaked to crawlers | S-5 + feature-10 SEO exclusivity |
| Beacon forging / abuse | Analytics rate limits + bot filter; events carry no PII |
| Stale number presented as fresh | Mandatory offline stale label; AC-10 asserts labelling on every figure |
| Deep-link hijack by a malicious app | Only canonical hosts emitted; resolution is OS-level (App/Universal Links), never an in-page redirect |

## Logging and degrade

- Logs: the public route logs slug + status only; UA is never logged; worker errors surface as a single console warning; matrix results live in release notes.
- Degrade matrix: worker registration failure -> normal web app; JS disabled -> no banner, content intact; clipboard blocked -> manual-select fallback; escape blocked -> copy fallback; analytics down -> analytics-owned error states; edge cache down -> origin serve per feature 10.
- Rate limits: public reads unchanged from feature 10; `/public/*` immutable + edge cached (no origin amplification); beacon and dashboard limits owned by analytics and the platform baseline.
