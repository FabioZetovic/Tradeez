import { describe, expect, it } from 'vitest'
import { explainFvg, pickCandle, toggleMark } from './fvg'
import type { SetupView } from '../api/drills'
import type { DomainBar } from '../chart/types'
import type { Projection } from '../drawing/types'

const T0 = 1_710_338_400
const bars: DomainBar[] = [0, 1, 2, 3].map((i) => ({
  time: T0 + i * 300,
  open: 80_000,
  high: 80_010,
  low: 79_990,
  close: 80_000,
  volume: 1,
}))

/** Candles 20 px apart, starting at x = 100. */
const projection: Projection = {
  timeToX: (time) => 100 + ((time - T0) / 300) * 20,
  xToTime: (x) => T0 + ((x - 100) / 20) * 300,
  priceToY: (ticks) => 500 - (ticks - 80_000),
  yToPrice: (y) => 80_000 + (500 - y),
}

const bullish: SetupView = {
  id: 's1',
  direction: 'BULLISH',
  startTime: T0,
  anchorTime: T0 + 300,
  endTime: T0 + 600,
  zoneLowTicks: 80_004,
  zoneHighTicks: 80_014,
  found: true,
  evidence: {
    candle1Time: T0,
    candle1HighTicks: 80_004,
    candle1LowTicks: 79_996,
    candle2Time: T0 + 300,
    candle3Time: T0 + 600,
    candle3HighTicks: 80_035,
    candle3LowTicks: 80_014,
    gapTicks: 10,
    sumRangeTicks: 112,
    lookback: 14,
    thresholdTicks: 2,
  },
}

describe('pickCandle', () => {
  it('snaps a click to the candle under it', () => {
    expect(pickCandle(140, projection, bars)).toBe(T0 + 600)
    expect(pickCandle(147, projection, bars)).toBe(T0 + 600)
  })

  it('ignores a click in the empty space beyond the last candle', () => {
    // The last candle is at x = 160; half the spacing is 10 px.
    expect(pickCandle(175, projection, bars)).toBeNull()
  })

  it('ignores a click the chart cannot place in time', () => {
    expect(pickCandle(140, { ...projection, xToTime: () => null }, bars)).toBeNull()
  })
})

describe('toggleMark', () => {
  it('adds a candle, keeping the marks in time order', () => {
    expect(toggleMark([T0 + 600], T0)).toEqual([T0, T0 + 600])
  })

  it('removes a candle that was already marked', () => {
    expect(toggleMark([T0, T0 + 600], T0)).toEqual([T0 + 600])
  })
})

describe('explainFvg (DEC-026)', () => {
  it('states the rule in the detector’s own numbers', () => {
    const [geometry, size] = explainFvg(bullish, 0.25, 25)
    expect(geometry).toBe(
      "Candle 1's high, 20001.00, is below candle 3's low, 20003.50. Neither traded in the " +
        '10 ticks (2.50 points) between them: a bullish gap.',
    )
    expect(size).toBe(
      'Big enough to count: the rule needs at least 2 ticks here — 25% of the 8.0-tick ' +
        'average range of the 14 candles before candle 1.',
    )
  })

  it('describes a bearish gap from candle 1’s low and candle 3’s high', () => {
    const bearish: SetupView = {
      ...bullish,
      direction: 'BEARISH',
      evidence: { ...bullish.evidence, candle1LowTicks: 80_020, candle3HighTicks: 80_010 },
    }
    const [geometry] = explainFvg(bearish, 0.25, 25)
    expect(geometry).toContain("Candle 1's low, 20005.00, is above candle 3's high, 20002.50")
    expect(geometry).toContain('a bearish gap')
  })

  it('does not claim more than the rule checked', () => {
    // An earlier draft said the gap "was crossed only by candle 2", which the rule never verifies.
    const text = explainFvg(bullish, 0.25, 25).join(' ')
    expect(text).not.toMatch(/only by candle 2/)
    expect(text).not.toMatch(/will|always|guarantee/i)
  })

  it('takes the percentage from the rule, not from the wording', () => {
    expect(explainFvg(bullish, 0.25, 40)[1]).toContain('40% of')
  })
})
