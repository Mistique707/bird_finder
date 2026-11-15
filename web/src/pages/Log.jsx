import { useMemo, useRef, useState } from 'react';
import { Download, FileJson, Pause, Play, Search, Trash2, X } from 'lucide-react';
import { getClip, downloadBlob } from '../lib/store.js';
import { BirdPhoto, ConfBadge, RegionalName, Segmented, fmtDateTime, relTime } from '../components/common.jsx';

const DAY = 864e5;
const startOfDay = (t) => { const d = new Date(t); d.setHours(0, 0, 0, 0); return d.getTime(); };

export default function Log({ settings, history, openSpecies, deleteDetections, setPage, showToast }) {
  const [view, setView] = useState('detections');
  const [query, setQuery] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [source, setSource] = useState('all');
  const [selected, setSelected] = useState(() => new Set());
  const [confirm, setConfirm] = useState(false);
  const [playingId, setPlayingId] = useState(null);
  const audioRef = useRef(null);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    const f = from ? new Date(from).setHours(0, 0, 0, 0) : -Infinity;
    const t = to ? new Date(to).setHours(23, 59, 59, 999) : Infinity;
    return history.filter((h) => (!q || h.common.toLowerCase().includes(q) || h.scientific.toLowerCase().includes(q))
      && h.time >= f && h.time <= t && (source === 'all' || h.source === source));
  }, [history, query, from, to, source]);

  const species = useMemo(() => {
    const map = new Map();
    for (const h of filtered) {
      const s = map.get(h.scientific) ?? { scientific: h.scientific, common: h.common, count: 0, first: h.time, last: h.time, best: h };
      s.count++;
      s.first = Math.min(s.first, h.time);
      s.last = Math.max(s.last, h.time);
      if (h.confidence > s.best.confidence) s.best = h;
      map.set(h.scientific, s);
    }
    return [...map.values()].sort((a, b) => b.count - a.count);
  }, [filtered]);

  const days = useMemo(() => {
    const today = startOfDay(Date.now());
    const counts = Array.from({ length: 14 }, (_, i) => ({ day: today - (13 - i) * DAY, n: 0 }));
    for (const h of history) {
      const idx = 13 - Math.round((today - startOfDay(h.time)) / DAY);
      if (idx >= 0 && idx < 14) counts[idx].n++;
    }
    return counts;
  }, [history]);
  const maxDay = Math.max(1, ...days.map((d) => d.n));
  const allSpecies = new Set(history.map((h) => h.scientific)).size;
  const activeDays = new Set(history.map((h) => startOfDay(h.time))).size;

  async function togglePlay(row) {
    const a = audioRef.current;
    if (playingId === row.id) { a.pause(); return; }
    const blob = await getClip(row.clipId);
    if (!blob) return showToast('Clip not found');
    document.querySelectorAll('audio').forEach((el) => el !== a && el.pause());
    if (a.src) URL.revokeObjectURL(a.src);
    a.src = URL.createObjectURL(blob);
    await a.play();
    setPlayingId(row.id);
  }

  function exportCsv() {
    const esc = (v) => `"${String(v ?? '').replace(/"/g, '""')}"`;
    const rows = [['time_utc', 'common_name', 'scientific_name', 'confidence', 'latitude', 'longitude', 'place', 'temp_c', 'weather', 'source', 'file', 'model'],
      ...filtered.map((h) => [new Date(h.time).toISOString(), h.common, h.scientific, h.confidence.toFixed(3), h.lat, h.lon, h.place, h.weather?.tempC, h.weather?.text, h.source, h.fileName, h.model])];
    downloadBlob(new Blob([rows.map((r) => r.map(esc).join(',')).join('\r\n')], { type: 'text/csv' }), `bird-log-${new Date().toISOString().slice(0, 10)}.csv`);
  }
  const exportJson = () => downloadBlob(new Blob([JSON.stringify(filtered, null, 2)], { type: 'application/json' }), `bird-log-${new Date().toISOString().slice(0, 10)}.json`);

  const toggleSel = (id) => setSelected((s) => { const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n; });
  const filtersOn = query || from || to || source !== 'all';

  if (!history.length) {
    return (
      <div className="log">
        <header className="page-head"><div><h1>Bird log</h1><p>Your detections live here, saved on this PC.</p></div></header>
        <div className="card empty-state">
          <h2>No birds logged yet</h2>
          <p>Start listening, or identify a recording, and every bird you find will be saved here with its clip, time, place and weather.</p>
          <div className="row-gap">
            <button className="btn primary" onClick={() => setPage('listen')}>Start listening</button>
            <button className="btn" onClick={() => setPage('analyze')}>Identify a file</button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="log">
      <header className="page-head">
        <div><h1>Bird log</h1><p>Everything you’ve heard, saved on this PC.</p></div>
        <div className="row-gap">
          <button className="btn" onClick={exportCsv}><Download size={16} /> CSV</button>
          <button className="btn" onClick={exportJson}><FileJson size={16} /> JSON</button>
        </div>
      </header>

      <div className="stats">
        <div className="stat card"><b>{history.length}</b><span>detections</span></div>
        <div className="stat card"><b>{allSpecies}</b><span>species</span></div>
        <div className="stat card"><b>{activeDays}</b><span>days out listening</span></div>
        <div className="stat card activity">
          <div className="bars" role="img" aria-label="Detections per day, last 14 days">
            {days.map((d) => <i key={d.day} style={{ height: `${(d.n / maxDay) * 100}%` }} title={`${new Date(d.day).toLocaleDateString()}: ${d.n}`} className={d.n ? '' : 'zero'} />)}
          </div>
          <span>last 14 days</span>
        </div>
      </div>

      <div className="log-toolbar card">
        <Segmented label="View" value={view} onChange={setView} options={[['detections', 'Detections'], ['species', 'Species list']]} />
        <label className="search"><Search size={16} /><input placeholder="Search species" value={query} onChange={(e) => setQuery(e.target.value)} /></label>
        <label className="date">From <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
        <label className="date">To <input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></label>
        <select value={source} onChange={(e) => setSource(e.target.value)} aria-label="Source">
          <option value="all">All sources</option><option value="mic">Microphone</option><option value="file">Files</option>
        </select>
        {filtersOn && <button className="link-btn" onClick={() => { setQuery(''); setFrom(''); setTo(''); setSource('all'); }}><X size={14} /> Clear</button>}
      </div>

      {selected.size > 0 && view === 'detections' && (
        <div className="selection-bar">
          <b>{selected.size} selected</b>
          {confirm ? (
            <>
              <span>Delete them and their clips? This can’t be undone.</span>
              <button className="btn danger" onClick={() => { deleteDetections([...selected]); setSelected(new Set()); setConfirm(false); showToast(`Deleted ${selected.size} detections`); }}>Delete</button>
              <button className="btn ghost" onClick={() => setConfirm(false)}>Cancel</button>
            </>
          ) : (
            <>
              <button className="btn danger" onClick={() => setConfirm(true)}><Trash2 size={15} /> Delete</button>
              <button className="btn ghost" onClick={() => setSelected(new Set())}>Clear selection</button>
            </>
          )}
        </div>
      )}

      {view === 'detections' ? (
        <div className="table card">
          <div className="t-head">
            <input type="checkbox" aria-label="Select all"
              checked={filtered.length > 0 && filtered.every((h) => selected.has(h.id))}
              onChange={(e) => setSelected(e.target.checked ? new Set(filtered.map((h) => h.id)) : new Set())} />
            <span>Bird</span><span>When</span><span>Where</span><span>Weather</span><span>Clip</span><span>Score</span>
          </div>
          {filtered.slice(0, 400).map((h) => (
            <div key={h.id} className={`t-row ${selected.has(h.id) ? 'sel' : ''}`}>
              <input type="checkbox" checked={selected.has(h.id)} onChange={() => toggleSel(h.id)} aria-label={`Select ${h.common}`} />
              <button className="t-bird" onClick={() => openSpecies(h, h)}>
                <BirdPhoto scientific={h.scientific} enabled={settings.showPhotos} />
                <span className="names">
                  <b>{h.common}</b>
                  <RegionalName scientific={h.scientific} common={h.common} lang={settings.regionalLang} />
                  <i>{h.scientific}</i>
                </span>
              </button>
              <span className="t-when" title={fmtDateTime(h.time)}>{fmtDateTime(h.time)}<small>{relTime(h.time)}</small></span>
              <span className="t-where">{h.place ?? `${h.lat.toFixed(3)}, ${h.lon.toFixed(3)}`}{h.source === 'file' && <small title={h.fileName}>from {h.fileName}</small>}</span>
              <span className="t-wx">{h.weather ? `${Math.round(h.weather.tempC)}°C · ${h.weather.text}` : '—'}</span>
              <span>{h.clipId ? (
                <button className="icon-btn" onClick={() => togglePlay(h)} aria-label={playingId === h.id ? 'Pause clip' : 'Play clip'}>
                  {playingId === h.id ? <Pause size={16} fill="currentColor" /> : <Play size={16} fill="currentColor" />}
                </button>
              ) : '—'}</span>
              <ConfBadge value={h.confidence} />
            </div>
          ))}
          {filtered.length > 400 && <p className="fine pad">Showing the latest 400 of {filtered.length}. Narrow the filters to see older ones.</p>}
          {!filtered.length && <p className="empty pad">No detections match these filters.</p>}
        </div>
      ) : (
        <div className="species-grid">
          {species.map((s) => (
            <button key={s.scientific} className="species-card card" onClick={() => openSpecies(s, s.best)}>
              <BirdPhoto scientific={s.scientific} enabled={settings.showPhotos} />
              <span className="names">
                <b>{s.common}</b>
                <RegionalName scientific={s.scientific} common={s.common} lang={settings.regionalLang} />
                <i>{s.scientific}</i>
                <small>{s.count}× · first {new Date(s.first).toLocaleDateString()} · last {relTime(s.last)}</small>
              </span>
              <ConfBadge value={s.best.confidence} />
            </button>
          ))}
          {!species.length && <p className="empty">No species match these filters.</p>}
        </div>
      )}
      <audio ref={audioRef} onPause={() => setPlayingId(null)} onEnded={() => setPlayingId(null)} hidden />
    </div>
  );
}
