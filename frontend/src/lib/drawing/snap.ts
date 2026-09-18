import type { DomainBar } from '../chart/types'
import type { Anchor } from './types'

/**
 * Magnet snapping.
 *
 * A trader marking a level means *that* high, not a price four ticks under it. Without a magnet,
 * every line is drawn slightly off the thing it refers to — which matters more here than on an
 * ordinary chart, because the Practice tab will later score stop placement against detected
 * structure. A stop "at the swing low" that is actually three ticks inside it is a different trade.
 *
 * Snapping to the tick grid needs no code at all: anchors hold integer ticks, so an off-grid price
 * is not representable (INVARIANT 3). This module is only about the stronger magnet — attaching to
 * a bar's open, high, low or close.
 */

/** How close, in ticks, a price must be to a bar level before the magnet takes it. */
export const MAGNET_TOLERANCE_TICKS = 6

export type SnapMode = 'off' | 'magnet'

export interface SnapResult {
  anchor: Anchor
  /** Which level it attached to, for showing the user why the price jumped. */
  snappedTo: 'open' | 'high' | 'low' | 'close' | null
}

/**
 * Snaps an anchor to the nearest OHLC level of the bar it sits on.
 *
 * Only the bar under the cursor is considered. Searching neighbours would let a line jump to a
 * level the user is not pointing at, which feels like the tool fighting them.
 */
export function snapAnchor(anchor: Anchor, bars: DomainBar[], mode: SnapMode): SnapResult {
  if (mode === 'off' || bars.length === 0) {
    return { anchor, snappedTo: null }
  }

  const bar = nearestBar(anchor.time, bars)
  if (!bar) return { anchor, snappedTo: null }

  const levels = [
    { name: 'open' as const, ticks: bar.open },
    { name: 'high' as const, ticks: bar.high },
    { name: 'low' as const, ticks: bar.low },
    { name: 'close' as const, ticks: bar.close },
  ]

  let best = levels[0]
  let bestDistance = Math.abs(anchor.priceTicks - best.ticks)
  for (const level of levels.slice(1)) {
    const d = Math.abs(anchor.priceTicks - level.ticks)
    if (d < bestDistance) {
      best = level
      bestDistance = d
    }
  }

  if (bestDistance > MAGNET_TOLERANCE_TICKS) {
    // Close to nothing in particular — leave the price where the user put it. A magnet that always
    // grabs is worse than none, because it makes precise placement impossible.
    return { anchor, snappedTo: null }
  }

  return {
    anchor: { time: bar.time, priceTicks: best.ticks },
    snappedTo: best.name,
  }
}

/**
 * The bar closest in time to a given moment.
 *
 * Binary search: a chart can hold twenty thousand bars and this runs on every mouse move during a
 * drag, so a linear scan would make the magnet feel sluggish exactly when precision matters.
 */
export function nearestBar(time: number, bars: DomainBar[]): DomainBar | null {
  if (bars.length === 0) return null

  let low = 0
  let high = bars.length - 1

  while (low < high) {
    const mid = (low + high) >> 1
    if (bars[mid].time < time) low = mid + 1
    else high = mid
  }

  const candidate = bars[low]
  const previous = bars[low - 1]
  if (!previous) return candidate

  return Math.abs(previous.time - time) <= Math.abs(candidate.time - time) ? previous : candidate
}
