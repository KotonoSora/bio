// Step-up round-trip resume: the interrupted action is parked in
// sessionStorage before the re-auth navigation and replayed when the callback
// lands back on the settings page. Cancelling the dialog clears it (BR-07).
// The delete variant (feature 02) carries the typed confirmation so the replay
// re-sends the identical request body.
export type PendingAction =
  | { kind: 'link'; provider: string; label: string }
  | { kind: 'unlink'; provider: string; label: string }
  | { kind: 'delete'; confirmation: string };

const KEY = 'acct.pendingAction';

export function setPendingAction(action: PendingAction): void {
  sessionStorage.setItem(KEY, JSON.stringify(action));
}

export function takePendingAction(): PendingAction | null {
  const raw = sessionStorage.getItem(KEY);
  if (!raw) return null;
  sessionStorage.removeItem(KEY);
  try {
    const parsed = JSON.parse(raw) as {
      kind?: string;
      provider?: string;
      label?: string;
      confirmation?: string;
    };
    if (parsed.kind === 'delete' && typeof parsed.confirmation === 'string') {
      return { kind: 'delete', confirmation: parsed.confirmation };
    }
    if ((parsed.kind === 'link' || parsed.kind === 'unlink') && typeof parsed.provider === 'string') {
      return { kind: parsed.kind, provider: parsed.provider, label: parsed.label ?? parsed.provider };
    }
  } catch {
    // Malformed residue: drop it.
  }
  return null;
}

export function clearPendingAction(): void {
  sessionStorage.removeItem(KEY);
}

// Conflict banner dismissal is per browser session (BA 04).
const DISMISS_KEY = 'acct.conflictDismissed';

export function conflictBannerDismissed(): boolean {
  return sessionStorage.getItem(DISMISS_KEY) === '1';
}

export function dismissConflictBanner(): void {
  sessionStorage.setItem(DISMISS_KEY, '1');
}
