/**
 * Between a chart's logical positions — candle indexes, fractional, running past either end into the empty space
 * a chart can show — and times (DEC-075). Two charts of different markets have different candles, so they are kept
 * on the same minutes by time, and each turns that time into its own positions.
 */

/** The usual step between candles: the smallest gap among the first ones. */
function step(times: number[]): number {
  let smallest = Infinity
  for (let i = 1; i < Math.min(times.length, 50); i++) smallest = Math.min(smallest, times[i] - times[i - 1])
  return Number.isFinite(smallest) && smallest > 0 ? smallest : 60
}

/** The time at a logical position: between candles in proportion, a step a candle beyond either end. */
export function timeAtLogical(times: number[], logical: number): number {
  const last = times.length - 1
  if (logical <= 0) return times[0] + logical * step(times)
  if (logical >= last) return times[last] + (logical - last) * step(times)
  const i = Math.floor(logical)
  return times[i] + (logical - i) * (times[i + 1] - times[i])
}

/** The logical position of a time: the inverse of {@link timeAtLogical}. */
export function logicalAt(times: number[], time: number): number {
  const last = times.length - 1
  if (time <= times[0]) return (time - times[0]) / step(times)
  if (time >= times[last]) return last + (time - times[last]) / step(times)
  let lo = 0
  let hi = last
  while (hi - lo > 1) {
    const mid = (lo + hi) >> 1
    if (times[mid] <= time) lo = mid
    else hi = mid
  }
  return lo + (time - times[lo]) / (times[lo + 1] - times[lo])
}
