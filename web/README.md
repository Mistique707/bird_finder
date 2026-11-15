# Bird Finder — Desktop (web)

React + Vite desktop companion to the Android app. BirdNET runs **in the browser** via
[LiteRT.js](https://www.npmjs.com/package/@litertjs/core) in a Web Worker — no server, and
audio never leaves the machine.

```bash
cd web
npm install
npm run dev        # first run downloads the BirdNET models (~40 MB) into public/models
```

Opens http://localhost:5173. Microphone access requires `localhost` or HTTPS.

## Features

- **Listen** — live mic ID (3 s windows, configurable step), live spectrogram, level meter,
  repeat suppression, post-roll clips, `Space` to start/stop.
- **Identify a file** — drag-and-drop batches of MP3 / AAC / M4A / WAV / FLAC / OGG / Opus;
  spectrogram with per-species timelines, click a segment to hear it, add results to the log.
- **Birds near you** — BirdNET's location model lists species expected at your place for any
  week of the year, with life-list progress.
- **Bird log** — stats, 14-day activity, detections or species-list view, filters,
  multi-select delete, CSV/JSON export. Clips are stored in IndexedDB.
- **Species panel** — Wikipedia photo + summary, your recording with the detected window
  highlighted, reference calls (Wikimedia Commons; Xeno-canto with a key), seasonality at
  your location, IUCN status (Wikidata), taxonomy, nearby records and range map (GBIF),
  shareable PNG card.
- **Settings** — threshold, clip length, repeat window, mic picker, location (device or a
  searched place), location filter (weighted / hard filter), BirdNET sensitivity, window
  step, regional names (21 languages), weather, desktop notifications, theme.

All online extras are keyless (Wikipedia, Wikidata, Commons, GBIF, Open-Meteo,
BigDataCloud) except optional Xeno-canto.

`npm run build` produces a static site in `dist/` you can host anywhere.

## Notes

- `scripts/setup.mjs` pins the meta model's dynamic batch dim (`-1`) to `1`, since LiteRT.js
  can't run dynamic shapes.
- The V2 meta model takes the **raw week number (1–48)**. The `cos(week·7.5°)+1` encoding is
  for the older V1 meta model; feeding it to V2 suppresses seasonal species.
