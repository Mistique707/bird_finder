// Copies the LiteRT wasm runtime + label files into public/ and downloads the BirdNET models
// (CC BY-NC-SA, not committed). Skips downloads that are already present.
import { cpSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';

const MODELS = 'https://raw.githubusercontent.com/woheller69/whoBIRD-TFlite/master/';
const files = ['BirdNET_GLOBAL_6K_V2.4_Model_FP16.tflite', 'BirdNET_GLOBAL_6K_V2.4_MData_Model_V2_FP16.tflite'];
const ASSETS = '../app/src/main/assets';

mkdirSync('public/models/labels', { recursive: true });
rmSync('public/litert-wasm', { recursive: true, force: true });
mkdirSync('public/litert-wasm');
// Only the plain and compat (no relaxed-SIMD) runtimes are used; skip the threaded/JSPI builds.
for (const f of ['litert_wasm_internal', 'litert_wasm_compat_internal']) {
  for (const ext of ['.js', '.wasm']) cpSync(`node_modules/@litertjs/core/wasm/${f}${ext}`, `public/litert-wasm/${f}${ext}`);
}
for (const f of readdirSync(ASSETS).filter((f) => f.startsWith('labels_'))) {
  cpSync(`${ASSETS}/${f}`, `public/models/labels/${f}`);
}

for (const f of files) {
  const dest = `public/models/${f}`;
  if (existsSync(dest)) continue;
  console.log(`Downloading ${f}…`);
  const res = await fetch(MODELS + f);
  if (!res.ok) throw new Error(`${f}: HTTP ${res.status}`);
  writeFileSync(dest, Buffer.from(await res.arrayBuffer()));
}

// LiteRT.js can't run dynamic shapes, and the meta model's tensors are [-1, N]. Pin the
// batch dim to 1 by rewriting each shape_signature int vector (length 2..4, first dim -1).
const meta = `public/models/${files[1]}`;
const buf = readFileSync(meta);
for (let i = 0; i + 8 <= buf.length; i += 4) {
  const n = buf.readInt32LE(i);
  if (n >= 2 && n <= 4 && buf.readInt32LE(i + 4) === -1) buf.writeInt32LE(1, i + 4);
}
writeFileSync(meta, buf);
console.log('Models ready.');
