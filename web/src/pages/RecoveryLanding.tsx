import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useParams } from 'react-router';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@resume-builder/ui';
import { authApi, type ProviderView } from '../api/account';
import { RecoveryProviderForm } from '../components/ProviderForms';

/** Single-use recovery invitation landing (BA 04): provider buttons only, never credentials. */
export default function RecoveryLanding() {
  const { t } = useTranslation();
  const { token = '' } = useParams();
  const [providers, setProviders] = useState<ProviderView[] | null>(null);

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
          <CardTitle className="text-lg">{t('recovery.title')}</CardTitle>
          <CardDescription>{t('recovery.body')}</CardDescription>
        </CardHeader>
        <CardContent>
          {providers === null ? (
            <p className="text-sm text-muted-foreground">{t('common.loading')}</p>
          ) : providers.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t('login.unavailable')}</p>
          ) : (
            <div className="space-y-2">
              {providers.map((p) => (
                <RecoveryProviderForm key={p.id} token={token} provider={p.id} label={p.label} />
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </main>
  );
}
