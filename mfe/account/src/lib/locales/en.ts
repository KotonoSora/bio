// Account vertical display copy (NFR-14). Keys are stable identifiers — server
// `code` strings never appear here; this dictionary owns presentation only.
// `{{name}}` placeholders are filled by i18next interpolation at call sites.
const en = {
  common: { cancel: 'Cancel', close: 'Close', dismiss: 'Dismiss', loading: 'Loading…', dash: '—' },
  login: { continueWith: 'Continue with {{provider}}', failed: "Sign-in didn't complete. Try again." },
  settings: {
    title: 'Account settings',
    accountEmail: 'Sign-in email',
    idpSource: 'Provided by your sign-in provider',
    status: { verified: 'Verified', notVerified: 'Not verified', relay: 'Relay address', conflict: 'Conflict' },
    promptAdd: 'Add a contact email for receipts.',
    conflictNotice: 'This email is already connected to another account. Set a contact email instead.',
    contactEmail: 'Contact email',
    contactPlaceholder: 'you@example.com',
    sendCode: 'Send verification code',
    codeLabel: '6-digit code',
    verifyCode: 'Verify code',
    resend: 'Resend code',
    resendIn: 'Resend in {{seconds}}s',
    codeSentTo: 'We sent a 6-digit code to {{email}}.',
    attemptsLeft: 'Attempts left: {{count}}',
    providers: 'Sign-in methods',
    linked: 'Linked',
    notLinked: 'Not linked',
    link: 'Link',
    unlink: 'Unlink',
    lastLinkHint: 'You need at least one sign-in method. Link another first.',
    unlinkTitle: 'Unlink {{provider}}?',
    unlinkBody:
      'This removes the sign-in method from your account; you can link it again any time. Confirming may ask you to re-authenticate.',
    sessions: 'Active sessions',
    current: 'This device',
    revoke: 'Revoke',
    revokeTitle: 'Revoke this session?',
    revokeBody: 'It may take up to 60 seconds for the session to stop working.',
    revokeAll: 'Sign out all other devices',
    revokeAllTitle: 'Sign out all other devices?',
    revokeAllBody:
      'It may take up to 60 seconds for the other sessions to stop working. This device stays signed in.',
    signOut: 'Sign out',
    network: 'Network',
    unknownDevice: 'Unknown device',
  },
  buckets: { now: 'Active now', today: 'Today', week: 'This week', earlier: 'Earlier' },
  stepUp: {
    title: "Confirm it's you",
    actionLine: "You're about to: {{action}}",
    window: 'This confirmation stays valid for 10 minutes.',
  },
  actions: { link: 'Link {{provider}}', unlink: 'Unlink {{provider}}' },
  errors: {
    throttled: 'Too many attempts. Try again in {{seconds}}.',
    stepUpRequired: "Confirm it's you to continue.",
    emailConflict: "This email can't be used here. Choose another.",
    invalidCode: "That code didn't work. Check it or request a new one.",
    lastLink: 'You need at least one sign-in method. Link another first.',
    sessionGone: 'That session no longer exists.',
    sessionEnded: 'You were signed out. Sign in again to continue.',
    unavailable: 'Temporarily unavailable. Try again.',
    generic: 'Something went wrong. Try again.',
  },
};

export type AccountResources = typeof en;
export default en;
