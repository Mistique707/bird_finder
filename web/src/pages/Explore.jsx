import { useEffect, useMemo, useRef, useState } from 'react';
import { Check, Crosshair, Languages, MapPin, Search, Sparkles, Trophy, X } from 'lucide-react';
import { occurrenceYear, weekOf, weekLabel, bundledNames, cachedWikiNames, LANGUAGES } from '../lib/engine.js';
import { iucnStatuses } from '../lib/apis.js';
import { TIERS, UNLIKELY, tierOf, seasonOf, groupOf, GROUP_LABELS, levelOf, dailyPick, iucnCode, IUCN } from '../lib/explore.js';
import { BirdPhoto, RegionalName } from '../components/common.jsx';

const PAGE = 36;
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const monthOfWeek = (w) => MONTHS[Math.floor((w - 1) / 4)];
const DEFAULT_FILTERS = { tier: 'all', season: 'all', group: 'all', status: 'all', sort: 'likely', unseen: false, huntOnly: false };

export default function Explore({ settings, updateSettings, engine, location, history, hunt, toggleHunt, openSpecies, active, setPage }) {
  const [week, setWeek] = useState(weekOf);
  const [year, setYear] = useState(null);
  const [iucn, setIucn] = useState({});
  const [iucnLoading, setIucnLoading] = useState(false);
  const [filters, setFilters] = useState(DEFAULT_FILTERS);
  const [query, setQuery] = useState('');
  const [shown, setShown] = useState(PAGE);
  const [localNames, setLocalNames] = useState(null);
  const sentinel = useRef(null);
  const lang = settings.regionalLang;
  const setFilter = (patch) => { setFilters((f) => ({ ...f, ...patch })); setShown(PAGE); };

  // 48-week occurrence for every species at this location (recomputed when the place changes).
  const locKey = location ? `${location.lat.toFixed(2)},${location.lon.toFixed(2)}` : null;
  useEffect(() => {
    if (!active || engine.state !== 'ready' || !location) return;
    let alive = true;
    setYear(null);
    occurrenceYear(location.lat, location.lon).then((y) => alive && setYear(y));
    return () => { alive = false; };
  }, [active, engine.state, locKey]); // eslint-disable-line react-hooks/exhaustive-deps

  // Conservation status for everything that occurs here at some point in the year.
  useEffect(() => {
    if (!year) return;
    let alive = true;
    const names = year.filter((s) => s.weeks.some((v) => v >= 0.005)).map((s) => s.scientific);
    setIucnLoading(true);
    iucnStatuses(names, (partial) => alive && setIucn(partial)).then((all) => {
      if (alive) { setIucn(all); setIucnLoading(false); }
    });
    return () => { alive = false; };
  }, [year]);

  // Local-language names for search.
  useEffect(() => {
    if (!lang) { setLocalNames(null); return; }
    let alive = true;
    const bundled = bundledNames(lang);
    if (bundled) bundled.then((m) => alive && setLocalNames(m));
    else setLocalNames(cachedWikiNames(lang));
    return () => { alive = false; };
  }, [lang, query]);

  const seen = useMemo(() => new Set(history.map((h) => h.scientific)), [history]);

  const species = useMemo(() => (year ?? []).map((s) => {
    const p = s.weeks[week - 1];
    const season = seasonOf(s.weeks, week);
    return { ...s, p, tier: tierOf(p), season, group: groupOf(s.common), status: iucnCode(iucn[s.scientific]) };
  }), [year, week, iucn]);

  // ---- Score: points for each logged species, by how rare it is here at its best ----
  const score = useMemo(() => {
    const byName = new Map(species.map((s) => [s.scientific, s]));
    let points = 0;
    const perTier = Object.fromEntries([...TIERS, UNLIKELY].map((t) => [t.id, { seen: 0, total: 0 }]));
    for (const s of species) if (s.p >= 0.005) perTier[s.tier.id].total++;
    for (const name of seen) {
      const s = byName.get(name);
      points += s ? tierOf(s.season.max).points : UNLIKELY.points;
      if (s && s.p >= 0.005) perTier[s.tier.id].seen++;
    }
    return { points, perTier, level: levelOf(points) };
  }, [species, seen]);

  // ---- Bird of the day: an uncommon or rare bird around now that you haven't logged ----
  const today = new Date().toDateString();
  const birdOfDay = useMemo(() => {
    const candidates = species.filter((s) => (s.tier.id === 'uncommon' || s.tier.id === 'rare') && !seen.has(s.scientific))
      .sort((a, b) => a.scientific.localeCompare(b.scientific));
    return dailyPick(candidates, `${today}|${locKey}`);
  }, [species, seen, today, locKey]);

  // ---- Filtering ----
  const q = query.trim().toLowerCase();
  const filtered = useMemo(() => {
    const f = filters;
    let list = species.filter((s) => {
      if (q) {
        const local = localNames?.get(s.scientific)?.toLowerCase();
        if (!s.common.toLowerCase().includes(q) && !s.scientific.toLowerCase().includes(q) && !local?.includes(q)) return false;
      } else if (s.p < 0.005 && !(f.huntOnly && hunt.includes(s.scientific))) return false; // search reaches every species
      if (f.tier !== 'all' && s.tier.id !== f.tier) return false;
      if (f.season === 'resident' && !s.season.resident) return false;
      if (f.season === 'visitor' && (s.season.resident || s.p < 0.03)) return false;
      if (f.season === 'arriving' && !s.season.arriving) return false;
      if (f.season === 'leaving' && !s.season.leaving) return false;
      if (f.group !== 'all' && s.group !== f.group) return false;
      if (f.status === 'threatened' && !['vu', 'en', 'cr'].includes(s.status)) return false;
      if (f.status === 'nt' && s.status !== 'nt') return false;
      if (f.status === 'lc' && s.status !== 'lc') return false;
      if (f.unseen && seen.has(s.scientific)) return false;
      if (f.huntOnly && !hunt.includes(s.scientific)) return false;
      return true;
    });
    const sorters = {
      likely: (a, b) => b.p - a.p,
      rarest: (a, b) => a.p - b.p,
      name: (a, b) => a.common.localeCompare(b.common),
      points: (a, b) => b.tier.points - a.tier.points || b.p - a.p,
    };
    return list.sort(sorters[f.sort]);
  }, [species, filters, q, localNames, seen, hunt]);

  const tierCounts = useMemo(() => {
    const c = {};
    for (const s of species) if (s.p >= 0.005) c[s.tier.id] = (c[s.tier.id] ?? 0) + 1;
    return c;
  }, [species]);

  // Infinite scroll.
  useEffect(() => {
    const el = sentinel.current;
    if (!el) return;
    const io = new IntersectionObserver(([e]) => e.isIntersecting && setShown((n) => n + PAGE), { rootMargin: '600px' });
    io.observe(el);
    return () => io.disconnect();
  }, [filtered.length > shown]); // eslint-disable-line react-hooks/exhaustive-deps

  const huntSpecies = hunt.map((h) => species.find((s) => s.scientific === h)).filter(Boolean);
  const filtersOn = JSON.stringify(filters) !== JSON.stringify(DEFAULT_FILTERS) || q;

  return (
    <div className="explore">
      <header className="page-head">
        <div>
          <h1>Birds near you</h1>
          <p>
            <MapPin size={14} /> {location?.name ?? 'Locating…'} · {weekLabel(week)}{' '}
            <button className="link-btn" onClick={() => setPage('settings')}>Change place</button>
          </p>
        </div>
        <label className="lang-pick">
          <Languages size={16} />
          <select value={lang} onChange={(e) => updateSettings({ regionalLang: e.target.value })} aria-label="Show names in">
            {LANGUAGES.map(([code, name]) => <option key={code} value={code}>{code ? name : 'Names: English only'}</option>)}
          </select>
        </label>
      </header>

      {!year ? (
        <div className="explore-top"><div className="card skeleton" style={{ height: 220 }} /><div className="card skeleton" style={{ height: 220 }} /><div className="card skeleton" style={{ height: 220 }} /></div>
      ) : (
        <div className="explore-top">
          <div className="card level-card">
            <div className="level-head">
              <Trophy size={22} />
              <div><small>Your level</small><b>{score.level.name}</b></div>
              <span className="pts">{score.points.toLocaleString()} pts</span>
            </div>
            <div className="level-bar"><i style={{ width: `${score.level.progress * 100}%` }} /></div>
            <small className="fine">{score.level.next ? `${score.level.next - score.points} pts to ${score.level.nextName}` : 'Top level reached!'}</small>
            <ul className="tier-progress">
              {TIERS.map((t) => (
                <li key={t.id}>
                  <span className={`tier-dot t-${t.id}`} /> {t.label}
                  <b>{score.perTier[t.id].seen}/{score.perTier[t.id].total}</b>
                  <small>+{t.points}</small>
                </li>
              ))}
            </ul>
          </div>

          {birdOfDay ? (
            <div className="card botd">
              <BirdPhoto scientific={birdOfDay.scientific} enabled={settings.showPhotos} large className="botd-photo" />
              <div className="botd-text">
                <span className="eyebrow"><Sparkles size={13} /> Bird of the day</span>
                <h2>{birdOfDay.common}</h2>
                <RegionalName scientific={birdOfDay.scientific} common={birdOfDay.common} lang={lang} />
                <i className="sci">{birdOfDay.scientific}</i>
                <p>
                  <span className={`tier-badge t-${birdOfDay.tier.id}`}>{birdOfDay.tier.label}</span>
                  {' '}{Math.round(birdOfDay.p * 100)}% likely this week · best around {monthOfWeek(birdOfDay.season.peakWeek)}
                </p>
                <div className="row-gap">
                  <button className={`btn ${hunt.includes(birdOfDay.scientific) ? 'hunting' : 'primary'}`} onClick={() => toggleHunt(birdOfDay.scientific)}>
                    <Crosshair size={15} /> {hunt.includes(birdOfDay.scientific) ? 'Hunting' : `Hunt it · +${birdOfDay.tier.points}`}
                  </button>
                  <button className="btn" onClick={() => openSpecies(birdOfDay)}>Learn more</button>
                </div>
              </div>
            </div>
          ) : <div className="card botd empty-botd"><p className="empty">You’ve logged every uncommon and rare bird around this week. Impressive!</p></div>}

          <div className="card hunt-card">
            <div className="card-head">
              <h2 className="eyebrow"><Crosshair size={13} /> Hunt list</h2>
              {huntSpecies.length > 0 && <small>{huntSpecies.filter((s) => seen.has(s.scientific)).length}/{huntSpecies.length} found</small>}
            </div>
            {huntSpecies.length ? (
              <ul className="hunt-list">
                {huntSpecies.map((s) => (
                  <li key={s.scientific} className={seen.has(s.scientific) ? 'found' : ''}>
                    <button className="hunt-open" onClick={() => openSpecies(s)}>
                      <BirdPhoto scientific={s.scientific} enabled={settings.showPhotos} className="sm" />
                      <span className="names"><b>{s.common}</b><small>{seen.has(s.scientific) ? 'Found!' : `${s.tier.label} · ${Math.round(s.p * 100)}% now`}</small></span>
                    </button>
                    {seen.has(s.scientific) ? <Check size={18} className="ok" /> : (
                      <button className="icon-btn" onClick={() => toggleHunt(s.scientific)} aria-label={`Remove ${s.common} from hunt list`}><X size={15} /></button>
                    )}
                  </li>
                ))}
              </ul>
            ) : (
              <p className="empty">Tap <Crosshair size={13} /> on any bird to add it here. When you hear one, you’ll get a “Target found” alert and the points.</p>
            )}
          </div>
        </div>
      )}

      <div className="card explore-controls">
        <div className="control-row">
          <label className="search grow">
            <Search size={16} />
            <input placeholder={`Search all 6,500 species${lang ? ' — English, scientific or local name' : ''}`} value={query} onChange={(e) => { setQuery(e.target.value); setShown(PAGE); }} />
            {query && <button className="icon-btn" onClick={() => setQuery('')} aria-label="Clear search"><X size={14} /></button>}
          </label>
          <label className="week-compact">
            <span>{weekLabel(week)}</span>
            <input type="range" min="1" max="48" value={week} onChange={(e) => { setWeek(+e.target.value); setShown(PAGE); }} aria-label="Week of year" />
            {week !== weekOf() && <button className="link-btn" onClick={() => setWeek(weekOf())}>Now</button>}
          </label>
        </div>

        <div className="chips" role="group" aria-label="Rarity">
          <span className="chips-label">Rarity</span>
          <Chip on={filters.tier === 'all'} onClick={() => setFilter({ tier: 'all' })}>All</Chip>
          {TIERS.map((t) => (
            <Chip key={t.id} on={filters.tier === t.id} onClick={() => setFilter({ tier: t.id })}>
              <span className={`tier-dot t-${t.id}`} /> {t.label} <small>{tierCounts[t.id] ?? 0}</small>
            </Chip>
          ))}
        </div>
        <div className="chips" role="group" aria-label="Season">
          <span className="chips-label">Season</span>
          {[['all', 'Any time'], ['resident', 'Year-round'], ['visitor', 'Seasonal visitors'], ['arriving', 'Arriving soon'], ['leaving', 'Leaving soon']].map(([id, label]) => (
            <Chip key={id} on={filters.season === id} onClick={() => setFilter({ season: id })}>{label}</Chip>
          ))}
        </div>
        <div className="control-row selects">
          <select value={filters.group} onChange={(e) => setFilter({ group: e.target.value })} aria-label="Bird group">
            <option value="all">All bird groups</option>
            {Object.entries(GROUP_LABELS).map(([id, label]) => <option key={id} value={id}>{label}</option>)}
          </select>
          <select value={filters.status} onChange={(e) => setFilter({ status: e.target.value })} aria-label="Conservation status">
            <option value="all">Any conservation status</option>
            <option value="threatened">Threatened (VU / EN / CR)</option>
            <option value="nt">Near threatened</option>
            <option value="lc">Least concern</option>
          </select>
          <select value={filters.sort} onChange={(e) => setFilter({ sort: e.target.value })} aria-label="Sort">
            <option value="likely">Most likely first</option>
            <option value="rarest">Rarest first</option>
            <option value="points">Most points</option>
            <option value="name">A–Z</option>
          </select>
          <label className="check"><input type="checkbox" checked={filters.unseen} onChange={(e) => setFilter({ unseen: e.target.checked })} /> Not yet logged</label>
          <label className="check"><input type="checkbox" checked={filters.huntOnly} onChange={(e) => setFilter({ huntOnly: e.target.checked })} /> Hunt list</label>
          {filtersOn && <button className="link-btn" onClick={() => { setFilters(DEFAULT_FILTERS); setQuery(''); setShown(PAGE); }}><X size={14} /> Reset</button>}
        </div>
        {iucnLoading && filters.status !== 'all' && <p className="fine">Loading conservation statuses from Wikidata…</p>}
      </div>

      {year && (
        <>
          <p className="fine">
            {filtered.length} {filtered.length === 1 ? 'species' : 'species'}
            {q ? ' match your search' : ' with at least 0.5% expected occurrence'} · tap a bird for details, <Crosshair size={12} /> to hunt it
          </p>
          <div className="grid">
            {filtered.slice(0, shown).map((s) => (
              <Tile key={s.scientific} s={s} week={week} lang={lang} photos={settings.showPhotos}
                seen={seen.has(s.scientific)} hunting={hunt.includes(s.scientific)}
                onOpen={() => openSpecies(s)} onHunt={() => toggleHunt(s.scientific)} />
            ))}
          </div>
          {!filtered.length && <div className="card empty-state"><h2>No birds match</h2><p>Try a different rarity or season, or clear the filters.</p></div>}
          {shown < filtered.length && <div ref={sentinel} className="sentinel" />}
        </>
      )}
    </div>
  );
}

function Chip({ on, onClick, children }) {
  return <button className={`chip ${on ? 'on' : ''}`} onClick={onClick} aria-pressed={on}>{children}</button>;
}

function Tile({ s, week, lang, photos, seen, hunting, onOpen, onHunt }) {
  const max = Math.max(s.season.max, 0.01);
  // Array.from: Float32Array.map would coerce these strings back to numbers (NaN).
  const points = Array.from(s.weeks, (v, i) => `${(i / 47) * 100},${28 - (v / max) * 26}`).join(' ');
  const tag = s.season.arriving ? 'Arriving soon' : s.season.leaving ? 'Leaving soon' : !s.season.resident && s.p >= 0.03 ? 'Seasonal visitor' : null;
  return (
    <div className={`tile ${seen ? 'is-seen' : ''}`}>
      <button className="tile-main" onClick={onOpen}>
        <BirdPhoto scientific={s.scientific} enabled={photos} className="tile-photo" />
        <span className={`tier-badge t-${s.tier.id} on-photo`}>{s.tier.label}</span>
        {s.status && s.status !== 'lc' && <span className={`iucn iucn-${s.status} on-photo-2`} title={IUCN[s.status]}>{s.status.toUpperCase()}</span>}
        <span className="tile-text">
          <b>{s.common}</b>
          <RegionalName scientific={s.scientific} common={s.common} lang={lang} />
          <i>{s.scientific}</i>
          <svg className="spark" viewBox="0 0 100 30" preserveAspectRatio="none" aria-hidden="true">
            <polyline points={`0,30 ${points} 100,30`} />
            <line x1={((week - 1) / 47) * 100} x2={((week - 1) / 47) * 100} y1="0" y2="30" />
          </svg>
          <span className="tile-foot">
            <span className="likely">{s.p >= 0.01 ? `${Math.round(s.p * 100)}%` : '<1%'} now</span>
            {tag && <span className="season-tag">{tag}</span>}
            <span className="tile-pts">{seen ? <><Check size={13} strokeWidth={3} /> Logged</> : `+${s.tier.points}`}</span>
          </span>
        </span>
      </button>
      {!seen && (
        <button className={`hunt-btn ${hunting ? 'on' : ''}`} onClick={onHunt} aria-pressed={hunting} aria-label={hunting ? `Stop hunting ${s.common}` : `Hunt ${s.common}`} title={hunting ? 'On your hunt list' : 'Add to hunt list'}>
          <Crosshair size={16} />
        </button>
      )}
    </div>
  );
}
