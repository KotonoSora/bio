import { ApiError } from '@resume-builder/mfe-kit';
import i18n from './i18n';

/**
 * BA 05 display copy per stable `code` (G4), resolved through the vertical's
 * i18next instance so the dictionary stays the single copy owner. Deny
 * answers carry no plan facts; `throttled` renders the `Retry-After` seconds.
 */
export function describeError(err: unknown): string {
  if (err instanceof ApiError) {
    switch (err.code) {
      case 'hostname_invalid':
        return i18n.t('errors.hostnameInvalid');
      case 'domain_denied':
        return i18n.t('errors.domainDenied');
      case 'domain_taken':
        return i18n.t('errors.domainTaken');
      case 'domain_forbidden':
        return i18n.t('errors.domainForbidden');
      case 'domain_quota_reached':
        return i18n.t('errors.domainQuota');
      case 'domain_not_found':
        return i18n.t('errors.domainNotFound');
      case 'throttled':
        return i18n.t('errors.throttled', { seconds: err.retryAfterSeconds ?? 60 });
      case 'unauthenticated':
        return i18n.t('errors.unauthenticated');
      default:
        return i18n.t(err.status === 503 ? 'errors.unavailable' : 'errors.generic');
    }
  }
  return i18n.t('errors.generic');
}

/** True when the error means the edge session is gone and the surface must log in again. */
export function isUnauthenticated(err: unknown): boolean {
  return err instanceof ApiError && err.status === 401;
}
