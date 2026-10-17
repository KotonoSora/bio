# Platform Baseline - Web Micro Frontends

Phase 2 TL · Cross-cutting baseline. Defines the micro frontend ("MFE") composition of the dashboard under `/app/*`. Extends `docs/02-architecture-techlead/00-platform-baseline/02-stack-and-build-pins.md` (Frontend integration) and refines the serving rules in `09-local-storage-and-nginx-spec.md`. First vertical: feature 01 account settings (`docs/01-requirements-ba/01-account-and-access/`).

## Scope and boundaries

- Applies to the authenticated dashboard zone (`/app/*`) only; public pages stay server-rendered by `public-site-service` - never by the SPA.
- Adoption rationale: vertical isolation + independent versioning now, per-vertical deployment later; overhead stays contained because composition is same-origin, artifacts ship inside the edge image, and no extra pipelines run today.
- Shell (`web/`): session entry (login, recovery, auth error), navigation, routing, design tokens, MFE loading + fallback. Owns no product vertical once extracted.
- Verticals (`mfe/<name>/`): one npm workspace per product area, built independently to `entry.js` + `style.css`, loaded by the shell at runtime. Extraction happens only when the feature ships - never stubs.

## Principles (mapped)

| Principle | Application |
|---|---|
| Technology-agnostic composition | Contract is DOM-level: a custom element (`rb-<name>-app`) + assigned properties + CustomEvents. The shell never imports MFE internals. |
| Isolate team code | Verticals never import each other and never share a runtime instance; React is bundled per MFE on purpose. |
| Team prefixes | Assets under `/app/mfe/<name>/`; events prefixed `rb:`; context types namespaced in `packages/mfe-kit`. |
| Native browser features | Dynamic `import()` + Custom Elements; no composition runtime, no import maps (edge CSP `script-src 'self'` blocks inline maps; same-origin dynamic import is allowed). |
| Resilient site | Skeleton + 10 000 ms timeout + error fallback with retry per mount; one broken vertical never takes down the shell. |

## The contract (`packages/mfe-kit`)

- `defineMfe(tag, mount)` registers the custom element; mount runs once connected AND `mfeContext` is assigned; dispose runs on disconnect.
- `MfeContext` (additive-only): `locale: 'en' | 'vi'`, `navigate(to)` (shell-router relative, e.g. `/login?notice=expired`). Never repurpose or remove a field.
- Failure signal: `MFE_ERROR_EVENT` (`rb:mfe-error`) dispatched on the element; the shell swaps to its fallback.
- Shared API core (build-time source, per-bundle copy): `ApiError`, `api()`, `csrfToken()`, `toApiError()`, `isUnauthenticated()`, `isRelayEmail()`.

## Build and discovery

- Root npm workspaces: `web` (shell), `mfe/*` (verticals), `packages/*` (shared source). One root lockfile; Docker/CI run `npm ci` once.
- `npm run build:mfes` (`scripts/build-mfes.mjs`) builds each vertical, collects it to `web/public/mfe/<name>/<version>/`, and writes `mfe.manifest.json`.
- Lib-mode builds pin `process.env.NODE_ENV` to `production` (Vite lib mode preserves it; the shell loads the module unbundled).
- Manifest (runtime contract): `{ "<name>": { tag, version, entry, css } }`; paths are SPA-base relative. The version directory comes from the vertical's `package.json` - bumping it is the deploy unit.

## Runtime composition (shell)

- Integration approach: client-side composition at runtime (custom elements + dynamic `import()`); server-side/edge-side HTML stitching is not used because dashboard routes are session-scoped and never cached as pre-assembled HTML.
- `web/src/mfe/MfeMount.tsx`: fetch manifest (page-lifetime cache) -> inject stylesheet as `<link>` (CSP `style-src 'self'` forbids runtime `<style>`) -> `import()` the entry -> create the element and assign context.
- Budget: manifest + stylesheet + entry inside 10 000 ms, else fallback + retry; runtime vertical errors switch to the same fallback.
- The service worker treats `mfe.manifest.json` as network-only; versioned MFE assets are immutable and cache-safe.

## Serving and deploy

- Edge serves `/app/mfe/` immutable (1 year) with the standard security headers; `mfe.manifest.json` stays under the no-cache `/app/` rule (`security-headers.conf`, `locations.conf`).
- Prod build: shell + all verticals compile into the edge image (`web/dist`), one compose build under Docker and Podman (Containerfile/Dockerfile pairs stay in sync). Independence today is build/version/package level; per-vertical containers remain a later option.
- `deploy/smoke.sh` asserts manifest + account entry + CSS are served through the edge.

## Performance budget

- Ceilings (zlib gzip of each emitted artifact): shell JS assets <= 150 KB, shell CSS <= 16 KB, each vertical `entry.js` <= 175 KB, each vertical `style.css` <= 20 KB. Ceilings do not grow to fit features; raising one needs a baseline amendment and a recorded rationale.
- Enforced by `scripts/check-bundle-budget.mjs` as the final step of `npm run build:web`; a breach fails the build and therefore the CI frontend gate.

## Guardrails

- No cross-MFE imports; shared changes go through `packages/*` (additive; consumers rebuild).
- No shared runtime instance (no federation runtime, no shared React between surfaces).
- Inter-vertical communication, when a second vertical needs it, is DOM events in the `rb:` namespace on the composed element or document - never a shared state instance.
- Display copy is owned per surface (shell vs vertical dictionaries, each via `react-i18next`); BA copy stays the source of truth; error `code` strings stay unlocalized.
- A new vertical = new `mfe/<name>/` workspace + manifest entry (automatic) + one smoke assertion - never a rewrite of the shell.
- Gated backlog (48-53) and assist (16) never get MFE stubs ahead of their amendments.

## Dev workflow

- Composed build: `npm run build:web` (MFEs + shell), then `npm run dev -w web` (Vite at `/app/`, API proxy to the gateway).
- Vertical-only iteration: `npm run dev -w mfe/account` mounts the MFE standalone with a stub context (HMR).
- Gates: `npm test --workspaces --if-present`; `npx tsc --noEmit` inside each workspace; `npm run build:web` before release.
- Contract tests (happy-dom): `defineMfe` lifecycle (connected + `mfeContext` -> mount, disconnect -> dispose) and shell loader failure paths (manifest fetch error, entry import error, 10 s budget expiry, `rb:mfe-error`, retry recovery) in `web/src/mfe/MfeMount.test.tsx` and `packages/mfe-kit/src/define-mfe.test.ts`; they run in the `npm test --workspaces` gate.
