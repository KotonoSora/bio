// Feature 01 - account and access e2e (default set) against the compose stack
// through the edge. Covers the surfaces reachable without a live IdP: SSO-only
// entry, redirect hardening, callback fail-closed, session/owner guards, CSRF,
// recovery fail-closed, routing, and edge headers. Login completion (new and
// returning users, logout) runs in the opt-in full-login suite
// (01-account-and-access.full-login.spec.ts). Case catalog:../01-account-and-access.test-cases.md
// Runbook: e2e/README.md.
import { expect, test } from '@playwright/test';

const NOTICE_EXPIRED = 'You were signed out. Sign in again to continue.';
const NOTICE_FAILED = "Sign-in didn't complete. Try again.";
const SSO_BUTTON = 'Continue with Dev SSO';

test.describe('login entry', () => {
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

  test('login notices render for failed and expired redirects (AC-04, AC-06)', async ({ page }) => {
    await page.goto('/app/login?notice=failed');
    await expect(page.getByText(NOTICE_FAILED)).toBeVisible();
    await page.goto('/app/login?notice=expired');
    await expect(page.getByText(NOTICE_EXPIRED)).toBeVisible();
  });

  test('login callbackURL is allowlist-sanitized in the form (AC-04)', async ({ page }) => {
    const hidden = () => page.locator('input[name="callbackURL"]');
    await page.goto('/app/login?next=https://evil.example');
    await expect(hidden()).toHaveValue('/app');
    await page.goto('/app/login?next=//evil.example');
    await expect(hidden()).toHaveValue('/app');
    await page.goto('/app/login?next=/welcome/username');
    await expect(hidden()).toHaveValue('/welcome/username');
  });

  test('providers endpoint lists enabled methods with unique ids (AC-02)', async ({ request }) => {
    const res = await request.get('/api/auth/providers');
    expect(res.status()).toBe(200);
    const body = (await res.json()) as { providers: { id: string; label: string }[] };
    expect(body.providers.length).toBeGreaterThan(0);
    const ids = body.providers.map((p) => p.id);
    expect(new Set(ids).size).toBe(ids.length);
    for (const p of body.providers) {
      expect(p.id, 'provider id').toBeTruthy();
      expect(p.label, 'provider label').toBeTruthy();
    }
    expect(ids, 'dev stack: placeholder SSO enabled').toContain('generic-oidc');
  });

  test('sign-in rejects an unknown provider uniformly: 422 validation_failed (AC-04)', async ({ request }) => {
    const res = await request.post('/api/auth/sign-in/social', {
      form: { provider: 'not-a-provider', callbackURL: '/app' },
    });
    expect(res.status()).toBe(422);
    const body = (await res.json()) as { code?: string; detail?: string };
    expect(body.code).toBe('validation_failed');
    expect(body.detail).toBe("Sign-in isn't available right now.");
  });

  test('SSO redirect carries state, nonce, and S256 PKCE; csrf cookie flags hold (AC-02, AC-04)', async ({
    page,
    context,
    request,
  }) => {
    // Server leg: sign-in answers a 302 whose Location carries every OIDC param
    // (fresh single-use state per attempt). Asserted from the response, so the
    // test is deterministic in headless too: the headless shell does not route-
    // intercept a form-POST -> 302 cross-origin navigation (headed chromium does).
    const signIn = await request.post('/api/auth/sign-in/social', {
      form: { provider: 'generic-oidc', callbackURL: '/app' },
      maxRedirects: 0,
    });
    expect(signIn.status()).toBe(302);
    const url = new URL(signIn.headers()['location']);
    // Authorize origin is environment-dependent: the placeholder IdP
    // (`https://idp.invalid`) in the base dev stack, the bundled mock IdP when
    // the full-login setup is on (e2e/scripts/enable-full-login.sh).
    expect(url.origin + url.pathname).toMatch(/^(https:\/\/idp\.invalid|http:\/\/localhost:9099)\/authorize$/);
    expect(url.searchParams.get('response_type')).toBe('code');
    expect(url.searchParams.get('client_id')).toBe('dev-smoke-client');
    expect(new URL(url.searchParams.get('redirect_uri')!).pathname).toBe('/api/auth/callback/generic-oidc');
    expect(url.searchParams.get('scope')).toContain('openid');
    expect(url.searchParams.get('code_challenge_method')).toBe('S256');
    expect(url.searchParams.get('code_challenge')).toMatch(/^[A-Za-z0-9_-]{43}$/); // RFC 7636 S256
    expect(url.searchParams.get('state')).toMatch(/^[A-Za-z0-9_-]{16,}$/);
    expect(url.searchParams.get('nonce')).toBeTruthy();

    // Browser leg: the button performs the navigation POST the server 302 needs
    // to govern (BA 04); the placeholder IdP is stubbed so nothing leaves the
    // machine in headed runs.
    await page.route('https://idp.invalid/**', (route) =>
      route.fulfill({ contentType: 'text/html', body: '<h1 id="idp-stub">IdP stub</h1>' }),
    );
    await page.goto('/app/login');
    const [post] = await Promise.all([
      page.waitForRequest((r) => r.url().includes('/api/auth/sign-in/social') && r.method() === 'POST'),
      page.getByRole('button', { name: SSO_BUTTON }).click(),
    ]);
    expect(post.postData() ?? '').toContain('provider=generic-oidc');

    const csrf = (await context.cookies()).find((c) => c.name === 'csrf_token');
    expect(csrf, 'csrf double-submit cookie is set').toBeTruthy();
    expect(csrf!.secure).toBe(true);
    expect(csrf!.httpOnly).toBe(false);
    expect(csrf!.sameSite).toBe('Lax');
  });
});

test.describe('callback security', () => {
  test('malformed callbacks fail closed to /app/auth/error, no session issued (AC-04)', async ({ page }) => {
    const malformed = [
      '/api/auth/callback/generic-oidc',
      '/api/auth/callback/generic-oidc?code=bogus',
      '/api/auth/callback/generic-oidc?code=bogus&state=bogus',
    ];
    for (const path of malformed) {
      await page.goto(path);
      await expect(page, path).toHaveURL(/\/app\/auth\/error$/);
      await expect(page.getByRole('heading', { name: NOTICE_FAILED })).toBeVisible();
      expect((await page.context().cookies()).map((c) => c.name), path).not.toContain('rb_session');
    }
  });

  test('valid state with a bogus code fails closed and the state is single-use (AC-04)', async ({ request }) => {
    const signIn = await request.post('/api/auth/sign-in/social', {
      form: { provider: 'generic-oidc', callbackURL: '/app' },
      maxRedirects: 0,
    });
    expect(signIn.status()).toBe(302);
    const state = new URL(signIn.headers()['location']).searchParams.get('state');
    expect(state).toBeTruthy();

    // First use: the code exchange fails server-side. Second use: the state was
    // already consumed. Both collapse to the generic failure - no oracle.
    for (const attempt of ['first use', 'replay']) {
      const res = await request.get(`/api/auth/callback/generic-oidc?code=bogus&state=${state}`, { maxRedirects: 0 });
      expect(res.status(), attempt).toBe(302);
      expect(res.headers()['location'], attempt).toBe('/auth/error');
      expect(res.headers()['set-cookie'] ?? '', attempt).not.toContain('rb_session');
    }
  });

  test('state minted for one provider is rejected on another (AC-04)', async ({ request }) => {
    const signIn = await request.post('/api/auth/sign-in/social', {
      form: { provider: 'generic-oidc', callbackURL: '/app' },
      maxRedirects: 0,
    });
    const state = new URL(signIn.headers()['location']).searchParams.get('state');
    expect(state).toBeTruthy();

    const res = await request.get(`/api/auth/callback/linkedin?code=bogus&state=${state}`, { maxRedirects: 0 });
    expect(res.status()).toBe(302);
    expect(res.headers()['location']).toBe('/auth/error');
  });
});

test.describe('session and owner guards', () => {
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

  test('unknown API routes are deny-by-default 401: no existence oracle (AC-08)', async ({ request }) => {
    const res = await request.get('/api/definitely-not-a-route');
    expect(res.status()).toBe(401);
    expect(res.headers()['content-type']).toContain('application/problem+json');
    const body = (await res.json()) as { code?: string };
    expect(body.code).toBe('unauthenticated');
  });

  test('mutations require the CSRF double-submit token (S-6, baseline 08)', async ({ playwright, baseURL }) => {
    const signOut = async (headers: Record<string, string>) => {
      const ctx = await playwright.request.newContext({ baseURL: baseURL! });
      try {
        const res = await ctx.post('/api/auth/sign-out', { headers });
        return { status: res.status(), body: (await res.json()) as { code?: string } };
      } finally {
        await ctx.dispose();
      }
    };
    const missing = await signOut({});
    expect(missing.status, 'no cookie, no header').toBe(403);
    expect(missing.body.code).toBe('csrf_invalid');
    const cookieOnly = await signOut({ Cookie: 'csrf_token=aaa' });
    expect(cookieOnly.status, 'cookie without header').toBe(403);
    expect(cookieOnly.body.code).toBe('csrf_invalid');
    const mismatch = await signOut({ Cookie: 'csrf_token=aaa', 'X-CSRF-Token': 'bbb' });
    expect(mismatch.status, 'mismatched pair').toBe(403);
    expect(mismatch.body.code).toBe('csrf_invalid');
    const match = await signOut({ Cookie: 'csrf_token=aaa', 'X-CSRF-Token': 'aaa' });
    expect(match.status, 'valid pair, no session').toBe(401);
    expect(match.body.code).toBe('unauthenticated');
  });
});

test.describe('recovery', () => {
  test('recovery landing renders the provider choreography (AC-12)', async ({ page }) => {
    await page.goto('/app/recovery/demo-invitation');
    await expect(page.getByText('Recover access', { exact: true })).toBeVisible();
    await expect(page.locator('form')).toHaveAttribute('action', '/api/auth/recovery/demo-invitation/link/generic-oidc');
    await expect(page.getByRole('button', { name: SSO_BUTTON })).toBeVisible();
  });

  test('recovery endpoints fail closed for unknown tokens (AC-12)', async ({ request }) => {
    const get = await request.get('/api/auth/recovery/unknown-token', { maxRedirects: 0 });
    expect(get.status()).toBe(302);
    expect(get.headers()['location']).toBe('/auth/error');

    const post = await request.post('/api/auth/recovery/unknown-token/link/generic-oidc', {
      form: {},
      maxRedirects: 0,
    });
    expect(post.status()).toBe(302);
    expect(post.headers()['location']).toBe('/auth/error');
    expect(post.headers()['set-cookie'] ?? '').not.toContain('rb_session');
  });
});

test.describe('routing and edge hardening', () => {
  test('first-login handoff route renders the username chooser (AC-13)', async ({ page }) => {
    await page.goto('/app/welcome/username');
    await expect(page.getByText('Pick your username', { exact: true })).toBeVisible();
    // Button-with-render renders an anchor: role is link, not button.
    await expect(page.getByRole('link', { name: 'Continue to dashboard' })).toBeVisible();
  });

  test('unknown app routes render the SPA not-found page (baseline routing)', async ({ page }) => {
    await page.goto('/app/no-such-page');
    await expect(page.getByText('Page not found', { exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Back to dashboard' })).toBeVisible();
  });

  test('edge sends hardened security headers on app documents (AC-15, baseline 08)', async ({ request }) => {
    const res = await request.get('/app/login');
    const h = res.headers();
    expect(h['content-security-policy']).toContain("default-src 'self'");
    // The SSO form hands off to the IdP via a server 302; form-action must allow
    // https targets or every real sign-in is blocked (regression: e2e round 1).
    expect(h['content-security-policy']).toContain("form-action 'self' https:");
    expect(h['x-content-type-options']).toBe('nosniff');
    expect(h['x-frame-options']).toBe('DENY');
    expect(h['referrer-policy']).toBe('strict-origin-when-cross-origin');
    expect(h['strict-transport-security']).toContain('max-age=');
  });
});
