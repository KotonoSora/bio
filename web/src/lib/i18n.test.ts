import { describe, expect, it } from 'vitest';
import i18n from './i18n';

// NFR-14 currently ships English only: no vi bundle is registered and the
// shell dictionary resolves through i18next (not raw keys).
describe('shell i18n', () => {
  it('registers English only', () => {
    expect(i18n.hasResourceBundle('vi', 'translation')).toBe(false);
    expect(i18n.resolvedLanguage).toBe('en');
  });

  it('resolves shell copy with interpolation', () => {
    expect(i18n.t('login.title')).toBe('Sign in');
    expect(i18n.t('login.continueWith', { provider: 'Google' })).toBe('Continue with Google');
  });
});
