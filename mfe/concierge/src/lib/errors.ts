import { ApiError, isUnauthenticated } from '@resume-builder/mfe-kit';
import i18n from './i18n';

/**
 * BA 05 display copy per stable `code` (TL 03 catalog; G4: codes are frozen).
 * Resolved through the vertical's i18next instance so the dictionary stays the
 * single copy owner. No blame copy, no invented thresholds; `throttled` renders
 * the `Retry-After` seconds the server stated.
 */
export function describeError(err: unknown): string {
  if (err instanceof ApiError) {
    switch (err.code) {
      case 'contract_not_found':
        return i18n.t('errors.contractNotFound');
      case 'task_not_found':
        return i18n.t('errors.taskNotFound');
      case 'task_off_menu':
        return `${i18n.t('errors.offMenu')} ${i18n.t('refusals.referral')}: /app/concierge`;
      case 'volume_exceeded':
        return i18n.t('errors.volumeExceeded');
      case 'transition_conflict':
        return i18n.t('errors.approvalsClosed');
      case 'staffing_incomplete':
        return i18n.t('errors.staffingIncomplete');
      case 'signature_invalid':
        return i18n.t('errors.signatureInvalid');
      case 'throttled':
        return i18n.t('errors.throttled', { seconds: err.retryAfterSeconds ?? 60 });
      case 'unauthenticated':
        return i18n.t('errors.sessionEnded');
      default:
        return i18n.t(err.status === 503 ? 'errors.unavailable' : 'errors.generic');
    }
  }
  return i18n.t('errors.generic');
}

export { isUnauthenticated };
