import { describe, expect, it } from 'vitest';
import { lastActiveBucket } from './format';

const NOW = new Date('2026-10-17T12:00:00');

describe('lastActiveBucket', () => {
  it('reads Active now within five minutes', () => {
    expect(lastActiveBucket('2026-10-17T11:58:00', NOW)).toBe('now');
    expect(lastActiveBucket('2026-10-17T11:54:59', NOW)).toBe('today');
  });

  it('reads Today across earlier local hours of the same day', () => {
    expect(lastActiveBucket('2026-10-17T00:05:00', NOW)).toBe('today');
  });

  it('reads This week up to seven days back', () => {
    expect(lastActiveBucket('2026-10-16T12:00:00', NOW)).toBe('week');
    expect(lastActiveBucket('2026-10-11T12:00:01', NOW)).toBe('week');
  });

  it('reads Earlier beyond seven days or unparseable input', () => {
    expect(lastActiveBucket('2026-10-09T12:00:00', NOW)).toBe('earlier');
    expect(lastActiveBucket('not-a-date', NOW)).toBe('earlier');
  });

  it('crosses midnight into This week, not Today', () => {
    const midnight = new Date('2026-10-18T00:30:00');
    expect(lastActiveBucket('2026-10-17T23:59:00', midnight)).toBe('week');
  });
});
