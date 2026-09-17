/**
 * Conversion between integer ticks and displayable prices.
 *
 * INVARIANT 3 — the domain stores prices as integer tick counts because binary floating point
 * cannot represent most decimal prices exactly, and a P&L engine that accumulates that error
 * produces numbers a trader cannot reconcile by hand. This module is the boundary where ticks
 * become decimals, and it is the only place that conversion is allowed to happen.
 */

/** Decimal places implied by a tick size. 0.25 -> 2, 0.01 -> 2, 1 -> 0. */
export function decimalsFor(tickSize: number): number {
  if (!Number.isFinite(tickSize) || tickSize <= 0) {
    throw new Error(`Invalid tick size: ${tickSize}`)
  }
  const text = tickSize.toString()
  if (text.includes('e-')) {
    // Very small tick sizes arrive in exponential form; the exponent is the decimal count.
    return Number(text.split('e-')[1])
  }
  const fraction = text.split('.')[1]
  return fraction ? fraction.length : 0
}

/**
 * Renders a tick count as a price.
 *
 * The multiply-then-round is not superstition. `80001 * 0.25` happens to be exact because 0.25 is a
 * power of two, but a tick size like 0.01 is not representable in binary, and the naive product
 * drifts — producing prices such as `20000.249999999996`. Rounding to the tick size's own precision
 * keeps the displayed value on the grid it belongs to.
 */
export function ticksToPrice(ticks: number, tickSize: number): number {
  const decimals = decimalsFor(tickSize)
  const scale = 10 ** decimals
  return Math.round(ticks * tickSize * scale) / scale
}

/** Inverse of {@link ticksToPrice}. Used when a user types or drags a price. */
export function priceToTicks(price: number, tickSize: number): number {
  return Math.round(price / tickSize)
}

/** Formats a tick count for display, with the instrument's natural precision. */
export function formatPrice(ticks: number, tickSize: number): string {
  return ticksToPrice(ticks, tickSize).toFixed(decimalsFor(tickSize))
}
