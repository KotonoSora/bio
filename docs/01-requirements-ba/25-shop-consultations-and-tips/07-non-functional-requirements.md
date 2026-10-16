# Consultations and Tips - Non-Functional Requirements (feature 25)

Phase 1 BA · Every threshold numeric.

## Performance

| # | Requirement |
|---|---|
| NFR-01 | Slot query p95 ≤ 250 ms (60-day window); hold creation p95 ≤ 300 ms; confirmation p95 ≤ 500 ms after grant. |
| NFR-02 | Tip checkout initiation p95 ≤ 400 ms; message write + moderation p95 ≤ 250 ms; wall render ≤ 60 s stale. |
| NFR-03 | Reminder sweeps every 5 min; delivery within ±5 min of T-24 h / T-1 h targets. |
| NFR-04 | Hold expiry sweep every 60 s; freed-slot visibility ≤ 60 s. |

## Limits and quotas

| # | Requirement |
|---|---|
| NFR-05 | Booking numbers: hold TTL 10 min; notice ≥ 2 h; horizon 60 days; granularity 15 min; buffers 0-120 min; durations 15-240 min. |
| NFR-06 | Tip numbers: floor 10 000 / ceiling 5 000 000 VND; presets 3-6; message ≤ 280 chars; one jar per profile. |
| NFR-07 | Rate envelopes: holds 20/h per buyer; reschedules 10/h; cancels 20/h; tip checkout 30/h per IP; slots 120/h per IP; moderation 200/h. |
| NFR-08 | Reminder volume ≤ 3 transactional sends per booking; no marketing-class sends. |

## Security and data

| # | Requirement |
|---|---|
| NFR-09 | Deny-by-default; creator + buyer scoping with RLS; signed tokens (HMAC, TTL slot + 24 h) are the only session-free path; IDOR 403 zero-rows. |
| NFR-10 | Guest tips: Turnstile + throttle + bounds; email stored hashed for receipts linkage, never sold or listed; receipts via the existing pipeline. |
| NFR-11 | Messages: sanitized plain text (links/markup stripped); pending invisible publicly; moderation audited; wall caches approved-only rows. |
| NFR-12 | Money: zero amount math in this feature; orders snapshot price-at-booking; ledger binding 32-40; refund reasons fixed (`cancel_ge_24h`, `creator_cancel`, `hold_expired`, `tip_final`, courtesy-refund audited). |

## Sessions, localization, accessibility

| # | Requirement |
|---|---|
| NFR-13 | Sessions cookie-based at the edge; guest tips need no session; guest bookings do not exist (BR-16). |
| NFR-14 | Slots rendered in buyer-local time **and** creator timezone; vi/en copy; VND integers only. |
| NFR-15 | Slot grid keyboard-navigable; policy checkbox screen-reader labeled; wall rows readable without JS. |
