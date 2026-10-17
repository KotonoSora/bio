import { describe, expect, it } from 'vitest';
import i18n from './i18n';

// NFR-14 currently ships English only: no vi bundle is registered and the
// vertical dictionary resolves through i18next (not raw keys).
describe('account MFE i18n', () => {
  it('registers English only', () => {
    expect(i18n.hasResourceBundle('vi', 'translation')).toBe(false);
    expect(i18n.resolvedLanguage).toBe('en');
  });

  it('resolves vertical copy with interpolation', () => {
    expect(i18n.t('settings.title')).toBe('Account settings');
    expect(i18n.t('actions.link', { provider: 'GitHub' })).toBe('Link GitHub');
    expect(i18n.t('settings.attemptsLeft', { count: 2 })).toBe('Attempts left: 2');
  });
});
