import { describe, expect, it } from 'vitest'
import type { ClockLayer } from '../api/clock'
import type { DomainBar } from '../chart/types'
import type { Projection } from '../drawing/types'
import type { ClockLayerKey } from './layers'
import { killzoneColor } from './layers'
import { renderClock, timeToX } from './render'

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

const LAYER: ClockLayer = {
  rules: { sessions: 'sessions-1.0.0', opens: 'opens-1.0.0', levels: 'levels-1.0.0' },
  windows: [
    { kind: 'TRADING_DAY', label: 'Tue', from: 0, to: 3900 },
    { kind: 'KILLZONE', label: 'London', from: 0, to: 600 },
    { kind: 'MACRO', label: 'Macro', from: 300, to: 450 },
  ],
  lines: [{ kind: 'PREVIOUS_DAY', label: 'PDH', from: 0, to: 3900, priceTicks: 120 }],
}

function render(shown: ClockLayerKey[]) {
  const { ctx, calls } = recorder()
  renderClock(ctx, LAYER, BARS, projection, {
    width: 300,
    height: 200,
    pane: { width: 240, height: 170 },
    shown: new Set(shown),
    step: STEP,
    tickSize: 0.25,
  })
  return calls
}

describe('painting the clock', () => {
  it('shades a killzone over its own bars, with its name in the strip', () => {
    const calls = render(['killzones'])
    const fills = calls.filter((c) => c.op === 'fillRect')
    // Full height, then the strip: the 00:00 and 00:05 bars, from the first one's left edge.
    expect(fills.map((c) => c.args)).toEqual([
      [-5, 0, 20, 170],
      [-5, 0, 20, 14],
    ])
    expect(fills[0].fill).toContain('rgba(88, 166, 255')
    expect(calls.find((c) => c.op === 'fillText')?.args[0]).toBe('London')
  })

  it('paints only what is switched on', () => {
    const calls = render(['previousDay'])
    expect(calls.filter((c) => c.op === 'fillRect')).toHaveLength(0)
    const line = calls.filter((c) => c.op === 'lineTo')
    expect(line).toHaveLength(1)
    expect(line[0].args[1]).toBe(80.5) // priceToY(120), on the pixel grid
    expect(calls.find((c) => c.op === 'fillText')?.args[0]).toBe('PDH 30.00')
  })

  it('keeps to the plotting area, off the price and time scales', () => {
    const calls = render(['days'])
    expect(calls.find((c) => c.op === 'rect')?.args).toEqual([0, 0, 240, 170])
    expect(calls.find((c) => c.op === 'clip')).toBeDefined()
  })

  it('clears and stops when there is no layer', () => {
    const { ctx, calls } = recorder()
    renderClock(ctx, null, BARS, projection, {
      width: 300,
      height: 200,
      pane: { width: 240, height: 170 },
      shown: new Set(['killzones']),
      step: STEP,
      tickSize: 0.25,
    })
    expect(calls.map((c) => c.op)).toEqual(['clearRect'])
  })

  it('colours a session level like its killzone', () => {
    expect(killzoneColor('London H')).toBe(killzoneColor('London'))
    expect(killzoneColor('NY AM L')).toBe(killzoneColor('NY AM'))
    expect(killzoneColor('NY PM H')).not.toBe(killzoneColor('NY AM H'))
  })
})
