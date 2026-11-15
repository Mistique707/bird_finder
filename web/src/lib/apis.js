// Keyless, CORS-friendly public APIs. Only species names / coordinates leave the machine.
const cache = new Map();
const cached = (key, fn) => {
  if (!cache.has(key)) cache.set(key, fn().catch(() => null));
  return cache.get(key);
};
const json = (url) => fetch(url).then((r) => (r.ok ? r.json() : Promise.reject(new Error(r.status))));
const wikiTitle = (s) => encodeURIComponent(s.replace(/ /g, '_'));

export const wikiUrl = (scientific) => `https://en.wikipedia.org/wiki/${wikiTitle(scientific)}`;

/** Photo + intro paragraph from Wikipedia. */
export const wikiSummary = (scientific) => cached(`wiki:${scientific}`, async () => {
  const j = await json(`https://en.wikipedia.org/api/rest_v1/page/summary/${wikiTitle(scientific)}`);
  return {
    title: j.title,
    extract: j.extract,
    thumb: j.thumbnail?.source ?? null,
    image: j.originalimage?.source ?? j.thumbnail?.source ?? null,
    url: j.content_urls?.desktop?.page ?? wikiUrl(scientific),
  };
});

/** Taxonomy from GBIF's backbone. */
export const gbifSpecies = (scientific) => cached(`gbif:${scientific}`, async () => {
  const j = await json(`https://api.gbif.org/v1/species/match?name=${encodeURIComponent(scientific)}&class=Aves`);
  if (!j.usageKey) return null;
  return { key: j.speciesKey ?? j.usageKey, order: j.order, family: j.family, genus: j.genus };
});

/** GBIF records within ~55 km of a point (a bounding box of ±0.5°). */
export const gbifNearby = (taxonKey, lat, lon) => cached(`near:${taxonKey}:${lat.toFixed(1)}:${lon.toFixed(1)}`, async () => {
  const box = (v) => `${(v - 0.5).toFixed(3)},${(v + 0.5).toFixed(3)}`;
  const j = await json(`https://api.gbif.org/v1/occurrence/search?taxonKey=${taxonKey}&decimalLatitude=${box(lat)}&decimalLongitude=${box(lon)}&limit=0`);
  return j.count;
});

/** World map tiles: GBIF basemap + occurrence density for a taxon (one 512 px tile at z=0). */
export const gbifMap = (taxonKey) => ({
  base: 'https://tile.gbif.org/3857/omt/0/0/0@2x.png?style=gbif-light',
  density: `https://api.gbif.org/v2/map/occurrence/density/0/0/0@2x.png?taxonKey=${taxonKey}&bin=hex&hexPerTile=70&style=classic.poly`,
});

/** IUCN status and a Commons recording of the bird (Wikidata P141 / P51). */
export const wikidata = (scientific) => cached(`wd:${scientific}`, async () => {
  const q = `SELECT ?statusLabel ?audio WHERE {
    ?item wdt:P225 "${scientific.replace(/"/g, '')}" .
    OPTIONAL { ?item wdt:P141 ?status . }
    OPTIONAL { ?item wdt:P51 ?audio . }
    SERVICE wikibase:label { bd:serviceParam wikibase:language "en" . }
  } LIMIT 5`;
  const j = await json(`https://query.wikidata.org/sparql?format=json&query=${encodeURIComponent(q)}`);
  const rows = j.results.bindings;
  const filePath = rows.find((r) => r.audio)?.audio.value;
  return {
    status: rows.find((r) => r.statusLabel)?.statusLabel.value ?? null,
    audio: filePath ? await commonsFileUrl(decodeURIComponent(filePath.split('/Special:FilePath/')[1])) : null,
  };
});

/** Direct upload.wikimedia.org URL (CORS-enabled, unlike the Special:FilePath redirect). */
async function commonsFileUrl(name) {
  const j = await json(`https://commons.wikimedia.org/w/api.php?action=query&titles=${encodeURIComponent(`File:${name}`)}&prop=imageinfo&iiprop=url&format=json&origin=*`);
  return Object.values(j.query?.pages ?? {})[0]?.imageinfo?.[0]?.url ?? null;
}

/** Best-rated Xeno-canto recordings (needs a free v3 key). */
export const xenoCanto = (scientific, key) => cached(`xc:${scientific}:${key}`, async () => {
  const query = encodeURIComponent(`sp:"${scientific}" q:A`);
  const j = await json(`https://xeno-canto.org/api/3/recordings?query=${query}&key=${encodeURIComponent(key)}&per_page=50`);
  return (j.recordings ?? []).slice(0, 5).map((r) => ({
    url: r.file?.startsWith('//') ? `https:${r.file}` : r.file,
    type: r.type, recordist: r.rec, country: r.cnt, length: r.length, page: `https://xeno-canto.org/${r.id}`,
  }));
});

const WMO = {
  0: 'Clear', 1: 'Mainly clear', 2: 'Partly cloudy', 3: 'Overcast', 45: 'Fog', 48: 'Rime fog',
  51: 'Light drizzle', 53: 'Drizzle', 55: 'Heavy drizzle', 61: 'Light rain', 63: 'Rain', 65: 'Heavy rain',
  71: 'Light snow', 73: 'Snow', 75: 'Heavy snow', 80: 'Rain showers', 81: 'Rain showers', 82: 'Violent showers',
  95: 'Thunderstorm', 96: 'Thunderstorm, hail', 99: 'Thunderstorm, hail',
};

/** Current weather + today's sunrise/sunset from Open-Meteo (no key). Cached 10 min per ~1 km. */
export function weather(lat, lon) {
  const slot = Math.floor(Date.now() / 600e3);
  return cached(`wx:${lat.toFixed(2)}:${lon.toFixed(2)}:${slot}`, async () => {
    const j = await json(`https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,is_day&daily=sunrise,sunset&timezone=auto&forecast_days=1`);
    return {
      tempC: j.current.temperature_2m,
      humidity: j.current.relative_humidity_2m,
      wind: j.current.wind_speed_10m,
      code: j.current.weather_code,
      text: WMO[j.current.weather_code] ?? 'Unknown',
      isDay: !!j.current.is_day,
      sunrise: j.daily.sunrise[0],
      sunset: j.daily.sunset[0],
      utcOffset: j.utc_offset_seconds,
    };
  });
}

/** "Kothrud, Maharashtra, India" for a coordinate. */
export const placeName = (lat, lon) => cached(`geo:${lat.toFixed(3)}:${lon.toFixed(3)}`, async () => {
  const j = await json(`https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=${lat}&longitude=${lon}&localityLanguage=en`);
  return [j.locality || j.city, j.principalSubdivision, j.countryName].filter(Boolean).filter((v, i, a) => a.indexOf(v) === i).join(', ') || null;
});

/** Place search for the manual location picker. */
export async function searchPlaces(q) {
  if (q.trim().length < 2) return [];
  const j = await json(`https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(q)}&count=6&language=en`);
  return (j.results ?? []).map((r) => ({
    name: [r.name, r.admin1, r.country].filter(Boolean).join(', '), lat: r.latitude, lon: r.longitude,
  }));
}
