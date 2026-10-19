// Timestamps are shown in the viewer's locale; the board is freshness-scoped
// (NFR-02: <= 5 min stale), so relative time is not used - an exact instant is
// the honest read. `null` renders as an em dash so an absent timestamp is
// visibly absent rather than an invented date.
const dateTime = new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'short' });

export function stamp(iso: string | null | undefined): string {
  if (!iso) return '—';
  const ms = Date.parse(iso);
  return Number.isFinite(ms) ? dateTime.format(new Date(ms)) : '—';
}
