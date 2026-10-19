// Refusal copy per recorded reason (BA 05 row "Boundary request" + the
// off-menu/over-volume rows). Rendered on the refused task row with the
// referral the server recorded; unknown reasons fall back to the boundary copy
// so a refusal is never shown without guidance.
export type RefusalReason =
  | 'off_menu'
  | 'over_volume'
  | 'boundary_billing'
  | 'boundary_policy'
  | 'boundary_contact'
  | 'boundary_content'
  | 'client_cancel';

import en from './locales/en';

export type RefusalKey = keyof (typeof en)['refusals'];

export const BOUNDARY_REASONS: ReadonlySet<string> = new Set([
  'boundary_billing',
  'boundary_policy',
  'boundary_contact',
  'boundary_content',
]);

export function refusalKey(reason: string | null): RefusalKey {
  if (reason === 'off_menu' || reason === 'over_volume' || reason === 'client_cancel') return reason;
  return 'boundary';
}
