import { describe, expect, it } from 'vitest'
import { SETTLE, enter } from './motion'

describe('enter (R-5)', () => {
  it('waits a beat past the first paint and lasts long enough to be seen', () => {
    const first = enter()
    expect(first.initial).toEqual({ opacity: 0, y: 14 })
    expect(first.transition.delay).toBe(SETTLE)
    expect(SETTLE).toBeGreaterThan(0)
    expect(first.transition.duration).toBeGreaterThanOrEqual(0.5)
  })

  it('staggers a group, and caps a long list so its last item never waits long', () => {
    expect(enter(3).transition.delay).toBeCloseTo(SETTLE + 0.18)
    expect(enter(40, { step: 0.04 }).transition.delay).toBeCloseTo(SETTLE + 0.4)
  })
})
