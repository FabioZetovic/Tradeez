import { describe, expect, it } from 'vitest'
import { hitTest } from './geometry'
import {
  ceHalfTicks,
  fibLevelTicks,
  formatFibLevel,
  formatHalfTicks,
  ICT_FIB_LEVELS,
  labelWidth,
} from './ict'
import { renderDrawings } from './render'
import { DEFAULT_STYLES, type Drawing, type Projection } from './types'

/**
 * The ICT tools (T8, DEC-048), number by number. A bullish leg on NQ from 20,000.00 to 20,100.00 —
 * 80,000 to 80,400 ticks — drawn the way ICT draws it: from the low to the high.
 */
const LOW = { time: 1100, priceTicks: 80_000 }
const HIGH = { time: 1300, priceTicks: 80_400 }
const LEG = [LOW, HIGH]

/** One pixel per second, one per tick, higher prices higher on screen. */
const projection: Projection = {
  timeToX: (time) => time - 1000,
  priceToY: (ticks) => 500 - (ticks - 80_000),
  xToTime: (x) => x + 1000,
  yToPrice: (y) => 80_000 + (500 - y),
}

const drawing = (over: Partial<Drawing> & Pick<Drawing, 'kind' | 'anchors'>): Drawing => ({
  id: 'd',
  style: DEFAULT_STYLES[over.kind],
  ...over,
})

describe('ICT Fibonacci levels (ICT-43)', () => {
  it('puts 1 at the start of the leg and 0 at its end', () => {
    expect(fibLevelTicks(LEG, 1)).toBe(80_000)
    expect(fibLevelTicks(LEG, 0)).toBe(80_400)
  })

  it('puts the OTE below the high of a bullish leg, and the targets above it (ICT-42)', () => {
    expect(fibLevelTicks(LEG, 0.62)).toBeCloseTo(80_152)
    expect(fibLevelTicks(LEG, 0.705)).toBeCloseTo(80_118)
    expect(fibLevelTicks(LEG, 0.79)).toBeCloseTo(80_084)
    expect(fibLevelTicks(LEG, 0.5)).toBe(80_200) // equilibrium
    expect(fibLevelTicks(LEG, -0.27)).toBeCloseTo(80_508)
    expect(fibLevelTicks(LEG, -1)).toBe(80_800) // a symmetrical move
    expect(fibLevelTicks(LEG, -2)).toBe(81_200)
  })

  it('mirrors for a bearish leg drawn from its high to its low', () => {
    expect(fibLevelTicks([HIGH, LOW], 0.705)).toBeCloseTo(80_282)
    expect(fibLevelTicks([HIGH, LOW], -1)).toBe(79_600)
  })

  it('names each level at a price a trader could place an order at: the nearest tick', () => {
    // 0.705 of a 400-tick leg is 282 ticks below the high: 20,029.50.
    expect(formatFibLevel(fibLevelTicks(LEG, 0.705), 0.25)).toBe('20029.50')
    // 0.62 of 101 ticks lands between ticks, and is rounded to one.
    expect(formatFibLevel(80_000 + 0.38 * 101, 0.25)).toBe('20009.50')
  })

  it("is ICT's set of levels, in order from the start of the leg past its end", () => {
    expect(ICT_FIB_LEVELS.map((l) => l.ratio)).toEqual([
      1, 0.79, 0.705, 0.62, 0.5, 0, -0.27, -0.62, -1, -2,
    ])
  })
})

describe('consequent encroachment (ICT-21)', () => {
  it('is the exact middle, in half-ticks, so an odd-sized zone keeps an integer midpoint', () => {
    expect(ceHalfTicks([{ time: 0, priceTicks: 80_000 }, { time: 0, priceTicks: 80_005 }])).toBe(160_005)
    expect(formatHalfTicks(160_005, 0.25)).toBe('20000.625')
    // Between whole ticks it takes one more decimal; on one, it reads like any price.
    expect(formatHalfTicks(160_004, 0.25)).toBe('20000.50')
  })
})

describe('hit testing the ICT tools', () => {
  it('a Fibonacci is grabbed by any level, to the right edge, or by its leg', () => {
    const fib = drawing({ kind: 'fibonacci', anchors: LEG })
    // The 0.705 line is at y = 500 − 118 = 382, from x=100 to the edge.
    expect(hitTest({ x: 700, y: 382 }, [fib], null, projection, 800).type).toBe('body')
    // Between levels, inside the OTE band: not a line.
    expect(hitTest({ x: 700, y: 365 }, [fib], null, projection, 800).type).toBe('none')
    // Left of where the leg begins: nothing.
    expect(hitTest({ x: 50, y: 382 }, [fib], null, projection, 800).type).toBe('none')
    // On the leg's diagonal, half way: (200, 300).
    expect(hitTest({ x: 200, y: 300 }, [fib], null, projection, 800).type).toBe('body')
  })

  it('a zone is grabbed by its outline or its CE line, not its inside', () => {
    const zone = drawing({
      kind: 'zone',
      anchors: [
        { time: 1100, priceTicks: 80_000 },
        { time: 1300, priceTicks: 80_100 },
      ],
    })
    // CE at 80,050: y = 450.
    expect(hitTest({ x: 200, y: 450 }, [zone], null, projection, 800).type).toBe('body')
    expect(hitTest({ x: 200, y: 425 }, [zone], null, projection, 800).type).toBe('none')
    expect(hitTest({ x: 400, y: 450 }, [zone], null, projection, 800).type).toBe('none')
  })

  it('a label is grabbed by its words', () => {
    const label = drawing({ kind: 'label', anchors: [{ time: 1200, priceTicks: 80_000 }], text: 'MSS' })
    expect(hitTest({ x: 215, y: 500 }, [label], null, projection, 800).type).toBe('body')
    expect(hitTest({ x: 200 + labelWidth('MSS') + 20, y: 500 }, [label], null, projection, 800).type).toBe(
      'none',
    )
  })
})

/** A context that records the text it was asked to write. */
function recorder() {
  const texts: string[] = []
  const fills: number[][] = []
  const ctx = new Proxy({} as Record<string, unknown>, {
    get: (target, key: string) => {
      if (key === 'fillText') return (text: string) => texts.push(text)
      if (key === 'fillRect') return (...r: number[]) => fills.push(r)
      if (key === 'measureText') return (text: string) => ({ width: text.length * 6 })
      if (key in target) return target[key]
      return () => {}
    },
    set: (target, key: string, value) => {
      target[key] = value
      return true
    },
  }) as unknown as CanvasRenderingContext2D
  return { ctx, texts, fills }
}

const options = { width: 800, height: 600, selectedId: null, tickValueCents: 500, tickSize: 0.25 }

describe('drawing the ICT tools', () => {
  it('a Fibonacci shows every level with its price, and shades the OTE', () => {
    const { ctx, texts, fills } = recorder()
    renderDrawings(ctx, [drawing({ kind: 'fibonacci', anchors: LEG })], projection, options)
    // Written most important first; on a 400-pixel leg every level has room.
    expect(texts[0]).toBe('0.705  20029.50')
    expect(texts).toHaveLength(10)
    expect(texts).toEqual(expect.arrayContaining([
      '1  20000.00',
      '0.79  20021.00',
      '0.705  20029.50',
      '0.62  20038.00',
      '0.5  20050.00',
      '0  20100.00',
      '-0.27  20127.00',
      '-0.62  20162.00',
      '-1  20200.00',
      '-2  20300.00',
    ]))
    // The OTE band, 0.62 to 0.79: y 348 to 416, from the leg's start to the right edge.
    expect(fills).toContainEqual([100, 348, 700, 68])
  })

  it('BUG-048: on a short leg the crowded levels give way, and 0.705 keeps its label', () => {
    const { ctx, texts } = recorder()
    // A 40-tick leg: 0.62, 0.705 and 0.79 sit a few pixels apart.
    const short = [LOW, { time: 1300, priceTicks: 80_040 }]
    renderDrawings(ctx, [drawing({ kind: 'fibonacci', anchors: short })], projection, options)
    expect(texts[0]).toMatch(/^0\.705 /)
    expect(texts.some((t) => t.startsWith('0.62 '))).toBe(false)
    expect(texts.some((t) => t.startsWith('0.79 '))).toBe(false)
  })

  it("a zone shows its name and its CE's price, to the half-tick", () => {
    const { ctx, texts } = recorder()
    const zone = drawing({
      kind: 'zone',
      anchors: [
        { time: 1100, priceTicks: 80_000 },
        { time: 1300, priceTicks: 80_005 },
      ],
      text: 'FVG',
    })
    renderDrawings(ctx, [zone], projection, options)
    expect(texts).toEqual(['FVG', 'CE 20000.625'])
  })

  it('a label shows its words', () => {
    const { ctx, texts } = recorder()
    renderDrawings(
      ctx,
      [drawing({ kind: 'label', anchors: [{ time: 1200, priceTicks: 80_000 }], text: 'Sweep' })],
      projection,
      options,
    )
    expect(texts).toEqual(['Sweep'])
  })
})
