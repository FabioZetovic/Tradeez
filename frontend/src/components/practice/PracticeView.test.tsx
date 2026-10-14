import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { PracticeView } from './PracticeView'
import { FakeChartAdapter } from '@/test/fakeChartAdapter'
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

const is = (call: Call, method: string, suffix: string) =>
  call.method === method && call.url.endsWith(suffix)

/**
 * A server that plays one replay through: create, lock, one bar, skip to an ambiguous result, a
 * second trade, and the end.
 */
function happyServer(overrides: Partial<Record<'plan', { status: number; body: unknown }>> = {}) {
  let locked = 0
  return (call: Call) => {
    if (is(call, 'GET', '/api/practice/sessions')) return { body: [] }
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

function renderPractice() {
  return render(<PracticeView adapterFactory={() => new FakeChartAdapter()} />)
}

async function startSession(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('button', { name: /start a session/i }))
  await screen.findByTestId('price-chart')
}

async function lockLong(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: /place long/i }))
  await user.click(screen.getByRole('button', { name: /lock plan/i }))
  await screen.findByText(/— locked/)
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

    await user.click(screen.getByRole('button', { name: /place long/i }))
    const numbers = screen.getByTestId('plan-numbers')
    expect(numbers).toHaveTextContent('40 ticks · $200.00')
    expect(numbers).toHaveTextContent('80 ticks · $400.00')
    expect(numbers).toHaveTextContent('2.00')

    // A stop above the price is not a long.
    const stop = screen.getByLabelText('Stop')
    await user.clear(stop)
    await user.type(stop, '20005{Enter}')
    expect(screen.getByRole('alert')).toHaveTextContent(/stop below the current price/)
    expect(screen.getByRole('button', { name: /lock plan/i })).toBeDisabled()

    await user.clear(stop)
    await user.type(stop, '19990{Enter}')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /lock plan/i }))
    await waitFor(() => expect(screen.getByTestId('practice-status')).toHaveTextContent('In trade'))

    const lock = calls.find((c) => is(c, 'POST', '/plan'))
    expect(lock?.body).toEqual({
      direction: 'LONG',
      stopTicks: 79_960,
      targetTicks: 80_086,
      contracts: 1,
    })
    // Nothing about the outcome is on screen yet, and neither is the entry price.
    expect(screen.getByText(/Trade 1 · Long 1 — locked/)).toBeInTheDocument()
    expect(screen.getByTestId('trade-entry')).toHaveTextContent('at the next price')
    expect(screen.queryByTestId('result-outcome')).not.toBeInTheDocument()
    expect(screen.getAllByTestId('trade-row')[0]).toHaveTextContent('Running')
    expect(screen.getByTestId('score-trades')).toHaveTextContent('0')
    // No ending a session with a trade still running.
    expect(screen.queryByRole('button', { name: /end session/i })).not.toBeInTheDocument()
  })

  it('reveals the entry with the next bar, then the result — labelled as an assumption', async () => {
    const user = userEvent.setup()
    renderPractice()
    await startSession(user)
    await lockLong(user)

    await user.click(screen.getByRole('button', { name: /next bar/i }))
    await waitFor(() => expect(screen.getByTestId('trade-entry')).toHaveTextContent('20001.75'))
    expect(calls.find((c) => is(c, 'POST', '/advance'))?.body).toEqual({ bars: 1 })

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
    expect(screen.getByRole('button', { name: /place long/i })).toBeInTheDocument()
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
    expect(screen.getByText(/Trade 2 · Long 1 — locked/)).toBeInTheDocument()
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
    expect(screen.getByText(/Place a bracket/i)).toBeInTheDocument()
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
    expect(screen.queryByRole('button', { name: /place long/i })).not.toBeInTheDocument()
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
    expect(screen.getByRole('group', { name: 'New York clock' })).toBeInTheDocument()

    // The cursor reaches the change: asked again. Past it, with a later change, not again.
    await user.click(screen.getByRole('button', { name: /next bar/i }))
    await waitFor(() => expect(asked).toBe(2))
    await user.click(screen.getByRole('button', { name: /next bar/i }))
    await waitFor(() => expect(screen.getByTestId('practice-cursor')).toBeInTheDocument())
    expect(asked).toBe(2)
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
    await user.click(screen.getByRole('button', { name: /place long/i }))
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
    await user.click(screen.getByRole('button', { name: /place long/i }))
    await user.click(screen.getByRole('button', { name: /lock plan/i }))

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
