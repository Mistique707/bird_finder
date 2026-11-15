// Quick self-check for the rarity/season/group heuristics: `node scripts/check-explore.mjs`
import assert from 'node:assert/strict';
import { tierOf, seasonOf, groupOf, levelOf, dailyPick } from '../src/lib/explore.js';

assert.equal(tierOf(0.9).id, 'common');
assert.equal(tierOf(0.2).id, 'uncommon');
assert.equal(tierOf(0.05).id, 'rare');
assert.equal(tierOf(0.01).id, 'scarce');
assert.equal(tierOf(0.001).id, 'unlikely');

const flat = Array(48).fill(0.8);
assert.equal(seasonOf(flat, 10).resident, true);

// Summer visitor present weeks 14–30: arriving just before, leaving just after.
const summer = Array.from({ length: 48 }, (_, i) => (i >= 13 && i <= 29 ? 0.7 : 0.01));
assert.equal(seasonOf(summer, 10).resident, false);
assert.equal(seasonOf(summer, 11).arriving, true);
assert.equal(seasonOf(summer, 28).leaving, true);
assert.equal(seasonOf(summer, 20).arriving, false);
// Wraps around the year end (winter visitor).
const winter = Array.from({ length: 48 }, (_, i) => (i >= 44 || i <= 8 ? 0.6 : 0.01));
assert.equal(seasonOf(winter, 42).arriving, true);

assert.equal(groupOf('Spotted Owlet'), 'owls');
assert.equal(groupOf('Black Kite'), 'raptors');
assert.equal(groupOf('Little Egret'), 'water');
assert.equal(groupOf('Asian Koel'), 'cuckoos');
assert.equal(groupOf('Indian Robin'), 'songbirds');
assert.equal(groupOf('Coppersmith Barbet'), 'woodpeckers');

assert.equal(levelOf(0).name, 'Hatchling');
assert.equal(levelOf(160).name, 'Birder');
assert.equal(levelOf(99999).next, null);
assert.equal(dailyPick([1, 2, 3], 'x'), dailyPick([1, 2, 3], 'x'));
console.log('explore checks passed');
