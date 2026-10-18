// T-08 / NFR-08 / G7 drills: the release archive exports the claim registry and
// the rendered pricing copy per tag, and reports the diff against the previous
// archived tag. Rendering follows the frozen contracts only - config values
// substitute into the registry patterns, absence stays absent (BR-03, BR-11),
// and the yearly amount is the server's exact VND math mirrored at archive time
// (BR-04, NFR-04) - never a hand-typed literal.
import assert from 'node:assert/strict';
import { afterEach, describe, it } from 'node:test';
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { diffSnapshots, parseArgs, renderSnapshot } from './plan-claims-release.mjs';

const SCRIPTS = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(SCRIPTS, '..');
const CLI = join(SCRIPTS, 'plan-claims-release.mjs');

const registry = JSON.parse(readFileSync(join(ROOT, 'plans-claims.json'), 'utf8'));
const byId = (snapshot, claimId) => snapshot.claims.find((c) => c.claimId === claimId);

/** The SPA's display format (BR-12): the archive renders what users read. */
const formatVnd = (value) =>
  new Intl.NumberFormat('en', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(value);

const roots = [];
afterEach(() => {
  while (roots.length > 0) rmSync(roots.pop(), { recursive: true, force: true });
});
const tmp = () => {
  const dir = mkdtempSync(join(tmpdir(), 'plan-claims-release-'));
  roots.push(dir);
  return dir;
};

describe('rendered-copy snapshot (NFR-08)', () => {
  it('substitutes the config values into the registry patterns', () => {
    const snapshot = renderSnapshot(registry, { monthlyVnd: 59_000, yearlyDiscountPct: 25, trialDays: 7 }, 'test-tag');
    assert.equal(byId(snapshot, 'yearly_saving_badge').rendered, 'Save 25% with yearly billing');
    assert.equal(byId(snapshot, 'trial_cta').rendered, 'Try Pro free for 7 days');
    assert.equal(byId(snapshot, 'monthly_price').rendered, `${formatVnd(59_000)} per month`);
    // Yearly = 12x monthly minus the discount, half-up at the dong (BR-04).
    assert.equal(byId(snapshot, 'yearly_price').rendered, `${formatVnd(531_000)} per year`);
    assert.equal(snapshot.config.yearlyVnd, 531_000);
  });

  it('leaves no unsubstituted token in any rendered claim', () => {
    const snapshot = renderSnapshot(registry, { monthlyVnd: 59_000, yearlyDiscountPct: 25, trialDays: 7 }, 'test-tag');
    for (const claim of snapshot.claims) {
      if (claim.rendered != null) assert.ok(!/\{\w+\}/.test(claim.rendered), `${claim.claimId} renders fully`);
    }
  });

  it('renders absence when config is unset - never a placeholder or default (BR-03, AC-09)', () => {
    const snapshot = renderSnapshot(registry, {}, 'test-tag');
    for (const claimId of ['yearly_saving_badge', 'trial_cta', 'monthly_price', 'yearly_price']) {
      assert.equal(byId(snapshot, claimId).rendered, null, `${claimId} is omitted`);
      assert.match(byId(snapshot, claimId).reason, /unset/);
    }
    assert.equal(byId(snapshot, 'pricing_heading').rendered, 'Simple pricing');
    assert.equal(snapshot.config.yearlyVnd, null);
  });

  it('omits the badge when only the price is configured (absence is per key)', () => {
    const snapshot = renderSnapshot(registry, { monthlyVnd: 59_000 }, 'test-tag');
    assert.equal(byId(snapshot, 'monthly_price').rendered, `${formatVnd(59_000)} per month`);
    assert.equal(byId(snapshot, 'yearly_price').rendered, `${formatVnd(708_000)} per year`);
    assert.equal(byId(snapshot, 'yearly_saving_badge').rendered, null);
    assert.equal(byId(snapshot, 'trial_cta').rendered, null);
  });

  it('refuses a discount above the 44% ceiling (NFR-05, S-3)', () => {
    assert.throws(() => renderSnapshot(registry, { monthlyVnd: 59_000, yearlyDiscountPct: 45 }, 'test-tag'), /44/);
  });
});

describe('diff vs previous tag (NFR-08, G7)', () => {
  const base = renderSnapshot(registry, { monthlyVnd: 59_000, yearlyDiscountPct: 25, trialDays: 7 }, 'prev');

  it('reports added, removed, and changed claims', () => {
    const current = {
      claims: [
        ...base.claims.filter((c) => c.claimId !== 'trial_cta'),
        { claimId: 'new_claim', rendered: 'New sentence' },
        { claimId: 'yearly_saving_badge', rendered: 'Save 20% with yearly billing' },
      ],
    };
    const diff = diffSnapshots(base, current);
    assert.deepEqual(diff.removed, ['trial_cta']);
    assert.deepEqual(diff.added, ['new_claim']);
    assert.deepEqual(diff.changed, [
      { claimId: 'yearly_saving_badge', from: 'Save 25% with yearly billing', to: 'Save 20% with yearly billing' },
    ]);
  });

  it('reports an absence flip as a change', () => {
    const absent = renderSnapshot(registry, {}, 'prev');
    const diff = diffSnapshots(absent, base);
    assert.ok(diff.changed.some((c) => c.claimId === 'trial_cta' && c.from === null));
  });

  it('is empty when nothing moved', () => {
    const diff = diffSnapshots(base, base);
    assert.deepEqual(diff, { added: [], removed: [], changed: [] });
  });
});

describe('release archive CLI (T-08, G7)', () => {
  const run = (args, options = {}) =>
    execFileSync(process.execPath, [CLI, ...args], { cwd: ROOT, encoding: 'utf8', ...options });

  it('archives the registry, the rendered snapshot, and the diff under <out>/<tag>/', () => {
    const out = tmp();
    const output = run([
      '--tag', '0.1.0-test',
      '--out', out,
      '--monthly-vnd', '59000',
      '--yearly-discount-pct', '25',
      '--trial-days', '7',
    ]);
    assert.match(output, /archived/);

    const dir = join(out, '0.1.0-test');
    for (const file of ['registry.json', 'rendered.json', 'diff.json']) {
      assert.ok(existsSync(join(dir, file)), `${file} archived`);
    }
    assert.deepEqual(JSON.parse(readFileSync(join(dir, 'registry.json'), 'utf8')), registry);
    const rendered = JSON.parse(readFileSync(join(dir, 'rendered.json'), 'utf8'));
    assert.equal(rendered.tag, '0.1.0-test');
    assert.equal(byId(rendered, 'yearly_saving_badge').rendered, 'Save 25% with yearly billing');
    assert.equal(byId(rendered, 'monthly_price').rendered, `${formatVnd(59_000)} per month`);
    const diff = JSON.parse(readFileSync(join(dir, 'diff.json'), 'utf8'));
    assert.equal(diff.previous, null);
    assert.deepEqual(diff.changed, []);
  });

  it('diffs the new tag against the previous archived tag', () => {
    const out = tmp();
    const common = ['--out', out, '--monthly-vnd', '59000', '--yearly-discount-pct', '25', '--trial-days', '7'];
    run(['--tag', '0.1.0-a', ...common]);

    // The registry moved (S-8: copy and registry move in one change); the next
    // tag must report the rendered-copy movement against 0.1.0-a.
    const movedRegistry = join(out, 'moved-plans-claims.json');
    writeFileSync(
      movedRegistry,
      JSON.stringify(
        registry.map((row) =>
          row.claimId === 'yearly_saving_badge'
            ? { ...row, textPattern: 'Save {yearlyDiscountPct}% when billed yearly' }
            : row,
        ),
        null,
        2,
      ),
    );
    run(['--tag', '0.1.0-b', '--registry', movedRegistry, ...common]);

    const diff = JSON.parse(readFileSync(join(out, '0.1.0-b', 'diff.json'), 'utf8'));
    assert.equal(diff.previous, '0.1.0-a');
    assert.deepEqual(diff.changed, [
      {
        claimId: 'yearly_saving_badge',
        from: 'Save 25% with yearly billing',
        to: 'Save 25% when billed yearly',
      },
    ]);
  });

  it('archives an absence snapshot when config is unset (BR-03)', () => {
    const out = tmp();
    run(['--tag', '0.1.0-empty', '--out', out, '--config-file', join(out, 'missing.env')]);
    const rendered = JSON.parse(readFileSync(join(out, '0.1.0-empty', 'rendered.json'), 'utf8'));
    assert.equal(byId(rendered, 'yearly_saving_badge').rendered, null);
    assert.equal(byId(rendered, 'monthly_price').rendered, null);
    assert.equal(rendered.config.monthlyVnd, null);
  });

  it('reads config from an env file when no flag is passed', () => {
    const out = tmp();
    const envFile = join(out, '.env');
    writeFileSync(
      envFile,
      ['# comment', 'BILLING_CATALOG_MONTHLY_VND=123000', 'BILLING_CATALOG_YEARLY_DISCOUNT_PCT=10', 'BILLING_CATALOG_TRIAL_DAYS=14'].join('\n'),
    );
    run(['--tag', '0.1.0-env', '--out', out, '--config-file', envFile]);
    const rendered = JSON.parse(readFileSync(join(out, '0.1.0-env', 'rendered.json'), 'utf8'));
    assert.equal(byId(rendered, 'trial_cta').rendered, 'Try Pro free for 14 days');
    assert.equal(byId(rendered, 'monthly_price').rendered, `${formatVnd(123_000)} per month`);
    assert.equal(rendered.config.yearlyVnd, 1_328_400);
  });

  it('refuses an out-of-range discount instead of archiving it (NFR-05)', () => {
    const out = tmp();
    assert.throws(() =>
      run(['--tag', '0.1.0-bad', '--out', out, '--monthly-vnd', '59000', '--yearly-discount-pct', '45']),
    );
    assert.ok(!existsSync(join(out, '0.1.0-bad')));
  });

  it('parses flags with the config-file default', () => {
    const parsed = parseArgs(['--tag', 't', '--monthly-vnd', '1000']);
    assert.equal(parsed.tag, 't');
    assert.equal(parsed.monthlyVnd, 1000);
    assert.equal(parsed.configFile, '.env');
  });
});
