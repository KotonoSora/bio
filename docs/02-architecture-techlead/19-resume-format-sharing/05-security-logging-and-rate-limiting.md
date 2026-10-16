# Resume Format Sharing - Security, Logging, and Rate Limiting (feature 19)

Phase 2 TL · BA sources: `docs/01-requirements-ba/19-resume-format-sharing/` (BR-01, BR-02, BR-04, BR-06, BR-07). Baseline: `docs/02-architecture-techlead/00-platform-baseline/08-security-baseline.md`; gates: `docs/02-architecture-techlead/00-platform-baseline/06-release-gates.md`.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | Privacy invariant: columnar (no PII columns exist) + validator allowlist at every write + weekly re-scan flags; publish scrub-proof over the exact bytes stored | AC-01; schema inspection + hostile publish matrix + re-scan drill |
| S-2 | Injection isolation: closed schema only; renderers escape text and drop style/class/markup from data; CSP holds on builder, gallery, dashboard preview, cv | AC-06; hostile-field corpus on all four surfaces |
| S-3 | IDOR + RLS: owner predicate `f_owner`/`c_owner`; public reads `f_public` (`status='published'`); foreign ids -> `403` (or `404` leak-free shape for gallery); no client-supplied `user_id` | AC-05; API + direct-PG probes |
| S-4 | Clone isolation: copy-not-pointer; byte-compare at clone; `RESTRICT` blocks hard-delete with clones; author-side changes never touch clone rows | AC-04; mutation matrix |
| S-5 | Moderation integrity: reason mandatory on removal; audit with actor + reason; reporter identity never exposed; takedown is a status flip (evidence preserved) | AC-07; audit reconstruction |
| S-6 | Abuse: Turnstile on report + publish hotspots; gallery/clone/report throttles; WAF-level burst rules ride the baseline | AC-07 (burst half); rate probes |
| S-7 | Attribution integrity: frozen string updates only on the author's next publish; live resolution via internal read (no client-trusted author data); purge removes rows entirely | AC-03; rename/deactivate/purge drill |
| S-8 | Purge participation: participant deletes `formats` + `format_clones` + thumbnail objects idempotently by `runId`; RESTRICT does not block purge (participant deletes clones first / same transaction) | Feature 02 drill re-run with formats fixture |
| S-9 | No ungoverned surfaces: no thumbnail upload route, no raw-JSON editor, no paywall hooks (all grep gates) | AC-11; CI greps |

## Rate envelopes

| Route class | Envelope | On breach |
|---|---|---|
| Gallery list/detail (anonymous) | 600/min/IP + edge cache | `429` + `Retry-After` |
| Draft writes | 120/h/user | `429` |
| Publish / unpublish | 30/h/user | `429` |
| Clone | 60/h/user | `429`; idempotent `already:true` unaffected |
| Report | 10/h/IP (Turnstile) | `429` |
| Ops removal | 30/min/operator, audited | `429`; anomaly alert |

## Degrade matrix

| Condition | Behavior |
|---|---|
| Profile resolve down at gallery read | Cached author strings render; `authorResolvable` defaults false (non-linked) - never a broken link, bounded by the resolve cache |
| Thumbnail generation fails | Card renders text-first without the image; retry on next publish; metric |
| Blob store down at publish | Publish fails atomically (draft intact); retry safe; alert |
| Edge purge event lost | Gallery TTL (60 s) heals; takedown still hides within the TTL bound |
| Re-scan job stalled | Alert; gallery unaffected; flags catch up (idempotent) |
| resume-service down | Gallery parity error page (5xx with retry copy); clone/selection unavailable; documents render their current template unaffected |

## Gates

- G4: endpoints additive-only; `GalleryCardResponse`/`GalleryDetailResponse` rename-frozen (SPA consumer); `unknown_template` reused unchanged (feature 18).
- G5: S-1, S-2, S-3, S-4 probes run per release.
- G7: drill set AC-01..AC-11 attaches to the tag; moderation path (T-06) must be green before the public gallery flag flips.
