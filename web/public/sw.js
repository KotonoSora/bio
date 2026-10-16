/* PWA shell service worker: same-numbers parity across surfaces (feature 11).
   Offline rules: network-only for /api/* (never serve stale API data);
   cache-first for the app shell with offline fallback to cached index. */
const SHELL = 'rb-shell-v1';
const SHELL_URLS = ['/app/', '/app/index.html', '/app/manifest.webmanifest'];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(SHELL).then((c) => c.addAll(SHELL_URLS)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== SHELL).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  const url = new URL(request.url);
  if (url.pathname.startsWith('/api/')) return; // network-only for /api/*
  if (request.method !== 'GET' || url.origin !== self.location.origin) return;
  event.respondWith(
    caches.match(request).then(
      (hit) =>
        hit ||
        fetch(request).then((res) => {
          const copy = res.clone();
          caches.open(SHELL).then((c) => c.put(request, copy));
          return res;
        }),
    ).catch(() => caches.match('/app/index.html')),
  );
});
