// Feature 05 - concierge managed service: the FULL staffed journey, end to end
// (opt-in). One client identity plus the agency side driven through the
// mesh-only internal routes with minted service JWTs - exactly the calls
// features 45-47 tooling makes; nothing is seeded behind the API's back.
//
//   cd e2e && npm run concierge:on   # idp:on wiring + zero-window SLA tier + account-service recreate
//   npm run test:concierge           # headless
//   npm run concierge:off && npm run idp:off   # restore afterwards
//
// Covers AC-01..AC-08 + BR-01..BR-12 on the reachable surfaces: staffing gate,
// setup-plan approval, dispatch approval-check contract, operator work with
// work refs, boundary refusals, signed-email approval with replay rejection,
// dual-confirmed pause/exit with handoff inventory, the SLA credit path, the
// invoicing/state event stream, and the client MFE surfaces.
//
// Budget per run: 2 dances = 4 auth ops (limit 20/min/IP) - run it a minute
// apart from the other suites (e2e/README.md).
import {
  expect,
  request as standaloneRequest,
  test,
  type APIRequestContext,
  type APIResponse,
  type Browser,
} from '@playwright/test';
import { createSign } from 'node:crypto';
import fs from 'node:fs';
import net from 'node:net';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const MOCK_ORIGIN = 'http://localhost:9099';
const ACCOUNT_DIRECT = process.env.E2E_ACCOUNT_DIRECT ?? 'http://localhost:8081';
const fullLogin = process.env.E2E_FULL_LOGIN === '1';
const conciergeMode = process.env.E2E_CONCIERGE === '1';

// One invocation shares one run id across worker restarts (the npm script sets
// E2E_CONCIERGE_RUN): a restarted worker must reuse the recorded seed instead
// of dancing again (auth envelope: 20 ops/min/IP, one dance = 2).
const RUN = process.env.E2E_CONCIERGE_RUN ?? String(Date.now());
const CLIENT_A = { sub: `e2e-concierge-a-${RUN}`, email: `e2e-concierge-a-${RUN}@example.test` };
const CLIENT_B = { sub: `e2e-concierge-b-${RUN}`, email: `e2e-concierge-b-${RUN}@example.test` };
const NAME = 'E2E Concierge Client';
// Composer default (CONCIERGE_OWNER_USER_IDS): the config-provisioned first owner.
const OWNER_SUB = 'concierge-owner-dev';
const OPERATOR_SUB = `e2e-concierge-operator-${RUN}`;
const OPERATOR_NAME = 'E2E Operator';
const STRANGER_SUB = `e2e-concierge-stranger-${RUN}`;
const REDIS = process.env.E2E_REDIS ?? 'localhost:6379';

const E2E_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const REPO_ROOT = path.resolve(E2E_DIR, '..');
const AUTH_A = path.join(E2E_DIR, '.auth', 'concierge.json');
const AUTH_B = path.join(E2E_DIR, '.auth', 'concierge-miss.json');
const SEED_RECORD = path.join(E2E_DIR, '.auth', 'concierge-seed.json');
const EMPTY_STATE = { cookies: [], origins: [] };

const SOW_A = [
  { kind: 'setup', volume: 1 },
  { kind: 'campaign', volume: 10 },
  { kind: 'post_batch', volume: 1 },
  { kind: 'automation', volume: 2 },
  { kind: 'report', volume: 2 },
];
const SOW_B = [
  { kind: 'setup', volume: 1 },
  { kind: 'campaign', volume: 2 },
  { kind: 'post_batch', volume: 25 },
];

interface SowItemDto {
  kind: string;
  volume: number | null;
}
interface SlaViewDto {
  tier: string;
  responseWindowMinutes: number;
  completionWindowMinutes: number;
  creditRule: string;
}
interface HandoffItemDto {
  key: string;
  at: string | null;
}
interface HandoffDto {
  startedAt: string | null;
  scheduledWork: string | null;
  items: HandoffItemDto[];
  completedAt: string | null;
}
interface ContractDto {
  id: string;
  status: string;
  slaTier: string;
  sow: SowItemDto[];
  sowVersion?: number;
  pendingTransition: string | null;
  activatedAt: string | null;
  exitedAt: string | null;
  sla?: SlaViewDto | null;
  handoff?: HandoffDto | null;
}
interface ApprovalDto {
  id: string;
  taskId: string | null;
  scope: string;
  channel: string;
  approvedAt: string;
}
interface TaskDto {
  id: string;
  kind: string;
  status: string;
  origin: string;
  requestedAt: string;
  approvedAt: string | null;
  completedAt: string | null;
  workRefs: string[];
  refusalReason: string | null;
  referral?: string | null;
  slaMiss?: string | null;
  notes?: string | null;
  operatorName?: string | null;
  approvals?: ApprovalDto[];
}

const seed: {
  ready: boolean;
  skip?: string;
  csrfA?: string;
  csrfB?: string;
  userIdA?: string;
  userIdB?: string;
} = { ready: false };

/**
 * Journey state shared across the ordered cases (workers=1). Playwright
 * restarts the worker after any failed test (and at the nested fixture group),
 * which silently resets in-process state and would cascade one real failure
 * into every later case - so the record is persisted next to the seed and
 * reloaded by the fresh worker, keyed by RUN.
 */
interface JourneyState {
  contract?: ContractDto;
  campaignTaskId?: string;
  campaignApprovalId?: string;
  scheduledTaskId?: string;
  contractB?: ContractDto;
  slaTaskId?: string;
}

const JOURNEY_RECORD = path.join(E2E_DIR, '.auth', 'concierge-journey.json');

function loadJourney(): JourneyState {
  try {
    const parsed = JSON.parse(fs.readFileSync(JOURNEY_RECORD, 'utf8')) as { run: string; state: JourneyState };
    return parsed.run === RUN ? parsed.state : {};
  } catch {
    return {};
  }
}

/** Write-through: every assignment survives the worker Playwright discards. */
const journey: JourneyState = new Proxy(loadJourney(), {
  set(target, prop, value: unknown) {
    (target as Record<string, unknown>)[prop as string] = value;
    fs.writeFileSync(JOURNEY_RECORD, JSON.stringify({ run: RUN, state: target }));
    return true;
  },
});

const csrfA = () => ({ 'X-CSRF-Token': seed.csrfA! });
const csrfB = () => ({ 'X-CSRF-Token': seed.csrfB! });

const api = (state: string) => standaloneRequest.newContext({ baseURL: baseUrl(), storageState: state });
const baseUrl = () => process.env.EDGE_URL ?? 'http://localhost';

interface PersistedSeed {
  run: string;
  csrfA: string;
  userIdA: string;
  csrfB: string;
  userIdB: string;
}

/** The recorded seed for *this* invocation, if one was already danced. */
function persistedSeed(): PersistedSeed | null {
  try {
    const parsed = JSON.parse(fs.readFileSync(SEED_RECORD, 'utf8')) as PersistedSeed;
    return parsed.run === RUN ? parsed : null;
  } catch {
    return null;
  }
}

/** A stored session still authorizes `/api/v1/me` (costs 0 auth-envelope ops). */
async function sessionLive(state: string): Promise<boolean> {
  if (!fs.existsSync(state)) return false;
  const ctx = await standaloneRequest.newContext({ baseURL: baseUrl(), storageState: state });
  try {
    return (await ctx.get('/api/v1/me')).status() === 200;
  } catch {
    return false;
  } finally {
    await ctx.dispose();
  }
}

/**
 * Both dances run once per invocation. The beforeAll hook re-fires when
 * Playwright restarts the worker (the nested client-B group swaps the
 * storage-state fixture), and the auth envelope is 20 ops/min/IP with one
 * dance costing 2 - so a restart must find the recorded seed and reuse it,
 * probing the stored sessions first (a dead session is re-danced; never a
 * silent skip).
 */
let seeding: Promise<void> | null = null;
function ensureSeeded(browser: Browser, baseURL: string): Promise<void> {
  seeding ??= (async () => {
    const recorded = persistedSeed();
    if (recorded && (await sessionLive(AUTH_A)) && (await sessionLive(AUTH_B))) {
      seed.csrfA = recorded.csrfA;
      seed.csrfB = recorded.csrfB;
      seed.userIdA = recorded.userIdA;
      seed.userIdB = recorded.userIdB;
      seed.ready = true;
      return;
    }
    const a = await dance(browser, baseURL, CLIENT_A, AUTH_A);
    if (!a.ok) {
      seed.skip = `account-service is not pointed at the mock IdP (got ${a.wiring}); run \`npm run concierge:on\` and retry`;
      return;
    }
    const b = await dance(browser, baseURL, CLIENT_B, AUTH_B);
    if (!b.ok) {
      seed.skip = `second dance failed (got ${b.wiring})`;
      return;
    }
    seed.csrfA = a.csrf;
    seed.csrfB = b.csrf;
    seed.userIdA = a.userId;
    seed.userIdB = b.userId;
    fs.writeFileSync(
      SEED_RECORD,
      JSON.stringify({ run: RUN, csrfA: a.csrf, userIdA: a.userId, csrfB: b.csrf, userIdB: b.userId }),
    );
    seed.ready = true;
  })();
  return seeding;
}

async function setNextUser(request: APIRequestContext, profile: { sub: string; email: string }) {
  const res = await request.post(`${MOCK_ORIGIN}/__control`, {
    data: { ...profile, name: NAME, emailVerified: true },
  });
  expect(res.ok(), 'mock IdP control call').toBe(true);
}

type DanceResult = { ok: true; csrf: string; userId: string } | { ok: false; wiring: string };

/** One real SSO dance in a throwaway context; persists the storage state. */
async function dance(browser: Browser, baseURL: string, user: { sub: string; email: string }, authFile: string): Promise<DanceResult> {
  const ctx = await browser.newContext({ baseURL, storageState: EMPTY_STATE });
  try {
    const signIn = await ctx.request.post('/api/auth/sign-in/social', {
      form: { provider: 'generic-oidc', callbackURL: '/app/concierge' },
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
    expect(cookies.find((c) => c.name === '__Host-rb_session')?.value, 'session cookie issued').toBeTruthy();
    const csrf = cookies.find((c) => c.name === 'csrf_token')?.value;
    expect(csrf, 'csrf double-submit cookie issued (platform baseline)').toBeTruthy();

    fs.mkdirSync(path.dirname(authFile), { recursive: true });
    await ctx.storageState({ path: authFile });

    const me = await ctx.request.get('/api/v1/me');
    expect(me.status(), 'fresh session authorizes /api/v1/me').toBe(200);
    const userId = ((await me.json()) as { id: string }).id;
    expect(userId, 'contracts are owner-scoped by the internal user id').toBeTruthy();
    return { ok: true, csrf: csrf!, userId };
  } finally {
    await ctx.close().catch(() => {});
  }
}

// --- Agency side: service JWTs exactly as the mesh signs them ----------------
// The dev keypair lives in .env (generated by `npm run idp:on`); the request
// filter verifies RS256 + aud=account-service. Read lazily so the suite skips
// (not fails) when the wiring is absent.

function devPrivateKey(): string | null {
  const envPath = path.join(REPO_ROOT, '.env');
  if (!fs.existsSync(envPath)) return null;
  const line = fs
    .readFileSync(envPath, 'utf8')
    .split(/\r?\n/)
    .find((l) => l.startsWith('INTERNAL_JWT_PRIVATE_KEY_PEM='));
  if (!line) return null;
  const value = line.slice('INTERNAL_JWT_PRIVATE_KEY_PEM='.length).trim().replace(/^"|"$/g, '');
  if (!value) return null;
  const pem = value.replace(/\\n/g, '\n');
  return pem.includes('BEGIN') ? pem : null;
}

function mintServiceJwt(sub: string): string {
  const pem = devPrivateKey();
  if (!pem) throw new Error('dev internal-JWT keypair missing: run `npm run idp:on`');
  const b64 = (part: object) => Buffer.from(JSON.stringify(part)).toString('base64url');
  const now = Math.floor(Date.now() / 1000);
  const header = b64({ alg: 'RS256', typ: 'JWT', kid: 'e0-dev-key' });
  const payload = b64({ sub, aud: 'account-service', iat: now, exp: now + 300 });
  const signer = createSign('RSA-SHA256');
  signer.update(`${header}.${payload}`);
  const signature = signer.sign(pem).toString('base64url');
  return `${header}.${payload}.${signature}`;
}

/**
 * Mesh call response surface. Playwright disposes an `APIResponse` together
 * with its context, and the helper closes the context before the caller reads
 * the body - so the response is snapshotted while the context is still alive.
 * Callbacks that only assert return nothing; their `undefined` passes through.
 */
interface SnapshotResponse {
  status(): number;
  ok(): boolean;
  json(): Promise<unknown>;
  text(): Promise<string>;
  headers(): Record<string, string>;
}

function isApiResponse(value: unknown): value is APIResponse {
  return typeof (value as APIResponse | undefined)?.status === 'function';
}

async function withServiceJwt<T>(
  sub: string,
  fn: (ctx: APIRequestContext) => Promise<T>,
): Promise<T extends APIResponse ? SnapshotResponse : T> {
  const ctx = await standaloneRequest.newContext({
    baseURL: ACCOUNT_DIRECT,
    extraHTTPHeaders: { Authorization: `Bearer ${mintServiceJwt(sub)}` },
  });
  try {
    const res = await fn(ctx);
    if (!isApiResponse(res)) return res as never;
    const status = res.status();
    const headers = res.headers();
    const body = await res.text();
    return {
      status: () => status,
      ok: () => status >= 200 && status < 300,
      json: async () => (body === '' ? null : (JSON.parse(body) as unknown)),
      text: async () => body,
      headers: () => headers,
    } as never;
  } finally {
    await ctx.dispose();
  }
}
const asOwner = <T,>(fn: (ctx: APIRequestContext) => Promise<T>) => withServiceJwt(OWNER_SUB, fn);
const asOperator = <T,>(fn: (ctx: APIRequestContext) => Promise<T>) => withServiceJwt(OPERATOR_SUB, fn);
const asStranger = <T,>(fn: (ctx: APIRequestContext) => Promise<T>) => withServiceJwt(STRANGER_SUB, fn);

// --- Event stream reads (the invoicing/scheduler signal, TL 03 events) -------

function respCommand(...args: string[]): Buffer {
  const parts = [`*${args.length}\r\n`];
  for (const arg of args) parts.push(`$${Buffer.byteLength(arg)}\r\n${arg}\r\n`);
  return Buffer.from(parts.join(''), 'utf8');
}

/** Raw RESP read (fire SELECT 7 + XRANGE events:account, collect reply text). */
async function eventStreamText(): Promise<string> {
  const [host, port] = REDIS.split(':');
  return new Promise<string>((resolve, reject) => {
    const socket = net.connect(Number(port ?? 6379), host);
    let buffer = '';
    const settle = (error?: Error) => {
      socket.destroy();
      error ? reject(error) : resolve(buffer);
    };
    socket.setTimeout(5000, () => settle(new Error(`redis ${host}:${port} timed out`)));
    socket.on('error', settle);
    socket.on('connect', () =>
      socket.write(Buffer.concat([respCommand('SELECT', '7'), respCommand('XRANGE', 'events:account', '-', '+')])),
    );
    socket.on('data', (chunk) => {
      buffer += chunk.toString('utf8');
      // The stream read is a nested array; collect until the socket goes quiet.
      clearTimeout(idle);
      idle = setTimeout(() => settle(), 400);
    });
    let idle: NodeJS.Timeout = setTimeout(() => settle(), 800);
  });
}

const contractBApproval = async (request: APIRequestContext, scope: string) =>
  request.post('/api/v1/concierge/approve', { data: { scope }, headers: csrfB() });

test.describe('concierge staffed journey (seeded session + agency powers)', () => {
  test.beforeAll(async ({ browser, baseURL }) => {
    if (!fullLogin || !conciergeMode) {
      seed.skip = 'opt-in suite: run `npm run concierge:on` once, then `npm run test:concierge` (see e2e/README.md)';
      return;
    }
    const probe = await standaloneRequest.newContext({ storageState: EMPTY_STATE });
    const health = await probe.get(`${MOCK_ORIGIN}/__health`).catch(() => null);
    await probe.dispose();
    if (!health?.ok()) {
      seed.skip = 'mock IdP not reachable on :9099; playwright starts it for E2E_FULL_LOGIN=1 runs';
      return;
    }
    if (!devPrivateKey()) {
      seed.skip = 'dev internal-JWT keypair missing in .env; run `npm run idp:on` and retry';
      return;
    }
    await ensureSeeded(browser, baseURL!);
  });

  test.beforeEach(() => {
    test.skip(!seed.ready, seed.skip ?? 'seeded sessions unavailable');
  });

  test.use({ storageState: AUTH_A });

  test('no plan yet: honest empty reads and the sales-assisted empty state (US-01, BR-01)', async ({
    request,
    page,
  }) => {
    const contract = await request.get('/api/v1/concierge');
    expect(contract.status()).toBe(200);
    expect(await contract.json(), 'TL 03: null when no draft exists').toBeNull();

    const tasks = await request.get('/api/v1/concierge/tasks');
    expect(tasks.status()).toBe(200);
    expect(await tasks.json()).toEqual([]);

    // Without a contract nothing can commit: the closed menu answers first (BR-01).
    const ask = await request.post('/api/v1/concierge/tasks', { data: { kind: 'campaign' }, headers: csrfA() });
    expect(ask.status()).toBe(404);
    expect(((await ask.json()) as { code?: string }).code).toBe('contract_not_found');
    for (const path of ['/api/v1/concierge/pause', '/api/v1/concierge/exit']) {
      const res = await request.post(path, { data: {}, headers: csrfA() });
      expect(res.status(), path).toBe(404);
      expect(((await res.json()) as { code?: string }).code).toBe('contract_not_found');
    }

    // BA 04: hidden until a draft exists - no fabricated board.
    await page.goto('/app/concierge');
    const empty = page.getByTestId('concierge-empty');
    await expect(empty).toBeVisible();
    await expect(empty).toContainText('No concierge plan yet');
    await expect(page.getByTestId('concierge-contract-card')).toHaveCount(0);
  });

  test('AC-05/BR-08: activation is refused while staffing is incomplete; nothing is invoiced', async ({
    request,
  }) => {
    // Deterministic register: deactivate templates, remove operators (the last
    // owner is guarded by last_owner_forbidden and stays).
    await asOwner(async (ctx) => {
      const templates = await ctx.get('/internal/v1/concierge/templates');
      expect(templates.status()).toBe(200);
      for (const t of (await templates.json()) as Array<{ id: string; active: boolean }>) {
        if (t.active) {
          const off = await ctx.patch(`/internal/v1/concierge/templates/${t.id}`, { data: { active: false } });
          expect(off.status()).toBe(200);
        }
      }
      const staff = await ctx.get('/internal/v1/concierge/staff');
      expect(staff.status()).toBe(200);
      for (const s of (await staff.json()) as Array<{ id: string; role: string; active: boolean }>) {
        if (s.active && s.role === 'operator') {
          const gone = await ctx.delete(`/internal/v1/concierge/staff/${s.id}`);
          expect(gone.status(), 'operators are removable').toBe(204);
        }
      }
    });

    // BA 03 step 1: the agency owner drafts the contract (client, SOW, tier).
    journey.contract = await asOwner(async (ctx) => {
      const created = await ctx.post('/internal/v1/concierge/contracts', {
        data: { userId: seed.userIdA, slaTier: 'rapid', sow: SOW_A },
      });
      expect(created.status(), 'agency owner creates the draft').toBe(201);
      const body = (await created.json()) as ContractDto;
      expect(body.status).toBe('draft');
      expect(body.sow.map((s) => s.kind)).toEqual(SOW_A.map((s) => s.kind));
      return body;
    });

    // BR-08 is a presence query at the activation endpoint, not runbook copy.
    const refused = await asOwner((ctx) =>
      ctx.patch(`/internal/v1/concierge/contracts/${journey.contract!.id}`, { data: { action: 'activate' } }),
    );
    expect(refused.status()).toBe(422);
    const problem = (await refused.json()) as { code?: string; detail?: string };
    expect(problem.code).toBe('staffing_incomplete');
    expect(problem.detail, 'internal-facing and specific').toMatch(/template|operator|owner/i);

    const after = await request.get('/api/v1/concierge');
    expect(((await after.json()) as ContractDto).status, 'no partial activation').toBe('draft');

    // Nothing is invoiced: the state event is the invoicing signal (TL 03).
    expect(await eventStreamText()).not.toContain(journey.contract!.id);
  });

  test('BR-08/BR-03: staffing + the approved setup plan gate activation; activation publishes the state event', async ({
    request,
    page,
  }) => {
    // Staffing register: >= 1 active template, >= 1 active operator, >= 1 owner.
    await asOwner(async (ctx) => {
      const owner = await ctx.post('/internal/v1/concierge/staff', {
        data: { userId: OWNER_SUB, role: 'owner', name: 'E2E Agency Owner' },
      });
      expect([201, 409], `owner registration stays idempotent across runs (${owner.status()})`).toContain(owner.status());
      const op = await ctx.post('/internal/v1/concierge/staff', {
        data: { userId: OPERATOR_SUB, role: 'operator', name: OPERATOR_NAME },
      });
      expect(op.status()).toBe(201);
      const tpl = await ctx.post('/internal/v1/concierge/templates', {
        data: { name: `e2e concierge template ${RUN}`, version: 1, menu: SOW_A.map((s) => s.kind) },
      });
      expect(tpl.status()).toBe(201);
    });

    // Staffed, but the setup plan is not approved yet: activation is refused (BR-03).
    const early = await asOwner((ctx) =>
      ctx.patch(`/internal/v1/concierge/contracts/${journey.contract!.id}`, { data: { action: 'activate' } }),
    );
    expect(early.status(), 'the setup plan must be approved before build').toBe(409);
    expect(((await early.json()) as { code?: string }).code).toBe('transition_conflict');

    // Client approves the setup plan and the SOW scope (BA 03 step 2, dashboard channel).
    const approval = await request.post('/api/v1/concierge/approve', {
      data: { scope: 'Setup plan: profile + page setup, first campaign' },
      headers: csrfA(),
    });
    expect(approval.status()).toBe(200);
    const approvalBody = (await approval.json()) as ApprovalDto;
    expect(approvalBody.channel).toBe('dashboard');
    expect(approvalBody.scope).toContain('Setup plan');

    const activated = await asOwner((ctx) =>
      ctx.patch(`/internal/v1/concierge/contracts/${journey.contract!.id}`, { data: { action: 'activate' } }),
    );
    expect(activated.status()).toBe(200);
    const active = (await activated.json()) as ContractDto;
    expect(active.status).toBe('active');
    expect(active.activatedAt).toBeTruthy();
    expect(active.sowVersion, 'SOW version noted (BA 04)').toBeGreaterThanOrEqual(1);
    expect(active.sla?.tier).toBe('rapid');
    journey.contract = active;

    // The activation event feeds invoicing + schedulers (TL 03 events).
    const events = await eventStreamText();
    expect(events).toContain('concierge.contract_state_changed.v1');
    expect(events, 'the state event names this contract').toContain(journey.contract.id);

    // Board seeding: the setup task lands requested (BA 03 step 4).
    const tasks = (await (await request.get('/api/v1/concierge/tasks')).json()) as TaskDto[];
    const setupTask = tasks.find((t) => t.kind === 'setup');
    expect(setupTask?.status, 'setup work is seeded on activation').toBe('requested');

    // UI: contract card + SOW panel render from server truth.
    await page.goto('/app/concierge');
    const card = page.getByTestId('concierge-contract-card');
    await expect(card).toBeVisible();
    await expect(card).toContainText('Active');
    await expect(card).toContainText('rapid');
    const sow = page.getByTestId('concierge-sow');
    await expect(sow).toContainText('campaign');
    await expect(sow).toContainText('10');
  });

  test('AC-08/BR-12: off-menu and over-volume asks are refused with guidance; a recorded SOW update unblocks', async ({
    request,
  }) => {
    // A kind outside the closed menu: refused with a referral path.
    const offMenu = await request.post('/api/v1/concierge/tasks', {
      data: { kind: 'billboard' },
      headers: csrfA(),
    });
    expect(offMenu.status()).toBe(422);
    const offBody = (await offMenu.json()) as { code?: string; detail?: string; referral?: string };
    expect(offBody.code).toBe('task_off_menu');
    expect(offBody.referral, 'refusal carries the referral path (BR-01)').toBeTruthy();

    // A menu kind the SOW did not pick is off-menu too (closed menu per contract).
    const unpicked = await request.post('/api/v1/concierge/tasks', {
      data: { kind: 'email' },
      headers: csrfA(),
    });
    expect(unpicked.status()).toBe(422);
    expect(((await unpicked.json()) as { code?: string }).code).toBe('task_off_menu');

    // Volume: within, then over (blocked with SOW-update guidance), then recorded update.
    const first = await request.post('/api/v1/concierge/tasks', {
      data: { kind: 'post_batch' },
      headers: csrfA(),
    });
    expect(first.status()).toBe(201);
    const over = await request.post('/api/v1/concierge/tasks', {
      data: { kind: 'post_batch' },
      headers: csrfA(),
    });
    expect(over.status()).toBe(422);
    const overBody = (await over.json()) as { code?: string; detail?: string };
    expect(overBody.code).toBe('volume_exceeded');
    expect(overBody.detail, 'SOW-update guidance (BA 05)').toMatch(/SOW|plan|volume/i);

    const updated = await asOwner((ctx) =>
      ctx.patch(`/internal/v1/concierge/contracts/${journey.contract!.id}`, {
        data: { action: 'update_sow', sow: SOW_A.map((s) => (s.kind === 'post_batch' ? { ...s, volume: 2 } : s)) },
      }),
    );
    expect(updated.status()).toBe(200);
    const updatedBody = (await updated.json()) as ContractDto;
    expect(updatedBody.sowVersion, 'a recorded SOW update bumps the version').toBeGreaterThan(journey.contract!.sowVersion ?? 1);

    const after = await request.post('/api/v1/concierge/tasks', {
      data: { kind: 'post_batch' },
      headers: csrfA(),
    });
    expect(after.status(), 'the recorded SOW update unblocks the request').toBe(201);
    journey.contract = updatedBody;
  });

  test('AC-02/BR-03/BR-04: unapproved work cannot start; approvals are immutable records (dispatch check)', async ({
    request,
  }) => {
    const created = await request.post('/api/v1/concierge/tasks', {
      data: { kind: 'campaign', notes: 'July launch' },
      headers: csrfA(),
    });
    expect(created.status()).toBe(201);
    const task = (await created.json()) as TaskDto;
    expect(task.status).toBe('requested');
    expect(task.origin).toBe('client');
    expect(task.notes).toBe('July launch');
    journey.campaignTaskId = task.id;

    // The gate is endpoint-verified, not operator discipline: unknown approvals 404.
    const unknown = await asOwner((ctx) => ctx.get('/internal/v1/concierge/approvals/apr-does-not-exist'));
    expect(unknown.status(), 'fail-closed: consumers treat non-200 as refuse').toBe(404);

    // Unapproved work cannot start.
    const early = await asOperator((ctx) =>
      ctx.post(`/internal/v1/concierge/tasks/${task.id}/status`, { data: { status: 'in_progress' } }),
    );
    expect(early.status()).toBe(409);
    expect(((await early.json()) as { code?: string }).code).toBe('transition_conflict');

    // Client approves once; the scope is recorded; re-approve returns the record.
    const approved = await request.post(`/api/v1/concierge/tasks/${task.id}/approve`, {
      data: { scope: 'Send the July launch batch as drafted' },
      headers: csrfA(),
    });
    expect(approved.status()).toBe(200);
    const approval = (await approved.json()) as ApprovalDto;
    expect(approval.channel).toBe('dashboard');
    expect(approval.scope).toContain('July launch');
    journey.campaignApprovalId = approval.id;

    const again = await request.post(`/api/v1/concierge/tasks/${task.id}/approve`, {
      data: { scope: 'different scope attempt' },
      headers: csrfA(),
    });
    expect(again.status()).toBe(200);
    expect(((await again.json()) as ApprovalDto).id, 'never edited, only superseded by a new record').toBe(approval.id);

    // The dispatch gate answers valid for an active contract, scope included.
    const check = await asOwner((ctx) => ctx.get(`/internal/v1/concierge/approvals/${approval.id}`));
    expect(check.status()).toBe(200);
    const checkBody = (await check.json()) as {
      valid: boolean;
      taskId: string;
      scope: string;
      contractStatus: string;
    };
    expect(checkBody.valid).toBe(true);
    expect(checkBody.taskId).toBe(task.id);
    expect(checkBody.scope).toContain('July launch');
    expect(checkBody.contractStatus).toBe('active');

    // The board shows the recorded approval (BA 04 approval history).
    const tasks = (await (await request.get('/api/v1/concierge/tasks')).json()) as TaskDto[];
    const row = tasks.find((t) => t.id === task.id)!;
    expect(row.status).toBe('approved');
    expect(row.approvedAt).toBeTruthy();
    expect(row.approvals?.[0]?.channel).toBe('dashboard');
  });

  test('AC-01/AC-07: operator work flows through the gated routes and lands in the client history', async ({
    request,
    page,
  }) => {
    const taskId = journey.campaignTaskId!;
    const started = await asOperator((ctx) =>
      ctx.post(`/internal/v1/concierge/tasks/${taskId}/status`, { data: { status: 'in_progress' } }),
    );
    expect(started.status()).toBe(200);
    expect(((await started.json()) as TaskDto).status).toBe('in_progress');

    const done = await asOperator((ctx) =>
      ctx.post(`/internal/v1/concierge/tasks/${taskId}/status`, {
        data: { status: 'done', workRefs: ['pub_campaign_batch_1'] },
      }),
    );
    expect(done.status()).toBe(200);
    const doneBody = (await done.json()) as TaskDto;
    expect(doneBody.status).toBe('done');
    expect(doneBody.completedAt).toBeTruthy();
    expect(doneBody.workRefs).toEqual(['pub_campaign_batch_1']);
    expect(doneBody.operatorName, 'names visible to the client (BA 04)').toBe(OPERATOR_NAME);

    // BR-05/NFR-05: the client history carries the action <= 5 min with its work reference.
    const tasks = (await (await request.get('/api/v1/concierge/tasks')).json()) as TaskDto[];
    const row = tasks.find((t) => t.id === taskId)!;
    expect(row.status).toBe('done');
    expect(Date.now() - new Date(row.completedAt!).getTime(), 'freshness <= 5 min').toBeLessThan(5 * 60_000);
    expect(row.workRefs).toEqual(['pub_campaign_batch_1']);

    // Monthly review entry (BR-05 step 5): the owner puts a report task on the board.
    // The client is explicit: the resolver's single-live-contract convenience is
    // ambiguous on a dev DB that still holds retired runs' contracts.
    const review = await asOwner((ctx) =>
      ctx.post('/internal/v1/concierge/tasks', {
        data: { userId: seed.userIdA, kind: 'report', notes: 'Monthly review' },
      }),
    );
    expect(review.status()).toBe(201);
    expect(((await review.json()) as TaskDto).origin).toBe('scheduled');

    // AC-01 backdoor: unassigned/non-staff callers cannot act; no service JWT at all is 401.
    const stranger = await asStranger((ctx) =>
      ctx.post(`/internal/v1/concierge/tasks/${taskId}/status`, { data: { status: 'in_progress' } }),
    );
    expect(stranger.status()).toBe(403);
    expect(((await stranger.json()) as { code?: string }).code).toBe('task_forbidden');

    const anonymous = await standaloneRequest.newContext({ baseURL: ACCOUNT_DIRECT });
    try {
      const probe = await anonymous.get('/internal/v1/concierge/contracts');
      expect(probe.status(), 'mesh identity check answers before any handler').toBe(401);
    } finally {
      await anonymous.dispose();
    }

    // UI: the work-log surface shows the operator action with its reference.
    await page.goto('/app/concierge');
    const log = page.getByTestId('concierge-work-log');
    await expect(log).toContainText('pub_campaign_batch_1');
  });

  test('AC-06/BR-07: boundary asks are refused with reasons and a referral path', async ({ request, page }) => {
    const asks: Array<[string, string]> = [
      ['boundary_billing', 'Update the card on file and charge the ad account directly.'],
      ['boundary_policy', 'Promise the platform that our next campaign will skip review.'],
      ['boundary_contact', 'Email my customer list directly to pitch the launch.'],
      ['boundary_content', 'Publish the sweepstakes copy with guaranteed-return claims.'],
    ];
    for (const [reason, notes] of asks) {
      const created = await request.post('/api/v1/concierge/tasks', {
        data: { kind: 'campaign', notes },
        headers: csrfA(),
      });
      expect(created.status()).toBe(201);
      const task = (await created.json()) as TaskDto;
      const refused = await asOperator((ctx) =>
        ctx.post(`/internal/v1/concierge/tasks/${task.id}/refuse`, {
          data: { reason, referral: 'https://help.example.test/concierge-boundaries' },
        }),
      );
      expect(refused.status()).toBe(200);
      const body = (await refused.json()) as TaskDto;
      expect(body.status).toBe('refused');
      expect(body.refusalReason).toBe(reason);
      expect(body.referral, 'referral where applicable').toBeTruthy();
    }

    // No billing-instrument capability exists on the concierge surface (S-4).
    for (const path of ['/api/v1/concierge/billing', '/api/v1/concierge/payment-methods']) {
      const probe = await request.get(path);
      expect([404, 405], `no billing route at ${path}`).toContain(probe.status());
    }

    // The refusal notice is visible on the task rows (BA 04).
    await page.goto('/app/concierge');
    const notice = page.getByTestId('concierge-refusal-notice').first();
    await expect(notice).toBeVisible();
    await expect(notice).toContainText("outside what we can do");
  });

  test('S-9/BR-04: signed-email confirmation approves once; replay of the signed link is rejected', async ({
    request,
  }) => {
    const created = await request.post('/api/v1/concierge/tasks', {
      data: { kind: 'report', notes: 'Report approved by email' },
      headers: csrfA(),
    });
    expect(created.status()).toBe(201);
    const task = (await created.json()) as TaskDto;

    const issued = await asOwner((ctx) =>
      ctx.post('/internal/v1/concierge/approvals/signed-email-link', { data: { taskId: task.id } }),
    );
    expect(issued.status()).toBe(201);
    const link = (await issued.json()) as { token: string; expiresAt: string };
    expect(link.token.split('.').length, 'the signed link is a JWT').toBe(3);
    expect(new Date(link.expiresAt).getTime() - Date.now(), 'short-lived (<= 15 min)').toBeLessThanOrEqual(15 * 60_000);

    const confirmed = await asOwner((ctx) =>
      ctx.post('/internal/v1/concierge/approvals/signed-email', { data: { token: link.token } }),
    );
    expect(confirmed.status()).toBe(200);
    const approval = (await confirmed.json()) as ApprovalDto;
    expect(approval.channel).toBe('signed_email');
    expect(approval.taskId).toBe(task.id);

    // Single-use jti: a replay is rejected with the stable code.
    const replay = await asOwner((ctx) =>
      ctx.post('/internal/v1/concierge/approvals/signed-email', { data: { token: link.token } }),
    );
    expect(replay.status()).toBe(422);
    expect(((await replay.json()) as { code?: string }).code).toBe('signature_invalid');

    const bogus = await asOwner((ctx) =>
      ctx.post('/internal/v1/concierge/approvals/signed-email', { data: { token: 'not.a.jwt' } }),
    );
    expect(bogus.status()).toBe(422);
  });

  test('AC-03/BR-10: pause is dual-confirmed; work halts at confirm; dispatch fails closed', async ({ request }) => {
    // Queue work first: request + approve an automation item.
    const created = await request.post('/api/v1/concierge/tasks', {
      data: { kind: 'automation', notes: 'IG welcome automation' },
      headers: csrfA(),
    });
    expect(created.status()).toBe(201);
    const task = (await created.json()) as TaskDto;
    const approved = await request.post(`/api/v1/concierge/tasks/${task.id}/approve`, {
      data: { scope: 'IG welcome automation copy' },
      headers: csrfA(),
    });
    expect(approved.status()).toBe(200);

    // Client requests pause: pending, and work continues until confirmed (BR-10).
    const pausing = await request.post('/api/v1/concierge/pause', { data: {}, headers: csrfA() });
    expect(pausing.status()).toBe(202);
    const pending = (await pausing.json()) as ContractDto;
    expect(pending.pendingTransition).toBe('pause');
    expect(pending.status).toBe('active');

    const ownerView = await asOwner((ctx) => ctx.get('/internal/v1/concierge/contracts'));
    expect(ownerView.status()).toBe(200);
    const seen = ((await ownerView.json()) as ContractDto[]).find((c) => c.id === journey.contract!.id);
    expect(seen?.pendingTransition, 'pending state is visible to the counterpart').toBe('pause');

    const duringPending = await asOperator((ctx) =>
      ctx.post(`/internal/v1/concierge/tasks/${task.id}/status`, { data: { status: 'in_progress' } }),
    );
    expect(duringPending.status(), 'work continues until the counterpart confirms').toBe(200);

    // Owner confirms: paused; work halts; the dispatch gate refuses.
    const paused = await asOwner((ctx) =>
      ctx.patch(`/internal/v1/concierge/contracts/${journey.contract!.id}`, { data: { action: 'confirm_pause' } }),
    );
    expect(paused.status()).toBe(200);
    const pausedBody = (await paused.json()) as ContractDto;
    expect(pausedBody.status).toBe('paused');
    // Nulls are omitted on the wire (platform JSON convention), so an absent
    // value means "none".
    expect(pausedBody.pendingTransition ?? null).toBeNull();

    const halted = await asOperator((ctx) =>
      ctx.post(`/internal/v1/concierge/tasks/${task.id}/status`, { data: { status: 'done', workRefs: ['x'] } }),
    );
    expect(halted.status(), 'queued concierge-origin work halts').toBe(409);

    const checkPaused = await asOwner((ctx) =>
      ctx.get(`/internal/v1/concierge/approvals/${journey.campaignApprovalId}`),
    );
    expect(((await checkPaused.json()) as { valid: boolean }).valid, 'paused -> dispatch refuses (fail-closed)').toBe(false);

    // Resume restores service and dispatch (owner-initiated counterpart).
    const resumed = await asOwner((ctx) =>
      ctx.patch(`/internal/v1/concierge/contracts/${journey.contract!.id}`, { data: { action: 'resume' } }),
    );
    expect(resumed.status()).toBe(200);
    expect(((await resumed.json()) as ContractDto).status).toBe('active');
    const checkActive = await asOwner((ctx) =>
      ctx.get(`/internal/v1/concierge/approvals/${journey.campaignApprovalId}`),
    );
    expect(((await checkActive.json()) as { valid: boolean }).valid).toBe(true);
  });

  test('AC-03/BR-10: the initiator can cancel a pending transition; nothing changes', async ({ request }) => {
    const pausing = await request.post('/api/v1/concierge/pause', { data: {}, headers: csrfA() });
    expect(pausing.status()).toBe(202);

    const canceled = await request.delete('/api/v1/concierge/transition', { headers: csrfA() });
    expect(canceled.status()).toBe(204);

    const now = (await (await request.get('/api/v1/concierge')).json()) as ContractDto;
    expect(now.pendingTransition ?? null).toBeNull();
    expect(now.status).toBe('active');

    // Cancelling with nothing pending is an honest 404.
    const again = await request.delete('/api/v1/concierge/transition', { headers: csrfA() });
    expect(again.status()).toBe(404);
  });

  test('AC-03/BR-10/BR-11: exit delivers the handoff inventory and severs agency links', async ({ request, page }) => {
    // Scheduled work is pending: the transfer-or-cancel decision is required.
    const scheduled = await request.post('/api/v1/concierge/tasks', {
      data: { kind: 'campaign', notes: 'Scheduled for next week' },
      headers: csrfA(),
    });
    expect(scheduled.status()).toBe(201);
    journey.scheduledTaskId = ((await scheduled.json()) as TaskDto).id;

    const exitAsk = await request.post('/api/v1/concierge/exit', { data: {}, headers: csrfA() });
    expect(exitAsk.status()).toBe(202);
    expect(((await exitAsk.json()) as ContractDto).pendingTransition).toBe('exit');

    const noDecision = await asOwner((ctx) =>
      ctx.patch(`/internal/v1/concierge/contracts/${journey.contract!.id}`, { data: { action: 'confirm_exit' } }),
    );
    expect(noDecision.status()).toBe(422);
    expect(((await noDecision.json()) as { detail?: string }).detail).toContain(
      'Choose what happens to your scheduled work',
    );

    const confirmed = await asOwner((ctx) =>
      ctx.patch(`/internal/v1/concierge/contracts/${journey.contract!.id}`, {
        data: { action: 'confirm_exit', scheduledWork: 'cancel', acknowledge: 'HANDOFF' },
      }),
    );
    expect(confirmed.status()).toBe(200);
    const handoff = (await confirmed.json()) as ContractDto;
    expect(handoff.handoff?.startedAt, 'handoff checklist starts at confirmation').toBeTruthy();
    expect(handoff.handoff?.scheduledWork, 'the client decision is recorded').toBe('cancel');

    const sealed = await asOwner((ctx) =>
      ctx.patch(`/internal/v1/concierge/contracts/${journey.contract!.id}`, { data: { action: 'complete_exit' } }),
    );
    expect(sealed.status()).toBe(200);
    const exited = (await sealed.json()) as ContractDto;
    expect(exited.status).toBe('exited');
    expect(exited.exitedAt).toBeTruthy();
    expect(exited.pendingTransition ?? null).toBeNull();
    expect(exited.handoff?.completedAt).toBeTruthy();
    const keys = (exited.handoff?.items ?? []).map((i) => i.key);
    expect(keys).toEqual(
      expect.arrayContaining(['credentials_inventory', 'ownership_confirmation', 'scheduled_work_decision', 'final_report']),
    );
    expect((exited.handoff?.items ?? []).every((i) => i.at), 'each item is timestamped').toBe(true);

    // Agency links sever: dispatch refuses and operator transitions are closed.
    const check = await asOwner((ctx) => ctx.get(`/internal/v1/concierge/approvals/${journey.campaignApprovalId}`));
    expect(((await check.json()) as { valid: boolean }).valid).toBe(false);
    const blocked = await asOperator((ctx) =>
      ctx.post(`/internal/v1/concierge/tasks/${journey.scheduledTaskId}/status`, { data: { status: 'done', workRefs: ['x'] } }),
    );
    expect(blocked.status()).toBe(409);

    // Exit event: invoicing stops (TL 03).
    const events = await eventStreamText();
    expect(events).toContain(journey.contract.id);
    expect(events).toContain('exited');

    // Client artifacts stay intact: history and work references keep resolving.
    const tasks = (await (await request.get('/api/v1/concierge/tasks')).json()) as TaskDto[];
    expect(tasks.find((t) => t.id === journey.campaignTaskId)?.workRefs).toEqual(['pub_campaign_batch_1']);

    // Approvals are closed on an inactive contract (BA 05).
    const late = await request.post(`/api/v1/concierge/tasks/${journey.scheduledTaskId}/approve`, {
      data: { scope: 'too late' },
      headers: csrfA(),
    });
    expect(late.status()).toBe(409);
    expect(((await late.json()) as { code?: string }).code).toBe('transition_conflict');

    // UI: exited chip + the handoff checklist with timestamps.
    await page.goto('/app/concierge');
    await expect(page.getByTestId('concierge-contract-card')).toContainText('Exited');
    const checklist = page.getByTestId('concierge-handoff');
    await expect(checklist).toBeVisible();
    await expect(checklist).toContainText('Credentials inventory');
    await expect(checklist).toContainText('Content ownership');
    await expect(checklist).toContainText('Final report');
  });

  test.describe('second client: SLA clock + request envelope (standard tier)', () => {
    test.use({ storageState: AUTH_B });

    test('AC-04/BR-06: a missed window surfaces the contract credit path, never silently', async ({
      request,
      page,
    }) => {
      // Owner drafts the standard-tier contract; the client approves; activation.
      const created = await asOwner((ctx) =>
        ctx.post('/internal/v1/concierge/contracts', { data: { userId: seed.userIdB, slaTier: 'standard', sow: SOW_B } }),
      );
      expect(created.status()).toBe(201);
      journey.contractB = (await created.json()) as ContractDto;

      const approve = await contractBApproval(request, 'Setup plan: profile setup + first campaign');
      expect(approve.status()).toBe(200);
      const active = await asOwner((ctx) =>
        ctx.patch(`/internal/v1/concierge/contracts/${journey.contractB!.id}`, { data: { action: 'activate' } }),
      );
      expect(active.status()).toBe(200);
      journey.contractB = (await active.json()) as ContractDto;

      const sla = journey.contractB.sla!;
      expect(sla.tier).toBe('standard');
      expect(sla.creditRule, 'the credit-rule key is config-owned, amount-free').toBeTruthy();
      test.skip(
        sla.responseWindowMinutes !== 0 || sla.completionWindowMinutes !== 0,
        'SLA miss drill needs the zero-window dev tier: run `npm run concierge:on` (see e2e/README.md)',
      );

      // The clock runs on task timestamps: unfulfilled past the response window.
      const requested = await request.post('/api/v1/concierge/tasks', {
        data: { kind: 'campaign', notes: 'Window drill' },
        headers: csrfB(),
      });
      expect(requested.status()).toBe(201);
      const task = (await requested.json()) as TaskDto;
      journey.slaTaskId = task.id;

      const afterRequest = (await (await request.get('/api/v1/concierge/tasks')).json()) as TaskDto[];
      const missedResponse = afterRequest.find((t) => t.id === task.id)!;
      expect(missedResponse.slaMiss, 'the elapsed response window surfaces on the board').toBe('response');

      const approved = await request.post(`/api/v1/concierge/tasks/${task.id}/approve`, {
        data: { scope: 'Run the campaign as drafted' },
        headers: csrfB(),
      });
      expect(approved.status()).toBe(200);

      const afterApprove = (await (await request.get('/api/v1/concierge/tasks')).json()) as TaskDto[];
      expect(afterApprove.find((t) => t.id === task.id)!.slaMiss, 'completion clock runs from approval').toBe(
        'completion',
      );

      // The board lifecycle is requested -> approved -> in_progress -> done.
      const started = await asOperator((ctx) =>
        ctx.post(`/internal/v1/concierge/tasks/${task.id}/status`, { data: { status: 'in_progress' } }),
      );
      expect(started.status()).toBe(200);

      // Fulfilment never erases the recorded miss (no silent remediation).
      const done = await asOperator((ctx) =>
        ctx.post(`/internal/v1/concierge/tasks/${task.id}/status`, { data: { status: 'done', workRefs: ['pub_sla_drill'] } }),
      );
      expect(done.status()).toBe(200);
      const afterDone = (await (await request.get('/api/v1/concierge/tasks')).json()) as TaskDto[];
      const fulfilled = afterDone.find((t) => t.id === task.id)!;
      expect(fulfilled.status).toBe('done');
      expect(fulfilled.slaMiss, 'the miss record stands').toBe('completion');

      // UI: SLA row states the windows, the miss, and the credit path (amount-free).
      await page.goto('/app/concierge');
      const slaRow = page.getByTestId('concierge-sla');
      await expect(slaRow).toBeVisible();
      await expect(slaRow).toContainText('We missed the');
      await expect(slaRow).toContainText('credit path');
      await expect(slaRow).not.toContainText('VND');
    });

    test('NFR-07: the client request envelope answers 429 + Retry-After (20/day/contract)', async ({ request }) => {
      // The SLA drill consumed request 1 of 20; fill the envelope, then breach.
      for (let i = 0; i < 19; i++) {
        const res = await request.post('/api/v1/concierge/tasks', {
          data: { kind: 'post_batch', notes: `envelope ${i + 2}` },
          headers: csrfB(),
        });
        expect(res.status(), `request ${i + 2} stays inside the envelope`).toBe(201);
      }
      const throttled = await request.post('/api/v1/concierge/tasks', {
        data: { kind: 'post_batch', notes: 'envelope 21' },
        headers: csrfB(),
      });
      expect(throttled.status()).toBe(429);
      expect(((await throttled.json()) as { code?: string }).code).toBe('throttled');
      expect(Number(throttled.headers()['retry-after']), 'Retry-After is stated').toBeGreaterThan(0);
    });
  });
});
