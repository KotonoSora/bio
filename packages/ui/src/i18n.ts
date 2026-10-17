// Locale detection for the MFE contract (`MfeContext.locale`) plus a
// dependency-free `{name}` template helper. Display copy is owned per surface
// through react-i18next (shell in `web/`, each vertical in `mfe/<name>/` —
// NFR-14); this module itself carries no i18n dependency.
//
// DEVIATION FLAGGED (owner decision, 2026-10-17): English-only shipping —
// the Vietnamese bundle was removed and the contract narrowed to 'en'.
export type Locale = 'en';

export function detectLocale(): Locale {
  return 'en';
}

/** Fills `{name}` placeholders; unknown names stay verbatim (visible in tests). */
export function fmt(template: string, vars: Record<string, string | number>): string {
  return template.replace(/\{(\w+)\}/g, (whole, name: string) =>
    Object.prototype.hasOwnProperty.call(vars, name) ? String(vars[name]) : whole,
  );
}
