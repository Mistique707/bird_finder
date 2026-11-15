// Main-thread handle on the BirdNET worker, plus species labels.
export const SAMPLE_RATE = 48000;
export const WINDOW = 144000; // 3 s — fixed by the model
export const MODEL = 'BirdNET GLOBAL 6K V2.4 FP16';

const BASE = import.meta.env.BASE_URL;
const worker = new Worker(new URL('./worker.js', import.meta.url), { type: 'module' });
const pending = new Map();
let nextId = 0;
worker.onmessage = ({ data: { id, result, error } }) => {
  const p = pending.get(id);
  pending.delete(id);
  error ? p.reject(new Error(error)) : p.resolve(result);
};
function call(type, args, transfer = []) {
  return new Promise((resolve, reject) => {
    const id = nextId++;
    pending.set(id, { resolve, reject });
    worker.postMessage({ id, type, args }, transfer);
  });
}

export let labels = [];

function parseLabels(text) {
  return text.split('\n').map((l) => l.trim()).filter(Boolean).map((l, index) => {
    const ix = l.indexOf('_');
    const scientific = ix > 0 ? l.slice(0, ix) : l;
    const common = ix > 0 ? l.slice(ix + 1) : l;
    // Non-bird tags (Dog, Engine, Human vocal…) have no space in the "scientific" part.
    return { index, scientific, common, bird: scientific.includes(' ') && scientific.toLowerCase() !== common.toLowerCase() };
  });
}

export async function loadEngine() {
  const [text] = await Promise.all([
    fetch(`${BASE}models/labels/labels_en.txt`).then((r) => r.text()),
    call('init', { base: BASE }),
  ]);
  labels = parseLabels(text);
}

/** BirdNET's 48-week calendar (4 "weeks" per month). */
export const weekOf = (date = new Date()) => {
  const doy = Math.ceil((date - Date.UTC(date.getUTCFullYear(), 0, 0)) / 864e5);
  return Math.min(48, Math.max(1, Math.ceil((doy * 48) / 366)));
};
export const weekLabel = (w) => {
  const month = new Date(2025, Math.floor((w - 1) / 4), 1).toLocaleString(undefined, { month: 'short' });
  return `${month}, wk ${((w - 1) % 4) + 1}`;
};

export const setPrior = (lat, lon, week) => call('setPrior', { lat, lon, week });

/** Occurrence probability per species for a place/week, birds only, best first. */
export async function occurrence(lat, lon, week) {
  const probs = await call('occurrence', { lat, lon, week });
  return labels.filter((l) => l.bird).map((l) => ({ ...l, p: probs[l.index] })).sort((a, b) => b.p - a.p);
}

/** Occurrence of one species at a place for each of the 48 weeks — "when is it around?". */
export async function seasonality(scientific, lat, lon) {
  const label = labels.find((l) => l.scientific === scientific);
  if (!label) return null;
  const out = [];
  for (let w = 1; w <= 48; w++) out.push((await call('occurrence', { lat, lon, week: w }))[label.index]);
  return out;
}

/**
 * Classify one 3 s window. Returns bird detections with score >= floor (so the UI can show
 * "possible" matches below the logging threshold).
 */
export async function classify(samples, s, floor = 0.1) {
  const pairs = await call('classify', {
    samples, floor: Math.min(floor, s.threshold), mode: s.useLocation ? s.locationMode : 'off',
    sensitivity: s.sensitivity, filterMin: s.filterMin,
  });
  return pairs.filter(([i]) => labels[i].bird).map(([i, confidence]) => ({ ...labels[i], confidence }));
}

export const spectrogram = (samples, width, height) =>
  call('spectrogram', { samples, width, height }).then((px) => new ImageData(px, width, height));

// ---- Regional names: bundled BirdNET translations, else Wikipedia language links ----
export const LANGUAGES = [
  ['', 'English only'],
  ['hi', 'Hindi (हिन्दी)'], ['mr', 'Marathi (मराठी)'], ['ta', 'Tamil (தமிழ்)'], ['te', 'Telugu (తెలుగు)'],
  ['bn', 'Bengali (বাংলা)'], ['kn', 'Kannada (ಕನ್ನಡ)'], ['gu', 'Gujarati (ગુજરાતી)'], ['pa', 'Punjabi (ਪੰਜਾਬੀ)'],
  ['ml', 'Malayalam (മലയാളം)'], ['es', 'Spanish (Español)'], ['fr', 'French (Français)'], ['de', 'German (Deutsch)'],
  ['it', 'Italian (Italiano)'], ['nl', 'Dutch (Nederlands)'], ['ru', 'Russian (Русский)'], ['ar', 'Arabic (العربية)'],
  ['zh', 'Chinese (中文)'], ['ja', 'Japanese (日本語)'], ['ko', 'Korean (한국어)'], ['th', 'Thai (ไทย)'],
];
const BUNDLED = new Set(['ar', 'de', 'es', 'fr', 'it', 'ja', 'ko', 'ml', 'nl', 'ru', 'th', 'zh']);
const bundledCache = new Map();
const wikiNameCache = new Map();

export async function regionalName(scientific, common, lang) {
  if (!lang) return null;
  if (BUNDLED.has(lang)) {
    if (!bundledCache.has(lang)) {
      bundledCache.set(lang, fetch(`${BASE}models/labels/labels_${lang}.txt`).then((r) => r.text())
        .then((t) => new Map(parseLabels(t).map((l) => [l.scientific, l.common]))));
    }
    const name = (await bundledCache.get(lang)).get(scientific);
    return name && name !== common ? name : null;
  }
  const key = `${lang}:${scientific}`;
  if (!wikiNameCache.has(key)) {
    const url = `https://en.wikipedia.org/w/api.php?action=query&prop=langlinks&format=json&redirects=1&origin=*&lllang=${lang}&titles=${encodeURIComponent(scientific)}`;
    wikiNameCache.set(key, fetch(url).then((r) => r.json())
      .then((j) => Object.values(j.query?.pages ?? {})[0]?.langlinks?.[0]?.['*']?.replace(/\s*\([^)]*\)$/, '') || null) // drop "(bird)"-style disambiguation
      .catch(() => null));
  }
  return wikiNameCache.get(key);
}
