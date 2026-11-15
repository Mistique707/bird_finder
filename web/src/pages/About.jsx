import emblem from '../assets/ic_launcher_emblem.png';
import { MODEL } from '../lib/engine.js';

const CREDITS = [
  ['BirdNET', 'Bird sound identification model © Cornell Lab of Ornithology & Chemnitz University of Technology. CC BY-NC-SA 4.0 — non-commercial use only.', 'https://birdnet.cornell.edu'],
  ['whoBIRD-TFlite', 'TensorFlow Lite conversion of BirdNET by woheller69.', 'https://github.com/woheller69/whoBIRD-TFlite'],
  ['LiteRT.js', 'Google’s on-device runtime, running the model in WebAssembly.', 'https://ai.google.dev/edge/litert/web'],
  ['Wikipedia & Wikimedia Commons', 'Photos, descriptions and reference recordings. CC BY-SA and per-file licences.', 'https://www.wikipedia.org'],
  ['Wikidata', 'IUCN conservation status and recording links. CC0.', 'https://www.wikidata.org'],
  ['GBIF', 'Taxonomy, occurrence counts and range maps.', 'https://www.gbif.org'],
  ['Open-Meteo', 'Weather, sunrise and place search. CC BY 4.0.', 'https://open-meteo.com'],
  ['BigDataCloud', 'Place names for coordinates.', 'https://www.bigdatacloud.com'],
  ['Xeno-canto', 'Optional reference calls (with your own key).', 'https://xeno-canto.org'],
];

export default function About() {
  return (
    <div className="about">
      <header className="about-hero card">
        <img src={emblem} alt="" />
        <div>
          <h1>Bird Finder</h1>
          <p>Identify birds by their calls, right on your PC. Desktop companion to the Bird Finder Android app.</p>
          <small>Model: {MODEL} · 6,522 species and sounds</small>
        </div>
      </header>

      <div className="about-cols">
        <section className="card">
          <h2 className="eyebrow">Privacy</h2>
          <p>Audio is analysed entirely in your browser and never uploaded. Your bird log and clips are stored only on this PC.</p>
          <p>To show photos, facts, maps and weather, the app sends a species’ scientific name or your approximate coordinates to the services listed under credits. Turn photos, reference calls and weather off in Settings to stay fully offline after the model has loaded.</p>
        </section>
        <section className="card">
          <h2 className="eyebrow">Keyboard</h2>
          <dl className="keys">
            <dt><kbd>Space</kbd></dt><dd>Start / stop listening</dd>
            <dt><kbd>Esc</kbd></dt><dd>Close the bird panel</dd>
          </dl>
          <h2 className="eyebrow">Tips for better results</h2>
          <ul className="tips">
            <li>Use a USB or external mic and point it toward the birds — laptop mics pick up fan noise.</li>
            <li>Early morning is best: the dawn chorus peaks around sunrise.</li>
            <li>If a bird you can clearly hear isn’t detected, lower the sensitivity threshold or switch the location filter to “Hard filter”.</li>
          </ul>
        </section>
      </div>

      <section className="card">
        <h2 className="eyebrow">Credits & licences</h2>
        <ul className="credits">
          {CREDITS.map(([name, text, url]) => (
            <li key={name}><a href={url} target="_blank" rel="noreferrer">{name}</a><span>{text}</span></li>
          ))}
        </ul>
      </section>
    </div>
  );
}
