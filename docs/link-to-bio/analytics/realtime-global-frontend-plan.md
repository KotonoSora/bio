# Realtime Global Frontend Plan (now-line panel — UI build steps)

PLAN ref: P1d-2 realtime UX (pro live window; free locked teaser; degraded honesty). BA source: `docs/link-to-bio/analytics/realtime-global.md`. Server truth in `docs/link-to-bio/analytics/realtime-global-backend-plan.md` (window, gating, throttle, degraded contract — never redefined here).

## Purpose

Give the frontend developer an ordered UI procedure for the live panel: polling lifecycle, now-line rendering, conditional sales strip, degraded banner, and the load discipline that keeps launch-night checking from becoming a self-DDoS. Follow steps 1–6 in order.

## Users

- Frontend developer: builds the now-line panel (pro) and locked teaser (free).
- QA: executes the liveness, backoff, and degraded drills.
- Pro creator: watches launches minute-by-minute; trusts the aggregates footnote for anything billable.

## Functional requirements

1. Placement and eligibility: panel lives atop `/app/analytics/overview` (pro) and in the PWA in the same position. Free plans render the locked teaser (static upsell card — zero fetches, zero timers; verified in request logs). Eligibility comes from the plan claim, not a separate call.
2. Step 1 — poll lifecycle: on mount (pro only) `GET realtime?profile=` immediately → render → schedule next poll in 15–30s with ±5s jitter (jitter de-synchronizes tab farms); `document.hidden` pauses the timer (background tabs issue zero requests — asserted via request logs); tab close/unmount clears the timer (no orphaned intervals across route changes).
3. Step 2 — now-line rendering: visitors count (big number), cpm now-line (30-point sparkline from `cpmSeries`, downsampled further on small screens with exact totals kept), top active links (live rank chips), live city/country list (coarse names only). All values stamped `as of HH:MM:SS` from `generatedAt` (never "live" without a timestamp — honesty copy).
4. Step 3 — conditional sales strip: `sales` key present → revenue now-line + units-per-link ticks with the pipeline-lag label (`orders lag ~X`); key absent → no strip at all (no placeholder, no zero-state — absence is the design, stated in the panel's info popover once).
5. Step 4 — backoff discipline (the anti-self-DDoS core): `429` → exponential backoff (15s → 30s → 60s ceiling) with `Slowing updates (throttled)` copy; consecutive `degraded` → stretch interval to 60s and show the last-good banner; 3 consecutive errors → stop polling, show `Reconnect` button (manual resume, never infinite retry). Ten open tabs still converge under the per-profile server throttle — the client does not coordinate across tabs (server throttle is the backstop, documented here so nobody "fixes" it client-side).
6. Step 5 — aggregates footnote: static copy `Billed and reported totals come from hourly aggregates, not this feed` with a link to the overview tab. Screenshot disputes end here.
7. Step 6 — fast-load rules: panel chunk lazy with the overview route; sparkline canvas (not SVG-per-point) beyond 30 points; no animation frames while hidden; PWA installed view identical to browser view (same-numbers rule covers realtime too).

## Data / API touches

- Panel → endpoint map: pro panel → `GET realtime?profile=` on the lifecycle above; free teaser → no calls. `403` (demoted mid-session) swaps panel to teaser without error copy; `429` → backoff flow; `degraded` → last-good banner + stretched interval.
- Type mirror: `Realtime {generatedAt, windowS:1800, visitors, cpmSeries[30], topLinks, geo, sales?, status?, lastGoodAt?}`.

## Acceptance criteria

- Liveness drill: scripted visits render within two cycles with correct `as-of` stamps; quiet profile reads zero (not stale).
- Teaser drill: free account issues zero realtime requests across desktop/mobile/PWA.
- Backoff drill: forced 429s stretch intervals exponentially with throttled copy; 3 errors → manual Reconnect.
- Hidden-tab drill: backgrounded tab issues zero requests (request-log verified).
- Degraded drill: forced outage shows last-good banner with numbers intact; recovery resumes automatically.
- Footnote drill: aggregates copy present with working overview link.

## Out of scope

- Bucket/poll/gating logic (backend plan above); bucket contract (database plan above); historical/audience/earnings UI; order tables; push/alerts (v2).

## Open questions

- Q1: Jitter range ±5s — widen at 1M-concurrent scale?
- Q2: Manual Reconnect after 3 errors — or auto-resume on window focus (already refetches)?
