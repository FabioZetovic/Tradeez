import type { DomainBar } from './types'
import type { Projection } from '../drawing/types'

/**
 * Where a time falls on screen.
 *
 * The chart can place bar times only. Any other time is placed by proportion inside the bar that
 * contains it — 08:30 sits in the middle of an hourly 08:00 candle. A time inside a closed market
 * sits at the edge of the last bar before it, because the chart draws gaps with no width; a time
 * past the newest bar sits at that bar's right edge, which is where a running session ends for now.
 * Null when the chart cannot place the bars at all.
 */
export function timeToX(
  time: number,
  bars: readonly DomainBar[],
  step: number,
  projection: Projection,
): number | null {
  if (bars.length === 0) return null
  // The last bar opening at or before the time.
  let lo = 0
  let hi = bars.length - 1
  let index = -1
  while (lo <= hi) {
    const mid = (lo + hi) >> 1
    if (bars[mid].time <= time) {
      index = mid
      lo = mid + 1
    } else {
      hi = mid - 1
    }
  }
  const at = Math.max(index, 0)
  const x = projection.timeToX(bars[at].time)
  if (x === null) return null
  const spacing = barSpacing(at, bars, projection)
  if (index === -1) return x - spacing / 2
  const fraction = Math.min(time - bars[at].time, step) / step
  return x - spacing / 2 + fraction * spacing
}

function barSpacing(index: number, bars: readonly DomainBar[], projection: Projection): number {
  const neighbour = index + 1 < bars.length ? index + 1 : index - 1
  if (neighbour < 0) return 6
  const a = projection.timeToX(bars[index].time)
  const b = projection.timeToX(bars[neighbour].time)
  return a === null || b === null ? 6 : Math.abs(b - a)
}
