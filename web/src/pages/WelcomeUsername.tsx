import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { Button, Card, CardContent, CardDescription, CardHeader, CardTitle } from '@resume-builder/ui';

/** First-login handoff target (AC-13); the picker itself ships with feature 06. */
export default function WelcomeUsername() {
  const { t } = useTranslation();
  return (
    <main className="mx-auto w-full max-w-md">
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">{t('welcome.title')}</CardTitle>
          <CardDescription>{t('welcome.body')}</CardDescription>
        </CardHeader>
        <CardContent>
          <Button render={<Link to="/" />} className="w-full">
            {t('welcome.cta')}
          </Button>
        </CardContent>
      </Card>
    </main>
  );
}
