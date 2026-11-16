import emblem from '../assets/ic_launcher_emblem.png';
import { MODEL } from '../lib/engine.js';

const CREDITS = [
  ['BirdNET', 'Cornell Lab of Ornithology and Chemnitz University of Technology. CC BY-NC-SA 4.0, non-commercial.', 'https://birdnet.cornell.edu'],
  ['whoBIRD-TFlite', 'TensorFlow Lite conversion of BirdNET by woheller69.', 'https://github.com/woheller69/whoBIRD-TFlite'],
  ['LiteRT.js', 'Runs the model in the browser.', 'https://ai.google.dev/edge/litert/web'],
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
          <p>Bird call identification for your PC.</p>
          <small>Model: {MODEL} · 6,522 species and sounds</small>
        </div>
      </header>

      <div className="about-cols">
        <section className="card">
          <h2 className="eyebrow">Privacy</h2>
          <p>Audio is analysed in your browser and never uploaded. Your log and clips stay on this PC.</p>
          <p>Photos, maps and weather send a species name or your rough location to the services below. You can turn them off in Settings.</p>
        </section>
        <section className="card">
          <h2 className="eyebrow">Keyboard</h2>
          <dl className="keys">
            <dt><kbd>Space</kbd></dt><dd>Start / stop listening</dd>
            <dt><kbd>Esc</kbd></dt><dd>Close the bird panel</dd>
          </dl>
          <h2 className="eyebrow">Tips for better results</h2>
          <ul className="tips">
            <li>Use an external mic if you can. Laptop mics pick up fan noise.</li>
            <li>Early morning is best.</li>
            <li>Missing a bird? Lower the sensitivity in Settings.</li>
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
