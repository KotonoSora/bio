# Link Blocks (content list + scheduling)

PLAN ref: P1b-1, P1b-2. Rendering/theme treatment lives in `appearance/themes-avatar.md`.

## Purpose

Define the editable content list of a Link-to-Bio page: an ordered set of blocks (links, socials, media, embeds, and a `resume` pointer) with visibility and time-scheduling. The type superset is frozen now so P2 resume/shop work needs no type migration later.

## Users

- Creator: *As a Creator, I want to add, edit, reorder, hide, and schedule blocks from the dashboard so that my page content stays ordered without a later type migration.* Adds, edits, reorders, hides, and schedules blocks from the dashboard.
- Visitor: *As a Visitor, I want to see only visible, currently-scheduled blocks in dashboard-defined order so that hidden or out-of-window content never leaks publicly.* Sees only visible, currently-scheduled blocks in dashboard-defined order.

## Functional requirements

1. Block types (fixed superset, no additions without migration note): `link|social|gallery|video|music|event|form|product|course|tip|resume|contact|embed`. (`embed` covers third-party embeds such as reviews or IG/TikTok posts.)
2. Dashboard CRUD on `/app/*`: create, edit, delete, duplicate; drag-to-reorder persists `position` and survives reload.
3. Per-block visibility: `is_visible` flag plus optional `visible_from` / `visible_until` schedule window (link-scheduler parity). Outside the window the block is hidden publicly without deletion.
4. Link presentation options: sizes small/medium/big/button, optional thumbnail, optional carousel grouping for galleries.
5. `resume`-type block carries `payload_json{document_id}` pointing at `docs/resume-builder/` documents; all other resume semantics live there.
6. Payloads are data only: no raw HTML/JS/CSS fields. Embeds store a URL + provider id; the renderer decides the markup.

## Data / API touches

- Table `profile_blocks{profile_id, type, position, payload_json, is_visible, visible_from?, visible_until?}` plus standard `user_id TEXT NOT NULL` + index + RLS policy. `position` is a dense integer re-sequenced on reorder.
- Endpoints: `GET /api/v1/blocks?profile_id=`, `POST /api/v1/blocks`, `PATCH /api/v1/blocks/:id`, `DELETE /api/v1/blocks/:id`, `POST /api/v1/blocks/reorder` (atomic position persist). All scoped `WHERE user_id = session.userId`; public reads resolve via `username + is_public=1` and filter `is_visible` + schedule window server-side.
- `payload_json` per-type schemas (examples): link `{url https:, title, size, thumbnail_r2_key?}`; social `{network, url}`; embed `{provider, url}`; resume `{document_id}`. Server validates `type` against the frozen enum and `url` against the `https:` allowlist.

## Acceptance criteria

- Reorder via drag then reload: order persists identically on dashboard and public page. *GWT: Given a drag reorder; When the dashboard and public page reload; Then the order persists identically on both.*
- Hidden or out-of-window block: absent from public HTML/JSON, still listed in dashboard with status reason. *GWT: Given a hidden or out-of-window block; When the public page and dashboard render; Then it is absent from public HTML/JSON and still listed in the dashboard with a status reason.*
- Unknown `type` or non-`https:` URL on create/update: rejected 422, nothing written. *GWT: Given an unknown `type` or non-`https:` URL on create/update; When submitted; Then the API rejects with 422 and nothing is written.*
- Public page renders blocks strictly by ascending `position`; ties broken by stable row order. *GWT: Given blocks with ascending `position` and ties; When the public page renders; Then blocks appear strictly by ascending `position` with ties broken by stable row order.*
- IDOR probe: user A mutating user B `profile_id`/block returns 403; RLS denies at DB. *GWT: Given user A mutating user B `profile_id`/block; When evaluated; Then the API returns 403 and RLS denies at DB.*
- XSS probe: script/iframe/event-handler in any payload field stripped; anchors carry `rel="noopener ugc"`; CSP holds. *GWT: Given script/iframe/event-handler input in any payload field; When rendered; Then it is stripped, anchors carry `rel="noopener ugc"`, and CSP holds.*

## Out of scope

- Profile identity/visibility (see `profile/public-profile.md`); themes/avatar (see `appearance/themes-avatar.md`); public SSR + cache (see `public-page/share-seo.md`); analytics (see `analytics/profile-analytics.md`).
- Resume documents/templates/print/portability (`docs/resume-builder/`); shop `products`, courses/lessons, tips purchase, leads capture/CSV, custom domains (separate majors); P4 publishing/automations/campaigns/agency.

## Open questions

- Q1: Max blocks per profile on free vs pro — unlimited v1, or a cap to bound page weight?
- Q2: Carousel autoplay and thumbnail dimensions — fixed platform values or per-block settings?
