// The claim registry is the copy source of truth (feature 03 D-4/BR-11): the
// whole pricing surface renders from `plans-claims.json` patterns + server
// values; a hard-typed number in copy fails the CI drift drill, and a claim
// without its registry row fails the extra-claim drill.
import registry from '../../../../plans-claims.json' with { type: 'json' };

export interface ClaimRow {
  claimId: string;
  textPattern: string;
  enforcement: string;
  key: string;
}

export const claimRows = registry as ClaimRow[];

export type ClaimValues = Record<string, string | null | undefined>;

/** Substitute every `{token}` with its value; an unknown token is a contract bug. */
export function renderClaim(pattern: string, values: ClaimValues = {}): string {
  return pattern.replace(/\{(\w+)\}/g, (_, token: string) => {
    const value = values[token];
    if (value == null) throw new Error(`claim token {${token}} has no value (absence is rendered upstream, never substituted)`);
    return value;
  });
}

/**
 * Renders a registry row for the given claim. Values may stay empty for
 * token-free claims (BR-11: every sentence is registry-backed, exact).
 */
export function claim(claimId: string, values: ClaimValues = {}): string {
  const row = claimRows.find((r) => r.claimId === claimId);
  if (!row) throw new Error(`unknown claim id "${claimId}" - the registry is the copy source (BR-11)`);
  return renderClaim(row.textPattern, values);
}

export const CLAIM_IDS = {
  pricingHeading: 'pricing_heading',
  pricingSubtitle: 'pricing_subtitle',
  termMonthly: 'term_monthly',
  termYearly: 'term_yearly',
  tierFree: 'tier_free',
  tierPro: 'tier_pro',
  monthlyPrice: 'monthly_price',
  yearlyPrice: 'yearly_price',
  yearlySavingBadge: 'yearly_saving_badge',
  trialCta: 'trial_cta',
  pricesRefreshing: 'prices_refreshing',
} as const;
