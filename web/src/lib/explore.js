// Rarity, seasonality, bird groups and points for the "Birds near you" page.
// All derived from BirdNET's location model: 48 weekly occurrence values per species.

export const TIERS = [
  { id: 'common', label: 'Common', min: 0.5, points: 10 },
  { id: 'uncommon', label: 'Uncommon', min: 0.15, points: 25 },
  { id: 'rare', label: 'Rare', min: 0.03, points: 50 },
  { id: 'scarce', label: 'Very rare', min: 0.005, points: 100 },
];
export const UNLIKELY = { id: 'unlikely', label: 'Unlikely here', min: 0, points: 150 };
export const tierOf = (p) => TIERS.find((t) => p >= t.min) ?? UNLIKELY;

/** Season profile from 48 weekly values, relative to the current week (1-based). */
export function seasonOf(weeks, week) {
  let max = 0, min = 1;
  for (const v of weeks) { if (v > max) max = v; if (v < min) min = v; }
  const now = weeks[week - 1];
  const ahead = [1, 2, 3, 4].reduce((s, k) => s + weeks[(week - 1 + k) % 48], 0) / 4;
  let peak = 0;
  weeks.forEach((v, i) => { if (v > weeks[peak]) peak = i; });
  const resident = max > 0 && min >= max * 0.3;
  return {
    resident,
    // Numbers still building toward this bird's seasonal peak over the next month.
    arriving: !resident && ahead >= 0.03 && ahead > now * 1.08 && now < max * 0.85,
    // Past its peak here and declining.
    leaving: !resident && now >= 0.03 && ahead < now * 0.92,
    peakWeek: peak + 1,
    max,
  };
}

// Groups by English common name — rough but readable, and needs no extra data.
export const GROUPS = [
  ['owls', 'Owls & nightjars', /\b(owl|owlet|nightjar|frogmouth)\b/i],
  ['raptors', 'Birds of prey', /\b(eagle|hawk|kite|falcon|buzzard|harrier|vulture|kestrel|osprey|shikra|baza|sparrowhawk|goshawk|hobby|merlin|caracara)\b/i],
  ['water', 'Waterbirds & waders', /\b(duck|teal|goose|swan|heron|egret|bittern|stork|ibis|spoonbill|crane|cormorant|darter|grebe|pelican|flamingo|rail|crake|coot|moorhen|gallinule|swamphen|jacana|plover|sandpiper|lapwing|stilt|avocet|tern|gull|snipe|curlew|godwit|redshank|greenshank|stint|dunlin|turnstone|pratincole|courser|thick-knee|skimmer|loon|diver|merganser|pochard|wigeon|shoveler|pintail|garganey|gadwall|mallard|whistling-duck|shelduck|scaup|eider|scoter|petrel|shearwater|albatross|gannet|booby|frigatebird|tropicbird|auk|puffin|murre|guillemot|penguin|oystercatcher|phalarope|ruff|knot|sanderling|dowitcher|yellowlegs|killdeer|dotterel|whimbrel)\b/i],
  ['kingfishers', 'Kingfishers, bee-eaters & rollers', /\b(kingfisher|bee-eater|roller|hoopoe|hornbill|trogon|motmot|kookaburra)\b/i],
  ['woodpeckers', 'Woodpeckers & barbets', /\b(woodpecker|flameback|barbet|wryneck|piculet|sapsucker|flicker|toucan|honeyguide)\b/i],
  ['pigeons', 'Pigeons & doves', /\b(pigeon|dove)\b/i],
  ['parrots', 'Parrots', /\b(parrot|parakeet|lorikeet|lory|macaw|cockatoo|cockatiel|budgerigar|conure|lovebird|hanging-parrot)\b/i],
  ['cuckoos', 'Cuckoos & coucals', /\b(cuckoo|koel|coucal|malkoha|roadrunner|ani)\b/i],
  ['game', 'Fowl, quail & peafowl', /\b(peafowl|junglefowl|partridge|quail|francolin|pheasant|grouse|spurfowl|guineafowl|turkey|chachalaca|guan|curassow|bustard|button)\b/i],
  ['swifts', 'Swifts, swallows & martins', /\b(swift|swiftlet|swallow|martin|treeswift)\b/i],
];
export const groupOf = (common) => GROUPS.find(([, , re]) => re.test(common))?.[0] ?? 'songbirds';
export const GROUP_LABELS = Object.fromEntries([...GROUPS.map(([id, label]) => [id, label]), ['songbirds', 'Songbirds & others']]);

export const LEVELS = [
  [0, 'Hatchling'], [50, 'Fledgling'], [150, 'Birder'], [400, 'Keen birder'], [800, 'Twitcher'], [1500, 'Master birder'], [3000, 'Legend'],
];
export function levelOf(points) {
  let i = 0;
  while (i + 1 < LEVELS.length && points >= LEVELS[i + 1][0]) i++;
  const [from, name] = LEVELS[i];
  const next = LEVELS[i + 1];
  return { name, from, next: next?.[0] ?? null, nextName: next?.[1] ?? null, progress: next ? (points - from) / (next[0] - from) : 1 };
}

/** Deterministic daily pick so "Bird of the day" is stable for the whole day. */
export function dailyPick(list, seedText) {
  if (!list.length) return null;
  let h = 2166136261;
  for (const c of seedText) h = Math.imul(h ^ c.charCodeAt(0), 16777619);
  return list[(h >>> 0) % list.length];
}

export const IUCN = {
  lc: 'Least concern', nt: 'Near threatened', vu: 'Vulnerable', en: 'Endangered', cr: 'Critically endangered',
  ew: 'Extinct in the wild', dd: 'Data deficient',
};
export const iucnCode = (label) => ({
  'least concern': 'lc', 'near threatened': 'nt', vulnerable: 'vu', endangered: 'en',
  'critically endangered': 'cr', 'extinct in the wild': 'ew', 'data deficient': 'dd',
})[label?.toLowerCase()] ?? null;
