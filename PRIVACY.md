# Bird Finder — Privacy Policy

_Last updated: 2026-06-17_

Bird Finder is a personal, non-commercial bird-call identification app. This policy
explains what data it handles. **There is no Bird Finder account, server, or analytics
operated by the developer** — the app runs on your device.

## What stays on your device
- **Microphone audio** is captured and analyzed **on-device** by the BirdNET TFLite model.
  Raw audio is **never uploaded**. Short clips of detections are saved **locally** on your
  phone (in the app's private storage) so you can review them, and are deleted when you
  delete the detection.
- **Detections** (species, confidence, time, coordinates, weather, model, clip path) are
  stored in a **local database** on your device only.
- **GPS location** is read on-device to tag detections and to filter species by region. It
  is stored locally with each detection.

## What is sent to third parties (and why)
The app makes outbound requests only to fetch supporting content. In these requests the app
sends the minimum needed — a **species name** and/or **coordinates** — not your audio or
your detection history:

| Service | Sent | Purpose |
|---------|------|---------|
| **Wikipedia / Wikimedia** | species name; language code | bird photos, descriptions, localized names |
| **Xeno-canto** | species name; your API key | reference call recordings |
| **OpenWeatherMap** (optional) | coordinates; your API key | weather at the time of a detection |

Each of these services has its own privacy policy. The OpenWeatherMap and Xeno-canto
features are optional and only active when you provide a key. You can turn photos and
reference calls off in **Settings → Identification**, and weather off in
**Settings → Advanced**.

## Permissions
- **Microphone** — to listen for and identify bird calls (only while you have started
  listening).
- **Location (fine)** — to tag detections and apply the regional species filter.
- **Notifications** — to show the ongoing "listening" notification for the foreground service.

## Data sharing & sale
The developer does **not** collect, sell, or share your data. Saved clips and the detection
log never leave your device except when **you** explicitly use the Share or Export feature.

## Children
The app is not directed at children and collects no personal information.

## Contact
This is a personal project. For questions, open an issue on the project's repository.
