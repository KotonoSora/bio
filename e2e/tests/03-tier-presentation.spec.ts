// Feature 03 - tier presentation e2e (default set) against the compose stack
// through the edge. Covers the public surfaces: the frozen `GET /api/v1/plans`
// contract, the pricing page rendering (tiers, claims, term toggle, saving
// badge, trial CTA), the absence rules, the claim-trace drill (registry <>
// rendered copy), and the first gate on the account plan read.
// Case catalog: e2e/README.md. Runbook: e2e/README.md.
import { expect, test } from '@playwright/test';
import registry from '../../plans-claims.json' with { type: 'json' };

interface PlanTier {
  id: string;
  name: string;
  claims: string[];
}

interface PlansPrices {
  monthlyVnd: number;
  yearlyVnd: number;
  yearlyDiscountPct?: number | null;
}

interface PlansResponse {
  version: string;
  tiers: PlanTier[];
  prices?: PlansPrices | null;
  trialDays?: number | null;
}

const CLAIM_KEYS = new Set([
  'yearlyDiscountPct',
  'trialDays',
  'profiles.limit',
  'plansResponse.version',
  'plansResponse.tiers',
  'plansResponse.prices',
  'plansResponse.trialDays',
]);



/** Same formatting the SPA applies (BR-12: the SPA formats, never computes). */
const formatVnd = (value: number) =>
  new Intl.NumberFormat('en', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(value);

/** BR-04 half-up, integer-only yearly math; 12x monthly when no discount is set. */
const expectedYearly = (monthlyVnd: number, pct?: number | null) =>
  pct == null ? monthlyVnd * 12 : monthlyVnd * 12 - Math.floor((monthlyVnd * 12 * pct + 50) / 100);

const claimRow = (claimId: string) => {
  const row = registry.find((r) => r.claimId === claimId);
  expect(row, `registry row for ${claimId}`).toBeTruthy();
  return row!;
};

async function readPlans(request: { get: (url: string) => Promise<import('@playwright/test').APIResponse> }): Promise<{
  body: PlansResponse;
  res: import('@playwright/test').APIResponse;
}> {
  const res = await request.get('/api/v1/plans');
  return { body: (await res.json()) as PlansResponse, res };
}

test.describe('public plans contract', () => {
  test('payload is the frozen shape, cache-first, no per-user data (T-04, S-7)', async ({ request }) => {
    const { body, res } = await readPlans(request);
    expect(res.status()).toBe(200);
    expect(res.headers()['content-type']).toContain('application/json');
    expect(res.headers()['cache-control']).toContain('public');
    expect(res.headers()['cache-control']).toContain('max-age=300');
    expect(res.headers()['set-cookie'], 'public read carries no cookies').toBeUndefined();

    expect(body.version, 'catalog version id for cache keys').toBeTruthy();
    expect(body.tiers.map((t) => t.id)).toEqual(['free', 'pro']);
    expect(body.tiers.map((t) => t.name)).toEqual(['Free', 'Pro']);
    for (const tier of body.tiers) {
      expect(tier.claims.length, `${tier.id} carries claim ids`).toBeGreaterThan(0);
      for (const claimId of tier.claims) {
        expect(claimRow(claimId), `claim ${claimId} resolves through the registry`).toBeTruthy();
      }
    }
    const raw = JSON.stringify(body);
    for (const personal of ['userId', 'user_id', 'email', 'nextAmountVnd']) {
      expect(raw, `public payload never carries ${personal}`).not.toContain(personal);
    }
  });

  test('yearly math is server-computed to the exact VND within the 44% ceiling (AC-03, BR-04, NFR-04)', async ({
    request,
  }) => {
    const { body } = await readPlans(request);
    const pct = body.prices?.yearlyDiscountPct ?? null;
    if (pct != null) {
      expect(Number.isInteger(pct)).toBe(true);
      expect(pct, 'yearlyDiscountPct <= 44 guardrail').toBeGreaterThanOrEqual(0);
      expect(pct).toBeLessThanOrEqual(44);
    }
    if (body.prices) {
      const { monthlyVnd, yearlyVnd } = body.prices;
      expect(Number.isInteger(monthlyVnd) && Number.isInteger(yearlyVnd), 'VND integers end to end').toBe(true);
      expect(yearlyVnd, 'yearly = 12x monthly minus the configured discount, to the VND').toBe(
        expectedYearly(monthlyVnd, pct),
      );
    }
    if (body.trialDays != null) {
      expect(Number.isInteger(body.trialDays)).toBe(true);
      expect(body.trialDays).toBeGreaterThan(0);
    }
  });

  test('the edge serves the public plans read cache-first within the 300 s bound (NFR-01, NFR-02)', async ({
    request,
  }) => {
    const first = await request.get('/api/v1/plans');
    const second = await request.get('/api/v1/plans');
    expect(first.status()).toBe(200);
    expect(second.status()).toBe(200);
    expect(second.headers()['x-cache-status'], 'edge cache answers the repeat read').toBe('HIT');
    expect(await second.json()).toEqual(await first.json());
  });

  test('credentials never personalize the public read (S-7)', async ({ request }) => {
    const anonymous = (await readPlans(request)).body;
    const authed = await request.get('/api/v1/plans', {
      headers: { Cookie: 'rb_session=not-a-real-session', Authorization: 'Bearer tampered' },
    });
    expect(authed.status()).toBe(200);
    expect((await authed.json()) as PlansResponse).toEqual(anonymous);
  });
});

test.describe('pricing page', () => {
  test('renders two tiers with registry-backed claims and the term toggle (AC-01, BR-01, BR-02)', async ({
    page,
    request,
  }) => {
    const { body } = await readPlans(request);
    await page.goto('/pricing');
    await expect(page).toHaveURL(/\/app\/pricing$/);

    await expect(page.getByRole('heading', { name: 'Simple pricing', exact: true })).toBeVisible();
    await expect(page.getByText(claimRow('pricing_subtitle').textPattern, { exact: true })).toBeVisible();

    // Exactly two tier cards; no third tier is presented (BR-01).
    const cards = page.getByRole('article');
    await expect(cards).toHaveCount(2);
    await expect(page.getByRole('heading', { name: 'Free', exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Pro', exact: true })).toBeVisible();

    // The term toggle defaults to monthly and carries both labels (BR-02).
    const toggle = page.getByRole('group', { name: 'Billing term' });
    await expect(toggle.getByRole('button', { name: 'Monthly' })).toHaveAttribute('aria-pressed', 'true');
    await expect(toggle.getByRole('button', { name: 'Yearly' })).toHaveAttribute('aria-pressed', 'false');

    // Every claim id the catalog serves renders from the registry (BR-11).
    for (const tier of body.tiers) {
      const card = cards.filter({ has: page.getByRole('heading', { name: tier.name, exact: true }) });
      for (const claimId of tier.claims) {
        await expect(card.getByText(claimRow(claimId).textPattern, { exact: true })).toBeVisible();
      }
      // No hand-typed numbers in claim copy: digits only ever come from payload values (BR-03).
      const copy = (await card.locator('li').allInnerTexts()).join(' ');
      expect(copy, `${tier.id} claims carry no literals`).not.toMatch(/\d/);
    }
  });

  test('prices render from the server payload to the exact VND; toggle swaps monthly/yearly (BR-12, NFR-04)', async ({
    page,
    request,
  }) => {
    const { body } = await readPlans(request);
    await page.goto('/app/pricing');
    const proCard = page.getByRole('article').filter({ has: page.getByRole('heading', { name: 'Pro', exact: true }) });

    if (!body.prices) {
      // Degrade honesty: no prices published -> absence + a soft retry line (TL 05).
      await expect(page.getByText(claimRow('prices_refreshing').textPattern, { exact: true })).toBeVisible();
      await expect(proCard.getByText(/per (month|year)/)).toHaveCount(0);
      return;
    }

    const monthly = `${formatVnd(body.prices.monthlyVnd)} per month`;
    const yearly = `${formatVnd(body.prices.yearlyVnd)} per year`;
    await expect(proCard.getByText(monthly, { exact: true })).toBeVisible();
    expect(monthly).not.toBe(yearly);
    await expect(page.getByText(claimRow('prices_refreshing').textPattern, { exact: true })).toHaveCount(0);

    const toggle = page.getByRole('group', { name: 'Billing term' });
    await toggle.getByRole('button', { name: 'Yearly' }).click();
    await expect(toggle.getByRole('button', { name: 'Yearly' })).toHaveAttribute('aria-pressed', 'true');
    await expect(proCard.getByText(yearly, { exact: true })).toBeVisible();
    await expect(proCard.getByText(monthly, { exact: true })).toHaveCount(0);

    await toggle.getByRole('button', { name: 'Monthly' }).click();
    await expect(proCard.getByText(monthly, { exact: true })).toBeVisible();
  });

  test('absence drill: unset config omits badge and trial CTA, page otherwise complete (AC-09, BR-03)', async ({
    page,
    request,
  }) => {
    const { body } = await readPlans(request);
    const pct = body.prices?.yearlyDiscountPct ?? null;
    const days = body.trialDays ?? null;
    await page.goto('/app/pricing');

    const badge = claimRow('yearly_saving_badge');
    const cta = claimRow('trial_cta');
    if (pct == null) {
      await expect(page.getByText(/Save .*%/)).toHaveCount(0);
    } else {
      const rendered = badge.textPattern.replace('{yearlyDiscountPct}', String(pct));
      await expect(page.getByText(rendered, { exact: true })).toBeVisible();
    }
    if (days == null) {
      await expect(page.getByRole('button', { name: /Try Pro free/ })).toHaveCount(0);
    } else {
      const rendered = cta.textPattern.replace('{trialDays}', String(days));
      await expect(page.getByRole('button', { name: rendered })).toBeVisible();
    }
    // Absence is never a placeholder: the page still carries both tiers and claims.
    await expect(page.getByRole('article')).toHaveCount(2);
    await expect(page.getByRole('heading', { name: 'Simple pricing', exact: true })).toBeVisible();
  });

  test('claim-trace drill: every registry sentence resolves to rendered copy + an enforcement point (AC-01)', async ({
    page,
    request,
  }) => {
    const { body } = await readPlans(request);
    const pct = body.prices?.yearlyDiscountPct ?? null;
    await page.goto('/app/pricing');
    // Mount readiness: count() evaluates synchronously, so gate the drill on
    // the first registry sentence with real auto-waiting before counting.
    await expect(
      page.getByRole('heading', { level: 1, name: claimRow('pricing_heading').textPattern, exact: true }),
    ).toBeVisible();

    // Registry integrity: every row names an enforcing spec + a config/catalog key.
    for (const row of registry) {
      expect(row.enforcement, `${row.claimId} enforcement path`).toMatch(/^docs\//);
      expect(CLAIM_KEYS.has(row.key), `${row.claimId} keys a known config/catalog field`).toBe(true);
    }

    // Pass 1 (monthly term): literal claims + the monthly price line.
    const monthlyValues: Record<string, string | null> = {
      '{price}': body.prices ? formatVnd(body.prices.monthlyVnd) : null,
    };
    for (const row of registry) {
      if (row.claimId === 'yearly_price' || row.claimId === 'yearly_saving_badge') continue;
      if (row.claimId === 'trial_cta') continue;
      // The soft retry line is itself the price-absence signal: when prices
      // render it must be gone; without prices it must render (BR-03, TL 05).
      if (row.claimId === 'prices_refreshing' && body.prices) continue;
      const tokens = row.textPattern.match(/\{\w+\}/g) ?? [];
      // Claims whose value is unset today are absent by design, never placeholders (BR-03).
      if (tokens.some((token) => monthlyValues[token] == null)) continue;
      const rendered = row.textPattern.replace(/\{\w+\}/g, (token) => monthlyValues[token]!);
      expect(
        await page.getByText(rendered, { exact: true }).count(),
        `${row.claimId} renders "${rendered}"`,
      ).toBeGreaterThan(0);
    }

    // Pass 2 (yearly term): the yearly price line, the saving badge, and the trial CTA.
    await page.getByRole('group', { name: 'Billing term' }).getByRole('button', { name: 'Yearly' }).click();
    const yearlyValues: Record<string, string | null> = {
      '{price}': body.prices ? formatVnd(body.prices.yearlyVnd) : null,
      '{yearlyDiscountPct}': pct == null ? null : String(pct),
      '{trialDays}': body.trialDays == null ? null : String(body.trialDays),
    };
    for (const claimId of ['yearly_price', 'yearly_saving_badge', 'trial_cta']) {
      const row = claimRow(claimId);
      const tokens = row.textPattern.match(/\{\w+\}/g) ?? [];
      if (tokens.some((token) => yearlyValues[token] == null)) continue;
      const rendered = row.textPattern.replace(/\{\w+\}/g, (token) => yearlyValues[token]!);
      expect(
        await page.getByText(rendered, { exact: true }).count(),
        `${row.claimId} renders "${rendered}"`,
      ).toBeGreaterThan(0);
    }
  });
});

test.describe('routing and guards', () => {
  test('the edge routes /pricing to the SPA and the account plan read stays authenticated (S-1)', async ({
    request,
  }) => {
    const entry = await request.get('/pricing', { maxRedirects: 0 });
    expect(entry.status()).toBe(302);
    expect(entry.headers()['location']).toBe('/app/pricing');

    const plan = await request.get('/api/v1/plan');
    expect(plan.status()).toBe(401);
    const body = (await plan.json()) as { code?: string };
    expect(body.code).toBe('unauthenticated');
  });

  test('gates trust the server snapshot only: client plan flags are ignored (BR-09, S-1)', async ({ request }) => {
    const plain = await request.get('/api/v1/plan');
    const tampered = await request.get('/api/v1/plan?plan=pro', {
      headers: { 'X-Plan': 'pro', 'X-Entitlement-Plan': 'pro' },
    });
    expect(tampered.status()).toBe(plain.status());
  });
});
