# Resume Sharing Frontend Plan (share dialog + A4 PDF view — UI build steps)

PLAN ref: P2-1 share UX (five copy buttons, revoke messaging, A4 PDF view, bio-embed toggle). BA source: `docs/resume-builder/sharing/resume-sharing-links.md`. Server truth in `docs/resume-builder/sharing/resume-sharing-links-backend-plan.md` (derivation, revocation, purge — never redefined here).

## Purpose

Give the frontend developer an ordered UI procedure for sharing: the five-button share dialog with per-type honesty captions, publish/revoke state messaging, the A4 PDF view, and the bio-embed toggle. Follow steps 1–6 in order. Copying a link never creates backend state (except the idempotent first-mint of the shortened URL, which the UI discloses once).

## Users

- Frontend developer: builds the share dialog + A4 view against the backend plan's contracts.
- QA: executes the parity-display, revocation-messaging, and mint-once drills.
- Job seeker: copies the right link per application, prints, revokes by unpublishing.

## Functional requirements

1. Entry points: `Share` button (document editor + list rows) opens the share dialog; `View live CV` jumps to the standard URL; A4 view lives beside the template preview (same frozen snapshot, print CSS). All three share the `ShareLinkRow` component (URL + copy button + status caption + type explainer) — one component, five instances.
2. Step 1 — dialog load flow: open → `GET share-links` → five rows render (standard `/:username/cv`; SEO row showing the SAME url with `Tags verified ✓` (rechecked per open — an SEO row that merely assumes tags is how tag regressions hide); shortened (short URL or `Mint on first copy` affordance — first copy triggers the helper's idempotent mint, disclosed as `Creates a permanent short link on first use`); custom (bound host URL or `Connect a custom domain` entry linking the account domain flow); bio-embed (`/:username` + anchor with live/dead state from block visibility). Unpublished document → dialog shows `Not public — publish to enable sharing` with the publish entry (copy buttons absent, not disabled-mysteriously).
3. Step 2 — copy flow: per-row copy (clipboard + `Copied` confirmation + first-copy mint disclosure where applicable); QR affordance beside the shortened row (jumps to the shortener QR flow with code preselected — QR pixels live there, previewed here as thumbnail only). No row ever shows a URL that 404s: row states derive from the same resolver rules (unpublished → rows hidden per step 1, not shown-dead).
4. Step 3 — revoke messaging flow: unpublish (from the snapshot publish bar) → share dialog (if open) flips to the not-public state live; already-copied links are covered by the backend revocation matrix — the dialog states it once (`Copies you already sent stop working within ~2 minutes of unpublishing`), so support never explains link half-life twice.
5. Step 4 — rename flow: username change → dialog URLs update on next open (derived, never stored — no stale URLs exist to refresh; the shortened row keeps working because its destination re-derives — stated as `Short links survive renames` caption, a real user anxiety answered in-UI).
6. Step 5 — A4 PDF view flow: `Preview A4` → frozen snapshot in A4 frame (same print CSS as export, Chrome/Safari/Firefox/Edge-verified styles) → `Print / Save PDF` calls `window.print()` (browser dialog; zero client PDF code — grep-verified absence of jsPDF/html2canvas). Draft edits after opening the A4 view do not leak in (bound to frozen version id at open, with the re-open notice pattern from the templates plan).
7. Step 6 — bio-embed toggle flow: `Show on my bio page` switch → binds/unbinds the `resume` block payload (via the block link-document endpoint) → public `/:username` gains/loses the section without breaking surrounding blocks (preview thumbnail of the bio section included so the toggle's effect is visible before saving).

## Data / API touches

- Screen → endpoint map: dialog → `GET share-links`; shortened first-copy → same call (idempotent mint server-side); custom connect → account domain flow (handoff, not embedded); bio toggle → block link-document endpoint; A4 view → snapshot reads (frozen version); print → `window.print()` only.
- Type mirror: `ShareLinks {standard, seoSameAsStandard, shortened?, custom?, bioAnchor?, unpublished: bool}`. Row states derive from `unpublished` + per-type presence (no separate status endpoint).

## Acceptance criteria

- Dialog drill: published document shows five live rows with verified SEO tag state; unpublished shows the publish entry, zero dead URLs.
- Copy drill: each row copies its exact URL; shortened first-copy discloses the mint once; QR jump preselects the code.
- Revoke drill: unpublish flips open dialog to not-public with the half-life copy; sent links die per the backend matrix.
- Rename drill: post-rename dialog shows new URLs; shortened row captioned rename-proof.
- A4 drill: frozen-snapshot A4 frame prints via browser print only; no PDF code in bundle (grep); draft edits don't leak.
- Toggle drill: bio switch adds/removes the section with live thumbnail; page never breaks.

## Out of scope

- Derivation/revocation/purge logic (backend plan above); source/cache contracts (database plan above); version timeline (snapshot frontend plan); template visuals (templates frontend plan); shortener/QR mechanics (url-shortener plans); domain verification UI (account major).

## Open questions

- Q1: QR thumbnail in the dialog — live render or static icon linking out (current: thumbnail preview, pixels from shortener flow)?
- Q2: Copy-count analytics (which type gets copied most) — useful signal or tracking overreach?
