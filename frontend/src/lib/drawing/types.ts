/**
 * The drawing model.
 *
 * DEC-004 names the custom drawing layer as the largest schedule risk in the project, so this is
 * deliberately five tools and no more, followed by a decision gate on whether to keep building by
 * hand or adopt KLineChart.
 *
 * **Anchors are (time, priceTicks), never (x, y).** Pixels are a function of zoom, pan and window
 * size; a drawing stored in pixels would slide off the level it was drawn on the moment any of
 * those changed. DEC-024 adds the other half: anchor to *raw* prices, so a later contract roll
 * re-adjusting the series cannot silently shift every line the user ever drew.
 *
 * Prices are integer ticks throughout (INVARIANT 3), which also means snapping to the tick grid is
 * free — an anchor cannot represent an off-grid price.
 */

export type DrawingKind = 'horizontal-line' | 'trend-line' | 'rectangle' | 'position'

/** A point in market space: when, and at what price. */
export interface Anchor {
  /** UTC epoch seconds. */
  time: number
  /** Integer tick count (INVARIANT 3). */
  priceTicks: number
}

export interface DrawingStyle {
  color: string
  lineWidth: number
  /** Fill for rectangles and position zones. Transparent-ish; the chart must stay readable. */
  fill?: string
}

/**
 * One drawing.
 *
 * Anchor counts by kind:
 * - `horizontal-line` — 1 (only the price matters; it spans the whole chart)
 * - `trend-line` — 2 (both ends)
 * - `rectangle` — 2 (opposite corners)
 * - `position` — 3, in a fixed order: **entry, stop, target**
 *
 * The position tool is the one that earns its place twice: it is a risk/reward measurement here,
 * and it is the Practice tab's trade-entry UI later. Building it now means the Practice slice
 * inherits a control the user already knows how to use.
 */
export interface Drawing {
  id: string
  kind: DrawingKind
  anchors: Anchor[]
  style: DrawingStyle
}

export const ANCHOR_COUNT: Record<DrawingKind, number> = {
  'horizontal-line': 1,
  'trend-line': 2,
  rectangle: 2,
  position: 3,
}

export const DEFAULT_STYLES: Record<DrawingKind, DrawingStyle> = {
  'horizontal-line': { color: '#58a6ff', lineWidth: 1 },
  'trend-line': { color: '#58a6ff', lineWidth: 2 },
  rectangle: { color: '#d29922', lineWidth: 1, fill: 'rgba(210, 153, 34, 0.12)' },
  position: { color: '#8b949e', lineWidth: 1 },
}

/** Screen-space point. Only ever produced by projecting an anchor, never stored. */
export interface Point {
  x: number
  y: number
}

/**
 * Converts between market space and screen space.
 *
 * Supplied by the chart adapter so the drawing layer never imports the charting library — the
 * overlay works against this four-function interface and nothing else (DEC-004).
 */
export interface Projection {
  timeToX(time: number): number | null
  priceToY(priceTicks: number): number | null
  xToTime(x: number): number | null
  yToPrice(y: number): number | null
}

/** What the pointer is currently over. */
export type HitTarget =
  | { type: 'none' }
  | { type: 'body'; drawingId: string }
  | { type: 'handle'; drawingId: string; anchorIndex: number }
