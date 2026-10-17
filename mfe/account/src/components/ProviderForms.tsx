import { useTranslation } from 'react-i18next';
import { csrfToken } from '@resume-builder/mfe-kit';
import { Button } from '@resume-builder/ui';

/**
 * Navigation-POST provider form for the step-up hand-off (BA 04): form POST is
 * the only way to let the server-issued 302 to the IdP govern. `csrf_token` is
 * the double-submit form half where the edge validates it.
 */
export function StepUpProviderForm({
  provider,
  label,
  returnTo,
  autoFocus = false,
}: {
  provider: string;
  label: string;
  returnTo: string;
  autoFocus?: boolean;
}) {
  const { t } = useTranslation();
  return (
    <form method="post" action="/api/auth/step-up">
      <input type="hidden" name="provider" value={provider} />
      <input type="hidden" name="returnTo" value={returnTo} />
      <input type="hidden" name="csrf_token" value={csrfToken()} />
      <Button type="submit" variant="outline" size="lg" className="w-full" autoFocus={autoFocus}>
        {t('login.continueWith', { provider: label })}
      </Button>
    </form>
  );
}
