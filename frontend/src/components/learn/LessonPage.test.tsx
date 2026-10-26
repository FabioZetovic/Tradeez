import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useParams } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { LessonPage } from './LessonPage'
import type { ConceptProgress, Learning } from '@/lib/api/learning'
import type { Lesson } from '@/lib/api/lessons'
import { FakeChartAdapter } from '@/test/fakeChartAdapter'

const LESSON: Lesson = {
  id: 'ICT-23',
  name: 'Inversion FVG',
  level: 3,
  detectability: 'D',
  status: 'IMPLEMENTED',
  statusNote: 'ifvg-1.0.0',
  summary: 'The document summary.',
  prerequisites: [],
  unlocks: [],
  rules: [
    {
      detector: 'IFVG',
      version: 'ifvg-1.0.0',
      parameters: {},
      definition: 'One of the gaps of fvg-1.0.0 that a candle closes straight through.',
      methodologySource: 'ICT',
      evidence: [],
    },
  ],
  shownOn: 'SETUPS',
  drills: [{ kind: 'IFVG_IDENTIFICATION', mode: 'IDENTIFY', title: 'Inversion fair value gaps' }],
  instrument: 'NQ',
  tickSize: 0.25,
  exchangeTimezone: 'America/Chicago',
  timeframe: '5m',
  examples: [],
  examplesNote: 'None turned up this time. Ask again for another look.',
  kind: 'LESSON',
  topic: 'Gaps & imbalances',
  text: {
    summary: 'A fair value gap that failed: price closed straight through it.',
    whatItIs: 'An inversion fair value gap is a gap that price closed through.',
    howToSpot: ['Find a fair value gap.', 'Watch for a close beyond its far edge.'],
    howToTrade: 'Sell back into an inverted bullish gap.',
  },
}

const progress = (id: string, over: Partial<ConceptProgress> = {}): ConceptProgress => ({
  id,
  kind: 'LESSON',
  topic: 'Gaps & imbalances',
  drilled: true,
  state: 'LEARNING',
  attempts: 2,
  recentCount: 2,
  recentAccuracy: 0.5,
  practised: 0,
  due: false,
  open: true,
  ...over,
})

const learning = (ifvg: Partial<ConceptProgress>): Learning => ({
  version: 'learning-1.1.0',
  rule: '',
  summary: { concepts: 2, complete: 0, drilled: 2, solid: 0, due: 0, ratedAttempts: 0, predictions: 0 },
  topics: [{ title: 'Gaps & imbalances', concepts: ['ICT-20', 'ICT-23'], complete: 0 }],
  concepts: [progress('ICT-20', { attempts: 0, recentAccuracy: undefined, state: 'NEW' }), progress('ICT-23', ifvg)],
  due: [],
})

const CURRICULUM = {
  topics: [],
  concepts: [
    { concept: { id: 'ICT-20', name: 'Fair value gap' }, detectors: [] },
    { concept: { id: 'ICT-23', name: 'Inversion FVG' }, detectors: [] },
  ],
}

function serve(routes: Record<string, unknown>) {
  const calls: string[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string) => {
      calls.push(url)
      const key = Object.keys(routes).find((k) => url.startsWith(k))
      const body = key ? routes[key] : undefined
      return Promise.resolve({
        ok: body !== undefined,
        status: body ? 200 : 500,
        statusText: '',
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => body ?? {},
      })
    }),
  )
  return calls
}

function Opened() {
  return <p data-testid="opened">{useParams()['*']}</p>
}

function renderLesson(id: string) {
  return render(
    <MemoryRouter initialEntries={[`/learn/${id}`]}>
      <Routes>
        <Route path="/learn/:id" element={<Page />} />
        <Route path="/drills/*" element={<Opened />} />
      </Routes>
    </MemoryRouter>,
  )
}

function Page() {
  const { id = '' } = useParams()
  return <LessonPage key={id} id={id} adapterFactory={() => new FakeChartAdapter()} />
}

afterEach(() => vi.unstubAllGlobals())

describe('a lesson (T45)', () => {
  it('in the template: topic and code, the summary, what it is, how to spot it, how it is traded, the rule folded', async () => {
    serve({ '/api/concepts/ICT-23/lesson': LESSON, '/api/learning': learning({}), '/api/concepts': CURRICULUM })
    renderLesson('ICT-23')
    expect(await screen.findByTestId('lesson-title')).toHaveTextContent('Inversion FVG')
    expect(screen.getByText('Gaps & imbalances · ICT-23')).toBeInTheDocument()
    expect(screen.getByTestId('lesson-summary')).toHaveTextContent('A fair value gap that failed')
    expect(within(screen.getByRole('region', { name: 'What it is' })).getByText(/a gap that price closed through/)).toBeInTheDocument()
    expect(within(screen.getByRole('region', { name: 'How to spot it' })).getAllByRole('listitem')).toHaveLength(2)
    expect(within(screen.getByRole('region', { name: 'How it is traded' })).getByText(/Sell back into/)).toBeInTheDocument()
    expect(screen.getByTestId('lesson-rule')).not.toHaveAttribute('open')
    expect(screen.getByTestId('examples-note')).toHaveTextContent('None turned up this time')
    expect(screen.getByRole('link', { name: 'Learn' })).toHaveAttribute('href', '/learn')
  })

  it('"Practise it": the record against the pass mark, and one click to its drill', async () => {
    serve({ '/api/concepts/ICT-23/lesson': LESSON, '/api/learning': learning({}), '/api/concepts': CURRICULUM })
    renderLesson('ICT-23')
    expect(await screen.findByTestId('lesson-record')).toHaveTextContent(
      '2 tries · 50% recently. Pass it at 5 answers averaging 70% or better to open the next lesson.',
    )
    await userEvent.click(await screen.findByRole('link', { name: 'Spot: Inversion fair value gaps' }))
    expect(screen.getByTestId('opened')).toHaveTextContent('IFVG_IDENTIFICATION')
  })

  it('locked, opened by its address: the summary and which drill to pass — not the lesson', async () => {
    serve({ '/api/concepts/ICT-23/lesson': LESSON, '/api/learning': learning({ open: false }), '/api/concepts': CURRICULUM })
    renderLesson('ICT-23')
    expect(await screen.findByText('Pass the Fair value gap drill to unlock this lesson.')).toBeInTheDocument()
    expect(screen.getByTestId('lesson-summary')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'What it is' })).toBeNull()
    expect(screen.queryByRole('region', { name: 'Practise it' })).toBeNull()
    expect(screen.getByRole('link', { name: 'Go to Fair value gap' })).toHaveAttribute('href', '/learn/ICT-20')
  })

  it("a merged concept's address leads to the lesson it is part of", async () => {
    const calls = serve({
      '/api/concepts/ICT-05/lesson': { ...LESSON, id: 'ICT-05', kind: 'MERGED', into: 'ICT-06', text: undefined },
      '/api/concepts/ICT-06/lesson': { ...LESSON, id: 'ICT-06', name: 'BOS / CHoCH' },
      '/api/learning': learning({}),
      '/api/concepts': CURRICULUM,
    })
    renderLesson('ICT-05')
    expect(await screen.findByText('BOS / CHoCH')).toBeInTheDocument()
    expect(calls.some((u) => u.startsWith('/api/concepts/ICT-06/lesson'))).toBe(true)
  })
})
