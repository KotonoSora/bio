# Short Links - Context and Scope (feature 29)

Phase 1 BA · Feature 29 of 53, built in E2 · Implemented by `docs/02-architecture-techlead/29-short-links/` (6 files). Consumers: feature 30 (qr-codes), feature 31 (short-link-analytics), gated features 48-50.

## Business objectives

| # | Objective | Measure |
|---|---|---|
| BO-1 | One redirector for every trackable link (campaign, QR, print, affiliate, ad): one namespace, one redirect contract, one abuse gate. | No other feature ships a redirect table or redirect endpoint. |
| BO-2 | Honest counting: every open of a default link is counted; proxies and caches never silently swallow opens. | 1,000 opens produce 1,000 counted hits (AC-02). |
| BO-3 | Millisecond redirects: resolve is the platform's fastest anonymous endpoint. | p95 <= 100 ms end-to-end (NFR-01). |
| BO-4 | One audited action kills any malicious destination platform-wide. | Suspension effective <= 5 min (NFR-04). |
| BO-5 | Spam-shaped infrastructure stays budgeted. | Mint throttled per account + per IP; bulk minting is ops-only and audited (BR-09, AC-06). |

## In scope

- Mint random base62 codes (>= 7 chars) and screened vanity codes (per-account).
- Resolve `/go/<code>` plus aliases `/r/`, `/a/` with a fixed state machine: live 302, permanent vanity 301, expired/dormant 410 gone page, suspended page.
- Destination safety: blocklist checked at mint and at resolve; report -> review -> suspend, audited, evidence preserved.
- Lifecycle: expiry, dormant fallback, tombstones, monthly quotas, per-IP throttles, hit events handed to analytics.

## Out of scope

- Click analytics presentation and fraud filtering (feature 31).
- QR generation and print output (feature 30).
- Profile `/:username` URLs and their SEO (features 06-10) - they never enter this system.
- Affiliate/ad attribution internals (features 49-50 are consumers, not owners).
- Custom short domains (not planned).

## Personas and RBAC matrix

| Role | CRUD Permissions | Description |
|---|---|---|
| Creator/operator | Create, read, update, tombstone own links | Mints campaign/QR/print links; reads status; hands off to stats/QR; never sees other owners' links. |
| Visitor | Read via resolve only | Opens `/go/<code>`; no account, no listing, no editing. |
| Abuse reviewer | Read all links, set status `suspended` | One audited action kills every link to a malicious destination; evidence rows preserved. |
| Platform ops | All permissions plus audited bulk mint | Bulk minting exists only as an audited ops action; no public bulk endpoint. |
| Analytics service | Read hit events (queue) | Owns counting and aggregates; never writes the link table. |

## Open questions (non-blocking; v1 defaults below)

| # | Question | v1 default | Revisit trigger |
|---|---|---|---|
| Q-01 | Vanity trademark screening depth | profanity + reserved-word list only | first legal report |
| Q-02 | Interstitial page for external destinations | none (direct redirect) | abuse spike from a destination class |
| Q-03 | Default expiry per campaign type | none (never) unless the campaign sets one | campaign feature 48 lands |

Related specs: mint/resolve contracts `docs/02-architecture-techlead/29-short-links/03-api-contracts-and-payloads-part-1.md`; schema `docs/02-architecture-techlead/29-short-links/02-database-schema-design.md`.
