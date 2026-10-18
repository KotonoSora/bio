import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { defineMfe, reportMfeError, type MfeContext } from '@resume-builder/mfe-kit';
import PricingPage from './PricingPage';
import { MfeContextProvider } from './mfe-context';
import './index.css';

// MFE entry (platform doc 10): registers the custom element the shell drives.
// React is bundled with this MFE on purpose — no shared runtime with the shell.
defineMfe('rb-plans-app', (root: HTMLElement, context: MfeContext) => {
  const reactRoot = createRoot(root, {
    // Resilience: an uncaught render error degrades to the shell's fallback
    // (web/src/mfe/MfeMount.tsx) instead of a blank section.
    onUncaughtError: () => reportMfeError(root),
  });
  reactRoot.render(
    <StrictMode>
      <MfeContextProvider value={context}>
        <PricingPage />
      </MfeContextProvider>
    </StrictMode>,
  );
  return () => reactRoot.unmount();
});
