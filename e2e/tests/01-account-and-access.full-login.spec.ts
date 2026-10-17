// Opt-in full-login e2e: drives a real SSO dance against the local mock IdP
// (e2e/mock-idp) and asserts login completion, identity stability, the
// open-redirect clamp, and logout. Setup once per machine:
//
//   cd e2e && npm run idp:on     # generic-oidc -> mock IdP (+ dev JWT keypair)
//   npm run test:full            # headless (mock is auto-started by playwright)
//   npm run test:headed:full     # visible browser
//   npm run idp:off              # restore the placeholder IdP afterwards
//
// Note: the mock speaks http and the production CSP only allows https form
// redirects, so tests start the IdP navigation directly from the server-issued
// authorize URL (the form-POST leg is covered by the default suite). Details: e2e/README.md.
import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const MOCK_ORIGIN = 'http://localhost:9099';
const fullLogin = process.env.E2E_FULL_LOGIN === '1';
// One identity per run: the first test creates it, later tests prove it resolves
// back to the same account.
const RUN_SUB = `e2e-sub-${Date.now()}`;
const RUN_EMAIL = `${RUN_SUB}@example.test`;
let accountId: string | undefined;
let probed = false;

async function requireReady(request: APIRequestContext) {
  if (!fullLogin) {
    test.skip(true, 'opt-in suite: run `npm run idp:on` once, then `npm run test:full` (see e2e/README.md)');
  }
  if (probed) return;
  const health = await request.get(`${MOCK_ORIGIN}/__health`).catch(() => null);
  test.skip(!health?.ok(), 'mock IdP not reachable on :9099; playwright starts it for `npm run test:full` runs');
  const signIn = await request.post('/api/auth/sign-in/social', {
    form: { provider: 'generic-oidc', callbackURL: '/app' },
    maxRedirects: 0,
  });
  const location = signIn.headers()['location'] ?? '';
  test.skip(!location.startsWith(MOCK_ORIGIN), 'account-service is not pointed at the mock IdP; run `npm run idp:on` and retry');
  probed = true;
}

async function setNextUser(request: APIRequestContext, user: Record<string, unknown>) {
  const res = await request.post(`${MOCK_ORIGIN}/__control`, { data: user });
  expect(res.ok(), 'mock IdP control call').toBe(true);
}

async function startAuthorize(request: APIRequestContext, callbackURL = '/app'): Promise<string> {
  const res = await request.post('/api/auth/sign-in/social', {
    form: { provider: 'generic-oidc', callbackURL },
    maxRedirects: 0,
  });
  expect(res.status()).toBe(302);
  const location = res.headers()['location'];
  expect(location).toContain(`${MOCK_ORIGIN}/authorize`);
  return location;
}

async function approve(page: Page, authorizeUrl: string, email: string) {
  await page.goto(authorizeUrl);
  await expect(page.locator('#idp-email')).toHaveText(email);
  await page.getByRole('button', { name: 'Approve sign-in' }).click();
}

async function me(page: Page) {
  const res = await page.request.get('/api/v1/me');
  expect(res.status()).toBe(200);
  return (await res.json()) as { id: string; email: string | null; emailVerified: boolean };
}

test.describe('full login (mock IdP)', () => {
  test('fresh SSO login lands on the username chooser with a hard session cookie (AC-02, AC-13)', async ({
    page,
    request,
    context,
  }) => {
    await requireReady(request);
    await setNextUser(request, { sub: RUN_SUB, email: RUN_EMAIL, name: 'E2E User', emailVerified: true });
    const authorizeUrl = await startAuthorize(request);
    await approve(page, authorizeUrl, RUN_EMAIL);

    await expect(page).toHaveURL(/\/app\/welcome\/username$/);
    // Secure sessions carry the __Host- prefix: host-only, no Domain attribute.
    const session = (await context.cookies()).find((c) => c.name.endsWith('rb_session'));
    expect(session, 'rb_session issued by the callback').toBeTruthy();
    expect(session!.name).toBe('__Host-rb_session');
    expect(session!.httpOnly).toBe(true);
    expect(session!.secure).toBe(true);
    expect(session!.sameSite).toBe('Lax');

    // No JS-readable session token exists; only the CSRF token is script-visible.
    const jsCookies = await page.evaluate(() => document.cookie);
    expect(jsCookies).toContain('csrf_token');
    expect(jsCookies).not.toContain('rb_session');

    const profile = await me(page);
    accountId = profile.id;
    expect(profile.email).toBe(RUN_EMAIL);
    expect(profile.emailVerified).toBe(true);
  });

  test('returning sub resolves to the same account and syncs the verified email (AC-03)', async ({ page, request }) => {
    await requireReady(request);
    const changed = `changed-${Date.now()}@example.test`;
    await setNextUser(request, { sub: RUN_SUB, email: changed, name: 'E2E User', emailVerified: true });
    const authorizeUrl = await startAuthorize(request);
    await approve(page, authorizeUrl, changed);

    await expect(page).toHaveURL(/\/app(\/|$)/);
    const profile = await me(page);
    expect(profile.id, 'same sub resolves to the same account').toBe(accountId ?? profile.id);
    expect(profile.email, 'verified email syncs when collision-free').toBe(changed);
  });

  test('off-allowlist callbackURL never redirects off-site (BR-04 open-redirect clamp)', async ({ page, request }) => {
    await requireReady(request);
    await setNextUser(request, { sub: RUN_SUB, email: RUN_EMAIL, name: 'E2E User', emailVerified: true });
    const authorizeUrl = await startAuthorize(request, 'https://evil.example/phish');

    const evilHits: string[] = [];
    let callbackLocation = '';
    page.on('request', (r) => {
      if (new URL(r.url()).hostname === 'evil.example') evilHits.push(r.url());
    });
    page.on('response', (r) => {
      if (r.url().includes('/api/auth/callback/')) callbackLocation = r.headers()['location'] ?? callbackLocation;
    });

    await approve(page, authorizeUrl, RUN_EMAIL);

    await expect(page).toHaveURL(/\/app(\/|$)/);
    expect(callbackLocation, 'server clamps to an /app target').toMatch(/^\/app/);
    expect(callbackLocation).not.toContain('evil');
    expect(evilHits, 'browser never navigates to the attacker origin').toHaveLength(0);
  });

  test('logout revokes the session and gates the dashboard again (AC-06)', async ({ page, request, context }) => {
    await requireReady(request);
    await setNextUser(request, { sub: RUN_SUB, email: RUN_EMAIL, name: 'E2E User', emailVerified: true });
    const authorizeUrl = await startAuthorize(request);
    await approve(page, authorizeUrl, RUN_EMAIL);
    await expect(page).toHaveURL(/\/app(\/|$)/);

    const csrf = (await context.cookies()).find((c) => c.name === 'csrf_token');
    expect(csrf, 'csrf double-submit token issued').toBeTruthy();
    const out = await page.request.post('/api/auth/sign-out', { headers: { 'X-CSRF-Token': csrf!.value } });
    expect(out.status()).toBe(204);
    expect(
      (await context.cookies()).find((c) => c.name.endsWith('rb_session')),
      'session cookie cleared',
    ).toBeUndefined();

    await page.goto('/app/');
    await expect(page).toHaveURL(/\/app\/login\?notice=expired/);
  });
});
