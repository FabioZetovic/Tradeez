import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ResultCard } from './ResultCard'
import type { PracticeState, ResultView, TradeView } from '@/lib/api/practice'

/**
 * The card a learner reads after every trade: how it ended, for how much, and why. NQ — a tick is
 * 0.25 points and $5.00 a contract.
 */

const T = 1_710_338_400

const STATE: PracticeState = {
  id: 's1',
  instrument: 'NQ',
  tickSize: 0.25,
  tickValueCents: 500,
  exchangeTimezone: 'America/Chicago',
  timeframe: '5m',
  status: 'ANALYSING',
  startTime: T,
  cursor: T + 3600,
  timeBasis: 'UTC',
  dataClass: 'IMPORTED_PRIVATE',
  trades: [],
  score: { closedTrades: 1, wins: 1, losses: 0, totalR: 0.8, netPnlCents: 3_750 },
}

/** A long planned at 80,000 (20,000.00), stop 79,990, target 80,008: ten ticks of risk. */
const LONG: TradeView = {
  number: 1,
  plan: {
    direction: 'LONG',
    stopTicks: 79_990,
    targetTicks: 80_008,
    contracts: 1,
    referenceTicks: 80_000,
    lockedAt: T,
    plannedRewardRisk: 0.8,
  },
}

const TARGET: ResultView = {
  outcome: 'TARGET',
  entryTime: T,
  entryTicks: 80_000,
  exitTime: T + 60,
  exitTicks: 80_008,
  pnlTicks: 8,
  netPnlCents: 3_750,
  commissionCents: 250,
  maeTicks: 2,
  mfeTicks: 8,
  gapExit: false,
  rMultiple: 0.8,
  resolution: 'M1',
  engineVersion: 'fill-1.2.0',
  fillModelVersion: 'slip-1t/comm-250c',
}

function card(result: ResultView, trade: TradeView = LONG) {
  render(<ResultCard state={STATE} trade={trade} result={result} />)
  return screen.getByRole('region', { name: 'Result' })
}

describe('the result of a trade', () => {
  it('reads a target hit: the outcome, the money in green, R, and every number behind them', () => {
    const shown = card(TARGET)
    expect(screen.getByTestId('result-outcome')).toHaveTextContent('Trade 1 · Target hit')
    expect(screen.getByTestId('result-net')).toHaveTextContent('$37.50')
    expect(screen.getByTestId('result-net').parentElement).toHaveClass('text-up')
    expect(screen.getByTestId('result-r')).toHaveTextContent('+0.80R')
    expect(shown).toHaveTextContent('Long1 contract')
    expect(shown).toHaveTextContent('Entry20000.00')
    expect(shown).toHaveTextContent('Exit20002.00')
    expect(shown).toHaveTextContent('Per contract8 ticks')
    expect(shown).toHaveTextContent('Commission$2.50')
    // Worst and best point, also in R against the ten ticks risked.
    expect(shown).toHaveTextContent('Worst point2 ticks (0.20R)')
    expect(shown).toHaveTextContent('Best point8 ticks (0.80R)')
    expect(within(shown).queryByRole('note')).not.toBeInTheDocument()
    expect(screen.queryByTestId('gap-note')).not.toBeInTheDocument()
  })

  it('reads a stop: the loss in red with a minus sign, and more than −1R once slippage is in', () => {
    card({ ...TARGET, outcome: 'STOP', exitTicks: 79_989, pnlTicks: -11, netPnlCents: -5_750, rMultiple: -1.1, mfeTicks: 3, maeTicks: 11 })
    expect(screen.getByTestId('result-outcome')).toHaveTextContent('Stopped out')
    expect(screen.getByTestId('result-net')).toHaveTextContent('−$57.50')
    expect(screen.getByTestId('result-net').parentElement).toHaveClass('text-down')
    expect(screen.getByTestId('result-r')).toHaveTextContent('−1.10R')
  })

  it('reads an ambiguous result as an assumption, in words that cannot be missed (INVARIANT 6)', () => {
    const shown = card({
      ...TARGET,
      outcome: 'AMBIGUOUS',
      assumption: 'The stop and the target were both touched inside the same minute. The result assumes the worst case: the stop.',
      exitTicks: 79_989,
      pnlTicks: -11,
      netPnlCents: -5_750,
      rMultiple: -1.1,
    })
    expect(screen.getByTestId('result-outcome')).toHaveTextContent('Ambiguous — worst case assumed')
    expect(within(shown).getByRole('note')).toHaveTextContent('assumes the worst case')
  })

  it('BUG-059: a gap past the stop is called worse than the stop', () => {
    card({ ...TARGET, outcome: 'STOP', exitTicks: 79_984, pnlTicks: -16, netPnlCents: -8_250, rMultiple: -1.6, gapExit: true })
    expect(screen.getByTestId('gap-note')).toHaveTextContent('worse than your stop')
  })

  it('BUG-059: a gap past the target is called better than the target, not compared with a stop', () => {
    card({ ...TARGET, exitTicks: 80_014, pnlTicks: 14, netPnlCents: 6_750, rMultiple: 1.4, gapExit: true })
    expect(screen.getByTestId('gap-note')).toHaveTextContent('better than your target')
    expect(screen.getByTestId('gap-note')).not.toHaveTextContent('Real stops')
  })

  it('reads a flat finish at the session end: no profit, so only the commission, in red', () => {
    card({ ...TARGET, outcome: 'TIME_EXIT', exitTicks: 80_000, pnlTicks: 0, netPnlCents: -250, rMultiple: 0 })
    expect(screen.getByTestId('result-outcome')).toHaveTextContent('Closed at the session end')
    expect(screen.getByTestId('result-net')).toHaveTextContent('−$2.50')
    expect(screen.getByTestId('result-r')).toHaveTextContent('0.00R')
  })

  it("reads the learner's own close, and how it was filled", () => {
    const shown = card({ ...TARGET, outcome: 'CLOSED', exitTicks: 80_002, pnlTicks: 2, netPnlCents: 750, rMultiple: 0.2 })
    expect(screen.getByTestId('result-outcome')).toHaveTextContent('Closed by you')
    expect(shown).toHaveTextContent('You closed it at market')
  })

  it("reads a short, with several contracts, its money for all of them", () => {
    const short: TradeView = {
      number: 3,
      plan: { ...LONG.plan, direction: 'SHORT', stopTicks: 80_010, targetTicks: 79_990, contracts: 3 },
    }
    const shown = card(
      { ...TARGET, exitTicks: 79_990, pnlTicks: 10, netPnlCents: 10 * 500 * 3 - 250 * 3, commissionCents: 750, rMultiple: 1 },
      short,
    )
    expect(screen.getByTestId('result-outcome')).toHaveTextContent('Trade 3 · Target hit')
    expect(shown).toHaveTextContent('Short3 contracts')
    expect(screen.getByTestId('result-net')).toHaveTextContent('$142.50')
    expect(shown).toHaveTextContent('Commission$7.50')
  })
})
