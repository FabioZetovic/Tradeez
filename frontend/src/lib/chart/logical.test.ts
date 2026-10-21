import { describe, expect, it } from 'vitest'
import { logicalAt, timeAtLogical } from './logical'

// Five-minute candles with one missing (the 4th), as real data has quiet minutes.
const TIMES = [0, 300, 600, 1200, 1500]

describe('logical positions and times (DEC-075)', () => {
  it('reads a candle at its own time, and between two in proportion', () => {
    expect(timeAtLogical(TIMES, 1)).toBe(300)
    expect(timeAtLogical(TIMES, 2.5)).toBe(900)
    expect(logicalAt(TIMES, 1200)).toBe(3)
    expect(logicalAt(TIMES, 900)).toBe(2.5)
  })

  it('runs past either end a step a candle, into the empty space a chart shows', () => {
    expect(timeAtLogical(TIMES, -2)).toBe(-600)
    expect(timeAtLogical(TIMES, 6.5)).toBe(2250)
    expect(logicalAt(TIMES, -600)).toBe(-2)
    expect(logicalAt(TIMES, 2250)).toBe(6.5)
  })

  it('each undoes the other', () => {
    for (const logical of [-3.5, 0, 0.25, 2.75, 4, 9]) {
      expect(logicalAt(TIMES, timeAtLogical(TIMES, logical))).toBeCloseTo(logical)
    }
  })

  it('takes a minute as the step of a single candle', () => {
    expect(timeAtLogical([1000], 2)).toBe(1120)
    expect(logicalAt([1000], 1120)).toBe(2)
  })
})
