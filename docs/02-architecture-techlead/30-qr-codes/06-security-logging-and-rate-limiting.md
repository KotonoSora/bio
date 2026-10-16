# QR Codes - Security, Logging, and Rate Limiting (feature 30)

Phase 2 TL · BA sources: `docs/01-requirements-ba/30-qr-codes/` (BR-02..BR-12, NFR-04..NFR-12). Baseline: `docs/02-architecture-techlead/00-platform-baseline/08-security-baseline.md`; gates: `docs/02-architecture-techlead/00-platform-baseline/06-release-gates.md`.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | Encode-short-only: payload built server-side from the code; no request field can influence it; decode test on every export | AC-01; CI decode matrix |
| S-2 | Floors enforced pre-render (level/quiet/module/contrast); refusal beats rendering; no below-floor bytes exist | AC-02, AC-04; refusal matrix |
| S-3 | Server-only official generation: one server encoder (zxing, grep gate); print downloads come from the render log; the only client-side encoder is feature 10's share utility (documented allowance) | AC-07; greps |
| S-4 | Download safety: owner-bound HMAC grants (15 min) on image + label; service-mediated streams; no direct storage URLs | AC-08; grant matrix |
| S-5 | Packs: ops-only (`ROLE_OPS` re-verified per request), Turnstile, atomic, audited with code list; creator token → 403 + audit | AC-09 |
| S-6 | Lifecycle integrity: generation refused for non-live parents (409); old prints follow link state (no orphan resolves possible); cascade only on exceptional purge | AC-03; lifecycle drill |
| S-7 | Abuse budgets: quotas (60 renders/h owner; 10 packs/day ops), per-endpoint envelopes, gateway WAF/rate | NFR-09; burst drills |
| S-8 | Preview honesty: watermark overlay `PREVIEW — not for print`; no download path serves preview bytes | AC-07 |
| S-9 | File hygiene: `links/qr/` 30-day retention sweep; purge removes rows + files with the account; no cross-user file paths | purge drill; path audit |
| S-10 | Secrets: grant keys env/vault only; no storage credentials exist (volume mount + permissions); render requests log ids, never destination bodies | secrets + log greps |

## Rate envelopes

| Route class | Envelope | On breach |
|---|---|---|
| Generate (single) | 60/h per user + gateway 120/min per IP | 429 |
| Sheet | 30/h per user | 429 |
| Download (grant) | 10/min per render | 429 |
| Ops packs | 10/day per operator | 429 + audit |
| Ops sheet/manage | standard ops session limits | 429 |

## Degrade matrix (fail safe, never fail open)

| Condition | Behavior |
|---|---|
| Storage volume down | 503 `render_unavailable`; no log row without stored bytes; re-render later |
| Redis (db 3) down | Quota falls back to DB counts (bounded, not bypassed); resolve cache unaffected (feature 29's path) |
| DB down | 503; no rendering without the live-link read |
| Feature 29 mint path down (packs) | Packs fail atomically; single-link renders unaffected |
| Feature 31 down | Rendering unaffected; stats handoff shows 31's degrade state |
| Turnstile provider down | Generation fails closed at the gateway challenge |

## Threat checklist

Print-run orphans (payload rule — impossible by construction), stale-target reprints (re-render from live), unscannable exports (floors), encoder sprawl (grep gate), pack partials (atomic transaction), preview pass-off (watermark + no download path), grant sharing (owner-bound, short TTL), path traversal (server-derived names), quota bypass (Redis + DB fallback), cross-user access (RLS + 403/404).

## Gates

- G4: OpenAPI diff additive; codes registered; generate routes session-scoped; download grants not session-scoped; ops routes role-guarded.
- G5: refusal matrix, decode CI, grant matrix, pack atomicity drill.
- G7: AC-01..AC-10 attach to the tag; container drill (Docker + Podman) per baseline release gates; native-dep + encoder greps clean.
- Greps: one server encoder; feature-10 allowance only on the client; no PDF artifacts; secrets clean.
