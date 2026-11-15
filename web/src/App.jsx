import { useCallback, useEffect, useRef, useState } from 'react';
import { AudioLines, BookOpen, Compass, FileAudio, Info, Settings as SettingsIcon } from 'lucide-react';
import { loadEngine, setPrior, classify, weekOf, MODEL } from './lib/engine.js';
import { startMic, wavBlob } from './lib/audio.js';
import { loadSettings, loadHistory, saveSettings, usePersisted, putClip, deleteClip, PUNE } from './lib/store.js';
import { placeName, weather as fetchWeather } from './lib/apis.js';
import Splash from './components/Splash.jsx';
import SpeciesDetail from './components/SpeciesDetail.jsx';
import Listen from './pages/Listen.jsx';
import Analyze from './pages/Analyze.jsx';
import Explore from './pages/Explore.jsx';
import Log from './pages/Log.jsx';
import Settings from './pages/Settings.jsx';
import About from './pages/About.jsx';
import wordmark from './assets/logo_wordmark.png';

const PAGES = [
  { id: 'listen', label: 'Listen', icon: AudioLines },
  { id: 'analyze', label: 'Identify a file', icon: FileAudio },
  { id: 'explore', label: 'Birds near you', icon: Compass },
  { id: 'log', label: 'Bird log', icon: BookOpen },
  { id: 'settings', label: 'Settings', icon: SettingsIcon },
  { id: 'about', label: 'About', icon: Info },
];

function micError(e) {
  if (e?.name === 'NotAllowedError') return 'Microphone access was blocked. Allow it from the address bar and try again.';
  if (e?.name === 'NotFoundError' || e?.name === 'OverconstrainedError') return 'That microphone isn’t available. Pick another one in Settings.';
  if (e?.name === 'NotReadableError') return 'The microphone is in use by another app.';
  return e?.message ?? String(e);
}

export default function App() {
  const [settings, setSettingsState] = useState(loadSettings);
  const [history, setHistory] = usePersisted('bf.history', loadHistory);
  const [engine, setEngine] = useState({ state: 'loading' });
  const [splashDone, setSplashDone] = useState(false);
  const [page, setPage] = useState('listen');
  const [location, setLocation] = useState(null);
  const [weather, setWeather] = useState(null);
  const [detail, setDetail] = useState(null); // { species, detection? }
  const [toast, setToast] = useState(null);

  const settingsRef = useRef(settings);
  settingsRef.current = settings;
  const locationRef = useRef(location);
  locationRef.current = location;
  const historyRef = useRef(history);
  historyRef.current = history;

  const updateSettings = useCallback((patch) => {
    setSettingsState((s) => {
      const next = { ...s, ...patch };
      saveSettings(next);
      return next;
    });
  }, []);

  // ---- Theme ----
  useEffect(() => {
    const root = document.documentElement;
    if (settings.theme === 'system') root.removeAttribute('data-theme');
    else root.setAttribute('data-theme', settings.theme);
  }, [settings.theme]);

  // ---- Model ----
  useEffect(() => {
    loadEngine().then(() => setEngine({ state: 'ready' }), (e) => setEngine({ state: 'error', error: e.message }));
  }, []);

  // ---- Location: browser geolocation or a manually chosen place ----
  const locate = useCallback(() => {
    const s = settingsRef.current;
    if (s.locationSource === 'manual' || !navigator.geolocation) {
      setLocation({ ...(s.manual ?? PUNE), source: 'manual' });
      return;
    }
    setLocation((l) => l ?? { ...(s.manual ?? PUNE), source: 'pending' });
    navigator.geolocation.getCurrentPosition(
      (p) => setLocation({ lat: p.coords.latitude, lon: p.coords.longitude, accuracy: p.coords.accuracy, source: 'browser' }),
      () => setLocation({ ...(s.manual ?? PUNE), source: 'fallback' }),
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 300000 },
    );
  }, []);
  useEffect(locate, [locate, settings.locationSource, settings.manual]);

  useEffect(() => {
    if (!location || location.name) return;
    let alive = true;
    placeName(location.lat, location.lon).then((name) => alive && name && setLocation((l) => (l === location ? { ...l, name } : l)));
    return () => { alive = false; };
  }, [location]);

  useEffect(() => {
    if (engine.state !== 'ready' || !location) return;
    setPrior(location.lat, location.lon, weekOf()).catch(console.error);
  }, [engine.state, location?.lat, location?.lon]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!location || !settings.weather) { setWeather(null); return; }
    let alive = true;
    fetchWeather(location.lat, location.lon).then((w) => alive && setWeather(w));
    return () => { alive = false; };
  }, [location?.lat, location?.lon, settings.weather]); // eslint-disable-line react-hooks/exhaustive-deps

  const showToast = useCallback((text) => {
    setToast({ text, key: Date.now() });
  }, []);
  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 3500);
    return () => clearTimeout(t);
  }, [toast]);

  // ---- Logging detections ----
  const logDetections = useCallback(async (dets, clip, meta = {}) => {
    const s = settingsRef.current;
    const loc = locationRef.current ?? PUNE;
    const clipId = clip ? crypto.randomUUID() : null;
    if (clip) await putClip(clipId, wavBlob(clip)).catch(console.error);
    const wx = s.weather ? await fetchWeather(loc.lat, loc.lon) : null;
    const seen = new Set(historyRef.current.map((h) => h.scientific));
    const rows = dets.map((d) => ({
      id: crypto.randomUUID(),
      time: meta.time ?? Date.now(),
      scientific: d.scientific,
      common: d.common,
      confidence: d.confidence,
      lat: loc.lat, lon: loc.lon, place: loc.name ?? null,
      weather: wx ? { tempC: wx.tempC, text: wx.text } : null,
      source: meta.source ?? 'mic',
      fileName: meta.fileName ?? null,
      offsetSec: meta.offsetSec ?? null,
      clipId,
      clipWindow: meta.clipWindow ?? null, // [start, end] s of the detected 3 s window inside the clip
      model: MODEL,
    }));
    setHistory((h) => [...rows, ...h].slice(0, 5000));
    const lifers = rows.filter((r) => !seen.has(r.scientific));
    showToast(rows.length === 1 ? `Logged ${rows[0].common}` : `Logged ${rows.length} birds`);
    if (s.notify && lifers.length && document.hidden && Notification.permission === 'granted') {
      new Notification('New bird for your log', { body: lifers.map((r) => r.common).join(', '), icon: '/favicon.png' });
    }
    return rows;
  }, [setHistory, showToast]);

  const deleteDetections = useCallback((ids) => {
    const drop = new Set(ids);
    setHistory((h) => {
      const keep = h.filter((r) => !drop.has(r.id));
      const stillUsed = new Set(keep.map((r) => r.clipId));
      h.filter((r) => drop.has(r.id) && r.clipId && !stillUsed.has(r.clipId)).forEach((r) => deleteClip(r.clipId).catch(() => {}));
      return keep;
    });
  }, [setHistory]);

  // ---- Live listening ----
  // One session object per start; every async callback checks it's still the current one, so
  // a quick stop/start (or a stop while the permission prompt is open) can't leak a mic.
  const [mic, setMic] = useState({ state: 'idle' });
  const [live, setLive] = useState([]);
  const [sessionLog, setSessionLog] = useState([]);
  const sessionRef = useRef(null);
  const analyserRef = useRef(null);

  const stopListening = useCallback(() => {
    const token = sessionRef.current;
    sessionRef.current = null;
    analyserRef.current = null;
    token?.flush?.();
    token?.mic?.stop();
    setMic({ state: 'idle' });
    setLive([]);
  }, []);

  const startListening = useCallback(async () => {
    if (sessionRef.current || engine.state !== 'ready') return;
    const token = { startedAt: Date.now() };
    sessionRef.current = token;
    setMic({ state: 'starting' });
    setSessionLog([]);
    const lastLogged = new Map();
    let busy = false, pending = null, lastClip = null;

    const flushPending = (clip) => {
      if (!pending) return;
      const p = pending;
      pending = null;
      // The detection window ended (now - p.time) before this clip's end.
      const len = clip.length / 48000, end = Math.max(3, len - (Date.now() - p.time) / 1000);
      logDetections(p.dets, clip, { source: 'mic', time: p.time, clipWindow: [end - 3, end] })
        .then((rows) => setSessionLog((l) => [...rows, ...l].slice(0, 30)));
    };
    token.flush = () => flushPending(lastClip);

    try {
      const s = settingsRef.current;
      const session = await startMic({
        deviceId: s.micId, stepSec: s.stepSec, clipSec: s.clipSec,
        onWindow: async (win, clip) => {
          if (sessionRef.current !== token) return;
          lastClip = clip;
          // Post-roll: save one window after the detection so the call sits mid-clip.
          flushPending(clip);
          if (busy) return; // drop a window rather than queue up if inference falls behind
          busy = true;
          try {
            const st = settingsRef.current;
            const found = await classify(win, st);
            if (sessionRef.current !== token) return;
            setLive(found.slice(0, 6));
            const now = Date.now();
            const fresh = found.filter((d) => d.confidence >= st.threshold
              && now - (lastLogged.get(d.scientific) ?? -Infinity) >= st.dedupeMin * 60e3);
            fresh.forEach((d) => lastLogged.set(d.scientific, now));
            if (fresh.length) {
              const dets = [...(pending?.dets ?? []), ...fresh].filter((d, i, a) => a.findIndex((x) => x.scientific === d.scientific) === i);
              pending = { dets, time: pending?.time ?? now };
            }
          } catch (e) {
            console.error(e);
          } finally {
            busy = false;
          }
        },
      });
      if (sessionRef.current !== token) { session.stop(); return; } // stopped while starting
      token.mic = session;
      analyserRef.current = session.analyser;
      setMic({ state: 'on', device: session.deviceLabel, startedAt: token.startedAt });
    } catch (e) {
      if (sessionRef.current === token) sessionRef.current = null;
      setMic({ state: 'idle', error: micError(e) });
    }
  }, [engine.state, logDetections]);

  const toggleListening = useCallback(() => {
    if (sessionRef.current) stopListening();
    else startListening();
  }, [startListening, stopListening]);

  useEffect(() => () => sessionRef.current?.mic?.stop(), []);

  // ---- Keyboard: Space = listen, Esc = close panel ----
  useEffect(() => {
    const onKey = (e) => {
      const typing = /^(INPUT|TEXTAREA|SELECT|BUTTON)$/.test(e.target.tagName) || e.target.isContentEditable;
      if (e.key === 'Escape') setDetail(null);
      if (e.code === 'Space' && !typing && splashDone) {
        e.preventDefault();
        toggleListening();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [toggleListening, splashDone]);

  const openSpecies = useCallback((species, detection = null) => setDetail({ species, detection }), []);

  const ctx = {
    settings, updateSettings, history, location, weather, engine, mic, live, sessionLog,
    analyserRef, toggleListening, logDetections, deleteDetections, openSpecies, locate, showToast, setPage,
  };

  return (
    <>
      {!splashDone && <Splash engine={engine} onDone={() => setSplashDone(true)} />}
      <div className="shell">
        <nav className="sidebar" aria-label="Main">
          <div className="brand">
            <img src={wordmark} alt="Bird Finder" />
          </div>
          <ul>
            {PAGES.map(({ id, label, icon: Icon }) => (
              <li key={id}>
                <button className={page === id ? 'active' : ''} onClick={() => setPage(id)} aria-current={page === id ? 'page' : undefined}>
                  <Icon size={18} strokeWidth={2} />
                  <span>{label}</span>
                  {id === 'listen' && mic.state === 'on' && <i className="rec-dot" title="Listening" />}
                  {id === 'log' && history.length > 0 && <em>{history.length}</em>}
                </button>
              </li>
            ))}
          </ul>
          <div className="sidebar-foot">
            <span className={`engine-dot ${engine.state}`} />
            {engine.state === 'ready' ? 'BirdNET ready · on-device' : engine.state === 'error' ? 'Model failed to load' : 'Loading BirdNET…'}
          </div>
        </nav>

        <main className="content">
          {PAGES.map(({ id }) => (
            <section key={id} className="page" hidden={page !== id}>
              {id === 'listen' && <Listen {...ctx} />}
              {id === 'analyze' && <Analyze {...ctx} />}
              {id === 'explore' && <Explore {...ctx} active={page === 'explore'} />}
              {id === 'log' && <Log {...ctx} />}
              {id === 'settings' && <Settings {...ctx} />}
              {id === 'about' && <About />}
            </section>
          ))}
        </main>
      </div>

      {detail && (
        <SpeciesDetail
          key={detail.detection?.id ?? detail.species.scientific}
          {...detail} settings={settings} location={location} history={history}
          onClose={() => setDetail(null)}
          onDelete={(id) => { deleteDetections([id]); setDetail(null); showToast('Detection deleted'); }}
          onOpen={openSpecies}
        />
      )}
      {toast && <div className="toast" key={toast.key} role="status">{toast.text}</div>}
    </>
  );
}
