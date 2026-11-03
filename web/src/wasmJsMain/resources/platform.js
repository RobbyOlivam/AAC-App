// Browser services for the web app (called from Kotlin): storage, speech, sounds and files.
// Everything stays on the device: IndexedDB for boards, settings and media; no network calls
// except fetching the app's own files (symbols) from where it was installed.
"use strict";

window.aac = (() => {
  // ---- Storage (IndexedDB: one object store, key → Uint8Array) -------------------------------
  let dbPromise = null;
  function db() {
    if (!dbPromise) {
      dbPromise = new Promise((resolve, reject) => {
        const req = indexedDB.open("aac-talk", 1);
        req.onupgradeneeded = () => req.result.createObjectStore("files");
        req.onsuccess = () => resolve(req.result);
        req.onerror = () => reject(req.error);
      });
      // Ask the browser not to clear our data when space is short (installed apps get this).
      if (navigator.storage && navigator.storage.persist) navigator.storage.persist().catch(() => {});
    }
    return dbPromise;
  }
  function tx(mode, work) {
    return db().then(d => new Promise((resolve, reject) => {
      const t = d.transaction("files", mode);
      const result = work(t.objectStore("files"));
      t.oncomplete = () => resolve(result.result === undefined ? null : result.result);
      t.onerror = () => reject(t.error);
    }));
  }
  const store = {
    get: key => tx("readonly", s => s.get(key)).then(v => v || null),
    put: (key, bytes) => tx("readwrite", s => s.put(bytes, key)).then(() => null),
    remove: key => tx("readwrite", s => s.delete(key)).then(() => null),
    keys: prefix => tx("readonly", s => s.getAllKeys()).then(all => all.filter(k => k.startsWith(prefix)).join("\n")),
  };

  // ---- Speech (the device's own voices; on iPad these are Apple's) ---------------------------
  let voices = [];
  let onVoices = null;
  function loadVoices() {
    voices = speechSynthesis.getVoices();
    if (onVoices) onVoices();
  }
  if ("speechSynthesis" in window) {
    loadVoices();
    speechSynthesis.addEventListener("voiceschanged", loadVoices);
  }
  const speech = {
    available: () => "speechSynthesis" in window,
    voices: () => voices.map(v => [v.voiceURI, v.name, v.lang].join("\t")).join("\n"),
    onVoicesChanged: callback => { onVoices = callback; },
    speak: (text, rate, pitch, volume, voiceId) => {
      if (!("speechSynthesis" in window)) return;
      const u = new SpeechSynthesisUtterance(text);
      u.rate = rate; u.pitch = pitch; u.volume = volume;
      const v = voices.find(x => x.voiceURI === voiceId);
      if (v) { u.voice = v; u.lang = v.lang; }
      speechSynthesis.cancel(); // like a talker: the newest message wins
      speechSynthesis.speak(u);
    },
    stop: () => { if ("speechSynthesis" in window) speechSynthesis.cancel(); },
  };

  // ---- Recorded sounds (from storage) --------------------------------------------------------
  let audio = null;
  const sounds = {
    play: (key, volume) => store.get(key).then(bytes => {
      if (!bytes) return;
      if (audio) { audio.pause(); URL.revokeObjectURL(audio.src); }
      audio = new Audio(URL.createObjectURL(new Blob([bytes])));
      audio.volume = volume;
      audio.play().catch(() => {});
    }),
  };

  // ---- Files (open from Files / save to Files) -----------------------------------------------
  const files = {
    // Resolves to the chosen file's name, then its bytes (two properties), or null if cancelled.
    pick: accept => new Promise(resolve => {
      const input = document.createElement("input");
      input.type = "file";
      if (accept) input.accept = accept;
      input.onchange = () => {
        const f = input.files && input.files[0];
        if (!f) { resolve(null); return; }
        f.arrayBuffer().then(buf => resolve({ name: f.name, bytes: new Uint8Array(buf) }));
      };
      input.oncancel = () => resolve(null);
      input.click();
    }),
    save: (name, bytes, mime) => {
      const file = new File([bytes], name, { type: mime });
      if (navigator.canShare && navigator.canShare({ files: [file] })) {
        return navigator.share({ files: [file] }).then(() => true, () => false);
      }
      const a = document.createElement("a");
      a.href = URL.createObjectURL(file);
      a.download = name;
      a.click();
      setTimeout(() => URL.revokeObjectURL(a.href), 10000);
      return Promise.resolve(true);
    },
  };

  // ---- The app's own files (symbols), cached for offline use by the service worker -----------
  const fetchBytes = url => fetch(url).then(r => r.ok ? r.arrayBuffer().then(b => new Uint8Array(b)) : null, () => null);

  // Bytes cross to Kotlin as Latin-1 strings (fast bulk copies).
  const bytes = {
    toLatin1: u8 => {
      let out = "";
      for (let i = 0; i < u8.length; i += 32768) out += String.fromCharCode.apply(null, u8.subarray(i, i + 32768));
      return out;
    },
    fromLatin1: s => {
      const u8 = new Uint8Array(s.length);
      for (let i = 0; i < s.length; i++) u8[i] = s.charCodeAt(i);
      return u8;
    },
  };

  const isStandalone = () => window.matchMedia("(display-mode: standalone)").matches || navigator.standalone === true;

  return { store, speech, sounds, files, fetchBytes, bytes, isStandalone };
})();

// Offline: register the service worker, then ask it to keep the app's code and every symbol.
if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("sw.js").then(() => navigator.serviceWorker.ready).then(reg => {
      // Wait a little, so the first pages' pictures load before the background download starts.
      setTimeout(() => {
        const urls = performance.getEntriesByType("resource").map(e => e.name).filter(u => u.startsWith(location.origin));
        reg.active && reg.active.postMessage({ type: "keep-offline", urls });
      }, 20000);
    }).catch(() => {});
  });
  navigator.serviceWorker.addEventListener("message", e => {
    if (e.data && e.data.type === "offline-ready") window.aacOfflineReady = true;
  });
}
