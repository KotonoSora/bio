// react-i18next setup for the concierge vertical (NFR-14): npm packages only —
// dictionaries are compiled in, no backend and no hosted localization service.
// English-only shipping (DEVIATION FLAGGED, owner decision 2026-10-17): a second
// language re-registers here plus a `supportedLngs` entry. Ownership stays per
// surface: this vertical owns its dictionary; the shell owns its own.
import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import en, { type ConciergeResources } from './locales/en';

// Typed `t()`: keys are derived from `en`, so a missing or renamed key fails
// the workspace type-check instead of rendering the raw key at runtime.
declare module 'i18next' {
  interface CustomTypeOptions {
    resources: { translation: ConciergeResources };
  }
}

void i18n.use(initReactI18next).init({
  lng: 'en',
  supportedLngs: ['en'],
  fallbackLng: 'en',
  resources: { en: { translation: en } },
  // Dictionaries ship inline (no backend): initialize synchronously so `t()`
  // works right after import — non-React callers and tests included.
  initAsync: false,
  interpolation: { escapeValue: false }, // React already escapes rendered output
});

export default i18n;
