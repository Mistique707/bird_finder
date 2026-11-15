import { useEffect, useRef, useState } from 'react';
import { Mic, Square, MapPin, Sunrise, Sunset, Thermometer, Wind, Loader2, Keyboard } from 'lucide-react';
import { colormap } from '../lib/colormap.js';
import { BirdPhoto, ConfBadge, RegionalName, fmtCoords, fmtSec, fmtTime } from '../components/common.jsx';
import landscape from '../assets/bg_landscape.jpg';
import swallow1 from '../assets/bird_swallow_1.png';
import swallow2 from '../assets/bird_swallow_2.png';

export default function Listen({ settings, engine, mic, live, sessionLog, analyserRef, toggleListening, openSpecies, location, weather, setPage }) {
  const stageRef = useRef(null);
  const canvasRef = useRef(null);
  const on = mic.state === 'on';
  const [, tick] = useState(0);

  // Elapsed timer.
  useEffect(() => {
    if (!on) return;
    const t = setInterval(() => tick((n) => n + 1), 1000);
    return () => clearInterval(t);
  }, [on]);

  // Level rings + spectrogram, straight from the AnalyserNode each frame (no React re-renders).
  useEffect(() => {
    if (!on) return;
    const analyser = analyserRef.current;
    const canvas = canvasRef.current;
    if (!analyser || !canvas) return;
    const g = canvas.getContext('2d');
    const time = new Float32Array(analyser.fftSize);
    const freq = new Uint8Array(analyser.frequencyBinCount);
    const maxBin = Math.floor((12000 / (analyser.context.sampleRate / 2)) * analyser.frequencyBinCount);
    const col = g.createImageData(1, canvas.height);
    const PX_PER_SEC = 110; // time-based so the scroll speed doesn't depend on the monitor's refresh rate
    let level = 0, raf, last = performance.now(), owed = 0;
    g.fillStyle = 'rgb(11,22,34)';
    g.fillRect(0, 0, canvas.width, canvas.height);

    const frame = (now) => {
      analyser.getFloatTimeDomainData(time);
      let sum = 0;
      for (const v of time) sum += v * v;
      const db = 20 * Math.log10(Math.sqrt(sum / time.length) + 1e-9); // ~-90..0 dBFS
      const target = Math.min(1, Math.max(0, (db + 70) / 55));
      level += (target - level) * (target > level ? 0.5 : 0.08); // fast attack, slow release
      stageRef.current?.style.setProperty('--level', level.toFixed(3));

      owed += ((now - last) / 1000) * PX_PER_SEC;
      last = now;
      const px = Math.min(canvas.width, Math.floor(owed));
      if (px > 0) {
        owed -= px;
        analyser.getByteFrequencyData(freq);
        for (let y = 0; y < canvas.height; y++) {
          const bin = Math.floor(((canvas.height - 1 - y) / canvas.height) * maxBin);
          const [r, gr, b] = colormap(Math.max(0, (freq[bin] - 45) / 170));
          const o = y * 4;
          col.data[o] = r; col.data[o + 1] = gr; col.data[o + 2] = b; col.data[o + 3] = 255;
        }
        g.drawImage(canvas, -px, 0);
        for (let x = canvas.width - px; x < canvas.width; x++) g.putImageData(col, x, 0);
      }
      raf = requestAnimationFrame(frame);
    };
    raf = requestAnimationFrame(frame);
    return () => {
      cancelAnimationFrame(raf);
      stageRef.current?.style.setProperty('--level', '0');
    };
  }, [on, analyserRef]);

  const strong = live.filter((d) => d.confidence >= settings.threshold);
  const possible = live.filter((d) => d.confidence < settings.threshold).slice(0, 3);
  const status = mic.state === 'starting' ? 'Starting microphone…'
    : on ? `Listening · ${fmtSec((Date.now() - mic.startedAt) / 1000)}`
    : engine.state !== 'ready' ? 'Loading BirdNET…' : 'Ready';

  return (
    <div className="listen">
      <div className={`stage ${on ? 'is-on' : ''}`} ref={stageRef} style={{ '--scene': `url(${landscape})` }}>
        <img className="swallow s1" src={swallow1} alt="" />
        <img className="swallow s2" src={swallow2} alt="" />
        <div className="stage-copy">
          <h1>Every bird has a voice.</h1>
          <p>Let’s discover who’s calling.</p>
        </div>

        <div className="mic-wrap">
          <span className="ring r-level" />
          <span className="ring r-pulse" />
          <span className="ring r-pulse delay" />
          <button
            className={`mic mic-${mic.state}`}
            onClick={toggleListening}
            disabled={engine.state !== 'ready'}
            aria-pressed={on}
            aria-label={on ? 'Stop listening' : 'Start listening'}
          >
            {mic.state === 'starting' ? <Loader2 className="spin" size={46} /> : on ? <Square size={40} fill="currentColor" /> : <Mic size={52} />}
          </button>
        </div>

        <div className="stage-status">
          <span className={`status-pill ${on ? 'live' : ''}`}>{on && <i className="rec-dot" />}{status}</span>
          {on && <span className="status-sub">{mic.device}</span>}
          {!on && mic.state === 'idle' && engine.state === 'ready' && <span className="status-sub"><Keyboard size={13} /> Press <kbd>Space</kbd> to start or stop</span>}
          {mic.error && <span className="status-error">{mic.error}</span>}
        </div>

        <div className={`spectro ${on ? 'show' : ''}`}>
          <canvas ref={canvasRef} width={900} height={120} aria-label="Live spectrogram" />
          <span className="spectro-axis"><b>12 kHz</b><b>6</b><b>0</b></span>
        </div>
      </div>

      <div className="listen-grid">
        <div className="card now">
          <h2 className="eyebrow">Now hearing</h2>
          {strong.length || possible.length ? (
            <ul className="live-list">
              {strong.map((d) => (
                <li key={d.scientific}>
                  <button onClick={() => openSpecies(d)}>
                    <BirdPhoto scientific={d.scientific} enabled={settings.showPhotos} className="sm" />
                    <span className="names">
                      <b>{d.common}</b>
                      <RegionalName scientific={d.scientific} common={d.common} lang={settings.regionalLang} />
                      <i>{d.scientific}</i>
                    </span>
                    <span className="meter"><span style={{ width: `${d.confidence * 100}%` }} /></span>
                    <ConfBadge value={d.confidence} />
                  </button>
                </li>
              ))}
              {possible.map((d) => (
                <li key={d.scientific} className="possible">
                  <button onClick={() => openSpecies(d)}>
                    <span className="names"><b>{d.common}?</b><i>{d.scientific}</i></span>
                    <span className="meter"><span style={{ width: `${d.confidence * 100}%` }} /></span>
                    <span className="conf conf-ghost">{Math.round(d.confidence * 100)}%</span>
                  </button>
                </li>
              ))}
            </ul>
          ) : (
            <p className="empty">{on ? 'Listening for calls… BirdNET checks every few seconds.' : 'Live matches will appear here while you listen.'}</p>
          )}
        </div>

        <div className="card logged">
          <div className="card-head">
            <h2 className="eyebrow">{sessionLog.length ? 'Logged this session' : 'How it works'}</h2>
            {sessionLog.length > 0 && <button className="link-btn" onClick={() => setPage('log')}>Open bird log →</button>}
          </div>
          {sessionLog.length ? (
            <ul className="logged-list">
              {sessionLog.map((r) => (
                <li key={r.id}>
                  <button onClick={() => openSpecies(r, r)}>
                    <BirdPhoto scientific={r.scientific} enabled={settings.showPhotos} />
                    <span className="names">
                      <b>{r.common}</b>
                      <RegionalName scientific={r.scientific} common={r.common} lang={settings.regionalLang} />
                      <small>{fmtTime(r.time)}{r.weather ? ` · ${Math.round(r.weather.tempC)}°C` : ''}</small>
                    </span>
                    <ConfBadge value={r.confidence} />
                  </button>
                </li>
              ))}
            </ul>
          ) : (
            <ol className="steps">
              <li><b>Listen to nature</b><span>Point your mic toward the sounds around you — a window works well.</span></li>
              <li><b>Find the bird</b><span>BirdNET identifies likely species from each 3-second slice of sound.</span></li>
              <li><b>Build your bird log</b><span>Every discovery is saved with its recording, time, place and weather.</span></li>
            </ol>
          )}
        </div>

        <aside className="card conditions">
          <h2 className="eyebrow">Conditions</h2>
          {location && (
            <div className="cond-row">
              <MapPin size={18} />
              <span>
                <b>{location.name ?? 'Locating…'}</b>
                <small>{fmtCoords(location.lat, location.lon)}{location.accuracy ? ` · ±${Math.round(location.accuracy)} m` : ''}
                  {location.source === 'fallback' && ' · default location'}</small>
              </span>
            </div>
          )}
          {weather && (
            <>
              <div className="cond-row">
                <Thermometer size={18} />
                <span><b>{Math.round(weather.tempC)}°C · {weather.text}</b><small>Humidity {weather.humidity}%</small></span>
              </div>
              <div className="cond-row">
                <Wind size={18} />
                <span><b>{Math.round(weather.wind)} km/h wind</b><small>{weather.wind > 25 ? 'Windy — expect noisier recordings' : 'Good listening conditions'}</small></span>
              </div>
              <div className="cond-row split">
                <span><Sunrise size={18} /><b>{weather.sunrise.slice(11)}</b></span>
                <span><Sunset size={18} /><b>{weather.sunset.slice(11)}</b></span>
              </div>
              <p className="fine">Birds sing most in the hour around sunrise — the dawn chorus.</p>
            </>
          )}
          <div className="cond-settings">
            <span>Threshold <b>{Math.round(settings.threshold * 100)}%</b></span>
            <span>Location filter <b>{settings.useLocation ? (settings.locationMode === 'filter' ? 'Filter' : 'Weighted') : 'Off'}</b></span>
            <button className="link-btn" onClick={() => setPage('settings')}>Tune →</button>
          </div>
        </aside>
      </div>
    </div>
  );
}
