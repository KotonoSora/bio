# Resume Portability - Security, Logging, and Rate Limiting (feature 21)

Phase 2 TL · BA sources: `docs/01-requirements-ba/21-resume-portability/` (BR-03..BR-10). Baseline: `docs/02-architecture-techlead/00-platform-baseline/08-security-baseline.md`; gates: `docs/02-architecture-techlead/00-platform-baseline/06-release-gates.md`.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | IDOR + RLS: export/import scope by server-set `user_id`; no cross-profile parameter exists; foreign ids -> `403`; direct PG as app role denied | AC-04; API + direct-PG probes |
| S-2 | Hostile-file gates: strict order (size pre-parse -> parse timeout -> version -> closed schema -> `https:` allowlist -> sanitize); parse bombs die at the size gate; every rejection counts `import.rejected{reason}` | AC-02; fuzz corpus + count assertion |
| S-3 | Stored XSS: sanitize at import AND capture; triple-surface drill (dashboard preview, public CV, re-export) proves text-only rendering | AC-05 |
| S-4 | SSRF: adapter fetches server-side only; private-range egress blocked; redirect cap 3; timeout 10 s; response cap 1MB; no token input exists anywhere (grep) | AC-08; private-range + redirect-chain probes |
| S-5 | Abuse economics: Turnstile on import/adapter endpoints; shared 20/day quota; single-flight per account; parse timeouts; burst throttles | AC-07 (throttle half); burst drill |
| S-6 | Audit: one append-only row per run (actor, action, target, timestamp; never content), 24-month retention (baseline shape) | AC-11; audit reconstruction |
| S-7 | No staging state: no `CREATE TABLE` in this feature; the only retry state is the ephemeral Redis manifest (TTL 24 h) + single-flight lock; Redis down -> fail closed | AC-10; grep + fail-closed drill |
| S-8 | Scope guard: the export query reaches only resume rows + resume-type blocks; payload inspection shows zero non-resume data | AC-07; structural review + payload inspection |
| S-9 | Secrets: environment/vault only; `.env` and compose overrides uncommitted; no secrets or fetched payloads in logs/bundles; adapter fetch logs status/latency only | Secrets audit on the release candidate |

## Rate envelopes

| Route class | Envelope | On breach |
|---|---|---|
| Export | 60/h/user burst guard; no daily quota (unlimited by design, audited) | 429 `throttled` |
| Import + adapters (shared) | 20/day/user + 10/h burst + Turnstile | 429 `import_quota_exhausted` / `throttled` |
| In-flight | 1 concurrent import per account (single-flight) | 429 `throttled` |
| Internal block read/create | service-level budget | 429 + trace |

## Degrade matrix

| Condition | Behavior |
|---|---|
| `profile-service` block read down | Export 503 retry; nothing partial is emitted (the stream starts after the query succeeds) |
| Block create down (phase 2) | `503 import_unavailable`; phase-1 documents are private; same-key retry completes relink |
| Redis down | Single-flight fails closed (503) - no duplicate-import window; exports unaffected |
| Adapter fetch slow/down | Adapter 503; zero partial drafts |
| Queue down | No impact (this feature emits no events); audit writes are synchronous |
| Fixture import fails in CI | Build fails naming the fixture - the change ships with a migration or documented break |

## Threat checklist

IDOR (owner scoping + RLS probe), import bombs (size gate + parse timeout + quota), stored XSS (double sanitize + triple-surface drill), SSRF (private-range block + caps + tokenless), exfiltration monitoring (audit from the first export), staging-table creep (grep gate), scheme abuse (`https:` allowlist), secrets (env/vault only, uncommitted), dependency risk (SBOM at release gate).

## Gates

- G4: envelope versioning + additive DTO governance (TL 03).
- G5: S-1, S-2, S-4, S-7 probes per release.
- G7: drill set AC-01..AC-12 attaches to the tag.
- Greps: zero `CREATE TABLE` in this directory; zero staging tables; no token input; no fetched-payload logging.
