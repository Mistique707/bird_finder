import { useEffect, useMemo, useRef, useState } from 'react';
import { CheckCircle2, FileAudio, Loader2, Pause, Play, Plus, Sparkles, Upload, X } from 'lucide-react';
import { classify, spectrogram, SAMPLE_RATE, WINDOW } from '../lib/engine.js';
import { decodeFile, AUDIO_ACCEPT } from '../lib/audio.js';
import { BirdPhoto, ConfBadge, RegionalName, Segmented, fmtSec } from '../components/common.jsx';

const SPEC_W = 1600, SPEC_H = 180;
const SAMPLES = [
  ['Asian Koel', 'File:Eudynamys scolopaceus - Asian Koel XC476378.mp3'],
  ['Oriental Magpie-Robin', 'File:Copsychus saularis - Oriental Magpie-Robin XC126472.ogg'],
];
const LANE_COLORS = ['#2e6ba8', '#2a8c9e', '#c4793a', '#7a5ea8', '#3f9a5c', '#b84f6a', '#8a7a2e', '#4a7fbf'];

export default function Analyze({ settings, engine, logDetections, openSpecies, showToast }) {
  const [files, setFiles] = useState([]);
  const [selected, setSelected] = useState(null);
  const [minConf, setMinConf] = useState(settings.threshold);
  const [hop, setHop] = useState(1.5);
  const [drag, setDrag] = useState(false);
  const [loadingSample, setLoadingSample] = useState(null);
  const inputRef = useRef(null);
  const busyRef = useRef(false);

  const patch = (id, p) => setFiles((fs) => fs.map((f) => (f.id === id ? { ...f, ...p } : f)));

  function addFiles(list) {
    const added = [...list].map((file) => ({ id: crypto.randomUUID(), file, name: file.name, status: 'queued', progress: 0, windows: [] }));
    if (!added.length) return;
    setFiles((fs) => [...fs, ...added]);
    setSelected((s) => s ?? added[0].id);
  }

  // Work through the queue one file at a time.
  useEffect(() => {
    if (engine.state !== 'ready' || busyRef.current) return;
    const next = files.find((f) => f.status === 'queued');
    if (!next) return;
    busyRef.current = true;
    (async () => {
      try {
        patch(next.id, { status: 'decoding' });
        const samples = await decodeFile(next.file);
        const duration = samples.length / SAMPLE_RATE;
        const image = await spectrogram(samples, SPEC_W, SPEC_H);
        patch(next.id, { status: 'analyzing', samples, duration, image, url: URL.createObjectURL(next.file) });
        const step = Math.round(hop * SAMPLE_RATE), windows = [];
        const total = Math.max(1, Math.ceil(Math.max(0, samples.length - WINDOW) / step) + 1);
        for (let k = 0, start = 0; k < total; k++, start += step) {
          const win = new Float32Array(WINDOW);
          win.set(samples.subarray(start, start + WINDOW));
          // Keep everything ≥ 10% so the threshold slider can re-filter without re-running.
          windows.push({ start: start / SAMPLE_RATE, dets: await classify(win, settings, 0.1) });
          if (k % 3 === 0 || k === total - 1) patch(next.id, { windows: [...windows], progress: (k + 1) / total });
        }
        patch(next.id, { status: 'done', progress: 1, hop });
      } catch (e) {
        patch(next.id, { status: 'error', error: e.name === 'EncodingError' ? 'This audio format can’t be decoded by the browser.' : e.message });
      } finally {
        busyRef.current = false;
        setFiles((fs) => [...fs]); // nudge the effect to pick up the next queued file
      }
    })();
  }, [files, engine.state]); // eslint-disable-line react-hooks/exhaustive-deps

  async function loadSample(title) {
    setLoadingSample(title);
    try {
      const info = await fetch(`https://commons.wikimedia.org/w/api.php?action=query&titles=${encodeURIComponent(title)}&prop=imageinfo&iiprop=url&format=json&origin=*`).then((r) => r.json());
      const url = Object.values(info.query.pages)[0].imageinfo[0].url;
      const blob = await fetch(url).then((r) => r.blob());
      addFiles([new File([blob], title.replace(/^File:/, ''), { type: blob.type })]);
    } catch {
      showToast('Couldn’t download the sample — are you online?');
    } finally {
      setLoadingSample(null);
    }
  }

  function removeFile(id) {
    setFiles((fs) => fs.filter((f) => f.id !== id || f.status === 'decoding' || f.status === 'analyzing'));
    if (selected === id) setSelected(files.find((f) => f.id !== id)?.id ?? null);
  }

  const current = files.find((f) => f.id === selected);

  return (
    <div className="analyze">
      <header className="page-head">
        <div>
          <h1>Identify a recording</h1>
          <p>Drop in recordings from a field recorder or phone — MP3, AAC/M4A, WAV, FLAC, OGG, Opus. Everything is analysed on this PC.</p>
        </div>
      </header>

      <div
        className={`dropzone ${drag ? 'drag' : ''} ${files.length ? 'compact' : ''}`}
        onDragOver={(e) => { e.preventDefault(); setDrag(true); }}
        onDragLeave={() => setDrag(false)}
        onDrop={(e) => { e.preventDefault(); setDrag(false); addFiles(e.dataTransfer.files); }}
      >
        <Upload size={files.length ? 20 : 30} strokeWidth={1.6} />
        <div>
          <b>Drag audio files here</b>
          <span> or </span>
          <button className="link-btn" onClick={() => inputRef.current.click()} disabled={engine.state !== 'ready'}>browse your PC</button>
        </div>
        {!files.length && (
          <div className="samples">
            <span>No recording handy? Try</span>
            {SAMPLES.map(([name, title]) => (
              <button key={title} className="chip-btn" onClick={() => loadSample(title)} disabled={!!loadingSample || engine.state !== 'ready'}>
                {loadingSample === title ? <Loader2 size={14} className="spin" /> : <Sparkles size={14} />} {name}
              </button>
            ))}
          </div>
        )}
        <input ref={inputRef} type="file" accept={AUDIO_ACCEPT} multiple hidden onChange={(e) => { addFiles(e.target.files); e.target.value = ''; }} />
      </div>

      {files.length > 0 && (
        <div className="analyze-body">
          <ul className="queue">
            {files.map((f) => (
              <li key={f.id} className={f.id === selected ? 'sel' : ''}>
                <button onClick={() => setSelected(f.id)}>
                  <FileAudio size={18} />
                  <span className="q-name">
                    <b title={f.name}>{f.name}</b>
                    <small>
                      {f.status === 'queued' && 'Waiting…'}
                      {f.status === 'decoding' && 'Decoding…'}
                      {f.status === 'analyzing' && `Analysing ${Math.round(f.progress * 100)}%`}
                      {f.status === 'done' && `${fmtSec(f.duration)} · ${speciesOf(f, minConf).length} species`}
                      {f.status === 'error' && 'Couldn’t read file'}
                    </small>
                  </span>
                  {(f.status === 'decoding' || f.status === 'analyzing') && <Loader2 size={16} className="spin" />}
                  {f.logged && <CheckCircle2 size={16} className="ok" />}
                </button>
                {f.status !== 'decoding' && f.status !== 'analyzing' && (
                  <button className="icon-btn q-x" onClick={() => removeFile(f.id)} aria-label={`Remove ${f.name}`}><X size={14} /></button>
                )}
                {f.status === 'analyzing' && <i className="q-progress" style={{ width: `${f.progress * 100}%` }} />}
              </li>
            ))}
          </ul>

          {current && (
            <FileResult
              key={current.id} f={current} settings={settings} minConf={minConf} setMinConf={setMinConf}
              hop={hop} setHop={setHop} openSpecies={openSpecies}
              onReanalyze={() => patch(current.id, { status: 'queued', windows: [], progress: 0, logged: false })}
              onLog={async (picked) => {
                for (const sp of [...picked].reverse()) { // newest-first log, so add the best match last
                  const center = sp.best.start + 1.5;
                  const s0 = Math.max(0, center - settings.clipSec / 2), s1 = Math.min(current.duration, s0 + settings.clipSec);
                  const clip = current.samples.slice(Math.round(s0 * SAMPLE_RATE), Math.round(s1 * SAMPLE_RATE));
                  await logDetections([{ ...sp, confidence: sp.best.confidence }], clip, {
                    source: 'file', fileName: current.name, offsetSec: sp.best.start, clipWindow: [sp.best.start - s0, sp.best.start - s0 + 3],
                  });
                }
                patch(current.id, { logged: true });
                showToast(`Added ${picked.length} species to your log`);
              }}
            />
          )}
        </div>
      )}
    </div>
  );
}

/** Group a file's windows into per-species results above minConf. */
function speciesOf(f, minConf) {
  const map = new Map();
  for (const w of f.windows) {
    for (const d of w.dets) {
      if (d.confidence < minConf) continue;
      const sp = map.get(d.scientific) ?? { ...d, best: { start: w.start, confidence: 0 }, hits: [] };
      sp.hits.push({ start: w.start, confidence: d.confidence });
      if (d.confidence > sp.best.confidence) sp.best = { start: w.start, confidence: d.confidence };
      map.set(d.scientific, sp);
    }
  }
  return [...map.values()].sort((a, b) => b.best.confidence - a.best.confidence);
}

/** Merge overlapping hit windows into [start, end] segments. */
function segments(hits) {
  const out = [];
  for (const h of [...hits].sort((a, b) => a.start - b.start)) {
    const last = out[out.length - 1];
    if (last && h.start <= last.end) { last.end = h.start + 3; last.peak = Math.max(last.peak, h.confidence); }
    else out.push({ start: h.start, end: h.start + 3, peak: h.confidence });
  }
  return out;
}

function FileResult({ f, settings, minConf, setMinConf, hop, setHop, openSpecies, onLog, onReanalyze }) {
  const canvasRef = useRef(null);
  const audioRef = useRef(null);
  const [pos, setPos] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [hover, setHover] = useState(null);
  const [unchecked, setUnchecked] = useState(() => new Set());
  const species = useMemo(() => speciesOf(f, minConf), [f.windows, minConf]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (f.image && canvasRef.current) canvasRef.current.getContext('2d').putImageData(f.image, 0, 0);
  }, [f.image]);

  useEffect(() => {
    if (!playing) return;
    let raf;
    const tick = () => { setPos(audioRef.current?.currentTime ?? 0); raf = requestAnimationFrame(tick); };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [playing]);

  const seek = (t, play = true) => {
    const a = audioRef.current;
    if (!a) return;
    a.currentTime = t;
    setPos(t);
    if (play) {
      document.querySelectorAll('audio').forEach((el) => el !== a && el.pause());
      a.play();
    }
  };

  if (f.status === 'error') return <div className="card result"><p className="status-error">{f.name}: {f.error}</p></div>;
  const dur = f.duration ?? 0;
  const picked = species.filter((s) => !unchecked.has(s.scientific));

  return (
    <div className="card result">
      <div className="result-head">
        <div>
          <h2 title={f.name}>{f.name}</h2>
          <small>{dur ? `${fmtSec(dur)} · ` : ''}{f.status === 'done' ? `${f.windows.length} windows analysed` : f.status === 'analyzing' ? `Analysing… ${Math.round(f.progress * 100)}%` : 'Decoding…'}</small>
        </div>
        <div className="result-controls">
          <label className="inline-slider">
            Show above <b>{Math.round(minConf * 100)}%</b>
            <input type="range" min="0.1" max="0.95" step="0.05" value={minConf} onChange={(e) => setMinConf(+e.target.value)} />
          </label>
          <Segmented label="Window overlap" value={hop} onChange={setHop} options={[[3, 'Fast'], [1.5, 'Detailed']]} />
          {f.status === 'done' && f.hop !== hop && <button className="btn sm" onClick={onReanalyze}>Re-analyse</button>}
        </div>
      </div>

      <div className="spec-view">
        <button className="play-btn" onClick={() => (playing ? audioRef.current.pause() : seek(pos >= dur - 0.05 ? 0 : pos))} disabled={!f.url} aria-label={playing ? 'Pause' : 'Play'}>
          {playing ? <Pause size={18} fill="currentColor" /> : <Play size={18} fill="currentColor" />}
        </button>
        <div className="spec-area">
          <div className="spec-img" onClick={(e) => { const r = e.currentTarget.getBoundingClientRect(); seek(((e.clientX - r.left) / r.width) * dur); }}>
            {f.image ? <canvas ref={canvasRef} width={SPEC_W} height={SPEC_H} /> : <div className="skeleton" />}
            {f.status === 'analyzing' && <span className="scan" style={{ left: `${f.progress * 100}%` }} />}
            {dur > 0 && <span className="playhead" style={{ left: `${(pos / dur) * 100}%` }} />}
            <span className="spec-label">0–12 kHz</span>
          </div>
          <div className="lanes">
            {species.slice(0, 8).map((sp, i) => (
              <div key={sp.scientific} className={`lane ${hover && hover !== sp.scientific ? 'dim' : ''}`} onMouseEnter={() => setHover(sp.scientific)} onMouseLeave={() => setHover(null)}>
                {segments(sp.hits).map((seg) => (
                  <button key={seg.start} style={{ left: `${(seg.start / dur) * 100}%`, width: `${(Math.min(dur, seg.end) - seg.start) / dur * 100}%`, background: LANE_COLORS[i % LANE_COLORS.length], opacity: 0.35 + seg.peak * 0.65 }}
                    title={`${sp.common} · ${fmtSec(seg.start)}–${fmtSec(Math.min(dur, seg.end))} · ${Math.round(seg.peak * 100)}%`} onClick={() => seek(seg.start)} />
                ))}
              </div>
            ))}
          </div>
          <div className="time-axis">{[0, 0.25, 0.5, 0.75, 1].map((t) => <span key={t}>{fmtSec(t * dur)}</span>)}</div>
        </div>
        {f.url && <audio ref={audioRef} src={f.url} onPlay={() => setPlaying(true)} onPause={() => setPlaying(false)} onEnded={() => setPlaying(false)} />}
      </div>

      {species.length > 0 ? (
        <>
          <ul className="species-results">
            {species.map((sp, i) => (
              <li key={sp.scientific} onMouseEnter={() => setHover(sp.scientific)} onMouseLeave={() => setHover(null)}>
                <input type="checkbox" checked={!unchecked.has(sp.scientific)} aria-label={`Include ${sp.common}`}
                  onChange={() => setUnchecked((u) => { const n = new Set(u); n.has(sp.scientific) ? n.delete(sp.scientific) : n.add(sp.scientific); return n; })} />
                <i className="swatch" style={{ background: LANE_COLORS[i % LANE_COLORS.length] }} />
                <BirdPhoto scientific={sp.scientific} enabled={settings.showPhotos} />
                <button className="names" onClick={() => openSpecies(sp)}>
                  <b>{sp.common}</b>
                  <RegionalName scientific={sp.scientific} common={sp.common} lang={settings.regionalLang} />
                  <i>{sp.scientific}</i>
                </button>
                <span className="segs">
                  {segments(sp.hits).slice(0, 4).map((seg) => (
                    <button key={seg.start} className="chip-btn" onClick={() => seek(seg.start)}><Play size={11} fill="currentColor" /> {fmtSec(seg.start)}</button>
                  ))}
                  {segments(sp.hits).length > 4 && <small>+{segments(sp.hits).length - 4}</small>}
                </span>
                <ConfBadge value={sp.best.confidence} />
              </li>
            ))}
          </ul>
          <div className="result-actions">
            <button className="btn primary" disabled={f.status !== 'done' || !picked.length} onClick={() => onLog(picked)}>
              <Plus size={16} /> Add {picked.length} to bird log
            </button>
            <span className="fine">Saves a {settings.clipSec}s clip around each bird’s best match.</span>
          </div>
        </>
      ) : f.status === 'done' ? (
        <p className="empty">No birds above {Math.round(minConf * 100)}%. Lower the slider, or try turning off the location filter in Settings if this recording is from elsewhere.</p>
      ) : null}
    </div>
  );
}
