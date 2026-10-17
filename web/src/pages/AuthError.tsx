import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { Button } from '@resume-builder/ui';

/** Generic failure landing (BA 05): the same page answers callback failures and recovery invalid states. */
export default function AuthError() {
  const { t } = useTranslation();
  return (
    <main className="mx-auto flex w-full max-w-md flex-col items-center gap-4 py-12 text-center">
      <h1 className="font-heading text-2xl font-semibold">{t('authError.title')}</h1>
      <p className="text-sm text-muted-foreground">{t('authError.support')}</p>
      <Button render={<Link to="/login" />}>{t('authError.back')}</Button>
    </main>
  );
}
