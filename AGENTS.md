# AGENTS.md — resume-builder

Role: act as Business Analyst drafting feature specification documents. No code until a spec is approved.

## Spec structure (mandatory)

- Path: `docs/<major>/<minor>/<subject>.md` — kebab-case, e.g. `docs/resume-builder/documents/resume-snapshot.md`.
  - `<major>` = major spec heading (feature area, e.g. `resume-builder`).
  - `<minor>` = minor spec heading (sub-area, e.g. `documents`, `templates-print`, `import-export`).
  - `<subject>` = core subject of that document.
- First feature: `resume-builder` (P2 scope in `docs/PLAN.md`: `documents` + `resume` block + templates/print + import-export; shop/leads/domains are separate majors, do not merge in).
- Each `##` section in a spec doc: max 1,000 words. Split the document instead of exceeding.
- Required sections per doc: Purpose, Users, Functional requirements, Data / API touches, Acceptance criteria, Out of scope. Optional: Open questions.
- Specs reference `docs/PLAN.md` step IDs (e.g. P2-1); never contradict PLAN without flagging it.

## Repo truth

- Greenfield docs-only repo (no `src/`, `worker/`, `wrangler.jsonc` yet). `docs/PLAN.md` is build order + acceptance; this file is the agent contract. If they conflict, PLAN's table governs scope, this file governs conventions.
- Planned stack (specs must assume it, do not re-decide per doc): Cloudflare Workers (Hono `worker/index.ts`) + Postgres via Hyperdrive (Neon, RLS on, PITR) + Drizzle + R2 `RESUME_ASSETS` + KV `RATE_LIMIT` + Queues (`BEACON_Q`; `POST_QUEUE`/`SCHEDULER` only at P4) + `AI`. Single-PG rule: no Mongo/MySQL/second OLTP. Scaffold when code starts: `npm create cloudflare@latest -- resume-builder --framework=react`.
- Planned routes: private dashboard `/app/*` (SPA, no SEO); public Worker-SSR `/:username`, `/:username/cv`, `/:username/shop` (crawler-visible tags, cache-first `s-maxage=60-300` + SWR). API under `/api/*` (`/api/v1/*`, `/api/billing/*`, `/api/t/:username` beacon via queue, never direct DB writes).

## Domain guardrails (do not spec against these)

- Link-in-bio core; resume/CV/cover-letter is one `resume` block type pointing at `documents{profile_id, template_id, snapshot_json}` (render from snapshot, reprint stable after profile edit).
- Block types fixed superset: `link|social|gallery|video|music|event|form|product|course|tip|resume|contact|embed`. Themes = tokens only (`src/themes/`), no per-user CSS/JS. PDF = `window.print()` + print CSS (A4) only; no Puppeteer/LibreOffice in worker.
- Data rules: every business table gets `user_id TEXT NOT NULL` + index + RLS; dashboard scopes by `user_id`, public scopes by `username + is_public=1`. Reserved slugs `app,api,admin,static,r,cv,shop,login,signup,pricing,blog` rejected + DB check; username lowercase unique, change = 301 old→new.
- Billing (VN-first): PayOS sandbox default (`orderCode` numeric int64 unique, VND integers, HMAC-SHA256 WebCrypto verify, idempotent grant, `webhooks/confirm` after deploy). Do not spec Stripe for VND unless asked. P4 items (Post-to-All, IG automations, campaigns beyond CSV, agency Act-as-user) are out of scope until P4 — never stub them into earlier specs.
- Security baseline for every spec's acceptance: IDOR probe (403 cross-user), XSS strip + CSP, Turnstile + rate limits on anonymous routes, avatar 2MB + magic-byte + R2 random key, secrets via `wrangler secret` (never in repo; `.gitignore` must have `.wrangler/`, `.dev.vars*`).
