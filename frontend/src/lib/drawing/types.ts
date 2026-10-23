import { THEME } from '../theme'
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

export type DrawingKind =
  | 'horizontal-line'
  | 'trend-line'
  | 'rectangle'
  | 'position'
  // DEC-048 (T8): ICT's own tools.
  | 'fibonacci'
  | 'zone'
  | 'label'

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
 * - `fibonacci` — 2: the leg's **start, end** (level 1 at the start, 0 at the end — ICT-43)
 * - `zone` — 2 (opposite corners), with its consequent encroachment at 50% (ICT-21)
 * - `label` — 1, with its words in `text`
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
  /**
   * Position only: where the zone stops, UTC epoch seconds. Absent, it runs to the chart's right
   * edge — a plan still open. Present, it is a practice trade that has ended, drawn entry to exit.
   */
  endTime?: number
  /** A label's words, or a zone's name. At most {@link MAX_TEXT} characters. */
  text?: string
  /** Position only (T28): where part of a practice trade comes off, drawn dashed between entry and target. */
  partialTicks?: number
}

export const MAX_TEXT = 80

export const ANCHOR_COUNT: Record<DrawingKind, number> = {
  'horizontal-line': 1,
  'trend-line': 2,
  rectangle: 2,
  position: 3,
  fibonacci: 2,
  zone: 2,
  label: 1,
}

export const DEFAULT_STYLES: Record<DrawingKind, DrawingStyle> = {
  'horizontal-line': { color: THEME.accent, lineWidth: 1 },
  'trend-line': { color: THEME.accent, lineWidth: 2 },
  rectangle: { color: THEME.warn, lineWidth: 1, fill: 'rgba(210, 153, 34, 0.12)' },
  position: { color: THEME.subtle, lineWidth: 1 },
  fibonacci: { color: THEME.subtle, lineWidth: 1 },
  zone: { color: '#39c5cf', lineWidth: 1, fill: 'rgba(57, 197, 207, 0.12)' },
  label: { color: THEME.ink, lineWidth: 1 },
}

/** Kinds whose words can be edited by double-clicking them. */
export const TEXT_KINDS: ReadonlySet<DrawingKind> = new Set(['label', 'zone'])

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
