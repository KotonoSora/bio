// Feature 05 - concierge managed service e2e (default set) against the compose
// stack through the edge. Covers the reachable anonymous half: owner-API auth
// guards, the mesh-only internal namespace (S-1/S-5: no browser shortcut to
// staffing, templates, or the approval-check route), and the session-gated
// SPA route (no fabricated board before a session).
// The staffed journey (AC-01..AC-08) is e2e-owned by the opt-in
// `05-concierge-managed-service.authenticated.spec.ts` (seeded session +
// agency-owner/operator service JWTs; see e2e/README.md).
import { expect, test } from '@playwright/test';

test.describe('owner API is session-gated before any route exists (BR-02, S-1)', () => {
  test('concierge reads require a session (401, no probe surface)', async ({ request }) => {
    for (const path of ['/api/v1/concierge', '/api/v1/concierge/tasks']) {
      const res = await request.get(path);
      expect(res.status(), `GET ${path}`).toBe(401);
      expect(((await res.json()) as { code?: string }).code).toBe('unauthenticated');
    }
  });

  test('mutations need the CSRF pair before anything else: 403 csrf_invalid (platform baseline)', async ({
    request,
  }) => {
    const mutations = [
      () => request.post('/api/v1/concierge/approve', { data: { scope: 'setup plan' } }),
      () => request.post('/api/v1/concierge/tasks', { data: { kind: 'campaign' } }),
      () => request.post('/api/v1/concierge/tasks/t-1/approve', { data: { scope: 'send the first batch' } }),
      () => request.post('/api/v1/concierge/pause', { data: {} }),
      () => request.post('/api/v1/concierge/exit', { data: {} }),
      () => request.delete('/api/v1/concierge/transition'),
    ];
    for (const mutate of mutations) {
      const res = await mutate();
      expect(res.status()).toBe(403);
      expect(((await res.json()) as { code?: string }).code).toBe('csrf_invalid');
    }
  });

  test('with the CSRF pair but no session: 401 unauthenticated, nothing commits (BR-02)', async ({
    playwright,
    baseURL,
  }) => {
    const ctx = await playwright.request.newContext({ baseURL: baseURL! });
    try {
      const headers = { Cookie: 'csrf_token=aaa', 'X-CSRF-Token': 'aaa' };
      const expect401 = async (res: import('@playwright/test').APIResponse, label: string) => {
        expect(res.status(), label).toBe(401);
        expect(((await res.json()) as { code?: string }).code).toBe('unauthenticated');
      };
      await expect401(await ctx.post('/api/v1/concierge/approve', { headers, data: { scope: 'setup plan' } }), 'setup approve');
      await expect401(await ctx.post('/api/v1/concierge/tasks', { headers, data: { kind: 'campaign' } }), 'request task');
      await expect401(
        await ctx.post('/api/v1/concierge/tasks/t-1/approve', { headers, data: { scope: 'x' } }),
        'approve task',
      );
      await expect401(await ctx.post('/api/v1/concierge/pause', { headers, data: {} }), 'pause');
      await expect401(await ctx.post('/api/v1/concierge/exit', { headers, data: {} }), 'exit');
      await expect401(await ctx.delete('/api/v1/concierge/transition', { headers }), 'cancel transition');
    } finally {
      await ctx.dispose();
    }
  });
});

test.describe('mesh-only concierge namespace (S-1, S-5: no browser shortcut)', () => {
  test('internal staff/template/approval routes never answer at the edge', async ({ request }) => {
    for (const path of [
      '/internal/v1/concierge/contracts',
      '/internal/v1/concierge/staff',
      '/internal/v1/concierge/templates',
      '/internal/v1/concierge/approvals/apr-1',
    ]) {
      const res = await request.get(path);
      expect(res.status(), `GET ${path} stays mesh-only`).toBe(404);
      const raw = await res.text();
      expect(raw, 'no contract data leaks').not.toContain('contractId');
      expect(raw, 'no tier data leaks').not.toContain('slaTier');
    }
    for (const path of [
      '/internal/v1/concierge/contracts',
      '/internal/v1/concierge/staff',
      '/internal/v1/concierge/templates',
      '/internal/v1/concierge/approvals/signed-email',
    ]) {
      const res = await request.post(path, { data: { userId: 'dev-user-1', role: 'owner', token: 'x' } });
      expect(res.status(), `POST ${path} stays mesh-only`).toBe(404);
    }
  });
});

test.describe('the concierge surface is a session-gated SPA route (BR-02)', () => {
  test('anonymous visits land on the sign-in step, never a fabricated board', async ({ page }) => {
    await page.goto('/app/concierge');
    await expect(page).toHaveURL(/\/app\/login/);
    // The sign-in step renders (the title is a styled card div, not a heading role).
    await expect(page.getByText('Choose a sign-in method to continue.')).toBeVisible();
    // No board, no contract card, no fabricated states before a session exists.
    await expect(page.getByRole('heading', { name: 'Concierge' })).toHaveCount(0);
    await expect(page.locator('[data-testid="concierge-board"]')).toHaveCount(0);
  });
});
