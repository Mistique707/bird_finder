import { useEffect, useRef, useState } from 'react';
import { CalendarDays, Crosshair, Download, ExternalLink, Image as ImageIcon, MapPin, Trash2, X } from 'lucide-react';
import { gbifMap, gbifNearby, gbifSpecies, wikidata, wikiSummary, xenoCanto } from '../lib/apis.js';
import { seasonality, weekOf, weekLabel } from '../lib/engine.js';
import { getClip, downloadBlob } from '../lib/store.js';
import { renderShareCard } from '../lib/shareCard.js';
import { ConfBadge, RegionalName, fmtCoords, fmtDateTime, useAsync } from './common.jsx';
import ClipPlayer from './ClipPlayer.jsx';

const IUCN = {
  'least concern': 'lc', 'near threatened': 'nt', vulnerable: 'vu', endangered: 'en',
  'critically endangered': 'cr', 'extinct in the wild': 'ew', extinct: 'ex', 'data deficient': 'dd',
};

export default function SpeciesDetail({ species, detection, settings, location, history, hunt, toggleHunt, onClose, onDelete, onOpen }) {
  const { scientific, common } = species;
  const here = detection ?? location ?? { lat: 18.52, lon: 73.86 };
  const panelRef = useRef(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [sharing, setSharing] = useState(false);

  const wiki = useAsync(() => (settings.showPhotos ? wikiSummary(scientific) : null), [scientific, settings.showPhotos]);
  const taxon = useAsync(() => gbifSpecies(scientific), [scientific]);
  const nearby = useAsync(() => taxon && gbifNearby(taxon.key, here.lat, here.lon), [taxon?.key, here.lat, here.lon]);
  const wd = useAsync(() => wikidata(scientific), [scientific]);
  const xc = useAsync(() => (settings.referenceCalls && settings.xenoCantoKey ? xenoCanto(scientific, settings.xenoCantoKey) : null),
    [scientific, settings.referenceCalls, settings.xenoCantoKey]);
  const season = useAsync(() => seasonality(scientific, here.lat, here.lon), [scientific, here.lat, here.lon]);
  const clip = useAsync(() => (detection?.clipId ? getClip(detection.clipId) : null), [detection?.clipId]);

  const mine = history.filter((h) => h.scientific === scientific);
  const thisWeek = weekOf();

  useEffect(() => { panelRef.current?.focus(); }, []);

  async function share() {
    setSharing(true);
    try {
      const blob = await renderShareCard(species, detection);
      downloadBlob(blob, `${common.replace(/\W+/g, '-')}.png`);
    } finally {
      setSharing(false);
    }
  }

  const clipHighlight = clip && detection ? (detection.clipWindow ?? centerHighlight(clip.size)) : null;

  return (
    <div className="drawer-scrim" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <aside className="drawer" ref={panelRef} tabIndex={-1} aria-label={`${common} details`} role="dialog" aria-modal="true">
        <header className="hero">
          {wiki?.image ? <img src={wiki.image} alt={common} /> : <div className="hero-empty"><ImageIcon size={42} strokeWidth={1.2} /></div>}
          <div className="hero-scrim" />
          <button className="icon-btn hero-close" onClick={onClose} aria-label="Close"><X size={20} /></button>
          <div className="hero-text">
            <h2>{common}</h2>
            <RegionalName scientific={scientific} common={common} lang={settings.regionalLang} className="hero-regional" />
            <p className="sci">{scientific}</p>
            <div className="hero-chips">
              {detection && <ConfBadge value={detection.confidence} />}
              {wd?.status && <span className={`iucn iucn-${IUCN[wd.status.toLowerCase()] ?? 'dd'}`} title="IUCN Red List status">{wd.status}</span>}
              {mine.length > 0 && <span className="chip-ghost">In your log ×{mine.length}</span>}
            </div>
          </div>
        </header>

        <div className="drawer-body">
          {detection && (
            <section>
              <h3>Your recording</h3>
              {clip ? (
                <>
                  <ClipPlayer src={clip} highlight={clipHighlight} label="your recording" />
                  <p className="fine"><span className="hl-key" /> highlighted = the 3 s window BirdNET heard it in</p>
                </>
              ) : <p className="muted">{detection.clipId ? (clip === undefined ? 'Loading clip…' : 'This clip is no longer stored.') : `Identified from ${detection.fileName ?? 'a file'}${detection.offsetSec != null ? ` at ${Math.round(detection.offsetSec)} s` : ''}.`}</p>}
            </section>
          )}

          {settings.referenceCalls && (
            <section>
              <h3>Reference call</h3>
              {wd === undefined && !xc?.length && <p className="muted">Looking for a reference recording…</p>}
              {wd?.audio && (
                <div className="ref">
                  <ClipPlayer src={wd.audio} label="reference call" />
                  <p className="fine">Wikimedia Commons recording via Wikidata</p>
                </div>
              )}
              {xc?.map((r) => (
                <div className="ref" key={r.url}>
                  <ClipPlayer src={r.url} label={`${r.type} reference`} />
                  <p className="fine">
                    <span className="tag">{r.type || 'recording'}</span> {r.recordist} · {r.country} ·{' '}
                    <a href={r.page} target="_blank" rel="noreferrer">xeno-canto</a>
                  </p>
                </div>
              ))}
              {wd !== undefined && !wd?.audio && !xc?.length && (
                <p className="muted">
                  No keyless recording found.{!settings.xenoCantoKey && ' Add a free Xeno-canto key in Settings → Advanced for more.'}
                </p>
              )}
            </section>
          )}

          {wiki?.extract && (
            <section>
              <h3>About</h3>
              <p className="prose">{wiki.extract}</p>
              <a className="fine link" href={wiki.url} target="_blank" rel="noreferrer">Read more on Wikipedia <ExternalLink size={12} /></a>
            </section>
          )}

          <section>
            <h3>When to find it near {detection ? 'this spot' : 'you'}</h3>
            {season ? (
              <>
                <div className="season" role="img" aria-label="Expected occurrence by week">
                  {season.map((p, i) => (
                    <i key={i} className={i + 1 === thisWeek ? 'now' : ''} style={{ height: `${Math.max(3, p * 100)}%` }} title={`${weekLabel(i + 1)}: ${Math.round(p * 100)}%`} />
                  ))}
                </div>
                <div className="season-axis">{['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'].map((m, i) => <span key={i}>{m}</span>)}</div>
                <p className="fine">BirdNET location model · {Math.round(season[thisWeek - 1] * 100)}% likely around this week</p>
              </>
            ) : season === undefined ? <div className="skeleton" style={{ height: 70 }} /> : <p className="muted">Not in BirdNET’s location model.</p>}
          </section>

          <section className="facts">
            <h3>Quick facts</h3>
            <dl>
              <dt>Order</dt><dd>{taxon?.order ?? '—'}</dd>
              <dt>Family</dt><dd>{taxon?.family ?? '—'}</dd>
              <dt>Conservation</dt><dd>{wd?.status ?? '—'}</dd>
              <dt>Records nearby</dt><dd>{nearby != null ? `${nearby.toLocaleString()} on GBIF within ~50 km` : '—'}</dd>
              {mine.length > 0 && (<><dt>You’ve logged it</dt><dd>{mine.length}× · first {new Date(mine[mine.length - 1].time).toLocaleDateString()}</dd></>)}
            </dl>
          </section>

          {taxon && (
            <section>
              <h3>Where it’s been recorded</h3>
              <div className="range-map">
                <img src={gbifMap(taxon.key).base} alt="" />
                <img src={gbifMap(taxon.key).density} alt={`Map of GBIF records for ${common}`} />
                <span className="pin" style={pinStyle(here.lat, here.lon)} title="You" />
              </div>
              <a className="fine link" href={`https://www.gbif.org/species/${taxon.key}`} target="_blank" rel="noreferrer">GBIF occurrence data <ExternalLink size={12} /></a>
            </section>
          )}

          {detection && (
            <section className="facts">
              <h3>Details</h3>
              <dl>
                <dt><CalendarDays size={14} /> Recorded</dt><dd>{fmtDateTime(detection.time)}</dd>
                <dt><MapPin size={14} /> Location</dt><dd>{detection.place && <>{detection.place}<br /></>}<span className="mono">{fmtCoords(detection.lat, detection.lon)}</span></dd>
                <dt>Weather</dt><dd>{detection.weather ? `${detection.weather.text}, ${detection.weather.tempC.toFixed(1)} °C` : '—'}</dd>
                <dt>Source</dt><dd>{detection.source === 'file' ? `File · ${detection.fileName}` : 'Live microphone'}</dd>
                <dt>Model</dt><dd>{detection.model}</dd>
              </dl>
            </section>
          )}

          {mine.length > 1 && (
            <section>
              <h3>Other times you heard it</h3>
              <div className="mini-list">
                {mine.filter((m) => m.id !== detection?.id).slice(0, 5).map((m) => (
                  <button key={m.id} onClick={() => onOpen(species, m)}>
                    <span>{fmtDateTime(m.time)}</span><ConfBadge value={m.confidence} />
                  </button>
                ))}
              </div>
            </section>
          )}
        </div>

        <footer className="drawer-actions">
          <span className="row-gap">
            {mine.length === 0 && (
              <button className={`btn ${hunt.includes(scientific) ? 'hunting' : 'primary'}`} onClick={() => toggleHunt(scientific)} aria-pressed={hunt.includes(scientific)}>
                <Crosshair size={16} /> {hunt.includes(scientific) ? 'On your hunt list' : 'Hunt this bird'}
              </button>
            )}
            <button className="btn" onClick={share} disabled={sharing}><Download size={16} /> {sharing ? 'Rendering…' : 'Share card'}</button>
          </span>
          {detection && (confirmDelete ? (
            <span className="confirm">
              Delete this detection?
              <button className="btn danger" onClick={() => onDelete(detection.id)}>Delete</button>
              <button className="btn ghost" onClick={() => setConfirmDelete(false)}>Cancel</button>
            </span>
          ) : (
            <button className="btn ghost danger-text" onClick={() => setConfirmDelete(true)}><Trash2 size={16} /> Delete</button>
          ))}
        </footer>
      </aside>
    </div>
  );
}

/** WAV size → duration → the 3 s window centred in the clip. */
function centerHighlight(bytes) {
  const dur = (bytes - 44) / 2 / 48000;
  if (dur <= 3.4) return null;
  const start = Math.max(0, (dur - 3) / 2);
  return [start, start + 3];
}

/**
 * Web-Mercator position of a point on the z=0 world tile, as CSS percentages of the map box,
 * which shows the tile's middle 70% vertically (the poles are cropped — see .range-map).
 */
function pinStyle(lat, lon) {
  const x = (lon + 180) / 360;
  const s = Math.sin((Math.max(-85, Math.min(85, lat)) * Math.PI) / 180);
  const y = 0.5 - Math.log((1 + s) / (1 - s)) / (4 * Math.PI);
  return { left: `${x * 100}%`, top: `${((y - 0.15) / 0.7) * 100}%` };
}
