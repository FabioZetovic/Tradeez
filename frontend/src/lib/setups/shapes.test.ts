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
    midHalfTicks: fields.midHalfTicks ?? (fields.lowTicks ?? 100) + (fields.highTicks ?? 110),
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

  it("T17: draws a block at its latest state — on while it stands — apart from another rule's block on the same candle", () => {
    const formed = setup({ detector: 'OB', kind: 'FORMED', end: 600, confirmedAt: 900 })
    const lost = setup({ detector: 'OB', kind: 'MEAN_THRESHOLD', end: 1500, confirmedAt: 1800 })
    const breaker = setup({ detector: 'BREAKER', kind: 'FORMED', end: 600, confirmedAt: 900 })
    const [ob, flip] = toShapes(layer([formed, lost, breaker]), STEP)
    expect(ob).toMatchObject({ setup: lost, from: 0, to: 'edge', label: 'OB MT lost', faded: false })
    expect(flip).toMatchObject({ setup: breaker, from: 0, to: 'edge', label: 'Breaker', faded: false })
  })

  it('T17: stops a block at the candle that invalidated it, faded — the furthest of the states that candle reached', () => {
    const at = { detector: 'UNICORN', end: 1500, confirmedAt: 1800 }
    const shapes = toShapes(
      layer([setup({ ...at, kind: 'TAPPED' }), setup({ ...at, kind: 'INVALIDATED' }), setup({ ...at, kind: 'MEAN_THRESHOLD' })]),
      STEP,
    )
    expect(shapes).toEqual([expect.objectContaining({ to: 1800, label: 'Unicorn invalidated', faded: true })])
  })

  it('T18: names premium and discount by their side, each on while its range stands; a range left stops, faded', () => {
    const premium = setup({ detector: 'PD', kind: 'FORMED', direction: 'BEARISH', lowTicks: 117, highTicks: 136 })
    const discount = setup({ detector: 'PD', kind: 'FORMED', direction: 'BULLISH', lowTicks: 97, highTicks: 116 })
    const range = setup({ detector: 'DEALING_RANGE', kind: 'FORMED' })
    const left = setup({ detector: 'DEALING_RANGE', kind: 'BROKEN_LOW', end: 1500, confirmedAt: 1800 })
    const [high, low, dr] = toShapes(layer([premium, discount, range, left]), STEP)
    expect(high).toMatchObject({ setup: premium, to: 'edge', label: 'Premium', faded: false })
    expect(low).toMatchObject({ setup: discount, to: 'edge', label: 'Discount', faded: false })
    expect(dr).toMatchObject({ setup: left, to: 1800, label: 'Range low broken', faded: true })
  })

  it('T18: runs a standard deviation level from its turn until the next leg projects the same level the same way', () => {
    const level = (anchor: number, kind: string, direction: Setup['direction'] = 'BULLISH') =>
      setup({ detector: 'STDEV', shape: 'LEVEL', kind, direction, start: anchor, anchor, end: anchor + 600, lowTicks: 200, highTicks: 200 })
    const first = level(300, 'TWO')
    const other = level(900, 'FOUR')
    const opposite = level(1200, 'TWO', 'BEARISH')
    const next = level(1500, 'TWO')
    const shapes = toShapes(layer([first, other, opposite, next]), STEP)
    expect(shapes[0]).toMatchObject({ setup: first, from: 300, to: 1500, label: 'SD −2' })
    expect(shapes[1]).toMatchObject({ setup: other, to: 'edge', label: 'SD −4' })
    expect(shapes[3]).toMatchObject({ setup: next, from: 1500, to: 'edge' })
  })

  it('T19: names a day by its profile, a week by the day of its extreme, a Judas swing by its window', () => {
    const shapes = toShapes(
      layer([
        setup({ detector: 'PO3', start: 0, end: 600 }),
        setup({ detector: 'DAILY_PROFILE', kind: 'LONDON_HIGH', direction: 'BEARISH', start: 900, end: 1500 }),
        setup({ detector: 'DAILY_PROFILE', kind: 'NY_REVERSAL', start: 1800, end: 2400 }),
        setup({ detector: 'WEEKLY_PROFILE', kind: 'TUESDAY', start: 2700, end: 3300 }),
        setup({ detector: 'WEEKLY_PROFILE', kind: 'MONDAY', direction: 'BEARISH', start: 3600, end: 4200 }),
        setup({ detector: 'JUDAS', kind: 'LONDON', start: 4500, end: 4800 }),
        setup({ detector: 'JUDAS', kind: 'NEW_YORK', direction: 'BEARISH', start: 5100, end: 5400 }),
      ]),
      STEP,
    )
    expect(shapes.map((s) => s.label)).toEqual([
      'Po3',
      'London high',
      'NY reversal',
      'Tue low of week',
      'Mon high of week',
      'Judas London',
      'Judas NY',
    ])
    // Over its own candles, like any plain hit: a day is not in force once it is over.
    expect(shapes[0]).toMatchObject({ from: 0, to: 900, faded: false })
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
