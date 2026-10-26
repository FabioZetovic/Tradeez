import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useParams } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { DrillsPage } from './DrillsPage'
import type { DrillKindView, RiskKindView } from '@/lib/api/drills'
import type { ConceptProgress } from '@/lib/api/learning'

const kind = (id: string, mode: DrillKindView['mode'], title: string, conceptId: string): DrillKindView => ({
  kind: id,
  mode,
  title,
  conceptId,
  detector: 'X',
  ruleVersion: 'x-1.0.0',
  instructions: `Do ${title}.`,
})

/** Out of the page's order on purpose, with one kind no topic names. */
const KINDS = [
  kind('BOS_CHOCH_CLASSIFICATION', 'CLASSIFY', 'Break of structure or change of character', 'ICT-06'),
  kind('FVG_IDENTIFICATION', 'IDENTIFY', 'Fair value gaps', 'ICT-20'),
  kind('LIQUIDITY_FIRST_PREDICTION', 'PREDICT', 'Which liquidity is taken first', 'ICT-16'),
  kind('OPEN_TRADE_MANAGEMENT', 'MANAGE', 'Manage an open trade', 'ICT-82'),
  kind('BRAND_NEW_IDENTIFICATION', 'IDENTIFY', 'Something new', 'ICT-99'),
  kind('VI_IDENTIFICATION', 'IDENTIFY', 'Volume imbalances', 'ICT-25'),
]

const concept = (id: string, over: Partial<ConceptProgress>): ConceptProgress => ({
  id,
  kind: 'LESSON',
  topic: '',
  drilled: true,
  state: 'LEARNING',
  attempts: 0,
  recentCount: 0,
  practised: 0,
  due: false,
  open: true,
  ...over,
})

const LEARNING = {
  concepts: [
    concept('ICT-20', { attempts: 12, recentCount: 12, recentAccuracy: 0.666 }),
    concept('ICT-06', { attempts: 1, recentCount: 1 }),
    concept('ICT-25', {}),
    concept('ICT-16', { attempts: 4, recentCount: 4, recentAccuracy: 0.5, practised: 4 }),
  ],
}

/** Learn's topics (DEC-081), the order the sections follow; ICT-99 is in none. */
const CURRICULUM = {
  topics: [
    { title: 'Structure', concepts: ['ICT-01', 'ICT-06'] },
    { title: 'Liquidity', concepts: ['ICT-16'] },
    { title: 'Gaps & imbalances', concepts: ['ICT-20', 'ICT-23', 'ICT-25'] },
    { title: 'Risk & management', concepts: ['ICT-83', 'ICT-82'] },
  ],
  concepts: [],
}

const RISK: RiskKindView[] = [
  { kind: 'CONTRACTS', limitSeconds: 30, answered: 3, right: 1 },
  { kind: 'STREAK', limitSeconds: 45, answered: 0, right: 0 },
  { kind: 'RECOVERY', limitSeconds: 30, answered: 2, right: 2 },
]

function serve(routes: Record<string, unknown>) {
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string) => {
      const body = routes[url]
      return Promise.resolve({
        ok: body !== undefined,
        status: body === undefined ? 500 : 200,
        statusText: '',
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => body ?? { message: 'down' },
      })
    }),
  )
}

const ALL = { '/api/drills/kinds': KINDS, '/api/learning': LEARNING, '/api/drills/risk/kinds': RISK, '/api/concepts': CURRICULUM }

function Opened() {
  const params = useParams()
  return <p data-testid="opened">{params['*']}</p>
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/drills']}>
      <Routes>
        <Route path="/drills" element={<DrillsPage />} />
        <Route path="/drills/*" element={<Opened />} />
        <Route path="/practice" element={<p>Practice</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

const card = (title: string) => screen.getByRole('link', { name: new RegExp(title) })

afterEach(() => vi.unstubAllGlobals())

describe('Drills, the cards (T43)', () => {
  it("groups the drills by Learn's topics, in Learn's order; the risk questions with Risk & management (T45)", async () => {
    serve(ALL)
    renderPage()
    expect(await screen.findByRole('heading', { name: 'Gaps & imbalances' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1, name: 'Drills' })).toBeInTheDocument()
    expect(screen.getByText('Short exercises on one idea at a time, on real NQ.')).toBeInTheDocument()
    expect(screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)).toEqual([
      'Structure',
      'Liquidity',
      'Gaps & imbalances',
      'Risk & management',
      'More drills',
    ])
    const gaps = screen.getByRole('region', { name: 'Gaps & imbalances' })
    expect(within(gaps).getAllByRole('link').map((l) => l.getAttribute('href'))).toEqual([
      '/drills/FVG_IDENTIFICATION',
      '/drills/VI_IDENTIFICATION',
    ])
    // A kind the server lists and no topic names still gets its card: every drill is reachable.
    expect(within(screen.getByRole('region', { name: 'More drills' })).getByRole('link')).toHaveAttribute(
      'href',
      '/drills/BRAND_NEW_IDENTIFICATION',
    )
    expect(
      within(screen.getByRole('region', { name: 'Risk & management' })).getAllByRole('link').map((l) => l.getAttribute('href')),
    ).toEqual(['/drills/OPEN_TRADE_MANAGEMENT', '/drills/risk/position-size', '/drills/risk/losing-streak', '/drills/risk/recovery'])
    expect(screen.getByRole('link', { name: /Next step/ })).toHaveAttribute('href', '/practice')
  })

  it('a card: the title, its line, the mode, a time hint', async () => {
    serve(ALL)
    renderPage()
    await screen.findByRole('heading', { name: 'Gaps & imbalances' })
    expect(card('Fair value gaps')).toHaveTextContent('Spot~2–3 minFair value gapsDo Fair value gaps.')
    expect(card('Break of structure')).toHaveTextContent('Name')
    expect(card('Which liquidity')).toHaveTextContent('Predict')
    expect(card('Manage an open trade')).toHaveTextContent('Manage')
    expect(card('Losing streak')).toHaveTextContent('Sums45 s eachLosing streak')
    expect(card('Position size')).toHaveTextContent('30 s each')
  })

  it("carries the real record: the lesson's tries and recent accuracy — predictions too — and risk's tally", async () => {
    serve(ALL)
    renderPage()
    await screen.findByRole('heading', { name: 'Gaps & imbalances' })
    const record = (title: string) => within(card(title)).getByTestId('drill-record')
    expect(record('Fair value gaps')).toHaveTextContent('12 tries · 67% recently')
    expect(record('Break of structure')).toHaveTextContent(/^1 try$/)
    expect(record('Volume imbalances')).toHaveTextContent('Not tried yet')
    expect(record('Which liquidity')).toHaveTextContent('4 tries · 50% recently')
    // No progress on the concept at all: nothing claimed.
    expect(record('Manage an open trade')).toHaveTextContent(/^\s*$/)
    expect(record('Position size')).toHaveTextContent('1 of 3 right')
    expect(record('Losing streak')).toHaveTextContent('Not tried yet')
    expect(record('Recovery')).toHaveTextContent('2 of 2 right')
  })

  it('one click opens the drill', async () => {
    serve(ALL)
    renderPage()
    await userEvent.click(await screen.findByRole('link', { name: /Fair value gaps/ }))
    expect(screen.getByTestId('opened')).toHaveTextContent('FVG_IDENTIFICATION')
  })

  it('without the records the cards still open their drills; without the list it says so', async () => {
    serve({ '/api/drills/kinds': KINDS })
    const { unmount } = renderPage()
    await screen.findByRole('heading', { name: 'Gaps & imbalances' })
    expect(within(card('Fair value gaps')).getByTestId('drill-record')).toHaveTextContent(/^\s*$/)
    expect(card('Position size')).not.toHaveTextContent('s each')
    expect(screen.queryByRole('alert')).toBeNull()
    unmount()

    serve({})
    renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load the list of drills')
    expect(screen.queryByRole('region', { name: 'Risk' })).toBeNull()
  })
})
