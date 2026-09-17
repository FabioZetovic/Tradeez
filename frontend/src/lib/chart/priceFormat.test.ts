import { describe, expect, it } from 'vitest'
import { decimalsFor, formatPrice, priceToTicks, ticksToPrice } from './priceFormat'

/**
 * INVARIANT 3 lives or dies here. Everything downstream — the price axis, the risk/reward tool,
 * every P&L figure a learner is shown — is built on these four functions being exactly right.
 */
describe('priceFormat', () => {
  describe('decimalsFor', () => {
    it('derives precision from the tick size', () => {
      expect(decimalsFor(0.25)).toBe(2)
      expect(decimalsFor(0.01)).toBe(2)
      expect(decimalsFor(0.5)).toBe(1)
      expect(decimalsFor(1)).toBe(0)
      expect(decimalsFor(0.0001)).toBe(4)
    })

    it('rejects a nonsensical tick size rather than producing NaN prices', () => {
      expect(() => decimalsFor(0)).toThrow()
      expect(() => decimalsFor(-0.25)).toThrow()
      expect(() => decimalsFor(Number.NaN)).toThrow()
    })
  })

  describe('ticksToPrice', () => {
    it('converts NQ tick counts to index points', () => {
      // 80000 ticks x 0.25 = 20000.00 — the generator's base price.
      expect(ticksToPrice(80_000, 0.25)).toBe(20_000)
      expect(ticksToPrice(80_001, 0.25)).toBe(20_000.25)
      expect(ticksToPrice(80_003, 0.25)).toBe(20_000.75)
    })

    it('handles negative tick counts', () => {
      expect(ticksToPrice(-4, 0.25)).toBe(-1)
    })

    it('does not accumulate floating-point drift on a non-binary tick size', () => {
      // This is the whole reason the domain stores integers. 0.01 is not representable in binary,
      // so the naive product gives 20000.249999999996 and the price axis would show a value that
      // could never trade. Every one of these must land exactly on the grid.
      for (let ticks = 2_000_000; ticks < 2_000_100; ticks++) {
        const price = ticksToPrice(ticks, 0.01)
        expect(Number.isInteger(Math.round(price * 100))).toBe(true)
        expect(price).toBeCloseTo(ticks / 100, 10)
      }
    })
  })

  describe('priceToTicks', () => {
    it('is the inverse of ticksToPrice', () => {
      for (const ticks of [0, 1, 79_999, 80_000, 123_457]) {
        expect(priceToTicks(ticksToPrice(ticks, 0.25), 0.25)).toBe(ticks)
      }
    })

    it('snaps an off-grid price onto the tick grid', () => {
      // A user dragging a stop-loss produces arbitrary prices. Storing one unrounded would mean an
      // order at a price that cannot exist.
      expect(priceToTicks(20_000.3, 0.25)).toBe(80_001) // nearest is 20000.25
      expect(priceToTicks(20_000.2, 0.25)).toBe(80_001)
      expect(priceToTicks(20_000.1, 0.25)).toBe(80_000) // nearest is 20000.00
    })
  })

  describe('formatPrice', () => {
    it('pads to the instrument precision so prices align in a column', () => {
      expect(formatPrice(80_000, 0.25)).toBe('20000.00')
      expect(formatPrice(80_002, 0.25)).toBe('20000.50')
      expect(formatPrice(80_000, 1)).toBe('80000')
    })
  })
})
