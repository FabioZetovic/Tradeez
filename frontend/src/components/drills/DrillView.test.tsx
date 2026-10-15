import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { DrillView } from './DrillView'
import { FakeChartAdapter } from '@/test/fakeChartAdapter'
import type { DrillKindView, DrillResponse, ResultView } from '@/lib/api/drills'

const T0 = 1_710_338_400
const BARS = Array.from({ length: 30 }, (_, i) => ({
  time: T0 + i * 300,
  open: 80_000,
  high: 80_010,
  low: 79_990,
  close: 80_005,
  volume: 10,
}))

const KINDS: DrillKindView[] = [
  {
    kind: 'FVG_IDENTIFICATION',
    mode: 'IDENTIFY',
    title: 'Fair value gaps',
    conceptId: 'ICT-20',
    detector: 'FVG',
    ruleVersion: 'fvg-1.0.0',
    instructions: 'Mark the middle candle of every fair value gap.',
  },
  {
    kind: 'BOS_CHOCH_CLASSIFICATION',
    mode: 'CLASSIFY',
    title: 'Break of structure or change of character',
    conceptId: 'ICT-06',
    detector: 'BOS_CHOCH',
    ruleVersion: 'structure-1.0.0',
    instructions: 'Is this a break of structure or a change of character?',
  },
]

const DRILL: DrillResponse = {
  id: 'drill-1',
  kind: 'FVG_IDENTIFICATION',
  mode: 'IDENTIFY',
  title: 'Fair value gaps',
  conceptId: 'ICT-20',
  instrument: 'NQ',
  tickSize: 0.25,
  exchangeTimezone: 'America/Chicago',
  timeframe: '5m',
  dataClass: 'IMPORTED_PRIVATE',
  fixture: false,
  graded: true,
  instructions: 'Mark the middle candle of every fair value gap in this chart, then check.',
  markName: 'the middle candle of a gap',
  rule: {
    detector: 'FVG',
    version: 'fvg-1.0.0',
    parameters: { minGapPercentOfAverageRange: 25, averageRangeLookback: 14 },
    definition: 'Bullish: candle 1 high below candle 3 low. At least 25% of the average range.',
    methodologySource: 'ICT three-candle fair value gap, as defined by rule fvg-1.0.0',
  },
  bars: BARS,
}

const FIXTURE: DrillResponse = {
  ...DRILL,
  instrument: 'DEMO-NQ',
  dataClass: 'SIMULATED',
  disclaimer: 'SIMULATED DATA — NOT MARKET HISTORY',
  fixture: true,
  graded: false,
  fixtureNotice: 'Developer fixture — not a graded exercise. These candles are simulated.',
}

/** Two gaps (middle candles 5 and 12); the learner found the first and marked candle 20 wrongly. */
const RESULT: ResultView = {
  found: 1,
  missed: 1,
  extra: 1,
  setups: [5, 12].map((i) => ({
    id: `s${i}`,
    direction: 'BULLISH' as const,
    shape: 'ZONE' as const,
    startTime: T0 + (i - 1) * 300,
    anchorTime: T0 + i * 300,
    endTime: T0 + (i + 1) * 300,
    zoneLowTicks: 80_004,
    zoneHighTicks: 80_014,
    found: i === 5,
    label: 'Bullish gap',
    explanation: [`Candle 1's high is below candle 3's low (gap ${i}).`, 'Big enough to count.'],
  })),
  extraMarks: [T0 + 20 * 300],
  excusedMarks: [],
}

const CLASSIFY: DrillResponse = {
  ...DRILL,
  id: 'drill-2',
  kind: 'BOS_CHOCH_CLASSIFICATION',
  mode: 'CLASSIFY',
  title: 'Break of structure or change of character',
  markName: undefined,
  rule: { ...DRILL.rule, detector: 'BOS_CHOCH', version: 'structure-1.0.0' },
  subject: { side: 'LOW', fromTime: T0 + 10 * 300, time: T0 + 25 * 300, priceTicks: 79_990 },
  options: [
    { value: 'BOS', label: 'Break of structure (BOS)' },
    { value: 'CHOCH', label: 'Change of character (CHoCH)' },
  ],
}

const NAMED = (choice: string): DrillResponse => ({
  ...CLASSIFY,
  choice,
  result: {
    answer: 'CHOCH',
    correct: choice === 'CHOCH',
    setups: [
      {
        id: 'b1',
        direction: 'BEARISH',
        kind: 'CHOCH',
        shape: 'LEVEL',
        startTime: T0 + 10 * 300,
        anchorTime: T0 + 25 * 300,
        endTime: T0 + 25 * 300,
        zoneLowTicks: 79_990,
        zoneHighTicks: 79_990,
        label: 'Change of character (CHoCH)',
        explanation: ['Structure was bullish.', 'Against the trend: a change of character (CHoCH).'],
      },
    ],
  },
})

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

function server({
  drill = DRILL,
  answer = (body: { marks?: number[]; choice?: string }) =>
    body.choice ? { body: NAMED(body.choice) } : { body: { ...drill, marks: body.marks, result: RESULT } },
}: {
  drill?: DrillResponse
  answer?: (body: { marks?: number[]; choice?: string }) => { status?: number; body: unknown }
} = {}) {
  return (call: Call) => {
    if (call.method === 'GET' && call.url.endsWith('/api/drills/kinds')) return { body: KINDS }
    if (call.method === 'POST' && call.url.endsWith('/api/drills')) {
      const kind = (call.body as { kind?: string }).kind
      return { status: 201, body: kind === 'BOS_CHOCH_CLASSIFICATION' ? CLASSIFY : drill }
    }
    if (call.method === 'POST' && call.url.endsWith('/answer')) {
      return answer(call.body as { marks?: number[]; choice?: string })
    }
    return { status: 404, body: { message: 'not found' } }
  }
}

/** Clicks the candle at `index`. The fake projection puts time t at x = t - 1000. */
function clickCandle(index: number) {
  const frame = screen.getByTestId('drill-overlay').parentElement!
  fireEvent.click(frame, { clientX: BARS[index].time - 1000, clientY: 100 })
}

async function startDrill(kind?: string) {
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
  // The list of drills arrives first; nothing can start before it.
  await screen.findByRole('option', { name: 'Fair value gaps' })
  if (kind) await user.selectOptions(screen.getByLabelText('Drill'), kind)
  await user.click(screen.getByRole('button', { name: /start a drill/i }))
  await screen.findByTestId('drill-overlay')
  // The chart hands the overlay its adapter in a second render. Wait until the overlay has drawn
  // with it; a person cannot click before that either.
  await waitFor(() => expect(adapters[0]?.getProjection).toHaveBeenCalled())
  return user
}

const started = () => calls.find((c) => c.method === 'POST' && c.url.endsWith('/api/drills'))

afterEach(() => vi.restoreAllMocks())

describe('Drills — spot (identification)', () => {
  beforeEach(() => mockApi(server()))

  it('offers every drill from the server, grouped by mode, and starts the one chosen on real NQ', async () => {
    await startDrill()
    const groups = screen.getAllByRole('group')
    expect(within(groups[0]).getByRole('option', { name: 'Fair value gaps' })).toBeInTheDocument()
    expect(within(groups[1]).getByRole('option', { name: /change of character/ })).toBeInTheDocument()
    expect(started()?.body).toEqual({ kind: 'FVG_IDENTIFICATION', instrument: 'NQ', timeframe: '5m' })
  })

  it('names the versioned rule it is graded against (DEC-026)', async () => {
    await startDrill()
    expect(screen.getByTestId('graded')).toHaveTextContent('Graded against fvg-1.0.0')
    expect(screen.getByText('The rule (fvg-1.0.0)')).toBeInTheDocument()
    expect(screen.getByText(/not proof that a pattern matters/)).toBeInTheDocument()
    expect(screen.queryByTestId('fixture-notice')).not.toBeInTheDocument()
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

  it("sends exactly the marked candles, then grades them and shows the server's explanations", async () => {
    const user = await startDrill()
    clickCandle(20)
    clickCandle(5)
    await user.click(screen.getByRole('button', { name: /check answer/i }))

    const answer = calls.find((c) => c.url.endsWith('/answer'))
    expect(answer?.body).toEqual({ marks: [BARS[5].time, BARS[20].time] })

    expect(await screen.findByTestId('drill-summary')).toHaveTextContent('Found 1 of 2 · 1 extra mark')
    expect(screen.getByTestId('drill-grade')).toHaveTextContent('Recall 50% · precision 50%, against fvg-1.0.0.')
    const gaps = screen.getAllByTestId('drill-gap')
    expect(gaps).toHaveLength(2)
    expect(within(gaps[0]).getByText('found')).toBeInTheDocument()
    expect(within(gaps[1]).getByText('missed')).toBeInTheDocument()
    expect(gaps[0]).toHaveTextContent('Bullish gap')
    expect(gaps[0]).toHaveTextContent("Candle 1's high is below candle 3's low (gap 5).")
    expect(screen.getByTestId('drill-extra')).toHaveTextContent('Marked, but not the middle candle of a gap under this rule')
  })

  it('says which marks counted neither way, and why', async () => {
    mockApi(
      server({
        answer: (body) => ({
          body: { ...DRILL, marks: body.marks, result: { ...RESULT, extra: 0, extraMarks: [], excusedMarks: [T0] } },
        }),
      }),
    )
    const user = await startDrill()
    await user.click(screen.getByRole('button', { name: /check answer/i }))
    expect(await screen.findByTestId('drill-excused')).toHaveTextContent(
      /Not counted either way: .* the rule needs candles outside this chart to judge it/,
    )
    expect(screen.queryByTestId('drill-extra')).not.toBeInTheDocument()
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

describe('Drills — name (classification)', () => {
  beforeEach(() => mockApi(server()))

  it('offers the names, takes no marks, and answers with the one chosen', async () => {
    const user = await startDrill('BOS_CHOCH_CLASSIFICATION')
    expect(started()?.body).toMatchObject({ kind: 'BOS_CHOCH_CLASSIFICATION' })
    expect(screen.queryByTestId('mark-count')).not.toBeInTheDocument()
    expect(screen.getByText('The marked low is…')).toBeInTheDocument()

    // The chart is not a place to mark: the question is about the structure already drawn.
    expect(screen.getByTestId('drill-overlay')).toHaveStyle({ cursor: 'default' })
    clickCandle(5)
    await user.click(screen.getByRole('button', { name: 'Break of structure (BOS)' }))
    expect(calls.find((c) => c.url.endsWith('/answer'))?.body).toEqual({ choice: 'BOS' })

    expect(await screen.findByTestId('drill-summary')).toHaveTextContent(
      'Not quite — the rule says Change of character (CHoCH); you chose Break of structure (BOS)',
    )
    expect(screen.getByTestId('drill-gap')).toHaveTextContent('Against the trend: a change of character (CHoCH).')
    expect(screen.getByTestId('drill-grade')).toHaveTextContent('Graded against structure-1.0.0.')
    expect(screen.queryByRole('button', { name: 'Change of character (CHoCH)' })).not.toBeInTheDocument()
  })

  it('says so when the name is right', async () => {
    const user = await startDrill('BOS_CHOCH_CLASSIFICATION')
    await user.click(screen.getByRole('button', { name: 'Change of character (CHoCH)' }))
    expect(await screen.findByTestId('drill-summary')).toHaveTextContent('Correct — Change of character (CHoCH)')
  })
})

describe('Drills — fixtures and refusals', () => {
  it('says it is a developer fixture before anything else when the market is simulated (DEC-034)', async () => {
    mockApi(server({ drill: FIXTURE }))
    const user = await startDrill()
    expect(screen.getByTestId('fixture-notice')).toHaveTextContent(/not a graded exercise/)
    expect(screen.getAllByRole('note')[0]).toHaveTextContent(/SIMULATED DATA/)
    expect(screen.queryByTestId('graded')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /check answer/i }))
    expect(await screen.findByTestId('drill-grade')).toHaveTextContent('Feedback on this chart, not a grade.')
  })

  it("shows the server's reason when a drill cannot be answered", async () => {
    mockApi(server({ answer: () => ({ status: 409, body: { message: 'This drill has already been answered.' } }) }))
    const user = await startDrill()
    await user.click(screen.getByRole('button', { name: /check answer/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent('already been answered')
  })

  it('asks for the chosen timeframe', async () => {
    mockApi(server())
    const user = userEvent.setup()
    render(<DrillView adapterFactory={() => new FakeChartAdapter()} />)
    await screen.findByRole('option', { name: 'Fair value gaps' })
    await user.click(screen.getByRole('button', { name: '1m' }))
    await user.click(screen.getByRole('button', { name: /start a drill/i }))
    await waitFor(() => expect(started()?.body).toMatchObject({ timeframe: '1m' }))
  })

  it('says so when the list of drills cannot be loaded', async () => {
    mockApi(() => ({ status: 500, body: { message: 'down' } }))
    render(<DrillView adapterFactory={() => new FakeChartAdapter()} />)
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load the list of drills')
    expect(screen.getByRole('button', { name: /start a drill/i })).toBeDisabled()
  })
})
