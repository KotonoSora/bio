import { useTranslation } from 'react-i18next';
import { Button } from '@resume-builder/ui';

/**
 * Navigation-POST provider forms (BA 04): form POST is the only way to let the
 * server-issued 302 to the IdP govern. Sign-in and recovery initiation are
 * CSRF-exempt surfaces; the step-up variant ships with the account MFE.
 */
function Hidden({ name, value }: { name: string; value: string }) {
  return <input type="hidden" name={name} value={value} />;
}

function SubmitButton({ label }: { label: string }) {
  return (
    <Button type="submit" variant="outline" size="lg" className="w-full">
      {label}
    </Button>
  );
}

export function SignInProviderForm({
  provider,
  label,
  callbackURL,
}: {
  provider: string;
  label: string;
  callbackURL: string;
}) {
  const { t } = useTranslation();
  return (
    <form method="post" action="/api/auth/sign-in/social">
      <Hidden name="provider" value={provider} />
      <Hidden name="callbackURL" value={callbackURL} />
      <SubmitButton label={t('login.continueWith', { provider: label })} />
    </form>
  );
}

export function RecoveryProviderForm({
  token,
  provider,
  label,
}: {
  token: string;
  provider: string;
  label: string;
}) {
  const { t } = useTranslation();
  return (
    <form method="post" action={`/api/auth/recovery/${encodeURIComponent(token)}/link/${encodeURIComponent(provider)}`}>
      <SubmitButton label={t('login.continueWith', { provider: label })} />
    </form>
  );
}
