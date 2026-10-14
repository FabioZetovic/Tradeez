import { decimalsFor, formatPrice } from '../chart/priceFormat'
import type { Anchor } from './types'

/**
 * The arithmetic behind the ICT drawing tools (T8, DEC-048), kept out of the renderer so it can be
 * checked number by number.
 */

/**
 * ICT's Fibonacci levels (ICT-43): retracements 0, 0.5 (equilibrium), 0.62, 0.705, 0.79 and 1, and
 * the extensions he uses as targets, −0.27, −0.62, −1 and −2. The 0.62–0.79 band is the optimal trade
 * entry (ICT-42), 0.705 its sweet spot.
 */
export const ICT_FIB_LEVELS = [
  { ratio: 1, label: '1' },
  { ratio: 0.79, label: '0.79' },
  { ratio: 0.705, label: '0.705' },
  { ratio: 0.62, label: '0.62' },
  { ratio: 0.5, label: '0.5' },
  { ratio: 0, label: '0' },
  { ratio: -0.27, label: '-0.27' },
  { ratio: -0.62, label: '-0.62' },
  { ratio: -1, label: '-1' },
  { ratio: -2, label: '-2' },
] as const

export const OTE = { from: 0.62, to: 0.79, sweetSpot: 0.705 } as const

/**
 * Where a level sits, in ticks — fractional, because 0.705 of a leg rarely lands on the grid.
 *
 * Level 1 is the leg's start and 0 its end, so drawing a bullish leg from its low to its high puts the
 * OTE below the high and the targets above it — the way ICT draws it, and the way TradingView does.
 */
export function fibLevelTicks([start, end]: Anchor[], ratio: number): number {
  return end.priceTicks + ratio * (start.priceTicks - end.priceTicks)
}

/**
 * A level's price as a trader can use it: on the tick grid, the nearest tick. An order cannot be
 * placed at 0.705 of a tick.
 */
export function formatFibLevel(ticks: number, tickSize: number): string {
  return formatPrice(Math.round(ticks), tickSize)
}

/**
 * The consequent encroachment of a zone — its exact middle (ICT-21) — in half-ticks, so that a
 * zone an odd number of ticks tall keeps an exact, integer midpoint (INVARIANT 3).
 */
export function ceHalfTicks([a, b]: Anchor[]): number {
  return a.priceTicks + b.priceTicks
}

/** A price in half-ticks, with one more decimal when it falls between two ticks: NQ's 0.125. */
export function formatHalfTicks(halfTicks: number, tickSize: number): string {
  if (halfTicks % 2 === 0) return formatPrice(halfTicks / 2, tickSize)
  return ((halfTicks * tickSize) / 2).toFixed(decimalsFor(tickSize) + 1)
}

/** The size labels are drawn at, and an estimate of their width for hit testing (no canvas there). */
export const LABEL_FONT_PX = 12

export function labelWidth(text: string): number {
  return Math.max(text.length, 1) * LABEL_FONT_PX * 0.6 + 12
}
