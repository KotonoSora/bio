import { createContext, useContext, type ReactNode } from 'react';
import type { MfeContext } from '@resume-builder/mfe-kit';

// The shell contract, provided to the whole MFE React tree. Components read it
// via `useMfeContext()` instead of importing shell internals (isolation).
const MfeContextReact = createContext<MfeContext | null>(null);

export function MfeContextProvider({ value, children }: { value: MfeContext; children: ReactNode }) {
  return <MfeContextReact.Provider value={value}>{children}</MfeContextReact.Provider>;
}

export function useMfeContext(): MfeContext {
  const context = useContext(MfeContextReact);
  if (!context) throw new Error('useMfeContext used outside <MfeContextProvider>');
  return context;
}
