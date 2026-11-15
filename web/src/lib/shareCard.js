import { wikiSummary } from './apis.js';
import wordmarkUrl from '../assets/logo_wordmark.png';

const load = (src) => new Promise((resolve) => {
  const img = new Image();
  img.crossOrigin = 'anonymous';
  img.onload = () => resolve(img);
  img.onerror = () => resolve(null);
  img.src = src;
});

/** Renders a 1080×1350 "spotted" card (photo, names, confidence, time, place, weather) as PNG. */
export async function renderShareCard(species, detection) {
  const W = 1080, H = 1350;
  const c = document.createElement('canvas');
  c.width = W; c.height = H;
  const g = c.getContext('2d');
  await document.fonts.ready;
  const info = await wikiSummary(species.scientific);
  const [photo, wordmark] = await Promise.all([info?.image ? load(info.image) : null, load(wordmarkUrl)]);

  g.fillStyle = '#10283f';
  g.fillRect(0, 0, W, H);
  if (photo) {
    const s = Math.max(W / photo.width, 980 / photo.height);
    g.drawImage(photo, (W - photo.width * s) / 2, (980 - photo.height * s) / 2, photo.width * s, photo.height * s);
  }
  const fade = g.createLinearGradient(0, 520, 0, 1000);
  fade.addColorStop(0, 'rgba(16,40,63,0)');
  fade.addColorStop(1, 'rgba(16,40,63,1)');
  g.fillStyle = fade;
  g.fillRect(0, 0, W, 1000);
  g.fillStyle = '#10283f';
  g.fillRect(0, 1000, W, H - 1000);

  g.fillStyle = '#8fbef1';
  g.font = '700 30px "Manrope Variable", sans-serif';
  g.fillText('I HEARD A', 72, 820);
  g.fillStyle = '#ffffff';
  let size = 92;
  do { g.font = `600 ${size}px "Fraunces Variable", Georgia, serif`; size -= 4; } while (g.measureText(species.common).width > W - 144);
  g.fillText(species.common, 72, 920);
  g.font = 'italic 400 40px "Fraunces Variable", Georgia, serif';
  g.fillStyle = 'rgba(255,255,255,.75)';
  g.fillText(species.scientific, 72, 980);

  const lines = detection ? [
    new Date(detection.time).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' }),
    detection.place ?? `${detection.lat.toFixed(3)}, ${detection.lon.toFixed(3)}`,
    detection.weather ? `${Math.round(detection.weather.tempC)}°C · ${detection.weather.text}` : null,
  ].filter(Boolean) : [];
  g.font = '500 34px "Manrope Variable", sans-serif';
  g.fillStyle = 'rgba(255,255,255,.88)';
  lines.forEach((l, i) => g.fillText(l, 72, 1080 + i * 52));

  if (detection) {
    const pct = `${Math.round(detection.confidence * 100)}%`;
    g.fillStyle = '#2e6ba8';
    roundRect(g, W - 72 - 210, 1052, 210, 110, 26);
    g.fill();
    g.fillStyle = '#fff';
    g.font = '800 52px "Manrope Variable", sans-serif';
    g.textAlign = 'center';
    g.fillText(pct, W - 72 - 105, 1110);
    g.font = '600 20px "Manrope Variable", sans-serif';
    g.fillText('CONFIDENCE', W - 72 - 105, 1142);
    g.textAlign = 'left';
  }

  if (wordmark) {
    g.fillStyle = '#ffffff';
    roundRect(g, 72, H - 150, 330, 92, 20);
    g.fill();
    g.drawImage(wordmark, 92, H - 136, 290, 290 * (wordmark.height / wordmark.width));
  }
  g.fillStyle = 'rgba(255,255,255,.5)';
  g.font = '500 22px "Manrope Variable", sans-serif';
  g.textAlign = 'right';
  g.fillText('Identified on-device with BirdNET', W - 72, H - 112);
  if (photo) g.fillText('Photo: Wikipedia / Wikimedia Commons', W - 72, H - 78);

  return new Promise((resolve) => c.toBlob(resolve, 'image/png'));
}

function roundRect(g, x, y, w, h, r) {
  g.beginPath();
  g.moveTo(x + r, y);
  g.arcTo(x + w, y, x + w, y + h, r);
  g.arcTo(x + w, y + h, x, y + h, r);
  g.arcTo(x, y + h, x, y, r);
  g.arcTo(x, y, x + w, y, r);
  g.closePath();
}
