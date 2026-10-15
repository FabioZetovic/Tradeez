import { describe, expect, it } from 'vitest'
import type { Setup, SetupLayer } from '../api/setups'
import type { DomainBar } from '../chart/types'
import type { Projection } from '../drawing/types'
import { renderSetups } from './render'

/** Five-minute bars ten pixels apart; a tick is one pixel, higher prices higher up. */
const STEP = 300
const BARS: DomainBar[] = [0, 300, 600, 900, 1200].map((time) => ({
  time,
  open: 100,
  high: 100,
  low: 100,
  close: 100,
  volume: 1,
}))

const projection: Projection = {
  timeToX: (time) => (time % STEP === 0 && time <= 1200 ? (time / STEP) * 10 : null),
  priceToY: (ticks) => 200 - ticks,
  xToTime: () => null,
  yToPrice: () => null,
}

/** A context that records what was painted. */
function recorder() {
  const calls: Array<{ op: string; args: unknown[]; fill?: unknown; stroke?: unknown }> = []
  const state: Record<string, unknown> = {}
  const record = (op: string) => (...args: unknown[]) =>
    calls.push({ op, args, fill: state.fillStyle, stroke: state.strokeStyle })
  const ctx = new Proxy(state, {
    get: (target, key: string) => {
      if (key === 'measureText') return (text: string) => ({ width: text.length * 2 })
      if (key in target) return target[key]
      return record(key)
    },
    set: (target, key: string, value) => {
      target[key] = value
      return true
    },
  }) as unknown as CanvasRenderingContext2D
  return { ctx, calls }
}

const GAP: Setup = {
  detector: 'FVG',
  shape: 'ZONE',
  direction: 'BULLISH',
  start: 0,
  anchor: 300,
  end: 600,
  confirmedAt: 900,
  lowTicks: 101,
  highTicks: 120,
}

function render(setups: Setup[], shown: string[] = ['FVG']) {
  const layer: SetupLayer = { rules: [], setups, rolls: [] }
  const { ctx, calls } = recorder()
  renderSetups(ctx, layer, BARS, projection, {
    width: 300,
    height: 300,
    pane: { width: 240, height: 250 },
    shown: new Set(shown),
    step: STEP,
  })
  return calls
}

describe('painting detected setups', () => {
  it('boxes a gap over its own three candles, from the first one\'s left edge to the last one\'s right', () => {
    const calls = render([GAP])
    const box = calls.find((c) => c.op === 'fillRect')
    // Candles at x = 0, 10, 20, five pixels either side; prices 120 down to 101.
    expect(box?.args).toEqual([-5, 80, 30, 19])
    expect(box?.fill).toBe('rgba(63, 185, 80, 0.16)')
  })

  it('dashes the consequent encroachment through the middle, in half-ticks', () => {
    const calls = render([GAP])
    const ce = calls.filter((c) => c.op === 'lineTo')
    // (101 + 120) / 2 = 110.5 ticks: y = 89.5, on the pixel grid at 90.5.
    expect(ce).toHaveLength(1)
    expect(ce[0].args).toEqual([25, 90.5])
    expect(calls.find((c) => c.op === 'setLineDash')?.args[0]).toEqual([3, 3])
  })

  it('colours a bearish gap red, and names the rule where it fits', () => {
    const calls = render([{ ...GAP, direction: 'BEARISH' }])
    expect(calls.find((c) => c.op === 'fillRect')?.fill).toBe('rgba(248, 81, 73, 0.16)')
    expect(calls.find((c) => c.op === 'fillText')?.args.slice(0, 1)).toEqual(['FVG'])
  })

  it('draws an opening range gap on to the newest candle, with its quarters', () => {
    const org: Setup = { ...GAP, detector: 'ORG', start: 0, anchor: 300, end: 300, lowTicks: 100, highTicks: 120 }
    const lines = render([org], ['ORG']).filter((c) => c.op === 'lineTo')
    // CE at 110, quarters at 105 and 115 — all to the right edge of the newest candle (x = 45).
    expect(lines.map((c) => c.args)).toEqual([
      [45, 90.5],
      [45, 95.5],
      [45, 85.5],
    ])
  })

  it('paints only the rules switched on', () => {
    expect(render([GAP], []).filter((c) => c.op === 'fillRect')).toHaveLength(0)
  })

  it('draws a level as a line over its span, and a point as a mark under a low', () => {
    const level: Setup = { ...GAP, detector: 'LVL', shape: 'LEVEL', direction: 'NEUTRAL', lowTicks: 150, highTicks: 150 }
    const point: Setup = { ...GAP, detector: 'PT', shape: 'POINT', anchor: 600, lowTicks: 90, highTicks: 90 }
    const calls = render([level, point], ['LVL', 'PT'])
    expect(calls.filter((c) => c.op === 'lineTo')[0].args).toEqual([25, 50.5])
    // The mark's tip sits four pixels under the low (y = 110), at the middle of its candle.
    expect(calls.find((c) => c.op === 'moveTo' && c.fill === 'rgba(63, 185, 80, 0.9)')?.args).toEqual([20, 114])
  })
})
