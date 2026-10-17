import { beforeEach, describe, expect, it, vi } from 'vitest';
import { clearPendingAction, setPendingAction, takePendingAction } from './stepup';

// The parked action is the step-up resume contract: link/unlink keep provider
// pinpointing, delete carries the typed confirmation so the replay re-sends an
// identical body (feature 02 BR-01).
const store = new Map<string, string>();
beforeEach(() => {
  store.clear();
  vi.stubGlobal('sessionStorage', {
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => void store.set(key, value),
    removeItem: (key: string) => void store.delete(key),
  });
});

describe('step-up pending action', () => {
  it('round-trips the delete variant with its confirmation', () => {
    setPendingAction({ kind: 'delete', confirmation: 'alice' });
    expect(takePendingAction()).toEqual({ kind: 'delete', confirmation: 'alice' });
    expect(takePendingAction()).toBeNull(); // take consumes
  });

  it('keeps the link/unlink variants provider-scoped', () => {
    setPendingAction({ kind: 'unlink', provider: 'github', label: 'GitHub' });
    expect(takePendingAction()).toEqual({ kind: 'unlink', provider: 'github', label: 'GitHub' });
    setPendingAction({ kind: 'link', provider: 'google', label: 'Google' });
    expect(takePendingAction()).toEqual({ kind: 'link', provider: 'google', label: 'Google' });
  });

  it('drops malformed residue instead of replaying it', () => {
    sessionStorage.setItem('acct.pendingAction', '{"kind":"delete"}');
    expect(takePendingAction()).toBeNull();
    sessionStorage.setItem('acct.pendingAction', '{"kind":"unlink"}');
    expect(takePendingAction()).toBeNull();
    sessionStorage.setItem('acct.pendingAction', 'not-json');
    expect(takePendingAction()).toBeNull();
    clearPendingAction();
    expect(takePendingAction()).toBeNull();
  });
});
