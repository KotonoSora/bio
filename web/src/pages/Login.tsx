import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router';
import { Card, CardContent, CardDescription, CardHeader, CardTitle, Notice } from '@resume-builder/ui';
import { authApi, type ProviderView } from '../api/account';
import { SignInProviderForm } from '../components/ProviderForms';

// Same allowlist the account-service enforces (BR-04); the server sanitizes
// again, this only keeps the hidden field well-formed.
const ALLOWED_PREFIXES = ['/app', '/welcome', '/account'];

export function safeCallbackURL(raw: string | null): string {
  if (!raw || !raw.startsWith('/') || raw.startsWith('//') || raw.includes('://') || raw.includes('\\')) return '/app';
  const allowed = ALLOWED_PREFIXES.some((p) => raw === p || raw.startsWith(`${p}/`) || raw.startsWith(`${p}?`));
  return allowed ? raw : '/app';
}

export default function Login() {
  const { t } = useTranslation();
  const [providers, setProviders] = useState<ProviderView[] | null>(null);
  const [params] = useSearchParams();
  const notice = params.get('notice');
  const callbackURL = safeCallbackURL(params.get('next'));

  useEffect(() => {
    authApi
      .providers()
      .then((r) => setProviders(r.providers))
      .catch(() => setProviders([]));
  }, []);

  return (
    <main className="mx-auto w-full max-w-sm">
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">{t('login.title')}</CardTitle>
          <CardDescription>{t('login.subtitle')}</CardDescription>
        </CardHeader>
        <CardContent>
          {notice === 'failed' && <Notice>{t('login.failed')}</Notice>}
          {notice === 'expired' && <Notice>{t('login.sessionEnded')}</Notice>}
          {providers === null ? (
            <p className="mt-3 text-sm text-muted-foreground">{t('common.loading')}</p>
          ) : providers.length === 0 ? (
            <p className="mt-3 text-sm text-muted-foreground">{t('login.unavailable')}</p>
          ) : (
            <div className="mt-3 space-y-2">
              {providers.map((p) => (
                <SignInProviderForm key={p.id} provider={p.id} label={p.label} callbackURL={callbackURL} />
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </main>
  );
}
