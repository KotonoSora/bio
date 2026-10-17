// Feature 02 - account deletion e2e (opt-in, seeded + per-case identities).
// One seeded session (identity A) for read-only/ui cases; destructive flows
// dance their own fresh identities per case (B: API guards + grace/restore,
// C: full UI request -> restore journey, D: username confirmation + held name).
//
//   cd e2e && npm run idp:on     # mock IdP + dev JWT keypair (once per machine)
//   npm run test:deletion        # headless
//   npm run idp:off              # restore the placeholder IdP afterwards
//
// Budget per run: 6 dances = 12 auth ops (limit 20/min/IP) - run it as its own
// command, a minute apart from the other suites (e2e/README.md). Tests share
// state by design (test 3 continues identity B's grace from test 2); the suite
// is ordered, workers=1.
import {
  expect,
  request as standaloneRequest,
  test,
  type APIRequestContext,
  type Browser,
  type BrowserContext,
  type Page,
} from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const MOCK_ORIGIN = 'http://localhost:9099';
const fullLogin = process.env.E2E_FULL_LOGIN === '1';
const DAY_MS = 86_400_000;

const RUN = Date.now();
const USER_A = { sub: `e2e-del-a-${RUN}`, email: `e2e-del-a-${RUN}@example.test` };
const USER_B = { sub: `e2e-del-b-${RUN}`, email: `e2e-del-b-${RUN}@example.test` };
const USER_C = { sub: `e2e-del-c-${RUN}`, email: `e2e-del-c-${RUN}@example.test` };
const USER_D = { sub: `e2e-del-d-${RUN}`, email: `e2e-del-d-${RUN}@example.test` };
const NAME = 'E2E Deletion User';
const HELD_USERNAME = `del${RUN}`;

const E2E_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const AUTH_FILE = path.join(E2E_DIR, '.auth', 'deletion.json');
const EMPTY_STATE = { cookies: [], origins: [] };

interface DeleteStatus {
  status: string;
  requestedAt: string | null;
  purgeDueAt: string | null;
  certificateRef: string | null;
}

const seed: { ready: boolean; skip?: string; csrf?: string; accountId?: string; sid?: string } = { ready: false };

async function setNextUser(request: APIRequestContext, profile: { sub: string; email: string }) {
  const res = await request.post(`${MOCK_ORIGIN}/__control`, {
    data: { ...profile, name: NAME, emailVerified: true },
  });
  expect(res.ok(), 'mock IdP control call').toBe(true);
}

type DanceResult = { ok: true; sid: string; csrf: string; accountId: string } | { ok: false; wiring: string };

/** One real SSO dance in a throwaway context; optionally persists storage state. */
async function dance(browser: Browser, baseURL: string, user: { sub: string; email: string }, storagePath: string): Promise<DanceResult> {
  const ctx = await browser.newContext({ baseURL, storageState: EMPTY_STATE });
  try {
    const signIn = await ctx.request.post('/api/auth/sign-in/social', {
      form: { provider: 'generic-oidc', callbackURL: '/app' },
      maxRedirects: 0,
    });
    expect(signIn.status()).toBe(302);
    const authorizeUrl = signIn.headers()['location'] ?? '';
    if (!authorizeUrl.startsWith(MOCK_ORIGIN)) return { ok: false, wiring: authorizeUrl };

    await setNextUser(ctx.request, user);
    const page = await ctx.newPage();
    await page.goto(authorizeUrl);
    await page.getByRole('button', { name: 'Approve sign-in' }).click();
    await page.waitForURL(/\/app(\/|$)/);

    const cookies = await ctx.cookies();
    const sid = cookies.find((c) => c.name === '__Host-rb_session')?.value;
    const csrf = cookies.find((c) => c.name === 'csrf_token')?.value;
    expect(sid, 'session cookie issued').toBeTruthy();
    expect(csrf, 'csrf cookie issued').toBeTruthy();

    fs.mkdirSync(path.dirname(storagePath), { recursive: true });
    await ctx.storageState({ path: storagePath });

    const me = await ctx.request.get('/api/v1/me');
    expect(me.status(), 'fresh session authorizes /api/v1/me').toBe(200);
    const accountId = ((await me.json()) as { id: string }).id;
    return { ok: true, sid: sid!, csrf: csrf!, accountId };
  } finally {
    await ctx.close().catch(() => {});
  }
}

/** Dance + reopen a request context bound to the persisted session. */
async function sessionCtx(
  browser: Browser,
  baseURL: string,
  user: { sub: string; email: string },
  label: string,
): Promise<{ ctx: BrowserContext; csrf: string; sid: string; accountId: string }> {
  const file = path.join(E2E_DIR, '.auth', `deletion-${label}.json`);
  const result = await dance(browser, baseURL, user, file);
  expect(result.ok, `dance for ${label}`).toBe(true);
  if (!result.ok) throw new Error(result.wiring);
  const ctx = await browser.newContext({ baseURL, storageState: file });
  return { ctx, csrf: result.csrf, sid: result.sid, accountId: result.accountId };
}

const csrfOf = (csrf: string) => ({ 'X-CSRF-Token': csrf });

async function codeOf(res: { json: () => Promise<unknown> }): Promise<string | undefined> {
  return ((await res.json()) as { code?: string }).code;
}

async function statusOf(ctx: BrowserContext | Page): Promise<{ http: number; body: DeleteStatus }> {
  const res = await ctx.request.get('/api/v1/account/delete-status');
  return { http: res.status(), body: (await res.json()) as DeleteStatus };
}

test.describe('account deletion (seeded session + per-case identities)', () => {
  test.beforeAll(async ({ browser, baseURL }) => {
    if (!fullLogin) {
      seed.skip = 'opt-in suite: run `npm run idp:on` once, then `npm run test:deletion` (see e2e/README.md)';
      return;
    }
    const probe = await standaloneRequest.newContext({ storageState: EMPTY_STATE });
    const health = await probe.get(`${MOCK_ORIGIN}/__health`).catch(() => null);
    await probe.dispose();
    if (!health?.ok()) {
      seed.skip = 'mock IdP not reachable on :9099; playwright starts it for E2E_FULL_LOGIN=1 runs';
      return;
    }
    const primary = await dance(browser, baseURL!, USER_A, AUTH_FILE);
    if (!primary.ok) {
      seed.skip = `account-service is not pointed at the mock IdP (got ${primary.wiring}); run \`npm run idp:on\` and retry`;
      return;
    }
    Object.assign(seed, { ready: true, csrf: primary.csrf, accountId: primary.accountId, sid: primary.sid });
  });

  test.beforeEach(() => {
    test.skip(!seed.ready, seed.skip ?? 'seeded session unavailable');
  });

  test.use({ storageState: AUTH_FILE });

  test('delete entry point and consequences screen: vanishes vs stays, typed gate disabled until exact (BR-01, T-10)', async ({
    page,
  }) => {
    await page.goto('/app/account/settings');
    await expect(page.getByRole('heading', { name: 'Account settings' })).toBeVisible({ timeout: 15_000 });

    // BR-01: always visible in account settings - no support gate, no maze.
    await page.getByRole('button', { name: 'Delete account' }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByText('What gets deleted after 30 days')).toBeVisible();
    await expect(dialog.getByText('Profile, links, and public pages')).toBeVisible();
    await expect(dialog.getByText('Documents, resumes, and files')).toBeVisible();
    await expect(dialog.getByText('Leads, contacts, sessions, and sign-in links')).toBeVisible();
    await expect(dialog.getByText('What stays')).toBeVisible();
    await expect(dialog.getByText('Orders and invoices (amounts and dates; buyer email hashed)')).toBeVisible();
    await expect(dialog.getByText('Legal-hold evidence for open disputes')).toBeVisible();
    await expect(dialog.getByText('Audit records plus the deletion record and certificate')).toBeVisible();

    // No typed confirmation, no commit: disabled until the exact value.
    const confirm = dialog.getByRole('button', { name: 'Delete my account' });
    await expect(confirm).toBeDisabled();
    await dialog.getByLabel('Confirmation').fill(`${HELD_USERNAME}-wrong`);
    await expect(confirm).toBeDisabled();
    await expect(dialog.getByText('Type DELETE to confirm.')).toBeVisible();
    await dialog.getByRole('button', { name: 'Never mind' }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);

    // The read-only visit committed nothing.
    const { http, body } = await statusOf(page);
    expect(http).toBe(200);
    expect(body.status).toBe('none');
  });

  test('request guards: nothing-to-cancel, wrong confirmation 422, then grace + immediate session death (BA 05, BR-02)', async ({
    browser,
    baseURL,
  }) => {
    const b = await sessionCtx(browser, baseURL!, USER_B, 'b1');
    try {
      const before = await statusOf(b.ctx);
      expect(before.body.status).toBe('none');

      const cancel = await b.ctx.request.post('/api/v1/account/delete-cancel', { headers: csrfOf(b.csrf) });
      expect(cancel.status()).toBe(409);
      expect(await codeOf(cancel)).toBe('delete_not_requested');

      const mismatch = await b.ctx.request.post('/api/v1/account/delete-request', {
        headers: csrfOf(b.csrf),
        data: { confirmation: `${HELD_USERNAME}-wrong` },
      });
      expect(mismatch.status()).toBe(422);
      expect(await codeOf(mismatch)).toBe('confirmation_mismatch');

      const request = await b.ctx.request.post('/api/v1/account/delete-request', {
        headers: csrfOf(b.csrf),
        data: { confirmation: 'DELETE' },
      });
      expect(request.status()).toBe(200);
      const body = (await request.json()) as DeleteStatus;
      expect(body.status).toBe('grace');
      const due = Date.parse(body.purgeDueAt!);
      expect(Number.isNaN(due)).toBe(false);
      expect(due - Date.now(), 'day-30 purge window').toBeGreaterThan(29.9 * DAY_MS);
      expect(due - Date.now()).toBeLessThan(30.1 * DAY_MS);

      // BR-02: all sessions revoked on commit - the requesting session dies too.
      expect((await b.ctx.request.get('/api/v1/me')).status()).toBe(401);
      expect((await b.ctx.request.get('/api/v1/account/delete-status')).status()).toBe(401);

      const probes = await standaloneRequest.newContext({ baseURL: baseURL!, storageState: EMPTY_STATE });
      try {
        const stale = await probes.get('/api/v1/me', { headers: { Cookie: `__Host-rb_session=${b.sid}` } });
        expect(stale.status(), 'server-side revocation, not just a cookie clear').toBe(401);
      } finally {
        await probes.dispose();
      }
    } finally {
      await b.ctx.close();
    }
  });

  test('grace: login works, re-request is idempotent, cancel restores, re-request resets the clock (AC-01, BR-03)', async ({
    browser,
    baseURL,
  }) => {
    const b = await sessionCtx(browser, baseURL!, USER_B, 'b2');
    try {
      // AC-01: login within grace works and lands on a restorable account.
      expect((await b.ctx.request.get('/api/v1/me')).status()).toBe(200);
      const grace = await statusOf(b.ctx);
      expect(grace.http).toBe(200);
      expect(grace.body.status).toBe('grace');
      const due1 = grace.body.purgeDueAt!;

      // BA 05: repeated request during grace is idempotent - no new row, no reset.
      const again = await b.ctx.request.post('/api/v1/account/delete-request', {
        headers: csrfOf(b.csrf),
        data: { confirmation: 'DELETE' },
      });
      expect(again.status()).toBe(200);
      const againBody = (await again.json()) as DeleteStatus;
      expect(againBody.status).toBe('grace');
      expect(againBody.purgeDueAt).toBe(due1);
      expect((await b.ctx.request.get('/api/v1/me')).status(), 'idempotent re-request leaves the grace session alive').toBe(200);

      // Restore: byte-identical state, session survives the cancel.
      const cancel = await b.ctx.request.post('/api/v1/account/delete-cancel', { headers: csrfOf(b.csrf) });
      expect(cancel.status()).toBe(200);
      expect(((await cancel.json()) as DeleteStatus).status).toBe('cancelled');
      expect((await b.ctx.request.get('/api/v1/me')).status()).toBe(200);
      expect((await statusOf(b.ctx)).body.status).toBe('cancelled');

      const cancelAgain = await b.ctx.request.post('/api/v1/account/delete-cancel', { headers: csrfOf(b.csrf) });
      expect(cancelAgain.status()).toBe(409);
      expect(await codeOf(cancelAgain)).toBe('delete_not_requested');

      // Drill 2: cancel then re-request reuses the row and resets requested_at/purge_due_at.
      const re = await b.ctx.request.post('/api/v1/account/delete-request', {
        headers: csrfOf(b.csrf),
        data: { confirmation: 'DELETE' },
      });
      expect(re.status()).toBe(200);
      const reBody = (await re.json()) as DeleteStatus;
      expect(reBody.status).toBe('grace');
      expect(Date.parse(reBody.purgeDueAt!), 'reset on re-request').toBeGreaterThan(Date.parse(due1));
      expect((await b.ctx.request.get('/api/v1/me')).status(), 're-request deactivates again').toBe(401);
    } finally {
      await b.ctx.close();
    }
  });

  test('UI journey: request -> signed-out status panel -> re-login -> grace panel -> restore (T-10)', async ({
    browser,
    baseURL,
  }) => {
    const c1 = await sessionCtx(browser, baseURL!, USER_C, 'c1');
    const page = await c1.ctx.newPage();
    try {
      await page.goto('/app/account/settings');
      await expect(page.getByRole('heading', { name: 'Account settings' })).toBeVisible({ timeout: 15_000 });
      await page.getByRole('button', { name: 'Delete account' }).click();
      const dialog = page.getByRole('dialog');
      await dialog.getByLabel('Confirmation').fill('DELETE');
      await dialog.getByRole('button', { name: 'Delete my account' }).click();

      // The request committed: the MFE shows the scheduled panel from the response,
      // and the session is already dead server-side.
      await expect(page.getByText('Deletion scheduled')).toBeVisible();
      await expect(page.getByText(/Your account is scheduled for deletion on/)).toBeVisible();
      await expect(page.getByText("You've been signed out.")).toBeVisible();
      expect((await c1.ctx.request.get('/api/v1/me')).status()).toBe(401);
    } finally {
      await c1.ctx.close();
    }

    const c2 = await sessionCtx(browser, baseURL!, USER_C, 'c2');
    const page2 = await c2.ctx.newPage();
    try {
      await page2.goto('/app/account/settings');
      await expect(page2.getByRole('heading', { name: 'Account settings' })).toBeVisible({ timeout: 15_000 });
      await expect(page2.getByText(/days left/)).toBeVisible();
      await expect(page2.getByText(/Your account is scheduled for deletion on/)).toBeVisible();

      await page2.getByRole('button', { name: 'Restore account' }).click();
      const confirm = page2.getByRole('alertdialog');
      await expect(confirm.getByText('Restore your account?')).toBeVisible();
      await confirm.getByRole('button', { name: 'Restore account' }).click();

      await expect(page2.getByText('Deletion cancelled')).toBeVisible();
      expect((await c2.ctx.request.get('/api/v1/me')).status(), 'restore keeps the session').toBe(200);
      expect((await statusOf(page2)).body.status).toBe('cancelled');
    } finally {
      await c2.ctx.close();
    }
  });

  test('username confirmation and held name: exact username required, no squat during grace (AC-05, BR-07)', async ({
    browser,
    baseURL,
    page,
  }) => {
    const d = await sessionCtx(browser, baseURL!, USER_D, 'd1');
    try {
      const created = await d.ctx.request.post('/api/v1/profiles', {
        headers: csrfOf(d.csrf),
        data: { username: HELD_USERNAME, displayName: 'Held name' },
      });
      expect(created.status(), 'profile create').toBe(200);

      // With a username, DELETE is no longer the expected typed confirmation.
      const wrong = await d.ctx.request.post('/api/v1/account/delete-request', {
        headers: csrfOf(d.csrf),
        data: { confirmation: 'DELETE' },
      });
      expect(wrong.status()).toBe(422);
      expect(await codeOf(wrong)).toBe('confirmation_mismatch');

      const ok = await d.ctx.request.post('/api/v1/account/delete-request', {
        headers: csrfOf(d.csrf),
        data: { confirmation: HELD_USERNAME },
      });
      expect(ok.status()).toBe(200);
      expect(((await ok.json()) as DeleteStatus).status).toBe('grace');

      // The name stays held while suspended: a squat attempt fails as username_taken.
      const claim = await page.request.post('/api/v1/profiles', {
        headers: { 'X-CSRF-Token': seed.csrf! },
        data: { username: HELD_USERNAME, displayName: 'Squat' },
      });
      expect(claim.status()).toBe(422);
      expect(await codeOf(claim)).toBe('username_taken');
    } finally {
      await d.ctx.close();
    }
  });
});
