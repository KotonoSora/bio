import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { CopyIcon } from 'lucide-react';
import {
  Badge,
  Button,
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  Input,
  Label,
  Notice,
} from '@resume-builder/ui';
import { domainsApi, planApi, type DomainResponse, type PlanResponse } from './api/domains';
import ConfirmDialog from './components/ConfirmDialog';
import { describeError, isUnauthenticated } from './lib/errors';
import { useMfeContext } from './mfe-context';

/**
 * Domains settings (feature 04, T-09): the plan gate first (feature 03 read is
 * truth) - a free account sees the upgrade path, never a fake form (AC-09);
 * a pro account manages bindings: add (normalized hostname), TXT challenge
 * shown once per round, status chips, verify/recheck/regenerate, and a remove
 * dialog. Server codes drive every message (BA 05) - no client-invented gate.
 */
export default function DomainsSettings() {
  const { navigate } = useMfeContext();
  const { t } = useTranslation();
  const [plan, setPlan] = useState<PlanResponse | null>(null);
  const [domains, setDomains] = useState<DomainResponse[]>([]);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [hostname, setHostname] = useState('');
  const [busy, setBusy] = useState<string | null>(null);
  const [removing, setRemoving] = useState<DomainResponse | null>(null);
  const [copied, setCopied] = useState<string | null>(null);
  // One-time values (S-2): kept in memory for this mount only, never re-fetched.
  const [revealed, setRevealed] = useState<Record<string, string>>({});

  const toLogin = useCallback(
    () => navigate('/login?notice=expired&next=' + encodeURIComponent('/app/account/domains')),
    [navigate],
  );

  const refuse = useCallback(
    (err: unknown) => {
      if (isUnauthenticated(err)) {
        toLogin();
        return;
      }
      setError(describeError(err));
    },
    [toLogin],
  );

  const booted = useRef(false);
  useEffect(() => {
    if (booted.current) return; // StrictMode double-invoke guard
    booted.current = true;
    void (async () => {
      try {
        const p = await planApi.plan();
        setPlan(p);
        if (p.plan === 'pro') {
          setDomains(await domainsApi.list());
        }
      } catch (err) {
        refuse(err);
      }
    })();
  }, [refuse]);

  const replace = (updated: DomainResponse) => {
    setDomains((rows) => rows.map((r) => (r.hostname === updated.hostname ? updated : r)));
    // A round's fresh value is revealed exactly once (S-2).
    if (updated.txtValue) setRevealed((r) => ({ ...r, [updated.hostname]: updated.txtValue! }));
  };

  async function add() {
    setError(null);
    setBusy('add');
    try {
      const created = await domainsApi.add(hostname.trim());
      setDomains((rows) => [...rows, created]);
      if (created.txtValue) setRevealed((r) => ({ ...r, [created.hostname]: created.txtValue! }));
      setHostname('');
      setNotice(t('domains.added'));
    } catch (err) {
      refuse(err);
    } finally {
      setBusy(null);
    }
  }

  async function verify(host: string) {
    setError(null);
    setBusy(host);
    try {
      const updated = await domainsApi.verify(host);
      replace(updated);
      setNotice(updated.status === 'verified' ? t('domains.verifiedNotice') : t('domains.notYet'));
    } catch (err) {
      refuse(err);
    } finally {
      setBusy(null);
    }
  }

  async function recheck(host: string) {
    setError(null);
    setBusy(host);
    try {
      replace(await domainsApi.recheck(host));
      setNotice(t('domains.recheckedNotice'));
    } catch (err) {
      refuse(err);
    } finally {
      setBusy(null);
    }
  }

  async function regenerate(host: string) {
    setError(null);
    setBusy(host);
    try {
      replace(await domainsApi.reissue(host));
    } catch (err) {
      refuse(err);
    } finally {
      setBusy(null);
    }
  }

  async function remove(host: string) {
    setError(null);
    try {
      await domainsApi.remove(host);
      setDomains((rows) => rows.filter((r) => r.hostname !== host));
      setNotice(t('domains.removedNotice'));
    } catch (err) {
      refuse(err);
    } finally {
      setRemoving(null);
    }
  }

  async function copy(value: string) {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(value);
      window.setTimeout(() => setCopied((c) => (c === value ? null : c)), 1500);
    } catch {
      // Clipboard unavailable: the value stays selectable in place.
    }
  }

  if (!plan) {
    return (
      <main className="mx-auto w-full max-w-2xl">
        <h1 className="font-heading text-2xl font-semibold">{t('domains.title')}</h1>
        {error ? <Notice tone="error">{error}</Notice> : <p className="mt-4 text-sm text-muted-foreground">{t('common.loading')}</p>}
      </main>
    );
  }

  // AC-09 (UI half): the free tier shows the upgrade path instead of a fake
  // form - no add input, no verify controls, no fabricated rows.
  if (plan.plan !== 'pro') {
    return (
      <main className="mx-auto w-full max-w-2xl space-y-6">
        <Card>
          <CardHeader>
            <CardTitle as="h2">{t('domains.title')}</CardTitle>
            <CardDescription>{t('domains.free.hint')}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-2">
            <p className="text-sm font-medium">{t('domains.free.gate')}</p>
            <p>
              <a className="text-sm font-medium underline underline-offset-4" href="/app/pricing">
                {t('domains.free.seePlans')}
              </a>
            </p>
          </CardContent>
        </Card>
      </main>
    );
  }

  return (
    <main className="mx-auto w-full max-w-2xl space-y-6">
      <Card>
        <CardHeader>
          <CardTitle as="h2">{t('domains.title')}</CardTitle>
          <CardDescription>{t('domains.description')}</CardDescription>
          <CardAction>
            <Badge variant="outline">Pro</Badge>
          </CardAction>
        </CardHeader>
        <CardContent className="space-y-4">
          {notice && (
            <Notice onDismiss={() => setNotice(null)} dismissLabel={t('common.dismiss')}>
              {notice}
            </Notice>
          )}
          {error && <Notice tone="error">{error}</Notice>}

          <form
            className="space-y-2"
            onSubmit={(e) => {
              e.preventDefault();
              if (hostname.trim()) void add();
            }}
          >
            <Label htmlFor="domain-hostname">{t('domains.addLabel')}</Label>
            <div className="flex items-center gap-2">
              <Input
                id="domain-hostname"
                name="hostname"
                required
                value={hostname}
                placeholder={t('domains.addPlaceholder')}
                onChange={(e) => setHostname(e.target.value)}
              />
              <Button type="submit" disabled={busy !== null || !hostname.trim()}>
                {busy === 'add' ? t('domains.adding') : t('domains.add')}
              </Button>
            </div>
          </form>

          {domains.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t('domains.empty')}</p>
          ) : (
            <ul className="divide-y">
              {domains.map((d) => (
                <DomainRow
                  key={d.hostname}
                  domain={d}
                  revealedValue={revealed[d.hostname] ?? null}
                  busy={busy === d.hostname}
                  copied={copied}
                  onCopy={copy}
                  onVerify={() => void verify(d.hostname)}
                  onRecheck={() => void recheck(d.hostname)}
                  onRegenerate={() => void regenerate(d.hostname)}
                  onRemove={() => setRemoving(d)}
                />
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      {removing && (
        <ConfirmDialog
          title={t('domains.removeTitle', { host: removing.displayHost })}
          body={t('domains.removeBody')}
          confirmLabel={t('domains.remove')}
          danger
          onCancel={() => setRemoving(null)}
          onConfirm={() => void remove(removing.hostname)}
        />
      )}
    </main>
  );
}

function DomainRow({
  domain: d,
  revealedValue,
  busy,
  copied,
  onCopy,
  onVerify,
  onRecheck,
  onRegenerate,
  onRemove,
}: {
  domain: DomainResponse;
  revealedValue: string | null;
  busy: boolean;
  copied: string | null;
  onCopy: (value: string) => void;
  onVerify: () => void;
  onRecheck: () => void;
  onRegenerate: () => void;
  onRemove: () => void;
}) {
  const { t } = useTranslation();
  const live = d.status === 'verified';
  const chipVariant =
    d.status === 'verified' ? 'default' : d.status === 'suspended' ? 'destructive' : 'outline';
  const expires = d.challengeExpiresAt
    ? new Intl.DateTimeFormat('en', { dateStyle: 'medium' }).format(new Date(d.challengeExpiresAt))
    : null;

  return (
    <li className="space-y-3 py-4 text-sm">
      <div className="flex items-center justify-between gap-3">
        <span className="flex items-center gap-2">
          <span className="font-medium">{d.displayHost}</span>
          <Badge variant={chipVariant}>{t(`domains.status.${d.status}`)}</Badge>
          <Badge variant={d.serving ? 'secondary' : 'outline'}>
            {d.serving ? t('domains.servingOn') : t('domains.servingOff')}
          </Badge>
        </span>
        <Button type="button" variant="outline" size="sm" onClick={onRemove}>
          {t('domains.remove')}
        </Button>
      </div>

      {!live && d.txtName && (
        <div className="space-y-2 rounded-md border p-3">
          <p className="font-medium">{t('domains.txtTitle')}</p>
          <dl className="grid gap-1 text-xs">
            <div className="flex items-center gap-2">
              <dt className="w-16 text-muted-foreground">{t('domains.txtName')}</dt>
              <dd className="font-mono">{d.txtName}</dd>
            </div>
            <div className="flex items-center gap-2">
              <dt className="w-16 text-muted-foreground">{t('domains.txtValue')}</dt>
              <dd className="font-mono">
                {revealedValue ?? t('common.dash')}
                {revealedValue && (
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    className="ml-2"
                    onClick={() => onCopy(revealedValue)}
                  >
                    <CopyIcon />
                    {copied === revealedValue ? t('domains.copied') : t('domains.copy')}
                  </Button>
                )}
              </dd>
            </div>
          </dl>
          <p className="text-xs text-muted-foreground">{t('domains.txtOnce')}</p>
          {d.challengeExpired ? (
            <p className="text-xs text-amber-700">{t('domains.challengeExpired')}</p>
          ) : (
            expires && <p className="text-xs text-muted-foreground">{t('domains.expires', { date: expires })}</p>
          )}
          {!!d.attempts && d.attempts > 0 && (
            <p className="text-xs text-muted-foreground">{t('domains.attempts', { count: d.attempts })}</p>
          )}
        </div>
      )}

      <div className="flex flex-wrap gap-2">
        {!live && (
          <Button type="button" size="sm" disabled={busy} onClick={onVerify}>
            {busy ? t('domains.verifying') : t('domains.verify')}
          </Button>
        )}
        {live && (
          <Button type="button" size="sm" variant="outline" disabled={busy} onClick={onRecheck}>
            {t('domains.recheck')}
          </Button>
        )}
        {!live && (
          <Button type="button" size="sm" variant="outline" disabled={busy} onClick={onRegenerate}>
            {t('domains.regenerate')}
          </Button>
        )}
      </div>
    </li>
  );
}
