import { describe, expect, it } from 'vitest';
import { claim, claimRows, renderClaim } from './claims';
import { formatVnd } from './prices';

/**
 * Drift drills the page can run at its own pace (the CI job scripts/check-plan-claims.mjs
 * is the hard gate; these keep the substitution mechanics honest).
 */
describe('claim registry (BR-11)', () => {
  it('token-free claims render exactly as the pattern states', () => {
    expect(claim('pricing_heading')).toBe('Simple pricing');
    expect(claim('pricing_subtitle')).toBe('Two plans. Cancel anytime.');
    expect(claim('tier_free')).toBe('Free');
    expect(claim('tier_pro')).toBe('Pro');
  });

  it('keyed claims substitute the server value, never a literal (BR-03)', () => {
    const rendered = claim('yearly_saving_badge', { yearlyDiscountPct: '25' });
    expect(rendered).toBe('Save 25% with yearly billing');
    expect(claim('trial_cta', { trialDays: '7' })).toBe('Try Pro free for 7 days');
  });

  it('absent values throw instead of rendering a placeholder (BR-03, AC-09)', () => {
    expect(() => claim('yearly_saving_badge', {})).toThrow();
    expect(() => claim('trial_cta', {})).toThrow();
  });

  it('unknown tokens absent from the values map surface as contract bugs', () => {
    expect(() => renderClaim('Save {nopeNumber}%', {})).toThrow();
  });

  it('every row carries an enforcing spec path (AC-01)', () => {
    for (const row of claimRows) {
      expect(row.enforcement).toMatch(/^docs\//);
      expect(row.key.length).toBeGreaterThan(0);
    }
  });
});

describe('money display (BR-12, NFR-04)', () => {
  it('formats server-traveled VND integers', () => {
    expect(formatVnd(59_000)).toBe(new Intl.NumberFormat('en', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(59_000));
    expect(formatVnd(601_800)).toContain('601,800');
  });

  it('refuses non-integers - the server owns exact money', () => {
    expect(() => formatVnd(1.5)).toThrow();
  });
});
