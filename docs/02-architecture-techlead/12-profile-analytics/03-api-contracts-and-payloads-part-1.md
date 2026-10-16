# Profile Analytics - Contracts and Payloads, Part 1: Beacon Intake (feature 12)

Phase 2 TL · BA sources: `docs/01-requirements-ba/12-profile-analytics/02-user-stories-and-business-rules.md` (BR-01, BR-02, BR-05, BR-10, BR-14). Owner reads: `docs/02-architecture-techlead/12-profile-analytics/04-api-contracts-and-payloads-part-2.md`.

## Beacon endpoint (public)

`POST /api/t/:username` — called by the feature-11 micro-script via relative URL through the gateway (same origin; no CORS).

```kotlin
data class BeaconRequest(
  val kind: String? = "view",       // view | click | banner | prompt
  val blockId: String? = null,      // for kind = click: exactly one of blockId | productId
  val productId: String? = null,    // shop link-out clicks (feature 22)
  val referrer: String? = null,     // falls back to the Referer header
  val utm: Map<String, String>? = null, // captured, forwarded to the audience slice (13)
  val hints: Hints? = null,         // coarse client hints only
  val bannerEvent: String? = null,  // show | escape (kind = banner)
  val escapeResult: String? = null  // native | browser | copy
) { data class Hints(val deviceClass: String?, val browser: String?) }
```

Handler order (zero PG writes — asserted by query-log test): challenge token claim (when configured) -> per-IP token bucket -> crawler denylist -> 10-minute dedupe -> username resolve -> block-id check -> enqueue + respond.

| Step | Rule | Rejection counter |
|---|---|---|
| Challenge | Token claim verified when the platform challenge is enabled | `challenge_invalid` |
| Throttle | Per-IP token bucket (120/min, burst 240); `429` + `Retry-After` | `throttled` |
| Bots | Configurable UA denylist, default-on; never stored | `bot` |
| Dedupe | `visitorKey = HMAC(salted ip+ua, dailySalt)`; 10-min window per (visitor, page/block) | (collapsed silently) |
| Resolve | profile-service resolve with redirects followed (BR-10); unknown or retired -> drop after `202` | `unknown_target` |
| Block check | Against the cached windowed blocks read; mismatch -> block count skipped, view stands | `unknown_block` |
| Product target | `productId` clicks (feature 22) are counted without a target check in v1 (no cheap verification path; bounded by throttle + dedupe) | - |

Responses: `202 {ok:true}` on every accepted path (including dropped targets — no oracle); `400` malformed shape; `429` + `Retry-After`.

## Queue message and raw archive

```json
{"v":1,"kind":"click","profileId":"prf_...","blockId":"blk_...","day":"2026-10-16",
 "referrerGroup":"instagram","country":"VN","deviceClass":"mobile","ts":"..."}
```

- `day` is derived server-side; no raw IP, no raw UA, no full referrer URL in the message.
- Enqueue to Redis stream `beacon:q` (consumer group `rollup`); at-least-once with idempotent upserts.
- Raw archive: one NDJSON file per drain per shard — `analytics/raw/y=YYYY/m=MM/d=DD/h=HH/part-<n>.ndjson` on the uploads volume; retention sweep 90d; never read by any API.

## Resolution cache

`resolve:{username}` -> `{profileId, blocks[], state}` in Redis, TTL 300 s; redirects followed from `username_redirects`; miss -> one profile-service call. Service degradation: resolver down -> drop + `unknown_target` counter, page unaffected.

## Vocabulary governance

`kind` and rejection reasons are closed vocabularies, extended additively only (`banner`/`prompt` arrived from feature 11; `deviceClass` values owned by 13). No field is renamed; additive changes are recorded here first.
