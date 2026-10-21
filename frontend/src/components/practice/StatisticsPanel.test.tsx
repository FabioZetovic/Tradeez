import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { StatisticsPanel } from './StatisticsPanel'
import type { Statistics, StatisticsGroup } from '@/lib/api/practice'

const group = (label: string, over: Partial<StatisticsGroup> = {}): StatisticsGroup => ({
  label,
  trades: 4,
  wins: 2,
  losses: 2,
  winRate: 50,
  avgWinR: 1.75,
  avgLossR: -1,
  expectancyR: 0.38,
  low: -1.19,
  high: 1.94,
  profitFactor: 1.75,
  totalR: 1.5,
  netPnlCents: 30_000,
  reading: 'Cannot be told from zero yet: the range spans it.',
  ...over,
})

const STATS: Statistics = {
  version: 'journal-stats-1.0.0',
  overall: group('All trades', { trades: 5, winRate: 66.67 }),
  dimensions: [
    { id: 'model', title: 'Model', groups: [group('Silver Bullet'), group('No model', { trades: 1, low: undefined, high: undefined, expectancyR: 2, wins: 1, losses: 0, winRate: 100, avgLossR: undefined, profitFactor: undefined, reading: 'One trade: no range yet.' })] },
    { id: 'killzone', title: 'Killzone', groups: [group('New York AM', { trades: 5 })] },
  ],
  notes: [],
}

describe('journal statistics (T25)', () => {
  it('reads the whole and then one grouping at a time, the trades behind every row', async () => {
    const user = userEvent.setup()
    render(<StatisticsPanel statistics={STATS} />)
    expect(screen.getByTestId('statistics-overall')).toHaveTextContent(
      '5 trades · win rate 67% · expectancy +0.38R (−1.19R to +1.94R) · profit factor 1.75 · total +1.50R',
    )
    expect(screen.getByText('Cannot be told from zero yet: the range spans it.')).toBeInTheDocument()

    const rows = screen.getAllByTestId('statistics-row')
    expect(rows.map((r) => [...r.querySelectorAll('td')].map((td) => td.textContent))).toEqual([
      ['Silver Bullet', '4', '50%', '+1.75R', '−1.00R', '+0.38R (−1.19R to +1.94R)', '1.75', '+1.50R'],
      // One trade: no range, no loss, no profit factor — and said so on hover.
      ['No model', '1', '100%', '+1.75R', '—', '+2.00R', '—', '+1.50R'],
    ])
    expect(rows[1]).toHaveAttribute('title', 'One trade: no range yet.')

    await user.click(within(screen.getByRole('group', { name: 'Group the trades by' })).getByRole('button', { name: 'Killzone' }))
    expect(screen.getAllByTestId('statistics-row')).toHaveLength(1)
    expect(screen.getByRole('columnheader', { name: 'Killzone' })).toBeInTheDocument()
    expect(screen.getByTestId('statistics-row')).toHaveTextContent('New York AM5')
    expect(screen.getByText(/journal-stats-1\.0\.0\./)).toBeInTheDocument()
  })

  it('with nothing finished yet, says so and shows no figures', () => {
    render(
      <StatisticsPanel
        statistics={{ ...STATS, overall: group('All trades', { trades: 0, winRate: undefined }), notes: ['No finished trade yet.'] }}
      />,
    )
    expect(screen.getByText('No finished trade yet.')).toBeInTheDocument()
    expect(screen.queryByTestId('statistics-overall')).toBeNull()
    expect(screen.queryByRole('table')).toBeNull()
  })
})
