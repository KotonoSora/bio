# e2e — Playwright suite for the compose stack

Browser-level end-to-end tests against the running platform, through the edge
(`http://localhost`), the same entry users hit — mirrors `deploy/smoke.sh`.
Tests never boot the stack; start it with `docker compose up -d` first.

## Layout

| Path | Purpose |
|---|---|
| `tests/01-account-and-access.spec.ts` | Default suite (20 cases): login entry, callback fail-closed, session/CSRF guards, recovery, routing, edge headers. Runs on the standing dev stack. |
| `tests/01-account-and-access.full-login.spec.ts` | Opt-in suite (4 cases): real SSO dance against the mock IdP, identity stability, open-redirect clamp, logout. |
| `tests/01-account-and-access.authenticated.spec.ts` | Opt-in suite (15 cases): one SSO dance per run seeds a session; every case then tests post-login surfaces with zero login steps (owner reads, sessions/revoke scoping, contact email, link/unlink guards, step-up, sign-out). |
| `mock-idp/server.mjs` | Zero-dependency mock OIDC IdP (authorize consent page, token exchange, RS256 id_token, JWKS). |
| `scripts/enable-full-login.sh` / `disable-full-login.sh` | Toggle `.env` between the mock and placeholder IdP; enable also ensures the dev internal-JWT keypair, disable blanks it again. Recreates gateway + affected services. |
| `01-account-and-access.test-cases.md` | The full case catalog (AC-01..AC-15 mapping, integration-owned list, runbook). |

## Run

```sh
cd e2e && npm install        # once
npm test                     # default suite, headless
npm run test:headed          # default suite, visible browser (400 ms slow-mo)
npm run report               # html report; headed runs record video to test-results/
```

Target another deployment with `EDGE_URL=http://host` (default `http://localhost`).

## Full-login suite (opt-in)

The dev stack ships with a placeholder IdP (`idp.invalid`) so sign-in cannot
complete. The mock IdP unlocks the login half of the feature:

```sh
cd e2e
npm run idp:on               # .env: GENERIC_OIDC_* -> localhost:9099 + restart account-service
npm run test:headed:full     # playwright auto-starts the mock (webServer); watch the login dance
npm run idp:off              # restore the placeholder IdP afterwards
```

`idp:on` also ensures a **dev-only internal-JWT keypair** in `.env` (generated
once with openssl; `idp:off` blanks it again). The gateway signs the internal
identity with it and account-service verifies it. Without the keypair the
gateway mints no identity JWT and account-service runs its `dev-user` fallback —
no real session can authorize `/api/v1/**`. With the keypair configured the mesh
verifiers leave their dev branch, so `idp:on` also mirrors it into
`MESH_JWT_PRIVATE_KEY_PEM`: account-service (and profile-service) sign
service-to-service `/internal/v1/*` calls with the same dev key, and
`idp:off` blanks that too.

How it works: `idp:on` sets `GENERIC_OIDC_AUTHORIZE_URI=http://localhost:9099/authorize`
(browser leg) and token/JWKS URLs via `host.docker.internal` (account-service leg).
Tests set the next identity with `POST /__control` and drive the consent page in
the browser. The mock speaks http and the edge CSP only allows https form
redirects, so full-login tests start from the server-issued authorize URL — the
real form-POST skip is covered by the default suite via request interception.

If the suite skips: the mock is not running or account-service is not pointed at
it — run `npm run idp:on`, then rerun.

## Seeded-session suite (skip login)

`tests/01-account-and-access.authenticated.spec.ts` covers post-login surfaces
without repeating the login dance per case: `beforeAll` completes the real SSO
dance in throwaway browser contexts — a primary session, a second session for
the same identity, and a foreign identity's session — then saves the primary
one via Playwright `storageState` to `.auth/authenticated.json` (gitignored).
All 15 cases run against those seeded sessions with zero login steps.

```sh
cd e2e
npm run idp:on               # once (mock IdP + dev JWT keypair)
npm run test:auth            # headless
npm run test:headed:auth     # visible browser
npm run idp:off              # restore the placeholder IdP afterwards
```

Budget: 9 auth ops per run (4 sign-ins + 4 callbacks + 1 step-up callback),
well inside the 20/min envelope; the rest is plain HTTP/UI on the seeded
sessions. Each run uses a fresh identity, so session lists, contact-email caps,
and resend gates stay deterministic (the mock consent page is approved once per
session — the browser visibly visits it in headed runs). Without `idp:on` the
suite skips; without `E2E_FULL_LOGIN=1` (default runs) it never starts.

## Account deletion suite (feature 02)

`tests/02-account-deletion.spec.ts` runs in the default set (no IdP): anonymous
owner guards (`401` before anything commits) and the no-shortcut probe (internal
purge/status/probe routes stay `404` at the edge).

`tests/02-account-deletion.authenticated.spec.ts` is opt-in and dances fresh
identities per case — A: UI gates + consequences copy, B: API guards +
grace/restore, C: full UI journey (request -> signed-out panel -> re-login ->
restore), D: username confirmation + held name during grace:

```sh
cd e2e
npm run idp:on               # once (mock IdP + dev JWT keypair)
npm run test:deletion        # headless
npm run test:headed:deletion # visible browser
npm run idp:off              # restore the placeholder IdP afterwards
```

Budget: 6 dances = 12 auth ops. The suite is ordered and runs `workers=1`
because cases share identity B/D state by design; run it as its own command, a
minute apart from `test:full`/`test:auth`. Without `idp:on` the suite skips;
without `E2E_FULL_LOGIN=1` (default runs) it never starts.

## Limits and troubleshooting

- Auth endpoints allow 20 ops/min per IP (`429 throttled`). A default run is
  ~11 ops and a full-login run is ~9 — together they fill the window, so run
  them a minute apart. The seeded-session suite adds its own 9: `test:full`
  intentionally excludes it; run `npm run test:auth` as its own command
  (repeat runs are fine — 9 ops each).
- Contact-email caps are per user (5/hour, resend gate 60 s). The seeded suite
  uses a fresh identity per run, so its 5 contact-email calls stay under the
  cap; don't add a sixth call to that suite without raising the budget.
- `EDGE_URL`/port overrides change `redirect_uri`; full-login tests expect the
  default `http://localhost` wiring.
- Failures keep artifacts under `test-results/` (screenshot, video, trace —
  `npx playwright show-trace <zip>`).
