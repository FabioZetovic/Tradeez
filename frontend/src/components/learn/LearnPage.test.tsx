import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { LearnPage } from './LearnPage'
import type { ConceptProgress, Learning } from '@/lib/api/learning'

const p = (id: string, topic: string, over: Partial<ConceptProgress> = {}): ConceptProgress => ({
  id,
  kind: 'LESSON',
  topic,
  drilled: true,
  state: 'NEW',
  attempts: 0,
  recentCount: 0,
  practised: 0,
  due: false,
  open: false,
  ...over,
})

const learning = (concepts: ConceptProgress[], suggested: string, complete = 0): Learning => ({
  version: 'learning-1.1.0',
  rule: 'learning-1.1.0. A lesson is passed…',
  summary: {
    concepts: concepts.length,
    complete,
    drilled: 4,
    solid: complete,
    due: 0,
    ratedAttempts: concepts.reduce((n, c) => n + c.attempts, 0),
    predictions: 0,
  },
  topics: [
    { title: 'Structure', concepts: ['ICT-01', 'ICT-03'], complete: concepts.filter((c) => c.topic === 'Structure' && ['SOLID', 'DONE'].includes(c.state)).length },
    { title: 'Premium & discount', concepts: ['ICT-41', 'ICT-44'], complete: 0 },
  ],
  concepts,
  suggested,
  due: [],
})

const CURRICULUM = {
  topics: [],
  concepts: [
    ['ICT-01', 'Swing high / swing low'],
    ['ICT-03', 'Market structure and order flow'],
    ['ICT-41', 'Premium, discount, equilibrium'],
    ['ICT-44', 'Standard deviation projections'],
  ].map(([id, name]) => ({ concept: { id, name }, detectors: [] })),
}

function serve(state: Learning) {
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string) => {
      const body = url === '/api/learning' ? state : url === '/api/concepts' ? CURRICULUM : undefined
      return Promise.resolve({
        ok: body !== undefined,
        status: body ? 200 : 500,
        statusText: '',
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => body ?? {},
      })
    }),
  )
}

const tile = (name: string) => screen.getAllByTestId('lesson-tile').find((t) => t.textContent?.includes(name))!

afterEach(() => vi.unstubAllGlobals())

describe('Learn, by topic (T45)', () => {
  it('a newcomer: topics in order, each with its progress, "Start here" on the first lesson, the next one locked', async () => {
    serve(
      learning(
        [
          p('ICT-01', 'Structure', { open: true }),
          p('ICT-03', 'Structure'),
          p('ICT-41', 'Premium & discount', { open: true }),
          p('ICT-44', 'Premium & discount', { kind: 'REFERENCE', drilled: false, state: 'UNREAD', open: true }),
        ],
        'ICT-01',
      ),
    )
    render(
      <MemoryRouter>
        <LearnPage />
      </MemoryRouter>,
    )
    expect(await screen.findByRole('heading', { level: 2, name: 'Structure' })).toBeInTheDocument()
    expect(screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)).toEqual(['Structure', 'Premium & discount'])
    expect(screen.getByTestId('learn-total')).toHaveTextContent('0 of 4 lessons done')
    expect(within(screen.getByRole('region', { name: 'Structure' })).getByTestId('topic-progress')).toHaveTextContent('0 of 2')
    expect(tile('Swing high')).toHaveTextContent('Start here')
    expect(tile('Swing high')).toHaveAttribute('href', '/learn/ICT-01')
    expect(tile('Market structure')).toHaveTextContent('Pass the Swing high / swing low drill to unlock')
    expect(within(tile('Market structure')).getByLabelText('Locked')).toBeInTheDocument()
    expect(tile('Standard deviation')).toHaveTextContent('Read only')
    expect(within(tile('Standard deviation')).queryByLabelText('Locked')).toBeNull()
    // No catalogue ids (BUG-105), no empty rings before any progress (BUG-107), nothing dimmed when locked (BUG-106).
    for (const t of screen.getAllByTestId('lesson-tile')) {
      expect(t).not.toHaveTextContent(/ICT-\d/)
      expect(within(t).queryByRole('img', { name: /recently|Complete/ })).toBeNull()
      expect(t.className).not.toMatch(/opacity-/)
    }
  })

  it('later: done lessons ticked, the next one glowing "Next", one due for review says so', async () => {
    serve(
      learning(
        [
          p('ICT-01', 'Structure', { open: true, state: 'SOLID', recentAccuracy: 0.9, attempts: 6 }),
          p('ICT-03', 'Structure', { open: true, state: 'LEARNING', recentAccuracy: 0.5, attempts: 2 }),
          p('ICT-41', 'Premium & discount', { open: true, state: 'SOLID', due: true }),
          p('ICT-44', 'Premium & discount', { kind: 'REFERENCE', drilled: false, state: 'DONE', open: true }),
        ],
        'ICT-03',
        3,
      ),
    )
    render(
      <MemoryRouter>
        <LearnPage />
      </MemoryRouter>,
    )
    await screen.findByRole('heading', { level: 2, name: 'Structure' })
    expect(tile('Swing high')).toHaveTextContent('Done')
    expect(tile('Market structure')).toHaveTextContent('Next')
    expect(tile('Market structure')).not.toHaveTextContent('Start here')
    expect(tile('Market structure').className.split(' ')).toContain('border-accent')
    expect(tile('Premium')).toHaveTextContent('Review')
    expect(within(screen.getByRole('region', { name: 'Structure' })).getByTestId('topic-progress')).toHaveTextContent('1 of 2')
  })

  it('says so when the lessons cannot be loaded', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new Error('down'))))
    render(
      <MemoryRouter>
        <LearnPage />
      </MemoryRouter>,
    )
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load your lessons')
  })
})

describe('Learn, the states that could clash', () => {
  it('a reference card between two lessons is never the drill to pass', async () => {
    const state = learning([p('ICT-01', 'Structure', { open: true }), p('ICT-03', 'Structure')], 'ICT-01')
    serve({
      ...state,
      topics: [{ title: 'Structure', concepts: ['ICT-01', 'ICT-44', 'ICT-03'], complete: 0 }],
      concepts: [...state.concepts, p('ICT-44', 'Structure', { kind: 'REFERENCE', drilled: false, state: 'UNREAD', open: true })],
    })
    render(
      <MemoryRouter>
        <LearnPage />
      </MemoryRouter>,
    )
    await screen.findByRole('heading', { level: 2, name: 'Structure' })
    expect(tile('Market structure')).toHaveTextContent('Pass the Swing high / swing low drill to unlock')
  })


  it('a locked lesson shows its lock even when due; "Start here" only for someone who has done nothing yet', async () => {
    const state = learning(
      [
        p('ICT-01', 'Structure', { open: true, attempts: 2, recentAccuracy: 0.5, state: 'LEARNING' }),
        p('ICT-03', 'Structure', { due: true, attempts: 3 }),
        p('ICT-41', 'Premium & discount', { open: true }),
        p('ICT-44', 'Premium & discount', { kind: 'REFERENCE', drilled: false, state: 'UNREAD', open: true }),
      ],
      'ICT-01',
    )
    serve({ ...state, summary: { ...state.summary, ratedAttempts: 5 } })
    render(
      <MemoryRouter>
        <LearnPage />
      </MemoryRouter>,
    )
    await screen.findByRole('heading', { level: 2, name: 'Structure' })
    expect(within(tile('Market structure')).getByLabelText('Locked')).toBeInTheDocument()
    expect(tile('Market structure')).not.toHaveTextContent('Review')
    expect(tile('Swing high')).not.toHaveTextContent('Start here')
    expect(tile('Swing high')).toHaveTextContent('Next')
  })
})
