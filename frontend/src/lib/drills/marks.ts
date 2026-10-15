import type { DomainBar } from '../chart/types'
import type { Projection } from '../drawing/types'
import { nearestBar } from '../drawing/snap'

/**
 * Identification drill helpers: turning a click into a candle, and a candle into a mark.
 *
 * Why each hit qualified is explained by the server, from the evidence stored with it (DEC-054).
 */

/**
 * The candle under a click, or null if the click was not on one.
 *
 * Snaps to the nearest candle, but only within half the gap between candles: a click in the empty
 * space right of the last candle is not a mark on it.
 */
export function pickCandle(x: number, projection: Projection, bars: DomainBar[]): number | null {
  const time = projection.xToTime(x)
  if (time === null) return null
  const bar = nearestBar(time, bars)
  if (!bar) return null

  const barX = projection.timeToX(bar.time)
  if (barX === null) return null
  const spacing = candleSpacing(projection, bars)
  return Math.abs(barX - x) <= Math.max(4, spacing / 2) ? bar.time : null
}

function candleSpacing(projection: Projection, bars: DomainBar[]): number {
  if (bars.length < 2) return 8
  const a = projection.timeToX(bars[0].time)
  const b = projection.timeToX(bars[1].time)
  return a === null || b === null ? 8 : Math.abs(b - a)
}

/** Adds the candle if it is not marked, removes it if it is. Kept sorted. */
export function toggleMark(marks: number[], time: number): number[] {
  return marks.includes(time)
    ? marks.filter((m) => m !== time)
    : [...marks, time].sort((a, b) => a - b)
}
