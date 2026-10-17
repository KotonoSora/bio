import type { MfeContext, MfeMountFn } from './context';

/** Fired on the MFE element when the remote app fails at runtime (shell shows its fallback). */
export const MFE_ERROR_EVENT = 'rb:mfe-error';

/** Reports a runtime failure to the shell; bubbles so host-level listeners also see it. */
export function reportMfeError(target: EventTarget): void {
  target.dispatchEvent(new CustomEvent(MFE_ERROR_EVENT, { bubbles: true }));
}

/**
 * Registers the MFE as a custom element ("DOM is the API"): the shell creates
 * `<tag>`, appends it, and assigns `mfeContext`; mounting happens once both
 * are present, dispose runs on disconnect. Each bundle registers its own tag;
 * a pre-defined tag is left alone (double-import safety).
 */
export function defineMfe(tag: string, mount: MfeMountFn): void {
  if (customElements.get(tag)) return;

  class MfeRoot extends HTMLElement {
    #context: MfeContext | null = null;
    #dispose: (() => void) | null = null;

    set mfeContext(value: MfeContext) {
      this.#context = value;
      this.#attemptMount();
    }

    get mfeContext(): MfeContext | null {
      return this.#context;
    }

    connectedCallback(): void {
      this.#attemptMount();
    }

    disconnectedCallback(): void {
      this.#dispose?.();
      this.#dispose = null;
    }

    #attemptMount(): void {
      if (!this.isConnected || this.#dispose || !this.#context) return;
      this.dataset.mfe = tag;
      this.#dispose = mount(this, this.#context);
    }
  }

  customElements.define(tag, MfeRoot);
}
