// Marginalia service worker: keeps the app shell available offline and caches cover images.
// Book data (Open Library JSON) is cached by the app itself, so it is not handled here.
const SHELL_CACHE = 'marginalia-shell-v1';
const COVER_CACHE = 'marginalia-covers-v1';
const MAX_COVERS = 400;

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(SHELL_CACHE)
      .then((cache) => cache.addAll(['./', './index.html', './manifest.webmanifest', './icon.svg']))
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((k) => k.startsWith('marginalia-') && k !== SHELL_CACHE && k !== COVER_CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

async function trim(cacheName, max) {
  const cache = await caches.open(cacheName);
  const keys = await cache.keys();
  for (const key of keys.slice(0, Math.max(0, keys.length - max))) await cache.delete(key);
}

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);

  if (url.origin === self.location.origin) {
    if (req.mode === 'navigate') {
      // Network first so updates arrive; the cached shell when offline.
      event.respondWith(
        fetch(req)
          .then((res) => {
            const copy = res.clone();
            caches.open(SHELL_CACHE).then((c) => c.put('./index.html', copy));
            return res;
          })
          .catch(() => caches.match('./index.html')),
      );
      return;
    }
    // Hashed build assets and fonts: cache first.
    event.respondWith(
      caches.match(req).then(
        (hit) =>
          hit ||
          fetch(req).then((res) => {
            if (res.ok) {
              const copy = res.clone();
              caches.open(SHELL_CACHE).then((c) => c.put(req, copy));
            }
            return res;
          }),
      ),
    );
    return;
  }

  if (url.hostname === 'covers.openlibrary.org') {
    event.respondWith(
      caches.open(COVER_CACHE).then(async (cache) => {
        const hit = await cache.match(req);
        if (hit) return hit;
        const res = await fetch(req);
        if (res.ok || res.type === 'opaque') {
          await cache.put(req, res.clone());
          trim(COVER_CACHE, MAX_COVERS);
        }
        return res;
      }),
    );
  }
});
