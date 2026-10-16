import { describe, expect, it } from 'vitest'
import type { DomainBar } from './types'
import type { Projection } from '../drawing/types'
import { candleAt, timeToX } from './timeToX'

/**
 * Five-minute bars with a closed market in the middle — 00:15 to 01:00 has no bars, as a
 * maintenance break or a weekend would not. The projection places bars the way the chart does:
 * ten pixels apart by index, gaps collapsed, and nothing at all for a time that is not a bar's.
 */
const STEP = 300
const TIMES = [0, 300, 600, 3600, 3900]
const BARS: DomainBar[] = TIMES.map((time) => ({
  time,
  open: 100,
  high: 100,
  low: 100,
  close: 100,
  volume: 1,
}))

const projection: Projection = {
  timeToX: (time) => {
    const index = TIMES.indexOf(time)
    return index === -1 ? null : index * 10
  },
  priceToY: (ticks) => 200 - ticks,
  xToTime: () => null,
  yToPrice: () => null,
}

describe('placing a time on the chart', () => {
  it('puts a time inside a bar by proportion', () => {
    expect(timeToX(150, BARS, STEP, projection)).toBe(0) // half way through the first bar
    expect(timeToX(0, BARS, STEP, projection)).toBe(-5) // its left edge
    expect(timeToX(3600, BARS, STEP, projection)).toBe(25)
  })

  it('puts a time in a closed market at the edge of the bar before it', () => {
    // The gap has no width on screen: the last bar's right edge is the next bar's left edge.
    expect(timeToX(2000, BARS, STEP, projection)).toBe(25)
  })

  it('clamps times before the first bar and after the newest', () => {
    expect(timeToX(-1000, BARS, STEP, projection)).toBe(-5)
    // A session still running ends, for now, at the newest candle's right edge.
    expect(timeToX(99_999, BARS, STEP, projection)).toBe(45)
  })

  it('has nothing to say without bars', () => {
    expect(timeToX(0, [], STEP, projection)).toBeNull()
  })
})


describe('the candle a time falls in', () => {
  it('is the last candle opening at or before it — across a closure too', () => {
    expect(candleAt(0, BARS)).toBe(0)
    expect(candleAt(299, BARS)).toBe(0)
    expect(candleAt(300, BARS)).toBe(300)
    // Inside the closed market: the candle before the gap.
    expect(candleAt(2000, BARS)).toBe(600)
    expect(candleAt(99_999, BARS)).toBe(3900)
  })

  it('is the time itself before the first candle, or with no candles at all', () => {
    expect(candleAt(-5, BARS)).toBe(-5)
    expect(candleAt(42, [])).toBe(42)
  })
})
