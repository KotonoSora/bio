import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useLocation, useNavigate } from 'react-router';
import { isRelayEmail, isUnauthenticated } from '@resume-builder/mfe-kit';
import { Card, CardContent, CardHeader, CardTitle } from '@resume-builder/ui';
import { accountApi, type MeResponse } from '../api/account';
import { profilesApi } from '../api/client';

export default function Dashboard() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const [me, setMe] = useState<MeResponse | null>(null);
  const [profiles, setProfiles] = useState<{ id: string; username: string }[]>([]);

  useEffect(() => {
    accountApi
      .me()
      .then(setMe)
      .catch((err) => {
        if (isUnauthenticated(err)) {
          navigate(`/login?notice=expired&next=${encodeURIComponent(`/app${location.pathname}`)}`);
        }
      });
    profilesApi
      .mine()
      .then(setProfiles)
      .catch(() => setProfiles([]));
  }, [navigate, location.pathname]);

  // BA 03 login step 7/9: absent/relay IdP email still logs in and gets the
  // contact-email completion prompt (never blocked).
  const promptContactEmail =
    me !== null && !me.contactVerified && (!me.email || isRelayEmail(me.email));

  return (
    <main className="space-y-6">
      <h1 className="font-heading text-2xl font-semibold">{t('nav.dashboard')}</h1>
      <p className="text-sm text-muted-foreground">{me ? me.name || me.email || me.id : t('common.loading')}</p>
      {promptContactEmail && (
        <p className="text-sm text-amber-700">
          <Link to="/account/settings" className="underline underline-offset-4">
            {t('settings.promptAdd')}
          </Link>
        </p>
      )}
      {profiles.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>Profiles</CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="space-y-1 text-sm">
              {profiles.map((p) => (
                <li key={p.id}>{p.username}</li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}
    </main>
  );
}
