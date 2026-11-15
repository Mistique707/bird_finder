import { useEffect, useState } from 'react';
import wordmark from '../assets/logo_wordmark.png';
import swallow from '../assets/bird_swallow_1.png';

const FACTS = [
  'A group of crows is called a “murder.”',
  'Hummingbirds are the only birds that can fly backwards.',
  'Owls can rotate their heads about 270 degrees.',
  'The Arctic Tern migrates around 70,000 km round-trip each year.',
  'Crows can recognise and remember individual human faces.',
  'The bee hummingbird is the smallest bird — under 2 grams.',
  'Flamingos get their pink colour from the food they eat.',
  'A woodpecker can peck around 20 times per second.',
  'The peregrine falcon can dive at over 300 km/h.',
  'Some birds sleep with one eye open, resting half the brain.',
  'Kingfishers inspired the nose shape of Japan’s bullet train.',
  'The Indian peafowl is the national bird of India.',
  'Mockingbirds can imitate dozens of other birds’ songs.',
  'Common swifts can stay airborne for months without landing.',
  'The Asian Koel’s rising “ku-oo” is one of India’s loudest summer sounds.',
  'Songbirds learn their songs much like babies learn to talk — by listening and practising.',
  'The dawn chorus peaks in the hour around sunrise, when sound carries furthest.',
  'Lyrebirds can mimic chainsaws and camera shutters.',
];

const MIN_MS = 2600;

export default function Splash({ engine, onDone }) {
  const [fact] = useState(() => FACTS[Math.floor(Math.random() * FACTS.length)]);
  const [minElapsed, setMinElapsed] = useState(false);
  const [leaving, setLeaving] = useState(false);

  useEffect(() => {
    const t = setTimeout(() => setMinElapsed(true), MIN_MS);
    return () => clearTimeout(t);
  }, []);

  const leave = () => {
    if (leaving) return;
    setLeaving(true);
    setTimeout(onDone, 450);
  };
  useEffect(() => {
    if (minElapsed && engine.state === 'ready') leave();
  }, [minElapsed, engine.state]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div className={`splash ${leaving ? 'leaving' : ''}`} onClick={leave} role="dialog" aria-label="Bird Finder is starting">
      <img className="splash-swallow" src={swallow} alt="" />
      <div className="splash-inner">
        <div className="splash-logo"><img src={wordmark} alt="Bird Finder" /></div>
        <p className="splash-tag">On-device bird call identification</p>
        <div className="splash-fact">
          <span>Did you know?</span>
          <p>{fact}</p>
        </div>
        <p className="splash-hint">
          {engine.state === 'error' ? `Couldn’t load the model: ${engine.error}` : engine.state === 'ready' ? 'Click to continue' : 'Warming up BirdNET…'}
        </p>
      </div>
      <div className={`splash-bar ${engine.state}`}><i /></div>
    </div>
  );
}
