// Feature 04 - custom domains e2e (opt-in, seeded session): the domains
// owner API and the domains settings surface.
// The pre-monetize state of the rename is structural: every user resolves
// free (the ledger materializes the free floor), so this suite pins the
// reachable halves: the fail-closed free gate, normalization/denylist
// validation, and the free-tier UI surface that shows the upgrade path
// instead of a fake form (AC-09). The pro half (add -> TXT -> verify ->
// serve -> remove) is e2e-owned by the opt-in `04-custom-domains.pro.spec.ts`
// (seeded plan snapshot + mock DoH resolver; `npm run domains:on`).
//
//   cd e2e && npm run idp:on     # mock IdP + dev JWT keypair (once per machine)
//   npm run test:domains         # headless
//   npm run idp:off              # restore the placeholder IdP afterwards
//
// Budget per run: 1 dance = 2 auth ops (limit 20/min/IP) - run it a minute
// apart from the other suites (e2e/README.md).
import {
  expect,
  request as standaloneRequest,
  test,
  type APIRequestContext,
  type Browser,
} from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const MOCK_ORIGIN = 'http://localhost:9099';
const fullLogin = process.env.E2E_FULL_LOGIN === '1';

const RUN = Date.now();
const USER_A = { sub: `e2e-domains-a-${RUN}`, email: `e2e-domains-a-${RUN}@example.test` };
const NAME = 'E2E Domains User';

const E2E_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const AUTH_FILE = path.join(E2E_DIR, '.auth', 'domains.json');
const EMPTY_STATE = { cookies: [], origins: [] };

interface DomainResponse {
  hostname: string;
  displayHost: string;
  status: string;
  txtName: string | null;
  txtValue: string | null;
  challengeExpiresAt: string | null;
  verifiedAt: string | null;
  lastRecheckedAt: string | null;
  serving: boolean;
}

const seed: { ready: boolean; skip?: string; csrf?: string } = { ready: false };

async function setNextUser(request: APIRequestContext, profile: { sub: string; email: string }) {
  const res = await request.post(`${MOCK_ORIGIN}/__control`, {
    data: { ...profile, name: NAME, emailVerified: true },
  });
  expect(res.ok(), 'mock IdP control call').toBe(true);
}

type DanceResult = { ok: true; csrf: string } | { ok: false; wiring: string };

/** Mutations carry the double-submit pair; cookies ride the storage state. */
const csrfHeaders = () => ({ 'X-CSRF-Token': seed.csrf! });

/** One real SSO dance in a throwaway context; persists the storage state. */
async function dance(browser: Browser, baseURL: string, user: { sub: string; email: string }): Promise<DanceResult> {
  const ctx = await browser.newContext({ baseURL, storageState: EMPTY_STATE });
  try {
    const signIn = await ctx.request.post('/api/auth/sign-in/social', {
      form: { provider: 'generic-oidc', callbackURL: '/app/account/domains' },
      maxRedirects: 0,
    });
    expect(signIn.status()).toBe(302);
    const authorizeUrl = signIn.headers()['location'] ?? '';
    if (!authorizeUrl.startsWith(MOCK_ORIGIN)) return { ok: false, wiring: authorizeUrl };

    await setNextUser(ctx.request, user);
    const page = await ctx.newPage();
    await page.goto(authorizeUrl);
    await page.getByRole('button', { name: 'Approve sign-in' }).click();
    // Fresh identities land on the welcome-username step (same as suite 01):
    // the seeded session is valid from there on; tests navigate their surfaces.
    await page.waitForURL(/\/app(\/|$)/);

    const cookies = await ctx.cookies();
    expect(cookies.find((c) => c.name === '__Host-rb_session')?.value, 'session cookie issued').toBeTruthy();
    const csrf = cookies.find((c) => c.name === 'csrf_token')?.value;
    expect(csrf, 'csrf double-submit cookie issued (platform baseline)').toBeTruthy();

    fs.mkdirSync(path.dirname(AUTH_FILE), { recursive: true });
    await ctx.storageState({ path: AUTH_FILE });

    const me = await ctx.request.get('/api/v1/me');
    expect(me.status(), 'fresh session authorizes /api/v1/me').toBe(200);
    return { ok: true, csrf: csrf! };
  } finally {
    await ctx.close().catch(() => {});
  }
}

test.describe('domains owner API and settings surface (seeded session)', () => {
  test.beforeAll(async ({ browser, baseURL }) => {
    if (!fullLogin) {
      seed.skip = 'opt-in suite: run `npm run idp:on` once, then `npm run test:domains` (see e2e/README.md)';
      return;
    }
    const probe = await standaloneRequest.newContext({ storageState: EMPTY_STATE });
    const health = await probe.get(`${MOCK_ORIGIN}/__health`).catch(() => null);
    await probe.dispose();
    if (!health?.ok()) {
      seed.skip = 'mock IdP not reachable on :9099; playwright starts it for E2E_FULL_LOGIN=1 runs';
      return;
    }
    const primary = await dance(browser, baseURL!, USER_A);
    if (!primary.ok) {
      seed.skip = `account-service is not pointed at the mock IdP (got ${primary.wiring}); run \`npm run idp:on\` and retry`;
      return;
    }
    seed.csrf = primary.csrf;
    seed.ready = true;
  });

  test.beforeEach(() => {
    test.skip(!seed.ready, seed.skip ?? 'seeded session unavailable');
  });

  test.use({ storageState: AUTH_FILE });

  test('malformed hostnames are rejected before any gate state can leak (AC-07, BR-02)', async ({ request }) => {
    const cases: Array<[string, string]> = [
      ['*.wildcard.e2e.test', 'wildcard'],
      [`x${'y'.repeat(300)}.e2e.test`, 'over 253 chars'],
      ['.leading-dot.e2e.test', 'empty label'],
      ['host..double-dot.e2e.test', 'empty middle label'],
      ['-dash.fine.e2e.test', 'label starts with a dash'],
    ];
    for (const [hostname, label] of cases) {
      const res = await request.post('/api/v1/domains', {
        data: { hostname },
        maxRedirects: 0,
        headers: csrfHeaders(),
      });
      expect(res.status(), `${label}: normalization rejects with 422 before the gate`).toBe(422);
      const body = (await res.json()) as { code?: string };
      expect(body.code, `${label} carries the stable code`).toBe('hostname_invalid');
    }
    // Nothing persisted for malformed input (BA 05: "nothing persisted").
    const list = await request.get('/api/v1/domains');
    const body = (await list.json()) as DomainResponse[];
    expect(list.status()).toBe(200);
    expect(Array.isArray(body), 'the list is a bare DomainResponse array (TL 03)').toBe(true);
    expect(body).toHaveLength(0);
  });

  test('denylisted hostnames are neutral-denied, not plan-gated (AC-07, BR-02)', async ({ request }) => {
    // A denylist hit denies on its own fact (the host can't ever be added):
    // the response reveals no plan state. Identity-provider domains are
    // seeded denylist rows (Q-01 maintained list).
    const res = await request.post('/api/v1/domains', {
      data: { hostname: 'accounts.google.com' },
      headers: csrfHeaders(),
    });
    expect(res.status()).toBe(422);
    const body = (await res.json()) as { code?: string };
    expect(body.code).toBe('domain_denied');
    // No plan facts in the deny answer (the problem-type URI may contain
    // "problems"; the Pro plan itself must never be mentioned).
    expect(JSON.stringify(body).toLowerCase(), 'no plan facts in the deny answer').not.toMatch(/\bpro\b/);
  });

  test('free accounts are denied with the fail-closed gate; no partial row (AC-09, S-6, BR-06)', async ({ request }) => {
    const host = `free-gate-${RUN}.e2e.test`;
    const res = await request.post('/api/v1/domains', { data: { hostname: host }, headers: csrfHeaders() });
    expect(res.status()).toBe(403);
    const body = (await res.json()) as { code?: string; detail?: string };
    expect(body.code).toBe('domain_forbidden');
    expect(body.detail, 'pro gate copy is stated plainly').toContain('Pro');

    // The list stays empty: the 403 means no row, ever (no partial state).
    const list = await request.get('/api/v1/domains');
    expect(list.status()).toBe(200);
    const rows = (await list.json()) as DomainResponse[];
    expect(Array.isArray(rows)).toBe(true);
    expect(rows).toHaveLength(0);

    // Verify/recheck/remove on the never-created host are honest 404s.
    const verify = await request.post(`/api/v1/domains/${host}/verify`, { data: {}, headers: csrfHeaders() });
    expect(verify.status()).toBe(404);
    const recheck = await request.post(`/api/v1/domains/${host}/recheck`, { data: {}, headers: csrfHeaders() });
    expect(recheck.status()).toBe(404);
    const remove = await request.delete(`/api/v1/domains/${host}`, { headers: csrfHeaders() });
    expect(remove.status()).toBe(404);
  });

  test('client plan flags cannot raise the gate (S-6, suite-03 tamper probe)', async ({ request }) => {
    const plain = await request.post('/api/v1/domains', {
      data: { hostname: `plain-${RUN}.e2e.test` },
      headers: csrfHeaders(),
    });
    const tampered = await request.post('/api/v1/domains', {
      data: { hostname: `tampered-${RUN}.e2e.test` },
      headers: { ...csrfHeaders(), 'X-Plan': 'pro', 'X-Entitlement-Plan': 'pro' },
    });
    expect(tampered.status()).toBe(plain.status());
    expect((await tampered.json()) as { code?: string }).toMatchObject({ code: 'domain_forbidden' });
  });

  test('the domains surface on a free account shows the upgrade path, never a fake form (AC-09 UI half)', async ({
    page,
  }) => {
    await page.goto('/app/account/domains');

    const card = page.locator('[data-slot="card"]').filter({ has: page.getByRole('heading', { name: 'Domains' }) });
    await expect(card).toBeVisible();

    // The honesty rule (BA 04): free shows the feature-03 upgrade path instead
    // of a fake form - no add input, no verify controls, no fake rows.
    await expect(card.getByText('Custom domains are a Pro feature.')).toBeVisible();
    const upgrade = card.getByRole('link', { name: 'See plans' });
    await expect(upgrade).toBeVisible();
    await upgrade.click();
    await expect(page).toHaveURL(/\/app\/pricing$/);

    await page.goBack();
    await expect(card.getByLabel(/Add a hostname/i)).toHaveCount(0);
    await expect(card.getByRole('button', { name: 'Add domain' })).toHaveCount(0);
    await expect(card.getByRole('button', { name: /Verify/ })).toHaveCount(0);
  });
});
