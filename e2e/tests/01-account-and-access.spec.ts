// Feature 01 - account and access: browser e2e against the compose stack via
// the edge (baseURL). Scope: surfaces reachable without a live IdP - the
// SSO-only entry UI, OIDC redirect hardening (state/nonce/PKCE), callback
// fail-closed, the password-dead drill, and anonymous session guards. Flows
// that need a real IdP or clock control (full login, linking, recovery,
// revocation) remain with the account-service integration tests under
// services/account-service/src/integrationTest.
import { expect, test } from '@playwright/test';

const NOTICE_EXPIRED = 'You were signed out. Sign in again to continue.';
const SSO_BUTTON = 'Continue with Dev SSO';

test('SSO-only sign-in surface: Dev SSO button, no password field (AC-01, AC-02)', async ({ page }) => {
  await page.goto('/app/login');
  await expect(page.getByText('Sign in', { exact: true })).toBeVisible();
  await expect(page.getByText('Choose a sign-in method to continue.')).toBeVisible();
  await expect(page.getByRole('button', { name: SSO_BUTTON })).toBeVisible();
  await expect(page.locator('input[type="password"]')).toHaveCount(0);
});

test('password endpoints are dead: 404 auth_disabled (AC-01)', async ({ request }) => {
  const probes = [
    '/api/auth/sign-up/email',
    '/api/auth/sign-in/email',
    '/api/auth/forget-password',
    '/api/auth/reset-password',
  ];
  for (const path of probes) {
    const res = await request.post(path, { data: { email: 'probe@example.com', password: 'hunter2-hunter2' } });
    expect(res.status(), path).toBe(404);
    const body = (await res.json()) as { code?: string };
    expect(body.code, path).toBe('auth_disabled');
  }
});

test('SSO redirect carries state, nonce, and S256 PKCE; csrf cookie flags hold (AC-02, AC-04)', async ({ page, context }) => {
  // The placeholder IdP (idp.invalid) must never leave the machine: fulfil the
  // authorize navigation with a stub so the redirect itself can be inspected.
  await page.route('https://idp.invalid/**', (route) =>
    route.fulfill({ contentType: 'text/html', body: '<h1 id="idp-stub">IdP stub</h1>' }),
  );
  await page.goto('/app/login');
  await page.getByRole('button', { name: SSO_BUTTON }).click();
  await page.waitForURL('https://idp.invalid/authorize**');
  await expect(page.locator('#idp-stub')).toBeVisible();

  const url = new URL(page.url());
  expect(url.searchParams.get('response_type')).toBe('code');
  expect(url.searchParams.get('client_id')).toBe('dev-smoke-client');
  expect(new URL(url.searchParams.get('redirect_uri')!).pathname).toBe('/api/auth/callback/generic-oidc');
  expect(url.searchParams.get('scope')).toContain('openid');
  expect(url.searchParams.get('code_challenge_method')).toBe('S256');
  expect(url.searchParams.get('code_challenge')).toMatch(/^[A-Za-z0-9_-]{43}$/); // RFC 7636 S256
  expect(url.searchParams.get('state')).toMatch(/^[A-Za-z0-9_-]{16,}$/);
  expect(url.searchParams.get('nonce')).toBeTruthy();

  const csrf = (await context.cookies()).find((c) => c.name === 'csrf_token');
  expect(csrf, 'csrf double-submit cookie is set').toBeTruthy();
  expect(csrf!.secure).toBe(true);
  expect(csrf!.httpOnly).toBe(false);
  expect(csrf!.sameSite).toBe('Lax');
});

test('malformed/replayed callbacks fail closed to /app/auth/error, no session issued (AC-04)', async ({ page }) => {
  const malformed = [
    '/api/auth/callback/generic-oidc',
    '/api/auth/callback/generic-oidc?code=bogus',
    '/api/auth/callback/generic-oidc?state=bogus',
    '/api/auth/callback/generic-oidc?code=bogus&state=bogus',
  ];
  for (const path of malformed) {
    await page.goto(path);
    await expect(page, path).toHaveURL(/\/app\/auth\/error$/);
    await expect(page.getByRole('heading', { name: "Sign-in didn't complete. Try again." })).toBeVisible();
    expect((await page.context().cookies()).map((c) => c.name), path).not.toContain('rb_session');
  }
});

test('anonymous dashboard bounces to sign-in with session-ended notice (AC-06, AC-13)', async ({ page }) => {
  await page.goto('/app/');
  await expect(page).toHaveURL(/\/app\/login\?notice=expired/);
  await expect(page.getByText(NOTICE_EXPIRED)).toBeVisible();
  await expect(page.getByRole('button', { name: SSO_BUTTON })).toBeVisible();
});

test('account settings MFE is session-gated (AC-08)', async ({ page }) => {
  await page.goto('/app/account/settings');
  await expect(page).toHaveURL(/\/app\/login\?notice=expired/, { timeout: 15_000 });
  await expect(page.getByText(NOTICE_EXPIRED)).toBeVisible();
});

test('anonymous owner APIs answer problem+json 401, uncacheable (AC-08, AC-14 partial)', async ({ request }) => {
  for (const path of ['/api/v1/me', '/api/v1/me/sessions', '/api/v1/me/providers']) {
    const res = await request.get(path);
    expect(res.status(), path).toBe(401);
    expect(res.headers()['content-type'], path).toContain('application/problem+json');
    expect(res.headers()['cache-control'], path).toContain('no-store');
    const body = (await res.json()) as { code?: string };
    expect(body.code, path).toBe('unauthenticated');
  }
});

test('first-login handoff route renders the username chooser (AC-13)', async ({ page }) => {
  await page.goto('/app/welcome/username');
  await expect(page.getByText('Pick your username', { exact: true })).toBeVisible();
  // Button-with-render renders an anchor: role is link, not button.
  await expect(page.getByRole('link', { name: 'Continue to dashboard' })).toBeVisible();
});
