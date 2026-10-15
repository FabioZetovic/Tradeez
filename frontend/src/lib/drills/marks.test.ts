import { describe, expect, it } from 'vitest'
import { pickCandle, toggleMark } from './marks'
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
