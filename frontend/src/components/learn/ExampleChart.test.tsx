import { render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ExampleChart } from './ExampleChart'
import { FakeChartAdapter } from '@/test/fakeChartAdapter'
import { recorder } from '@/test/canvasRecorder'
import type { Example } from '@/lib/api/lessons'

const T0 = 1_710_338_400
const bars = (base: number) =>
  Array.from({ length: 10 }, (_, i) => ({
    time: T0 + i * 300,
    open: base,
    high: base + 4,
    low: base - 4,
    close: base + 2,
    volume: 10,
  }))

const EXAMPLE: Example = {
  detector: 'SMT',
  label: 'Bearish SMT divergence',
  explanation: ['This market made a higher high and ES did not.'],
  timeframe: '5m',
  bars: bars(80_000),
  setup: {
    detector: 'SMT',
    shape: 'POINT',
    direction: 'BEARISH',
    start: T0,
    anchor: T0 + 1500,
    end: T0 + 1800,
    confirmedAt: T0 + 2100,
    lowTicks: 80_004,
    highTicks: 80_004,
    midHalfTicks: 160_008,
  },
  rule: { detector: 'SMT', version: 'smt-1.0.0', conceptId: 'ICT-66', parameters: {}, methodologySource: 'ICT' },
  companion: { instrument: 'ES', tickSize: 0.25, bars: bars(23_000) },
}

describe('ExampleChart (DEC-075)', () => {
  it("shows an SMT example with ES's candles under it", async () => {
    const adapters: FakeChartAdapter[] = []
    render(
      <ExampleChart
        example={EXAMPLE}
        tickSize={0.25}
        exchangeTimezone="America/Chicago"
        adapterFactory={() => {
          const adapter = new FakeChartAdapter()
          adapters.push(adapter)
          return adapter
        }}
      />,
    )
    expect(screen.getByRole('figure', { name: 'ES over the same minutes' })).toBeInTheDocument()
    await waitFor(() => expect(adapters.map((a) => a.bars[0]?.open)).toEqual([80_000, 23_000]))
  })

  it('bands the hit faintly under the candles, from its start to the candle it is known by (T37)', async () => {
    const adapter = new FakeChartAdapter()
    render(
      <ExampleChart
        example={{ ...EXAMPLE, companion: undefined }}
        tickSize={0.25}
        exchangeTimezone="America/Chicago"
        adapterFactory={() => adapter}
      />,
    )
    await waitFor(() => expect(adapter.layers.some((layer) => layer.zOrder === 'bottom')).toBe(true))
    const { ctx, calls } = recorder()
    for (const layer of adapter.layers) if (layer.zOrder === 'bottom') layer.paint(ctx, { width: 800, height: 400 })
    // The fake puts a candle's centre at x = t - 1000; the band starts at its left edge, half a candle (150) before,
    // and runs to the end of the 5-minute candle at end.
    expect(calls.filter((c) => c.op === 'fillRect').map((c) => c.args)).toEqual([[T0 - 1150, 0, 2100, 400]])
  })

  it('shows one chart for a rule that reads one market', () => {
    render(
      <ExampleChart
        example={{ ...EXAMPLE, companion: undefined }}
        tickSize={0.25}
        exchangeTimezone="America/Chicago"
        adapterFactory={() => new FakeChartAdapter()}
      />,
    )
    expect(screen.queryByRole('figure', { name: 'ES over the same minutes' })).not.toBeInTheDocument()
  })
})
