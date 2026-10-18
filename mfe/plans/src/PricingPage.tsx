import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError, isUnauthenticated } from '@resume-builder/mfe-kit';
import { Button, Notice } from '@resume-builder/ui';
import { plansApi, trialApi } from './api/plans';
import { CLAIM_IDS, claim, claimRows } from './lib/claims';
import { formatVnd, type BillingTerm, type PlansResponse } from './lib/prices';
import { useMfeContext } from './mfe-context';

export default function PricingPage() {
  const { navigate } = useMfeContext();
  const [plans, setPlans] = useState<PlansResponse | null>(null);
  const [term, setTerm] = useState<BillingTerm>('monthly');
  const [notice, setNotice] = useState<string | null>(null);
  const [trialBusy, setTrialBusy] = useState(false);
  const boot = useRef(false);

  useEffect(() => {
    if (boot.current) return; // StrictMode double-invoke guard
    boot.current = true;
    plansApi
      .plans()
      .then(setPlans)
      .catch(() => setPlans(null)); // Degrade honesty: absence, never invented rows (S-9).
  }, []);

  const startTrial = useCallback(async () => {
    setTrialBusy(true);
    try {
      await trialApi.start();
      // Feature 36 owns the effects; the panel rerenders server truth.
      setNotice(null);
    } catch (err) {
      if (isUnauthenticated(err)) {
        navigate(`/login?notice=trial&next=${encodeURIComponent('/app/pricing')}`);
        return;
      }
      if (err instanceof ApiError) setNotice(err.message);
      else setNotice(null);
    } finally {
      setTrialBusy(false);
    }
  }, [navigate]);

  const prices = plans?.prices ?? null;
  const discountPct = prices?.yearlyDiscountPct ?? null;

  return (
    <main className="mx-auto w-full max-w-3xl space-y-6">
      <header className="space-y-1">
        <h1 className="font-heading text-2xl font-semibold">{claim(CLAIM_IDS.pricingHeading)}</h1>
        <p className="text-sm text-muted-foreground">{claim(CLAIM_IDS.pricingSubtitle)}</p>
      </header>

      {notice && <Notice onDismiss={() => setNotice(null)}>{notice}</Notice>}

      {/* Term toggle (BA 04): monthly default; yearly prices only from catalog + config. */}
      <div role="group" aria-label="Billing term" className="inline-flex rounded-lg border p-1">
        <TermButton label={claim(CLAIM_IDS.termMonthly)} active={term === 'monthly'} onClick={() => setTerm('monthly')} />
        <TermButton label={claim(CLAIM_IDS.termYearly)} active={term === 'yearly'} onClick={() => setTerm('yearly')} />
      </div>

      {!plans && (
        <div className="space-y-3">
          <p>{claim(CLAIM_IDS.pricesRefreshing)}</p>
          <Button type="button" variant="outline" size="sm" onClick={() => void plansApi.plans().then(setPlans).catch(() => setPlans(null))}>
            Retry
          </Button>
        </div>
      )}

      {plans && (
        <div className="grid gap-4 md:grid-cols-2">
          {plans.tiers.map((tier) => (
            <TierCard
              key={tier.id}
              name={tier.name}
              claims={tier.claims}
              term={term}
              prices={tier.id === 'pro' ? prices : null}
              discountPct={tier.id === 'pro' ? discountPct : null}
              trialDays={tier.id === 'pro' ? (plans.trialDays ?? null) : null}
              trialBusy={trialBusy}
              onTrial={() => void startTrial()}
            />
          ))}
        </div>
      )}

      {plans && !prices && <p className="text-sm text-muted-foreground">{claim(CLAIM_IDS.pricesRefreshing)}</p>}
    </main>
  );
}

function TermButton({ label, active, onClick }: { label: string; active: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={onClick}
      className={
        'rounded-md px-3 py-1.5 text-sm font-medium transition-colors ' +
        (active ? 'bg-muted text-foreground' : 'text-muted-foreground hover:bg-muted hover:text-foreground')
      }
    >
      {label}
    </button>
  );
}

interface TierCardProps {
  name: string;
  claims: string[];
  term: BillingTerm;
  prices: { monthlyVnd: number; yearlyVnd: number } | null;
  discountPct: number | null;
  trialDays: number | null;
  trialBusy: boolean;
  onTrial: () => void;
}

/**
 * One tier card (BR-01: none may be omitted, none invented). Claim sentences
 * render through the registry; price and badge lines render only when the
 * server payload carries the values (absence is the design, BR-03).
 */
function TierCard({ name, claims, term, prices, discountPct, trialDays, trialBusy, onTrial }: TierCardProps) {
  const registryRows = claimRows.filter((r) => claims.includes(r.claimId));
  return (
    <article className="rounded-xl border p-4">
      <h2 className="font-heading text-lg font-semibold">{name}</h2>
      <ul className="mt-2 space-y-1 text-sm">
        {registryRows.map((row) => (
          <li key={row.claimId}>{row.textPattern}</li>
        ))}
      </ul>
      {name === 'Pro' && prices && (
        <div className="mt-3 space-y-2">
          {discountPct != null && (
            <p>
              {claim(CLAIM_IDS.yearlySavingBadge, { yearlyDiscountPct: String(discountPct) })}
            </p>
          )}
          <p className="font-medium">
            {claim(term === 'monthly' ? CLAIM_IDS.monthlyPrice : CLAIM_IDS.yearlyPrice, {
              price: formatVnd(term === 'monthly' ? prices.monthlyVnd : prices.yearlyVnd),
            })}
          </p>
        </div>
      )}
      {name === 'Pro' && trialDays != null && (
        <div className="mt-3">
          <Button type="button" onClick={onTrial} disabled={trialBusy}>
            {claim(CLAIM_IDS.trialCta, { trialDays: String(trialDays) })}
          </Button>
        </div>
      )}
    </article>
  );
}
