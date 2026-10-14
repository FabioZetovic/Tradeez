import { describe, expect, it } from 'vitest'
import {
  distanceToRectOutline,
  distanceToSegment,
  hitTest,
  HIT_TOLERANCE,
  isInsideRect,
  positionMetrics,
  positionRight,
  translateAnchors,
} from './geometry'
import { renderDrawings } from './render'
import { canRedo, canUndo, drawingReducer, initialDrawingState } from './store'
import { MAGNET_TOLERANCE_TICKS, nearestBar, snapAnchor } from './snap'
import { DEFAULT_STYLES, type Drawing, type Projection } from './types'
import type { DomainBar } from '../chart/types'

/**
 * The drawing layer's core, tested without a canvas.
 *
 * Hit testing, history and snapping are where a hand-built drawing tool actually goes wrong, and
 * all three are pure functions here — so they can be tested properly rather than by clicking and
 * hoping. That separation is the main reason building this by hand is defensible at all (DEC-004).
 */

/**
 * A linear projection: 1 pixel per second horizontally, 1 pixel per tick vertically, y inverted so
 * higher prices sit higher on screen, exactly as a real chart does.
 */
const projection: Projection = {
  timeToX: (time) => time - 1000,
  priceToY: (ticks) => 500 - (ticks - 80_000),
  xToTime: (x) => x + 1000,
  yToPrice: (y) => 80_000 + (500 - y),
}

const CHART_WIDTH = 800

function drawing(over: Partial<Drawing> & Pick<Drawing, 'id' | 'kind' | 'anchors'>): Drawing {
  return { style: DEFAULT_STYLES[over.kind], ...over } as Drawing
}

describe('geometry', () => {
  describe('distanceToSegment', () => {
    it('measures perpendicular distance when the point is beside the segment', () => {
      expect(distanceToSegment({ x: 5, y: 10 }, { x: 0, y: 0 }, { x: 10, y: 0 })).toBe(10)
    })

    it('measures to the nearest endpoint when the point is past the end', () => {
      // The segment, not the infinite line. Clicking well beyond a short trend line must not select
      // it, or a busy chart becomes impossible to work with.
      expect(distanceToSegment({ x: 30, y: 0 }, { x: 0, y: 0 }, { x: 10, y: 0 })).toBe(20)
    })

    it('handles a degenerate zero-length segment', () => {
      // Happens mid-drag, the instant a two-point tool is created before the second point moves.
      expect(distanceToSegment({ x: 3, y: 4 }, { x: 0, y: 0 }, { x: 0, y: 0 })).toBe(5)
    })
  })

  describe('rectangles', () => {
    it('reports zero distance on an edge', () => {
      expect(distanceToRectOutline({ x: 5, y: 0 }, { x: 0, y: 0 }, { x: 10, y: 10 })).toBe(0)
    })

    it('measures to the outline from inside, not zero', () => {
      // The interior is intentionally not part of the outline: a large rectangle must not swallow
      // clicks meant for the candles underneath it.
      expect(distanceToRectOutline({ x: 5, y: 5 }, { x: 0, y: 0 }, { x: 10, y: 10 })).toBe(5)
    })

    it('recognises containment regardless of which corners were given', () => {
      expect(isInsideRect({ x: 5, y: 5 }, { x: 10, y: 10 }, { x: 0, y: 0 })).toBe(true)
      expect(isInsideRect({ x: 15, y: 5 }, { x: 0, y: 0 }, { x: 10, y: 10 })).toBe(false)
    })
  })

  describe('hitTest', () => {
    const hline = drawing({
      id: 'h1',
      kind: 'horizontal-line',
      anchors: [{ time: 1200, priceTicks: 80_000 }],
    })
    const trend = drawing({
      id: 't1',
      kind: 'trend-line',
      anchors: [
        { time: 1100, priceTicks: 80_100 },
        { time: 1300, priceTicks: 80_200 },
      ],
    })

    it('finds a horizontal line anywhere along its width', () => {
      // y for 80_000 is 500. x is irrelevant — the line spans the chart.
      const hit = hitTest({ x: 700, y: 500 }, [hline], null, projection, CHART_WIDTH)
      expect(hit).toEqual({ type: 'body', drawingId: 'h1' })
    })

    it('misses a horizontal line that is more than the tolerance away', () => {
      const hit = hitTest(
        { x: 700, y: 500 + HIT_TOLERANCE + 2 },
        [hline],
        null,
        projection,
        CHART_WIDTH,
      )
      expect(hit.type).toBe('none')
    })

    it('prefers a handle over the body it sits on', () => {
      // The handle is a small target deliberately placed on top of a large one. Aiming at it and
      // getting the body instead would make reshaping nearly impossible.
      const handlePoint = { x: 100, y: 400 } // trend anchor 0: time 1100, ticks 80_100
      const hit = hitTest(handlePoint, [trend], 't1', projection, CHART_WIDTH)
      expect(hit).toEqual({ type: 'handle', drawingId: 't1', anchorIndex: 0 })
    })

    it('offers no handles on a drawing that is not selected', () => {
      const hit = hitTest({ x: 100, y: 400 }, [trend], null, projection, CHART_WIDTH)
      expect(hit).toEqual({ type: 'body', drawingId: 't1' })
    })

    it('picks the most recently added drawing when two overlap', () => {
      const older = drawing({
        id: 'old',
        kind: 'horizontal-line',
        anchors: [{ time: 1200, priceTicks: 80_000 }],
      })
      const newer = drawing({
        id: 'new',
        kind: 'horizontal-line',
        anchors: [{ time: 1200, priceTicks: 80_000 }],
      })
      const hit = hitTest({ x: 400, y: 500 }, [older, newer], null, projection, CHART_WIDTH)
      expect(hit).toEqual({ type: 'body', drawingId: 'new' })
    })

    it('cannot hit a drawing that is scrolled out of view', () => {
      const offscreen: Projection = { ...projection, timeToX: () => null }
      const hit = hitTest({ x: 400, y: 500 }, [hline], null, offscreen, CHART_WIDTH)
      expect(hit.type).toBe('none')
    })
  })

  describe('positionMetrics', () => {
    const entry = { time: 1000, priceTicks: 80_000 }

    it('reads a long from a target above the entry', () => {
      const m = positionMetrics([
        entry,
        { time: 1000, priceTicks: 79_960 }, // stop 40 ticks below
        { time: 1000, priceTicks: 80_080 }, // target 80 ticks above
      ])
      expect(m.direction).toBe('long')
      expect(m.riskTicks).toBe(40)
      expect(m.rewardTicks).toBe(80)
      expect(m.ratio).toBe(2)
    })

    it('reads a short from a target below the entry', () => {
      // One tool for both directions: drag the target under the entry and it is a short. A mode
      // switch would be a thing to forget mid-analysis.
      const m = positionMetrics([
        entry,
        { time: 1000, priceTicks: 80_040 },
        { time: 1000, priceTicks: 79_880 },
      ])
      expect(m.direction).toBe('short')
      expect(m.riskTicks).toBe(40)
      expect(m.rewardTicks).toBe(120)
      expect(m.ratio).toBe(3)
    })

    it('reports no ratio rather than Infinity when risk is zero', () => {
      // Happens while dragging the stop through the entry. Infinity on screen is a bug report.
      const m = positionMetrics([entry, entry, { time: 1000, priceTicks: 80_080 }])
      expect(m.ratio).toBeNull()
    })
  })

  describe('a position that has ended (DEC-046)', () => {
    const anchors = [
      { time: 1100, priceTicks: 80_000 },
      { time: 1100, priceTicks: 79_960 },
      { time: 1100, priceTicks: 80_080 },
    ]

    it('runs to the right edge while open, and stops at its exit once ended', () => {
      const open = drawing({ id: 'p', kind: 'position', anchors })
      expect(positionRight(open, projection, CHART_WIDTH)).toBe(CHART_WIDTH)
      const ended = { ...open, endTime: 1400 }
      expect(positionRight(ended, projection, CHART_WIDTH)).toBe(400)
    })

    it('stays visible when it opened and closed inside one bar', () => {
      const ended = drawing({ id: 'p', kind: 'position', anchors, endTime: 1100 })
      expect(positionRight(ended, projection, CHART_WIDTH)).toBeGreaterThan(100)
      expect(positionRight(ended, projection, CHART_WIDTH)).toBeLessThan(110)
    })

    it('is painted from entry to exit, not to the edge of the chart', () => {
      // A context that records only what the zone is made of: its two filled rectangles.
      const fills: number[][] = []
      const ctx = new Proxy(
        { fillRect: (...r: number[]) => fills.push(r), measureText: () => ({ width: 0 }) },
        { get: (target, key) => (key in target ? target[key as keyof typeof target] : () => {}) },
      ) as unknown as CanvasRenderingContext2D
      const options = { width: CHART_WIDTH, height: 600, selectedId: null, tickValueCents: 500 }

      renderDrawings(ctx, [drawing({ id: 'p', kind: 'position', anchors, endTime: 1400 })], projection, options)
      // Reward zone then risk zone, both from x = 100 (time 1100) to x = 400 (time 1400).
      expect(fills.map(([x, , w]) => [x, w])).toEqual([
        [100, 300],
        [100, 300],
      ])

      fills.length = 0
      renderDrawings(ctx, [drawing({ id: 'p', kind: 'position', anchors })], projection, options)
      expect(fills.map(([x, , w]) => [x, w])).toEqual([
        [100, 700],
        [100, 700],
      ])
    })

    it('cannot be clicked to the right of its exit', () => {
      const ended = drawing({ id: 'p', kind: 'position', anchors, endTime: 1400 })
      // On the entry level, inside the trade, then well past its exit.
      expect(hitTest({ x: 300, y: 500 }, [ended], null, projection, CHART_WIDTH)).toEqual({
        type: 'body',
        drawingId: 'p',
      })
      expect(hitTest({ x: 600, y: 500 }, [ended], null, projection, CHART_WIDTH)).toEqual({
        type: 'none',
      })
    })
  })

  describe('translateAnchors', () => {
    it('moves anchors through the projection, not by adding to stored values', () => {
      const moved = translateAnchors(
        [{ time: 1200, priceTicks: 80_000 }],
        50, // right 50px = +50s
        -20, // up 20px = +20 ticks
        projection,
      )
      expect(moved[0]).toEqual({ time: 1250, priceTicks: 80_020 })
    })
  })
})

describe('store', () => {
  const d1 = drawing({ id: 'a', kind: 'horizontal-line', anchors: [{ time: 1, priceTicks: 10 }] })
  const d2 = drawing({ id: 'b', kind: 'horizontal-line', anchors: [{ time: 2, priceTicks: 20 }] })

  it('adds and selects in one step', () => {
    const s = drawingReducer(initialDrawingState, { type: 'add', drawing: d1 })
    expect(s.drawings).toEqual([d1])
    expect(s.selectedId).toBe('a')
  })

  it('undoes and redoes an add', () => {
    let s = drawingReducer(initialDrawingState, { type: 'add', drawing: d1 })
    s = drawingReducer(s, { type: 'add', drawing: d2 })
    expect(s.drawings).toHaveLength(2)

    s = drawingReducer(s, { type: 'undo' })
    expect(s.drawings).toEqual([d1])
    expect(canRedo(s)).toBe(true)

    s = drawingReducer(s, { type: 'redo' })
    expect(s.drawings).toHaveLength(2)
  })

  it('does not record history for a drag preview', () => {
    // A drag fires on every mouse move. Recording each one would mean fifty presses of undo to
    // reverse a single gesture.
    let s = drawingReducer(initialDrawingState, { type: 'add', drawing: d1 })
    const historyDepth = s.past.length

    const moved = { ...d1, anchors: [{ time: 9, priceTicks: 99 }] }
    s = drawingReducer(s, { type: 'preview', drawing: moved })

    expect(s.drawings[0].anchors[0].time).toBe(9)
    expect(s.past).toHaveLength(historyDepth)
  })

  it('discards the redo branch once a new edit is made', () => {
    // Otherwise redo could walk forward into a state that never existed on this timeline.
    let s = drawingReducer(initialDrawingState, { type: 'add', drawing: d1 })
    s = drawingReducer(s, { type: 'add', drawing: d2 })
    s = drawingReducer(s, { type: 'undo' })
    expect(canRedo(s)).toBe(true)

    s = drawingReducer(s, { type: 'add', drawing: d2 })
    expect(canRedo(s)).toBe(false)
  })

  it('clears a selection that undo made non-existent', () => {
    // Otherwise handles hang in space over a drawing that is no longer there.
    let s = drawingReducer(initialDrawingState, { type: 'add', drawing: d1 })
    s = drawingReducer(s, { type: 'add', drawing: d2 })
    expect(s.selectedId).toBe('b')

    s = drawingReducer(s, { type: 'undo' })
    expect(s.selectedId).toBeNull()
  })

  it('removing clears the selection when it was the selected one', () => {
    let s = drawingReducer(initialDrawingState, { type: 'add', drawing: d1 })
    s = drawingReducer(s, { type: 'remove', id: 'a' })
    expect(s.drawings).toHaveLength(0)
    expect(s.selectedId).toBeNull()
  })

  it('loading from the server resets history', () => {
    // The server's state is the new origin; undoing past it would resurrect drawings the user
    // deleted in a previous session.
    let s = drawingReducer(initialDrawingState, { type: 'add', drawing: d1 })
    s = drawingReducer(s, { type: 'load', drawings: [d2] })
    expect(s.drawings).toEqual([d2])
    expect(canUndo(s)).toBe(false)
  })

  it('does nothing when there is nothing to undo', () => {
    expect(drawingReducer(initialDrawingState, { type: 'undo' })).toBe(initialDrawingState)
  })
})

describe('snap', () => {
  const bars: DomainBar[] = [
    { time: 1000, open: 80_000, high: 80_050, low: 79_950, close: 80_020, volume: 1 },
    { time: 1300, open: 80_020, high: 80_090, low: 80_000, close: 80_070, volume: 1 },
  ]

  it('attaches to the nearest OHLC level within tolerance', () => {
    const result = snapAnchor({ time: 1000, priceTicks: 80_047 }, bars, 'magnet')
    expect(result.snappedTo).toBe('high')
    expect(result.anchor.priceTicks).toBe(80_050)
  })

  it('leaves the price alone when nothing is close', () => {
    // A magnet that always grabs makes precise placement impossible.
    const far = 80_050 - MAGNET_TOLERANCE_TICKS - 10
    const result = snapAnchor({ time: 1000, priceTicks: far }, bars, 'magnet')
    expect(result.snappedTo).toBeNull()
    expect(result.anchor.priceTicks).toBe(far)
  })

  it('does nothing when the magnet is off', () => {
    const result = snapAnchor({ time: 1000, priceTicks: 80_049 }, bars, 'off')
    expect(result.snappedTo).toBeNull()
    expect(result.anchor.priceTicks).toBe(80_049)
  })

  it('only considers the bar under the cursor', () => {
    // 80_090 is the *second* bar's high. Pointing at the first bar must not reach across to it.
    const result = snapAnchor({ time: 1000, priceTicks: 80_088 }, bars, 'magnet')
    expect(result.anchor.priceTicks).not.toBe(80_090)
  })

  describe('nearestBar', () => {
    it('finds the closest bar on either side', () => {
      expect(nearestBar(1010, bars)?.time).toBe(1000)
      expect(nearestBar(1290, bars)?.time).toBe(1300)
    })

    it('handles times outside the series', () => {
      expect(nearestBar(0, bars)?.time).toBe(1000)
      expect(nearestBar(99_999, bars)?.time).toBe(1300)
    })

    it('returns null for an empty series', () => {
      expect(nearestBar(1000, [])).toBeNull()
    })
  })
})
