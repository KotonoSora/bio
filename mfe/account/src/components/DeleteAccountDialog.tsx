import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  Input,
  Label,
  Notice,
} from '@resume-builder/ui';

/** BR-01 typed gate: the profile username exactly, else the literal `DELETE`. */
export const LITERAL_DELETE = 'DELETE';

/**
 * Consequences + typed confirmation (BA 04 "Consequences" and "Typed
 * confirmation input"): full vanishes/stays lists, no pre-checked state, and
 * the final button arms only on the exact expected value (case-sensitive).
 * `expected === null` while the profile lookup is in flight - the gate stays
 * closed until it lands.
 */
export default function DeleteAccountDialog({
  expected,
  error,
  busy,
  onConfirm,
  onCancel,
}: {
  expected: string | null;
  error: string | null;
  busy: boolean;
  onConfirm: (confirmation: string) => void;
  onCancel: () => void;
}) {
  const { t } = useTranslation();
  const [typed, setTyped] = useState('');
  const armed = expected !== null && typed === expected;
  const vanishes = [
    t('settings.deletion.vanishes.profile'),
    t('settings.deletion.vanishes.files'),
    t('settings.deletion.vanishes.activity'),
  ];
  const stays = [
    t('settings.deletion.stays.orders'),
    t('settings.deletion.stays.legal'),
    t('settings.deletion.stays.audit'),
  ];
  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !busy) onCancel();
      }}
    >
      <DialogContent closeLabel={t('common.close')} className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t('settings.deletion.dialogTitle')}</DialogTitle>
          <DialogDescription>{t('settings.deletion.dialogIntro')}</DialogDescription>
        </DialogHeader>
        <div className="space-y-1">
          <h3 className="font-heading text-sm font-medium">{t('settings.deletion.vanishesTitle')}</h3>
          <ul className="list-disc space-y-1 pl-5 text-sm text-muted-foreground">
            {vanishes.map((line) => (
              <li key={line}>{line}</li>
            ))}
          </ul>
        </div>
        <div className="space-y-1">
          <h3 className="font-heading text-sm font-medium">{t('settings.deletion.staysTitle')}</h3>
          <ul className="list-disc space-y-1 pl-5 text-sm text-muted-foreground">
            {stays.map((line) => (
              <li key={line}>{line}</li>
            ))}
          </ul>
        </div>
        {error && <Notice>{error}</Notice>}
        <div className="space-y-2">
          <Label htmlFor="delete-confirmation">{t('settings.deletion.confirmationLabel')}</Label>
          <Input
            id="delete-confirmation"
            autoComplete="off"
            disabled={busy}
            value={typed}
            onChange={(e) => setTyped(e.target.value)}
          />
          {expected !== null && (
            <p className="text-xs text-muted-foreground">
              {expected === LITERAL_DELETE
                ? t('settings.deletion.hintDelete')
                : t('settings.deletion.hintUsername', { username: expected })}
            </p>
          )}
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" disabled={busy} onClick={onCancel}>
            {t('settings.deletion.neverMind')}
          </Button>
          <Button
            type="button"
            variant="destructive"
            disabled={!armed || busy}
            onClick={() => onConfirm(typed)}
          >
            {t('settings.deletion.confirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
