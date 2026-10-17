// @vitest-environment happy-dom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MFE_ERROR_EVENT } from '@resume-builder/mfe-kit';
import { detectLocale } from '@resume-builder/ui';
import i18n from '../lib/i18n';
import MfeMount from './MfeMount';
import { resetManifestCache } from './manifest';

// Keep the real manifest fetch/caching under test; only map manifest paths to
// fixture specifiers (dynamic imports resolve against this module at runtime).
vi.mock('./manifest', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./manifest')>();
  return { ...actual, assetUrl: (path: string) => path };
});

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const manifestResponse = (body: unknown) =>
  new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });

const accountEntry = {
  account: { tag: 'rb-test-app', version: '0.1.0', entry: './__fixtures__/mfe-entry.js' },
};
const missingEntry = {
  account: { tag: 'rb-test-app', version: '0.1.0', entry: './__fixtures__/missing.js' },
};

let container: HTMLDivElement;
let root: Root;

beforeEach(() => {
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(async () => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  await act(async () => {
    root.unmount();
  });
  container.remove();
  document.head.replaceChildren();
  resetManifestCache();
});

const settle = async (rounds = 5) => {
  for (let i = 0; i < rounds; i++) {
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
  }
};

const render = async () => {
  await act(async () => {
    root.render(
      <MemoryRouter>
        <MfeMount name="account" />
      </MemoryRouter>,
    );
  });
};

const busy = () => container.querySelector('section')?.getAttribute('aria-busy');

describe('MfeMount', () => {
  it('degrades to the retryable fallback when the manifest cannot be fetched', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('down', { status: 503 })));

    await render();
    await settle();

    expect(container.textContent).toContain(i18n.t('mfe.failed'));
    expect(container.querySelector('button')?.textContent).toBe(i18n.t('common.retry'));
    expect(busy()).toBe('false');
    expect(container.querySelector('rb-test-app')).toBeNull();
  });

  it('degrades to the fallback when the entry module fails to load', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => manifestResponse(missingEntry)));

    await render();
    await settle();

    expect(container.textContent).toContain(i18n.t('mfe.failed'));
    expect(container.querySelector('rb-test-app')).toBeNull();
  });

  it('shows the skeleton while loading and degrades when the mount budget expires', async () => {
    vi.useFakeTimers();
    vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(() => {}))); // never settles

    await render();
    expect(busy()).toBe('true');

    await act(async () => {
      await vi.advanceTimersByTimeAsync(10_000);
    });

    expect(busy()).toBe('false');
    expect(container.textContent).toContain(i18n.t('mfe.failed'));
  });

  it('composes the vertical on success: element mounted, context assigned, locale applied', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => manifestResponse(accountEntry)));

    await render();
    await settle();

    const el = container.querySelector('rb-test-app') as HTMLElement | null;
    expect(el).not.toBeNull();
    expect(el?.dataset.mfe).toBe('rb-test-app');
    expect(el?.textContent).toBe(`test-app:${detectLocale()}`);
    expect(busy()).toBe('false');
    expect(container.textContent).not.toContain(i18n.t('mfe.failed'));
  });

  it('recovers via retry: refetches the manifest and mounts the vertical', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response('down', { status: 503 }))
      .mockResolvedValueOnce(manifestResponse(accountEntry));
    vi.stubGlobal('fetch', fetchMock);

    await render();
    await settle();
    expect(container.textContent).toContain(i18n.t('mfe.failed'));

    const retry = container.querySelector('button');
    await act(async () => {
      retry?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });
    await settle();

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(container.querySelector('rb-test-app')).not.toBeNull();
    expect(container.textContent).not.toContain(i18n.t('mfe.failed'));
  });

  it('switches to the fallback when the vertical reports rb:mfe-error', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => manifestResponse(accountEntry)));

    await render();
    await settle();
    const el = container.querySelector('rb-test-app');
    expect(el).not.toBeNull();

    await act(async () => {
      el?.dispatchEvent(new CustomEvent(MFE_ERROR_EVENT, { bubbles: true }));
    });

    expect(container.textContent).toContain(i18n.t('mfe.failed'));
  });
});
