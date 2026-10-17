// Step-up round-trip resume: the interrupted action is parked in
// sessionStorage before the re-auth navigation and replayed when the callback
// lands back on the settings page. Cancelling the dialog clears it (BR-07).
export interface PendingAction {
  kind: 'link' | 'unlink';
  provider: string;
  label: string;
}

const KEY = 'acct.pendingAction';

export function setPendingAction(action: PendingAction): void {
  sessionStorage.setItem(KEY, JSON.stringify(action));
}

export function takePendingAction(): PendingAction | null {
  const raw = sessionStorage.getItem(KEY);
  if (!raw) return null;
  sessionStorage.removeItem(KEY);
  try {
    const parsed = JSON.parse(raw) as PendingAction;
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
