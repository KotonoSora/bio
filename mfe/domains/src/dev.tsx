// Standalone dev harness only (`npm run dev -w mfe/domains`): mounts the MFE
// with a stub context so the vertical can be iterated with HMR. The composed
// shell mounts the built assets instead (web/src/mfe/MfeMount.tsx).
import type { MfeHostElement } from '@resume-builder/mfe-kit';
import './entry';

const element = document.createElement('rb-domains-app') as MfeHostElement;
element.mfeContext = {
  locale: 'en',
  navigate: (to) => console.log('[mfe/domains dev] navigate ->', to),
};
element.setAttribute('style', 'display:block; max-width:42rem; margin:0 auto; padding:2rem 1.5rem;');
document.getElementById('root')!.replaceChildren(element);
