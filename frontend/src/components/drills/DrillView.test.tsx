import { StrictMode } from 'react'
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router'
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
  {
    kind: 'LIQUIDITY_FIRST_PREDICTION',
    mode: 'PREDICT',
    title: 'Which liquidity is taken first',
    conceptId: 'ICT-16',
    detector: 'LIQUIDITY',
    ruleVersion: 'liquidity-1.0.0',
    instructions: 'The chart stops here. Which does price take first?',
  },
  {
    kind: 'OPEN_TRADE_MANAGEMENT',
    mode: 'MANAGE',
    title: 'Manage an open trade',
    conceptId: 'ICT-82',
    detector: 'MANAGE',
    ruleVersion: 'manage-1.0.0',
    instructions: 'A trade is open and in profit. Hold it, move its stop to the entry, or take half off?',
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

/** The chart stops after candle 29; above rests buy-side at 80,030, below sell-side at 79,980. */
const PREDICTION: DrillResponse = {
  ...DRILL,
  id: 'drill-3',
  kind: 'LIQUIDITY_FIRST_PREDICTION',
  mode: 'PREDICT',
  title: 'Which liquidity is taken first',
  conceptId: 'ICT-16',
  markName: undefined,
  instructions: 'The chart stops here. Which does price take first?',
  rule: { ...DRILL.rule, detector: 'LIQUIDITY', version: 'liquidity-1.0.0' },
  question: {
    version: 'predict-1.0.0',
    definition: 'predict-1.0.0. The targets: the nearest resting buy-side and sell-side either side of the close.',
    closeTicks: 80_005,
    targets: [
      {
        value: 'BSL',
        role: 'ERL',
        shape: 'LEVEL',
        side: 'ABOVE',
        fromTime: T0 + 20 * 300,
        lowTicks: 80_030,
        highTicks: 80_030,
        distanceTicks: 25,
        obstacleSwings: 2,
        obstacleGaps: 1,
        label: 'Buy-side at 20007.50',
      },
      {
        value: 'SSL',
        role: 'ERL',
        shape: 'LEVEL',
        side: 'BELOW',
        fromTime: T0 + 22 * 300,
        lowTicks: 79_980,
        highTicks: 79_980,
        distanceTicks: 25,
        obstacleSwings: 0,
        obstacleGaps: 0,
        label: 'Sell-side at 19995.00',
      },
    ],
    context: [
      {
        what: 'IRL_BULLISH_GAP',
        shape: 'ZONE',
        fromTime: T0 + 15 * 300,
        lowTicks: 79_990,
        highTicks: 79_995,
        label: 'IRL · bullish gap',
      },
    ],
  },
  options: [
    { value: 'BSL', label: 'Buy-side at 20007.50' },
    { value: 'SSL', label: 'Sell-side at 19995.00' },
  ],
}

/** The hidden future: the second candle after the chart takes the sell-side. */
const REVEALED = [30, 31, 32].map((i) => ({
  time: T0 + i * 300,
  open: 80_000,
  high: 80_010,
  low: i === 31 ? 79_975 : 79_990,
  close: 80_000,
  volume: 10,
}))

const PREDICTED = (choice: string): DrillResponse => ({
  ...PREDICTION,
  choice,
  revealed: REVEALED,
  result: {
    answer: 'SSL',
    correct: choice === 'SSL',
    setups: [],
    outcomeTime: T0 + 31 * 300,
    candlesAfter: 2,
    explanation: [
      'Price took the sell-side at 19995.00 first — 2 candles after the chart ended.',
      'The buy-side at 20007.50, 25 ticks from the last close, had not been taken by then.',
    ],
  },
})

/** A long from 80,000, stop 79,992, target 80,024: open at +1.13R when the chart stops (T26). */
const MANAGEMENT: DrillResponse = {
  ...PREDICTION,
  id: 'drill-4',
  kind: 'OPEN_TRADE_MANAGEMENT',
  mode: 'MANAGE',
  title: 'Manage an open trade',
  conceptId: 'ICT-82',
  instructions: 'A trade is open and in profit. Hold it, move its stop to the entry, or take half off?',
  rule: { ...DRILL.rule, detector: 'MANAGE', version: 'manage-1.0.0', definition: 'A trade opened at a candle close.' },
  question: {
    version: 'manage-1.0.0',
    definition: 'A trade opened at a candle close.',
    closeTicks: 80_009,
    targets: [],
    context: [
      { what: 'TRADE_TARGET', shape: 'LEVEL', fromTime: T0 + 15 * 300, lowTicks: 80_024, highTicks: 80_024, label: 'Target 20006.00' },
      { what: 'TRADE_ENTRY', shape: 'LEVEL', fromTime: T0 + 15 * 300, lowTicks: 80_000, highTicks: 80_000, label: 'Long from 20000.00 · now +1.13R' },
      { what: 'TRADE_STOP', shape: 'LEVEL', fromTime: T0 + 15 * 300, lowTicks: 79_992, highTicks: 79_992, label: 'Stop 19998.00' },
    ],
  },
  options: [
    { value: 'HOLD', label: 'Hold — leave the stop and the target' },
    { value: 'BREAKEVEN', label: 'Stop to breakeven — move the stop to the entry' },
    { value: 'HALF', label: 'Half off — close half at the next open, hold the rest' },
  ],
}

const MANAGED = (choice: string): DrillResponse => ({
  ...MANAGEMENT,
  choice,
  revealed: REVEALED,
  result: {
    answer: 'HOLD,BREAKEVEN',
    correct: choice !== 'HALF',
    setups: [],
    outcomeTime: T0 + 31 * 300,
    candlesAfter: 2,
    explanation: [
      'When the chart stopped, the long stood at +1.13R: entry 20000.00, stop 19998.00, target 20006.00.',
      'Hold: the target filled at 20006.00, 2 candles later — +3.00R.',
      'Stop to breakeven: the target filled at 20006.00, 2 candles later — +3.00R.',
      'Half off at the next open, 20002.50 (+1.25R on that half), the rest held: +2.13R in all.',
      'One trade shows which choice paid here, not which pays.',
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
      return {
        status: 201,
        body:
          kind === 'BOS_CHOCH_CLASSIFICATION'
            ? CLASSIFY
            : kind === 'LIQUIDITY_FIRST_PREDICTION'
              ? PREDICTION
              : kind === 'OPEN_TRADE_MANAGEMENT'
                ? MANAGEMENT
                : drill,
      }
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

/** The drill's screen, as its card opens it (T43). */
function renderDrill(kind: string, adapters: FakeChartAdapter[] = []) {
  return render(
    <MemoryRouter>
      <DrillView
        kind={kind}
        adapterFactory={() => {
          const adapter = new FakeChartAdapter()
          adapters.push(adapter)
          return adapter
        }}
      />
    </MemoryRouter>,
  )
}

async function startDrill(kind = 'FVG_IDENTIFICATION') {
  const user = userEvent.setup()
  const adapters: FakeChartAdapter[] = []
  renderDrill(kind, adapters)
  await screen.findByTestId('drill-overlay')
  // The chart hands the overlay its adapter in a second render. Wait until the overlay has its layer on that chart
  // (T37); a person cannot click before the chart is drawn either.
  await waitFor(() => expect(adapters[0]?.layers.some((layer) => layer.zOrder === 'top')).toBe(true))
  return user
}

const started = () => calls.find((c) => c.method === 'POST' && c.url.endsWith('/api/drills'))

afterEach(() => vi.restoreAllMocks())

describe('Drills — spot (identification)', () => {
  beforeEach(() => mockApi(server()))

  it('asks for its drill on arrival — once, on real NQ, leaving the timeframe to the server (T43)', async () => {
    await startDrill()
    expect(started()?.body).toEqual({ kind: 'FVG_IDENTIFICATION', instrument: 'NQ' })
    expect(calls.filter((c) => c.method === 'POST' && c.url.endsWith('/api/drills'))).toHaveLength(1)
    expect(screen.getByTestId('drill-mode')).toHaveTextContent('Spot')
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Fair value gaps')
    // The timeframe the server drew, said.
    expect(screen.getByTestId('drill-chart')).toHaveTextContent('NQ · 5m')
    expect(screen.getByText(/Mark the middle candle of every fair value gap in this chart/)).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: 'All drills' })[0]).toHaveAttribute('href', '/drills')
    // No list to choose from, no timeframe to pick.
    expect(screen.queryByRole('combobox')).toBeNull()
    expect(screen.queryByRole('button', { name: '1m' })).toBeNull()
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
    expect(screen.getByTestId('mark-count')).toHaveTextContent('0 marked')

    clickCandle(5)
    clickCandle(20)
    expect(screen.getByTestId('mark-count')).toHaveTextContent('2 marked')

    clickCandle(20)
    expect(screen.getByTestId('mark-count')).toHaveTextContent('1 marked')
  })

  it('ignores a click that was really a drag of the chart', async () => {
    await startDrill()
    const frame = screen.getByTestId('drill-overlay').parentElement!
    const x = BARS[5].time - 1000
    fireEvent.pointerDown(frame, { clientX: x - 40, clientY: 100 })
    fireEvent.click(frame, { clientX: x, clientY: 100 })
    expect(screen.getByTestId('mark-count')).toHaveTextContent('0 marked')
  })

  it("sends exactly the marked candles, then grades them and shows the server's explanations", async () => {
    const user = await startDrill()
    clickCandle(20)
    clickCandle(5)
    await user.click(screen.getByRole('button', { name: /check answer/i }))

    const answer = calls.find((c) => c.url.endsWith('/answer'))
    expect(answer?.body).toEqual({ marks: [BARS[5].time, BARS[20].time] })

    expect(await screen.findByTestId('drill-summary')).toHaveTextContent('1/2 found · 1 extra')
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
    expect(screen.getAllByRole('link', { name: 'All drills' })).toHaveLength(2)
    await user.click(screen.getByRole('button', { name: /next drill/i }))
    await waitFor(() => expect(screen.queryByTestId('drill-summary')).not.toBeInTheDocument())
    expect(screen.getByTestId('mark-count')).toHaveTextContent('0 marked')
    const asked = calls.filter((c) => c.method === 'POST' && c.url.endsWith('/api/drills'))
    expect(asked.map((c) => c.body)).toEqual([
      { kind: 'FVG_IDENTIFICATION', instrument: 'NQ' },
      { kind: 'FVG_IDENTIFICATION', instrument: 'NQ' },
    ])
  })

  it('clears marks before checking', async () => {
    const user = await startDrill()
    clickCandle(3)
    await user.click(screen.getByRole('button', { name: 'Clear' }))
    expect(screen.getByTestId('mark-count')).toHaveTextContent('0 marked')
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

describe('Drills — manage (T26)', () => {
  beforeEach(() => mockApi(server({ answer: (body) => ({ body: MANAGED(body.choice ?? '') }) })))

  it('draws the open trade and offers three choices — no targets, no names, nothing after the moment', async () => {
    await startDrill('OPEN_TRADE_MANAGEMENT')
    expect(started()?.body).toMatchObject({ kind: 'OPEN_TRADE_MANAGEMENT' })
    expect(screen.getByTestId('graded')).toHaveTextContent('Graded by what price did · manage-1.0.0')
    expect(screen.getByText('The trade is open and in profit. What do you do with it?')).toBeInTheDocument()
    const answer = screen.getByRole('region', { name: 'Answer' })
    expect(within(answer).getAllByRole('button').map((b) => b.textContent)).toEqual([
      'Hold — leave the stop and the target',
      'Stop to breakeven — move the stop to the entry',
      'Half off — close half at the next open, hold the rest',
    ])
    expect(screen.queryByTestId('target-facts')).toBeNull()
    expect(screen.queryByTestId('drill-summary')).toBeNull()
  })

  it('a choice among those that paid most is right, and the hidden candles come with every choice told', async () => {
    const user = await startDrill('OPEN_TRADE_MANAGEMENT')
    await user.click(screen.getByRole('button', { name: 'Stop to breakeven — move the stop to the entry' }))
    expect(calls.find((c) => c.url.endsWith('/answer'))?.body).toEqual({ choice: 'BREAKEVEN' })
    expect(await screen.findByTestId('drill-summary')).toHaveTextContent('Yes — Hold and Stop to breakeven paid most here')
    expect(screen.getByTestId('drill-grade')).toHaveTextContent('Graded by what real price did with each choice')
    expect(screen.getByText(/the rest held: \+2\.13R in all/)).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Answer' })).toBeNull()
  })

  it('a choice that paid less says which paid most, and which was chosen', async () => {
    const user = await startDrill('OPEN_TRADE_MANAGEMENT')
    await user.click(screen.getByRole('button', { name: 'Half off — close half at the next open, hold the rest' }))
    expect(await screen.findByTestId('drill-summary')).toHaveTextContent(
      'Hold and Stop to breakeven paid most here; you chose Half off',
    )
  })
})

describe('Drills — predict', () => {
  beforeEach(() =>
    mockApi(server({ answer: (body) => ({ body: PREDICTED(body.choice ?? '') }) })),
  )

  it('draws two targets with what the chart says about each — and nothing after the moment', async () => {
    await startDrill('LIQUIDITY_FIRST_PREDICTION')
    expect(started()?.body).toMatchObject({ kind: 'LIQUIDITY_FIRST_PREDICTION' })
    expect(screen.getByTestId('graded')).toHaveTextContent('Graded by what price did · predict-1.0.0')
    expect(screen.getByText('The rule (predict-1.0.0, on liquidity-1.0.0)')).toBeInTheDocument()
    expect(screen.getByText(/the nearest resting buy-side and sell-side/)).toBeInTheDocument()
    const facts = screen.getAllByTestId('target-facts')
    expect(facts[0]).toHaveTextContent('25 ticks away · on the way: 2 swing points, 1 open gap')
    expect(facts[1]).toHaveTextContent('25 ticks away · nothing on the way')
    expect(screen.queryByTestId('mark-count')).not.toBeInTheDocument()
    expect(screen.queryByTestId('drill-summary')).not.toBeInTheDocument()
  })

  it('answers with the target chosen, then shows the hidden candles and what decided it', async () => {
    const adapters: FakeChartAdapter[] = []
    const user = userEvent.setup()
    renderDrill('LIQUIDITY_FIRST_PREDICTION', adapters)
    await waitFor(() => expect(adapters.at(-1)?.bars).toHaveLength(30))

    await user.click(screen.getByRole('button', { name: 'Buy-side at 20007.50' }))
    expect(calls.find((c) => c.url.endsWith('/answer'))?.body).toEqual({ choice: 'BSL' })
    expect(await screen.findByTestId('drill-summary')).toHaveTextContent(
      'Not this time — Sell-side at 19995.00 came first; you chose Buy-side at 20007.50',
    )
    expect(screen.getByTestId('drill-grade')).toHaveTextContent('2 candles after the chart')
    expect(screen.getByText(/had not been taken by then/)).toBeInTheDocument()
    // The candles that were hidden are on the chart now, after the ones that were shown, and the
    // chart is framed afresh around all of them.
    await waitFor(() => expect(adapters.at(-1)?.bars).toHaveLength(33))
    expect(adapters.at(-1)?.bars.at(-1)?.time).toBe(T0 + 32 * 300)
    expect(adapters.at(-1)?.fitCalls).toBeGreaterThan(0)
    expect(screen.queryByRole('button', { name: 'Sell-side at 19995.00' })).not.toBeInTheDocument()
  })

  it('says so when the target chosen came first', async () => {
    const user = await startDrill('LIQUIDITY_FIRST_PREDICTION')
    await user.click(screen.getByRole('button', { name: 'Sell-side at 19995.00' }))
    expect(await screen.findByTestId('drill-summary')).toHaveTextContent('Yes — Sell-side at 19995.00 came first')
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

  it("asks for one drill, not two, when React's strict mode mounts the screen twice", async () => {
    mockApi(server())
    render(
      <StrictMode>
        <MemoryRouter>
          <DrillView kind="FVG_IDENTIFICATION" adapterFactory={() => new FakeChartAdapter()} />
        </MemoryRouter>
      </StrictMode>,
    )
    await screen.findByTestId('drill-overlay')
    expect(calls.filter((c) => c.method === 'POST' && c.url.endsWith('/api/drills'))).toHaveLength(1)
  })

  it('says so when no chart can be found, and tries again', async () => {
    let fail = true
    mockApi((call) => {
      if (call.method === 'POST' && fail) return { status: 503, body: { message: 'No real NQ data is loaded.' } }
      return server()(call)
    })
    const user = userEvent.setup()
    renderDrill('FVG_IDENTIFICATION')
    expect(await screen.findByRole('alert')).toHaveTextContent('No real NQ data is loaded.')
    fail = false
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByTestId('drill-overlay')).toBeInTheDocument()
  })
})

describe('Drills — SMT, two charts (DEC-075)', () => {
  const ES_BARS = BARS.map((b) => ({ ...b, open: 23_000, high: 23_004, low: 22_996, close: 23_002 }))
  const SMT: DrillResponse = {
    ...DRILL,
    id: 'drill-smt',
    kind: 'SMT_IDENTIFICATION',
    title: 'SMT divergence',
    conceptId: 'ICT-66',
    rule: { ...DRILL.rule, detector: 'SMT', version: 'smt-1.0.0' },
    companion: { instrument: 'ES', tickSize: 0.25, bars: ES_BARS },
  }

  it("draws ES under NQ over the same minutes, keeps it on the drill chart's stretch, and takes marks on NQ only", async () => {
    mockApi(server({ drill: SMT }))
    const adapters: FakeChartAdapter[] = []
    renderDrill('SMT_IDENTIFICATION', adapters)
    expect(await screen.findByRole('figure', { name: 'ES over the same minutes' })).toBeInTheDocument()
    await waitFor(() => expect(adapters).toHaveLength(2))
    await waitFor(() => expect(adapters[1].bars).toEqual(ES_BARS))
    expect(screen.getAllByTestId('drill-overlay')).toHaveLength(1)

    adapters[0].visibleRange = { from: T0, to: T0 + 29 * 300 }
    await waitFor(() => expect(adapters[0].viewportListeners.length).toBeGreaterThan(0))
    act(() => adapters[0].emitViewportChange())
    expect(adapters[1].shownRange).toEqual({ from: T0, to: T0 + 29 * 300 })
  })

  it('shows one chart for a drill on one market', async () => {
    mockApi(server())
    await startDrill()
    expect(screen.queryByRole('figure', { name: /over the same minutes/ })).not.toBeInTheDocument()
  })
})
