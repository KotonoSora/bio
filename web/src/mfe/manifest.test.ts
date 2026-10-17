import { afterEach, describe, expect, it, vi } from 'vitest';
import { assetUrl, fetchManifest, resetManifestCache } from './manifest';

const ok = (body: unknown) =>
  new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });

afterEach(() => {
  resetManifestCache();
  vi.unstubAllGlobals();
});

describe('assetUrl', () => {
  it('resolves manifest paths against the SPA base so entries stay under /app/', () => {
    expect(assetUrl('mfe/account/0.1.0/entry.js')).toBe('/app/mfe/account/0.1.0/entry.js');
  });
});

describe('fetchManifest', () => {
  it('parses the manifest once and reuses the page-lifetime cache', async () => {
    const fetchMock = vi.fn(async () =>
      ok({ account: { tag: 'rb-account-app', version: '0.1.0', entry: 'mfe/account/0.1.0/entry.js' } }),
    );
    vi.stubGlobal('fetch', fetchMock);

    const first = await fetchManifest();
    const second = await fetchManifest();

    expect(first.account.tag).toBe('rb-account-app');
    expect(second).toBe(first);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledWith('/app/mfe.manifest.json');
  });

  it('rejects a non-OK response and lets a later call retry over the network', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response('nope', { status: 503 }))
      .mockResolvedValueOnce(ok({}));
    vi.stubGlobal('fetch', fetchMock);

    await expect(fetchManifest()).rejects.toThrow('503');
    await expect(fetchManifest()).resolves.toEqual({});
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('rejects malformed entries', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ok({ account: { version: '0.1.0' } })));
    await expect(fetchManifest()).rejects.toThrow('bad entry "account"');
  });
});
