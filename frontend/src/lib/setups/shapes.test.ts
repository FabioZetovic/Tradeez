import { describe, expect, it } from 'vitest'
import type { Setup, SetupLayer } from '../api/setups'
import { toShapes } from './shapes'

const STEP = 300

function setup(fields: Partial<Setup>): Setup {
  return {
    detector: 'FVG',
    shape: 'ZONE',
    direction: 'BULLISH',
    start: 0,
    anchor: 300,
    end: 600,
    confirmedAt: 900,
    lowTicks: 100,
    highTicks: 110,
    ...fields,
  }
}

const layer = (setups: Setup[], rolls: number[] = []): SetupLayer => ({ rules: [], setups, rolls })

describe('what each hit looks like on the chart', () => {
  it('draws a plain hit over its own candles, named by its kind or its rule', () => {
    const [vi, gap] = toShapes(
      layer([setup({ detector: 'VI' }), setup({ detector: 'VOID', kind: 'GAP', start: 900, end: 1200 })]),
      STEP,
    )
    expect(vi).toMatchObject({ from: 0, to: 900, label: 'VI', faded: false })
    expect(gap).toMatchObject({ from: 900, to: 1500, label: 'GAP' })
  })

  it("keeps one box per gap at its latest state: live ones run to the edge, finished ones stop and fade", () => {
    const open = setup({ detector: 'FVG_STATE', kind: 'OPEN', end: 600, confirmedAt: 900 })
    const tapped = setup({ detector: 'FVG_STATE', kind: 'TAPPED', end: 1500, confirmedAt: 1800 })
    const other = setup({ detector: 'FVG_STATE', kind: 'OPEN', anchor: 3000, start: 2700, end: 3300, confirmedAt: 3600 })
    const filled = setup({ ...other, kind: 'FILLED', end: 3900, confirmedAt: 4200 })
    const shapes = toShapes(layer([open, tapped, other, filled]), STEP)

    expect(shapes).toHaveLength(2)
    expect(shapes[0]).toMatchObject({ setup: tapped, from: 0, to: 'edge', label: 'FVG tapped', faded: false })
    expect(shapes[1]).toMatchObject({ setup: filled, from: 2700, to: 4200, label: 'FVG filled', faded: true })
  })

  it('takes the furthest state when one candle reached several', () => {
    const at = { detector: 'FVG_STATE', end: 1500, confirmedAt: 1800 }
    const shapes = toShapes(
      layer([setup({ ...at, kind: 'TAPPED' }), setup({ ...at, kind: 'INVERTED' }), setup({ ...at, kind: 'CE' })]),
      STEP,
    )
    expect(shapes).toHaveLength(1)
    expect(shapes[0].label).toBe('FVG inverted')
  })

  it('stops anything still in force at the next contract roll', () => {
    const live = setup({ detector: 'FVG_STATE', kind: 'CE' })
    const nwog = setup({ detector: 'OPENING_GAP', kind: 'NWOG', start: 0, anchor: 3600, end: 3600 })
    const shapes = toShapes(layer([live, nwog], [100, 7200]), STEP)
    // Each stops at the first roll after it began.
    expect(shapes.find((s) => s.setup === live)?.to).toBe(100)
    expect(shapes.find((s) => s.setup === nwog)?.to).toBe(7200)
  })

  it('runs an NDOG from the reopen to the last candle before the next closure, an NWOG to the edge', () => {
    const ndog = setup({ detector: 'OPENING_GAP', kind: 'NDOG', start: 0, anchor: 3600, end: 3600 })
    const nwog = setup({ detector: 'OPENING_GAP', kind: 'NWOG', start: 90_000, anchor: 230_000, end: 230_000 })
    const [first, second] = toShapes(layer([ndog, nwog]), STEP)
    expect(first).toMatchObject({ from: 3600, to: 90_300, label: 'NDOG' })
    expect(second).toMatchObject({ from: 230_000, to: 'edge', label: 'NWOG' })
  })

  it('puts highs above and lows below, names ranks and labels, and leaves plain swings unnamed (T11)', () => {
    const point = (detector: string, kind: string, direction: Setup['direction']) =>
      setup({ detector, kind, direction, shape: 'POINT', lowTicks: 120, highTicks: 120 })
    const [swing, ith, hh, hl] = toShapes(
      layer([
        point('SWING', 'HIGH', 'BEARISH'),
        point('SWING_TIER', 'ITH', 'BEARISH'),
        point('STRUCTURE', 'HH', 'BULLISH'),
        point('STRUCTURE', 'HL', 'BULLISH'),
      ]),
      STEP,
    )
    expect(swing).toMatchObject({ above: true, label: '' })
    expect(ith).toMatchObject({ above: true, label: 'ITH' })
    // A higher high is bullish structure, but it is still a high: above the candle.
    expect(hh).toMatchObject({ above: true, label: 'HH' })
    expect(hl).toMatchObject({ above: false, label: 'HL' })
  })

  it('T12: one line per liquidity level — running on while it rests, stopped and faint once taken', () => {
    const level = (kind: string, end: number, confirmedAt: number, direction: Setup['direction']) =>
      setup({ detector: 'LIQUIDITY', kind, shape: 'LEVEL', direction, start: 0, anchor: 0, end, confirmedAt, lowTicks: 120, highTicks: 120 })
    const shapes = toShapes(
      layer([
        level('RESTING', 300, 600, 'BEARISH'),
        level('TAKEN', 1500, 1800, 'BEARISH'),
        level('RESTING', 300, 600, 'BULLISH'),
      ]),
      STEP,
    )
    expect(shapes).toHaveLength(2)
    expect(shapes[0]).toMatchObject({ label: 'BSL', to: 1800, faded: true })
    expect(shapes[1]).toMatchObject({ label: 'SSL', to: 'edge', faded: false })
  })

  it('draws a break as a line from the swing to the candle that broke it, by its SMC name', () => {
    const choch = setup({ detector: 'BOS_CHOCH', kind: 'CHOCH', shape: 'LEVEL', start: 0, anchor: 900, end: 900 })
    const [shape] = toShapes(layer([choch]), STEP)
    expect(shape).toMatchObject({ from: 0, to: 1200, label: 'CHoCH' })
  })

  it('runs an opening range gap from 09:30 to the next one’s 16:15 close, with its quarters', () => {
    const monday = setup({ detector: 'ORG', start: 0, anchor: 60_000, end: 60_000 })
    const tuesday = setup({ detector: 'ORG', start: 84_000, anchor: 145_000, end: 145_000 })
    const [first, second] = toShapes(layer([monday, tuesday]), STEP)
    expect(first).toMatchObject({ from: 60_000, to: 84_300, quadrants: true })
    expect(second).toMatchObject({ from: 145_000, to: 'edge' })
  })
})
