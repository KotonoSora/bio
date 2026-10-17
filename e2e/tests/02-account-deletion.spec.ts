// Feature 02 - account deletion e2e (default set) against the compose stack
// through the edge. Covers the surfaces reachable without a live IdP: owner
// guards on the deletion endpoints (401/403 before anything commits) and the
// no-shortcut surface (BR-11) - internal purge/status/probe routes must stay
// invisible at the edge (AC-09). The authenticated half (request, grace,
// restore, username cooldown) runs in the opt-in suite
// (02-account-deletion.authenticated.spec.ts). Runbook: e2e/README.md.
import { expect, test } from '@playwright/test';

const PROBLEM = 'application/problem+json';

test.describe('deletion entry guards (anonymous)', () => {
  test('delete-status without a session: 401 problem+json, uncacheable (BR-01)', async ({ request }) => {
    const res = await request.get('/api/v1/account/delete-status');
    expect(res.status()).toBe(401);
    expect(res.headers()['content-type']).toContain(PROBLEM);
    expect(res.headers()['cache-control']).toContain('no-store');
    expect(((await res.json()) as { code?: string }).code).toBe('unauthenticated');
  });

  test('unknown deletion subroutes are deny-by-default: 401, no existence oracle', async ({ request }) => {
    const res = await request.get('/api/v1/account/not-a-route');
    expect(res.status()).toBe(401);
    expect(res.headers()['content-type']).toContain(PROBLEM);
    expect(((await res.json()) as { code?: string }).code).toBe('unauthenticated');
  });

  test('mutations need the CSRF pair before anything else: 403 csrf_invalid (S-6)', async ({ request }) => {
    for (const path of ['/api/v1/account/delete-request', '/api/v1/account/delete-cancel']) {
      const res = await request.post(path, { data: { confirmation: 'DELETE' } });
      expect(res.status(), path).toBe(403);
      expect(((await res.json()) as { code?: string }).code, path).toBe('csrf_invalid');
    }
  });

  test('with the CSRF pair but no session: 401 unauthenticated, nothing committed (BR-01)', async ({
    playwright,
    baseURL,
  }) => {
    const ctx = await playwright.request.newContext({ baseURL: baseURL! });
    try {
      const res = await ctx.post('/api/v1/account/delete-request', {
        headers: { Cookie: 'csrf_token=aaa', 'X-CSRF-Token': 'aaa' },
        data: { confirmation: 'DELETE' },
      });
      expect(res.status()).toBe(401);
      expect(((await res.json()) as { code?: string }).code).toBe('unauthenticated');
    } finally {
      await ctx.dispose();
    }
  });
});

test.describe('no-shortcut surface (AC-09, BR-11)', () => {
  test('internal purge, status, and probe routes are invisible at the edge: 404', async ({ request }) => {
    const probes: [string, 'GET' | 'POST'][] = [
      ['/internal/v1/account/purge', 'POST'],
      ['/internal/v1/account/status/probe-user', 'GET'],
      ['/internal/v1/account/deletion-status/probe-user', 'GET'],
      ['/internal/v1/account/purge-probe/probe-user', 'GET'],
    ];
    for (const [path, method] of probes) {
      const res =
        method === 'POST'
          ? await request.post(path, { data: { userId: 'probe-user', runId: 'probe-run' } })
          : await request.get(path);
      expect(res.status(), `${method} ${path}`).toBe(404);
    }
  });

  test('support-style probing of the owner API stays deny-by-default: 401, no purge trigger exists (BR-11)', async ({
    playwright,
    baseURL,
  }) => {
    const ctx = await playwright.request.newContext({ baseURL: baseURL! });
    try {
      const res = await ctx.post('/api/v1/account/purge', {
        headers: { Cookie: 'csrf_token=aaa', 'X-CSRF-Token': 'aaa' },
        data: { userId: 'probe-user' },
      });
      expect(res.status()).toBe(401); // route absent, deny-by-default answer - no existence oracle
      expect(((await res.json()) as { code?: string }).code).toBe('unauthenticated');
    } finally {
      await ctx.dispose();
    }
  });
});
