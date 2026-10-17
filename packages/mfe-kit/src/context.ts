/**
 * The shell ↔ MFE contract (platform doc 10). Values flow in via `mfeContext`
 * (the "DOM is the API" property); notifications flow out as DOM CustomEvents
 * (`reportMfeError` -> `MFE_ERROR_EVENT`). Additive-only: never repurpose a
 * field, never remove one.
 */
export interface MfeContext {
  /** Shell-resolved UI locale (NFR-14: copy ships in en + vi, no negotiation). */
  locale: 'en' | 'vi';
  /** Navigates the shell router; `to` is router-relative (`/login?notice=expired`). */
  navigate: (to: string) => void;
}

/** Mount/dispose pair an MFE returns from its entry; dispose runs on disconnect. */
export type MfeMountFn = (root: HTMLElement, context: MfeContext) => () => void;

/** The DOM surface the shell drives: a `<tag>` element with assigned `mfeContext`. */
export interface MfeHostElement extends HTMLElement {
  mfeContext: MfeContext;
}
