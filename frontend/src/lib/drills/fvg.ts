import type { SetupView } from '../api/drills'
import type { DomainBar } from '../chart/types'
import type { Projection } from '../drawing/types'
import { nearestBar } from '../drawing/snap'
import { formatPrice } from '../chart/priceFormat'

/**
 * FVG drill helpers: turning a click into a candle, and a detector hit into words.
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

/**
 * Why a gap qualified, in the detector's own numbers (DEC-026).
 *
 * Generated rather than written: the sentence is assembled from the evidence stored with the hit,
 * so it names the exact prices of the exact candles, and it can never drift from the rule the way
 * hand-written lesson text would. It explains what the rule saw — not whether the gap matters.
 */
export function explainFvg(
  setup: SetupView,
  tickSize: number,
  minGapPercent: number,
): string[] {
  const e = setup.evidence
  const price = (ticks: number) => formatPrice(ticks, tickSize)
  const points = formatPrice(e.gapTicks, tickSize)
  const average = (e.sumRangeTicks / e.lookback).toFixed(1)

  // Worded to say exactly what the rule checks and no more. An earlier draft said the gap "was
  // crossed only by candle 2", which the rule does not establish: on real data candle 2 need not
  // span the whole gap. The explanation must not claim more than the evidence (DEC-026).
  const geometry =
    setup.direction === 'BULLISH'
      ? `Candle 1's high, ${price(e.candle1HighTicks)}, is below candle 3's low, ` +
        `${price(e.candle3LowTicks)}. Neither traded in the ${e.gapTicks} ticks ` +
        `(${points} points) between them: a bullish gap.`
      : `Candle 1's low, ${price(e.candle1LowTicks)}, is above candle 3's high, ` +
        `${price(e.candle3HighTicks)}. Neither traded in the ${e.gapTicks} ticks ` +
        `(${points} points) between them: a bearish gap.`

  const size =
    `Big enough to count: the rule needs at least ${e.thresholdTicks} ticks here — ${minGapPercent}% of ` +
    `the ${average}-tick average range of the ${e.lookback} candles before candle 1.`

  return [geometry, size]
}
