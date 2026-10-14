import { describe, expect, it } from 'vitest'
import { LabelSpace } from './labelSpace'

describe('LabelSpace (BUG-048)', () => {
  it('keeps a label where it asked to be when there is room', () => {
    const space = new LabelSpace()
    expect(space.place(100, 200, 80)).toBe(200)
    // Far enough away, side by side or one above the other: both fit.
    expect(space.place(300, 200, 80)).toBe(200)
    expect(space.place(100, 230, 80)).toBe(230)
  })

  it('moves a label that would overlap up a line at a time, then gives up', () => {
    const space = new LabelSpace()
    expect(space.place(100, 200, 80)).toBe(200)
    // The same spot again: one line up, then two.
    expect(space.place(100, 200, 80)).toBe(188)
    expect(space.place(100, 200, 80)).toBe(176)
    // Allowed no moves, it has nowhere to go.
    expect(space.place(100, 200, 80, 0)).toBeNull()
    // Four pixels lower still overlaps the first: it climbs past every line already taken.
    expect(space.place(100, 204, 80, 4)).toBe(156)
  })
})
