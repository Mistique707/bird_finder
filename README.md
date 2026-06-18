# Bird Finder

Native Android app that listens to the microphone in real time, identifies bird
species **on-device** with BirdNET TFLite (no internet required for inference),
and logs every detection with timestamp, GPS, model metadata, weather, and a
saved audio clip into a local Room database with a history screen.

- **minSdk:** 26 — **targetSdk:** 35 — **Compose**, **Room**, **coroutines/Flow**
- Default location: **Pune, India** (18.5204, 73.8567)
- License of *your* app: you choose. The BirdNET model weights are CC BY-NC-SA
  (non-commercial) — the classifier lives behind a Kotlin interface so it can be
  swapped for a commercial-friendly model later without touching the rest.

## Publishing checklist (free, non-commercial only)

This app is fine to publish **for free with no ads / IAP / paid tier** — the BirdNET model
is **CC BY-NC-SA (non-commercial)**. Any monetization first requires swapping BirdNET for a
commercial-friendly model (e.g. Perch) via the `BirdClassifier` interface.

Before a Play Store release:
- [x] **Attribution** — an in-app **Settings → About & licenses** screen credits BirdNET,
      whoBIRD, Xeno-canto, Wikipedia/Wikimedia and OpenWeatherMap. Per-recording Xeno-canto
      attribution shows on each call; a Wikipedia (CC BY-SA) credit shows on the detail screen.
- [x] **Privacy policy** — see [PRIVACY.md](PRIVACY.md); host it at a public URL and put that
      URL in `AboutScreen.PRIVACY_URL` and the Play Console listing.
- [ ] **Move API keys off-device** — `OWM_API_KEY` / `XENOCANTO_API_KEY` compile into the
      APK as readable strings. For a public release, either (a) ship without keys and let users
      paste their own (already supported in Settings → Advanced), or (b) proxy the calls through
      a small backend that holds the keys. **Rotate any key that has been shared.**
- [ ] **Data safety form** — declare that audio is processed on-device and that species
      names / coordinates are sent to Wikipedia, Xeno-canto and OpenWeatherMap.
- [ ] **Foreground service** — be ready to justify `FOREGROUND_SERVICE_MICROPHONE` (and
      location) in the Play Console; capture is clearly user-initiated via the mic button.
- [ ] **Confirm rights to bundled art** (logo, background, bird PNGs) and that the name
      "Bird Finder" doesn't conflict with an existing trademark.

> Not legal advice — verify the current terms of each service before publishing.

### Features

- Live listening with a one-tap mic button, level pulse, and on-device BirdNET ID.
- Each detection logged with **GPS** (high-accuracy fused fix), UTC time, weather,
  model version, and a saved clip.
- **Bird photos** (Wikipedia) and **reference calls** (Xeno-canto) shown on the
  detail screen so you can compare your recording against a known call.
- **Repeat suppression**: the same species isn't re-logged within a configurable
  window (default 3 min) so continuous callers don't flood the log.
- **Share a detection** as a "wrapped"-style card image (photo + name + confidence
  + time + place + weather) bundled with the audio clip.
- History with photo thumbnails, stats, date + species filters, swipe-to-delete.
- Export all detections to **CSV**, light/dark/system theme.

### Online vs. offline

Inference is **fully offline**. Three features make outbound calls and degrade
gracefully when offline or disabled in Settings:

| Feature | Service | Key? | Notes |
|---------|---------|------|-------|
| Bird photo | Wikipedia REST + action API | no | species name → thumbnail; cached |
| Reference call | Xeno-canto API **v3** | **yes (free)** | species name → sample recording; streamed |
| Weather | OpenWeatherMap | yes (free) | skipped if absent |

Only the species name / coordinates are sent. Toggle photos and reference calls
off under **Settings → Identification**.

> **Reference calls need a free Xeno-canto key.** Their old keyless v2 API was
> retired; v3 returns `401` without a `key`. Get one at
> https://xeno-canto.org/account and either put `XENOCANTO_API_KEY=…` in
> `local.properties` or paste it in-app under **Settings → Advanced**. Photos do
> **not** need any key.

### Audio length vs. the model

The BirdNET model input is fixed at **3 seconds** (144 000 samples @ 48 kHz), so
identification always runs on 3 s windows. The **saved clip** is decoupled and
defaults to **6 s** (configurable 3/6/9 s in Settings → Detection) so you have
more context to review and compare. Inference correctness is unaffected.

## 1. Place the model files

The TFLite models and labels are not in this repo. Download them and drop into
`app/src/main/assets/`:

| File | Source |
|------|--------|
| `BirdNET_GLOBAL_6K_V2.4_Model_FP16.tflite` | https://github.com/woheller69/whoBIRD-TFlite |
| `BirdNET_GLOBAL_6K_V2.4_MData_Model_V2_FP16.tflite` | https://github.com/woheller69/whoBIRD-TFlite |
| `labels_en.txt` | `app/src/main/assets/labels_en.txt` from https://github.com/woheller69/whoBIRD |

These files are excluded from git via `.gitignore`. Their license (CC BY-NC-SA
4.0) is non-commercial. Don't redistribute them in a commercial product.

To swap in a different model, implement the
[`BirdClassifier`](app/src/main/java/com/example/birdfinder/classify/BirdClassifier.kt)
interface and substitute it in
[`DetectionPipeline`](app/src/main/java/com/example/birdfinder/pipeline/DetectionPipeline.kt).
Audio capture, sliding-window framing, persistence, and UI stay the same.

## 2. (Optional) OpenWeatherMap API key

Weather columns (`weatherTempC`, `weatherCondition`) are filled by a single OWM
call per 10 minutes per coordinate. Skipped silently if no key is configured.

Create `local.properties` at the project root (gitignored already):

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
OWM_API_KEY=your_open_weather_map_key_here
```

Get a free key at https://openweathermap.org/api.

## 3. Build & install on your phone

You need JDK 17+ and the Android SDK. The Gradle wrapper bootstraps Gradle
itself on first run.

```powershell
# In PowerShell, from the project root:
.\gradlew.bat assembleDebug

# Or from bash on Windows:
./gradlew assembleDebug
```

The unsigned debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

Install it on a connected device with USB debugging enabled:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Or open the project in Android Studio (Iguana / Koala or newer) and run with the
play button.

## 4. Using it

1. Launch **Bird Finder**.
2. Grant **microphone**, **fine location**, and **notifications** when prompted.
3. Tap **Start** on the Listen tab — a persistent notification appears and the
   pipeline stays alive with the screen off.
4. Play a bird call near the phone. Within ~3 seconds the species shows in the
   live feed and is logged to History.
5. Open **History** to browse, filter by species substring, or pick a date
   range. Tap a row to see full metadata and **play back** the saved WAV clip.

## 5. Architecture at a glance

```
mic ┐
    │  AudioCaptureSource  (48 kHz mono PCM 16-bit)
    ▼
SlidingWindow              (3 s inference window + 6 s clip, every 1.5 s — int16-cast-as-float)
    │
    ▼
BirdNetClassifier          (audio model → sigmoid; meta model multiplied as prior)
    │
    ▼  filtered ≥ threshold (default 0.7)
DetectionPipeline          (enrich with high-accuracy GPS + OWM, save 6 s WAV, write Room rows)
    │
    ▼
Room (detections)  ─▶  HistoryScreen / DetailScreen ◀─ BirdMediaClient (Wikipedia + Xeno-canto)
```

## 6. Critical model I/O contract

If you swap classifiers, mind these:

- **Sample rate:** 48 000 Hz mono.
- **Window:** float32 `[1, 144000]` (3 s at 48 kHz).
- **Normalization (BirdNET-specific):** int16 PCM samples are cast to float
  **without** dividing by 32768. Values stay in `[-32768, 32767]`. Getting this
  wrong silently produces near-zero confidences — verified against the upstream
  whoBIRD inference loop.
- **Output:** `[1, 6522]` raw logits → per-class sigmoid.
- **Meta model:** input `[lat, lon, cos(toRadians(week*7.5))+1.0]` where
  `week = ceil(dayOfYear*48/366)` ∈ [1, 48]. Output is element-wise multiplied
  with the sigmoid'd audio output before thresholding.

## 7. What's NOT in v1 (TODO hooks)

- Source separation (e.g. MixIT pre-stage).
- Cloud sync / multi-device.
- Auth.
- Play Store packaging.
- Model fine-tuning.

## Attribution

- **BirdNET** weights: © Cornell Lab of Ornithology et al., CC BY-NC-SA 4.0.
- **whoBIRD** (whoBIRD-TFlite + whoBIRD app): © `woheller69`, GPLv3 — used
  here as a technical reference only; no code copied.
