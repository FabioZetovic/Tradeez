import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { DrillView } from './DrillView'
import { FakeChartAdapter } from '@/test/fakeChartAdapter'
import type { DrillResponse, ResultView } from '@/lib/api/drills'

const T0 = 1_710_338_400
const BARS = Array.from({ length: 30 }, (_, i) => ({
  time: T0 + i * 300,
  open: 80_000,
  high: 80_010,
  low: 79_990,
  close: 80_005,
  volume: 10,
}))

const DRILL: DrillResponse = {
  id: 'drill-1',
  kind: 'FVG_IDENTIFICATION',
  instrument: 'DEMO-NQ',
  tickSize: 0.25,
  exchangeTimezone: 'America/Chicago',
  timeframe: '5m',
  dataClass: 'SIMULATED',
  disclaimer: 'SIMULATED DATA — NOT MARKET HISTORY',
  fixture: true,
  graded: false,
  fixtureNotice: 'Developer fixture — not a graded exercise. These candles are simulated.',
  instructions: 'Mark the middle candle of every fair value gap in this chart, then check.',
  rule: {
    detector: 'FVG',
    version: 'fvg-1.0.0',
    parameters: { minGapPercentOfAverageRange: 25, averageRangeLookback: 14 },
    definition: 'Bullish: candle 1 high below candle 3 low. At least 25% of the average range.',
    methodologySource: 'ICT three-candle fair value gap, as defined by rule fvg-1.0.0',
  },
  bars: BARS,
}

const evidence = (i: number) => ({
  candle1Time: T0 + (i - 1) * 300,
  candle1HighTicks: 80_004,
  candle1LowTicks: 79_996,
  candle2Time: T0 + i * 300,
  candle3Time: T0 + (i + 1) * 300,
  candle3HighTicks: 80_035,
  candle3LowTicks: 80_014,
  gapTicks: 10,
  sumRangeTicks: 112,
  lookback: 14,
  thresholdTicks: 2,
})

/** Two gaps (middle candles 5 and 12); the learner found the first and marked candle 20 wrongly. */
const RESULT: ResultView = {
  found: 1,
  missed: 1,
  extra: 1,
  setups: [5, 12].map((i) => ({
    id: `s${i}`,
    direction: 'BULLISH' as const,
    startTime: T0 + (i - 1) * 300,
    anchorTime: T0 + i * 300,
    endTime: T0 + (i + 1) * 300,
    zoneLowTicks: 80_004,
    zoneHighTicks: 80_014,
    found: i === 5,
    evidence: evidence(i),
  })),
  extraMarks: [T0 + 20 * 300],
}

interface Call {
  method: string
  url: string
  body: unknown
}

let calls: Call[] = []

function mockApi(handler: (call: Call) => { status?: number; body: unknown }) {
  calls = []
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string, init?: RequestInit) => {
      const call = {
        method: init?.method ?? 'GET',
        url,
        body: typeof init?.body === 'string' ? JSON.parse(init.body) : undefined,
      }
      calls.push(call)
      const { status = 200, body } = handler(call)
      return Promise.resolve({
        ok: status < 400,
        status,
        statusText: String(status),
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => body,
        text: async () => JSON.stringify(body),
      })
    }),
  )
}

function server(answer: { status?: number; body: unknown } = { body: { ...DRILL, marks: [], result: RESULT } }) {
  return (call: Call) => {
    if (call.method === 'POST' && call.url.endsWith('/api/drills')) return { status: 201, body: DRILL }
    if (call.method === 'POST' && call.url.endsWith('/answer')) return answer
    return { status: 404, body: { message: 'not found' } }
  }
}

/** Clicks the candle at `index`. The fake projection puts time t at x = t - 1000. */
function clickCandle(index: number) {
  const frame = screen.getByTestId('drill-overlay').parentElement!
  fireEvent.click(frame, { clientX: BARS[index].time - 1000, clientY: 100 })
}

async function startDrill() {
  const user = userEvent.setup()
  const adapters: FakeChartAdapter[] = []
  render(
    <DrillView
      adapterFactory={() => {
        const adapter = new FakeChartAdapter()
        adapters.push(adapter)
        return adapter
      }}
    />,
  )
  await user.click(screen.getByRole('button', { name: /start a drill/i }))
  await screen.findByTestId('drill-overlay')
  // The chart hands the overlay its adapter in a second render. Wait until the overlay has drawn
  // with it; a person cannot click before that either.
  await waitFor(() => expect(adapters[0]?.getProjection).toHaveBeenCalled())
  return user
}

afterEach(() => vi.restoreAllMocks())

describe('Drills — FVG identification', () => {
  beforeEach(() => mockApi(server()))

  it('says it is a developer fixture before anything else (DEC-034)', async () => {
    await startDrill()
    expect(screen.getByTestId('fixture-notice')).toHaveTextContent(/not a graded exercise/)
    expect(screen.getAllByRole('note')[0]).toHaveTextContent(/SIMULATED DATA/)
    expect(calls[0].body).toEqual({ kind: 'FVG_IDENTIFICATION', instrument: 'DEMO-NQ', timeframe: '5m' })
  })

  it('shows the versioned rule it is judged by (DEC-026)', async () => {
    await startDrill()
    expect(screen.getByText('The rule (fvg-1.0.0)')).toBeInTheDocument()
    expect(screen.getByText(/not proof that a gap matters/)).toBeInTheDocument()
  })

  it('marks candles by clicking them, and unmarks on a second click', async () => {
    await startDrill()
    expect(screen.getByTestId('mark-count')).toHaveTextContent('0 candles marked')

    clickCandle(5)
    clickCandle(20)
    expect(screen.getByTestId('mark-count')).toHaveTextContent('2 candles marked')

    clickCandle(20)
    expect(screen.getByTestId('mark-count')).toHaveTextContent('1 candle marked')
  })

  it('ignores a click that was really a drag of the chart', async () => {
    await startDrill()
    const frame = screen.getByTestId('drill-overlay').parentElement!
    const x = BARS[5].time - 1000
    fireEvent.pointerDown(frame, { clientX: x - 40, clientY: 100 })
    fireEvent.click(frame, { clientX: x, clientY: 100 })
    expect(screen.getByTestId('mark-count')).toHaveTextContent('0 candles marked')
  })

  it('sends exactly the marked candles, then explains every gap from the evidence', async () => {
    const user = await startDrill()
    clickCandle(20)
    clickCandle(5)
    await user.click(screen.getByRole('button', { name: /check answer/i }))

    const answer = calls.find((c) => c.url.endsWith('/answer'))
    expect(answer?.body).toEqual({ marks: [BARS[5].time, BARS[20].time] })

    expect(await screen.findByTestId('drill-summary')).toHaveTextContent('Found 1 of 2 · 1 extra mark')
    const gaps = screen.getAllByTestId('drill-gap')
    expect(gaps).toHaveLength(2)
    expect(within(gaps[0]).getByText('found')).toBeInTheDocument()
    expect(within(gaps[1]).getByText('missed')).toBeInTheDocument()
    expect(gaps[0]).toHaveTextContent(
      "Candle 1's high, 20001.00, is below candle 3's low, 20003.50. Neither traded in the 10 ticks",
    )
    expect(gaps[0]).toHaveTextContent('25% of the 8.0-tick average range')
    expect(screen.getByTestId('drill-extra')).toHaveTextContent(/not the middle candle of a gap/)
    expect(screen.getByText('Feedback on this chart, not a grade.')).toBeInTheDocument()
  })

  it('stops taking marks once answered, and offers the next drill', async () => {
    const user = await startDrill()
    await user.click(screen.getByRole('button', { name: /check answer/i }))
    await screen.findByTestId('drill-summary')

    expect(screen.queryByRole('button', { name: /check answer/i })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /next drill/i }))
    await waitFor(() => expect(screen.queryByTestId('drill-summary')).not.toBeInTheDocument())
    expect(screen.getByTestId('mark-count')).toHaveTextContent('0 candles marked')
    expect(calls.filter((c) => c.method === 'POST' && c.url.endsWith('/api/drills'))).toHaveLength(2)
  })

  it('clears marks before checking', async () => {
    const user = await startDrill()
    clickCandle(3)
    await user.click(screen.getByRole('button', { name: /clear marks/i }))
    expect(screen.getByTestId('mark-count')).toHaveTextContent('0 candles marked')
  })

  it('never shows the answer before it is given', async () => {
    await startDrill()
    expect(screen.queryByTestId('drill-gap')).not.toBeInTheDocument()
    expect(screen.queryByText(/found|missed/i)).not.toBeInTheDocument()
  })
})

describe('Drills — refusals', () => {
  it("shows the server's reason when a drill cannot be answered", async () => {
    mockApi(server({ status: 409, body: { message: 'This drill has already been answered.' } }))
    const user = await startDrill()
    await user.click(screen.getByRole('button', { name: /check answer/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent('already been answered')
  })

  it('asks for the chosen timeframe', async () => {
    mockApi(server())
    const user = userEvent.setup()
    render(<DrillView adapterFactory={() => new FakeChartAdapter()} />)
    await user.click(screen.getByRole('button', { name: '15m' }))
    await user.click(screen.getByRole('button', { name: /start a drill/i }))
    await waitFor(() => expect(calls[0]?.body).toMatchObject({ timeframe: '15m' }))
  })
})
