import { useTranslation } from 'react-i18next';
import { Button, Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@resume-builder/ui';
import { StepUpProviderForm } from './ProviderForms';
import type { ProviderView } from '../api/account';

/**
 * BR-07 re-auth dialog: names the pending sensitive action, states the 10-min
 * freshness window, and reuses the login provider set. On cancel the caller
 * clears the parked action (nothing executes partially).
 */
export default function StepUpDialog({
  providers,
  actionLabel,
  returnTo,
  onCancel,
}: {
  providers: ProviderView[];
  actionLabel: string;
  returnTo: string;
  onCancel: () => void;
}) {
  const { t } = useTranslation();
  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) onCancel();
      }}
    >
      <DialogContent closeLabel={t('common.close')}>
        <DialogHeader>
          <DialogTitle>{t('stepUp.title')}</DialogTitle>
          <DialogDescription>{t('stepUp.actionLine', { action: actionLabel })}</DialogDescription>
        </DialogHeader>
        <p className="text-xs text-muted-foreground">{t('stepUp.window')}</p>
        <div className="space-y-2">
          {providers.map((p, i) => (
            <StepUpProviderForm
              key={p.id}
              provider={p.id}
              label={p.label}
              returnTo={returnTo}
              autoFocus={i === 0}
            />
          ))}
        </div>
        <Button type="button" variant="outline" className="w-full" onClick={onCancel}>
          {t('common.cancel')}
        </Button>
      </DialogContent>
    </Dialog>
  );
}
