// Feature 03 - tier presentation e2e (opt-in, seeded session):
// the account plan read `GET /api/v1/plan` and the account-screen plan panel.
// The pre-monetize state of the rename is structural: every user resolves
// free (the ledger materializes the free floor), so the pro/subscription/trial
// branches stay unavailable until features 35/36 land (AC-05/AC-06 execution
// halves ride that batch; e2e only covers the reachable states today).
//
//   cd e2e && npm run idp:on     # mock IdP + dev JWT keypair (once per machine)
//   npm run test:plan            # headless
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
const USER_A = { sub: `e2e-plan-a-${RUN}`, email: `e2e-plan-a-${RUN}@example.test` };
const NAME = 'E2E Plan User';

const E2E_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const AUTH_FILE = path.join(E2E_DIR, '.auth', 'plan.json');
const EMPTY_STATE = { cookies: [], origins: [] };

interface PlanResponse {
  plan: string;
  subscription: {
    status: string;
    term: string;
    periodEnd: string;
    nextAmountVnd: number;
  } | null;
  trial: { days: number; endsAt: string } | null;
}

const seed: { ready: boolean; skip?: string } = { ready: false };

async function setNextUser(request: APIRequestContext, profile: { sub: string; email: string }) {
  const res = await request.post(`${MOCK_ORIGIN}/__control`, {
    data: { ...profile, name: NAME, emailVerified: true },
  });
  expect(res.ok(), 'mock IdP control call').toBe(true);
}

type DanceResult = { ok: true } | { ok: false; wiring: string };

/** One real SSO dance in a throwaway context; persists the storage state. */
async function dance(browser: Browser, baseURL: string, user: { sub: string; email: string }): Promise<DanceResult> {
  const ctx = await browser.newContext({ baseURL, storageState: EMPTY_STATE });
  try {
    const signIn = await ctx.request.post('/api/auth/sign-in/social', {
      form: { provider: 'generic-oidc', callbackURL: '/app/account/settings' },
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

    fs.mkdirSync(path.dirname(AUTH_FILE), { recursive: true });
    await ctx.storageState({ path: AUTH_FILE });

    const me = await ctx.request.get('/api/v1/me');
    expect(me.status(), 'fresh session authorizes /api/v1/me').toBe(200);
    return { ok: true };
  } finally {
    await ctx.close().catch(() => {});
  }
}

test.describe('plan read and account panel (seeded session)', () => {
  test.beforeAll(async ({ browser, baseURL }) => {
    if (!fullLogin) {
      seed.skip = 'opt-in suite: run `npm run idp:on` once, then `npm run test:plan` (see e2e/README.md)';
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
    seed.ready = true;
  });

  test.beforeEach(() => {
    test.skip(!seed.ready, seed.skip ?? 'seeded session unavailable');
  });

  test.use({ storageState: AUTH_FILE });

  test('the account plan read resolves free plainly; no invented subscription rows (AC-04, BR-03, D-1)', async ({
    request,
  }) => {
    const res = await request.get('/api/v1/plan');
    expect(res.status()).toBe(200);

    // Per-user read is never cached shared (TL 01 cache posture).
    const cache = res.headers()['cache-control'] ?? '';
    expect(cache).toContain('private');

    const body = (await res.json()) as PlanResponse;
    expect(body.plan).toBe('free');
    // Absence stays the design: the projection is not answerable pre-feature 36,
    // and free rows are never invented.
    expect(body.subscription).toBeFalsy();
    expect(body.trial).toBeFalsy();
    const raw = await (await request.get('/api/v1/plan')).text();
    for (const invented of ['nextAmountVnd', 'periodEnd', 'renewal']) {
      expect(raw, `no invented ${invented} pre-monetize`).not.toContain(invented);
    }
  });

  test('gates trust the server snapshot only: client plan flags are ignored (BR-09, S-1)', async ({ request }) => {
    const plain = (await (await request.get('/api/v1/plan')).json()) as PlanResponse;
    const tampered = (await (await request.get('/api/v1/plan?plan=pro', {
      headers: { 'X-Plan': 'pro', 'X-Entitlement-Plan': 'pro' },
    })).json()) as PlanResponse;
    expect(tampered.plan).toBe('free');
    expect(tampered).toEqual(plain);
  });

  test('the account panel shows the current tier chip and hides paid-state rows (BR-05, T-06)', async ({ page }) => {
    await page.goto('/app/account/settings');

    const planCard = page.locator('[data-slot="card"]').filter({ has: page.getByRole('heading', { name: 'Plan' }) });
    await expect(planCard).toBeVisible();
    await expect(planCard.getByText('Current plan', { exact: true })).toBeVisible();

    // Free state (BR-05): both the chip and the current-plan row report it;
    // renewal/trial rows are omitted and cancel/change stay hidden
    // (BA 04: always-visible is for paid states only).
    await expect(planCard.locator('[data-slot="badge"]')).toHaveText('Free');
    await expect(planCard.locator('p').filter({ hasText: 'Current plan' })).toContainText('Free');
    await expect(planCard.getByRole('button', { name: 'Cancel plan' })).toHaveCount(0);
    await expect(planCard.getByRole('button', { name: 'Change plan' })).toHaveCount(0);
    await expect(planCard.getByText('Your renewal price')).toHaveCount(0);
    await expect(planCard.getByText(/Trial/)).toHaveCount(0);

    // The upgrade path leads to the pricing page (US-04): exact effects there.
    await planCard.getByRole('link', { name: 'See plans' }).click();
    await expect(page).toHaveURL(/\/app\/pricing$/);
    await expect(page.getByRole('heading', { name: 'Simple pricing', exact: true })).toBeVisible();
  });
});
