import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { defineMfe, reportMfeError, type MfeContext } from '@resume-builder/mfe-kit';
import AccountSettings from './AccountSettings';
import { MfeContextProvider } from './mfe-context';
import './index.css';
import './lib/i18n';

// MFE entry (platform doc 10): registers the custom element the shell drives.
// React is bundled with this MFE on purpose — no shared runtime with the shell.
defineMfe('rb-account-app', (root: HTMLElement, context: MfeContext) => {
  const reactRoot = createRoot(root, {
    // Resilience: an uncaught render error degrades to the shell's fallback
    // (web/src/mfe/MfeMount.tsx) instead of a blank section.
    onUncaughtError: () => reportMfeError(root),
  });
  reactRoot.render(
    <StrictMode>
      <MfeContextProvider value={context}>
        <AccountSettings />
      </MfeContextProvider>
    </StrictMode>,
  );
  return () => reactRoot.unmount();
});
