/* global caches, clients, fetch, self, URL */
/*
 * Chunk-00 item 13: offline client cache groundwork.
 * Precaches the /offline fallback shell; cache-first for build assets,
 * network-first for navigations (falling back to /offline), and never touches
 * API traffic. Bump VERSION to invalidate every old cache on activate.
 */
const VERSION = 'squamed-offline-v1';
const OFFLINE_URL = '/offline';

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(VERSION)
      .then((cache) => cache.add(OFFLINE_URL))
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== VERSION).map((key) => caches.delete(key))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET') return;

  const url = new URL(request.url);
  if (url.pathname.startsWith('/api')) return; // API responses are never cached

  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request).catch(() =>
        caches.match(request).then((cached) => cached || caches.match(OFFLINE_URL)),
      ),
    );
    return;
  }

  const cacheable =
    url.origin === self.location.origin &&
    (url.pathname.startsWith('/_next/static') ||
      url.pathname.startsWith('/icons') ||
      url.pathname.startsWith('/images'));
  if (!cacheable) return;

  event.respondWith(
    caches.match(request).then(
      (cached) =>
        cached ||
        fetch(request).then((response) => {
          const copy = response.clone();
          caches.open(VERSION).then((cache) => cache.put(request, copy));
          return response;
        }),
    ),
  );
});
