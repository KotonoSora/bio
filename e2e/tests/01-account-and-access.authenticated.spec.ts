// Opt-in seeded-session e2e: ONE real SSO dance per run (beforeAll) establishes
// the session; every case below then exercises post-login behavior with ZERO
// login steps. The login logic itself is covered by the full-login suite
// (E2E-01-21..24). Setup once per machine:
//
//   cd e2e && npm run idp:on     # generic-oidc -> mock IdP (+ dev JWT keypair)
//   npm run test:auth            # headless
//   npm run test:headed:auth     # visible browser
//   npm run idp:off              # restore the placeholder IdP afterwards
//
// Budget per run: 4 sign-ins + 4 callbacks + 1 step-up callback = 9 auth ops
// (limit 20/min/IP); every other case is free. Case catalog:
// ../01-account-and-access.test-cases.md. Runbook: ../README.md.
import { expect, request as standaloneRequest, test, type APIRequestContext, type Browser, type Page } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const MOCK_ORIGIN = 'http://localhost:9099';
const fullLogin = process.env.E2E_FULL_LOGIN === '1';

// One identity per run (fresh account => deterministic lists/counts), a second
// session for the same identity for the revoke cases, and a foreign identity
// for the owner-scoping case.
const RUN = Date.now();
const SUB = `e2e-auth-${RUN}`;
const EMAIL = `${SUB}@example.test`;
const FOREIGN_SUB = `e2e-auth-foreign-${RUN}`;
const FOREIGN_EMAIL = `${FOREIGN_SUB}@example.test`;
const NAME = 'E2E Session User';
const CONTACT_EMAIL = `contact-${RUN}@example.test`;

const E2E_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const AUTH_FILE = path.join(E2E_DIR, '.auth', 'authenticated.json');
// Contexts created inside this describe inherit its test.use({ storageState })
// (test-runner option merge). Setup/probe contexts must be raw, so they pass
// this explicitly - it overrides the file-level option.
const EMPTY_STATE = { cookies: [], origins: [] };

interface Me {
  id: string;
  name: string | null;
  email: string | null;
  emailVerified: boolean;
  emailConflict: boolean;
  contactEmail: string | null;
  contactVerified: boolean;
}

interface SessionSummary {
  sid: string;
  current: boolean;
  deviceLabel: string | null;
  ipClass: string | null;
  createdAt: string;
  lastActiveAt: string;
}

const seed: {
  ready: boolean;
  skip?: string;
  accountId?: string;
  primarySid?: string;
  secondarySid?: string;
  foreignSid?: string;
  csrf?: string;
} = { ready: false };

const csrfHeaders = () => ({ 'X-CSRF-Token': seed.csrf! });

async function setNextUser(request: APIRequestContext, profile: { sub: string; email: string }) {
  const res = await request.post(`${MOCK_ORIGIN}/__control`, {
    data: { ...profile, name: NAME, emailVerified: true },
  });
  expect(res.ok(), 'mock IdP control call').toBe(true);
}

type DanceResult = { ok: true; sid: string; csrf: string; me: Me } | { ok: false; wiring: string };

/**
 * One real SSO dance in a throwaway browser context (consent page approved in
 * the browser; the callback sets the session cookie naturally). Returns the
 * issued session; optionally writes the context storage state for reuse by the
 * test contexts (test.use storageState).
 */
async function dance(browser: Browser, baseURL: string, sub: string, email: string, storagePath?: string): Promise<DanceResult> {
  const ctx = await browser.newContext({ baseURL, storageState: EMPTY_STATE });
  try {
    const signIn = await ctx.request.post('/api/auth/sign-in/social', {
      form: { provider: 'generic-oidc', callbackURL: '/app' },
      maxRedirects: 0,
    });
    expect(signIn.status()).toBe(302);
    const authorizeUrl = signIn.headers()['location'] ?? '';
    if (!authorizeUrl.startsWith(MOCK_ORIGIN)) return { ok: false, wiring: authorizeUrl };

    await setNextUser(ctx.request, { sub, email });
    const page = await ctx.newPage();
    await page.goto(authorizeUrl);
    await page.getByRole('button', { name: 'Approve sign-in' }).click();
    await page.waitForURL(/\/app(\/|$)/);

    const cookies = await ctx.cookies();
    const sid = cookies.find((c) => c.name === '__Host-rb_session')?.value;
    const csrf = cookies.find((c) => c.name === 'csrf_token')?.value;
    expect(sid, 'session cookie issued by the callback').toBeTruthy();
    expect(csrf, 'csrf double-submit cookie issued').toBeTruthy();

    const res = await ctx.request.get('/api/v1/me');
    expect(res.status(), 'seeded session authorizes /api/v1/me').toBe(200);
    const me = (await res.json()) as Me;

    if (storagePath) {
      fs.mkdirSync(path.dirname(storagePath), { recursive: true });
      await ctx.storageState({ path: storagePath });
    }
    return { ok: true, sid: sid!, csrf: csrf!, me };
  } finally {
    // Best-effort: a wedged browser close must not mask a good dance.
    await ctx.close().catch(() => {});
  }
}

async function sessions(page: Page): Promise<SessionSummary[]> {
  const res = await page.request.get('/api/v1/me/sessions');
  expect(res.status()).toBe(200);
  return ((await res.json()) as { sessions: SessionSummary[] }).sessions;
}

test.describe('authenticated surfaces (seeded session — login skipped)', () => {
  test.beforeAll(async ({ browser, baseURL }) => {
    if (!fullLogin) {
      seed.skip = 'opt-in suite: run `npm run idp:on` once, then `npm run test:auth` (see e2e/README.md)';
      return;
    }
    // Standalone contexts (not the request fixture): the fixture-level
    // storageState does not exist yet while the seed dance is running.
    const probe = await standaloneRequest.newContext({ storageState: EMPTY_STATE });
    const health = await probe.get(`${MOCK_ORIGIN}/__health`).catch(() => null);
    await probe.dispose();
    if (!health?.ok()) {
      seed.skip = 'mock IdP not reachable on :9099; playwright starts it for E2E_FULL_LOGIN=1 runs';
      return;
    }

    const primary = await dance(browser, baseURL!, SUB, EMAIL, AUTH_FILE);
    if (!primary.ok) {
      seed.skip = `account-service is not pointed at the mock IdP (got ${primary.wiring}); run \`npm run idp:on\` and retry`;
      return;
    }
    const secondary = await dance(browser, baseURL!, SUB, EMAIL);
    const foreign = await dance(browser, baseURL!, FOREIGN_SUB, FOREIGN_EMAIL);
    if (!secondary.ok || !foreign.ok) {
      seed.skip = 'IdP wiring changed while seeding sessions';
      return;
    }
    Object.assign(seed, {
      ready: true,
      accountId: primary.me.id,
      primarySid: primary.sid,
      secondarySid: secondary.sid,
      foreignSid: foreign.sid,
      csrf: primary.csrf,
    });
  });

  test.beforeEach(() => {
    test.skip(!seed.ready, seed.skip ?? 'seeded session unavailable');
  });

  test.use({ storageState: AUTH_FILE });

  test('seeded session reads its account: verified IdP email, no contact email yet (AC-03)', async ({ page }) => {
    const res = await page.request.get('/api/v1/me');
    expect(res.status()).toBe(200);
    expect(res.headers()['cache-control']).toContain('no-store');
    const me = (await res.json()) as Me;
    expect(me.id).toBe(seed.accountId);
    expect(me.email).toBe(EMAIL);
    expect(me.emailVerified).toBe(true);
    expect(me.emailConflict).toBe(false);
    expect(me.contactEmail).toBeNull();
    expect(me.contactVerified).toBe(false);
  });

  test('settings providers list marks the linked method and nothing else (AC-05)', async ({ page }) => {
    const res = await page.request.get('/api/v1/me/providers');
    expect(res.status()).toBe(200);
    const { providers } = (await res.json()) as { providers: { id: string; label: string; linked: boolean }[] };
    for (const p of providers) expect(typeof p.linked, `linked flag for ${p.id}`).toBe('boolean');
    const linked = providers.filter((p) => p.linked);
    expect(linked).toHaveLength(1);
    expect(linked[0]).toMatchObject({ id: 'generic-oidc', label: 'Dev SSO', linked: true });
  });

  test('sessions list: both seeded sessions, exactly one current, frozen summary shape (AC-06)', async ({ page }) => {
    const list = await sessions(page);
    expect(list).toHaveLength(2);
    const current = list.filter((s) => s.current);
    expect(current).toHaveLength(1);
    expect(current[0].sid).toBe(seed.primarySid);
    expect(list.map((s) => s.sid).sort()).toEqual([seed.primarySid!, seed.secondarySid!].sort());
    for (const s of list) {
      // Frozen DTO keys: no user_id or raw network data ever leaves the service.
      expect(Object.keys(s).sort()).toEqual(['createdAt', 'current', 'deviceLabel', 'ipClass', 'lastActiveAt', 'sid']);
      expect(Number.isNaN(Date.parse(s.createdAt)), `createdAt parses: ${s.createdAt}`).toBe(false);
      expect(Number.isNaN(Date.parse(s.lastActiveAt)), `lastActiveAt parses: ${s.lastActiveAt}`).toBe(false);
    }
  });

  test('dashboard renders the signed-in shell instead of bouncing (AC-13)', async ({ page }) => {
    await page.goto('/app/');
    await expect(page).toHaveURL(/\/app\/$/);
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
    await expect(page.getByText(NAME)).toBeVisible();
    await expect(page.locator('input[type="password"]')).toHaveCount(0);
  });

  test('account settings MFE mounts with the seeded session: provider and sessions visible (AC-08)', async ({ page }) => {
    await page.goto('/app/account/settings');
    await expect(page.getByRole('heading', { name: 'Account settings' })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText('Sign-in methods')).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText('Dev SSO')).toBeVisible();
    await expect(page.getByText('Linked', { exact: true })).toBeVisible();
    await expect(page.getByText('Active sessions')).toBeVisible();
    await expect(page.getByText('This device')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Sign out all other devices' })).toBeEnabled();
  });

  test('contact email: code accepted once, immediate resend is gated (BR-03, NFR-06)', async ({ page }) => {
    const first = await page.request.post('/api/v1/me/contact-email', {
      headers: csrfHeaders(),
      data: { email: CONTACT_EMAIL },
    });
    expect(first.status()).toBe(202);

    const resend = await page.request.post('/api/v1/me/contact-email', {
      headers: csrfHeaders(),
      data: { email: CONTACT_EMAIL },
    });
    expect(resend.status()).toBe(429);
    const body = (await resend.json()) as { code?: string; retryAfterSeconds?: number };
    expect(body.code).toBe('throttled');
    expect(body.retryAfterSeconds, 'resend gate TTL in seconds').toBeGreaterThan(0);
    expect(body.retryAfterSeconds!).toBeLessThanOrEqual(60);
  });

  test('owner JSON bodies validate: bad email and malformed code are 422 validation_failed', async ({ page }) => {
    const badEmail = await page.request.post('/api/v1/me/contact-email', {
      headers: csrfHeaders(),
      data: { email: 'not-an-email' },
    });
    expect(badEmail.status()).toBe(422);
    expect(((await badEmail.json()) as { code?: string }).code).toBe('validation_failed');

    const badCode = await page.request.post('/api/v1/me/contact-email/verify', {
      headers: csrfHeaders(),
      data: { code: '12' },
    });
    expect(badCode.status()).toBe(422);
    expect(((await badCode.json()) as { code?: string }).code).toBe('validation_failed');
  });

  test('wrong verification code fails closed, contact stays unverified (BR-03)', async ({ page }) => {
    const res = await page.request.post('/api/v1/me/contact-email/verify', {
      headers: csrfHeaders(),
      data: { code: '000000' },
    });
    expect(res.status()).toBe(422);
    expect(((await res.json()) as { code?: string }).code).toBe('email_code_invalid');

    const me = (await (await page.request.get('/api/v1/me')).json()) as Me;
    expect(me.contactVerified).toBe(false);
    expect(me.contactEmail).toBeNull();
  });

  test('the last sign-in method cannot be unlinked: 409 last_link_forbidden (BR-05)', async ({ page }) => {
    const res = await page.request.post('/api/auth/unlink/generic-oidc', { headers: csrfHeaders() });
    expect(res.status()).toBe(409);
    expect(((await res.json()) as { code?: string }).code).toBe('last_link_forbidden');

    const { providers } = (await (await page.request.get('/api/v1/me/providers')).json()) as {
      providers: { id: string; linked: boolean }[];
    };
    expect(providers.find((p) => p.id === 'generic-oidc')?.linked).toBe(true);
  });

  test('linking an already-linked method is refused: 409 already_linked (BR-05)', async ({ page }) => {
    const res = await page.request.post('/api/auth/link/generic-oidc', { headers: csrfHeaders() });
    expect(res.status()).toBe(409);
    expect(((await res.json()) as { code?: string }).code).toBe('already_linked');
  });

  test('step-up re-dance: authorize handoff, return to returnTo, no new session (BR-07)', async ({ page, baseURL }) => {
    const start = await page.request.post('/api/auth/step-up?provider=generic-oidc&returnTo=%2Fapp%2Faccount%2Fsettings', {
      headers: csrfHeaders(),
      maxRedirects: 0,
    });
    expect(start.status()).toBe(302);
    const authorize = new URL(start.headers()['location']);
    expect(authorize.origin + authorize.pathname).toBe(`${MOCK_ORIGIN}/authorize`);
    expect(authorize.searchParams.get('state')).toBeTruthy();
    expect(authorize.searchParams.get('code_challenge_method')).toBe('S256');

    // Complete the re-dance over HTTP: consent page, approve, callback.
    const ctx = await standaloneRequest.newContext({ baseURL, storageState: EMPTY_STATE });
    try {
      await ctx.get(start.headers()['location']);
      const approve = await ctx.get(`${MOCK_ORIGIN}/approve?state=${authorize.searchParams.get('state')}`, {
        maxRedirects: 0,
      });
      const callbackUrl = approve.headers()['location'];
      expect(callbackUrl).toContain('/api/auth/callback/generic-oidc');
      const callback = await ctx.get(callbackUrl, {
        headers: { Cookie: `__Host-rb_session=${seed.primarySid}` },
        maxRedirects: 0,
      });
      expect(callback.status()).toBe(302);
      expect(callback.headers()['location']).toBe('/app/account/settings');
      expect(callback.headers()['set-cookie'] ?? '', 'step-up issues no new session').not.toContain('rb_session');
    } finally {
      await ctx.dispose();
    }

    // The refreshed session still works and no extra session appeared.
    expect((await sessions(page)).map((s) => s.sid).sort()).toEqual([seed.primarySid!, seed.secondarySid!].sort());
  });

  test('revoking one session is owner-scoped: own other dies, foreign sid is a 404 (AC-06, AC-08)', async ({ page, baseURL }) => {
    const gone = await page.request.delete(`/api/v1/me/sessions/${seed.secondarySid}`, { headers: csrfHeaders() });
    expect(gone.status()).toBe(204);

    const again = await page.request.delete(`/api/v1/me/sessions/${seed.secondarySid}`, { headers: csrfHeaders() });
    expect(again.status()).toBe(404);
    expect(((await again.json()) as { code?: string }).code).toBe('session_not_found');

    const probes = await standaloneRequest.newContext({ baseURL, storageState: EMPTY_STATE });
    try {
      const revoked = await probes.get('/api/v1/me', { headers: { Cookie: `__Host-rb_session=${seed.secondarySid}` } });
      expect(revoked.status(), 'revoked session stops authorizing').toBe(401);

      const foreignAlive = await probes.get('/api/v1/me', { headers: { Cookie: `__Host-rb_session=${seed.foreignSid}` } });
      expect(foreignAlive.status(), 'foreign session untouched').toBe(200);
    } finally {
      await probes.dispose();
    }

    // Foreign sid: deny-by-default 404 from the owner's CSRF-valid context.
    const foreign = await page.request.delete(`/api/v1/me/sessions/${seed.foreignSid}`, { headers: csrfHeaders() });
    expect(foreign.status(), 'foreign rows resolve to zero rows, no existence oracle').toBe(404);
    expect(((await foreign.json()) as { code?: string }).code).toBe('session_not_found');

    expect((await sessions(page)).map((s) => s.sid)).toEqual([seed.primarySid!]);
  });

  test('revoke-all-others keeps the current session alive (BR-06)', async ({ page, browser, baseURL }) => {
    const extra = await dance(browser, baseURL!, SUB, EMAIL);
    expect(extra.ok).toBe(true);
    if (!extra.ok) return;
    expect(await sessions(page)).toHaveLength(2);

    const res = await page.request.delete('/api/v1/me/sessions', { headers: csrfHeaders() });
    expect(res.status()).toBe(204);

    const probes = await standaloneRequest.newContext({ baseURL, storageState: EMPTY_STATE });
    try {
      const killed = await probes.get('/api/v1/me', { headers: { Cookie: `__Host-rb_session=${extra.sid}` } });
      expect(killed.status(), 'the other session was revoked').toBe(401);
    } finally {
      await probes.dispose();
    }

    const kept = await page.request.get('/api/v1/me');
    expect(kept.status(), 'the current session survives').toBe(200);
    expect((await sessions(page)).map((s) => s.sid)).toEqual([seed.primarySid!]);
  });

  test('authenticated mutations still require the CSRF pair: 403, session intact (S-6)', async ({ page }) => {
    const res = await page.request.post('/api/auth/sign-out');
    expect(res.status()).toBe(403);
    expect(((await res.json()) as { code?: string }).code).toBe('csrf_invalid');

    const me = await page.request.get('/api/v1/me');
    expect(me.status(), 'failed CSRF attempt must not end the session').toBe(200);
  });

  test('sign-out revokes the seeded session end to end (AC-06)', async ({ page, baseURL }) => {
    const out = await page.request.post('/api/auth/sign-out', { headers: csrfHeaders() });
    expect(out.status()).toBe(204);
    expect((await page.context().cookies()).find((c) => c.name === '__Host-rb_session')).toBeUndefined();

    const probes = await standaloneRequest.newContext({ baseURL, storageState: EMPTY_STATE });
    try {
      const stale = await probes.get('/api/v1/me', { headers: { Cookie: `__Host-rb_session=${seed.primarySid}` } });
      expect(stale.status(), 'server-side session revoked, not just the cookie cleared').toBe(401);
    } finally {
      await probes.dispose();
    }

    await page.goto('/app/');
    await expect(page).toHaveURL(/\/app\/login\?notice=expired/);
  });
});
