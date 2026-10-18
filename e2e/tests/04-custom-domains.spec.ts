// Feature 04 - custom domains e2e (default set) against the compose stack
// through the edge. Covers the reachable anonymous half: owner-API auth
// guards, the mesh-only domain namespace, the foreign-host neutral error
// contract (BR-04/BR-11: unknown/unverified hosts never render a profile,
// never redirect), and the platform-host routing order lock (resolve runs
// only for non-platform hosts).
// Case catalog: e2e/README.md (feature 04 section). Runbook: e2e/README.md.
import { expect, test } from '@playwright/test';

// A foreign host: anything that is not the dev platform host. The edge
// accepts any Host (server_name _) and forwards the original value to the
// gateway, which is exactly the surface an attacker or a caring creator
// controls when they point DNS at the platform.
const FOREIGN_HOST = 'unknown-creator.e2e.test';

test.describe('owner API is session-gated before any route exists (S-6, BR-06)', () => {
  test('the domains list requires a session (401, no probe surface)', async ({ request }) => {
    const res = await request.get('/api/v1/domains');
    expect(res.status()).toBe(401);
    const body = (await res.json()) as { code?: string };
    expect(body.code).toBe('unauthenticated');
  });

  test('mutations need the CSRF pair before anything else: 403 csrf_invalid (S-6, platform baseline)', async ({
    request,
  }) => {
    const host = `unauth-${Date.now()}.e2e.test`;
    const mutations = [
      () => request.post('/api/v1/domains', { data: { hostname: host } }),
      () => request.post(`/api/v1/domains/${host}/verify`, { data: {} }),
      () => request.post(`/api/v1/domains/${host}/recheck`, { data: {} }),
      () => request.post(`/api/v1/domains/${host}/reissue`, { data: {} }),
      () => request.delete(`/api/v1/domains/${host}`),
    ];
    for (const mutate of mutations) {
      const res = await mutate();
      expect(res.status()).toBe(403);
      expect(((await res.json()) as { code?: string }).code).toBe('csrf_invalid');
    }
  });

  test('with the CSRF pair but no session: 401 unauthenticated, nothing commits (BR-01)', async ({
    playwright,
    baseURL,
  }) => {
    const ctx = await playwright.request.newContext({ baseURL: baseURL! });
    try {
      const host = `unauth-${Date.now()}.e2e.test`;
      const headers = { Cookie: 'csrf_token=aaa', 'X-CSRF-Token': 'aaa' };
      const add = await ctx.post('/api/v1/domains', { headers, data: { hostname: host } });
      expect(add.status()).toBe(401);
      expect(((await add.json()) as { code?: string }).code).toBe('unauthenticated');
      const verify = await ctx.post(`/api/v1/domains/${host}/verify`, { headers, data: {} });
      expect(verify.status()).toBe(401);
      const recheck = await ctx.post(`/api/v1/domains/${host}/recheck`, { headers, data: {} });
      expect(recheck.status()).toBe(401);
      const remove = await ctx.delete(`/api/v1/domains/${host}`, { headers });
      expect(remove.status()).toBe(401);
    } finally {
      await ctx.dispose();
    }
  });

  test('free-tier client plan flags cannot raise the gate (S-6, BR-06 fail-closed shape)', async ({
    playwright,
    baseURL,
  }) => {
    const ctx = await playwright.request.newContext({ baseURL: baseURL! });
    try {
      const res = await ctx.post('/api/v1/domains', {
        headers: { Cookie: 'csrf_token=aaa', 'X-CSRF-Token': 'aaa', 'X-Plan': 'pro', 'X-Entitlement-Plan': 'pro' },
        data: { hostname: 'tampered.e2e.test' },
      });
      // No session: client-supplied plan flags never reach an entitlement
      // decision - the session gate answers first and no domain code leaks.
      expect(res.status()).toBe(401);
      expect(((await res.json()) as { code?: string }).code).toBe('unauthenticated');
    } finally {
      await ctx.dispose();
    }
  });
});

test.describe('mesh-only domain namespace (S-5, no browser shortcut)', () => {
  test('internal resolve/reclaim/denylist routes never answer at the edge', async ({ request }) => {
    for (const path of [
      '/internal/v1/domains/unknown-creator.e2e.test',
      '/internal/v1/domains/by-user/dev-user-1',
      '/internal/v1/domains/unknown-creator.e2e.test/reclaim',
      '/internal/v1/domains/denylist/unknown-creator.e2e.test',
    ]) {
      const res = await request.get(path);
      expect(res.status(), `GET ${path} stays mesh-only`).toBe(404);
      const raw = await res.text();
      expect(raw, 'no binding data leaks').not.toContain('hostname');
      expect(raw).not.toContain('profile');
    }
  });

  test('denylist/resolve writes are unreachable from the public edge', async ({ request }) => {
    for (const path of ['/internal/v1/domains/denylist', '/internal/v1/domains/unknown-creator.e2e.test/reclaim']) {
      const res = await request.post(path, { data: { hostname: 'x.e2e.test', evidenceRef: 'e2e' } });
      expect(res.status(), `POST ${path} stays mesh-only`).toBe(404);
    }
  });
});

test.describe('foreign hosts render the neutral error page (BR-04, BR-11, US-03)', () => {
  test('unknown host request: neutral 404, no profile content, no redirect, no echo beyond the host', async ({
    request,
  }) => {
    const res = await request.get('/any-slug', {
      maxRedirects: 0,
      headers: { Host: FOREIGN_HOST },
    });
    expect(res.status()).toBe(404);
    expect(res.headers()['location'], 'unknown hosts never redirect (no open-redirect surface)').toBeUndefined();

    const raw = await res.text();
    expect(raw).toContain("This site isn't available.");
    expect(raw, 'no profile SSR leaks').not.toContain('ssr-shell');
    expect(raw).not.toContain('username');
    expect(raw).not.toContain('render');
  });

  test('the neutral page carries the negative-cache contract (NFR-05: 60 s)', async ({ request }) => {
    const res = await request.get('/probe-slug', { headers: { Host: FOREIGN_HOST } });
    expect(res.status()).toBe(404);
    const cache = res.headers()['cache-control'] ?? '';
    expect(cache).toContain('public');
    expect(cache).toContain('max-age=60');
    expect(cache, 'per-host key, never shared with a session').not.toContain('private');
    expect(res.headers()['set-cookie'], 'the neutral page carries no cookies').toBeUndefined();
  });

  test('blocked states render the identical neutral shape (NFR-09 parity class)', async ({ request }) => {
    // Two foreign hosts, two unknown slugs: identical status, identical body,
    // identical timing class (no content differences beyond nothing).
    const first = await request.get('/alpha', { headers: { Host: `a-${Date.now()}.e2e.test` } });
    const second = await request.get('/beta', { headers: { Host: `b-${Date.now()}.e2e.test` } });
    expect(first.status()).toBe(404);
    expect(second.status()).toBe(404);
    expect(await second.text()).toBe(await first.text());
  });
});

test.describe('platform-host routing order (BR-04: resolve is a foreign-host concern)', () => {
  test('the platform host keeps its public-site SSR path untouched', async ({ request }) => {
    const res = await request.get('/some-slug', { maxRedirects: 0 });
    expect(res.status()).toBe(200);
    const body = (await res.json()) as { username?: string; render?: string };
    expect(body.username).toBe('some-slug');
    expect(body.render).toContain('ssr-shell');
  });
});
