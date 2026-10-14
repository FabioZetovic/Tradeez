import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { TradeLog } from './TradeLog'
import type { PracticeState, ResultView, TradeView } from '@/lib/api/practice'

const T = 1_710_338_400

const RESULT: ResultView = {
  outcome: 'TARGET',
  entryTime: T,
  entryTicks: 80_000,
  exitTime: T + 600,
  exitTicks: 80_080,
  pnlTicks: 80,
  netPnlCents: 39_750,
  commissionCents: 250,
  maeTicks: 4,
  mfeTicks: 80,
  gapExit: false,
  rMultiple: 2,
  resolution: 'M1',
  engineVersion: 'fill-1.1.0',
  fillModelVersion: 'slip-1t/comm-250c',
}

const PLAN = {
  direction: 'LONG' as const,
  stopTicks: 79_960,
  targetTicks: 80_080,
  contracts: 1,
  referenceTicks: 80_000,
  lockedAt: T,
  plannedRewardRisk: 2,
}

const trade = (number: number, result?: ResultView): TradeView => ({ number, plan: PLAN, result })

const STATE: PracticeState = {
  id: 's',
  instrument: 'NQ',
  tickSize: 0.25,
  tickValueCents: 500,
  exchangeTimezone: 'America/Chicago',
  timeframe: '5m',
  status: 'IN_TRADE',
  startTime: T,
  cursor: T + 900,
  timeBasis: 'UTC',
  dataClass: 'IMPORTED_PRIVATE',
  trades: [trade(1, RESULT), trade(2, RESULT), trade(3)],
  score: { closedTrades: 2, wins: 2, losses: 0, totalR: 4, netPnlCents: 79_500 },
}

describe('TradeLog', () => {
  it('BUG-042: a finished trade can be opened from the keyboard', async () => {
    const onSelect = vi.fn()
    const user = userEvent.setup()
    render(<TradeLog state={STATE} selected={2} onSelect={onSelect} />)

    expect(screen.getByRole('button', { name: 'Show trade 2' })).toHaveAttribute('aria-pressed', 'true')
    screen.getByRole('button', { name: 'Show trade 1' }).focus()
    await user.keyboard('{Enter}')
    expect(onSelect).toHaveBeenCalledExactlyOnceWith(1)
  })

  it('offers nothing to open for a trade still running', () => {
    render(<TradeLog state={STATE} onSelect={() => {}} />)
    expect(screen.queryByRole('button', { name: 'Show trade 3' })).not.toBeInTheDocument()
    expect(screen.getAllByTestId('trade-row')[2]).toHaveTextContent('Running')
  })
})
