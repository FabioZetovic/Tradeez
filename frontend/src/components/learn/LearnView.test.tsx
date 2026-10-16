import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { LearnView } from './LearnView'
import { FakeChartAdapter } from '@/test/fakeChartAdapter'
import type { Curriculum } from '@/lib/api/concepts'
import type { Lesson } from '@/lib/api/lessons'

const T0 = 1_710_338_400

const concept = (id: string, name: string, level: number, status: Lesson['status'] = 'DESCRIBED') => ({
  id,
  name,
  level,
  detectability: 'D',
  status,
  statusNote: '',
  rules: [],
  summary: `${name} explained.`,
  prerequisites: [],
})

const FVG_RULE = {
  detector: 'FVG',
  version: 'fvg-1.0.0',
  conceptId: 'ICT-20',
  parameters: {},
  methodologySource: 'ICT fair value gap, as defined by rule fvg-1.0.0',
}

const CURRICULUM: Curriculum = {
  levels: [
    { level: 0, theme: 'Foundations', concepts: 'F-1', unlocksWhen: '' },
    { level: 3, theme: 'Imbalances', concepts: 'ICT-20', unlocksWhen: '' },
  ],
  concepts: [
    { concept: concept('F-1', 'Candle anatomy', 0), detectors: [] },
    { concept: concept('ICT-20', 'Fair value gap', 3, 'IMPLEMENTED'), detectors: [FVG_RULE] },
  ],
}

const bars = Array.from({ length: 40 }, (_, i) => ({
  time: T0 + i * 300,
  open: 80_000,
  high: 80_010,
  low: 79_990,
  close: 80_005,
  volume: 1,
}))

const example = (i: number) => ({
  detector: 'FVG',
  label: 'Bullish gap',
  explanation: [`Candle 1's high is below candle 3's low (example ${i}).`],
  timeframe: '5m',
  bars,
  setup: {
    detector: 'FVG',
    shape: 'ZONE' as const,
    direction: 'BULLISH' as const,
    start: T0 + 29 * 300,
    anchor: T0 + 30 * 300,
    end: T0 + 31 * 300,
    confirmedAt: T0 + 32 * 300,
    lowTicks: 80_004,
    highTicks: 80_014,
  },
  rule: FVG_RULE,
})

const FVG_LESSON: Lesson = {
  id: 'ICT-20',
  name: 'Fair value gap',
  level: 3,
  detectability: 'D',
  status: 'IMPLEMENTED',
  statusNote: 'fvg-1.0.0, re-measured on real NQ',
  summary: 'Three consecutive candles where the middle candle moved so fast that the wicks do not overlap.',
  prerequisites: [{ id: 'F-1', name: 'Candle anatomy' }],
  unlocks: [],
  rules: [
    {
      detector: 'FVG',
      version: 'fvg-1.0.0',
      parameters: {},
      definition: 'Bullish: candle 1 high below candle 3 low, at least 25% of the average range.',
      methodologySource: 'ICT fair value gap, as defined by rule fvg-1.0.0',
    },
  ],
  shownOn: 'SETUPS',
  drills: [{ kind: 'FVG_IDENTIFICATION', mode: 'IDENTIFY', title: 'Fair value gaps' }],
  instrument: 'NQ',
  tickSize: 0.25,
  exchangeTimezone: 'America/Chicago',
  timeframe: '5m',
  examples: [1, 2, 3].map(example),
}

const CANDLE_LESSON: Lesson = {
  ...FVG_LESSON,
  id: 'F-1',
  name: 'Candle anatomy',
  level: 0,
  status: 'DESCRIBED',
  statusNote: '',
  summary: 'A candle summarises a time period.',
  prerequisites: [],
  unlocks: [{ id: 'ICT-20', name: 'Fair value gap' }],
  rules: [],
  shownOn: undefined,
  drills: [],
  examples: [],
  examplesNote: 'Tradeez does not detect this concept yet, so there are no examples of it to show.',
}

let urls: string[] = []

function mockApi(lesson: (url: string) => { status?: number; body: unknown }) {
  urls = []
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string) => {
      urls.push(url)
      const { status = 200, body } = url.endsWith('/api/concepts') ? { body: CURRICULUM } : lesson(url)
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

const lessons = (url: string) => ({
  body: url.includes('/api/concepts/F-1/') ? CANDLE_LESSON : { ...FVG_LESSON, timeframe: new URL(url, 'http://x').searchParams.get('timeframe') ?? '5m' },
})

const lessonRequests = () => urls.filter((u) => u.includes('/lesson'))

function renderView(onPractise = vi.fn()) {
  render(<LearnView onPractise={onPractise} adapterFactory={() => new FakeChartAdapter()} />)
  return { user: userEvent.setup(), onPractise }
}

afterEach(() => vi.restoreAllMocks())

describe('Learn — lesson cards', () => {
  beforeEach(() => mockApi(lessons))

  it('lists the curriculum by level, and opens on the first concept Tradeez detects', async () => {
    renderView()
    expect(await screen.findByTestId('lesson-title')).toHaveTextContent('Fair value gap')
    const curriculum = screen.getByRole('region', { name: 'Curriculum' })
    expect(within(curriculum).getByText(/Level 0 · Foundations/)).toBeInTheDocument()
    expect(within(curriculum).getByRole('button', { name: /ICT-20/ })).toHaveAttribute('aria-current', 'true')
    expect(lessonRequests()[0]).toContain('/api/concepts/ICT-20/lesson?timeframe=5m')
  })

  it('reads what the concept is, the rule in words, and real examples explained by the server', async () => {
    renderView()
    expect(await screen.findByTestId('lesson-summary')).toHaveTextContent('Three consecutive candles')
    expect(screen.getByTestId('lesson-rule')).toHaveTextContent('fvg-1.0.0')
    expect(screen.getByTestId('lesson-rule')).toHaveTextContent('at least 25% of the average range')
    expect(screen.getByText(/not proof that the pattern matters/)).toBeInTheDocument()
    const examples = screen.getAllByTestId('lesson-example')
    expect(examples).toHaveLength(3)
    expect(examples[1]).toHaveTextContent("Candle 1's high is below candle 3's low (example 2).")
    expect(examples[0]).toHaveTextContent(/Bullish gap · .*2024.* · 5m · known when the .* candle closed/)
    expect(screen.getByText(/not picked to look good/)).toBeInTheDocument()
  })

  it('asks again for other examples, and on another timeframe', async () => {
    const { user } = renderView()
    await screen.findAllByTestId('lesson-example')
    await user.click(screen.getByRole('button', { name: 'Show others' }))
    await waitFor(() => expect(lessonRequests()).toHaveLength(2))
    await user.click(screen.getByRole('button', { name: '1m' }))
    await waitFor(() => expect(lessonRequests().at(-1)).toContain('timeframe=1m'))
  })

  it('opens a concept from the list and from its links, and says when there is nothing to show', async () => {
    const { user } = renderView()
    await screen.findByTestId('lesson-title')
    await user.click(screen.getByRole('button', { name: /F-1 Candle anatomy/ }))
    expect(await screen.findByTestId('examples-note')).toHaveTextContent('does not detect this concept yet')
    expect(screen.getByTestId('lesson-title')).toHaveTextContent('Candle anatomy')
    expect(screen.queryByRole('region', { name: 'The rule' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Show others' })).not.toBeInTheDocument()

    // Its "builds toward" link leads back to the gap.
    await user.click(screen.getByRole('button', { name: 'ICT-20 Fair value gap' }))
    await waitFor(() => expect(screen.getByTestId('lesson-title')).toHaveTextContent('Fair value gap'))
  })

  it('sends the learner to the drill that practises the concept', async () => {
    const { user, onPractise } = renderView()
    await user.click(await screen.findByRole('button', { name: 'Spot: Fair value gaps' }))
    expect(onPractise).toHaveBeenCalledWith('FVG_IDENTIFICATION')
  })

  it("shows the server's reason when a lesson cannot be loaded", async () => {
    mockApi(() => ({ status: 400, body: { message: 'Examples are shown on [1m, 5m, 15m] charts.' } }))
    renderView()
    expect(await screen.findByRole('alert')).toHaveTextContent('Examples are shown on [1m, 5m, 15m] charts.')
  })
})
