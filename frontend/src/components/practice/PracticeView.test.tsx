import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { PracticeView } from './PracticeView'
import { FakeChartAdapter } from '@/test/fakeChartAdapter'
import { recorder } from '@/test/canvasRecorder'
import type {
  PracticeState,
  ResultView,
  ScoreView,
  SessionResponse,
  SessionSummary,
  TradeView,
} from '@/lib/api/practice'

const ID = '11111111-2222-3333-4444-555555555555'
const CURSOR = 1_710_338_400

const BARS = [
  { time: CURSOR - 900, open: 79_990, high: 80_004, low: 79_985, close: 80_000, volume: 10 },
  { time: CURSOR - 600, open: 80_000, high: 80_010, low: 79_996, close: 80_004, volume: 10 },
  { time: CURSOR - 300, open: 80_004, high: 80_012, low: 80_001, close: 80_006, volume: 10 },
]

const NO_SCORE: ScoreView = { closedTrades: 0, wins: 0, losses: 0, totalR: 0, netPnlCents: 0 }

const ANALYSING: PracticeState = {
  id: ID,
  instrument: 'DEMO-NQ',
  tickSize: 0.25,
  tickValueCents: 500,
  exchangeTimezone: 'America/Chicago',
  timeframe: '5m',
  status: 'ANALYSING',
  startTime: CURSOR,
  cursor: CURSOR,
  timeBasis: 'UTC',
  dataClass: 'SIMULATED',
  disclaimer: 'SIMULATED DATA — NOT MARKET HISTORY',
  generatorVersion: 'demo-gen-1.2.0',
  trades: [],
  score: NO_SCORE,
}

const PLAN = {
  direction: 'LONG' as const,
  stopTicks: 79_960,
  targetTicks: 80_086,
  contracts: 1,
  referenceTicks: 80_006,
  lockedAt: CURSOR,
  plannedRewardRisk: 1.74,
}

const AMBIGUOUS: ResultView = {
  outcome: 'AMBIGUOUS',
  assumption:
    'The stop and the target were both touched inside the same second, so which came first ' +
    'cannot be known from this data. The result assumes the worst case: the stop.',
  entryTime: CURSOR,
  entryTicks: 80_007,
  exitTime: CURSOR + 1234,
  exitTicks: 79_959,
  pnlTicks: -48,
  netPnlCents: -24_250,
  commissionCents: 250,
  maeTicks: 48,
  mfeTicks: 79,
  gapExit: false,
  rMultiple: -1.04,
  resolution: 'S1',
  engineVersion: 'fill-1.1.0',
  fillModelVersion: 'slip-1t/comm-250c',
}

/** Closed by the learner at market, two bars in (DEC-057): no assumption, the planned exit unseen. */
const CLOSED_BY_YOU: ResultView = {
  ...AMBIGUOUS,
  outcome: 'CLOSED',
  assumption: undefined,
  exitTime: CURSOR + 600,
  exitTicks: 80_015,
  pnlTicks: 8,
  netPnlCents: 3_750,
  maeTicks: 8,
  mfeTicks: 9,
  rMultiple: 0.17,
  engineVersion: 'fill-1.2.0',
}

const OPEN: TradeView = { number: 1, plan: PLAN }
const ENTERED: TradeView = { ...OPEN, entry: { time: CURSOR, ticks: 80_007 } }
const CLOSED: TradeView = { ...ENTERED, result: AMBIGUOUS }
const AFTER_ONE: ScoreView = {
  closedTrades: 1,
  wins: 0,
  losses: 1,
  totalR: -1.04,
  netPnlCents: -24_250,
}
/** The standard costs (T27), as the server answers for a learner who has set none. */
const STANDARD = {
  feesCents: 450,
  marketSlippageTicks: 1,
  stopSlippageTicks: 1,
  limitThroughTicks: 1,
  fillModel: 'mkt-1t/slip-1t/thru-1t/comm-450c',
}

/** A learner with no finished trade that says how sure they were (calibration-1.0.0). */
const NO_CALIBRATION = {
  version: 'calibration-1.0.0',
  question: 'How sure were you that the trade would reach its target before its stop?',
  trades: 0,
  decided: 0,
  hits: 0,
  closedByHand: 0,
  timeExits: 0,
  ambiguous: 0,
  buckets: [],
  summary: ['No finished trade says how sure you were yet.'],
}

/** A learner with no finished trade (journal-stats-1.0.0). */
const NO_STATISTICS = {
  version: 'journal-stats-1.0.0',
  overall: { label: 'All trades', trades: 0, wins: 0, losses: 0, totalR: 0, netPnlCents: 0, reading: 'No trades.' },
  dimensions: [],
  notes: ["No finished trade yet: the figures appear as the replay shows each trade's exit."],
}

/** Above 2^53 on purpose: as a JSON number this would be rounded (BUG-028). */
const SEED = '1713776017923440752'

interface Call {
  method: string
  url: string
  body: unknown
}

type Handler = (call: Call) => { status?: number; body: unknown }

let calls: Call[] = []

function mockApi(handler: Handler) {
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

/** Matches a call by method and the end of its path; the query string (a view timeframe, T34) aside. */
const is = (call: Call, method: string, suffix: string) =>
  call.method === method && call.url.split('?')[0].endsWith(suffix)

/**
 * A server that plays one replay through: create, lock, one bar, skip to an ambiguous result, a
 * second trade, and the end.
 */
function happyServer(overrides: Partial<Record<'plan', { status: number; body: unknown }>> = {}) {
  let locked = 0
  return (call: Call) => {
    if (is(call, 'GET', '/api/practice/sessions')) return { body: [] }
    if (is(call, 'GET', '/api/practice/calibration')) return { body: NO_CALIBRATION }
    if (is(call, 'GET', '/api/practice/settings')) return { body: STANDARD }
    if (is(call, 'GET', '/api/practice/statistics')) return { body: NO_STATISTICS }
    if (is(call, 'POST', '/api/practice/sessions')) {
      return { status: 201, body: { state: ANALYSING, bars: BARS } satisfies SessionResponse }
    }
    if (is(call, 'POST', '/plan')) {
      if (overrides.plan) return overrides.plan
      locked++
      return {
        body:
          locked === 1
            ? { ...ANALYSING, status: 'IN_TRADE', trades: [OPEN] }
            : {
                ...ANALYSING,
                status: 'IN_TRADE',
                cursor: CURSOR + 1500,
                trades: [CLOSED, { number: 2, plan: { ...PLAN, lockedAt: CURSOR + 1500 } }],
                score: AFTER_ONE,
              },
      }
    }
    if (is(call, 'POST', '/advance')) {
      return {
        body: {
          state: { ...ANALYSING, status: 'IN_TRADE', cursor: CURSOR + 300, trades: [ENTERED] },
          bars: [
            { time: CURSOR, open: 80_007, high: 80_015, low: 79_999, close: 80_010, volume: 10 },
          ],
        },
      }
    }
    if (is(call, 'POST', '/skip')) {
      return {
        body: {
          // Flat again: the trade is over, the session is not — so no seed yet.
          state: { ...ANALYSING, cursor: CURSOR + 1500, trades: [CLOSED], score: AFTER_ONE },
          bars: [
            { time: CURSOR + 1200, open: 80_010, high: 80_090, low: 79_950, close: 79_970, volume: 10 },
          ],
        },
      }
    }
    if (is(call, 'POST', '/close')) {
      return {
        body: {
          state: {
            ...ANALYSING,
            cursor: CURSOR + 601,
            trades: [{ ...ENTERED, result: CLOSED_BY_YOU }],
            score: { closedTrades: 1, wins: 1, losses: 0, totalR: 0.17, netPnlCents: 3_750 },
          },
          bars: [
            { time: CURSOR + 300, open: 80_010, high: 80_016, low: 80_004, close: 80_015, volume: 10 },
            { time: CURSOR + 600, open: 80_015, high: 80_015, low: 80_015, close: 80_015, volume: 1 },
          ],
        },
      }
    }
    if (is(call, 'POST', '/end')) {
      return {
        body: {
          ...ANALYSING,
          status: 'COMPLETE',
          cursor: CURSOR + 1500,
          trades: [CLOSED],
          score: AFTER_ONE,
          seed: SEED,
        },
      }
    }
    return { status: 404, body: { message: 'not found' } }
  }
}

/** Every chart the last render made: setups are layers painted by them (T37), not elements on the page. */
let charts: FakeChartAdapter[] = []

function renderPractice() {
  charts = []
  return render(
    <PracticeView
      adapterFactory={() => {
        const chart = new FakeChartAdapter()
        charts.push(chart)
        return chart
      }}
    />,
  )
}

/** How many setup layers the charts paint: the rules drawn on them, the review's own among them. */
const setupLayers = () => charts.flatMap((chart) => chart.layers).filter((layer) => layer.zOrder === 'normal').length

async function startSession(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('button', { name: /start a session/i }))
  await screen.findByTestId('price-chart')
}

async function lockLong(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: /plan long/i }))
  await user.click(screen.getByRole('button', { name: /place trade/i }))
  await screen.findByText(/— placed/)
}

async function endSession(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: /end session…/i }))
  await user.click(screen.getByRole('button', { name: /^end session$/i }))
  await waitFor(() => expect(screen.getByTestId('practice-status')).toHaveTextContent('Ended'))
}

afterEach(() => vi.restoreAllMocks())

describe('Practice — a replay', () => {
  beforeEach(() => mockApi(happyServer()))

  it('starts flat at a hidden moment, labelled as simulated, without fetching it twice', async () => {
    const user = userEvent.setup()
    renderPractice()
    await startSession(user)

    expect(screen.getByTestId('practice-status')).toHaveTextContent('Flat')
    expect(screen.getByRole('note')).toHaveTextContent(/SIMULATED DATA/)
    expect(calls.filter((c) => c.method === 'POST' && c.url.endsWith('/sessions'))).toHaveLength(1)
    expect(calls.some((c) => c.method === 'GET' && c.url.endsWith(`/sessions/${ID}`))).toBe(false)
    // Nothing to log until a trade is placed.
    expect(screen.queryByRole('region', { name: 'Trades' })).not.toBeInTheDocument()
  })

  it('builds a bracket, explains a bad one, and locks exactly what was shown', async () => {
    const user = userEvent.setup()
    renderPractice()
    await startSession(user)

    await user.click(screen.getByRole('button', { name: /plan long/i }))
    const numbers = screen.getByTestId('plan-numbers')
    expect(numbers).toHaveTextContent('40 ticks · $200.00')
    expect(numbers).toHaveTextContent('80 ticks · $400.00')
    expect(numbers).toHaveTextContent('2.00')

    // A stop above the price is not a long.
    const stop = screen.getByLabelText('Stop')
    await user.clear(stop)
    await user.type(stop, '20005{Enter}')
    expect(screen.getByRole('alert')).toHaveTextContent(/stop below the current price/)
    expect(screen.getByRole('button', { name: /place trade/i })).toBeDisabled()

    await user.clear(stop)
    await user.type(stop, '19990{Enter}')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /place trade/i }))
    await waitFor(() => expect(screen.getByTestId('practice-status')).toHaveTextContent('In trade'))

    const lock = calls.find((c) => is(c, 'POST', '/plan'))
    expect(lock?.body).toEqual({
      direction: 'LONG',
      stopTicks: 79_960,
      targetTicks: 80_086,
      contracts: 1,
    })
    // Nothing about the outcome is on screen yet, and neither is the entry price.
    expect(screen.getByText(/Trade 1 · Long 1 — placed/)).toBeInTheDocument()
    expect(screen.getByTestId('trade-entry')).toHaveTextContent('at the next price')
    expect(screen.queryByTestId('result-outcome')).not.toBeInTheDocument()
    expect(screen.getAllByTestId('trade-row')[0]).toHaveTextContent('Running')
    expect(screen.getByTestId('score-trades')).toHaveTextContent('0')
    // No ending a session with a trade still running.
    expect(screen.queryByRole('button', { name: /end session/i })).not.toBeInTheDocument()
  })

  it('T22: names a model and a bias, reads the checklist while drafting, and keeps both with the trade', async () => {
    const checklist = {
      model: 'MODEL_2022',
      title: 'The 2022 model',
      version: 'model-2022-1.0.0',
      items: [
        { id: 'killzone', text: 'Inside a killzone: London, 02:00–05:00.', met: true },
        { id: 'shift', text: 'A bullish market structure shift.', met: true },
        { id: 'gap', text: 'Price is not in the gap.', met: false },
      ],
      met: 2,
      total: 3,
    }
    const server = happyServer()
    mockApi((call) => {
      if (is(call, 'POST', '/context')) {
        const body = call.body as { model?: string }
        return {
          body: {
            killzone: 'LONDON',
            biasReasoning: 'Yesterday stayed inside the day before\'s range.',
            biasRule: 'daily-bias-1.0.0',
            ...(body.model ? { checklist } : {}),
          },
        }
      }
      if (is(call, 'POST', '/plan')) {
        return { body: { ...ANALYSING, status: 'IN_TRADE', trades: [{ ...OPEN, plan: { ...OPEN.plan, model: 'MODEL_2022', checklist } }] } }
      }
      return server(call)
    })
    const user = userEvent.setup()
    renderPractice()
    await startSession(user)
    await user.click(screen.getByRole('button', { name: /plan long/i }))
    expect(await screen.findByTestId('plan-context')).toHaveTextContent('In the London killzone.')

    await user.selectOptions(screen.getByLabelText('Model'), 'MODEL_2022')
    await user.click(screen.getByRole('button', { name: 'Bullish' }))
    expect(await screen.findByTestId('checklist')).toHaveTextContent('The 2022 model checklist · 2 of 3')
    expect(calls.filter((c) => is(c, 'POST', '/context')).at(-1)?.body).toMatchObject({ direction: 'LONG', model: 'MODEL_2022' })

    await user.click(screen.getByRole('button', { name: /place trade/i }))
    await waitFor(() => expect(screen.getByTestId('practice-status')).toHaveTextContent('In trade'))
    expect(calls.find((c) => is(c, 'POST', '/plan'))?.body).toMatchObject({ model: 'MODEL_2022', bias: 'BULLISH' })
    expect(screen.getByTestId('trade-model')).toHaveTextContent('2/3')
  })

  it('T28: takes part off at a price, and draws the stop at the entry once that has filled', async () => {
    const server = happyServer()
    const placed = { ...PLAN, contracts: 2, partialTicks: 80_046, partialContracts: 1, breakevenAfterPartial: true }
    const filled = { time: CURSOR + 60, ticks: 80_046, contracts: 1, pnlTicks: 39, gap: false }
    mockApi((call) => {
      if (is(call, 'POST', '/plan')) {
        return { body: { ...ANALYSING, status: 'IN_TRADE', trades: [{ ...ENTERED, plan: placed, partial: filled }] } }
      }
      return server(call)
    })
    const user = userEvent.setup()
    renderPractice()
    await startSession(user)
    await user.click(screen.getByRole('button', { name: /plan long/i }))
    await user.clear(screen.getByLabelText('Contracts'))
    await user.type(screen.getByLabelText('Contracts'), '2')
    await user.type(screen.getByLabelText('Partial'), '20011.50{Enter}')
    // Back to one contract, the partial is set aside rather than blocking the trade; at two it is back.
    await user.clear(screen.getByLabelText('Contracts'))
    await user.type(screen.getByLabelText('Contracts'), '1')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    await user.clear(screen.getByLabelText('Contracts'))
    await user.type(screen.getByLabelText('Contracts'), '2')
    expect(screen.getByLabelText('Partial')).toHaveValue('20011.50')
    await user.click(screen.getByRole('checkbox', { name: /stop to the entry/ }))
    await user.click(screen.getByRole('button', { name: /place trade/i }))
    await waitFor(() => expect(screen.getByTestId('practice-status')).toHaveTextContent('In trade'))

    expect(calls.find((c) => is(c, 'POST', '/plan'))?.body).toMatchObject({
      contracts: 2,
      partialTicks: 80_046,
      partialContracts: 1,
      breakevenAfterPartial: true,
    })
    expect(screen.getByTestId('trade-partial')).toHaveTextContent('1 taken off at 20011.50')
    expect(screen.getByTestId('trade-stop')).toHaveTextContent('20001.75 — moved to the entry')
  })

  it('throws a draft away: nothing was placed, so nothing is sent', async () => {
    // Fabio's report: a bracket drawn with "Place long" looked like a trade and could not be taken
    // back, and price crossing its stop did nothing. It was a draft all along — now it says so, and
    // it can be discarded.
    const user = userEvent.setup()
    renderPractice()
    await startSession(user)

    await user.click(screen.getByRole('button', { name: /plan long/i }))
    expect(screen.getByTestId('draft-title')).toHaveTextContent(/not placed/i)
    expect(screen.getByTestId('practice-status')).toHaveTextContent('Flat')

    await user.click(screen.getByRole('button', { name: /discard/i }))
    expect(screen.queryByTestId('plan-numbers')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /plan long/i })).toBeInTheDocument()
    expect(calls.some((c) => is(c, 'POST', '/plan'))).toBe(false)
  })

  it('closes a running trade at market, and shows the fill at once', async () => {
    const user = userEvent.setup()
    renderPractice()
    await startSession(user)
    await lockLong(user)
    // A placed trade has no Discard any more: it can only be closed.
    expect(screen.queryByRole('button', { name: /discard/i })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /close at market/i }))
    const outcome = await screen.findByTestId('result-outcome')
    expect(calls.find((c) => is(c, 'POST', '/close'))?.url).toContain('timeframe=5m')
    expect(outcome).toHaveTextContent('Trade 1 · Closed by you')
    expect(screen.getByTestId('result-net')).toHaveTextContent('$37.50')
    expect(screen.getByRole('region', { name: 'Result' })).toHaveTextContent(/You closed it at market/)
    expect(screen.getByTestId('practice-status')).toHaveTextContent('Flat')
    expect(screen.getAllByTestId('trade-row')[0]).toHaveTextContent('Closed by you')
    expect(screen.getByTestId('score-trades')).toHaveTextContent('1')
    expect(screen.queryByRole('button', { name: /close at market/i })).not.toBeInTheDocument()
  })

  it('reveals the entry with the next bar, then the result — labelled as an assumption', async () => {
    const user = userEvent.setup()
    renderPractice()
    await startSession(user)
    await lockLong(user)

    await user.click(screen.getByRole('button', { name: /next bar/i }))
    await waitFor(() => expect(screen.getByTestId('trade-entry')).toHaveTextContent('20001.75'))
    expect(calls.find((c) => is(c, 'POST', '/advance'))?.body).toEqual({ bars: 1, timeframe: '5m' })

    await user.click(screen.getByRole('button', { name: /skip to result/i }))
    const outcome = await screen.findByTestId('result-outcome')
    expect(outcome).toHaveTextContent('Trade 1 · Ambiguous — worst case assumed')
    const result = screen.getByRole('region', { name: 'Result' })
    expect(within(result).getByRole('note')).toHaveTextContent(/assumes the worst case/)
    expect(screen.getByTestId('result-net')).toHaveTextContent('−$242.50')
    expect(screen.getByTestId('result-r')).toHaveTextContent('−1.04R')

    // Flat again, scored, free to trade on — and the seed, the whole future, still hidden.
    expect(screen.getByTestId('practice-status')).toHaveTextContent('Flat')
    expect(screen.getByTestId('score-trades')).toHaveTextContent('1')
    expect(screen.getByTestId('score-winrate')).toHaveTextContent('0%')
    expect(screen.getByTestId('score-r')).toHaveTextContent('−1.04R')
    expect(screen.getByTestId('score-net')).toHaveTextContent('−$242.50')
    expect(screen.queryByTestId('result-seed')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /plan long/i })).toBeInTheDocument()
  })

  it('T24: reads the finished trade as it happened, and draws its hits on the chart beside the rest', async () => {
    const review = {
      version: 'review-1.0.0',
      rules: ['LIQUIDITY', 'SWEEP', 'MSS', 'FVG'],
      moments: [
        { time: CURSOR, phase: 'TRADE', kind: 'DECISION', title: 'You placed a long', lines: [] },
        { time: CURSOR + 1200, phase: 'TRADE', kind: 'EXIT', title: 'Stop and target inside one candle, scored as the stop at 19989.75: −1.04R.', lines: [] },
      ],
      summary: [],
      layer: {
        rules: [{ detector: 'FVG', version: 'fvg-1.0.0', conceptId: 'ICT-20', parameters: {}, methodologySource: 'x' }],
        setups: [
          { detector: 'FVG', shape: 'ZONE', direction: 'BULLISH', start: CURSOR - 600, anchor: CURSOR - 300, end: CURSOR,
            confirmedAt: CURSOR, lowTicks: 79_990, highTicks: 80_000, midHalfTicks: 159_990 },
        ],
        rolls: [],
      },
    }
    const server = happyServer()
    let skips = 0
    mockApi((call) => {
      if (is(call, 'GET', '/trades/1/review')) return { body: review }
      if (is(call, 'POST', '/skip') && ++skips === 2) {
        const second = { number: 2, plan: PLAN, entry: { time: CURSOR + 1500, ticks: 79_970 }, result: { ...AMBIGUOUS, outcome: 'TARGET' } }
        return { body: { state: { ...ANALYSING, cursor: CURSOR + 3000, trades: [CLOSED, second], score: AFTER_ONE }, bars: [] } }
      }
      return server(call)
    })
    const user = userEvent.setup()
    renderPractice()
    await startSession(user)
    await lockLong(user)
    await user.click(screen.getByRole('button', { name: /skip to result/i }))
    await screen.findByTestId('result-outcome')
    const overlays = setupLayers()

    await user.click(screen.getByRole('button', { name: 'Read the review' }))
    expect(await screen.findByTestId('review-exit')).toHaveTextContent('scored as the stop at 19989.75')
    expect(calls.some((c) => c.method === 'GET' && c.url === `/api/practice/sessions/${ID}/trades/1/review`)).toBe(true)
    await user.click(screen.getByRole('button', { name: 'Show its rules on the chart' }))
    expect(setupLayers()).toBe(overlays + 1)
    await user.click(screen.getByRole('button', { name: 'Hide its rules on the chart' }))
    expect(setupLayers()).toBe(overlays)

    // Drawn again, then another trade ends: its review is its own, and trade 1's hits come off the chart.
    await user.click(screen.getByRole('button', { name: 'Show its rules on the chart' }))
    await lockLong(user)
    await user.click(screen.getByRole('button', { name: /skip to result/i }))
    await waitFor(() => expect(screen.getByTestId('result-outcome')).toHaveTextContent('Trade 2'))
    expect(setupLayers()).toBe(overlays)
    expect(screen.getByRole('region', { name: 'Review' })).toHaveTextContent('Trade 2, as it happened')
    await user.click(screen.getByRole('button', { name: 'Show trade 1' }))
    expect(setupLayers()).toBe(overlays)
    expect(screen.getByRole('button', { name: 'Read the review' })).toBeInTheDocument()
  })

  it('takes a second trade after the first, priced from where the replay is now', async () => {
    const user = userEvent.setup()
    renderPractice()
    await startSession(user)
    await lockLong(user)
    await user.click(screen.getByRole('button', { name: /skip to result/i }))
    await screen.findByTestId('result-outcome')

    // The first trade's bracket is gone; a new one starts at the latest close, 79,970.
    expect(screen.queryByTestId('plan-numbers')).not.toBeInTheDocument()
    await lockLong(user)
    expect(calls.filter((c) => is(c, 'POST', '/plan'))[1].body).toEqual({
      direction: 'LONG',
      stopTicks: 79_930,
      targetTicks: 80_050,
      contracts: 1,
    })
    expect(screen.getByText(/Trade 2 · Long 1 — placed/)).toBeInTheDocument()
    const rows = screen.getAllByTestId('trade-row')
    expect(rows).toHaveLength(2)
    expect(rows[0]).toHaveTextContent('Ambiguous')
    expect(rows[1]).toHaveTextContent('Running')
  })

  it('BUG-041: undo after a trade never brings its old bracket back', async () => {
    const user = userEvent.setup()
    renderPractice()
    await startSession(user)
    await lockLong(user)
    await user.click(screen.getByRole('button', { name: /skip to result/i }))
    await screen.findByTestId('result-outcome')
    expect(screen.queryByTestId('plan-numbers')).not.toBeInTheDocument()

    // Undo and redo are for the learner's own analysis. The bracket became trade 1; it is history,
    // not a draft to step back into.
    // Checked after every step: each undo walks back through the history one entry at a time.
    for (let i = 0; i < 3; i++) {
      await user.click(screen.getByTitle('Undo (Ctrl+Z)'))
      expect(screen.queryByTestId('plan-numbers')).not.toBeInTheDocument()
    }
    expect(screen.getByText(/Draft a bracket/i)).toBeInTheDocument()
  })

  it('BUG-045: an unanswered "End the session?" does not come back after a trade', async () => {
    const user = userEvent.setup()
    renderPractice()
    await startSession(user)
    await user.click(screen.getByRole('button', { name: /end session…/i }))
    expect(screen.getByRole('button', { name: /keep trading/i })).toBeInTheDocument()

    // Instead of answering, the learner trades — and is flat again when it is over.
    await lockLong(user)
    await user.click(screen.getByRole('button', { name: /skip to result/i }))
    await screen.findByTestId('result-outcome')
    expect(screen.queryByRole('button', { name: /keep trading/i })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /end session…/i })).toBeInTheDocument()
  })

  it('BUG-046: a trade that has just closed is shown, even after an earlier one was picked', async () => {
    const server = happyServer()
    let skips = 0
    const second: TradeView = {
      number: 2,
      plan: PLAN,
      entry: { time: CURSOR + 1500, ticks: 79_970 },
      result: { ...AMBIGUOUS, outcome: 'TARGET', assumption: undefined, rMultiple: 1.5, netPnlCents: 29_750 },
    }
    mockApi((call) => {
      if (is(call, 'POST', '/skip') && ++skips === 2) {
        return {
          body: {
            state: {
              ...ANALYSING,
              cursor: CURSOR + 3000,
              trades: [CLOSED, second],
              score: { closedTrades: 2, wins: 1, losses: 1, totalR: 0.46, netPnlCents: 5_500 },
            },
            bars: [],
          },
        }
      }
      return server(call)
    })
    const user = userEvent.setup()
    renderPractice()
    await startSession(user)
    await lockLong(user)
    await user.click(screen.getByRole('button', { name: /skip to result/i }))
    await screen.findByTestId('result-outcome')
    await user.click(screen.getByRole('button', { name: 'Show trade 1' }))

    await lockLong(user)
    await user.click(screen.getByRole('button', { name: /skip to result/i }))
    await waitFor(() =>
      expect(screen.getByTestId('result-outcome')).toHaveTextContent('Trade 2 · Target hit'),
    )
    // And trade 1 is still a click away.
    await user.click(screen.getByRole('button', { name: 'Show trade 1' }))
    expect(screen.getByTestId('result-outcome')).toHaveTextContent('Trade 1')
  })

  it('ends only when asked twice, and only then shows the seed', async () => {
    const user = userEvent.setup()
    renderPractice()
    await startSession(user)
    await lockLong(user)
    await user.click(screen.getByRole('button', { name: /skip to result/i }))
    await screen.findByTestId('result-outcome')

    await user.click(screen.getByRole('button', { name: /end session…/i }))
    await user.click(screen.getByRole('button', { name: /keep trading/i }))
    expect(calls.some((c) => is(c, 'POST', '/end'))).toBe(false)

    await endSession(user)
    expect(calls.filter((c) => is(c, 'POST', '/end'))).toHaveLength(1)
    // Only now is the seed shown — it is the reproduction key (DEC-012, DEC-038).
    expect(screen.getByTestId('result-seed')).toHaveTextContent(new RegExp(`^${SEED}$`))
    expect(screen.getByText('Session over')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /play/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /plan long/i })).not.toBeInTheDocument()
  })

  it('ignores a second click while a step is in flight', async () => {
    // The session row is optimistic-locked, so a double click would otherwise earn a 409.
    let release: () => void = () => {}
    const gate = new Promise<void>((resolve) => (release = resolve))
    const server = happyServer()
    mockApi(server)
    const fetchMock = vi.mocked(globalThis.fetch)
    const original = fetchMock.getMockImplementation()!
    fetchMock.mockImplementation(async (url, init) => {
      if (String(url).endsWith('/advance')) await gate
      return original(url, init)
    })

    const user = userEvent.setup()
    renderPractice()
    await startSession(user)
    const next = screen.getByRole('button', { name: /next bar/i })
    await user.click(next)
    await user.click(next)
    await act(async () => release())

    await waitFor(() => expect(screen.getByTestId('practice-cursor')).toBeInTheDocument())
    expect(calls.filter((c) => is(c, 'POST', '/advance'))).toHaveLength(1)
  })
})

describe('Practice — the scorecard (T23)', () => {
  it('sets a risk limit before a session, refuses one out of range, clears it, and reads the draft against it', async () => {
    let saved: number | undefined = 15_000
    const server = happyServer()
    mockApi((call) => {
      const limit = () => ({ body: saved === undefined ? STANDARD : { ...STANDARD, riskLimitCents: saved } })
      if (is(call, 'GET', '/api/practice/settings')) return limit()
      if (is(call, 'PUT', '/api/practice/settings')) {
        saved = (call.body as { riskLimitCents?: number }).riskLimitCents
        return limit()
      }
      return server(call)
    })
    const user = userEvent.setup()
    renderPractice()
    const field = screen.getByLabelText('Risk limit per trade ($)')
    await waitFor(() => expect(field).toHaveValue('150'))
    const puts = () => calls.filter((c) => is(c, 'PUT', '/api/practice/settings')).map((c) => c.body)

    await user.clear(field)
    await user.type(field, '0.5{Enter}')
    expect(screen.getByRole('alert')).toHaveTextContent('A risk limit is $1 to $1,000,000.')
    expect(field).toHaveValue('150')
    expect(puts()).toEqual([])

    // BUG-089: a decimal comma is the decimal point, not a thousands separator — not $25,050.
    await user.clear(field)
    await user.type(field, '250,50{Enter}')
    await waitFor(() => expect(field).toHaveValue('250.5'))
    await user.clear(field)
    await user.type(field, '$1,250{Enter}')
    await waitFor(() => expect(field).toHaveValue('1250'))
    expect(screen.queryByRole('alert')).toBeNull()
    await user.clear(field)
    await user.keyboard('{Enter}')
    await waitFor(() => expect(field).toHaveValue(''))
    await user.type(field, '150{Enter}')
    await waitFor(() => expect(field).toHaveValue('150'))
    // Saved all at once: the costs go with the limit, as they were.
    const { fillModel: _, ...costs } = STANDARD
    expect(puts()).toEqual([{ ...costs, riskLimitCents: 25_050 }, { ...costs, riskLimitCents: 125_000 }, costs,
      { ...costs, riskLimitCents: 15_000 }])

    // Forty ticks at $5: $200, over the $150 limit — said, not prevented.
    await startSession(user)
    await user.click(screen.getByRole('button', { name: /plan long/i }))
    expect(screen.getByTestId('risk-limit')).toHaveTextContent('over your $150.00 limit')
    expect(screen.getByRole('button', { name: /place trade/i })).toBeEnabled()
  })

  it('T27: shows the costs in force, and saves a change to one with the rest as they were', async () => {
    let saved: Record<string, unknown> = { ...STANDARD, riskLimitCents: 30_000 }
    const server = happyServer()
    mockApi((call) => {
      if (is(call, 'GET', '/api/practice/settings')) return { body: saved }
      if (is(call, 'PUT', '/api/practice/settings')) {
        saved = { ...(call.body as object), fillModel: 'whatever the server names it' }
        return { body: saved }
      }
      return server(call)
    })
    const user = userEvent.setup()
    renderPractice()
    const costs = await screen.findByRole('region', { name: 'Your costs' })
    expect(within(costs).getByLabelText('Fees per round trip ($)')).toHaveValue('4.5')
    expect(screen.getByTestId('costs-model')).toHaveTextContent('(mkt-1t/slip-1t/thru-1t/comm-450c)')
    const stop = within(costs).getByRole('group', { name: 'Slippage at the stop, ticks' })
    expect(within(stop).getByRole('button', { name: '1' })).toHaveAttribute('aria-pressed', 'true')

    await user.click(within(stop).getByRole('button', { name: '2' }))
    const puts = () => calls.filter((c) => is(c, 'PUT', '/api/practice/settings')).map((c) => c.body)
    await waitFor(() => expect(puts()).toHaveLength(1))
    expect(puts()[0]).toEqual({ riskLimitCents: 30_000, feesCents: 450, marketSlippageTicks: 1, stopSlippageTicks: 2, limitThroughTicks: 1 })

    const fees = within(costs).getByLabelText('Fees per round trip ($)')
    await user.clear(fees)
    await user.type(fees, '3.25{Enter}')
    await waitFor(() => expect(puts()).toHaveLength(2))
    expect(puts()[1]).toMatchObject({ feesCents: 325, stopSlippageTicks: 2 })
    // More than $50 a round trip is not a fee: nothing is sent, and the learner is told (BUG-089).
    await user.clear(fees)
    await user.type(fees, '75{Enter}')
    expect(puts()).toHaveLength(2)
    expect(screen.getByRole('alert')).toHaveTextContent('Fees are $0 to $50 a round trip, written like 4.50 or 4,50.')
    // BUG-089: four dollars fifty, not $450 refused.
    await user.clear(fees)
    await user.type(fees, '3,25{Enter}')
    await waitFor(() => expect(puts()).toHaveLength(3))
    expect(puts()[2]).toMatchObject({ feesCents: 325 })

    await user.click(within(costs).getByRole('button', { name: 'On the touch' }))
    await waitFor(() => expect(puts()).toHaveLength(4))
    expect(puts()[3]).toMatchObject({ limitThroughTicks: 0, feesCents: 325 })

    // And the draft reads its costs: $3.25 fees, a tick in, up to two out — at most $18.25.
    await startSession(user)
    await user.click(screen.getByRole('button', { name: /plan long/i }))
    expect(screen.getByTestId('plan-costs')).toHaveTextContent('$3.25 fees · slippage 1 tick in, up to 2 out — at most $18.25')
  })

  it('sends how sure the learner is with the plan, and starts the next draft unsaid', async () => {
    mockApi(happyServer())
    const user = userEvent.setup()
    renderPractice()
    await startSession(user)
    await user.click(screen.getByRole('button', { name: /plan long/i }))
    await user.selectOptions(screen.getByLabelText('Target before stop'), '70% sure')
    await user.click(screen.getByRole('button', { name: /place trade/i }))
    await waitFor(() => expect(screen.getByTestId('practice-status')).toHaveTextContent('In trade'))
    expect(calls.find((c) => is(c, 'POST', '/plan'))?.body).toMatchObject({ direction: 'LONG', confidence: 70 })
  })

  it('reads calibration in sentences, with the trades behind every rate', async () => {
    const server = happyServer()
    mockApi((call) =>
      is(call, 'GET', '/api/practice/calibration')
        ? {
            body: {
              ...NO_CALIBRATION,
              trades: 7,
              decided: 6,
              hits: 2,
              meanConfidence: 66.7,
              low: 10,
              high: 70,
              closedByHand: 1,
              buckets: [
                { confidence: 50, trades: 2, hits: 1 },
                { confidence: 80, trades: 4, hits: 1 },
              ],
              summary: [
                'On 6 trades the bracket decided, you said 67% on average; 2 reached the target first (33%).',
                'A hit rate from 10% to 70% fits these results (95%), and your 67% is inside it.',
                'Not counted: 1 closed by hand. The bracket did not decide it.',
              ],
            },
          }
        : server(call),
    )
    renderPractice()
    const panel = await screen.findByRole('region', { name: 'Calibration' })
    expect(panel).toHaveTextContent('How sure were you that the trade would reach its target before its stop?')
    expect(within(panel).getByTestId('calibration-summary')).toHaveTextContent(
      'On 6 trades the bracket decided, you said 67% on average; 2 reached the target first (33%).A hit rate from 10% to 70%',
    )
    const rows = await within(panel).findAllByTestId('calibration-row')
    expect(rows.map((r) => [...r.querySelectorAll('td')].map((td) => td.textContent))).toEqual([
      ['50%', '2', '1', '50%'],
      ['80%', '4', '1', '25%'],
    ])
    expect(panel).toHaveTextContent('calibration-1.0.0.')
  })

  it('T25: the journal shows the statistics between the sessions and the calibration', async () => {
    mockApi(happyServer())
    renderPractice()
    const stats = await screen.findByRole('region', { name: 'Statistics' })
    expect(stats).toHaveTextContent('No finished trade yet: the figures appear as the replay shows each trade')
    const panels = screen.getAllByRole('region').map((r) => r.getAttribute('aria-label'))
    expect(panels.indexOf('Journal')).toBeLessThan(panels.indexOf('Statistics'))
    expect(panels.indexOf('Statistics')).toBeLessThan(panels.indexOf('Calibration'))
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('with nothing to compare yet, says how to start and shows no table', async () => {
    mockApi(happyServer())
    renderPractice()
    const panel = await screen.findByRole('region', { name: 'Calibration' })
    expect(panel).toHaveTextContent('No finished trade says how sure you were yet.')
    expect(within(panel).queryByRole('table')).toBeNull()
  })
})

describe('Practice — playing', () => {
  it('plays at the chosen speed, and stops by itself when a trade closes', async () => {
    const server = happyServer()
    mockApi((call) =>
      is(call, 'POST', '/advance')
        ? {
            body: {
              state: { ...ANALYSING, cursor: CURSOR + 1500, trades: [CLOSED], score: AFTER_ONE },
              bars: [
                { time: CURSOR, open: 80_007, high: 80_090, low: 79_950, close: 79_970, volume: 10 },
              ],
            },
          }
        : server(call),
    )
    const user = userEvent.setup()
    renderPractice()
    await startSession(user)
    await lockLong(user)

    const speed = screen.getByRole('group', { name: /replay speed/i })
    await user.click(within(speed).getByRole('button', { name: '8×' }))
    expect(within(speed).getByRole('button', { name: '8×' })).toHaveAttribute('aria-pressed', 'true')

    await user.click(screen.getByRole('button', { name: 'Play' }))
    expect(screen.getByRole('button', { name: 'Pause' })).toBeInTheDocument()
    // One step closes the trade; play stops so the result is read, not scrolled past.
    await screen.findByTestId('result-outcome')
    await waitFor(() => expect(screen.getByRole('button', { name: 'Play' })).toBeInTheDocument())
    await new Promise((resolve) => setTimeout(resolve, 300))
    expect(calls.filter((c) => is(c, 'POST', '/advance'))).toHaveLength(1)
  })
})

describe('Practice — a short, and the bracket around it', () => {
  beforeEach(() => mockApi(happyServer()))

  it('builds a short: stop above the price, target below, and sends exactly that', async () => {
    const user = userEvent.setup()
    renderPractice()
    await startSession(user)

    // The price is 80,006 (20,001.50): the default short risks 40 ticks above for 80 below.
    await user.click(screen.getByRole('button', { name: /plan short/i }))
    expect(screen.getByTestId('draft-title')).toHaveTextContent(/draft · short at market/i)
    expect(screen.getByLabelText('Stop')).toHaveValue('20011.50')
    expect(screen.getByLabelText('Target')).toHaveValue('19981.50')
    expect(screen.getByTestId('plan-numbers')).toHaveTextContent('40 ticks · $200.00')

    // A stop below the price is not a short's stop.
    const stop = screen.getByLabelText('Stop')
    await user.clear(stop)
    await user.type(stop, '20000{Enter}')
    expect(screen.getByRole('alert')).toHaveTextContent(/short needs its stop above the current price/)
    expect(screen.getByRole('button', { name: /place trade/i })).toBeDisabled()

    await user.clear(stop)
    await user.type(stop, '20004{Enter}')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByTestId('plan-numbers')).toHaveTextContent('10 ticks · $50.00')
    expect(screen.getByTestId('plan-numbers')).toHaveTextContent('8.00')

    const contracts = screen.getByLabelText('Contracts')
    await user.clear(contracts)
    await user.type(contracts, '3')
    expect(screen.getByTestId('plan-numbers')).toHaveTextContent('10 ticks · $150.00')

    await user.click(screen.getByRole('button', { name: /place trade/i }))
    await waitFor(() => expect(screen.getByTestId('practice-status')).toHaveTextContent('In trade'))
    expect(calls.find((c) => is(c, 'POST', '/plan'))?.body).toEqual({
      direction: 'SHORT',
      stopTicks: 80_016,
      targetTicks: 79_926,
      contracts: 3,
    })
  })

  it('BUG-089: a price box reads a decimal comma, and says so when its text is not a price', async () => {
    const user = userEvent.setup()
    renderPractice()
    await startSession(user)
    await user.click(screen.getByRole('button', { name: /plan long/i }))
    await user.clear(screen.getByLabelText('Stop'))
    await user.type(screen.getByLabelText('Stop'), '19991,75{Enter}')
    expect(screen.getByLabelText('Stop')).toHaveValue('19991.75')

    await user.clear(screen.getByLabelText('Target'))
    await user.type(screen.getByLabelText('Target'), '2002x{Enter}')
    expect(screen.getByRole('alert')).toHaveTextContent('Not a price: write it like 20011.25 or 20011,25.')
    expect(screen.getByLabelText('Target')).toHaveValue('2002x')
    expect(screen.getByLabelText('Target')).toHaveAttribute('aria-invalid', 'true')
    // Put right, it is taken and the word goes.
    await user.clear(screen.getByLabelText('Target'))
    await user.type(screen.getByLabelText('Target'), '20030,5{Enter}')
    expect(screen.getByLabelText('Target')).toHaveValue('20030.50')
    expect(screen.queryByText(/Not a price/)).toBeNull()

    await user.type(screen.getByLabelText('Draw on liquidity'), 'high{Enter}')
    expect(screen.getByRole('alert')).toHaveTextContent('Not a price')
  })

  it('BUG-090: flipping mirrors the partial through the price with the stop and the target', async () => {
    const user = userEvent.setup()
    renderPractice()
    await startSession(user)
    await user.click(screen.getByRole('button', { name: /plan long/i }))
    await user.clear(screen.getByLabelText('Contracts'))
    await user.type(screen.getByLabelText('Contracts'), '2')
    // Forty ticks above the 20001.50 price; flipped, forty below.
    await user.type(screen.getByLabelText('Partial'), '20011.50{Enter}')
    await user.click(screen.getByRole('button', { name: /flip to short/i }))
    expect(screen.getByLabelText('Partial')).toHaveValue('19991.50')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /flip to long/i }))
    expect(screen.getByLabelText('Partial')).toHaveValue('20011.50')
  })

  it('BUG-091: a discarded draft takes its partial and its draw with it', async () => {
    const user = userEvent.setup()
    renderPractice()
    await startSession(user)
    await user.click(screen.getByRole('button', { name: /plan long/i }))
    await user.clear(screen.getByLabelText('Contracts'))
    await user.type(screen.getByLabelText('Contracts'), '2')
    await user.type(screen.getByLabelText('Partial'), '20011.50{Enter}')
    await user.type(screen.getByLabelText('Draw on liquidity'), '20030{Enter}')
    await user.click(screen.getByRole('button', { name: /discard/i }))

    await user.click(screen.getByRole('button', { name: /plan long/i }))
    expect(screen.getByLabelText('Partial')).toHaveValue('')
    expect(screen.getByLabelText('Draw on liquidity')).toHaveValue('')
  })

  it('flips a draft long into the mirror-image short through the price, and back', async () => {
    const user = userEvent.setup()
    renderPractice()
    await startSession(user)
    await user.click(screen.getByRole('button', { name: /plan long/i }))
    expect(screen.getByLabelText('Stop')).toHaveValue('19991.50')
    expect(screen.getByLabelText('Target')).toHaveValue('20021.50')

    await user.click(screen.getByRole('button', { name: /flip to short/i }))
    expect(screen.getByTestId('draft-title')).toHaveTextContent(/short/i)
    expect(screen.getByLabelText('Stop')).toHaveValue('20011.50')
    expect(screen.getByLabelText('Target')).toHaveValue('19981.50')

    await user.click(screen.getByRole('button', { name: /flip to long/i }))
    expect(screen.getByLabelText('Stop')).toHaveValue('19991.50')
  })

  it('BUG-058: shows whole cents while a fraction of a contract is typed, and refuses it', async () => {
    const user = userEvent.setup()
    renderPractice()
    await startSession(user)
    await user.click(screen.getByRole('button', { name: /plan long/i }))
    const stop = screen.getByLabelText('Stop')
    await user.clear(stop)
    await user.type(stop, '19991.25{Enter}') // 41 ticks of risk
    const contracts = screen.getByLabelText('Contracts')
    await user.clear(contracts)
    await user.type(contracts, '2.7')
    expect(screen.getByTestId('plan-numbers')).toHaveTextContent('41 ticks · $553.50')
    expect(screen.getByTestId('plan-numbers').textContent).not.toMatch(/\.\d{3}/)
    expect(screen.getByRole('alert')).toHaveTextContent(/whole number/)
    expect(screen.getByRole('button', { name: /place trade/i })).toBeDisabled()
  })

  it('steps ten candles at once with Next 10', async () => {
    const user = userEvent.setup()
    renderPractice()
    await startSession(user)
    await user.click(screen.getByRole('button', { name: /next 10/i }))
    await waitFor(() => expect(calls.find((c) => is(c, 'POST', '/advance'))?.body).toEqual({ bars: 10, timeframe: '5m' }))
  })

  it('stops playing when a step is refused, and says why', async () => {
    const server = happyServer()
    mockApi((call) =>
      is(call, 'POST', '/advance')
        ? { status: 409, body: { message: 'The replay has reached its end.' } }
        : server(call),
    )
    const user = userEvent.setup()
    renderPractice()
    await startSession(user)
    await user.click(screen.getByRole('button', { name: 'Play' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('The replay has reached its end.')
    await waitFor(() => expect(screen.getByRole('button', { name: 'Play' })).toBeInTheDocument())
    await new Promise((resolve) => setTimeout(resolve, 1200))
    expect(calls.filter((c) => is(c, 'POST', '/advance'))).toHaveLength(1)
  })
})

describe('Practice — the New York clock (T7)', () => {
  it('asks for the clock once, then again only when the replay reaches its next change', async () => {
    const server = happyServer()
    let asked = 0
    let cursor = CURSOR
    mockApi((call) => {
      if (is(call, 'GET', '/clock')) {
        asked++
        // The first layer can change at the next bar; the second not for a long while.
        return {
          body: {
            rules: { sessions: 'sessions-1.0.0', opens: 'opens-1.0.0', levels: 'levels-1.0.0' },
            windows: [],
            lines: [],
            nextChange: asked === 1 ? CURSOR + 300 : CURSOR + 99_999,
          },
        }
      }
      if (is(call, 'POST', '/advance')) {
        cursor += 300
        return {
          body: {
            state: { ...ANALYSING, cursor },
            bars: [{ time: cursor - 300, open: 80_006, high: 80_012, low: 80_004, close: 80_010, volume: 10 }],
          },
        }
      }
      return server(call)
    })
    const user = userEvent.setup()
    renderPractice()
    await startSession(user)
    await waitFor(() => expect(asked).toBe(1))
    // Its parts are switched in the layers menu (T38); the default ones are on as chips.
    expect(screen.getByRole('list', { name: 'Layers on' })).toHaveTextContent('Killzones')

    // The cursor reaches the change: asked again. Past it, with a later change, not again.
    await user.click(screen.getByRole('button', { name: /next bar/i }))
    await waitFor(() => expect(asked).toBe(2))
    await user.click(screen.getByRole('button', { name: /next bar/i }))
    await waitFor(() => expect(screen.getByTestId('practice-cursor')).toBeInTheDocument())
    expect(asked).toBe(2)
  })
})

describe('Practice — any timeframe (T34)', () => {
  it('switches mid-replay, steps in the viewed candles, and completes a forming candle in place', async () => {
    const server = happyServer()
    // Viewed on 15m, five minutes into the candle that opened at the cursor's last boundary.
    const forming = { time: CURSOR, open: 80_006, high: 80_011, low: 80_004, close: 80_010, volume: 10 }
    const formed = { ...forming, high: 80_030, close: 80_025, volume: 30 }
    const fifteen = [{ time: CURSOR - 900, open: 79_990, high: 80_012, low: 79_985, close: 80_006, volume: 30 }, forming]
    mockApi((call) => {
      if (call.method === 'GET' && call.url === `/api/practice/sessions/${ID}?timeframe=15m`) {
        return { body: { state: { ...ANALYSING, cursor: CURSOR + 300 }, bars: fifteen } }
      }
      if (is(call, 'POST', '/advance')) {
        return { body: { state: { ...ANALYSING, cursor: CURSOR + 900 }, bars: [formed] } }
      }
      return server(call)
    })
    let adapter = new FakeChartAdapter()
    render(<PracticeView adapterFactory={() => (adapter = new FakeChartAdapter())} />)
    const user = userEvent.setup()
    await startSession(user)

    const timeframes = screen.getByRole('group', { name: 'Chart timeframe' })
    expect(within(timeframes).getByRole('button', { name: '5m' })).toHaveAttribute('aria-pressed', 'true')
    await user.click(within(timeframes).getByRole('button', { name: '15m' }))
    await waitFor(() =>
      expect(within(timeframes).getByRole('button', { name: '15m' })).toHaveAttribute('aria-pressed', 'true'),
    )
    expect(adapter.bars).toEqual(fifteen)

    await user.click(screen.getByRole('button', { name: /next bar/i }))
    await waitFor(() => expect(adapter.updates).toContainEqual(formed))
    expect(calls.find((c) => is(c, 'POST', '/advance'))?.body).toEqual({ bars: 1, timeframe: '15m' })
    // Completed where it stood: no second candle at the same time, no new series.
    expect(adapter.bars).toEqual(fifteen)
    expect(adapter.updates.filter((b) => b.time === CURSOR)).toEqual([formed])
  })
})

describe('Practice — detected setups (T9)', () => {
  const CURRICULUM = {
    levels: [],
    concepts: [
      {
        concept: { id: 'ICT-20', name: 'Fair value gap (BISI / SIBI)', summary: 'Three consecutive candles.' },
        detectors: [{ detector: 'FVG', version: 'fvg-1.0.0', conceptId: 'ICT-20' }],
      },
    ],
  }
  const LAYER = {
    rules: [],
    rolls: [],
    setups: [
      {
        detector: 'FVG',
        shape: 'ZONE',
        direction: 'BULLISH',
        start: CURSOR - 900,
        anchor: CURSOR - 600,
        end: CURSOR - 300,
        confirmedAt: CURSOR,
        lowTicks: 80_001,
        highTicks: 80_004,
      },
    ],
  }

  it('asks for nothing until a rule is switched on, then follows the cursor one request at a time', async () => {
    const server = happyServer()
    let cursor = CURSOR
    const answers: Array<(body: unknown) => void> = []
    mockApi((call) => {
      if (is(call, 'GET', '/api/concepts')) return { body: CURRICULUM }
      if (call.method === 'GET' && call.url.includes('/setups?')) {
        // Held until the test answers, like a slow server.
        return { body: new Promise((resolve) => answers.push(resolve)) }
      }
      if (is(call, 'POST', '/advance')) {
        cursor += 300
        return {
          body: {
            state: { ...ANALYSING, cursor },
            bars: [{ time: cursor - 300, open: 80_006, high: 80_012, low: 80_004, close: 80_010, volume: 10 }],
          },
        }
      }
      return server(call)
    })
    const setupCalls = () => calls.filter((c) => c.url.includes('/setups?'))
    const user = userEvent.setup()
    renderPractice()
    await startSession(user)

    // Off by default: a learner finds a gap before being shown one. Switched in the layers menu (T38).
    await user.click(screen.getByRole('button', { name: /Layers/ }))
    const fvg = await screen.findByRole('switch', { name: 'FVG' })
    expect(fvg).toHaveAttribute('aria-checked', 'false')
    expect(screen.getByText('FVG', { selector: 'span' }).getAttribute('title')).toMatch(/Fair value gap .*ICT-20.*fvg-1\.0\.0/)
    expect(setupCalls()).toHaveLength(0)

    await user.click(fvg)
    await user.keyboard('{Escape}')
    await waitFor(() => expect(setupCalls()).toHaveLength(1))
    expect(setupCalls()[0].url).toBe(`/api/practice/sessions/${ID}/setups?detectors=FVG&timeframe=5m`)

    // Painted with the chart (T37): nothing until an answer arrives, then the setups layer reaches the renderer.
    const painted = () => {
      const { ctx, calls } = recorder()
      for (const layer of charts[0].layers) if (layer.zOrder === 'normal') layer.paint(ctx, { width: 800, height: 400 })
      return calls.filter((c) => c.op === 'rect' && String(c.args) === '0,0,800,400').length
    }
    expect(painted()).toBe(0)
    // Two bars while the first answer is still out: no second request yet...
    await user.click(screen.getByRole('button', { name: /next bar/i }))
    await user.click(screen.getByRole('button', { name: /next bar/i }))
    await waitFor(() => expect(cursor).toBe(CURSOR + 600))
    expect(setupCalls()).toHaveLength(1)
    // ...and once it comes, exactly one more, for where the replay is now.
    await act(async () => answers[0](LAYER))
    await waitFor(() => expect(painted()).toBe(1))
    await waitFor(() => expect(setupCalls()).toHaveLength(2))
    await act(async () => answers[1](LAYER))
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(setupCalls()).toHaveLength(2)

    // Switched off — by its chip's × — the replay moves on without asking.
    await user.click(screen.getByRole('button', { name: 'Remove FVG' }))
    await user.click(screen.getByRole('button', { name: /next bar/i }))
    await waitFor(() => expect(cursor).toBe(CURSOR + 900))
    expect(setupCalls()).toHaveLength(2)
  })
})

describe('Practice — waiting for a setup', () => {
  it('keeps the bracket entry on the market price as the replay moves', async () => {
    // The order is at market, so the entry is wherever price is now. Stop and target are the
    // learner's absolute levels and stay put; the risk between them and the entry changes.
    const server = happyServer()
    mockApi((call) =>
      is(call, 'POST', '/advance')
        ? {
            body: {
              state: { ...ANALYSING, cursor: CURSOR + 300 },
              bars: [
                { time: CURSOR, open: 80_006, high: 80_012, low: 80_004, close: 80_010, volume: 10 },
              ],
            },
          }
        : server(call),
    )
    const user = userEvent.setup()
    renderPractice()
    await startSession(user)
    await user.click(screen.getByRole('button', { name: /plan long/i }))
    expect(screen.getByTestId('plan-numbers')).toHaveTextContent('40 ticks · $200.00')

    await user.click(screen.getByRole('button', { name: /next bar/i }))

    await waitFor(() => expect(screen.getByText(/next price after 20002\.50/)).toBeInTheDocument())
    // Stop still 79,966; entry now 80,010: 44 ticks of risk.
    expect(screen.getByTestId('plan-numbers')).toHaveTextContent('44 ticks · $220.00')
    expect(screen.getByLabelText('Stop')).toHaveValue('19991.50')
  })
})

describe('Practice — refusals', () => {
  it("shows the server's reason when a plan is refused", async () => {
    mockApi(
      happyServer({
        plan: {
          status: 409,
          body: { message: 'The market is closed at this point in the replay.' },
        },
      }),
    )
    const user = userEvent.setup()
    renderPractice()
    await startSession(user)
    await user.click(screen.getByRole('button', { name: /plan long/i }))
    await user.click(screen.getByRole('button', { name: /place trade/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/market is closed/)
    expect(screen.getByTestId('practice-status')).toHaveTextContent('Flat')
  })
})

describe('Practice — journal', () => {
  it('scores each session by its closed trades only', async () => {
    const rows: SessionSummary[] = [
      {
        id: 'a',
        instrument: 'DEMO-NQ',
        timeframe: '5m',
        status: 'IN_TRADE',
        createdAt: CURSOR,
        trades: 1,
        score: NO_SCORE,
      },
      {
        id: 'b',
        instrument: 'NQ',
        timeframe: '1m',
        status: 'COMPLETE',
        createdAt: CURSOR - 60,
        trades: 3,
        score: { closedTrades: 3, wins: 2, losses: 1, totalR: 3, netPnlCents: 79_250 },
      },
    ]
    mockApi((call) =>
      is(call, 'GET', '/api/practice/sessions') ? { body: rows } : { status: 404, body: {} },
    )
    renderPractice()

    const [running, done] = await screen.findAllByTestId('journal-row')
    expect(running).toHaveTextContent('In trade')
    expect(running).not.toHaveTextContent('$')
    // No R multiple and no win rate for a trade still running.
    expect(running).not.toHaveTextContent(/\d\.\d\dR|%/)
    expect(within(running).getByRole('button')).toHaveTextContent('Resume')

    expect(done).toHaveTextContent('Ended')
    expect(done).toHaveTextContent('67%')
    expect(done).toHaveTextContent('+3.00R')
    expect(done).toHaveTextContent('$792.50')
    expect(within(done).getByRole('button')).toHaveTextContent('Review')
  })

  const journalRow = (status: SessionSummary['status']): SessionSummary => ({
    id: ID,
    instrument: 'DEMO-NQ',
    timeframe: '5m',
    status,
    createdAt: CURSOR,
    trades: 1,
    score: NO_SCORE,
  })

  it('resumes a running trade where it was left: placed, its levels kept, and closable', async () => {
    mockApi((call) => {
      if (is(call, 'GET', '/api/practice/sessions')) return { body: [journalRow('IN_TRADE')] }
      if (is(call, 'GET', `/api/practice/sessions/${ID}`)) {
        return { body: { state: { ...ANALYSING, status: 'IN_TRADE', trades: [ENTERED] }, bars: BARS } }
      }
      return { status: 404, body: { message: 'not found' } }
    })
    const user = userEvent.setup()
    renderPractice()
    await user.click(within(await screen.findByTestId('journal-row')).getByRole('button', { name: 'Resume' }))

    expect(await screen.findByText(/Trade 1 · Long 1 — placed/)).toBeInTheDocument()
    expect(screen.getByTestId('practice-status')).toHaveTextContent('In trade')
    const trade = screen.getByRole('region', { name: 'Trade' })
    expect(trade).toHaveTextContent('19990.00') // the stop as it was placed
    expect(trade).toHaveTextContent('20021.50') // and the target
    expect(screen.getByRole('button', { name: /close at market/i })).toBeEnabled()
    // Resuming is a read: nothing was moved or placed to get here.
    expect(calls.filter((c) => c.method === 'POST')).toHaveLength(0)
  })

  it('reviews an ended session: its result, and no way to trade on', async () => {
    mockApi((call) => {
      if (is(call, 'GET', '/api/practice/sessions')) return { body: [journalRow('COMPLETE')] }
      if (is(call, 'GET', `/api/practice/sessions/${ID}`)) {
        return {
          body: {
            state: { ...ANALYSING, status: 'COMPLETE', cursor: CURSOR + 1500, trades: [CLOSED], score: AFTER_ONE, seed: SEED },
            bars: BARS,
          },
        }
      }
      return { status: 404, body: { message: 'not found' } }
    })
    const user = userEvent.setup()
    renderPractice()
    await user.click(within(await screen.findByTestId('journal-row')).getByRole('button', { name: 'Review' }))

    await waitFor(() => expect(screen.getByTestId('practice-status')).toHaveTextContent('Ended'))
    expect(screen.getAllByTestId('trade-row')[0]).toHaveTextContent('Ambiguous')
    expect(screen.queryByRole('button', { name: /plan long/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /close at market/i })).not.toBeInTheDocument()
  })

  it('says so when a session cannot be opened', async () => {
    mockApi((call) => {
      if (is(call, 'GET', '/api/practice/sessions')) return { body: [journalRow('ANALYSING')] }
      return { status: 500, body: {} }
    })
    const user = userEvent.setup()
    renderPractice()
    await user.click(within(await screen.findByTestId('journal-row')).getByRole('button', { name: 'Resume' }))
    expect(await screen.findByText('Could not load this session.')).toBeInTheDocument()
  })
})

/** Monday 09:35 New York on the fictional week: what a real session shows instead of its date. */
const SHOWN_MONDAY_0935 = Date.UTC(2000, 0, 10, 9, 35) / 1000
/** The real moment behind it: Monday 7 April 2025, 10:05 New York. */
const REAL_START = Date.UTC(2025, 3, 7, 14, 5) / 1000

/** The happy server, playing a session on real NQ behind a hidden date (DEC-025). */
function realServer() {
  const simulated = happyServer()
  const real = (s: PracticeState): PracticeState => ({
    ...s,
    instrument: 'NQ',
    dataClass: 'IMPORTED_PRIVATE',
    disclaimer: undefined,
    generatorVersion: undefined,
    seed: undefined,
    timeBasis: 'NEW_YORK_WALL_CLOCK',
    dataSource: 'massive',
    startTime: SHOWN_MONDAY_0935,
    cursor: s.status === 'ANALYSING' && s.trades.length === 0 ? SHOWN_MONDAY_0935 : SHOWN_MONDAY_0935 + 300,
    trades: s.trades.map((t) => (t.result ? { ...t, result: { ...t.result, resolution: 'M1' } } : t)),
    revealedStartTime: s.status === 'COMPLETE' ? REAL_START : undefined,
  })
  return (call: Call) => {
    const response = simulated(call)
    const body = response.body as { state?: PracticeState } | PracticeState
    if (body && 'state' in body && body.state) {
      return { ...response, body: { ...body, state: real(body.state) } }
    }
    if (body && 'status' in body && 'id' in body) return { ...response, body: real(body) }
    return response
  }
}

describe('Practice — real NQ behind a hidden date (T4, DEC-025)', () => {
  beforeEach(() => mockApi(realServer()))

  it('asks for real NQ, with no filters unless some are chosen', async () => {
    const user = userEvent.setup()
    renderPractice()
    await startSession(user)
    expect(calls.find((c) => is(c, 'POST', '/api/practice/sessions'))?.body).toEqual({
      instrument: 'NQ',
      timeframe: '5m',
    })
  })

  it('asks for the ICT session and the weekday chosen', async () => {
    const user = userEvent.setup()
    renderPractice()
    await user.selectOptions(await screen.findByRole('combobox', { name: /^session/i }), 'LONDON')
    await user.selectOptions(screen.getByRole('combobox', { name: /^day/i }), 'TUESDAY')
    await startSession(user)
    expect(calls.find((c) => is(c, 'POST', '/api/practice/sessions'))?.body).toEqual({
      instrument: 'NQ',
      timeframe: '5m',
      killzone: 'LONDON',
      weekday: 'TUESDAY',
    })
  })

  it('BUG-067: never offers Asia on a Friday, when the market is shut', async () => {
    const user = userEvent.setup()
    renderPractice()
    const session = await screen.findByRole('combobox', { name: /^session/i })
    const day = screen.getByRole('combobox', { name: /^day/i })
    await user.selectOptions(session, 'ASIA')
    expect(screen.getByRole('option', { name: 'Friday' })).toBeDisabled()
    expect(screen.getByRole('option', { name: 'Thursday' })).toBeEnabled()
    await user.selectOptions(session, 'LONDON')
    await user.selectOptions(day, 'FRIDAY')
    expect(screen.getByRole('option', { name: /^Asia/ })).toBeDisabled()
  })

  it('shows New York wall-clock time and keeps the date hidden', async () => {
    const adapter = new FakeChartAdapter()
    const user = userEvent.setup()
    render(<PracticeView adapterFactory={() => adapter} />)
    await startSession(user)

    expect(screen.getByTestId('practice-cursor')).toHaveTextContent('Mon 09:35 NY')
    expect(screen.getByTestId('date-hidden')).toHaveTextContent('date hidden')
    expect(adapter.mountOptions?.axisTimezone).toBe('wall-clock')
    expect(screen.queryByText(/SIMULATED DATA/)).not.toBeInTheDocument()
  })

  it('keeps the date hidden after a trade, reveals it when the session ends, and names the source', async () => {
    const user = userEvent.setup()
    renderPractice()
    await startSession(user)
    await lockLong(user)
    await user.click(screen.getByRole('button', { name: /skip to result/i }))
    await screen.findByTestId('result-outcome')

    // Another trade could still use the date.
    expect(screen.getByTestId('date-hidden')).toBeInTheDocument()
    expect(screen.queryByTestId('revealed-date')).not.toBeInTheDocument()
    expect(screen.getByText(/Resolved on one-minute bars/)).toBeInTheDocument()

    await endSession(user)
    const revealed = screen.getByTestId('revealed-date')
    expect(revealed).toHaveTextContent('Monday 7 April 2025')
    expect(revealed).toHaveTextContent('10:05')
    expect(screen.queryByTestId('date-hidden')).not.toBeInTheDocument()
    expect(screen.queryByTestId('result-seed')).not.toBeInTheDocument()
    expect(screen.getByTestId('provenance')).toHaveTextContent(/Real NQ history from massive/)
  })
})
