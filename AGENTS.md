# AGENTS.md — resume-builder

Role: two-phase spec agent executing the Master System Prompt. Phase 1 = Business Analyst (`docs/01-requirements-ba/`); Phase 2 = Tech Lead (`docs/02-architecture-techlead/`). Docs-only repo — root holds `.git/`, `docs/`, `AGENTS.md`, `MIGRATION.md`; no `src/`, no CI. No code until a spec is approved.

## Migration status (in force)

- The numbered tree is the only write target. The legacy tree (`docs/<major>/…`) is frozen: never add or edit files there; each feature's legacy files are deleted after its migration is validated (git history is the archive). Batch order + per-feature status: `MIGRATION.md`.
- Paths: `docs/01-requirements-ba/<nn-feature>/` and `docs/02-architecture-techlead/<nn-feature>/`, kebab-case, 2-digit prefixes. Feature numbers are shared between phases; never renumber. Platform baseline: `docs/02-architecture-techlead/00-platform-baseline/`.
- Hard cap: every `.md` file stays under 1,000 words. Split by topic first; when one topic overflows, split into `-part-1`, `-part-2`, … and continue the directory's sequential file numbers (e.g. `03-api-contracts-and-payloads-part-1.md`, `04-api-contracts-and-payloads-part-2.md`, `05-…`, `06-security-logging-and-rate-limiting.md`).

## Phase 1 — BA file set (per feature)

`01-document-context-and-scope` (objectives, in/out scope, personas + RBAC table `Role | CRUD Permissions | Description`) · `02-user-stories-and-business-rules` (story table `Story ID | As a | I want to | So that | Priority`, numbered `BR-xx` rules) · `03-workflow-and-user-journey` (`Step # | Actor/System | Trigger Action | System Response | Rules`) · `04-ui-data-dictionary` (`Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior`) · `05-exception-handling-and-errors` (`Scenario | Path Type | System Handling | Displayed Error Message`) · `06-acceptance-criteria` (BDD table `AC ID | Scenario | Given | When | Then`) · `07-non-functional-requirements` (quantified latency, SLA, masking, session, quotas, localization).

Table-first for all structured content. No vague terms — every threshold is a number with a unit. `US-`/`BR-`/`AC-` IDs are stable; never renumber.

## Phase 2 — TL file set + mandated stack (no re-decisions)

`01-architecture-and-tech-stack` (service boundaries, compose slice, `build.gradle.kts`, React integration) · `02-database-schema-design` (PostgreSQL DDL + Flyway + indexes + RLS; database-per-service) · api contracts file(s) (endpoints, Kotlin data-class DTOs, status codes, RFC 9457 error JSON; part-split as needed) · `0X-local-file-storage-and-nginx-spec` (only where the feature owns storage artifacts; see the storage amendment in `MIGRATION.md`) · implementation plan (`Task ID | Subsystem (FE/BE/DevOps) | Step Description | Dependency | Acceptance Goal`) · security file last (`Spring Security OAuth2/OIDC + internal JWT`, tracing, gateway rate rules, degrade matrix). File numbers shift with part splits (example above).

Stack: microservices · Spring Boot `4.1.1` · Kotlin `2.4.21` · Gradle `9.8.1` (Kotlin DSL, wrapper-only) · PostgreSQL `18.6` per service (Flyway on boot, RLS on) · React.js `19.3` · Docker Compose (Podman parity kept as release acceptance) · Redis for cache/queue · local-disk storage only: all files/media live on VPS disk via Docker bind mounts/named volumes and are served by Nginx (no AWS/S3/MinIO or any object-store tool). Edge topology: Nginx serves the SPA + media and fronts the gateway; gateway terminates sessions and signs internal identity; each service owns its database — no cross-service table access, only APIs/events.

## Feature register

Shared numbering (build order; names + sources + status in `MIGRATION.md`): 01–05 account · 06–16 link-to-bio · 17–21 resume · 22–26 shop · 27–28 leads · 29–31 url-shortener · 32–40 monetize · 41–42 publish · 43–44 automate · 45–47 agency · 48–53 gated backlog (marketing, affiliate, advertising, sales, data-analysis, user-behavior).

## Guardrails (carry over; do not spec against)

- Auth: SSO-only (Google, Apple, Facebook, LinkedIn + generic OIDC). No passwords, no reset flow. Deny-by-default + service-side scoping + per-DB RLS + audited ops actions.
- Money: eight equal rails (`payos|sepay|vnpay|zalopay|napas|google-pay|apple-pay|stripe`), explicit `provider`, no default; VND integers; idempotent grants. Never invent finance-pending values (fee percentages, yearly discount).
- Data: every business table has `user_id TEXT NOT NULL` + index + RLS; dashboard reads scope by server-set `user_id`, public reads by `username + is_public=1`. Reserved slugs (`app,api,admin,static,r,cv,shop,login,signup,pricing,blog,go,r,a`) rejected by the profile service.
- Storage: no object-store tool exists (no AWS/S3/MinIO/CDN); files live under `./data/uploads/<service>/…` on Docker volumes and the Nginx edge serves them — canonical rules in `docs/02-architecture-techlead/00-platform-baseline/09-local-storage-and-nginx-spec.md`.
- Content: blocks fixed superset; themes token-only; PDF = `window.print()` A4 only.
- Gated until shipped: 48–53 backlog; AI design assist (feature 16). Never stub gated scope into earlier features.
- Secrets via environment/vault only; never commit `.env` or compose overrides.
- Contradicting an approved spec requires inline `DEVIATION FLAGGED` on first use, `DEVIATION CARRIED` after, with rationale.

## Verification (run on every file you touch)

Word count (per file, <1,000) + backticked-path resolution — all green required (whole numbered tree resolves; `00-platform-baseline` batch landed):

```bash
python3 - docs/a.md docs/b.md <<'EOF'
import re, sys, os
for f in sys.argv[1:]:
    text = open(f).read()
    n = len(text.split())
    refs = sorted(set(re.findall(r'docs/[A-Za-z0-9/_.\-]+', text)))
    missing = [r for r in refs if not (os.path.exists(r) or os.path.exists(r + '.md'))]
    print(('FAIL' if n > 1000 else 'ok').ljust(4), f'{n:4}w', f, ('missing: ' + str(missing)) if missing else '')
EOF
```

Tone: short, factual, no emojis. TL files cite their BA sources; BA files cite the TL files that implement them.
