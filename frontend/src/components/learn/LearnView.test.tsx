import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { LearnView } from './LearnView'
import { FakeChartAdapter } from '@/test/fakeChartAdapter'
import type { Curriculum } from '@/lib/api/concepts'
import type { Lesson } from '@/lib/api/lessons'
import type { ConceptProgress, Learning } from '@/lib/api/learning'

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

const progress = (over: Partial<ConceptProgress> & { id: string }): ConceptProgress => ({
  rated: false,
  state: 'UNREAD',
  attempts: 0,
  recentCount: 0,
  practised: 0,
  due: false,
  open: true,
  ...over,
})

/** F-1 unread; the gap learning, at 64% over 11, due today; level 3 not suggested yet. */
const LEARNING: Learning = {
  version: 'learning-1.0.0',
  rule: 'learning-1.0.0. A concept is solid once it has at least 10 attempts and the last 20 average at least 80%.',
  summary: { concepts: 2, complete: 0, rated: 1, solid: 0, due: 1, ratedAttempts: 11, predictions: 0 },
  levels: [
    { level: 0, theme: 'Foundations', open: true, complete: false, unlocksWhen: 'always open' },
    { level: 3, theme: 'Imbalances', open: false, complete: false, unlocksWhen: 'Level 1 complete' },
  ],
  concepts: [
    progress({ id: 'F-1' }),
    progress({ id: 'ICT-20', rated: true, state: 'LEARNING', attempts: 11, recentCount: 11, recentAccuracy: 0.636, nextReview: '2026-10-16', due: true, open: false }),
  ],
  suggested: 'F-1',
  due: ['ICT-20'],
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
    midHalfTicks: 160_018,
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
/** What GET /api/learning answers; null for a failure. */
let learningNow: Learning | null = LEARNING
/** What the server answers once F-1 is marked read: done, level 0 complete, the gap suggested. */
const afterRead: Learning = {
  ...LEARNING,
  summary: { ...LEARNING.summary, complete: 1 },
  levels: [{ ...LEARNING.levels[0], complete: true }, LEARNING.levels[1]],
  concepts: [progress({ id: 'F-1', state: 'DONE', readAt: Date.UTC(2026, 9, 16) / 1000 }), LEARNING.concepts[1]],
  suggested: 'ICT-20',
}

function mockApi(lesson: (url: string) => { status?: number; body: unknown }) {
  urls = []
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string) => {
      urls.push(url)
      const { status = 200, body } = url.endsWith('/api/concepts')
        ? { body: CURRICULUM }
        : url.endsWith('/api/learning')
          ? learningNow ? { body: learningNow } : { status: 500, body: {} }
          : url.endsWith('/read')
            ? { body: afterRead }
            : lesson(url)
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

const ladder = () => screen.getByRole('navigation', { name: 'Curriculum ladder' })

const lessonRequests = () => urls.filter((u) => u.includes('/lesson'))

function renderView(onPractise = vi.fn()) {
  render(<LearnView onPractise={onPractise} adapterFactory={() => new FakeChartAdapter()} />)
  return { user: userEvent.setup(), onPractise }
}

afterEach(() => vi.restoreAllMocks())

describe('Learn — lesson cards', () => {
  beforeEach(() => {
    learningNow = LEARNING
    mockApi(lessons)
  })

  it('lists the curriculum by level, and opens on the first concept Tradeez detects', async () => {
    renderView()
    expect(await screen.findByTestId('lesson-title')).toHaveTextContent('Fair value gap')
    const curriculum = screen.getByRole('region', { name: 'Curriculum' })
    expect(within(curriculum).getByText(/Level 0 · Foundations/)).toBeInTheDocument()
    expect(within(ladder()).getByRole('button', { name: /ICT-20/ })).toHaveAttribute('aria-current', 'true')
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
    await user.click(within(ladder()).getByRole('button', { name: /F-1 Candle anatomy/ }))
    expect(await screen.findByTestId('examples-note')).toHaveTextContent('does not detect this concept yet')
    expect(screen.getByTestId('lesson-title')).toHaveTextContent('Candle anatomy')
    expect(screen.queryByRole('region', { name: 'The rule' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Show others' })).not.toBeInTheDocument()

    // Its "builds toward" link leads back to the gap.
    await user.click(within(screen.getByRole('article', { name: 'Lesson: Candle anatomy' })).getByRole('button', { name: 'ICT-20 Fair value gap' }))
    await waitFor(() => expect(screen.getByTestId('lesson-title')).toHaveTextContent('Fair value gap'))
  })

  it('sends the learner to the drill that practises the concept', async () => {
    const { user, onPractise } = renderView()
    await user.click(await screen.findByRole('button', { name: 'Spot: Fair value gaps' }))
    expect(onPractise).toHaveBeenCalledWith('FVG_IDENTIFICATION')
  })

  it('names a prediction drill as one, on a concept nothing detects (DEC-058)', async () => {
    mockApi((url) => ({
      body: url.includes('/api/concepts/F-1/')
        ? { ...CANDLE_LESSON, drills: [{ kind: 'LIQUIDITY_FIRST_PREDICTION', mode: 'PREDICT', title: 'Which liquidity is taken first' }] }
        : FVG_LESSON,
    }))
    const { user, onPractise } = renderView()
    await screen.findByTestId('lesson-title')
    await user.click(within(ladder()).getByRole('button', { name: /F-1 Candle anatomy/ }))
    await user.click(await screen.findByRole('button', { name: 'Predict: Which liquidity is taken first' }))
    expect(onPractise).toHaveBeenCalledWith('LIQUIDITY_FIRST_PREDICTION')
  })

  it("shows the server's reason when a lesson cannot be loaded", async () => {
    mockApi(() => ({ status: 400, body: { message: 'Examples are shown on [1m, 5m, 15m] charts.' } }))
    renderView()
    expect(await screen.findByRole('alert')).toHaveTextContent('Examples are shown on [1m, 5m, 15m] charts.')
  })
})

describe('Learn — progress (T16, DEC-059)', () => {
  beforeEach(() => {
    learningNow = LEARNING
    mockApi(lessons)
  })

  it("marks each concept in the ladder, says what is due and what is next, and when a level opens", async () => {
    const { user } = renderView()
    const summary = await screen.findByTestId('learning-summary')
    expect(summary).toHaveTextContent('0 of 1 graded concepts solid')
    expect(summary).toHaveTextContent('0 of 2 complete')
    expect(screen.getByTestId('suggested')).toHaveTextContent('F-1 Candle anatomy')
    expect(within(screen.getByTestId('due')).getByRole('button', { name: 'Review ICT-20 Fair value gap' })).toHaveTextContent('ICT-20')
    const gap = within(ladder()).getByRole('button', { name: /ICT-20 Fair value gap/ })
    expect(within(gap).getByTestId('status-mark')).toHaveTextContent('64%')
    expect(within(gap).getByTestId('status-mark')).toHaveTextContent('due')
    expect(screen.getByTestId('level-3')).toHaveTextContent('· later')
    expect(within(screen.getByTestId('level-3')).getByTitle('Opens when: Level 1 complete')).toBeInTheDocument()
    // The next step opens its card.
    await user.click(screen.getByTestId('suggested'))
    await waitFor(() => expect(screen.getByTestId('lesson-title')).toHaveTextContent('Candle anatomy'))
    // The ladder suggests; it never locks. A card of a later level still opens.
    await user.click(within(screen.getByTestId('due')).getByRole('button'))
    await waitFor(() => expect(screen.getByTestId('lesson-title')).toHaveTextContent('Fair value gap'))
  })

  it("shows the learner's record on the card, with the rule it is measured by", async () => {
    renderView()
    const panel = await screen.findByRole('region', { name: 'Your progress' })
    expect(panel).toHaveTextContent('Learning')
    expect(screen.getByTestId('progress-line')).toHaveTextContent('11 graded attempts · 64% over the last 11 attempts.')
    expect(screen.getByTestId('review-line')).toHaveTextContent('Due for review today.')
    expect(within(panel).getByText(/at least 10 attempts/)).toBeInTheDocument()
  })

  it('marks a card as read, and takes the new state from the server', async () => {
    const { user } = renderView()
    await screen.findByTestId('lesson-title')
    await user.click(within(ladder()).getByRole('button', { name: /F-1 Candle anatomy/ }))
    await waitFor(() => expect(screen.getByTestId('lesson-title')).toHaveTextContent('Candle anatomy'))
    expect(screen.getByRole('region', { name: 'Your progress' })).toHaveTextContent('Not read yet')

    await user.click(screen.getByRole('button', { name: 'Mark as read' }))
    expect(await screen.findByTestId('read-on')).toHaveTextContent('Card read 16 Oct 2026')
    expect(urls.some((u) => u.endsWith('/api/learning/concepts/F-1/read'))).toBe(true)
    expect(screen.getByTestId('suggested')).toHaveTextContent('ICT-20')
    expect(screen.getByTestId('level-0')).toHaveTextContent('· complete')
    expect(screen.queryByRole('button', { name: 'Mark as read' })).not.toBeInTheDocument()
  })

  it('still reads when progress cannot be loaded', async () => {
    learningNow = null
    renderView()
    expect(await screen.findByTestId('lesson-title')).toHaveTextContent('Fair value gap')
    expect(screen.queryByTestId('learning-summary')).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Your progress' })).not.toBeInTheDocument()
  })
})
