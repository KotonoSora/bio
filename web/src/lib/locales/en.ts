// Shell display copy (NFR-14). Keys are stable identifiers — server `code`
// strings never appear here; this dictionary owns presentation only.
// `{{name}}` placeholders are filled by i18next interpolation at call sites.
const en = {
  nav: { dashboard: 'Dashboard', resumes: 'Resumes', settings: 'Account settings' },
  common: { loading: 'Loading…', retry: 'Try again' },
  mfe: { failed: "This section couldn't load." },
  login: {
    title: 'Sign in',
    subtitle: 'Choose a sign-in method to continue.',
    continueWith: 'Continue with {{provider}}',
    unavailable: "Sign-in isn't available right now.",
    failed: "Sign-in didn't complete. Try again.",
    sessionEnded: 'You were signed out. Sign in again to continue.',
  },
  authError: {
    title: "Sign-in didn't complete. Try again.",
    support: 'This link expired or was already used. Contact support.',
    back: 'Back to sign in',
  },
  recovery: {
    title: 'Recover access',
    body: 'Link a sign-in method to regain access to your account. No password is ever set.',
  },
  welcome: {
    title: 'Pick your username',
    body: 'Your username is your public address. The picker arrives with the public profile release; you can continue to your dashboard for now.',
    cta: 'Continue to dashboard',
  },
  notFound: { title: 'Page not found', body: 'This address does not exist or has moved.', cta: 'Back to dashboard' },
  settings: { promptAdd: 'Add a contact email for receipts.' },
};

export type ShellResources = typeof en;
export default en;
