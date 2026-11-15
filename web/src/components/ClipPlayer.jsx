import { useEffect, useMemo, useRef, useState } from 'react';
import { Pause, Play } from 'lucide-react';
import { fmtSec } from './common.jsx';

const BARS = 120;

/** Play/pause + clickable waveform. `highlight` = [startSec, endSec] of the detected call. */
export default function ClipPlayer({ src, highlight, label }) {
  const audioRef = useRef(null);
  const [peaks, setPeaks] = useState(null);
  const [duration, setDuration] = useState(0);
  const [pos, setPos] = useState(0);
  const [playing, setPlaying] = useState(false);
  const url = useMemo(() => (src instanceof Blob ? URL.createObjectURL(src) : src), [src]);
  useEffect(() => () => { if (src instanceof Blob) URL.revokeObjectURL(url); }, [src, url]);

  useEffect(() => {
    let alive = true;
    (async () => {
      const data = src instanceof Blob ? await src.arrayBuffer() : await fetch(url).then((r) => r.arrayBuffer());
      const buf = await new OfflineAudioContext(1, 1, 48000).decodeAudioData(data);
      const ch = buf.getChannelData(0), per = Math.floor(ch.length / BARS);
      const p = Array.from({ length: BARS }, (_, b) => {
        let m = 0;
        for (let i = b * per; i < (b + 1) * per; i++) m = Math.max(m, Math.abs(ch[i]));
        return m;
      });
      const max = Math.max(...p, 1e-6);
      if (alive) { setPeaks(p.map((v) => v / max)); setDuration(buf.duration); }
    })().catch(() => alive && setPeaks([]));
    return () => { alive = false; };
  }, [src, url]);

  useEffect(() => {
    if (!playing) return;
    let raf;
    const tick = () => { setPos(audioRef.current?.currentTime ?? 0); raf = requestAnimationFrame(tick); };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [playing]);

  const toggle = () => {
    const a = audioRef.current;
    if (!a) return;
    if (a.paused) {
      document.querySelectorAll('audio').forEach((el) => el !== a && el.pause());
      if (a.ended) a.currentTime = 0;
      a.play();
    } else a.pause();
  };
  const seek = (e) => {
    const r = e.currentTarget.getBoundingClientRect();
    const t = ((e.clientX - r.left) / r.width) * (duration || 0);
    audioRef.current.currentTime = t;
    setPos(t);
  };

  const frac = duration ? pos / duration : 0;
  const [hs, he] = highlight ?? [];
  return (
    <div className="clip-player">
      <button className="play-btn" onClick={toggle} aria-label={playing ? 'Pause' : `Play ${label ?? 'recording'}`}>
        {playing ? <Pause size={18} fill="currentColor" /> : <Play size={18} fill="currentColor" />}
      </button>
      <div className="wave" onClick={seek} role="slider" aria-label="Seek" aria-valuemin={0} aria-valuemax={Math.round(duration)} aria-valuenow={Math.round(pos)} tabIndex={0}
        onKeyDown={(e) => { if (e.key === 'ArrowRight') audioRef.current.currentTime += 1; if (e.key === 'ArrowLeft') audioRef.current.currentTime -= 1; }}>
        {highlight && duration > 0 && (
          <span className="wave-hl" style={{ left: `${(hs / duration) * 100}%`, width: `${((he - hs) / duration) * 100}%` }} />
        )}
        {(peaks?.length ? peaks : Array(BARS).fill(0.08)).map((v, i) => (
          <i key={i} className={i / BARS < frac ? 'played' : ''} style={{ height: `${Math.max(6, v * 100)}%` }} />
        ))}
      </div>
      <span className="clip-time">{fmtSec(pos)} / {fmtSec(duration)}</span>
      <audio ref={audioRef} src={url} preload="metadata" onPlay={() => setPlaying(true)} onPause={() => setPlaying(false)}
        onLoadedMetadata={(e) => Number.isFinite(e.currentTarget.duration) && setDuration((d) => d || e.currentTarget.duration)}
        onEnded={() => { setPlaying(false); setPos(duration); }} />
    </div>
  );
}
