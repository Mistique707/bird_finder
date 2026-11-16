# Bird Finder

Hear a bird, find out what it is.

Bird Finder listens through your microphone and tells you which birds are singing. It runs
[BirdNET](https://birdnet.cornell.edu) — Cornell's bird sound model, which knows over 6,000
species — right on your device. Your audio never leaves your phone or PC, and identification
works without internet.

There are two versions: an **Android app** for taking outside, and a **desktop app** for
listening from home and going through recordings.

## Download

Grab the latest from the [Releases page](https://github.com/Mistique707/bird_finder/releases):

- **Android** — `BirdFinder.apk`. Open it on your phone and allow installing from unknown sources.
- **Windows** — `BirdFinder-Desktop.zip`. Unzip it and double-click `Start Bird Finder.bat`.
  It opens in your browser (Chrome or Edge work best). Keep the little black window open while
  you use it.

## What it does

**Both apps**

- Live identification from the microphone, checking a new 3-second slice every 1.5 seconds
- A location and season filter, so you get birds that actually live where you are at this time of year
- A bird log with the recording, time, place and weather for every find
- Photos and descriptions from Wikipedia
- Bird names in 21 languages, including Hindi, Marathi, Tamil, Malayalam and Bengali
- Repeat suppression, so a bird singing for ten minutes is logged once
- Light and dark themes

**Android**

- Keeps listening with the screen off
- GPS-tagged detections
- Share a detection as an image card with its audio clip

**Desktop**

- A live spectrogram while you listen
- **Identify a file** — drop in recordings (MP3, AAC/M4A, WAV, FLAC, OGG) and see exactly when
  each bird calls, with a coloured timeline per species
- **Birds near you** — every bird expected at your location this week, from common to very rare.
  Filter by rarity, season (arriving soon, leaving soon), bird group or conservation status, and
  search all species by English, scientific or local name
- **Go hunting** — a daily Bird of the day, a hunt list with a "Target found" alert when you hear
  one, points for rarer birds, and birder levels
- A detail page for every bird: your recording next to a reference call, when it's around your
  area, its conservation status and a map of where it's been seen
- Bird log with stats, filters and CSV/JSON export
- Shareable bird cards

## Tech stack

| | Android | Desktop |
|---|---|---|
| Language & UI | Kotlin, Jetpack Compose (Material 3) | React 19, Vite, hand-written CSS |
| Bird model | BirdNET 2.4 on TensorFlow Lite | BirdNET 2.4 on LiteRT.js (WebAssembly), in a Web Worker |
| Audio | AudioRecord in a foreground service | Web Audio API + AudioWorklet |
| Storage | Room (SQLite) | localStorage, IndexedDB for clips |
| Location | Fused location provider | Browser geolocation or a searched place |
| Online extras | Wikipedia, Xeno-canto, OpenWeatherMap | Wikipedia, Wikidata, Wikimedia Commons, GBIF, Open-Meteo |

Every online extra is optional. Identification itself is always offline.

## How it works

1. The microphone audio is resampled to 48 kHz mono.
2. Every 1.5 seconds, the last 3 seconds go through BirdNET, which scores every species.
3. A second BirdNET model scores how likely each species is at your location in this week of the year, and the two are combined.
4. Anything above your confidence threshold that hasn't been logged in the last few minutes is saved with its clip.

## Building it yourself

**Android** — needs JDK 17+ and the Android SDK. Download the two model files from
[whoBIRD-TFlite](https://github.com/woheller69/whoBIRD-TFlite)
(`BirdNET_GLOBAL_6K_V2.4_Model_FP16.tflite` and `BirdNET_GLOBAL_6K_V2.4_MData_Model_V2_FP16.tflite`)
into `app/src/main/assets/`, then:

```
./gradlew assembleDebug
```

**Desktop** — needs Node.js 20+:

```
cd web
npm install
npm run dev
```

The first run downloads the models for you. `npm run build` makes a static site in `web/dist/`.

## Credits

- [BirdNET](https://birdnet.cornell.edu) by the Cornell Lab of Ornithology and Chemnitz University of Technology
- TensorFlow Lite conversion from [whoBIRD-TFlite](https://github.com/woheller69/whoBIRD-TFlite)
- Photos and text from Wikipedia and Wikimedia Commons; taxonomy and maps from GBIF; weather from Open-Meteo

## License

The BirdNET model is licensed [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/),
so Bird Finder is free and non-commercial.
