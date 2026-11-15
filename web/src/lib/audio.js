import { SAMPLE_RATE, WINDOW } from './engine.js';

// Batches 128-sample render quanta into ~43 ms chunks so we post ~23 messages/s, not 375.
const WORKLET = `registerProcessor('capture', class extends AudioWorkletProcessor {
  constructor() { super(); this.buf = new Float32Array(2048); this.n = 0; }
  process(inputs) {
    const ch = inputs[0][0];
    if (ch) for (let i = 0; i < ch.length; i++) {
      this.buf[this.n++] = ch[i];
      if (this.n === this.buf.length) { this.port.postMessage(this.buf.slice()); this.n = 0; }
    }
    return true;
  }
});`;
const workletUrl = URL.createObjectURL(new Blob([WORKLET], { type: 'text/javascript' }));

/**
 * Opens the mic and calls onWindow(window, clip) every stepSec with the latest 3 s window and
 * the latest clipSec of audio (both 48 kHz, int16 scale). Returns { stop, analyser }.
 */
export async function startMic({ deviceId, stepSec, clipSec, onWindow }) {
  // Browser voice DSP (echo cancel, noise suppression, AGC) mangles birdsong — turn it off.
  const stream = await navigator.mediaDevices.getUserMedia({
    audio: {
      deviceId: deviceId ? { exact: deviceId } : undefined,
      echoCancellation: false, noiseSuppression: false, autoGainControl: false, channelCount: 1,
    },
  });
  let ctx, source;
  try {
    ctx = new AudioContext({ sampleRate: SAMPLE_RATE });
    source = ctx.createMediaStreamSource(stream);
  } catch {
    // Firefox refuses to connect a mic to a context at a different rate; resample ourselves.
    await ctx?.close();
    ctx = new AudioContext();
    source = ctx.createMediaStreamSource(stream);
  }
  await ctx.resume(); // may start suspended if the click's user activation was consumed by the permission prompt
  await ctx.audioWorklet.addModule(workletUrl);
  const node = new AudioWorkletNode(ctx, 'capture');
  const analyser = ctx.createAnalyser();
  analyser.fftSize = 2048;
  analyser.smoothingTimeConstant = 0;
  source.connect(analyser);
  source.connect(node);

  const clipLen = Math.max(WINDOW, Math.round(clipSec * SAMPLE_RATE));
  const step = Math.round(stepSec * SAMPLE_RATE);
  const ring = new Float32Array(clipLen);
  const ratio = ctx.sampleRate / SAMPLE_RATE;
  let filled = 0, sinceEmit = 0, pos = 0, prev = 0, stopped = false;

  const push = (s) => {
    ring[filled % clipLen] = s * 32768; // BirdNET was trained on int16-scale input
    filled++; sinceEmit++;
  };
  node.port.onmessage = ({ data }) => {
    if (stopped) return;
    if (ratio === 1) for (const s of data) push(s);
    else {
      // Streaming linear-interpolation resampler to 48 kHz.
      for (; pos < data.length; pos += ratio) {
        const i = Math.floor(pos), f = pos - i;
        push((i === 0 ? prev : data[i - 1]) * (1 - f) + data[i] * f);
      }
      pos -= data.length;
      prev = data[data.length - 1];
    }
    if (filled >= WINDOW && sinceEmit >= step) {
      sinceEmit = 0;
      onWindow(lastN(ring, filled, WINDOW), lastN(ring, filled, Math.min(filled, clipLen)));
    }
  };

  const track = stream.getAudioTracks()[0];
  return {
    analyser,
    deviceLabel: track?.label ?? 'Microphone',
    stop() {
      stopped = true;
      node.port.onmessage = null;
      stream.getTracks().forEach((t) => t.stop());
      ctx.close();
    },
  };
}

function lastN(ring, filled, n) {
  const out = new Float32Array(n);
  for (let i = 0, src = filled - n; i < n; i++, src++) out[i] = ring[src % ring.length];
  return out;
}

export async function listMics() {
  const devices = await navigator.mediaDevices.enumerateDevices();
  return devices.filter((d) => d.kind === 'audioinput' && d.deviceId !== 'default' && d.deviceId !== 'communications');
}

/** Decode any browser-supported audio file (mp3, aac/m4a, wav, ogg, opus, flac…) to 48 kHz mono, int16 scale. */
export async function decodeFile(file) {
  const buf = await new OfflineAudioContext(1, 1, SAMPLE_RATE).decodeAudioData(await file.arrayBuffer());
  const mono = new Float32Array(buf.length);
  for (let c = 0; c < buf.numberOfChannels; c++) {
    const ch = buf.getChannelData(c);
    for (let i = 0; i < ch.length; i++) mono[i] += (ch[i] * 32768) / buf.numberOfChannels;
  }
  return mono;
}

/** 16-bit PCM WAV from int16-scale floats. */
export function wavBlob(samples) {
  const view = new DataView(new ArrayBuffer(44 + samples.length * 2));
  const str = (o, s) => [...s].forEach((c, i) => view.setUint8(o + i, c.charCodeAt(0)));
  str(0, 'RIFF'); view.setUint32(4, 36 + samples.length * 2, true); str(8, 'WAVEfmt ');
  view.setUint32(16, 16, true); view.setUint16(20, 1, true); view.setUint16(22, 1, true);
  view.setUint32(24, SAMPLE_RATE, true); view.setUint32(28, SAMPLE_RATE * 2, true);
  view.setUint16(32, 2, true); view.setUint16(34, 16, true); str(36, 'data');
  view.setUint32(40, samples.length * 2, true);
  for (let i = 0; i < samples.length; i++) {
    view.setInt16(44 + i * 2, Math.max(-32768, Math.min(32767, samples[i])), true);
  }
  return new Blob([view], { type: 'audio/wav' });
}

// File types to offer in the picker. "audio/*" alone hides .aac on Windows (it has no
// registered audio MIME type there), so list extensions explicitly.
export const AUDIO_ACCEPT = 'audio/*,.aac,.m4a,.mp3,.wav,.ogg,.oga,.opus,.flac,.webm,.mp4,.3gp,.amr,.caf,.aif,.aiff';
