import { ApiError } from '@resume-builder/mfe-kit';
import i18n from './i18n';

/**
 * BA 05 display copy per stable `code` (G4), resolved through the vertical's
 * i18next instance so the dictionary stays the single copy owner. Never
 * reveals account existence or provider detail; `throttled` renders the
 * `Retry-After` seconds.
 */
export function describeError(err: unknown): string {
  if (err instanceof ApiError) {
    switch (err.code) {
      case 'throttled':
        return i18n.t('errors.throttled', { seconds: err.retryAfterSeconds ?? 60 });
      case 'step_up_required':
        return i18n.t('errors.stepUpRequired');
      case 'email_conflict':
        return i18n.t('errors.emailConflict');
      case 'email_code_invalid':
        return i18n.t('errors.invalidCode');
      case 'last_link_forbidden':
        return i18n.t('errors.lastLink');
      case 'session_not_found':
        return i18n.t('errors.sessionGone');
      case 'confirmation_mismatch':
        return i18n.t('errors.confirmationMismatch');
      case 'delete_not_requested':
        return i18n.t('errors.deleteNotRequested');
      case 'purge_completed':
        return i18n.t('errors.purgeCompleted');
      case 'unauthenticated':
        return i18n.t('errors.sessionEnded');
      case 'auth_failed':
        return i18n.t('login.failed');
      default:
        return i18n.t(err.status === 503 ? 'errors.unavailable' : 'errors.generic');
    }
  }
  return i18n.t('errors.generic');
}

/** True when the error means the edge session is gone and the SPA must log in again. */
export function isUnauthenticated(err: unknown): boolean {
  return err instanceof ApiError && err.status === 401;
}
