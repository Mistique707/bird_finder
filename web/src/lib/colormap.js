/** Night-sky → sky-blue → white ramp, matching the app palette. Used by both spectrograms. */
export function colormap(t) {
  t = Math.min(1, Math.max(0, t));
  const stops = [[11, 22, 34], [28, 67, 105], [63, 134, 214], [143, 190, 241], [255, 255, 255]];
  const s = t * (stops.length - 1), i = Math.min(stops.length - 2, Math.floor(s)), f = s - i;
  return stops[i].map((c, k) => Math.round(c + (stops[i + 1][k] - c) * f));
}
