import { useEffect, useMemo, useState } from 'react';
import { Check, MapPin, Search } from 'lucide-react';
import { occurrence, weekOf, weekLabel } from '../lib/engine.js';
import { BirdPhoto, RegionalName } from '../components/common.jsx';

const PAGE = 36;

export default function Explore({ settings, engine, location, history, openSpecies, active, setPage }) {
  const [week, setWeek] = useState(weekOf);
  const [list, setList] = useState(null);
  const [query, setQuery] = useState('');
  const [shown, setShown] = useState(PAGE);
  const [onlyUnseen, setOnlyUnseen] = useState(false);

  useEffect(() => {
    if (!active || engine.state !== 'ready' || !location) return;
    let alive = true;
    occurrence(location.lat, location.lon, week).then((l) => alive && setList(l.filter((s) => s.p >= 0.02)));
    return () => { alive = false; };
  }, [active, engine.state, location?.lat, location?.lon, week]); // eslint-disable-line react-hooks/exhaustive-deps

  const seen = useMemo(() => new Set(history.map((h) => h.scientific)), [history]);
  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (list ?? []).filter((s) => (!q || s.common.toLowerCase().includes(q) || s.scientific.toLowerCase().includes(q)) && (!onlyUnseen || !seen.has(s.scientific)));
  }, [list, query, onlyUnseen, seen]);
  const likely = (list ?? []).filter((s) => s.p >= 0.5);
  const likelySeen = likely.filter((s) => seen.has(s.scientific)).length;

  return (
    <div className="explore">
      <header className="page-head">
        <div>
          <h1>Birds near you</h1>
          <p>
            <MapPin size={14} /> {location?.name ?? 'Locating…'} — species BirdNET’s location model expects here in{' '}
            <b>{weekLabel(week)}</b>.{' '}
            <button className="link-btn" onClick={() => setPage('settings')}>Change location</button>
          </p>
        </div>
        {list && (
          <div className="lifelist" title="Species with ≥50% expected occurrence that are in your log">
            <svg viewBox="0 0 36 36" aria-hidden="true">
              <circle cx="18" cy="18" r="15.9" className="track" />
              <circle cx="18" cy="18" r="15.9" className="fill" strokeDasharray={`${likely.length ? (likelySeen / likely.length) * 100 : 0} 100`} />
            </svg>
            <span><b>{likelySeen}/{likely.length}</b><small>common local birds logged</small></span>
          </div>
        )}
      </header>

      <div className="explore-controls card">
        <label className="week-slider">
          <span>Week of year</span>
          <input type="range" min="1" max="48" value={week} onChange={(e) => { setWeek(+e.target.value); setShown(PAGE); }} />
          <span className="months">{['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'].map((m) => <i key={m}>{m}</i>)}</span>
        </label>
        <div className="explore-filters">
          <label className="search"><Search size={16} /><input placeholder="Search species" value={query} onChange={(e) => setQuery(e.target.value)} /></label>
          <label className="check"><input type="checkbox" checked={onlyUnseen} onChange={(e) => setOnlyUnseen(e.target.checked)} /> Not yet in my log</label>
          {week !== weekOf() && <button className="link-btn" onClick={() => setWeek(weekOf())}>Back to this week</button>}
        </div>
      </div>

      {!list ? (
        <div className="grid">{Array.from({ length: 12 }, (_, i) => <div key={i} className="tile skeleton" />)}</div>
      ) : (
        <>
          <p className="fine">{filtered.length} species with at least 2% expected occurrence</p>
          <div className="grid">
            {filtered.slice(0, shown).map((s) => (
              <button key={s.scientific} className="tile" onClick={() => openSpecies(s)}>
                <BirdPhoto scientific={s.scientific} enabled={settings.showPhotos} className="tile-photo" />
                {seen.has(s.scientific) && <span className="seen"><Check size={13} strokeWidth={3} /> Logged</span>}
                <span className="tile-text">
                  <b>{s.common}</b>
                  <RegionalName scientific={s.scientific} common={s.common} lang={settings.regionalLang} />
                  <i>{s.scientific}</i>
                  <span className="likely">
                    <span className="meter"><span style={{ width: `${s.p * 100}%` }} /></span>
                    {Math.round(s.p * 100)}%
                  </span>
                </span>
              </button>
            ))}
          </div>
          {shown < filtered.length && <button className="btn more" onClick={() => setShown((n) => n + PAGE)}>Show more ({filtered.length - shown} left)</button>}
        </>
      )}
    </div>
  );
}
