# Short Links (core shortening service)

PLAN ref: none — new scope beyond PLAN P0–P5, flagged explicitly. Proposed as P4-growth (serves ads, affiliate, campaigns, QR); needs a plan update before build. First consumer-ready paths are the affiliate `/r/` and ad `/a/` redirects, which converge onto this service instead of growing their own. Profile `/:username` URLs are NOT short links and never enter this system.

## Purpose

Define the platform's single URL-shortening service: every trackable link — campaign URLs, QR targets, SMS/print links, affiliate and ad redirects — mints here, resolves here, and counts here. Without one service, each feature builds its own redirector with its own namespace, analytics, and abuse holes (exactly how username collisions and uncounted redirects happen). One namespace, one redirect contract, one abuse gate.

## Users

- Creator/operator: mints a short link for a destination (campaign, QR print, bio overflow), sets expiry, reads its stats.
- Visitor: opens a short URL and lands on the destination in milliseconds, with no interstitial except where safety demands.
- Abuse reviewer: blocks malicious destinations once, killing every short link pointing at them.

## Functional requirements

1. Namespace (collision-proof by construction): all short links live under reserved prefixes (`/go/<code>` primary; `/r/` and `/a/` grandfathered as affiliate/ad aliases resolving through the same table). The bare `/:username` namespace is never used for short codes — username claims and short codes can never collide, and the reserved-slug list gains `go, r, a` permanently.
2. Code generation: random base62, 7+ characters (unguessable, non-sequential — no enumeration); optional vanity codes (`/go/launch-sale`) subject to availability, length/floor rules, profanity/reserved screening, and owner scoping (vanity is per-account, never global-first-come for trademarked terms — see Q1). Codes are immutable once minted (destination editable, code never recycled while any click history exists).
3. Redirect semantics (normative, counting depends on it): default 302 with `Cache-Control: private, no-store` so every open re-hits the counter (cached 301s silently undercount — the classic shortener analytics bug); 301 only for explicitly permanent vanity moves, documented as uncounted-after-first. Redirect target validated at mint AND re-validated on resolve (destination allowlist `https:` + blocklist, same policy family as embeds).
4. Destination safety: blocklist-checked at mint and resolve (phishing/malware lists + platform abuse list); untrusted-but-unlisted destinations resolve directly in v1 (no interstitial page — deferred per Q2); reported links suspend in one audited action, killing resolution while preserving evidence rows.
5. Expiry and lifecycle: optional `expires_at` (campaign-scoped links expire with their campaign by default); expired codes return a branded gone-page (410 with creator contact, never a redirect to homepage); deleted destinations (creator removed the target) flip links to a dormant state instead of 404ing blindly where a fallback URL was declared.
6. Creation budgets: per-account mint quotas + per-IP rate limits (shorteners are spam infrastructure in the wrong hands); bulk minting is an audited ops action, never a public loop.

## Data / API touches

- New table `short_links{code UNIQUE, owner_id?, destination, kind (random|vanity), campaign_id?, expires_at?, status (live|suspended|expired|dormant)}` with standard `user_id` + RLS posture (owners manage own; ops manage all). Resolve path reads a hot cache (KV/CDN) with PG as truth; destination blocklist cached with short TTL. All PG via `worker/db/*`.
- Endpoints: `POST /api/v1/links` (mint), `PATCH /api/v1/links/:code` (destination/expiry only), `DELETE /api/v1/links/:code` (tombstones, never hard-deletes while clicks exist); public `GET /go/:code` (+ `/r/`, `/a/` aliases) — fast redirect, queue-logged hit, zero PG writes on the hot path. Ops suspend/resolve-report endpoints (audited).
- QR codes encode short URLs and inherit their lifecycle automatically (see `links/qr-codes.md`); affiliate/ad aliases carry their signed refs as query params, never as separate tables.

## Acceptance criteria

- Collision drill: username `go`/`r`/`a` claims rejected; short code never resolves as a profile and vice versa (namespace test matrix green).
- Counting drill: 1,000 opens of a 302 short link = 1,000 counted hits (no CDN/proxy caching swallows); 301 vanity documented as uncounted-after-first with an explicit test asserting the difference.
- Safety drill: blocklisted destination rejected at mint AND at resolve (re-listed after mint); reported link suspends platform-wide within minutes with evidence preserved.
- Expiry drill: lapsed code serves the branded gone-page; dormant-target link uses the declared fallback or the gone-page, never a blind 404.
- Abuse drill: mint-flood throttled per account/IP; bulk minting outside ops impossible (403 + audit).

## Out of scope

- Click analytics presentation and fraud filtering detail (see `tracking/link-analytics.md`); QR generation and print guidance (see `links/qr-codes.md`).
- Profile URLs and SEO (`docs/link-to-bio/`); affiliate attribution and ad measurement (their own minors — converged as consumers, not redefined); custom short domains (named future work, see Q2 in tracking doc); P4/P5.

## Open questions

- Q1: Vanity governance — trademark dispute process for contested codes (first-come vs claim-review)?
- Q2: Interstitial warning pages for unlisted-but-untrusted destinations — safety gain worth the friction, or blocklist-only in v1?
