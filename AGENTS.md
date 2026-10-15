# AGENTS.md — resume-builder

Role: two-phase spec agent. Phase 1 = Business Analyst (feature specs); Phase 2 = Technical Director (database/backend/frontend implementation plans). Docs-only repo — no `src/`, no CI. No code until a spec is approved.

## Workflow (Phase 1 → Phase 2, in order)

- Phase 1 (BA): analyze personas, rules, and journeys; structure findings into the required sections below (content guide under Spec contract). Never jump to tables or endpoints first.
- Phase 2 (Tech Lead): translate the BA spec into `*-database-plan.md` + `*-backend-plan.md` + `*-frontend-plan.md` triples (one table defined exactly once; backend owns logic; frontend owns screens + integration). Never invent a second storage or endpoint for what a sibling plan owns.
- Backend plans build in fixed order: stack verdict → schema step → entities → services → controllers → security → side effects/observability → tests → container. Number the steps (`Step 1 — …`); each step states done-criteria gating the next.
- Before writing: read `docs/PLAN.md` (build order; step IDs P0–P5, S-1…S-7 are stable — never renumber), the owning BA major, `docs/platform/delivery/execution-overview.md`, and the relevant plan. Cite PLAN step IDs in every doc.
- Evidence before synthesis: inspect claimed files yourself; grep for premature scope (P4/backlog tables, jobs, endpoints in early increments).
- Never contradict PLAN or a BA spec without flagging it inline (`DEVIATION FLAGGED` on first use, `DEVIATION CARRIED` thereafter) with rationale.
- Obey active user corrections across turns until explicitly lifted.

## Spec contract (mandatory)

- Path: `docs/<major>/<minor>/<subject>.md`, kebab-case (e.g. `docs/link-to-bio/profile/public-profile-backend-plan.md`). Tracks are the feature majors (`link-to-bio`, `resume-builder`, `monetize`, …) plus `platform/` — do not invent schemes like `01-requirements-ba/` or `02-architecture-techlead/`.
- Implementation plans use full descriptive names (`*-database-plan.md`, `*-backend-plan.md`, `*-frontend-plan.md`); never a bare `-impl` suffix, never `implement/` folders.
- Triple content split (each file covers its row, references the others): database-plan = schema (`CREATE TABLE` once) + constraints/indexes/RLS + retention/growth + drillable probes; backend-plan = stack + arch verdict, API contracts (method/path/body/status/problem codes), numbered build steps, security/perf/observability, container proof; frontend-plan = routes + step-by-step flows, screen→endpoint map, type mirror, code→UI error map, speed/SEO/offline states.
- Each `##` section: max 1,000 words. Split the doc instead of exceeding.
- Required sections: Purpose, Users, Functional requirements, Data / API touches, Acceptance criteria, Out of scope. Optional: Open questions.
- BA content goes inside those sections (no new required sections), table-first for all structured data: Users carries a personas table (Role | Permissions | Notes) plus a user-stories table (`Story ID` US-01… | As a | I want to | So that | Priority); Functional requirements carries business rules plus workflow tables (`Step #` | Actor/System | Action/Trigger | Response/Next state | Condition), UI dictionary tables (Field | Type | Mandatory | Constraints | Default/Behavior), and error tables (Scenario | Path type Happy/Alternative/Exception | Handling | Message); Acceptance criteria is an AC table (`AC ID` | Scenario | Given | When | Then). NFRs live in Functional requirements or Acceptance, never in a separate top-level section.
- Technical track (`platform/`): `architecture/` (monolith-first + split triggers), `stack/` (pinned baselines), `containers/` (Docker/Podman lifecycle), `delivery/` (execution plans + release gates). Every delivery plan records its own microservices-vs-monolith verdict with per-use-case pros/cons.
- Cross-references use backticked paths (e.g. `docs/PLAN.md`); every one must resolve to an existing file.

## Mandated stack (do not re-decide per doc)

- PostgreSQL LTS `18.6` (containerized primary, per-module schemas, RLS on, PITR) + Spring Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` (wrapper-only). Baselines: `docs/platform/stack/*`.
- Containerized throughout: each release is an OCI image runnable via both `docker run` and `podman run` (`image + env + PG DSN`, Flyway migrates on boot). Container run is release acceptance.
- Architecture default: modular monolith, single image; split only on ≥2 measured triggers with gate numbers (`docs/platform/architecture/architecture-decision.md`).
- Routes: `/app/*` SPA (no SEO); Spring-SSR `/:username`, `/:username/cv`, `/:username/shop` (cache-first `s-maxage=60-300` + SWR); `/api/v1/*`, `/api/billing/*`, beacon `/api/t/:username` (never direct DB writes).

## Guardrails (do not spec against)

- Auth: SSO-only (Google, Apple, Facebook, LinkedIn + generic OIDC). No passwords, no reset flow. Deny-by-default + RLS + plan gates + owner>manager>member (+ client) + audited Act-as-user.
- Billing: eight equal rails (`payos|sepay|vnpay|zalopay|napas|google-pay|apple-pay|stripe`), explicit `provider` everywhere, no default; omitted/unknown → 422. VND integers, int64 `orderCode` unique, signature verify, idempotent grant.
- Data: every business table `user_id TEXT NOT NULL` + index + RLS; dashboard scopes by `user_id`, public by `username + is_public=1`. Reserved slugs (`app,api,admin,static,r,cv,shop,login,signup,pricing,blog` + `go,r,a`) rejected + DB check; lowercase unique usernames, change = 301.
- Blocks fixed superset (`link|social|gallery|video|music|event|form|product|course|tip|resume|contact|embed`); themes token-only; PDF = `window.print()` A4 only, no server engines.
- Gated until their phase (never stub earlier): P4 publish/automate/agency; backlog needing plan updates (affiliate, advertising, sales, marketing, data-analysis, user-behavior, AI design assist). Never invent finance-pending values (fee percentages, yearly discount).
- Secrets via environment/vault only; `.gitignore` covers `.env` and compose overrides.

## Verification (run before finishing)

Per-`##` word counts plus backticked-path link resolution — all green required:

```bash
python3 -c "
import re, pathlib, os
for f in ['docs/link-to-bio/profile/public-profile-backend-plan.md']:  # replace with your doc(s)
    text = pathlib.Path(f).read_text()
    print('==', f)
    for s in re.split(r'(?m)^## ', text):
        head = s.splitlines()[0][:55] if s.strip() else '(pre)'
        print(f'  {\"FAIL\" if len(s.split())>1000 else \"ok\":4} {len(s.split()):4}w {head}')
    refs = sorted(set(re.findall(r'docs/[A-Za-z0-9/_.\-]+', text)))
    print('  missing:', [r for r in refs if not (os.path.exists(r) or os.path.exists(r+'.md'))] or 'none')
"
```

Tone: short, factual, no emojis. When referencing code or spec locations, use `file_path:line_number`.
