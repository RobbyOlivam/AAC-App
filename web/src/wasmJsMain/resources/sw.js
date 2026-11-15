// Offline support: keeps the app and its symbol pictures on the device.
// Pages and code: served from the cache at once, refreshed in the background, so an update
// shows on the next launch. Symbols: cached once (they don't change).
"use strict";
const CACHE = "aac-talk-v2";
const SHELL = ["./", "index.html", "platform.js", "aac.js", "manifest.webmanifest", "icons/icon-180.png", "icons/icon-192.png", "icons/icon-512.png", "icons/icon.svg", "symbols/index.tsv"];

self.addEventListener("install", event => {
  event.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", event => {
  event.waitUntil(caches.keys()
    .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});

self.addEventListener("fetch", event => {
  const req = event.request;
  if (req.method !== "GET" || new URL(req.url).origin !== location.origin) return;
  const isSymbol = new URL(req.url).pathname.includes("/symbols/");
  event.respondWith(caches.open(CACHE).then(cache => cache.match(req, { ignoreSearch: true }).then(hit => {
    const network = fetch(req).then(res => {
      if (res.ok) cache.put(req, res.clone());
      return res;
    });
    if (hit) {
      if (!isSymbol) event.waitUntil(network.catch(() => {}));
      return hit;
    }
    return network;
  })));
});

// The page asks for everything it loaded (the app's code) and every symbol to be kept offline.
self.addEventListener("message", event => {
  if (!event.data || event.data.type !== "keep-offline") return;
  event.waitUntil((async () => {
    const cache = await caches.open(CACHE);
    const urls = event.data.urls || [];
    try {
      const index = await (await fetch("symbols/index.tsv")).text();
      for (const line of index.split("\n")) {
        const p = line.split("\t");
        if (p.length >= 2 && p[0] && p[1]) urls.push(`symbols/${p[0]}/${p[1]}.svg`);
      }
    } catch (e) { /* offline: try again next launch */ }
    let done = 0;
    for (let i = 0; i < urls.length; i += 8) {
      await Promise.all(urls.slice(i, i + 8).map(async u => {
        if (await cache.match(u)) return;
        try { const r = await fetch(u); if (r.ok) await cache.put(u, r); } catch (e) { /* skip */ }
      }));
      done = Math.min(urls.length, i + 8);
    }
    const clients = await self.clients.matchAll();
    clients.forEach(c => c.postMessage({ type: "offline-ready", count: done }));
  })());
});
