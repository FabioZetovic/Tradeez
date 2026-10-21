import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { TradeReview } from './TradeReview'
import type { PracticeState, TradeReviewView } from '@/lib/api/practice'

/** A real session's review on its hidden-date clock: Monday 10:00 New York written as UTC (DEC-025). */
const T = Date.UTC(2001, 0, 1, 10, 0) / 1000

const STATE: PracticeState = {
  id: 's1',
  instrument: 'NQ',
  tickSize: 0.25,
  tickValueCents: 500,
  exchangeTimezone: 'America/Chicago',
  timeframe: '5m',
  status: 'ANALYSING',
  startTime: T - 3600,
  cursor: T + 3600,
  timeBasis: 'NEW_YORK_WALL_CLOCK',
  dataClass: 'IMPORTED_PRIVATE',
  trades: [],
  score: { closedTrades: 1, wins: 1, losses: 0, totalR: 2, netPnlCents: 39_500 },
}

const REVIEW: TradeReviewView = {
  version: 'review-1.0.0',
  rules: ['LIQUIDITY', 'SWEEP', 'MSS', 'FVG'],
  moments: [
    {
      time: T - 600,
      phase: 'BEFORE',
      kind: 'RULE',
      title: 'Sweep of sell-side liquidity',
      lines: ['This candle traded below the swing low at 20013.50 and closed back above it.'],
    },
    { time: T, phase: 'TRADE', kind: 'DECISION', title: 'You placed a long', lines: ['Long 1 contract at market from 20000.00.'] },
    { time: T + 30, phase: 'TRADE', kind: 'ENTRY', title: 'Filled at 20000.25', lines: [] },
    { time: T + 300, phase: 'TRADE', kind: 'RULE', title: 'Bullish gap', lines: ['Candle 1 high below candle 3 low.'] },
    { time: T + 900, phase: 'TRADE', kind: 'EXIT', title: 'You closed it at 20005.00: +0.50R.', lines: [] },
    { time: T + 1800, phase: 'AFTER', kind: 'HELD', title: 'Had you held, the target would have filled at 20020.00: +2.00R.', lines: [] },
  ],
  summary: ['Your draw at 20030.00 was not reached in what the replay has shown.'],
  layer: {
    rules: [],
    setups: [
      {
        detector: 'FVG',
        shape: 'ZONE',
        direction: 'BULLISH',
        start: T,
        anchor: T + 300,
        end: T + 600,
        confirmedAt: T + 300,
        lowTicks: 80_004,
        highTicks: 80_010,
        midHalfTicks: 160_014,
      },
    ],
    rolls: [],
  },
}

function serve(response: { status: number; body: unknown }) {
  const fetchMock = vi.fn(() =>
    Promise.resolve({
      ok: response.status < 400,
      status: response.status,
      statusText: String(response.status),
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => response.body,
      text: async () => JSON.stringify(response.body),
    }),
  )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

afterEach(() => vi.unstubAllGlobals())

describe('a trade told as it happened (T24)', () => {
  it('is read only when asked, then told in order: before it, while it ran, after it', async () => {
    const fetchMock = serve({ status: 200, body: REVIEW })
    const user = userEvent.setup()
    render(<TradeReview state={STATE} number={1} drawn={false} onDraw={vi.fn()} />)
    expect(fetchMock).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Read the review' }))
    expect(fetchMock).toHaveBeenCalledWith('/api/practice/sessions/s1/trades/1/review', expect.anything())
    const before = await screen.findByRole('region', { name: 'Before you placed it' })
    expect(within(before).getAllByRole('listitem')).toHaveLength(1)
    expect(before).toHaveTextContent('Mon 09:50 NYSweep of sell-side liquidityThis candle traded below the swing low')
    const during = screen.getByRole('region', { name: 'While it ran' })
    expect(within(during).getAllByRole('listitem').map((li) => li.dataset.testid)).toEqual([
      'review-decision',
      'review-entry',
      'review-rule',
      'review-exit',
    ])
    // The learner's own moments stand out; a rule's hit does not.
    expect(within(screen.getByTestId('review-decision')).getByText('You placed a long')).toHaveClass('font-medium')
    expect(within(within(during).getByTestId('review-rule')).getByText('Bullish gap')).not.toHaveClass('font-medium')
    expect(screen.getByRole('region', { name: 'After it ended' })).toHaveTextContent(
      'Had you held, the target would have filled at 20020.00: +2.00R.',
    )
    expect(screen.getByTestId('review-summary')).toHaveTextContent('Your draw at 20030.00 was not reached')
    expect(screen.getByText(/The rules read: LIQUIDITY, SWEEP, MSS, FVG\. review-1\.0\.0\./)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Read the review' })).toBeNull()
  })

  it('draws its hits on the chart, and takes them off, on request', async () => {
    const fetchMock = serve({ status: 200, body: REVIEW })
    const onDraw = vi.fn()
    const user = userEvent.setup()
    const { rerender } = render(<TradeReview state={STATE} number={2} drawn={false} onDraw={onDraw} />)
    await user.click(screen.getByRole('button', { name: 'Read the review' }))
    const show = await screen.findByRole('button', { name: 'Show its rules on the chart' })
    expect(fetchMock).toHaveBeenCalledWith('/api/practice/sessions/s1/trades/2/review', expect.anything())
    expect(show).toHaveAttribute('aria-pressed', 'false')
    await user.click(show)
    expect(onDraw).toHaveBeenLastCalledWith(REVIEW.layer)

    rerender(<TradeReview state={STATE} number={2} drawn onDraw={onDraw} />)
    const hide = screen.getByRole('button', { name: 'Hide its rules on the chart' })
    expect(hide).toHaveAttribute('aria-pressed', 'true')
    await user.click(hide)
    expect(onDraw).toHaveBeenLastCalledWith(null)
  })

  it('offers nothing to draw when the rules saw nothing, and says so when the review cannot be read', async () => {
    serve({ status: 200, body: { ...REVIEW, layer: { ...REVIEW.layer, setups: [] } } })
    const user = userEvent.setup()
    const { unmount } = render(<TradeReview state={STATE} number={1} drawn={false} onDraw={vi.fn()} />)
    await user.click(screen.getByRole('button', { name: 'Read the review' }))
    await screen.findByRole('region', { name: 'While it ran' })
    expect(screen.queryByRole('button', { name: /its rules on the chart/ })).toBeNull()
    unmount()

    serve({ status: 409, body: { message: 'not yet' } })
    render(<TradeReview state={STATE} number={1} drawn={false} onDraw={vi.fn()} />)
    await user.click(screen.getByRole('button', { name: 'Read the review' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not read the review.')
    expect(screen.getByRole('button', { name: 'Read the review' })).toBeEnabled()
  })
})
