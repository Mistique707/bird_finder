import { useEffect, useState } from 'react';

export const PUNE = { lat: 18.5204, lon: 73.8567, name: 'Pune, India' };

export const DEFAULT_SETTINGS = {
  theme: 'system',            // system | light | dark
  threshold: 0.7,             // log detections at/above this score
  clipSec: 6,                 // saved clip length
  dedupeMin: 3,               // don't re-log a species within N minutes
  stepSec: 1.5,               // hop between 3 s windows (smaller = more overlap, more CPU)
  sensitivity: 1.0,           // BirdNET logit scale, 0.5–1.5
  micId: '',
  useLocation: true,          // BirdNET location/season model
  locationMode: 'weight',     // weight | filter
  filterMin: 0.03,            // occurrence cut-off for "filter" mode
  locationSource: 'browser',  // browser | manual
  manual: PUNE,
  showPhotos: true,
  referenceCalls: true,
  weather: true,
  regionalLang: '',
  notify: false,              // desktop notification for new species
  xenoCantoKey: '',
};

const read = (k, d) => { try { return JSON.parse(localStorage.getItem(k)) ?? d; } catch { return d; } };
const write = (k, v) => { try { localStorage.setItem(k, JSON.stringify(v)); } catch { /* full or blocked */ } };

/** useState that persists to localStorage. */
export function usePersisted(key, initial) {
  const [value, setValue] = useState(() => (typeof initial === 'function' ? initial() : read(key, initial)));
  useEffect(() => write(key, value), [key, value]);
  return [value, setValue];
}

export const loadSettings = () => ({ ...DEFAULT_SETTINGS, ...read('bf.settings', {}) });

/** History, migrating rows saved by the first web version (key "birdfinder.history"). */
export function loadHistory() {
  const current = read('bf.history', null);
  if (current) return current;
  return read('birdfinder.history', []).map((h) => ({
    ...h, id: String(h.id), place: null, weather: null, fileName: h.source === 'mic' ? null : h.source,
    source: h.source === 'mic' ? 'mic' : 'file', clipId: null, model: 'BirdNET GLOBAL 6K V2.4 FP16',
  }));
}
export const saveSettings = (s) => write('bf.settings', s);

// ---- Audio clips in IndexedDB (localStorage is far too small for WAVs) ----
let dbPromise;
function db() {
  dbPromise ??= new Promise((resolve, reject) => {
    const req = indexedDB.open('birdfinder', 1);
    req.onupgradeneeded = () => req.result.createObjectStore('clips');
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
  return dbPromise;
}
async function tx(mode, fn) {
  const store = (await db()).transaction('clips', mode).objectStore('clips');
  return new Promise((resolve, reject) => {
    const req = fn(store);
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}
export const putClip = (id, blob) => tx('readwrite', (s) => s.put(blob, id));
export const getClip = (id) => tx('readonly', (s) => s.get(id));
export const deleteClip = (id) => tx('readwrite', (s) => s.delete(id));
export const clearClips = () => tx('readwrite', (s) => s.clear());

export function downloadBlob(blob, name) {
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 1000);
}
