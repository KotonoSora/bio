// Drills for `scripts/check-plan-claims.mjs` (feature 03 T-07, TL 02 verification
// drill 2, AC-02/BR-11): a seeded hard-typed copy fails the build-time gate, a
// tier claim without a registry row fails the extra-claim check, and a registry
// row referenced by no copy site fails the stale-row check. The gate reads
// `process.cwd()` as the repo root, so every case runs against an isolated
// fixture root seeded from the real tree (import the gate with a cache-busting
// query; it is a top-level script, not a module).
import assert from 'node:assert/strict';
import { afterEach, describe, it } from 'node:test';
import { cpSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const SCRIPTS = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(SCRIPTS, '..');
const CATALOG = 'services/billing-service/src/main/kotlin/com/resume/billing/domain/PlanCatalog.kt';
const FIXTURE_PATHS = [
  'plans-claims.json',
  'mfe/plans/src/lib/claims.ts',
  'mfe/plans/src/PricingPage.tsx',
  CATALOG,
];

const originalCwd = process.cwd();
const roots = [];

afterEach(() => {
  process.chdir(originalCwd);
  process.exitCode = 0;
  while (roots.length > 0) rmSync(roots.pop(), { recursive: true, force: true });
});

/** Copies the gate's inputs into a temp repo root. */
function fixtureRoot() {
  const root = mkdtempSync(join(tmpdir(), 'plan-claims-drill-'));
  roots.push(root);
  for (const rel of FIXTURE_PATHS) {
    const target = join(root, rel);
    mkdirSync(dirname(target), { recursive: true });
    cpSync(join(ROOT, rel), target);
  }
  return root;
}

function edit(root, rel, mutate) {
  const file = join(root, rel);
  writeFileSync(file, mutate(readFileSync(file, 'utf8')));
}

/** Runs the gate against a fixture root, quiet; returns its exit code. */
async function runDrill(root) {
  process.chdir(root);
  const log = console.log;
  const error = console.error;
  console.log = () => {};
  console.error = () => {};
  try {
    await import(`./check-plan-claims.mjs?drill=${Math.random()}`);
    return process.exitCode ?? 0;
  } finally {
    console.log = log;
    console.error = error;
    process.exitCode = 0;
  }
}

describe('pricing copy drift gate (AC-02, BR-11)', () => {
  it('the untouched vertical passes', async () => {
    assert.equal(await runDrill(fixtureRoot()), 0);
  });

  it('a seeded hard-typed saving percentage fails the build', async () => {
    const root = fixtureRoot();
    edit(root, 'mfe/plans/src/PricingPage.tsx', (text) => `${text}\nconst seededStale = 'Save 44% with yearly billing';\n`);
    assert.equal(await runDrill(root), 1);
  });

  it('a seeded hard-typed price line fails the build', async () => {
    for (const staged of ['59000 per month', '59,000 VND per month', '708000 per year']) {
      const root = fixtureRoot();
      edit(root, 'mfe/plans/src/PricingPage.tsx', (text) => `${text}\nconst seededPrice = '${staged}';\n`);
      assert.equal(await runDrill(root), 1, `seeded price "${staged}" must fail the drill`);
    }
  });

  it('a seeded hard-typed trial day count fails the build', async () => {
    const root = fixtureRoot();
    edit(root, 'mfe/plans/src/PricingPage.tsx', (text) => `${text}\nconst seededTrial = 'Try Pro free for 7 days';\n`);
    assert.equal(await runDrill(root), 1);
  });

  it('a catalog tier claim without a registry row fails the extra-claim check', async () => {
    const root = fixtureRoot();
    edit(root, 'plans-claims.json', (text) => {
      const rows = JSON.parse(text);
      return JSON.stringify(rows.filter((row) => row.claimId !== 'tips_enabled'), null, 2);
    });
    assert.equal(await runDrill(root), 1);
  });

  it('a registry row referenced by no copy site fails the stale-row check', async () => {
    const root = fixtureRoot();
    edit(root, 'plans-claims.json', (text) => {
      const rows = JSON.parse(text);
      rows.push({
        claimId: 'ghost_claim',
        textPattern: 'Ghost sentence',
        enforcement: 'docs/02-architecture-techlead/03-tier-presentation/01-architecture-and-tech-stack.md',
        key: 'plansResponse.tiers',
      });
      return JSON.stringify(rows, null, 2);
    });
    assert.equal(await runDrill(root), 1);
  });

  it('a claim row without an enforcing spec path fails', async () => {
    const root = fixtureRoot();
    edit(root, 'plans-claims.json', (text) => {
      const rows = JSON.parse(text);
      rows[0].enforcement = 'not-a-spec';
      return JSON.stringify(rows, null, 2);
    });
    assert.equal(await runDrill(root), 1);
  });

  it('a copy source that stops rendering through the registry fails', async () => {
    const root = fixtureRoot();
    edit(root, 'mfe/plans/src/PricingPage.tsx', (text) => text.replaceAll('row.textPattern', 'row.claimId'));
    assert.equal(await runDrill(root), 1);
  });
});
