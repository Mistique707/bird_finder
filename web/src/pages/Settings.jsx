import { useEffect, useRef, useState } from 'react';
import { ChevronDown, Crosshair, Download, MapPin, RotateCcw, Search, Trash2 } from 'lucide-react';
import { LANGUAGES } from '../lib/engine.js';
import { listMics } from '../lib/audio.js';
import { searchPlaces } from '../lib/apis.js';
import { DEFAULT_SETTINGS, clearClips, downloadBlob } from '../lib/store.js';
import { Segmented, Toggle, fmtCoords } from '../components/common.jsx';

function sensitivityLabel(t) {
  if (t >= 0.85) return ['Strict', 'Only very confident matches'];
  if (t >= 0.6) return ['Balanced', 'Recommended for most places'];
  return ['Lenient', 'More matches, more false positives'];
}

export default function Settings({ settings, updateSettings, history, location, locate, deleteDetections, showToast, mic }) {
  const [mics, setMics] = useState([]);
  const [advanced, setAdvanced] = useState(false);
  const [confirmClear, setConfirmClear] = useState(false);
  const s = settings;
  const [label, hint] = sensitivityLabel(s.threshold);

  const refreshMics = () => listMics().then(setMics).catch(() => {});
  useEffect(() => {
    refreshMics();
    navigator.mediaDevices?.addEventListener?.('devicechange', refreshMics);
    return () => navigator.mediaDevices?.removeEventListener?.('devicechange', refreshMics);
  }, [mic.state]);
  const micsNeedPermission = mics.length > 0 && mics.every((m) => !m.label);

  async function setNotify(on) {
    if (on && Notification.permission !== 'granted') {
      const p = await Notification.requestPermission();
      if (p !== 'granted') return showToast('Notifications are blocked for this site');
    }
    updateSettings({ notify: on });
  }

  return (
    <div className="settings">
      <header className="page-head"><div><h1>Settings</h1><p>Changes save automatically.</p></div></header>

      <div className="settings-cols">
        <div>
          <Section title="Detection">
            <div className="field">
              <div className="field-head"><b>Sensitivity</b><span className="pill">{label} · {Math.round(s.threshold * 100)}%</span></div>
              <input type="range" min="0.3" max="0.95" step="0.05" value={s.threshold} onChange={(e) => updateSettings({ threshold: +e.target.value })} aria-label="Confidence threshold" />
              <small>{hint}. Birds are logged when BirdNET’s score reaches this threshold.</small>
            </div>
            <div className="field">
              <b>Recording length</b>
              <Segmented label="Recording length" value={s.clipSec} onChange={(v) => updateSettings({ clipSec: v })} options={[[3, '3 s'], [6, '6 s'], [9, '9 s']]} />
              <small>Saved clip length. Identification always analyses 3-second windows.</small>
            </div>
            <div className="field">
              <b>Ignore repeats of the same bird</b>
              <Segmented label="Repeat window" value={s.dedupeMin} onChange={(v) => updateSettings({ dedupeMin: v })} options={[[0, 'Off'], [1, '1 min'], [3, '3 min'], [5, '5 min'], [10, '10 min']]} />
              <small>Keeps the log tidy when a bird calls continuously.</small>
            </div>
          </Section>

          <Section title="Microphone">
            <div className="field">
              <b>Audio input</b>
              <select value={s.micId} onChange={(e) => updateSettings({ micId: e.target.value })}>
                <option value="">System default</option>
                {mics.map((m, i) => <option key={m.deviceId} value={m.deviceId}>{m.label || `Microphone ${i + 1}`}</option>)}
              </select>
              <small>
                {micsNeedPermission ? 'Start listening once to see device names. ' : ''}
                USB or shotgun mics give far better results than a laptop’s built-in mic. Applies the next time you start listening.
                Echo cancellation, noise suppression and auto-gain are always off, since they distort birdsong.
              </small>
            </div>
          </Section>

          <Section title="Location">
            <div className="field">
              <b>Where you’re listening</b>
              <Segmented label="Location source" value={s.locationSource} onChange={(v) => updateSettings({ locationSource: v })}
                options={[['browser', 'Use this device’s location'], ['manual', 'Choose a place']]} />
            </div>
            {s.locationSource === 'manual' && <PlacePicker value={s.manual} onPick={(p) => updateSettings({ manual: p })} />}
            {location && (
              <div className="loc-now">
                <MapPin size={16} />
                <span><b>{location.name ?? 'Unknown place'}</b><small>{fmtCoords(location.lat, location.lon)}{location.source === 'fallback' ? ' · location unavailable, using the fallback place' : ''}</small></span>
                {s.locationSource === 'browser' && <button className="btn ghost sm" onClick={locate}><Crosshair size={14} /> Refresh</button>}
              </div>
            )}
            <Toggle label="Location & season filter" hint="Uses BirdNET’s location model to favour birds that actually occur here at this time of year." checked={s.useLocation} onChange={(v) => updateSettings({ useLocation: v })} />
            {s.useLocation && (
              <div className="field">
                <b>Filter style</b>
                <Segmented label="Filter style" value={s.locationMode} onChange={(v) => updateSettings({ locationMode: v })}
                  options={[['weight', 'Weighted'], ['filter', 'Hard filter']]} />
                <small>{s.locationMode === 'weight'
                  ? 'Score × how likely the bird is here (same as the Android app). Unusual visitors need a very clear call.'
                  : `Drops species below ${Math.round(s.filterMin * 100)}% expected occurrence; everything else keeps its raw score (BirdNET-Analyzer style).`}</small>
              </div>
            )}
          </Section>
        </div>

        <div>
          <Section title="Appearance">
            <Segmented label="Theme" value={s.theme} onChange={(v) => updateSettings({ theme: v })} options={[['system', 'System'], ['light', 'Light'], ['dark', 'Dark']]} />
          </Section>

          <Section title="Identification">
            <Toggle label="Show bird photos" hint="From Wikipedia — no key needed." checked={s.showPhotos} onChange={(v) => updateSettings({ showPhotos: v })} />
            <Toggle label="Reference calls to compare" hint="Wikimedia Commons recordings; Xeno-canto too with a key (Advanced)." checked={s.referenceCalls} onChange={(v) => updateSettings({ referenceCalls: v })} />
            <Toggle label="Log weather with detections" hint="Open-Meteo, no key needed." checked={s.weather} onChange={(v) => updateSettings({ weather: v })} />
            <div className="field">
              <b>Also show names in</b>
              <select value={s.regionalLang} onChange={(e) => updateSettings({ regionalLang: e.target.value })}>
                {LANGUAGES.map(([code, name]) => <option key={code} value={code}>{name}</option>)}
              </select>
              <small>Shown alongside the English name where a translation exists. Indian languages come from Wikipedia.</small>
            </div>
            <Toggle label="Desktop notification for new birds" hint="When a species you’ve never logged is heard while this tab is in the background." checked={s.notify} onChange={setNotify} />
          </Section>

          <Section title="Data">
            <p className="fine">{history.length} detections stored in this browser. Clips are kept in local storage on this PC.</p>
            <div className="row-gap">
              <button className="btn" onClick={() => downloadBlob(new Blob([JSON.stringify(history, null, 2)], { type: 'application/json' }), 'bird-log.json')} disabled={!history.length}>
                <Download size={16} /> Export everything
              </button>
              {confirmClear ? (
                <span className="confirm">
                  Delete all {history.length} detections and clips?
                  <button className="btn danger" onClick={async () => { deleteDetections(history.map((h) => h.id)); await clearClips(); setConfirmClear(false); showToast('Bird log cleared'); }}>Delete all</button>
                  <button className="btn ghost" onClick={() => setConfirmClear(false)}>Cancel</button>
                </span>
              ) : (
                <button className="btn danger-soft" onClick={() => setConfirmClear(true)} disabled={!history.length}><Trash2 size={16} /> Clear all detections</button>
              )}
            </div>
          </Section>

          <section className="settings-section">
            <button className="adv-toggle" onClick={() => setAdvanced((a) => !a)} aria-expanded={advanced}>
              <span className="eyebrow">Advanced</span><ChevronDown size={18} className={advanced ? 'flip' : ''} />
            </button>
            {advanced && (
              <div className="card section-card">
                <div className="field">
                  <div className="field-head"><b>BirdNET sensitivity</b><span className="pill">{s.sensitivity.toFixed(2)}</span></div>
                  <input type="range" min="0.5" max="1.5" step="0.05" value={s.sensitivity} onChange={(e) => updateSettings({ sensitivity: +e.target.value })} aria-label="BirdNET sensitivity" />
                  <small>Scales the model’s raw output before the sigmoid. Above 1 finds quieter or partial calls; below 1 is more conservative.</small>
                </div>
                <div className="field">
                  <b>Window step</b>
                  <Segmented label="Window step" value={s.stepSec} onChange={(v) => updateSettings({ stepSec: v })} options={[[0.75, '0.75 s'], [1, '1 s'], [1.5, '1.5 s'], [3, '3 s']]} />
                  <small>How often a new 3 s window is analysed while listening. Smaller = faster reactions, more CPU.</small>
                </div>
                {s.locationMode === 'filter' && (
                  <div className="field">
                    <div className="field-head"><b>Hard-filter cut-off</b><span className="pill">{Math.round(s.filterMin * 100)}%</span></div>
                    <input type="range" min="0.01" max="0.2" step="0.01" value={s.filterMin} onChange={(e) => updateSettings({ filterMin: +e.target.value })} aria-label="Filter cut-off" />
                  </div>
                )}
                <div className="field">
                  <b>Xeno-canto API key</b>
                  <input type="password" autoComplete="off" spellCheck={false} placeholder="Optional" value={s.xenoCantoKey} onChange={(e) => updateSettings({ xenoCantoKey: e.target.value.trim() })} />
                  <small>Adds top-rated reference recordings. Get a free key at <a href="https://xeno-canto.org/account" target="_blank" rel="noreferrer">xeno-canto.org/account</a>. Stored only in this browser.</small>
                </div>
                <button className="btn ghost" onClick={() => { updateSettings({ ...DEFAULT_SETTINGS, xenoCantoKey: s.xenoCantoKey }); showToast('Settings reset'); }}>
                  <RotateCcw size={16} /> Reset settings to defaults
                </button>
              </div>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}

function Section({ title, children }) {
  return (
    <section className="settings-section">
      <h2 className="eyebrow">{title}</h2>
      <div className="card section-card">{children}</div>
    </section>
  );
}

function PlacePicker({ value, onPick }) {
  const [q, setQ] = useState('');
  const [results, setResults] = useState([]);
  const [lat, setLat] = useState(String(value?.lat ?? ''));
  const [lon, setLon] = useState(String(value?.lon ?? ''));
  const timer = useRef(null);

  useEffect(() => {
    clearTimeout(timer.current);
    timer.current = setTimeout(() => searchPlaces(q).then(setResults).catch(() => setResults([])), 300);
    return () => clearTimeout(timer.current);
  }, [q]);

  const latN = Number(lat), lonN = Number(lon);
  const coordsValid = lat !== '' && lon !== '' && Math.abs(latN) <= 90 && Math.abs(lonN) <= 180;

  return (
    <div className="field place-picker">
      <label className="search"><Search size={16} /><input placeholder="Search a town, park or region…" value={q} onChange={(e) => setQ(e.target.value)} /></label>
      {results.length > 0 && (
        <ul className="place-results">
          {results.map((r) => (
            <li key={`${r.lat},${r.lon}`}>
              <button onClick={() => { onPick(r); setQ(''); setResults([]); setLat(String(r.lat)); setLon(String(r.lon)); }}>
                <MapPin size={14} /> {r.name}
              </button>
            </li>
          ))}
        </ul>
      )}
      <div className="coords">
        <label>Latitude<input inputMode="decimal" value={lat} onChange={(e) => setLat(e.target.value)} /></label>
        <label>Longitude<input inputMode="decimal" value={lon} onChange={(e) => setLon(e.target.value)} /></label>
        <button className="btn sm" disabled={!coordsValid} onClick={() => onPick({ lat: latN, lon: lonN, name: null })}>Use coordinates</button>
      </div>
      <small>Current: {value?.name ?? (value ? fmtCoords(value.lat, value.lon) : '—')}</small>
    </div>
  );
}
