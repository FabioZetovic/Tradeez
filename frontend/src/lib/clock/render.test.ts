import { describe, expect, it } from 'vitest'
import type { ClockLayer } from '../api/clock'
import type { DomainBar } from '../chart/types'
import type { Projection } from '../drawing/types'
import type { ClockLayerKey } from './layers'
import { killzoneColor } from './layers'
import { renderClock } from './render'
import { recorder } from '@/test/canvasRecorder'

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

const LAYER: ClockLayer = {
  rules: { sessions: 'sessions-1.0.0', opens: 'opens-1.0.0', levels: 'levels-1.0.0', liquidity: 'liquidity-1.0.0' },
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

  it('T12: ends a level where its liquidity was taken, with a cross', () => {
    const taken: ClockLayer = {
      ...LAYER,
      windows: [],
      lines: [{ kind: 'PREVIOUS_DAY', label: 'PDH', from: 0, to: 3900, priceTicks: 120, takenAt: 600 }],
    }
    const { ctx, calls } = recorder()
    renderClock(ctx, taken, BARS, projection, {
      width: 300,
      height: 200,
      pane: { width: 240, height: 170 },
      shown: new Set(['previousDay']),
      step: STEP,
      tickSize: 0.25,
    })
    const ends = calls.filter((c) => c.op === 'lineTo').map((c) => c.args)
    // The level runs to 00:10 (x = 15), not to the end of the day; then the cross around that point.
    expect(ends[0]).toEqual([15, 80.5])
    expect(ends.slice(1)).toEqual([
      [18, 83.5],
      [18, 77.5],
    ])
  })

  it('paints only what is switched on', () => {
    const calls = render(['previousDay'])
    expect(calls.filter((c) => c.op === 'fillRect')).toHaveLength(0)
    const line = calls.filter((c) => c.op === 'lineTo')
    expect(line).toHaveLength(1)
    expect(line[0].args[1]).toBe(80.5) // priceToY(120), on the pixel grid
    expect(calls.find((c) => c.op === 'fillText')?.args[0]).toBe('PDH 30.00')
  })

  it('BUG-048: two labels that would overlap do not — the more important keeps its place', () => {
    // As seen on the real chart: the 09:30 open fifteen ticks under PDH, both ending at 17:00.
    const crowded: ClockLayer = {
      ...LAYER,
      windows: [],
      lines: [
        { kind: 'OPEN', label: '09:30 open', from: 0, to: 3900, priceTicks: 117 },
        { kind: 'PREVIOUS_DAY', label: 'PDH', from: 0, to: 3900, priceTicks: 120 },
      ],
    }
    const { ctx, calls } = recorder()
    renderClock(ctx, crowded, BARS, projection, {
      width: 300,
      height: 200,
      pane: { width: 240, height: 170 },
      shown: new Set(['opens', 'previousDay']),
      step: STEP,
      tickSize: 0.25,
    })
    const written = calls.filter((c) => c.op === 'fillText').map((c) => [c.args[0], c.args[2]])
    // PDH first, where it belongs (its line at y=80.5); the open a line above, not on top of it.
    expect(written[0]).toEqual(['PDH 30.00', 73.5])
    expect(written[1][0]).toBe('09:30 open 29.25')
    expect(written[1][1]).toBeLessThan(73.5 - 10)
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
