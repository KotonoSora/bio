# Short Links Frontend Plan (link manager + mint flows — UI build steps)

PLAN ref: P2 link-manager UX (mint, edit destination/expiry, tombstone, copy, stats entry, QR entry; quotas visible). BA source: `docs/url-shortener/links/short-links.md`. Server truth in `docs/url-shortener/links/short-links-backend-plan.md` (codes, lifecycle, quotas — never redefined here).

## Purpose

Give the frontend developer an ordered UI procedure for the link manager: minting with honest quota feedback, destination/expiry editing, tombstone deletes, copy-to-clipboard, and handoffs to stats and QR flows. Follow steps 1–6 in order. Codes are never editable, never recycled — the UI makes both facts visible instead of merely enforcing them.

## Users

- Frontend developer: builds `/app/links` against the backend plan's contracts.
- QA: executes the mint/edit/tombstone/quota drills.
- Creator/operator: mints campaign/QR/bio-overflow links, reads status, hands off to stats/QR.

## Functional requirements

1. Route and modern UI shell: `/app/links` (owner-only) — table (short URL, destination preview, status chip, expiry, opens (from the analytics reads, same `generated_at` honesty), overflow menu) + `New short link` entry. Client state: `links[]` keyed by code; `quota {used, limit, resetsAt}` from the mint endpoint's quota headers (every mint response carries quota state — the UI never guesses remaining quota).
2. Step 1 — mint flow: dialog (destination URL input with `https://` prefix hint + instant `destination_blocked` pre-check? No — blocklist lives server-side; the client checks scheme/format only and lets the server judge safety) → kind toggle (random default; vanity reveals code input with live availability check via `HEAD`-style probe endpoint? No extra endpoint — availability is proven at mint; the UI offers `Check` calling a lightweight `GET /api/v1/links/check?code=` read? That endpoint does not exist in the backend plan — DO NOT invent it: vanity availability feedback arrives from the mint response itself (`409 code_taken` with suggestions? Backend returns conflict; UI maps to `Taken — try …` with generated alternatives computed client-side from the requested string + random suffixes). Correction implemented: mint directly; `409` maps to taken-state with client-generated alternatives; no invented check endpoint. → expiry picker (optional, campaign default hint) → `POST` → `201` prepends the row with copy button focused → `422 destination_blocked` names the reason → `429` shows quota exhausted + reset time.
3. Step 2 — edit flow: row → edit dialog (destination + expiry ONLY — code/kind/owner fields rendered read-only with lock icons and `Codes never change` caption) → `PATCH` → `200` updates row → tombstoned/expired rows show their state chips with `what this means` popovers (gone-page / suspension copy stated plainly).
4. Step 3 — tombstone flow: delete → confirm dialog states permanence honestly (`Link stops resolving. Past stats are kept. The code is never reused.`) → `DELETE` → row flips to expired-state chip (row stays visible with history — tombstones are visible, not vanished; vanishing would imply reuse). No hard-delete affordance exists anywhere.
5. Step 4 — copy/handoff flow: copy button per row (short URL + optional QR-deep-link to the QR tab with code preselected); `Stats` per row jumps to the analytics per-link view with the code preselected (same numbers, one hop); bulk actions do not exist for creators (no bulk UI — bulk is ops-only, so no bulk affordance to audit).
6. Step 5 — quota visibility: quota meter in the route header (`N of M links · resets …`); at 90% an upsell-adjacent notice (plan page link, never a dark pattern); exhausted mint button disabled with reset countdown (disabled + reason, not a surprise 429 after form effort — though the server still enforces).
7. Step 6 — edge cases: expired session mid-mint (form preserved, re-login resumes); destination edit to a now-blocklisted URL (server 422, row keeps old destination — optimistic edits roll back, stated in-UI as `kept previous destination`).

## Data / API touches

- Screen → endpoint map: list → owner link reads; mint → `POST /api/v1/links`; edit → `PATCH /:code`; tombstone → `DELETE`; stats/QR → handoff routes with code param. Session cookie + CSRF; Turnstile widget on the mint dialog submit (abuse-shaped action gets the widget).
- Type mirror: `ShortLink {code, destination, kind, expiresAt?, status, quota?}`. Code → UI map: `destination_blocked` → dialog error with reason; `link_quota_exhausted` → quota panel + reset time; `409 code_taken` → vanity alternatives; `immutable_field` → (unreachable by UI — fields locked); `403` → re-login.

## Acceptance criteria

- Mint drill: valid mint prepends + focuses copy; blocked destination names reason; exhausted quota disables pre-submit with countdown; taken vanity offers alternatives with no invented endpoint (network log proof: only `POST` mint calls).
- Edit drill: code/kind visibly locked with caption; destination/expiry save with rollback + `kept previous` copy on 422.
- Tombstone drill: confirm states the three facts (stops resolving / stats kept / never reused); row persists as expired-state chip.
- Handoff drill: stats jump lands preselected with identical figures; QR jump preselects the code.
- Security drill: no bulk UI exists (grep); no `user_id` in bodies; no client-side availability oracle beyond mint responses.

## Out of scope

- Mint/resolve/suspend logic (backend plan above); table DDL (database plan above); QR generation UI (qr frontend plan); stats dashboards (analytics frontend plan); affiliate/ad attribution UIs (their minors).

## Open questions

- Q1: Vanity alternative suggestions — client-generated suffixes enough, or server-provided list (requires new endpoint — deferred)?
- Q2: Row-level inline destination edit vs dialog — dialog v1, inline later?
