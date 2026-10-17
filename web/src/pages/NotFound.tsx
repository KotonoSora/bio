import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { Button } from '@resume-builder/ui';

export default function NotFound() {
  const { t } = useTranslation();
  return (
    <main className="mx-auto flex w-full max-w-md flex-col items-center gap-4 py-12 text-center">
      <h1 className="font-heading text-2xl font-semibold">{t('notFound.title')}</h1>
      <p className="text-sm text-muted-foreground">{t('notFound.body')}</p>
      <Button variant="link" render={<Link to="/" />}>
        {t('notFound.cta')}
      </Button>
    </main>
  );
}
