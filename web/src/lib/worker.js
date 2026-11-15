// Runs BirdNET off the UI thread so listening/spectrogram animation never stutters.
//
// Audio model: Float32 [1, 144000] @ 48 kHz -> [1, 6522] logits (it normalises input itself).
// Meta model:  [lat, lon, week 1..48] -> [1, 6522] occurrence probability for that place/week.
// Note the V2 meta model wants the RAW week; cos(week*7.5°)+1 is the V1 encoding and zeroes
// out seasonal species when fed to V2.
import { loadLiteRt, loadAndCompile, Tensor } from '@litertjs/core';
import { colormap } from './colormap.js';

// LiteRT loads its Emscripten glue with importScripts(), which throws in module workers.
// Synchronous XHR is allowed in workers, so emulate it.
self.importScripts = (url) => {
  const xhr = new XMLHttpRequest();
  xhr.open('GET', url, false);
  xhr.send();
  (0, eval)(xhr.responseText);
};

let audioModel, metaModel, prior = null;

async function run(model, data, shape) {
  const input = new Tensor(data, shape);
  const [out] = await model.run(input);
  input.delete();
  const arr = Float32Array.from(out.toTypedArray());
  out.delete();
  return arr;
}

const handlers = {
  async init({ base }) {
    // Emscripten resolves the .wasm relative to the worker script unless told otherwise.
    self.Module = { locateFile: (f) => `${base}litert-wasm/${f}` };
    await loadLiteRt(`${base}litert-wasm/`);
    [audioModel, metaModel] = await Promise.all([
      loadAndCompile(`${base}models/BirdNET_GLOBAL_6K_V2.4_Model_FP16.tflite`, { accelerator: 'wasm' }),
      loadAndCompile(`${base}models/BirdNET_GLOBAL_6K_V2.4_MData_Model_V2_FP16.tflite`, { accelerator: 'wasm' }),
    ]);
    return true;
  },

  /** Occurrence probabilities for a place + week (also used by the Explore page). */
  occurrence({ lat, lon, week }) {
    return run(metaModel, new Float32Array([lat, lon, week]), [1, 3]);
  },

  /** All 48 weeks for every class, week-major: out[(w - 1) * n + i]. */
  async occurrenceYear({ lat, lon }) {
    let out;
    for (let w = 1; w <= 48; w++) {
      const p = await handlers.occurrence({ lat, lon, week: w });
      out ??= new Float32Array(48 * p.length);
      out.set(p, (w - 1) * p.length);
    }
    return out;
  },

  async setPrior({ lat, lon, week }) {
    prior = await handlers.occurrence({ lat, lon, week });
    return true;
  },

  /**
   * mode: 'off' | 'weight' (score × occurrence, like the Android app) |
   *       'filter' (drop species below filterMin occurrence, BirdNET-Analyzer style).
   * sensitivity: BirdNET's logit scale (0.5–1.5); >1 = more detections.
   * Returns [index, score] pairs >= floor, best first.
   */
  async classify({ samples, mode, sensitivity, filterMin, floor }) {
    const logits = await run(audioModel, samples, [1, samples.length]);
    const out = [];
    for (let i = 0; i < logits.length; i++) {
      let p = 1 / (1 + Math.exp(-sensitivity * logits[i]));
      if (prior && mode === 'weight') p *= prior[i];
      if (prior && mode === 'filter' && prior[i] < filterMin) p = 0;
      if (p >= floor) out.push([i, p]);
    }
    return out.sort((a, b) => b[1] - a[1]);
  },

  /** Magnitude spectrogram as RGBA pixels (width × height), 0–12 kHz, for a whole file. */
  spectrogram({ samples, width, height }) {
    const N = 1024, half = N / 2, maxBin = Math.floor((12000 / 24000) * half);
    const hop = Math.max(1, Math.floor((samples.length - N) / width));
    const win = Float32Array.from({ length: N }, (_, i) => 0.5 - 0.5 * Math.cos((2 * Math.PI * i) / N));
    const cols = [];
    let peak = 1e-9;
    const re = new Float32Array(N), im = new Float32Array(N);
    for (let x = 0; x < width; x++) {
      const start = x * hop;
      for (let i = 0; i < N; i++) { re[i] = (samples[start + i] || 0) * win[i]; im[i] = 0; }
      fft(re, im);
      const col = new Float32Array(maxBin);
      for (let b = 0; b < maxBin; b++) {
        col[b] = Math.log10(1 + Math.hypot(re[b], im[b]));
        if (col[b] > peak) peak = col[b];
      }
      cols.push(col);
    }
    const px = new Uint8ClampedArray(width * height * 4);
    for (let x = 0; x < width; x++) {
      for (let y = 0; y < height; y++) {
        const bin = Math.floor(((height - 1 - y) / height) * maxBin);
        const [r, g, b] = colormap(Math.max(0, (cols[x][bin] / peak - 0.35) / 0.65));
        const o = (y * width + x) * 4;
        px[o] = r; px[o + 1] = g; px[o + 2] = b; px[o + 3] = 255;
      }
    }
    return px;
  },
};

/** In-place iterative radix-2 FFT. */
function fft(re, im) {
  const n = re.length;
  for (let i = 1, j = 0; i < n; i++) {
    let bit = n >> 1;
    for (; j & bit; bit >>= 1) j ^= bit;
    j ^= bit;
    if (i < j) { [re[i], re[j]] = [re[j], re[i]]; [im[i], im[j]] = [im[j], im[i]]; }
  }
  for (let len = 2; len <= n; len <<= 1) {
    const ang = (-2 * Math.PI) / len, wr = Math.cos(ang), wi = Math.sin(ang);
    for (let i = 0; i < n; i += len) {
      let cr = 1, ci = 0;
      for (let k = 0; k < len / 2; k++) {
        const a = i + k, b = a + len / 2;
        const tr = re[b] * cr - im[b] * ci, ti = re[b] * ci + im[b] * cr;
        re[b] = re[a] - tr; im[b] = im[a] - ti; re[a] += tr; im[a] += ti;
        [cr, ci] = [cr * wr - ci * wi, cr * wi + ci * wr];
      }
    }
  }
}

self.onmessage = async ({ data: { id, type, args } }) => {
  try {
    const result = await handlers[type](args);
    self.postMessage({ id, result }, result?.buffer ? [result.buffer] : []);
  } catch (e) {
    self.postMessage({ id, error: e?.message ?? String(e) });
  }
};
