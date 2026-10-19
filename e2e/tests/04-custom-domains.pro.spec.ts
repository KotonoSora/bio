// Feature 04 - custom domains: the FULL use case, end to end (opt-in).
//
// This drives the actual feature through the edge exactly as the product
// defines it (BA 03 "Serving"): add a hostname -> publish the TXT challenge
// -> verify DNS control -> the verified host serves the BOUND profile with
// canonical https://{host}/{username} -> remove -> neutral again. The
// default suites pin the reachable halves; this suite is the whole story.
//
// Two structural facts make the pro half unreachable in the default stack, so
// this suite seeds exactly what features 35/36 (monetize) and real DNS would
// provide, nothing more:
//   * plan: the entitlement snapshot cache Redis `rb:plan:{userId}` (db 4) is
//     written per run - the same cache the ITs seed (TL 04 D-3, fail-closed).
//   * DNS: profile-service resolves TXT via `DOMAINS_DOH_RESOLVER`; `npm run
//     domains:on` points it at e2e/mock-doh (started by playwright for
//     E2E_DOMAINS_PRO=1) and this suite registers the challenge answer through
//     the mock's /__control endpoint. A wrong or missing record still fails
//     closed - exactly the production resolver contract.
//
//   cd e2e && npm run domains:on    # idp:on wiring + mock DoH resolver
//   npm run test:domains-pro        # headless
//   npm run domains:off && npm run idp:off   # restore afterwards
//
// Budget per run: 1 dance = 2 auth ops (limit 20/min/IP) - run it a minute
// apart from the other suites (e2e/README.md).
import {
  expect,
  request as standaloneRequest,
  test,
  type APIRequestContext,
  type Browser,
  type Playwright,
} from '@playwright/test';
import fs from 'node:fs';
import net from 'node:net';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const MOCK_ORIGIN = 'http://localhost:9099';
const MOCK_DOH_ORIGIN = 'http://localhost:9097';
const fullLogin = process.env.E2E_FULL_LOGIN === '1';
const domainsPro = process.env.E2E_DOMAINS_PRO === '1';

const RUN = Date.now();
const USER = { sub: `e2e-domains-pro-${RUN}`, email: `e2e-domains-pro-${RUN}@example.test` };
const NAME = 'E2E Domains Pro User';
const USERNAME = `prodom${RUN}`.toLowerCase();
const HOST = `serve-${RUN}.e2e.test`;

const E2E_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const AUTH_FILE = path.join(E2E_DIR, '.auth', 'domains-pro.json');
const EMPTY_STATE = { cookies: [], origins: [] };

interface DomainResponse {
  hostname: string;
  displayHost: string;
  status: string;
  txtName: string | null;
  txtValue: string | null;
  verifiedAt: string | null;
  serving: boolean;
}

const seed: { ready: boolean; skip?: string; csrf?: string; userId?: string } = { ready: false };

const csrfHeaders = () => ({ 'X-CSRF-Token': seed.csrf! });

async function setNextUser(request: APIRequestContext, profile: { sub: string; email: string }) {
  const res = await request.post(`${MOCK_ORIGIN}/__control`, {
    data: { ...profile, name: NAME, emailVerified: true },
  });
  expect(res.ok(), 'mock IdP control call').toBe(true);
}

type DanceResult = { ok: true; csrf: string } | { ok: false; wiring: string };

/** One real SSO dance in a throwaway context; persists the storage state. */
async function dance(browser: Browser, baseURL: string): Promise<DanceResult> {
  const ctx = await browser.newContext({ baseURL, storageState: EMPTY_STATE });
  try {
    const signIn = await ctx.request.post('/api/auth/sign-in/social', {
      form: { provider: 'generic-oidc', callbackURL: '/app/account/domains' },
      maxRedirects: 0,
    });
    expect(signIn.status()).toBe(302);
    const authorizeUrl = signIn.headers()['location'] ?? '';
    if (!authorizeUrl.startsWith(MOCK_ORIGIN)) return { ok: false, wiring: authorizeUrl };

    await setNextUser(ctx.request, USER);
    const page = await ctx.newPage();
    await page.goto(authorizeUrl);
    await page.getByRole('button', { name: 'Approve sign-in' }).click();
    await page.waitForURL(/\/app(\/|$)/);

    const cookies = await ctx.cookies();
    expect(cookies.find((c) => c.name === '__Host-rb_session')?.value, 'session cookie issued').toBeTruthy();
    const csrf = cookies.find((c) => c.name === 'csrf_token')?.value;
    expect(csrf, 'csrf double-submit cookie issued (platform baseline)').toBeTruthy();

    fs.mkdirSync(path.dirname(AUTH_FILE), { recursive: true });
    await ctx.storageState({ path: AUTH_FILE });
    return { ok: true, csrf: csrf! };
  } finally {
    await ctx.close().catch(() => {});
  }
}

// profile-service reads its entitlement snapshot from Redis db 4 (TL 04 D-3);
// this suite seeds the exact `rb:plan:{userId}` key the ITs seed. Zero-dep RESP.
const REDIS = process.env.E2E_REDIS ?? 'localhost:6379';
const PLAN_DB = 4;

function respCommand(...args: string[]): Buffer {
  const parts = [`*${args.length}\r\n`];
  for (const arg of args) parts.push(`$${Buffer.byteLength(arg)}\r\n${arg}\r\n`);
  return Buffer.from(parts.join(''), 'utf8');
}

async function redisApply(commands: string[][]): Promise<void> {
  const [host, port] = REDIS.split(':');
  await new Promise<void>((resolve, reject) => {
    const socket = net.connect(Number(port ?? 6379), host);
    let buffer = '';
    let settled = false;
    const replies: string[] = [];
    const settle = (error?: Error) => {
      if (settled) return;
      settled = true;
      socket.destroy();
      error ? reject(error) : resolve();
    };
    socket.setTimeout(5000, () => settle(new Error(`redis ${host}:${port} timed out`)));
    socket.on('error', (error) => settle(error));
    socket.on('connect', () => socket.write(Buffer.concat(commands.map((c) => respCommand(...c)))));
    socket.on('data', (chunk) => {
      buffer += chunk.toString('utf8');
      let index: number;
      while ((index = buffer.indexOf('\r\n')) !== -1) {
        replies.push(buffer.slice(0, index));
        buffer = buffer.slice(index + 2);
      }
      if (replies.length >= commands.length) {
        const bad = replies.find((r) => r.startsWith('-'));
        settle(bad ? new Error(`redis error: ${bad}`) : undefined);
      }
    });
  });
}

const seedPlan = (userId: string) =>
  redisApply([
    ['SELECT', String(PLAN_DB)],
    ['SET', `rb:plan:${userId}`, 'pro', 'EX', '900'],
  ]);
const dropPlan = (userId: string) =>
  redisApply([
    ['SELECT', String(PLAN_DB)],
    ['DEL', `rb:plan:${userId}`],
  ]);

/** One cookieless visitor request on the custom host (no session, no cookies). */
async function visitorGet(playwright: Playwright, baseURL: string, urlPath: string) {
  const ctx = await playwright.request.newContext({ baseURL, storageState: EMPTY_STATE });
  try {
    const res = await ctx.get(urlPath, { headers: { Host: HOST }, maxRedirects: 0 });
    return { status: res.status(), headers: res.headers(), body: await res.text() };
  } finally {
    await ctx.dispose().catch(() => {});
  }
}

test.describe('custom domains full use case: add, prove DNS, serve, remove (seeded pro)', () => {
  test.beforeAll(async ({ browser, baseURL }) => {
    if (!fullLogin || !domainsPro) {
      seed.skip = 'opt-in suite: run `npm run domains:on` once, then `npm run test:domains-pro` (see e2e/README.md)';
      return;
    }
    const probe = await standaloneRequest.newContext({ storageState: EMPTY_STATE });
    const idpHealth = await probe.get(`${MOCK_ORIGIN}/__health`).catch(() => null);
    const dohHealth = await probe.get(`${MOCK_DOH_ORIGIN}/__health`).catch(() => null);
    await probe.dispose();
    if (!idpHealth?.ok()) {
      seed.skip = 'mock IdP not reachable on :9099; playwright starts it for E2E_FULL_LOGIN=1 runs';
      return;
    }
    if (!dohHealth?.ok()) {
      seed.skip = 'mock DoH not reachable on :9097; playwright starts it for E2E_DOMAINS_PRO=1 runs';
      return;
    }
    const primary = await dance(browser, baseURL!);
    if (!primary.ok) {
      seed.skip = `account-service is not pointed at the mock IdP (got ${primary.wiring}); run \`npm run domains:on\` and retry`;
      return;
    }
    seed.csrf = primary.csrf;

    // Seed what features 35/36 and real DNS would provide: the claimed
    // username (the serving address) and the plan snapshot the gate reads.
    const owner = await standaloneRequest.newContext({ baseURL, storageState: AUTH_FILE });
    try {
      const me = await owner.get('/api/v1/me');
      expect(me.status(), 'fresh session authorizes /api/v1/me').toBe(200);
      seed.userId = ((await me.json()) as { id: string }).id;
      expect(seed.userId, 'the plan key is the internal user id').toBeTruthy();

      const claim = await owner.post('/api/v1/profiles', {
        headers: csrfHeaders(),
        data: { username: USERNAME, displayName: NAME },
      });
      expect(claim.status(), 'username claimed: the address the host will serve').toBe(200);

      await seedPlan(seed.userId!);
    } finally {
      await owner.dispose();
    }
    seed.ready = true;
  });

  test.beforeEach(() => {
    test.skip(!seed.ready, seed.skip ?? 'seeded pro session unavailable');
  });

  test.use({ storageState: AUTH_FILE });

  test('add -> publish TXT -> verify -> the host serves the bound profile (AC-01..AC-04, BR-01, BR-04)', async ({
    request,
    playwright,
    baseURL,
  }) => {
    const add = await request.post('/api/v1/domains', { data: { hostname: HOST }, headers: csrfHeaders() });
    expect(add.status(), 'pro accounts add: 201 pending').toBe(201);
    const added = (await add.json()) as DomainResponse;
    expect(added.status).toBe('pending');
    expect(added.hostname).toBe(HOST);
    expect(added.txtName, 'the challenge name the owner publishes').toBe(`_rb-challenge.${HOST}`);
    expect(added.txtValue, 'the token is revealed exactly once (S-2)').toBeTruthy();
    expect(added.serving, 'a pending binding never serves').toBe(false);

    // The DNS act: publish `rb-verify=<token>` where verification will look.
    const published = await request.post(`${MOCK_DOH_ORIGIN}/__control`, {
      data: { name: added.txtName, value: `rb-verify=${added.txtValue}` },
    });
    expect(published.ok(), 'mock DoH accepted the TXT answer').toBe(true);

    const verify = await request.post(`/api/v1/domains/${HOST}/verify`, { data: {}, headers: csrfHeaders() });
    expect(verify.status()).toBe(200);
    const verified = (await verify.json()) as DomainResponse;
    expect(verified.status, 'DNS control proven: verified').toBe('verified');
    expect(verified.verifiedAt, 'the verification instant is recorded').toBeTruthy();
    expect(verified.serving, 'verified + pro: serving starts at the commit').toBe(true);
    expect(verified.txtValue, 'the spent token is never echoed again (S-2)').toBeNull();

    // BA 03 "Serving" step 1: the visitor opens the custom host.
    const served = await visitorGet(playwright, baseURL!, `/${USERNAME}`);
    expect(served.status).toBe(200);
    const bio = JSON.parse(served.body) as { username: string; canonical: string; render: string };
    expect(bio.username).toBe(USERNAME);
    expect(bio.canonical, 'canonical is rewritten to the custom host (BR-04)').toBe(`https://${HOST}/${USERNAME}`);
    expect(bio.render).toContain(`ssr-shell:${USERNAME}`);
  });

  test('every bio path on a verified host renders the BOUND profile, canonical included (BR-04)', async ({
    playwright,
    baseURL,
  }) => {
    // The requested slug never changes whose page this host serves: a verified
    // host renders the bound profile's SSR - one step of indirection that used
    // to leak the requested slug into the response.
    const stranger = await visitorGet(playwright, baseURL!, `/stranger-${RUN}`);
    expect(stranger.status).toBe(200);
    const body = JSON.parse(stranger.body) as { username: string; canonical: string };
    expect(body.username, 'the bound profile is the only page this host renders').toBe(USERNAME);
    expect(body.canonical).toBe(`https://${HOST}/${USERNAME}`);
  });

  test('removing the binding halts serving: the host is neutral again (BR-04, NFR-04)', async ({
    request,
    playwright,
    baseURL,
  }) => {
    const removed = await request.delete(`/api/v1/domains/${HOST}`, { headers: csrfHeaders() });
    expect(removed.status()).toBe(204);

    // The removal publishes domain.state_changed.v1; the gateway's consumer
    // drops its decision (the primary path, pinned in gateway
    // DomainStateConsumerIT) and the 30 s positive TTL is the documented
    // backstop - poll the full bound instead of a fixed sleep.
    let last: Awaited<ReturnType<typeof visitorGet>> | null = null;
    const deadline = Date.now() + 40_000;
    while (Date.now() < deadline) {
      last = await visitorGet(playwright, baseURL!, `/${USERNAME}`);
      if (last.status === 404) break;
      await new Promise((resolve) => setTimeout(resolve, 500));
    }

    expect(last?.status, 'serving halts after removal').toBe(404);
    expect(last?.body).toContain("This site isn't available.");
    expect(last?.headers['location'], 'no open-redirect surface (BR-11)').toBeUndefined();
    expect(last?.headers['cache-control'] ?? '', 'negative cache: 60 s (NFR-05)').toContain('max-age=60');
    expect(last?.headers['set-cookie'], 'the neutral page carries no cookies').toBeUndefined();
  });

  test.afterAll(async ({ baseURL }) => {
    if (!seed.ready) return;
    // Best-effort hygiene: never leave a plan snapshot or a binding behind.
    if (seed.userId) await dropPlan(seed.userId).catch(() => {});
    const owner = await standaloneRequest.newContext({ baseURL: baseURL!, storageState: AUTH_FILE });
    try {
      await owner.delete(`/api/v1/domains/${HOST}`, { headers: csrfHeaders() });
    } catch {
      /* the suite's last test already removed it */
    } finally {
      await owner.dispose();
    }
    const doh = await standaloneRequest.newContext();
    try {
      await doh.delete(`${MOCK_DOH_ORIGIN}/__control`);
    } catch {
      /* mock lifecycle is playwright's when it started it */
    } finally {
      await doh.dispose();
    }
  });
});
