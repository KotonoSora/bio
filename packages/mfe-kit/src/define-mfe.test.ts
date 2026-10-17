// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { MfeContext } from './context';
import { MFE_ERROR_EVENT, defineMfe, reportMfeError } from './define-mfe';

const context: MfeContext = { locale: 'en', navigate: () => {} };

let seq = 0;
const nextTag = () => `rb-kit-test-${++seq}`;

type TestElement = HTMLElement & { mfeContext: MfeContext };

afterEach(() => {
  document.body.replaceChildren();
});

describe('defineMfe lifecycle', () => {
  it('mounts only when connected and context are both present, then disposes on disconnect', () => {
    const tag = nextTag();
    const dispose = vi.fn();
    const mount = vi.fn(() => dispose);
    defineMfe(tag, mount);

    const el = document.createElement(tag) as TestElement;
    el.mfeContext = context; // context first, still detached: no mount
    expect(mount).not.toHaveBeenCalled();

    document.body.appendChild(el); // connected + context: mount
    expect(mount).toHaveBeenCalledTimes(1);
    expect(mount).toHaveBeenCalledWith(el, context);
    expect(el.dataset.mfe).toBe(tag);

    el.remove(); // disconnect: dispose
    expect(dispose).toHaveBeenCalledTimes(1);
  });

  it('mounts on context assignment while connected, once per connection', () => {
    const tag = nextTag();
    const dispose = vi.fn();
    const mount = vi.fn(() => dispose);
    defineMfe(tag, mount);

    const el = document.createElement(tag) as TestElement;
    document.body.appendChild(el); // connected first, no context yet
    expect(mount).not.toHaveBeenCalled();

    el.mfeContext = context;
    expect(mount).toHaveBeenCalledTimes(1);

    el.mfeContext = context; // re-assignment while mounted: no second mount
    expect(mount).toHaveBeenCalledTimes(1);

    el.remove();
    expect(dispose).toHaveBeenCalledTimes(1);

    document.body.appendChild(el); // reconnect with retained context: mounts again
    expect(mount).toHaveBeenCalledTimes(2);
  });

  it('keeps the first registration for a tag (double-import safety)', () => {
    const tag = nextTag();
    const first = vi.fn(() => () => {});
    const second = vi.fn(() => () => {});
    defineMfe(tag, first);
    defineMfe(tag, second);

    const el = document.createElement(tag) as TestElement;
    el.mfeContext = context;
    document.body.appendChild(el);

    expect(first).toHaveBeenCalledTimes(1);
    expect(second).not.toHaveBeenCalled();
  });
});

describe('reportMfeError', () => {
  it('dispatches the bubbling rb:mfe-error CustomEvent from the failing element', () => {
    const el = document.createElement('div');
    document.body.appendChild(el);
    const seen = vi.fn();
    document.body.addEventListener(MFE_ERROR_EVENT, seen);

    reportMfeError(el);

    expect(seen).toHaveBeenCalledTimes(1);
    const event = seen.mock.calls[0][0] as CustomEvent;
    expect(event.type).toBe('rb:mfe-error');
    expect(event.bubbles).toBe(true);
    expect(event.target).toBe(el);
  });
});
