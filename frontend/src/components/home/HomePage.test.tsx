import { render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { HomePage } from './HomePage'
import { FakeChartAdapter } from '@/test/fakeChartAdapter'
import { getCurriculum, type Curriculum } from '@/lib/api/concepts'
import { listDrillKinds, type DrillKindView } from '@/lib/api/drills'
import { getLearning, type Learning } from '@/lib/api/learning'
import { getBars, listInstruments, type BarSeries, type Instrument } from '@/lib/api/marketData'
import { getChartClock } from '@/lib/api/clock'
import { getStatistics, listSessions, type SessionSummary, type Statistics } from '@/lib/api/practice'
import { getNews, type News } from '@/lib/api/news'

vi.mock('@/lib/api/concepts', () => ({ getCurriculum: vi.fn() }))
vi.mock('@/lib/api/drills', () => ({ listDrillKinds: vi.fn() }))
vi.mock('@/lib/api/learning', () => ({ getLearning: vi.fn() }))
vi.mock('@/lib/api/marketData', () => ({ getBars: vi.fn(), listInstruments: vi.fn() }))
vi.mock('@/lib/api/clock', () => ({ getChartClock: vi.fn() }))
vi.mock('@/lib/api/practice', () => ({ getStatistics: vi.fn(), listSessions: vi.fn() }))
vi.mock('@/lib/api/news', () => ({ getNews: vi.fn() }))

const USER = { id: 'u1', email: 'fabio@example.com', displayName: 'Fabio Zetović', createdAt: '2026-09-17T00:00:00Z' }

const concept = (id: string, name: string) => ({ concept: { id, name, summary: `${name} in one sentence.` }, detectors: [] })
const CURRICULUM = {
  levels: [],
  concepts: [concept('ICT-01', 'Swing high / swing low'), concept('ICT-03', 'Market structure'), concept('ICT-20', 'Fair value gap')],
} as unknown as Curriculum

const progress = (id: string, state: string, recentAccuracy?: number) => ({ id, state, recentAccuracy })
const LEARNING = {
  summary: { concepts: 72, complete: 18, rated: 9, solid: 1, due: 2, ratedAttempts: 30, predictions: 4 },
  concepts: [progress('ICT-01', 'SOLID', 0.9), progress('ICT-03', 'LEARNING', 0.75), progress('ICT-20', 'LEARNING')],
  suggested: 'ICT-20',
  // Not in curriculum order: shown in it.
  due: ['ICT-20', 'ICT-01'],
} as unknown as Learning

const kind = (k: string, mode: string, conceptId: string) => ({ kind: k, mode, conceptId }) as DrillKindView
const KINDS = [
  kind('SWING_HIGHS', 'IDENTIFY', 'ICT-01'),
  kind('FVG', 'IDENTIFY', 'ICT-20'),
  kind('BOS_CHOCH', 'CLASSIFY', 'ICT-03'),
  kind('NEXT_TARGET', 'PREDICT', 'ICT-01'), // a prediction: practice, not counted
]

const SESSIONS = [{ trades: 3 }, { trades: 1 }] as SessionSummary[]
const STATISTICS = { overall: { trades: 4, totalR: -1.25 } } as Statistics

const NQ = { symbol: 'NQ', tickSize: 0.25, exchangeTimezone: 'America/Chicago', dataTo: Date.parse('2026-10-02T21:00:00Z') / 1000 } as Instrument
const bar = (iso: string, close: number) => ({ time: Date.parse(iso) / 1000, open: close, high: close, low: close, close, volume: 1 })
const BARS = {
  bars: [bar('2026-10-01T20:55:00Z', 80_000), bar('2026-10-02T13:30:00Z', 80_100), bar('2026-10-02T20:55:00Z', 80_040)],
} as BarSeries

function mockAll() {
  vi.mocked(getCurriculum).mockResolvedValue(CURRICULUM)
  vi.mocked(getLearning).mockResolvedValue(LEARNING)
  vi.mocked(listDrillKinds).mockResolvedValue(KINDS)
  vi.mocked(listSessions).mockResolvedValue(SESSIONS)
  vi.mocked(getStatistics).mockResolvedValue(STATISTICS)
  vi.mocked(listInstruments).mockResolvedValue([NQ])
  vi.mocked(getBars).mockResolvedValue(BARS)
  vi.mocked(getChartClock).mockResolvedValue({ rules: {}, windows: [], lines: [] } as never)
  vi.mocked(getNews).mockResolvedValue({ events: [] } as unknown as News)
}

function renderHome(adapter = new FakeChartAdapter()) {
  render(
    <MemoryRouter>
      <HomePage user={USER} adapterFactory={() => adapter} />
    </MemoryRouter>,
  )
  return adapter
}

const card = (name: string) => screen.getByRole('region', { name })
const link = (name: string | RegExp) => screen.getByRole('link', { name })

beforeEach(mockAll)
afterEach(() => {
  vi.useRealTimers()
  vi.clearAllMocks()
})

describe('Home (T39)', () => {
  it('greets by the hour, by first name, with the date', async () => {
    for (const [iso, greeting] of [
      ['2026-10-23T08:00:00', 'Good morning'],
      ['2026-10-23T14:00:00', 'Good afternoon'],
      ['2026-10-23T20:00:00', 'Good evening'],
    ] as const) {
      vi.useFakeTimers({ toFake: ['Date'] })
      vi.setSystemTime(new Date(iso))
      const { unmount } = render(
        <MemoryRouter>
          <HomePage user={USER} adapterFactory={() => new FakeChartAdapter()} />
        </MemoryRouter>,
      )
      expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(`${greeting}, Fabio.`)
      expect(screen.getByText('Friday, 23 October')).toBeInTheDocument()
      unmount()
      vi.useRealTimers()
    }
  })

  it('shows each step with its real progress — Learn and Drills as bars, Practice and Chart as figures', async () => {
    renderHome()
    const learn = await screen.findByRole('progressbar', { name: '18 of 72 concepts complete' })
    expect(learn).toHaveAttribute('aria-valuenow', '25')
    // Pattern drills only: one of three is on a solid concept.
    expect(await screen.findByRole('progressbar', { name: '1 of 3 drills solid' })).toHaveAttribute('aria-valuenow', '33')
    expect(link(/Practice/)).toHaveTextContent('2 sessions · 4 trades')
    expect(link(/Learn/)).toHaveAttribute('href', '/learn')
    expect(link(/Drills/)).toHaveAttribute('href', '/drills')
    expect(link(/STEP 4/)).toHaveAttribute('href', '/chart')
  })

  it('suggests the next lesson, and lists what is due in curriculum order with its recent accuracy', async () => {
    renderHome()
    const next = await screen.findByRole('region', { name: 'Up next' })
    await waitFor(() => expect(next).toHaveTextContent('Fair value gap'))
    expect(next).toHaveTextContent('Fair value gap in one sentence.')
    expect(within(next).getByRole('link', { name: /Continue lesson/ })).toHaveAttribute('href', '/learn/ICT-20')

    const rows = within(card('Due for review')).getAllByRole('link')
    expect(rows.map((r) => r.getAttribute('href'))).toEqual(['/learn/ICT-01', '/learn/ICT-20'])
    expect(within(rows[0]).getByRole('img', { name: '90% recently' })).toBeInTheDocument()
    expect(within(rows[1]).getByRole('img', { name: 'Not graded yet' })).toBeInTheDocument()
  })

  it('adds up the practice so far, net, in R', async () => {
    renderHome()
    expect(await screen.findByTestId('total-r')).toHaveTextContent('−1.25R')
    expect(screen.getByTestId('total-r')).toHaveClass('text-down')
    expect(card('Practice so far')).toHaveTextContent('4 practice trades, net of costs')
    expect(within(card('Practice so far')).getByRole('link', { name: /Open journal/ })).toHaveAttribute('href', '/practice')
  })

  it('a new account is told what to do, never shown an invented number', async () => {
    vi.mocked(getLearning).mockResolvedValue({
      ...LEARNING,
      summary: { ...LEARNING.summary, complete: 0 },
      concepts: [],
      suggested: undefined,
      due: [],
    })
    vi.mocked(listSessions).mockResolvedValue([])
    vi.mocked(getStatistics).mockResolvedValue({ overall: { trades: 0, totalR: 0 } } as Statistics)
    renderHome()
    expect(await screen.findByText("Everything open is complete — review what's due.")).toBeInTheDocument()
    expect(card('Due for review')).toHaveTextContent('Nothing due today.')
    expect(await screen.findByText('No trades yet — start a session.')).toBeInTheDocument()
    expect(screen.queryByTestId('total-r')).toBeNull()
    expect(link(/Practice/)).toHaveTextContent('0 sessions · 0 trades')
    expect(await screen.findByRole('progressbar', { name: '0 of 3 drills solid' })).toHaveAttribute('aria-valuenow', '0')
  })

  it('says so when progress cannot be loaded, and shows no bar for it', async () => {
    vi.mocked(getLearning).mockRejectedValue(new Error('503'))
    renderHome()
    expect(await screen.findByText('Your progress could not be loaded.')).toBeInTheDocument()
    expect(screen.queryAllByRole('progressbar')).toHaveLength(0)
  })

  it('draws the latest NQ session, still, from the close before it, and links to the Chart tab', async () => {
    const adapter = renderHome()
    await waitFor(() => expect(adapter.bars).toHaveLength(2))
    // The session that began at 17:00 New York on Thursday; Thursday's candle only measures the change.
    expect(adapter.mountOptions?.still).toBe(true)
    expect(screen.getByTestId('home-price')).toHaveTextContent('20010.00')
    expect(screen.getByTestId('home-change')).toHaveTextContent('+10.00')
    expect(link(/NQ, the latest session/)).toHaveAttribute('href', '/chart')
    // Two days back, on 5-minute candles.
    expect(getBars).toHaveBeenCalledWith({ symbol: 'NQ', timeframe: '5m', from: NQ.dataTo! - 2 * 86_400, to: NQ.dataTo })
  })

  it("lists today's and tomorrow's high-impact releases, New York time, and opens the week", async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-27T16:00:00Z')) // Tuesday, noon in New York
    const release = (key: string, iso: string, impact: 'HIGH' | 'MEDIUM') => ({
      key,
      title: `${key} title`,
      agency: 'BLS',
      impact,
      at: Date.parse(iso) / 1000,
      link: '',
    })
    vi.mocked(getNews).mockResolvedValue({
      events: [
        release('CPI', '2026-10-27T12:30:00Z', 'HIGH'), // today 08:30
        release('JOLTS', '2026-10-27T14:00:00Z', 'MEDIUM'), // medium: not here
        release('FOMC', '2026-10-28T18:00:00Z', 'HIGH'), // tomorrow 14:00
        release('NFP', '2026-10-30T12:30:00Z', 'HIGH'), // Friday: not here
      ],
    } as unknown as News)
    renderHome()
    const card = await screen.findByRole('region', { name: 'News today' })
    await waitFor(() => expect(within(card).getAllByRole('listitem')).toHaveLength(2))
    expect(within(card).getAllByRole('listitem').map((li) => li.textContent)).toEqual([
      'Today 08:30CPI title',
      'Tomorrow 14:00FOMC title',
    ])
    expect(within(card).getByRole('link', { name: /Week ahead/ })).toHaveAttribute('href', '/news')
  })

  it('says when there is no high-impact news, or no calendar', async () => {
    renderHome()
    expect(await within(card('News today')).findByText('No high-impact news today or tomorrow.')).toBeInTheDocument()
  })

  it('a calendar that could not be loaded is said to be so', async () => {
    vi.mocked(getNews).mockRejectedValue(new Error('503'))
    renderHome()
    expect(await within(card('News today')).findByText('The calendar could not be loaded.')).toBeInTheDocument()
  })
})
