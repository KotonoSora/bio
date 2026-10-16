# Resume Documents - Security, Logging, and Rate Limiting (feature 17)

Phase 2 TL · BA sources: `docs/01-requirements-ba/17-resume-documents/` (BR-01, BR-04, BR-05, BR-07). Baseline: `docs/02-architecture-techlead/00-platform-baseline/08-security-baseline.md`; gates: `docs/02-architecture-techlead/00-platform-baseline/06-release-gates.md`.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | IDOR + RLS: every owner route scopes by server-set `user_id`; RLS policies `d_owner`/`s_owner` back it in the database; foreign ids deny (`403`, or `404` shape where leak-free); no route accepts a client-supplied `user_id` | AC-05; API + direct-PG probe |
| S-2 | Stored-clean content: sanitizer strips script/iframe/event-handlers at capture time; renderers inherit safety; CSP holds on dashboard and `/cv` | AC-06; payload corpus + CSP header check |
| S-3 | Immutability: no UPDATE grant on `snapshots`, no re-save route; archive DELETE only after verified blob write; audit rows for save/publish/delete/archive | AC-01, AC-07; grant + behavior probes |
| S-4 | Single-public integrity: publish transaction + partial unique; race forced in drill; unpublish clears both flags atomically | AC-08; race drill |
| S-5 | Dangling-public prevention: unlink-before-delete ordering; unlink idempotent; crash drill proves the safe direction; no path deletes a linked document without confirm | AC-09; crash-injection drill |
| S-6 | Public read discipline: internal `published` read returns parity `404` for missing/private/draft-only/retired; no version bodies leak on other routes | AC-04; oracle drill |
| S-7 | Archive integrity: private bucket, server-side reads only, no presigned URLs; object write verified before PG delete; NDJSON append semantics (one version per line) | AC-07; bucket-policy probe |
| S-8 | Purge participation: participant handler deletes rows + archive objects idempotently by `runId`; counts in the certificate; no cross-user deletes by construction (RLS + scoped queries) | Feature 02 drill re-run with a resume fixture |
| S-9 | Logging: ids/versions/timings only - never resume content, never snapshot bodies; audit retention 24 months | Log scan |

## Rate envelopes

| Route class | Envelope | On breach |
|---|---|---|
| Create / duplicate | 60/h/user + Turnstile on create hotspots | `429 throttled` + `Retry-After` |
| Snapshot save | 30/h/user | `429`; client keeps the draft |
| Version reads | 600/min/user | `429` |
| Internal `published` read | service budget per public-site render, trace-attributed | `429`; public-site falls back to parity `404` for that render |
| Purge participant | runner-driven, idempotent | retry per feature 02 |

## Degrade matrix

| Condition | Behavior |
|---|---|
| profile-service read down during capture | Save fails closed (`503 capture_unavailable`); no partial version; draft intact; retry safe |
| profile-service unlink down during confirmed delete | Delete aborts; block + document both stay; retry safe; alert |
| Blob store down | Archive job pauses (nothing deleted); retrieval errors with the stated message; saves/publishes unaffected |
| Archive job stalled | PG grows past the 30-day boundary; alert; no data loss; catches up (idempotent) |
| resume-service down | `/cv` renders parity `404`; dashboard resume section unavailable; profile page otherwise unaffected |
| public-site down | Resume features unaffected (read side only) |

## Gates

- G4: endpoints additive-only; `PublicResumeResponse` rename-frozen (feature 10 consumer); linkage rows additive in feature 08's contract.
- G5: S-1, S-2, S-3, S-5 probes run per release.
- G7: drill set AC-01..AC-10 attaches to the tag; feature 02 purge drill gains a resume fixture (one public document + archive object).
