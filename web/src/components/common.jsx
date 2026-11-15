import { useEffect, useState } from 'react';
import { Bird } from 'lucide-react';
import { wikiSummary } from '../lib/apis.js';
import { regionalName } from '../lib/engine.js';

/** Resolves an async value keyed by deps: undefined while loading, null if missing/failed. */
export function useAsync(fn, deps) {
  const [value, setValue] = useState(undefined);
  useEffect(() => {
    let alive = true;
    setValue(undefined);
    Promise.resolve(fn()).then((v) => alive && setValue(v ?? null), () => alive && setValue(null));
    return () => { alive = false; };
  }, deps); // eslint-disable-line react-hooks/exhaustive-deps
  return value;
}

export function BirdPhoto({ scientific, enabled = true, large = false, className = '' }) {
  const info = useAsync(() => (enabled ? wikiSummary(scientific) : null), [scientific, enabled]);
  const src = large ? info?.image : info?.thumb;
  const [loaded, setLoaded] = useState(false);
  return (
    <div className={`photo ${className} ${loaded ? 'loaded' : ''}`}>
      {src ? <img src={src} alt="" loading="lazy" onLoad={() => setLoaded(true)} /> : <Bird className="photo-fallback" strokeWidth={1.4} />}
    </div>
  );
}

export function RegionalName({ scientific, common, lang, className = 'regional' }) {
  const name = useAsync(() => regionalName(scientific, common, lang), [scientific, lang]);
  return name ? <span className={className} lang={lang}>{name}</span> : null;
}

export function ConfBadge({ value }) {
  const tier = value >= 0.85 ? 'high' : value >= 0.7 ? 'mid' : 'low';
  return <span className={`conf conf-${tier}`}>{Math.round(value * 100)}%</span>;
}

export function Segmented({ value, options, onChange, label }) {
  return (
    <div className="segmented" role="radiogroup" aria-label={label}>
      {options.map(([v, text]) => (
        <button key={String(v)} role="radio" aria-checked={value === v} className={value === v ? 'on' : ''} onClick={() => onChange(v)}>
          {text}
        </button>
      ))}
    </div>
  );
}

export function Toggle({ checked, onChange, label, hint }) {
  return (
    <label className="toggle-row">
      <span>
        <b>{label}</b>
        {hint && <small>{hint}</small>}
      </span>
      <input type="checkbox" role="switch" checked={checked} onChange={(e) => onChange(e.target.checked)} />
      <i aria-hidden="true" />
    </label>
  );
}

export const fmtTime = (t) => new Date(t).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
export const fmtDateTime = (t) => new Date(t).toLocaleString([], { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
export const fmtSec = (s) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`;
export const fmtCoords = (lat, lon) => `${Math.abs(lat).toFixed(4)}°${lat >= 0 ? 'N' : 'S'}, ${Math.abs(lon).toFixed(4)}°${lon >= 0 ? 'E' : 'W'}`;

export function relTime(t) {
  const s = Math.round((Date.now() - t) / 1000);
  if (s < 45) return 'just now';
  if (s < 3600) return `${Math.round(s / 60)} min ago`;
  if (s < 86400) return `${Math.round(s / 3600)} h ago`;
  return new Date(t).toLocaleDateString([], { day: 'numeric', month: 'short' });
}
