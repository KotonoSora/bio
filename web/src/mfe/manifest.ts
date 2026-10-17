// Runtime MFE discovery (platform doc 10): the deploy build writes
// /app/mfe.manifest.json; the shell resolves each vertical from it and
// lazy-imports the versioned entry. Fetched once per page load.
export interface MfeManifestEntry {
  tag: string;
  version: string;
  /** Path relative to the SPA base, e.g. `mfe/account/0.1.0/entry.js`. */
  entry: string;
  /** Same, for the vertical's stylesheet (loader injects it as a <link>). */
  css?: string;
}

export type MfeManifest = Record<string, MfeManifestEntry>;

const MANIFEST_FILE = 'mfe.manifest.json';

/** Resolves a manifest-relative path against the SPA base (`/app/`). */
export function assetUrl(relative: string): string {
  return `${import.meta.env.BASE_URL}${relative}`;
}

let cached: Promise<MfeManifest> | null = null;

/** Fetches and validates the manifest; a failure clears the cache so retry re-hits the network. */
export function fetchManifest(): Promise<MfeManifest> {
  cached ??= fetch(assetUrl(MANIFEST_FILE))
    .then(async (res) => {
      if (!res.ok) throw new Error(`mfe manifest: HTTP ${res.status}`);
      const body = (await res.json()) as MfeManifest;
      if (typeof body !== 'object' || body === null) throw new Error('mfe manifest: malformed');
      for (const [name, entry] of Object.entries(body)) {
        if (!entry.tag || !entry.entry) throw new Error(`mfe manifest: bad entry "${name}"`);
      }
      return body;
    })
    .catch((err: unknown) => {
      cached = null;
      throw err;
    });
  return cached;
}

/** Test seam: drops the page-lifetime cache. */
export function resetManifestCache(): void {
  cached = null;
}
