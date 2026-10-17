// Locale detection for the MFE contract (`MfeContext.locale`) plus a
// dependency-free `{name}` template helper. Display copy is owned per surface
// through react-i18next (shell in `web/`, each vertical in `mfe/<name>/` —
// NFR-14); this module itself carries no i18n dependency.
export type Locale = 'en' | 'vi';

export function detectLocale(): Locale {
  if (typeof navigator === 'undefined') return 'en';
  const tag = (navigator.languages?.[0] ?? navigator.language ?? '').toLowerCase();
  return tag.startsWith('vi') ? 'vi' : 'en';
}

/** Fills `{name}` placeholders; unknown names stay verbatim (visible in tests). */
export function fmt(template: string, vars: Record<string, string | number>): string {
  return template.replace(/\{(\w+)\}/g, (whole, name: string) =>
    Object.prototype.hasOwnProperty.call(vars, name) ? String(vars[name]) : whole,
  );
}
