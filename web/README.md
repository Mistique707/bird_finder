# Bird Finder — Desktop

The PC version of Bird Finder. It's a React app that runs the BirdNET model directly in the
browser, so there's no server and no audio ever leaves your computer.

Just want to use it? Download `BirdFinder-Desktop.zip` from the
[Releases page](https://github.com/Mistique707/bird_finder/releases) instead.

## Running it

You need Node.js 20 or newer.

```
npm install
npm run dev
```

It opens at http://localhost:5173. The first run downloads the BirdNET models (about 40 MB) into
`public/models`. The microphone only works on `localhost` or over HTTPS.

`npm run build` creates a static site in `dist/` that you can host anywhere.

## Pages

- **Listen** — live identification from the mic, with a spectrogram and level meter. `Space` starts and stops.
- **Identify a file** — drag in recordings and see when each bird calls.
- **Birds near you** — what to expect at your location this week, with rarity, season, group and conservation filters, plus the hunt list, Bird of the day, points and levels.
- **Bird log** — everything you've found, with clips, stats and CSV/JSON export.
- **Settings** — sensitivity, clip length, microphone, location, filter style, language and theme.

## Project layout

```
src/
  App.jsx            app shell, listening session, bird log
  pages/             one file per page
  components/        species panel, clip player, shared UI
  lib/engine.js      talks to the model worker, labels, local names
  lib/worker.js      runs BirdNET with LiteRT.js off the main thread
  lib/audio.js       microphone capture, file decoding, WAV export
  lib/apis.js        Wikipedia, Wikidata, GBIF, Open-Meteo
  lib/explore.js     rarity tiers, seasons, groups, points
desktop/             launcher for the packaged Windows build
scripts/setup.mjs    downloads and prepares the models
```

`node scripts/check-explore.mjs` runs a quick check of the rarity and season logic.

## Notes

- LiteRT.js can't run models with a dynamic batch size, so `setup.mjs` fixes the location
  model's batch dimension to 1.
- The location model (V2) takes the plain week number, 1 to 48.
