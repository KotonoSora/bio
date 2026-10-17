import type { ReactNode } from 'react';
import { cn } from 'cn';
import { Alert, AlertAction, AlertDescription } from './alert';
import { Button } from './button';

const TONES = {
  error: 'border-destructive/25 bg-destructive/5 text-destructive',
  warn: 'border-amber-300 bg-amber-50 text-amber-900',
  info: 'border-sky-300 bg-sky-50 text-sky-900',
} as const;

/** Inline status banner shared by the shell and verticals; dismissible variants get the BA 04 dismiss control. */
export function Notice({
  tone = 'error',
  onDismiss,
  dismissLabel,
  children,
}: {
  tone?: keyof typeof TONES;
  onDismiss?: () => void;
  dismissLabel?: string;
  children: ReactNode;
}) {
  return (
    <Alert role="status" className={cn('mt-4', TONES[tone])}>
      <AlertDescription className="text-current">{children}</AlertDescription>
      {onDismiss && (
        <AlertAction>
          <Button
            type="button"
            variant="ghost"
            size="xs"
            onClick={onDismiss}
            className="h-auto px-1.5 py-0 text-current underline underline-offset-3 hover:bg-transparent"
          >
            {dismissLabel}
          </Button>
        </AlertAction>
      )}
    </Alert>
  );
}
