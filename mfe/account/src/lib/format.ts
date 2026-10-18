// BA 04 "Sessions list - last active": coarse buckets derived from
// `last_active_at`. Boundary semantics: a session touched within the last five
// minutes reads "Active now"; same local day reads "Today"; within seven days
// reads "This week"; anything older reads "Earlier".
export type ActiveBucket = 'now' | 'today' | 'week' | 'earlier';

// Feature 03 (BR-12): the SPA formats server-traveled VND integers; it never
// computes discounts, proration, or percentages itself.
const vndFormatter = new Intl.NumberFormat('en', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 });

export function formatVnd(value: number): string {
  if (!Number.isInteger(value)) throw new Error('VND amounts are integers end to end (NFR-04)');
  return vndFormatter.format(value);
}

const ACTIVE_NOW_MS = 5 * 60 * 1000;
const WEEK_MS = 7 * 24 * 60 * 60 * 1000;

export function lastActiveBucket(lastActiveAt: string, now: Date = new Date()): ActiveBucket {
  const at = new Date(lastActiveAt);
  if (Number.isNaN(at.getTime())) return 'earlier';
  const diff = now.getTime() - at.getTime();
  if (diff < ACTIVE_NOW_MS) return 'now';
  if (sameLocalDay(at, now)) return 'today';
  if (diff < WEEK_MS) return 'week';
  return 'earlier';
}

function sameLocalDay(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}
