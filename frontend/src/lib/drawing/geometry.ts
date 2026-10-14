import type { Anchor, Drawing, HitTarget, Point, Projection } from './types'
import { ceHalfTicks, fibLevelTicks, ICT_FIB_LEVELS, LABEL_FONT_PX, labelWidth } from './ict'

/**
 * Hit testing and measurement.
 *
 * Pure functions over plain numbers: no canvas, no React, no chart library. That is what makes the
 * hardest part of the drawing layer — deciding what the user is pointing at — testable without any
 * of the machinery around it.
 */

/** How close the pointer must be, in pixels, to count as touching something. */
export const HIT_TOLERANCE = 6

/** Handle squares drawn at each anchor when a drawing is selected. */
export const HANDLE_SIZE = 7
const HANDLE_HIT_TOLERANCE = 8

function distance(a: Point, b: Point): number {
  return Math.hypot(a.x - b.x, a.y - b.y)
}

/**
 * Shortest distance from a point to a line *segment*.
 *
 * The segment part matters: with an infinite line, clicking far past the end of a short trend line
 * would still select it, which feels broken on a busy chart.
 */
export function distanceToSegment(p: Point, a: Point, b: Point): number {
  const dx = b.x - a.x
  const dy = b.y - a.y
  const lengthSquared = dx * dx + dy * dy

  if (lengthSquared === 0) return distance(p, a)

  // Project p onto the line, then clamp to [0,1] to stay within the segment.
  let t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSquared
  t = Math.max(0, Math.min(1, t))

  return distance(p, { x: a.x + t * dx, y: a.y + t * dy })
}

/** Distance to a rectangle's outline, given opposite corners. Zero when on an edge. */
export function distanceToRectOutline(p: Point, a: Point, b: Point): number {
  const left = Math.min(a.x, b.x)
  const right = Math.max(a.x, b.x)
  const top = Math.min(a.y, b.y)
  const bottom = Math.max(a.y, b.y)

  const corners = {
    tl: { x: left, y: top },
    tr: { x: right, y: top },
    bl: { x: left, y: bottom },
    br: { x: right, y: bottom },
  }

  return Math.min(
    distanceToSegment(p, corners.tl, corners.tr),
    distanceToSegment(p, corners.tr, corners.br),
    distanceToSegment(p, corners.br, corners.bl),
    distanceToSegment(p, corners.bl, corners.tl),
  )
}

export function isInsideRect(p: Point, a: Point, b: Point): boolean {
  return (
    p.x >= Math.min(a.x, b.x) &&
    p.x <= Math.max(a.x, b.x) &&
    p.y >= Math.min(a.y, b.y) &&
    p.y <= Math.max(a.y, b.y)
  )
}

/** Projects an anchor to screen space, or null if it is outside the visible range. */
export function project(anchor: Anchor, projection: Projection): Point | null {
  const x = projection.timeToX(anchor.time)
  const y = projection.priceToY(anchor.priceTicks)
  return x === null || y === null ? null : { x, y }
}

/**
 * What is under the pointer.
 *
 * Handles win over bodies, and later drawings win over earlier ones. Both rules match what a user
 * expects: the small grab target you are aiming at should beat the large shape behind it, and the
 * thing you just drew should be the thing you select.
 */
export function hitTest(
  point: Point,
  drawings: Drawing[],
  selectedId: string | null,
  projection: Projection,
  chartWidth: number,
): HitTarget {
  // Reverse order: topmost (most recently added) first.
  for (let i = drawings.length - 1; i >= 0; i--) {
    const drawing = drawings[i]

    // Handles only exist on the selected drawing, and only it can be reshaped.
    if (drawing.id === selectedId) {
      for (let anchorIndex = 0; anchorIndex < drawing.anchors.length; anchorIndex++) {
        const handle = project(drawing.anchors[anchorIndex], projection)
        if (handle && distance(point, handle) <= HANDLE_HIT_TOLERANCE) {
          return { type: 'handle', drawingId: drawing.id, anchorIndex }
        }
      }
    }

    if (hitsBody(point, drawing, projection, chartWidth)) {
      return { type: 'body', drawingId: drawing.id }
    }
  }

  return { type: 'none' }
}

function hitsBody(
  point: Point,
  drawing: Drawing,
  projection: Projection,
  chartWidth: number,
): boolean {
  const points = drawing.anchors.map((a) => project(a, projection))
  if (points.some((p) => p === null)) {
    // A drawing scrolled out of the visible range cannot be clicked. Not an error — it simply is
    // not on screen.
    return false
  }
  const [p0, p1, p2] = points as Point[]

  switch (drawing.kind) {
    case 'horizontal-line':
      // Spans the full width, so only the vertical distance matters.
      return Math.abs(point.y - p0.y) <= HIT_TOLERANCE && point.x >= 0 && point.x <= chartWidth

    case 'trend-line':
      return distanceToSegment(point, p0, p1) <= HIT_TOLERANCE

    case 'rectangle':
      // The outline is grabbable; the interior is not. Filling the interior would make a large
      // rectangle swallow every click inside it, including on the candles the user wants to read.
      return distanceToRectOutline(point, p0, p1) <= HIT_TOLERANCE

    case 'position': {
      // Entry, stop and target are horizontal levels spanning the tool's own time range.
      const left = Math.min(p0.x, chartWidth)
      const right = positionRight(drawing, projection, chartWidth)
      const withinSpan =
        point.x >= Math.min(left, right) - HIT_TOLERANCE && point.x <= right + HIT_TOLERANCE
      return (
        withinSpan &&
        [p0, p1, p2].some((p) => Math.abs(point.y - p.y) <= HIT_TOLERANCE)
      )
    }

    case 'fibonacci': {
      // Any level, from the leg's earlier end to the right edge, or the leg itself.
      const left = Math.min(p0.x, p1.x) - HIT_TOLERANCE
      if (distanceToSegment(point, p0, p1) <= HIT_TOLERANCE) return true
      if (point.x < left || point.x > chartWidth) return false
      return ICT_FIB_LEVELS.some(({ ratio }) => {
        const y = projection.priceToY(fibLevelTicks(drawing.anchors, ratio))
        return y !== null && Math.abs(point.y - y) <= HIT_TOLERANCE
      })
    }

    case 'zone': {
      // The outline, like a rectangle, or the consequent-encroachment line across it.
      if (distanceToRectOutline(point, p0, p1) <= HIT_TOLERANCE) return true
      const ce = projection.priceToY(ceHalfTicks(drawing.anchors) / 2)
      return (
        ce !== null &&
        Math.abs(point.y - ce) <= HIT_TOLERANCE &&
        point.x >= Math.min(p0.x, p1.x) &&
        point.x <= Math.max(p0.x, p1.x)
      )
    }

    case 'label': {
      // The text box to the right of the point. Estimated: there is no canvas here to measure on.
      const half = LABEL_FONT_PX
      return (
        point.x >= p0.x - HIT_TOLERANCE &&
        point.x <= p0.x + labelWidth(drawing.text ?? '') &&
        Math.abs(point.y - p0.y) <= half
      )
    }

    default:
      return false
  }
}

/**
 * Where a position zone ends on screen: the chart's right edge for a plan still open, or the exit
 * of a trade that has ended ({@link Drawing.endTime}).
 */
export function positionRight(drawing: Drawing, projection: Projection, chartWidth: number): number {
  if (drawing.endTime === undefined) return chartWidth
  const left = projection.timeToX(drawing.anchors[0].time)
  const right = projection.timeToX(drawing.endTime)
  if (left === null || right === null) return chartWidth
  // A trade that opened and closed inside one bar still gets a sliver the eye can find.
  return Math.min(Math.max(right, left + MIN_TRADE_WIDTH), chartWidth)
}

const MIN_TRADE_WIDTH = 6

/**
 * Risk and reward for a position tool, in ticks.
 *
 * Direction is inferred from where the target sits relative to entry, so the same tool serves longs
 * and shorts without a mode switch — drag the target above entry and it is a long, below and it is
 * a short.
 */
export interface PositionMetrics {
  direction: 'long' | 'short'
  riskTicks: number
  rewardTicks: number
  /** Reward divided by risk, or null when the stop sits exactly on the entry. */
  ratio: number | null
}

export function positionMetrics(anchors: Anchor[]): PositionMetrics {
  const [entry, stop, target] = anchors
  const riskTicks = Math.abs(entry.priceTicks - stop.priceTicks)
  const rewardTicks = Math.abs(target.priceTicks - entry.priceTicks)

  return {
    direction: target.priceTicks >= entry.priceTicks ? 'long' : 'short',
    riskTicks,
    rewardTicks,
    // Guarded rather than allowed to produce Infinity: a zero-risk trade is a user error mid-drag,
    // not a fact to display.
    ratio: riskTicks === 0 ? null : rewardTicks / riskTicks,
  }
}

/**
 * Moves every anchor by a screen-space delta, converting back to market space.
 *
 * Deltas are applied through the projection rather than added to stored coordinates, because the
 * relationship between pixels and price is not linear across the whole chart and is not constant
 * across zoom levels.
 */
export function translateAnchors(
  anchors: Anchor[],
  dx: number,
  dy: number,
  projection: Projection,
): Anchor[] {
  return anchors.map((anchor) => {
    const p = project(anchor, projection)
    if (!p) return anchor

    const time = projection.xToTime(p.x + dx)
    const priceTicks = projection.yToPrice(p.y + dy)

    return {
      time: time ?? anchor.time,
      priceTicks: priceTicks ?? anchor.priceTicks,
    }
  })
}
