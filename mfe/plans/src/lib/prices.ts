// BR-12/NFR-04: amounts travel as server-computed VND integers; the SPA only
// formats. No discount, proration, or percentage math exists client-side
// (CI prose-drills enforce it; the server owns the yearly math).
const vndFormatter = new Intl.NumberFormat('en', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 });

export function formatVnd(value: number): string {
  if (!Number.isInteger(value)) throw new Error('VND amounts are integers end to end (NFR-04)');
  return vndFormatter.format(value);
}

export type BillingTerm = 'monthly' | 'yearly';

/** Frozen payload shape (`PlansResponse`, TL 03) - naming is the contract (G4). */
export interface PlansResponse {
  version: string;
  tiers: PlanTier[];
  prices?: PlanPrices | null;
  trialDays?: number | null;
}

export interface PlanTier {
  id: string;
  name: string;
  claims: string[];
}

export interface PlanPrices {
  monthlyVnd: number;
  yearlyVnd: number;
  yearlyDiscountPct?: number | null;
}
