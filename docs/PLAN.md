# Detailed plan — link-in-bio + resume block (Cloudflare-first)

Source: `AGENTS.md` is the compact agent contract. This file is the build order + acceptance + security detail.

## 0. Stack validation (targets: 10M registered / 100k concurrent normal / 1M concurrent peak; security > cost)

- Keep on Cloudflare (cheapest edge per GB): Workers (Hono `worker/index.ts`), R2 `RESUME_ASSETS`, KV `RATE_LIMIT`, `AI` (JD-tailor), Turnstile, WAF + rate rules. All Workers-safe via fetch/WebCrypto.
- External: Postgres primary (Neon serverless scale-to-zero + Hyperdrive pooling, RLS on, PITR) from P1 — D1 cannot hold 10M users (~200–500GB) and is dropped as truth. PayOS (VN sandbox), Resend (receipts + P4 campaigns). No self-hosted DB in v1.
- Techstack changes vs starter: (1) Queues from P1 — `BEACON_Q` (click/view pipeline) + at P4 `POST_QUEUE`/`SCHEDULER`; no `setTimeout` delays, no direct DB writes from beacon. (2) OAuth token vault at P4 — `social_accounts{...access_token_enc...}` via `cf-aes-gcm` + `TOKEN_ENC_KEY`; never log tokens. (3) Read replicas + top-profile ISR pre-render at P5 (not upfront). PWA install prompt instead of native mobile app.
- Cost math: at 1M concurrent (~10–50k req/s) bandwidth + DB I/O dominate. Cheapest levers in order: (a) cache-hit >95% on `/:username` (CDN+KV, TTL 60–300s + SWR), (b) queue-batch all writes (hourly rollups), (c) Neon autoscale-to-zero + scale-up on demand, (d) read replicas last. Single-PG rule (no Mongo/MySQL second OLTP): two systems = two bills + two audit logs + cross-DB integrity gaps — worse cost AND worse security. Offload by tier (PG truth / R2 blobs+archives / KV+CDN cache), never by feature.
- Load gates (k6, staging mirror): P1 exit ≥10k concurrent public reads p95 <500ms; P3 exit ≥100k concurrent normal-use mix; P5 exit ≥1M concurrent read-heavy + 100k mixed. No phase ships past its gate.
- Database decision: Postgres primary from P1 (Neon + Hyperdrive, RLS on, PITR, least-privilege app vs migration roles). Size math: profile+blocks+1 snapshot ≈ 20–50KB/user → 10M users ≈ 200–500GB — D1's ~10GB cap is disqualified, so D1 is not used as truth. Growth killers (raw clicks, snapshot history) never touch PG: `link_clicks{day,count}` aggregates only via queue consumer, `snapshot_json` versions >30d to R2, raw beacon logs to R2 NDJSON 90d TTL. Portability: all SQL via `worker/db/*`, TEXT ids, ISO8601 timestamps. MySQL/PlanetScale: weaker Workers/Drizzle story — no reason to prefer. MongoDB: rejected — joins (`users→profiles→blocks→orders`), billing transactions, and Drizzle SQL-first all fight a document store; two systems also double audit/backup surface against our security-first rank.
- Backend decision: Option A default = Workers Hono (`worker/index.ts`) serves `/api/v1/*` + SSR `/:username` with PG via Hyperdrive (pooled, statement timeout 5s, single-flight per slug on miss storms); Option B = external API (NestJS/Spring/.NET/Laravel on Fly/Render/VPS) + Cloudflare as DNS/CDN/WAF/proxy, Workers keep static + SSR and proxy `/api/*` to `API_ORIGIN`. Contract frozen so A→B is proxy-only: JSON REST, cookie session, `user_id` server-scoped, idempotent `orderCode`/webhooks, no direct DB from client. Choose B only on: sustained >50k req/s API (not cacheable), PG-only ORM/RBAC needs, or team skill. Cloudflare-only-DNS mode = B without Workers SSR (not recommended — loses edge SEO/cache).
- Security note (top priority): WAF managed + rate rules on all anonymous routes from P0; Turnstile on signup/login/checkout/beacon; RLS on every business table + app-level `user_id` scoping (both, always); OAuth/PayOS secrets in `wrangler secret` with quarterly rotation; append-only `audit_log` for plan/role/username/domain/impersonation; PG PITR with quarterly restore drill. Engine or cache never replaces these — keep HMAC verify + IDOR probes regardless.
- Leave Cloudflare only on signal: queue backlog sustained → add consumers/partitions; PG primary CPU >70% at p95 → read replicas for public reads (writes stay primary); top-1% slugs hot → ISR pre-render + longer TTL. KV is throttle/cache only, never truth.

## P0 — scaffold (0.5 day)

| Step | Action | Done when |
|------|--------|-----------|
| P0-1 | `npm create cloudflare@latest -- resume-builder --framework=react` | C3 starter boots, `src/App.tsx` renders |
| P0-2 | Wire `wrangler.jsonc`: Hyperdrive→PG (`DB`), `RESUME_ASSETS`, `RATE_LIMIT`, `AI`, `BEACON_Q`; `run_worker_first: ["/api/*", "/:username", "/:username/cv", "/:username/shop"]`; reserve `POST_QUEUE`/`SCHEDULER` commented-out until P4 | `wrangler types` passes; PG reachable via Hyperdrive locally |
| P0-3 | WAF managed rules + rate rules on anonymous routes; Turnstile keys in `.dev.vars`; `.gitignore` (`.wrangler/`, `.dev.vars*`) | No secrets in repo; abuse paths throttled from day one |
| P0-4 | Verify: `npm run dev`, `npm run build && npm run preview`, `npx wrangler deploy` to `*.workers.dev` | All three green |

## P1 — share + SEO (link.me parity, researched from link.me/faq 2026)

| Step | Action | Done when |
|------|--------|-----------|
| P1a-1 | Better Auth on PG (RLS on, least-privilege app role; session + password reset + 30-day delete flow) | Signup/login/logout works locally; RLS blocks cross-user reads at DB level |
| P1a-2 | `profiles{user_id, username UNIQUE lowercase, display_name, bio, avatar_r2_key, theme_id, is_public, seo_title, seo_desc}` + reserved slugs (`app,api,admin,static,r,cv,login,signup,pricing,blog,shop`) in app + DB check | Duplicate/uppercase/reserved username rejected |
| P1a-3 | Username-change 301 table; free plan = unlimited links + badge | Old URL 301s to new |
| P1b-1 | `profile_blocks{profile_id, type, position, payload_json, is_visible, visible_from?, visible_until?}`, type ∈ `link\|social\|gallery\|video\|music\|event\|form\|product\|course\|tip\|resume\|contact\|embed` | Superset exists, P2 needs no type migration |
| P1b-2 | Dashboard CRUD + drag-order (`position` persist) + link sizes (small/medium/big/button) + carousel + thumbnails | Reorder survives reload |
| P1b-3 | Themes as tokens (`src/themes/`: bg/text/font, video avatar; animated links + own logo = pro gate; no custom CSS upload) | Token switch restyles page, no CSS injection possible |
| P1b-4 | Avatar upload: 2MB cap + magic-byte check → R2 random key | Non-image rejected, key unguessable |
| P1c-1 | Worker SSR `/:username`, `/:username/cv`: title/meta/canonical/OG/Twitter/JSON-LD `Person` | Cold crawler (curl, no JS) sees tags |
| P1c-2 | Cache-first: `public, s-maxage=60-300, SWR=600`, purge on publish; KV single-flight lock per slug (miss storm guard); `robots.txt` + `sitemap.xml` (only `is_public=1`) | Cache-hit >95% at gate; publish visible <2 min, private excluded |
| P1d-1 | Beacon `POST /api/t/:username` → `BEACON_Q` (KV-dedupe 10min) → hourly consumer upserts `profile_views{day,views,interactions}` + `link_clicks{day,count}`; raw NDJSON to R2 (90d TTL); never direct PG writes from beacon | Zero PG writes per hit; consumer lag <1h at gate |
| P1d-2 | Derive views/clicks/engagement/period-compare/source-city (CF `cf-ipcountry` + Referer)/per-link; realtime-30min pro-only via KV window; QR = same URL | Dashboard shows link.me-parity numbers |

## P2 — resume + shop + leads + portability (link.me shops/courses parity, VN-adapted)

| Step | Action | Done when |
|------|--------|-----------|
| P2-1 | `documents{profile_id, template_id, snapshot_json}` ← `resume` block; `src/templates/` screen + `@media print` A4; `window.print()` only | Reprint after profile edit unchanged |
| P2-2 | Shop `/:username/shop`: `products{profile_id, kind: merch\|digital\|paywall\|course\|tip, title, price_vnd, file_r2_key}`; merch = POD listing (no inventory); digital/paywall = R2 signed URL after paid `orders{product_id}` | Paid buyer gets one-time download link |
| P2-3 | Courses: `course_sections/lessons{mp4_r2_key, materials}` + buyer-email claim + progress; tips = fixed-amount product | Student resumes last lesson |
| P2-4 | Leads: `form` block + Connect → `leads{profile_id, email, source, consent_at}` + CSV export (campaigns deferred to P4) | Export contains consent timestamps |
| P2-5 | JSON import/export profile+blocks+documents; LinkedIn/GitHub adapters; fixture profile for migrations | Round-trip lossless |
| P2-6 | `custom_domains{hostname, profile_id, verified}` via `_cf-challenge TXT`, serve same SSR | Unverified host never serves another user's page |

## P3 — monetize VN (PayOS sandbox-first)

| Step | Action | Done when |
|------|--------|-----------|
| P3-1 | `orders{user_id, product_id?, plan?, amount_vnd INT, status, orderCode UNIQUE}`; `POST /api/billing/checkout` → numeric `orderCode` + PayOS link (VND integers, same-origin return/cancel) | Sandbox link opens, amounts exact |
| P3-2 | Webhook `POST /api/billing/webhook`: HMAC-SHA256 via WebCrypto verify → idempotent grant (`paid` once) → entitlements; `webhooks/confirm` after deploy; local test via `cloudflared tunnel → wrangler dev` | Replay/tamper tests: single grant, 401 on bad sig |
| P3-3 | Plans: free 1 profile/unlimited links/tips/basic + badge; pro unlimited + domain + realtime + AI JD-tailor (`AI` + KV cap, `ai_generations{...credits}`) | Gate enforced server-side, not just UI |
| P3-4 | `orgs/org_members` skeleton for agency seats (full roster in P4) | Table exists, no orphan queries |

## P4 — publish + automate + agency (needs OAuth + Queues + audit; do NOT build in P1–P3)

| Step | Action | Done when |
|------|--------|-----------|
| P4-1 | `social_accounts` OAuth per network (YT/IG/TikTok/FB/X/Threads/LinkedIn; IG = Business) with encrypted vault + `TOKEN_ENC_KEY`; per-plan connection caps | Tokens never logged, refresh works |
| P4-2 | Composer + per-platform caption limits → `scheduled_posts{video_r2_key, caption_json, run_at, status}` via `SCHEDULER` + `POST_QUEUE` fan-out; poll status, never `await` publish in request | Failed post retryable with state |
| P4-3 | IG automations: `automations{kind: comment\|dm, match, reply_templates, delay_s}` + watcher; public-reply + 1-link DM → `leads`; contacts quota enforced pre-send | 100/yr-style quota blocks over-send |
| P4-4 | Resend broadcast to active `leads` + `campaigns{stats_json}` webhook (no segments/scheduling v1) | Delivered/Open/Click recorded |
| P4-5 | Agency: invite-by-username, roles owner/manager/member, scoped Act-as-user + audit log, leaderboard (views/clicks/CTR/sales) | Impersonation fully audited |

## P5 — scale-out (only after P3 gate passes at 100k concurrent)

| Step | Action | Done when |
|------|--------|-----------|
| P5-1 | PG read replicas for public reads (writes stay primary); Hyperdrive split read/write | Replica lag <1s at 1M concurrent reads |
| P5-2 | ISR pre-render top-10k slugs to R2 + raise TTL to 300s; KV single-flight already in P1c | Cache-hit >95%, origin req/s flat as traffic 10x |
| P5-3 | k6 gates: 10k (P1 exit) → 100k mixed (P3 exit) → 1M read-heavy + 100k mixed (P5 exit), p95 <500ms reads / <1s checkout | No phase ships past a red gate |
| P5-4 | Quarterly PG restore drill + secret rotation (`TOKEN_ENC_KEY`, PayOS, Hyperdrive creds) | Restore + rotation runbook green |

## Security checklist (run before each public deploy)

| # | Probe | Pass |
|---|-------|------|
| S-1 | IDOR: `userA` reads/writes `userB profile_id/block/order` | 403 everywhere |
| S-2 | XSS: script/iframe/event-handler in bio/link/payload/embed | Stripped, CSP holds |
| S-3 | Webhook: bad sig / replay / amount tamper | 401 / single grant / reject |
| S-4 | Uploads: fake image, SVG/HTML, 5MB file | Rejected or neutralized |
| S-5 | Abuse: rapid signup/login/checkout/beacon, unverified domain | Throttled (Turnstile+WAF), domain refused |
| S-6 | RLS: direct PG connection as app role reads another user's row; `audit_log` covers grant/role/domain/impersonation | Denied at DB + logged |
| S-7 | Commands: `npm run db:migrate` on staging mirror, `npm run build && npm run preview`, k6 gate for the milestone | Green + manual pass above |
